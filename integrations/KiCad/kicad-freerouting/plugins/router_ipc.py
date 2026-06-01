# ---------------------------------------------------------------------------
# router_ipc.py — IPC/API-based routing workflow
# ---------------------------------------------------------------------------
# This module implements the modern routing workflow: serialize the board
# to JSON via KiCad's IPC API, start Freerouting as a local REST API
# server, upload the JSON, start the job, poll for completion, download
# the result, and apply it back to KiCad.
# ---------------------------------------------------------------------------

import json
import subprocess
import textwrap
import threading
import time

import pcbnew

from .api_client import FreeroutingApiClient
from .config import API_JOB_TIMEOUT, API_POLL_INTERVAL, API_SERVER_STARTUP_TIMEOUT
from .gui_helpers import wx_show_error
from .ipc_helpers import get_board_json_via_ipc
from .java_utils import detect_os_architecture, get_local_java_executable_path
from .process_utils import ProcessDialog


class IpcRouter:
    """IPC/API-based routing workflow.

    Orchestrates the full lifecycle: board serialization → API server
    startup → session/job creation → JSON upload → job start → polling
    → result download → board update.
    """

    def __init__(self, plugin):
        """Args:
            plugin: The parent ``FreeroutingPlugin`` instance.
        """
        self.plugin = plugin
        self._api_process = None

    # ------------------------------------------------------------------
    # Main entry point
    # ------------------------------------------------------------------

    def run(self):
        """Execute the complete IPC/API routing workflow.

        Returns:
            ``True`` if routing completed and results were applied.
        """
        # Step 1: Serialize board to JSON
        print("Serializing board to JSON via KiCad IPC API...")
        try:
            board_json = get_board_json_via_ipc()
        except Exception as e:
            wx_show_error(textwrap.dedent(f"""
                Failed to serialize board to JSON via IPC:
                {e}

                Falling back to DSN mode.
            """))
            return False  # Caller should fall back to DSN

        # Step 2: Save debug JSON
        self._save_debug(board_json, self.plugin.json_debug_path)

        # Step 3: Ensure Java is available
        os_name, _ = detect_os_architecture()
        if not self.plugin.java_path:
            self.plugin.java_path = get_local_java_executable_path(os_name)
        if not self.plugin.java_path:
            wx_show_error("Java 25+ is required but could not be found.")
            return False

        # Step 4: Build API server command and start it
        self._build_api_command()
        if not self._start_api_server():
            return False

        try:
            return self._run_workflow(board_json)
        except Exception as e:
            wx_show_error(f"IPC routing workflow failed:\n{e}")
            return False

    # ------------------------------------------------------------------
    # API server management
    # ------------------------------------------------------------------

    def _build_api_command(self):
        """Build the command to start Freerouting as a headless API server."""
        self.plugin.module_command = [
            str(self.plugin.java_path),
            "-jar",
            str(self.plugin.module_path),
            "-api_server.enabled=true",
            "-api_server.endpoints=http://127.0.0.1:37864",
            "-api_server.authentication.enabled=false",
            "-gui.enabled=false",
        ]

    def _start_api_server(self):
        """Launch the Freerouting API server and wait for it to be ready.

        Returns:
            ``True`` if the server started successfully.
        """
        print("Starting Freerouting API server...")
        try:
            self._api_process = subprocess.Popen(
                self.plugin.module_command,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
        except Exception as e:
            wx_show_error(f"Failed to start Freerouting API server:\n{e}")
            return False

        client = FreeroutingApiClient()
        for attempt in range(API_SERVER_STARTUP_TIMEOUT):
            time.sleep(1)
            if client.health_check():
                print("Freerouting API server is ready.")
                return True
            if self._api_process.poll() is not None:
                stderr = self._api_process.stderr.read() if self._api_process.stderr else ""
                wx_show_error(textwrap.dedent(f"""
                    Freerouting API server exited prematurely
                    (exit code {self._api_process.returncode}).
                    stderr:
                    {stderr}
                """))
                return False

        wx_show_error("Freerouting API server did not become ready in time.")
        self._api_process.terminate()
        return False

    # ------------------------------------------------------------------
    # Workflow steps
    # ------------------------------------------------------------------

    def _run_workflow(self, board_json):
        """Execute the REST API workflow after the server is ready.

        Returns:
            ``True`` on success.
        """
        client = FreeroutingApiClient()

        # Create session
        print("Creating Freerouting session...")
        session_id = client.create_session(host_name="KiCad")
        if not session_id:
            raise RuntimeError("Failed to create session.")
        print(f"Session: {session_id}")

        # Bind to GUI visualizer (best-effort)
        client.set_monitored_session(session_id)

        # Enqueue job
        job_name = (
            self.plugin.board.GetFileName().stem
            if self.plugin.board.GetFileName()
            else "KiCad_Job"
        )
        print(f"Enqueuing job '{job_name}'...")
        job_id = client.enqueue_job(session_id, job_name=job_name)
        if not job_id:
            raise RuntimeError("Failed to enqueue job.")
        print(f"Job: {job_id}")

        # Upload JSON
        print("Uploading board JSON...")
        if not client.upload_json_input(job_id, board_json):
            raise RuntimeError("Failed to upload JSON input.")

        # Start job
        print("Starting routing job...")
        if not client.start_job(job_id):
            raise RuntimeError("Failed to start job.")

        # Poll for completion with progress dialog
        return self._poll_and_apply(client, job_id, session_id)

    def _poll_and_apply(self, client, job_id, session_id):
        """Show a progress dialog, poll for completion, and apply results.

        Returns:
            ``True`` on success.
        """
        dialog = ProcessDialog(
            None,
            textwrap.dedent(f"""
                Freerouting IPC Routing in Progress
                Job: {job_id}
                Session: {session_id}

                * to complete, wait for routing to finish
                * to terminate, press Terminate here
            """),
        )

        result = {"success": False, "output_json": None, "cancelled": False}

        def poll():
            try:
                ok, output = client.wait_for_job_completion(
                    job_id,
                    poll_interval=API_POLL_INTERVAL,
                    timeout=API_JOB_TIMEOUT,
                )
                result["success"] = ok
                result["output_json"] = output
            except Exception as e:
                result["success"] = False
                result["error"] = str(e)
            finally:
                from .gui_helpers import wx_safe_invoke
                wx_safe_invoke(dialog.terminate)

        poll_thread = threading.Thread(target=poll, daemon=True)
        dialog.Show()
        poll_thread.start()
        modal_result = dialog.ShowModal()
        dialog.Destroy()
        poll_thread.join(timeout=15)

        if modal_result == dialog.result_button:
            print("Routing cancelled by user.")
            client.cancel_job(job_id)
            return False

        if not result["success"]:
            wx_show_error(f"Routing failed:\n{result.get('error', 'Unknown error')}")
            return False

        output_json = result["output_json"]
        if not output_json:
            wx_show_error("Routing completed but no output was returned.")
            return False

        # Save result JSON for debugging
        self._save_debug(output_json, self.plugin.routing_dir / "freerouting_result.json")

        # Apply result back to KiCad
        print("Applying routing result to KiCad...")
        try:
            self._apply_result_to_kicad(output_json)
            print("Routing result applied successfully.")
        except Exception as e:
            print(f"Warning: could not apply result: {e}")
            wx_show_error(textwrap.dedent(f"""
                Routing completed, but the result could not be applied
                to KiCad automatically.  The result JSON has been saved to:
                {self.plugin.routing_dir / "freerouting_result.json"}

                Error: {e}
            """))
        return True

    # ------------------------------------------------------------------
    # Result application
    # ------------------------------------------------------------------

    def _apply_result_to_kicad(self, json_str):
        """Apply a KiCad JSON routing result back to the board.

        Tries IPC write-back methods first, then falls back to manual
        pcbnew API calls.
        """
        try:
            board_data = json.loads(json_str)
        except json.JSONDecodeError as e:
            raise RuntimeError(f"Invalid JSON result: {e}")

        board = pcbnew.GetBoard()
        if board is None:
            raise RuntimeError("No board loaded.")

        # Try IPC write-back
        for method_name in ("ApplyBoardJson", "import_json", "ImportBoardJson"):
            if hasattr(pcbnew, method_name):
                try:
                    getattr(pcbnew, method_name)(board, json_str)
                    return
                except Exception as e:
                    print(f"pcbnew.{method_name}() failed: {e}")

        # Fallback: manual trace/via creation
        self._apply_traces_and_vias(board, board_data)

    def _apply_traces_and_vias(self, board, data):
        """Manually create traces and vias from JSON data via pcbnew API."""
        # Build net name → net code mapping
        net_map = {}
        for net in data.get("nets", []):
            name = net.get("name", "")
            nid = net.get("id", 0)
            if name and nid:
                net_map[name] = nid

        # Traces
        for trace in data.get("traces", []):
            try:
                net_code = net_map.get(trace.get("netName", ""))
                if net_code is None:
                    continue
                net = board.GetNetByNetCode(net_code)
                if net is None:
                    continue
                width = int(trace.get("width", 0.25) * 1e6)
                layer = trace.get("layerIndex", 0)
                points = trace.get("points", [])
                for i in range(len(points) - 1):
                    t = pcbnew.PCB_TRACK(board)
                    t.SetStart(pcbnew.VECTOR2I(int(points[i]["x"] * 1e6), int(points[i]["y"] * 1e6)))
                    t.SetEnd(pcbnew.VECTOR2I(int(points[i + 1]["x"] * 1e6), int(points[i + 1]["y"] * 1e6)))
                    t.SetWidth(width)
                    t.SetLayer(layer)
                    t.SetNet(net)
                    board.Add(t)
            except Exception as e:
                print(f"Warning: could not apply trace: {e}")

        # Vias
        for via in data.get("vias", []):
            try:
                net_code = net_map.get(via.get("netName", ""))
                if net_code is None:
                    continue
                net = board.GetNetByNetCode(net_code)
                if net is None:
                    continue
                pos = via.get("position", {})
                v = pcbnew.PCB_VIA(board)
                v.SetPosition(pcbnew.VECTOR2I(int(pos.get("x", 0) * 1e6), int(pos.get("y", 0) * 1e6)))
                v.SetWidth(int(via.get("diameter", 0.8) * 1e6))
                v.SetDrill(int(via.get("drill", 0.4) * 1e6))
                v.SetNet(net)
                board.Add(v)
            except Exception as e:
                print(f"Warning: could not apply via: {e}")

        try:
            pcbnew.Refresh()
        except Exception:
            pass

    # ------------------------------------------------------------------
    # Utility
    # ------------------------------------------------------------------

    @staticmethod
    def _save_debug(content, path):
        """Save a string to a file for debugging purposes."""
        try:
            with open(path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"Debug file saved to: {path}")
        except Exception as e:
            print(f"Warning: could not save debug file: {e}")