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
      2. KiCad 3rdparty Python site-packages in Documents or OneDrive
      3. %LOCALAPPDATA%/KiCad/<version>/3rdparty/Python*
    """
    candidates = []

    # 1. Local workspace venv
    here = Path(__file__).resolve().parent
    candidates.append(here.parent.parent.parent / ".venv" / "Lib" / "site-packages")
    candidates.append(here.parent / ".venv" / "Lib" / "site-packages")

    # 2. Installed 3rdparty Python site-packages (e.g. Documents/KiCad/x.x/3rdparty/Python*/site-packages)
    # Check parent trees of plugin installation directory
    for ancestor in (here.parent, here.parent.parent):
        for py_dir in ancestor.glob("Python*"):
            if py_dir.is_dir():
                candidates.append(py_dir / "site-packages")

    for candidate in candidates:
        if candidate.is_dir() and str(candidate) not in sys.path:
            logger.debug(f"Adding candidate site-packages to sys.path: {candidate}")
            sys.path.insert(0, str(candidate))


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
            if board_data:
                logger.info(
                    f"Extracted board via IPC: {len(board_data.get('layers', []))} layers, "
                    f"{len(board_data.get('components', []))} components, "
                    f"{len(board_data.get('nets', []))} nets, "
                    f"outline clearance: {board_data.get('outline', {}).get('clearance')} mm."
                )
                return board_data
        except Exception as e:
            logger.warning(f"IPC board extraction failed: {e}. Trying in-process fallback if available...", exc_info=True)

        # In-process SWIG fallback if running inside KiCad
        try:
            if hasattr(self.plugin, "board") and self.plugin.board:
                try:
                    from .board_json_helpers import _build_board_json_manually
                except Exception:
                    try:
                        from plugins.board_json_helpers import _build_board_json_manually
                    except Exception:
                        from board_json_helpers import _build_board_json_manually

                board_json_str = _build_board_json_manually(self.plugin.board)
                board_data = json.loads(board_json_str)

                # Resolve edge clearance from .kicad_pro if possible
                try:
                    board_path = Path(self.plugin.board.GetFileName())
                    pro_path = board_path.with_suffix(".kicad_pro")
                    if pro_path.is_file():
                        with open(pro_path, "r", encoding="utf-8") as f:
                            pro_data = json.load(f)
                        val = (
                            pro_data.get("board", {})
                            .get("design_settings", {})
                            .get("rules", {})
                            .get("min_copper_edge_clearance")
                        )
                        if val is not None and isinstance(val, (int, float)) and val > 0:
                            if "outline" in board_data:
                                board_data["outline"]["clearance"] = float(val)
                except Exception as pro_err:
                    logger.debug(f"Could not inspect .kicad_pro in fallback: {pro_err}")

                logger.info("Successfully extracted board using in-process SWIG fallback.")
                return board_data
        except Exception as fb_err:
            logger.error(f"In-process board extraction fallback also failed: {fb_err}", exc_info=True)

        return None

    def apply_routing_result(self, output_json_str: str, replace_unfixed: bool = True) -> bool:
        """Write routed tracks and vias back to KiCad using an atomic commit."""
        logger.info("Writing routed board back to KiCad...")

        # 1. In-process direct write-back:
        # In KiCad 9/10 GUI sessions, an active ActionPlugin runs on KiCad's main thread.
        # Calling IPC socket APIs from this thread causes KiCad's event loop to deadlock/timeout.
        # Writing directly to pcbnew.GetBoard() with BOARD_COMMIT is instantaneous and native.
        try:
            import pcbnew
            if hasattr(pcbnew, "GetBoard") and pcbnew.GetBoard() is not None:
                logger.info("Applying routing result via in-process pcbnew commit...")
                self.plugin._apply_result_to_kicad(output_json_str)
                logger.info("Routing result applied successfully via in-process commit.")
                return True
        except Exception as e:
            logger.warning(f"In-process commit unavailable or failed: {e}. Trying IPC write-back...", exc_info=True)

        # 2. Out-of-process IPC write-back (for standalone runner / tests / KiCad 11+)
        try:
            here = Path(__file__).resolve().parent
            for candidate in (here, here / "ipc_bridge", here.parent / "ipc_bridge"):
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
            # Final fallback: in-process commit
            try:
                self.plugin._apply_result_to_kicad(output_json_str)
                logger.info("Routing result applied successfully via in-process fallback.")
                return True
            except Exception as fb_err:
                logger.error(f"In-process fallback also failed: {fb_err}", exc_info=True)
            wx_show_error(f"Failed to apply routing results via KiCad IPC:\n{e}")
            return False

    def apply_routing_ses(self, ses_path: Path) -> bool:
        """Import routed tracks and vias from a Specctra SES file into KiCad."""
        logger.info(f"Importing routed SES file into KiCad: {ses_path}...")
        try:
            # KiCad's ImportSpecctraSES fails with "Unexpected 'place'" if (lock_type position) is present.
            try:
                content = ses_path.read_text(encoding="utf-8")
                if "(lock_type position)" in content:
                    content = content.replace("(lock_type position)", "")
                    ses_path.write_text(content, encoding="utf-8")
                    logger.info("Sanitized SES file by removing '(lock_type position)' for KiCad compatibility.")
            except Exception as se:
                logger.debug(f"Could not sanitize SES file: {se}")

            import pcbnew
            board = getattr(self.plugin, "board", None)
            if board is None and hasattr(pcbnew, "GetBoard"):
                try:
                    board = pcbnew.GetBoard()
                except Exception:
                    pass

            ok = False
            if board is not None:
                try:
                    ok = pcbnew.ImportSpecctraSES(board, str(ses_path))
                except Exception as be:
                    logger.debug(f"pcbnew.ImportSpecctraSES(board, path) failed: {be}")

            if not ok:
                try:
                    ok = pcbnew.ImportSpecctraSES(str(ses_path))
                except Exception as fe:
                    logger.debug(f"pcbnew.ImportSpecctraSES(path) failed: {fe}")

            if ok:
                logger.info("Successfully imported SES file into KiCad.")
                try:
                    if hasattr(pcbnew, "UpdateUserInterface"):
                        pcbnew.UpdateUserInterface()
                    pcbnew.Refresh()
                except Exception:
                    pass
                return True
            else:
                logger.warning("pcbnew.ImportSpecctraSES returned False.")
                return False
        except Exception as e:
            logger.error(f"Failed to import SES file: {e}", exc_info=True)
            wx_show_error(f"Failed to import routing result SES:\n{e}")
            return False

    # ------------------------------------------------------------------
    # Freerouting headless API server & GUI process management
    # ------------------------------------------------------------------

    def build_gui_command(self, input_json_path: Path, output_file_path: Path) -> None:
        """Build the command to launch Freerouting in interactive GUI mode with board JSON."""
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        self.plugin.module_command = [
            str(self.plugin.java_path),
            "-jar",
            str(self.plugin.module_path),
            "-de",
            str(input_json_path),
            "-do",
            str(output_file_path),
            "-host",
            "KiCad",
            "--gui.enabled=true",
            "--api_server.enabled=false",
            "--mcp_server.enabled=false",
            f"--logging.file.location={LOG_DIR}",
        ]
        logger.info(f"Built IPC GUI command: {' '.join(self.plugin.module_command)}")

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

    def start_api_server(self, pump_callback=None) -> bool:
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
        poll_interval = 0.1
        max_attempts = int(API_SERVER_STARTUP_TIMEOUT / poll_interval)
        for i in range(max_attempts):
            if pump_callback:
                try:
                    pump_callback()
                except Exception:
                    pass
            time.sleep(poll_interval)
            if i % 5 == 0:
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
