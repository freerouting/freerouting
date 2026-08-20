#!/usr/bin/env python3
"""Run comprehensive headless benchmarks across PCBench in-repo fixtures and store in benchmarks.json."""

from __future__ import annotations

import argparse
import collections
import concurrent.futures
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


def compute_sha256(path: Path) -> str:
    """Compute sha256 of a file."""
    if not path.exists():
        return ""
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


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
    timeout_budget: str,
    version_label: str,
    git_sha: str,
    jar_sha256: str,
    jar_size: int,
    worker_slots: queue.Queue[int],
    worker_status: dict[int, dict[str, Any]],
    status_lock: threading.Lock,
) -> dict[str, Any]:
    """Execute Freerouting on a single PCBench board and return a benchmark run record."""
    board_id = board_dir.name
    dsn_path = board_dir / "unrouted.dsn"
    ses_path = output_dir / f"{board_id}--unrouted--{version_label}.ses"
    manifest_path = output_dir / f"{board_id}--unrouted--{version_label}-result.json"
    log_path = log_dir / f"{board_id}--unrouted--{version_label}.log"

    wid = worker_slots.get()
    t0 = time.perf_counter()

    with status_lock:
        worker_status[wid] = {
            "board": board_id,
            "start": t0,
            "last_line": "Starting autorouter process...",
            "active": True,
        }

    # Timeout calculation
    parts = timeout_budget.split(":")
    if len(parts) == 3:
        timeout_sec = int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
    else:
        timeout_sec = 60

    cmd = [
        "java",
        "-Dfreerouting.log.file.level=INFO",
        "-Dfreerouting.log.console.level=INFO",
        "-jar",
        str(jar_path),
        "--gui.enabled=false",
        "--api_server.enabled=false",
        "--mcp_server.enabled=false",
        "-de",
        str(dsn_path),
        "-do",
        str(ses_path),
        "-dct",
        "0",
        f"--router.result_json={manifest_path}",
        "--router.max_passes=20",
        f"--router.job_timeout={timeout_budget}",
        f"--logging.file.location={log_path}",
    ]

    exit_code = 0
    crashed = False
    timed_out = False
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
        if proc.stdout:
            for line in proc.stdout:
                stdout_lines.append(line)
                clean = line.strip()
                if clean:
                    short = clean
                    for pfx in (" INFO   ", " DEBUG  ", " WARN   ", " ERROR  "):
                        if pfx in clean:
                            short = clean.split(pfx, 1)[1]
                            break
                    with status_lock:
                        worker_status[wid]["last_line"] = short[:70]
        proc.wait(timeout=timeout_sec + 20)
        exit_code = proc.returncode
    except subprocess.TimeoutExpired:
        timed_out = True
        try:
            proc.kill()
        except Exception:
            pass
        exit_code = -1
        stdout_lines.append("TIMEOUT")
    except Exception as e:
        crashed = True
        exit_code = -2
        stdout_lines.append(str(e))
    finally:
        with status_lock:
            worker_status[wid]["active"] = False
            worker_status[wid]["last_line"] = "Idle"
        worker_slots.put(wid)

    stdout_text = "".join(stdout_lines)
    wall_time = round(time.perf_counter() - t0, 2)

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
    min_viol_um = violations_info.get("min_violation_um", violations_info.get("min_violation_mm", None))
    max_viol_um = violations_info.get("max_violation_um", violations_info.get("max_violation_mm", None))
    avg_viol_um = violations_info.get("avg_violation_um", violations_info.get("avg_violation_mm", None))
    score_val = manifest_data.get("normalized_score", None)
    final_state = manifest_data.get("final_state", "FAILED" if (crashed or timed_out) else "COMPLETED")
    phases = manifest_data.get("phases", {})
    if not phases.get("autorouter", {}).get("duration_seconds") and stdout_text:
        phases = parse_phases_from_text(stdout_text)
    resources = manifest_data.get("resource_usage", {})

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
            "oom_detected": "OutOfMemoryError" in stdout_text,
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
    }

    return run_record


