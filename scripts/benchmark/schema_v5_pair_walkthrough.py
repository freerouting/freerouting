#!/usr/bin/env python3
"""Extract one current vs v1.9 schema-v5 pair and print flattened scoring fields.

Does not rewrite benchmarks.json. New harness records already stamp schema_version 5
and flatten L_min / V_min / B_min onto the top-level bounds object. Historical rows that
omit schema_version are treated as pre-v5 by Validate-BenchmarkSchema.ps1.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

V19_MARKERS = ("1.9.0", "v1.9", "1.9")


def _label(run: dict[str, Any]) -> str:
    binary = run.get("binary") or {}
    return str(binary.get("version_label") or binary.get("filename") or "")


def _is_v19(run: dict[str, Any]) -> bool:
    label = _label(run).lower()
    return any(marker.lower() in label for marker in V19_MARKERS)


def _fixture_path(run: dict[str, Any]) -> str:
    fixture = run.get("fixture") or {}
    path = str(fixture.get("relative_path") or fixture.get("filename") or "")
    if path.startswith("PCBench/"):
        return path[len("PCBench/") :]
    return path


def _flatten(run: dict[str, Any]) -> dict[str, Any]:
    quality = run.get("quality") or {}
    bounds = run.get("bounds") or {}
    stats = ((run.get("result") or {}).get("board_statistics")) if isinstance(run.get("result"), dict) else None
    nested_bounds = {}
    if isinstance(stats, dict):
        nested_bounds = stats.get("bounds") or {}
    return {
        "schema_version": run.get("schema_version"),
        "version_label": _label(run),
        "fixture": _fixture_path(run),
        "max_connections": quality.get("max_connections"),
        "unrouted_connections": quality.get("unrouted_connections"),
        "clearance_violations": quality.get("clearance_violations"),
        "trace_length_mm": quality.get("trace_length_mm"),
        "via_count": quality.get("via_count"),
        "bend_count": quality.get("bend_count"),
        "pin_count": quality.get("pin_count"),
        "min_trace_length_mm": bounds.get("min_trace_length_mm", nested_bounds.get("min_trace_length_mm")),
        "min_via_count": bounds.get("min_via_count", nested_bounds.get("min_via_count")),
        "min_bend_count": bounds.get("min_bend_count", nested_bounds.get("min_bend_count")),
        "complexity_c": bounds.get("complexity_c"),
        "native_score": quality.get("quality_score"),
        "current_router_score": quality.get("current_router_score"),
        "current_optimizer_score": quality.get("current_optimizer_score"),
        "exit_code": (run.get("exit") or {}).get("code"),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--json",
        type=Path,
        default=Path("scripts/benchmark/results/benchmarks.json"),
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("docs/research/schema_v5_current_v19_pair.md"),
    )
    args = parser.parse_args()
    payload = json.loads(args.json.read_text(encoding="utf-8"))
    runs = payload.get("runs") if isinstance(payload, dict) else payload
    if not isinstance(runs, list):
        raise SystemExit("benchmarks.json must contain a runs list")

    by_fixture: dict[str, dict[str, list[dict[str, Any]]]] = {}
    for run in runs:
        if not isinstance(run, dict):
            continue
        path = _fixture_path(run)
        if not path:
            continue
        bucket = by_fixture.setdefault(path, {"v19": [], "current": []})
        if _is_v19(run):
            bucket["v19"].append(run)
        else:
            bucket["current"].append(run)

    def pick_current(candidates: list[dict[str, Any]]) -> dict[str, Any]:
        def rank(run: dict[str, Any]) -> tuple[int, int, int]:
            flat = _flatten(run)
            has_bounds = 1 if flat["min_trace_length_mm"] is not None else 0
            schema = int(run.get("schema_version") or 0)
            has_length = 1 if flat["trace_length_mm"] is not None else 0
            return (has_bounds, schema, has_length)

        return max(candidates, key=rank)

    best: tuple[int, str, dict[str, Any], dict[str, Any]] | None = None
    for path, bucket in by_fixture.items():
        if not bucket["v19"] or not bucket["current"]:
            continue
        current_run = pick_current(bucket["current"])
        v19_run = pick_current(bucket["v19"])
        current_flat = _flatten(current_run)
        v19_flat = _flatten(v19_run)
        score = 0
        if current_flat["min_trace_length_mm"] is not None:
            score += 10
        if v19_flat["min_trace_length_mm"] is not None:
            score += 5
        if current_flat["schema_version"] == 5:
            score += 3
        if current_flat["trace_length_mm"] is not None:
            score += 1
        if best is None or score > best[0]:
            best = (score, path, current_flat, v19_flat)

    if best is None:
        current_only: tuple[str, dict[str, Any]] | None = None
        best_rank = (-1, -1, -1)
        for path, bucket in by_fixture.items():
            if not bucket["current"]:
                continue
            run = pick_current(bucket["current"])
            flat = _flatten(run)
            rank = (
                1 if flat["schema_version"] == 5 else 0,
                1 if flat["min_trace_length_mm"] is not None else 0,
                1 if flat["trace_length_mm"] is not None else 0,
            )
            if rank > best_rank:
                best_rank = rank
                current_only = (path, flat)
        if current_only is None:
            raise SystemExit("no current/v1.9 pair or schema-v5 current row found")
        path, current_flat = current_only
        v19_flat = {key: None for key in current_flat}
        lines = [
            "# Schema v5 current vs v1.9 pair walkthrough",
            "",
            "No v1.9 rows remain in `benchmarks.json` (stale `1.9.0` / `v1.9.0` cache",
            "entries were removed so overnight PCBench can route those binaries again).",
            "The table below is one schema-v5 **current** extract; the v1.9 column is empty",
            "until those runs are recached.",
            "",
            "New records use `schema_version: 5` and a top-level `bounds` object.",
            "Join key: fixture path with a leading `PCBench/` prefix stripped.",
            "",
            f"Fixture: `{path}`",
            "",
            "## Flattened fields",
            "",
            "| Field | Current | v1.9 |",
            "|---|---:|---:|",
        ]
        for key in current_flat:
            if key == "fixture":
                continue
            lines.append(f"| `{key}` | {current_flat[key]} | {v19_flat[key]} |")
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(args.out)
        return 0

    _, path, current_flat, v19_flat = best
    lines = [
        "# Schema v5 current vs v1.9 pair walkthrough",
        "",
        "This is a **single** paired extract from `scripts/benchmark/results/benchmarks.json`.",
        "It does not rewrite the corpus. New records already use `schema_version: 5` and a",
        "top-level `bounds` object (`min_trace_length_mm`, `min_via_count`, `min_bend_count`).",
        "Rows without `schema_version` remain historical (validator: pre-v5).",
        "Join key: fixture path with a leading `PCBench/` prefix stripped.",
        "",
        f"Fixture: `{path}`",
        "",
        "## Flattened fields",
        "",
        "| Field | Current | v1.9 |",
        "|---|---:|---:|",
    ]
    for key in current_flat:
        if key == "fixture":
            continue
        lines.append(f"| `{key}` | {current_flat[key]} | {v19_flat[key]} |")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
