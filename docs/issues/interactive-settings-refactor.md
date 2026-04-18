# [ISSUE] Refactor `InteractiveSettings` – Decouple GUI State from Headless/CLI/API Mode

## Summary

`InteractiveSettings` is a GUI-only concept (current active layer, manual trace widths, item selection filter, zoom/drag preferences, etc.) that leaks into `HeadlessBoardManager` and is used unconditionally throughout the `interactive` package. In headless / CLI / API mode the field is never initialised, which causes `NullPointerException` crashes whenever any code path that is shared between the GUI and non-GUI modes touches `interactiveSettings`.

**Immediate crash (reproducible today):**

```
2026-04-18 18:09:02.201 ERROR  Cannot assign field "layer" because "this.interactiveSettings" is null
java.lang.NullPointerException: Cannot assign field "layer" because "this.interactiveSettings" is null
	at app.freerouting.interactive.GuiBoardManager.set_layer(GuiBoardManager.java:970)
	at app.freerouting.interactive.GuiBoardManager.loadFromSpecctraDsn(GuiBoardManager.java:2126)
	at app.freerouting.gui.BoardFrame.load(BoardFrame.java:402)
	at app.freerouting.gui.GuiManager.create_board_frame(GuiManager.java:346)
	at app.freerouting.gui.GuiManager.InitializeGUI(GuiManager.java:303)
	at app.freerouting.Freerouting.main(Freerouting.java:646)
```

## Root Cause

`HeadlessBoardManager.loadFromSpecctraDsn` uses `DsnReader.readBoard`, which bypasses `create_board()`. Because `create_board()` is the only place that `new InteractiveSettings(board)` is called, `interactiveSettings` is `null` when `GuiBoardManager.loadFromSpecctraDsn` then calls `set_layer(0)`.

Additionally, `interactiveSettings` is declared `public` on `HeadlessBoardManager`, meaning it is part of the public API surface of every mode, even headless ones.

## Current State

| Class | Responsibility | `interactiveSettings` usage |
|---|---|---|
| `HeadlessBoardManager` | Headless/CLI/API routing | Declares the field; initialised only via `create_board()` |
| `GuiBoardManager` | GUI routing | Reads/writes the field constantly; also de-serialises it from `.frb` binary files |
| `InteractiveSettings` | Stores GUI-session state | Board-level array size, serialisable, no separation from routing concerns |
| `RouteState`, `DragState`, `StitchRouteState`, … | Interactive routing states | Access `hdlg.interactiveSettings.*` directly (package-visible fields) |

## Goal

1. **Ensure the GUI always has a valid `InteractiveSettings` instance** before any GUI-specific method (e.g. `set_layer`) is called.
2. **Remove `interactiveSettings` from the public API of `HeadlessBoardManager`** – it must not be a concern for headless / CLI / API consumers.
3. **Bind GUI settings two-way to the relevant Swing panels** so that any change in a panel is immediately reflected in `InteractiveSettings` and vice-versa, without ad-hoc synchronisation code scattered across the codebase.
4. **Write unit / integration tests** for each behaviour change.

## Sub-Issues

