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

from .config import (
    DEFAULT_ROUTING_MODE, API_POLL_INTERVAL, API_JOB_TIMEOUT,
    SAVE_DEBUG_JSON, DEBUG_JSON_DIR, DEBUG_INPUT_JSON_FILENAME, DEBUG_OUTPUT_JSON_FILENAME,
    LOG_DIR,
)
from .gui_helpers import has_pcbnew_api, wx_show_error, wx_safe_invoke
from .ipc_helpers import is_ipc_available, get_board_json_via_ipc
from .process_utils import ProcessDialog, STATUS_UNDETERMINED, STATUS_IN_PROGRESS, STATUS_PASS, STATUS_FAIL
from .router_dsn import DsnRouter
from .router_ipc import IpcRouter
from .api_client import FreeroutingApiClient

import logging

def _setup_logger():
    try:
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        log_file = LOG_DIR / "freerouting_kicad_plugin.log"
        
        logger = logging.getLogger("freerouting")
        logger.setLevel(logging.DEBUG)
        
        if not logger.handlers:
            fh = logging.FileHandler(log_file, mode='a', encoding='utf-8')
            fh.setLevel(logging.DEBUG)
            
            formatter = logging.Formatter('%(asctime)s - %(levelname)s - [%(filename)s:%(lineno)d] - %(message)s')
            fh.setFormatter(formatter)
            logger.addHandler(fh)
            
            ch = logging.StreamHandler()
            ch.setLevel(logging.DEBUG)
            ch.setFormatter(formatter)
            logger.addHandler(ch)
            
        logger.info("Logging configured successfully in plugin.py.")
        return logger
    except Exception as e:
        print(f"Failed to setup logging: {e}")
        return logging.getLogger("freerouting")

