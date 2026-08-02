#!/usr/bin/env python3
"""
validate.py — Layer 3: Post-Translation Validation

Usage:
    python scripts/i18n/validate.py --locale de
    python scripts/i18n/validate.py --all
    python scripts/i18n/validate.py --locale de --bundle gui.BoardMenuFile -v
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from context_store import DEFAULT_CONTEXT_DIR, filter_bundles, load_context_dir  # noqa: E402
from glossary import validate_glossaries  # noqa: E402
from i18n_output import out, symbol  # noqa: E402
from properties_io import (  # noqa: E402
    PLACEHOLDER_RE,
    SUPPORTED_LOCALES,
    bundle_name_from_path,
    english_properties_files,
    load_properties,
    locale_properties_path,
)

HTML_KEYS = {"trace_hover_info", "pin_hover_info", "via_hover_info", "net_hover_info"}


def bundles_to_validate(context: Dict[str, Dict[str, Any]], bundles: Optional[List[str]]) -> Set[str]:
    if bundles:
        return set(bundles)
    return {entry["bundle"] for entry in context.values()} if context else {
        bundle_name_from_path(p) for p in english_properties_files()
    }


def validate_locale(
    locale: str,
    context: Dict[str, Dict[str, Any]],
    *,
    bundles: Optional[List[str]] = None,
    verbose: bool = False,
) -> Tuple[int, int, int, int, int, int]:
    total_keys = 0
    missing_keys = 0
    placeholder_violations = 0
    html_violations = 0
    orphan_keys = 0
    stale_keys = 0

    allowed_bundles = bundles_to_validate(context, bundles)

    for english_path in english_properties_files():
        bundle = bundle_name_from_path(english_path)
        if bundle not in allowed_bundles:
            continue

        english_props = load_properties(english_path)
        locale_props = load_properties(locale_properties_path(english_path, locale))

        if not locale_props and english_props:
            out(f"\n  {symbol('warn')} Bundle '{bundle}': No locale file")
            missing_keys += len(english_props)
            continue

        for key in english_props:
            total_keys += 1
            qualified_key = f"{bundle}.{key}"
            if key not in locale_props:
                if verbose:
                    out(f"  {symbol('fail')} {qualified_key}: missing from {locale}")
                missing_keys += 1
                continue

            english_value = english_props[key]
            locale_value = locale_props[key]

            eng_ph = set(PLACEHOLDER_RE.findall(english_value))
            loc_ph = set(PLACEHOLDER_RE.findall(locale_value))
            missing_ph = eng_ph - loc_ph
            extra_ph = loc_ph - eng_ph
            if missing_ph or extra_ph:
                if verbose:
                    if missing_ph:
                        out(f"  {symbol('warn')} {qualified_key}: missing placeholders {missing_ph}")
                    if extra_ph:
                        out(f"  {symbol('warn')} {qualified_key}: extra placeholders {extra_ph}")
                placeholder_violations += 1

            ctx = context.get(qualified_key, {})
            if key in HTML_KEYS or ctx.get("is_html"):
                html_tags = re.findall(r"</?[a-z][a-z0-9]*\b[^>]*>", english_value)
                for tag in html_tags:
                    if tag not in locale_value:
                        if verbose:
                            out(f"  {symbol('warn')} {qualified_key}: missing HTML tag '{tag}'")
                        html_violations += 1
                        break

            if ctx.get("needs_retranslation", False):
                if verbose:
                    out(f"  {symbol('warn')} {qualified_key}: stale (English changed)")
                stale_keys += 1

        for key in locale_props:
            if key not in english_props:
                if verbose:
                    out(f"  {symbol('warn')} {bundle}.{key}: orphan key in {locale}")
                orphan_keys += 1

    return total_keys, missing_keys, placeholder_violations, html_violations, orphan_keys, stale_keys


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate locale .properties files")
    parser.add_argument("--locale", "-l", type=str, help="Target locale code")
    parser.add_argument("--all", "-a", action="store_true", help="Validate all supported locales")
    parser.add_argument(
        "--input",
        "-i",
        type=Path,
        default=DEFAULT_CONTEXT_DIR,
        help=f"Context directory (default: {DEFAULT_CONTEXT_DIR})",
    )
    parser.add_argument("--verbose", "-v", action="store_true", help="Print detailed violations")
    parser.add_argument(
        "--bundle",
        "-b",
        action="append",
        dest="bundles",
        help="Limit to bundle(s), e.g. gui.BoardMenuFile",
    )
    args = parser.parse_args()

    glossary_errors = validate_glossaries()
    if glossary_errors:
        out(f"{symbol('fail')} Glossary validation failed:")
        for error in glossary_errors:
            out(f"  - {error}")
        sys.exit(1)

    if not args.locale and not args.all:
        parser.error("Specify --locale or --all")

    context: Dict[str, Dict[str, Any]] = {}
    if args.input.exists():
        context = load_context_dir(args.input)
        if args.bundles:
            context = filter_bundles(context, args.bundles)
        out(f"{symbol('info')} Loaded context for {len(context)} keys from {args.input}")
    else:
        out(f"{symbol('warn')} Context not found: {args.input}. Skipping stale checks.")

    locales = SUPPORTED_LOCALES if args.all else [args.locale]
    all_passed = True

    for locale in locales:
        out(f"\n{'=' * 60}")
        out(f"  Validating locale: {locale.upper()}")
        out(f"{'=' * 60}")

        total, missing, pl_v, html_v, orphans, stale = validate_locale(
            locale, context, bundles=args.bundles, verbose=args.verbose
        )

        out(f"\n  {symbol('stats')} Results for {locale.upper()}:")
        out(f"     Total keys checked: {total}")
        out(f"     Missing keys: {missing}")
        out(f"     Placeholder violations: {pl_v}")
        out(f"     HTML violations: {html_v}")
        out(f"     Orphan keys: {orphans}")
        out(f"     Stale translations: {stale}")

        if missing > 0 or pl_v > 0 or html_v > 0:
            out(f"  {symbol('fail')} VALIDATION FAILED for {locale.upper()}")
            all_passed = False
        else:
            out(f"  {symbol('ok')} VALIDATION PASSED for {locale.upper()}")

    if all_passed:
        out(f"\n{'=' * 60}")
        out(f"  {symbol('ok')} ALL LOCALES VALIDATED SUCCESSFULLY")
        out(f"{'=' * 60}")
        sys.exit(0)

    out(f"\n{'=' * 60}")
    out(f"  {symbol('fail')} SOME LOCALES HAVE ISSUES")
    out(f"{'=' * 60}")
    sys.exit(1)


if __name__ == "__main__":
    main()
