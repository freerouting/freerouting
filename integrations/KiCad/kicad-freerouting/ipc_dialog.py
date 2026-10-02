"""
ipc_dialog.py — Modern Tkinter Progress Dialog for KiCad IPC Plugin
---------------------------------------------------------------------
Displays a clean, responsive modal progress dialog when Freerouting
is launched via KiCad 10+ IPC mode.

Features:
- Thread-safe queue architecture avoiding any Tcl/Tk lock contention.
- Stage-by-stage checklist with visual state indicators (pending, active, pass, fail).
- Animated progress bar and live status updates.
- Real-time display of the latest router console log line.
- Responsive Cancel/Stop button with clean job cancellation support.
- Open Log button to inspect plugin logs on error or completion.
- Safe headless fallback if Tkinter/display is unavailable.
"""

from __future__ import annotations

import logging
import os
import queue
import subprocess
import sys
import threading
from pathlib import Path
from typing import Callable, Optional

logger = logging.getLogger("freerouting.ipc_dialog")

# Stage definitions
STAGE_JAVA = 0
STAGE_EXTRACT = 1
STAGE_SERVER = 2
STAGE_ROUTE = 3
STAGE_COMMIT = 4

STAGE_LABELS = [
    "Check Java 25+ JRE",
    "Extract PCB geometry and design rules",
    "Connect to Freerouting API server",
    "Auto-route PCB connections",
    "Apply routed tracks and vias to KiCad",
]

STATE_PENDING = "pending"
STATE_ACTIVE = "active"
STATE_PASS = "pass"
STATE_FAIL = "fail"

ICONS = {
    STATE_PENDING: "○",
    STATE_ACTIVE: "▶",
    STATE_PASS: "✓",
    STATE_FAIL: "✗",
}

COLORS = {
    STATE_PENDING: "#888888",
    STATE_ACTIVE: "#0066cc",
    STATE_PASS: "#1b873f",
    STATE_FAIL: "#cc2200",
}


def clean_log_line(raw_line: str) -> tuple[str, str]:
    """Clean a Freerouting log line for display in the progress dialog.

    Strips timestamp, log level, and thread/context identifiers like
    '2026-09-17 16:02:01.320 INFO [91E51A\\6F17D8] ' and returns
    (cleaned_summary, raw_stripped).
    """
    import re
    raw_stripped = raw_line.strip()
    cleaned = re.sub(
        r"^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d+)?\s+(?:INFO|WARN|WARNING|DEBUG|ERROR|TRACE)\s+(?:\[[^\]]*\]\s*)?",
        "",
        raw_stripped,
    ).strip()
    cleaned = re.sub(r"\s+on board '[0-9a-fA-F]+'", "", cleaned).strip()
    if not cleaned:
        cleaned = raw_stripped
    return cleaned, raw_stripped


class LogTailer(threading.Thread):
    """Background thread that tails freerouting.log and dispatches progress lines."""

    def __init__(self, log_path: Path, job_id: str = None, on_log_line=None):
        super().__init__()
        self.daemon = True
        self.log_path = Path(log_path)
        self.job_prefix = f"[{job_id[:6].upper()}]" if job_id else ""
        self.on_log_line = on_log_line
        self._stop_event = threading.Event()
        self.seek_pos = 0
        if self.log_path.is_file():
            try:
                self.seek_pos = self.log_path.stat().st_size
            except Exception:
                self.seek_pos = 0

    def stop(self):
        self._stop_event.set()

    def run(self):
        import time as _time
        seek_pos = self.seek_pos
        last_dispatch = 0.0
        skip_keywords = (
            "GET v1/",
            "POST v1/",
            "PUT v1/",
            "DELETE v1/",
            "API key validation",
            "cid=",
            "HttpChannel",
            "org.eclipse.jetty",
        )
        while not self._stop_event.is_set():
            try:
                if self.log_path.is_file():
                    current_size = self.log_path.stat().st_size
                    if current_size < seek_pos:
                        seek_pos = 0
                    if current_size > seek_pos:
                        with open(self.log_path, "r", encoding="utf-8", errors="replace") as f:
                            f.seek(seek_pos)
                            while True:
                                line = f.readline()
                                if not line:
                                    break
                                if not line.endswith("\n"):
                                    break
                                seek_pos = f.tell()
                                stripped = line.strip()
                                if not stripped:
                                    continue
                                if any(k in stripped for k in skip_keywords):
                                    continue
                                is_milestone = (
                                    "Auto-routing" in stripped
                                    or "completed" in stripped.lower()
                                    or "finished with state" in stripped.lower()
                                    or (self.job_prefix and self.job_prefix in stripped)
                                )
                                is_progress = (
                                    "Pass #" in stripped
                                    or "Pass " in stripped
                                    or "items remaining" in stripped
                                )
                                now = _time.time()
                                if is_milestone or (is_progress and (now - last_dispatch >= 0.1)):
                                    if self.on_log_line:
                                        self.on_log_line(stripped)
                                        last_dispatch = now
            except Exception:
                # Ignore transient file read or decoding errors while log file is being written
                pass
            self._stop_event.wait(0.1)


