"""Terminal UI, progress formatting, and dashboard rendering for PCBench."""

from __future__ import annotations

import collections
import re
import sys
import threading
import time
from typing import Any

# Color definitions
C_RESET = "\033[0m"
C_BOLD = "\033[1m"
C_DIM = "\033[2m"
C_CYAN = "\033[36m"
C_BCYAN = "\033[1;36m"
C_GREEN = "\033[32m"
C_BGREEN = "\033[1;32m"
C_YELLOW = "\033[33m"
C_BYELLOW = "\033[1;33m"
C_BLUE = "\033[34m"
C_BBLUE = "\033[1;34m"
C_MAGENTA = "\033[35m"
C_BMAGENTA = "\033[1;35m"
C_RED = "\033[31m"
C_BRED = "\033[1;31m"
C_WHITE = "\033[37m"
C_BWHITE = "\033[1;37m"
C_GRAY = "\033[90m"

_render_lock = threading.Lock()
MINIMUM_SCALE_REFERENCE_SECONDS = 30 * 60


def format_fixture_name(name: str, limit: int = 37) -> str:
    """Keep short fixture names. Longer names keep the first and last 16 characters."""
    text = name or ""
    if len(text) <= limit:
        return text
    return f"{text[:16]}...{text[-16:]}"


def format_result_timing(seconds: float, suffix: str = "") -> str:
    """Right-align the minutes so result rows share one time column."""
    timing = f"({seconds / 60:7.1f}m"
    if suffix:
        timing += f", {suffix}"
    return timing + ")"


def format_recent_result(
    completed: int,
    total: int,
    label: str,
    timing: str,
    name: str,
    tier_place: tuple[int, int, str] | None = None,
) -> str:
    """Progress, padded outcome, timing, then the full fixture name.

    ``tier_place`` is ``(index, tier size, tier)`` in run order, so the 12th
    board of 48 tier C boards reads ``  12/ 48 of T-C``.
    """
    head = f"{completed:4d}/{total:4d}"
    if tier_place is not None:
        index, tier_total, tier = tier_place
        head = f"{head} {index:3d}/{tier_total:3d} of T-{tier}"
    return f"[{head}] {label:<8} {timing} {name}"


def assign_tier_places(boards: list[dict[str, Any]]) -> dict[str, tuple[int, int, str]]:
    """Map each board id to its 1-based index inside its tier.

    ``boards`` must already be in benchmark order. The denominator counts the
    selected boards of that tier, including ones skipped because they are
    already cached.
    """
    totals: dict[str, int] = {}
    order: list[tuple[str, str]] = []
    for board in boards:
        board_id = board.get("board_id")
        if not isinstance(board_id, str) or not board_id:
            continue
        tier = str(board.get("tier") or "?")
        totals[tier] = totals.get(tier, 0) + 1
        order.append((board_id, tier))
    seen: dict[str, int] = {}
    places: dict[str, tuple[int, int, str]] = {}
    for board_id, tier in order:
        seen[tier] = seen.get(tier, 0) + 1
        places[board_id] = (seen[tier], totals[tier], tier)
    return places


def format_worker_tag(wid: int, tier: str | None = None) -> str:
    """Worker index, plus the fixture tier while a board is running."""
    if tier:
        return f"[W-{wid:02d} T-{tier}]"
    return f"[W-{wid:02d}]"


def colorize_status_line(msg: str) -> str:
    """Highlight status keywords in color."""
    msg = re.sub(r"\bCLEAN\b", f"{C_BGREEN}CLEAN{C_RESET}", msg)
    msg = re.sub(r"\bROUTED\b", f"{C_BYELLOW}ROUTED{C_RESET}", msg)
    msg = re.sub(r"\bUNROUTED\b", f"{C_BBLUE}UNROUTED{C_RESET}", msg)
    msg = re.sub(r"\bTIMEOUT\b", f"{C_BRED}TIMEOUT{C_RESET}", msg)
    msg = re.sub(r"\bERROR\b", f"{C_BRED}ERROR{C_RESET}", msg)
    return msg


def format_duration(seconds: float) -> str:
    """Format a duration as H:MM:SS. Hours grow past 24."""
    whole = max(0, int(round(seconds)))
    hours, remainder = divmod(whole, 3600)
    minutes, secs = divmod(remainder, 60)
    return f"{hours:02d}:{minutes:02d}:{secs:02d}"


def format_elapsed_clock(seconds: float) -> str:
    """Minutes and seconds for the worker clock. Minutes grow past 59."""
    whole = max(0, int(seconds))
    return f"{whole // 60:02d}:{whole % 60:02d}"


