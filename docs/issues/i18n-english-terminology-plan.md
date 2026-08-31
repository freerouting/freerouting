# English terminology implementation plan

**Status:** In Progress (English files only; foreign files to be regenerated later)  
**Authority:** Grok 4.6 High recommendations, accepted over Composer 2.5 where they differ. The side-by-side comparison is the Cursor canvas **terminology recommendations** from the same discussion; this document is the implementation contract.

### Execution Progress & Task List

- [x] **Task 1: Mechanical key and placeholder renames (Commit 1)**
  - [x] Rename `eagle_*` keys to `fusion_*` in `BoardMenuFile_en.properties`
  - [x] Rename placeholder token `{{unrouted_count}}` to `{{incomplete_count}}` in `ScreenMessages_en.properties`
  - [x] Update `ScreenMessages.java` parameter `unroutedCount` -> `incompleteCount`
- [x] **Task 2: English glossary values alignment (Commit 2)**
  - [x] Update definitions in `scripts/i18n/glossary/_default.json`
  - [x] Update definitions in `scripts/i18n/glossary/en.json`
- [ ] **Task 3: English UI terminology and code copy (Commit 3)**
  - [ ] Phase C1: Autorouter / Auto-routing property updates
  - [ ] Phase C2: Incomplete connections property updates
  - [ ] Phase C3: Fusion and file formats copy updates (`BoardMenuFile_en.properties`, `BoardExportActions.java` string literals)
  - [ ] Phase C4: Component keepout copy updates (`WindowSelectParameter_en`, `WindowVisibility_en`, `ColorTableModel_en`)
  - [ ] Phase C5: Unconnected traces vs route stubs (`CleanupWindows_en`, `BoardMenuInfo_en`, javadoc)
  - [ ] Phase C6: Net-class column tooltips (`WindowNetClasses_en`)
  - [ ] Phase C7: Push-and-shove, rename `ScreenMessages.setPostRouteInfo` -> `setOptimizationInfo`
  - [ ] Phase C8: Sweep all `*_en.properties` for leftover terminology
  - [ ] Phase D: Java comments / javadoc cleanup in touched files
- [ ] **Task 4: Documentation contract & verification (Commit 4)**
  - [ ] Phase E: Add English terminology contract to `docs/translations.md`
  - [ ] Phase F: Refresh context (`extract-context.py`), validate English (`validate.py --locale en -v`), run parity test and code style checks

---

## 1. Purpose

Apply a single, professional English terminology standard across:

1. English UI sources (`src/main/resources/**/*_en.properties`)
2. English glossary sources (`scripts/i18n/glossary/_default.json` and `en.json`)
3. Java identifiers, property keys, and hardcoded English dialog filters that would otherwise contradict the new copy
4. A short terminology contract in `docs/translations.md`

This pass exists so the later full-locale re-translation is driven by correct English, not by leftover EAGLE, `.bin`, `Autorouting`, or `place keepout` wording.

---

## 2. Accepted decisions (Grok 4.6)

| # | Topic | Implement this |
|---|---|---|
| 1 | Autorouter naming | **Autorouter** (engine), **Autoroute** (verb/action), **Auto-routing** (stage). Drop `Autorouting`, `auto-router`, `Auto-router`. Window headers stay Title Case (`Auto-routing Completed`). Mid-sentence, **Autorouter** and **Optimizer** stay capitalized as feature names. |
| 2 | Incomplete vs unrouted | Labels, score, and summary: **incomplete connections**. Status sentences may keep the verb **remain unrouted**. |
| 3 | File formats | Drop **`.bin`**. Open / Save As design: **`.dsn`**. Save (Freerouting snapshot): **`.frb`**. Never list `.dsn` and `.frb` together as generic “supported extensions”. |
| 4 | Fusion vs EAGLE | **Autodesk Fusion** only. Menu/export copy: **Export Autodesk Fusion Script**. Do not say “session script” for `.scr` (session = Specctra `.ses`). Rename `eagle_*` keys to `fusion_*`. |
| 5 | Scope | English properties + English glossary + required Java/key alignment + `docs/translations.md`. Helpset HTML and locale re-translation are follow-ups. |
| 6 | Keepouts | UI: **Component keepout**. Keep glossary key `place keepout` as a required Specctra alias. Do not show “place keepout” in the UI. |
| 7 | Cleanup windows | **Unconnected traces and vias** (not Dangling copper). Keep **Route stubs**. |
| 8 | Net-class table | Accurate shove/pull-tight tooltip. Keep abbreviated headers **Max. Trace Length** / **Min. Trace Length**; put the full phrase in column tooltips 7 and 8. |
| 9 | Glossary accuracy | Update existing `_default.json` / `en.json` definitions now. Do **not** add new glossary keys in this pass (see §4.2). Do not rename `AirLine` or the Clearance Violations window. |
| 10 | Optimizer | **Optimization** / **Optimizer**. No post-route / postroute / post-routing in user-facing English. |
| 11 | Style | American English. **push-and-shove**, **rip-up**, UI **air wire**, Freerouting **trace** (KiCad alias: track), Freerouting **conduction area** (KiCad alias: copper pour). **Freerouting** with capital F. Sentence case for ordinary labels. |