class IpcRoutingDialog:
    """Tkinter-based progress dialog for the KiCad IPC routing workflow."""

    def __init__(self, title: str = "Freerouting PCB Autorouter"):
        self.title = title
        self.cancel_event = threading.Event()
        self.is_completed = False
        self.is_failed = False
        self.error_message: Optional[str] = None
        self._queue: queue.Queue = queue.Queue()
        self._root = None
        self._stage_labels = []
        self._stage_icons = []
        self._status_var = None
        self._detail_var = None
        self._log_line_var = None
        self._log_lines: list[str] = []
        self._progressbar = None
        self._cancel_btn = None
        self._log_btn = None
        self._icon_img = None

    def create_ui(self) -> bool:
        """Initializes the Tkinter window. Returns False if Tkinter is unavailable."""
        try:
            import tkinter as tk
            from tkinter import font as tkfont
            from tkinter import ttk
        except ImportError:
            logger.warning("Tkinter not available; running headlessly without progress dialog.")
            return False

        try:
            self._root = tk.Tk()
            self._root.title(self.title)
            self._root.geometry("540x460")
            self._root.minsize(500, 420)
            self._root.protocol("WM_DELETE_WINDOW", self._on_cancel)

            # Center window on screen
            self._root.update_idletasks()
            w = self._root.winfo_width()
            h = self._root.winfo_height()
            sw = self._root.winfo_screenwidth()
            sh = self._root.winfo_screenheight()
            x = max(0, (sw - w) // 2)
            y = max(0, (sh - h) // 2)
            self._root.geometry(f"+{x}+{y}")

            # Window icon
            here = Path(__file__).resolve().parent
            icon_path = here / "icon_24x24.png"
            if icon_path.is_file():
                try:
                    self._icon_img = tk.PhotoImage(file=str(icon_path))
                    self._root.iconphoto(False, self._icon_img)
                except Exception as e:
                    logger.debug(f"Could not set window icon: {e}")

            # Main container with padding
            main_frame = ttk.Frame(self._root, padding="16 12 16 12")
            main_frame.pack(fill=tk.BOTH, expand=True)

            # Header Frame (Title + Subtitle)
            header_frame = ttk.Frame(main_frame)
            header_frame.pack(fill=tk.X, pady=(0, 8))

            title_font = tkfont.Font(family="Segoe UI", size=13, weight="bold")
            subtitle_font = tkfont.Font(family="Segoe UI", size=9)
            console_font = tkfont.Font(family="Consolas", size=8)

            title_label = ttk.Label(
                header_frame,
                text="Freerouting",
                font=title_font,
            )
            title_label.pack(anchor=tk.W)

            subtitle_label = ttk.Label(
                header_frame,
                text="Advanced PCB Autorouter for KiCad",
                font=subtitle_font,
            )
            subtitle_label.pack(anchor=tk.W)

            sep = ttk.Separator(main_frame, orient=tk.HORIZONTAL)
            sep.pack(fill=tk.X, pady=(0, 8))

            # Stages Frame
            stages_frame = ttk.Frame(main_frame)
            stages_frame.pack(fill=tk.X, pady=(0, 8))

            bold_font = tkfont.Font(family="Segoe UI", size=9, weight="bold")
            normal_font = tkfont.Font(family="Segoe UI", size=9)

            for i, label_text in enumerate(STAGE_LABELS):
                row = ttk.Frame(stages_frame)
                row.pack(fill=tk.X, pady=2)

                icon_lbl = tk.Label(
                    row,
                    text=ICONS[STATE_PENDING],
                    fg=COLORS[STATE_PENDING],
                    font=bold_font,
                    width=2,
                    anchor="w",
                )
                icon_lbl.pack(side=tk.LEFT)
                self._stage_icons.append(icon_lbl)

                text_lbl = ttk.Label(
                    row,
                    text=label_text,
                    font=normal_font,
                )
                text_lbl.pack(side=tk.LEFT, fill=tk.X, expand=True)
                self._stage_labels.append(text_lbl)

            sep2 = ttk.Separator(main_frame, orient=tk.HORIZONTAL)
            sep2.pack(fill=tk.X, pady=(0, 8))

            # Progress Bar & Status Info
            self._status_var = tk.StringVar(value="Initializing...")
            self._detail_var = tk.StringVar(value="")
            self._log_line_var = tk.StringVar(value="")

            status_lbl = ttk.Label(
                main_frame,
                textvariable=self._status_var,
                font=bold_font,
            )
            status_lbl.pack(anchor=tk.W, pady=(0, 2))

            detail_lbl = ttk.Label(
                main_frame,
                textvariable=self._detail_var,
                font=subtitle_font,
            )
            detail_lbl.pack(anchor=tk.W, pady=(0, 4))

            self._progressbar = ttk.Progressbar(main_frame, mode="indeterminate")
            self._progressbar.pack(fill=tk.X, pady=(0, 6))
            self._progressbar.start(15)

            # Live Router Console Lines (last 3 lines)
            console_lbl = ttk.Label(
                main_frame,
                textvariable=self._log_line_var,
                font=console_font,
                wraplength=490,
                foreground="#333333",
                justify=tk.LEFT,
            )
            console_lbl.pack(anchor=tk.W, fill=tk.X, pady=(0, 10))

            # Button Bar
            btn_frame = ttk.Frame(main_frame)
            btn_frame.pack(fill=tk.X, side=tk.BOTTOM)

            self._log_btn = ttk.Button(
                btn_frame,
                text="Open Log",
                command=self._open_log_file,
            )
            self._log_btn.pack(side=tk.LEFT)

            self._cancel_btn = ttk.Button(
                btn_frame,
                text="Cancel",
                command=self._on_cancel,
            )
            self._cancel_btn.pack(side=tk.RIGHT)

            # Start queue polling loop on the main GUI thread
            self._poll_queue()

            return True

        except Exception as e:
            logger.warning(f"Failed to create GUI progress dialog: {e}", exc_info=True)
            self._root = None
            return False

    # ------------------------------------------------------------------
    # Thread-Safe Queue Message Dispatcher (Runs on Main Thread)
    # ------------------------------------------------------------------

    def _poll_queue(self) -> None:
        """Processes pending UI update messages from the worker thread."""
        if not self._root:
            return

        try:
            for _ in range(50):
                try:
                    msg_type, args = self._queue.get_nowait()
                except queue.Empty:
                    break

                try:
                    if msg_type == "stage":
                        idx, state = args
                        if 0 <= idx < len(self._stage_icons):
                            self._stage_icons[idx].config(
                                text=ICONS.get(state, "○"),
                                fg=COLORS.get(state, "#888888"),
                            )
                    elif msg_type == "status":
                        message, detail = args
                        if self._status_var:
                            self._status_var.set(message)
                        if self._detail_var and detail:
                            self._detail_var.set(detail)
                    elif msg_type == "log_line":
                        line = args[0]
                        if line:
                            if len(line) > 130:
                                line = line[:127] + "..."
                            self._log_lines.append(line)
                            if len(self._log_lines) > 3:
                                self._log_lines = self._log_lines[-3:]
                            if self._log_line_var:
                                self._log_line_var.set("\n".join(self._log_lines))
                    elif msg_type == "success":
                        summary = args[0]
                        if self._progressbar:
                            self._progressbar.stop()
                            self._progressbar.config(mode="determinate", value=100)
                        if self._status_var:
                            self._status_var.set("✓ Routing Completed Successfully")
                        if self._detail_var:
                            self._detail_var.set(summary)
                        if self._cancel_btn:
                            self._cancel_btn.config(text="Close", command=self._close, state="normal")
                    elif msg_type == "error":
                        err = args[0]
                        if self._progressbar:
                            self._progressbar.stop()
                        if self._status_var:
                            self._status_var.set("✗ Routing Failed")
                        if self._detail_var:
                            self._detail_var.set(err)
                        if self._cancel_btn:
                            self._cancel_btn.config(text="Close", command=self._close, state="normal")
                except Exception as msg_err:
                    logger.debug(f"Error handling UI queue message {msg_type}: {msg_err}")
        except Exception as e:
            logger.debug(f"Error in UI queue polling: {e}")
        finally:
            if self._root:
                try:
                    self._root.after(40, self._poll_queue)
                except Exception:
                    # Root window may have been closed or destroyed during shutdown
                    pass

    # ------------------------------------------------------------------
    # Worker Thread API (100% thread-safe: pushes to queue only)
    # ------------------------------------------------------------------

    def set_stage(self, stage_idx: int, state: str) -> None:
        """Updates the status icon and font of a pipeline stage."""
        if not self._root:
            logger.info(f"[Stage {stage_idx}] {state.upper()}: {STAGE_LABELS[stage_idx]}")
            return
        self._queue.put(("stage", (stage_idx, state)))

    def set_status(self, message: str, detail: str = "") -> None:
        """Updates the dynamic status message and detail line."""
        if not self._root:
            logger.info(f"{message} - {detail}" if detail else message)
            return
        self._queue.put(("status", (message, detail)))

    def set_log_line(self, line: str) -> None:
        """Updates the real-time console log line displayed in the dialog."""
        if not self._root:
            return
        self._queue.put(("log_line", (line,)))

    def complete_success(self, summary_msg: str) -> None:
        """Marks the dialog as successfully completed."""
        self.is_completed = True
        if not self._root:
            logger.info(f"SUCCESS: {summary_msg}")
            return
        self._queue.put(("success", (summary_msg,)))

    def complete_error(self, err_msg: str) -> None:
        """Marks the dialog as failed with an error message."""
        self.is_failed = True
        self.error_message = err_msg
        if not self._root:
            logger.error(f"ERROR: {err_msg}")
            return
        self._queue.put(("error", (err_msg,)))

    # ------------------------------------------------------------------
    # User Actions
    # ------------------------------------------------------------------

    def _on_cancel(self) -> None:
        """Triggered when user clicks Cancel or closes window during operation."""
        if self.is_completed or self.is_failed:
            self._close()
            return

        self.cancel_event.set()
        self.set_status("Cancelling routing job...", "Please wait...")
        if self._cancel_btn:
            self._cancel_btn.config(state="disabled")

    def _close(self) -> None:
        """Closes the dialog."""
        if self._root:
            try:
                self._root.destroy()
            except Exception:
                # Root window might already be destroyed
                pass
            self._root = None

    def _open_log_file(self) -> None:
        """Opens the Freerouting plugin log file in default text editor."""
        from config import LOG_DIR
        log_file = LOG_DIR / "freerouting_ipc_plugin.log"
        if not log_file.is_file():
            return

        try:
            if sys.platform == "win32":
                os.startfile(str(log_file))
            elif sys.platform == "darwin":
                subprocess.Popen(["open", str(log_file)])
            else:
                subprocess.Popen(["xdg-open", str(log_file)])
        except Exception as e:
            logger.warning(f"Could not open log file: {e}")

    # ------------------------------------------------------------------
    # Execution Runner
    # ------------------------------------------------------------------

    def run(self, worker_target: Callable[[IpcRoutingDialog], None]) -> None:
        """Runs the dialog on main thread while executing worker in background."""
        has_gui = self.create_ui()

        worker_thread = threading.Thread(
            target=worker_target,
            args=(self,),
            name="FreeroutingWorker",
            daemon=True,
        )
        worker_thread.start()

        if has_gui and self._root:
            try:
                self._root.mainloop()
            except KeyboardInterrupt:
                self.cancel_event.set()
        else:
            worker_thread.join()
