#!/usr/bin/env python3
"""Benchmark storage manager for Freerouting.

Supports:
- Per-version storage (2A): scripts/benchmark/results/versions/benchmarks-<version>.json
- Line-delimited streaming JSONL (2B):
    scripts/benchmark/results/versions/benchmarks-<version>.jsonl
    scripts/benchmark/results/benchmarks.jsonl
- Monolithic JSON (legacy): scripts/benchmark/results/benchmarks.json
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import sys
import tempfile
from typing import Any, Iterable, Iterator


FLOAT_KEYS = {
    "score",
    "score_before",
    "score_after",
    "final_score",
    "quality_score",
    "optimizer_score",
    "router_score",
    "current_router_score",
    "current_optimizer_score",
    "final_quality_score",
    "final_optimizer_score",
    "normalized_score",
}


def render_compact(value: Any, key: str | None = None) -> str:
    """Renders compact JSON formatting with .2f float formatting for float score keys."""
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if key in FLOAT_KEYS and isinstance(value, (int, float)):
        return "null" if not math.isfinite(float(value)) else format(float(value), ".2f")
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return "null" if not math.isfinite(value) else format(value, ".2f")
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, list):
        return "[" + ",".join(render_compact(item) for item in value) + "]"
    if isinstance(value, dict):
        return (
            "{"
            + ",".join(
                json.dumps(str(k), ensure_ascii=False) + ":" + render_compact(item, k)
                for k, item in value.items()
            )
            + "}"
        )
    raise TypeError(type(value).__name__)


def replace_file_retrying(source: Path, destination: Path, attempts: int = 20) -> None:
    """Atomically replaces destination with source with retry on Windows file lock."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(1, attempts + 1):
        try:
            os.replace(source, destination)
            return
        except OSError:
            if attempt == attempts:
                raise
            import time
            time.sleep(min(0.05 * attempt, 0.5))


def get_versions_dir(results_dir: Path) -> Path:
    return results_dir / "versions"


def load_runs_from_jsonl(path: Path) -> Iterator[dict[str, Any]]:
    """Yields parsed JSON objects from a JSONL file."""
    if not path.exists():
        return
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line_str = line.strip()
            if line_str:
                try:
                    yield json.loads(line_str)
                except Exception:
                    continue


