"""
standalone_runner.py — CLI test utility for KiCad IPC Board Reader & Writer
----------------------------------------------------------------------------
Connects to a running KiCad IPC server (GUI or headless kicad-cli api-server)
and either exports the board as Freerouting-compatible KiCadBoardJson, or
applies a routed KiCadBoardJson payload back into KiCad using atomic commits.

Usage:
    # Read board from KiCad:
    python standalone_runner.py [--socket <socket_path>] [--output <out.json>]

    # Apply routed traces and vias back to KiCad:
    python standalone_runner.py [--socket <socket_path>] --apply <routed.json>
"""

import argparse
import json
import logging
import sys
from pathlib import Path

# Add current folder to sys.path
sys.path.insert(0, str(Path(__file__).parent))

from ipc_board_reader import KiCadIpcBoardReader
from ipc_board_writer import KiCadIpcBoardWriter

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("freerouting.ipc_runner")


def main():
    parser = argparse.ArgumentParser(
        description="Interact with KiCad IPC API: extract PCB or apply routed traces."
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
        help="Path for exported JSON file (when extracting). Default: freerouting_ipc_input.json",
    )
    parser.add_argument(
        "--apply",
        "-a",
        type=str,
        default=None,
        help="Path to routed KiCadBoardJson file to apply back to the board.",
    )
    parser.add_argument(
        "--keep-unfixed",
        action="store_true",
        help="Do not delete existing unlocked tracks/vias when applying routed data.",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=5000,
        help="Socket timeout in milliseconds. Default: 5000",
    )

    args = parser.parse_args()

    # --- Mode 1: Apply routed JSON back to KiCad ---
    if args.apply:
        apply_path = Path(args.apply).resolve()
        if not apply_path.is_file():
            logger.error(f"Input file not found: {apply_path}")
            sys.exit(1)

        logger.info(f"Loading routed data from: {apply_path}")
        with open(apply_path, "r", encoding="utf-8") as f:
            board_data = json.load(f)

        logger.info(f"Connecting to KiCad IPC (socket={args.socket})...")
        try:
            writer = KiCadIpcBoardWriter(socket_path=args.socket, timeout_ms=args.timeout)
            logger.info(f"Connected to KiCad — Target board: '{writer.board.name}'")
        except Exception as e:
            logger.error(f"Failed to connect to KiCad IPC server: {e}")
            sys.exit(1)

        result = writer.write_routed_board(
            board_data,
            replace_unfixed=not args.keep_unfixed,
            commit_message="Freerouting Autoroute (IPC)",
        )
        logger.info(
            f"Write-back complete: created {result['created_tracks']} tracks, "
            f"{result['created_vias']} vias (removed {result['removed_tracks']} old tracks, "
            f"{result['removed_vias']} old vias)."
        )
        return

    # --- Mode 2: Extract board from KiCad ---
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