logger = _setup_logger()



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
        logger.info("Starting _run_steps...")
        board = pcbnew.GetBoard()
        board_path = Path(board.GetFileName())
        dirpath = board_path.parent
        logger.info(f"Loaded board: {board_path.name}")

        # Load plugin configuration
        here_path = Path(__file__).parent
        config = configparser.ConfigParser()
        config.read(here_path / "plugin.ini")
        module_file = config["artifact"]["location"]
        logger.info(f"Freerouting jar configured at: {module_file}")

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
            logger.info(f"Using temp routing dir due to spaces in path: {self.routing_dir}")
        else:
            self.routing_dir = dirpath

        self.module_input = self.routing_dir / "freerouting.dsn"
        self.temp_input = self.routing_dir / "temp-freerouting.dsn"
        self.module_output = self.routing_dir / "freerouting.ses"
        self.json_debug_path = self.routing_dir / "freerouting_debug.json"

        # Clean up previous temp files
        for f in (self.temp_input, self.module_output):
            f.unlink(missing_ok=True)

        # --- Show the progress dialog and force an immediate paint ---
        dialog = ProcessDialog(None)
        dialog.show_and_paint()   # renders fully before background thread starts
        logger.info("Progress dialog displayed.")

        app = wx.GetApp() or wx.App()

        def pump_events():
            """Process pending wx events so the dialog stays responsive."""
            app.ProcessPendingEvents()

        # Use a background thread for pre-flight checks so the dialog stays responsive
        check_results = {"java_path": "", "java_ok": False, "ipc_ok": False}

        def run_checks():
            logger.info("Background thread checking Java and IPC availability...")
            from .java_utils import detect_os_architecture, get_local_java_executable_path
            os_name, _ = detect_os_architecture()

            # Java check
            path = get_local_java_executable_path(os_name)
            ok = bool(path)
            check_results["java_path"] = path
            check_results["java_ok"] = ok
            logger.info(f"Java version check: ok={ok}, path={path}")
            def update_java():
                dialog.set_java_status(STATUS_PASS if ok else STATUS_FAIL)
            wx.CallAfter(update_java)

            # IPC check
            ipc = (self.routing_mode == "IPC" and is_ipc_available())
            check_results["ipc_ok"] = ipc
            logger.info(f"IPC capability check: ok={ipc}")
            def update_ipc():
                dialog.set_ipc_status(STATUS_PASS if ipc else STATUS_FAIL)
            wx.CallAfter(update_ipc)

        # Start with spinner for both checks
        dialog.set_java_status(STATUS_IN_PROGRESS)
        dialog.set_ipc_status(STATUS_IN_PROGRESS)
        pump_events()

        check_thread = threading.Thread(target=run_checks, daemon=True)
        check_thread.start()

        # Wait for the thread, pumping events so the dialog renders/animates
        import time as _time
        while check_thread.is_alive():
            pump_events()
            _time.sleep(0.05)

        pump_events()

        java_ok = check_results["java_ok"]
        java_path = check_results["java_path"]
        ipc_ok = check_results["ipc_ok"]

        if not java_ok:
            logger.error("Java 25+ JRE check failed.")
            dialog.Destroy()
            wx_show_error("Java 25+ is required but could not be found.\nPlease install Java 25 or later.")
            self._cleanup()
            return

        self.java_path = java_path

        # Determine routing mode
        if ipc_ok:
            logger.info("=== Routing mode: IPC/API ===")
            router = IpcRouter(self)
        else:
            if self.routing_mode == "IPC":
                logger.warning("IPC not available, falling back to DSN mode.")
            else:
                logger.info("=== Routing mode: DSN (legacy) ===")
            router = DsnRouter(self)

        # ============================================================
        # Stage: Starting up Freerouting API server
        # (only for IPC mode — DSN mode does not use the REST API)
        # ============================================================
        if isinstance(router, IpcRouter):
            dialog.set_api_status(STATUS_IN_PROGRESS)
            pump_events()

            client = FreeroutingApiClient()
            router._build_api_command()

            if client.health_check():
                # Already running — nothing to do
                logger.info("Freerouting API server is already running.")
                dialog.set_api_status(STATUS_PASS)
                pump_events()
            else:
                # Not running — start it and wait for it to become ready
                logger.info("Starting Freerouting API server...")
                if not router._start_api_server():
                    logger.error("Could not start Freerouting API server.")
                    dialog.set_api_status(STATUS_FAIL)
                    pump_events()
                    dialog.Destroy()
                    wx_show_error(
                        "Could not start the Freerouting API server.\n"
                        "Check that the Freerouting JAR is present and Java 25+ is installed."
                    )
                    self._cleanup()
                    return
                dialog.set_api_status(STATUS_PASS)
                pump_events()

        # ============================================================
        # Stage 4-6: Execute routing (each stage updates the dialog)
        # ============================================================
        cancelled = False
        success = False
        output_data = None
        try:
            if isinstance(router, IpcRouter):
                cancelled, success, output_data = self._run_ipc_stages(router, dialog, pump_events)
            else:
                cancelled, success = self._run_dsn_stages(router, dialog, pump_events)
        except Exception as e:
            logger.error(f"Routing stages failed with exception: {e}", exc_info=True)
            wx_show_error(f"Routing failed:\n{e}")
        finally:
            logger.info("Destroying ProcessDialog before applying results.")
            dialog.Destroy()

        # Apply results back to KiCad AFTER progress dialog is destroyed!
        if success and not cancelled:
            if isinstance(router, IpcRouter):
                logger.info("Applying routing result to KiCad (IPC mode)...")
                try:
                    self._apply_result_to_kicad(output_data)
                    logger.info("Routing result applied successfully.")
                except Exception as e:
                    logger.error(f"Could not apply result: {e}", exc_info=True)
                    wx_show_error(textwrap.dedent(f"""
                        Routing completed, but the result could not be applied
                        to KiCad automatically.  The result JSON has been saved to:
                        {self.routing_dir / "freerouting_result.json"}

                        Error: {e}
                    """))
            else:
                logger.info("Importing Specctra SES file into KiCad (DSN mode)...")
                if not router.import_ses():
                    logger.error("Failed to import Specctra SES file.")
        else:
            logger.info(f"Routing finished: success={success}, cancelled={cancelled}")

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
            ``(cancelled, success, output_json)``
        """
        logger.info("Executing IPC stages...")
        client = FreeroutingApiClient()

        # --- Stage 3: Sending board to Freerouting ---
        dialog.set_sending_status(STATUS_IN_PROGRESS)
        pump_events()

        # Serialize board to JSON
        logger.info("Serializing board to JSON via KiCad IPC API...")
        try:
            board_json = get_board_json_via_ipc()
            logger.info("Board serialized successfully.")
        except Exception as e:
            logger.error(f"Failed to serialize board to JSON via IPC: {e}", exc_info=True)
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error(textwrap.dedent(f"""
                Failed to serialize board to JSON via IPC:
                {e}
            """))
            return False, False, None

        if SAVE_DEBUG_JSON:
            DEBUG_JSON_DIR.mkdir(parents=True, exist_ok=True)
            self._save_debug(board_json, DEBUG_JSON_DIR / DEBUG_INPUT_JSON_FILENAME)

        # Create session, enqueue job, upload JSON
        session_id = client.create_session(host_name="KiCad")
        if not session_id:
            logger.error("Failed to create Freerouting session.")
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to create Freerouting session.")
            return False, False, None

        client.set_monitored_session(session_id)

        filename = self.board.GetFileName()
        job_name = (
            Path(filename).stem
            if filename
            else "KiCad_Job"
        )
        job_id = client.enqueue_job(session_id, job_name=job_name)
        if not job_id:
            logger.error("Failed to enqueue routing job.")
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to enqueue routing job.")
            return False, False, None

        if not client.upload_json_input(job_id, board_json):
            logger.error("Failed to upload board JSON.")
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to upload board JSON.")
            return False, False, None

        if not client.start_job(job_id):
            logger.error("Failed to start routing job.")
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to start routing job.")
            return False, False, None

        dialog.set_sending_status(STATUS_PASS)
        pump_events()

        # --- Stage 4: Auto-router is running ---
        dialog.set_routing_status(STATUS_IN_PROGRESS)
        pump_events()

        result = {"success": False, "output_json": None, "cancelled": False}

        def poll():
            logger.info("Starting background poll thread...")
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
                wx_safe_invoke(dialog.terminate)

        poll_thread = threading.Thread(target=poll, daemon=True)
        poll_thread.start()

        # Modal loop for the routing stage
        modal_result = dialog.ShowModal()
        poll_thread.join(timeout=15)

        if modal_result == dialog.result_button:
            logger.warning("Routing cancelled by user.")
            client.cancel_job(job_id)
            dialog.set_routing_status(STATUS_FAIL)
            return True, False, None

        if not result["success"]:
            logger.error(f"Routing job failed: {result.get('error', 'Unknown error')}")
            dialog.set_routing_status(STATUS_FAIL)
            wx_show_error(f"Routing failed:\n{result.get('error', 'Unknown error')}")
            return False, False, None

        dialog.set_routing_status(STATUS_PASS)
        pump_events()

        # --- Stage 5: Receiving the results ---
        dialog.set_receiving_status(STATUS_IN_PROGRESS)
        pump_events()

        output_json = result["output_json"]
        if not output_json:
            logger.error("Routing completed but no output JSON was returned.")
            dialog.set_receiving_status(STATUS_FAIL)
            wx_show_error("Routing completed but no output was returned.")
            return False, False, None

        if SAVE_DEBUG_JSON:
            self._save_debug(output_json, DEBUG_JSON_DIR / DEBUG_OUTPUT_JSON_FILENAME)

        dialog.set_receiving_status(STATUS_PASS)
        pump_events()

        return False, True, output_json

    def _run_dsn_stages(self, router, dialog, pump_events):
        """Execute the DSN routing workflow with status updates.

        Returns:
            ``(cancelled, success)``
        """
        logger.info("Executing DSN stages...")
        # --- Stage 3: Sending board to Freerouting ---
        dialog.set_sending_status(STATUS_IN_PROGRESS)
        pump_events()

        if not router.prepare(self.board, self.dirpath, self.here_path, self.java_path, self.module_file):
            logger.error("Failed to prepare DSN router.")
            dialog.set_sending_status(STATUS_FAIL)
            return False, False

        dialog.set_sending_status(STATUS_PASS)
        pump_events()

        # --- Stage 4: Auto-router is running ---
        dialog.set_routing_status(STATUS_IN_PROGRESS)
        pump_events()

        # Create a ProcessThread to run Freerouting
        from .process_utils import ProcessThread

        def on_complete():
            logger.info("ProcessThread completed. Terminating dialog...")
            wx_safe_invoke(dialog.terminate)

        invoker = ProcessThread(self.module_command, on_complete)
        invoker.start()

        modal_result = dialog.ShowModal()
        invoker.join(timeout=10)

        if modal_result == dialog.result_button:
            logger.warning("Routing cancelled by user.")
            invoker.terminate()
            dialog.set_routing_status(STATUS_FAIL)
            return True, False

        if not invoker.has_ok():
            logger.error(f"Routing process failed. Return code: {invoker.process.returncode if invoker.process else 'None'}")
            dialog.set_routing_status(STATUS_FAIL)
            invoker.show_error()
            return False, False

        dialog.set_routing_status(STATUS_PASS)
        pump_events()

        # --- Stage 5: Receiving the results ---
        dialog.set_receiving_status(STATUS_IN_PROGRESS)
        pump_events()

        # We will do import_ses after the dialog is closed, but mark progress here
        dialog.set_receiving_status(STATUS_PASS)
        pump_events()

        return False, True

    @staticmethod
    def _save_debug(content, path):
        """Save a string to a file for debugging purposes."""
        try:
            with open(path, "w", encoding="utf-8") as f:
                f.write(content)
            logger.info(f"Debug file saved to: {path}")
        except Exception as e:
            logger.error(f"Warning: could not save debug file: {e}", exc_info=True)

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
        logger.info("Falling back to manual trace/via creation.")
        net_map = {}
        for net in board_data.get("nets", []):
            name = net.get("name", "")
            nid = net.get("id", 0)
            if name and nid:
                net_map[name] = nid

        def lookup_net(board, net_code):
            if hasattr(board, "GetNetByNetCode"):
                return board.GetNetByNetCode(net_code)
            elif hasattr(board, "FindNet"):
                return board.FindNet(net_code)
            return None

        has_commit = hasattr(pcbnew, "BOARD_COMMIT")
        if has_commit:
            commit = pcbnew.BOARD_COMMIT()
            logger.info("Using BOARD_COMMIT for manual trace/via application.")
        else:
            commit = None
            logger.info("BOARD_COMMIT not available, applying changes directly.")

        for trace in board_data.get("traces", []):
            try:
                net_code = net_map.get(trace.get("netName", ""))
                if net_code is None:
                    continue
                net = lookup_net(board, net_code)
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

        for via in board_data.get("vias", []):
            try:
                net_code = net_map.get(via.get("netName", ""))
                if net_code is None:
                    continue
                net = lookup_net(board, net_code)
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
                logger.info("BOARD_COMMIT pushed successfully.")
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


# Register the plugin with KiCad's plugin manager.
FreeroutingPlugin().register()