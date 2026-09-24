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
#   * ``board_json_helpers`` — board JSON serialization for JSON/API mode
#   * ``api_client``    — Freerouting REST API client
#   * ``process_utils`` — ProcessDialog and ProcessThread
#   * ``router_json_api`` — JSON/API routing workflow (experimental)
#   * ``router_dsn``    — Legacy DSN routing workflow (default)
#
# DSN mode is the default production path.  Set ``routing_mode`` to
# ``ROUTING_MODE_JSON`` for the experimental JSON/API bridge on KiCad 9+.
# ---------------------------------------------------------------------------

import configparser
import json
import textwrap
import threading
from pathlib import Path

import pcbnew
import wx

from .config import (
    DEFAULT_ROUTING_MODE,
    ROUTING_MODE_DSN,
    ROUTING_MODE_IPC,
    ROUTING_MODE_JSON,
    normalize_routing_mode,
    DEFAULT_GUI_ENABLED,
    API_POLL_INTERVAL,
    API_JOB_TIMEOUT,
    SAVE_DEBUG_JSON,
    DEBUG_JSON_DIR,
    DEBUG_INPUT_JSON_FILENAME,
    DEBUG_OUTPUT_JSON_FILENAME,
    LOG_DIR,
)
from .gui_helpers import has_pcbnew_api, wx_show_error, wx_safe_invoke
from .board_json_helpers import is_json_api_mode_available, serialize_board_to_json
from .process_utils import (
    ProcessDialog,
    STATUS_IN_PROGRESS,
    STATUS_PASS,
    STATUS_FAIL,
    clean_log_line,
    LogTailer,
)
from .router_dsn import DsnRouter
from .router_json_api import JsonApiRouter
from .router_ipc import IpcRouter, is_ipc_available
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


def get_plugin_mode_label(mode, gui_enabled, fallback=False):
    """Return standardized Plugin Mode text for display in the progress dialog.

    Labels follow the standard pattern:
      * DSN + API (legacy) / DSN + GUI (legacy)
      * IPC + API / IPC + GUI
      * JSON + API (legacy) / JSON + GUI (legacy)
    """
    suffix = "GUI" if gui_enabled else "API"
    if mode == ROUTING_MODE_IPC:
        return f"Plugin Mode: IPC + {suffix}"
    elif mode == ROUTING_MODE_JSON:
        return f"Plugin Mode: JSON + {suffix} (legacy)"
    else:
        fb = " - IPC Fallback" if fallback else ""
        return f"Plugin Mode: DSN + {suffix} (legacy{fb})"



