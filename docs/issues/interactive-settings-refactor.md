# [ISSUE] Refactor `InteractiveSettings` – Decouple GUI State from Headless/CLI/API Mode

## Summary

`InteractiveSettings` is a GUI-only concept (current active layer, manual trace widths, item selection filter, zoom/drag preferences, etc.) that leaks into `HeadlessBoardManager` and is used unconditionally throughout the `interactive` package. In headless / CLI / API mode the field is never initialised, which causes `NullPointerException` crashes whenever any code path shared between GUI and non-GUI modes touches `interactiveSettings`.

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

## Architecture Context

`InteractiveSettings` **extends** `GuiSettings`, which **implements** `SettingsSource` (priority 50) and feeds `RouterSettings` into the `SettingsMerger` pipeline. This is a critical architectural relationship that must be preserved and made explicit throughout the codebase:

```
SettingsMerger
  └── priority  0 : DefaultSettings
  └── priority 10 : JsonFileSettings
  └── priority 20 : DsnFileSettings
  └── priority 30 : SesFileSettings
  └── priority 40 : RulesFileSettings
  └── priority 50 : GuiSettings  ◄──── InteractiveSettings IS the concrete GuiSettings in GUI mode
  └── priority 55 : EnvironmentVariablesSource
  └── priority 60 : CliSettings
  └── priority 70 : ApiSettings
```

Because `InteractiveSettings` IS the `GuiSettings` source in the merger, every field the user changes in the GUI **must** flow through `InteractiveSettings` → `GuiSettings.getSettings()` → `SettingsMerger` → final `RouterSettings`. This means:

- `InteractiveSettings.getSettings()` must override `GuiSettings.getSettings()` to return a **live snapshot** built from current field values.
- The singleton instance must be **the** registered `GuiSettings` source in the merger at application startup and never replaced by a plain `GuiSettings` instance.

Since there is only ever one active GUI instance, **`InteractiveSettings` should be a singleton within the GUI session**. This eliminates stale-reference bugs and ensures the merger always reads from the authoritative source.

## Root Cause of the Crash

`HeadlessBoardManager.loadFromSpecctraDsn` uses `DsnReader.readBoard`, which bypasses `create_board()`. Because `create_board()` is the only place that `new InteractiveSettings(board)` is called, `interactiveSettings` is `null` when `GuiBoardManager.loadFromSpecctraDsn` then calls `set_layer(0)`.

Additionally, `interactiveSettings` is declared `public` on `HeadlessBoardManager`, meaning it is part of the public API surface of every mode, even headless ones.

## Current State

| Class | Responsibility | `interactiveSettings` usage |
|---|---|---|
| `HeadlessBoardManager` | Headless/CLI/API routing | Declares the field; initialised only via `create_board()` |
| `GuiBoardManager` | GUI routing | Reads/writes the field constantly; also de-serialises it from `.frb` binary files |
| `InteractiveSettings` | GUI-session state **and** the live `GuiSettings` source for `SettingsMerger` | Board-level array size, serialisable; `getSettings()` not yet overridden to reflect live state |
| `GuiSettings` | `SettingsSource` at priority 50, supplies `RouterSettings` to `SettingsMerger` | Base class of `InteractiveSettings`; currently holds a static snapshot, not live GUI state |
| `RouteState`, `DragState`, `StitchRouteState`, … | Interactive routing states | Access `hdlg.interactiveSettings.*` directly (package-visible fields) |

## Goals

1. **Fix the crash** – ensure the GUI always has a valid `InteractiveSettings` instance before `set_layer` or any other GUI method is called.
2. **Make `InteractiveSettings` a singleton** – exactly one instance exists for the lifetime of the GUI session; all Swing panels and the `SettingsMerger` share this instance.
3. **Remove `interactiveSettings` from `HeadlessBoardManager`** – it must not be a concern for headless / CLI / API consumers.
4. **Two-way binding** – every field in `InteractiveSettings` (including those inherited from `GuiSettings`/`RouterSettings`) is bound bidirectionally to the corresponding Swing control, so changes in the GUI immediately update `InteractiveSettings` (and therefore the `SettingsMerger` pipeline) and vice-versa.
5. **Keep `GuiSettings`/`SettingsMerger` integration intact** – the singleton instance is registered as the `GuiSettings` source in the merger at application startup and never replaced.
6. **Update all JavaDoc** – classes and methods must accurately describe the `InteractiveSettings → GuiSettings → SettingsMerger` chain.
7. **Write unit / integration tests** for each behaviour change.

