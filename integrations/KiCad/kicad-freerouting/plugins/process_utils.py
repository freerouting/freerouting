# ---------------------------------------------------------------------------
# process_utils.py — External process management
# ---------------------------------------------------------------------------
# Two classes for running Freerouting (or other external tools) as a
# subprocess:
#   * ``ProcessDialog`` — a wx dialog with status indicators and a
#     "Terminate" button that lets the user cancel a long-running operation.
#   * ``ProcessThread`` — a daemon thread that runs a subprocess, letting
#     stdout/stderr flow through to the console so the user can see
#     Freerouting's log output in the terminal window.
# ---------------------------------------------------------------------------

import platform
import shlex
import subprocess
import threading
import textwrap

import wx

from .gui_helpers import wx_caption, wx_show_error


# ------------------------------------------------------------------
# Status indicator constants
# ------------------------------------------------------------------
STATUS_UNDETERMINED = 0
STATUS_IN_PROGRESS = 1
STATUS_PASS = 2
STATUS_FAIL = 3

# Unicode symbols for each state
_SYMBOLS = {
    STATUS_UNDETERMINED: "\u25CB",       # ○ open circle
    STATUS_IN_PROGRESS: "\u21BB",         # ↻ clockwise open circle arrow
    STATUS_PASS: "\u2713",                # ✓ check mark
    STATUS_FAIL: "\u2717",                # ✗ ballot X
}

# Colours for each state (works on both light and dark themes)
_COLORS = {
    STATUS_UNDETERMINED: wx.Colour(160, 160, 160),   # gray
    STATUS_IN_PROGRESS: wx.Colour(160, 160, 160),     # gray (animated)
    STATUS_PASS: wx.Colour(0, 176, 80),               # green
    STATUS_FAIL: wx.Colour(220, 38, 38),              # red
}


class StatusIndicator(wx.Panel):
    """A small read-only indicator showing in-progress / pass / fail status.

    Displays a Unicode symbol (↻ spinner, ✓ check, ✗ X, ○ pending) next to
    a label.  The symbols are rendered as text so they look correct on both
    light and dark themes.  When set to ``STATUS_IN_PROGRESS``, a built-in
    timer animates the spinner by rotating through Unicode arrow characters.

    Attributes:
        label: The display label for this indicator.
    """

    # Spinner animation characters (rotating arrows)
    _SPIN_CHARS = [
        "\u21BB",  # ↻
        "\u21C0",  # ⇀
        "\u21C1",  # ⇁
        "\u21C2",  # ⇂
        "\u21C3",  # ⇃
        "\u21B7",  # ↷
        "\u21BA",  # ↺
        "\u21B9",  # ↹
        "\u21C4",  # ⇄
        "\u21C5",  # ⇅
        "\u21C6",  # ⇆
        "\u21C7",  # ⇇
    ]

    def __init__(self, parent, label, status=STATUS_UNDETERMINED):
        super().__init__(parent)
        self._status = status
        self._spin_phase = 0

        self._sizer = wx.BoxSizer(wx.HORIZONTAL)

        symbol = _SYMBOLS[status]
        colour = _COLORS[status]
        self._symbol_label = wx.StaticText(self, wx.ID_ANY, symbol)
        font = self._symbol_label.GetFont()
        font.SetPointSize(font.GetPointSize() + 2)
        font.SetWeight(wx.FONTWEIGHT_BOLD)
        self._symbol_label.SetFont(font)
        self._symbol_label.SetForegroundColour(colour)
        self._sizer.Add(self._symbol_label, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 8)

        self._label = wx.StaticText(self, wx.ID_ANY, label)
        self._sizer.Add(self._label, 0, wx.ALIGN_CENTER_VERTICAL)

        self.SetSizer(self._sizer)

        # Animation timer for in-progress state
        self._timer = wx.Timer(self)
        self.Bind(wx.EVT_TIMER, self._on_timer, self._timer)

    def _on_timer(self, event):
        """Advance the spinner animation by one frame."""
        self._spin_phase = (self._spin_phase + 1) % len(self._SPIN_CHARS)
        self._symbol_label.SetLabel(self._SPIN_CHARS[self._spin_phase])

    def set_status(self, status):
        """Update the indicator to the given status.

        Args:
            status: One of ``STATUS_UNDETERMINED``, ``STATUS_IN_PROGRESS``,
                ``STATUS_PASS``, or ``STATUS_FAIL``.
        """
        self._status = status
        if status == STATUS_IN_PROGRESS:
            self._spin_phase = 0
            self._symbol_label.SetLabel(self._SPIN_CHARS[0])
            self._symbol_label.SetForegroundColour(_COLORS[STATUS_IN_PROGRESS])
            self._timer.Start(100)
        else:
            self._timer.Stop()
            self._symbol_label.SetLabel(_SYMBOLS[status])
            self._symbol_label.SetForegroundColour(_COLORS[status])