Composer 2.5 is the baseline. The only **disagreement** implemented here is item 7 (Unconnected traces and vias, not Dangling copper). All Composer items marked “nuance” in the canvas follow Grok.

---

## 3. Scope

### In scope

| Area | Paths |
|---|---|
| English properties | `src/main/resources/app/freerouting/**/*_en.properties` |
| English glossary | `scripts/i18n/glossary/_default.json`, `scripts/i18n/glossary/en.json` |
| Terminology contract | `docs/translations.md` (new section; do not rewrite the translator workflow) |
| Java required for keys / copy | Property-key references, placeholder argument names, hardcoded `FileNameExtensionFilter` labels, comments that still say post-route or EAGLE in user-facing constructors |
| Context refresh | `python scripts/i18n/extract-context.py` then `--check` |
| Quality gates | `gradlew.bat spotlessCheck checkstyleMain checkstyleTest checkstyleRewriteRecipes` and `python scripts/i18n/extract-context.py --check` |

### Out of scope (explicit follow-ups)

| Area | Why |
|---|---|
| `src/main/resources/app/freerouting/helpset/**` | Still says `.bin`, Cadsoft-Eagle, postroute. Separate documentation pass. |
| Non-English `*_{locale}.properties` **wording** | Blocked until maintainer approval. |
| Non-English glossary **wording** (`de.json`, `fr.json`, …) | Same gate. Values stay as they are except mechanical key/placeholder renames in §4.1. |
| `src_v19/` | Frozen compatibility tree. |
| Locale LLM re-translation | Step 0 of a later change, not this one. |
| New glossary keys | Deferred to the approved re-translation pass (§8). |
| Renaming `AirLine`, `WindowUnconnectedRoute`, `PLACE_KEEPOUTS` enums | Implementation names; UI strings change, type names can wait. |
| `spotlessApply` | Never as an automatic cleanup. |

### Related English docs (optional, same PR if touched)

`docs/command_line_arguments.md` already says Autodesk Fusion script for `-do`. Only edit it if a CLI string in `Freerouting_en.properties` would otherwise disagree with that page. Do not broaden into README or architecture wording unless a sentence still says EAGLE as a current export target.

---

## 4. Invariants (read before any edit)

### 4.1 Locale files: wording vs mechanical structure

`validate.py` compares **key sets** and **placeholder token names** between English and every locale. It does not require translated wording to match.

Therefore:

| Change type | English | Other locales |
|---|---|---|
| Wording only | Yes | **No** |
| Property **key** rename (`eagle_script` → `fusion_script`) | Yes | **Yes — rename the key, keep the existing translated value** |
| Placeholder **token** rename (`{{unrouted_count}}` → `{{incomplete_count}}`) | Yes | **Yes — rename the token only** |
| New English keys | Avoid in this pass | Would create missing-key failures |

Use a small Python one-off or `scripts/i18n/rename-message-keys.py` (extend `BUNDLE_RENAMES`) for mechanical renames so every locale bundle stays in parity. Do not hand-edit 30+ locale files for wording.

`EnglishPropertiesParityTest` will fail if English keys move and locale keys do not.

### 4.2 Glossary key-set invariant

`scripts/i18n/glossary.py` `validate_glossaries()` requires every `_default.json` key to exist in **every** `{locale}.json`.

