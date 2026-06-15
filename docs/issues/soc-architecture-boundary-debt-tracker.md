# Architecture Boundary Debt Tracker

> Last updated: 2026-06-15
>
> Scope: SoC and module-boundary debt identified while implementing Command Pattern groundwork for `InteractiveState`.

---

## Purpose

This tracker records architectural boundary constraints. All boundary rules have been promoted to strict checks (0 frozen rules remaining).

- Enforcement tests: `src/test/java/app/freerouting/architecture/ModuleBoundariesArchTest.java`

Use this file to trace historical boundary debt remediation.

---

## Boundary Catalog

### Strict boundaries (must pass now)

1. `rules`/`drc`/`geometry`/`datastructures` do not depend on `gui`/`interactive`/`api`.
2. `api` and `management` do not depend on `interactive.GuiBoardManager` or `interactive.InteractiveState`.
3. `io.specctra` no dependency on `interactive`/`gui`/`management` (covered by `SpecctraPackageArchTest`).
4. `core`/`board`/`autoroute` should not depend on `gui` or `interactive`.
5. `api`/`management` should not depend on `gui` or `boardgraphics`.
6. `core` should not directly depend on `GuiBoardManager`/`InteractiveState`.
7. `interactive` state-machine types should not leak outside `gui` + `interactive`.
8. `io.specctra.parser` internals should stay behind `io.specctra` public entry points.

### Frozen boundaries (current debt; no further drift)

*(None - all rules are strictly enforced)*

---

## Current Debt Inventory

### D1 - `core` + routing pipeline coupled to GUI/editor types

- **Rule:** `coreBoardAutorouteMustNotDependOnGuiOrInteractive`
- **Representative violations:**
  - `app.freerouting.autoroute.BatchAutorouter` -> `app.freerouting.interactive.RatsNest`
  - `app.freerouting.core.BoardFileDetails` -> `app.freerouting.gui.FileFormat`
  - `app.freerouting.core.scoring.BoardStatistics` -> `app.freerouting.gui.FileFormat`
- **Impact:** Headless/service paths still carry GUI/editor coupling.
- **Remediation plan:**
  1. Move `FileFormat` to a headless-neutral package (e.g. `core` or `io`).
  2. Extract unrouted-net counting/reporting from `interactive.RatsNest` into an autoroute/headless service.
  3. Replace direct references in `autoroute`/`core` with the extracted service.
- **Status:** FIXED

### D2 - API/management depend on GUI enum

- **Rule:** `apiAndManagementShouldNotDependOnGuiEnumsOrTypes`
- **Representative violations:**
  - `app.freerouting.management.RoutingJobScheduler` -> `app.freerouting.gui.FileFormat.DSN`
  - `app.freerouting.api.v1.JobControllerV1` -> `app.freerouting.gui.FileFormat.DSN`
  - `app.freerouting.management.RoutingJobSchedulerActionThread` -> `app.freerouting.gui.FileFormat.SES`
- **Impact:** REST/service layer depends on GUI namespace.
- **Remediation plan:**
  1. Relocate `FileFormat` out of `gui`.
  2. Update API and scheduler code to import the relocated enum.
  3. Keep GUI wrappers only in `gui` package.
- **Status:** FIXED

### D3 - Core `RoutingJob` uses `GuiBoardManager` directly

- **Rule:** `coreShouldNotUseGuiBoardManagerDirectly`
- **Representative violations:**
  - `app.freerouting.core.RoutingJob.read_rules_file(..., GuiBoardManager, ...)`
- **Impact:** Core code is not fully headless-safe at the type boundary.
- **Remediation plan:**
  1. Replace `GuiBoardManager` parameter with `BoardManager` or `RoutingBoard`.
  2. Push GUI-only logic to `GuiBoardManager` call sites.
  3. Keep rule parsing in headless-safe classes.
- **Status:** FIXED

### D4 - Interactive package leakage

- **Rule:** `guiStateMachineShouldOnlyBeUsedFromGuiAndInteractiveLayers`
- **Representative violations:**
  - API/management still instantiate `HeadlessBoardManager` from `interactive` package.
  - Autoroute uses `interactive.RatsNest`.
- **Impact:** Editor-state package is a mixed GUI/headless surface.
- **Remediation plan:**
  1. Split headless-safe classes out of `interactive` into `core`/`board`/`management` appropriate packages.
  2. Keep `interactive` focused on GUI-session state machine.
  3. Update imports and ArchUnit rules after split.
- **Status:** FIXED

### D5 - Parser internals leaked outside `io.specctra`

- **Rule:** `specctraParserInternalsShouldNotLeakOutsideIoSpecctra`
- **Representative violations:**
  - `app.freerouting.board.Communication` uses `io.specctra.parser.CoordinateTransform`
  - `app.freerouting.drc.DesignRulesChecker` uses parser coordinate conversion
  - GUI/interactive code references `io.specctra.parser.DsnFile.ReadResult`
- **Impact:** Parser internals are used as public contracts.
- **Remediation plan:**
  1. Promote required public types to `io.specctra` API package.
  2. Add adapters in `io.specctra` that hide parser classes.
  3. Migrate external callers to public wrappers.
- **Status:** FIXED

---

## Operating Rules

1. When fixing a debt item, keep the ArchUnit rule frozen until all known violations for that rule are removed.
2. Once a frozen rule reaches zero violations, remove freezing for that rule and make it strict.
3. Update this document and `docs/architecture.md` in the same PR as any rule-status change.
4. For each debt item, include at least one focused regression test in the owning package.

---

## Quick Validation Commands

```powershell
Set-Location "C:\Work\freerouting"
.\gradlew.bat test --tests "app.freerouting.architecture.ModuleBoundariesArchTest" --tests "app.freerouting.io.SpecctraPackageArchTest"
```