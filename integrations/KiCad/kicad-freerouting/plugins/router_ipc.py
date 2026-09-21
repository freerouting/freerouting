# ---------------------------------------------------------------------------
# router_ipc.py — KiCad Protobuf IPC routing workflow
# ---------------------------------------------------------------------------
# Official IPC routing path for KiCad 9/10+ (and target default for KiCad 11+):
#   1. Connect to KiCad via Protocol Buffers IPC (kipy / NNG socket or pipe).
#   2. Extract the complete board geometry, footprints, nets, and design rules
#      (including copper_edge_clearance from .kicad_pro).
#   3. Start Freerouting headless REST API server (if not already running).
#   4. Upload serialized KiCadBoardJson payload and trigger routing.
#   5. Poll for completion or stream progress.
#   6. Apply routed tracks and vias back into KiCad using an atomic commit
#      transaction (single-step Ctrl+Z undo support).
# ---------------------------------------------------------------------------

import json
import logging
import os
import platform
import subprocess
import sys
import textwrap
import threading
import time
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

from .api_client import FreeroutingApiClient
from .config import (
    API_JOB_TIMEOUT,
    API_POLL_INTERVAL,
    API_SERVER_STARTUP_TIMEOUT,
    DEBUG_INPUT_JSON_FILENAME,
    DEBUG_JSON_DIR,
    DEBUG_OUTPUT_JSON_FILENAME,
    LOG_DIR,
    SAVE_DEBUG_JSON,
)
from .gui_helpers import wx_safe_invoke, wx_show_error, wx_show_warning

logger = logging.getLogger("freerouting.ipc_router")


def discover_venv_site_packages() -> None:
    """Ensure site-packages containing kicad-python (kipy) are in sys.path.

    Checks:
      1. integrations/KiCad/.venv
      2. %LOCALAPPDATA%/KiCad/<version>/3rdparty/Python*
    """
    repo_venv_site_packages = (
        Path(__file__).resolve().parent.parent.parent / ".venv" / "Lib" / "site-packages"
    )
    if repo_venv_site_packages.is_dir() and str(repo_venv_site_packages) not in sys.path:
        sys.path.insert(0, str(repo_venv_site_packages))


def is_kipy_installed() -> bool:
    """Check if the `kicad-python` (kipy) package is importable."""
    discover_venv_site_packages()
    try:
        import kipy  # noqa: F401
        return True
    except ImportError:
        return False


def is_ipc_available(socket_path: Optional[str] = None, timeout_ms: int = 1500) -> Tuple[bool, str]:
    """Test if KiCad's IPC API server is reachable.

    Returns:
        (is_available, error_or_info_message)
    """
    if not is_kipy_installed():
        return False, "kicad-python package (kipy) is not installed."

    try:
        import kipy
        k = kipy.KiCad(socket_path=socket_path, timeout_ms=timeout_ms)
        v = k.get_version()
        board = k.get_board()
        return True, f"Connected to KiCad {v} (Board: '{board.name}')"
    except Exception as e:
        return False, str(e)


class IpcRouter:
    """KiCad Protobuf IPC routing workflow.

    Orchestrates:
      IPC board extraction → local Freerouting REST API server →
      JSON upload & routing → atomic IPC write-back with single-step undo.
    """

    def __init__(self, plugin):
        """Args:
            plugin: The parent ``FreeroutingPlugin`` instance.
        """
        self.plugin = plugin
        self._api_process = None
        self._ipc_reader = None
        self._ipc_writer = None

    # ------------------------------------------------------------------
    # Pre-flight IPC check
    # ------------------------------------------------------------------

    def check_availability(self) -> Tuple[bool, str]:
        """Verify that the IPC server is active and accessible."""
        return is_ipc_available()

    # ------------------------------------------------------------------
    # Main workflow
    # ------------------------------------------------------------------

    def extract_board(self) -> Optional[Dict[str, Any]]:
        """Extract board geometry and design rules from KiCad via IPC."""
        logger.info("Extracting board data via Protocol Buffers IPC...")
        try:
            # Ensure ipc_bridge directory is in sys.path
            # Check both plugins/ipc_bridge (installed/clean structure) and sibling ipc_bridge
            here = Path(__file__).resolve().parent
            for candidate in (here / "ipc_bridge", here.parent / "ipc_bridge"):
                if candidate.is_dir() and str(candidate) not in sys.path:
                    sys.path.insert(0, str(candidate))

            from ipc_board_reader import KiCadIpcBoardReader
            self._ipc_reader = KiCadIpcBoardReader()
            board_data = self._ipc_reader.read_board_data()
            logger.info(
                f"Extracted board via IPC: {len(board_data.get('layers', []))} layers, "
                f"{len(board_data.get('components', []))} components, "
                f"{len(board_data.get('nets', []))} nets, "
                f"outline clearance: {board_data.get('outline', {}).get('clearance')} mm."
            )
            return board_data
        except Exception as e:
            logger.error(f"Failed to extract board via IPC: {e}", exc_info=True)
            return None

    def apply_routing_result(self, output_json_str: str, replace_unfixed: bool = True) -> bool:
        """Write routed tracks and vias back to KiCad using an atomic commit."""
        logger.info("Writing routed board back to KiCad via IPC commit...")
        try:
            here = Path(__file__).resolve().parent
            for candidate in (here / "ipc_bridge", here.parent / "ipc_bridge"):
                if candidate.is_dir() and str(candidate) not in sys.path:
                    sys.path.insert(0, str(candidate))

            from ipc_board_writer import KiCadIpcBoardWriter
            board_data = json.loads(output_json_str)

            writer = KiCadIpcBoardWriter()
            res = writer.write_routed_board(
                board_data,
                replace_unfixed=replace_unfixed,
                commit_message="Freerouting Autoroute",
            )
            logger.info(
                f"Successfully committed changes to KiCad via IPC: "
                f"created {res['created_tracks']} tracks, {res['created_vias']} vias "
                f"(removed {res['removed_tracks']} old tracks, {res['removed_vias']} old vias)."
            )
            return True
        except Exception as e:
            logger.error(f"Failed to write back routed board via IPC: {e}", exc_info=True)
            wx_show_error(f"Failed to apply routing results via KiCad IPC:\n{e}")
            return False

    # ------------------------------------------------------------------
    # Freerouting headless API server management
    # ------------------------------------------------------------------

    def build_api_command(self):
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

    def start_api_server(self) -> bool:
        """Launch the Freerouting API server and wait for health check."""
        logger.info("Starting Freerouting API server...")
        try:
            popen_kwargs = {}
            if platform.system() == "Windows":
                popen_kwargs["creationflags"] = getattr(subprocess, "CREATE_NO_WINDOW", 0x08000000)
            else:
                popen_kwargs["start_new_session"] = True

            self._api_process = subprocess.Popen(
                self.plugin.module_command,
                **popen_kwargs,
            )
        except Exception as e:
            logger.error(f"Failed to start Freerouting API server: {e}", exc_info=True)
            wx_show_error(f"Failed to start Freerouting API server:\n{e}")
            return False

        client = FreeroutingApiClient()
        for _ in range(API_SERVER_STARTUP_TIMEOUT):
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