## Sub-Issues

| # | Title |
|---|---|
| 1 | Fix immediate NPE: initialise `interactiveSettings` before `set_layer` is called in `GuiBoardManager` |
| 2 | Make `InteractiveSettings` a singleton and move it out of `HeadlessBoardManager` |
| 3 | Introduce `BoardManager.getInteractiveSettings()` optional accessor and update `BoardManager` JavaDoc |
| 4 | Replace direct field access with accessor methods; update `InteractiveSettings` JavaDoc |
| 5 | Two-way binding: all GUI panels ↔ `InteractiveSettings` fields (including inherited `GuiSettings` fields) |
| 6 | Register the singleton as the live `GuiSettings` source in `SettingsMerger`; update `GuiSettings` JavaDoc |
| 7 | Guard headless / API code paths against `interactiveSettings` usage |
| 8 | Integration tests: GUI load path initialises settings; headless path never requires them |

---

## Sub-Issue 01 – Fix immediate NPE in `GuiBoardManager.loadFromSpecctraDsn` {#sub-01}

**File:** `src/main/java/app/freerouting/interactive/GuiBoardManager.java`

### Problem

`GuiBoardManager.loadFromSpecctraDsn` calls `super.loadFromSpecctraDsn(...)` which uses `DsnReader.readBoard` (bypassing `create_board`), then immediately calls `set_layer(0)`. At this point `interactiveSettings` is `null`.

### Proposed Fix

After `super.loadFromSpecctraDsn` succeeds and `this.board` is set, instantiate `interactiveSettings` if it is still `null`:

```java
@Override
public DsnFile.ReadResult loadFromSpecctraDsn(
        InputStream inputStream, BoardObservers boardObservers,
        IdentificationNumberGenerator identificationNumberGenerator) {
    var result = super.loadFromSpecctraDsn(inputStream, boardObservers, identificationNumberGenerator);
    if (this.board != null && this.interactiveSettings == null) {
        this.interactiveSettings = InteractiveSettings.getOrCreate(this.board);
        this.initialize_manual_trace_half_widths();
    }
    if (result != DsnFile.ReadResult.ERROR) {
        this.set_layer(0);
    }
    return result;
}
```

Note: `InteractiveSettings.getOrCreate(board)` is introduced in Sub-Issue 02.

### JavaDoc updates

- `GuiBoardManager.loadFromSpecctraDsn` – document that it ensures `interactiveSettings` is initialised before delegating to `set_layer`.

### Unit Tests

- `GuiBoardManagerLoadTest.loadDsn_doesNotThrow_whenNoPriorCreateBoard` – load a minimal DSN without calling `create_board` first; assert no NPE and layer is 0.
- `GuiBoardManagerLoadTest.loadDsn_setsInteractiveSettingsLayerToZero` – assert `get_settings().get_layer() == 0` after load.

---

## Sub-Issue 02 – Make `InteractiveSettings` a singleton and remove it from `HeadlessBoardManager` {#sub-02}

**Files:** `InteractiveSettings.java`, `HeadlessBoardManager.java`, `GuiBoardManager.java`

### Problem

`HeadlessBoardManager` owns and exposes `interactiveSettings`, tightly coupling a GUI concept to headless infrastructure. Multiple `new InteractiveSettings(board)` calls can produce divergent instances, making it impossible to guarantee that the `SettingsMerger` always sees the latest GUI state.

### Proposed Fix

1. Convert `InteractiveSettings` to a **per-GUI-session singleton** using a static holder with double-checked locking:

```java
/** The single GUI-session instance; null when running headless. */
private static volatile InteractiveSettings instance;

/**
 * Returns the singleton, creating it (bound to {@code board}) if not yet initialised.
 * In headless mode this method is never called; use {@link BoardManager#getInteractiveSettings()}
 * to safely obtain the instance (returns {@code null} when headless).
 */
public static InteractiveSettings getOrCreate(RoutingBoard board) {
    if (instance == null) {
        synchronized (InteractiveSettings.class) {
            if (instance == null) {
                instance = new InteractiveSettings(board);
            }
        }
    }
    return instance;
}

/** Resets the singleton (test use only). */
static void resetForTesting() {
    instance = null;
}
```

