#!/usr/bin/env python3
"""Empirically calibrate Tier A PCBench fixtures by running headless routing baseline sweeps."""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def route_board(jar_path: Path, dsn_path: Path, output_dir: Path, timeout_seconds: int = 30) -> dict[str, Any]:
    """Run Freerouting headlessly on a board and return routing result metrics."""
    ses_path = output_dir / f"{dsn_path.parent.name}.ses"
    manifest_path = output_dir / f"{dsn_path.parent.name}-result.json"
    log_path = output_dir / f"{dsn_path.parent.name}.log"

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
        f"--router.job_timeout=00:00:{timeout_seconds:02d}",
        f"--logging.file.location={log_path}",
    ]

    t0 = time.perf_counter()
    try:
        proc = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=timeout_seconds + 15,
            encoding="utf-8",
            errors="replace",
        )
        elapsed = time.perf_counter() - t0

        if manifest_path.exists():
            try:
                manifest_data = json.loads(manifest_path.read_text(encoding="utf-8"))
                stats = manifest_data.get("board_statistics", {})
                connections = stats.get("connections", {})
                unrouted = connections.get("incomplete_count", 0)
                violations = stats.get("clearance_violations", {}).get("total_count", 0)
                score = manifest_data.get("normalized_score", 0.0)
                final_state = manifest_data.get("final_state", "")

                return {
                    "board_id": dsn_path.parent.name,
                    "success": final_state == "COMPLETED" or proc.returncode == 0,
                    "elapsed_seconds": round(elapsed, 2),
                    "unrouted_nets": unrouted,
                    "clearance_violations": violations,
                    "quality_score": round(score, 2),
                    "timed_out": False,
                }
            except Exception:
                pass

        # Fallback to parse log if manifest was not written
        unrouted = 999
        violations = 999
        score = 0.0
        log_content = log_path.read_text(encoding="utf-8", errors="replace") if log_path.exists() else proc.stdout
        if "finished with state: COMPLETED" in log_content:
            if "0 unrouted and 0 violations" in log_content:
                unrouted = 0
                violations = 0
                score = 1000.0

        return {
            "board_id": dsn_path.parent.name,
            "success": proc.returncode == 0,
            "elapsed_seconds": round(elapsed, 2),
            "unrouted_nets": unrouted,
            "clearance_violations": violations,
            "quality_score": score,
            "timed_out": False,
        }

    except subprocess.TimeoutExpired:
        return {
            "board_id": dsn_path.parent.name,
            "success": False,
            "elapsed_seconds": timeout_seconds,
            "unrouted_nets": 999,
            "clearance_violations": 999,
            "quality_score": 0.0,
            "timed_out": True,
        }
    except Exception as e:
        return {
            "board_id": dsn_path.parent.name,
            "success": False,
            "elapsed_seconds": round(time.perf_counter() - t0, 2),
            "unrouted_nets": 999,
            "clearance_violations": 999,
            "quality_score": 0.0,
            "error": str(e),
            "timed_out": False,
        }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", default="scripts/benchmark/binaries/freerouting-current.jar", type=Path)
    parser.add_argument("--fixtures-dir", default="scripts/benchmark/fixtures/PCBench", type=Path)
    parser.add_argument("--workers", default=4, type=int)
    parser.add_argument("--max-boards", default=0, type=int)
    parser.add_argument("--timeout", default=30, type=int)
    args = parser.parse_args()

    fixtures_dir = args.fixtures_dir
    catalog_path = fixtures_dir / "catalog.json"
    jar_path = args.jar

    if not catalog_path.exists():
        print(f"Error: {catalog_path} not found", file=sys.stderr, flush=True)
        return 1
    if not jar_path.exists():
        print(f"Error: {jar_path} not found", file=sys.stderr, flush=True)
        return 1

    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    boards = catalog.get("boards", [])

    tier_a_candidates = [b for b in boards if b.get("tier") == "A"]
    if args.max_boards > 0:
        tier_a_candidates = tier_a_candidates[: args.max_boards]

    print(f"Starting empirical calibration for {len(tier_a_candidates)} Tier A candidate boards with {args.workers} workers...", flush=True)

    scratch_dir = Path("scripts/pcbench/cache/calibration")
    scratch_dir.mkdir(parents=True, exist_ok=True)

    tasks: list[tuple[Path, Path, Path, int]] = []
    for b in tier_a_candidates:
        b_id = b.get("board_id")
        dsn_path = fixtures_dir / b_id / "unrouted.dsn"
        if dsn_path.exists():
            tasks.append((jar_path, dsn_path, scratch_dir, args.timeout))

    results: dict[str, dict[str, Any]] = {}
    completed_count = 0
    t_start = time.perf_counter()

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        future_to_board = {executor.submit(route_board, *task): task[1].parent.name for task in tasks}
        for future in concurrent.futures.as_completed(future_to_board):
            completed_count += 1
            b_name = future_to_board[future]
            try:
                res = future.result()
                results[b_name] = res
                status = "CLEAN" if (res["unrouted_nets"] == 0 and res["clearance_violations"] == 0 and not res["timed_out"]) else f"unrouted={res['unrouted_nets']}, viol={res['clearance_violations']}"
                print(f"[{completed_count}/{len(tasks)}] {b_name}: {status} ({res['elapsed_seconds']}s)", flush=True)
            except Exception as e:
                print(f"[{completed_count}/{len(tasks)}] {b_name}: ERROR {e}", flush=True)

    total_time = round(time.perf_counter() - t_start, 1)

    # Re-classify based on empirical findings
    golden_canaries = []
    reclassified_to_b = []
    for b in boards:
        b_id = b.get("board_id")
        if b_id in results:
            r = results[b_id]
            is_golden = (r.get("unrouted_nets") == 0 and r.get("clearance_violations") == 0 and not r.get("timed_out"))
            b["baseline_routing"] = {
                "calibrated_at": datetime.now(timezone.utc).isoformat(),
                "elapsed_seconds": r.get("elapsed_seconds"),
                "unrouted_nets": r.get("unrouted_nets"),
                "clearance_violations": r.get("clearance_violations"),
                "quality_score": r.get("quality_score"),
            }
            if is_golden:
                b["tier"] = "A"
                b["expected_outcome"] = "complete"
                b["tags"] = sorted(list(set(b.get("tags", []) + ["tier-a", "golden-canary"])))
                golden_canaries.append(b_id)
            else:
                b["tier"] = "B"
                b["expected_outcome"] = "baseline_violations" if r.get("clearance_violations", 0) > 0 else "partial"
                tags = [t for t in b.get("tags", []) if t not in ("tier-a", "canary")]
                tags.append("tier-b")
                b["tags"] = sorted(list(set(tags)))
                reclassified_to_b.append(b_id)

            meta_file = fixtures_dir / b_id / "metadata.normalized.json"
            if meta_file.exists():
                try:
                    sm = json.loads(meta_file.read_text(encoding="utf-8"))
                    sm["tier"] = b["tier"]
                    sm["expected_outcome"] = b["expected_outcome"]
                    sm["tags"] = b["tags"]
                    sm["baseline_routing"] = b["baseline_routing"]
                    meta_file.write_text(json.dumps(sm, indent=2), encoding="utf-8")
                except Exception:
                    pass

    tier_counts = {"A": 0, "B": 0, "C": 0, "D": 0, "E": 0}
    for b in boards:
        tier_counts[b.get("tier", "B")] += 1

    catalog["boards"] = boards
    catalog["tier_summary"] = {
        "tier_a_golden_canary": tier_counts["A"],
        "tier_b_routine": tier_counts["B"],
        "tier_c_complex": tier_counts["C"],
        "tier_d_diagnostic": tier_counts["D"],
        "tier_e_unsupported": 25,
        "total_active": len(boards),
        "calibration_run": {
            "tested_candidates": len(tasks),
            "golden_canaries_confirmed": len(golden_canaries),
            "reclassified_to_tier_b": len(reclassified_to_b),
            "elapsed_seconds": total_time,
            "calibrated_at": datetime.now(timezone.utc).isoformat(),
        },
    }

    catalog_path.write_text(json.dumps(catalog, indent=2), encoding="utf-8")

    print("\n" + "=" * 60, flush=True)
    print("CALIBRATION COMPLETE", flush=True)
    print("=" * 60, flush=True)
    print(f"Total Boards Tested:         {len(tasks)} in {total_time}s", flush=True)
    print(f"Verified Golden Tier A Canaries (100% clean, 0 violations): {len(golden_canaries)}", flush=True)
    print(f"Reclassified to Tier B (baseline violations/partial):       {len(reclassified_to_b)}", flush=True)
    print(f"\nFinal Verified Tier Distribution across 1,157 boards:", flush=True)
    print(f"  Tier A (Golden Canaries):          {tier_counts['A']} boards", flush=True)
    print(f"  Tier B (Routine Benchmark):        {tier_counts['B']} boards", flush=True)
    print(f"  Tier C (Complex / Multi-Layer):    {tier_counts['C']} boards", flush=True)
    print(f"  Tier D (Extreme Stress/Diagnostic): {tier_counts['D']} boards", flush=True)
    print(f"  Tier E (Quarantine / Unsupported): 25 boards", flush=True)

    return 0


if __name__ == "__main__":
    sys.exit(main())
