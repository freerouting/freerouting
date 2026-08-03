#!/usr/bin/env python3
"""
translate.py — Layer 2: LLM Translation Runner

Uses context metadata from scripts/i18n/context/ to translate English
.properties files into target locales.

Usage:
    python scripts/i18n/translate.py --locale de --missing-only
    python scripts/i18n/translate.py --locale de --bundle gui.BoardMenuFile --dry-run
    python scripts/i18n/translate.py --all --missing-only
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from context_store import (  # noqa: E402
    DEFAULT_CONTEXT_DIR,
    filter_bundles,
    load_context_dir,
    mark_keys_translated,
    save_context_dir,
)
from i18n_output import err, out, symbol  # noqa: E402
from llm_client import batch_size, translate_batch  # noqa: E402
from llm_client import call_llm  # noqa: E402
from prompt_builder import build_batch_prompt, build_single_prompt, enrich_entry  # noqa: E402
from properties_io import (  # noqa: E402
    ICON_KEY_RE,
    PLACEHOLDER_RE,
    SUPPORTED_LOCALES,
    bundle_name_from_path,
    english_properties_path,
    load_properties,
    locale_properties_path,
    write_properties,
)


def validate_placeholders(english: str, translation: str) -> bool:
    eng_ph = set(PLACEHOLDER_RE.findall(english))
    loc_ph = set(PLACEHOLDER_RE.findall(translation))
    missing = eng_ph - loc_ph
    extra = loc_ph - eng_ph
    if missing:
        err(f"      {symbol('warn')} Missing placeholders in translation: {missing}")
        return False
    if extra:
        err(f"      {symbol('warn')} Extra placeholders in translation: {extra}")
        return False

    return True


def validate_html(english: str, translation: str) -> bool:
    import re

    html_tags = re.findall(r"</?[a-z][a-z0-9]*\b[^>]*>", english)
    for tag in html_tags:
        if tag not in translation:
            err(f"      {symbol('warn')} Missing HTML tag: {tag}")
            return False
    return True


def get_work_items(
    context: Dict[str, Dict[str, Any]],
    english_path: Path,
    locale: str,
    missing_only: bool,
) -> List[Tuple[str, str, Dict[str, Any], Optional[str]]]:
    """
    Return (key, english_value, context_entry, previous_translation) for keys needing work.
    """
    english_props = load_properties(english_path)
    existing_props = load_properties(locale_properties_path(english_path, locale))
    bundle = bundle_name_from_path(english_path)
    items: List[Tuple[str, str, Dict[str, Any], Optional[str]]] = []

    for key, english_value in english_props.items():
        if ICON_KEY_RE.match(english_value):
            continue

        qualified = f"{bundle}.{key}"
        entry = context.get(qualified, {})
        previous = existing_props.get(key)

        if missing_only:
            if previous and not entry.get("needs_retranslation", False):
                continue
        # Full run: translate every non-icon key

        stale_previous = previous if entry.get("needs_retranslation") and previous else None
        items.append((key, english_value, entry, stale_previous))

    return items


def default_entry(bundle: str, key: str, english_value: str) -> Dict[str, Any]:
    return {
        "bundle": bundle,
        "bundle_desc": "UI component",
        "key": key,
        "english_value": english_value,
        "ui_role": "label",
        "grammatical_role": "fragment",
        "has_placeholders": bool(PLACEHOLDER_RE.findall(english_value)),
        "placeholders": PLACEHOLDER_RE.findall(english_value),
        "is_html": english_value.strip().startswith("<html"),
        "max_length_hint": None,
        "related_keys": [],
        "code_references": [],
    }


def translate_items_batch(
    bundle: str,
    batch: List[Tuple[str, str, Dict[str, Any], Optional[str]]],
    locale: str,
) -> Tuple[Dict[str, str], Set[str], int]:
    """Translate a batch; returns translations, failed keys, failure count."""
    entries: List[Dict[str, Any]] = []
    previous_by_key: Dict[str, str] = {}
    keys = []

    for key, english_value, entry, previous in batch:
        keys.append(key)
        full_entry = enrich_entry(entry if entry else default_entry(bundle, key, english_value), english_value)
        entries.append(full_entry)
        if previous:
            previous_by_key[key] = previous

    prompt = build_batch_prompt(bundle, entries, locale, previous_by_key=previous_by_key)
    parsed = translate_batch(prompt, keys)

    translations: Dict[str, str] = {}
    failures = 0
    failed_keys: Set[str] = set()

    if parsed is not None and len(parsed) == len(keys):
        for key, english_value, entry, _previous in batch:
            translation = parsed[key]
            full_entry = enrich_entry(entry if entry else default_entry(bundle, key, english_value), english_value)
            if full_entry.get("has_placeholders") and not validate_placeholders(english_value, translation):
                failures += 1
                failed_keys.add(key)
                continue
            if full_entry.get("is_html") and not validate_html(english_value, translation):
                failures += 1
                failed_keys.add(key)
                continue
            translations[key] = translation
        return translations, failed_keys, failures

    # Fallback: one key at a time
    for key, english_value, entry, previous in batch:
        full_entry = enrich_entry(entry if entry else default_entry(bundle, key, english_value), english_value)
        prompt = build_single_prompt(full_entry, locale, previous_translation=previous)
        out(f"  {symbol('sync')} Translating: {key}...", end="")
        translation = call_llm(prompt, max_tokens=300)
        if translation is None:
            out(" FAILED")
            failures += 1
            failed_keys.add(key)
            continue
        out(" OK")
        if full_entry.get("has_placeholders") and not validate_placeholders(english_value, translation):
            failures += 1
            failed_keys.add(key)
            continue
        if full_entry.get("is_html") and not validate_html(english_value, translation):
            failures += 1
            failed_keys.add(key)
            continue
        translations[key] = translation
        time.sleep(0.05)

    return translations, failed_keys, failures


def translate_bundle(
    context: Dict[str, Dict[str, Any]],
    english_path: Path,
    locale: str,
    *,
    dry_run: bool = False,
    missing_only: bool = False,
) -> Tuple[Dict[str, str], int, int, int, Set[str], int]:
    """Returns result props, stale, fresh, unchanged, translated_keys, failure_count."""
    english_props = load_properties(english_path)
    existing_props = load_properties(locale_properties_path(english_path, locale))
    bundle = bundle_name_from_path(english_path)

    result: Dict[str, str] = dict(existing_props)
    stale_count = 0
    fresh_count = 0
    unchanged_count = 0
    translated_keys: Set[str] = set()
    failure_count = 0

    work_items = get_work_items(context, english_path, locale, missing_only)
    work_keys = {item[0] for item in work_items}

    for key, english_value in english_props.items():
        if ICON_KEY_RE.match(english_value):
            result[key] = english_value
            unchanged_count += 1
            continue
        if key not in work_keys:
            if key in existing_props:
                result[key] = existing_props[key]
                unchanged_count += 1
            continue
        if existing_props.get(key) and key in work_keys:
            stale_count += 1

    if dry_run:
        for key, english_value, _entry, _prev in work_items:
            result[key] = f"[{locale}] {english_value}"
            out(f"  [DRY-RUN] Would translate: {key} = \"{english_value}\"")
            fresh_count += 1
        return result, stale_count, fresh_count, unchanged_count, translated_keys, failure_count

    size = batch_size()
    for offset in range(0, len(work_items), size):
        batch = work_items[offset: offset + size]
        if len(batch) > 1:
            out(f"  {symbol('sync')} Batch translating {len(batch)} keys...")
        translations, failed_keys, batch_failures = translate_items_batch(bundle, batch, locale)
        failure_count += batch_failures

        for key, english_value, _entry, _prev in batch:
            if key in translations:
                result[key] = translations[key]
                translated_keys.add(key)
                fresh_count += 1
            elif key in failed_keys or key not in translations:
                # Keep previous or fall back to English
                fallback = existing_props.get(key, english_value)
                result[key] = fallback
                if key in failed_keys:
                    err(f"  {symbol('fail')} Keeping fallback for {key}")

        if offset + size < len(work_items):
            time.sleep(0.1)

    return result, stale_count, fresh_count, unchanged_count, translated_keys, failure_count


def translate_locale(
    context: Dict[str, Dict[str, Any]],
    locale: str,
    context_dir: Path,
    *,
    dry_run: bool = False,
    missing_only: bool = False,
    bundles: Optional[List[str]] = None,
) -> int:
    out(f"\n{symbol('world')} Translating to locale: {locale.upper()}")
    out("=" * 60)

    filtered = filter_bundles(context, bundles)
    bundle_names = sorted({entry["bundle"] for entry in filtered.values()})
    if bundles and not bundle_names:
        err(f"{symbol('fail')} No matching bundles for filter: {', '.join(bundles)}")
        return 1

    total_stale = 0
    total_fresh = 0
    total_unchanged = 0
    total_failures = 0
    total_bundles = 0

    for bundle in bundle_names:
        english_path = english_properties_path(bundle)
        if not english_path.exists():
            continue

        out(f"\n{symbol('bundle')} Bundle: {bundle}")
        result, stale, fresh, unchanged, translated_keys, failures = translate_bundle(
            context,
            english_path,
            locale,
            dry_run=dry_run,
            missing_only=missing_only,
        )

        total_stale += stale
        total_fresh += fresh
        total_unchanged += unchanged
        total_failures += failures
        total_bundles += 1

        if not dry_run and translated_keys:
            mark_keys_translated(context, bundle, translated_keys)
            locale_path = locale_properties_path(english_path, locale)
            write_properties(locale_path, result)
            out(f"  {symbol('ok')} Wrote {len(result)} keys to {locale_path}")

    if not dry_run and missing_only:
        save_context_dir(context, context_dir)

    out(f"\n{symbol('stats')} Translation Summary for {locale.upper()}:")
    out(f"   Bundles processed: {total_bundles}")
    out(f"   New translations: {total_fresh}")
    out(f"   Already up-to-date (skipped): {total_unchanged}")
    out(f"   Stale (needed re-translation): {total_stale}")
    out(f"   Failures: {total_failures}")

    return total_failures


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Translate English .properties files using context-augmented LLM prompts"
    )
    parser.add_argument("--locale", "-l", type=str, help="Target locale code (e.g., de, fr)")
    parser.add_argument("--all", "-a", action="store_true", help="Translate to all supported locales")
    parser.add_argument(
        "--input",
        "-i",
        type=Path,
        default=DEFAULT_CONTEXT_DIR,
        help=f"Context directory (default: {DEFAULT_CONTEXT_DIR})",
    )
    parser.add_argument("--dry-run", "-n", action="store_true", help="Preview without calling LLM")
    parser.add_argument(
        "--missing-only",
        "-m",
        action="store_true",
        help="Only translate missing or stale keys (recommended)",
    )
    parser.add_argument(
        "--bundle",
        "-b",
        action="append",
        dest="bundles",
        help="Limit to bundle(s), e.g. gui.BoardMenuFile (repeatable)",
    )
    args = parser.parse_args()

    if not args.locale and not args.all:
        parser.error("Specify --locale or --all")

    if not args.input.exists():
        err(f"{symbol('fail')} Context directory not found: {args.input}")
        err("   Run: python scripts/i18n/extract-context.py")
        sys.exit(1)

    context = load_context_dir(args.input)
    out(f"{symbol('info')} Loaded context for {len(context)} keys from {args.input}")

    if args.missing_only:
        out(f"{symbol('search')} Missing-only mode: processing missing or stale keys only")
    elif not args.dry_run:
        out(f"{symbol('warn')} Full translation mode — use --missing-only for incremental updates")

    locales = SUPPORTED_LOCALES if args.all else [args.locale]
    exit_code = 0

    for locale in locales:
        failures = translate_locale(
            context,
            locale,
            args.input,
            dry_run=args.dry_run,
            missing_only=args.missing_only,
            bundles=args.bundles,
        )
        if failures:
            exit_code = 1

    if args.dry_run:
        out(f"\n{symbol('warn')} DRY-RUN completed. No translations were written.")

    sys.exit(exit_code)


if __name__ == "__main__":
    main()
