#!/usr/bin/env python3
"""Run comprehensive headless benchmarks across PCBench in-repo fixtures and store in benchmarks.json."""

from __future__ import annotations

import argparse
import atexit
import collections
import hashlib
import json
import os
import platform
import queue
import re
import subprocess
import sys
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

# Reconfigure stdout/stderr to UTF-8 with fallback
try:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

# Enable ANSI colors on Windows Console
if sys.platform == "win32":
    try:
        import ctypes
        kernel32 = ctypes.windll.kernel32
        handle = kernel32.GetStdHandle(-11)
        mode = ctypes.c_ulong()
        if kernel32.GetConsoleMode(handle, ctypes.byref(mode)):
            kernel32.SetConsoleMode(handle, mode.value | 0x0004)
    except Exception:
        pass

# Color definitions
C_RESET = "\033[0m"
C_BOLD = "\033[1m"
C_DIM = "\033[2m"
C_CYAN = "\033[36m"
C_BCYAN = "\033[1;36m"
C_GREEN = "\033[32m"
C_BGREEN = "\033[1;32m"
C_YELLOW = "\033[33m"
C_BYELLOW = "\033[1;33m"
C_BLUE = "\033[34m"
C_BBLUE = "\033[1;34m"
C_MAGENTA = "\033[35m"
C_BMAGENTA = "\033[1;35m"
C_RED = "\033[31m"
C_BRED = "\033[1;31m"
C_WHITE = "\033[37m"
C_BWHITE = "\033[1;37m"
C_GRAY = "\033[90m"