class ProcessDialog(wx.Dialog):
    """Modal dialog shown while Freerouting is running.

    Displays status indicators for all stages of the routing pipeline
    and a "Terminate" button.  Indicators start in the *undetermined*
    state (gray circle) and are updated by the caller via the various
    ``set_*_status()`` methods.

    The order of indicators is:
      1. Detecting Java 25+ JRE
      2. Checking if KiCad IPC API is available
      3. Sending board to Freerouting
      4. Auto-router is running
      5. Receiving the results

    Attributes:
        result_button: Modal result ID when the user presses Terminate.
        result_terminate: Modal result ID when ``terminate()`` is called.
    """

    def __init__(self, parent, text=""):
        self.result_button = wx.NewIdRef()
        self.result_terminate = wx.NewIdRef()

        super().__init__(
            parent, id=wx.ID_ANY, title=wx_caption,
            pos=wx.DefaultPosition, size=wx.Size(-1, -1),
            style=wx.CAPTION,
        )
        self.SetSizeHints(wx.DefaultSize, wx.DefaultSize)

        sizer = wx.BoxSizer(wx.VERTICAL)

        # --- status indicators (vertical stack) ---
        indicator_sizer = wx.BoxSizer(wx.VERTICAL)

        self.java_indicator = StatusIndicator(self, "Detecting Java 25+ JRE", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.java_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        self.ipc_indicator = StatusIndicator(self, "Checking if KiCad IPC API is available", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.ipc_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        self.sending_indicator = StatusIndicator(self, "Sending board to Freerouting", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.sending_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        self.routing_indicator = StatusIndicator(self, "Auto-router is running", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.routing_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        self.receiving_indicator = StatusIndicator(self, "Receiving the results", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.receiving_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        sizer.Add(indicator_sizer, 0, wx.ALIGN_CENTER_HORIZONTAL | wx.TOP | wx.BOTTOM, 10)

        # --- message text (optional) ---
        if text:
            self.text = wx.StaticText(self, wx.ID_ANY, text, wx.DefaultPosition, wx.DefaultSize, 0)
            self.text.Wrap(-1)
            sizer.Add(self.text, 0, wx.ALIGN_CENTER_HORIZONTAL | wx.ALL, 10)

        self.line = wx.StaticLine(self, wx.ID_ANY, wx.DefaultPosition, wx.DefaultSize, wx.LI_HORIZONTAL)
        sizer.Add(self.line, 0, wx.EXPAND | wx.ALL, 5)

        # --- terminate button ---
        self.bttn = wx.Button(self, wx.ID_ANY, "Terminate", wx.DefaultPosition, wx.DefaultSize, 0)
        self.bttn.SetDefault()
        sizer.Add(self.bttn, 0, wx.ALIGN_CENTER_HORIZONTAL | wx.ALL, 5)

        self.SetSizer(sizer)
        self.Layout()
        sizer.Fit(self)
        self.Centre(wx.BOTH)

        self.bttn.Bind(wx.EVT_BUTTON, self._on_click)

    # -- public API -------------------------------------------------------

    def set_java_status(self, status):
        """Update the Java detection indicator."""
        self.java_indicator.set_status(status)

    def set_ipc_status(self, status):
        """Update the IPC API indicator."""
        self.ipc_indicator.set_status(status)

    def set_sending_status(self, status):
        """Update the 'Sending board to Freerouting' indicator."""
        self.sending_indicator.set_status(status)

    def set_routing_status(self, status):
        """Update the 'Auto-router is running' indicator."""
        self.routing_indicator.set_status(status)

    def set_receiving_status(self, status):
        """Update the 'Receiving the results' indicator."""
        self.receiving_indicator.set_status(status)

    def terminate(self):
        """Close the dialog with the "programmatic termination" result."""
        self.EndModal(self.result_terminate)

    # -- internal ---------------------------------------------------------

    def _on_click(self, event):
        self.EndModal(self.result_button)


class ProcessThread(threading.Thread):
    """Run an external command in a daemon thread.

    The subprocess inherits the parent's stdout/stderr so that
    Freerouting's console output is visible in the terminal window.
    ``show_error()`` can still display a diagnostic dialog if the
    process fails to start or exits with a non-zero code.

    Attributes:
        command: The command list passed to ``Popen``.
        process: The ``subprocess.Popen`` instance (or ``None``).
        error: Exception object if the process could not be started.
    """

    def __init__(self, command, on_complete=None):
        super().__init__()
        self.setDaemon(True)
        self.command = command
        self.on_complete = on_complete
        self.process = None
        self.error = None

    # -- public API -------------------------------------------------------

    def run(self):
        """Execute the command."""
        try:
            self.process = subprocess.Popen(
                self.command,
            )
            self.process.wait()
        except FileNotFoundError:
            self.error = (
                f"Command not found: {self.command[0]}. "
                "Make sure the executable is in your PATH."
            )
        except Exception as e:
            self.error = e
        finally:
            if self.on_complete is not None:
                self.on_complete()

    def has_ok(self):
        """Return ``True`` if the process exited with code 0."""
        return self.has_process() and self.process.returncode == 0

    def has_code(self):
        """Return ``True`` if the process exited with a non-zero code."""
        return self.has_process() and self.process.returncode != 0

    def has_error(self):
        """Return ``True`` if the process could not be started."""
        return self.error is not None

    def has_process(self):
        """Return ``True`` if the process was started."""
        return self.process is not None

    def terminate(self):
        """Send SIGTERM, then SIGKILL if the process doesn't exit."""
        if self.has_process() and self.process.poll() is None:
            try:
                self.process.terminate()
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
            except Exception as e:
                print(f"Error terminating process: {e}")

    def show_error(self):
        """Display a diagnostic dialog with command, exit code, and output."""
        if platform.system() == "Windows":
            cmd_str = subprocess.list2cmdline(self.command)
        else:
            cmd_str = " ".join(shlex.quote(a) for a in self.command)

        if self.has_error():
            wx_show_error(textwrap.dedent(f"""
                Process failure:
                ---
                command:
                {cmd_str}
                ---
                error:
                {self.error}"""))
        elif self.has_code():
            wx_show_error(textwrap.dedent(f"""
                Program failure:
                ---
                command:
                {cmd_str}
                ---
                exit code: {self.process.returncode}
                (console output was shown in the terminal window)"""))