#!/usr/bin/env python3
"""Preserve the fixed PCBench assignment across conversion and classification."""

from __future__ import annotations

import unittest

from classify_pcbench_tiers import classify_board
from generate_board_metadata import apply_preserved_regroup_fields


class RegroupMetadataTest(unittest.TestCase):
    def test_classifier_uses_the_six_hour_budget(self) -> None:
        board = {"board": {"nets": 10, "components": 5, "layers": 2, "area_cm2": 10}}
        tier, _expected, timeout, _tags = classify_board(board)
        self.assertEqual("A", tier)
        self.assertEqual("06:00:00", timeout)

    def test_conversion_keeps_the_fixed_assignment(self) -> None:
        rewritten = {"tags": ["4-layer"], "board": {"nets": 1}}
        existing = {
            "tier": "C",
            "tier_index": 6,
            "reference_seconds": 2400.5,
            "expected_outcome": "partial",
            "timeout_budget": "06:00:00",
            "tags": ["2-layer", "tier-c"],
        }
        apply_preserved_regroup_fields(rewritten, existing)
        self.assertEqual("C", rewritten["tier"])
        self.assertEqual(6, rewritten["tier_index"])
        self.assertEqual(2400.5, rewritten["reference_seconds"])
        self.assertEqual("06:00:00", rewritten["timeout_budget"])
        self.assertEqual(["4-layer", "tier-c"], rewritten["tags"])


if __name__ == "__main__":
    unittest.main()