class ProcessLock:
    """Cross-platform single-instance file lock to prevent concurrent benchmark runs."""

    def __init__(self, lock_path: Path):
        self.lock_path = lock_path
        self.file_handle = None

    def acquire(self) -> bool:
        self.lock_path.parent.mkdir(parents=True, exist_ok=True)
        try:
            if sys.platform == "win32":
                import msvcrt
                self.file_handle = open(self.lock_path, "a+")
                self.file_handle.seek(0)
                msvcrt.locking(self.file_handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                self.file_handle = open(self.lock_path, "a+")
                fcntl.flock(self.file_handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)

            self.file_handle.seek(0)
            self.file_handle.truncate()
            self.file_handle.write(f"pid={os.getpid()}\nstarted={datetime.now(timezone.utc).isoformat()}\n")
            self.file_handle.flush()
            return True
        except (BlockingIOError, PermissionError, OSError):
            if self.file_handle:
                try:
                    self.file_handle.close()
                except Exception:
                    pass
                self.file_handle = None
            return False

    def release(self) -> None:
        if self.file_handle:
            try:
                if sys.platform == "win32":
                    import msvcrt
                    self.file_handle.seek(0)
                    msvcrt.locking(self.file_handle.fileno(), msvcrt.LK_UNLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(self.file_handle.fileno(), fcntl.LOCK_UN)
                self.file_handle.close()
            except Exception:
                pass
            self.file_handle = None
            try:
                self.lock_path.unlink(missing_ok=True)
            except Exception:
                pass


def compute_sha256(path: Path) -> str:
    """Compute sha256 of a file."""
    if not path.exists():
        return ""
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


_TRACE_MIN = re.compile(r"\(trace_min\s+([0-9]*\.?[0-9]+)\)")


def board_min_track_width_um(board_dir: Path) -> float | None:
    """Return the minimum track width of the board's KiCad design rules, in micrometers.

    The hand routing of a board may use any width down to this value, but the Specctra export only
    carries the net class widths. Reads ``(setup (trace_min ...))`` from ``processed.kicad_pcb``,
    which all PCBench fixtures have in the KiCad 5 format. Returns None when it is missing.
    """
    pcb_path = board_dir / "processed.kicad_pcb"
    try:
        text = pcb_path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None
    match = _TRACE_MIN.search(text)
    if not match:
        return None
    width_um = round(float(match.group(1)) * 1000.0, 3)
    return width_um if width_um > 0 else None


def format_fixture_name(name: str, limit: int = 37) -> str:
    """Keep short fixture names. Longer names keep the first and last 16 characters."""
    text = name or ""
    if len(text) <= limit:
        return text
    return f"{text[:16]}...{text[-16:]}"


def format_result_timing(seconds: float, suffix: str = "") -> str:
    """Right-align the minutes so result rows share one time column."""
    timing = f"({seconds / 60:7.1f}m"
    if suffix:
        timing += f", {suffix}"
    return timing + ")"


def format_recent_result(
    completed: int,
    total: int,
    label: str,
    timing: str,
    name: str,
    tier_place: tuple[int, int, str] | None = None,
) -> str:
    """Progress, padded outcome, timing, then the full fixture name.

    ``tier_place`` is ``(index, tier size, tier)`` in run order, so the 12th
    board of 48 tier C boards reads ``  12/ 48 of T-C``.
    """
    head = f"{completed:4d}/{total:4d}"
    if tier_place is not None:
        index, tier_total, tier = tier_place
        head = f"{head} {index:3d}/{tier_total:3d} of T-{tier}"
    return f"[{head}] {label:<8} {timing} {name}"


def assign_tier_places(boards: list[dict[str, Any]]) -> dict[str, tuple[int, int, str]]:
    """Map each board id to its 1-based index inside its tier.

    ``boards`` must already be in benchmark order. The denominator counts the
    selected boards of that tier, including ones skipped because they are
    already cached.
    """
    totals: dict[str, int] = {}
    order: list[tuple[str, str]] = []
    for board in boards:
        board_id = board.get("board_id")
        if not isinstance(board_id, str) or not board_id:
            continue
        tier = str(board.get("tier") or "?")
        totals[tier] = totals.get(tier, 0) + 1
        order.append((board_id, tier))
    seen: dict[str, int] = {}
    places: dict[str, tuple[int, int, str]] = {}
    for board_id, tier in order:
        seen[tier] = seen.get(tier, 0) + 1
        places[board_id] = (seen[tier], totals[tier], tier)
    return places


def format_worker_tag(wid: int, tier: str | None = None) -> str:
    """Worker index, plus the fixture tier while a board is running."""
    if tier:
        return f"[W-{wid:02d} T-{tier}]"
    return f"[W-{wid:02d}]"


def colorize_status_line(msg: str) -> str:
    """Highlight status keywords in color."""
    msg = re.sub(r"\bCLEAN\b", f"{C_BGREEN}CLEAN{C_RESET}", msg)
    msg = re.sub(r"\bROUTED\b", f"{C_BYELLOW}ROUTED{C_RESET}", msg)
    msg = re.sub(r"\bUNROUTED\b", f"{C_BBLUE}UNROUTED{C_RESET}", msg)
    msg = re.sub(r"\bTIMEOUT\b", f"{C_BRED}TIMEOUT{C_RESET}", msg)
    msg = re.sub(r"\bERROR\b", f"{C_BRED}ERROR{C_RESET}", msg)
    return msg


def parse_phases_from_text(text: str) -> dict[str, Any]:
    phases = {
        "fanout": {"duration_seconds": None, "passes_completed": 0, "log_found": False},
        "autorouter": {"duration_seconds": None, "passes_completed": 0},
        "optimizer": {"duration_seconds": None, "passes_completed": 0},
    }
    # Fanout
    m_fan = re.search(r"Fanout (?:phase|stage) completed:.*completed in ([\d\.]+) seconds.*escaped pins:\s*(\d+)/(\d+)\s*\(([\d\.]+)%\)", text)
    if m_fan:
        fan_passes = len(re.findall(r"Fanout pass #\d+", text))
        phases["fanout"] = {
            "duration_seconds": float(m_fan.group(1)),
            "escaped_pin_count": int(m_fan.group(2)),
            "smd_pin_count": int(m_fan.group(3)),
            "escape_rate_pct": float(m_fan.group(4)),
            "passes_completed": fan_passes,
            "log_found": True,
        }

    # Autorouter
    m_auto = re.search(r"Auto-rout\w+ (?:phase|stage) completed:.*completed in ([\d\.]+) seconds", text)
    auto_passes = re.findall(r"Auto-rout\w+ pass #(\d+)", text)
    auto_pass_count = max([int(p) for p in auto_passes], default=0)
    if m_auto:
        phases["autorouter"] = {
            "duration_seconds": float(m_auto.group(1)),
            "passes_completed": auto_pass_count,
        }
    elif auto_pass_count > 0:
        phases["autorouter"] = {
            "passes_completed": auto_pass_count,
        }

    # Optimizer
    m_opt = re.search(r"Optimi\w+ (?:phase|stage) completed:.*completed in ([\d\.]+) seconds", text)
    opt_passes = re.findall(r"Optimi\w+ pass #(\d+)", text)
    opt_pass_count = max([int(p) for p in opt_passes], default=0)
    if m_opt:
        phases["optimizer"] = {
            "duration_seconds": float(m_opt.group(1)),
            "passes_completed": opt_pass_count,
        }
    elif opt_pass_count > 0:
        phases["optimizer"] = {
            "passes_completed": opt_pass_count,
        }

    return phases


def route_single_board(
    jar_path: Path,
    board_dir: Path,
    output_dir: Path,
    log_dir: Path,
    user_data_dir: Path,
    timeout_budget: str,
    version_label: str,
    git_sha: str,
    jar_sha256: str,
    jar_size: int,
    tier: str,
    wid: int,
    worker_status: dict[int, dict[str, Any]],
    active_procs: dict[int, subprocess.Popen],
    status_lock: threading.Lock,
    cancel_event: threading.Event,
    force_kill_event: threading.Event,
    *,
    reference_seconds: float = 0.0,
    queued_ids: list[str] | None = None,
    neck_width_um: float | None = None,
) -> dict[str, Any] | None:
    """Execute Freerouting on a single PCBench board and return a benchmark run record."""
    if cancel_event.is_set() or force_kill_event.is_set():
        return None

    board_id = board_dir.name
    dsn_path = board_dir / "unrouted.dsn"
    ses_path = output_dir / f"{board_id}--unrouted--{version_label}.ses"
    manifest_path = output_dir / f"{board_id}--unrouted--{version_label}-result.json"
    log_path = log_dir / f"{board_id}--unrouted--{version_label}.log"

    t0 = time.perf_counter()

    with status_lock:
        worker_status[wid] = {
            "board": board_id,
            "tier": tier or "?",
            "start": t0,
            "last_line": "Starting autorouter process...",
            "active": True,
            "reference_seconds": reference_seconds,
        }
        if queued_ids is not None:
            try:
                queued_ids.remove(board_id)
            except ValueError:
                pass

    # Isolate per-worker user data directory to prevent concurrent write contention on freerouting.json
    worker_user_data_dir = user_data_dir / f"worker-{wid}"
    worker_user_data_dir.mkdir(parents=True, exist_ok=True)

    # Timeout calculation
    parts = timeout_budget.split(":")
    if len(parts) == 3:
        timeout_sec = int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
    else:
        timeout_sec = 60

    cmd = [
        "java",
        "-Dsun.stdout.buffered=false",
        "-Xmx4g",
        # First heap failure ends the JVM. A swallowed OutOfMemoryError otherwise leaves the
        # process alive and the worker slot busy until the job timeout.
        "-XX:+ExitOnOutOfMemoryError",
        "-Dfreerouting.log.file.level=INFO",
        "-Dfreerouting.log.console.level=INFO",
        "-jar",
        str(jar_path),
        f"--user_data_path={worker_user_data_dir.resolve()}",
        "--gui.enabled=false",
        "--api_server.enabled=false",
        "--mcp_server.enabled=false",
        "--router.max_threads=1",
        # Separate from router.max_threads. One optimizer thread avoids a board copy per core.
        "--router.optimizer.max_threads=1",
        "-de",
        str(dsn_path),
        "-do",
        str(ses_path),
        "-dct",
        "0",
        f"--router.result_json={manifest_path}",
        "--router.fanout.enabled=true",
        f"--router.job_timeout={timeout_budget}",
        f"--logging.file.location={log_path}",
    ]
    if neck_width_um:
        cmd.append(f"--router.neck_width_um={neck_width_um:g}")

    exit_code = 0
    crashed = False
    timed_out = False
    fatal_seen = threading.Event()
    stdout_lines: list[str] = []

    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
        with status_lock:
            if force_kill_event.is_set():
                try:
                    proc.kill()
                except (ProcessLookupError, OSError):
                    pass
            else:
                active_procs[wid] = proc

        def stream_reader():
            if proc.stdout:
                for line in iter(proc.stdout.readline, ""):
                    if not line:
                        break
                    stdout_lines.append(line)
                    clean = line.strip()
                    if clean:
                        # Ignore async background version-checker lines so they don't overwrite router progress
                        if "New version available" in clean or "Failed to check for new version" in clean:
                            continue
                        short = clean
                        for pfx in (" INFO   ", " DEBUG  ", " WARN   ", " ERROR  "):
                            if pfx in clean:
                                short = clean.split(pfx, 1)[1]
                                break
                        if fatal_process_output(short) or fatal_process_output(clean):
                            fatal_seen.set()
                        with status_lock:
                            worker_status[wid]["last_line"] = short[:90]
                            worker_status[wid]["last_line_time"] = time.perf_counter()

        reader_thread = threading.Thread(target=stream_reader, daemon=True)
        reader_thread.start()

        # Allow a grace period for post-timeout routing wrap-up, DRC calculation, SES export, and JVM shutdown
        hard_timeout = timeout_sec + max(120, int(timeout_sec * 0.10))
        deadline = time.monotonic() + hard_timeout
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                timed_out = True
                try:
                    proc.kill()
                except Exception:
                    # The process may already have exited.
                    pass
                exit_code = -1
                stdout_lines.append("\nTIMEOUT: Process exceeded hard timeout budget\n")
                break
            # After a fatal line, give the JVM a moment to exit on its own, then kill it.
            wait_for = 3.0 if fatal_seen.is_set() else min(1.0, remaining)
            try:
                exit_code = proc.wait(timeout=min(wait_for, remaining))
                break
            except subprocess.TimeoutExpired:
                if not fatal_seen.is_set():
                    continue
                crashed = True
                try:
                    proc.kill()
                except Exception:
                    # The process may already have exited.
                    pass
                try:
                    exit_code = proc.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    exit_code = -1
                stdout_lines.append(
                    "\nFATAL: Process stayed alive after an uncaught error and was terminated\n"
                )
                break

        reader_thread.join(timeout=2.0)
    except Exception as e:
        crashed = True
        exit_code = -2
        stdout_lines.append(str(e))
    finally:
        with status_lock:
            active_procs.pop(wid, None)
            if wid in worker_status:
                worker_status[wid]["active"] = False
                worker_status[wid]["last_line"] = "Idle"

    if force_kill_event.is_set():
        return None

    stdout_text = "".join(stdout_lines)
    oom_in_log = log_tail_contains(log_path, "OutOfMemoryError")
    # A Task Manager kill, a non-zero JVM exit, or an OOM in stdout/log is a failed run
    # even when an earlier manifest still describes a fully routed board.
    if not timed_out and (
        fatal_seen.is_set()
        or fatal_process_output(stdout_text)
        or oom_in_log
        or exit_code not in (0, None)
    ):
        crashed = True
    wall_time = round(time.perf_counter() - t0, 2)

    # Clean up any stray SES files created in working directory if an older binary split on '+'
    stray_suffix = f"{board_id.split('+')[-1]}--unrouted--{version_label}.ses"
    stray_root_ses = Path(stray_suffix)
    if stray_root_ses.exists():
        try:
            if not ses_path.exists():
                stray_root_ses.replace(ses_path)
            else:
                stray_root_ses.unlink(missing_ok=True)
        except OSError:
            # Best-effort relocation or deletion of stray SES file
            pass

    # Clean up truncated prefix file in output_dir (e.g. mechkeys_MF68) if it exists
    if "+" in board_id:
        truncated_prefix = output_dir / board_id.split("+")[0]
        if truncated_prefix.is_file():
            try:
                truncated_prefix.unlink(missing_ok=True)
            except OSError:
                # Best-effort deletion of truncated prefix artifact
                pass

    # Read normalized metadata from fixture
    meta_path = board_dir / "metadata.normalized.json"
    fixture_meta: dict[str, Any] = {}
    if meta_path.exists():
        try:
            fixture_meta = json.loads(meta_path.read_text(encoding="utf-8"))
        except Exception:
            pass

    # Read result manifest if present
    manifest_data: dict[str, Any] = {}
    if manifest_path.exists():
        try:
            manifest_data = json.loads(manifest_path.read_text(encoding="utf-8"))
        except Exception:
            pass

    stats = manifest_data.get("board_statistics", {})
    connections = stats.get("connections", {})
    unrouted_count = connections.get("incomplete_count", None)
    violations_info = stats.get("clearance_violations", {})
    violations_count = violations_info.get("total_count", None)
    router_introduced_count = violations_info.get("router_introduced_count", None)
    pre_existing_count = violations_info.get("pre_existing_count", None)
    unfixable_count = violations_info.get("unfixable_count", None)
    min_viol_um = violations_info.get("min_violation_um", violations_info.get("min_violation_mm", None))
    max_viol_um = violations_info.get("max_violation_um", violations_info.get("max_violation_mm", None))
    avg_viol_um = violations_info.get("avg_violation_um", violations_info.get("avg_violation_mm", None))
    score_val = manifest_data.get("normalized_score", None)
    final_state = manifest_data.get("final_state", "FAILED" if (crashed or timed_out) else "COMPLETED")
    if crashed and not timed_out:
        final_state = "FAILED"
    elif timed_out and final_state == "COMPLETED":
        final_state = "TIMED_OUT"
    phases = manifest_data.get("phases", {})
    if not phases.get("autorouter", {}).get("duration_seconds") and stdout_text:
        phases = parse_phases_from_text(stdout_text)

    # For versions like 2.4.1 where board_statistics is at the root of result manifest
    # instead of nested in phases.autorouter.after, normalize phases structure
    if stats and not phases.get("autorouter", {}).get("after", {}).get("board_statistics"):
        if "autorouter" not in phases:
            phases["autorouter"] = {}
        phases["autorouter"]["after"] = {
            "board_statistics": stats,
            "score": score_val,
            "router_score": score_val,
            "score_source": "current",
        }
        if "before" not in phases["autorouter"]:
            phases["autorouter"]["before"] = {
                "board_statistics": None,
                "score": None,
                "score_source": "current",
            }
    resources = manifest_data.get("resource_usage", {})
    cpu_score = manifest_data.get("cpu_score")
    if cpu_score is not None:
        try:
            cpu_score = int(cpu_score)
            if cpu_score > 10_000:
                cpu_score = int(round(cpu_score / 1000.0))
        except (TypeError, ValueError):
            cpu_score = None
    if cpu_score is None:
        m_hw = re.search(r"Hardware:\s+\d+\s+CPU cores,\s+(\d+)\s+CPU score", stdout_text)
        if m_hw:
            cpu_score = int(m_hw.group(1))
            if cpu_score > 10_000:
                cpu_score = int(round(cpu_score / 1000.0))

    if not manifest_data and stdout_text:
        # Fallback to parsing metrics from stdout for versions without result_json (e.g. 2.2.4, 2.3.0)
        m_comp = re.findall(
            r"Auto-rout\w+ (?:session|stage) completed:.*final score:\s*([\d\.]+)(?:\s*\((.*?)\))?.*using ([\d\.]+) total CPU seconds,\s*([\d\.]+) GB total allocated,\s*and\s*([\d\.]+) MB peak heap usage",
            stdout_text,
        )
        if m_comp:
            score_str, extra, cpu_s, alloc_gb, peak_mb = m_comp[-1]
            try:
                score_val = float(score_str)
            except Exception:
                pass
            try:
                resources = {
                    "cpu_time": float(cpu_s),
                    "max_memory": float(alloc_gb),
                    "peak_memory": float(peak_mb),
                }
            except Exception:
                pass
            unrouted_count = 0
            violations_count = 0
            if extra:
                m_u = re.search(r"(\d+)\s+unrouted", extra)
                if m_u:
                    unrouted_count = int(m_u.group(1))
                m_v = re.search(r"(\d+)\s+violations?", extra)
                if m_v:
                    violations_count = int(m_v.group(1))
            elif score_val is not None and score_val < 990:
                unrouted_count = 1
        else:
            m_pass = re.findall(r"Auto-rout\w+ pass #\d+.*(?:score of|score)\s*([\d\.]+)(?:\s*\((.*?)\))?", stdout_text)
            if m_pass:
                score_str, extra = m_pass[-1]
                try:
                    score_val = float(score_str)
                except Exception:
                    pass
                unrouted_count = 0
                violations_count = 0
                if extra:
                    m_u = re.search(r"(\d+)\s+unrouted", extra)
                    if m_u:
                        unrouted_count = int(m_u.group(1))
                    m_v = re.search(r"(\d+)\s+violations?", extra)
                    if m_v:
                        violations_count = int(m_v.group(1))
                elif score_val is not None and score_val < 990:
                    unrouted_count = 1

    b_board = fixture_meta.get("board", {})
    b_cad = fixture_meta.get("cad", {})
    tier = fixture_meta.get("tier", "B")

    cache_key = f"{board_id}--unrouted--{version_label}--{git_sha}"

    run_record = {
        "cache_key": cache_key,
        "run_at": datetime.now(timezone.utc).isoformat(),
        "system": {
            "os": platform.platform(),
            "cpu_name": platform.processor(),
            "cpu_logical_cores": os.cpu_count() or 4,
            "cpu_score": cpu_score,
            "cpu_score_effective": cpu_score,
        },
        "binary": {
            "filename": jar_path.name,
            "version_label": version_label,
            "sha256": jar_sha256,
            "size_bytes": jar_size,
            "git_sha": git_sha,
        },
        "fixture": {
            "filename": "unrouted.dsn",
            "group": "PCBench",
            "relative_path": f"PCBench/{board_id}/unrouted.dsn",
            "size_bytes": dsn_path.stat().st_size if dsn_path.exists() else 0,
            "sha256": compute_sha256(dsn_path),
            "host_cad": b_cad.get("host", "KiCad's Pcbnew"),
            "host_version": b_cad.get("version", ""),
            "layer_count": b_board.get("layers", 2),
            "net_count": b_board.get("nets", 0),
            "component_count": b_board.get("components", 0),
            "board_width_mm": b_board.get("dimensions_mm", {}).get("width", 0.0),
            "board_height_mm": b_board.get("dimensions_mm", {}).get("height", 0.0),
            "board_area_cm2": b_board.get("area_cm2", 0.0),
            "tier": tier,
        },
        "phases": {
            "fanout": phases.get("fanout", {}),
            "autorouter": phases.get("autorouter", {}),
            "optimizer": phases.get("optimizer", {}),
        },
        "quality": {
            "total_nets": b_board.get("nets", 0),
            "unrouted_connections": unrouted_count,
            "clearance_violations": violations_count,
            "router_introduced_violations": router_introduced_count,
            "pre_existing_violations": pre_existing_count,
            "unfixable_clearance_violations": unfixable_count,
            "min_violation_um": min_viol_um,
            "max_violation_um": max_viol_um,
            "avg_violation_um": avg_viol_um,
            "quality_score": score_val,
            "wall_clock_seconds": wall_time,
            "cpu_seconds": resources.get("cpu_time", 0.0),
            "peak_heap_mb": resources.get("peak_memory", 0.0),
            "total_allocated_gb": resources.get("max_memory", 0.0),
        },
        "exit": {
            "code": exit_code,
            "state": final_state,
            "crashed": crashed,
            "oom_detected": ("OutOfMemoryError" in stdout_text) or oom_in_log,
            "timed_out": timed_out,
        },
        "log_analysis": {
            "warn_count": stdout_text.count("WARN"),
            "error_count": stdout_text.count("ERROR"),
            "exceptions": stdout_text.count("Exception"),
        },
        "log_file": str(log_path.relative_to(log_dir.parent.parent)),
        "result_json": str(manifest_path.relative_to(output_dir.parent.parent)) if manifest_path.exists() else None,
        "output_file": str(ses_path.relative_to(output_dir.parent.parent)) if ses_path.exists() else None,
        "job_timeout": timeout_budget,
        "neck_width_um": neck_width_um,
    }

    return run_record


_render_lock = threading.Lock()
# A tier scale needs this much reference time before it replaces the prior of 1.
MINIMUM_SCALE_REFERENCE_SECONDS = 30 * 60
_VERSION_LABEL = re.compile(
    r"^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:\.(\d+))?(?:[-.]?([A-Za-z]+)(\d+)?)?"
)
_FATAL_OUTPUT_MARKERS = (
    "OutOfMemoryError",
    "StackOverflowError",
    "Exception in thread",
    "terminated abnormally",
    "terminated due to a fatal error",
    "terminated due to an error",
)


def fatal_process_output(text: str) -> bool:
    """True when child-process output means the run cannot continue."""
    return any(marker in text for marker in _FATAL_OUTPUT_MARKERS)


def log_tail_contains(path: Path, marker: str, tail_bytes: int = 262144) -> bool:
    """True when the end of a log file contains marker. A missing file is false."""
    try:
        size = path.stat().st_size
        with path.open("rb") as handle:
            if size > tail_bytes:
                handle.seek(size - tail_bytes)
            text = handle.read().decode("utf-8", errors="replace")
    except OSError:
        return False
    return marker in text


def version_sort_key(label: str) -> tuple:
    """Order version labels so 2.6.0 ranks above 2.6.0-RC6, which ranks above 2.6.0-RC3."""
    match = _VERSION_LABEL.match((label or "").strip())
    if not match:
        return (0, 0, 0, 0, 0, "", 0)
    major, minor, patch, build, tag, tag_number = match.groups()
    return (
        int(major or 0),
        int(minor or 0),
        int(patch or 0),
        int(build or 0),
        1 if not tag else 0,
        (tag or "").casefold(),
        int(tag_number or 0),
    )


def _parse_run_at(value: str) -> datetime:
    if not value:
        return datetime.min.replace(tzinfo=timezone.utc)
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError:
        return datetime.min.replace(tzinfo=timezone.utc)
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed


def fixture_id_from_run(run: dict[str, Any]) -> str | None:
    """Board id from a corpus run record."""
    relative = str((run.get("fixture") or {}).get("relative_path") or "").replace("\\", "/")
    parts = [part for part in relative.split("/") if part]
    if len(parts) >= 2 and parts[0] == "PCBench":
        return parts[1]
    cache_key = str(run.get("cache_key") or "")
    marker = "--unrouted--"
    if marker in cache_key:
        return cache_key.split(marker, 1)[0] or None
    return None


def run_is_failure(run: dict[str, Any]) -> bool:
    """A crashed, timed-out, or non-zero run is not a duration reference."""
    exit_info = run.get("exit") or {}
    if exit_info.get("crashed") or exit_info.get("oom_detected") or exit_info.get("timed_out"):
        return True
    code = exit_info.get("code")
    if code not in (0, None):
        return True
    if exit_info.get("state") in {"FAILED", "TERMINATED", "INVALID"}:
        return True
    wall = (run.get("quality") or {}).get("wall_clock_seconds")
    return not isinstance(wall, (int, float)) or wall <= 0


def latest_successful_reference_seconds(runs: Any) -> dict[str, float]:
    """Wall-clock seconds of the newest successful version, per fixture.

    Failed runs are ignored. When the newest recorded version has only failures,
    the next older version that completed is used.
    """
    best: dict[str, tuple[tuple, datetime, float]] = {}
    for run in runs:
        if not isinstance(run, dict) or run_is_failure(run):
            continue
        board_id = fixture_id_from_run(run)
        if not board_id:
            continue
        version = str((run.get("binary") or {}).get("version_label") or "")
        run_at = _parse_run_at(str(run.get("run_at") or ""))
        seconds = float((run.get("quality") or {}).get("wall_clock_seconds") or 0.0)
        rank = (version_sort_key(version), run_at)
        current = best.get(board_id)
        if current is None or rank > (current[0], current[1]):
            best[board_id] = (version_sort_key(version), run_at, seconds)
    return {board_id: item[2] for board_id, item in best.items()}


def format_duration(seconds: float) -> str:
    """Format a duration as H:MM:SS. Hours grow past 24."""
    whole = max(0, int(round(seconds)))
    hours, remainder = divmod(whole, 3600)
    minutes, secs = divmod(remainder, 60)
    return f"{hours:02d}:{minutes:02d}:{secs:02d}"


def format_elapsed_clock(seconds: float) -> str:
    """Minutes and seconds for the worker clock. Minutes grow past 59."""
    whole = max(0, int(seconds))
    return f"{whole // 60:02d}:{whole % 60:02d}"


def format_worker_progress(elapsed_seconds: float, expected_seconds: float | None) -> str:
    """``[22:51]`` or ``[22:51 ~42m]`` when the estimate rounds to at least one minute."""
    clock = format_elapsed_clock(elapsed_seconds)
    if expected_seconds is None or expected_seconds <= 0:
        return f"[{clock}]"
    minutes = int(expected_seconds / 60 + 0.5)
    if minutes <= 0:
        return f"[{clock}]"
    return f"[{clock} ~{minutes}m]"


def expected_fixture_seconds(
    reference_seconds: float,
    tier: str,
    scales: dict[str, float],
) -> float | None:
    """Seconds this fixture should take on the current build.

    The reference is the latest successful run of this fixture on the newest
    recorded version, or the catalog time when no such run exists. The tier
    scale is the current build's speed on boards of the same difficulty, and
    it stays at 1 until this run has enough evidence. The product is the same
    estimate the suite ETA uses for that board.
    """
    if reference_seconds <= 0:
        return None
    return reference_seconds * scale_for(scales, tier)


def duration_scales(completed: list[tuple[str, float, float]]) -> dict[str, float]:
    """Return per-tier speed ratios and a global ratio under the empty-string key.

    A ratio is actual seconds divided by reference seconds. It stays at 1 until
    the compared reference time reaches MINIMUM_SCALE_REFERENCE_SECONDS.
    """
    totals: dict[str, list[float]] = {}
    for tier, reference, actual in completed:
        if reference <= 0:
            continue
        for key in (tier, ""):
            bucket = totals.setdefault(key, [0.0, 0.0])
            bucket[0] += actual
            bucket[1] += reference
    scales = {
        key: actual / reference
        for key, (actual, reference) in totals.items()
        if reference >= MINIMUM_SCALE_REFERENCE_SECONDS
    }
    scales.setdefault("", 1.0)
    return scales


def scale_for(scales: dict[str, float], tier: str) -> float:
    return scales.get(tier, scales.get("", 1.0))


def estimate_remaining_seconds(
    queued: list[tuple[str, float]],
    running: list[tuple[str, float, float]],
    completed: list[tuple[str, float, float]],
    workers: int,
) -> tuple[float, float]:
    """Estimate suite seconds left, and the reference-weighted speed ratio.

    Running boards keep only their remaining expected time. Waiting boards go
    to the worker that becomes free first. Expected time is the fixture's
    latest successful duration times the tier ratio.
    """
    scales = duration_scales(completed)
    slots = max(1, workers)
    free = [0.0] * slots
    extras: list[float] = []
    weighted = 0.0
    weight = 0.0
    for index, (tier, reference, elapsed) in enumerate(running):
        expected = max(0.0, reference) * scale_for(scales, tier)
        remaining = max(0.0, expected - max(0.0, elapsed))
        if index < slots:
            free[index] = remaining
        else:
            extras.append(remaining)
        weighted += max(0.0, reference) * scale_for(scales, tier)
        weight += max(0.0, reference)
    for tier, reference in queued:
        expected = max(0.0, reference) * scale_for(scales, tier)
        slot = min(range(slots), key=lambda item: free[item])
        free[slot] += expected
        weighted += expected
        weight += max(0.0, reference)
    eta = max([*free, *extras], default=0.0)
    shown_scale = weighted / weight if weight else scales.get("", 1.0)
    return eta, shown_scale




def render_dashboard(
    completed: int,
    total: int,
    t_start: float,
    worker_status: dict[int, dict[str, Any]],
    recent_messages: collections.deque[str],
    status_lock: threading.Lock,
    version_label: str = "",
    cancel_requested: bool = False,
    force_kill_requested: bool = False,
    keyboard_supported: bool = True,
    in_place: bool = True,
    last_saved_at: float | None = None,
    target_workers: int | None = None,
    board_reference: dict[str, tuple[str, float]] | None = None,
    queued_ids: list[str] | None = None,
    completed_samples: list[tuple[str, float, float]] | None = None,
) -> None:
    """Print updated multi-worker dashboard with live logs and recent history."""
    now = time.perf_counter()
    queued: list[tuple[str, float]] = []
    running: list[tuple[str, float, float]] = []
    samples: list[tuple[str, float, float]] = []
    with status_lock:
        if queued_ids is not None:
            for board_id in queued_ids:
                queued.append((board_reference or {}).get(board_id, ("?", 0.0)))
        if completed_samples is not None:
            samples = list(completed_samples)
        for info in worker_status.values():
            if not info.get("active"):
                continue
            running.append(
                (
                    str(info.get("tier") or "?"),
                    float(info.get("reference_seconds") or 0.0),
                    max(0.0, now - float(info.get("start", now))),
                )
            )
    slots = target_workers if target_workers is not None else max(1, len(running))
    remaining_secs, _shown_scale = estimate_remaining_seconds(queued, running, samples, slots)
    eta_str = format_duration(remaining_secs)
    scales = duration_scales(samples)
    pct = (completed / total) * 100.0 if total > 0 else 0.0

    ver_part = f" | {C_BWHITE}Version:{C_RESET} {C_BCYAN}{version_label}{C_RESET}" if version_label else ""

    if force_kill_requested:
        status_hint = f" | {C_BRED}[TERMINATING]{C_RESET}"
    elif cancel_requested:
        status_hint = f" | {C_BYELLOW}[DRAINING - ESC / Q to force kill]{C_RESET}"
    elif not keyboard_supported:
        status_hint = ""
    else:
        status_hint = f" | {C_DIM}[+ / - adjust workers | ESC / Q to stop]{C_RESET}"

    worker_count_str = f"{len(worker_status)}"
    if target_workers is not None and len(worker_status) != target_workers:
        worker_count_str = f"{len(worker_status)} (target {target_workers})"

    lines = []
    lines.append(f"{C_BCYAN}{'=' * 155}{C_RESET}")
    lines.append(
        f"{C_BWHITE}PCBench Benchmark:{C_RESET} {C_BYELLOW}{completed}/{total}{C_RESET} "
        f"({C_BGREEN}{pct:5.1f}%{C_RESET}){ver_part} | "
        f"{C_BWHITE}ETA:{C_RESET} {C_CYAN}{eta_str}{C_RESET} | "
        f"{C_BWHITE}Workers:{C_RESET} {C_BMAGENTA}{worker_count_str}{C_RESET} | "
        f"{C_BWHITE}Saved:{C_RESET} {C_BYELLOW}{format_saved_age(last_saved_at, time.perf_counter())}{C_RESET}"
        f"{status_hint}"
    )
    lines.append(f"{C_CYAN}{'-' * 155}{C_RESET}")

    now = time.perf_counter()
    with status_lock:
        for wid in sorted(worker_status.keys()):
            info = worker_status[wid]
            if info.get("active"):
                run_sec = int(now - info.get("start", now))
                board = format_fixture_name(str(info.get("board", "unknown")))
                tier = str(info.get("tier") or "?")
                expected = expected_fixture_seconds(
                    float(info.get("reference_seconds") or 0.0),
                    tier,
                    scales,
                )
                progress = format_worker_progress(run_sec, expected)
                last = info.get("last_line", "")
                last_time = info.get("last_line_time")
                idle_sec = int(now - last_time) if last_time else run_sec
                idle_str = f" ({idle_sec // 60}m ago)" if idle_sec >= 60 and last else ""
                lines.append(
                    f"  {C_BMAGENTA}{format_worker_tag(wid, tier)}{C_RESET} "
                    f"{C_BWHITE}{board:<37}{C_RESET} "
                    f"{C_BYELLOW}{progress}{C_RESET} -> {C_GRAY}{last}{idle_str}{C_RESET}"
                )
            else:
                lines.append(f"  {C_BMAGENTA}{format_worker_tag(wid)}{C_RESET} {C_DIM}Idle{C_RESET}")

    lines.append(f"{C_CYAN}{'-' * 155}{C_RESET}")
    lines.append(f"{C_BWHITE}Latest results:{C_RESET}")
    if recent_messages:
        for msg in recent_messages:
            lines.append(f"  {colorize_status_line(msg)}")
    else:
        lines.append(f"  {C_DIM}(None completed yet){C_RESET}")
    lines.append(f"{C_BCYAN}{'=' * 155}{C_RESET}")

    output_text = "\n".join(lines)
    with _render_lock:
        try:
            if in_place and sys.stdout.isatty():
                sys.stdout.write("\033[H\033[J" + output_text + "\n")
                sys.stdout.flush()
            else:
                print(output_text, flush=True)
        except Exception:
            try:
                safe_text = output_text.encode("ascii", errors="replace").decode("ascii")
                print(safe_text, flush=True)
            except Exception:
                pass


DEFAULT_TIER_ORDER = ("A", "D", "C", "B")
_KNOWN_TIERS = frozenset({"A", "B", "C", "D"})
CORPUS_SAVE_INTERVAL_SECONDS = 180.0


def corpus_save_is_due(
    last_saved_at: float | None,
    now: float,
    *,
    force: bool = False,
    interval_seconds: float = CORPUS_SAVE_INTERVAL_SECONDS,
) -> bool:
    """Return whether benchmarks.json may be rewritten now."""
    if force or last_saved_at is None:
        return True
    return (now - last_saved_at) >= interval_seconds


def format_saved_age(last_saved_at: float | None, now: float) -> str:
    """Toolbar age of the last successful benchmarks.json swap."""
    if last_saved_at is None:
        return "never"
    return f"{max(0.0, (now - last_saved_at) / 60):.1f}m ago"


def should_stop_main_loop(
    active_workers: int, queue_empty: bool, cancel_requested: bool, force_kill: bool
) -> bool:
    """Stop when the run is finished, or when a graceful stop has drained the workers."""
    if force_kill:
        return True
    if active_workers > 0:
        return False
    return queue_empty or cancel_requested


def parse_tier_order(raw: str) -> list[str]:
    """Return the tier run order.

    An empty value or ``All`` uses A, then D, then C, then B. A comma-separated
    list runs those tiers in the given order. ``--tier C,A`` runs C before A.
    """
    tokens = [part.strip().upper() for part in (raw or "").split(",") if part.strip()]
    if not tokens or tokens == ["ALL"]:
        return list(DEFAULT_TIER_ORDER)
    order: list[str] = []
    for token in tokens:
        if token == "ALL":
            raise ValueError("'All' cannot be combined with named tiers")
        if token not in _KNOWN_TIERS:
            raise ValueError(f"Unknown tier '{token}'. Use A, B, C, D, or a comma-separated list.")
        if token not in order:
            order.append(token)
    return order


def replace_file_retrying(source: Path, destination: Path, attempts: int = 12) -> None:
    """Replace destination with source, retrying Windows sharing violations.

    WinError 5 (Access is denied) is raised when another process has the
    destination open without delete sharing. The routed record must already be
    durable elsewhere before this is called.
    """
    delay_seconds = 0.25
    last_error: PermissionError | None = None
    for attempt in range(attempts):
        try:
            source.replace(destination)
            return
        except PermissionError as error:
            last_error = error
            if attempt == attempts - 1:
                break
            time.sleep(delay_seconds)
            delay_seconds = min(delay_seconds + 0.25, 1.0)
    assert last_error is not None
    raise last_error


def append_jsonl_record(path: Path, record: dict[str, Any], attempts: int = 8) -> None:
    """Append one JSON object. The last line for a cache_key is the record to keep."""
    line = json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
    delay_seconds = 0.05
    last_error: PermissionError | None = None
    for attempt in range(attempts):
        try:
            with path.open("a", encoding="utf-8", newline="\n") as handle:
                handle.write(line)
                handle.flush()
                os.fsync(handle.fileno())
            return
        except PermissionError as error:
            last_error = error
            if attempt == attempts - 1:
                break
            time.sleep(delay_seconds)
            delay_seconds = min(delay_seconds * 2, 0.5)
    assert last_error is not None
    raise last_error


def read_jsonl_records(path: Path) -> dict[str, dict[str, Any]]:
    """Return the latest record for each cache_key. Broken lines are skipped."""
    records: dict[str, dict[str, Any]] = {}
    if not path.exists():
        return records
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError:
            continue
        if not isinstance(record, dict):
            continue
        key = record.get("cache_key")
        if isinstance(key, str) and key:
            records[key] = record
    return records


def default_worker_count() -> int:
    """Three quarters of the logical processors, at least one."""
    processors = os.cpu_count() or 1
    return max(1, round(processors * 0.75))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", default="scripts/benchmark/binaries/freerouting-current.jar", type=Path)
    parser.add_argument("--fixtures-dir", default="scripts/benchmark/fixtures/PCBench", type=Path)
    parser.add_argument(
        "--tier",
        default="All",
        help="Tier filter and run order. Default All runs A, D, C, then B. "
        "A comma-separated list runs those tiers in that order, for example C,A.",
    )
    parser.add_argument(
        "--workers",
        default=default_worker_count(),
        type=int,
        help="Parallel routing workers (default: round(logical processors * 0.75))",
    )
    parser.add_argument("--max-boards", default=0, type=int)
    parser.add_argument("--version-label", default="v2.3.1-SNAPSHOT")
    parser.add_argument("--filter", default="", help="Filter fixtures by substring/pattern")
    parser.add_argument("--force", action="store_true", help="Force rerun even if already in benchmarks.json")
    parser.add_argument("--timeout", default="06:00:00", help="Job timeout budget for all tiers (default: 06:00:00)")
    parser.add_argument(
        "--neck-width-from-board",
        action="store_true",
        help="Pass each board's KiCad minimum track width as --router.neck_width_um. "
        "Changes the routing: use a separate --version-label.",
    )
    args = parser.parse_args()
    try:
        tier_order = parse_tier_order(args.tier)
    except ValueError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1

    fixtures_dir = args.fixtures_dir
    catalog_path = fixtures_dir / "catalog.json"
    jar_path = args.jar
    output_dir = Path("scripts/benchmark/outputs")
    log_dir = Path("scripts/benchmark/logs")
    results_dir = Path("scripts/benchmark/results")
    user_data_dir = Path("scripts/benchmark/.user_data")
    benchmarks_json = results_dir / "benchmarks.json"
    journal_file = results_dir / "run-journal.jsonl"

    output_dir.mkdir(parents=True, exist_ok=True)
    log_dir.mkdir(parents=True, exist_ok=True)
    results_dir.mkdir(parents=True, exist_ok=True)
    user_data_dir.mkdir(parents=True, exist_ok=True)

    # Enforce single active benchmark instance
    lock_file = results_dir / ".benchmark.lock"
    proc_lock = ProcessLock(lock_file)
    if not proc_lock.acquire():
        existing_info = ""
        try:
            if lock_file.exists():
                existing_info = f" ({lock_file.read_text(encoding='utf-8').strip()})"
        except Exception:
            pass
        print(
            f"\n{C_BRED}Error: Another instance of the benchmark is already running!{C_RESET}\n"
            f"{C_YELLOW}Lock file: {lock_file}{existing_info}{C_RESET}\n"
            f"Please wait for the active run to finish or terminate it before starting a new one.\n",
            file=sys.stderr,
            flush=True,
        )
        return 1

    # Clean up any stray SES files created in root working directory
    for stray_ses in Path(".").glob("*--unrouted--*.ses"):
        try:
            stray_ses.unlink(missing_ok=True)
        except OSError:
            # Best-effort cleanup of stray SES files at startup
            pass

    atexit.register(proc_lock.release)

    if not catalog_path.exists() or not jar_path.exists():
        print("Missing catalog.json or freerouting JAR", file=sys.stderr)
        return 1

    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    boards = catalog.get("boards", [])
    tier_rank = {tier: index for index, tier in enumerate(tier_order)}
    boards = [b for b in boards if b.get("tier") in tier_rank]
    # tier_index is fixed from the 2.6.0-RC2 runtimes. Odd indexes are the slower
    # boards and even indexes are the quicker ones, so the average stays stable.
    boards.sort(key=lambda board: (tier_rank[board.get("tier")], board.get("tier_index", 10**9)))

    if args.filter:
        filter_terms = [f.strip().lower() for f in args.filter.split(",") if f.strip()]
        boards = [b for b in boards if any(term in b.get("board_id", "").lower() for term in filter_terms)]

    if args.max_boards > 0:
        boards = boards[: args.max_boards]

    tier_places = assign_tier_places(boards)

    jar_sha256 = compute_sha256(jar_path)
    jar_size = jar_path.stat().st_size

    # Get current git SHA
    try:
        git_sha = subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], text=True).strip()
    except Exception:
        git_sha = "unknown"

    # Load existing benchmarks.json
    bench_data: dict[str, Any] = {"runs": []}
    if benchmarks_json.exists():
        try:
            bench_data = json.loads(benchmarks_json.read_text(encoding="utf-8"))
        except Exception:
            pass

    existing_runs = {r.get("cache_key"): r for r in bench_data.get("runs", []) if r.get("cache_key")}
    # A locked benchmarks.json can drop a completed swap. The journal line is the
    # record that must win until a later swap succeeds.
    for cache_key, journal_run in read_jsonl_records(journal_file).items():
        existing_runs[cache_key] = journal_run

    tasks = []
    board_reference: dict[str, tuple[str, float]] = {}
    board_neck_width: dict[str, float | None] = {}
    queued_ids: list[str] = []
    already_completed = 0
    historical_reference = latest_successful_reference_seconds(existing_runs.values())
    for b in boards:
        b_id = b.get("board_id")
        b_dir = fixtures_dir / b_id
        budget = args.timeout
        board_tier = b.get("tier") or "?"
        reference_seconds = float(historical_reference.get(b_id) or b.get("reference_seconds") or 0.0)

        if not args.force:
            is_already_run = any(
                r.get("fixture", {}).get("relative_path") == f"PCBench/{b_id}/unrouted.dsn"
                and r.get("binary", {}).get("version_label") == args.version_label
                and (r.get("quality", {}).get("unrouted_connections") is not None or r.get("quality", {}).get("final_unrouted") is not None)
                and (not run_is_failure(r) or (r.get("exit") or {}).get("timed_out"))
                for r in existing_runs.values()
            )
            if is_already_run:
                already_completed += 1
                continue

        if (b_dir / "unrouted.dsn").exists():
            tasks.append(
                (
                    jar_path,
                    b_dir,
                    output_dir,
                    log_dir,
                    user_data_dir,
                    budget,
                    args.version_label,
                    git_sha,
                    jar_sha256,
                    jar_size,
                    board_tier,
                )
            )
            board_reference[b_id] = (board_tier, reference_seconds)
            if args.neck_width_from_board:
                board_neck_width[b_id] = board_min_track_width_um(b_dir)
            queued_ids.append(b_id)

    print(
        f"Starting PCBench Corpus Benchmark ({len(boards)} total, {already_completed} already cached, "
        f"{len(tasks)} remaining to run, Tier={",".join(tier_order)}, Workers={args.workers})...\n",
        flush=True,
    )

    def save_benchmarks_atomic(attempts: int = 12) -> bool:
        """Write benchmarks.json. Return False when Windows still denies the swap.

        Callers append the run to run-journal.jsonl first. A False return leaves
        that line in place so the next successful swap, or the next process,
        merges the same record.
        """
        bench_data["runs"] = list(existing_runs.values())
        bench_data["total_runs"] = len(bench_data["runs"])
        bench_data["generated_at"] = datetime.now(timezone.utc).isoformat()
        tmp_file = benchmarks_json.with_name(
            f".{benchmarks_json.name}.{os.getpid()}.{time.time_ns()}.tmp"
        )
        try:
            tmp_file.write_text(json.dumps(bench_data, indent=2), encoding="utf-8")
            replace_file_retrying(tmp_file, benchmarks_json, attempts=attempts)
            return True
        except PermissionError:
            return False
        finally:
            try:
                tmp_file.unlink(missing_ok=True)
            except OSError:
                # Replace already consumed the temp file, or another worker removed it.
                pass

    last_corpus_save_at: list[float | None] = [None]

    def clear_run_journal() -> None:
        if not journal_file.exists():
            return
        with journal_file.open("w", encoding="utf-8", newline="\n") as handle:
            handle.flush()
            os.fsync(handle.fileno())

    def save_corpus(*, force: bool = False, attempts: int = 12) -> str:
        """Rewrite benchmarks.json at most once per interval, or immediately when forced.

        Returns saved, throttled, or locked. A forced save runs on process exit,
        including Ctrl+C and ESC/Q, even when the interval has not elapsed.
        """
        if not corpus_save_is_due(last_corpus_save_at[0], time.perf_counter(), force=force):
            return "throttled"
        if not save_benchmarks_atomic(attempts=attempts):
            return "locked"
        last_corpus_save_at[0] = time.perf_counter()
        try:
            clear_run_journal()
        except PermissionError:
            # The corpus file already has the runs. The leftover journal lines
            # match them and are merged again on the next start.
            pass
        return "saved"

    def persist_run(record: dict[str, Any]) -> str:
        """Append the journal line, then swap the corpus file when it is due.

        Returns saved, throttled, locked, or unsaved. The fixture result is
        already in existing_runs. A throttled return still leaves the journal line.
        """
        journal_saved = False
        try:
            append_jsonl_record(journal_file, record)
            journal_saved = True
        except PermissionError:
            journal_saved = False
        outcome = save_corpus()
        if outcome != "saved" and not journal_saved:
            return "unsaved"
        return outcome

    if not tasks:
        if save_benchmarks_atomic(attempts=40):
            try:
                clear_run_journal()
            except PermissionError:
                pass
            print(
                "All requested boards are already benchmarked in benchmarks.json! Regenerating reports...",
                flush=True,
            )
            subprocess.run(
                ["powershell", "-ExecutionPolicy", "Bypass", "-File", "scripts/benchmark/run-benchmarks.ps1", "-ReportOnly"],
                check=False,
            )
        else:
            print(
                "All requested boards are already recorded, but benchmarks.json stayed locked. "
                "Results remain in run-journal.jsonl; the report was not regenerated.",
                flush=True,
            )
        return 0

    # Worker tracking structures
    task_queue: queue.Queue[Any] = queue.Queue()
    for task in tasks:
        task_queue.put(task)

    result_queue: queue.Queue[tuple[str, dict[str, Any] | None, Exception | None]] = queue.Queue()

    worker_status: dict[int, dict[str, Any]] = {}
    active_procs: dict[int, subprocess.Popen] = {}
    status_lock = threading.Lock()
    recent_messages: collections.deque[str] = collections.deque(maxlen=10)

    completed = 0
    clean_count = 0
    routed_viol_count = 0
    unrouted_count = 0
    timeout_count = 0
    error_count = 0
    completed_samples: list[tuple[str, float, float]] = []
    t_start = time.perf_counter()

    tracker = {"completed": 0}
    stop_refresh = threading.Event()
    cancel_event = threading.Event()
    force_kill_event = threading.Event()
    keyboard_supported = sys.platform == "win32" or (hasattr(sys, "stdin") and sys.stdin.isatty())

    target_workers = max(1, args.workers)
    active_worker_threads = 0
    worker_lock = threading.Lock()
    worker_threads: list[threading.Thread] = []

    def trigger_render() -> None:
        render_dashboard(
            tracker["completed"],
            len(tasks),
            t_start,
            worker_status,
            recent_messages,
            status_lock,
            version_label=args.version_label,
            cancel_requested=cancel_event.is_set(),
            force_kill_requested=force_kill_event.is_set(),
            keyboard_supported=keyboard_supported,
            in_place=True,
            last_saved_at=last_corpus_save_at[0],
            target_workers=target_workers,
            board_reference=board_reference,
            queued_ids=queued_ids,
            completed_samples=completed_samples,
        )

    def worker_thread_func(wid: int) -> None:
        nonlocal active_worker_threads
        while not cancel_event.is_set() and not force_kill_event.is_set():
            with worker_lock:
                if active_worker_threads > target_workers:
                    active_worker_threads -= 1
                    with status_lock:
                        worker_status.pop(wid, None)
                    return

            try:
                task = task_queue.get(timeout=0.2)
            except queue.Empty:
                with worker_lock:
                    active_worker_threads -= 1
                    with status_lock:
                        worker_status.pop(wid, None)
                return

            if cancel_event.is_set() or force_kill_event.is_set():
                task_queue.task_done()
                with worker_lock:
                    active_worker_threads -= 1
                    with status_lock:
                        worker_status.pop(wid, None)
                return

            board_name = task[1].name
            rec = None
            err = None
            try:
                rec = route_single_board(
                    *task,
                    wid,
                    worker_status,
                    active_procs,
                    status_lock,
                    cancel_event,
                    force_kill_event,
                    reference_seconds=board_reference.get(board_name, ("?", 0.0))[1],
                    queued_ids=queued_ids,
                    neck_width_um=board_neck_width.get(board_name),
                )
            except Exception as exc:
                err = exc
            finally:
                task_queue.task_done()

            result_queue.put((board_name, rec, err))

        with worker_lock:
            active_worker_threads -= 1
            with status_lock:
                worker_status.pop(wid, None)

    def spawn_worker_if_needed() -> None:
        nonlocal active_worker_threads
        # Must be called while holding worker_lock
        while (
            active_worker_threads < target_workers
            and not task_queue.empty()
            and not cancel_event.is_set()
            and not force_kill_event.is_set()
        ):
            with status_lock:
                used_wids = set(worker_status.keys())
                wid = 1
                while wid in used_wids:
                    wid += 1
                worker_status[wid] = {
                    "board": "Idle",
                    "start": 0.0,
                    "last_line": "Idle",
                    "active": False,
                }
            active_worker_threads += 1
            t = threading.Thread(target=worker_thread_func, args=(wid,), daemon=True)
            worker_threads.append(t)
            t.start()

    def on_increase_workers() -> None:
        nonlocal target_workers
        with worker_lock:
            if target_workers >= 32:
                with status_lock:
                    recent_messages.append(
                        f"{C_BYELLOW}[WORKERS] Maximum worker limit (32) reached.{C_RESET}"
                    )
                trigger_render()
                return

            target_workers += 1
            new_target = target_workers
            spawn_worker_if_needed()

        with status_lock:
            if task_queue.empty():
                recent_messages.append(
                    f"{C_BYELLOW}[WORKERS] Target workers increased to {new_target} (no remaining tasks in queue).{C_RESET}"
                )
            else:
                recent_messages.append(
                    f"{C_BGREEN}[WORKERS] Target workers increased to {new_target}. Opened a new slot.{C_RESET}"
                )
        trigger_render()

    def on_decrease_workers() -> None:
        nonlocal target_workers
        with worker_lock:
            if target_workers <= 1:
                with status_lock:
                    recent_messages.append(
                        f"{C_BYELLOW}[WORKERS] Minimum worker count (1) reached.{C_RESET}"
                    )
                trigger_render()
                return

            target_workers -= 1
            new_target = target_workers
            with status_lock:
                active_cnt = sum(1 for w in worker_status.values() if w.get("active"))

        with status_lock:
            if active_cnt > new_target:
                recent_messages.append(
                    f"{C_BYELLOW}[WORKERS] Target workers decreased to {new_target}. Gracefully waiting for running tasks to finish...{C_RESET}"
                )
            else:
                recent_messages.append(
                    f"{C_BYELLOW}[WORKERS] Target workers decreased to {new_target}.{C_RESET}"
                )
        trigger_render()

    def on_cancel_key() -> None:
        if not cancel_event.is_set():
            cancel_event.set()
            with status_lock:
                active_cnt = sum(1 for w in worker_status.values() if w.get("active"))
                recent_messages.append(
                    f"{C_BYELLOW}[GRACEFUL STOP] Draining {active_cnt} active worker(s). Press ESC/Q again to force kill.{C_RESET}"
                )
        else:
            force_kill_event.set()
            with status_lock:
                recent_messages.append(
                    f"{C_BRED}[FORCE KILL] Terminating all active processes immediately...{C_RESET}"
                )
                for p in list(active_procs.values()):
                    try:
                        p.kill()
                    except (ProcessLookupError, OSError):
                        pass
        trigger_render()

    def background_refresh() -> None:
        while not stop_refresh.is_set() and not force_kill_event.is_set():
            stop_refresh.wait(1.0 if cancel_event.is_set() else 5.0)
            if not stop_refresh.is_set() and not force_kill_event.is_set():
                trigger_render()

    def keyboard_listener() -> None:
        if sys.platform == "win32":
            try:
                import msvcrt
                while not stop_refresh.is_set() and not force_kill_event.is_set():
                    if msvcrt.kbhit():
                        ch = msvcrt.getch()
                        if ch in (b"\x1b", b"q", b"Q"):
                            on_cancel_key()
                            if force_kill_event.is_set():
                                break
                        elif ch in (b"+", b"="):
                            on_increase_workers()
                        elif ch in (b"-", b"_"):
                            on_decrease_workers()
                        elif ch in (b"\x00", b"\xe0"):
                            try:
                                msvcrt.getch()
                            except Exception:
                                pass
                    time.sleep(0.05)
            except Exception:
                pass
        elif hasattr(sys, "stdin") and sys.stdin.isatty():
            try:
                import select
                import termios
                import tty
                fd = sys.stdin.fileno()
                old_settings = termios.tcgetattr(fd)
                try:
                    tty.setcbreak(fd)
                    while not stop_refresh.is_set() and not force_kill_event.is_set():
                        rlist, _, _ = select.select([sys.stdin], [], [], 0.05)
                        if rlist:
                            ch = sys.stdin.read(1)
                            if ch in ("\x1b", "q", "Q"):
                                on_cancel_key()
                                if force_kill_event.is_set():
                                    break
                            elif ch in ("+", "="):
                                on_increase_workers()
                            elif ch in ("-", "_"):
                                on_decrease_workers()
                finally:
                    termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
            except Exception:
                pass

    with worker_lock:
        spawn_worker_if_needed()

    refresh_thread = threading.Thread(target=background_refresh, daemon=True)
    refresh_thread.start()

    key_thread = threading.Thread(target=keyboard_listener, daemon=True)
    key_thread.start()

    corpus_ready = False
    try:
        while True:
            if force_kill_event.is_set():
                break
            try:
                b_name, rec, err = result_queue.get(timeout=0.2)
            except queue.Empty:
                with worker_lock:
                    drained = active_worker_threads == 0
                    queued = task_queue.empty()
                if should_stop_main_loop(0 if drained else 1, queued, cancel_event.is_set(), False):
                    if cancel_event.is_set() and not queued:
                        with status_lock:
                            recent_messages.append(
                                f"{C_BYELLOW}[GRACEFUL STOP] Left {task_queue.qsize()} board(s) queued.{C_RESET}"
                            )
                        trigger_render()
                    break
                continue

            if rec is None and err is None:
                continue

            if err is not None:
                error_count += 1
                msg = format_recent_result(
                    completed, len(tasks), "ERROR", f"({err})", b_name, tier_places.get(b_name)
                )
                recent_messages.append(msg)
                try:
                    save_corpus()
                except OSError:
                    pass
                trigger_render()
                continue

            completed += 1
            tracker["completed"] = completed
            existing_runs[rec["cache_key"]] = rec
            q = rec.get("quality", {})
            exit_info = rec.get("exit", {})
            unr = q.get("unrouted_connections", q.get("final_unrouted"))
            viol = q.get("clearance_violations")
            router_viol = q.get("router_introduced_violations")
            sec = q.get("wall_clock_seconds", 0.0)
            sample_tier, sample_reference = board_reference.get(b_name, ("?", 0.0))
            is_timeout = exit_info.get("timed_out", False)
            failed_run = run_is_failure(rec)
            if not failed_run:
                with status_lock:
                    completed_samples.append((sample_tier, sample_reference, float(sec or 0.0)))

            unfixable_viol = q.get("unfixable_clearance_violations")
            if unfixable_viol is None:
                unfixable_viol = 0
            if router_viol is not None:
                is_clean = unr == 0 and router_viol == 0
            else:
                is_clean = unr == 0 and (viol == 0 or (viol is not None and viol <= unfixable_viol))

            if is_clean:
                label = "CLEAN"
                suffix = ""
            elif unr == 0:
                label = "ROUTED"
                suffix = f"viol={viol}"
            else:
                label = "UNROUTED"
                suffix = f"unr={unr}, viol={viol}"

            fatal_exit = exit_info.get("oom_detected") or exit_info.get("crashed") or (
                exit_info.get("code") not in (0, None) and not is_timeout
            )
            if fatal_exit:
                error_count += 1
                label = "ERROR"
                suffix = "oom" if exit_info.get("oom_detected") else f"exit={exit_info.get('code')}"
            elif is_timeout:
                timeout_count += 1
                suffix = f"{label}, {suffix}" if suffix else label
                label = "TIMEOUT"
            else:
                if is_clean:
                    clean_count += 1
                elif unr == 0:
                    routed_viol_count += 1
                else:
                    unrouted_count += 1

            msg = format_recent_result(
                completed,
                len(tasks),
                label,
                format_result_timing(float(sec or 0.0), suffix),
                b_name,
                tier_places.get(b_name),
            )
            recent_messages.append(msg)

            try:
                save_outcome = persist_run(rec)
            except OSError:
                save_outcome = "unsaved"
            if save_outcome in ("locked", "unsaved"):
                kept_in = "run-journal.jsonl" if save_outcome == "locked" else "memory"
                recent_messages.append(
                    f"[save] {b_name}: benchmarks.json was locked; {label} result kept in {kept_in}"
                )

            trigger_render()

    except KeyboardInterrupt:
        print("\nBenchmark interrupted by user (Ctrl+C). Saving current progress...", flush=True)
    finally:
        stop_refresh.set()
        cancel_event.set()
        if force_kill_event.is_set():
            for p in list(active_procs.values()):
                try:
                    p.kill()
                except (ProcessLookupError, OSError):
                    pass
        for t in worker_threads:
            t.join(timeout=0.5)
        try:
            corpus_ready = save_corpus(force=True, attempts=40) == "saved"
        except OSError:
            corpus_ready = False
        for stray_ses in Path(".").glob("*--unrouted--*.ses"):
            try:
                stray_ses.unlink(missing_ok=True)
            except OSError:
                # Best-effort cleanup of stray SES files on exit
                pass

    if force_kill_event.is_set():
        print(f"\n{C_BRED}Benchmark force-terminated by user.{C_RESET}", flush=True)
    elif cancel_event.is_set():
        if corpus_ready:
            print(f"\n{C_BYELLOW}Benchmark stopped gracefully. All completed board results have been saved.{C_RESET}", flush=True)
        else:
            print(
                f"\n{C_BYELLOW}Benchmark stopped. benchmarks.json stayed locked; "
                f"completed results remain in run-journal.jsonl.{C_RESET}",
                flush=True,
            )

    total_time = round(time.perf_counter() - t_start, 1)
    print(f"\nPCBench Corpus Benchmark finished {completed} boards in {total_time}s.", flush=True)
    print(f"Results: {clean_count} Clean, {routed_viol_count} Routed with violations, {unrouted_count} Unrouted, {timeout_count} Timeouts, {error_count} Errors.")

    # Regenerate reports only from the corpus file. A locked swap must not
    # publish a report that is missing journaled runs.
    if corpus_ready:
        print("Regenerating Markdown and HTML reports...", flush=True)
        subprocess.run(["powershell", "-ExecutionPolicy", "Bypass", "-File", "scripts/benchmark/run-benchmarks.ps1", "-ReportOnly"], check=False)
    else:
        print(
            "benchmarks.json stayed locked. Completed results remain in run-journal.jsonl. "
            "The report was not regenerated.",
            flush=True,
        )

    return 0


if __name__ == "__main__":
    sys.exit(main())
