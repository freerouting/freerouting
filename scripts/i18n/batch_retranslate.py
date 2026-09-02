#!/usr/bin/env python3
"""
batch_retranslate.py — Resumable batch re-translation runner for Freerouting locales.

Orchestrates clean full re-translation using Gemini:
1. Tracks progress persistently in scripts/i18n/.retranslation_progress.json so completed
   locales are never re-translated from scratch if interrupted.
2. Reports progress, elapsed time, and ETA dynamically.
3. Concise logging: reports bundle names with language codes and suppresses routine batch/write noise.
4. Auto-drops orphan keys on bundle write without destructive file purging.
5. Retries failed keys with missing_only=True.
6. Normalizes escape sequences via repair_locale_file().
7. Validates output via validate_locale() (verifies 0 errors).
8. Syncs context flags via sync_translated_flags().
9. Records verified completion in progress state immediately.

Usage:
    python scripts/i18n/batch_retranslate.py --locales de fr es
    python scripts/i18n/batch_retranslate.py --batch 1
    python scripts/i18n/batch_retranslate.py --all
    python scripts/i18n/batch_retranslate.py --all --reset-progress
"""

from __future__ import annotations

import argparse
import importlib
import json
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Set

sys.path.insert(0, str(Path(__file__).resolve().parent))

from context_store import (
    DEFAULT_CONTEXT_DIR,
    load_context_dir,
    save_context_dir,
    sync_translated_flags,
)
_fix_escapes = importlib.import_module("fix-property-escapes")
repair_locale_file = _fix_escapes.repair_locale_file
from i18n_output import err, out, symbol
from llm_client import gemini_api_key
from properties_io import (
    SUPPORTED_LOCALES,
    english_properties_files,
)
from translate import translate_locale
from validate import validate_locale

PROGRESS_FILE = Path("scripts/i18n/.retranslation_progress.json")

LOCALE_NAMES: Dict[str, str] = {
    "ar": "Arabic",
    "bn": "Bengali",
    "ca": "Catalan",
    "cs": "Czech",
    "da": "Danish",
    "de": "German",
    "el": "Greek",
    "en": "English",
    "es": "Spanish",
    "fi": "Finnish",
    "fr": "French",
    "he": "Hebrew",
    "hi": "Hindi",
    "hr": "Croatian",
    "hu": "Hungarian",
    "id": "Indonesian",
    "it": "Italian",
    "ja": "Japanese",
    "ko": "Korean",
    "lt": "Lithuanian",
    "nb": "Norwegian Bokmål",
    "nl": "Dutch",
    "pl": "Polish",
    "pt": "Portuguese",
    "pt_br": "Portuguese (Brazil)",
    "ro": "Romanian",
    "ru": "Russian",
    "sk": "Slovak",
    "sl": "Slovenian",
    "sv": "Swedish",
    "th": "Thai",
    "tr": "Turkish",
    "uk": "Ukrainian",
    "vi": "Vietnamese",
    "zh": "Chinese (Simplified)",
    "zh_tw": "Chinese (Traditional)",
}

BATCHES = {
    "1": ["de", "fr", "es", "it", "nl", "pt", "pt_br"],
    "2": ["pl", "cs", "hu", "ro", "ru", "uk", "sk"],
    "3": ["da", "sv", "fi", "nb", "ca", "el", "sl"],
    "4": ["zh", "zh_tw", "ja", "ko", "vi", "th", "id"],
    "5": ["ar", "he", "hi", "bn", "tr", "hr", "lt"],
}


