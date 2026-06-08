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
import logging

import pcbnew

from .config import LOG_DIR
from .gui_helpers import wx_show_error, wx_safe_invoke
from .process_utils import ProcessDialog, ProcessThread

logger = logging.getLogger("freerouting")



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

        # Save input DSN debug file if enabled
        from .config import SAVE_DEBUG_JSON, DEBUG_JSON_DIR
        if SAVE_DEBUG_JSON:
            DEBUG_JSON_DIR.mkdir(parents=True, exist_ok=True)
            import shutil
            try:
                shutil.copy2(self.plugin.module_input, DEBUG_JSON_DIR / "freerouting_input_board.dsn")
                logger.info(f"Saved debug input DSN to: {DEBUG_JSON_DIR / 'freerouting_input_board.dsn'}")
            except Exception as e:
                logger.error(f"Failed to copy input DSN to debug dir: {e}", exc_info=True)

        self._build_command()
        return True

    def _export_dsn(self):
        """Export the board to a temporary DSN file via pcbnew."""
        logger.info("Exporting board to DSN via pcbnew.ExportSpecctraDSN...")
        try:
            logger.info("Trying pcbnew.ExportSpecctraDSN(board, filename)...")
            ok = pcbnew.ExportSpecctraDSN(self.plugin.board, str(self.plugin.temp_input))
        except TypeError:
            try:
                logger.info("Trying pcbnew.ExportSpecctraDSN(filename) fallback...")
                ok = pcbnew.ExportSpecctraDSN(str(self.plugin.temp_input))
            except Exception as e:
                logger.error(f"Failed pcbnew.ExportSpecctraDSN(filename): {e}", exc_info=True)
                ok = False
        except Exception as e:
            logger.error(f"Failed pcbnew.ExportSpecctraDSN(board, filename): {e}", exc_info=True)
            ok = False

        if ok and self.plugin.temp_input.is_file():
            logger.info("DSN export succeeded.")
            return True
        logger.error("Failed to invoke pcbnew.ExportSpecctraDSN")
        wx_show_error("Failed to invoke pcbnew.ExportSpecctraDSN")
        return False

    def _sanitize_dsn(self):
        """Copy the exported DSN, stripping problematic characters."""
        logger.info("Sanitizing exported DSN file...")
        with open(self.plugin.module_input, "w") as fw:
            with open(self.plugin.temp_input, "r", encoding="utf-8") as fr:
                first = True
                for line in fr:
                    if first:
                        fw.write(f"(pcb {self.plugin.module_input.name}\n")
                        first = False
                    else:
                        fw.write(search_n_strip(line))
        logger.info("Sanitization complete.")

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
        logger.info(f"Built DSN routing command: {' '.join(self.plugin.module_command)}")

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
            logger.info("DSN routing process complete callback fired.")
            wx_safe_invoke(dialog.terminate)

        invoker = ProcessThread(self.plugin.module_command, on_complete)
        dialog.Show()
        logger.info("Starting DSN routing process thread...")
        invoker.start()
        result = dialog.ShowModal()
        dialog.Destroy()

        try:
            if result == dialog.result_button:
                logger.warning("Routing cancelled by user.")
                invoker.terminate()
                return False
            elif result == dialog.result_terminate:
                if invoker.has_ok():
                    logger.info("Routing process exited with success.")
                    return True
                logger.error("Routing process exited with error status.")
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
        from .config import SAVE_DEBUG_JSON, DEBUG_JSON_DIR
        if SAVE_DEBUG_JSON and self.plugin.module_output.is_file():
            DEBUG_JSON_DIR.mkdir(parents=True, exist_ok=True)
            import shutil
            try:
                shutil.copy2(self.plugin.module_output, DEBUG_JSON_DIR / "freerouting_output_board.dsn")
                logger.info(f"Saved debug output SES (as DSN filename) to: {DEBUG_JSON_DIR / 'freerouting_output_board.dsn'}")
            except Exception as e:
                logger.error(f"Failed to copy output SES to debug dir: {e}", exc_info=True)

        logger.info("Importing Specctra SES into KiCad...")
        try:
            logger.info("Trying pcbnew.ImportSpecctraSES(board, filename)...")
            ok = pcbnew.ImportSpecctraSES(self.plugin.board, str(self.plugin.module_output))
        except TypeError:
            try:
                logger.info("Trying pcbnew.ImportSpecctraSES(filename) fallback...")
                ok = pcbnew.ImportSpecctraSES(str(self.plugin.module_output))
            except Exception as e:
                logger.error(f"Failed pcbnew.ImportSpecctraSES(filename): {e}", exc_info=True)
                ok = False
        except Exception as e:
            logger.error(f"Failed pcbnew.ImportSpecctraSES(board, filename): {e}", exc_info=True)
            ok = False

        if ok:
            logger.info("SES import succeeded.")
            try:
                if hasattr(pcbnew, "UpdateUserInterface"):
                    pcbnew.UpdateUserInterface()
                pcbnew.Refresh()
                logger.info("KiCad UI refreshed after DSN import.")
            except Exception as e:
                logger.warning(f"Could not refresh KiCad UI: {e}")

            try:
                self.plugin.module_input.unlink(missing_ok=True)
            except Exception as e:
                logger.warning(f"Could not delete input DSN file: {e}")

            try:
                self.plugin.module_output.unlink(missing_ok=True)
            except Exception as e:
                logger.warning(f"Could not delete output SES file: {e}")

            return True
        logger.error("Failed to invoke pcbnew.ImportSpecctraSES")
        wx_show_error("Failed to invoke pcbnew.ImportSpecctraSES")
        return False