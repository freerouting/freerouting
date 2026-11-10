#!/usr/bin/env python3
"""Run comprehensive headless benchmarks across PCBench in-repo fixtures and store in benchmarks.json."""

from __future__ import annotations

import argparse
import atexit
import collections
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import queue
import subprocess
import sys
import threading
import time
from typing import Any

# Ensure benchmark tools can be imported
_benchmark_dir = Path(__file__).resolve().parents[1] / "benchmark"
if str(_benchmark_dir) not in sys.path:
    sys.path.insert(0, str(_benchmark_dir))
try:
    from benchmark_store import append_version_run, render_compact, save_version_runs
except ImportError:
    append_version_run = None
    render_compact = None
    save_version_runs = None

# Re-export analysis, runner, and UI helpers for backward compatibility and test suites
from pcbench_analysis import (
    CORPUS_SAVE_INTERVAL_SECONDS,
    DEFAULT_TIER_ORDER,
    _KNOWN_TIERS,
    _VERSION_LABEL,
    _parse_run_at,
    append_jsonl_record,
    corpus_save_is_due,
    default_worker_count,
    fixture_id_from_run,
    latest_successful_reference_seconds,
    parse_tier_order,
    read_jsonl_records,
    replace_file_retrying,
    run_is_failure,
    should_stop_main_loop,
    version_sort_key,
)
from pcbench_runner import (
    ProcessLock,
    _FATAL_OUTPUT_MARKERS,
    _TRACE_MIN,
    board_min_track_width_um,
    compute_sha256,
    fatal_process_output,
    log_tail_contains,
    parse_phases_from_text,
    route_single_board,
)
from pcbench_ui import (
    C_BBLUE,
    C_BCYAN,
    C_BGREEN,
    C_BMAGENTA,
    C_BOLD,
    C_BRED,
    C_BWHITE,
    C_BYELLOW,
    C_BLUE,
    C_CYAN,
    C_DIM,
    C_GRAY,
    C_GREEN,
    C_MAGENTA,
    C_RED,
    C_RESET,
    C_WHITE,
    C_YELLOW,
    MINIMUM_SCALE_REFERENCE_SECONDS,
    _render_lock,
    assign_tier_places,
    colorize_status_line,
    duration_scales,
    estimate_remaining_seconds,
    expected_fixture_seconds,
    format_duration,
    format_elapsed_clock,
    format_fixture_name,
    format_recent_result,
    format_result_timing,
    format_saved_age,
    format_worker_progress,
    format_worker_tag,
    render_dashboard,
    scale_for,
)

# Reconfigure stdout/stderr to UTF-8 with fallback
try:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    # Ignore terminal reconfiguration errors in environments that do not support it
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
        # Ignore ANSI initialization failure on non-standard Windows consoles
        pass

REPORT_GENERATION_INTERVAL_SECONDS = 600.0


