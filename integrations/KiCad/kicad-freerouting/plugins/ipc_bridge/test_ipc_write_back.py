"""
test_ipc_write_back.py — Automated verification of KiCad IPC Write-Back
-----------------------------------------------------------------------
Launches KiCad 10.99 Nightly headless api-server with a temporary copy of
dev-board.kicad_pcb, writes routed tracks and vias using KiCadIpcBoardWriter,
and asserts that KiCad reflects the new geometry and nets.
"""

import json
import logging
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

# Setup paths
bridge_dir = Path(__file__).parent.resolve()
sys.path.insert(0, str(bridge_dir))

from ipc_board_reader import KiCadIpcBoardReader
from ipc_board_writer import KiCadIpcBoardWriter

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("test_ipc_write_back")


def main():
    kicad_cli = Path(r"C:\Program Files\KiCad\10.99\bin\kicad-cli.exe")
    if not kicad_cli.is_file():
        logger.error(f"kicad-cli not found at {kicad_cli}")
        sys.exit(1)

    repo_root = bridge_dir.parent.parent.parent.parent.resolve()
    if not (repo_root / "fixtures").is_dir():
        repo_root = repo_root.parent
    fixture_pcb = repo_root / "fixtures" / "Issue558-dev-board-autoroute-demo" / "dev-board.kicad_pcb"
    if not fixture_pcb.is_file():
        logger.error(f"Fixture not found at {fixture_pcb}")
        sys.exit(1)

    # 1. Create temporary working directory for test board
    temp_dir = tempfile.mkdtemp(prefix="kicad_ipc_test_")
    test_pcb = Path(temp_dir) / "dev-board.kicad_pcb"
    shutil.copy2(fixture_pcb, test_pcb)

    # Copy project file if present
    fixture_pro = fixture_pcb.with_suffix(".kicad_pro")
    if fixture_pro.is_file():
        shutil.copy2(fixture_pro, Path(temp_dir) / "dev-board.kicad_pro")

    socket_file = Path(temp_dir) / "test_api.sock"
    socket_arg = f"ipc://{socket_file}"
    # NNG client connect path on Windows:
    client_socket = f"ipc://ipc\\{socket_file}"

    logger.info(f"Starting headless api-server on {test_pcb}...")
    server_proc = subprocess.Popen(
        [
            str(kicad_cli),
            "api-server",
            "--socket",
            socket_arg,
            str(test_pcb),
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    try:
        # 2. Wait for server to become ready
        logger.info("Waiting for KiCad IPC server...")
        writer = None
        for attempt in range(15):
            time.sleep(1)
            try:
                writer = KiCadIpcBoardWriter(socket_path=client_socket, timeout_ms=3000)
                logger.info(f"Connected to KiCad IPC server on attempt {attempt + 1}!")
                break
            except Exception:
                # Connection refused until server is fully initialized; retry
                pass

        if writer is None:
            raise RuntimeError("Could not connect to KiCad IPC server within timeout.")

        board = writer.board
        initial_tracks = len(board.get_tracks())
        initial_vias = len(board.get_vias())
        logger.info(f"Initial board state: {initial_tracks} tracks, {initial_vias} vias.")
        assert initial_tracks == 0, f"Expected 0 initial tracks, got {initial_tracks}"
        assert initial_vias == 0, f"Expected 0 initial vias, got {initial_vias}"

        # 3. Create synthetic routed data
        test_payload = {
            "layers": [
                {"index": 0, "name": "F.Cu", "type": "signal"},
                {"index": 1, "name": "B.Cu", "type": "signal"},
            ],
            "traces": [
                {
                    "netName": "+3V3",
                    "width": 0.25,
                    "layerIndex": 0,
                    "points": [
                        {"x": 120.5, "y": 68.15},
                        {"x": 122.1, "y": 68.15},
                    ],
                },
                {
                    "netName": "+3V3",
                    "width": 0.3,
                    "layerIndex": 1,
                    "points": [
                        {"x": 122.1, "y": 68.15},
                        {"x": 123.8, "y": 68.15},
                    ],
                },
            ],
            "vias": [
                {
                    "netName": "+3V3",
                    "position": {"x": 122.1, "y": 68.15},
                    "diameter": 0.6,
                    "drill": 0.3,
                }
            ],
        }

        # 4. Apply write-back
        logger.info("Writing test routed tracks and vias via IPC...")
        for n in board.get_nets():
            if n.name in ["+3V3", "GND"]:
                logger.info(f"Board net '{n.name}': {repr(n.proto)}")
        res = writer.write_routed_board(test_payload, replace_unfixed=True)
        logger.info(f"Write result: {res}")
        assert res["created_tracks"] == 2, f"Expected 2 track segments (1 per trace), got {res['created_tracks']}"
        assert res["created_vias"] == 1, f"Expected 1 via, got {res['created_vias']}"

        # 5. Verify board reflects created items
        tracks = board.get_tracks()
        vias = board.get_vias()
        logger.info(f"Verified board state: {len(tracks)} tracks, {len(vias)} vias.")
        for i, trk in enumerate(tracks):
            logger.info(f"Track {i}: net={trk.net}, proto={trk.proto}")
        for i, v in enumerate(vias):
            logger.info(f"Via {i}: net={v.net}, proto={v.proto}")

        assert len(tracks) == 2, f"Board should have 2 tracks, got {len(tracks)}"
        assert len(vias) == 1, f"Board should have 1 via, got {len(vias)}"

        # Check track details
        t0 = tracks[0]
        assert t0.net and t0.net.name == "+3V3", f"Track net should be '+3V3', got {t0.net}"
        assert t0.width == 250000 or t0.width == 300000, f"Unexpected track width: {t0.width}"

        # Check via details
        v0 = vias[0]
        assert v0.net and v0.net.name == "+3V3", f"Via net should be '+3V3', got {v0.net}"
        assert v0.diameter == 600000, f"Via diameter should be 600000 nm, got {v0.diameter}"
        assert v0.drill_diameter == 300000, f"Via drill should be 300000 nm, got {v0.drill_diameter}"
        assert abs(v0.position.x - 122100000) < 1000, f"Via X mismatch: {v0.position.x}"
        assert abs(v0.position.y - 68150000) < 1000, f"Via Y mismatch: {v0.position.y}"

        # 6. Test replace_unfixed: apply a replacement payload
        replacement_payload = {
            "layers": [
                {"index": 0, "name": "F.Cu", "type": "signal"},
            ],
            "traces": [
                {
                    "netName": "D+",
                    "width": 0.2,
                    "layerIndex": 0,
                    "points": [
                        {"x": 120.0, "y": 30.0},
                        {"x": 125.0, "y": 30.0},
                    ],
                }
            ],
            "vias": [],
        }
        logger.info("Applying replacement payload (replace_unfixed=True)...")
        res2 = writer.write_routed_board(replacement_payload, replace_unfixed=True)
        logger.info(f"Replacement result: {res2}")
        assert res2["removed_tracks"] == 2, f"Expected 2 removed tracks, got {res2['removed_tracks']}"
        assert res2["removed_vias"] == 1, f"Expected 1 removed via, got {res2['removed_vias']}"
        assert res2["created_tracks"] == 1, f"Expected 1 created track, got {res2['created_tracks']}"

        current_tracks = board.get_tracks()
        current_vias = board.get_vias()
        assert len(current_tracks) == 1, f"Expected 1 track after replacement, got {len(current_tracks)}"
        logger.info(f"Replacement track net: {current_tracks[0].net}, proto={current_tracks[0].proto}")
        assert current_tracks[0].net and current_tracks[0].net.name == "D+", f"Replacement track should belong to 'D+' net, got {current_tracks[0].net}"

        logger.info("ALL IPC WRITE-BACK TESTS PASSED SUCCESSFULLY!")

    finally:
        # Terminate server process and clean tempdir
        logger.info("Terminating headless api-server...")
        server_proc.terminate()
        try:
            server_proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server_proc.kill()

        try:
            shutil.rmtree(temp_dir, ignore_errors=True)
        except Exception:
            # Temporary directory cleanup is best-effort on Windows
            pass


if __name__ == "__main__":
    main()
