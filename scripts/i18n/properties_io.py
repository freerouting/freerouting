"""Shared .properties file I/O and bundle path helpers."""

from __future__ import annotations

from pathlib import Path
from typing import Dict, List

RESOURCE_ROOT = Path("src/main/resources/app/freerouting")

PLACEHOLDER_RE = __import__("re").compile(r"(%[sd]|%\.\d+f|%[df]|\{\{[^}]+\}\})")
ICON_KEY_RE = __import__("re").compile(r"^\{\{icon:.+\}\}$")

SUPPORTED_LOCALES = [
    "de", "fr", "ru", "bn", "hi", "ko", "ja",
    "zh", "zh_tw", "ar", "pt", "es",
]


def bundle_name_from_path(path: Path) -> str:
    """Convert .../gui/BoardMenuFile_en.properties to 'gui.BoardMenuFile'."""
    rel = path.relative_to(RESOURCE_ROOT)
    name = str(rel.with_suffix("")).replace("\\", "/").replace("/", ".")
    if name.endswith("_en"):
        name = name[:-3]
    return name


def english_properties_files() -> List[Path]:
    return sorted(RESOURCE_ROOT.rglob("*_en.properties"))


def english_properties_path(bundle_name: str) -> Path:
    rel_path = bundle_name.replace(".", "/")
    return RESOURCE_ROOT / f"{rel_path}_en.properties"


def locale_properties_path(english_path: Path, locale: str) -> Path:
    locale_name = english_path.name.replace("_en.properties", f"_{locale}.properties")
    return english_path.parent / locale_name


def load_properties(path: Path) -> Dict[str, str]:
    """Load a .properties file, returning a dict of key->value."""
    result: Dict[str, str] = {}
    if not path.exists():
        return result
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    i = 0
    num_lines = len(lines)
    while i < num_lines:
        line = lines[i].strip()
        i += 1
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        while line.endswith("\\") and not line.endswith("\\\\") and i < num_lines:
            line = line[:-1] + lines[i].strip()
            i += 1
        if "=" in line:
            key, _, value = line.partition("=")
            result[key.strip()] = value.strip()
        elif ":" in line:
            key, _, value = line.partition(":")
            result[key.strip()] = value.strip()
    return result


def write_properties(path: Path, props: Dict[str, str]) -> None:
    """Write a .properties file with sorted keys."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for key in sorted(props.keys()):
            f.write(f"{key}={props[key]}\n")
