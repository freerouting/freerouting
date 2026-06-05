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
import logging

from pathlib import Path

import pcbnew

from .api_client import FreeroutingApiClient

logger = logging.getLogger("freerouting")

from .config import (
    API_JOB_TIMEOUT, API_POLL_INTERVAL, API_SERVER_STARTUP_TIMEOUT,
    SAVE_DEBUG_JSON, DEBUG_JSON_DIR, DEBUG_INPUT_JSON_FILENAME, DEBUG_OUTPUT_JSON_FILENAME,
    LOG_DIR,
)
from .gui_helpers import wx_show_error
from .ipc_helpers import get_board_json_via_ipc
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
        logger.info("Serializing board to JSON via KiCad IPC API...")
        try:
            board_json = get_board_json_via_ipc()
            logger.info("Serialization succeeded.")
        except Exception as e:
            logger.error(f"Failed to serialize board to JSON via IPC: {e}", exc_info=True)
            wx_show_error(textwrap.dedent(f"""
                Failed to serialize board to JSON via IPC:
                {e}

                Falling back to DSN mode.
            """))
            return False  # Caller should fall back to DSN

        # Step 2: Save debug JSON (if enabled)
        if SAVE_DEBUG_JSON:
            DEBUG_JSON_DIR.mkdir(parents=True, exist_ok=True)
            self._save_debug(board_json, DEBUG_JSON_DIR / DEBUG_INPUT_JSON_FILENAME)

        # Step 3: Build API server command and start it
        self._build_api_command()
        if not self._start_api_server():
            return False

        try:
            return self._run_workflow(board_json)
        except Exception as e:
            logger.error(f"IPC routing workflow failed: {e}", exc_info=True)
            wx_show_error(f"IPC routing workflow failed:\n{e}")
            return False

    # ------------------------------------------------------------------
    # API server management
    # ------------------------------------------------------------------

    def _build_api_command(self):
        """Build the command to start Freerouting as a headless API server."""
        LOG_DIR.mkdir(parents=True, exist_ok=True)

        self.plugin.module_command = [
            str(self.plugin.java_path),
            "-jar",
            str(self.plugin.module_path),
            "--api_server.enabled=true",
            "--api_server.endpoints=http://127.0.0.1:37864",
            "--api_server.authentication.enabled=false",
            "--gui.enabled=false",
            f"--logging.file.location={LOG_DIR}",
        ]
        logger.info(f"Built API server command: {' '.join(self.plugin.module_command)}")

    def _start_api_server(self):
        """Launch the Freerouting API server and wait for it to be ready.

        Returns:
            ``True`` if the server started successfully.
        """
        logger.info("Starting Freerouting API server...")
        try:
            self._api_process = subprocess.Popen(
                self.plugin.module_command,
            )
        except Exception as e:
            logger.error(f"Failed to start Freerouting API server: {e}", exc_info=True)
            wx_show_error(f"Failed to start Freerouting API server:\n{e}")
            return False

        client = FreeroutingApiClient()
        for attempt in range(API_SERVER_STARTUP_TIMEOUT):
            time.sleep(1)
            if client.health_check():
                logger.info("Freerouting API server is ready.")
                return True
            if self._api_process.poll() is not None:
                logger.error(f"Freerouting API server exited prematurely (exit code {self._api_process.returncode}).")
                wx_show_error(textwrap.dedent(f"""
                    Freerouting API server exited prematurely
                    (exit code {self._api_process.returncode}).
                """))
                return False

        logger.error("Freerouting API server did not become ready in time.")
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
        logger.info("Creating Freerouting session...")
        session_id = client.create_session(host_name="KiCad")
        if not session_id:
            raise RuntimeError("Failed to create session.")
        logger.info(f"Session created: {session_id}")

        # Bind to GUI visualizer (best-effort)
        client.set_monitored_session(session_id)

        # Enqueue job
        filename = self.plugin.board.GetFileName()
        job_name = (
            Path(filename).stem
            if filename
            else "KiCad_Job"
        )
        logger.info(f"Enqueuing job '{job_name}'...")
        job_id = client.enqueue_job(session_id, job_name=job_name)
        if not job_id:
            raise RuntimeError("Failed to enqueue job.")
        logger.info(f"Job enqueued: {job_id}")

        # Upload JSON
        logger.info("Uploading board JSON...")
        if not client.upload_json_input(job_id, board_json):
            raise RuntimeError("Failed to upload JSON input.")

        # Start job
        logger.info("Starting routing job...")
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
            logger.info("Starting background poll thread in router_ipc...")
            try:
                ok, output = client.wait_for_job_completion(
                    job_id,
                    poll_interval=API_POLL_INTERVAL,
                    timeout=API_JOB_TIMEOUT,
                )
                result["success"] = ok
                result["output_json"] = output
                logger.info(f"Background poll finished. success={ok}")
            except Exception as e:
                logger.error(f"Error in poll thread: {e}", exc_info=True)
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
            logger.warning("Routing cancelled by user.")
            client.cancel_job(job_id)
            return False

        if not result["success"]:
            logger.error(f"Routing failed: {result.get('error', 'Unknown error')}")
            wx_show_error(f"Routing failed:\n{result.get('error', 'Unknown error')}")
            return False

        output_json = result["output_json"]
        if not output_json:
            logger.error("Routing completed but no output JSON was returned.")
            wx_show_error("Routing completed but no output was returned.")
            return False

        # Save result JSON for debugging (if enabled)
        if SAVE_DEBUG_JSON:
            self._save_debug(output_json, DEBUG_JSON_DIR / DEBUG_OUTPUT_JSON_FILENAME)

        # Apply result back to KiCad
        logger.info("Applying routing result to KiCad...")
        try:
            self._apply_result_to_kicad(output_json)
            logger.info("Routing result applied successfully.")
        except Exception as e:
            logger.error(f"Warning: could not apply result: {e}", exc_info=True)
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
                    logger.info(f"Trying pcbnew.{method_name}(board, json_str)...")
                    getattr(pcbnew, method_name)(board, json_str)
                    logger.info(f"pcbnew.{method_name}(board, json_str) succeeded.")
                    return
                except TypeError:
                    try:
                        logger.info(f"Trying pcbnew.{method_name}(json_str) fallback...")
                        getattr(pcbnew, method_name)(json_str)
                        logger.info(f"pcbnew.{method_name}(json_str) succeeded.")
                        return
                    except Exception as e:
                        logger.error(f"pcbnew.{method_name}(json_str) failed: {e}", exc_info=True)
                except Exception as e:
                    logger.error(f"pcbnew.{method_name}(board, json_str) failed: {e}", exc_info=True)

        # Fallback: manual trace/via creation
        self._apply_traces_and_vias(board, board_data)

    def _apply_traces_and_vias(self, board, data):
        """Manually create traces and vias from JSON data via pcbnew API."""
        logger.info("Falling back to manual trace/via creation in router_ipc.")
        # Build net name → net code mapping
        net_map = {}
        for net in data.get("nets", []):
            name = net.get("name", "")
            nid = net.get("id", 0)
            if name and nid:
                net_map[name] = nid

        has_commit = hasattr(pcbnew, "BOARD_COMMIT")
        if has_commit:
            commit = pcbnew.BOARD_COMMIT()
            logger.info("Using BOARD_COMMIT for manual trace/via application in router_ipc.")
        else:
            commit = None
            logger.info("BOARD_COMMIT not available, applying changes directly in router_ipc.")

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
                    if commit:
                        commit.Add(t)
            except Exception as e:
                logger.error(f"Warning: could not apply trace: {e}", exc_info=True)

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
                if commit:
                    commit.Add(v)
            except Exception as e:
                logger.error(f"Warning: could not apply via: {e}", exc_info=True)

        if commit:
            try:
                commit.Push()
                logger.info("BOARD_COMMIT pushed successfully in router_ipc.")
            except Exception as e:
                logger.error(f"BOARD_COMMIT Push failed, trying commit.Push(board): {e}")
                try:
                    commit.Push(board)
                except Exception as e2:
                    logger.error(f"commit.Push(board) failed too: {e2}", exc_info=True)

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
            logger.info(f"Debug file saved to: {path}")
        except Exception as e:
            logger.error(f"Warning: could not save debug file: {e}", exc_info=True)