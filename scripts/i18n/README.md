# Contextual Translations — i18n Translation Pipeline

Improves translation quality by supplying **additional context** to LLMs during the translation workflow.

## Layout

```
scripts/i18n/
├── extract-context.py      # Layer 1: scan English + Java → context/
├── translate.py            # Layer 2: LLM translation (batch + incremental)
├── validate.py             # Layer 3: parity / placeholder / HTML checks
├── prune-unused-keys.py    # Remove keys not referenced from Java (all locales)
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
./gradlew test --tests app.freerouting.i18n.EnglishPropertiesParityTest
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

Context JSON is written with **deterministic ordering** (sorted property keys, sorted list fields, stable metadata field order via `sort_keys`) so git diffs show only lines that actually changed.

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

# Translate all locales except ones already finished
python scripts/i18n/translate.py --all --exclude-locale ar

# Incremental: skip keys that already have translations
python scripts/i18n/translate.py --all --missing-only --exclude-locale ar

# After a full (non --missing-only) translation run, sync context flags:
python scripts/i18n/extract-context.py --sync-translated --all
```

**Batch mode:** keys are sent to Gemini in batches (default 15, set `LLM_BATCH_SIZE=1` to force per-key). Failed batch parses fall back to single-key calls with retry/backoff.

**Setup (Gemini 3.7 Flash):**

```bash
export GEMINI_API_KEY=...   # AI Studio auth key (AQ.…) or legacy key (AIza…)
export LLM_MODEL=gemini-3.7-flash   # optional; this is the default
python scripts/i18n/translate.py --locale de --bundle gui.BoardMenuFile
```

PowerShell:

```powershell
$env:GEMINI_API_KEY = "AQ...."
python scripts/i18n/translate.py --locale de --missing-only
```

Google AI Studio now issues **auth keys** with an `AQ.` prefix (replacing legacy `AIza…` keys). Pass them via `GEMINI_API_KEY`; the client sends them in the `x-goog-api-key` header on the native Gemini REST endpoint. **Restart Cursor** (or your terminal) after setting the variable so Python subprocesses inherit it.

**Stale keys:** when `needs_retranslation` is set, the previous locale translation is included in the prompt as an outdated hint.

### 3. Validate

```bash
python scripts/i18n/validate.py --locale de
python scripts/i18n/validate.py --all
python scripts/i18n/validate.py --locale de --bundle gui.BoardMenuFile -v
```

### 4. Prune unused English keys

When Java code no longer references property keys (menu refactors, removed windows, etc.), remove stale entries from **all** locale files and context metadata:

```bash
# 1. Detect unused keys (writes build/reports/i18n/EnglishBundlesContainUnusedKeysReport.*)
./gradlew test --tests app.freerouting.i18n.EnglishPropertiesParityTest.englishBundlesDoNotContainUnusedKeys

# 2. Preview removals
python scripts/i18n/prune-unused-keys.py

# 3. Apply removals + refresh context
python scripts/i18n/prune-unused-keys.py --apply
python scripts/i18n/extract-context.py
./gradlew test --tests app.freerouting.i18n.EnglishPropertiesParityTest
```

The pruner reads `build/reports/i18n/EnglishBundlesContainUnusedKeysReport.json`, deletes listed keys from every `*_{locale}.properties` file, updates `scripts/i18n/context/`, and removes **orphan bundles** that have no Java class in `src/main/java` (for example legacy `WindowSnapshot` resources).

The parity test `englishBundlesDoNotContainUnusedKeys` writes `build/reports/i18n/EnglishBundlesContainUnusedKeysReport.*` and logs a warning when unused keys are found. It does **not** fail CI — review the report manually before pruning (the scanner misses inherited-bundle and dynamic enum keys; blind `--apply` can remove still-used strings).

## Configuration

| Variable | Default | Description |
|---|---|---|
| `GEMINI_API_KEY` | *(required)* | Google AI Studio API key (`AQ.…` or legacy `AIza…`); `GOOGLE_API_KEY` is accepted as an alias |
| `LLM_MODEL` | `gemini-3.7-flash` | Gemini model id for `generateContent` |
| `LLM_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta` | Gemini REST base URL (override only for testing) |
| `LLM_GEMINI_THINKING_LEVEL` | `minimal` | Gemini 3.x only: `minimal`, `low`, `medium`, `high` |
| `LLM_GEMINI_THINKING_BUDGET` | `0` | Gemini 2.5 and earlier: thinking token budget (`0` = off; `-1`/`default` = model default) |
| `LLM_BATCH_SIZE` | `15` | Keys per Gemini request (1–25) |

