"""Shared .properties file I/O and bundle path helpers."""

from __future__ import annotations

import os
import re
import time
from collections import Counter
from pathlib import Path
from typing import Dict, List, Tuple

RESOURCE_ROOT = Path("src/main/resources/app/freerouting")

PLACEHOLDER_RE = __import__("re").compile(r"(%[sd]|%\.\d+f|%[df]|\{\{[^}]+\}\})")
ICON_KEY_RE = __import__("re").compile(r"^\{\{icon:.+\}\}$")

# Target locales for translation/validation (English "en" is the source bundle).
SUPPORTED_LOCALES = [
    "ar", "bn", "cs", "de", "es", "fr", "hi", "hu", "id", "it", "ja", "ko",
    "nl", "pl", "pt", "pt_br", "ro", "ru", "sv", "th", "tr", "uk", "vi", "zh", "zh_tw",
]

_WRITE_RETRIES = 3
_WRITE_RETRY_DELAY_S = 0.25

# Java .properties escape sequences we preserve verbatim in translated files.
_JAVA_ESCAPE_SEQUENCES: Tuple[str, ...] = ("\\n", "\\t", "\\r", "\\f", "\\\\", '\\"')
# Literal backslash + "n" as stored in .properties files (not a newline character).
PROPERTY_NEWLINE_TOKEN = "\\n"

_JAVA_ESCAPE_TOKEN_RE = re.compile(r'\\([ntrfb\\"])')


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


def normalize_property_escapes(value: str) -> str:
    """Convert control characters to Java-style escape sequences for .properties files."""
    if not value:
        return value
    normalized = value.replace("\r\n", "\n").replace("\r", "\n")
    out: List[str] = []
    for ch in normalized:
        if ch == "\n":
            out.append("\\n")
        elif ch == "\t":
            out.append("\\t")
        elif ch == "\f":
            out.append("\\f")
        elif ch == "\x00":
            continue
        else:
            out.append(ch)
    return "".join(out)


def count_property_escapes(value: str) -> Counter[str]:
    """Count Java escape tokens after normalizing control characters."""
    normalized = normalize_property_escapes(value)
    counts: Counter[str] = Counter()
    for match in _JAVA_ESCAPE_TOKEN_RE.finditer(normalized):
        token = "\\" + match.group(1)
        counts[token] += 1
    return counts


def validate_property_escapes(english: str, translation: str) -> Tuple[bool, Counter[str], Counter[str]]:
    """Return (ok, english_counts, locale_counts) for Java escape sequences."""
    eng_counts = count_property_escapes(english)
    loc_counts = count_property_escapes(translation)
    return eng_counts == loc_counts, eng_counts, loc_counts


def english_has_property_escapes(english: str) -> bool:
    return any(count_property_escapes(english).values())


def split_property_newlines(value: str) -> List[str]:
    """Split on literal \\n tokens (two-character sequences in the file)."""
    return value.split(PROPERTY_NEWLINE_TOKEN)


def join_property_newlines(segments: List[str]) -> str:
    """Join segments with literal \\n tokens."""
    return PROPERTY_NEWLINE_TOKEN.join(segments)


def sanitize_segment_translation(segment: str) -> str:
    """Force one segment to a single line with no embedded \\n tokens."""
    if not segment:
        return segment
    text = segment.replace("\r\n", " ").replace("\r", " ").replace("\n", " ")
    text = text.replace(PROPERTY_NEWLINE_TOKEN, " ")
    leading = len(text) - len(text.lstrip(" "))
    prefix = text[:leading]
    body = " ".join(text[leading:].split())
    return prefix + body


def validate_segment_join(english: str, translation: str) -> bool:
    """Verify segment-split counts match after segment-wise translation."""
    return len(split_property_newlines(english)) == len(split_property_newlines(translation))


def should_translate_by_segments(english: str, *, newline_threshold: int = 3) -> bool:
    """Use per-segment translation when English has many \\n tokens."""
    return count_property_escapes(english).get(PROPERTY_NEWLINE_TOKEN, 0) >= newline_threshold


def _split_property_line(line: str) -> tuple[str, str] | None:
    """Split on the first unescaped '=' or ':' (Java .properties key separator)."""
    for i, ch in enumerate(line):
        if ch in ("=", ":") and (i == 0 or line[i - 1] != "\\"):
            return line[:i].strip(), line[i + 1 :].strip()
    return None


def load_properties(path: Path) -> Dict[str, str]:
    """Load a .properties file, returning a dict of key->value."""
    result: Dict[str, str] = {}
    if not path.exists():
        return result
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    i = 0
    num_lines = len(lines)
    current_key: str | None = None
    while i < num_lines:
        line = lines[i].rstrip("\n\r")
        i += 1
        stripped = line.strip()
        if not stripped:
            if current_key is not None:
                result[current_key] = result[current_key] + "\n"
            continue
        if stripped.startswith("#") or stripped.startswith("!"):
            current_key = None
            continue
        while stripped.endswith("\\") and not stripped.endswith("\\\\") and i < num_lines:
            stripped = stripped[:-1] + lines[i].strip()
            i += 1
        split = _split_property_line(stripped)
        if split is not None:
            key, value = split
            result[key] = value
            current_key = key
        elif current_key is not None:
            # Orphan continuation line (LLM wrote a real newline instead of \n).
            result[current_key] = result[current_key] + "\n" + line.rstrip("\n\r")
        else:
            current_key = None
    return result


def sanitize_property_value(value: str) -> str:
    """Remove unsafe characters and normalize escape sequences for .properties files."""
    if not value:
        return value
    cleaned = normalize_property_escapes(value)
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
