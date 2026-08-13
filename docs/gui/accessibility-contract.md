# GUI Accessibility Contract

> Part of the GUI Separation & Accessibility initiative. See
> [`docs/issues/soc-gui-separation-and-accessibility-plan.md`](../issues/soc-gui-separation-and-accessibility-plan.md).
> Locked decisions: **D7** (component-only, forced headless), **D8** (pure-JDK `AccessibleContext` harness),
> **D9** (canvas a11y depth), **D19** (EN + `hu`), **D22** (stable locators + shared registry).

This contract defines how Freerouting Swing components expose themselves to assistive technology and to the
automated accessibility test harness. It is the single source of truth for both product code (what to set) and
the test harness (what to assert).

## 1. Accessible properties

Every interactive / informative control in scope MUST expose the following via its
`javax.accessibility.AccessibleContext`:

| Property | How to set | Notes |
| --- | --- | --- |
| **Accessible name** | `c.getAccessibleContext().setAccessibleName(...)` | Human-readable, **translated** (via `TextManager`). Never null for interactive controls. |
| **Accessible role** | implicit from the Swing component | Do not fight the JDK role (e.g. `JMenu`→`menu`, `JMenuItem`→`menu item`, `JComboBox`→`combo box`). Assert the JDK-provided role. |
| **Accessible description** | `setAccessibleDescription(...)` | Optional; use for non-obvious controls (icons, custom widgets). |
| **Accessible state** | automatic (`enabled`, `visible`, `showing`, …) | Tests assert `enabled` / `visible` where meaningful. |
| **Accessible value** | `AccessibleValue` where the control has one | e.g. sliders, progress. |

For `JLabel`-like text that is purely informative, an accessible name is required; role stays `label`.

**Label-for:** a control that edits a value must be associated with its label (`JLabel.setLabelFor(comp)`), so
screen readers announce the pair. Menu items get their accessible name from their (translated) text plus the
locator (below).

## 2. Stable locators (D22)

Accessibility names are **translated**, so tests must never locate a component by its (locale-dependent) text.
Instead, each in-scope control carries a **stable, locale-independent locator**:

- Set with `Component.setName(locator)` — the canonical, non-displayed, programmatic ID.
- Constant lives in `app.freerouting.gui.a11y.GuiLocators` (the shared registry of locator constants).
- Applied via `app.freerouting.gui.a11y.A11y.tag(component, GuiLocators.X)` at construction time.
- The harness resolves a component **by locator** (`A11y.findByLocator`), never by translated text.

**Locator naming:** `dot.separated.lower.snake`, prefixed by area — e.g. `menu.file`, `menu.file.open`,
`toolbar.layer.select`, `status.message`, `status.layer.current`. Locators are stable public API for tests;
do not rename without updating `GuiLocators` and all referencing tests.

**Forbidden locator mechanisms (D7/D22):** no private-field reflection, no screen coordinates, no
`setVisible(true)` on top-level frames, no translated-label lookups.

## 3. Test-harness rules (D7/D8)

- Tests run **component-only** and **forced headless** (`testGui` sets `-Djava.awt.headless=true`).
- Top-level windows (`JFrame`/`JDialog`/`JWindow`) are **not** constructed (headless forbids it); tests build
  the component under test directly and attach it to a container root.
- All component construction / mutation / action invocation happens on the **EDT** — the harness wraps these
  in `EventQueue.invokeAndWait` and asserts `EventQueue.isDispatchThread()`.
- The harness walks the **`AccessibleContext`** tree (`getAccessibleChildrenCount` / `getAccessibleChild`) for
  structure and uses `Component.getName()` for locator matching; it asserts name/role/state/value.
- A failure message must include the **accessible path** (root→leaf), the component **role**, and the
  **locator** that was sought.
- Sibling components must not share a duplicate or empty accessible name; the harness checks this.
  The audit covers product-owned, laid-out sibling groups only; it does **not** descend into Swing popup
  windows (`JPopupMenu` and subclasses, e.g. the combo box's `BasicComboPopup`), whose LAF-internal
  chrome (scrollbars/buttons) is not product content and would otherwise cause false findings.

## 4. Locales (D19)

MVP workflows run in **English (`en`)** and **Hungarian (`hu`)**. Locators are identical across locales; only
the accessible name/description change. A Hungarian resource-parity check (`_hu` bundle keys match `_en`) backs
this — see `scripts/i18n` and the `EnglishPropertiesParityTest`.

## 5. Canvas / routing-area accessibility depth (D9)

The routed-board canvas is a custom-painted component (no per-item `AccessibleContext`). Its accessibility is
provided through **critical keyboard/menu alternatives** plus **inspect/item lists** (e.g. incompletes and
violation lists) rather than per-pixel accessible objects. Canvas-level a11y is intentionally shallow and is
expanded in Phase 11.

## 6. Component-only production seams

Major frame-owned surfaces expose reusable component seams so accessibility tests do not construct a
`JFrame` or invoke window visibility:

- `BoardMenuBar.createComponentOnly(...)` builds translated top-level menus, stable menu-item
  locators, and keyboard accelerators while reporting actions through a callback.
- `BoardToolbar.createComponentOnly(...)` builds mode/unit controls and toolbar actions without a
  board session; `setComponentOnlyEnabled(...)` applies the production enablement convention.
- `WindowVisibility.createComponentOnly(...)` builds the layer/object visibility settings content
  and emits slider state changes without constructing the top-level visibility window.

These seams are production components, not test fixtures. Tests may assert action callbacks and
accessible state on them, but must not replace them with private-field reflection or coordinate
driving.

Every component-only GUI test must leave no displayable top-level windows or GUI/routing worker
threads. `GuiA11yHarness.requireNoLeakedGuiResources()` provides the shared post-workflow check.