def trigger_report_generation(*, verbose: bool = False) -> bool:
    """Regenerate benchmark reports (Markdown, HTML, chart data)."""
    repo_root = Path(__file__).resolve().parents[2]
    ps_script = repo_root / "scripts" / "benchmark" / "run-benchmarks.ps1"
    ps_cmd = "powershell" if sys.platform == "win32" else "pwsh"
    try:
        proc = subprocess.run(
            [ps_cmd, "-ExecutionPolicy", "Bypass", "-File", str(ps_script), "-ReportOnly"],
            stdout=None if verbose else subprocess.PIPE,
            stderr=None if verbose else subprocess.PIPE,
            text=True,
            check=False,
        )
        if proc.returncode == 0:
            return True
    except Exception:
        # PowerShell runner failed; fall back to direct Python generator
        pass

    # Direct Python fallback
    try:
        export_script = repo_root / "scripts" / "benchmark" / "export_reports.py"
        if export_script.exists():
            proc = subprocess.run(
                [sys.executable, str(export_script)],
                stdout=None if verbose else subprocess.PIPE,
                stderr=None if verbose else subprocess.PIPE,
                text=True,
                check=False,
            )
            return proc.returncode == 0
    except Exception:
        # Background report generation is non-fatal to live benchmarking
        pass
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", default="scripts/benchmark/binaries/freerouting-current.jar", type=Path)
    parser.add_argument("--fixtures-dir", default="scripts/benchmark/fixtures/PCBench", type=Path)
    parser.add_argument(
        "--tier",
        default="All",
        help="Tier filter and run order. Default All runs A, D, C, then B. "
        "A comma-separated list runs those tiers in that order, for example C,A or T.",
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
        "--report-interval",
        default=REPORT_GENERATION_INTERVAL_SECONDS,
        type=float,
        help="Interval in seconds between periodic report updates during benchmark (default: 600s / 10m)",
    )
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
            # Lock file might have been removed or locked by sibling process
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
            # File may already be deleted or locked
            pass

    atexit.register(proc_lock.release)

    if not catalog_path.exists() or not jar_path.exists():
        print("Missing catalog.json or freerouting JAR", file=sys.stderr)
        return 1

    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    boards = catalog.get("boards", [])
    tier_rank = {tier: index for index, tier in enumerate(tier_order)}

    def matches_tier_filter(board: dict[str, Any]) -> bool:
        if board.get("tier") in tier_rank:
            return True
        if "T" in tier_rank and ("tier-t" in (board.get("tags") or []) or board.get("tier_t") is True):
            return True
        return False

    def board_tier_sort_key(board: dict[str, Any]) -> tuple[int, int]:
        if "T" in tier_rank and ("tier-t" in (board.get("tags") or []) or board.get("tier_t") is True):
            rank = tier_rank["T"]
        else:
            rank = tier_rank.get(board.get("tier"))
        return (rank if rank is not None else 10**9, board.get("tier_index", 10**9))

    boards = [b for b in boards if matches_tier_filter(b)]
    boards.sort(key=board_tier_sort_key)

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
            # Ignore malformed or incomplete existing benchmarks file
            pass

    existing_runs = {r.get("cache_key"): r for r in bench_data.get("runs", []) if r.get("cache_key")}
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
        f"{len(tasks)} remaining to run, Tier={','.join(tier_order)}, Workers={args.workers})...\n",
        flush=True,
    )

    def save_benchmarks_atomic(attempts: int = 12) -> bool:
        bench_data["runs"] = list(existing_runs.values())
        bench_data["total_runs"] = len(bench_data["runs"])
        bench_data["generated_at"] = datetime.now(timezone.utc).isoformat()
        tmp_file = benchmarks_json.with_name(
            f".{benchmarks_json.name}.{os.getpid()}.{time.time_ns()}.tmp"
        )
        try:
            if render_compact:
                content = render_compact(bench_data) + "\n"
            else:
                content = json.dumps(bench_data, separators=(",", ":")) + "\n"
            tmp_file.write_text(content, encoding="utf-8")
            replace_file_retrying(tmp_file, benchmarks_json, attempts=attempts)
            if save_version_runs:
                try:
                    ver_runs = [
                        r
                        for r in existing_runs.values()
                        if (r.get("binary") or {}).get("version_label") == args.version_label
                    ]
                    if ver_runs:
                        save_version_runs(benchmarks_json.parent, args.version_label, ver_runs)
                except Exception:
                    # Ignore version save errors during atomic file swap
                    pass
            return True
        except PermissionError:
            return False
        finally:
            try:
                tmp_file.unlink(missing_ok=True)
            except OSError:
                # Temp file may have already been replaced
                pass

    last_corpus_save_at: list[float | None] = [None]

    def clear_run_journal() -> None:
        if not journal_file.exists():
            return
        with journal_file.open("w", encoding="utf-8", newline="\n") as handle:
            handle.flush()
            os.fsync(handle.fileno())

    def save_corpus(*, force: bool = False, attempts: int = 12) -> str:
        if not corpus_save_is_due(last_corpus_save_at[0], time.perf_counter(), force=force):
            return "throttled"
        if not save_benchmarks_atomic(attempts=attempts):
            return "locked"
        last_corpus_save_at[0] = time.perf_counter()
        try:
            clear_run_journal()
        except PermissionError:
            # Another process holds the journal open; will clear next cycle
            pass
        return "saved"

    def persist_run(record: dict[str, Any]) -> str:
        journal_saved = False
        try:
            append_jsonl_record(journal_file, record)
            journal_saved = True
        except PermissionError:
            journal_saved = False
        if append_version_run:
            try:
                append_version_run(benchmarks_json.parent, args.version_label, record)
            except Exception:
                # Version JSONL append is non-fatal to corpus progression
                pass
        outcome = save_corpus()
        if outcome != "saved" and not journal_saved:
            return "unsaved"
        return outcome

    if not tasks:
        if save_benchmarks_atomic(attempts=40):
            try:
                clear_run_journal()
            except PermissionError:
                # Journal lock will release when process exits
                pass
            print(
                "All requested boards are already benchmarked in benchmarks.json! Regenerating reports...",
                flush=True,
            )
            trigger_report_generation(verbose=True)
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
    worker_lock = threading.Lock()
    cancel_event = threading.Event()
    force_kill_event = threading.Event()
    stop_refresh = threading.Event()

    active_worker_threads = 0
    worker_threads: list[threading.Thread] = []
    worker_id_counter = 0
    target_workers = max(1, args.workers)

    tracker = {"completed": 0, "total": len(tasks)}
    recent_messages: collections.deque[str] = collections.deque(maxlen=10)
    completed_samples: list[tuple[str, float, float]] = []

    completed = 0
    clean_count = 0
    routed_viol_count = 0
    unrouted_count = 0
    timeout_count = 0
    error_count = 0

    t_start = time.perf_counter()

    def trigger_render():
        render_dashboard(
            completed=tracker["completed"],
            total=tracker["total"],
            t_start=t_start,
            worker_status=worker_status,
            recent_messages=recent_messages,
            status_lock=status_lock,
            version_label=args.version_label,
            cancel_requested=cancel_event.is_set(),
            force_kill_requested=force_kill_event.is_set(),
            keyboard_supported=sys.stdin.isatty(),
            in_place=True,
            last_saved_at=last_corpus_save_at[0],
            target_workers=target_workers,
            board_reference=board_reference,
            queued_ids=queued_ids,
            completed_samples=completed_samples,
        )

    def worker_loop(wid: int):
        nonlocal active_worker_threads
        while not cancel_event.is_set() and not force_kill_event.is_set():
            with worker_lock:
                if active_worker_threads > target_workers:
                    active_worker_threads -= 1
                    with status_lock:
                        worker_status.pop(wid, None)
                    trigger_render()
                    return

            try:
                task = task_queue.get(timeout=0.2)
            except queue.Empty:
                break

            (
                jar,
                b_dir,
                out_dir,
                l_dir,
                u_dir,
                budget,
                v_label,
                sha,
                j_sha,
                j_size,
                t_tier,
            ) = task
            b_name = b_dir.name
            t_ref = board_reference.get(b_name, ("?", 0.0))[1]
            t_neck = board_neck_width.get(b_name)

            try:
                run_rec = route_single_board(
                    jar,
                    b_dir,
                    out_dir,
                    l_dir,
                    u_dir,
                    budget,
                    v_label,
                    sha,
                    j_sha,
                    j_size,
                    t_tier,
                    wid,
                    worker_status,
                    active_procs,
                    status_lock,
                    cancel_event,
                    force_kill_event,
                    reference_seconds=t_ref,
                    queued_ids=queued_ids,
                    neck_width_um=t_neck,
                )
                result_queue.put((b_name, run_rec, None))
            except Exception as e:
                result_queue.put((b_name, None, e))
            finally:
                task_queue.task_done()

        with worker_lock:
            active_worker_threads -= 1
            with status_lock:
                worker_status.pop(wid, None)
        trigger_render()

    def spawn_worker_if_needed():
        nonlocal active_worker_threads, worker_id_counter
        while active_worker_threads < target_workers:
            wid = worker_id_counter + 1
            worker_id_counter += 1
            active_worker_threads += 1
            with status_lock:
                worker_status[wid] = {"active": False, "board": "", "last_line": "Idle"}
            t = threading.Thread(target=worker_loop, args=(wid,), daemon=True)
            worker_threads.append(t)
            t.start()

    def on_increase_workers():
        nonlocal target_workers
        with worker_lock:
            target_workers += 1
            spawn_worker_if_needed()
        trigger_render()

    def on_decrease_workers():
        nonlocal target_workers
        with worker_lock:
            if target_workers > 1:
                target_workers -= 1
        trigger_render()

    def on_cancel_key():
        if cancel_event.is_set():
            force_kill_event.set()
            for p in list(active_procs.values()):
                try:
                    p.kill()
                except (ProcessLookupError, OSError):
                    # Process may have already exited
                    pass
        else:
            cancel_event.set()
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
                                # Consume extended key byte
                                pass
                    time.sleep(0.05)
            except Exception:
                # Keyboard monitoring unavailable
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
                # Raw tty mode unavailable
                pass

    with worker_lock:
        spawn_worker_if_needed()

    refresh_thread = threading.Thread(target=background_refresh, daemon=True)
    refresh_thread.start()

    key_thread = threading.Thread(target=keyboard_listener, daemon=True)
    key_thread.start()

    corpus_ready = False
    last_report_at = time.perf_counter()
    last_reported_completed = 0

    def maybe_generate_periodic_reports() -> None:
        nonlocal last_report_at, last_reported_completed
        now = time.perf_counter()
        if (now - last_report_at) >= args.report_interval:
            if completed > last_reported_completed:
                try:
                    if save_corpus(force=True, attempts=12) == "saved":
                        if trigger_report_generation(verbose=False):
                            with status_lock:
                                recent_messages.append(
                                    f"[report] Updated benchmark reports ({completed}/{len(tasks)} boards completed)"
                                )
                            last_reported_completed = completed
                except Exception:
                    # Periodic report generation failure is non-fatal
                    pass
            last_report_at = now

    try:
        while True:
            if force_kill_event.is_set():
                break
            try:
                b_name, rec, err = result_queue.get(timeout=0.2)
            except queue.Empty:
                maybe_generate_periodic_reports()
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
                    # Ignore save failure on transient lock; journal retains entry
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

            maybe_generate_periodic_reports()
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
                    # Process may have already exited
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
                # Stray SES cleanup is best-effort
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
        trigger_report_generation(verbose=True)
    else:
        print(
            "benchmarks.json stayed locked. Completed results remain in run-journal.jsonl. "
            "The report was not regenerated.",
            flush=True,
        )

    return 0


if __name__ == "__main__":
    sys.exit(main())