2. Remove `public InteractiveSettings interactiveSettings` from `HeadlessBoardManager`.
3. Add `protected InteractiveSettings interactiveSettings` to `GuiBoardManager`.
4. Move `initialize_manual_trace_half_widths()` and `get_settings()` implementations from `HeadlessBoardManager` to `GuiBoardManager`; provide a **no-op** default in `HeadlessBoardManager` (returns `null` from `get_settings()`).

### JavaDoc updates

- `InteractiveSettings` class-level Javadoc – document the singleton contract, the `GuiSettings → SettingsMerger` relationship, and that the instance is `null`/inaccessible in headless mode.
- `HeadlessBoardManager` – remove all references to `InteractiveSettings`; document that GUI settings are not applicable in headless mode.
- `GuiBoardManager` – document that it holds the singleton reference and that the instance is the live `GuiSettings` source registered in `SettingsMerger`.

### Unit Tests

- `InteractiveSettingsSingletonTest.getOrCreate_returnsSameInstance` – two consecutive `getOrCreate` calls return the identical reference.
- `InteractiveSettingsSingletonTest.resetForTesting_allowsFreshCreation` – after reset a new instance is created.
- `HeadlessBoardManagerTest.getSettings_returnsNull` – confirm `null` from a headless manager.
- `GuiBoardManagerTest.getSettings_returnsNonNull_afterLoad` – non-null after DSN load.

---

## Sub-Issue 03 – Optional `getInteractiveSettings()` accessor and `BoardManager` JavaDoc {#sub-03}

**File:** `src/main/java/app/freerouting/interactive/BoardManager.java`

### Problem

External classes call `boardManager.get_settings()` without knowing whether the manager is in GUI or headless mode, leading to potential NPEs. The interface JavaDoc currently implies settings are always available.

### Proposed Fix

Rename `get_settings()` to `getInteractiveSettings()` (keeping a deprecated `get_settings()` bridge during transition), and add:

```java
/**
 * Returns the interactive GUI settings singleton, or {@code null} when running headless.
 *
 * <p>The returned {@link InteractiveSettings} instance is also the {@link GuiSettings}
 * source registered in the {@link app.freerouting.settings.SettingsMerger} at priority 50.
 * Callers must not cache this reference; always obtain it through this accessor.
 *
 * @return the singleton {@link InteractiveSettings}, or {@code null} in headless mode
 */
@Nullable InteractiveSettings getInteractiveSettings();

/**
 * Returns {@code true} if this manager runs with an active GUI and therefore provides
 * a non-null {@link InteractiveSettings} singleton.
 */
default boolean isInteractiveModeSupported() { return false; }
```

`GuiBoardManager` overrides `isInteractiveModeSupported()` to return `true`.

### JavaDoc updates

- `BoardManager` interface – full rewrite to document the GUI/headless duality and the `InteractiveSettings → GuiSettings → SettingsMerger` chain.
- `BoardManager.get_settings()` – mark `@Deprecated` with pointer to `getInteractiveSettings()`.

### Unit Tests

- `BoardManagerContractTest` – parameterised test verifying that any `BoardManager` implementation satisfies: if `isInteractiveModeSupported()` then `getInteractiveSettings() != null`; else `getInteractiveSettings() == null`.

---

## Sub-Issue 04 – Encapsulate fields and update `InteractiveSettings` JavaDoc {#sub-04}

**Files:** `InteractiveSettings.java`, `RouteState.java`, `DragState.java`, `StitchRouteState.java`, `TileConstructionState.java`, `InspectedItemState.java`, `GuiBoardManager.java`

### Problem

Fields like `interactiveSettings.layer`, `interactiveSettings.push_enabled`, etc. are accessed directly (package-visible), bypassing encapsulation and making null-guarding impossible in one place. Additionally, the `InteractiveSettings` JavaDoc does not mention its role as the live `GuiSettings` source in the settings pipeline.

### Proposed Fix

1. Make **all** non-final fields in `InteractiveSettings` `private`.
2. Route all external access through existing getters/setters (most already exist; add the missing few).
3. In calling code, access settings via `boardHandling.getInteractiveSettings()` with a null guard, or via dedicated delegates on `GuiBoardManager`.

### JavaDoc updates

- Every field and method in `InteractiveSettings` – add `@see GuiSettings`, `@see app.freerouting.settings.SettingsMerger` where relevant.
- Class-level Javadoc – explicitly state: *"This class is the concrete `GuiSettings` source (priority 50) supplied to the `SettingsMerger`. Any field mutation is therefore visible to the router settings pipeline on the next `merge()` call."*

