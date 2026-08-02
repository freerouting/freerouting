"""Split, compact context storage (english text lives in *_en.properties)."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Set

DEFAULT_CONTEXT_DIR = Path("scripts/i18n/context")
LEGACY_CONTEXT_FILE = Path("scripts/i18n/i18n-context.json")

# Fields persisted to disk (english_value is read from properties at runtime).
PERSISTED_FIELDS = {
    "english_hash",
    "needs_retranslation",
    "ui_role",
    "grammatical_role",
    "has_placeholders",
    "placeholders",
    "is_html",
    "max_length_hint",
    "related_keys",
    "java_class",
    "code_references",
    "bundle_desc",
}


def _bundle_file_name(bundle: str) -> str:
    return bundle.replace("/", "_") + ".json"


def _compact_entry(entry: Dict[str, Any]) -> Dict[str, Any]:
    return {k: entry[k] for k in PERSISTED_FIELDS if k in entry}


def load_context_dir(context_dir: Path = DEFAULT_CONTEXT_DIR) -> Dict[str, Dict[str, Any]]:
    """Load all bundle context files into qualified-key map."""
    if not context_dir.exists():
        return load_legacy_context(LEGACY_CONTEXT_FILE)

    context: Dict[str, Dict[str, Any]] = {}
    for path in sorted(context_dir.glob("*.json")):
        bundle = path.stem
        with open(path, "r", encoding="utf-8") as f:
            bundle_keys: Dict[str, Any] = json.load(f)
        for key, meta in bundle_keys.items():
            qualified = f"{bundle}.{key}"
            entry = dict(meta)
            entry["bundle"] = bundle
            entry["key"] = key
            context[qualified] = entry
    return context


def load_legacy_context(path: Path) -> Dict[str, Dict[str, Any]]:
    if not path.exists():
        return {}
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_context_dir(
    context: Dict[str, Dict[str, Any]],
    context_dir: Path = DEFAULT_CONTEXT_DIR,
) -> None:
    """Write context split by bundle; omits english_value from disk."""
    context_dir.mkdir(parents=True, exist_ok=True)

    by_bundle: Dict[str, Dict[str, Any]] = {}
    for qualified, entry in context.items():
        bundle = entry["bundle"]
        key = entry["key"]
        by_bundle.setdefault(bundle, {})[key] = _compact_entry(entry)

    existing = {p.name for p in context_dir.glob("*.json")}
    written = set()
    for bundle, keys in sorted(by_bundle.items()):
        file_name = _bundle_file_name(bundle)
        written.add(file_name)
        path = context_dir / file_name
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(keys, f, indent=2, ensure_ascii=False)
            f.write("\n")

    for orphan in existing - written:
        (context_dir / orphan).unlink(missing_ok=True)


def normalize_for_check(entry: Dict[str, Any]) -> Dict[str, Any]:
    """Strip workflow-volatile fields before comparing committed vs computed context."""
    normalized = _compact_entry(entry)
    normalized.pop("needs_retranslation", None)
    return normalized


def context_diff(
    computed: Dict[str, Dict[str, Any]],
    committed: Dict[str, Dict[str, Any]],
) -> List[str]:
    """Return human-readable diffs; empty list means committed context is fresh."""
    diffs: List[str] = []
    computed_keys = set(computed)
    committed_keys = set(committed)

    for key in sorted(committed_keys - computed_keys):
        diffs.append(f"removed key: {key}")
    for key in sorted(computed_keys - committed_keys):
        diffs.append(f"new key: {key}")

    for key in sorted(computed_keys & committed_keys):
        if normalize_for_check(computed[key]) != normalize_for_check(committed[key]):
            diffs.append(f"metadata changed: {key}")

    return diffs


def filter_bundles(
    context: Dict[str, Dict[str, Any]],
    bundles: Optional[Iterable[str]],
) -> Dict[str, Dict[str, Any]]:
    if not bundles:
        return context
    allowed: Set[str] = set(bundles)
    return {
        qk: entry for qk, entry in context.items() if entry.get("bundle") in allowed
    }


def mark_keys_translated(
    context: Dict[str, Dict[str, Any]],
    bundle_name: str,
    keys: Iterable[str],
) -> None:
    for key in keys:
        qualified = f"{bundle_name}.{key}"
        entry = context.get(qualified)
        if entry is not None:
            entry["needs_retranslation"] = False
