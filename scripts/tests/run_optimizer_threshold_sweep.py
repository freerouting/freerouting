import os
import sys
import json
import csv
import time
import subprocess
from pathlib import Path

FIXTURES = [
    {
        "id": "ili9341_breakout",
        "name": "kicad-projects_ili9341-breakout",
        "path": "scripts/benchmark/fixtures/PCBench/kicad-projects_ili9341-breakout/unrouted.dsn"
    },
    {
        "id": "autosave_postcard",
        "name": "kitspace__autosave-postcard",
        "path": "scripts/benchmark/fixtures/PCBench/kitspace__autosave-postcard/unrouted.dsn"
    },
    {
        "id": "neowall",
        "name": "NeoWall_NeoWall",
        "path": "scripts/benchmark/fixtures/PCBench/NeoWall_NeoWall/unrouted.dsn"
    },
    {
        "id": "chaos_looper",
        "name": "kitspace_ChaosLooper",
        "path": "scripts/benchmark/fixtures/PCBench/kitspace_ChaosLooper/unrouted.dsn"
    },
    {
        "id": "motor_controllers",
        "name": "newer-motor-controllers_design3",
        "path": "scripts/benchmark/fixtures/PCBench/newer-motor-controllers_design3/unrouted.dsn"
    }
]

THRESHOLDS = [1.0, 1.2, 1.5, 1.8, 2.0, 2.2, 2.5, 3.0, 3.5, 4.0]
JAR_PATH = Path("build/libs/freerouting-current-executable.jar").resolve()
OUTPUT_DIR = Path("build/threshold_sweep").resolve()
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

