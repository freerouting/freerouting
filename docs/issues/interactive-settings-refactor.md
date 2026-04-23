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
| 1 | ~~Fix immediate NPE: initialise `interactiveSettings` before `set_layer` is called in `GuiBoardManager`~~ ✅ |
| 2 | ~~Make `InteractiveSettings` a singleton and move it out of `HeadlessBoardManager`~~ ✅ |
| 3 | ~~Introduce `BoardManager.getInteractiveSettings()` optional accessor and update `BoardManager` JavaDoc~~ ✅ |
| 4 | ~~Replace direct field access with accessor methods; update `InteractiveSettings` JavaDoc~~ ✅ |
| 5 | ~~Two-way binding: all GUI panels ↔ `InteractiveSettings` fields (including inherited `GuiSettings` fields)~~ ✅ |
| 6 | ~~Register the singleton as the live `GuiSettings` source in `SettingsMerger`; update `GuiSettings` JavaDoc~~ ✅ |
| 7 | ~~Guard headless / API code paths against `interactiveSettings` usage~~ ✅ |
| 8 | ~~Integration tests: GUI load path initialises settings; headless path never requires them~~ ✅ |

---

## Sub-Issue 01 – Fix immediate NPE in `GuiBoardManager.loadFromSpecctraDsn` ✅ {#sub-01}

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

Note: `InteractiveSettings.getOrCreate(board)` is introduced in Sub-Issue 02. For the **first** load in a session `getOrCreate` is appropriate; **subsequent** loads must use `InteractiveSettings.reset(board)` to guarantee the singleton is rebound to the new board (see the design note in Sub-Issue 02).

### JavaDoc updates

- `GuiBoardManager.loadFromSpecctraDsn` – document that it ensures `interactiveSettings` is initialised before delegating to `set_layer`.

### Unit Tests

- `GuiBoardManagerLoadTest.loadDsn_doesNotThrow_whenNoPriorCreateBoard` – load a minimal DSN without calling `create_board` first; assert no NPE and layer is 0.
- `GuiBoardManagerLoadTest.loadDsn_setsInteractiveSettingsLayerToZero` – assert `get_settings().get_layer() == 0` after load.

---

## Sub-Issue 02 – Make `InteractiveSettings` a singleton and remove it from `HeadlessBoardManager` ✅ {#sub-02}

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

> **Design note – reinitialise on every design load:**
> The singleton must be **reset and recreated** each time a new design (DSN or `.frb` binary) is loaded into the GUI. Loading a new design replaces `this.board` with a new `RoutingBoard` instance whose layer count, net list, and design rules differ from the previous board. An `InteractiveSettings` that was constructed for the old board (e.g. a different `manual_trace_half_width_arr` size) is therefore invalid for the new one.
>
> Concretely, `GuiBoardManager.loadFromSpecctraDsn` (and the equivalent binary-load path) must call `InteractiveSettings.reset(this.board)` — a new public factory method that atomically replaces the singleton:
>
> ```java
> /**
>  * Discards the current singleton and creates a fresh one bound to {@code board}.
>  * Must be called whenever a new design is loaded into the GUI session.
>  *
>  * @param board the newly loaded {@link RoutingBoard}; must not be {@code null}
>  * @return the new singleton instance
>  */
> public static InteractiveSettings reset(RoutingBoard board) {
>     synchronized (InteractiveSettings.class) {
>         instance = new InteractiveSettings(board);
>         return instance;
>     }
> }
> ```
>
> - `getOrCreate(board)` continues to be used for the **initial** creation (first load in a session, or after `resetForTesting()`).
> - `reset(board)` is used for every **subsequent** load to guarantee the singleton is always bound to the currently active board.
> - All registered `PropertyChangeListener`s (GUI panels) must be **re-registered** after a `reset` call, since the old instance is discarded. `GuiBoardManager.refreshGuiFromSettings()` is the natural place to do this: it re-subscribes panels to the new singleton and pushes the fresh settings values to their controls.

