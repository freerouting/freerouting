#!/usr/bin/env python3
"""
translate.py — Layer 2: LLM Translation Runner

Uses the context metadata from i18n-context.json to translate
English .properties files into target locales. Each key is sent
to an LLM API with its full context (bundle, UI role, placeholders, etc.)
to produce higher-quality translations.

Configuration via environment variables:
  LLM_PROVIDER=openai|anthropic|ollama (default: openai)
  LLM_API_KEY=sk-...               (default: reads OPENAI_API_KEY)
  LLM_MODEL=gpt-4o-mini            (default: gpt-4o-mini)
  LLM_BASE_URL=...                 (default: https://api.openai.com/v1)

Usage:
    python scripts/i18n/translate.py --locale de
    python scripts/i18n/translate.py --locale fr --dry-run
    python scripts/i18n/translate.py --all
    python scripts/i18n/translate.py --locale de --input scripts/i18n/i18n-context.json
    python scripts/i18n/translate.py --locale de --missing-only  # Only translate missing/stale keys
"""

import argparse
import hashlib
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

RESOURCE_ROOT = Path("src/main/resources/app/freerouting")
DEFAULT_CONTEXT = Path("scripts/i18n/i18n-context.json")

SUPPORTED_LOCALES = [
    "de", "fr", "ru", "bn", "hi", "ko", "ja",
    "zh", "zh_tw", "ar", "pt", "es"
]

PLACEHOLDER_RE = re.compile(r"(%[sd]|%\.\d+f|%[df]|\{\{[^}]+\}\})")
ICON_KEY_RE = re.compile(r"^\{\{icon:.+\}\}$")


def load_context(path: Path) -> Dict[str, Dict[str, Any]]:
    """Load the context metadata JSON file."""
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


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
    with open(path, "w", encoding="utf-8") as f:
        for key in sorted(props.keys()):
            f.write(f"{key}={props[key]}\n")


def english_properties_path(context_entry: Dict[str, Any]) -> Path:
    """Given a context entry, return the path to its English properties file."""
    bundle = context_entry["bundle"]
    # Convert "gui.BoardMenuFile" -> "gui/BoardMenuFile_en.properties"
    rel_path = bundle.replace(".", "/")
    return RESOURCE_ROOT / f"{rel_path}_en.properties"


def locale_properties_path(english_path: Path, locale: str) -> Path:
    """Given an English properties file path, return the path for a locale."""
    name = english_path.name
    # Replace _en.properties with _{locale}.properties
    locale_name = name.replace("_en.properties", f"_{locale}.properties")
    return english_path.parent / locale_name


def build_prompt(key: str, ctx: Dict[str, Any], locale: str) -> str:
    """Build a context-augmented prompt for a single key."""
    bundle_desc = ctx.get("bundle_desc", "")
    ui_role = ctx.get("ui_role", "label")
    gram_role = ctx.get("grammatical_role", "fragment")
    has_placeholders = ctx.get("has_placeholders", False)
    placeholders = ctx.get("placeholders", [])
    is_html = ctx.get("is_html", False)
    related = ctx.get("related_keys", [])
    max_len = ctx.get("max_length_hint")
    english_value = ctx.get("english_value", "")

    prompt_parts = [
        f"Translate the following UI string from English to {locale.upper()}.",
        "",
        "CONTEXT:",
        f"  Bundle: {ctx['bundle']} ({bundle_desc})",
        f"  UI Role: {ui_role}",
        f"  Grammatical Role: {gram_role}",
    ]

    if has_placeholders:
        prompt_parts.append(f"  Placeholders: {', '.join(placeholders)} — KEEP these EXACTLY as-is")
    else:
        prompt_parts.append("  Placeholders: none")

    prompt_parts.append(f"  HTML: {'yes — preserve all HTML tags exactly' if is_html else 'no'}")

    if max_len:
        prompt_parts.append(f"  Max Length: {max_len} characters")
    else:
        prompt_parts.append(f"  Max Length: no limit")

    if related:
        prompt_parts.append(f"  Related Keys: {', '.join(related[:5])}")

    prompt_parts.extend([
        "",
        "PCB TERMINOLOGY (keep these terms consistent across all translations):",
        "  - 'via' = a plated hole connecting layers (keep as 'via' or translate to the local PCB term)",
        "  - 'trace' = a copper track/wire on the board",
        "  - 'net' = an electrical connection between pins",
        "  - 'padstack' = the hole + pad pattern for a component pin or via",
        "  - 'clearance' = the minimum distance between two copper features",
        "  - 'fanout' = short traces from BGA pads to vias on other layers",
        "  - 'ripup' = removing an existing trace to reroute it",
        "  - 'ratsnest' = the visual air-wire showing unconnected pins",
        "  - 'keepout' = an area where traces/vias are not allowed",
        "  - 'silkscreen' = the white text/outline layer on the PCB",
        "  - 'courtyard' = the minimum keepout boundary around a component",
        "",
        "RULES:",
        "  - Preserve ALL placeholder tokens (%s, %d, {{...}} etc.) exactly as shown",
        "  - Preserve ALL HTML tags (<html>, <b>, <br>, etc.) exactly as shown",
        "  - Preserve \\n escape sequences for multiline strings",
        "  - Keep the same level of formality as the original",
        "  - Do NOT add or remove punctuation that changes meaning",
        "  - Respond with ONLY the translated text, no explanations",
        "",
        f"ENGLISH: \"{english_value}\"",
        f"TRANSLATION ({locale.upper()}):",
    ])

    return "\n".join(prompt_parts)


