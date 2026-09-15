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
from llm_client import batch_size, call_llm, gemini_api_key, max_tokens_for_values, parse_json_response, translate_batch  # noqa: E402
from prompt_builder import (  # noqa: E402
    build_batch_prompt,
    build_segments_prompt,
    build_single_prompt,
    enrich_entry,
)
from properties_io import (  # noqa: E402
    ICON_KEY_RE,
    PLACEHOLDER_RE,
    SUPPORTED_LOCALES,
    bundle_name_from_path,
    english_properties_path,
    join_property_newlines,
    load_properties,
    locale_properties_path,
    normalize_property_escapes,
    sanitize_segment_translation,
    should_translate_by_segments,
    split_property_newlines,
    validate_property_escapes,
    validate_segment_join,
    write_properties,
)

FALLBACK_PROBE_KEYS = {"text_manager_fallback_class_probe", "text_manager_fallback_common_probe"}


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


def validate_escapes(english: str, translation: str) -> bool:
    ok, eng_counts, loc_counts = validate_property_escapes(english, translation)
    if ok:
        return True
    err(f"      {symbol('warn')} Escape sequence mismatch: English {dict(eng_counts)} vs translation {dict(loc_counts)}")
    return False


def translate_by_segments(
    bundle: str,
    key: str,
    english_value: str,
    entry: Dict[str, Any],
    locale: str,
) -> Optional[str]:
    """Translate each \\n-delimited segment and rejoin (exact \\n count guaranteed)."""
    segments = split_property_newlines(english_value)
    translated = [""] * len(segments)
    full_entry = enrich_entry(entry, english_value)
    full_entry["key"] = key

    work: List[tuple[int, str]] = [(index, segment) for index, segment in enumerate(segments) if segment != ""]
    if not work:
        return join_property_newlines(translated)

    def translate_one(index: int, segment: str) -> Optional[str]:
        prompt = build_segments_prompt(bundle, full_entry, locale, [(index, segment)])
        response = call_llm(prompt, max_tokens=max_tokens_for_values([segment]))
        if response is None:
            return None
        parsed = parse_json_response(response)
        if parsed and str(index) in parsed:
            value = parsed[str(index)]
            if isinstance(value, str):
                return sanitize_segment_translation(value.strip().strip("\"'"))
        return sanitize_segment_translation(response.strip().strip("\"'"))

    size = min(batch_size(), 8)
    for offset in range(0, len(work), size):
        chunk = work[offset : offset + size]
        prompt = build_segments_prompt(bundle, full_entry, locale, chunk)
        indices = [str(index) for index, _segment in chunk]
        english_parts = [segment for _index, segment in chunk]
        parsed = translate_batch(prompt, indices, english_values=english_parts)
        if parsed is not None and len(parsed) == len(chunk):
            for index, _segment in chunk:
                translated[index] = sanitize_segment_translation(parsed[str(index)].strip().strip("\"'"))
            continue

        err("  Failed to parse segment batch JSON; retrying segments individually")
        for index, segment in chunk:
            value = translate_one(index, segment)
            if value is None:
                return None
            translated[index] = value

    return join_property_newlines(translated)


