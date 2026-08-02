"""Locale-specific PCB terminology glossaries for LLM prompts."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, List, Optional

GLOSSARY_DIR = Path("scripts/i18n/glossary")


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
