# ---------------------------------------------------------------------------
# plugin.py — KiCad Freerouting Plugin entry point
# ---------------------------------------------------------------------------
# This is the main plugin class that KiCad loads.  It extends
# ``pcbnew.ActionPlugin`` and delegates all work to the specialized
# modules in this package:
#
#   * ``config``        — constants and settings
#   * ``gui_helpers``   — wx GUI utilities (dialogs, thread-safe invoke)
#   * ``java_utils``    — Java detection, version checking, JRE install
#   * ``ipc_helpers``   — KiCad IPC API detection and board serialization
#   * ``api_client``    — Freerouting REST API client
#   * ``process_utils`` — ProcessDialog and ProcessThread
#   * ``router_ipc``    — IPC/API routing workflow
#   * ``router_dsn``    — Legacy DSN routing workflow
#
# The plugin auto-detects whether KiCad's IPC API is available and
# chooses the appropriate routing mode.  When IPC is unavailable it
# falls back to the legacy DSN file-exchange workflow transparently.
# ---------------------------------------------------------------------------

import configparser
import textwrap
import threading
from pathlib import Path

import pcbnew
import wx

from .config import DEFAULT_ROUTING_MODE, API_POLL_INTERVAL, API_JOB_TIMEOUT
from .gui_helpers import has_pcbnew_api, wx_show_error, wx_safe_invoke
from .ipc_helpers import is_ipc_available, get_board_json_via_ipc
from .process_utils import ProcessDialog, STATUS_UNDETERMINED, STATUS_IN_PROGRESS, STATUS_PASS, STATUS_FAIL
from .router_dsn import DsnRouter
from .router_ipc import IpcRouter


