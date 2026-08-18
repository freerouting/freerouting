#!/usr/bin/env python3
"""
extract-context.py — Layer 1: Context Metadata Extraction

Scans all *_en.properties files and Java source code to build per-bundle
context JSON under scripts/i18n/context/.

Usage:
    python scripts/i18n/extract-context.py
    python scripts/i18n/extract-context.py --check
    python scripts/i18n/extract-context.py --output scripts/i18n/context
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

# Allow imports from scripts/i18n/
sys.path.insert(0, str(Path(__file__).resolve().parent))

from context_store import (  # noqa: E402
    DEFAULT_CONTEXT_DIR,
    context_diff,
    load_context_dir,
    save_context_dir,
    sync_translated_flags,
)
from properties_io import SUPPORTED_LOCALES  # noqa: E402
from i18n_output import out, symbol  # noqa: E402
from java_scanner import scan_java_key_usages  # noqa: E402
from properties_io import (  # noqa: E402
    ICON_KEY_RE,
    PLACEHOLDER_RE,
    bundle_name_from_path,
    english_properties_files,
    load_properties,
)

JAVA_SOURCE_ROOT = Path("src/main/java")

UI_ROLE_PATTERNS: List[Tuple[str, str]] = [
    (r"_tooltip$", "tooltip"),
    (r"_button$", "button_label"),
    (r"_header$", "dialog_title"),
    (r"^title$|_title$", "dialog_title"),
    (r"^message_|_message$", "message"),
    (r"_error_?\d*$|^error_", "error_message"),
    (r"_hover_info$", "hover_info"),
    (r"^confirm_", "confirmation_dialog"),
    (r"_info$", "info_label"),
]

HTML_KEYS: Set[str] = {"trace_hover_info", "pin_hover_info", "via_hover_info", "net_hover_info"}


def infer_ui_role(key: str) -> str:
    for pattern, role in UI_ROLE_PATTERNS:
        if re.search(pattern, key):
            return role
    return "label"


def infer_grammatical_role(value: str) -> str:
    if len(value) < 5:
        return "fragment"
    if value[0].isupper() and value.endswith("."):
        return "full_sentence"
    if value[0].isupper():
        return "noun_phrase"
    first_word = value.split()[0] if value.split() else ""
    verb_indicators = {
        "saves", "save", "write", "writes", "read", "reads", "show", "shows",
        "display", "displays", "set", "create", "creates", "delete", "deletes",
        "add", "adds", "remove", "removes", "open", "opens", "close", "closes",
        "export", "exports", "import", "imports", "generate", "start", "stop",
        "enable", "disable",
    }
    if first_word.lower() in verb_indicators:
        return "verb_phrase"
    return "fragment"


def extract_placeholders(value: str) -> List[str]:
    return PLACEHOLDER_RE.findall(value)


def is_html(key: str, value: str) -> bool:
    return key in HTML_KEYS or value.strip().startswith("<html")


def detect_max_length_hint(key: str) -> Optional[int]:
    if key.endswith("_tooltip"):
        return None
    if key.endswith("_button") or key == "title":
        return 30
    if key.startswith("error_") or key.startswith("message_"):
        return None
    return None


def infer_related_keys(all_keys: List[str], key: str) -> List[str]:
    related: Set[str] = set()
    parts = key.split("_")
    if len(parts) > 1:
        for i in range(len(parts) - 1, 0, -1):
            prefix = "_".join(parts[:i])
            prefix_matches = [k for k in all_keys if k != key and k.startswith(prefix)]
            if prefix_matches:
                related.update(prefix_matches)
                break
    return sorted(related)[:8]


def compute_hash(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def get_java_class_for_bundle(bundle_name: str) -> Optional[str]:
    full_class = f"app.freerouting.{bundle_name}"
    java_path = JAVA_SOURCE_ROOT / f"{full_class.replace('.', '/')}.java"
    if java_path.exists():
        return full_class
    known_aliases = {
        "gui.AirLine": "app.freerouting.gui.workspace.progress.RatsNest",
        "drc.AirLine": "app.freerouting.gui.workspace.progress.RatsNest",
        "rules.NetClasses": "app.freerouting.gui.windows.routing.WindowNetClasses",
    }
    return known_aliases.get(bundle_name)


def human_readable_bundle_desc(bundle_name: str) -> str:
    area_map = {
        "gui.rendering.": "board graphics/colors",
        "gui.": "GUI (graphical user interface)",
        "interactive.": "interactive routing session",
        "rules.": "design rules",
        "Common": "shared/common strings",
    }
    for prefix, area in area_map.items():
        if bundle_name.startswith(prefix):
            return area
    if bundle_name == "Freerouting":
        return "main application"
    return "UI component"


def extract_all_context(context_dir: Path) -> Dict[str, Dict[str, Any]]:
    previous = load_context_dir(context_dir)
    java_usages = scan_java_key_usages()
    context: Dict[str, Dict[str, Any]] = {}
    all_keys_by_bundle: Dict[str, List[str]] = {}

    for props_file in english_properties_files():
        bundle = bundle_name_from_path(props_file)
        all_keys_by_bundle[bundle] = list(load_properties(props_file).keys())

    for props_file in english_properties_files():
        bundle = bundle_name_from_path(props_file)
        props = load_properties(props_file)
        bundle_java = java_usages.get(bundle, {})
        all_bundle_keys = all_keys_by_bundle.get(bundle, [])

        for key, value in props.items():
            if ICON_KEY_RE.match(value):
                continue

            qualified_key = f"{bundle}.{key}"
            placeholders = sorted(extract_placeholders(value))
            current_hash = compute_hash(value)
            prev_entry = previous.get(qualified_key)
            if prev_entry is None:
                needs_retranslation = False
            else:
                needs_retranslation = prev_entry.get("english_hash") != current_hash

            code_refs = sorted(bundle_java.get(key, []))
            related = infer_related_keys(all_bundle_keys, key)

            context[qualified_key] = {
                "bundle": bundle,
                "bundle_desc": human_readable_bundle_desc(bundle),
                "key": key,
                "english_hash": current_hash,
                "needs_retranslation": needs_retranslation,
                "ui_role": infer_ui_role(key),
                "grammatical_role": infer_grammatical_role(value),
                "has_placeholders": len(placeholders) > 0,
                "placeholders": placeholders,
                "is_html": is_html(key, value),
                "max_length_hint": detect_max_length_hint(key),
                "related_keys": related,
                "java_class": get_java_class_for_bundle(bundle),
                "code_references": code_refs,
            }

    return context


def print_summary(context: Dict[str, Dict[str, Any]]) -> None:
    bundles = {entry["bundle"] for entry in context.values()}
    ui_roles = {entry["ui_role"] for entry in context.values()}
    gram_roles = {entry["grammatical_role"] for entry in context.values()}
    with_placeholders = sum(1 for entry in context.values() if entry["has_placeholders"])
    html_count = sum(1 for entry in context.values() if entry["is_html"])
    with_code = sum(1 for entry in context.values() if entry.get("code_references"))
    flagged = sum(1 for entry in context.values() if entry.get("needs_retranslation"))

    out(f"\n{symbol('stats')} Context Extraction Summary:")
    out(f"   Total keys: {len(context)}")
    out(f"   Bundles: {len(bundles)}")
    out(f"   UI roles: {', '.join(sorted(ui_roles))}")
    out(f"   Grammatical roles: {', '.join(sorted(gram_roles))}")
    out(f"   Keys with placeholders: {with_placeholders}")
    out(f"   HTML-formatted keys: {html_count}")
    out(f"   Keys with Java code references: {with_code}")
    out(f"   Keys flagged needs_retranslation: {flagged}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Extract context metadata from English .properties files"
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_CONTEXT_DIR,
        help=f"Output directory for per-bundle context JSON (default: {DEFAULT_CONTEXT_DIR})",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Verify committed context matches current English sources (exit 1 if stale)",
    )
    parser.add_argument(
        "--sync-translated",
        action="store_true",
        help="Clear needs_retranslation for keys that already have locale translations",
    )
    parser.add_argument(
        "--locale",
        "-l",
        type=str,
        help="Target locale for --sync-translated (e.g. de)",
    )
    parser.add_argument(
        "--all",
        "-a",
        action="store_true",
        help="All supported locales for --sync-translated",
    )
    args = parser.parse_args()

    if args.sync_translated:
        if not args.locale and not args.all:
            parser.error("Specify --locale or --all with --sync-translated")
        context = load_context_dir(args.output)
        locales = SUPPORTED_LOCALES if args.all else [args.locale]
        total_cleared = 0
        for locale in locales:
            cleared = sync_translated_flags(context, locale)
            total_cleared += cleared
            out(f"{symbol('ok')} {locale}: cleared needs_retranslation on {cleared} keys")
        save_context_dir(context, args.output)
        out(f"{symbol('stats')} Synced translated flags ({total_cleared} keys total)")
        sys.exit(0)

    computed = extract_all_context(args.output)

    if args.check:
        committed = load_context_dir(args.output)
        diffs = context_diff(computed, committed)
        if diffs:
            out(f"{symbol('fail')} Context is stale — run extract-context.py and commit scripts/i18n/context/")
            for line in diffs[:30]:
                out(f"   - {line}")
            if len(diffs) > 30:
                out(f"   ... and {len(diffs) - 30} more")
            sys.exit(1)
        out(f"{symbol('ok')} Context is up to date ({len(computed)} keys)")
        sys.exit(0)

    save_context_dir(computed, args.output)
    out(f"{symbol('ok')} Wrote context metadata for {len(computed)} keys to {args.output}")
    print_summary(computed)


if __name__ == "__main__":
    main()
