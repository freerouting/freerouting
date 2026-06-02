# ---------------------------------------------------------------------------
# process_utils.py — External process management
# ---------------------------------------------------------------------------
# Two classes for running Freerouting (or other external tools) as a
# subprocess:
#   * ``ProcessDialog`` — a wx dialog with a Terminate button that lets
#     the user cancel a long-running operation.
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


class ProcessDialog(wx.Dialog):
    """Modal dialog shown while Freerouting is running.

    Displays a message and a "Terminate" button.  The caller can close
    the dialog programmatically via ``terminate()`` (e.g. when the
    routing job finishes) or the user can press Terminate to cancel.

    Attributes:
        result_button: Modal result ID when the user presses Terminate.
        result_terminate: Modal result ID when ``terminate()`` is called.
    """

    def __init__(self, parent, text):
        self.result_button = wx.NewIdRef()
        self.result_terminate = wx.NewIdRef()

        super().__init__(
            parent, id=wx.ID_ANY, title=wx_caption,
            pos=wx.DefaultPosition, size=wx.Size(-1, -1),
            style=wx.CAPTION,
        )
        self.SetSizeHints(wx.DefaultSize, wx.DefaultSize)

        sizer = wx.BoxSizer(wx.VERTICAL)

        self.text = wx.StaticText(self, wx.ID_ANY, text, wx.DefaultPosition, wx.DefaultSize, 0)
        self.text.Wrap(-1)
        sizer.Add(self.text, 0, wx.ALIGN_CENTER_HORIZONTAL | wx.ALL, 10)

        self.line = wx.StaticLine(self, wx.ID_ANY, wx.DefaultPosition, wx.DefaultSize, wx.LI_HORIZONTAL)
        sizer.Add(self.line, 0, wx.EXPAND | wx.ALL, 5)

        self.bttn = wx.Button(self, wx.ID_ANY, "Terminate", wx.DefaultPosition, wx.DefaultSize, 0)
        self.bttn.SetDefault()
        sizer.Add(self.bttn, 0, wx.ALIGN_CENTER_HORIZONTAL | wx.ALL, 5)

        self.SetSizer(sizer)
        self.Layout()
        sizer.Fit(self)
        self.Centre(wx.BOTH)

        self.bttn.Bind(wx.EVT_BUTTON, self._on_click)

    # -- public API -------------------------------------------------------

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