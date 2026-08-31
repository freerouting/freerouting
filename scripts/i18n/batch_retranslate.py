#!/usr/bin/env python3
"""
batch_retranslate.py — Resumable batch re-translation runner for Freerouting locales.

Orchestrates clean full re-translation using Gemini 3.7 Flash:
1. Tracks progress persistently in scripts/i18n/.retranslation_progress.json so completed
   locales are never re-translated from scratch if interrupted.
2. Auto-drops orphan keys on bundle write without destructive file purging.
3. Translates bundles via translate_locale().
4. Retries any failed keys with missing_only=True.
5. Normalizes escape sequences via repair_locale_file().
6. Validates output via validate_locale() (verifies 0 errors).
7. Syncs context flags via sync_translated_flags().
8. Records verified completion in progress state immediately.

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

BATCHES = {
    "1": ["de", "fr", "es", "it", "nl", "pt", "pt_br"],
    "2": ["pl", "cs", "hu", "ro", "ru", "uk", "sk"],
    "3": ["da", "sv", "fi", "nb", "ca", "el", "sl"],
    "4": ["zh", "zh_tw", "ja", "ko", "vi", "th", "id"],
    "5": ["ar", "he", "hi", "bn", "tr", "hr", "lt"],
}


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
    out(f"\n{'=' * 65}")
    out(f"  {symbol('world')} Processing locale: {locale.upper()}")
    out(f"{'=' * 65}")

    completed_set: Set[str] = set(progress.get("completed_locales", []))

    # Check if already completed and verified
    if not force and locale in completed_set and is_locale_verified(locale, context_dir):
        out(f"  {symbol('ok')} Locale {locale.upper()} is already completed and verified. Skipping.")
        return True

    # 1. Load context
    context = load_context_dir(context_dir)

    # 2. Translate all bundles
    out(f"  {symbol('sync')} Translating all bundles via Gemini 3.7 Flash...")
    failures = translate_locale(context, locale, context_dir, missing_only=False)

    # 3. Retry missing/failed keys if any failed
    if failures > 0:
        out(f"  {symbol('warn')} Retrying {failures} failed keys with missing_only=True...")
        failures = translate_locale(context, locale, context_dir, missing_only=True)

    # 4. Repair escape sequences
    repaired_keys = 0
    for eng_path in english_properties_files():
        rewritten, _, _, _ = repair_locale_file(eng_path, locale)
        repaired_keys += rewritten
    if repaired_keys > 0:
        out(f"  {symbol('ok')} Repaired escape sequences in {repaired_keys} key(s)")

    # 5. Validate
    context = load_context_dir(context_dir)
    total, missing, pl_v, html_v, esc_v, orphans, stale = validate_locale(
        locale, context, verbose=False
    )
    out(
        f"  {symbol('stats')} Validation: total={total}, missing={missing}, "
        f"placeholders={pl_v}, html={html_v}, escapes={esc_v}, orphans={orphans}"
    )

    is_valid = (missing == 0 and pl_v == 0 and html_v == 0 and esc_v == 0 and orphans == 0)
    if not is_valid:
        err(f"  {symbol('fail')} Locale {locale.upper()} validation FAILED (has missing or invalid keys)")
        return False

    # 6. Sync translated flags
    cleared = sync_translated_flags(context, locale)
    save_context_dir(context, context_dir)
    out(f"  {symbol('ok')} Synced context: cleared {cleared} needs_retranslation flags")

    # 7. Record progress permanently
    if locale not in completed_set:
        completed_set.add(locale)
        progress["completed_locales"] = sorted(completed_set)
        save_progress(progress)
        out(f"  {symbol('ok')} Saved progress: {len(completed_set)} locale(s) completed")

    out(f"  {symbol('ok')} Locale {locale.upper()} COMPLETED SUCCESSFULLY\n")
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

    start_time = time.time()
    successful: List[str] = []
    failed: List[str] = []

    for index, locale in enumerate(targets, start=1):
        if locale not in remaining and not args.force:
            out(f"\n>>> [{index}/{len(targets)}] {locale.upper()} (Already completed - skipping)")
            successful.append(locale)
            continue

        out(f"\n>>> [{index}/{len(targets)}] Starting {locale.upper()}...")
        ok = retranslate_locale(locale, progress, force=args.force)
        if ok:
            successful.append(locale)
        else:
            failed.append(locale)

    elapsed = time.time() - start_time
    out(f"\n{'=' * 65}")
    out(f"  BATCH SUMMARY: {len(successful)} completed, {len(failed)} failed in {elapsed:.1f}s")
    if successful:
        out(f"  {symbol('ok')} Completed: {', '.join(successful)}")
    if failed:
        err(f"  {symbol('fail')} Incomplete/Failed: {', '.join(failed)}")
    out(f"{'=' * 65}\n")

    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