### Unit Tests

- ArchUnit rule `NoDirectFieldAccessToInteractiveSettingsTest` – no class outside `InteractiveSettings` itself accesses its fields directly.
- `InteractiveSettingsAccessTest` – all setters respect the `read_only` flag.

---

## Sub-Issue 05 – Two-way binding: all GUI panels ↔ `InteractiveSettings` (incl. inherited `GuiSettings` / `RouterSettings` fields) {#sub-05}

**Files:** `InteractiveSettings.java`, `GuiSettings.java`, panel classes in `app.freerouting.gui` (e.g. `SelectParameterWindow`, `RouteParameterWindow`, layer selector, autoroute-parameter window), `GuiBoardManager.java`

### Problem

There is no reliable two-way synchronisation between GUI controls and `InteractiveSettings`. This includes both the fields declared directly on `InteractiveSettings` (layer, push_enabled, …) **and** the `RouterSettings` fields exposed through the inherited `GuiSettings.getSettings()` (max passes, optimiser settings, etc.) that are shown in the autoroute-parameter window. Without synchronisation the merger can see stale values.

### Proposed Fix

Adopt a **`java.beans.PropertyChangeSupport`** pattern:

1. Add `PropertyChangeSupport pcs = new PropertyChangeSupport(this)` to `InteractiveSettings`.
2. Every setter (own and overrides of `GuiSettings`-related mutators) fires a named `PropertyChangeEvent`.
3. `InteractiveSettings.getSettings()` overrides `GuiSettings.getSettings()` and **builds the `RouterSettings` snapshot on-demand** from current field values, so the merger always gets a fresh view.
4. GUI panels register as `PropertyChangeListener` on the singleton during `GuiBoardManager` initialisation.
5. `GuiBoardManager.refreshGuiFromSettings()` iterates all panels and pushes current `InteractiveSettings` values to their controls (called after DSN load, binary load, or settings reset).
6. Panel action listeners call the appropriate setter on `InteractiveSettings`; `PropertyChangeEvent` propagates to any other registered listeners.

### JavaDoc updates

- `InteractiveSettings` – document the `PropertyChangeSupport` contract and the named property keys.
- `InteractiveSettings.getSettings()` – document the override contract: returns a live snapshot derived from current field values.
- `GuiSettings.getSettings()` – update to note that subclasses should override to provide a live snapshot.
- `GuiBoardManager.refreshGuiFromSettings()` – new method; document when to call it.

### Unit Tests

- `InteractiveSettingsPropertyChangeTest.setLayer_firesPropertyChangeEvent` – setting layer fires `PropertyChangeEvent("layer", oldValue, newValue)`.
- `InteractiveSettingsPropertyChangeTest.getSettings_reflectsCurrentFieldValues` – mutate a field, call `getSettings()`, assert the returned `RouterSettings` reflects the change.
- `GuiBoardManagerBindingTest` (mock panels via interface) – after DSN load, `refreshGuiFromSettings` is called and mock panel receives the correct layer value.

---

## Sub-Issue 06 – Register singleton as the live `GuiSettings` source in `SettingsMerger`; update `GuiSettings` JavaDoc {#sub-06}

**Files:** `GuiSettings.java`, `SettingsMerger.java`, application startup code (e.g. `GuiManager.java`)

### Problem

The `SettingsMerger` holds a `GuiSettings` instance constructed at startup with a static `RouterSettings` snapshot. It is never refreshed when the user changes something in the GUI. The `InteractiveSettings` singleton (which extends `GuiSettings`) must be **the** live source at priority 50 so that every `merge()` call sees current GUI state.

### Proposed Fix

1. At GUI startup (in `GuiManager` or `GuiBoardManager` constructor), replace any plain `GuiSettings` source in the merger with the `InteractiveSettings` singleton:

```java
SettingsMerger merger = routingJob.getSettingsMerger();
merger.addOrReplaceSources(InteractiveSettings.getOrCreate(board));
```

2. `InteractiveSettings.getSettings()` (overriding `GuiSettings.getSettings()`) returns a freshly built `RouterSettings` snapshot from current field values on every call.
3. No other code should add a plain `GuiSettings` instance to the merger when a GUI is active.

### JavaDoc updates