def run_single(fixture, threshold):
    fix_id = fixture["id"]
    dsn_path = Path(fixture["path"]).resolve()
    ses_path = OUTPUT_DIR / f"{fix_id}_t{threshold}.ses"
    manifest_path = OUTPUT_DIR / f"{fix_id}_t{threshold}_result.json"
    log_path = OUTPUT_DIR / f"{fix_id}_t{threshold}.log"

    cmd = [
        "java",
        "-jar", str(JAR_PATH),
        "--gui.enabled=false",
        "--api_server.enabled=false",
        "--mcp_server.enabled=false",
        "--router.max_threads=1",
        "-de", str(dsn_path),
        "-do", str(ses_path),
        f"--router.optimizer.improvement_threshold={threshold}",
        f"--router.result_json={manifest_path}",
        f"--logging.file.location={log_path}"
    ]

    t0 = time.perf_counter()
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
    elapsed = time.perf_counter() - t0

    if not manifest_path.exists():
        print(f"  FAILED: {fixture['name']} at {threshold}% - no result json")
        return None

    try:
        with open(manifest_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print(f"  ERROR reading manifest: {e}")
        return None

    opt_phase = data.get("phases", {}).get("optimizer", {})
    b_before = opt_phase.get("before", {}).get("board_statistics", {})
    b_after = opt_phase.get("after", {}).get("board_statistics", {})

    vias_before = b_before.get("vias", {}).get("total_count", 0)
    vias_after = b_after.get("vias", {}).get("total_count", 0)
    vias_cut = vias_before - vias_after

    len_before = b_before.get("traces", {}).get("total_length_mm", 0.0)
    len_after = b_after.get("traces", {}).get("total_length_mm", 0.0)
    len_cut = len_before - len_after

    score_before = opt_phase.get("before", {}).get("score", 0.0)
    score_after = opt_phase.get("after", {}).get("score", 0.0)
    score_gain = score_after - score_before

    passes = opt_phase.get("passes_completed", 0)
    opt_duration = opt_phase.get("duration_seconds", 0.0)
    auto_duration = data.get("phases", {}).get("autorouter", {}).get("duration_seconds", 0.0)

    return {
        "fixture_id": fix_id,
        "fixture_name": fixture["name"],
        "threshold": threshold,
        "passes": passes,
        "auto_duration_s": auto_duration,
        "opt_duration_s": opt_duration,
        "total_duration_s": elapsed,
        "vias_before": vias_before,
        "vias_after": vias_after,
        "vias_cut": vias_cut,
        "len_before_mm": len_before,
        "len_after_mm": len_after,
        "len_cut_mm": len_cut,
        "score_before": score_before,
        "score_after": score_after,
        "score_gain": score_gain,
        "incomplete_count": data.get("board_statistics", {}).get("connections", {}).get("incomplete_count", 0),
        "clearance_violations": data.get("board_statistics", {}).get("clearance_violations", {}).get("total_count", 0)
    }

def main():
    print(f"Starting Optimizer Threshold Sweep on {len(FIXTURES)} fixtures x {len(THRESHOLDS)} thresholds...")
    print(f"Thresholds: {THRESHOLDS}")
    print(f"Executable JAR: {JAR_PATH}")
    print("-" * 75)

    all_results = []
    for threshold in THRESHOLDS:
        print(f"\n>>> Running Threshold: {threshold}% (threshold={threshold/100.0:.4f})")
        t_start = time.perf_counter()
        t_results = []
        for fixture in FIXTURES:
            print(f"  Running {fixture['name']}...", end="", flush=True)
            res = run_single(fixture, threshold)
            if res:
                t_results.append(res)
                all_results.append(res)
                print(f" Done in {res['opt_duration_s']:.1f}s | Passes: {res['passes']} | Vias: {res['vias_before']}->{res['vias_after']} (-{res['vias_cut']}) | Score: {res['score_before']:.1f}->{res['score_after']:.1f}")
            else:
                print(" FAILED")

        t_elapsed = time.perf_counter() - t_start
        tot_passes = sum(r["passes"] for r in t_results)
        tot_vias_cut = sum(r["vias_cut"] for r in t_results)
        tot_opt_time = sum(r["opt_duration_s"] for r in t_results)
        print(f"  Summary for {threshold}%: Opt Time={tot_opt_time:.1f}s (Wall={t_elapsed:.1f}s) | Passes={tot_passes} | Vias Cut={tot_vias_cut}")

    # Save raw JSON
    raw_path = Path("docs/research/optimizer_threshold_sweep_raw.json")
    with open(raw_path, "w", encoding="utf-8") as f:
        json.dump(all_results, f, indent=2)
    print(f"\nRaw results saved to {raw_path}")

    # Save CSV
    csv_path = Path("docs/research/optimizer_threshold_sweep_results.csv")
    if all_results:
        keys = list(all_results[0].keys())
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=keys)
            writer.writeheader()
            writer.writerows(all_results)
        print(f"CSV saved to {csv_path}")

    # Print Cumulative Summary Table
    print("\n" + "=" * 95)
    print("CUMULATIVE RESULTS ACROSS ALL 5 FIXTURES")
    print("=" * 95)
    base_10_time = None
    base_10_vias = None

    summary_rows = []
    for threshold in THRESHOLDS:
        t_res = [r for r in all_results if r["threshold"] == threshold]
        tot_passes = sum(r["passes"] for r in t_res)
        tot_opt_time = sum(r["opt_duration_s"] for r in t_res)
        tot_total_time = sum(r["total_duration_s"] for r in t_res)
        tot_vias_before = sum(r["vias_before"] for r in t_res)
        tot_vias_after = sum(r["vias_after"] for r in t_res)
        tot_vias_cut = sum(r["vias_cut"] for r in t_res)
        tot_len_cut = sum(r["len_cut_mm"] for r in t_res)
        tot_score_gain = sum(r["score_gain"] for r in t_res)

        if threshold == 1.0:
            base_10_time = tot_opt_time
            base_10_vias = tot_vias_cut

        time_saved_pct = ((base_10_time - tot_opt_time) / base_10_time * 100) if base_10_time else 0.0
        via_retention_pct = (tot_vias_cut / base_10_vias * 100) if base_10_vias else 100.0

        summary_rows.append({
            "threshold": threshold,
            "passes": tot_passes,
            "opt_time_s": tot_opt_time,
            "time_saved_pct": time_saved_pct,
            "vias_start": tot_vias_before,
            "vias_end": tot_vias_after,
            "vias_cut": tot_vias_cut,
            "via_retention_pct": via_retention_pct,
            "len_cut_mm": tot_len_cut,
            "score_gain": tot_score_gain
        })

    print(f"{'Threshold':>10} | {'Passes':>6} | {'Opt Time (s)':>12} | {'Time Saved':>10} | {'Vias Cut':>10} | {'Via Retain':>10} | {'Len Cut (mm)':>12} | {'Score Gain':>10}")
    print("-" * 95)
    for sr in summary_rows:
        print(f"{sr['threshold']:>9.1f}% | {sr['passes']:>6} | {sr['opt_time_s']:>11.1f}s | {sr['time_saved_pct']:>9.1f}% | {sr['vias_cut']:>4d} / {sr['vias_start']:<3d} | {sr['via_retention_pct']:>9.1f}% | {sr['len_cut_mm']:>11.1f}mm | {sr['score_gain']:>10.1f}")
    print("=" * 95)

if __name__ == "__main__":
    main()
