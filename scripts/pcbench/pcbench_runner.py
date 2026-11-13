"""Worker subprocess execution, process locking, and log analysis for PCBench."""

from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import threading
import time
from typing import Any

_TRACE_MIN = re.compile(r"\(trace_min\s+([0-9]*\.?[0-9]+)\)")

_FATAL_OUTPUT_MARKERS = (
    "OutOfMemoryError",
    "StackOverflowError",
    "Exception in thread",
    "terminated abnormally",
    "terminated due to a fatal error",
    "terminated due to an error",
)


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
            self.file_handle.write(
                f"pid={os.getpid()}\nstarted={datetime.now(timezone.utc).isoformat()}\n"
            )
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


def parse_phases_from_text(text: str) -> dict[str, Any]:
    phases = {
        "fanout": {"duration_seconds": None, "passes_completed": 0, "log_found": False},
        "autorouter": {"duration_seconds": None, "passes_completed": 0},
        "optimizer": {"duration_seconds": None, "passes_completed": 0},
    }
    # Fanout
    m_fan = re.search(
        r"Fanout (?:phase|stage) completed:.*completed in ([\d\.]+) seconds.*escaped pins:\s*(\d+)/(\d+)\s*\(([\d\.]+)%\)",
        text,
    )
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
    m_auto = re.search(
        r"Auto-rout\w+ (?:phase|stage) completed:.*completed in ([\d\.]+) seconds", text
    )
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
    m_opt = re.search(
        r"Optimi\w+ (?:phase|stage) completed:.*completed in ([\d\.]+) seconds", text
    )
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
        "-Djava.awt.headless=true",
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
        popen_kwargs: dict[str, Any] = {
            "stdout": subprocess.PIPE,
            "stderr": subprocess.STDOUT,
            "text": True,
            "encoding": "utf-8",
            "errors": "replace",
            "bufsize": 1,
        }
        if sys.platform == "win32" and hasattr(subprocess, "BELOW_NORMAL_PRIORITY_CLASS"):
            popen_kwargs["creationflags"] = subprocess.BELOW_NORMAL_PRIORITY_CLASS

        proc = subprocess.Popen(cmd, **popen_kwargs)

        if sys.platform != "win32" and hasattr(os, "setpriority") and hasattr(os, "PRIO_PROCESS"):
            try:
                os.setpriority(os.PRIO_PROCESS, proc.pid, 10)
            except (OSError, TypeError):
                # Best-effort nice adjustment; do not fail the run if unsupported or process already exited
                pass
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
    min_viol_um = violations_info.get(
        "min_violation_um", violations_info.get("min_violation_mm", None)
    )
    max_viol_um = violations_info.get(
        "max_violation_um", violations_info.get("max_violation_mm", None)
    )
    avg_viol_um = violations_info.get(
        "avg_violation_um", violations_info.get("avg_violation_mm", None)
    )
    score_val = manifest_data.get("normalized_score", None)
    final_state = manifest_data.get(
        "final_state", "FAILED" if (crashed or timed_out) else "COMPLETED"
    )
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
            m_pass = re.findall(
                r"Auto-rout\w+ pass #\d+.*(?:score of|score)\s*([\d\.]+)(?:\s*\((.*?)\))?",
                stdout_text,
            )
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
        "result_json": (
            str(manifest_path.relative_to(output_dir.parent.parent))
            if manifest_path.exists()
            else None
        ),
        "output_file": (
            str(ses_path.relative_to(output_dir.parent.parent)) if ses_path.exists() else None
        ),
        "job_timeout": timeout_budget,
        "neck_width_um": neck_width_um,
    }

    return run_record
