#!/usr/bin/env python3
"""
extract-context.py — Layer 1: Context Metadata Extraction

Scans all *_en.properties files and Java source code to build
i18n-context.json — a per-key context metadata file that provides
LLMs with enough information to produce high-quality translations.

Usage:
    python scripts/i18n/extract-context.py [--output scripts/i18n/i18n-context.json]
"""

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

RESOURCE_ROOT = Path("src/main/resources/app/freerouting")
JAVA_SOURCE_ROOT = Path("src/main/java")
DEFAULT_OUTPUT = Path("scripts/i18n/i18n-context.json")

# Regex patterns for inferring UI role from key name
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

# Keys whose values are HTML
HTML_KEYS: Set[str] = {"trace_hover_info", "pin_hover_info", "via_hover_info", "net_hover_info"}

# Placeholder patterns
PLACEHOLDER_RE = re.compile(r"(%[sd]|%\.\d+f|%[df]|\{\{[^}]+\}\})")

# Keys that are icon-only references
ICON_KEY_RE = re.compile(r"^\{\{icon:.+\}\}$")


def bundle_name_from_path(path: Path) -> str:
    """Convert a file path like .../gui/BoardMenuFile_en.properties to 'gui.BoardMenuFile'."""
    rel = path.relative_to(RESOURCE_ROOT)
    name = str(rel.with_suffix("")).replace("\\", "/").replace("/", ".")
    if name.endswith("_en"):
        name = name[:-3]
    return name


def english_properties_files() -> List[Path]:
    """Return all *_en.properties files under RESOURCE_ROOT."""
    return sorted(RESOURCE_ROOT.rglob("*_en.properties"))


def load_properties(path: Path) -> Dict[str, str]:
    """Load a .properties file, returning a dict of key->value."""
    result: Dict[str, str] = {}
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or line.startswith("!"):
                continue
            # Handle escaped line continuations
            while line.endswith("\\") and not line.endswith("\\\\"):
                line = line[:-1] + f.readline().strip()
            if "=" in line:
                key, _, value = line.partition("=")
                result[key.strip()] = value.strip()
            elif ":" in line:
                key, _, value = line.partition(":")
                result[key.strip()] = value.strip()
    return result


def infer_ui_role(key: str) -> str:
    """Infer the UI role from the key name using suffix/prefix patterns."""
    for pattern, role in UI_ROLE_PATTERNS:
        if re.search(pattern, key):
            return role
    return "label"


def infer_grammatical_role(value: str) -> str:
    """Infer grammatical role from the English value shape."""
    if len(value) < 5:
        return "fragment"
    if value[0].isupper() and value.endswith("."):
        return "full_sentence"
    if value[0].isupper() and not value.endswith("."):
        return "noun_phrase"
    # Starts with lowercase — likely verb phrase or fragment
    first_word = value.split()[0] if value.split() else ""
    # Common verb indicators
    verb_indicators = {"saves", "save", "write", "writes", "read", "reads",
                       "show", "shows", "display", "displays", "set", "create",
                       "creates", "delete", "deletes", "add", "adds", "remove",
                       "removes", "open", "opens", "close", "closes", "export",
                       "exports", "import", "imports", "generate", "start",
                       "stop", "enable", "disable"}
    if first_word.lower() in verb_indicators:
        return "verb_phrase"
    return "fragment"


def extract_placeholders(value: str) -> List[str]:
    """Extract placeholder expressions from a value string."""
    return PLACEHOLDER_RE.findall(value)


def is_html(value: str) -> bool:
    """Check if a value is HTML content."""
    return value.strip().startswith("<html") or value.strip().startswith("<html>")


def detect_max_length_hint(key: str) -> Optional[int]:
    """Return a suggested max length hint based on key patterns."""
    if key.endswith("_tooltip"):
        return None  # tooltips can be long
    if key.endswith("_button") or key == "title":
        return 30
    if key.startswith("error_") or key.startswith("message_"):
        return None  # messages can be full sentences
    return None


def infer_related_keys(all_keys: List[str], key: str) -> List[str]:
    """Find related keys that share a common prefix."""
    # Extract base prefix (before first underscore from the right,
    # or the full key if no underscore)
    parts = key.split("_")
    if len(parts) <= 1:
        return []
    # Try prefixes of decreasing length
    for i in range(len(parts) - 1, 0, -1):
        prefix = "_".join(parts[:i])
        related = [k for k in all_keys if k != key and k.startswith(prefix)]
        if related:
            return related
    return []


