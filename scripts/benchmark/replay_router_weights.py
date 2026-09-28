#!/usr/bin/env python3
"""Replay router V2 scores onto current-tree autorouter snapshots with chosen weights."""

from __future__ import annotations

import argparse
import csv
import json
import math
from collections import defaultdict
from pathlib import Path
from typing import Any

DIFFICULTY_FLOOR = 1.0
DEPTH_SCALE = 1000.0
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
    stored = n(difficulty.get("difficulty_d"))
    if stored and stored > 0:
        return max(1.0, stored)
    return max(1.0, pin * layers)


def unrouted_penalty(open_frac: float, w1: float, w2: float, split: float) -> float:
    split = min(1.0, max(0.0, split))
    if split <= 0.0:
        first_open, second_open = 0.0, open_frac
    elif split >= 1.0:
        first_open, second_open = open_frac, 0.0
    else:
        first_open = min(1.0, max(0.0, (open_frac - split) / (1.0 - split)))
        second_open = min(1.0, open_frac / split)
    return w1 * first_open + w2 * second_open


def router_score(
    stats: dict[str, Any] | None,
    w1: float,
    w2: float,
    w_c: float,
    w_d: float,
    split: float = 0.5,
) -> tuple[float | None, dict[str, float] | None]:
    if not stats:
        return None, None
    connections = n(g(stats, "connections", "maximum_count"))
    incomplete = n(g(stats, "connections", "incomplete_count"))
    viol_count = n(g(stats, "clearance_violations", "total_count"))
    viol_um = n(g(stats, "clearance_violations", "total_violation_um"))
    if connections is None or incomplete is None or viol_count is None or viol_um is None:
        return None, None
    scale = max(difficulty_d(stats), DIFFICULTY_FLOOR)
    open_frac = (incomplete / connections) if connections > 0 else 0.0
    unrouted = unrouted_penalty(open_frac, w1, w2, split) if connections > 0 else 0.0
    count = w_c * max(0.0, viol_count) / scale
    depth = w_d * max(0.0, viol_um) / DEPTH_SCALE / scale
    raw = 1000.0 - unrouted - count - depth
    return max(0.0, raw), {
        "unrouted": unrouted,
        "count": count,
        "depth": depth,
        "raw": raw,
        "D": scale,
        "incomplete": incomplete,
        "connections": connections,
        "viol_count": viol_count,
        "viol_um": viol_um,
        "open_frac": (incomplete / connections) if connections > 0 else 0.0,
    }


def pct(values: list[float], p: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round((p / 100) * (len(ordered) - 1))))
    return ordered[index]


