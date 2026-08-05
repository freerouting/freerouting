"""Context-augmented prompt construction for single and batch translation."""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from glossary import glossary_prompt_lines


def enrich_entry(entry: Dict[str, Any], english_value: str) -> Dict[str, Any]:
    merged = dict(entry)
    merged["english_value"] = english_value
    return merged


def _latin_script_preserved_names() -> str:
    return "DSN, SES, Specctra, Freerouting, Andras Fuchs"


def _rules_block() -> List[str]:
    preserved = _latin_script_preserved_names()
    return [
        "RULES:",
        "  - Preserve ALL placeholder tokens (%s, %d, {{...}} etc.) exactly as shown",
        "  - Preserve ALL HTML tags (<html>, <b>, <br>, etc.) exactly as shown",
        "  - Preserve Java .properties escape sequences VERBATIM as two-character tokens:",
        "    \\n (backslash + letter n), \\t, \\r, \\f, \\\\, \\\" — NOT real line breaks or tabs",
        "  - If ENGLISH contains \\n, your translation must contain the same number of \\n tokens",
        "  - In JSON responses, write \\n inside strings (e.g. \"Line one\\nLine two\"), never literal newlines",
        "  - Keep the same level of formality as the original",
        "  - Do NOT add or remove punctuation that changes meaning",
        "  - Translate UI text into the target language; use the localized PCB term from",
        "    the glossary (the text after '='), not the English glossary key",
        "  - Do NOT leave untranslated English UI words (e.g. clearance, shove fixed)",
        "    unless they appear in the preserved Latin-script list below",
        f"  - Keep these names in original Latin script exactly: {preserved}",
    ]


def build_single_prompt(
    entry: Dict[str, Any],
    locale: str,
    *,
    previous_translation: Optional[str] = None,
) -> str:
    english_value = entry.get("english_value", "")
    lines = [
        f"Translate the following UI string from English to {locale.upper()}.",
        "",
        _context_block(entry),
        "",
        *glossary_prompt_lines(locale),
        "",
        *_rules_block(),
        "  - Respond with ONLY the translated text, no explanations",
        "",
    ]

    if previous_translation:
        lines.extend([
            f"PREVIOUS TRANSLATION (outdated — improve for the new English): \"{previous_translation}\"",
            "",
        ])

    lines.extend([
        f"ENGLISH: \"{english_value}\"",
        f"TRANSLATION ({locale.upper()}):",
    ])
    return "\n".join(lines)


def build_batch_prompt(
    bundle: str,
    entries: List[Dict[str, Any]],
    locale: str,
    *,
    previous_by_key: Optional[Dict[str, str]] = None,
) -> str:
    previous_by_key = previous_by_key or {}
    lines = [
        f"Translate the following UI strings from English to {locale.upper()}.",
        f"Bundle: {bundle}",
        "",
        *_rules_block(),
        "  - Respond with ONLY a JSON object mapping each key to its translation",
        "  - Example: {\"save\": \"Speichern\", \"confirm_cancel\": \"Text\\nMore text\"}",
        "  - Use \\n (two characters) inside JSON string values, never literal line breaks",
        "",
        *glossary_prompt_lines(locale),
        "",
        "STRINGS:",
    ]

    for entry in entries:
        key = entry["key"]
        ctx = _context_block(entry, indent="  ")
        lines.append(f"  KEY: {key}")
        lines.append(ctx)
        lines.append(f"  ENGLISH: \"{entry.get('english_value', '')}\"")
        prev = previous_by_key.get(key)
        if prev:
            lines.append(f"  PREVIOUS TRANSLATION (outdated): \"{prev}\"")
        lines.append("")

    lines.append("JSON RESPONSE:")
    return "\n".join(lines)


def _context_block(entry: Dict[str, Any], indent: str = "") -> str:
    p = indent
    lines = [
        f"{p}CONTEXT:",
        f"{p}  Bundle: {entry.get('bundle', '')} ({entry.get('bundle_desc', '')})",
        f"{p}  UI Role: {entry.get('ui_role', 'label')}",
        f"{p}  Grammatical Role: {entry.get('grammatical_role', 'fragment')}",
    ]
    if entry.get("has_placeholders"):
        ph = ", ".join(entry.get("placeholders", []))
        lines.append(f"{p}  Placeholders: {ph} — KEEP exactly as-is")
    else:
        lines.append(f"{p}  Placeholders: none")

    html = entry.get("is_html", False)
    lines.append(
        f"{p}  HTML: {'yes — preserve all HTML tags exactly' if html else 'no'}"
    )

    max_len = entry.get("max_length_hint")
    lines.append(f"{p}  Max Length: {max_len if max_len else 'no limit'}")

    related = entry.get("related_keys") or []
    if related:
        lines.append(f"{p}  Related Keys: {', '.join(related[:5])}")

    code_refs = entry.get("code_references") or []
    if code_refs:
        lines.append(f"{p}  Used in Java: {', '.join(code_refs[:3])}")

    return "\n".join(lines)
