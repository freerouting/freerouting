#!/usr/bin/env python3
"""Run comprehensive headless benchmarks across PCBench in-repo fixtures and store in benchmarks.json."""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
import platform
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def compute_sha256(path: Path) -> str:
    """Compute sha256 of a file."""
    if not path.exists():
        return ""
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


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
) -> dict[str, Any]:
    """Execute Freerouting on a single PCBench board and return a benchmark run record."""
    board_id = board_dir.name
    dsn_path = board_dir / "unrouted.dsn"
    ses_path = output_dir / f"{board_id}--unrouted--{version_label}.ses"
    manifest_path = output_dir / f"{board_id}--unrouted--{version_label}-result.json"
    log_path = log_dir / f"{board_id}--unrouted--{version_label}.log"

    # Timeout calculation
    parts = timeout_budget.split(":")
    if len(parts) == 3:
        timeout_sec = int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
    else:
        timeout_sec = 60

    cmd = [
        "java",
        "-jar",
        str(jar_path),
        "--gui.enabled=false",
        "--api_server.enabled=false",
        "--mcp_server.enabled=false",
        "-de",
        str(dsn_path),
        "-do",
        str(ses_path),
        f"--router.result_json={manifest_path}",
        "--router.max_passes=20",
        f"--router.job_timeout={timeout_budget}",
        f"--logging.file.location={log_path}",
    ]

    t0 = time.perf_counter()
    exit_code = 0
    crashed = False
    timed_out = False
    stdout_text = ""

    try:
        proc = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=timeout_sec + 20,
            encoding="utf-8",
            errors="replace",
        )
        exit_code = proc.returncode
        stdout_text = proc.stdout
    except subprocess.TimeoutExpired as e:
        timed_out = True
        exit_code = -1
        stdout_text = e.stdout if e.stdout else "TIMEOUT"
    except Exception as e:
        crashed = True
        exit_code = -2
        stdout_text = str(e)

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
    min_viol_mm = violations_info.get("min_violation_mm", None)
    max_viol_mm = violations_info.get("max_violation_mm", None)
    avg_viol_mm = violations_info.get("avg_violation_mm", None)
    score_val = manifest_data.get("normalized_score", None)
    final_state = manifest_data.get("final_state", "FAILED" if (crashed or timed_out) else "COMPLETED")
    phases = manifest_data.get("phases", {})
    resources = manifest_data.get("resource_usage", {})

    b_board = fixture_meta.get("board", {})
    b_cad = fixture_meta.get("cad", {})
    tier = fixture_meta.get("tier", "B")

    cache_key = f"{board_id}--unrouted--{version_label}--{git_sha}"

    run_record = {
        "cache_key": cache_key,
        "run_at": datetime.now(timezone.utc).isoformat(),
        "run_mode": "CLI",
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
            "final_unrouted": unrouted_count,
            "clearance_violations": violations_count,
            "min_violation_mm": min_viol_mm,
            "max_violation_mm": max_viol_mm,
            "avg_violation_mm": avg_viol_mm,
            "quality_score": score_val,
            "wall_clock_seconds": wall_time,
            "cpu_seconds": resources.get("cpu_time", 0.0),
            "peak_heap_mb": resources.get("peak_memory", 0.0),
            "total_allocated_gb": resources.get("max_memory", 0.0),
        },
        "drc": {
            "final_unrouted": unrouted_count,
            "summary_violations": violations_count,
            "min_violation_mm": min_viol_mm,
            "max_violation_mm": max_viol_mm,
            "avg_violation_mm": avg_viol_mm,
            "final_quality_score": score_val,
        },
        "log_analysis": {
            "warn_count": stdout_text.count("WARN"),
            "error_count": stdout_text.count("ERROR"),
            "load_error": "Loading board file" not in stdout_text and exit_code != 0,
            "exceptions": stdout_text.count("Exception"),
            "timed_out": timed_out,
            "metric_source": "manifest" if manifest_data else "log",
        },
        "exit": {
            "code": exit_code,
            "crashed": crashed,
            "oom_detected": "OutOfMemoryError" in stdout_text,
            "timed_out": timed_out,
        },
        "log_file": str(log_path.relative_to(log_dir.parent.parent)),
        "result_json": str(manifest_path.relative_to(output_dir.parent.parent)) if manifest_path.exists() else None,
        "output_file": str(ses_path.relative_to(output_dir.parent.parent)) if ses_path.exists() else None,
        "schema_version": 2,
    }

    return run_record


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", default="scripts/benchmark/binaries/freerouting-current.jar", type=Path)
    parser.add_argument("--fixtures-dir", default="scripts/benchmark/fixtures/PCBench", type=Path)
    parser.add_argument("--tier", default="All", help="Filter by tier: 'A', 'B', 'C', 'D', or 'All'")
    parser.add_argument("--workers", default=4, type=int)
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
    bench_data: dict[str, Any] = {"schema_version": 2, "runs": []}
    if benchmarks_json.exists():
        try:
            bench_data = json.loads(benchmarks_json.read_text(encoding="utf-8"))
        except Exception:
            pass

    existing_runs = {r.get("cache_key"): r for r in bench_data.get("runs", [])}

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
                and r.get("quality", {}).get("final_unrouted") is not None
                for r in existing_runs.values()
            )
            if is_already_run:
                already_completed += 1
                continue

        if (b_dir / "unrouted.dsn").exists():
            tasks.append((jar_path, b_dir, output_dir, log_dir, budget, args.version_label, git_sha, jar_sha256, jar_size))

    print(
        f"Starting PCBench Corpus Benchmark ({len(boards)} total, {already_completed} already cached, "
        f"{len(tasks)} remaining to run, Tier={args.tier}, Workers={args.workers})...",
        flush=True,
    )

    if not tasks:
        print("All requested boards are already benchmarked in benchmarks.json! Regenerating reports...", flush=True)
        subprocess.run(["powershell", "-ExecutionPolicy", "Bypass", "-File", "scripts/benchmark/run-benchmarks.ps1", "-ReportOnly"], check=False)
        return 0

    completed = 0
    t_start = time.perf_counter()

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        future_to_board = {executor.submit(route_single_board, *task): task[1].name for task in tasks}
        for future in concurrent.futures.as_completed(future_to_board):
            completed += 1
            b_name = future_to_board[future]
            try:
                rec = future.result()
                existing_runs[rec["cache_key"]] = rec
                q = rec["quality"]
                unr = q.get("final_unrouted")
                viol = q.get("clearance_violations")
                sec = q.get("wall_clock_seconds")
                status = f"CLEAN ({sec}s)" if (unr == 0 and viol == 0) else f"unr={unr}, viol={viol} ({sec}s)"
                print(f"[{completed}/{len(tasks)}] {b_name}: {status}", flush=True)

                # Incremental flush every 25 boards
                if completed % 25 == 0 or completed == len(tasks):
                    bench_data["runs"] = list(existing_runs.values())
                    bench_data["total_runs"] = len(bench_data["runs"])
                    bench_data["generated_at"] = datetime.now(timezone.utc).isoformat()
                    benchmarks_json.write_text(json.dumps(bench_data, indent=2), encoding="utf-8")

            except Exception as e:
                print(f"[{completed}/{len(tasks)}] {b_name}: ERROR {e}", flush=True)

    # Final save
    bench_data["runs"] = list(existing_runs.values())
    bench_data["total_runs"] = len(bench_data["runs"])
    bench_data["generated_at"] = datetime.now(timezone.utc).isoformat()
    benchmarks_json.write_text(json.dumps(bench_data, indent=2), encoding="utf-8")

    total_time = round(time.perf_counter() - t_start, 1)
    print(f"\nPCBench Corpus Benchmark completed {len(tasks)} boards in {total_time}s.", flush=True)

    # Regenerate reports
    print("Regenerating Markdown and HTML reports...", flush=True)
    subprocess.run(["powershell", "-ExecutionPolicy", "Bypass", "-File", "scripts/benchmark/run-benchmarks.ps1", "-ReportOnly"], check=False)

    return 0


if __name__ == "__main__":
    sys.exit(main())