def format_duration(seconds: float) -> str:
    """Format seconds into a human-friendly string (e.g. 1h 23m, 4m 12s, 45s)."""
    if seconds < 60:
        return f"{seconds:.0f}s"
    total_minutes = int(seconds // 60)
    rem_seconds = int(seconds % 60)
    if total_minutes < 60:
        return f"{total_minutes}m {rem_seconds:02d}s"
    hours = total_minutes // 60
    rem_minutes = total_minutes % 60
    return f"{hours}h {rem_minutes:02d}m"


def load_progress() -> Dict[str, Any]:
    """Load persistent progress state from disk."""
    if PROGRESS_FILE.exists():
        try:
            with open(PROGRESS_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            err(f"  {symbol('warn')} Could not load progress file: {e}")
    return {"completed_locales": []}


def save_progress(progress: Dict[str, Any]) -> None:
    """Save persistent progress state to disk."""
    PROGRESS_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(PROGRESS_FILE, "w", encoding="utf-8") as f:
        json.dump(progress, f, indent=2, sort_keys=True)


def is_locale_verified(locale: str, context_dir: Path = DEFAULT_CONTEXT_DIR) -> bool:
    """Check whether a locale already has 100% valid translation files on disk."""
    context = load_context_dir(context_dir)
    total, missing, pl_v, html_v, esc_v, orphans, stale = validate_locale(
        locale, context, verbose=False
    )
    return total > 0 and missing == 0 and pl_v == 0 and html_v == 0 and esc_v == 0 and orphans == 0


def retranslate_locale(
    locale: str,
    progress: Dict[str, Any],
    context_dir: Path = DEFAULT_CONTEXT_DIR,
    force: bool = False,
) -> bool:
    """Run full re-translation lifecycle for a single locale with resumption support."""
    completed_set: Set[str] = set(progress.get("completed_locales", []))

    # Check if already completed and verified
    if not force and locale in completed_set and is_locale_verified(locale, context_dir):
        out(f"  {symbol('ok')} Locale {locale.upper()} is already completed and verified. Skipping.")
        return True

    locale_start = time.time()

    # 1. Load context
    context = load_context_dir(context_dir)

    # 2. Translate all bundles
    failures = translate_locale(context, locale, context_dir, missing_only=False)

    # 3. Retry missing/failed keys if any failed
    if failures > 0:
        out(f"  {symbol('warn')} [{locale}] Retrying {failures} failed keys with missing_only=True...")
        failures = translate_locale(context, locale, context_dir, missing_only=True)

    # 4. Repair escape sequences
    repaired_keys = 0
    for eng_path in english_properties_files():
        rewritten, _, _, _ = repair_locale_file(eng_path, locale)
        repaired_keys += rewritten
    if repaired_keys > 0:
        out(f"  {symbol('ok')} [{locale}] Repaired escape sequences in {repaired_keys} key(s)")

    # 5. Validate
    context = load_context_dir(context_dir)
    total, missing, pl_v, html_v, esc_v, orphans, stale = validate_locale(
        locale, context, verbose=False
    )

    is_valid = (missing == 0 and pl_v == 0 and html_v == 0 and esc_v == 0 and orphans == 0)
    if not is_valid:
        err(
            f"  {symbol('fail')} Locale {locale.upper()} validation FAILED: missing={missing}, "
            f"placeholders={pl_v}, html={html_v}, escapes={esc_v}, orphans={orphans}"
        )
        return False

    # 6. Sync translated flags
    cleared = sync_translated_flags(context, locale)
    save_context_dir(context, context_dir)

    # 7. Record progress permanently
    if locale not in completed_set:
        completed_set.add(locale)
        progress["completed_locales"] = sorted(completed_set)
        save_progress(progress)

    elapsed_locale = time.time() - locale_start
    out(
        f"  {symbol('ok')} [{locale.upper()}] COMPLETED in {format_duration(elapsed_locale)} "
        f"({total} keys, 0 missing, 0 errors)\n"
    )
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="Resumable batch re-translation for Freerouting locales")
    parser.add_argument("--locale", "-l", help="Single locale code")
    parser.add_argument("--locales", nargs="+", help="One or more locale codes")
    parser.add_argument("--batch", "-b", choices=list(BATCHES.keys()), help="Predefined batch number (1-5)")
    parser.add_argument("--all", "-a", action="store_true", help="All 35 target locales")
    parser.add_argument("--reset-progress", action="store_true", help="Clear saved progress and restart from 0")
    parser.add_argument("--force", "-f", action="store_true", help="Force re-translation even if marked completed")
    args = parser.parse_args()

    if not gemini_api_key():
        err(f"{symbol('fail')} GEMINI_API_KEY is not set.")
        return 1

    progress = load_progress()
    if args.reset_progress:
        progress = {"completed_locales": []}
        save_progress(progress)
        out(f"{symbol('trash')} Cleared saved progress state.")

    targets: List[str] = []
    if args.locale:
        targets = [args.locale.strip().lower()]
    elif args.locales:
        targets = [loc.strip().lower() for loc in args.locales]
    elif args.batch:
        targets = BATCHES[args.batch]
    elif args.all:
        targets = SUPPORTED_LOCALES
    else:
        parser.error("Specify --locale, --locales, --batch, or --all")

    completed = set(progress.get("completed_locales", []))
    remaining = [loc for loc in targets if loc not in completed or args.force]
    out(
        f"{symbol('info')} Targets: {len(targets)} total, {len(targets) - len(remaining)} already completed, "
        f"{len(remaining)} remaining to process"
    )
    if not remaining:
        out(f"{symbol('ok')} All requested target locales are already completed!")
        return 0

    session_start = time.time()
    successful: List[str] = []
    failed: List[str] = []
    durations: List[float] = []

    for index, locale in enumerate(targets, start=1):
        lang_name = LOCALE_NAMES.get(locale, locale.upper())

        if locale not in remaining and not args.force:
            out(f">>> [{index}/{len(targets)}] {locale.upper()} ({lang_name}) - Already completed, skipping")
            successful.append(locale)
            continue

        completed_in_targets = len([t for t in targets if t in progress.get("completed_locales", [])])
        pct = (completed_in_targets / len(targets)) * 100.0

        time_line = f"Elapsed: {format_duration(time.time() - session_start)}"
        if durations:
            avg_time = sum(durations) / len(durations)
            rem_locales = len([loc for loc in targets if loc not in progress.get("completed_locales", [])])
            eta_str = format_duration(avg_time * rem_locales)
            time_line += f" | ETA: ~{eta_str} (avg {format_duration(avg_time)}/locale)"

        out(f"\n{'=' * 68}")
        out(f">>> [{index}/{len(targets)} - {pct:.1f}%] Processing {locale.upper()} ({lang_name})")
        out(f"    {time_line}")
        out(f"{'=' * 68}")

        loc_t0 = time.time()
        ok = retranslate_locale(locale, progress, force=args.force)
        loc_dur = time.time() - loc_t0

        if ok:
            successful.append(locale)
            durations.append(loc_dur)
            new_pct = (len(progress.get("completed_locales", [])) / len(targets)) * 100.0
            out(f"[PROGRESS] {len(progress.get('completed_locales', []))}/{len(targets)} locales completed ({new_pct:.1f}%)\n")
        else:
            failed.append(locale)

    elapsed = time.time() - session_start
    out(f"\n{'=' * 68}")
    out(f"  BATCH SUMMARY: {len(successful)} completed, {len(failed)} failed in {format_duration(elapsed)}")
    if successful:
        out(f"  {symbol('ok')} Completed: {', '.join(successful)}")
    if failed:
        err(f"  {symbol('fail')} Incomplete/Failed: {', '.join(failed)}")
    out(f"{'=' * 68}\n")

    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