| # | Title | Link |
|---|---|---|
| 1 | Fix immediate NPE: initialise `interactiveSettings` before `set_layer` is called in `GuiBoardManager` | [SUB-01](#sub-01) |
| 2 | Move `interactiveSettings` from `HeadlessBoardManager` to `GuiBoardManager` | [SUB-02](#sub-02) |
| 3 | Introduce `BoardManager.getInteractiveSettings()` optional accessor | [SUB-03](#sub-03) |
| 4 | Replace direct field access (`hdlg.interactiveSettings.layer`) with accessor methods | [SUB-04](#sub-04) |
| 5 | Two-way binding: GUI panels ↔ `InteractiveSettings` | [SUB-05](#sub-05) |
| 6 | Guard headless code paths against `interactiveSettings` usage | [SUB-06](#sub-06) |
| 7 | Integration tests: GUI load path initialises settings; headless path never requires them | [SUB-07](#sub-07) |

---

## Sub-Issue 01 – Fix immediate NPE in `GuiBoardManager.loadFromSpecctraDsn` {#sub-01}

**File:** `src/main/java/app/freerouting/interactive/GuiBoardManager.java`

### Problem

`GuiBoardManager.loadFromSpecctraDsn` calls `super.loadFromSpecctraDsn(...)` which uses `DsnReader.readBoard` (bypassing `create_board`), then immediately calls `set_layer(0)`. At this point `interactiveSettings` is `null`.

### Proposed Fix

After `super.loadFromSpecctraDsn` succeeds and `this.board` is set, instantiate `interactiveSettings` if it is still `null`:

```java
@Override
public DsnFile.ReadResult loadFromSpecctraDsn(...) {
    var result = super.loadFromSpecctraDsn(...);
    if (this.board != null && this.interactiveSettings == null) {
        this.interactiveSettings = new InteractiveSettings(this.board);
        this.initialize_manual_trace_half_widths();
    }
    if (result != DsnFile.ReadResult.ERROR) {
        this.set_layer(0);
    }
    return result;
}
```

### Unit Test

- `GuiBoardManagerLoadTest.loadDsn_doesNotThrow_whenNoPriorCreateBoard` – load a minimal DSN without calling `create_board` first, assert no NPE, assert layer is 0.
- `GuiBoardManagerLoadTest.loadDsn_setsInteractiveSettingsLayerToZero` – assert `get_settings().get_layer() == 0` after load.

---

## Sub-Issue 02 – Move `interactiveSettings` from `HeadlessBoardManager` to `GuiBoardManager` {#sub-02}

**Files:** `HeadlessBoardManager.java`, `GuiBoardManager.java`, `BoardManager.java`

### Problem

`HeadlessBoardManager` owns and exposes `interactiveSettings`, tightly coupling a GUI concept to headless infrastructure.

### Proposed Fix

1. Remove `public InteractiveSettings interactiveSettings` from `HeadlessBoardManager`.
2. Add `protected InteractiveSettings interactiveSettings` to `GuiBoardManager`.
3. Move all `interactiveSettings`-related code in `HeadlessBoardManager` (`initialize_manual_trace_half_widths`, `get_settings`) into `GuiBoardManager` with proper overrides.
4. In `HeadlessBoardManager.initialize_manual_trace_half_widths`, provide a no-op default implementation (the headless board does not need manual widths).
5. Update `BoardManager.get_settings()` to return `@Nullable InteractiveSettings`, or provide a separate `hasInteractiveSettings()` predicate.

### Unit Test

- `HeadlessBoardManagerTest.getSettings_returnsNull_inHeadlessMode` – confirm `headlessBoardManager.get_settings()` returns `null`.
- `GuiBoardManagerTest.getSettings_returnsNonNull_afterLoad` – confirm non-null after a successful DSN load.

---

## Sub-Issue 03 – Introduce optional `getInteractiveSettings()` accessor on `BoardManager` {#sub-03}

**File:** `src/main/java/app/freerouting/interactive/BoardManager.java`

### Problem

External classes call `boardManager.get_settings()` without knowing whether the manager is in GUI or headless mode, leading to potential NPEs.

### Proposed Fix

Rename or supplement `get_settings()` with:

```java
/** Returns the interactive GUI settings, or {@code null} if running headless. */
@Nullable InteractiveSettings getInteractiveSettings();
```

Add a `boolean isInteractiveModeSupported()` default method that returns `false`, overridden to `true` in `GuiBoardManager`.

### Unit Test

- `BoardManagerContractTest` – verify that any `BoardManager` implementation either returns non-null from `getInteractiveSettings` and `isInteractiveModeSupported() == true`, or returns null and `isInteractiveModeSupported() == false`.

---

## Sub-Issue 04 – Replace direct field access with accessor methods {#sub-04}

**Files:** `RouteState.java`, `DragState.java`, `StitchRouteState.java`, `TileConstructionState.java`, `InspectedItemState.java`, `GuiBoardManager.java`

### Problem

Fields like `interactiveSettings.layer`, `interactiveSettings.push_enabled`, etc. are accessed directly across the package, bypassing encapsulation and making it impossible to guard against `null` in one place.

### Proposed Fix

1. Make all fields in `InteractiveSettings` `private` (they are currently package-private or package-visible).
2. Route all access through existing getters/setters (most already exist).
3. In calling code, access settings via `boardHandling.getInteractiveSettings()` with a null guard or by using a dedicated delegate on `GuiBoardManager` (e.g. `getCurrentLayer()`, `isPushEnabled()`).

### Unit Test

- Compile-time test: verify no direct field access outside `InteractiveSettings` (enforced via Checkstyle rule or ArchUnit test).
- `InteractiveSettingsAccessTest` – verify that all setters respect `read_only` flag.

---

## Sub-Issue 05 – Two-way binding: GUI panels ↔ `InteractiveSettings` {#sub-05}

**Files:** Panel classes in `app.freerouting.gui` (e.g. `SelectParameterWindow`, `RouteParameterWindow`, layer selector), `GuiBoardManager.java`, `InteractiveSettings.java`

### Problem

There is no reliable two-way synchronisation between the GUI controls and `InteractiveSettings`. Changes to settings are pushed into the object imperatively, but there is no mechanism to refresh the GUI from a freshly loaded/de-serialised settings object.

### Proposed Fix

Adopt a simple **observer / property-change** pattern:

1. Add `java.beans.PropertyChangeSupport` to `InteractiveSettings`.
2. Each setter fires a named property-change event.
3. GUI panels register as `PropertyChangeListener` on the settings object during `GuiBoardManager` initialisation.
4. On DSN load or binary load, call `refreshGuiFromSettings()` on `GuiBoardManager` which pushes all current values to the panels.
5. Panel actions (button clicks, combo-box selections) call the setter on `interactiveSettings`; the property-change event propagates back to any other listeners if needed.

### Unit Test

- `InteractiveSettingsPropertyChangeTest` – verify that setting `layer = 3` fires `PropertyChangeEvent` with old/new values.
- `GuiBoardManagerBindingTest` (mock panels) – verify that loading a DSN calls `refreshGuiFromSettings` and that the mock panel receives the correct layer value.

---

## Sub-Issue 06 – Guard headless / API code paths against `interactiveSettings` usage {#sub-06}

**Files:** Any class in `app.freerouting.api`, `app.freerouting.autoroute`, or `app.freerouting.core` that currently touches `interactiveSettings` directly.

### Problem

Some autorouting or API code paths may transitively reach `interactiveSettings` through `BoardManager`. These paths must work correctly when `interactiveSettings` is `null`.

### Proposed Fix

1. Search for all references to `interactiveSettings` outside the `gui` and `interactive` packages.
2. For each reference, either: (a) pass the required value as an explicit parameter, or (b) provide a sensible default value when `interactiveSettings` is `null`.
3. Add `@SuppressWarnings` / Checkstyle suppression documentation explaining why each remaining reference is intentional.

### Unit Test

- `HeadlessRoutingTest` – run a full autoroute pass on a simple DSN in headless mode and assert no NPE or `IllegalStateException` is thrown.
- `ApiRoutingTest` – same via the REST API layer.

---

## Sub-Issue 07 – Integration tests: GUI load path and headless path {#sub-07}

**Files:** `src/test/java/app/freerouting/tests/`

### Problem

There are no automated tests that cover the GUI startup path (without a display) or that verify the headless path is fully independent of `InteractiveSettings`.

### Proposed Fix

1. Add `GuiStartupHeadlessTest` using a mocked/minimal `GraphicsEnvironment` (headless AWT) to run `GuiBoardManager.loadFromSpecctraDsn` and assert:
   - `interactiveSettings` is non-null after load.
   - `interactiveSettings.get_layer() == 0`.
   - `initialize_manual_trace_half_widths()` sets sensible values derived from the board rules.
2. Add `HeadlessCompleteRoutingTest` to verify a routing job completes without touching `interactiveSettings`.
3. Ensure both tests are included in `./gradlew check`.

---

## Acceptance Criteria (Parent Issue)

- [ ] No `NullPointerException` when starting the GUI without CLI arguments.
- [ ] `interactiveSettings` is `null` / inaccessible in headless mode.
- [ ] All GUI panel values are correctly initialised from `InteractiveSettings` after DSN load or binary load.
- [ ] Changes in any GUI panel are immediately reflected in `InteractiveSettings` without additional synchronisation calls.
- [ ] All existing routing tests (`./gradlew test`) pass without regression.
- [ ] New unit tests added for each sub-issue pass (`./gradlew check`).
- [ ] No Checkstyle violations introduced.

