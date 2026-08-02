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
SET_LANGUAGE_PATTERN = re.compile(r"\bsetLanguage\s*\(")
INHERITED_TM_USAGE_PATTERN = re.compile(r"\btm\.(?:getText|setText)\s*\(")
EXTENDS_INTERACTIVE_STATE_PATTERN = re.compile(
    r"\bextends\s+(?:InteractiveState|\w+State)\b"
)
DYNAMIC_GET_TEXT_PATTERN = re.compile(r"\bgetText\(\s*(.+?)\.toString\(\)\s*\)")
LOCAL_ENUM_ARRAY_PATTERN = re.compile(
    r"(?:final\s+)?([A-Za-z_][A-Za-z0-9_.]*)\[\]\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*\1\.values\(\)\s*;"
)
TEXT_MANAGER_SUFFIX_USAGE_PATTERN = re.compile(
    r"\b([A-Za-z_][A-Za-z0-9_]*)tm\.(?:getText|setText)\s*\("
)
CREATE_WORD_WRAP_LABEL_PATTERN = re.compile(r"\bcreateWordWrapLabel\s*\(\s*\"([^\"]+)\"")
STATIC_STRING_ARRAY_PATTERN = re.compile(
    r"private\s+static\s+final\s+String\[\]\s+[A-Za-z_][A-Za-z0-9_]*\s*=\s*\{([^}]+)\}"
)
SEGMENTED_BUTTONS_PATTERN = re.compile(r"new\s+SegmentedButtons\s*\((.*?)\)", re.DOTALL)
QUOTED_STRING_PATTERN = re.compile(r'"([^"]+)"')
FIELD_DECLARATION_PATTERN = re.compile(
    r"(?m)^\s*(?:private|protected|public)?\s*(?:static\s+)?(?:final\s+)?"
    r"([A-Z][A-Za-z0-9_$.<>]*)\s+([a-z_][A-Za-z0-9_]*)\s*(?:[=;])"
)
ENUM_ARRAY_DECLARATION_PATTERN = re.compile(
    r"(?m)^\s*(?:final\s+)?([A-Z][A-Za-z0-9_$.<>]*)\[\]\s+([a-z_][A-Za-z0-9_]*)"
    r"\s*=\s*[^;]*\.values\(\)\s*;"
)

BUNDLE_ALIASES = {
    "app.freerouting.rules.NetClasses": "app.freerouting.gui.WindowNetClasses",
}

_source_files_by_name: Dict[str, Path] | None = None


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
    if SET_LANGUAGE_PATTERN.search(source) and INHERITED_TM_USAGE_PATTERN.search(source):
        owners.add(current_class)
    if INHERITED_TM_USAGE_PATTERN.search(source):
        owners.add(current_class)
    if "extends WindowObjectList" in source:
        owners.add("app.freerouting.gui.WindowObjectList")
    if (
        EXTENDS_INTERACTIVE_STATE_PATTERN.search(source)
        and INHERITED_TM_USAGE_PATTERN.search(source)
        and current_class.startswith("app.freerouting.interactive.")
        and current_class != "app.freerouting.interactive.InteractiveState"
    ):
        owners.add("app.freerouting.interactive.InteractiveState")

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
    if INHERITED_TM_USAGE_PATTERN.search(source):
        variables.add("tm")
    for match in TEXT_MANAGER_SUFFIX_USAGE_PATTERN.finditer(source):
        variables.add(match.group(1) + "tm")
    return variables


def _collect_keys_for_variable(source: str, var: str, keys: Set[str]) -> None:
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


def _source_files_cache() -> Dict[str, Path]:
    global _source_files_by_name
    if _source_files_by_name is None:
        _source_files_by_name = {
            path.name: path for path in JAVA_SOURCE_ROOT.rglob("*.java")
        }
    return _source_files_by_name


def _find_enum_body(source: str, enum_type: str) -> str | None:
    pattern = re.compile(rf"(?s)\benum\s+{re.escape(enum_type)}\s*\{{")
    match = pattern.search(source)
    if not match:
        return None
    start = match.end()
    depth = 1
    for index, char in enumerate(source[start:], start=start):
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start:index]
    return None


def _resolve_enum_constants(source: str, enum_type: str) -> Set[str]:
    enum_body = _find_enum_body(source, enum_type)
    if enum_body is None:
        enum_file = _source_files_cache().get(f"{enum_type}.java")
        if enum_file is not None:
            enum_body = _find_enum_body(enum_file.read_text(encoding="utf-8"), enum_type)
    if enum_body is None:
        enum_pattern = re.compile(rf"(?s)\benum\s+{re.escape(enum_type)}\s*\{{")
        for java_file in _source_files_cache().values():
            enum_source = java_file.read_text(encoding="utf-8")
            if enum_pattern.search(enum_source):
                enum_body = _find_enum_body(enum_source, enum_type)
                if enum_body is not None:
                    break
    if enum_body is None:
        return set()

    end = enum_body.find(";")
    constant_section = enum_body[:end] if end >= 0 else enum_body
    constant_section = re.sub(r"//.*", "", constant_section)
    constant_section = re.sub(r"/\*.*?\*/", "", constant_section, flags=re.DOTALL)
    constants: Set[str] = set()
    for raw in constant_section.split(","):
        constant = raw.strip()
        if not constant:
            continue
        for sep in ("(", "{"):
            if sep in constant:
                constant = constant[: constant.index(sep)].strip()
        if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", constant):
            constants.add(constant)
    return constants


def _resolve_dynamic_enum_keys(source: str) -> Set[str]:
    keys: Set[str] = set()
    field_types = {
        match.group(2): match.group(1)
        for match in FIELD_DECLARATION_PATTERN.finditer(source)
    }
    enum_array_types = {
        match.group(2): match.group(1).split(".")[-1]
        for match in ENUM_ARRAY_DECLARATION_PATTERN.finditer(source)
    }
    for match in LOCAL_ENUM_ARRAY_PATTERN.finditer(source):
        enum_array_types[match.group(2)] = match.group(1).split(".")[-1]

    for match in DYNAMIC_GET_TEXT_PATTERN.finditer(source):
        expression = match.group(1).strip()
        if "[" in expression:
            expression = expression[: expression.index("[")].strip()
        if ".values()" in expression:
            enum_type = expression[: expression.index(".values()")].split(".")[-1]
            keys.update(_resolve_enum_constants(source, enum_type))
            continue
        if expression.startswith("this."):
            expression = expression[len("this.") :]
        enum_type = field_types.get(expression) or enum_array_types.get(expression)
        if enum_type is not None:
            keys.update(_resolve_enum_constants(source, enum_type.split(".")[-1]))
    return keys


def _extract_keys_from_file(source: str) -> Set[str]:
    keys: Set[str] = set()
    variables = _resolve_text_manager_variables(source)
    for var in variables:
        _collect_keys_for_variable(source, var, keys)

    for match in SEGMENTED_BUTTONS_PATTERN.finditer(source):
        quoted = QUOTED_STRING_PATTERN.findall(match.group(1))
        if len(quoted) > 1:
            keys.update(quoted[1:])

    for match in CREATE_WORD_WRAP_LABEL_PATTERN.finditer(source):
        keys.add(match.group(1))

    for match in STATIC_STRING_ARRAY_PATTERN.finditer(source):
        keys.update(QUOTED_STRING_PATTERN.findall(match.group(1)))

    keys.update(_resolve_dynamic_enum_keys(source))
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