def call_llm(prompt: str, locale: str) -> Optional[str]:
    """
    Call the configured LLM API with the given prompt.
    Supports OpenAI, Anthropic, and Ollama.
    """
    provider = os.environ.get("LLM_PROVIDER", "openai").lower()
    api_key = os.environ.get("LLM_API_KEY") or os.environ.get("OPENAI_API_KEY", "")

    if provider == "openai":
        model = os.environ.get("LLM_MODEL", "gpt-4o-mini")
        base_url = os.environ.get("LLM_BASE_URL", "https://api.openai.com/v1")
        return _call_openai(prompt, model, api_key, base_url)
    elif provider == "anthropic":
        model = os.environ.get("LLM_MODEL", "claude-3-haiku-20240307")
        return _call_anthropic(prompt, model, api_key)
    elif provider == "ollama":
        model = os.environ.get("LLM_MODEL", "llama3.2")
        base_url = os.environ.get("LLM_BASE_URL", "http://localhost:11434")
        return _call_ollama(prompt, model, base_url)
    else:
        print(f"  ❌ Unknown LLM provider: {provider}", file=sys.stderr)
        return None


def _call_openai(prompt: str, model: str, api_key: str, base_url: str) -> Optional[str]:
    """Call OpenAI-compatible API."""
    try:
        import requests
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.0,
            "max_tokens": 200,
        }
        resp = requests.post(
            f"{base_url.rstrip('/')}/chat/completions",
            headers=headers,
            json=payload,
            timeout=30,
        )
        resp.raise_for_status()
        data = resp.json()
        content = data["choices"][0]["message"]["content"].strip()
        # Strip surrounding quotes if the LLM added them
        content = content.strip("\"'")
        return content
    except ImportError:
        print("  ❌ Missing 'requests' library. Install with: pip install requests", file=sys.stderr)
        return None
    except Exception as e:
        print(f"  ❌ OpenAI API error: {e}", file=sys.stderr)
        return None


def _call_anthropic(prompt: str, model: str, api_key: str) -> Optional[str]:
    """Call Anthropic Claude API."""
    try:
        import requests
        headers = {
            "x-api-key": api_key,
            "anthropic-version": "2023-06-01",
            "Content-Type": "application/json",
        }
        payload = {
            "model": model or "claude-3-haiku-20240307",
            "max_tokens": 200,
            "messages": [{"role": "user", "content": prompt}],
        }
        resp = requests.post(
            "https://api.anthropic.com/v1/messages",
            headers=headers,
            json=payload,
            timeout=30,
        )
        resp.raise_for_status()
        data = resp.json()
        return data["content"][0]["text"].strip().strip("\"'")
    except ImportError:
        print("  ❌ Missing 'requests' library. Install with: pip install requests", file=sys.stderr)
        return None
    except Exception as e:
        print(f"  ❌ Anthropic API error: {e}", file=sys.stderr)
        return None


def _call_ollama(prompt: str, model: str, base_url: str) -> Optional[str]:
    """Call local Ollama API."""
    try:
        import requests
        payload = {
            "model": model or "llama3.2",
            "prompt": prompt,
            "stream": False,
            "options": {"temperature": 0.0},
        }
        resp = requests.post(
            f"{base_url.rstrip('/') or 'http://localhost:11434'}/api/generate",
            json=payload,
            timeout=60,
        )
        resp.raise_for_status()
        data = resp.json()
        return data.get("response", "").strip().strip("\"'")
    except ImportError:
        print("  ❌ Missing 'requests' library. Install with: pip install requests", file=sys.stderr)
        return None
    except Exception as e:
        print(f"  ❌ Ollama API error: {e}", file=sys.stderr)
        return None


