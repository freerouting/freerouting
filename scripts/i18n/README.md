# Contextual Translations — i18n Translation Pipeline

Improves translation quality by supplying **additional context** to LLMs during the translation workflow.

## Problem

When an LLM receives a bare key-value pair like `"file=File"`, it has no idea where this string appears, what UI role it plays, or what constraints it has. This produces inconsistent, grammatically wrong, or contextually inappropriate translations.

## Solution

Three Python scripts that extract per-key context metadata and use it to build context-augmented LLM prompts:

```
scripts/i18n/
├── extract-context.py    # Scans *_en.properties → i18n-context.json
├── translate.py          # Reads context → calls LLM → writes locale files
├── validate.py           # Post-translation integrity checks
├── i18n-context.json     # Generated context metadata (committed to repo)
├── requirements.txt      # Python dependencies
└── README.md             # This file
```

## Workflow

### 1. Extract Context

```bash
python scripts/i18n/extract-context.py
```

This scans all `*_en.properties` files and produces `i18n-context.json` with per-key metadata:

| Context Field | Example |
|---|---|
| `bundle` | `gui.BoardMenuFile` |
| `bundle_desc` | `GUI (graphical user interface)` |
| `ui_role` | `tooltip`, `button_label`, `dialog_title`, `message`, `label` |
| `grammatical_role` | `verb_phrase`, `noun_phrase`, `full_sentence`, `fragment` |
| `has_placeholders` | `true` / `false` |
| `placeholders` | `["%s", "{{version}}"]` |
| `is_html` | `true` / `false` |
| `max_length_hint` | `30` (for buttons), `null` (for tooltips) |
| `related_keys` | `["save", "save_tooltip", "save_message"]` |
| `english_hash` | `sha256:...` (for change detection) |

### 2. Translate

```bash
# Translate to German (requires LLM_API_KEY)
python scripts/i18n/translate.py --locale de

# Dry-run (shows what would be translated without calling the API)
python scripts/i18n/translate.py --locale fr --dry-run

# Translate to all 12 locales
python scripts/i18n/translate.py --all

# Only translate missing or stale keys (efficient for incremental updates)
python scripts/i18n/translate.py --locale de --missing-only
```

The `--missing-only` flag is the recommended workflow for efficiency:
- It skips keys that already have valid translations
- It only processes keys missing from the locale file OR whose English source changed
- This saves significant tokens when only a few keys need updating
- Run `./gradlew test --tests EnglishPropertiesParityTest` first to see what's missing

Each key is sent to the LLM with a context-augmented prompt like:

```
Translate the following UI string from English to DE.

CONTEXT:
  Bundle: gui.BoardMenuFile (GUI (graphical user interface))
  UI Role: tooltip
  Grammatical Role: verb_phrase
  Placeholders: none
  HTML: no
  Related Keys: save, save_message, save_and_exit

RULES:
  - Preserve ALL placeholder tokens (%s, %d, {{...}}) exactly as shown
  - Preserve ALL HTML tags (<html>, <b>, <br>) exactly as shown
  - Respond with ONLY the translated text, no explanations

ENGLISH: "saves the design to disk in the internal .bin file format"
TRANSLATION (DE):
```

### 3. Validate

```bash
# Validate German translations
python scripts/i18n/validate.py --locale de

# Validate all locales
python scripts/i18n/validate.py --all
```

Checks:
- All keys present in `*_en.properties` also exist in `*_{locale}.properties`
- Placeholder tokens (`%s`, `%d`, `{{...}}`) are preserved exactly
- HTML tags are preserved in HTML-formatted keys
- No orphan keys (keys in locale files that don't exist in English)
- English hashes match (flags stale translations when source changed)

## Configuration

Set via environment variables:

| Variable | Default | Description |
|---|---|---|
| `LLM_PROVIDER` | `openai` | `openai`, `anthropic`, or `ollama` |
| `LLM_API_KEY` | `OPENAI_API_KEY` | API key for the LLM provider |
| `LLM_MODEL` | `gpt-4o-mini` | Model name |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | Base URL for API |

## Recommended Usage & Timing

This pipeline is **maintainer tooling**, not part of the Gradle build. Run it when English strings change or when onboarding a new locale — not on every commit.

### When to run each step

| Trigger | Script | Frequency |
|---|---|---|
| English `*_en.properties` edited or new keys added | `extract-context.py` | **Every time** English source changes (before translating) |
| New/changed English strings need locale coverage | `translate.py --missing-only` | After `extract-context.py`, per locale being updated |
| Before merging translation PRs | `validate.py` | After `translate.py`, for each affected locale |
| Full locale bootstrap (new language) | `translate.py --locale xx` (no `--missing-only`) | Once per new locale, then switch to `--missing-only` |
| Quarterly hygiene / release prep | `validate.py --all` | Optional; catches drift across all 12 locales |

### Standard incremental workflow (preferred)

```bash
# 1. Install deps (once)
pip install -r scripts/i18n/requirements.txt

# 2. After editing English strings — refresh context + mark changed keys
python scripts/i18n/extract-context.py
# Commit the updated i18n-context.json together with English + locale changes.

# 3. Translate only what's missing or stale (saves LLM tokens)
export LLM_API_KEY=sk-...
python scripts/i18n/translate.py --locale de --missing-only

# 4. Verify integrity
python scripts/i18n/validate.py --locale de

# 5. Optional: existing parity test
./gradlew test --tests app.freerouting.i18n.EnglishPropertiesParityTest
```

### Incremental translation guarantees

- **`--missing-only` is required** for day-to-day updates. Without it, `translate.py` re-calls the LLM for **every** key (~900+ per locale).
- **`extract-context.py` compares against the previous `i18n-context.json`** and sets `needs_retranslation: true` only for keys whose English text changed.
- **`translate.py --missing-only`** skips keys that already have a locale value **and** are not flagged `needs_retranslation`.
- After a successful translation run, `translate.py` clears `needs_retranslation` in `i18n-context.json` so repeat runs do not re-translate the same keys.
- Icon placeholders (`{{icon:…}}`) are never sent to the LLM.

### What *not* to do

- Do **not** run `translate.py --all` on a schedule — it is expensive and unnecessary unless rebuilding every locale from scratch.
- Do **not** skip `extract-context.py` after English edits — stale detection depends on the committed context snapshot.
- Do **not** run the full pipeline in CI by default (requires API keys and costs tokens). Use `validate.py` in CI if desired.

## Incremental Updates

The pipeline is fully incremental. On subsequent runs:

1. `extract-context.py` diffs against the previous `i18n-context.json` and flags changed keys with `needs_retranslation: true`
2. `translate.py --missing-only` processes only missing keys and flagged stale keys
3. `translate.py` clears `needs_retranslation` after successful translation
4. `validate.py` reports any keys still flagged or missing

This means if a single English string changes, only that key (per locale) is re-translated — not all ~900 keys.

## Supported Locales

`de`, `fr`, `ru`, `bn`, `hi`, `ko`, `ja`, `zh`, `zh_tw`, `ar`, `pt`, `es`