This pass may **only change values** of keys that already exist in `_default.json`. Do not add `Autodesk Fusion script`, `route stub`, or `unconnected traces` as new glossary keys until the re-translation pass (where every locale file can receive a real definition together).

Fold Fusion into the existing `DSN` / `SES` / `FRB` definitions. Describe unconnected traces and route stubs in `docs/translations.md` until those keys are added.

### 4.3 Placeholder substitution is positional

`TextManager.getText(key, args…)` replaces `{{…}}` in **occurrence order**, not by name. Renaming `{{unrouted_count}}` is safe at runtime if argument order stays `(score, incompleteCount, violationCount)`.

Still rename the token: the name is documentation for translators, and `validate.py` compares token **sets**.

### 4.4 Formatting and git

- LF line endings; do not change `core.autocrlf`.
- Do not run `spotlessApply`.
- Never stage files automatically.
- Do not commit until asked.

---

## 5. Canonical English (copy this into `docs/translations.md`)

### Feature names

| Role | Canonical | Do not use |
|---|---|---|
| Engine | Autorouter | auto-router, Auto-router |
| Verb / button | Autoroute | Autorouting as a verb |
| Pipeline stage | Auto-routing | Autorouting, Post-routing |
| Optimizer engine | Optimizer | Postroute, Post-router |
| Optimizer stage | Optimization | post-route pass |

### Domain terms

| Role | Canonical | Do not use in UI |
|---|---|---|
| Ratsnest metric | Incomplete connections | Unrouted Connections (as a label) |
| Status verb | remain unrouted | — (allowed in sentences only) |
| Internal save | `.frb` | `.bin` |
| Design import / Save As | Specctra design file (`.dsn`) | `.frb` as an open format |
| Specctra export | Specctra session file (`.ses`) | session script for `.scr` |
| Fusion export | Autodesk Fusion Script (`.scr`) | EAGLE session script |
| Placement keepout | Component keepout | place keepout |
| Orphaned routing | Unconnected traces and vias | Dangling copper, Unconnected Routes |
| Short leftover copper | Route stubs | Dangling copper |
| Copper model | Conduction area | copper pour (except glossary KiCad alias) |
| Routed copper | Trace | track (except glossary KiCad alias) |
| Ratsnest graphic | Air wire | air line (implementation name `AirLine` stays) |

---

## 6. Implementation phases

Work in this order. Each phase should leave the tree compilable.

### Phase A — English glossary values

**Files:** `scripts/i18n/glossary/_default.json`, `scripts/i18n/glossary/en.json`

Update **existing** keys only. `_default.json` is the prompt definition; `en.json` is the English prompt line (usually `term (short definition)`).

