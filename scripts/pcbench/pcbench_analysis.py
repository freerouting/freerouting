"""Analysis, duration reference calculations, and persistence helpers for PCBench."""

from __future__ import annotations

from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import time
from typing import Any

DEFAULT_TIER_ORDER = ("A", "D", "C", "B")
_KNOWN_TIERS = frozenset({"A", "B", "C", "D", "T"})
CORPUS_SAVE_INTERVAL_SECONDS = 180.0

_VERSION_LABEL = re.compile(
    r"^v?(?P<major>\d+)(?:\.(?P<minor>\d+))?(?:\.(?P<patch>\d+))?(?:\.(?P<build>\d+))?"
    r"(?:-(?P<tag>[A-Za-z]+)(?P<tag_number>\d+)?)?$"
)


def version_sort_key(label: str) -> tuple:
    """Order version labels so 2.6.0 ranks above 2.6.0-RC6, which ranks above 2.6.0-RC3."""
    match = _VERSION_LABEL.match((label or "").strip())
    if not match:
        return (0, 0, 0, 0, 0, "", 0)
    major, minor, patch, build, tag, tag_number = match.groups()
    return (
        int(major or 0),
        int(minor or 0),
        int(patch or 0),
        int(build or 0),
        1 if not tag else 0,
        (tag or "").casefold(),
        int(tag_number or 0),
    )


def _parse_run_at(value: str) -> datetime:
    if not value:
        return datetime.min.replace(tzinfo=timezone.utc)
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError:
        return datetime.min.replace(tzinfo=timezone.utc)
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed


def fixture_id_from_run(run: dict[str, Any]) -> str | None:
    """Board id from a corpus run record."""
    relative = str((run.get("fixture") or {}).get("relative_path") or "").replace("\\", "/")
    parts = [part for part in relative.split("/") if part]
    if len(parts) >= 2 and parts[0] == "PCBench":
        return parts[1]
    cache_key = str(run.get("cache_key") or "")
    marker = "--unrouted--"
    if marker in cache_key:
        return cache_key.split(marker, 1)[0] or None
    return None


def run_is_failure(run: dict[str, Any]) -> bool:
    """A crashed, timed-out, or non-zero run is not a duration reference."""
    exit_info = run.get("exit") or {}
    if exit_info.get("crashed") or exit_info.get("oom_detected") or exit_info.get("timed_out"):
        return True
    code = exit_info.get("code")
    if code not in (0, None):
        return True
    if exit_info.get("state") in {"FAILED", "TERMINATED", "INVALID"}:
        return True
    wall = (run.get("quality") or {}).get("wall_clock_seconds")
    return not isinstance(wall, (int, float)) or wall <= 0


def latest_successful_reference_seconds(runs: Any) -> dict[str, float]:
    """Wall-clock seconds of the newest successful version, per fixture.

    Failed runs are ignored. When the newest recorded version has only failures,
    the next older version that completed is used.
    """
    best: dict[str, tuple[tuple, datetime, float]] = {}
    for run in runs:
        if not isinstance(run, dict) or run_is_failure(run):
            continue
        board_id = fixture_id_from_run(run)
        if not board_id:
            continue
        version = str((run.get("binary") or {}).get("version_label") or "")
        run_at = _parse_run_at(str(run.get("run_at") or ""))
        seconds = float((run.get("quality") or {}).get("wall_clock_seconds") or 0.0)
        rank = (version_sort_key(version), run_at)
        current = best.get(board_id)
        if current is None or rank > (current[0], current[1]):
            best[board_id] = (version_sort_key(version), run_at, seconds)
    return {board_id: item[2] for board_id, item in best.items()}


def should_stop_main_loop(
    active_workers: int, queue_empty: bool, cancel_requested: bool, force_kill: bool
) -> bool:
    """Stop when the run is finished, or when a graceful stop has drained the workers."""
    if force_kill:
        return True
    if active_workers > 0:
        return False
    return queue_empty or cancel_requested


def parse_tier_order(raw: str) -> list[str]:
    """Return the tier run order.

    An empty value or ``All`` uses A, then D, then C, then B. A comma-separated
    list runs those tiers in the given order. ``--tier C,A`` runs C before A.
    """
    tokens = [part.strip().upper() for part in (raw or "").split(",") if part.strip()]
    if not tokens or tokens == ["ALL"]:
        return list(DEFAULT_TIER_ORDER)
    order: list[str] = []
    for token in tokens:
        if token == "ALL":
            raise ValueError("'All' cannot be combined with named tiers")
        if token not in _KNOWN_TIERS:
            raise ValueError(f"Unknown tier '{token}'. Use A, B, C, D, T, or a comma-separated list.")
        if token not in order:
            order.append(token)
    return order


def replace_file_retrying(source: Path, destination: Path, attempts: int = 12) -> None:
    """Replace destination with source, retrying Windows sharing violations.

    WinError 5 (Access is denied) is raised when another process has the
    destination open without delete sharing. The routed record must already be
    durable elsewhere before this is called.
    """
    delay_seconds = 0.25
    last_error: PermissionError | None = None
    for attempt in range(attempts):
        try:
            source.replace(destination)
            return
        except PermissionError as error:
            last_error = error
            if attempt == attempts - 1:
                break
            time.sleep(delay_seconds)
            delay_seconds = min(delay_seconds + 0.25, 1.0)
    assert last_error is not None
    raise last_error


def append_jsonl_record(path: Path, record: dict[str, Any], attempts: int = 8) -> None:
    """Append one JSON object. The last line for a cache_key is the record to keep."""
    line = json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
    delay_seconds = 0.05
    last_error: PermissionError | None = None
    for attempt in range(attempts):
        try:
            with path.open("a", encoding="utf-8", newline="\n") as handle:
                handle.write(line)
                handle.flush()
                os.fsync(handle.fileno())
            return
        except PermissionError as error:
            last_error = error
            if attempt == attempts - 1:
                break
            time.sleep(delay_seconds)
            delay_seconds = min(delay_seconds * 2, 0.5)
    assert last_error is not None
    raise last_error


def read_jsonl_records(path: Path) -> dict[str, dict[str, Any]]:
    """Return the latest record for each cache_key. Broken lines are skipped."""
    records: dict[str, dict[str, Any]] = {}
    if not path.exists():
        return records
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError:
            continue
        if not isinstance(record, dict):
            continue
        key = record.get("cache_key")
        if isinstance(key, str) and key:
            records[key] = record
    return records


def default_worker_count() -> int:
    """Three quarters of the logical processors, at least one."""
    processors = os.cpu_count() or 1
    return max(1, round(processors * 0.75))


def corpus_save_is_due(
    last_saved_at: float | None,
    now: float,
    *,
    force: bool = False,
    interval_seconds: float = CORPUS_SAVE_INTERVAL_SECONDS,
) -> bool:
    """Return whether benchmarks.json may be rewritten now."""
    if force or last_saved_at is None:
        return True
    return (now - last_saved_at) >= interval_seconds