- `GuiSettings` class-level Javadoc – document that in GUI mode its sole concrete implementation is `InteractiveSettings`, which acts as both the settings store and the live `SettingsSource`; plain `GuiSettings` instances should not be added to the merger when a GUI is active.
- `GuiSettings.getSettings()` – document the override contract: implementations must return a current snapshot built from live field values.
- `SettingsMerger` class-level Javadoc – update the priority-order list to note that priority 50 is occupied by `InteractiveSettings` (a `GuiSettings` subclass) in GUI mode, or absent in headless mode.

### Unit Tests

- `SettingsMergerGuiIntegrationTest.merge_usesLiveInteractiveSettingsValues` – mutate the `InteractiveSettings` singleton, call `merge()`, assert returned `RouterSettings` reflects the new value.
- `SettingsMergerGuiIntegrationTest.merge_withNoGuiSource_usesDefaults` – in headless mode (no `GuiSettings` registered), merger falls back to lower-priority defaults.

---

## Sub-Issue 07 – Guard headless / API code paths against `interactiveSettings` usage {#sub-07}

**Files:** Any class in `app.freerouting.api`, `app.freerouting.autoroute`, or `app.freerouting.core` that currently touches `interactiveSettings` directly.

### Problem

Some autorouting or API code paths may transitively reach `interactiveSettings` through `BoardManager`. These paths must work correctly when `interactiveSettings` is `null`.

### Proposed Fix

1. Search for all references to `interactiveSettings` outside the `gui` and `interactive` packages.
2. For each reference either: (a) pass the required value as an explicit parameter, or (b) provide a sensible default when `interactiveSettings` is `null`.
3. Add inline comments documenting why each remaining reference is intentional.

### JavaDoc updates

- Any `api` or `core` class that previously had implicit GUI assumptions – remove or qualify those assumptions.

### Unit Tests

- `HeadlessRoutingTest` – full autoroute pass on a simple DSN in headless mode; assert no NPE or `IllegalStateException`.
- `ApiRoutingTest` – same via the REST API layer.

---

## Sub-Issue 08 – Integration tests: GUI load path and headless path {#sub-08}

**Files:** `src/test/java/app/freerouting/tests/`

### Problem

There are no automated tests covering the GUI startup path (without a display) or verifying that the headless path is fully independent of `InteractiveSettings`.

### Proposed Fix

1. Add `GuiStartupHeadlessTest` (headless AWT via `-Djava.awt.headless=true`) running `GuiBoardManager.loadFromSpecctraDsn` and asserting:
   - `InteractiveSettings.getOrCreate(board)` is non-null after load.
   - `getInteractiveSettings().get_layer() == 0`.
   - `initialize_manual_trace_half_widths()` produces values derived from board rules.
   - `SettingsMerger.merge()` reflects the current `InteractiveSettings` state.
2. Add `HeadlessCompleteRoutingTest` – routing job completes without touching `InteractiveSettings`; `getInteractiveSettings()` returns `null` throughout.
3. Ensure both test classes are included in `./gradlew check`.

### JavaDoc updates

- `GuiBoardManager` – cross-reference `GuiStartupHeadlessTest` in the class-level Javadoc usage example.

---

## Acceptance Criteria (Parent Issue)

- [ ] No `NullPointerException` when starting the GUI without CLI arguments.
- [ ] `InteractiveSettings` is a singleton; `InteractiveSettings.getOrCreate(board)` always returns the same instance within a GUI session.
- [ ] `interactiveSettings` is `null` / inaccessible in headless mode (`HeadlessBoardManager.getInteractiveSettings()` returns `null`).
- [ ] The singleton is registered as the live `GuiSettings` source (priority 50) in `SettingsMerger`; `merge()` always reflects the current GUI state.
- [ ] All GUI panel values are correctly initialised from `InteractiveSettings` after DSN load or binary load (`refreshGuiFromSettings()` is called).
- [ ] Changes in any GUI panel are immediately reflected in `InteractiveSettings` via `PropertyChangeEvent`-firing setters; no additional synchronisation calls needed elsewhere.
- [ ] All fields in `InteractiveSettings` (own and inherited) are `private`; external access is through getters/setters only.
- [ ] All classes involved have accurate JavaDoc describing the `InteractiveSettings → GuiSettings → SettingsMerger` chain.
- [ ] All existing routing tests (`./gradlew test`) pass without regression.
- [ ] New unit tests for each sub-issue pass (`./gradlew check`).
- [ ] No Checkstyle or ArchUnit violations introduced.