| Key | New `_default.json` direction | `en.json` notes |
|---|---|---|
| `autorouter` | Batch automatic routing engine. No `auto-router` alias. | Drop `/ auto-router`. |
| `autoroute` | Verb: run automatic routing; distinct from the Autorouter. | Unchanged role, drop hyphenated engine alias if present. |
| `optimizer` | Optimization-stage engine that shortens and cleans existing traces. **Not** a post-routing pass. | Replace “post-routing pass”. |
| `board outline` | PCB edge boundary. Routed copper stays inside. Keepouts may extend outside when that option is enabled. | Match. |
| `air line` | Implementation name for an incomplete-connection record. User-facing term: air wire. | Keep key `air line`. |
| `incomplete` / `incompletes` | Incomplete connection(s) shown in the ratsnest (air wires). | Align with metric name. |
| `ratsnest` | Temporary air wires showing incomplete connections between connectable items, not pads only. | |
| `net` | Named electrical network of terminals (pins, vias, conduction areas). Not “two or more components”. | |
| `SMD` | Surface-mount device. Board objects are SMD pads/pins. | Not “SMD = pad”. |
| `attach smd` | Allow vias to attach at the center of SMD pads. | Via-specific. |
| `via` | Plated hole connecting copper on two or more layers (pads, traces, or areas). | |
| `plated hole` | Hole with a metallized wall; not automatically a signal via. | |
| `terminal item` | Connectable item that ends a routed group, typically a pin or conduction area — not every via. | |
| `score` | Composite quality metric: incomplete connections, clearance violations, bends, trace length, and via cost. | |
| `Specctra` | Specctra DSN-compatible interchange format, also used by Electra and KiCad export. | Not “also called Electra DSN”. |
| `jump wire` | Physical wire or single-sided routing substitute. Distinct from a Specctra jumper layer. | |
| `courtyard` | Component placement clearance boundary, not a general routing keepout. | |
| `reference designator` | Component identifier (often printed on silkscreen). | |
| `component keepout` | Zone where components must not be placed. UI term. | Drop “UI also calls this place keepout” as if they were co-equal UI labels. |
| `place keepout` | Specctra grammar name for component keepout. Do not use in UI. | Required alias, not optional. |
| `DSN` | Specctra design file exchanged with KiCad, Autodesk Fusion, and similar EDA tools. | Drop EAGLE as a current host. |
| `FRB` | Freerouting internal board snapshot (`.frb`). Not an advertised Open format. | Keep name FRB. |
| `SES` | Specctra session file (`.ses`) for importing routing into the host CAD. | Distinct from Fusion `.scr`. |
| `unrouted` | Adjective: not yet connected by finished copper. Do not use as the ratsnest metric noun. | |
| `push and shove` | Interactive routing that displaces existing traces (shove). Prose: push-and-shove. | |
| `ripup` | Remove trace segments so a net can be rerouted. UI: rip-up. | |
| `trace` | Copper conductor on one layer. KiCad: track. | Do not present track as the Freerouting UI term. |
| `clearance violation` | Design-rule failure: items closer than allowed. The millimetre figure in the list is the **shortfall**, not a rename of the violation. | |
| `shove fixed` | Traces/vias cannot be displaced by push-and-shove and are excluded from pull-tight when this net-class flag is set. | Align with `column_tooltip_5`. |

Do not change other locale JSON files in this phase.

### Phase B — Mechanical key and placeholder renames

Do this **before** editing English sentences that sit on those keys, so diffs stay reviewable.

#### B1. Fusion property keys

Bundle: `src/main/resources/app/freerouting/gui/menus/BoardMenuFile*.properties`

| Old key | New key |
|---|---|
| `eagle_script` | `fusion_script` |
| `eagle_script_tooltip` | `fusion_script_tooltip` |
| `info_eagle_scr_extension` | `info_fusion_scr_extension` |
| `status_creating_eagle_script` | `status_creating_fusion_script` |
| `status_eagle_script_file_label` | `status_fusion_script_file_label` |

Apply to **every** locale file in that bundle. Keep translated values (they will still say Eagle until re-translation; that is expected and must be called out in the PR).

Update Java `tm.getText("…")` if any live references exist. Context currently lists `code_references: []` for these keys (Save As uses chooser filters instead of a dedicated menu item). Still rename the keys so leftover strings and future menu wiring are Fusion-named.

Update `scripts/i18n/rename-message-keys.py` historical map comments only if that file is used as documentation; do not re-run old generic `message_*` migrations.

#### B2. Score placeholder token

| File pattern | Change |
|---|---|
| `ScreenMessages*.properties` `score=` | `{{unrouted_count}}` → `{{incomplete_count}}` (token only) |
| `ScreenMessages.java` `setBoardScore` | Rename parameter `unroutedCount` → `incompleteCount`. Keep argument order. |

Call sites of `setBoardScore` must compile after the parameter rename; no behavior change.

### Phase C — English property strings

Edit `*_en.properties` only (except keys already renamed in Phase B). Proposed values below are the implementation target; match punctuation and American English.

#### C1. Autorouter / Auto-routing

| File | Key | New value |
|---|---|---|
| `gui/windows/board/WindowRoutingSummary_en.properties` | `header_completed` | `Auto-routing Completed` |
| same | `header_interrupted` | `Auto-routing Interrupted` |
| same | `checkbox_show_summary` | `Show this summary after auto-routing finishes` |
| `gui/windows/routing/WindowAutorouteParameter_en.properties` | `autoroute` | `Auto-routing` |
| same | `autoroute_tooltip` | During the Auto-routing stage, the Autorouter establishes electrical connections by placing traces and vias. (Keep technical meaning; capitalize Autorouter.) |
| `gui/board/GuiManager_en.properties` | `auto_start_routing_message` | `The Autorouter is about to start.\n\nDefault settings will be used unless you cancel now and change them.\nYou can start Auto-routing later by clicking the Autoroute button.` |
| `Freerouting_en.properties` | `command_line_help` | Replace “autorouting passes” with “auto-routing passes”. Replace “EAGLE session script (.scr)” with “Autodesk Fusion script (.scr)”. |