def format_worker_progress(elapsed_seconds: float, expected_seconds: float | None) -> str:
    """``[22:51]`` or ``[22:51 ~42m]`` when the estimate rounds to at least one minute."""
    clock = format_elapsed_clock(elapsed_seconds)
    if expected_seconds is None or expected_seconds <= 0:
        return f"[{clock}]"
    minutes = int(expected_seconds / 60 + 0.5)
    if minutes <= 0:
        return f"[{clock}]"
    return f"[{clock} ~{minutes}m]"


def expected_fixture_seconds(
    reference_seconds: float,
    tier: str,
    scales: dict[str, float],
) -> float | None:
    """Seconds this fixture should take on the current build.

    The reference is the latest successful run of this fixture on the newest
    recorded version, or the catalog time when no such run exists. The tier
    scale is the current build's speed on boards of the same difficulty, and
    it stays at 1 until this run has enough evidence. The product is the same
    estimate the suite ETA uses for that board.
    """
    if reference_seconds <= 0:
        return None
    return reference_seconds * scale_for(scales, tier)


def duration_scales(completed: list[tuple[str, float, float]]) -> dict[str, float]:
    """Return per-tier speed ratios and a global ratio under the empty-string key.

    A ratio is actual seconds divided by reference seconds. It stays at 1 until
    the compared reference time reaches MINIMUM_SCALE_REFERENCE_SECONDS.
    """
    totals: dict[str, list[float]] = {}
    for tier, reference, actual in completed:
        if reference <= 0:
            continue
        for key in (tier, ""):
            bucket = totals.setdefault(key, [0.0, 0.0])
            bucket[0] += actual
            bucket[1] += reference
    scales = {
        key: actual / reference
        for key, (actual, reference) in totals.items()
        if reference >= MINIMUM_SCALE_REFERENCE_SECONDS
    }
    scales.setdefault("", 1.0)
    return scales


def scale_for(scales: dict[str, float], tier: str) -> float:
    return scales.get(tier, scales.get("", 1.0))


def estimate_remaining_seconds(
    queued: list[tuple[str, float]],
    running: list[tuple[str, float, float]],
    completed: list[tuple[str, float, float]],
    workers: int,
) -> tuple[float, float]:
    """Estimate suite seconds left, and the reference-weighted speed ratio.

    Running boards keep only their remaining expected time. Waiting boards go
    to the worker that becomes free first. Expected time is the fixture's
    latest successful duration times the tier ratio.
    """
    scales = duration_scales(completed)
    slots = max(1, workers)
    free = [0.0] * slots
    extras: list[float] = []
    weighted = 0.0
    weight = 0.0
    for index, (tier, reference, elapsed) in enumerate(running):
        expected = max(0.0, reference) * scale_for(scales, tier)
        remaining = max(0.0, expected - max(0.0, elapsed))
        if index < slots:
            free[index] = remaining
        else:
            extras.append(remaining)
        weighted += max(0.0, reference) * scale_for(scales, tier)
        weight += max(0.0, reference)
    for tier, reference in queued:
        expected = max(0.0, reference) * scale_for(scales, tier)
        slot = min(range(slots), key=lambda item: free[item])
        free[slot] += expected
        weighted += expected
        weight += max(0.0, reference)
    eta = max([*free, *extras], default=0.0)
    shown_scale = weighted / weight if weight else scales.get("", 1.0)
    return eta, shown_scale


def format_saved_age(last_saved_at: float | None, now: float) -> str:
    """Toolbar age of the last successful benchmarks.json swap."""
    if last_saved_at is None:
        return "never"
    return f"{max(0.0, (now - last_saved_at) / 60):.1f}m ago"


