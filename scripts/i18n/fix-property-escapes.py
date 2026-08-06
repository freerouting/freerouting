#!/usr/bin/env python3
"""
fix-property-escapes.py — Repair locale .properties escape sequences.

Merges orphan continuation lines, converts real newlines/tabs to \\n/\\t,
and rewrites files so Java escape tokens match the English source counts.

Usage:
    python scripts/i18n/fix-property-escapes.py --all
    python scripts/i18n/fix-property-escapes.py --locale de --dry-run
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Dict, List, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from i18n_output import err, out, symbol  # noqa: E402
from properties_io import (  # noqa: E402
    SUPPORTED_LOCALES,
    _split_property_line,
    bundle_name_from_path,
    english_has_property_escapes,
    english_properties_files,
    load_properties,
    locale_properties_path,
    normalize_property_escapes,
    sanitize_property_value,
    validate_property_escapes,
    write_properties,
)


def repair_locale_file(
    english_path: Path,
    locale: str,
    *,
    dry_run: bool = False,
    clear_mismatch_keys: bool = False,
) -> Tuple[int, int, List[str], int]:
    """Return (keys_rewritten, escape_mismatches, mismatch_messages, keys_cleared)."""
    english_props = load_properties(english_path)
    locale_path = locale_properties_path(english_path, locale)
    if not locale_path.exists():
        return 0, 0, [], 0

    locale_props = load_properties(locale_path)
    repaired: Dict[str, str] = {}
    keys_rewritten = 0
    escape_mismatches = 0
    mismatch_messages: List[str] = []
    keys_cleared = 0

    for key, value in locale_props.items():
        normalized = normalize_property_escapes(value)
        if normalized != value:
            keys_rewritten += 1
        repaired[key] = normalized

    mismatch_keys: List[str] = []
    for key, english_value in english_props.items():
        if not english_has_property_escapes(english_value):
            continue
        bundle = bundle_name_from_path(english_path)
        if key not in repaired:
            escape_mismatches += 1
            mismatch_messages.append(f"{bundle}.{key} ({locale}): missing translation")
            mismatch_keys.append(key)
            continue
        ok, eng_counts, loc_counts = validate_property_escapes(english_value, repaired[key])
        if not ok:
            escape_mismatches += 1
            mismatch_messages.append(
                f"{bundle}.{key} ({locale}): English {dict(eng_counts)} vs locale {dict(loc_counts)}"
            )
            mismatch_keys.append(key)

    if clear_mismatch_keys:
        for key in mismatch_keys:
            if key in repaired:
                del repaired[key]
                keys_cleared += 1

    raw_text = locale_path.read_text(encoding="utf-8")
    would_change = (
        keys_rewritten > 0
        or keys_cleared > 0
        or _file_has_orphan_lines(locale_path)
        or _serialized_props(repaired) != _normalize_file_text(raw_text)
    )

    if would_change and not dry_run:
        write_properties(locale_path, repaired)

    if clear_mismatch_keys:
        mismatch_messages = []

    return keys_rewritten, len(mismatch_messages), mismatch_messages, keys_cleared


def _serialized_props(props: Dict[str, str]) -> str:
    lines = [f"{key}={sanitize_property_value(props[key])}\n" for key in sorted(props.keys())]
    return "".join(lines)


def _normalize_file_text(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n")


def _file_has_orphan_lines(path: Path) -> bool:
    current_key: str | None = None
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or stripped.startswith("!"):
            if not stripped and current_key is not None:
                continue
            current_key = None
            continue
        if _split_property_line(stripped) is not None:
            current_key = _split_property_line(stripped)[0]
        elif current_key is not None:
            return True
        else:
            return True
    return False


def main() -> None:
    parser = argparse.ArgumentParser(description="Repair Java escape sequences in locale .properties files")
    parser.add_argument("--locale", "-l", type=str, help="Target locale code")
    parser.add_argument("--all", "-a", action="store_true", help="Repair all supported locales")
    parser.add_argument("--dry-run", "-n", action="store_true", help="Report changes without writing files")
    parser.add_argument(
        "--clear-mismatch-keys",
        action="store_true",
        help="Remove keys whose escape counts still mismatch after repair (for re-translation)",
    )
    args = parser.parse_args()

    if not args.locale and not args.all:
        parser.error("Specify --locale or --all")

    locales = SUPPORTED_LOCALES if args.all else [args.locale]
    total_rewritten = 0
    total_mismatches = 0
    total_cleared = 0
    all_mismatch_messages: List[str] = []

    for locale in locales:
        locale_rewritten = 0
        locale_mismatches = 0
        locale_cleared = 0
        for english_path in english_properties_files():
            rewritten, mismatches, messages, cleared = repair_locale_file(
                english_path,
                locale,
                dry_run=args.dry_run,
                clear_mismatch_keys=args.clear_mismatch_keys,
            )
            locale_rewritten += rewritten
            locale_mismatches += mismatches
            locale_cleared += cleared
            all_mismatch_messages.extend(messages)

        total_rewritten += locale_rewritten
        total_mismatches += locale_mismatches
        total_cleared += locale_cleared
        action = "Would rewrite" if args.dry_run else "Rewrote"
        cleared_msg = f", cleared {locale_cleared} key(s)" if locale_cleared else ""
        out(
            f"{symbol('ok')} {locale.upper()}: {action} {locale_rewritten} key(s)"
            f"{cleared_msg}, {locale_mismatches} escape mismatch(es)"
        )

    out(f"\n{symbol('stats')} Total keys normalized: {total_rewritten}")
    if total_cleared:
        out(f"{symbol('stats')} Keys cleared for re-translation: {total_cleared}")
    out(f"{symbol('stats')} Remaining escape mismatches: {total_mismatches}")

    if all_mismatch_messages:
        out(f"\n{symbol('warn')} Keys that still need re-translation:")
        for message in all_mismatch_messages:
            out(f"  - {message}")
        sys.exit(1)

    sys.exit(0)


if __name__ == "__main__":
    main()
