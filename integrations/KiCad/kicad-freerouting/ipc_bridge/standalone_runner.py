"""
standalone_runner.py — CLI test utility for KiCad IPC Board Reader
------------------------------------------------------------------
Connects to a running KiCad IPC server (GUI or headless kicad-cli api-server)
and exports the board as Freerouting-compatible KiCadBoardJson.

Usage:
    python standalone_runner.py [--socket <socket_path>] [--output <out.json>]
"""

import argparse
import json
import logging
import sys
from pathlib import Path

# Add current folder to sys.path so ipc_board_reader can be imported
sys.path.insert(0, str(Path(__file__).parent))

from ipc_board_reader import KiCadIpcBoardReader

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("freerouting.ipc_runner")


def main():
    parser = argparse.ArgumentParser(
        description="Extract PCB from KiCad IPC API to Freerouting JSON format."
    )
    parser.add_argument(
        "--socket",
        type=str,
        default=None,
        help="NNG IPC socket path (e.g. ipc://<path>). Default: reads KICAD_API_SOCKET or system default.",
    )
    parser.add_argument(
        "--output",
        "-o",
        type=str,
        default="freerouting_ipc_input.json",
        help="Path for exported JSON file. Default: freerouting_ipc_input.json",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=5000,
        help="Socket timeout in milliseconds. Default: 5000",
    )

    args = parser.parse_args()

    logger.info(f"Connecting to KiCad IPC (socket={args.socket})...")
    try:
        reader = KiCadIpcBoardReader(socket_path=args.socket, timeout_ms=args.timeout)
        logger.info(
            f"Connected to KiCad {reader.kicad_version} (API {reader.api_version}) — Board: '{reader.board.name}'"
        )
    except Exception as e:
        logger.error(f"Failed to connect to KiCad IPC server: {e}")
        logger.error(
            "Ensure KiCad is running with 'Preferences > Plugins > Enable KiCad API' checked, "
            "or 'kicad-cli api-server' is running."
        )
        sys.exit(1)

    logger.info("Extracting board geometry and design rules...")
    board_data = reader.read_board_data()

    logger.info(
        f"Extraction complete: {len(board_data['layers'])} layers, "
        f"{len(board_data['components'])} components, "
        f"{len(board_data['nets'])} nets, "
        f"outline clearance: {board_data['outline'].get('clearance')} mm."
    )

    out_path = Path(args.output).resolve()
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(board_data, f, indent=2)

    logger.info(f"Saved KiCadBoardJson payload to: {out_path}")


if __name__ == "__main__":
    main()