Also scan remaining `*_en.properties` for `Autorouting`, `autorouting`, `auto-router`, and `Auto-router` after the table edits and fix any leftover.

#### C2. Incomplete connections

| File | Key | New value |
|---|---|---|
| `gui/windows/board/WindowRoutingSummary_en.properties` | `connections_incomplete` | `Incomplete connections:` |
| `gui/workspace/progress/ScreenMessages_en.properties` | `score` | `Score: {{score}} ({{incomplete_count}} incomplete connections, {{violation_count}} clearance violations)` |
| `gui/workspace/progress/ScreenMessages_en.properties` | `autoroute_end_message` | Keep: `Autoroute {{status}}; {{incomplete_count}} connections remain unrouted.` |
| `gui/interactive/InteractiveState_en.properties` | `autoroute_end_message` | Same sentence as ScreenMessages (they must stay twins). |

Do not change `GuiBoardManager_en.properties` ratsnest lines unless they still say “unrouted connections” as a noun; they already use incomplete connections.

#### C3. Fusion and file formats

| File | Key | New value |
|---|---|---|
| `gui/menus/BoardMenuFile_en.properties` | `fusion_script` | `Export Autodesk Fusion Script` |
| same | `fusion_script_tooltip` | `Write an Autodesk Fusion script (.scr) with the routing changes.` |
| same | `info_fusion_scr_extension` | `Autodesk Fusion expects the script file extension .scr.` |
| same | `info_legal_file_extensions` | `Supported design-file extension is .dsn.` |
| same | `save_tooltip` | `Save the design in Freerouting's internal .frb format.` |
| same | `status_creating_fusion_script` | `Creating Autodesk Fusion script file` |
| same | `status_fusion_script_file_label` | `Autodesk Fusion script file` |
| `gui/board/GuiManager_en.properties` | `open_own_design_tooltip` | Already `.dsn`; keep unless it mentions `.bin`/`.frb`. |

Hardcoded chooser filters in `BoardExportActions.java` (currently English literals):

| Current literal | Move to properties (BoardFrame or BoardMenuFile) | Suggested English |
|---|---|---|
| `SPECCTRA Session file (*.ses)` | e.g. `filter_specctra_session` | `Specctra session file (*.ses)` |
| `Autodesk Fusion Script file (*.scr)` | e.g. `filter_fusion_script` | `Autodesk Fusion script (*.scr)` |
| `SPECCTRA Design file (*.dsn)` | e.g. `filter_specctra_design` | `Specctra design file (*.dsn)` |
| `KiCad Session JSON file (*.json)` | e.g. `filter_kicad_session_json` | `KiCad session JSON file (*.json)` |

**New keys require locale copies.** To stay inside this pass without LLM translation: add the four filter keys to **English** and copy the **English value** into every locale `BoardMenuFile_*.properties` (or BoardFrame bundle, whichever owns the `tm` used in `BoardExportActions`). That is a structural stub, not a translation. Document it in the PR. Prefer attaching the keys to `BoardFrame_en.properties` if that is the `tm` already used for `message_fusion_saved`.

If adding keys to all locales is rejected as too noisy, leave the chooser filters as Java literals but fix capitalization (`Specctra`, Fusion wording) in Java only. **Preferred:** properties + English stubs.

#### C4. Component keepout

| File | Key | New value |
|---|---|---|
| `gui/windows/routing/WindowSelectParameter_en.properties` | `COMPONENT_KEEPOUT` | `Component keepout` |
| same | `COMPONENT_KEEPOUT_tooltip` | `Allow component keepout areas to be selected.` (already correct) |
| same | `F_Courtyard_tooltip` / `B_Courtyard_tooltip` | Front/back courtyard: component placement clearance boundary (not “keepout boundary”). |
| `gui/windows/board/WindowVisibility_en.properties` | `PLACE_KEEPOUTS` | `Component Keepouts` |
| `gui/rendering/ColorTableModel_en.properties` | `PLACE_KEEPOUTS` | `Component Keepouts` |