def render_dashboard(
    completed: int,
    total: int,
    t_start: float,
    worker_status: dict[int, dict[str, Any]],
    recent_messages: collections.deque[str],
    status_lock: threading.Lock,
    version_label: str = "",
    cancel_requested: bool = False,
    force_kill_requested: bool = False,
    keyboard_supported: bool = True,
    in_place: bool = True,
    last_saved_at: float | None = None,
    target_workers: int | None = None,
    board_reference: dict[str, tuple[str, float]] | None = None,
    queued_ids: list[str] | None = None,
    completed_samples: list[tuple[str, float, float]] | None = None,
) -> None:
    """Print updated multi-worker dashboard with live logs and recent history."""
    now = time.perf_counter()
    queued: list[tuple[str, float]] = []
    running: list[tuple[str, float, float]] = []
    samples: list[tuple[str, float, float]] = []
    with status_lock:
        if queued_ids is not None:
            for board_id in queued_ids:
                queued.append((board_reference or {}).get(board_id, ("?", 0.0)))
        if completed_samples is not None:
            samples = list(completed_samples)
        for info in worker_status.values():
            if not info.get("active"):
                continue
            running.append(
                (
                    str(info.get("tier") or "?"),
                    float(info.get("reference_seconds") or 0.0),
                    max(0.0, now - float(info.get("start", now))),
                )
            )
    slots = target_workers if target_workers is not None else max(1, len(running))
    remaining_secs, _shown_scale = estimate_remaining_seconds(queued, running, samples, slots)
    eta_str = format_duration(remaining_secs)
    scales = duration_scales(samples)
    pct = (completed / total) * 100.0 if total > 0 else 0.0

    ver_part = f" | {C_BWHITE}Version:{C_RESET} {C_BCYAN}{version_label}{C_RESET}" if version_label else ""

    if force_kill_requested:
        status_hint = f" | {C_BRED}[TERMINATING]{C_RESET}"
    elif cancel_requested:
        status_hint = f" | {C_BYELLOW}[DRAINING - ESC / Q to force kill]{C_RESET}"
    elif not keyboard_supported:
        status_hint = ""
    else:
        status_hint = f" | {C_DIM}[+ / - adjust workers | ESC / Q to stop]{C_RESET}"

    worker_count_str = f"{len(worker_status)}"
    if target_workers is not None and len(worker_status) != target_workers:
        worker_count_str = f"{len(worker_status)} (target {target_workers})"

    lines = []
    lines.append(f"{C_BCYAN}{'=' * 155}{C_RESET}")
    lines.append(
        f"{C_BWHITE}PCBench Benchmark:{C_RESET} {C_BYELLOW}{completed}/{total}{C_RESET} "
        f"({C_BGREEN}{pct:5.1f}%{C_RESET}){ver_part} | "
        f"{C_BWHITE}ETA:{C_RESET} {C_CYAN}{eta_str}{C_RESET} | "
        f"{C_BWHITE}Workers:{C_RESET} {C_BMAGENTA}{worker_count_str}{C_RESET} | "
        f"{C_BWHITE}Saved:{C_RESET} {C_BYELLOW}{format_saved_age(last_saved_at, time.perf_counter())}{C_RESET}"
        f"{status_hint}"
    )
    lines.append(f"{C_CYAN}{'-' * 155}{C_RESET}")

    now = time.perf_counter()
    with status_lock:
        for wid in sorted(worker_status.keys()):
            info = worker_status[wid]
            if info.get("active"):
                run_sec = int(now - info.get("start", now))
                board = format_fixture_name(str(info.get("board", "unknown")))
                tier = str(info.get("tier") or "?")
                expected = expected_fixture_seconds(
                    float(info.get("reference_seconds") or 0.0),
                    tier,
                    scales,
                )
                progress = format_worker_progress(run_sec, expected)
                last = info.get("last_line", "")
                last_time = info.get("last_line_time")
                idle_sec = int(now - last_time) if last_time else run_sec
                idle_str = f" ({idle_sec // 60}m ago)" if idle_sec >= 60 and last else ""
                lines.append(
                    f"  {C_BMAGENTA}{format_worker_tag(wid, tier)}{C_RESET} "
                    f"{C_BWHITE}{board:<37}{C_RESET} "
                    f"{C_BYELLOW}{progress}{C_RESET} -> {C_GRAY}{last}{idle_str}{C_RESET}"
                )
            else:
                lines.append(f"  {C_BMAGENTA}{format_worker_tag(wid)}{C_RESET} {C_DIM}Idle{C_RESET}")

    lines.append(f"{C_CYAN}{'-' * 155}{C_RESET}")
    lines.append(f"{C_BWHITE}Latest results:{C_RESET}")
    if recent_messages:
        for msg in recent_messages:
            lines.append(f"  {colorize_status_line(msg)}")
    else:
        lines.append(f"  {C_DIM}(None completed yet){C_RESET}")
    lines.append(f"{C_BCYAN}{'=' * 155}{C_RESET}")

    output_text = "\n".join(lines)
    with _render_lock:
        try:
            if in_place and sys.stdout.isatty():
                sys.stdout.write("\033[H\033[J" + output_text + "\n")
                sys.stdout.flush()
            else:
                print(output_text, flush=True)
        except Exception:
            try:
                safe_text = output_text.encode("ascii", errors="replace").decode("ascii")
                print(safe_text, flush=True)
            except Exception:
                pass
