"""Unit tests for .properties escape handling."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from properties_io import (
    PROPERTY_NEWLINE_TOKEN,
    _split_property_line,
    count_property_escapes,
    join_property_newlines,
    load_properties,
    normalize_property_escapes,
    sanitize_property_value,
    sanitize_segment_translation,
    split_property_newlines,
    validate_property_escapes,
    write_properties,
)


class PropertyEscapeTests(unittest.TestCase):
    def test_normalize_converts_real_newline_to_escape(self) -> None:
        self.assertEqual(normalize_property_escapes("a\nb"), "a\\nb")

    def test_normalize_preserves_existing_escape_tokens(self) -> None:
        self.assertEqual(normalize_property_escapes("a\\nb"), "a\\nb")

    def test_validate_matches_after_normalization(self) -> None:
        english = "Line one\\nLine two"
        translation = "Zeile eins\nZeile zwei"
        ok, _, _ = validate_property_escapes(english, translation)
        self.assertTrue(ok)

    def test_validate_fails_on_escape_count_mismatch(self) -> None:
        english = "Line one\\n\\nLine two"
        translation = "Zeile eins\\nZeile zwei"
        ok, eng_counts, loc_counts = validate_property_escapes(english, translation)
        self.assertFalse(ok)
        self.assertNotEqual(eng_counts, loc_counts)

    def test_load_properties_treats_blank_lines_as_continuations(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "test_de.properties"
            path.write_text(
                "command_line_help=VERWENDUNG\n\n  freerouting [PARAMETER]\n",
                encoding="utf-8",
            )
            props = load_properties(path)
            self.assertEqual(props["command_line_help"], "VERWENDUNG\n\n  freerouting [PARAMETER]")

    def test_split_property_line_ignores_escaped_equals(self) -> None:
        self.assertIsNone(_split_property_line("-hr <m\\=n>\\nvalue"))
        key, value = _split_property_line("command_line_help=USAGE\\n\\nPARAMETERS")
        self.assertEqual(key, "command_line_help")
        self.assertEqual(value, "USAGE\\n\\nPARAMETERS")

    def test_split_join_property_newlines_preserves_count(self) -> None:
        english = "Line one\\n\\nLine two\\nLine three"
        segments = split_property_newlines(english)
        joined = join_property_newlines(segments)
        self.assertEqual(joined, english)
        self.assertEqual(
            count_property_escapes(joined)[PROPERTY_NEWLINE_TOKEN],
            count_property_escapes(english)[PROPERTY_NEWLINE_TOKEN],
        )

    def test_should_translate_by_segments(self) -> None:
        from properties_io import should_translate_by_segments

        self.assertFalse(should_translate_by_segments("one\\ntwo"))
        self.assertTrue(should_translate_by_segments("a\\nb\\nc\\nd"))

    def test_sanitize_segment_translation_removes_embedded_newlines(self) -> None:
        dirty = "  Loads the file\nand more\\nextra"
        cleaned = sanitize_segment_translation(dirty)
        self.assertNotIn("\n", cleaned)
        self.assertNotIn("\\n", cleaned)
        self.assertTrue(cleaned.startswith("  Loads the file"))

    def test_load_properties_merges_orphan_continuation_lines(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "test_de.properties"
            path.write_text(
                "confirm_cancel=Die Platine wurde geändert.\n"
                "Möchten Sie wirklich beenden?\n"
                "confirm_exit_no=Nein\n",
                encoding="utf-8",
            )
            props = load_properties(path)
            self.assertEqual(
                props["confirm_cancel"],
                "Die Platine wurde geändert.\nMöchten Sie wirklich beenden?",
            )

    def test_write_properties_uses_escape_tokens_not_real_newlines(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "test_de.properties"
            write_properties(path, {"confirm_cancel": "Line one\nLine two"})
            text = path.read_text(encoding="utf-8")
            self.assertIn("\\n", text)
            self.assertNotIn("Line one\nLine two", text)

    def test_sanitize_property_value_normalizes_tabs(self) -> None:
        self.assertEqual(sanitize_property_value("a\tb"), "a\\tb")

    def test_count_property_escapes(self) -> None:
        counts = count_property_escapes("a\\n\\nb")
        self.assertEqual(counts["\\n"], 2)


if __name__ == "__main__":
    unittest.main()
