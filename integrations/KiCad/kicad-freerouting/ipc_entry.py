"""
ipc_entry.py — Out-of-Process Entrypoint for KiCad 10+ Modern Plugins
---------------------------------------------------------------------
Invoked by KiCad 10+ when the user triggers the action defined in `plugin.json`.
Runs in the isolated Python virtual environment managed by KiCad with
dependencies installed from `requirements.txt` (including `kicad-python` and `pynng`).

Workflow:
1. Immediately hides any raw console window spawned on Windows.
2. Launches an interactive GUI progress dialog (`IpcRoutingDialog`).
3. Connects to KiCad via Protocol Buffers IPC (`kipy.KiCad`).
4. Extracts board geometry and design rules via `KiCadIpcBoardReader`.
5. Starts or discovers the Freerouting background API server (Java 25+ JRE).
6. Uploads board JSON to Freerouting REST API and tracks routing progress.
7. Commits routed tracks and vias back into KiCad via atomic transaction.
"""

from __future__ import annotations

import argparse
import json
import logging
import subprocess
import sys
import threading
from pathlib import Path

# 0. Hide Windows console window immediately if allocated
if sys.platform == "win32":
    try:
        import ctypes
        hwnd = ctypes.windll.kernel32.GetConsoleWindow()
        if hwnd:
            ctypes.windll.user32.ShowWindow(hwnd, 0)  # SW_HIDE
    except Exception:
        pass

# Add plugins and ipc_bridge to sys.path
here = Path(__file__).resolve().parent
plugins_dir = here / "plugins" if (here / "plugins").is_dir() else here
ipc_bridge_dir = plugins_dir / "ipc_bridge"
for p in (here, plugins_dir, ipc_bridge_dir):
    if p.is_dir() and str(p) not in sys.path:
        sys.path.insert(0, str(p))

from config import (
    API_JOB_TIMEOUT,
    API_POLL_INTERVAL,
    DEBUG_INPUT_JSON_FILENAME,
    DEBUG_JSON_DIR,
    DEBUG_OUTPUT_JSON_FILENAME,
    LOG_DIR,
    SAVE_DEBUG_JSON,
)
from api_client import FreeroutingApiClient
from java_utils import detect_os_architecture, get_local_java_executable_path
from ipc_board_reader import KiCadIpcBoardReader
from ipc_board_writer import KiCadIpcBoardWriter
from ipc_dialog import (
    IpcRoutingDialog,
    LogTailer,
    clean_log_line,
    STAGE_JAVA,
    STAGE_EXTRACT,
    STAGE_SERVER,
    STAGE_ROUTE,
    STAGE_COMMIT,
    STATE_ACTIVE,
    STATE_PASS,
    STATE_FAIL,
)

LOG_DIR.mkdir(parents=True, exist_ok=True)
log_file = LOG_DIR / "freerouting_ipc_plugin.log"

logging_handlers = [
    logging.FileHandler(log_file, mode="a", encoding="utf-8"),
]
if sys.stdout and getattr(sys.stdout, "isatty", lambda: False)():
    logging_handlers.append(logging.StreamHandler(sys.stdout))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=logging_handlers,
)
logger = logging.getLogger("freerouting.ipc_entry")


def handle_uncaught_exception(exc_type, exc_value, exc_traceback):
    """Ensure any unhandled exception is written to the log file."""
    if issubclass(exc_type, KeyboardInterrupt):
        sys.__excepthook__(exc_type, exc_value, exc_traceback)
        return
    logger.critical("Uncaught exception in plugin runner:", exc_info=(exc_type, exc_value, exc_traceback))


sys.excepthook = handle_uncaught_exception


