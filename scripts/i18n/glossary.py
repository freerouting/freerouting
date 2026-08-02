"""Locale-specific PCB terminology glossaries for LLM prompts."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, List, Optional

GLOSSARY_DIR = Path("scripts/i18n/glossary")

# All shipped UI locales, including English source (en uses _default + en.json).
GLOSSARY_LOCALES = [
    "ar", "bn", "de", "en", "es", "fr", "hi", "ja", "ko", "pt", "ru", "zh", "zh_tw",
]


def load_default_glossary() -> Dict[str, str]:
    default_path = GLOSSARY_DIR / "_default.json"
    if not default_path.exists():
        return {}
    with open(default_path, "r", encoding="utf-8") as f:
        return json.load(f)


def validate_glossaries() -> List[str]:
    """Return human-readable errors when locale glossaries are missing or incomplete."""
    errors: List[str] = []
    default_keys = set(load_default_glossary())
    if not default_keys:
        errors.append("glossary/_default.json is missing or empty")
        return errors

    for locale in GLOSSARY_LOCALES:
        locale_path = GLOSSARY_DIR / f"{locale}.json"
        if not locale_path.exists():
            errors.append(f"missing glossary/{locale}.json")
            continue
        with open(locale_path, "r", encoding="utf-8") as f:
            locale_terms = json.load(f)
        missing = sorted(default_keys - set(locale_terms))
        if missing:
            preview = ", ".join(missing[:5])
            suffix = f" (+{len(missing) - 5} more)" if len(missing) > 5 else ""
            errors.append(f"glossary/{locale}.json missing {len(missing)} term(s): {preview}{suffix}")
    return errors


def load_glossary(locale: str) -> Dict[str, str]:
    """
    Load glossary for a locale. Merges _default.json with {locale}.json.
    Values are full prompt lines describing how to translate each term.
    """
    terms: Dict[str, str] = {}
    default_path = GLOSSARY_DIR / "_default.json"
    if default_path.exists():
        with open(default_path, "r", encoding="utf-8") as f:
            terms.update(json.load(f))

    locale_path = GLOSSARY_DIR / f"{locale}.json"
    if locale_path.exists():
        with open(locale_path, "r", encoding="utf-8") as f:
            terms.update(json.load(f))
    return terms


def glossary_prompt_lines(locale: str) -> List[str]:
    terms = load_glossary(locale)
    lines = ["PCB TERMINOLOGY (keep these terms consistent across all translations):"]
    for term, description in sorted(terms.items()):
        lines.append(f"  - '{term}' = {description}")
    return lines