### JavaDoc updates

- `InteractiveSettings` class-level Javadoc – document the singleton contract, the `GuiSettings → SettingsMerger` relationship, and that the instance is `null`/inaccessible in headless mode.
- `HeadlessBoardManager` – remove all references to `InteractiveSettings`; document that GUI settings are not applicable in headless mode.
- `GuiBoardManager` – document that it holds the singleton reference and that the instance is the live `GuiSettings` source registered in `SettingsMerger`.

### Unit Tests

- `InteractiveSettingsSingletonTest.getOrCreate_returnsSameInstance` – two consecutive `getOrCreate` calls return the identical reference.
- `InteractiveSettingsSingletonTest.resetForTesting_allowsFreshCreation` – after `resetForTesting()` a new instance is created.
- `InteractiveSettingsSingletonTest.reset_replacesInstance` – `reset(newBoard)` returns a different object reference than the previous singleton; the old reference is no longer returned by `getOrCreate`.
- `InteractiveSettingsSingletonTest.reset_rebindsToNewBoard` – after `reset(boardB)`, `getOrCreate(boardA)` returns the instance bound to `boardB` (the singleton ignores the stale board argument).
- `HeadlessBoardManagerTest.getSettings_returnsNull` – confirm `null` from a headless manager.
- `GuiBoardManagerTest.getSettings_returnsNonNull_afterLoad` – non-null after DSN load.
- `GuiBoardManagerTest.secondLoad_reinitializesInteractiveSettings` – load a second DSN into the same `GuiBoardManager`; assert that `getInteractiveSettings()` returns a **new** instance (not the one from the first load) and that `get_layer()` is `0`.

---

## Sub-Issue 03 – Optional `getInteractiveSettings()` accessor and `BoardManager` JavaDoc ✅ {#sub-03}

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

## Sub-Issue 04 – Encapsulate fields and update `InteractiveSettings` JavaDoc ✅ {#sub-04}

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

## Accessibility Identifiers for GUI Test Automation {#accessibility-identifiers}

### The Question

> *Is there a way to use accessibility identifiers on the GUI to automate testing?*

**Yes — and it is the recommended strategy for every Swing testing library.** Java Swing has two complementary mechanisms that serve as "test IDs", and both are already part of the standard JDK with no extra dependency.

---

### Mechanism 1 – `Component.setName(String)` / `Component.getName()`

Every `java.awt.Component` (and therefore every Swing widget) has a `name` property settable via `setName()`. This name:

- is **not** displayed to the user
- is **not** the label text (`JButton.getText()`) — it is a separate identity string
- survives look-and-feel changes, layout refactors, and i18n label updates
- is used by **AssertJ Swing** as its primary component-lookup key

```java
// In production GUI code (e.g. SelectParameterWindow constructor):
JComboBox<String> layerCombo = new JComboBox<>();
layerCombo.setName("layer-selector");   // ← test ID

JCheckBox pushCheck = new JCheckBox("Push enabled");
pushCheck.setName("push-enabled");      // ← test ID
```

```java
// In test code (AssertJ Swing):
window.comboBox("layer-selector").selectItem(2);
window.checkBox("push-enabled").check();
```

AssertJ Swing docs explicitly state: *"Using a unique name for GUI components **guarantees** that we can always find them, regardless of any change in the GUI"* — this is the most robust lookup strategy available.

---

### Mechanism 2 – `AccessibleContext.setAccessibleName(String)` + `setAccessibleDescription(String)`

`javax.accessibility.AccessibleContext` (exposed via `component.getAccessibleContext()`) holds:

| Field | Purpose |
|---|---|
| `accessibleName` | Short human-readable label (read by screen readers and test tools) |
| `accessibleDescription` | Longer tooltip-style description |
| `accessibleRole` | Role enum (`PUSH_BUTTON`, `CHECK_BOX`, `COMBO_BOX`, …) |