def _accept_translation(
    key: str,
    english_value: str,
    translation: str,
    entry: Dict[str, Any],
    *,
    assembled_from_segments: bool = False,
) -> Optional[str]:
    full_entry = enrich_entry(entry, english_value)
    if full_entry.get("has_placeholders") and not validate_placeholders(english_value, translation):
        return None
    if full_entry.get("is_html") and not validate_html(english_value, translation):
        return None
    if assembled_from_segments:
        if not validate_segment_join(english_value, translation):
            err(f"      {symbol('warn')} Segment count mismatch after assembly")
            return None
    elif not validate_escapes(english_value, translation):
        return None
    return normalize_property_escapes(translation)


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
        if ICON_KEY_RE.match(english_value) or key in FALLBACK_PROBE_KEYS:
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
    translations: Dict[str, str] = {}
    failures = 0
    failed_keys: Set[str] = set()
    normal_batch: List[Tuple[str, str, Dict[str, Any], Optional[str]]] = []

    for key, english_value, entry, _previous in batch:
        if should_translate_by_segments(english_value):
            raw = translate_by_segments(
                bundle, key, english_value, entry if entry else default_entry(bundle, key, english_value), locale
            )
            if raw is None:
                err(f"    {symbol('fail')} Segment translation failed for {key}")
                failures += 1
                failed_keys.add(key)
                continue
            accepted = _accept_translation(
                key,
                english_value,
                raw,
                entry if entry else default_entry(bundle, key, english_value),
                assembled_from_segments=True,
            )
            if accepted is None:
                err(f"    {symbol('fail')} Segment validation failed for {key}")
                failures += 1
                failed_keys.add(key)
                continue
            translations[key] = accepted
        else:
            normal_batch.append((key, english_value, entry, _previous))

    if not normal_batch:
        return translations, failed_keys, failures

    entries: List[Dict[str, Any]] = []
    previous_by_key: Dict[str, str] = {}
    keys = []

    for key, english_value, entry, previous in normal_batch:
        keys.append(key)
        full_entry = enrich_entry(entry if entry else default_entry(bundle, key, english_value), english_value)
        entries.append(full_entry)
        if previous:
            previous_by_key[key] = previous

    prompt = build_batch_prompt(bundle, entries, locale, previous_by_key=previous_by_key)
    english_values = [english_value for _key, english_value, _entry, _previous in normal_batch]
    parsed, api_ok = translate_batch(prompt, keys, english_values=english_values)

    if not api_ok:
        # The API call itself failed (capacity spike, quota, or network error).
        # Do not cascade into individual failing requests.
        for key, english_value, _entry, _previous in normal_batch:
            failed_keys.add(key)
            failures += 1
        return translations, failed_keys, failures

    if parsed is not None and len(parsed) == len(keys):
        for key, english_value, entry, _previous in normal_batch:
            translation = parsed[key]
            accepted = _accept_translation(
                key, english_value, translation, entry if entry else default_entry(bundle, key, english_value)
            )
            if accepted is None:
                failures += 1
                failed_keys.add(key)
                continue
            translations[key] = accepted
        return translations, failed_keys, failures

    # Fallback: one key at a time (only if the batch API call succeeded but parsing failed)
    for key, english_value, entry, previous in normal_batch:
        full_entry = enrich_entry(entry if entry else default_entry(bundle, key, english_value), english_value)
        prompt = build_single_prompt(full_entry, locale, previous_translation=previous)
        translation = call_llm(prompt, max_tokens=max_tokens_for_values([english_value]))
        if translation is None:
            err(f"    {symbol('fail')} Translation failed for {key}")
            failures += 1
            failed_keys.add(key)
            continue
        accepted = _accept_translation(
            key, english_value, translation, entry if entry else default_entry(bundle, key, english_value)
        )
        if accepted is None:
            if should_translate_by_segments(english_value):
                raw = translate_by_segments(bundle, key, english_value, full_entry, locale)
                accepted = _accept_translation(key, english_value, raw or "", full_entry) if raw else None
            if accepted is None:
                err(f"    {symbol('fail')} Translation validation failed for {key}")
                failures += 1
                failed_keys.add(key)
                continue
        translations[key] = accepted
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

    # Only retain keys that exist in English (automatically eliminates orphan keys and test probe keys)
    result: Dict[str, str] = {
        k: v for k, v in existing_props.items()
        if k in english_props and k not in FALLBACK_PROBE_KEYS
    }
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
        translations, failed_keys, batch_failures = translate_items_batch(bundle, batch, locale)
        failure_count += batch_failures

        for key, english_value, _entry, _prev in batch:
            if key in translations:
                result[key] = translations[key]
                translated_keys.add(key)
                fresh_count += 1
            elif key in failed_keys or key not in translations:
                if key in existing_props:
                    result[key] = existing_props[key]
                    err(f"  {symbol('fail')} Keeping previous translation for {key}")
                else:
                    err(f"  {symbol('fail')} No translation available for {key}")

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

    for index, bundle in enumerate(bundle_names, start=1):
        english_path = english_properties_path(bundle)
        if not english_path.exists():
            continue

        out(f"  [{locale}] [{index}/{len(bundle_names)}] {bundle}")
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

        if not dry_run and (translated_keys or result):
            mark_keys_translated(context, bundle, translated_keys)
            locale_path = locale_properties_path(english_path, locale)
            write_properties(locale_path, result)

    if not dry_run:
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
    parser.add_argument(
        "--exclude-locale",
        "-x",
        action="append",
        dest="exclude_locales",
        default=[],
        help="Skip locale(s) with --all (repeatable), e.g. --exclude-locale ar",
    )
    args = parser.parse_args()

    if not args.locale and not args.all:
        parser.error("Specify --locale or --all")

    if not args.input.exists():
        err(f"{symbol('fail')} Context directory not found: {args.input}")
        err("   Run: python scripts/i18n/extract-context.py")
        sys.exit(1)

    if not args.dry_run and not gemini_api_key():
        err(f"{symbol('fail')} GEMINI_API_KEY is not set.")
        err("   Set your Google AI Studio key, then restart Cursor so Python can read it.")
        err("   Example (PowerShell): $env:GEMINI_API_KEY = \"AQ....\"")
        sys.exit(1)

    context = load_context_dir(args.input)
    out(f"{symbol('info')} Loaded context for {len(context)} keys from {args.input}")

    if args.missing_only:
        out(f"{symbol('search')} Missing-only mode: processing missing or stale keys only")
    elif not args.dry_run:
        out(f"{symbol('warn')} Full translation mode — use --missing-only for incremental updates")

    locales = SUPPORTED_LOCALES if args.all else [args.locale]
    excluded = {code.strip().lower() for code in (args.exclude_locales or []) if code.strip()}
    if excluded:
        locales = [loc for loc in locales if loc.lower() not in excluded]
        out(f"{symbol('info')} Excluding locale(s): {', '.join(sorted(excluded))}")
    if not locales:
        err(f"{symbol('fail')} No locales left to translate after exclusions")
        sys.exit(1)
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
