"""
ipc_entry.py — Out-of-Process Entrypoint for KiCad 10+ Modern Plugins
---------------------------------------------------------------------
Invoked by KiCad 10+ when the user triggers the action defined in `plugin.json`.
Runs in the isolated Python virtual environment managed by KiCad with
dependencies installed from `requirements.txt` (including `kicad-python` and `pynng`).

Workflow:
1. Connects to the active KiCad instance via Protocol Buffers IPC (`kipy.KiCad`).
2. Discovers or starts the local Freerouting application (Java 25+).
3. Executes board extraction via `KiCadIpcBoardReader`.
4. Uploads board JSON to Freerouting REST API and triggers routing job.
5. Polls or streams job completion.
6. Writes routed tracks and vias back into KiCad via atomic commit transaction.
"""

import argparse
import json
import logging
import os
import sys
from pathlib import Path

# Add plugins and ipc_bridge to sys.path
here = Path(__file__).resolve().parent
plugins_dir = here / "plugins"
ipc_bridge_dir = plugins_dir / "ipc_bridge"
for p in (plugins_dir, ipc_bridge_dir):
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

LOG_DIR.mkdir(parents=True, exist_ok=True)
log_file = LOG_DIR / "freerouting_ipc_plugin.log"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[
        logging.FileHandler(log_file, mode="a", encoding="utf-8"),
        logging.StreamHandler(sys.stdout),
    ],
)
logger = logging.getLogger("freerouting.ipc_entry")


def main():
    parser = argparse.ArgumentParser(description="Freerouting IPC Runner")
    parser.add_argument("--socket", type=str, default=None, help="KiCad IPC socket path")
    parser.add_argument("--job-timeout", type=int, default=API_JOB_TIMEOUT, help="Max routing timeout in seconds")
    args = parser.parse_args()

    logger.info("=== Starting Freerouting IPC Plugin Action ===")

    # 1. Connect to KiCad IPC
    try:
        reader = KiCadIpcBoardReader(socket_path=args.socket)
        logger.info(f"Connected to KiCad {reader.kicad_version} — Board: '{reader.board.name}'")
    except Exception as e:
        logger.error(f"Failed to connect to KiCad IPC: {e}")
        logger.error("Please verify that 'Preferences > Plugins > Enable KiCad API' is enabled in KiCad.")
        sys.exit(1)

    # 2. Extract Board JSON
    logger.info("Extracting board geometry and design rules via IPC...")
    board_data = reader.read_board_data()
    board_json_str = json.dumps(board_data, indent=2)

    if SAVE_DEBUG_JSON:
        DEBUG_JSON_DIR.mkdir(parents=True, exist_ok=True)
        try:
            with open(DEBUG_JSON_DIR / DEBUG_INPUT_JSON_FILENAME, "w", encoding="utf-8") as f:
                f.write(board_json_str)
        except Exception as e:
            logger.warning(f"Could not save debug input JSON: {e}")

    # 3. Check or Start Freerouting API Server
    client = FreeroutingApiClient()
    if not client.health_check():
        logger.info("Freerouting API server is not running, launching...")
        os_name, _ = detect_os_architecture()
        java_path = get_local_java_executable_path(os_name)
        if not java_path:
            logger.error("Java 25+ JRE not found.")
            sys.exit(1)

        jar_path = plugins_dir / "jar" / "freerouting-current-executable.jar"
        if not jar_path.is_file():
            # Check configured location in plugin.ini or root build
            candidates = [
                plugins_dir / "jar" / "freerouting-2.4.1.jar",
                here.parent.parent / "build" / "libs" / "freerouting-current-executable.jar",
            ]
            for c in candidates:
                if c.is_file():
                    jar_path = c
                    break

        if not jar_path.is_file():
            logger.error(f"Freerouting executable JAR not found. Checked: {jar_path}")
            sys.exit(1)

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
        import subprocess
        logger.info(f"Launching API server: {' '.join(cmd)}")
        proc = subprocess.Popen(cmd)

        import time
        ready = False
        for _ in range(30):
            time.sleep(1)
            if client.health_check():
                ready = True
                break
        if not ready:
            logger.error("Freerouting API server failed to become ready.")
            proc.terminate()
            sys.exit(1)

    # 4. Enqueue and Run Job
    logger.info("Connecting to Freerouting API server...")
    session_id = client.create_session(host_name="KiCad")
    if not session_id:
        logger.error("Failed to create Freerouting session.")
        sys.exit(1)

    client.set_monitored_session(session_id)
    job_name = Path(reader.board.name).stem if reader.board.name else "KiCad_IPC_Job"
    job_id = client.enqueue_job(session_id, job_name=job_name)
    if not job_id:
        logger.error("Failed to enqueue job.")
        sys.exit(1)

    if not client.upload_json_input(job_id, board_json_str):
        logger.error("Failed to upload board JSON.")
        sys.exit(1)

    if not client.start_job(job_id):
        logger.error("Failed to start job.")
        sys.exit(1)

    logger.info(f"Routing job '{job_id}' started. Polling for results...")
    ok, output_json = client.wait_for_job_completion(
        job_id,
        poll_interval=API_POLL_INTERVAL,
        timeout=args.job_timeout,
    )

    if not ok or not output_json:
        logger.error("Routing job failed or timed out.")
        sys.exit(1)

    if SAVE_DEBUG_JSON:
        try:
            with open(DEBUG_JSON_DIR / DEBUG_OUTPUT_JSON_FILENAME, "w", encoding="utf-8") as f:
                f.write(output_json)
        except Exception as e:
            logger.warning(f"Could not save debug output JSON: {e}")

    # 5. Write Routed Tracks & Vias Back into KiCad
    logger.info("Writing routed tracks and vias back into KiCad via IPC commit...")
    writer = KiCadIpcBoardWriter(socket_path=args.socket)
    routed_data = json.loads(output_json)
    result = writer.write_routed_board(
        routed_data,
        replace_unfixed=True,
        commit_message="Freerouting Autoroute",
    )
    logger.info(
        f"Routing complete! Committed {result['created_tracks']} tracks and "
        f"{result['created_vias']} vias to KiCad in a single atomic transaction."
    )


if __name__ == "__main__":
    main()