This is the **assistive technology API** (JAWS, NVDA, Java Access Bridge on Windows). It also doubles as a test-automation hook: tools like **QF-Test** and **Marathon** use `accessibleName` as the primary identity. The Oracle Swing accessibility tutorial notes: *"The information that enables assistive technologies can be used for other tools, as well, such as automated GUI testers."*

```java
JComboBox<String> layerCombo = new JComboBox<>();
layerCombo.setName("layer-selector");                                    // test automation
layerCombo.getAccessibleContext().setAccessibleName("Active layer");     // screen readers
layerCombo.getAccessibleContext().setAccessibleDescription(
    "Selects the copper layer currently being routed");                  // tooltip/AT
```

---

### Comparison: `setName` vs `setAccessibleName`

| | `setName(String)` | `setAccessibleName(String)` |
|---|---|---|
| **Used by** | AssertJ Swing, UISpec4J, abego GuiTesting | QF-Test, Marathon, JAWS, NVDA, Java Access Bridge |
| **Visible to user?** | No | No (but read aloud by screen readers) |
| **Survives i18n?** | Yes (set a stable key) | Typically localised — can diverge from test ID |
| **Best practice** | Stable machine-readable key (e.g. `"layer-selector"`) | Human-readable label (can be localised) |
| **Set together?** | ✅ Yes — they are independent fields | ✅ Yes |

**Recommendation: always set both.** `setName` gives the stable test automation key; `setAccessibleName` gives the screen-reader-friendly label.

---

### Naming Convention for This Project

Adopt a **kebab-case component-ID** convention mirroring common web-accessibility practice:

```
<panel-area>-<widget-type>-<semantic-name>
```

Examples:

| Component | `setName` ID | `setAccessibleName` |
|---|---|---|
| Layer selector combo | `"route-layer-selector"` | `"Active routing layer"` |
| Push-enabled checkbox | `"route-push-enabled"` | `"Enable push routing"` |
| Manual trace width spinner | `"route-trace-width"` | `"Manual trace width"` |
| Auto-route start button | `"autoroute-start-button"` | `"Start auto-routing"` |
| Max passes spinner | `"autoroute-max-passes"` | `"Maximum routing passes"` |

---

### What Needs to Change in the Codebase

1. **Every interactive widget** in `app.freerouting.gui` that is read or written by `InteractiveSettings` must get a `setName(STABLE_ID)` call in its constructor or factory method.
2. Add `setAccessibleName(localizedLabel)` + `setAccessibleDescription(...)` for assistive technology compliance (good practice regardless of testing).
3. Maintain a **constants class** (e.g. `GUIComponentIds`) that centralises all ID strings — test code and production code import the same constants, preventing typos:

```java
// src/main/java/app/freerouting/gui/GUIComponentIds.java
public final class GUIComponentIds {
    private GUIComponentIds() {}

    // Route parameter panel
    public static final String ROUTE_LAYER_SELECTOR    = "route-layer-selector";
    public static final String ROUTE_PUSH_ENABLED      = "route-push-enabled";
    public static final String ROUTE_TRACE_WIDTH       = "route-trace-width";

    // Autoroute panel
    public static final String AUTOROUTE_MAX_PASSES    = "autoroute-max-passes";
    public static final String AUTOROUTE_START_BUTTON  = "autoroute-start-button";
    // …
}
```

```java
// In production code:
pushCheck.setName(GUIComponentIds.ROUTE_PUSH_ENABLED);

// In test code:
window.checkBox(GUIComponentIds.ROUTE_PUSH_ENABLED).requireSelected();
```

---

### Sub-Issue Addition: `GUIComponentIds` and `setName` pass

This work should be added as part of **Sub-Issue 05** (two-way binding), since the binding wiring and the widget naming are both introduced at panel-construction time. Concretely:

- Create `GUIComponentIds.java` constants class.
- For every panel class touched in Sub-Issue 05, add `setName(GUIComponentIds.CONSTANT)` and `getAccessibleContext().setAccessibleName(...)` to each interactive widget.
- Add a Checkstyle or ArchUnit rule `AllGuiWidgetsMustHaveNameTest` that verifies all `JButton`, `JCheckBox`, `JComboBox`, `JSpinner`, `JTextField` instances inside the `app.freerouting.gui` package have a non-null, non-empty name at runtime (checked via a post-construction unit test rather than a static rule).

---

## GUI Testing: Technology Research & Decision {#gui-testing-research}

Testing a Swing GUI requires choosing between three broad strategies that can be used independently or in combination. The table below summarises what was evaluated; the recommended approach follows.

### Options Evaluated

#### 1. AssertJ Swing (`org.assertj:assertj-swing`, latest 3.17.1 — Sep 2020)
A fork of FEST Swing, the most widely recommended Swing functional-testing library. Simulates real OS-level user gestures (mouse clicks, keyboard input) against live Swing components running in the EDT.

| Dimension | Assessment |
|---|---|
| **Last release** | 3.17.1 (Sep 2020); no commits since early 2021 |
| **Java 17/21/25 compatibility** | Requires `--add-opens` JVM flags for internal AWT packages on Java 9+; works with flag set but not officially validated on Java 25 |
| **CI / headless** | Needs a real or virtual display (Xvfb on Linux CI); requires pairing with Caciocavallo (see below) for truly headless runs |
| **License** | Apache 2.0 — compatible with GPLv3 |
| **Verdict** | ✅ **Viable for end-to-end / smoke GUI tests** but requires extra CI setup; should **not** be the primary unit-testing strategy because of the display requirement and slow execution |

#### 2. Caciocavallo (`net.java.openjdk.cacio:cacio-tta`)
A pluggable AWT toolkit that renders Swing to an in-memory buffer, enabling fully headless Swing tests without Xvfb. Used as a companion to AssertJ Swing or UISpec4J in CI pipelines.

| Dimension | Assessment |
|---|---|
| **Last release** | Active; Java 17 branch explicitly supported with `--add-exports` flags |
| **Java 21/25** | Requires `--add-exports java.desktop/...=ALL-UNNAMED`; no module descriptor — may need flag management in Gradle test JVM args |
| **CI** | Ideal for Linux CI (GitHub Actions) without a display server |
| **License** | GPL-2.0 — compatible with GPLv3 |
| **Verdict** | ✅ **Adopt as the headless display backend** for any tests that need to instantiate real Swing components in CI |

#### 3. UISpec4J
Lightweight alternative to AssertJ Swing; uses its own EDT interception model. Can pair with Caciocavallo for headless execution.

| Dimension | Assessment |
|---|---|
| **Last release** | 2.5 snapshot; infrequent maintenance |
| **Verdict** | ⚠️ Lower community momentum than AssertJ Swing; **skip in favour of AssertJ Swing** |

#### 4. MVP + Mockito: test the Presenter without any Swing components ✅ **PRIMARY STRATEGY**
The most robust and fastest testing strategy. The GUI is split into:
- **View interface** (`SettingsView`, `LayerSelectorView`, …) — thin contracts with only setter/getter/listener methods; no Swing imports.
- **Presenter** (part of `GuiBoardManager` or thin collaborators) — holds the logic; depends only on the View interface and `InteractiveSettings`.
- **Swing implementation** — implements the View interface; untested at unit level, covered by smoke/integration tests only.