def render_dashboard(
    completed: int,
    total: int,
    t_start: float,
    worker_status: dict[int, dict[str, Any]],
    recent_messages: collections.deque[str],
    status_lock: threading.Lock,
    in_place: bool = True,
) -> None:
    """Print updated multi-worker dashboard with live logs and recent history."""
    elapsed = time.perf_counter() - t_start
    avg_per_board = elapsed / completed if completed > 0 else 0
    remaining_secs = avg_per_board * (total - completed)
    eta_str = time.strftime("%H:%M:%S", time.gmtime(remaining_secs))
    elapsed_str = time.strftime("%H:%M:%S", time.gmtime(elapsed))
    pct = (completed / total) * 100.0 if total > 0 else 0.0

    lines = []
    lines.append(f"{C_BCYAN}{'=' * 105}{C_RESET}")
    lines.append(
        f"{C_BWHITE}PCBench Benchmark:{C_RESET} {C_BYELLOW}{completed}/{total}{C_RESET} "
        f"({C_BGREEN}{pct:5.1f}%{C_RESET}) | "
        f"{C_BWHITE}Elapsed:{C_RESET} {C_CYAN}{elapsed_str}{C_RESET} | "
        f"{C_BWHITE}ETA:{C_RESET} {C_CYAN}{eta_str}{C_RESET} ({C_YELLOW}{avg_per_board:.1f}s/board{C_RESET}) | "
        f"{C_BWHITE}Workers:{C_RESET} {C_BMAGENTA}{len(worker_status)}{C_RESET}"
    )
    lines.append(f"{C_CYAN}{'-' * 105}{C_RESET}")
    lines.append(f"{C_BWHITE}Active Workers:{C_RESET}")

    now = time.perf_counter()
    with status_lock:
        for wid in sorted(worker_status.keys()):
            info = worker_status[wid]
            if info.get("active"):
                run_sec = int(now - info.get("start", now))
                dur_str = f"{run_sec // 60:02d}:{run_sec % 60:02d}"
                board = info.get("board", "unknown")
                last = info.get("last_line", "")
                lines.append(
                    f"  {C_BMAGENTA}[Worker {wid}]{C_RESET} "
                    f"{C_BWHITE}{board:<36}{C_RESET} "
                    f"{C_BYELLOW}[{dur_str}]{C_RESET} -> {C_GRAY}{last}{C_RESET}"
                )
            else:
                lines.append(f"  {C_BMAGENTA}[Worker {wid}]{C_RESET} {C_DIM}Idle{C_RESET}")

    lines.append(f"{C_CYAN}{'-' * 105}{C_RESET}")
    lines.append(f"{C_BWHITE}Recent Completed (Last 10):{C_RESET}")
    if recent_messages:
        for msg in recent_messages:
            lines.append(f"  {colorize_status_line(msg)}")
    else:
        lines.append(f"  {C_DIM}(None completed yet){C_RESET}")
    lines.append(f"{C_BCYAN}{'=' * 105}{C_RESET}")

    output_text = "\n".join(lines)
    if in_place and sys.stdout.isatty():
        sys.stdout.write("\033[H\033[J" + output_text + "\n")
        sys.stdout.flush()
    else:
        print(output_text, flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", default="scripts/benchmark/binaries/freerouting-current.jar", type=Path)
    parser.add_argument("--fixtures-dir", default="scripts/benchmark/fixtures/PCBench", type=Path)
    parser.add_argument("--tier", default="All", help="Filter by tier: 'A', 'B', 'C', 'D', or 'All'")
    parser.add_argument("--workers", default=8, type=int)
    parser.add_argument("--max-boards", default=0, type=int)
    parser.add_argument("--version-label", default="v2.3.1-SNAPSHOT")
    parser.add_argument("--force", action="store_true", help="Force rerun even if already in benchmarks.json")
    args = parser.parse_args()

    fixtures_dir = args.fixtures_dir
    catalog_path = fixtures_dir / "catalog.json"
    jar_path = args.jar
    output_dir = Path("scripts/benchmark/outputs")
    log_dir = Path("scripts/benchmark/logs")
    results_dir = Path("scripts/benchmark/results")
    benchmarks_json = results_dir / "benchmarks.json"

    output_dir.mkdir(parents=True, exist_ok=True)
    log_dir.mkdir(parents=True, exist_ok=True)
    results_dir.mkdir(parents=True, exist_ok=True)

    if not catalog_path.exists() or not jar_path.exists():
        print("Missing catalog.json or freerouting JAR", file=sys.stderr)
        return 1

    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    boards = catalog.get("boards", [])

    if args.tier != "All":
        boards = [b for b in boards if b.get("tier") == args.tier]

    if args.max_boards > 0:
        boards = boards[: args.max_boards]

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

    tasks = []
    already_completed = 0
    for b in boards:
        b_id = b.get("board_id")
        b_dir = fixtures_dir / b_id
        budget = b.get("timeout_budget", "00:05:00")
        
        if not args.force:
            is_already_run = any(
                r.get("fixture", {}).get("relative_path") == f"PCBench/{b_id}/unrouted.dsn"
                and r.get("binary", {}).get("version_label") == args.version_label
                and (r.get("quality", {}).get("unrouted_connections") is not None or r.get("quality", {}).get("final_unrouted") is not None)
                for r in existing_runs.values()
            )
            if is_already_run:
                already_completed += 1
                continue

        if (b_dir / "unrouted.dsn").exists():
            tasks.append((jar_path, b_dir, output_dir, log_dir, budget, args.version_label, git_sha, jar_sha256, jar_size))

    print(
        f"Starting PCBench Corpus Benchmark ({len(boards)} total, {already_completed} already cached, "
        f"{len(tasks)} remaining to run, Tier={args.tier}, Workers={args.workers})...\n",
        flush=True,
    )

    if not tasks:
        print("All requested boards are already benchmarked in benchmarks.json! Regenerating reports...", flush=True)
        subprocess.run(["powershell", "-ExecutionPolicy", "Bypass", "-File", "scripts/benchmark/run-benchmarks.ps1", "-ReportOnly"], check=False)
        return 0

    def save_benchmarks_atomic():
        bench_data["runs"] = list(existing_runs.values())
        bench_data["total_runs"] = len(bench_data["runs"])
        bench_data["generated_at"] = datetime.now(timezone.utc).isoformat()
        tmp_file = benchmarks_json.with_suffix(".tmp")
        tmp_file.write_text(json.dumps(bench_data, indent=2), encoding="utf-8")
        tmp_file.replace(benchmarks_json)

    # Worker tracking structures
    worker_slots: queue.Queue[int] = queue.Queue()
    for wid in range(1, args.workers + 1):
        worker_slots.put(wid)

    worker_status: dict[int, dict[str, Any]] = {
        wid: {"board": "Idle", "start": 0.0, "last_line": "Idle", "active": False}
        for wid in range(1, args.workers + 1)
    }
    status_lock = threading.Lock()
    recent_messages: collections.deque[str] = collections.deque(maxlen=10)

    completed = 0
    clean_count = 0
    routed_viol_count = 0
    unrouted_count = 0
    timeout_count = 0
    error_count = 0
    t_start = time.perf_counter()

    tracker = {"completed": 0}
    stop_refresh = threading.Event()

    def background_refresh():
        while not stop_refresh.is_set():
            stop_refresh.wait(5.0)
            if not stop_refresh.is_set():
                render_dashboard(
                    tracker["completed"],
                    len(tasks),
                    t_start,
                    worker_status,
                    recent_messages,
                    status_lock,
                    in_place=True,
                )

    refresh_thread = threading.Thread(target=background_refresh, daemon=True)
    refresh_thread.start()

    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
            future_to_board = {
                executor.submit(
                    route_single_board,
                    *task,
                    worker_slots,
                    worker_status,
                    status_lock,
                ): task[1].name
                for task in tasks
            }

            for future in concurrent.futures.as_completed(future_to_board):
                completed += 1
                tracker["completed"] = completed
                b_name = future_to_board[future]
                elapsed = time.perf_counter() - t_start
                avg_per_board = elapsed / completed if completed > 0 else 0
                remaining_secs = avg_per_board * (len(tasks) - completed)
                eta_str = time.strftime("%H:%M:%S", time.gmtime(remaining_secs))
                elapsed_str = time.strftime("%H:%M:%S", time.gmtime(elapsed))
                pct = (completed / len(tasks)) * 100.0

                try:
                    rec = future.result()
                    existing_runs[rec["cache_key"]] = rec
                    q = rec.get("quality", {})
                    exit_info = rec.get("exit", {})
                    unr = q.get("unrouted_connections", q.get("final_unrouted"))
                    viol = q.get("clearance_violations")
                    sec = q.get("wall_clock_seconds", 0.0)
                    is_timeout = exit_info.get("timed_out", False)

                    if is_timeout:
                        timeout_count += 1
                        status = f"TIMEOUT ({sec:.1f}s)"
                    elif unr == 0 and viol == 0:
                        clean_count += 1
                        status = f"CLEAN ({sec:.1f}s)"
                    elif unr == 0:
                        routed_viol_count += 1
                        status = f"ROUTED (viol={viol}, {sec:.1f}s)"
                    else:
                        unrouted_count += 1
                        status = f"UNROUTED (unr={unr}, viol={viol}, {sec:.1f}s)"

                    msg = f"[{completed:4d}/{len(tasks)} {pct:5.1f}%] [Elapsed:{elapsed_str} ETA:{eta_str} ({avg_per_board:.1f}s/board)] {b_name}: {status}"
                    recent_messages.append(msg)

                    # Real-time atomic save on every completed board
                    save_benchmarks_atomic()

                    # Render dashboard with active workers & recent 10 completed
                    render_dashboard(
                        completed,
                        len(tasks),
                        t_start,
                        worker_status,
                        recent_messages,
                        status_lock,
                        in_place=True,
                    )

                    # Trigger report regeneration every 50 boards in background
                    if completed % 50 == 0:
                        try:
                            subprocess.Popen(
                                ["powershell", "-ExecutionPolicy", "Bypass", "-File", "scripts/benchmark/run-benchmarks.ps1", "-ReportOnly"],
                                stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL,
                            )
                        except Exception:
                            pass

                except Exception as e:
                    error_count += 1
                    msg = f"[{completed:4d}/{len(tasks)} {pct:5.1f}%] {b_name}: ERROR {e}"
                    recent_messages.append(msg)
                    save_benchmarks_atomic()
                    render_dashboard(
                        completed,
                        len(tasks),
                        t_start,
                        worker_status,
                        recent_messages,
                        status_lock,
                        in_place=True,
                    )

    except KeyboardInterrupt:
        print("\nBenchmark interrupted by user. Saving current progress...", flush=True)
    finally:
        stop_refresh.set()
        refresh_thread.join(timeout=1.0)
        save_benchmarks_atomic()

    total_time = round(time.perf_counter() - t_start, 1)
    print(f"\nPCBench Corpus Benchmark finished {completed} boards in {total_time}s.", flush=True)
    print(f"Results: {clean_count} Clean, {routed_viol_count} Routed with violations, {unrouted_count} Unrouted, {timeout_count} Timeouts, {error_count} Errors.")

    # Regenerate reports
    print("Regenerating Markdown and HTML reports...", flush=True)
    subprocess.run(["powershell", "-ExecutionPolicy", "Bypass", "-File", "scripts/benchmark/run-benchmarks.ps1", "-ReportOnly"], check=False)

    return 0


if __name__ == "__main__":
    sys.exit(main())