## Recommended timing

| Trigger | Action |
|---|---|
| English `*_en.properties` changed | `extract-context.py` → commit `context/` |
| Java refactor removed UI strings | parity test → `prune-unused-keys.py --apply` → `extract-context.py` |
| Fill locale gaps | `translate.py --locale xx --missing-only` |
| Before merging translation PR | `validate.py --locale xx` |
| New locale (once) | full `translate.py --locale xx`, then `--missing-only` |
| Release hygiene | `validate.py --all` |
| Glossary term fix (translator) | Edit `glossary/{locale}.json` → request full locale re-run ([`docs/translations.md`](../../docs/translations.md)) |
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
- Hand-edit `*_{locale}.properties` for translation fixes (see [Translator guide](../../docs/translations.md)).

## For translators

Locale bundles under `src/main/resources/**/**_{locale}.properties` are **generated**. If a translation is wrong:

1. Update **`scripts/i18n/glossary/{locale}.json`** (localized term first; keep DSN, SES, Specctra, Freerouting, Andras Fuchs in Latin script).
2. Open a PR or issue with a **re-translation request for the whole locale** — do not patch individual property files.

Full policy and maintainer rerun steps: **[`docs/translations.md`](../../docs/translations.md)**.

## Glossaries

Locale-specific PCB terms live in `scripts/i18n/glossary/`:

| File | Purpose |
|---|---|
| `_default.json` | English definitions shared as fallback (90 Freerouting/Specctra terms) |
| `{locale}.json` | Locale-specific translation guidance (overrides `_default` in LLM prompts) |

One `{locale}.json` exists for every shipped UI locale, plus glossary-only locales prepared for upcoming translations. `validate.py` fails if any file listed in `GLOSSARY_LOCALES` is missing or lacks a term from `_default.json`.

| Locale | File |
|---|---|
| Arabic | `ar.json` |
| Bengali | `bn.json` |
| Catalan | `ca.json` |
| Czech | `cs.json` |
| Danish | `da.json` |
| German | `de.json` |
| Greek | `el.json` |
| English | `en.json` |
| Spanish | `es.json` |
| Finnish | `fi.json` |
| French | `fr.json` |
| Hebrew | `he.json` |
| Hindi | `hi.json` |
| Croatian | `hr.json` |
| Hungarian | `hu.json` |
| Indonesian | `id.json` |
| Italian | `it.json` |
| Japanese | `ja.json` |
| Korean | `ko.json` |
| Lithuanian | `lt.json` |
| Norwegian Bokmål | `nb.json` |
| Dutch | `nl.json` |
| Polish | `pl.json` |
| Portuguese | `pt.json` |
| Portuguese (Brazil) | `pt_br.json` |
| Romanian | `ro.json` |
| Russian | `ru.json` |
| Slovak | `sk.json` |
| Slovenian | `sl.json` |
| Swedish | `sv.json` |
| Thai | `th.json` |
| Turkish | `tr.json` |
| Ukrainian | `uk.json` |
| Vietnamese | `vi.json` |
| Chinese (Simplified) | `zh.json` |
| Chinese (Traditional) | `zh_tw.json` |

Terms were mined from `*_en.properties` and Java UI strings (autorouter, net class, conduction area, push/shove, Specctra DSN/SES, etc.). Extend `_default.json` first, then add the same keys to every `{locale}.json` when introducing new PCB terminology.

**Glossary value style:** each locale entry should lead with the **localized UI term** (the text users should see). English glossary keys are lookup labels only. Keep `DSN`, `SES`, `Specctra`, `Freerouting`, and `Andras Fuchs` in Latin script inside translations when they appear in UI text. Do not embed English UI words such as `clearance` or `shove fixed` in non-English glossary values — that causes the LLM to copy them untranslated.

## Supported locales

**Source:** `en` (English `*_en.properties`)

**Translation targets:** `ar`, `bn`, `cs`, `de`, `es`, `fr`, `hi`, `hu`, `id`, `it`, `ja`, `ko`, `nl`, `pl`, `pt`, `pt_br`, `ro`, `ru`, `sv`, `th`, `tr`, `uk`, `vi`, `zh`, `zh_tw`

See also [docs/developer.md](../../docs/developer.md#translations-i18n).
