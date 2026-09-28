#!/usr/bin/env python3
"""Replay optimizer V2 scores onto current-tree snapshots with chosen weights."""

from __future__ import annotations

import argparse
import csv
import json
import math
from collections import defaultdict
from pathlib import Path
from typing import Any

LENGTH_FLOOR = 1.0
DIFFICULTY_FLOOR = 1.0
LABEL = "v2.4.2-SNAPSHOT"


def n(value: Any) -> float | None:
    if value is None:
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def g(obj: Any, *keys: str) -> Any:
    current = obj
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def difficulty_d(stats: dict[str, Any] | None) -> float:
    if not stats:
        return 1.0
    difficulty = stats.get("difficulty") or {}
    pin = n(difficulty.get("pin_count"))
    if pin is None:
        pin = n(g(stats, "items", "pin_count")) or 0
    layers = n(difficulty.get("signal_layer_count"))
    if layers is None:
        layers = n(g(stats, "layers", "signal_count")) or 0
    return max(1.0, pin * layers)


def bounds(stats: dict[str, Any] | None) -> dict[str, float] | None:
    payload = (stats or {}).get("bounds") or {}
    length = n(payload.get("min_trace_length_mm"))
    vias = n(payload.get("min_via_count"))
    bends = n(payload.get("min_bend_count"))
    if length is None and vias is None and bends is None:
        return None
    return {
        "Lmin": 0.0 if length is None else length,
        "Vmin": 0.0 if vias is None else vias,
        "Bmin": 0.0 if bends is None else bends,
    }


def opt_score(
    stats: dict[str, Any] | None,
    bnds: dict[str, float] | None,
    w_l: float,
    w_v: float,
    w_b: float,
) -> tuple[float | None, dict[str, float] | None]:
    if not stats or not bnds:
        return None, None
    length = n(g(stats, "traces", "total_length_mm"))
    vias = n(g(stats, "vias", "total_count"))
    bends = n(g(stats, "bends", "total_count"))
    if length is None or vias is None or bends is None:
        return None, None
    scale = max(difficulty_d(stats), DIFFICULTY_FLOOR)
    d_length = w_l * max(0.0, length - bnds["Lmin"]) / max(bnds["Lmin"], LENGTH_FLOOR)
    d_via = w_v * max(0.0, vias - bnds["Vmin"]) / scale
    d_bend = w_b * max(0.0, bends - bnds["Bmin"]) / scale
    score = max(0.0, 1000.0 - d_length - d_via - d_bend)
    return score, {"dL": d_length, "dV": d_via, "dB": d_bend, "D": scale}


