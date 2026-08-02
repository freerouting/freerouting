# Contextual Translations — i18n Translation Pipeline

Improves translation quality by supplying **additional context** to LLMs during the translation workflow.

## Layout

```
scripts/i18n/
├── extract-context.py      # Layer 1: scan English + Java → context/
├── translate.py            # Layer 2: LLM translation (batch + incremental)
├── validate.py             # Layer 3: parity / placeholder / HTML checks
├── context/                # Per-bundle metadata (committed; no english_value stored)
├── glossary/               # Locale-specific PCB terminology (_default.json, de.json, …)
├── properties_io.py        # Shared .properties I/O
├── context_store.py        # Split context load/save/check
├── java_scanner.py         # TextManager getText() scanning
├── llm_client.py           # Retry/backoff + batch API calls
├── prompt_builder.py       # Context-augmented prompts
├── i18n_output.py          # Windows-safe console output
├── requirements.txt
└── README.md
```

English source strings always live in `src/main/resources/**/**_en.properties`. Context JSON stores metadata and change flags only.

## Quick start

```bash
pip install -r scripts/i18n/requirements.txt

# After editing English strings
python scripts/i18n/extract-context.py

# Incremental translation (recommended)
export LLM_API_KEY=sk-...
python scripts/i18n/translate.py --locale de --missing-only

# Verify
python scripts/i18n/validate.py --locale de
```

## Workflow

### 1. Extract context

```bash
python scripts/i18n/extract-context.py
python scripts/i18n/extract-context.py --check   # CI: fail if context is stale
```

Produces one JSON file per bundle under `scripts/i18n/context/`:

| Field | Purpose |
|---|---|
| `english_hash` | SHA-256 of English value (change detection) |
| `needs_retranslation` | `true` when English changed since last commit |
| `ui_role` | tooltip, button_label, dialog_title, … |
| `grammatical_role` | verb_phrase, noun_phrase, full_sentence, fragment |
| `placeholders` / `has_placeholders` | `%s`, `{{name}}`, etc. |
| `is_html` | HTML preservation required |
| `related_keys` | Same-prefix keys for consistency |
| `code_references` | Java classes referencing this key (from source scan) |

Also scans Java sources for `TextManager.getText("key")` usages.

### 2. Translate

```bash
# Recommended: only missing + stale keys
python scripts/i18n/translate.py --locale de --missing-only

# Preview without API calls
python scripts/i18n/translate.py --locale de --missing-only --dry-run

# Single bundle while developing
python scripts/i18n/translate.py --locale de --bundle gui.BoardMenuFile --missing-only

# Full locale bootstrap (once per new language — expensive)
python scripts/i18n/translate.py --locale pt
```

**Batch mode:** keys are sent to the LLM in batches (default 15, set `LLM_BATCH_SIZE=1` to force per-key). Failed batch parses fall back to single-key calls with retry/backoff.

**Stale keys:** when `needs_retranslation` is set, the previous locale translation is included in the prompt as an outdated hint.

### 3. Validate

```bash
python scripts/i18n/validate.py --locale de
python scripts/i18n/validate.py --all
python scripts/i18n/validate.py --locale de --bundle gui.BoardMenuFile -v
```

## Configuration

| Variable | Default | Description |
|---|---|---|
| `LLM_PROVIDER` | `openai` | `openai`, `anthropic`, or `ollama` |
| `LLM_API_KEY` | `OPENAI_API_KEY` | API key |
| `LLM_MODEL` | provider-specific | `gpt-4o-mini` / `claude-3-haiku-20240307` / `llama3.2` |
| `LLM_BASE_URL` | provider-specific | OpenAI or Ollama base URL |
| `LLM_BATCH_SIZE` | `15` | Keys per LLM request (1–25) |

## Recommended timing

| Trigger | Action |
|---|---|
| English `*_en.properties` changed | `extract-context.py` → commit `context/` |
| Fill locale gaps | `translate.py --locale xx --missing-only` |
| Before merging translation PR | `validate.py --locale xx` |
| New locale (once) | full `translate.py --locale xx`, then `--missing-only` |
| Release hygiene | `validate.py --all` |
| Every PR (CI) | `extract-context.py --check` (no LLM, no API key) |

### Incremental guarantees

- **`--missing-only`** skips keys with an existing locale value that are not flagged `needs_retranslation`.
- **`extract-context.py`** sets `needs_retranslation` by diffing against the committed `context/` snapshot.
- **`translate.py`** clears the flag after successful translation and exits **non-zero** on LLM/validation failures.
- Icon keys (`{{icon:…}}`) are never translated.

### Do not

- Run `translate.py --all --missing-only` on a schedule without English changes.
- Skip `extract-context.py` after English edits.
- Run LLM translation in CI (use `--check` and `validate.py` only).

## Glossaries

Locale-specific PCB terms live in `scripts/i18n/glossary/{locale}.json`, merged over `_default.json`. Add a file when bootstrapping a new locale for better technical consistency.

## Supported locales

`de`, `fr`, `ru`, `bn`, `hi`, `ko`, `ja`, `zh`, `zh_tw`, `ar`, `pt`, `es`

See also [docs/developer.md](../../docs/developer.md#translations-i18n).
