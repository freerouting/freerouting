#!/usr/bin/env python3
"""Unit tests for segment-wise translation in translate.py (no live API calls)."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

import translate  # noqa: E402

# Two segments separated by the .properties \n token (a backslash followed by n).
ENGLISH = "First line\\nSecond line"


class TranslateBySegmentsTests(unittest.TestCase):
    def test_batch_result_is_used(self) -> None:
        # translate_batch returns (parsed, api_ok). The tuple used to be taken for the dict, so
        # the batch result was never used and every segment went through translate_one.
        batch = ({"0": "Erste Zeile", "1": "Zweite Zeile"}, True)
        with patch.object(translate, "translate_batch", return_value=batch), patch.object(
            translate, "call_llm", side_effect=AssertionError("per-segment fallback must not run")
        ):
            result = translate.translate_by_segments("Bundle", "key", ENGLISH, {}, "de")
        self.assertEqual(result, "Erste Zeile\\nZweite Zeile")

    def test_unparseable_json_is_not_written_as_translation(self) -> None:
        unparseable = '```json { "0": "Erste Zeile" '  # truncated: no closing brace
        with patch.object(translate, "translate_batch", return_value=(None, True)), patch.object(
            translate, "call_llm", return_value=unparseable
        ):
            result = translate.translate_by_segments("Bundle", "key", ENGLISH, {}, "de")
        self.assertIsNone(result)

    def test_plain_text_response_is_still_accepted(self) -> None:
        with patch.object(translate, "translate_batch", return_value=(None, True)), patch.object(
            translate, "call_llm", side_effect=["Erste Zeile", "Zweite Zeile"]
        ):
            result = translate.translate_by_segments("Bundle", "key", ENGLISH, {}, "de")
        self.assertEqual(result, "Erste Zeile\\nZweite Zeile")


if __name__ == "__main__":
    unittest.main()