def validate_placeholders(english: str, translation: str) -> bool:
    """Verify that all placeholder tokens from the English are preserved in the translation."""
    english_placeholders = set(PLACEHOLDER_RE.findall(english))
    translation_placeholders = set(PLACEHOLDER_RE.findall(translation))
    missing = english_placeholders - translation_placeholders
    if missing:
        print(f"      ⚠️  Missing placeholders in translation: {missing}")
        return False
    return True


def validate_html(english: str, translation: str) -> bool:
    """Verify that HTML tags are preserved in the translation."""
    html_tags = re.findall(r"</?[a-z][a-z0-9]*\b[^>]*>", english)
    for tag in html_tags:
        if tag not in translation:
            print(f"      ⚠️  Missing HTML tag: {tag}")
            return False
    return True


def get_missing_keys(
    context: Dict[str, Dict[str, Any]],
    english_path: Path,
    locale: str,
) -> List[Tuple[str, str, Dict[str, Any]]]:
    """
    Get list of keys that need translation.
    Returns list of (key, english_value, context) tuples for:
    - Keys missing entirely from locale file
    - Keys whose English value has changed (hash mismatch)
    """
    english_props = load_properties(english_path)
    locale_path = locale_properties_path(english_path, locale)
    existing_props = load_properties(locale_path)

    # Build bundle prefix from path
    rel = english_path.relative_to(RESOURCE_ROOT)
    bundle_name = str(rel.with_suffix("")).replace("\\", "/").replace("/", ".")
    bundle_name = bundle_name[:-3]  # remove "_en"

    result: List[Tuple[str, str, Dict[str, Any]]] = []

    for key, english_value in english_props.items():
        qualified_key = f"{bundle_name}.{key}"
        ctx = context.get(qualified_key, {})

        # Skip icon-only keys (e.g., "{{icon:undo}}") — they are not translatable
        if ICON_KEY_RE.match(english_value):
            continue

        existing_translation = existing_props.get(key)
        stored_hash = ctx.get("english_hash", "") if ctx else ""
        current_hash = hashlib.sha256(english_value.encode("utf-8")).hexdigest()

        # Include key if: missing from locale, or English value changed (stale)
        if not existing_translation or stored_hash != current_hash:
            result.append((key, english_value, ctx))

    return result


def translate_bundle(
    context: Dict[str, Dict[str, Any]],
    english_path: Path,
    locale: str,
    dry_run: bool = False,
    missing_only: bool = False,
) -> Tuple[Dict[str, str], int, int, int]:
    """Translate all keys in a single bundle for a given locale."""
    english_props = load_properties(english_path)
    locale_path = locale_properties_path(english_path, locale)
    existing_props = load_properties(locale_path)

    # Build bundle prefix from path
    rel = english_path.relative_to(RESOURCE_ROOT)
    bundle_name = str(rel.with_suffix("")).replace("\\", "/").replace("/", ".")
    bundle_name = bundle_name[:-3]  # remove "_en"

    result: Dict[str, str] = {}
    stale_count = 0
    fresh_count = 0
    unchanged_count = 0

    # Get only missing/stale keys if missing_only mode
    keys_to_translate = get_missing_keys(context, english_path, locale) if missing_only else None
    keys_to_translate_set = {k for k, _, _ in keys_to_translate} if keys_to_translate else set()

    for key, english_value in english_props.items():
        qualified_key = f"{bundle_name}.{key}"
        ctx = context.get(qualified_key)

        # Skip icon-only keys (e.g., "{{icon:undo}}") — they are not translatable
        if ICON_KEY_RE.match(english_value):
            result[key] = english_value
            unchanged_count += 1
            continue

        # If missing_only and this key is already up-to-date, copy existing translation
        if missing_only and key not in keys_to_translate_set:
            existing_translation = existing_props.get(key)
            if existing_translation:
                result[key] = existing_translation
                unchanged_count += 1
                continue

        # Count as stale if existing but needs re-translation
        existing_translation = existing_props.get(key)
        if existing_translation and key in keys_to_translate_set:
            stale_count += 1

        # Need to translate this key
        if dry_run:
            result[key] = f"[{locale}] {english_value}"
            print(f"  [DRY-RUN] Would translate: {key} = \"{english_value}\"")
            fresh_count += 1
            continue

               # Use the context from i18n-context.json or build a default
        key_ctx = ctx if ctx else {
            "bundle": bundle_name,
            "bundle_desc": "UI component",
            "key": key,
            "english_value": english_value,
            "ui_role": "label",
            "grammatical_role": "fragment",
            "has_placeholders": False,
            "placeholders": [],
            "is_html": False,
            "max_length_hint": None,
            "related_keys": [],
        }

        if not ctx:
            print(f"  ⚠️  No context found for {qualified_key}, using raw translation")

        prompt = build_prompt(key, key_ctx, locale)
        print(f"  🔄 Translating: {key}...", end="", flush=True)

        translation = call_llm(prompt, locale)
        if translation is None:
            print(" FAILED")
            # Fall back to English value so we don't break the build
            translation = english_value
        else:
            print(" OK")

        # Validate placeholders and HTML
        if key_ctx.get("has_placeholders"):
            validate_placeholders(english_value, translation)
        if key_ctx.get("is_html"):
            validate_html(english_value, translation)

        result[key] = translation
        fresh_count += 1
        time.sleep(0.1)  # Rate limiting

    return result, stale_count, fresh_count, unchanged_count


