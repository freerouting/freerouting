# Translation guide

Freerouting UI strings are generated from English sources using the LLM pipeline in [`scripts/i18n/`](../scripts/i18n/README.md). **Locale `.properties` files are build output**, not the primary place to fix wording.

## For translators and reviewers

### Do not edit locale `.properties` files directly

Avoid hand-editing files such as `Common_de.properties`, `gui/BoardMenuFile_ar.properties`, and other `*_{locale}.properties` under `src/main/resources/app/freerouting/`.

Direct edits are discouraged because they:

- Are overwritten on the next pipeline run for that locale
- Fix one string but leave the same PCB term wrong elsewhere
- Bypass glossary consistency checks
- Make it hard for maintainers to know what still needs regeneration

**Exception:** English source strings live in `*_en.properties`. Those are maintained by developers (copy review, new keys, placeholder fixes). Translators normally do not edit English unless they are also improving source copy.

### How to fix an improper translation

1. **Identify the PCB or UI concept** (e.g. clearance, shove fixed, net class), not just one menu label.
2. **Edit the locale glossary only:** `scripts/i18n/glossary/{locale}.json`
   - Lead with the **localized term** users should see (not English embedded in the value).
   - Keep these in Latin script when they appear in UI text: **DSN**, **SES**, **Specctra**, **Freerouting**, **Andras Fuchs**.
   - If a term is missing, add the same key to `_default.json` first, then to every `{locale}.json` (see [`scripts/i18n/README.md`](../scripts/i18n/README.md#glossaries)).
3. **Open a pull request or issue** that includes:
   - The glossary change (required)
   - A **re-translation request** for the **entire locale** (all `*_{locale}.properties` files for that language)
   - Example issue title: `i18n: re-translate de after glossary fix (clearance terms)`
   - Brief note of what was wrong and what you changed in the glossary

Do **not** submit PRs that only patch individual `*_{locale}.properties` lines unless a maintainer explicitly asked for a hotfix.

### Why full-locale re-translation?

Glossary entries drive **every** string that mentions a term. Fixing `clearance` in one dialog but not in toolbars, Common keys, and error messages leaves a mixed UI. Regenerating the whole locale applies the glossary consistently.

---

## English terminology (source language)

English UI copy and glossaries adhere to a single canonical terminology standard. Translators and contributors modifying English strings must follow these conventions. For complete implementation details and history, see [`docs/issues/i18n-english-terminology-plan.md`](issues/i18n-english-terminology-plan.md).

### Feature names

| Role | Canonical | Do not use |
|---|---|---|
| Engine | **Autorouter** | auto-router, Auto-router |
| Verb / button | **Autoroute** | Autorouting as a verb |
| Pipeline stage | **Auto-routing** | Autorouting, Post-routing |
| Optimizer engine | **Optimizer** | Postroute, Post-router |
| Optimizer stage | **Optimization** | post-route pass |

### Domain terms

| Role | Canonical | Do not use in UI |
|---|---|---|
| Ratsnest metric | **Incomplete connections** | Unrouted Connections (as a label) |
| Status verb | **remain unrouted** | — (allowed in sentences only) |
| Internal save | **.frb** | .bin |
| Design import / Save As | **Specctra design file (.dsn)** | .frb as an open format |
| Specctra export | **Specctra session file (.ses)** | session script for .scr |
| Fusion export | **Autodesk Fusion Script (.scr)** | EAGLE session script |
| Placement keepout | **Component keepout** | place keepout |
| Orphaned routing | **Unconnected traces and vias** | Dangling copper, Unconnected Routes |
| Short leftover copper | **Route stubs** | Dangling copper |
| Copper model | **Conduction area** | copper pour (except glossary KiCad alias) |
| Routed copper | **Trace** | track (except glossary KiCad alias) |
| Ratsnest graphic | **Air wire** | air line (implementation name `AirLine` stays) |

### Core terminology rules

- **Autorouter vs Auto-routing vs Autoroute:** Use **Autorouter** for the batch engine, **Autoroute** for the action/verb, and **Auto-routing** for the pipeline stage. Window headers use Title Case (`Auto-routing Completed`). Mid-sentence, **Autorouter** and **Optimizer** remain capitalized as proper feature names.
- **Incomplete connections vs remain unrouted:** Use **incomplete connections** for labels, metrics, and score summaries. The verb form **remain unrouted** is permitted only within descriptive sentences (e.g. `Autoroute completed; X connections remain unrouted.`).
- **File formats:** Freerouting internal board snapshots use **.frb** (never `.bin`). Specctra interchange designs use **.dsn**, session files use **.ses**, and Autodesk Fusion scripts use **.scr**. Never refer to `.scr` as an EAGLE script or session script.
- **Keepouts:** User-facing UI elements must use **Component keepout** (never `place keepout`). The term `place keepout` is reserved as a Specctra grammar alias in glossaries and file parsers.
- **Style and spelling:** American English is standard. The product name is always capitalized as **Freerouting**. Use **push-and-shove**, **rip-up**, and sentence case for ordinary UI labels.

---

## For maintainers (running a re-translation)

When a contributor updates `scripts/i18n/glossary/{locale}.json` and requests a locale refresh:

```powershell
cd c:\Work\freerouting
pip install -r scripts/i18n/requirements.txt

$env:GEMINI_API_KEY = "AQ...."  # restart Cursor after setting if translate fails
$env:LLM_MODEL = "gemini-3.7-flash"   # optional; default
$locale = "de"                  # target locale

# Full overwrite for that locale
Get-ChildItem -Path "src/main/resources/app/freerouting" -Recurse -Filter "*_$locale.properties" | Remove-Item
python scripts/i18n/extract-context.py
python scripts/i18n/translate.py --locale $locale
python scripts/i18n/extract-context.py --sync-translated --locale $locale
python scripts/i18n/validate.py --locale $locale -v
./gradlew test --tests app.freerouting.i18n.*
```

Commit the regenerated `*_{locale}.properties`, updated glossary, and `scripts/i18n/context/` if it changed.

To refresh **without** deleting files (only if keys are flagged stale):
`python scripts/i18n/translate.py --locale $locale --missing-only`
Glossary-only changes do **not** auto-flag keys; prefer full locale regeneration above.

---

## Related documentation

| Document | Audience |
|---|---|
| [`scripts/i18n/README.md`](../scripts/i18n/README.md) | Pipeline setup, LLM config, validation |
| [`docs/developer.md`](developer.md#translations-i18n) | Developer workflow after English edits |
| [`docs/issues/`](issues/) | Per-issue i18n notes when relevant |

## Supported locales

**Source:** `en`
**Targets:** `ar`, `bn`, `cs`, `de`, `es`, `fr`, `hi`, `hu`, `id`, `it`, `ja`, `ko`, `nl`, `pl`, `pt`, `pt_br`, `ro`, `ru`, `sv`, `th`, `tr`, `uk`, `vi`, `zh`, `zh_tw`