def compute_hash(value: str) -> str:
    """Compute SHA-256 hash of a string value for change detection."""
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def get_java_class_for_bundle(bundle_name: str) -> Optional[str]:
    """
    Try to determine which Java class uses this bundle.
    Maps "gui.BoardMenuFile" -> "app.freerouting.gui.BoardMenuFile"
    """
    # The bundle name after "app.freerouting." is the class path
    full_class = f"app.freerouting.{bundle_name}"
    java_path = JAVA_SOURCE_ROOT / f"{full_class.replace('.', '/')}.java"
    if java_path.exists():
        return full_class
    # Try some common aliases
    known_aliases = {
        "gui.AirLine": "app.freerouting.interactive.RatsNest",
        "drc.AirLine": "app.freerouting.interactive.RatsNest",
        "rules.NetClasses": "app.freerouting.gui.WindowNetClasses",
    }
    if bundle_name in known_aliases:
        return known_aliases[bundle_name]
    return None


def human_readable_bundle_desc(bundle_name: str) -> str:
    """Generate a human-readable bundle description for the LLM prompt."""
    # Map known bundle prefixes to UI areas
    area_map = {
        "gui.": "GUI (graphical user interface)",
        "interactive.": "interactive routing session",
        "boardgraphics.": "board graphics/colors",
        "rules.": "design rules",
        "Common": "shared/common strings",
    }
    for prefix, area in area_map.items():
        if bundle_name.startswith(prefix):
            return area
    if bundle_name == "Freerouting":
        return "main application"
    return "UI component"


def extract_all_context(output_path: Path) -> Dict[str, Dict[str, Any]]:
    """Extract context metadata from all English properties files."""
    context: Dict[str, Dict[str, Any]] = {}
    all_keys_by_bundle: Dict[str, List[str]] = {}

    # First pass: load all keys per bundle
    for props_file in english_properties_files():
        bundle = bundle_name_from_path(props_file)
        props = load_properties(props_file)
        all_keys_by_bundle[bundle] = list(props.keys())

    # Second pass: build context per key
    for props_file in english_properties_files():
        bundle = bundle_name_from_path(props_file)
        props = load_properties(props_file)
        all_bundle_keys = all_keys_by_bundle.get(bundle, [])

        for key, value in props.items():
            qualified_key = f"{bundle}.{key}"

            # Skip icon-only keys
            if ICON_KEY_RE.match(value):
                continue

            placeholders = extract_placeholders(value)
            html_flag = is_html(value)

            ctx: Dict[str, Any] = {
                "bundle": bundle,
                "bundle_desc": human_readable_bundle_desc(bundle),
                "key": key,
                "english_value": value,
                "english_hash": compute_hash(value),
                "ui_role": infer_ui_role(key),
                "grammatical_role": infer_grammatical_role(value),
                "has_placeholders": len(placeholders) > 0,
                "placeholders": placeholders,
                "is_html": html_flag,
                "max_length_hint": detect_max_length_hint(key),
                "related_keys": infer_related_keys(all_bundle_keys, key),
                "java_class": get_java_class_for_bundle(bundle),
            }
            context[qualified_key] = ctx

    return context


def write_context(context: Dict[str, Dict[str, Any]], output_path: Path) -> None:
    """Write context metadata to a JSON file."""
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(context, f, indent=2, ensure_ascii=False)
    print(f"✅ Wrote context metadata for {len(context)} keys to {output_path}")


def print_summary(context: Dict[str, Dict[str, Any]]) -> None:
    """Print a summary of the extracted context."""
    bundles = set(ctx["bundle"] for ctx in context.values())
    ui_roles = set(ctx["ui_role"] for ctx in context.values())
    gram_roles = set(ctx["grammatical_role"] for ctx in context.values())
    with_placeholders = sum(1 for ctx in context.values() if ctx["has_placeholders"])
    html_count = sum(1 for ctx in context.values() if ctx["is_html"])

    print(f"\n📊 Context Extraction Summary:")
    print(f"   Total keys: {len(context)}")
    print(f"   Bundles: {len(bundles)} ({', '.join(sorted(bundles))})")
    print(f"   UI roles found: {', '.join(sorted(ui_roles))}")
    print(f"   Grammatical roles: {', '.join(sorted(gram_roles))}")
    print(f"   Keys with placeholders: {with_placeholders}")
    print(f"   HTML-formatted keys: {html_count}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Extract context metadata from English .properties files"
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"Output path for context JSON (default: {DEFAULT_OUTPUT})",
    )
    args = parser.parse_args()

    context = extract_all_context(args.output)
    write_context(context, args.output)
    print_summary(context)


if __name__ == "__main__":
    main()