Presenters are tested with **Mockito** mock views — zero Swing, zero EDT, zero display. This is the [Oracle-recommended MVC pattern](https://www.oracle.com/technical-resources/articles/javase/application-design-with-mvc.html) applied at unit-test granularity.

```java
@Test
void setLayer_updatesViewAndInteractiveSettings() {
    SettingsView mockView = mock(SettingsView.class);
    InteractiveSettings settings = InteractiveSettings.getOrCreate(mockBoard);
    SettingsPresenter presenter = new SettingsPresenter(mockView, settings);

    presenter.onLayerChanged(2);

    assertThat(settings.get_layer()).isEqualTo(2);
    verify(mockView).showLayer(2);
}
```

| Dimension | Assessment |
|---|---|
| **Speed** | Milliseconds per test — no EDT, no rendering |
| **CI** | Fully headless; no extra JVM flags or display backend needed |
| **Coverage** | Covers all business logic in the presenter and the `PropertyChangeSupport` wiring |
| **Java 25** | Zero compatibility risk |
| **Verdict** | ✅ **Primary unit-testing strategy for all GUI logic** |

#### 5. `java.awt.headless=true` + `SwingUtilities.invokeAndWait` boundary tests
For cases where a real Swing component must be constructed (e.g., verifying that `InteractiveSettings.getOrCreate` integrates with a `BoardFrame`), run with `-Djava.awt.headless=true` and avoid any component that requires a native peer. `JFrame`, `JDialog` etc. will throw `HeadlessException`; use `JPanel` / model-only classes instead. This is sufficient for sub-issue 08's `GuiStartupHeadlessTest`.

---

### Recommended Testing Pyramid for This Issue

```
┌─────────────────────────────────────┐
│  E2E / Smoke (AssertJ Swing +       │  ← few; slow; CI with Caciocavallo
│  Caciocavallo on GitHub Actions)    │
├─────────────────────────────────────┤
│  Integration: headless=true,        │  ← DSN load, SettingsMerger wiring
│  model + presenter, no Swing peers  │
├─────────────────────────────────────┤
│  Unit: MVP Presenter + Mockito      │  ← MAJORITY of tests; fast; no display
│  (PropertyChangeSupport, setters,   │
│   RouterSettings snapshot)          │
└─────────────────────────────────────┘
```

### Gradle / CI Configuration Required

```groovy
// build.gradle – test JVM args for Caciocavallo on headless CI
tasks.withType(Test).configureEach {
    // Required for AssertJ Swing + Caciocavallo on Java 17+
    jvmArgs(
        "--add-exports=java.desktop/java.awt=ALL-UNNAMED",
        "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED"
    )
    // Use Caciocavallo in-memory toolkit when no display is available
    if (System.getenv("CI") != null) {
        systemProperty "awt.toolkit", "com.github.caciocavallosilano.cacio.ctc.CTCToolkit"
        systemProperty "java.awt.graphicsenv", "com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment"
    }
}
```

```groovy
// test dependencies
testImplementation "org.assertj:assertj-swing-junit5:3.17.1"
testImplementation "com.github.caciocavallosilano.caciocavallo:cacio-tta:1.11"
```

> **Note on Java 25:** Caciocavallo's `--add-exports` approach relies on strong encapsulation relaxation. If the build switches to a stricter module boundary in a future Java release, the AssertJ Swing + Caciocavallo tier may need to be re-evaluated. The MVP + Mockito tier is immune to this risk.

---

## Two-Way Binding: Technology Research & Decision {#binding-research}

### Options Evaluated

#### 1. JGoodies Binding (`com.jgoodies:jgoodies-binding`, latest 2.7.0)
Mature library that connects JavaBean properties to Swing components via `ValueModel` / `PropertyAdapter` abstractions. Reduces boilerplate and supports two-way sync out of the box.

| Dimension | Assessment |
|---|---|
| **Last release** | 2.7.0 (≈ 2013); no commits since |
| **Java 17/21/25 compatibility** | Not guaranteed; no module-info; known classpath issues on newer JDK versions |
| **License** | BSD — compatible with GPLv3 |
| **Dependency risk** | High — abandoned, unlikely to be patched for Java 25 modules or VirtualThread edge cases |
| **Verdict** | ❌ **Do not adopt** — abandoned; would introduce a fragile transitive dependency |

#### 2. BetterBeansBinding / JSR-295 BeansBinding
Successor to the abandoned JSR-295 specification. Both projects are unmaintained (last activity > 10 years ago) and do not support modern Java versions.

| Verdict | ❌ **Do not adopt** — dead project |

#### 3. JavaFX Properties / ObservableValue
JavaFX's `javafx.beans.property.*` package provides first-class reactive properties with full two-way binding support (`Bindings.bindBidirectional`). However, this project uses **Swing** for its GUI and mixing JavaFX into a Swing application requires `Platform.runLater` bridging, adding substantial complexity and a JavaFX runtime dependency.

| Verdict | ❌ **Do not adopt** — wrong toolkit; adds JavaFX module dependency |

#### 4. Pure `java.beans.PropertyChangeSupport` + Observer / MVP pattern ✅ **CHOSEN**
The approach documented in the [Oracle Java SE MVC guide](https://www.oracle.com/technical-resources/articles/javase/application-design-with-mvc.html) and used throughout the existing codebase. No external dependency. Ships with every JDK. Works on Java 25. Fully compatible with GPLv3.

**Pattern:** `InteractiveSettings` acts as the **Model** in an MVP-style trio:
- **Model** – `InteractiveSettings`: holds state, fires `PropertyChangeEvent` on every mutation.
- **View** – Swing panel classes (`SelectParameterWindow`, `RouteParameterWindow`, …): registers as `PropertyChangeListener`; updates controls; calls setters on user action.
- **Presenter** – `GuiBoardManager` (`refreshGuiFromSettings()`): orchestrates initial push of model state to all views after a DSN/binary load.

**Why this is the correct choice for this codebase:**
- Zero new dependency – consistent with the project's lean dependency philosophy.
- Already used elsewhere in the `interactive` package for board-change notification.
- Fully thread-safe when event dispatch is guarded by `SwingUtilities.invokeLater`.
- Composable: individual panels subscribe only to the properties they care about (named-property listeners), avoiding unnecessary repaints.
- Trivially testable: swap Swing components for mock `PropertyChangeListener` implementations.

### Implementation Checklist (informs Sub-Issue 05)

1. `InteractiveSettings` gets a `private final PropertyChangeSupport pcs = new PropertyChangeSupport(this)`.
2. `addPropertyChangeListener` / `removePropertyChangeListener` delegates expose it publicly.
3. Every setter in `InteractiveSettings` fires `pcs.firePropertyChange(PROP_NAME, oldVal, newVal)` using `public static final String` constants (e.g. `PROP_LAYER = "layer"`).
4. `InteractiveSettings.getSettings()` override constructs and returns a fresh `RouterSettings` snapshot — ensures `SettingsMerger` always sees current GUI values.
5. Each GUI panel implements `PropertyChangeListener` and subscribes to the singleton during `GuiBoardManager` initialisation (or panel construction).
6. `GuiBoardManager.refreshGuiFromSettings()` triggers a full push from model → all registered views (called after DSN/binary load and settings reset). It must also **re-register** all panels as `PropertyChangeListener`s on the new singleton whenever `InteractiveSettings.reset(board)` has been called, since the old instance is discarded and its listener list is gone.
7. Panel action/change listeners invoke the appropriate `InteractiveSettings` setter; the resulting `PropertyChangeEvent` propagates to all other registered listeners — no extra sync calls needed.
8. **On every design load** (`loadFromSpecctraDsn`, binary load, file → open): call `InteractiveSettings.reset(this.board)` to replace the singleton, then call `refreshGuiFromSettings()` to re-subscribe all panels and push fresh values to their controls.

---

## Sub-Issue 05 – Two-way binding: all GUI panels ↔ `InteractiveSettings` (incl. inherited `GuiSettings` / `RouterSettings` fields) ✅ {#sub-05}

**Files:** `InteractiveSettings.java`, panel classes in `app.freerouting.gui` (`WindowRouteParameter`, `WindowSelectParameter`, `WindowMoveParameter`), `GuiBoardManager.java`, `BoardFrame.java`

### Problem

There was no reliable two-way synchronisation between GUI controls and `InteractiveSettings`. Without synchronisation the merger could see stale values and panels would not refresh when settings changed programmatically (e.g. after a new design load).

### Implementation — what was done

**`InteractiveSettings.java`**

- Added 18 `public static final String PROP_*` named property key constants (e.g. `PROP_LAYER`, `PROP_PUSH_ENABLED`, …) so all callers use constants, never bare strings.
- Added `private transient PropertyChangeSupport pcs` — `transient` so it is excluded from Java serialisation and automatically re-created in `readObject()` after binary deserialisation.
- Added four public listener-management methods:
  - `addPropertyChangeListener(PropertyChangeListener)`
  - `removePropertyChangeListener(PropertyChangeListener)`
  - `addPropertyChangeListener(String propertyName, PropertyChangeListener)` (named-property variant)
  - `removePropertyChangeListener(String propertyName, PropertyChangeListener)`
- **Every setter** now captures the old value and calls `pcs.firePropertyChange(PROP_*, old, new)` after the mutation. `set_zoom_with_wheel` only fires when the value actually changes (guards against redundant events).
- Overrode `getSettings()` to return a `new RouterSettings()` (all-null, so the merger skips this source for router-specific fields). Documents that Sub-Issue 06 will wire the full pipeline once the fields are mapped.
- `readObject()` re-creates the transient `pcs` after deserialisation so loaded instances immediately support listeners.

**`GuiBoardManager.java`**

- Added `refreshGuiFromSettings()`: walks the parent-component chain to find the owning `BoardFrame`, iterates `getPermanentSubwindows()`, registers a lambda `PropertyChangeListener` on the new singleton for each window, then calls `refresh()` immediately to push current values to controls.
- Called `refreshGuiFromSettings()` at the end of both `loadFromSpecctraDsn` and `loadFromBinary` (after `set_layer(0)`) so panels always reflect the freshly-loaded board state.

**`BoardFrame.java`**

- Added `getPermanentSubwindows()` public accessor so `GuiBoardManager.refreshGuiFromSettings()` can reach the panels from the `interactive` package without a cross-package field access.

**GUI panels: `WindowRouteParameter`, `WindowSelectParameter`, `WindowMoveParameter`**

- Each subscribes to the `InteractiveSettings` singleton at the end of its constructor:
  ```java
  InteractiveSettings is = this.board_handling.getInteractiveSettings();
  if (is != null) {
      is.addPropertyChangeListener(_ -> SwingUtilities.invokeLater(this::refresh));
  }
  ```
  Any programmatic field change (from `refreshGuiFromSettings()` or another panel's action listener) automatically refreshes the control on the EDT.

### JavaDoc updates

- `InteractiveSettings` class-level Javadoc – documents the `PropertyChangeSupport` contract, the named property keys, and the serialisation note for `pcs`.
- `InteractiveSettings.getSettings()` – documents the override contract and the Sub-Issue 06 forward-reference.
- `GuiBoardManager.refreshGuiFromSettings()` – full Javadoc documenting when to call it and why re-registration is necessary after `reset()`.

### Unit Tests — `InteractiveSettingsPropertyChangeTest` (11 tests, all passing)

| Test | Verifies |
|---|---|
| `setLayer_firesPropertyChangeEvent` | Correct property name, old value, new value |
| `setPushEnabled_firesPropertyChangeEvent` | Boolean toggle fires event |
| `setStitchRoute_firesPropertyChangeEvent` | Route-mode toggle fires event |
| `setAutomaticNeckdown_firesPropertyChangeEvent` | Neckdown toggle fires event |
| `setManualTraceHalfWidth_firesPropertyChangeEvent` | Array-entry mutation fires event |
| `setHilightRoutingObstacle_firesPropertyChangeEvent` | Hilight toggle fires event |
| `setZoomWithWheel_firesEventOnlyWhenValueChanges` | No spurious event when value unchanged |
| `removePropertyChangeListener_stopsReceivingEvents` | Unsubscribed listener receives no further events |
| `addNullListener_doesNotThrow` | Null listener silently ignored |
| `getSettings_returnsNonNullRouterSettings` | Override returns non-null `RouterSettings` |
| `setter_doesNotFireEvent_whenReadOnly` | `read_only` gate suppresses all events |

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

## Sub-Issue 08 – Integration tests: GUI load path and headless path ✅ {#sub-08}

**Files:**
- `src/test/java/app/freerouting/interactive/GuiStartupHeadlessTest.java`
- `src/test/java/app/freerouting/tests/HeadlessCompleteRoutingTest.java`

### Problem

There were no automated tests covering the GUI startup path (without a display) or verifying that the headless path is fully independent of `InteractiveSettings`.

### Implementation

1. Added `GuiStartupHeadlessTest` (package `app.freerouting.interactive`, headless AWT via `-Djava.awt.headless=true`) using `HeadlessBoardManager` for board loading and manually replicating the `GuiBoardManager.loadFromSpecctraDsn` singleton-reset steps. Tests assert:
   - `InteractiveSettings.getOrCreate(board)` is non-null after `InteractiveSettings.reset(board)`.
   - `get_layer()` returns `0` immediately after reset.
   - `initialize_manual_trace_half_widths()` produces values equal to the board rule defaults.
   - `SettingsMerger.merge()` reflects live mutations of the registered `InteractiveSettings` singleton.
   - Calling `reset(board)` a second time replaces the singleton and returns a fresh instance.
   - The merged `RouterSettings` is non-null even with only `DefaultSettings` + `InteractiveSettings` sources.

2. Added `HeadlessCompleteRoutingTest` (package `app.freerouting.tests`, extends `RoutingFixtureTest`) – routing jobs complete without touching `InteractiveSettings`:
   - Full routing pass on `Issue508-DAC2020_bm01.dsn` (3 passes, 50 items, 90 s timeout) reaches a terminal state.
   - Empty-board routing (`empty_board.dsn`, 1 pass, 30 s timeout) completes without NPE.
   - `HeadlessBoardManager.getInteractiveSettings()` returns `null` throughout headless routing.

3. Both test classes are picked up by `./gradlew check` automatically (JUnit 5 discovery).

### JavaDoc updates

- `GuiBoardManager` class-level Javadoc updated to cross-reference `GuiStartupHeadlessTest` as a usage example of the GUI load path.

---

## Acceptance Criteria (Parent Issue)

- [x] No `NullPointerException` when starting the GUI without CLI arguments.
- [x] `InteractiveSettings` is a singleton; `InteractiveSettings.getOrCreate(board)` always returns the same instance within a GUI session.
- [x] `InteractiveSettings.reset(board)` is called on **every** design load (DSN or binary); the singleton is always bound to the currently active board.
- [x] After `reset`, `GuiBoardManager.refreshGuiFromSettings()` re-subscribes all panels as `PropertyChangeListener`s on the new singleton and pushes fresh values to their controls.
- [x] `interactiveSettings` is `null` / inaccessible in headless mode (`HeadlessBoardManager.getInteractiveSettings()` returns `null`).
- [x] The singleton is registered as the live `GuiSettings` source (priority 50) in `SettingsMerger`; `merge()` always reflects the current GUI state.
- [x] All GUI panel values are correctly initialised from `InteractiveSettings` after DSN load or binary load (`refreshGuiFromSettings()` is called).
- [x] Changes in any GUI panel are immediately reflected in `InteractiveSettings` via `PropertyChangeEvent`-firing setters; no additional synchronisation calls needed elsewhere.
- [x] All fields in `InteractiveSettings` (own and inherited) are `private`; external access is through getters/setters only.
- [x] All classes involved have accurate JavaDoc describing the `InteractiveSettings → GuiSettings → SettingsMerger` chain.
- [x] All existing routing tests (`./gradlew test`) pass without regression.
- [x] New unit tests for each sub-issue pass (`./gradlew check`).
- [ ] No Checkstyle or ArchUnit violations introduced.