def summarize(name: str, values: list[float]) -> None:
    if not values:
        print(name, "empty")
        return
    print(
        name,
        "min/p10/p50/p90/max/mean",
        round(min(values), 3),
        round(pct(values, 10), 3),
        round(pct(values, 50), 3),
        round(pct(values, 90), 3),
        round(max(values), 3),
        round(sum(values) / len(values), 3),
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--w1", type=float, default=1000.0 / 3.0)
    parser.add_argument("--w2", type=float, default=2000.0 / 3.0)
    parser.add_argument("--wc", type=float, default=25.0)
    parser.add_argument("--wd", type=float, default=300.0)
    parser.add_argument("--split", type=float, default=0.5)
    parser.add_argument(
        "--json",
        type=Path,
        default=Path("scripts/benchmark/results/benchmarks.json"),
    )
    parser.add_argument("--csv", type=Path)
    args = parser.parse_args()
    csv_path = args.csv or Path(
        f"docs/research/router_v2_weights_W1{int(round(args.w1))}_W2{int(round(args.w2))}_WC{int(args.wc)}_WD{int(args.wd)}.csv"
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
        autorouter = (run.get("phases") or {}).get("autorouter") or {}
        before = autorouter.get("before")
        after = autorouter.get("after")
        if not isinstance(before, dict) or not isinstance(after, dict):
            continue
        before_stats = before.get("board_statistics")
        after_stats = after.get("board_statistics")
        score_before, pen_before = router_score(
            before_stats, args.w1, args.w2, args.wc, args.wd, args.split
        )
        score_after, pen_after = router_score(
            after_stats, args.w1, args.w2, args.wc, args.wd, args.split
        )
        _, pen_after_wd1 = router_score(after_stats, args.w1, args.w2, args.wc, 1.0, args.split)
        score_linear, _ = router_score(after_stats, 500.0, 500.0, args.wc, args.wd, 0.5)
        score_before_linear, _ = router_score(
            before_stats, 500.0, 500.0, args.wc, args.wd, 0.5
        )
        score_hinge, _ = router_score(after_stats, 0.0, 1000.0, args.wc, args.wd, 0.5)
        score_before_hinge, _ = router_score(
            before_stats, 0.0, 1000.0, args.wc, args.wd, 0.5
        )
        if (
            score_before is None
            or score_after is None
            or pen_before is None
            or pen_after is None
            or pen_after_wd1 is None
            or score_linear is None
            or score_before_linear is None
            or score_hinge is None
            or score_before_hinge is None
        ):
            continue
        score_after_wd1 = max(
            0.0,
            1000.0 - pen_after_wd1["unrouted"] - pen_after_wd1["count"] - pen_after_wd1["depth"],
        )
        candidates.append(
            {
                "relative_path": path,
                "board": Path(path).parent.name,
                "tier": fixture.get("tier"),
                "run_at": run.get("run_at"),
                "score_before": round(score_before, 2),
                "score_before_linear": round(score_before_linear, 2),
                "score_after": round(score_after, 2),
                "score_after_linear": round(score_linear, 2),
                "score_before_hinge": round(score_before_hinge, 2),
                "score_after_hinge": round(score_hinge, 2),
                "score_after_wd1": round(score_after_wd1, 2),
                "delta": round(score_after - score_before, 2),
                "u_before": round(pen_before["unrouted"], 3),
                "u_after": round(pen_after["unrouted"], 3),
                "c_after": round(pen_after["count"], 3),
                "d_after": round(pen_after["depth"], 3),
                "d_after_wd1": round(pen_after_wd1["depth"], 3),
                "raw_after": round(pen_after["raw"], 3),
                "D": round(pen_after["D"], 2),
                "incomplete_after": pen_after["incomplete"],
                "connections": pen_after["connections"],
                "open_frac_before": round(pen_before["open_frac"], 4),
                "open_frac_after": round(pen_after["open_frac"], 4),
                "drc_after": pen_after["viol_count"],
                "viol_um_after": round(pen_after["viol_um"], 2),
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

    after_scores = [row["score_after"] for row in rows]
    wd1_scores = [row["score_after_wd1"] for row in rows]
    u_pen = [row["u_after"] for row in rows]
    c_pen = [row["c_after"] for row in rows]
    d_pen = [row["d_after"] for row in rows]
    dirty = [row for row in rows if row["drc_after"] > 0 or row["viol_um_after"] > 0]
    open_rows = [row for row in rows if row["incomplete_after"] > 0]
    floored = [row for row in rows if row["score_after"] <= 0]
    would_floor = [row for row in rows if row["raw_after"] < 0]
    print(f"weights W1={args.w1:.3f} W2={args.w2:.3f} WC={args.wc} WD={args.wd} split={args.split}")
    print("unique_boards", len(rows))
    print("tiers", {tier: sum(1 for row in rows if row["tier"] == tier) for tier in "ABCD"})
    summarize("score_before two-half", [row["score_before"] for row in rows])
    summarize("score_before hinge", [row["score_before_hinge"] for row in rows])
    summarize("score_before linear", [row["score_before_linear"] for row in rows])
    print(
        "before ==0",
        sum(1 for row in rows if row["score_before"] <= 0.005),
        "nonzero",
        sum(1 for row in rows if row["score_before"] > 0.005),
        "==1000",
        sum(1 for row in rows if row["score_before"] >= 999.995),
        "open>=0.5",
        sum(1 for row in rows if row["open_frac_before"] >= args.split - 1e-9),
    )
    summarize("score_after two-half", after_scores)
    summarize("score_after wd1", wd1_scores)
    summarize("score_after hinge", [row["score_after_hinge"] for row in rows])
    summarize("score_after linear", [row["score_after_linear"] for row in rows])
    print(
        "after ==0",
        len(floored),
        "raw<0",
        len(would_floor),
        "not 1000",
        sum(1 for row in rows if row["score_after"] < 999.995),
        ">=900",
        sum(1 for row in rows if row["score_after"] >= 900),
        ">=800",
        sum(1 for row in rows if row["score_after"] >= 800),
    )
    print("complete after", sum(1 for row in rows if row["incomplete_after"] <= 0))
    print("open after", len(open_rows), "drc after", len(dirty))
    summarize("unrouted_pts all", u_pen)
    summarize("count_pts all", c_pen)
    summarize("depth_pts all", d_pen)
    summarize("unrouted_pts open", [row["u_after"] for row in open_rows])
    summarize("count_pts dirty", [row["c_after"] for row in dirty])
    summarize("depth_pts dirty", [row["d_after"] for row in dirty])
    summarize("open_frac after", [row["open_frac_after"] for row in rows])
    summarize("open_frac open-only", [row["open_frac_after"] for row in open_rows])
    summarize("viol_count dirty", [row["drc_after"] for row in dirty])
    summarize("viol_um dirty", [row["viol_um_after"] for row in dirty])
    print("depth>=1pt", sum(1 for row in rows if row["d_after"] >= 1))
    print("depth>=10pt", sum(1 for row in rows if row["d_after"] >= 10))
    print("count>=10pt", sum(1 for row in rows if row["c_after"] >= 10))
    print("unrouted>=10pt", sum(1 for row in rows if row["u_after"] >= 10))
    dominate = {"unrouted": 0, "count": 0, "depth": 0, "none": 0}
    for row in rows:
        terms = [("unrouted", row["u_after"]), ("count", row["c_after"]), ("depth", row["d_after"])]
        terms.sort(key=lambda item: item[1], reverse=True)
        if terms[0][1] < 0.01:
            dominate["none"] += 1
        else:
            dominate[terms[0][0]] += 1
    print("dominant term", dominate)
    print(
        "1pct connections first half =",
        round(args.w1 / (100.0 * max(1e-6, 1.0 - args.split)), 2),
        "pts; last half =",
        round(args.w2 / (100.0 * max(1e-6, args.split)), 2),
        "pts",
    )
    print("1 violation =", "25/D", "pts at WC; median D", round(pct([row["D"] for row in rows], 50), 1))
    print("1 mm stacked depth =", round(args.wd, 2), "/D pts")
    print("1 violation equiv depth um at median D", round(DEPTH_SCALE * args.wc / args.wd, 1) if args.wd else None)
    print("top depth boards:")
    for row in sorted(rows, key=lambda item: -item["d_after"])[:8]:
        print(
            f"  d={row['d_after']:7.2f} c={row['c_after']:7.2f} u={row['u_after']:7.2f} "
            f"score={row['score_after']:7.2f} n={row['drc_after']:5g} um={row['viol_um_after']:8.1f} "
            f"D={row['D']:7.1f} {row['board']}"
        )
    print("top remaining-open boards:")
    for row in sorted(rows, key=lambda item: -item["u_after"])[:8]:
        print(
            f"  u={row['u_after']:7.2f} open={row['open_frac_after']:.3f} "
            f"twohalf={row['score_after']:7.2f} hinge={row['score_after_hinge']:7.2f} "
            f"linear={row['score_after_linear']:7.2f} {row['board']}"
        )
    not1000 = [row for row in rows if row["score_after"] < 999.995]
    if not1000:
        summarize("after not-1000", [row["score_after"] for row in not1000])
        print(
            "not1000 bins <800",
            sum(1 for row in not1000 if row["score_after"] < 800),
            "800-900",
            sum(1 for row in not1000 if 800 <= row["score_after"] < 900),
            "900-950",
            sum(1 for row in not1000 if 900 <= row["score_after"] < 950),
            "950-990",
            sum(1 for row in not1000 if 950 <= row["score_after"] < 990),
            "990-999",
            sum(1 for row in not1000 if 990 <= row["score_after"] < 999.995),
        )
        print(
            "not1000 only_unrouted",
            sum(1 for row in not1000 if row["u_after"] > 0.01 and row["c_after"] <= 0.01 and row["d_after"] <= 0.01),
            "only_drc",
            sum(1 for row in not1000 if row["u_after"] <= 0.01 and (row["c_after"] > 0.01 or row["d_after"] > 0.01)),
            "both",
            sum(1 for row in not1000 if row["u_after"] > 0.01 and (row["c_after"] > 0.01 or row["d_after"] > 0.01)),
        )
    print("wrote", csv_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