def run_pipeline(dialog: IpcRoutingDialog, args: argparse.Namespace) -> None:
    """Executes the full routing pipeline, posting updates to the progress dialog."""
    logger.info("=== Starting Freerouting IPC Plugin Action ===")
    logger.info(f"Python: {sys.executable} ({sys.version.split()[0]})")
    logger.info(f"Arguments: {args}")

    reader = None
    client = FreeroutingApiClient()

    try:
        # --------------------------------------------------------------
        # Stage 0: Check Java 25+ JRE (skip if API server is already running)
        # --------------------------------------------------------------
        java_path = None
        if client.health_check():
            logger.info("Existing Freerouting API server detected; skipping Java check.")
            dialog.set_stage(STAGE_JAVA, STATE_PASS)
        else:
            dialog.set_stage(STAGE_JAVA, STATE_ACTIVE)
            dialog.set_status("Detecting Java 25+ JRE...")

            os_name, _ = detect_os_architecture()
            java_candidate = get_local_java_executable_path(os_name)
            if not java_candidate:
                err = "Java 25+ JRE not found. Please install Java 25 or higher, or start Freerouting API server."
                logger.error(err)
                dialog.set_stage(STAGE_JAVA, STATE_FAIL)
                dialog.complete_error(err)
                return

            java_path = Path(java_candidate)
            # Prefer javaw.exe on Windows to prevent any console window popup
            if sys.platform == "win32":
                javaw_candidate = java_path.with_name("javaw.exe")
                if javaw_candidate.is_file():
                    java_path = javaw_candidate

            dialog.set_stage(STAGE_JAVA, STATE_PASS)

        if dialog.cancel_event.is_set():
            dialog.complete_error("Routing cancelled by user.")
            return

        # --------------------------------------------------------------
        # Stage 1: Connect to KiCad IPC & Extract Board Geometry
        # --------------------------------------------------------------
        dialog.set_stage(STAGE_EXTRACT, STATE_ACTIVE)
        dialog.set_status("Connecting to KiCad IPC...")

        try:
            reader = KiCadIpcBoardReader(socket_path=args.socket)
            logger.info(f"Connected to KiCad {reader.kicad_version} — Board: '{reader.board.name}'")
        except Exception as e:
            err = f"Failed to connect to KiCad IPC: {e}\nPlease verify that 'Preferences > Plugins > Enable KiCad API' is enabled."
            logger.error(err, exc_info=True)
            dialog.set_stage(STAGE_EXTRACT, STATE_FAIL)
            dialog.complete_error(err)
            return

        board_title = reader.board.name if reader.board.name else "PCB"
        dialog.set_status(f"Extracting PCB geometry and design rules...", detail=f"Board: '{board_title}'")
        board_data = reader.read_board_data()
        board_json_str = json.dumps(board_data, indent=2)

        if SAVE_DEBUG_JSON:
            DEBUG_JSON_DIR.mkdir(parents=True, exist_ok=True)
            try:
                with open(DEBUG_JSON_DIR / DEBUG_INPUT_JSON_FILENAME, "w", encoding="utf-8") as f:
                    f.write(board_json_str)
            except Exception as e:
                logger.warning(f"Could not save debug input JSON: {e}")

        dialog.set_stage(STAGE_EXTRACT, STATE_PASS)

        if dialog.cancel_event.is_set():
            dialog.complete_error("Routing cancelled by user.")
            return

        # --------------------------------------------------------------
        # Stage 2: Start / Discover Freerouting API Server
        # --------------------------------------------------------------
        dialog.set_stage(STAGE_SERVER, STATE_ACTIVE)
        dialog.set_status("Connecting to Freerouting API server...")

        if not client.health_check():
            logger.info("Freerouting API server is not running; launching in background...")
            dialog.set_status("Starting Freerouting background service...")

            if not java_path:
                os_name, _ = detect_os_architecture()
                java_candidate = get_local_java_executable_path(os_name)
                if not java_candidate:
                    err = "Java 25+ JRE not found. Please install Java 25 or higher to launch the API server."
                    logger.error(err)
                    dialog.set_stage(STAGE_SERVER, STATE_FAIL)
                    dialog.complete_error(err)
                    return
                java_path = Path(java_candidate)
                if sys.platform == "win32":
                    javaw_candidate = java_path.with_name("javaw.exe")
                    if javaw_candidate.is_file():
                        java_path = javaw_candidate

            jar_path = plugins_dir / "jar" / "freerouting.jar"
            if not jar_path.is_file():
                candidates = [
                    plugins_dir / "jar" / "freerouting-2.5.0-RC12.jar",
                    here.parent.parent / "build" / "libs" / "freerouting-current-executable.jar",
                ]
                for c in candidates:
                    if c.is_file():
                        jar_path = c
                        break

            if not jar_path.is_file():
                err = f"Freerouting executable JAR not found. Checked: {jar_path}"
                logger.error(err)
                dialog.set_stage(STAGE_SERVER, STATE_FAIL)
                dialog.complete_error(err)
                return

            cmd = [
                str(java_path),
                "-jar",
                str(jar_path),
                "--api_server.enabled=true",
                "--api_server.endpoints=http://127.0.0.1:37864",
                "--api_server.authentication.enabled=false",
                "--gui.enabled=false",
                f"--logging.file.location={LOG_DIR}",
            ]
            creationflags = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
            logger.info(f"Launching API server: {' '.join(cmd)}")
            proc = subprocess.Popen(
                cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                creationflags=creationflags,
            )

            import time
            ready = False
            for _ in range(35):
                if dialog.cancel_event.is_set():
                    proc.terminate()
                    dialog.complete_error("Cancelled while starting API server.")
                    return
                time.sleep(1)
                if client.health_check():
                    ready = True
                    break

            if not ready:
                proc.terminate()
                err = "Freerouting API server failed to respond within 35 seconds."
                logger.error(err)
                dialog.set_stage(STAGE_SERVER, STATE_FAIL)
                dialog.complete_error(err)
                return

        dialog.set_stage(STAGE_SERVER, STATE_PASS)

        if dialog.cancel_event.is_set():
            dialog.complete_error("Routing cancelled by user.")
            return

        # --------------------------------------------------------------
        # Stage 3: Auto-route PCB Connections
        # --------------------------------------------------------------
        dialog.set_stage(STAGE_ROUTE, STATE_ACTIVE)
        dialog.set_status("Initializing routing job...")

        session_id = client.create_session(host_name="KiCad")
        if not session_id:
            err = "Failed to create Freerouting session."
            logger.error(err)
            dialog.set_stage(STAGE_ROUTE, STATE_FAIL)
            dialog.complete_error(err)
            return

        client.set_monitored_session(session_id)
        job_name = Path(reader.board.name).stem if (reader and reader.board.name) else "KiCad_IPC_Job"
        job_id = client.enqueue_job(session_id, job_name=job_name)
        if not job_id:
            err = "Failed to enqueue routing job."
            logger.error(err)
            dialog.set_stage(STAGE_ROUTE, STATE_FAIL)
            dialog.complete_error(err)
            return

        if not client.upload_json_input(job_id, board_json_str):
            err = "Failed to upload board JSON payload."
            logger.error(err)
            dialog.set_stage(STAGE_ROUTE, STATE_FAIL)
            dialog.complete_error(err)
            return

        if not client.start_job(job_id):
            err = "Failed to start routing job."
            logger.error(err)
            dialog.set_stage(STAGE_ROUTE, STATE_FAIL)
            dialog.complete_error(err)
            return

        logger.info(f"Routing job '{job_id}' started. Polling for results...")

        def _flush_logs() -> None:
            for h in logger.handlers:
                try:
                    if isinstance(h, logging.FileHandler):
                        h.flush()
                except Exception:
                    pass

        def _on_route_progress(state: str, elapsed: float, info: dict) -> None:
            if state in ("COMPLETED", "FINISHED", "DONE"):
                dialog.set_status(
                    f"Routing completed in {elapsed:.0f}s! Downloading results...",
                    detail=f"Job: {job_id[:8]}... | State: COMPLETED",
                )
            else:
                dialog.set_status(
                    f"Routing in progress... ({elapsed:.0f}s elapsed)",
                    detail=f"Job: {job_id[:8]}... | State: {state}",
                )

        completion_event = threading.Event()
        terminal_state_holder = ["RUNNING"]

        log_path = LOG_DIR / "freerouting.log"
        tailer = None
        try:
            def _on_tailer_line(raw_line: str) -> None:
                clean_msg, _ = clean_log_line(raw_line)
                if clean_msg:
                    dialog.set_log_line(clean_msg)
                if "finished with state:" in raw_line:
                    if "COMPLETED" in raw_line:
                        terminal_state_holder[0] = "COMPLETED"
                        dialog.set_status(
                            "Routing completed! Downloading results...",
                            detail=f"Job: {job_id[:8]}... | State: COMPLETED",
                        )
                        completion_event.set()
                    elif any(s in raw_line for s in ("FAILED", "CANCELLED", "TIMED_OUT", "ERROR")):
                        terminal_state_holder[0] = "FAILED"
                        dialog.set_status(
                            "Routing ended.",
                            detail=f"Job: {job_id[:8]}... | State: FAILED",
                        )
                        completion_event.set()

            tailer = LogTailer(log_path, job_id=job_id, on_log_line=_on_tailer_line)
            tailer.start()

            ok, output_json = client.wait_for_job_completion(
                job_id,
                poll_interval=API_POLL_INTERVAL,
                timeout=args.job_timeout,
                progress_callback=_on_route_progress,
                cancel_event=dialog.cancel_event,
                completion_event=completion_event,
                terminal_state_holder=terminal_state_holder,
            )
        finally:
            if tailer is not None:
                tailer.stop()
            _flush_logs()

        if dialog.cancel_event.is_set():
            dialog.set_stage(STAGE_ROUTE, STATE_FAIL)
            dialog.complete_error("Routing cancelled by user.")
            return

        if not ok or not output_json:
            err = "Routing job failed or timed out."
            logger.error(err)
            dialog.set_stage(STAGE_ROUTE, STATE_FAIL)
            dialog.complete_error(err)
            return

        dialog.set_stage(STAGE_ROUTE, STATE_PASS)
        dialog.set_stage(STAGE_COMMIT, STATE_ACTIVE)
        dialog.set_status("Applying routed tracks and vias to KiCad...")
        _flush_logs()

        if SAVE_DEBUG_JSON:
            try:
                with open(DEBUG_JSON_DIR / DEBUG_OUTPUT_JSON_FILENAME, "w", encoding="utf-8") as f:
                    f.write(output_json)
            except Exception as e:
                logger.warning(f"Could not save debug output JSON: {e}")

        if dialog.cancel_event.is_set():
            dialog.complete_error("Routing cancelled by user.")
            return

        # --------------------------------------------------------------
        # Stage 4: Apply Routed Tracks & Vias Back into KiCad
        # --------------------------------------------------------------
        logger.info("Writing routed tracks and vias back into KiCad via IPC commit...")
        _flush_logs()
        # Reuse existing reader.board connection to avoid duplicate socket sessions
        writer = KiCadIpcBoardWriter(board=reader.board if reader else None, socket_path=args.socket)
        routed_data = json.loads(output_json)
        result = writer.write_routed_board(
            routed_data,
            replace_unfixed=True,
            commit_message="Freerouting Autoroute",
        )

        success_msg = (
            f"Committed {result['created_tracks']} tracks and "
            f"{result['created_vias']} vias to KiCad."
        )
        logger.info(f"Routing complete! {success_msg}")
        _flush_logs()
        dialog.set_stage(STAGE_COMMIT, STATE_PASS)
        dialog.complete_success(success_msg)

    except Exception as e:
        logger.error(f"Error during routing pipeline execution: {e}", exc_info=True)
        dialog.complete_error(f"Unexpected error: {e}")
    finally:
        for handler in logger.handlers:
            try:
                handler.flush()
            except Exception:
                pass


def main():
    parser = argparse.ArgumentParser(description="Freerouting IPC Runner")
    parser.add_argument("--socket", type=str, default=None, help="KiCad IPC socket path")
    parser.add_argument("--job-timeout", type=int, default=API_JOB_TIMEOUT, help="Max routing timeout in seconds")
    parser.add_argument("--headless", action="store_true", help="Run headlessly without GUI progress dialog")
    args = parser.parse_args()

    dialog = IpcRoutingDialog()
    if args.headless:
        # Run headlessly without creating GUI window
        run_pipeline(dialog, args)
    else:
        # Run with Tkinter modal progress dialog
        dialog.run(lambda d: run_pipeline(d, args))


if __name__ == "__main__":
    main()