class FreeroutingPlugin(pcbnew.ActionPlugin):
    """KiCad action plugin that launches Freerouting for auto-routing.

    Supports two routing modes:
      * **DSN** (default): exports/imports Specctra DSN/SES files.
      * **JSON/API** (experimental, KiCad 9+): SWIG board walk → JSON →
        Freerouting localhost REST API.  Not KiCad protobuf IPC.

    Set ``routing_mode`` to ``ROUTING_MODE_JSON`` to opt into JSON/API mode.
    Legacy ``"IPC"`` is accepted as an alias for ``ROUTING_MODE_JSON``.
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
        self.routing_mode = normalize_routing_mode(DEFAULT_ROUTING_MODE)
        self.gui_enabled = DEFAULT_GUI_ENABLED

    # ------------------------------------------------------------------
    # Main entry point
    # ------------------------------------------------------------------

    def Run(self):
        """KiCad plugin entry point."""
        # Always run steps: if DSN mode lacks SWIG APIs (e.g. KiCad 11+),
        # _run_steps will automatically promote to IPC mode.
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

        # Determine configured routing mode
        routing_mode = DEFAULT_ROUTING_MODE
        if "settings" in config and "routing_mode" in config["settings"]:
            routing_mode = config["settings"].get("routing_mode", fallback=DEFAULT_ROUTING_MODE)
        self.routing_mode = normalize_routing_mode(routing_mode)

        gui_enabled = DEFAULT_GUI_ENABLED
        if "settings" in config and "gui" in config["settings"]:
            try:
                gui_enabled = config["settings"].getboolean("gui", fallback=DEFAULT_GUI_ENABLED)
            except Exception:
                gui_enabled = DEFAULT_GUI_ENABLED
        self.gui_enabled = gui_enabled
        logger.info(f"Freerouting GUI enabled: {self.gui_enabled}, routing mode: {self.routing_mode}")

        # Set common attributes needed by routers
        self.board = board
        self.dirpath = dirpath
        self.here_path = here_path
        self.module_file = module_file
        self.module_path = here_path / module_file

        # Handle spaces in project path
        if " " in str(dirpath):
            import tempfile
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
            """Process pending wx events and advance animation so the dialog stays responsive."""
            try:
                app.ProcessPendingEvents()
                wx.YieldIfNeeded()
                if dialog:
                    dialog.pulse()
            except Exception:
                # Event pumping may fail during teardown or recursive yield; ignore
                pass

        # Check for KiCad 11+ SWIG removal fallback
        active_mode = self.routing_mode
        if active_mode == ROUTING_MODE_DSN and not has_pcbnew_api():
            logger.warning("SWIG ExportSpecctraDSN not available (KiCad 11+ detected). Falling back to Protobuf IPC mode.")
            active_mode = ROUTING_MODE_IPC

        # Configure dialog display for the active mode
        dialog.set_routing_mode_label(get_plugin_mode_label(active_mode, self.gui_enabled))
        if active_mode == ROUTING_MODE_IPC:
            dialog.set_ipc_indicator_label("Checking KiCad IPC socket")
        elif active_mode == ROUTING_MODE_JSON:
            dialog.set_ipc_indicator_label("Checking JSON/API availability")
        else:
            dialog.hide_json_api_indicator()

        # Use a background thread for pre-flight checks so the dialog stays responsive
        check_results = {
            "java_path": "",
            "java_ok": False,
            "ipc_ok": False,
            "ipc_msg": "",
            "json_api_ok": False,
        }

        def run_checks():
            logger.info("Background thread checking Java, IPC, and JSON/API availability...")
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

            # IPC check (if IPC mode is requested or promoted)
            if active_mode == ROUTING_MODE_IPC:
                ipc_ok, ipc_msg = is_ipc_available()
                check_results["ipc_ok"] = ipc_ok
                check_results["ipc_msg"] = ipc_msg
                logger.info(f"IPC capability check: ok={ipc_ok}, msg={ipc_msg}")

                def update_ipc():
                    dialog.set_json_api_status(STATUS_PASS if ipc_ok else STATUS_FAIL)
                wx.CallAfter(update_ipc)

            # JSON/API bridge check (if JSON mode is requested)
            elif active_mode == ROUTING_MODE_JSON:
                json_ok = is_json_api_mode_available()
                check_results["json_api_ok"] = json_ok
                logger.info(f"JSON/API capability check: ok={json_ok}")

                def update_json_api():
                    dialog.set_json_api_status(STATUS_PASS if json_ok else STATUS_FAIL)
                wx.CallAfter(update_json_api)

        # Start with spinner for active checks
        dialog.set_java_status(STATUS_IN_PROGRESS)
        if active_mode in (ROUTING_MODE_IPC, ROUTING_MODE_JSON):
            dialog.set_json_api_status(STATUS_IN_PROGRESS)
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
        ipc_msg = check_results["ipc_msg"]
        json_api_ok = check_results["json_api_ok"]

        if not java_ok:
            logger.error("Java 25+ JRE check failed.")
            dialog.Destroy()
            wx_show_error(
                "Java 25+ is required but could not be found.\n"
                "Please install Java 25 or later from https://adoptium.net/temurin/releases."
            )
            self._cleanup()
            return

        self.java_path = java_path

        # Determine router and handle bidirectional fallback
        router = None
        if active_mode == ROUTING_MODE_IPC:
            if ipc_ok:
                logger.info("=== Routing mode: Protobuf IPC ===")
                router = IpcRouter(self)
            else:
                logger.warning(f"IPC mode requested but unavailable: {ipc_msg}")
                # If DSN is available (KiCad 9/10), offer smooth fallback to DSN
                if has_pcbnew_api():
                    logger.info("Falling back from IPC mode to DSN mode...")
                    dialog.set_routing_mode_label(get_plugin_mode_label(ROUTING_MODE_DSN, self.gui_enabled, fallback=True))
                    dialog.hide_json_api_indicator()
                    pump_events()
                    router = DsnRouter(self)
                else:
                    dialog.Destroy()
                    wx_show_error(
                        f"KiCad IPC API is not reachable:\n{ipc_msg}\n\n"
                        "To use Freerouting on this KiCad version, please enable the KiCad API in:\n"
                        "Preferences > Plugins > Enable KiCad API."
                    )
                    self._cleanup()
                    return
        elif active_mode == ROUTING_MODE_JSON:
            if json_api_ok:
                logger.info("=== Routing mode: JSON/API (transitional) ===")
                router = JsonApiRouter(self)
            else:
                logger.warning("JSON/API mode not available, falling back to DSN mode.")
                router = DsnRouter(self)
        else:
            logger.info("=== Routing mode: DSN (default) ===")
            router = DsnRouter(self)

        # ============================================================
        # Stage: Starting up Freerouting API server
        # (for headless IPC mode and JSON/API mode — GUI mode runs JAR directly)
        # ============================================================
        is_headless_api = (isinstance(router, IpcRouter) and not self.gui_enabled) or isinstance(router, JsonApiRouter)
        if is_headless_api:
            dialog.set_api_status(STATUS_IN_PROGRESS)
            pump_events()

            client = FreeroutingApiClient()
            if isinstance(router, IpcRouter):
                router.build_api_command()
                started = router.start_api_server(pump_callback=pump_events) if not client.health_check() else True
            else:
                router._build_api_command()
                started = router._start_api_server(pump_callback=pump_events) if not client.health_check() else True

            if not started:
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
        elif isinstance(router, IpcRouter) and self.gui_enabled:
            dialog.set_routing_mode_label(get_plugin_mode_label(ROUTING_MODE_IPC, self.gui_enabled))
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
                if self.gui_enabled:
                    cancelled, success, output_data = self._run_ipc_gui_stages(router, dialog, pump_events)
                else:
                    cancelled, success, output_data = self._run_ipc_stages(router, dialog, pump_events)
            elif isinstance(router, JsonApiRouter):
                cancelled, success, output_data = self._run_json_api_stages(router, dialog, pump_events)
            else:
                cancelled, success = self._run_dsn_stages(router, dialog, pump_events)
        except Exception as e:
            logger.error(f"Routing stages failed with exception: {e}", exc_info=True)
            wx_show_error(f"Routing failed:\n{e}")
        finally:
            logger.info("Destroying ProcessDialog before applying results.")
            dialog.Destroy()

        def apply_results_deferred():
            try:
                # Apply results back to KiCad AFTER progress dialog is destroyed and event loop finishes cleanup
                if success and not cancelled:
                    if isinstance(router, IpcRouter):
                        if isinstance(output_data, dict) and output_data.get("type") == "ses":
                            logger.info("Applying routing result to KiCad via SES import...")
                            try:
                                ok = router.apply_routing_ses(output_data["path"])
                                if not ok:
                                    raise RuntimeError("ImportSpecctraSES failed to import SES result")
                                logger.info("Routing result applied successfully via SES import.")
                            except Exception as e:
                                logger.error(f"Could not apply SES result: {e}", exc_info=True)
                                wx_show_error(f"Routing completed, but failed to apply results via SES:\n{e}")
                        elif isinstance(output_data, dict) and output_data.get("type") == "json":
                            logger.info("Applying routing result to KiCad via JSON...")
                            try:
                                ok = router.apply_routing_result(output_data["data"])
                                if ok is False:
                                    raise RuntimeError("Failed to apply JSON routing result to board")
                                logger.info("Routing result applied successfully via IPC.")
                            except Exception as e:
                                logger.error(f"Could not apply JSON result: {e}", exc_info=True)
                                wx_show_error(f"Routing completed, but failed to apply results via IPC:\n{e}")
                        else:
                            logger.info("Applying routing result to KiCad via Protocol Buffers IPC commit...")
                            try:
                                ok = router.apply_routing_result(output_data)
                                if ok is False:
                                    raise RuntimeError("Failed to apply IPC routing result to board")
                                logger.info("Routing result applied successfully via IPC.")
                            except Exception as e:
                                logger.error(f"Could not apply IPC result: {e}", exc_info=True)
                                wx_show_error(f"Routing completed, but failed to apply results via IPC:\n{e}")
                    elif isinstance(router, JsonApiRouter):
                        logger.info("Applying routing result to KiCad (JSON/API mode)...")
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
                        if self.module_output.is_file():
                            logger.info("Importing Specctra SES file into KiCad (DSN mode)...")
                            if not router.import_ses():
                                logger.error("Failed to import Specctra SES file.")
                        else:
                            logger.warning("Specctra SES file does not exist.")
                else:
                    logger.info(f"Routing finished: success={success}, cancelled={cancelled}")
            finally:
                # Clean up temp directory if one was created
                self._cleanup()

        wx.CallAfter(apply_results_deferred)

    def _cleanup(self):
        """Remove the temporary routing directory if one was created."""
        if hasattr(self, "routing_dir") and self.routing_dir != self.dirpath:
            import shutil
            try:
                shutil.rmtree(str(self.routing_dir), ignore_errors=True)
            except Exception as e:
                print(f"Warning: could not remove temp dir: {e}")

    def _run_ipc_stages(self, router, dialog, pump_events):
        """Execute the Protobuf IPC routing workflow with status updates.

        Returns:
            ``(cancelled, success, output_json)``
        """
        logger.info("Executing Protobuf IPC stages...")
        client = FreeroutingApiClient()

        # --- Stage 3: Sending board to Freerouting ---
        dialog.set_sending_status(STATUS_IN_PROGRESS)
        pump_events()

        # Run extraction in worker thread while pumping events on main thread
        # to prevent deadlock with KiCad's main event loop handling IPC requests
        import time as _time
        extraction = {"data": None, "error": None}

        def extract_worker():
            try:
                extraction["data"] = router.extract_board(allow_swig=False)
            except Exception as e:
                extraction["error"] = e

        extract_thread = threading.Thread(target=extract_worker, daemon=True)
        extract_thread.start()

        while extract_thread.is_alive():
            pump_events()
            _time.sleep(0.05)

        pump_events()
        board_data = extraction["data"]
        if not board_data:
            logger.warning(f"IPC board extraction worker returned no data ({extraction.get('error')}); attempting main-thread fallback...")
            board_data = router.extract_board(allow_swig=True)

        if not board_data:
            logger.error(f"Failed to extract board via IPC or fallback: {extraction.get('error')}")
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to extract board data from KiCad via Protocol Buffers IPC.")
            return False, False, None

        board_json_str = json.dumps(board_data, indent=2)
        if SAVE_DEBUG_JSON:
            DEBUG_JSON_DIR.mkdir(parents=True, exist_ok=True)
            self._save_debug(board_json_str, DEBUG_JSON_DIR / DEBUG_INPUT_JSON_FILENAME)

        session_id = client.create_session(host_name="KiCad")
        pump_events()
        if not session_id:
            logger.error("Failed to create Freerouting session.")
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to create Freerouting session.")
            return False, False, None

        client.set_monitored_session(session_id)

        filename = self.board.GetFileName()
        job_name = Path(filename).stem if filename else "KiCad_IPC_Job"
        job_id = client.enqueue_job(session_id, job_name=job_name)
        pump_events()
        if not job_id:
            logger.error("Failed to enqueue routing job.")
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to enqueue routing job.")
            return False, False, None

        if not client.upload_json_input(job_id, board_json_str):
            logger.error("Failed to upload board JSON.")
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to upload board JSON.")
            return False, False, None
        pump_events()

        if not client.start_job(job_id):
            logger.error("Failed to start routing job.")
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to start routing job.")
            return False, False, None

        dialog.set_sending_status(STATUS_PASS)
        pump_events()

        # --- Stage 4: Auto-router is running ---
        dialog.set_message("Routing in background...")
        dialog.set_detail("")
        dialog.set_routing_status(STATUS_IN_PROGRESS)
        pump_events()

        result = {"success": False, "output_json": None, "cancelled": False}

        def on_log_line(line):
            clean_msg, full_msg = clean_log_line(line)
            if clean_msg:
                wx_safe_invoke(dialog.set_detail, clean_msg, full_msg)

        log_tailer = LogTailer(LOG_DIR / "freerouting.log", job_id=job_id, on_log_line=on_log_line)
        log_tailer.start()

        def poll():
            logger.info("Starting background poll thread for IPC routing...")
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
                log_tailer.stop()
                wx_safe_invoke(dialog.terminate)

        poll_thread = threading.Thread(target=poll, daemon=True)
        poll_thread.start()

        modal_result = dialog.ShowModal()
        log_tailer.stop()
        poll_thread.join(timeout=15)
        log_tailer.join(timeout=2)

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

        # --- Stage 5: Receiving results ---
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

    def _run_ipc_gui_stages(self, router, dialog, pump_events):
        """Execute the Protobuf IPC routing workflow with interactive Freerouting GUI.

        1. Extracts board via IPC/in-process fast-path.
        2. Saves freerouting_input_board.json.
        3. Launches Freerouting GUI with input JSON and output SES/JSON.
        4. Waits for user to complete routing and close the GUI.
        5. Imports and applies the result.

        Returns:
            ``(cancelled, success, output_info)``
        """
        logger.info("Executing Protobuf IPC stages in GUI mode...")

        # --- Stage 3: Extracting board from KiCad ---
        dialog.set_sending_status(STATUS_IN_PROGRESS)
        pump_events()

        import time as _time
        extraction = {"data": None, "error": None}

        def extract_worker():
            try:
                extraction["data"] = router.extract_board(allow_swig=False)
            except Exception as e:
                extraction["error"] = e

        extract_thread = threading.Thread(target=extract_worker, daemon=True)
        extract_thread.start()

        while extract_thread.is_alive():
            pump_events()
            _time.sleep(0.05)

        pump_events()
        board_data = extraction["data"]
        if not board_data:
            logger.warning(f"IPC board extraction worker returned no data ({extraction.get('error')}); attempting main-thread fallback...")
            board_data = router.extract_board(allow_swig=True)

        if not board_data:
            logger.error(f"Failed to extract board via IPC or fallback: {extraction.get('error')}")
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error("Failed to extract board data from KiCad via Protocol Buffers IPC.")
            return False, False, None

        input_json_str = json.dumps(board_data, indent=2)
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        input_json_path = LOG_DIR / "freerouting_input_board.json"
        try:
            with open(input_json_path, "w", encoding="utf-8") as f:
                f.write(input_json_str)
            logger.info(f"Saved input board JSON to: {input_json_path}")
        except Exception as e:
            logger.error(f"Failed to write input JSON: {e}", exc_info=True)
            dialog.set_sending_status(STATUS_FAIL)
            return False, False, None

        if SAVE_DEBUG_JSON:
            DEBUG_JSON_DIR.mkdir(parents=True, exist_ok=True)
            self._save_debug(input_json_str, DEBUG_JSON_DIR / DEBUG_INPUT_JSON_FILENAME)

        dialog.set_sending_status(STATUS_PASS)
        pump_events()

        # Target output files in LOG_DIR (clean up any previous runs)
        output_ses_path = LOG_DIR / "freerouting_output_board.ses"
        output_json_path = LOG_DIR / "freerouting_output_board.json"
        for p in (output_ses_path, output_json_path):
            p.unlink(missing_ok=True)

        # --- Stage 4: Auto-router is running in GUI window ---
        dialog.set_routing_status(STATUS_IN_PROGRESS)
        dialog.set_message(
            "Freerouting is running in GUI mode.\n\n"
            "Please complete routing in the Freerouting window,\n"
            "save the result, and close Freerouting."
        )
        pump_events()

        router.build_gui_command(input_json_path, output_ses_path)

        def on_complete():
            logger.info("IPC GUI routing process complete callback fired.")
            wx_safe_invoke(dialog.terminate)

        from .process_utils import ProcessThread
        invoker = ProcessThread(
            self.module_command,
            on_complete=on_complete,
            output_handler=None,
        )
        logger.info("Starting IPC GUI routing process thread...")
        invoker.start()
        modal_result = dialog.ShowModal()

        try:
            if modal_result == dialog.result_button:
                logger.warning("Routing cancelled by user.")
                invoker.terminate()
                invoker.join(3)
                dialog.set_routing_status(STATUS_FAIL)
                return True, False, None
            elif modal_result == dialog.result_terminate:
                invoker.join(10)
                if invoker.has_ok():
                    logger.info("Routing process exited with success.")
                else:
                    logger.warning("Routing process exited with non-zero status.")
        finally:
            if invoker.is_alive():
                invoker.terminate()
                invoker.join(3)

        if not invoker.has_ok():
            logger.error("Freerouting process failed or exited with non-zero status.")
            dialog.set_routing_status(STATUS_FAIL)
            wx_show_error(textwrap.dedent("""
                Freerouting closed unexpectedly or failed during execution.
                Check freerouting.log for details.
            """))
            return False, False, None

        dialog.set_routing_status(STATUS_PASS)
        pump_events()

        # --- Stage 5: Receiving the results ---
        dialog.set_receiving_status(STATUS_IN_PROGRESS)
        pump_events()

        if output_json_path.is_file() and output_json_path.stat().st_size > 0:
            logger.info(f"Found output JSON: {output_json_path}")
            try:
                with open(output_json_path, "r", encoding="utf-8") as f:
                    out_data = json.load(f)
                if SAVE_DEBUG_JSON:
                    self._save_debug(json.dumps(out_data, indent=2), DEBUG_JSON_DIR / DEBUG_OUTPUT_JSON_FILENAME)
                dialog.set_receiving_status(STATUS_PASS)
                pump_events()
                return False, True, {"type": "json", "data": out_data}
            except Exception as e:
                logger.error(f"Failed to read output JSON: {e}", exc_info=True)

        if output_ses_path.is_file() and output_ses_path.stat().st_size > 0:
            logger.info(f"Found output SES: {output_ses_path} ({output_ses_path.stat().st_size} bytes)")
            dialog.set_receiving_status(STATUS_PASS)
            pump_events()
            return False, True, {"type": "ses", "path": output_ses_path}

        logger.warning("Neither non-empty output JSON nor SES file was generated.")
        dialog.set_receiving_status(STATUS_FAIL)
        pump_events()
        return False, False, None

    def _run_json_api_stages(self, router, dialog, pump_events):
        """Execute the JSON/API routing workflow with status updates.

        Returns:
            ``(cancelled, success, output_json)``
        """
        logger.info("Executing JSON/API stages...")
        client = FreeroutingApiClient()

        # --- Stage 3: Sending board to Freerouting ---
        dialog.set_sending_status(STATUS_IN_PROGRESS)
        pump_events()

        # Serialize board to JSON
        logger.info("Serializing board to JSON for JSON/API mode...")
        try:
            board_json = serialize_board_to_json()
            logger.info("Board serialized successfully.")
        except Exception as e:
            logger.error(f"Failed to serialize board to JSON: {e}", exc_info=True)
            dialog.set_sending_status(STATUS_FAIL)
            wx_show_error(textwrap.dedent(f"""
                Failed to serialize board to JSON:
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
        dialog.set_message("Routing in background...")
        dialog.set_detail("")
        dialog.set_routing_status(STATUS_IN_PROGRESS)
        pump_events()

        result = {"success": False, "output_json": None, "cancelled": False}

        def on_log_line(line):
            clean_msg, full_msg = clean_log_line(line)
            if clean_msg:
                wx_safe_invoke(dialog.set_detail, clean_msg, full_msg)

        log_tailer = LogTailer(LOG_DIR / "freerouting.log", job_id=job_id, on_log_line=on_log_line)
        log_tailer.start()

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
                log_tailer.stop()
                wx_safe_invoke(dialog.terminate)

        poll_thread = threading.Thread(target=poll, daemon=True)
        poll_thread.start()

        # Modal loop for the routing stage
        modal_result = dialog.ShowModal()
        log_tailer.stop()
        poll_thread.join(timeout=15)
        log_tailer.join(timeout=2)

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
        gui_enabled = getattr(self, "gui_enabled", True)
        if gui_enabled:
            dialog.set_message("Freerouting GUI is running...\nComplete routing and close the window,\nor press Terminate to cancel.")
            dialog.set_detail("")
        else:
            dialog.set_message("Routing in background...")
            dialog.set_detail("")
        pump_events()

        # Create a ProcessThread to run Freerouting
        from .process_utils import ProcessThread, clean_log_line

        def on_complete():
            logger.info("ProcessThread completed. Terminating dialog...")
            wx_safe_invoke(dialog.terminate)

        def output_handler(line):
            if not gui_enabled:
                clean_msg, full_msg = clean_log_line(line)
                if clean_msg:
                    wx_safe_invoke(dialog.set_detail, clean_msg, full_msg)

        invoker = ProcessThread(
            self.module_command,
            on_complete=on_complete,
            output_handler=output_handler if not gui_enabled else None,
        )

        invoker.start()

        modal_result = dialog.ShowModal()

        if modal_result == dialog.result_button:
            logger.warning("Routing cancelled by user.")
            invoker.terminate()
            invoker.join(timeout=3)
            dialog.set_routing_status(STATUS_FAIL)
            return True, False

        invoker.join(timeout=10)

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
    def _apply_result_to_kicad(json_str, board=None):
        """Apply a KiCad JSON routing result back to the board."""
        import json
        if isinstance(json_str, dict):
            board_data = json_str
            json_str = json.dumps(board_data)
        else:
            board_data = json.loads(json_str)
        if board is None:
            board = pcbnew.GetBoard()
        if board is None:
            raise RuntimeError("No board loaded.")

        # Try native JSON import helpers on pcbnew
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

        has_commit = hasattr(pcbnew, "BOARD_COMMIT")
        commit = None
        if has_commit:
            try:
                commit = pcbnew.BOARD_COMMIT(board)
            except Exception:
                try:
                    commit = pcbnew.BOARD_COMMIT()
                except Exception:
                    commit = None

        try:
            # 1. Remove existing unlocked tracks and vias
            try:
                to_remove = [t for t in board.GetTracks() if not (hasattr(t, "IsLocked") and t.IsLocked())]
                for t in to_remove:
                    board.Remove(t)
                    if commit:
                        try:
                            commit.Remove(t)
                        except Exception:
                            # Item removal registration in commit failed or unsupported
                            pass
                if to_remove:
                    logger.info(f"Removed {len(to_remove)} unlocked existing tracks/vias.")
            except Exception as e:
                logger.debug(f"Could not remove existing tracks: {e}")

            # 2. Coordinate scaling (KiCad internal unit is nanometers: 1 mm = 1e6 nm)
            unit = board_data.get("unit", "MM").upper()
            if unit == "MM":
                scale = 1e6
            elif unit == "MIL":
                scale = 25400.0
            elif unit == "UM":
                scale = 1000.0
            else:
                scale = 1e6
            logger.info(f"Dynamic coordinate scaling set to: {scale} (unit: {unit})")

            # 3. Layer mapping (JSON layer index -> KiCad layer ID)
            layer_map = {}
            for l_json in board_data.get("layers", []):
                l_idx = l_json.get("index")
                l_name = l_json.get("name")
                if l_idx is not None and l_name:
                    try:
                        kicad_lid = board.GetLayerID(l_name)
                        if kicad_lid != getattr(pcbnew, "UNDEFINED_LAYER", -1):
                            layer_map[l_idx] = kicad_lid
                    except Exception:
                        # Layer name lookup failed; skip layer ID mapping
                        pass

            top_layer = layer_map.get(0, 0)
            bot_layer = layer_map.get(
                len(board_data.get("layers", [])) - 1,
                getattr(board, "GetCopperLayerCount", lambda: 2)() - 1,
            )

            # 4. Net lookup helper
            def lookup_net(board, net_name):
                if not net_name:
                    return None
                try:
                    if hasattr(board, "FindNet"):
                        net = board.FindNet(str(net_name))
                        if net:
                            return net
                except Exception:
                    # Net lookup failed; return None
                    pass
                return None

            for trace in board_data.get("traces", []):
                try:
                    net_name = trace.get("netName", "")
                    net = lookup_net(board, net_name)
                    if not net:
                        logger.warning(
                            f"Net '{net_name}' could not be resolved; skipping trace to prevent unassigned copper."
                        )
                        continue
                    width = int(round(trace.get("width", 0.25) * scale))
                    layer_idx = trace.get("layerIndex", 0)
                    layer = layer_map.get(layer_idx, layer_idx)
                    points = trace.get("points", [])
                    for i in range(len(points) - 1):
                        t = pcbnew.PCB_TRACK(board)
                        t.SetStart(pcbnew.VECTOR2I(int(round(points[i]["x"] * scale)), int(round(points[i]["y"] * scale))))
                        t.SetEnd(pcbnew.VECTOR2I(int(round(points[i + 1]["x"] * scale)), int(round(points[i + 1]["y"] * scale))))
                        t.SetWidth(width)
                        t.SetLayer(layer)
                        t.SetNet(net)
                        board.Add(t)
                        if commit:
                            try:
                                commit.Add(t)
                            except Exception:
                                # Adding track to commit failed or unsupported
                                pass
                except Exception as e:
                    logger.error(f"Warning: could not apply trace: {e}", exc_info=True)

            for via in board_data.get("vias", []):
                try:
                    net_name = via.get("netName", "")
                    net = lookup_net(board, net_name)
                    if not net:
                        logger.warning(
                            f"Net '{net_name}' could not be resolved; skipping via to prevent unassigned copper."
                        )
                        continue
                    pos = via.get("position", {})
                    v = pcbnew.PCB_VIA(board)
                    v.SetPosition(pcbnew.VECTOR2I(int(round(pos.get("x", 0) * scale)), int(round(pos.get("y", 0) * scale))))
                    v.SetWidth(int(round(via.get("diameter", 0.8) * scale)))
                    v.SetDrill(int(round(via.get("drill", 0.4) * scale)))
                    start_l = layer_map.get(via.get("startLayerIndex", 0), top_layer)
                    end_l = layer_map.get(
                        via.get("endLayerIndex", len(board_data.get("layers", [])) - 1),
                        bot_layer,
                    )
                    if hasattr(v, "SetLayerPair"):
                        v.SetLayerPair(start_l, end_l)
                    v.SetNet(net)
                    board.Add(v)
                    if commit:
                        try:
                            commit.Add(v)
                        except Exception:
                            # Adding via to commit failed or unsupported
                            pass
                except Exception as e:
                    logger.error(f"Warning: could not apply via: {e}", exc_info=True)

            if commit:
                try:
                    commit.Push()
                    logger.info("BOARD_COMMIT pushed successfully in _apply_result_to_kicad.")
                except Exception as e:
                    try:
                        commit.Push(board)
                    except Exception as e2:
                        logger.error(f"commit.Push failed: {e2}", exc_info=True)
        except Exception:
            if commit and hasattr(commit, "Revert"):
                try:
                    commit.Revert()
                except Exception:
                    # Commit rollback failed or already finalized
                    pass
            raise

        try:
            pcbnew.Refresh()
        except Exception:
            # Refresh may fail in headless mode or if UI window is not yet attached
            pass


# Register the plugin with KiCad's plugin manager.
try:
    FreeroutingPlugin().register()
except Exception as e:
    # Occurs when imported in headless Python CLI outside KiCad GUI process
    logger.debug(f"Action plugin registration skipped: {e}")

