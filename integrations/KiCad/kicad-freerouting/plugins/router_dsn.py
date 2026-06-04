# ---------------------------------------------------------------------------
# router_dsn.py — Legacy DSN file-exchange routing workflow
# ---------------------------------------------------------------------------
# This module implements the original routing workflow: export the board
# as a Specctra DSN file, run Freerouting in headless mode, and import
# the resulting SES file back into KiCad.  It is used as a fallback
# when the IPC API is not available.
# ---------------------------------------------------------------------------

import re
import textwrap

import pcbnew

from .config import LOG_DIR
from .gui_helpers import wx_show_error, wx_safe_invoke
from .process_utils import ProcessDialog, ProcessThread


def search_n_strip(s):
    """Remove characters that cause issues in Specctra DSN files.

    KiCad net names and reference designators sometimes contain Greek
    letters (Ω, µ, Φ) that the Specctra parser cannot handle.
    """
    return re.sub("[ΩµΦ]", "", s)


class DsnRouter:
    """Legacy DSN-based routing workflow.

    This class encapsulates the prepare → export → run → import cycle
    for the file-based routing approach.  It is used by the main plugin
    when IPC mode is not available.
    """

    def __init__(self, plugin):
        """Args:
            plugin: The parent ``FreeroutingPlugin`` instance.
        """
        self.plugin = plugin

    def prepare(self, board, dirpath, here_path, java_path, module_file):
        """Set up file paths and export the board to DSN.

        Returns:
            ``True`` on success, ``False`` on export failure.
        """
        self.plugin.board = board
        self.plugin.dirpath = dirpath
        self.plugin.here_path = here_path
        self.plugin.java_path = java_path
        self.plugin.module_file = module_file
        self.plugin.module_path = here_path / module_file


        # Set up routing directory (handle spaces in path)
        from pathlib import Path
        import tempfile

        if " " in str(dirpath):
            self.plugin.routing_dir = Path(tempfile.mkdtemp(prefix="freerouting_"))
        else:
            self.plugin.routing_dir = dirpath

        self.plugin.module_input = self.plugin.routing_dir / "freerouting.dsn"
        self.plugin.temp_input = self.plugin.routing_dir / "temp-freerouting.dsn"
        self.plugin.module_output = self.plugin.routing_dir / "freerouting.ses"
        self.plugin.module_rules = self.plugin.routing_dir / "freerouting.rules"

        for f in (self.plugin.temp_input, self.plugin.module_output, self.plugin.module_rules):
            f.unlink(missing_ok=True)

        # Export DSN
        if not self._export_dsn():
            return False

        # Clean up DSN (strip offending characters)
        self._sanitize_dsn()
        self._build_command()
        return True

    def _export_dsn(self):
        """Export the board to a temporary DSN file via pcbnew."""
        ok = pcbnew.ExportSpecctraDSN(str(self.plugin.temp_input))
        if ok and self.plugin.temp_input.is_file():
            return True
        wx_show_error("Failed to invoke pcbnew.ExportSpecctraDSN")
        return False

    def _sanitize_dsn(self):
        """Copy the exported DSN, stripping problematic characters."""
        with open(self.plugin.module_input, "w") as fw:
            with open(self.plugin.temp_input, "r", encoding="utf-8") as fr:
                first = True
                for line in fr:
                    if first:
                        fw.write(f"(pcb {self.plugin.module_input.name}\n")
                        first = False
                    else:
                        fw.write(search_n_strip(line))

    def _build_command(self):
        """Build the command line for running Freerouting in DSN mode."""
        LOG_DIR.mkdir(parents=True, exist_ok=True)

        self.plugin.module_command = [
            str(self.plugin.java_path),
            "-jar",
            str(self.plugin.module_path),
            "-de",
            str(self.plugin.module_input),
            "-do",
            str(self.plugin.module_output),
            "-host",
            str(self.plugin.host),
            f"--logging.file.location={LOG_DIR}",
        ]

    def run(self):
        """Run Freerouting and show a progress dialog.

        Returns:
            ``True`` if Freerouting exited successfully.
        """
        dialog = ProcessDialog(
            None,
            textwrap.dedent("""
                Complete or Terminate Freerouting:
                * to complete, close Java window
                * to terminate, press Terminate here
            """),
        )

        def on_complete():
            wx_safe_invoke(dialog.terminate)

        invoker = ProcessThread(self.plugin.module_command, on_complete)
        dialog.Show()
        invoker.start()
        result = dialog.ShowModal()
        dialog.Destroy()

        try:
            if result == dialog.result_button:
                invoker.terminate()
                return False
            elif result == dialog.result_terminate:
                if invoker.has_ok():
                    return True
                invoker.show_error()
                return False
            return False
        finally:
            invoker.join(10)

    def import_ses(self):
        """Import the generated SES file back into KiCad.

        Returns:
            ``True`` on success.
        """
        ok = pcbnew.ImportSpecctraSES(str(self.plugin.module_output))
        if ok and self.plugin.module_output.is_file():
            self.plugin.module_input.unlink(missing_ok=True)
            self.plugin.module_output.unlink(missing_ok=True)
            return True
        wx_show_error("Failed to invoke pcbnew.ImportSpecctraSES")
        return False