def load_version_runs(results_dir: Path, version: str) -> list[dict[str, Any]]:
    """Loads all runs for a single version."""
    v_dir = get_versions_dir(results_dir)
    jsonl_path = v_dir / f"benchmarks-{version}.jsonl"
    if jsonl_path.exists():
        return list(load_runs_from_jsonl(jsonl_path))

    json_path = v_dir / f"benchmarks-{version}.json"
    if json_path.exists():
        with open(json_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            return data.get("runs", [])

    return []


def load_all_runs(results_dir: Path) -> dict[str, dict[str, Any]]:
    """Loads all benchmark runs indexed by cache_key.

    Merges per-version storage (versions/benchmarks-*.jsonl and .json) with
    unified benchmarks.json and benchmarks.jsonl so newly added runs are never missed.
    """
    v_dir = get_versions_dir(results_dir)
    cache: dict[str, dict[str, Any]] = {}

    jsonl_files = sorted(v_dir.glob("benchmarks-*.jsonl"))
    json_files = sorted(v_dir.glob("benchmarks-*.json"))
    versions_mtime = max(
        [p.stat().st_mtime for p in jsonl_files + json_files],
        default=0.0,
    )

    # 1. Load from per-version JSONL files
    for p in jsonl_files:
        for run in load_runs_from_jsonl(p):
            ck = run.get("cache_key")
            if ck:
                cache[ck] = run

    # 2. Also load from per-version JSON files (for any runs or versions not yet in JSONL)
    for p in json_files:
        try:
            with open(p, "r", encoding="utf-8") as f:
                data = json.load(f)
                for run in data.get("runs", []):
                    ck = run.get("cache_key")
                    if ck and ck not in cache:
                        cache[ck] = run
        except Exception:
            pass

    # 3. Check unified benchmarks.json for any new or updated runs
    unified_json = results_dir / "benchmarks.json"
    new_runs_found = False
    if unified_json.exists():
        unified_mtime = unified_json.stat().st_mtime
        try:
            with open(unified_json, "r", encoding="utf-8") as f:
                data = json.load(f)
                for run in data.get("runs", []):
                    ck = run.get("cache_key")
                    if ck and (ck not in cache or unified_mtime > versions_mtime):
                        if ck not in cache:
                            new_runs_found = True
                        cache[ck] = run
        except Exception:
            pass

    # 4. Check unified benchmarks.jsonl for any additional or newer streamed records
    unified_jsonl = results_dir / "benchmarks.jsonl"
    if unified_jsonl.exists():
        unified_jsonl_mtime = unified_jsonl.stat().st_mtime
        for run in load_runs_from_jsonl(unified_jsonl):
            ck = run.get("cache_key")
            if ck and (ck not in cache or unified_jsonl_mtime > versions_mtime):
                if ck not in cache:
                    new_runs_found = True
                cache[ck] = run

    # If new runs were found in benchmarks.json or benchmarks.jsonl that weren't in versions/, synchronize them
    if new_runs_found and cache:
        try:
            from collections import defaultdict

            by_version: dict[str, list[dict[str, Any]]] = defaultdict(list)
            for r in cache.values():
                v = (r.get("binary") or {}).get("version_label") or "unknown"
                by_version[v].append(r)
            for v, v_runs in by_version.items():
                save_version_runs(results_dir, v, v_runs)
        except Exception:
            pass

    return cache


def save_version_runs(
    results_dir: Path,
    version: str,
    runs: list[dict[str, Any]],
    schema_version: int = 1,
) -> None:
    """Saves runs for a specific version into per-version JSON and JSONL files."""
    if not runs:
        return
    v_dir = get_versions_dir(results_dir)
    v_dir.mkdir(parents=True, exist_ok=True)

    # 1. Write JSONL (streaming line-delimited)
    jsonl_path = v_dir / f"benchmarks-{version}.jsonl"
    tmp_jsonl = v_dir / f".benchmarks-{version}.jsonl.{os.getpid()}.tmp"
    with open(tmp_jsonl, "w", encoding="utf-8", newline="\n") as f:
        for r in runs:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    replace_file_retrying(tmp_jsonl, jsonl_path)

    # 2. Write JSON (compact schema)
    json_path = v_dir / f"benchmarks-{version}.json"
    ver_data = {
        "schema_version": schema_version,
        "version_label": version,
        "total_runs": len(runs),
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "runs": runs,
    }
    tmp_json = v_dir / f".benchmarks-{version}.json.{os.getpid()}.tmp"
    with open(tmp_json, "w", encoding="utf-8", newline="\n") as f:
        f.write(render_compact(ver_data) + "\n")
    replace_file_retrying(tmp_json, json_path)


def append_version_run(results_dir: Path, version: str, run: dict[str, Any]) -> None:
    """Fast O(1) append of a single run to version JSONL and unified JSONL."""
    v_dir = get_versions_dir(results_dir)
    v_dir.mkdir(parents=True, exist_ok=True)

    jsonl_path = v_dir / f"benchmarks-{version}.jsonl"
    with open(jsonl_path, "a", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(run, ensure_ascii=False) + "\n")

    unified_jsonl = results_dir / "benchmarks.jsonl"
    with open(unified_jsonl, "a", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(run, ensure_ascii=False) + "\n")


def sync_all(results_dir: Path, retain_versions: set[str] | None = None) -> None:
    """Synchronizes per-version storage, benchmarks.jsonl, and benchmarks.json."""
    all_runs = load_all_runs(results_dir)
    if retain_versions:
        all_runs = {
            k: r
            for k, r in all_runs.items()
            if r.get("binary", {}).get("version_label") in retain_versions
        }
        # Purge obsolete per-version files for un-retained versions
        v_dir = get_versions_dir(results_dir)
        if v_dir.exists():
            for p in list(v_dir.glob("benchmarks-*.*")):
                v_name = p.stem.replace("benchmarks-", "", 1)
                if v_name not in retain_versions:
                    try:
                        p.unlink(missing_ok=True)
                    except OSError:
                        pass

    # Group by version
    from collections import defaultdict
    by_version: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for r in all_runs.values():
        v = r.get("binary", {}).get("version_label") or "unknown"
        by_version[v].append(r)

    # Save each version
    for v, runs in by_version.items():
        save_version_runs(results_dir, v, runs)

    # Write unified JSONL
    unified_jsonl = results_dir / "benchmarks.jsonl"
    tmp_jsonl = results_dir / f".benchmarks.jsonl.{os.getpid()}.tmp"
    with open(tmp_jsonl, "w", encoding="utf-8", newline="\n") as f:
        for r in all_runs.values():
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    replace_file_retrying(tmp_jsonl, unified_jsonl)

    # Write unified benchmarks.json (compact)
    unified_json = results_dir / "benchmarks.json"
    full_data = {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "total_runs": len(all_runs),
        "runs": list(all_runs.values()),
    }
    tmp_json = results_dir / f".benchmarks.json.{os.getpid()}.tmp"
    with open(tmp_json, "w", encoding="utf-8", newline="\n") as f:
        f.write(render_compact(full_data) + "\n")
    replace_file_retrying(tmp_json, unified_json)


def main() -> None:
    parser = argparse.ArgumentParser(description="Manage Freerouting benchmark storage formats.")
    parser.add_argument(
        "--results-dir",
        type=Path,
        default=Path("scripts/benchmark/results"),
        help="Path to benchmark results directory",
    )
    parser.add_argument(
        "--sync",
        action="store_true",
        help="Synchronize all formats (versions/, benchmarks.jsonl, benchmarks.json)",
    )
    parser.add_argument(
        "--prune-unretained",
        action="store_true",
        help="Retain only 1.9.0, 2.4.1, 2.5.0, 2.6.0-RC10",
    )
    args = parser.parse_args()

    results_dir = args.results_dir.resolve()
    retain = {"1.9.0", "2.4.1", "2.5.0", "2.6.0-RC10"} if args.prune_unretained else None

    if args.sync or args.prune_unretained:
        print(f"Synchronizing benchmark storage in {results_dir}...")
        sync_all(results_dir, retain_versions=retain)
        print("Done!")
    else:
        runs = load_all_runs(results_dir)
        print(f"Loaded {len(runs)} benchmark runs.")


if __name__ == "__main__":
    main()
