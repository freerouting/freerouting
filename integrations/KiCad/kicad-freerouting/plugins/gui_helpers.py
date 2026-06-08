# ---------------------------------------------------------------------------
# gui_helpers.py — GUI utility functions for the KiCad Freerouting plugin
# ---------------------------------------------------------------------------
# All wxPython GUI helpers live here: thread-safe invocation, message
# dialogs, and the shared application instance setup.  Keeping GUI code
# in a separate module makes it easy to test the rest of the plugin
# logic without a display.
# ---------------------------------------------------------------------------

import wx
import wx.aui

# ------------------------------------------------------------------
# Shared wx.App instance
# ------------------------------------------------------------------
# KiCad's Python console uses the "Phoenix" wxPython build.  A single
# wx.App must exist before any GUI widgets are created.  We create one
# lazily if KiCad hasn't already done so.
if "phoenix" in wx.PlatformInfo:
    if not wx.GetApp():
        theApp = wx.App()
    else:
        theApp = wx.GetApp()


# ------------------------------------------------------------------
# Thread-safe GUI invocation
# ------------------------------------------------------------------
# KiCad's main thread owns the GUI.  Worker threads (e.g. the polling
# loop that waits for routing completion) must use wx.CallAfter to
# safely interact with widgets.
def wx_safe_invoke(function, *args, **kwargs):
    """Schedule *function* to run on the main GUI thread.

    Use this from background threads to safely update UI elements or
    close dialogs.  Wraps ``wx.CallAfter`` for a simpler signature.
    """
    wx.CallAfter(function, *args, **kwargs)


# ------------------------------------------------------------------
# Dialog helpers
# ------------------------------------------------------------------
# Caption used by all plugin message dialogs for consistent branding.
wx_caption = "KiCad Freerouting Plugin"


def wx_show_warning(text):
    """Display a warning dialog with Yes/No buttons.

    Returns the user's choice (``wx.ID_YES`` or ``wx.ID_NO``).
    """
    style = wx.YES_NO | wx.ICON_WARNING
    dialog = wx.MessageDialog(None, message=text, caption=wx_caption, style=style)
    result = dialog.ShowModal()
    dialog.Destroy()
    return result


def wx_show_error(text):
    """Display an error dialog with an OK button.  Blocks until dismissed."""
    style = wx.OK | wx.ICON_ERROR
    dialog = wx.MessageDialog(None, message=text, caption=wx_caption, style=style)
    dialog.ShowModal()
    dialog.Destroy()


def has_pcbnew_api():
    """Return ``True`` if the required pcbnew SWIG APIs are available.

    Some older KiCad builds (or non-nightly releases) may lack the
    Specctra DSN/SES import/export functions.  This guard prevents
    crashes when those functions are missing.
    """
    import pcbnew
    return hasattr(pcbnew, "ExportSpecctraDSN") and hasattr(pcbnew, "ImportSpecctraSES")