Do not rename the Java enum `PLACE_KEEPOUTS`.

#### C5. Unconnected traces vs route stubs

| File | Key | New value |
|---|---|---|
| `gui/windows/board/CleanupWindows_en.properties` | `unconnected_route` | `Unconnected traces and vias` |
| same | `no_unconnected_route_found` | Already `No unconnected traces or vias found.` — keep. |
| `gui/menus/BoardMenuInfo_en.properties` | `unconnected_route` | `Unconnected traces and vias` |
| CleanupWindows | `route_stubs` | `Route stubs` (sentence-case title if other window titles are Title Case: **Route Stubs** is current; pick Title Case to match `Clearance Violations` — **keep `Route Stubs`**). |

Java: keep class `WindowUnconnectedRoute` and keys `unconnected_route` / `no_unconnected_route_found`. Update the class javadoc to say traces/vias with no terminal item, not “not connected to their net” if that comment is misleading.

#### C6. Net-class column

| File | Key | New value |
|---|---|---|
| `gui/windows/routing/WindowNetClasses_en.properties` | `column_tooltip_5` | `When enabled, traces and vias in this net class cannot be displaced during push-and-shove routing and are excluded from pull-tight optimization.` |
| same | `MAX_TRACE_LENGTH` | Keep `Max. Trace Length` |
| same | `MIN_TRACE_LENGTH` | Keep `Min. Trace Length` |
| same | `column_tooltip_7` | `Minimum cumulative trace length for this net class. 0 means no minimum.` |
| same | `column_tooltip_8` | `Maximum cumulative trace length for this net class. 0 means no maximum.` |
| same | `column_tooltip_9` | `Whether this net class is ignored by the Autorouter.` |
| same | `SHOVE_FIXED` | Keep header; tooltip carries the precision. |

#### C7. Push-and-shove, post-route keys, Optimizer

| File | Key | New value |
|---|---|---|
| `gui/windows/routing/WindowRouteParameter_en.properties` | `push&shove_enabled` | `Push-and-shove enabled` |
| same | `push&shove_enabled_tooltip` | `Allow interactive routing to displace existing traces (push-and-shove).` |
| `gui/workspace/progress/ScreenMessages_en.properties` | `post_route_add` | Keep user-visible `Vias added: …` (already not “post-route”). **Do not rename the key** in this pass. |
| same | `post_route_layer` | Keep `Trace length: …`. |

Java: `ScreenMessages.setPostRouteInfo` — rename method to `setOptimizationInfo` (or similar) and update call sites + javadoc. Keep property keys `post_route_*` until re-translation if a key rename is not worth the locale churn. If the method is public API used by tests, update tests.

`WindowAutorouteParameter` stage label `optimization` is already `Optimization` — keep.

#### C8. Sweep (after the tables)

Search `*_en.properties` for:

- `.bin`
- `EAGLE`, `Eagle`, `Cadsoft`
- `place keepout`
- `Autorouting` / `autorouting`
- `auto-router` / `Auto-router`
- `Unrouted Connections`
- `post-route`, `postroute`, `Post-routing`
- `session script` (should only remain if it truly means Specctra `.ses`, which it should not)

Fix every hit in English sources. Do not “fix” locale files.

### Phase D — Java comments and leftover auto-router prose

Limited to files already touched or user-visible:

- `ScreenMessages.java` — javadoc “post-route statistics”
- `WindowUnconnectedRoute.java` — class javadoc
- `BoardExportActions.java` — filter labels (Phase C3)
- `StoppableThread.java` / `NamedAlgorithm.java` — “auto-router” in javadoc; change to Autorouter where it describes the engine

Do not drive-by comment cleanup in `src_v19/` or unrelated routing algorithms.

### Phase E — Terminology contract in `docs/translations.md`

Add a section **English terminology (source language)** after the translator rules, containing:

- The canonical tables from §5
- Rule: Autorouter / Autoroute / Auto-routing
- Rule: incomplete connections vs remain unrouted
- Rule: `.dsn` / `.ses` / `.frb` / Fusion `.scr`
- Rule: Component keepout vs Specctra place keepout
- Pointer to this plan for the implementation history

Do not change the “do not edit locale properties” workflow.

### Phase F — Context and validation

