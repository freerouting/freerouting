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
from pathlib import Path

import pcbnew

from .config import DEFAULT_ROUTING_MODE
from .gui_helpers import has_pcbnew_api, wx_show_error
from .ipc_helpers import is_ipc_available
from .router_dsn import DsnRouter
from .router_ipc import IpcRouter


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
        """Prepare the environment and dispatch to the appropriate router."""
        board = pcbnew.GetBoard()
        board_path = Path(board.GetFileName())
        dirpath = board_path.parent

        # Load plugin configuration
        here_path = Path(__file__).parent
        config = configparser.ConfigParser()
        config.read(here_path / "plugin.ini")
        module_file = config["artifact"]["location"]

        # Choose routing mode
        if self.routing_mode == "IPC" and is_ipc_available():
            print("=== Routing mode: IPC/API ===")
            router = IpcRouter(self)
        else:
            if self.routing_mode == "IPC":
                print("IPC not available, falling back to DSN mode.")
            else:
                print("=== Routing mode: DSN (legacy) ===")
            router = DsnRouter(self)

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
            print(f"Using temp routing dir: {self.routing_dir}")
        else:
            self.routing_dir = dirpath

        self.module_input = self.routing_dir / "freerouting.dsn"
        self.temp_input = self.routing_dir / "temp-freerouting.dsn"
        self.module_output = self.routing_dir / "freerouting.ses"
        self.json_debug_path = self.routing_dir / "freerouting_debug.json"

        # Clean up previous temp files
        for f in (self.temp_input, self.module_output):
            f.unlink(missing_ok=True)

        # Execute the chosen workflow
        if isinstance(router, IpcRouter):
            self._run_ipc(router)
        else:
            self._run_dsn(router)

        # Clean up temp directory if one was created
        self._cleanup()

    def _run_ipc(self, router):
        """Execute the IPC/API routing workflow."""
        return router.run()

    def _run_dsn(self, router):
        """Execute the legacy DSN routing workflow."""
        from .java_utils import detect_os_architecture, get_local_java_executable_path

        os_name, _ = detect_os_architecture()
        java_path = get_local_java_executable_path(os_name)

        if not router.prepare(self.board, self.dirpath, self.here_path, java_path, self.module_file):
            return False

        if not router.run():
            return False

        return router.import_ses()

    def _cleanup(self):
        """Remove the temporary routing directory if one was created."""
        if hasattr(self, "routing_dir") and self.routing_dir != self.dirpath:
            import shutil
            try:
                shutil.rmtree(str(self.routing_dir), ignore_errors=True)
            except Exception as e:
                print(f"Warning: could not remove temp dir: {e}")


# Register the plugin with KiCad's plugin manager.
FreeroutingPlugin().register()