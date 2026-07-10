#!/usr/bin/env python3
"""
validate.py — Layer 3: Post-Translation Validation

After running translate.py, this script validates that:
1. All keys present in *_en.properties also exist in *_{locale}.properties (parity)
2. Placeholder tokens (%s, %d, {{...}}) are preserved exactly
3. HTML tags are preserved in HTML-formatted keys
4. No orphan keys exist (keys in locale files that don't exist in English)
5. English hashes match (change detection: flag keys whose English source changed)

Usage:
    python scripts/i18n/validate.py --locale de
    python scripts/i18n/validate.py --all
    python scripts/i18n/validate.py --input scripts/i18n/i18n-context.json
"""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Set, Tuple

RESOURCE_ROOT = Path("src/main/resources/app/freerouting")
DEFAULT_CONTEXT = Path("scripts/i18n/i18n-context.json")

SUPPORTED_LOCALES = [
    "de", "fr", "ru", "bn", "hi", "ko", "ja",
    "zh", "zh_tw", "ar", "pt", "es"
]

PLACEHOLDER_RE = re.compile(r"(%[sd]|%\.\d+f|%[df]|\{\{[^}]+\}\})")
HTML_KEYS = {"trace_hover_info", "pin_hover_info", "via_hover_info", "net_hover_info"}


def load_properties(path: Path) -> Dict[str, str]:
    """Load a .properties file, returning a dict of key->value."""
    result: Dict[str, str] = {}
    if not path.exists():
        return result
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or line.startswith("!"):
                continue
            if "=" in line:
                key, _, value = line.partition("=")
                result[key.strip()] = value.strip()
            elif ":" in line:
                key, _, value = line.partition(":")
                result[key.strip()] = value.strip()
    return result


def load_context(path: Path) -> Dict[str, Dict[str, Any]]:
    """Load the context metadata JSON file."""
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def english_properties_files() -> List[Path]:
    """Return all *_en.properties files."""
    return sorted(RESOURCE_ROOT.rglob("*_en.properties"))


def locale_path_for(english_path: Path, locale: str) -> Path:
    """Given an English properties file path, return the path for a locale."""
    name = english_path.name.replace("_en.properties", f"_{locale}.properties")
    return english_path.parent / name


def bundle_name_from_path(path: Path) -> str:
    """Convert path to bundle name (e.g., 'gui.BoardMenuFile')."""
    rel = path.relative_to(RESOURCE_ROOT)
    name = str(rel.with_suffix("")).replace("\\", "/").replace("/", ".")
    if name.endswith("_en"):
        name = name[:-3]
    return name


