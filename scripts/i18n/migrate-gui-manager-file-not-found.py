#!/usr/bin/env python3
"""Replace file_not_found_prefix/suffix with file_not_found in GuiManager locale bundles."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from properties_io import load_properties, write_properties

RESOURCE_ROOT = Path("src/main/resources/app/freerouting/gui")


def main() -> int:
    changed = 0
    for path in sorted(RESOURCE_ROOT.glob("GuiManager_*.properties")):
        props = load_properties(path)
        if "file_not_found_prefix" not in props and "file_not_found_suffix" not in props:
            continue
        prefix = props.pop("file_not_found_prefix", "")
        suffix = props.pop("file_not_found_suffix", "")
        if "file_not_found" not in props:
            if path.name == "GuiManager_en.properties":
                props["file_not_found"] = "File '{{filename}}' not found."
            else:
                props["file_not_found"] = f"{prefix.strip()} {{{{filename}}}} {suffix.strip()}".strip()
        write_properties(path, props)
        changed += 1
        print(path)
    print(f"Updated {changed} GuiManager locale file(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
