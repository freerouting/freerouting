"""Store helpers for the PCBench corpus writer."""

from __future__ import annotations

import tempfile
import threading
import unittest
from pathlib import Path
from typing import Any

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

    def test_dashboard_separators_are_155_characters_long(self) -> None:
        import io
        import threading
        import collections
        from unittest.mock import patch

        captured = io.StringIO()
        status_lock = threading.Lock()
        worker_status = {
            1: {"board": "board-A", "start": 0.0, "last_line": "Routing...", "active": True},
            2: {"board": "Idle", "start": 0.0, "last_line": "Idle", "active": False},
        }
        messages = collections.deque(["[1/10] board-A: CLEAN (5.2s)"])

        with patch("sys.stdout", captured):
            store.render_dashboard(
                completed=1,
                total=10,
                t_start=0.0,
                worker_status=worker_status,
                recent_messages=messages,
                status_lock=status_lock,
                version_label="2.6.0-RC2",
                in_place=False,
                target_workers=4,
            )

        output = captured.getvalue()
        # Strip ANSI escape codes to inspect clean characters
        import re
        plain = re.sub(r"\033\[[0-9;]*m", "", output)
        lines = [line for line in plain.splitlines() if line.strip()]

        # Top separator
        self.assertEqual(lines[0], "=" * 155)
        # Separator after header
        self.assertEqual(lines[2], "-" * 155)
        # Separator after active workers (header + 2 workers)
        self.assertEqual(lines[6], "-" * 155)
        # Bottom separator
        self.assertEqual(lines[9], "=" * 155)

        # Header contains target workers and +/- hint
        self.assertIn("Workers: 2 (target 4)", lines[1])
        self.assertIn("[+ / - adjust workers | ESC / Q to stop]", lines[1])

    def test_dynamic_worker_scaling_increments_and_decrements_gracefully(self) -> None:
        import queue
        import time

        task_queue: queue.Queue[str] = queue.Queue()
        for i in range(6):
            task_queue.put(f"task-{i}")

        result_queue: queue.Queue[tuple[str, int]] = queue.Queue()
        worker_status: dict[int, dict[str, Any]] = {}
        status_lock = threading.Lock()
        cancel_event = threading.Event()
        force_kill_event = threading.Event()
        worker_lock = threading.Lock()
        target_workers = 2
        active_worker_threads = 0
        worker_threads: list[threading.Thread] = []

        def mock_worker_func(wid: int) -> None:
            nonlocal active_worker_threads
            while not cancel_event.is_set() and not force_kill_event.is_set():
                with worker_lock:
                    if active_worker_threads > target_workers:
                        active_worker_threads -= 1
                        with status_lock:
                            worker_status.pop(wid, None)
                        return

                try:
                    task = task_queue.get(timeout=0.05)
                except queue.Empty:
                    with worker_lock:
                        active_worker_threads -= 1
                        with status_lock:
                            worker_status.pop(wid, None)
                    return

                with status_lock:
                    worker_status[wid]["active"] = True
                    worker_status[wid]["board"] = task
                time.sleep(0.02)
                result_queue.put((task, wid))
                task_queue.task_done()
                with status_lock:
                    worker_status[wid]["active"] = False

            with worker_lock:
                active_worker_threads -= 1
                with status_lock:
                    worker_status.pop(wid, None)

        def spawn_worker_if_needed() -> None:
            nonlocal active_worker_threads
            while (
                active_worker_threads < target_workers
                and not task_queue.empty()
                and not cancel_event.is_set()
                and not force_kill_event.is_set()
            ):
                with status_lock:
                    used = set(worker_status.keys())
                    wid = 1
                    while wid in used:
                        wid += 1
                    worker_status[wid] = {"active": False, "board": "Idle"}
                active_worker_threads += 1
                t = threading.Thread(target=mock_worker_func, args=(wid,), daemon=True)
                worker_threads.append(t)
                t.start()

        # Start with 2 workers
        with worker_lock:
            spawn_worker_if_needed()
        self.assertEqual(active_worker_threads, 2)

        # Increase target to 3
        with worker_lock:
            target_workers = 3
            spawn_worker_if_needed()
        self.assertEqual(active_worker_threads, 3)

        # Decrease target to 1
        with worker_lock:
            target_workers = 1

        # Wait for all tasks to be processed
        results = []
        for _ in range(6):
            res = result_queue.get(timeout=2.0)
            results.append(res)

        for t in worker_threads:
            t.join(timeout=1.0)

        self.assertEqual(len(results), 6)
        self.assertEqual(active_worker_threads, 0)
        self.assertEqual(len(worker_status), 0)


if __name__ == "__main__":
    unittest.main()