def pct(values: list[float], p: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round((p / 100) * (len(ordered) - 1))))
    return ordered[index]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wl", type=float, required=True)
    parser.add_argument("--wv", type=float, required=True)
    parser.add_argument("--wb", type=float, required=True)
    parser.add_argument(
        "--json",
        type=Path,
        default=Path("scripts/benchmark/results/benchmarks.json"),
    )
    parser.add_argument("--csv", type=Path)
    args = parser.parse_args()
    csv_path = args.csv or Path(
        f"docs/research/optimizer_v2_weights_WL{int(args.wl)}_WV{int(args.wv)}_WB{int(args.wb)}.csv"
    )

    payload = json.loads(args.json.read_text(encoding="utf-8"))
    candidates: list[dict[str, Any]] = []
    for run in payload["runs"]:
        label = run.get("version_label") or (run.get("binary") or {}).get("version_label")
        if label != LABEL:
            continue
        fixture = run.get("fixture") or {}
        path = fixture.get("relative_path") if isinstance(fixture, dict) else None
        if not path:
            continue
        optimizer = (run.get("phases") or {}).get("optimizer") or {}
        before = optimizer.get("before")
        after = optimizer.get("after")
        if not isinstance(before, dict) or not isinstance(after, dict):
            continue
        before_stats = before.get("board_statistics")
        after_stats = after.get("board_statistics")
        bnds = bounds(after_stats) or bounds(before_stats)
        score_before, pen_before = opt_score(before_stats, bnds, args.wl, args.wv, args.wb)
        score_after, pen_after = opt_score(after_stats, bnds, args.wl, args.wv, args.wb)
        if score_before is None or score_after is None or pen_before is None or pen_after is None:
            continue
        candidates.append(
            {
                "relative_path": path,
                "board": Path(path).parent.name,
                "tier": fixture.get("tier"),
                "run_at": run.get("run_at"),
                "optimizer_cpu_seconds": n(optimizer.get("cpu_seconds")),
                "optimizer_passes": optimizer.get("passes_completed"),
                "score_before": round(score_before, 2),
                "score_after": round(score_after, 2),
                "delta": round(score_after - score_before, 2),
                "dL_before": round(pen_before["dL"], 2),
                "dV_before": round(pen_before["dV"], 2),
                "dB_before": round(pen_before["dB"], 2),
                "dL_after": round(pen_after["dL"], 2),
                "dV_after": round(pen_after["dV"], 2),
                "dB_after": round(pen_after["dB"], 2),
                "D": round(pen_before["D"], 2),
                "incomplete_after": n(g(after_stats, "connections", "incomplete_count")),
                "drc_after": n(g(after_stats, "clearance_violations", "total_count")),
                "Lmin": round(bnds["Lmin"], 2),
                "Vmin": int(bnds["Vmin"]),
                "Bmin": int(bnds["Bmin"]),
            }
        )

    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in candidates:
        grouped[row["relative_path"]].append(row)
    rows = []
    for items in grouped.values():
        items.sort(key=lambda item: item.get("run_at") or "", reverse=True)
        rows.append(items[0])
    rows.sort(key=lambda row: (row["score_after"], row["board"]))

    csv_path.parent.mkdir(parents=True, exist_ok=True)
    with csv_path.open("w", encoding="utf-8", newline="\n") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    before_scores = [row["score_before"] for row in rows]
    after_scores = [row["score_after"] for row in rows]
    deltas = [row["delta"] for row in rows]
    zeros = [row for row in rows if row["score_after"] <= 0]
    print(f"weights WL={args.wl} WV={args.wv} WB={args.wb}")
    print("unique_boards", len(rows))
    print("tiers", {tier: sum(1 for row in rows if row["tier"] == tier) for tier in "ABCD"})
    print(
        "before min/p10/p50/p90/max",
        min(before_scores),
        pct(before_scores, 10),
        pct(before_scores, 50),
        pct(before_scores, 90),
        max(before_scores),
    )
    print(
        "after min/p10/p50/p90/max",
        min(after_scores),
        pct(after_scores, 10),
        pct(after_scores, 50),
        pct(after_scores, 90),
        max(after_scores),
    )
    print(
        "delta min/p10/p50/p90/max",
        min(deltas),
        pct(deltas, 10),
        pct(deltas, 50),
        pct(deltas, 90),
        max(deltas),
    )
    print(
        "improved",
        sum(1 for delta in deltas if delta > 0.005),
        "same",
        sum(1 for delta in deltas if abs(delta) <= 0.005),
        "worse",
        sum(1 for delta in deltas if delta < -0.005),
    )
    print("score_after==0", len(zeros))
    print("score_after>=900", sum(1 for row in rows if row["score_after"] >= 900))
    if zeros:
        print(
            "zero mean dL/dV/dB",
            round(sum(row["dL_after"] for row in zeros) / len(zeros), 1),
            round(sum(row["dV_after"] for row in zeros) / len(zeros), 1),
            round(sum(row["dB_after"] for row in zeros) / len(zeros), 1),
        )
    print("all mean dL/dV/dB",
          round(sum(row["dL_after"] for row in rows) / len(rows), 1),
          round(sum(row["dV_after"] for row in rows) / len(rows), 1),
          round(sum(row["dB_after"] for row in rows) / len(rows), 1),
          )
    print("wrote", csv_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
