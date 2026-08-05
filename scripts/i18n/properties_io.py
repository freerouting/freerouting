"""Shared .properties file I/O and bundle path helpers."""

from __future__ import annotations

import os
import time
from pathlib import Path
from typing import Dict, List

RESOURCE_ROOT = Path("src/main/resources/app/freerouting")

PLACEHOLDER_RE = __import__("re").compile(r"(%[sd]|%\.\d+f|%[df]|\{\{[^}]+\}\})")
ICON_KEY_RE = __import__("re").compile(r"^\{\{icon:.+\}\}$")

# Target locales for translation/validation (English "en" is the source bundle).
SUPPORTED_LOCALES = [
    "ar", "bn", "de", "es", "fr", "hi", "it", "ja", "ko", "pt", "ru", "zh", "zh_tw",
]

_WRITE_RETRIES = 3
_WRITE_RETRY_DELAY_S = 0.25


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


def sanitize_property_value(value: str) -> str:
    """Remove characters that break .properties files or Windows file I/O."""
    if not value:
        return value
    # NUL and lone CR confuse Java loaders and can trigger Windows write errors.
    cleaned = value.replace("\x00", "").replace("\r", "")
    return cleaned.strip()


def write_properties(path: Path, props: Dict[str, str]) -> None:
    """Write a .properties file with sorted keys (atomic replace, brief retry)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        f"{key}={sanitize_property_value(props[key])}\n" for key in sorted(props.keys())
    ]
    content = "".join(lines)
    tmp_path = path.with_name(path.name + ".tmp")

    last_error: OSError | None = None
    for attempt in range(_WRITE_RETRIES):
        try:
            with open(tmp_path, "w", encoding="utf-8", newline="\n") as f:
                f.write(content)
            os.replace(tmp_path, path)
            return
        except OSError as exc:
            last_error = exc
            if tmp_path.exists():
                try:
                    tmp_path.unlink()
                except OSError:
                    pass
            if attempt + 1 < _WRITE_RETRIES:
                time.sleep(_WRITE_RETRY_DELAY_S)
    raise OSError(
        f"Failed to write {path} after {_WRITE_RETRIES} attempts: {last_error}"
    ) from last_error