```text
python scripts/i18n/extract-context.py
python scripts/i18n/extract-context.py --check
python scripts/i18n/validate.py --locale en -v
python scripts/i18n/validate.py --all
gradlew.bat test --tests app.freerouting.i18n.EnglishPropertiesParityTest
gradlew.bat spotlessCheck checkstyleMain checkstyleTest checkstyleRewriteRecipes
```

On Windows use `gradlew.bat` as above.

Expect `needs_retranslation` flags in `scripts/i18n/context/` for every English value that changed. **Commit those context updates** with the English pass; they are how the later translation run knows what is stale. Do not run `translate.py`.

If `--all` reports placeholder mismatches, Phase B2 was incomplete.

If glossary validation fails, a new `_default.json` key was added — revert the new key (see §4.2).

---

## 7. Suggested PR / commit shape (when asked to commit)

Keep formatting-only or mechanical key-rename diffs reviewable:

1. `chore(i18n): rename eagle_* keys and unrouted_count placeholder` (mechanical, all locales)
2. `fix(i18n): align English glossary with Autorouter terminology`
3. `fix(i18n): apply English UI terminology (Fusion, Auto-routing, keepouts)`
4. `docs: add English terminology contract`

That split is optional; one commit is acceptable if the user prefers a single change. Do not include website files or unrelated WIP.

---

## 8. After maintainer approval (not this pass)

Record these as a checklist for the translation change:

1. Add glossary keys (all locales, real definitions, not English stubs):
   - `Autodesk Fusion script` — `.scr` command script for importing routing into Fusion
   - `unconnected traces` — connected traces/vias with no terminal item
   - `route stub` — short leftover trace or via still attached to a net
   - `jumper layer` — Specctra layer type, distinct from jump wire
2. Re-translate **all** locales from English + updated glossaries (`docs/translations.md` maintainer steps).
3. Replace English stub values that were copied into locale files for new filter keys (Phase C3).
4. Re-translate Fusion menu strings that still say Eagle in locale files after the mechanical key rename.
5. English (and then locale) JavaHelp: `helpset/en/html_files/FileMenu.html`, `WindowAutorouteParameter.html`, `HelpTOC.xml`, `HelpIndex.xml` — `.frb`, Fusion, Auto-routing, Optimizer.
6. Optional type renames: `WindowUnconnectedRoute`, `setPostRouteInfo` leftovers, `PLACE_KEEPOUTS` display-only enum.

**Do not start this list until the maintainer says to run re-translation.**

---

## 9. Acceptance criteria (this pass)

- [ ] No user-facing English string says EAGLE/Eagle as a current export target.
- [ ] No user-facing English string advertises `.bin`.
- [ ] Stage titles use **Auto-routing**; engine uses **Autorouter**; actions use **Autoroute**.
- [ ] Score and summary labels say **incomplete connections**; end-of-run sentence may say **remain unrouted**.
- [ ] Select/visibility UI says **Component keepout(s)**; glossary still has `place keepout` as Specctra alias.
- [ ] Info menu cleanup item is **Unconnected traces and vias**, not Dangling copper.
- [ ] `column_tooltip_5` mentions both shove and pull-tight.
- [ ] `_default.json` and `en.json` match §6 Phase A; no new glossary keys.
- [ ] `docs/translations.md` contains the canonical term table.
- [ ] `extract-context.py --check`, `validate.py --all`, i18n parity test, and Checkstyle/Spotless check pass.
- [ ] Non-English **wording** is unchanged except mechanical key/placeholder/filter-stub structure.
- [ ] Helpset still stale (accepted); listed in §8.

---

## 10. Risk notes

- **Fusion keys with old locale values:** German users will still see “Eagle” until re-translation. Prefer shipping that inconsistency for one cycle over leaving English on EAGLE.
- **Chooser filter stubs:** English text in non-English choosers if Phase C3 copies English into locale files. Better than untranslated `SPECCTRA` literals only if the filters are actually shown; they are.
- **Save As vs Save:** `info_legal_file_extensions` must not mention `.frb` if that string is used as a Save As hint. Confirm any remaining `getText("info_legal_file_extensions")` call sites when implementing; context currently has empty `code_references`.
- **FRB load:** `BoardFrameFileActions` still loads `.frb`. UI must not market it as Open, but Save remains valid. Do not remove load code in this pass.
)