class FreeroutingPlugin(pcbnew.ActionPlugin):
    """KiCad action plugin that launches Freerouting for auto-routing.

    Supports two routing modes:
      * **IPC/API** (default, requires KiCad 9+): serializes the board
        to JSON via the IPC API and communicates with Freerouting through
        its REST API.
      * **DSN** (legacy fallback): exports/imports Specctra DSN/SES files.

    The mode is selected automatically based on IPC availability.
    """

    def defaults(self):
        """Set plugin metadata (called by KiCad on registration)."""
        self.name = "Freerouting"
        self.category = "PCB auto routing"
        self.description = "Freerouting for PCB auto routing"
        self.show_toolbar_button = True
        self.icon_file_name = str(Path(__file__).parent / "icon_24x24.png")
        self.host = "KiCad"
        self.SPECCTRA = True
        self.routing_mode = DEFAULT_ROUTING_MODE

    # ------------------------------------------------------------------
    # Main entry point
    # ------------------------------------------------------------------

    def Run(self):
        """KiCad plugin entry point."""
        if self.SPECCTRA:
            if has_pcbnew_api():
                self._run_steps()
            else:
                wx_show_error(
                    "Missing required python API:\n"
                    "  pcbnew.ExportSpecctraDSN\n"
                    "  pcbnew.ImportSpecctraSES\n"
                    "Try a KiCad development nightly build."
                )
        else:
            self._run_steps()

    # ------------------------------------------------------------------
    # Internal workflow
    # ------------------------------------------------------------------

    def _run_steps(self):
        """Prepare the environment, run pre-flight checks, and dispatch to the appropriate router."""
        board = pcbnew.GetBoard()
        board_path = Path(board.GetFileName())
        dirpath = board_path.parent

        # Load plugin configuration
        here_path = Path(__file__).parent
        config = configparser.ConfigParser()
        config.read(here_path / "plugin.ini")
        module_file = config["artifact"]["location"]

        # Set common attributes needed by both routers
        self.board = board
        self.dirpath = dirpath
        self.here_path = here_path
        self.module_file = module_file
        self.module_path = here_path / module_file

        # Handle spaces in project path
        import tempfile
        if " " in str(dirpath):
            self.routing_dir = Path(tempfile.mkdtemp(prefix="freerouting_"))
            print(f"Using temp routing dir: {self.routing_dir}")
        else:
            self.routing_dir = dirpath

        self.module_input = self.routing_dir / "freerouting.dsn"
        self.temp_input = self.routing_dir / "temp-freerouting.dsn"
        self.module_output = self.routing_dir / "freerouting.ses"
        self.json_debug_path = self.routing_dir / "freerouting_debug.json"

        # Clean up previous temp files
        for f in (self.temp_input, self.module_output):
            f.unlink(missing_ok=True)

        # --- Show the progress dialog (modeless so timers/CallAfter work) ---
        dialog = ProcessDialog(None)
        dialog.Show()

        app = wx.GetApp() or wx.App()

        def pump_events():
            """Process pending wx events so the dialog stays responsive."""
            app.ProcessPendingEvents()

        # ============================================================
        # Stage 1: Detect Java 25+ JRE
        # ============================================================
        dialog.set_java_status(STATUS_IN_PROGRESS)
        pump_events()

        from .java_utils import detect_os_architecture, get_local_java_executable_path
        os_name, _ = detect_os_architecture()
        java_path = get_local_java_executable_path(os_name)
        java_ok = bool(java_path)
        dialog.set_java_status(STATUS_PASS if java_ok else STATUS_FAIL)
        pump_events()

        if not java_ok:
            dialog.Destroy()
            wx_show_error("Java 25+ is required but could not be found.\nPlease install Java 25 or later.")
            self._cleanup()
            return

        self.java_path = java_path

        # ============================================================
        # Stage 2: Check KiCad IPC API
        # ============================================================
        dialog.set_ipc_status(STATUS_IN_PROGRESS)
        pump_events()

        ipc_ok = (self.routing_mode == "IPC" and is_ipc_available())
        dialog.set_ipc_status(STATUS_PASS if ipc_ok else STATUS_FAIL)
        pump_events()

        # Determine routing mode
        if ipc_ok:
            print("=== Routing mode: IPC/API ===")
            router = IpcRouter(self)
        else:
            if self.routing_mode == "IPC":
                print("IPC not available, falling back to DSN mode.")
            else:
                print("=== Routing mode: DSN (legacy) ===")
            router = DsnRouter(self)

        # ============================================================
        # Stage 3-5: Execute routing (each stage updates the dialog)
        # ============================================================
        cancelled = False
        try:
            if isinstance(router, IpcRouter):
                cancelled = self._run_ipc_stages(router, dialog, pump_events)
            else:
                cancelled = self._run_dsn_stages(router, dialog, pump_events)
        except Exception as e:
            wx_show_error(f"Routing failed:\n{e}")
        finally:
            dialog.Destroy()

        # Clean up temp directory if one was created
        self._cleanup()

    def _cleanup(self):
        """Remove the temporary routing directory if one was created."""
        if hasattr(self, "routing_dir") and self.routing_dir != self.dirpath:
            import shutil
            try:
                shutil.rmtree(str(self.routing_dir), ignore_errors=True)
            except Exception as e:
                print(f"Warning: could not remove temp dir: {e}")

    def _run_ipc_stages(self, router, dialog, pump_events):
        """Execute the IPC/API routing workflow with status updates.

        Returns:
            ``True`` if the user cancelled.
        """
        client = FreeroutingApiClient()

        # --- Stage 3: Sending board to Freerouting ---
        dialog.set_sending_status(STATUS_IN_PROGRESS)
        pump_events()

        # Serialize board to JSON
        print("Serializing board to JSON via KiCad IPC API...")
        try:
            board_json = get_board_json_via_ipc()
        except Exception as e:
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error(textwrap.dedent(f"""
                Failed to serialize board to JSON via IPC:
                {e}
            """))
            return False

        self._save_debug(board_json, self.json_debug_path)

        # Start API server
        router._build_api_command()
        if not router._start_api_server():
            dialog.set_sending_status(STATUS_FAIL)
            return False

        # Create session, enqueue job, upload JSON
        session_id = client.create_session(host_name="KiCad")
        if not session_id:
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to create Freerouting session.")
            return False

        client.set_monitored_session(session_id)

        job_name = (
            self.board.GetFileName().stem
            if self.board.GetFileName()
            else "KiCad_Job"
        )
        job_id = client.enqueue_job(session_id, job_name=job_name)
        if not job_id:
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to enqueue routing job.")
            return False

        if not client.upload_json_input(job_id, board_json):
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to upload board JSON.")
            return False

        if not client.start_job(job_id):
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to start routing job.")
            return False

        dialog.set_sending_status(STATUS_PASS)
        pump_events()

        # --- Stage 4: Auto-router is running ---
        dialog.set_routing_status(STATUS_IN_PROGRESS)
        pump_events()

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
                wx_safe_invoke(dialog.terminate)

        poll_thread = threading.Thread(target=poll, daemon=True)
        poll_thread.start()

        # Modal loop for the routing stage
        modal_result = dialog.ShowModal()
        poll_thread.join(timeout=15)

        if modal_result == dialog.result_button:
            print("Routing cancelled by user.")
            client.cancel_job(job_id)
            dialog.set_routing_status(STATUS_FAIL)
            return True

        if not result["success"]:
            dialog.set_routing_status(STATUS_FAIL)
            wx_show_error(f"Routing failed:\n{result.get('error', 'Unknown error')}")
            return False

        dialog.set_routing_status(STATUS_PASS)
        pump_events()

        # --- Stage 5: Receiving the results ---
        dialog.set_receiving_status(STATUS_IN_PROGRESS)
        pump_events()

        output_json = result["output_json"]
        if not output_json:
            dialog.set_receiving_status(STATUS_FAIL)
            wx_show_error("Routing completed but no output was returned.")
            return False

        # Save result JSON for debugging
        self._save_debug(output_json, self.routing_dir / "freerouting_result.json")

        # Apply result back to KiCad
        print("Applying routing result to KiCad...")
        try:
            self._apply_result_to_kicad(output_json)
            print("Routing result applied successfully.")
            dialog.set_receiving_status(STATUS_PASS)
        except Exception as e:
            print(f"Warning: could not apply result: {e}")
            dialog.set_receiving_status(STATUS_FAIL)
            wx_show_error(textwrap.dedent(f"""
                Routing completed, but the result could not be applied
                to KiCad automatically.  The result JSON has been saved to:
                {self.routing_dir / "freerouting_result.json"}

                Error: {e}
            """))
        pump_events()

        return False

    def _run_dsn_stages(self, router, dialog, pump_events):
        """Execute the DSN routing workflow with status updates.

        Returns:
            ``True`` if the user cancelled.
        """
        # --- Stage 3: Sending board to Freerouting ---
        dialog.set_sending_status(STATUS_IN_PROGRESS)
        pump_events()

        if not router.prepare(self.board, self.dirpath, self.here_path, self.java_path, self.module_file):
            dialog.set_sending_status(STATUS_FAIL)
            return False

        dialog.set_sending_status(STATUS_PASS)
        pump_events()

        # --- Stage 4: Auto-router is running ---
        dialog.set_routing_status(STATUS_IN_PROGRESS)
        pump_events()

        # Create a ProcessThread to run Freerouting
        from .process_utils import ProcessThread

        def on_complete():
            wx_safe_invoke(dialog.terminate)

        invoker = ProcessThread(self.module_command, on_complete)
        invoker.start()

        modal_result = dialog.ShowModal()
        invoker.join(timeout=10)

        if modal_result == dialog.result_button:
            print("Routing cancelled by user.")
            invoker.terminate()
            dialog.set_routing_status(STATUS_FAIL)
            return True

        if not invoker.has_ok():
            dialog.set_routing_status(STATUS_FAIL)
            invoker.show_error()
            return False

        dialog.set_routing_status(STATUS_PASS)
        pump_events()

        # --- Stage 5: Receiving the results ---
        dialog.set_receiving_status(STATUS_IN_PROGRESS)
        pump_events()

        if not router.import_ses():
            dialog.set_receiving_status(STATUS_FAIL)
            return False

        dialog.set_receiving_status(STATUS_PASS)
        pump_events()

        return False

    @staticmethod
    def _save_debug(content, path):
        """Save a string to a file for debugging purposes."""
        try:
            with open(path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"Debug file saved to: {path}")
        except Exception as e:
            print(f"Warning: could not save debug file: {e}")

    @staticmethod
    def _apply_result_to_kicad(json_str):
        """Apply a KiCad JSON routing result back to the board."""
        import json
        board_data = json.loads(json_str)
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
        net_map = {}
        for net in board_data.get("nets", []):
            name = net.get("name", "")
            nid = net.get("id", 0)
            if name and nid:
                net_map[name] = nid

        for trace in board_data.get("traces", []):
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

        for via in board_data.get("vias", []):
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


# Register the plugin with KiCad's plugin manager.
FreeroutingPlugin().register()