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
```

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

## Incremental Updates

The pipeline is fully incremental. On subsequent runs:

1. `extract-context.py` recomputes SHA-256 hashes of all English values
2. `translate.py` compares hashes — only keys whose English value changed are re-translated
3. `validate.py` flags any keys whose hash doesn't match the stored value

This means if a single English string like `message_18` changes from `"Pins not found"` to `"Pads not found"`, only that one key gets re-translated, not all 2000.

## Supported Locales

`de`, `fr`, `ru`, `bn`, `hi`, `ko`, `ja`, `zh`, `zh_tw`, `ar`, `pt`, `es`