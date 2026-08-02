#!/usr/bin/env python3
"""Remove unused i18n keys reported by EnglishPropertiesParityTest."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from context_store import _compact_entry, write_json_deterministic
from properties_io import (
    SUPPORTED_LOCALES,
    english_properties_path,
    load_properties,
    locale_properties_path,
    write_properties,
)

REPO_ROOT = Path(__file__).resolve().parents[2]
REPORT_JSON = REPO_ROOT / "build/reports/i18n/EnglishBundlesContainUnusedKeysReport.json"
CONTEXT_DIR = REPO_ROOT / "scripts/i18n/context"

# Bundles with no matching Java class in src/main/java (legacy v1.9-only UI).
ORPHAN_BUNDLES = {
    "app.freerouting.gui.WindowSnapshot",
    "app.freerouting.gui.WindowSnapshotSettings",
    "app.freerouting.gui.WindowNetSamples",
    "app.freerouting.interactive.RatsNest",
}


def bundle_class_to_resource_name(bundle_class: str) -> str:
    prefix = "app.freerouting."
    if not bundle_class.startswith(prefix):
        raise ValueError(f"Unexpected bundle class: {bundle_class}")
    return bundle_class[len(prefix) :]


def parse_report_items(items: List[str]) -> Dict[str, Set[str]]:
    unused_by_bundle: Dict[str, Set[str]] = {}
    for block in items:
        lines = block.replace("\r\n", "\n").split("\n")
        if not lines:
            continue
        bundle = lines[0].strip()
        keys: Set[str] = set()
        for line in lines:
            stripped = line.strip()
            if stripped.startswith("- "):
                keys.add(stripped[2:])
        if keys:
            unused_by_bundle[bundle] = keys
    return unused_by_bundle


def load_unused_keys(report_path: Path) -> Dict[str, Set[str]]:
    if not report_path.exists():
        print(f"Report not found: {report_path}", file=sys.stderr)
        print("Run: ./gradlew test --tests app.freerouting.i18n.EnglishPropertiesParityTest.englishBundlesDoNotContainUnusedKeys", file=sys.stderr)
        sys.exit(1)
    data = json.loads(report_path.read_text(encoding="utf-8"))
    return parse_report_items(data.get("items", []))


def prune_bundle_keys(bundle_resource: str, keys_to_remove: Set[str], apply: bool) -> Tuple[int, int]:
    english_path = english_properties_path(bundle_resource)
    if not english_path.exists():
        return 0, 0

    removed = 0
    files_touched = 0
    locale_paths = [english_path]
    for locale in SUPPORTED_LOCALES:
        locale_paths.append(locale_properties_path(english_path, locale))

    for path in locale_paths:
        if not path.exists():
            continue
        props = load_properties(path)
        before = len(props)
        for key in keys_to_remove:
            if key in props:
                del props[key]
        after = len(props)
        if after != before:
            files_touched += 1
            removed += before - after
            if apply:
                write_properties(path, props)
    return removed, files_touched


def delete_orphan_bundle(bundle_class: str, apply: bool) -> int:
    bundle_resource = bundle_class_to_resource_name(bundle_class)
    english_path = english_properties_path(bundle_resource)
    if not english_path.exists():
        return 0

    deleted = 0
    paths = [english_path]
    for locale in SUPPORTED_LOCALES:
        paths.append(locale_properties_path(english_path, locale))

    for path in paths:
        if path.exists():
            deleted += 1
            if apply:
                path.unlink()

    context_name = bundle_resource.replace(".", ".", 1)
    context_file = CONTEXT_DIR / f"{context_name}.json"
    if context_file.exists():
        deleted += 1
        if apply:
            context_file.unlink()

    return deleted


def prune_context_keys(bundle_class: str, keys_to_remove: Set[str], apply: bool) -> int:
    bundle_resource = bundle_class_to_resource_name(bundle_class)
    context_file = CONTEXT_DIR / f"{bundle_resource}.json"
    if not context_file.exists():
        return 0

    bundle_keys = json.loads(context_file.read_text(encoding="utf-8"))
    before = len(bundle_keys)
    for key in keys_to_remove:
        bundle_keys.pop(key, None)
    after = len(bundle_keys)
    if after != before and apply:
        compact = {key: _compact_entry(meta) for key, meta in sorted(bundle_keys.items())}
        write_json_deterministic(context_file, compact)
    return before - after


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=REPORT_JSON)
    parser.add_argument("--apply", action="store_true", help="Write changes (default is dry-run)")
    args = parser.parse_args()

    unused_by_bundle = load_unused_keys(args.report)
    mode = "APPLY" if args.apply else "DRY-RUN"
    print(f"=== prune-unused-keys ({mode}) ===")

    total_keys = 0
    total_files = 0

    for bundle_class in sorted(ORPHAN_BUNDLES):
        if bundle_class in unused_by_bundle or english_properties_path(bundle_class_to_resource_name(bundle_class)).exists():
            count = delete_orphan_bundle(bundle_class, args.apply)
            if count:
                print(f"  orphan bundle {bundle_class}: removed {count} file(s)")
                total_files += count
        unused_by_bundle.pop(bundle_class, None)

    for bundle_class, keys in sorted(unused_by_bundle.items()):
        bundle_resource = bundle_class_to_resource_name(bundle_class)
        removed, files = prune_bundle_keys(bundle_resource, keys, args.apply)
        context_removed = prune_context_keys(bundle_class, keys, args.apply)
        if removed or context_removed:
            print(
                f"  {bundle_class}: {len(keys)} unused key(s), "
                f"{removed} property entries from {files} file(s)"
                + (f", {context_removed} context entries" if context_removed else "")
            )
            total_keys += len(keys)
            total_files += files

    print(f"Done. Bundles processed: {len(unused_by_bundle) + len(ORPHAN_BUNDLES & set(unused_by_bundle))}, keys targeted: {total_keys}")
    if not args.apply:
        print("Re-run with --apply to write changes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
