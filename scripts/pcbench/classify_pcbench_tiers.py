#!/usr/bin/env python3
"""Assign evaluation tiers (A-E) to PCBench fixtures based on complexity heuristics."""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def classify_board(meta: dict[str, Any]) -> tuple[str, str, str, list[str]]:
    """Classify a board into (tier, expected_outcome, timeout_budget, extra_tags).
    
    Returns:
        tier: 'A' | 'B' | 'C' | 'D' | 'E'
        expected_outcome: 'complete' | 'partial' | 'timeout' | 'unsupported'
        timeout_budget: '00:01:00' | '00:05:00' | '00:15:00' | '00:30:00'
        tags: list of descriptive tags
    """
    board = meta.get("board", {})
    layers = board.get("layers", 2)
    nets = board.get("nets", 0)
    components = board.get("components", 0)
    area_cm2 = board.get("area_cm2", 0.0)
    zones = board.get("zones", 0)

    tags = [f"{layers}-layer"]
    if zones > 0:
        tags.append("plane" if zones >= 2 else "zone")

    # Tier D: Extreme stress / pathological size
    if nets > 450 or components > 350 or layers >= 10 or area_cm2 > 600:
        tags.extend(["tier-d", "extreme-stress"])
        return "D", "partial", "00:30:00", tags

    # Tier A: Canary / Fast smoke-test suite
    if layers <= 2 and nets <= 35 and components <= 25 and area_cm2 <= 45:
        tags.extend(["tier-a", "canary", "small"])
        return "A", "complete", "00:01:00", tags

    # Tier B: Routine benchmark suite (standard 2-4 layer, moderate net count)
    if layers <= 4 and nets <= 160 and components <= 140 and area_cm2 <= 180:
        tags.extend(["tier-b", "routine", "medium" if nets > 60 else "small"])
        return "B", "complete", "00:05:00", tags

    # Tier C: Complex / Multi-layer / High density
    tags.extend(["tier-c", "complex", "large" if nets > 250 else "medium"])
    return "C", "partial", "00:15:00", tags


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pcbench-fixtures-dir", default="scripts/benchmark/fixtures/PCBench", type=Path)
    parser.add_argument("--metadata-yaml", default="scripts/benchmark/fixtures/metadata.yaml", type=Path)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    fixtures_dir = args.pcbench_fixtures_dir
    catalog_path = fixtures_dir / "catalog.json"
    if not catalog_path.exists():
        print(f"Error: {catalog_path} not found", file=sys.stderr)
        return 1

    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    boards = catalog.get("boards", [])

    tier_counts = {"A": 0, "B": 0, "C": 0, "D": 0, "E": 0}
    updated_boards = []

    for b in boards:
        board_id = b.get("board_id")
        tier, expected, timeout, tags = classify_board(b)
        tier_counts[tier] += 1

        b["tier"] = tier
        b["expected_outcome"] = expected
        b["timeout_budget"] = timeout
        # Merge tags preserving uniques
        existing_tags = b.get("tags", [])
        combined_tags = sorted(list(set(existing_tags + tags)))
        b["tags"] = combined_tags

        updated_boards.append(b)

        if not args.dry_run:
            meta_file = fixtures_dir / board_id / "metadata.normalized.json"
            if meta_file.exists():
                try:
                    single_meta = json.loads(meta_file.read_text(encoding="utf-8"))
                    single_meta["tier"] = tier
                    single_meta["expected_outcome"] = expected
                    single_meta["timeout_budget"] = timeout
                    single_meta["tags"] = combined_tags
                    meta_file.write_text(json.dumps(single_meta, indent=2), encoding="utf-8")
                except Exception as e:
                    print(f"Warning: failed to update {meta_file}: {e}", file=sys.stderr)

    catalog["boards"] = updated_boards
    catalog["tier_summary"] = {
        "tier_a_canary": tier_counts["A"],
        "tier_b_routine": tier_counts["B"],
        "tier_c_complex": tier_counts["C"],
        "tier_d_diagnostic": tier_counts["D"],
        "tier_e_unsupported": 25,
        "total_active": len(updated_boards),
    }

    print("Tier Classification Summary:")
    print(f"  Tier A (Canary / Fast Gate):       {tier_counts['A']} boards")
    print(f"  Tier B (Routine Benchmark):        {tier_counts['B']} boards")
    print(f"  Tier C (Complex / Multi-Layer):    {tier_counts['C']} boards")
    print(f"  Tier D (Extreme Stress/Diagnostic): {tier_counts['D']} boards")
    print(f"  Tier E (Quarantine / Unsupported): 25 boards")
    print(f"  Total In-Repo Active Boards:       {len(updated_boards)}")

    if not args.dry_run:
        catalog_path.write_text(json.dumps(catalog, indent=2), encoding="utf-8")
        print(f"\nUpdated {catalog_path} and all normalized metadata files.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
