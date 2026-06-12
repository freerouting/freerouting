# Codebase Structure Analysis & Recommendations

## 1. Executive Summary

The `Freerouting` codebase closely follows standard Java project structures (Gradle layout) but exhibits signs of organic growth that have led to overcrowding in key packages and some unconventional organizational choices.

The current structure is deeply nested under `src/main/java/app/freerouting`. While functional, the lack of sub-packaging in major modules like `gui`, `board`, and `interactive` makes navigation difficult and degrades maintainability.

**Key Goals of this Recommendation:**
*   **Improve Discoverability**: Group related classes to make the project easier to navigate.
*   **Enhance Modularity**: Distinct separation between Data/Model, Logic/Algorithms, and UI (View).
*   **Standardize Naming**: Remove redundant prefixes and adopt modern Java naming conventions.
*   **Correct Source Separation**: Move test code out of `src/main/java`.

---

## 3. Package-Level Refactoring

### 3.1. GUI Module (`app.freerouting.gui`)
**Current Content**: ~72 files (Windows, Menus, Panels, Dialogs) mixed together.

**Recommendation**:
Split into functional sub-packages.

| New Package | Description | Classes to Move (Examples) |
| :--- | :--- | :--- |
| `gui.windows` or `gui.dialogs` | Top-level frames and dialogs | `WindowAbout`, `WindowVia`, `WindowRouteParameter`, `BoardFrame` |
| `gui.menus` | Menu bars and popup menus | `BoardMenuFile`, `PopupMenuMain`, `BoardMenuBar` |
| `gui.components` | Reusable custom UI widgets | `SegmentedButtons`, `PlaceholderTextField`, `ComboBoxClearance` |
| `gui.panels` | Usage-specific panels | `BoardPanel`, `BoardPanelStatus`, `ObjectInfoPanel` |
| `gui.config` | UI settings and defaults | `GUIDefaults...`, `ColorManager` |
| `gui.utils` | Helpers and managers | `GuiManager`, `Cursor` |

### 3.2. Board Model (`app.freerouting.board`)
**Current Content**: ~51 files, mixing Domain Entities, Algorithms, and Spatial Structures.

**Recommendation**:
Separate the "What" (Model) from the "How" (Algorithms).

| New Package | Description | Classes to Move (Examples) |
| :--- | :--- | :--- |
| `board.model` | Core domain entities | `Item`, `Component`, `Pin`, `Trace`, `Via`, `DrillItem`, `Layer` |
| `board.algo` | Manipulation algorithms | `PullTightAlgo`, `ShoveTraceAlgo`, `ForcedPadAlgo`, `MoveDrillItemAlgo` |
| `board.spatial` | Spatial indexing & search trees | `ShapeSearchTree`, `SearchTreeManager`, `ShapeTraceEntries` |
| `board.structure` | Board container structures | `BasicBoard`, `RoutingBoard`, `LayerStructure` |

### 3.3. Autoroute Module (`app.freerouting.autoroute`)
**Current Content**: ~46 files, mixing Engines, Batch processes, and Search Algorithms.

**Recommendation**:

| New Package | Description | Classes to Move (Examples) |
| :--- | :--- | :--- |
| `autoroute.engine` | Core control logic | `AutorouteControl`, `AutorouteEngine` |
| `autoroute.batch` | Batch processing & optimization | `BatchAutorouter`, `BatchOptimizer`, `OptimizeRouteTask` |
| `autoroute.algo` | pathfinding algorithms | `MazeSearchAlgo`, `InsertFoundConnectionAlgo`, `LocateFoundConnectionAlgo` |
| `autoroute.model` | Routing-specific data structures | `ExpansionRoom`, `BoardHistory`, `ItemAutorouteInfo` |

### 3.4. Interactive Module (`app.freerouting.interactive`)
**Current Content**: ~44 files, mostly "States" for the editor state machine and Managers.

**Recommendation**:

| New Package | Description | Classes to Move (Examples) |
| :--- | :--- | :--- |
| `interactive.states` | All state implementations | `DragState`, `RouteState`, `MenuState`, `InteractiveState` |
| `interactive.managers` | Controllers/Managers | `GuiBoardManager`, `HeadlessBoardManager`, `Settings` |
| `interactive.replay` | Replay functionality | `ActivityReplayFile...` |

---

## 4. Class Naming Improvements

**Issue**: Many classes carry redundant prefixes relative to their context or package. "Hungarian-style" naming is inconsistent.

**Recommendations**:

1.  **Remove repetitive Prefixes**:
    *   If a class is in `gui.menus`, `BoardMenuFile` -> `FileMenu`.
    *   If a class is in `gui.windows`, `WindowAbout` -> `AboutDialog`.
    *   `BoardFrame` -> `MainFrame` or `ApplicationWindow`.
    *   `BoardPanel` -> `MainBoardPanel`.

2.  **Modernize Geometry Naming** (`app.freerouting.geometry.planar`):
    *   `FloatPoint`, `IntPoint` can remain if they strictly denote type precision, but consider `Point2D`, `Point2I` or just `Point` (generic). Prioritize clarity over legacy "Float/Int" prefixes where possible, though this is low priority compared to structural changes.

3.  **Clarify "Manager" classes**:
    *   `GuiManager` is vague. Is it `GuiController`, `WindowManager`, or `StyleManager`? Refine based on specific responsibility.

---

## 5. Test Code Organization

**Critical Issue**: `src/main/java/app/freerouting/tests` exists.
**Action**: Move `BoardValidator` (and any other real tests) to `src/test/java/app/freerouting/...`.
If `BoardValidator` is production code (used for runtime validation), rename the package to `app.freerouting.validation` or `app.freerouting.drc`.

---

## 6. Resource Files (`src/main/resources`)

The resource structure under `src/main/resources/app/freerouting` tightly mocks the class structure for localization `.properties` files.

**Action**:
*   **Sync with Refactoring**: When moving `WindowAbout.java` to `gui/dialogs/AboutDialog.java`, the corresponding properties file `WindowAbout_*.properties` **MUST** be moved and renamed to `gui/dialogs/AboutDialog_*.properties`.
*   **Centralize?**: Alternatively, consider grouping all localization strings into fewer bundles (e.g., `messages_en.properties`, `menus_en.properties`) instead of one-per-class, to reduce file clutter (currently ~485 resource files in GUI alone).

---

## 7. Migration Roadmap

1.  **Preparation**: Ensure comprehensive test coverage exists to prevent regression.
2.  **Phase 1: Test Cleanup**: Move the `tests` package out of `src/main`.
3.  **Phase 2: High-Level Repackaging**: Create `gui.dialogs`, `gui.menus` etc., and move files. Update imports.
4.  **Phase 3: Resource Sync**: Move and rename properties files matching Phase 2.
5.  **Phase 4: Class Renaming**: Execute renamed (e.g., `WindowAbout` -> `AboutDialog`).

---

## 8. Cleanup Recommendations

*   **Root Files**: Files like `google_checks.xml`, `launch4j.xml`, etc., are cluttering the root. Consider moving configuration files to a `config/` directory where possible.


 This analysis provides a blueprint for a more maintainable, standard, and navigable codebase.
