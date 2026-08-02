"""Scan Java sources for TextManager getText/setText key usages."""

from __future__ import annotations

import re
from pathlib import Path
from typing import Dict, List, Set

JAVA_SOURCE_ROOT = Path("src/main/java")

TEXT_MANAGER_OWNER_PATTERN = re.compile(
    r"new\s+TextManager\s*\(\s*([A-Za-z_][A-Za-z0-9_$.]*)\.class"
)
TEXT_MANAGER_VARIABLE_PATTERN = re.compile(
    r"(?:TextManager\s+)?([A-Za-z_][A-Za-z0-9_\.]*)\s*=\s*new\s+TextManager\s*\("
)
THIS_CLASS_PATTERN = re.compile(r"new\s+TextManager\s*\(\s*(?:this\.)?getClass\s*\(")
SEGMENTED_BUTTONS_PATTERN = re.compile(r"new\s+SegmentedButtons\s*\((.*?)\)", re.DOTALL)
QUOTED_STRING_PATTERN = re.compile(r'"([^"]+)"')

BUNDLE_ALIASES = {
    "app.freerouting.gui.AirLine": "app.freerouting.interactive.RatsNest",
    "app.freerouting.drc.AirLine": "app.freerouting.interactive.RatsNest",
    "app.freerouting.rules.NetClasses": "app.freerouting.gui.WindowNetClasses",
}


def class_name_to_bundle(full_class: str) -> str:
    prefix = "app.freerouting."
    if full_class.startswith(prefix):
        return full_class[len(prefix):]
    return full_class


def _to_class_name(java_file: Path) -> str:
    rel = java_file.relative_to(JAVA_SOURCE_ROOT)
    return str(rel.with_suffix("")).replace("\\", "/").replace("/", ".")


def _resolve_class_name(current_package: str, reference: str) -> str:
    if reference.startswith("app.freerouting."):
        return reference
    if "." in reference:
        return f"{current_package.rsplit('.', 1)[0]}.{reference}"
    return f"{current_package}.{reference}"


def _resolve_bundle_owners(java_file: Path, source: str) -> Set[str]:
    current_class = _to_class_name(java_file)
    current_package = current_class.rsplit(".", 1)[0]
    owners: Set[str] = set()

    for match in TEXT_MANAGER_OWNER_PATTERN.finditer(source):
        owners.add(_resolve_class_name(current_package, match.group(1)))
    if THIS_CLASS_PATTERN.search(source):
        owners.add(current_class)

    resolved: Set[str] = set()
    for owner in owners:
        resolved.add(BUNDLE_ALIASES.get(owner, owner))
    return resolved


def _resolve_text_manager_variables(source: str) -> Set[str]:
    variables: Set[str] = set()
    for match in TEXT_MANAGER_VARIABLE_PATTERN.finditer(source):
        var = match.group(1)
        variables.add(var)
        if var.startswith("this."):
            variables.add(var[len("this."):])
    return variables


def _extract_keys_from_file(source: str) -> Set[str]:
    keys: Set[str] = set()
    variables = _resolve_text_manager_variables(source)
    for var in variables:
        get_text = re.compile(
            rf"\b(?:[A-Za-z_][A-Za-z0-9_]*\.)*{re.escape(var)}\.getText\(\s*\"([^\"]+)\""
        )
        set_text = re.compile(
            rf"\b(?:[A-Za-z_][A-Za-z0-9_]*\.)*{re.escape(var)}\.setText\(\s*[^,]+,\s*\"([^\"]+)\""
        )
        for pattern in (get_text, set_text):
            for match in pattern.finditer(source):
                key = match.group(1)
                if not key.startswith("{{icon:"):
                    keys.add(key)

    for match in SEGMENTED_BUTTONS_PATTERN.finditer(source):
        quoted = QUOTED_STRING_PATTERN.findall(match.group(1))
        if len(quoted) > 1:
            keys.update(quoted[1:])

    return keys


def scan_java_key_usages() -> Dict[str, Dict[str, List[str]]]:
    """
    Return map: bundle_name -> key -> list of referencing Java classes.
    Example: {"gui.BoardMenuFile": {"save": ["app.freerouting.gui.BoardMenuFile"]}}
    """
    usages: Dict[str, Dict[str, Set[str]]] = {}

    for java_file in JAVA_SOURCE_ROOT.rglob("*.java"):
        if java_file.name == "TextManager.java":
            continue
        source = java_file.read_text(encoding="utf-8")
        owners = _resolve_bundle_owners(java_file, source)
        if not owners:
            continue
        keys = _extract_keys_from_file(source)
        if not keys:
            continue
        java_class = _to_class_name(java_file)
        for owner in owners:
            bundle = class_name_to_bundle(owner)
            bundle_map = usages.setdefault(bundle, {})
            for key in keys:
                bundle_map.setdefault(key, set()).add(java_class)

    return {
        bundle: {key: sorted(classes) for key, classes in key_map.items()}
        for bundle, key_map in usages.items()
    }