def validate_locale(
    locale: str,
    context: Dict[str, Dict[str, Any]],
    verbose: bool = False,
) -> Tuple[int, int, int, int, int, int]:
    """
    Validate all bundles for a single locale.
    Returns (total_keys, missing_keys, placeholder_violations, html_violations, orphan_keys).
    """
    total_keys = 0
    missing_keys = 0
    placeholder_violations = 0
    html_violations = 0
    orphan_keys = 0
    stale_keys = 0

    for english_path in english_properties_files():
        bundle = bundle_name_from_path(english_path)
        english_props = load_properties(english_path)
        locale_file = locale_path_for(english_path, locale)
        locale_props = load_properties(locale_file)

        if not locale_props and english_props:
            print(f"\n  ⚠️  Bundle '{bundle}': No locale file found at {locale_file}")
            missing_keys += len(english_props)
            continue

        # Check missing keys
        for key in english_props:
            total_keys += 1
            qualified_key = f"{bundle}.{key}"
            if key not in locale_props:
                if verbose:
                    print(f"  ❌ {qualified_key}: missing from {locale} bundle")
                missing_keys += 1
                continue

            english_value = english_props[key]
            locale_value = locale_props[key]

            # Check placeholders
            eng_placeholders = set(PLACEHOLDER_RE.findall(english_value))
            loc_placeholders = set(PLACEHOLDER_RE.findall(locale_value))
            missing_pl = eng_placeholders - loc_placeholders
            if missing_pl:
                if verbose:
                    print(f"  ⚠️  {qualified_key}: missing placeholders {missing_pl} in {locale}")
                placeholder_violations += 1

            # Check HTML integrity
            if key in HTML_KEYS or (qualified_key in context and context[qualified_key].get("is_html")):
                html_tags = re.findall(r"</?[a-z][a-z0-9]*\b[^>]*>", english_value)
                for tag in html_tags:
                    if tag not in locale_value:
                        if verbose:
                            print(f"  ⚠️  {qualified_key}: missing HTML tag '{tag}' in {locale}")
                        html_violations += 1
                        break

            # Check if English source changed (stale)
            if context:
                ctx = context.get(qualified_key)
                if ctx:
                    stored_hash = ctx.get("english_hash", "")
                    current_hash = hashlib.sha256(english_value.encode("utf-8")).hexdigest()
                    if stored_hash and stored_hash != current_hash:
                        if verbose:
                            print(f"  ⚠️  {qualified_key}: English source changed (stale translation)")
                        stale_keys += 1

        # Check orphan keys (in locale but not in English)
        for key in locale_props:
            if key not in english_props:
                if verbose:
                    print(f"  ⚠️  {bundle}.{key}: orphan key in {locale} (not in English bundle)")
                orphan_keys += 1

    return total_keys, missing_keys, placeholder_violations, html_violations, orphan_keys, stale_keys


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Validate locale .properties files against English originals"
    )
    parser.add_argument(
        "--locale", "-l",
        type=str,
        help="Target locale code (e.g., de, fr)",
    )
    parser.add_argument(
        "--all", "-a",
        action="store_true",
        help="Validate all supported locales",
    )
    parser.add_argument(
        "--input", "-i",
        type=Path,
        default=DEFAULT_CONTEXT,
        help=f"Context metadata JSON file (default: {DEFAULT_CONTEXT})",
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Print detailed violation messages",
    )
    args = parser.parse_args()

    if not args.locale and not args.all:
        parser.error("Specify --locale or --all")

    context: Dict[str, Dict[str, Any]] = {}
    if args.input.exists():
        context = load_context(args.input)
        print(f"📖 Loaded context for {len(context)} keys from {args.input}")
    else:
        print(f"⚠️  Context file not found: {args.input}. Skipping hash validation.")

    locales = SUPPORTED_LOCALES if args.all else [args.locale]
    all_passed = True

    for locale in locales:
        print(f"\n{'='*60}")
        print(f"  Validating locale: {locale.upper()}")
        print(f"{'='*60}")

        total, missing, pl_violations, html_violations, orphans, stale = validate_locale(
            locale, context, verbose=args.verbose
        )

        print(f"\n  📊 Results for {locale.upper()}:")
        print(f"     Total keys checked: {total}")
        print(f"     Missing keys: {missing}")
        print(f"     Placeholder violations: {pl_violations}")
        print(f"     HTML violations: {html_violations}")
        print(f"     Orphan keys (not in English): {orphans}")
        print(f"     Stale translations (source changed): {stale}")

        if missing > 0 or pl_violations > 0 or html_violations > 0:
            print(f"  ❌ VALIDATION FAILED for {locale.upper()}")
            all_passed = False
        else:
            print(f"  ✅ VALIDATION PASSED for {locale.upper()}")

    if all_passed:
        print(f"\n{'='*60}")
        print("  ✅ ALL LOCALES VALIDATED SUCCESSFULLY")
        print(f"{'='*60}")
        sys.exit(0)
    else:
        print(f"\n{'='*60}")
        print("  ❌ SOME LOCALES HAVE ISSUES")
        print(f"{'='*60}")
        sys.exit(1)


if __name__ == "__main__":
    main()