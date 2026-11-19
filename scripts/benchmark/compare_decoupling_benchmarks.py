#!/usr/bin/env python3
"""Benchmark comparison: Decoupling Capacitor Routing vs Baseline."""

from __future__ import annotations

import os
import re
import subprocess
import sys
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
JAR_PATH = REPO_ROOT / "build" / "libs" / "freerouting-current-executable.jar"
OUTPUT_DIR = REPO_ROOT / "build" / "benchmark_outputs"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

FIXTURES = [
    # Top capacitor-rich large boards from PCBench and fixtures
    REPO_ROOT / "scripts" / "benchmark" / "fixtures" / "PCBench" / "DAC-ADAU1966_DAC-ADAU1966" / "unrouted.dsn",
    REPO_ROOT / "scripts" / "benchmark" / "fixtures" / "PCBench" / "DSP-ADAU1452_DSP-ADAU1452" / "unrouted.dsn",
    REPO_ROOT / "scripts" / "benchmark" / "fixtures" / "PCBench" / "CoreOne-xCORE200-Original_CoreOne" / "unrouted.dsn",
    REPO_ROOT / "scripts" / "benchmark" / "fixtures" / "PCBench" / "kitspace_OtterCastAudioV2" / "unrouted.dsn",
    REPO_ROOT / "fixtures" / "Issue145-smoothieboard.dsn",
    REPO_ROOT / "fixtures" / "Plane-via-adc-decoupling-4layer.dsn",
]


def run_board(board_path: Path, decoupling_enabled: bool, max_passes: int = 2) -> dict:
    mode_name = "WITH_DECOUPLING" if decoupling_enabled else "WITHOUT_DECOUPLING"
    board_name = board_path.stem if board_path.stem != "unrouted" else board_path.parent.name
    out_ses = OUTPUT_DIR / f"{board_name}_{mode_name}.ses"
    
    cmd = [
        "java",
        "-jar",
        str(JAR_PATH),
        "-de",
        str(board_path),
        "-do",
        str(out_ses),
        f"--router.autorouter.max_passes={max_passes}",
        "--router.optimizer.max_passes=1",
        f"--router.decoupling.enabled={'true' if decoupling_enabled else 'false'}",
        "--router.decoupling.include_review_candidates=true",
    ]
    
    start_time = time.perf_counter()
    proc = subprocess.run(cmd, capture_output=True, text=True, cwd=str(REPO_ROOT))
    elapsed_time = time.perf_counter() - start_time
    
    output = proc.stdout + "\n" + proc.stderr
    
    # Parse log metrics
    res = {
        "board": board_name,
        "mode": mode_name,
        "exit_code": proc.returncode,
        "elapsed_sec": round(elapsed_time, 2),
        "decoupling_straps": 0,
        "decoupling_vias": 0,
        "decoupling_length_mm": 0.0,
        "unrouted": None,
        "clearance_violations": None,
        "router_score": None,
        "optimizer_score": None,
        "peak_heap_mb": None,
    }
    
    # Decoupling summary
    m_dec = re.search(r"Decoupling routing complete:\s*(\d+)\s*eligible candidates,\s*(\d+)\s*straps routed,.*?(\d+)\s*decoupling vias placed.*?Total strap length:\s*([\d\.]+)\s*mm", output)
    if m_dec:
        res["decoupling_straps"] = int(m_dec.group(2))
        res["decoupling_vias"] = int(m_dec.group(3))
        res["decoupling_length_mm"] = float(m_dec.group(4))
        
    # Auto-routing completion
    m_auto = re.findall(r"Auto-routing pass #(\d+).*?with score ([\d\.]+)\s*\((\d+)\s*unrouted and (\d+)\s*violations\)", output)
    if m_auto:
        last_pass = m_auto[-1]
        res["router_score"] = float(last_pass[1])
        res["unrouted"] = int(last_pass[2])
        res["clearance_violations"] = int(last_pass[3])
        
    # Optimization completion
    m_opt = re.findall(r"Optimization was completed.*?with the score of ([\d\.]+)\s*\((\d+)\s*unrouted and (\d+)\s*violations\)", output)
    if m_opt:
        res["optimizer_score"] = float(m_opt[-1][0])
        res["unrouted"] = int(m_opt[-1][1])
        res["clearance_violations"] = int(m_opt[-1][2])
        
    # Peak heap
    m_heap = re.findall(r"([\d\.]+)\s*MB peak heap usage", output)
    if m_heap:
        res["peak_heap_mb"] = float(m_heap[-1])
        
    if out_ses.exists():
        try:
            out_ses.unlink()
        except OSError:
            pass
            
    return res


def main():
    print(f"Starting Freerouting Benchmark: Decoupling Routing vs Baseline (1 iteration per config)")
    print(f"Binary: {JAR_PATH}")
    print(f"Boards: {[b.stem for b in FIXTURES]}\n")
    
    results = []
    
    for board_path in FIXTURES:
        if not board_path.exists():
            print(f"Warning: {board_path} not found, skipping.")
            continue
            
        print(f"=== Benchmarking {board_path.stem} ===")
        print("  Running WITHOUT decoupling...")
        res_baseline = run_board(board_path, decoupling_enabled=False, max_passes=2)
        print(f"    Elapsed: {res_baseline['elapsed_sec']}s, Unrouted: {res_baseline['unrouted']}, DRC Violations: {res_baseline['clearance_violations']}, Peak Heap: {res_baseline['peak_heap_mb']} MB")
        
        print("  Running WITH decoupling...")
        res_decoupling = run_board(board_path, decoupling_enabled=True, max_passes=2)
        print(f"    Elapsed: {res_decoupling['elapsed_sec']}s, Straps Routed: {res_decoupling['decoupling_straps']}, Vias Placed: {res_decoupling['decoupling_vias']}, Unrouted: {res_decoupling['unrouted']}, DRC Violations: {res_decoupling['clearance_violations']}, Peak Heap: {res_decoupling['peak_heap_mb']} MB")
        print()
        
        results.append((res_baseline, res_decoupling))
        
    print("\n" + "=" * 80)
    print("FINAL BENCHMARK COMPARISON MATRIX")
    print("=" * 80)
    header = f"{'Board':<35} | {'Mode':<18} | {'Time(s)':<8} | {'Straps':<7} | {'Vias':<5} | {'Unrouted':<9} | {'DRC':<4} | {'Peak Heap':<10}"
    print(header)
    print("-" * len(header))
    
    for r_base, r_dec in results:
        b_name = r_base["board"]
        print(f"{b_name:<35} | {'WITHOUT_DECOUPLING':<18} | {r_base['elapsed_sec']:<8.2f} | {'-':<7} | {'-':<5} | {str(r_base['unrouted']):<9} | {str(r_base['clearance_violations']):<4} | {str(r_base['peak_heap_mb']) + ' MB':<10}")
        print(f"{'':<35} | {'WITH_DECOUPLING':<18} | {r_dec['elapsed_sec']:<8.2f} | {str(r_dec['decoupling_straps']):<7} | {str(r_dec['decoupling_vias']):<5} | {str(r_dec['unrouted']):<9} | {str(r_dec['clearance_violations']):<4} | {str(r_dec['peak_heap_mb']) + ' MB':<10}")
        print("-" * len(header))


if __name__ == "__main__":
    main()
