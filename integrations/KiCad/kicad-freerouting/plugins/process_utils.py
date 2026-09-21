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
import re
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

    Displays a Unicode symbol next to a label.  The symbols are rendered as
    text so they look correct on both light and dark themes.  When set to
    ``STATUS_IN_PROGRESS``, a built-in timer animates the spinner by cycling
    through Unicode arrow characters.

    Attributes:
        label: The display label for this indicator.
    """

    # Spinner animation: 8 positions of a clockwise rotating arrow
    _SPIN_CHARS = [
        "\u2191",  # ↑ up arrow
        "\u2197",  # ↗ up-right arrow
        "\u2192",  # → right arrow
        "\u2198",  # ↘ down-right arrow
        "\u2193",  # ↓ down arrow
        "\u2199",  # ↙ down-left arrow
        "\u2190",  # ← left arrow
        "\u2196",  # ↖ up-left arrow
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
        font.SetPointSize(font.GetPointSize() + 4)
        font.SetWeight(wx.FONTWEIGHT_BOLD)
        self._symbol_label.SetFont(font)
        self._symbol_label.SetForegroundColour(colour)
        # Prevent parent dialog from overriding our foreground colour
        self._symbol_label.SetBackgroundColour(wx.NullColour)
        self._sizer.Add(self._symbol_label, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 8)

        self._label = wx.StaticText(self, wx.ID_ANY, label)
        self._label.SetForegroundColour(wx.SystemSettings.GetColour(wx.SYS_COLOUR_WINDOWTEXT))
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
            self._timer.Start(120)
        else:
            self._timer.Stop()
            self._symbol_label.SetLabel(_SYMBOLS[status])
            self._symbol_label.SetForegroundColour(_COLORS[status])
        # Force an immediate repaint so the change is visible right away,
        # even when the caller is about to do blocking work on the main thread.
        self._symbol_label.Refresh()
        self._symbol_label.Update()
        self.Refresh()
        self.Update()


class ProcessDialog(wx.Dialog):
    """Modal dialog shown while Freerouting is running.

    Displays status indicators for all stages of the routing pipeline
    and a "Terminate" button.  Indicators start in the *undetermined*
    state (gray circle) and are updated by the caller via the various
    ``set_*_status()`` methods.

    The order of indicators is:
      1. Detecting Java 25+ JRE
      2. Checking JSON/API bridge availability (JSON mode only)
      3. Starting up Freerouting API server
      4. Sending board to Freerouting
      5. Auto-router is running
      6. Receiving the results

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

        # Use system colours so the dialog works on both light and dark themes
        win_bg = wx.SystemSettings.GetColour(wx.SYS_COLOUR_WINDOW)
        win_fg = wx.SystemSettings.GetColour(wx.SYS_COLOUR_WINDOWTEXT)
        self.SetBackgroundColour(win_bg)
        self.SetForegroundColour(win_fg)

        sizer = wx.BoxSizer(wx.VERTICAL)
        indicator_sizer = wx.BoxSizer(wx.VERTICAL)

        # --- routing mode indicator ---
        self.mode_indicator = StatusIndicator(self, "Plugin Mode: DSN (legacy)", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.mode_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        # --- status indicators (vertical stack) ---
        self.java_indicator = StatusIndicator(self, "Detecting Java 25+ JRE", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.java_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        self.json_api_indicator = StatusIndicator(self, "Checking IPC / API bridge availability", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.json_api_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        self.api_indicator = StatusIndicator(self, "Starting up Freerouting API", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.api_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        self.sending_indicator = StatusIndicator(self, "Sending board to Freerouting", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.sending_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        self.routing_indicator = StatusIndicator(self, "Auto-router is running", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.routing_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        self.receiving_indicator = StatusIndicator(self, "Receiving the results", STATUS_UNDETERMINED)
        indicator_sizer.Add(self.receiving_indicator, 0, wx.ALIGN_LEFT | wx.LEFT | wx.TOP | wx.RIGHT, 10)

        sizer.Add(indicator_sizer, 0, wx.ALIGN_CENTER_HORIZONTAL | wx.TOP | wx.BOTTOM, 10)

        # --- message text / live progress detail ---
        self.message_label = wx.StaticText(
            self, wx.ID_ANY, text or "", wx.DefaultPosition, wx.DefaultSize, wx.ALIGN_CENTER_HORIZONTAL
        )
        self.message_label.SetForegroundColour(win_fg)
        self.message_label.Wrap(320)
        sizer.Add(self.message_label, 0, wx.ALIGN_CENTER_HORIZONTAL | wx.LEFT | wx.RIGHT | wx.TOP, 8)

        self.detail_label = wx.StaticText(
            self, wx.ID_ANY, "", wx.DefaultPosition, wx.DefaultSize, wx.ALIGN_CENTER_HORIZONTAL
        )
        self.detail_label.SetForegroundColour(wx.Colour(160, 160, 160))
        detail_font = self.detail_label.GetFont()
        detail_font.SetPointSize(max(detail_font.GetPointSize() - 1, 8))
        self.detail_label.SetFont(detail_font)
        self.detail_label.Wrap(320)
        sizer.Add(self.detail_label, 0, wx.ALIGN_CENTER_HORIZONTAL | wx.LEFT | wx.RIGHT | wx.BOTTOM, 8)

        # Alias for backwards compatibility
        self.text = self.message_label

        self.line = wx.StaticLine(self, wx.ID_ANY, wx.DefaultPosition, wx.DefaultSize, wx.LI_HORIZONTAL)
        sizer.Add(self.line, 0, wx.EXPAND | wx.ALL, 5)

        # --- terminate button ---
        self.bttn = wx.Button(self, wx.ID_ANY, "Terminate", wx.DefaultPosition, wx.DefaultSize, 0)
        self.bttn.SetDefault()
        sizer.Add(self.bttn, 0, wx.ALIGN_CENTER_HORIZONTAL | wx.ALL, 5)

        self.SetSizer(sizer)
        sizer.Fit(self)
        # Enforce a minimum size so all indicators and the button are visible
        min_size = self.GetBestSize()
        min_size.SetHeight(max(min_size.GetHeight(), 300))
        min_size.SetWidth(max(min_size.GetWidth(), 340))
        self.SetMinSize(min_size)
        self.SetSize(min_size)
        self.Centre(wx.BOTH)

        self.bttn.Bind(wx.EVT_BUTTON, self._on_click)

    def set_routing_mode_label(self, mode_str):
        """Update the routing mode indicator."""
        if hasattr(self, "mode_indicator") and self.mode_indicator:
            # Set text on the inner label
            if hasattr(self.mode_indicator, "_label"):
                self.mode_indicator._label.SetLabel(mode_str)
            self.mode_indicator.set_status(STATUS_UNDETERMINED)
            self.mode_indicator.Layout()
            self.Layout()

    def set_java_status(self, status):
        """Update the Java detection indicator."""
        self.java_indicator.set_status(status)

    def set_json_api_status(self, status):
        """Update the JSON/API or IPC bridge indicator."""
        self.json_api_indicator.set_status(status)

    def set_ipc_indicator_label(self, label):
        """Update the label of the second indicator (e.g. for IPC vs JSON)."""
        if hasattr(self.json_api_indicator, "_label"):
            self.json_api_indicator._label.SetLabel(label)
            self.json_api_indicator.Layout()

    def hide_json_api_indicator(self):
        """Hide the JSON/API indicator when running in DSN mode."""
        self.json_api_indicator.Hide()
        self.Layout()

    def set_api_status(self, status):
        """Update the 'Starting up Freerouting API' indicator."""
        self.api_indicator.set_status(status)

    def set_sending_status(self, status):
        """Update the 'Sending board to Freerouting' indicator."""
        self.sending_indicator.set_status(status)

    def set_routing_status(self, status):
        """Update the 'Auto-router is running' indicator."""
        self.routing_indicator.set_status(status)

    def set_receiving_status(self, status):
        """Update the 'Receiving the results' indicator."""
        self.receiving_indicator.set_status(status)

    def set_message(self, text, tooltip=None):
        """Update the informational message or heading."""
        if hasattr(self, "message_label") and self.message_label:
            try:
                self.message_label.SetLabel(text)
                if tooltip:
                    self.message_label.SetToolTip(tooltip)
                self.message_label.Wrap(320)
                self.Layout()
                self.Refresh()
                self.Update()
            except Exception:
                # Dialog or widget may have been hidden or destroyed during message dispatch
                pass

    def set_detail(self, text, tooltip=None):
        """Update the live progress detail line with a tooltip."""
        if hasattr(self, "detail_label") and self.detail_label:
            try:
                self.detail_label.SetLabel(text)
                if tooltip:
                    self.detail_label.SetToolTip(tooltip)
                elif text:
                    self.detail_label.SetToolTip(text)
                self.detail_label.Wrap(320)
                self.Layout()
                self.Refresh()
                self.Update()
            except Exception:
                # Detail label may have been destroyed during background stream update
                pass

    def terminate(self):
        """Close the dialog with the "programmatic termination" result."""
        try:
            if self and hasattr(self, "IsModal") and self.IsModal():
                self.EndModal(self.result_terminate)
        except Exception:
            # Dialog may have already been closed or destroyed
            pass

    def show_and_paint(self):
        """Show the dialog and force an immediate synchronous paint.

        Call this instead of ``Show()`` when the caller will immediately
        start a background thread and pump events.  Without this, the
        dialog window appears blank/white until the first ``ProcessPendingEvents``
        call returns, because the OS paint message is still queued.
        """
        self.Show()
        self.Raise()
        self.Update()    # flush pending layout synchronously
        self.Refresh()   # mark the window dirty
        # One round-trip through the event loop to dispatch the paint event
        app = wx.GetApp()
        if app:
            app.ProcessPendingEvents()

    # -- internal ---------------------------------------------------------

    def _on_click(self, event):
        try:
            if self and hasattr(self, "IsModal") and self.IsModal():
                self.EndModal(self.result_button)
        except Exception:
            # Modal loop may have already ended
            pass


def clean_log_line(raw_line):
    """Clean a Freerouting log line for display in the progress dialog.

    Strips timestamp, log level, and thread/context identifiers like
    '2026-09-17 16:02:01.320 INFO [91E51A\\6F17D8] ' and returns
    (cleaned_summary, full_line).
    """
    raw_stripped = raw_line.strip()
    cleaned = re.sub(
        r"^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d+)?\s+(?:INFO|WARN|WARNING|DEBUG|ERROR|TRACE)\s+(?:\[[^\]]*\]\s*)?",
        "",
        raw_stripped,
    ).strip()
    if not cleaned:
        cleaned = raw_stripped
    return cleaned, raw_stripped


class ProcessThread(threading.Thread):
    """Run an external command in a daemon thread.

    The subprocess inherits the parent's stdout/stderr, or optionally
    captures stdout to stream lines to an output handler.
    ``show_error()`` can still display a diagnostic dialog if the
    process fails to start or exits with a non-zero code.

    Attributes:
        command: The command list passed to ``Popen``.
        process: The ``subprocess.Popen`` instance (or ``None``).
        error: Exception object if the process could not be started.
    """

    def __init__(self, command, on_complete=None, output_handler=None):
        super().__init__()
        self.setDaemon(True)
        self.command = command
        self.on_complete = on_complete
        self.output_handler = output_handler
        self.process = None
        self.error = None
        self.cancelled = False
        self.last_output_lines = []
        self._lock = threading.Lock()

    # -- public API -------------------------------------------------------

    def run(self):
        """Execute the command."""
        try:
            popen_kwargs = {}
            if platform.system() == "Windows":
                # Suppress the empty console window on Windows across all modes
                popen_kwargs["creationflags"] = getattr(subprocess, "CREATE_NO_WINDOW", 0x08000000)
            else:
                popen_kwargs["start_new_session"] = True

            if self.output_handler is not None:
                popen_kwargs["stdout"] = subprocess.PIPE
                popen_kwargs["stderr"] = subprocess.STDOUT
                popen_kwargs["text"] = True
                popen_kwargs["bufsize"] = 1

            with self._lock:
                if self.cancelled:
                    return
                self.process = subprocess.Popen(
                    self.command,
                    **popen_kwargs
                )

            if self.output_handler is not None and self.process.stdout is not None:
                for line in iter(self.process.stdout.readline, ""):
                    if self.cancelled:
                        break
                    line_str = line.strip()
                    if line_str:
                        self.last_output_lines.append(line_str)
                        if len(self.last_output_lines) > 20:
                            self.last_output_lines.pop(0)
                        self.output_handler(line_str)
                try:
                    self.process.stdout.close()
                except (OSError, ValueError):
                    # Stream may already be closed or broken during process shutdown
                    pass

            self.process.wait()
        except FileNotFoundError:
            self.error = (
                f"Command not found: {self.command[0]}. "
                "Make sure the executable is in your PATH."
            )
        except Exception as e:
            if not self.cancelled:
                self.error = e
        finally:
            callback = None
            with self._lock:
                if not self.cancelled:
                    callback = self.on_complete
                    self.on_complete = None
            if callback is not None:
                callback()

    def has_ok(self):
        """Return ``True`` if the process exited with code 0 and was not cancelled."""
        return self.has_process() and self.process.returncode == 0 and not self.cancelled

    def has_code(self):
        """Return ``True`` if the process exited with a non-zero code (and wasn't cancelled)."""
        return self.has_process() and self.process.returncode != 0 and not self.cancelled

    def has_error(self):
        """Return ``True`` if the process could not be started (and wasn't cancelled)."""
        return self.error is not None and not self.cancelled

    def has_process(self):
        """Return ``True`` if the process was started."""
        return self.process is not None

    def terminate(self):
        """Send SIGTERM, then SIGKILL if the process doesn't exit."""
        proc = None
        with self._lock:
            self.cancelled = True
            self.on_complete = None
            proc = self.process

        if proc is not None and proc.poll() is None:
            try:
                proc.terminate()
                proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                try:
                    proc.kill()
                except OSError:
                    # Process may have already exited before kill was delivered
                    pass
            except Exception as e:
                print(f"Error terminating process: {e}")

    def show_error(self):
        """Display a diagnostic dialog with command, exit code, and output."""
        if self.cancelled:
            return
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
            diag_lines = "\n".join(self.last_output_lines[-10:]) if self.last_output_lines else ""
            if diag_lines:
                output_desc = f"output:\n{diag_lines}"
            elif self.output_handler is not None:
                output_desc = "(no output was captured)"
            else:
                output_desc = "(console output was shown in the terminal window)"

            wx_show_error(textwrap.dedent(f"""
                Program failure:
                ---
                command:
                {cmd_str}
                ---
                exit code: {self.process.returncode}
                {output_desc}"""))