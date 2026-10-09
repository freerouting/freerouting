"""Store helpers for the PCBench corpus writer."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import run_corpus_benchmark as store


class BenchmarkStoreTest(unittest.TestCase):
    def test_corpus_file_is_written_at_most_once_per_interval_and_always_on_exit(self) -> None:
        self.assertTrue(store.corpus_save_is_due(None, 1_000.0))
        self.assertFalse(store.corpus_save_is_due(1_000.0, 1_179.0))
        self.assertTrue(store.corpus_save_is_due(1_000.0, 1_180.0))
        self.assertTrue(store.corpus_save_is_due(1_000.0, 1_001.0, force=True))

    def test_saved_age_is_seconds_since_the_last_corpus_swap(self) -> None:
        self.assertEqual(store.format_saved_age(None, 50.0), "never")
        self.assertEqual(store.format_saved_age(40.0, 40.2), "0s ago")
        self.assertEqual(store.format_saved_age(10.0, 52.9), "42s ago")

    def test_journal_keeps_the_latest_score_for_a_cache_key(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            journal = Path(tmp) / "run-journal.jsonl"
            store.append_jsonl_record(
                journal,
                {"cache_key": "board", "quality": {"unrouted_connections": 4, "quality_score": 10.0}},
            )
            store.append_jsonl_record(
                journal,
                {"cache_key": "board", "quality": {"unrouted_connections": 0, "quality_score": 100.0}},
            )
            with journal.open("a", encoding="utf-8", newline="\n") as handle:
                handle.write("{broken\n")
                handle.write('"skip-me"\n')

            records = store.read_jsonl_records(journal)

        self.assertEqual(records["board"]["quality"]["unrouted_connections"], 0)
        self.assertEqual(records["board"]["quality"]["quality_score"], 100.0)
        self.assertEqual(list(records), ["board"])

    def test_locked_corpus_file_keeps_the_previous_score_and_the_journal(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            journal = root / "run-journal.jsonl"
            source = root / ".benchmarks.json.tmp"
            destination = root / "benchmarks.json"
            destination.write_text('{"runs":[]}\n', encoding="utf-8")
            source.write_text('{"runs":[{"cache_key":"board"}]}\n', encoding="utf-8")
            record = {"cache_key": "board", "quality": {"unrouted_connections": 0, "quality_score": 100.0}}
            store.append_jsonl_record(journal, record)

            original_replace = Path.replace

            def locked_replace(path: Path, target: Path) -> Path:
                raise PermissionError(13, "Access is denied", str(target))

            Path.replace = locked_replace  # type: ignore[method-assign]
            try:
                with self.assertRaises(PermissionError):
                    store.replace_file_retrying(source, destination, attempts=1)
            finally:
                Path.replace = original_replace  # type: ignore[method-assign]

            self.assertEqual(destination.read_text(encoding="utf-8"), '{"runs":[]}\n')
            self.assertEqual(store.read_jsonl_records(journal)["board"]["quality"]["quality_score"], 100.0)
            self.assertTrue(source.exists())

    def test_replace_retries_until_the_swap_succeeds(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "next.json"
            destination = root / "benchmarks.json"
            destination.write_text("old\n", encoding="utf-8")
            source.write_text("new\n", encoding="utf-8")
            original_replace = Path.replace
            attempts = {"count": 0}

            def flaky_replace(path: Path, target: Path) -> Path:
                attempts["count"] += 1
                if attempts["count"] < 3:
                    raise PermissionError(13, "Access is denied", str(target))
                return original_replace(path, target)

            Path.replace = flaky_replace  # type: ignore[method-assign]
            try:
                store.replace_file_retrying(source, destination, attempts=5)
            finally:
                Path.replace = original_replace  # type: ignore[method-assign]

            self.assertEqual(attempts["count"], 3)
            self.assertEqual(destination.read_text(encoding="utf-8"), "new\n")


if __name__ == "__main__":
    unittest.main()