def translate_locale(
    context: Dict[str, Dict[str, Any]],
    locale: str,
    dry_run: bool = False,
    missing_only: bool = False,
) -> int:
    """Translate all English bundles for a single locale."""
    print(f"\n🌍 Translating to locale: {locale.upper()}")
    print("=" * 60)

    total_stale = 0
    total_fresh = 0
    total_unchanged = 0
    total_bundles = 0

    # Collect unique English properties file paths from context
    bundle_paths: Set[Path] = set()
    for ctx in context.values():
        bundle_paths.add(english_properties_path(ctx))

    for english_path in sorted(bundle_paths):
        if not english_path.exists():
            continue

        bundle_name = english_path.stem.replace("_en", "")
        print(f"\n📦 Bundle: {bundle_name}")

        result, stale, fresh, unchanged = translate_bundle(
            context, english_path, locale, dry_run=dry_run, missing_only=missing_only
        )

        total_stale += stale
        total_fresh += fresh
        total_unchanged += unchanged
        total_bundles += 1

        if not dry_run:
            # Write the locale properties file
            locale_path = locale_properties_path(english_path, locale)
            write_properties(locale_path, result)
            print(f"  ✅ Wrote {len(result)} keys to {locale_path}")

    print(f"\n📊 Translation Summary for {locale.upper()}:")
    print(f"   Bundles processed: {total_bundles}")
    print(f"   New translations: {total_fresh}")
    print(f"   Already up-to-date (skipped): {total_unchanged}")
    print(f"   Stale (needed re-translation): {total_stale}")

    return total_fresh


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Translate English .properties files using context-augmented LLM prompts"
    )
    parser.add_argument(
        "--locale", "-l",
        type=str,
        help=f"Target locale code (e.g., de, fr). Supported: {', '.join(SUPPORTED_LOCALES)}",
    )
    parser.add_argument(
        "--all", "-a",
        action="store_true",
        help="Translate to all supported locales",
    )
    parser.add_argument(
        "--input", "-i",
        type=Path,
        default=DEFAULT_CONTEXT,
        help=f"Context metadata JSON file (default: {DEFAULT_CONTEXT})",
    )
    parser.add_argument(
        "--dry-run", "-n",
        action="store_true",
        help="Show what would be translated without calling the LLM API",
    )
    parser.add_argument(
        "--missing-only", "-m",
        action="store_true",
        help="Only translate keys missing or stale in locale files (skip already-translated keys)",
    )
    args = parser.parse_args()

    if not args.locale and not args.all:
        parser.error("Specify --locale or --all")

    if not args.input.exists():
        print(f"❌ Context file not found: {args.input}")
        print("   Run extract-context.py first:")
        print("   python scripts/i18n/extract-context.py")
        sys.exit(1)

    context = load_context(args.input)
    print(f"📖 Loaded context for {len(context)} keys from {args.input}")

    if args.missing_only:
        print("🔍 Missing-only mode: only processing keys missing or with changed English values")

    locales = SUPPORTED_LOCALES if args.all else [args.locale]

    for locale in locales:
        translate_locale(context, locale, dry_run=args.dry_run, missing_only=args.missing_only)

    if args.dry_run:
        print("\n⚠️  DRY-RUN completed. No translations were written.")
        print("   Remove --dry-run to actually translate.")


if __name__ == "__main__":
    main()