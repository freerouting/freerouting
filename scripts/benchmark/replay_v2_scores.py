#!/usr/bin/env python3
"""Replay current-tree V2 scores onto stored v1.9 (and current) phase snapshots.

Uses DefaultSettings V2 weights and D = max(1, P * L). Lower bounds are taken
from the matching current-tree run of the same fixture. Does not reroute.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import tempfile
from pathlib import Path
from typing import Any

# DefaultSettings V2 weights
W_UNROUTED = 1000.0
W_COUNT = 25.0
W_DEPTH = 1.0
U_SCALE = 1000.0
W_LENGTH = 1.0
W_VIA = 1.0
W_BEND = 1.0
LENGTH_FLOOR = 1.0
DIFFICULTY_FLOOR = 1.0

CURRENT_LABEL = "v2.4.2-SNAPSHOT"
V19_LABEL = "v1.9.0"


def _n(value: Any) -> float | None:
    if value is None:
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if not math.isfinite(number):
        return None
    return number


def _get(obj: Any, *keys: str) -> Any:
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
    pin = _n(difficulty.get("pin_count"))
    if pin is None:
        pin = _n(_get(stats, "items", "pin_count")) or 0.0
    layers = _n(difficulty.get("signal_layer_count"))
    if layers is None:
        layers = _n(_get(stats, "layers", "signal_count")) or 0.0
    return max(1.0, pin * layers)


def bounds_from_stats(stats: dict[str, Any] | None) -> dict[str, float | int] | None:
    if not stats:
        return None
    bounds = stats.get("bounds") or {}
    length = _n(bounds.get("min_trace_length_mm"))
    vias = _n(bounds.get("min_via_count"))
    bends = _n(bounds.get("min_bend_count"))
    if length is None and vias is None and bends is None:
        return None
    return {
        "min_trace_length_mm": 0.0 if length is None else length,
        "min_via_count": 0 if vias is None else int(vias),
        "min_bend_count": 0 if bends is None else int(bends),
    }


def v2_router_score(stats: dict[str, Any] | None, d: float) -> float | None:
    if not stats:
        return None
    connections = _n(_get(stats, "connections", "maximum_count"))
    incomplete = _n(_get(stats, "connections", "incomplete_count"))
    violations = _n(_get(stats, "clearance_violations", "total_count"))
    depth = _n(_get(stats, "clearance_violations", "total_violation_um"))
    if connections is None or incomplete is None:
        return None
    connections = max(0.0, connections)
    incomplete = max(0.0, incomplete)
    violations = 0.0 if violations is None else max(0.0, violations)
    depth = 0.0 if depth is None else max(0.0, depth)
    unrouted = W_UNROUTED * incomplete / connections if connections > 0 else 0.0
    scale = max(d, DIFFICULTY_FLOOR)
    drc = W_COUNT * violations / scale
    drc += W_DEPTH * depth / max(1.0, U_SCALE) / scale
    return max(0.0, 1000.0 - unrouted - drc)


def v2_optimizer_score(
    stats: dict[str, Any] | None, bounds: dict[str, float | int] | None, d: float
) -> float | None:
    if not stats or not bounds:
        return None
    length = _n(_get(stats, "traces", "total_length_mm"))
    vias = _n(_get(stats, "vias", "total_count"))
    bends = _n(_get(stats, "bends", "total_count"))
    if length is None or vias is None or bends is None:
        return None
    min_length = max(0.0, float(bounds["min_trace_length_mm"]))
    min_vias = max(0.0, float(bounds["min_via_count"]))
    min_bends = max(0.0, float(bounds["min_bend_count"]))
    scale = max(d, DIFFICULTY_FLOOR)
    length_penalty = (
        W_LENGTH * max(0.0, length - min_length) / max(min_length, LENGTH_FLOOR)
    )
    via_penalty = W_VIA * max(0.0, vias - min_vias) / scale
    bend_penalty = W_BEND * max(0.0, bends - min_bends) / scale
    return max(0.0, 1000.0 - length_penalty - via_penalty - bend_penalty)


def apply_replay(snapshot: dict[str, Any] | None, bounds: dict[str, float | int] | None) -> bool:
    if not isinstance(snapshot, dict):
        return False
    stats = snapshot.get("board_statistics")
    if not isinstance(stats, dict):
        return False
    d = difficulty_d(stats)
    router = v2_router_score(stats, d)
    optimizer = v2_optimizer_score(stats, bounds, d)
    if router is None and optimizer is None:
        return False
    if router is not None:
        snapshot["current_router_score"] = round(router, 2)
    if optimizer is not None:
        snapshot["current_optimizer_score"] = round(optimizer, 2)
    snapshot["current_score_source"] = "replay_v2"
    return True


def fixture_path(run: dict[str, Any]) -> str | None:
    fixture = run.get("fixture") or {}
    if isinstance(fixture, dict):
        return fixture.get("relative_path")
    return None


def version_label(run: dict[str, Any]) -> str | None:
    return run.get("version_label") or (run.get("binary") or {}).get("version_label")


def preferred_bounds(run: dict[str, Any]) -> dict[str, float | int] | None:
    phases = run.get("phases") or {}
    for phase_name in ("autorouter", "optimizer", "fanout"):
        phase = phases.get(phase_name) or {}
        for key in ("after", "before"):
            stats = (phase.get(key) or {}).get("board_statistics")
            bounds = bounds_from_stats(stats)
            if bounds:
                return bounds
    return bounds_from_stats(run.get("board_statistics"))


def atomic_write_json(path: Path, payload: Any) -> None:
    fd, tmp_name = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(payload, handle, indent=2)
            handle.write("\n")
        os.replace(tmp_name, path)
    except Exception:
        if os.path.exists(tmp_name):
            os.remove(tmp_name)
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--json",
        type=Path,
        default=Path("scripts/benchmark/results/benchmarks.json"),
    )
    parser.add_argument(
        "--csv",
        type=Path,
        default=Path("docs/research/v19_v2_replay.csv"),
    )
    parser.add_argument("--write", action="store_true", help="Write replay fields back into benchmarks.json")
    args = parser.parse_args()

    payload = json.loads(args.json.read_text(encoding="utf-8"))
    runs: list[dict[str, Any]] = payload["runs"]

    current_by_fixture: dict[str, dict[str, Any]] = {}
    for run in runs:
        if version_label(run) == CURRENT_LABEL:
            path = fixture_path(run)
            if path:
                current_by_fixture[path] = run

    rows: list[dict[str, Any]] = []
    updated = 0
    skipped = 0
    for run in runs:
        label = version_label(run)
        if label not in (V19_LABEL, CURRENT_LABEL):
            continue
        path = fixture_path(run)
        current = current_by_fixture.get(path or "")
        bounds = preferred_bounds(current) if current else preferred_bounds(run)
        if not bounds:
            skipped += 1
            continue
        phases = run.get("phases") or {}
        touched = False
        scores: dict[str, Any] = {}
        for phase_name in ("fanout", "autorouter", "optimizer"):
            phase = phases.get(phase_name) or {}
            for edge in ("before", "after"):
                if apply_replay(phase.get(edge), bounds):
                    touched = True
                    snap = phase.get(edge) or {}
                    scores[f"{phase_name}_{edge}_router"] = snap.get("current_router_score")
                    scores[f"{phase_name}_{edge}_optimizer"] = snap.get("current_optimizer_score")
        if not touched:
            skipped += 1
            continue
        updated += 1
        quality = run.get("quality") or {}
        autorouter_after = (phases.get("autorouter") or {}).get("after") or {}
        optimizer_after = (phases.get("optimizer") or {}).get("after") or {}
        quality["current_router_score"] = autorouter_after.get("current_router_score")
        quality["current_optimizer_score"] = optimizer_after.get("current_optimizer_score") or autorouter_after.get(
            "current_optimizer_score"
        )
        run["quality"] = quality
        if label == V19_LABEL:
            rows.append(
                {
                    "relative_path": path,
                    "native_score": _n(autorouter_after.get("score")),
                    "v2_router_after": autorouter_after.get("current_router_score"),
                    "v2_optimizer_after": (optimizer_after.get("current_optimizer_score")
                    if optimizer_after
                    else autorouter_after.get("current_optimizer_score")),
                    "v2_optimizer_before": ((phases.get("optimizer") or {}).get("before") or {}).get(
                        "current_optimizer_score"
                    ),
                    "unrouted": _n(_get(autorouter_after.get("board_statistics") or {}, "connections", "incomplete_count")),
                    "drc": _n(_get(autorouter_after.get("board_statistics") or {}, "clearance_violations", "total_count")),
                    "min_trace_length_mm": bounds["min_trace_length_mm"],
                    "min_via_count": bounds["min_via_count"],
                    "min_bend_count": bounds["min_bend_count"],
                    "difficulty_d": difficulty_d(autorouter_after.get("board_statistics")),
                }
            )

    args.csv.parent.mkdir(parents=True, exist_ok=True)
    if rows:
        with args.csv.open("w", encoding="utf-8", newline="\n") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
            writer.writeheader()
            writer.writerows(rows)

    if args.write:
        atomic_write_json(args.json, payload)

    print(f"updated_runs={updated} skipped={skipped} v19_csv_rows={len(rows)} csv={args.csv}")
    if args.write:
        print(f"wrote {args.json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
