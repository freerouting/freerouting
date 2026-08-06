#!/usr/bin/env python3
"""Remove locale keys whose value equals English (verbatim copy / fill artifacts)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from properties_io import SUPPORTED_LOCALES, english_properties_files, load_properties, write_properties


def strip_bundle(english_path: Path, apply: bool) -> tuple[int, int]:
    english_keys = load_properties(english_path)
    removed_total = 0
    files_touched = 0

    for locale in SUPPORTED_LOCALES:
        locale_path = english_path.parent / english_path.name.replace("_en.", f"_{locale}.")
        if not locale_path.exists():
            continue
        locale_keys = load_properties(locale_path)
        before = len(locale_keys)
        for key, english_value in english_keys.items():
            if locale_keys.get(key) == english_value:
                locale_keys.pop(key, None)
        after = len(locale_keys)
        if after != before:
            files_touched += 1
            removed_total += before - after
            if apply:
                write_properties(locale_path, locale_keys)

    return removed_total, files_touched


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()

    mode = "APPLY" if args.apply else "DRY-RUN"
    print(f"=== strip-english-copied-locale-keys ({mode}) ===")

    total_keys = 0
    total_files = 0
    for english_path in english_properties_files():
        removed, files = strip_bundle(english_path, args.apply)
        if removed:
            bundle = english_path.name.replace("_en.properties", "")
            print(f"  {bundle}: removed {removed} key(s) from {files} locale file(s)")
            total_keys += removed
            total_files += files

    print(f"Done. Removed {total_keys} entries from {total_files} file(s)")
    if not args.apply and total_keys:
        print("Re-run with --apply to write changes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
