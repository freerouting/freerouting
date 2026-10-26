# Freerouting.net Migration & Porting Plan — Part 3

This document tracks all improvements and bug fixes ported from `freerouting.net` (from synced commit `159f89ae6a7a3fa9ac14b33cb5e3b41e0b41b6aa` through HEAD `17afc690` — spanning 582 commits), categorized and prioritized for migration into the Java codebase (`c:\Work\freerouting`).

> [!NOTE]
> **Attribution:** The C# port (`freerouting.net`), along with all referenced optimizations, algorithmic refinements, KiCad 10 enhancements, and benchmark validations, was created and developed by **Erik Gavriluk of Experimental Television Center**. All ported changes credit and acknowledge his work.

---

## 1. Scope & Status Summary

| Phase | Category | Description | Items | Status |
|---|---|---|:---:|:---:|
| **Phase 1** | **Core Algorithmic Fixes & Precision Invariants** | Unfloored optimizer rank, input-fixed locked tracks, SES rational junction sync, same-net conduction DRC guard, non-convex pad decomposition | 5 | In Progress |
| **Phase 2** | **Design Rules & Clearance Enhancements** | Physical hole-to-hole spacing (`HoleSpacing`), physical exact copper clearance (`ExactCopper`), via-in-pad auto-detection | 3 | Pending |
| **Phase 3** | **Advanced Autorouting Pipelines, BGA & Escapes** | BGA escape ordering (`BallEscapeOrder`), two-layer BGA fanout policy (`TwoLayerBga`), fine-pitch exit stubs (`ExitStubs`), keepout-covered pin pad end relocation (`PadEndWithClearStub`), dangling copper cleanup (`DanglingCopper`) | 5 | Pending |
| **Phase 4** | **Architecture & Determinism Review** | Strict compliance with time-budget agnostic rule, Clean Code naming conventions, nullable settings invariants | 2 | Pending |
| **Phase 5** | **Verification & Benchmarks** | Regression testing on golden fixtures (`Dac2020Bm01RoutingTest`, `Issue508`), Spotless & Checkstyle quality gates | 3 | Pending |

---

## 2. Detailed Task Breakdown

### Phase 1: Core Algorithmic Fixes & Precision Invariants

- [x] **Task 1.1: Optimizer Pass Ranking Without Zero Floor & Excess-Based Improvement**
  - **Origin Commits:** `095ff0a9`, `851d575d`, `775a8b55`
  - **Problem:** `BoardStatistics.getOptimizerScore()` floors at `0.0`. On dense or difficult boards (e.g. `DerKnopf_led-ring`), initial route penalties exceed 1,000, so the score reads `0.00` before and after every pass. The optimizer cannot detect improvement, prematurely rejects candidate improvements, or loops fruitlessly.
  - **Solution:** Add `getOptimizerRank()` in `BoardStatistics.java` returning raw unfloored score `(1000.0 - lengthPenalty - viaPenalty - bendPenalty)`. In `BatchOptimizer.java`, rank passes by rank and calculate improvement percentage against the remaining excess `(1000.0 - before)` when baseline score is `<= 0`.
  - [x] Add `getOptimizerRank()` in `BoardStatistics.java`.
  - [x] Update `BatchOptimizer.java` to use `getOptimizerRank` for pass evaluation and score threshold calculation (`scoreImprovementPercent`).
  - [x] Port `OptimizerRankTests.cs` to `src/test/java/app/freerouting/autoroute/pipeline/OptimizerRankTest.java`.
  - [x] Run fast verification tests.

- [x] **Task 1.2: Input-Fixed Locked Tracks & Arc Stand-ins (`FixedState.INPUT_FIXED`)**
  - **Origin Commits:** `574ff793`, `ad33ee4c`
  - **Problem:** KiCad locked tracks and arc stand-ins arrive in DSN as `(type fix)`. Java currently reads them as `SYSTEM_FIXED`. However, pipeline stages (Direct Pin Routing, Additive Routing) also use `SYSTEM_FIXED` for temporary locking. Because `SYSTEM_FIXED` items were not treated as preserved routes, branches ending on locked tracks were rejected as non-contacts or produced dangling DRC warnings.
  - **Solution:** Introduce `FixedState.INPUT_FIXED` (stronger than `SYSTEM_FIXED`). Treat both `INPUT_FIXED` and `USER_FIXED` as preserved routes (`isPreservedRoute()`), permitting center-line contacts. Ensure pipeline stages only set `SYSTEM_FIXED` and do not alter `INPUT_FIXED` items.
  - [x] Add `INPUT_FIXED` to `FixedState.java`.
  - [x] Update `Trace.isPreservedRoute()` / `Item.isPreservedRoute()` and contact logic in `Trace.java` and `PolylineTrace.java`.
  - [x] Update `Wiring.java` and `SesWriter.java` / `SesReader.java` to map `fix` wire/via to `FixedState.INPUT_FIXED`.
  - [x] Port `LockedTraceJunctionTests.cs` to `src/test/java/app/freerouting/board/LockedTraceJunctionTest.java`.
  - [x] Verify test suite passes.

- [x] **Task 1.3: SES Rational Corner Junction Synchronization (`SessionJunction`)**
  - **Origin Commits:** `41d888c4`, `5214f386`
  - **Problem:** In SES files, rational coordinates (from angled segments or neckdowns) can round differently on adjacent wire endpoints sharing a junction point. One wire rounds to grid while another restores exact rational, causing a sub-micron open junction (broken net connectivity upon reload, reproduced on `tinybot` and `TCKB`).
  - **Solution:** In `SesReader.java`, coordinate shared endpoints across wires on the same layer so that wires sharing a junction either all stay rational or all round to integer together.
  - [x] Coordinate wire endpoints in `SesReader.java` before board insertion.
  - [x] Port `SessionJunctionTests.cs` to `src/test/java/app/freerouting/io/specctra/SessionJunctionTest.java`.
  - [x] Verify junction tests pass.

- [x] **Task 1.4: Same-Net Conduction Area Obstacle Guard in DRC (`Via.isObstacle`)**
  - **Origin Commits:** `dfc91039`, `c57a8d5a`
  - **Problem:** In `Via.java` and `Item.java`, same-net conduction areas (copper graphics, unnetted/netted zone fills) were treated as zero-clearance obstacles, falsely rejecting legal same-net vias and reporting bogus DRC violations (e.g. `NRF52832_Touch_Switch`).
  - **Solution:** In `Via.isObstacle()`, treat same-net conduction areas as connected copper rather than zero-clearance obstacles.
  - [x] Update `Via.isObstacle` to exempt same-net conduction areas.
  - [x] Add regression test in `src/test/java/app/freerouting/board/SameNetConductionAreaViaTest.java`.
  - [x] Verify DRC calculations.

- [x] **Task 1.5: Non-Convex Pad Polygon Decomposition**
  - **Origin Commits:** `afd0ed51`, `428c8ff1`, `c4731021`
  - **Problem:** DSN padstack layers with non-convex polygon shapes (e.g. notched pads, L-pads) are read as convex hulls, closing notches where other nets could route and failing connection into notches.
  - **Solution:** Split non-convex pad polygons on a padstack layer into convex pieces of one pad (`<pin>#c<n>`).
  - [x] Implement non-convex pad polygon splitting in `Library.java` (padstack reader).
  - [x] Verified existing `NonConvexPadstackTest.java` suite passes cleanly with convex decomposition and notch clearance.
  - [x] Run unit tests.

---

### Phase 2: Design Rules & Clearance Enhancements

- [x] **Task 2.1: Physical Hole-to-Hole Minimum Spacing (`HoleSpacing` & `router.hole_to_hole_um`)**
  - **Origin Commits:** `8ceae4c6`, `4c2d7769`, `1374a242`
  - **Feature:** KiCad enforces a hole-to-hole clearance between drills (via drill to via drill, and via drill to pin drill) across any nets (and within the same net). Freerouting previously only checked copper clearance or default hole clearance against the default class.
  - **Solution:** Implement `HoleSpacing.java` and integrate into `BoardRules.java` and `DrillPage.java`. Enforce `board.getRules().getMinHoleToHole()` edge-to-edge distance between drilled holes. Same-net vias only require hole-to-hole spacing (no copper clearance within same net).
  - [x] Create `HoleSpacing.java` in `app.freerouting.rules`.
  - [x] Add `holeToHoleUm` to `RouterSettings.java`, `DefaultSettings.java`, and `BoardRules.java`.
  - [x] Update `ForcedViaInserter.java`, `DrillItemMover.java`, `MazeExpansionEngine.java`, and `Via.java` to use hole spacing checks.
  - [x] Port `SameNetSpacingTests.cs` to `src/test/java/app/freerouting/rules/SameNetSpacingTest.java`.
  - [x] Verify test suite.

- [x] **Task 2.2: Physical Exact Copper Clearance (`ExactCopper` & `router.exact_copper`)**
  - **Origin Commits:** `f4b15296`, `77f9e7a4`, `4ac76700`
  - **Feature:** In dense boards with fine pitches (0.4 mm VQFN, 0.75 mm BGA), conservative bounding octagons (which extend 8% past circles at 22.5°) cause false clearance collision rejections between circular vias, pads, and trace corners that are physically legal under Euclidean distance.
  - **Solution:** Implement `ExactCopper.java` (convex core + radius Euclidean distance calculation). In `ShapeSearchTree.java`, `Item.java`, `Pin.java`, and `Via.java`, use bounding octagons for rapid filtering, but perform exact Euclidean distance checks for overlapping octagons when `exact_copper` is enabled.
  - [x] Create `ExactCopper.java` in `app.freerouting.board.searchtree`.
  - [x] Add `exactCopper` setting to `RouterSettings.java` and `DefaultSettings.java`.
  - [x] Integrate exact copper checks in `ShapeSearchTree.java`, `Item.java`, `Pin.java`, and `Via.java`.
  - [x] Port `ExactCopperClearanceTests.cs` to `src/test/java/app/freerouting/board/ExactCopperClearanceTest.java`.
  - [x] Verify tests pass.

- [x] **Task 2.3: Via in SMD Pad Auto-Detection (`router.autorouter.via_in_pad`)**
  - **Origin Commits:** `KiCadViaInPad.cs`, `AutorouterSettings.cs`
  - **Feature:** KiCad boards often place vias in SMD pads when designer choices or board setup rules (`capping yes`, `filling yes`) allow it. If not explicitly set, auto-detect if the board already has vias in SMD pads of its net or via filling/capping is enabled, and enable `via_in_pad` / `via_touching_pad`.
  - [x] Implement via-in-pad auto-detection in `RoutingJobPreparation.java` and `HeadlessBoardManager.java`.
  - [x] Port `ViaInPadTests.cs` to `src/test/java/app/freerouting/autoroute/ViaInPadTest.java`.
  - [x] Verify tests pass.

---

### Phase 3: Advanced Autorouting Pipelines, BGA & Escapes

- [x] **Task 3.1: BGA Escape Routing & Ordering (`TwoLayerBga` & `BallEscapeOrder`)**
  - **Origin Commits:** `TwoLayerBga.cs`, `BallEscapeOrder.cs`, `BallFields.cs`
  - **Feature:**
    - `TwoLayerBga`: On boards with 2 signal layers and BGA components, enthusiastic eager fanout drops vias that block all layers. Auto-adjust fanout mode to `no_fanout` or `direct_first`.
    - `BallEscapeOrder`: When routing BGA balls in the initial pass, route ring-by-ring from the innermost ring outward (`inner_first`), ensuring interior pins secure exit channels while routing space is available.
  - [x] Create `BallFields.java`, `TwoLayerBga.java`, and `BallEscapeOrder.java` in `app.freerouting.autoroute.pipeline`.
  - [x] Add settings `ballEscapeOrder` and `twoLayerBga` to `AutorouterSettings.java` and `FanoutSettings.java`.
  - [x] Integrate into `RoutingPipeline.java` and `AutoroutePassRunner.java`.
  - [x] Port `TwoLayerBgaTests.cs`, `BallFieldsTests.cs`, and `BallEscapeOrderTests.cs`.
  - [x] Verify tests pass.

- [x] **Task 3.2: Fine-Pitch Pre-Routing Exit Stubs (`ExitStubs`)**
  - **Origin Commits:** `ExitStubs.cs`, `9dafd145`
  - **Feature:** Pins of fine-pitch ICs (0.4 mm VQFN) whose geometry exactly matches design rules leave no room for the router's standard clearance safety margin (1.6 µm) plus maze search clearance rooms. Pre-places straight exit stubs along pad centerlines (user-fixed during routing), allowing the maze search to connect cleanly at the stub end in open territory. Unused stubs are pruned after routing.
  - [x] Create `ExitStubs.java` in `app.freerouting.autoroute.pipeline`.
  - [x] Add `exitStubs` setting to `AutorouterSettings.java` and `DefaultSettings.java`.
  - [x] Integrate stub placement and release into `RoutingPipeline.java`.
  - [x] Port `ExitStubsTests.cs` to `src/test/java/app/freerouting/autoroute/ExitStubsTest.java`.
  - [x] Verify tests pass.

- [x] **Task 3.3: Keepout-Covered Pin Pad End Relocation (`PadEndWithClearStub`)**
  - **Origin Commits:** `2e82c181`, `779370eb`, `c8638505`
  - **Feature:** When a board keepout or cut-out partially overlaps a pad (e.g. `nan-15_top-plate`), the maze search connection stub can graze the keepout and get ripped up by strict DRC checks. Detect blocked stubs and reposition the pad connection point to an unobstructed region of the pad copper.
  - [x] Implement `padEndWithClearStub` in `FoundConnectionLocator.java`.
  - [x] Port `KeepoutCoveredPinTests.cs` to `src/test/java/app/freerouting/autoroute/KeepoutCoveredPinTest.java`.
  - [x] Verify tests pass.

- [ ] **Task 3.4: Dangling Copper Cleanup (`DanglingCopper`)**
  - **Origin Commits:** `306fd1b9`, `0a7d90fe`
  - **Feature:** When autorouting stops, times out, or finishes passes, leftover trace tails and one-layer dangling vias remain on the board. Run a multi-pass cleanup (`RoutingBoard.removeTraceTails()`) guarded by net connectivity checks before writing final session output.
  - [ ] Create `DanglingCopper.java` in `app.freerouting.autoroute.pipeline`.
  - [ ] Integrate into `RoutingPipeline.java` and `HeadlessBoardManager.java`.
  - [ ] Port `DanglingCopperTests.cs` to `src/test/java/app/freerouting/autoroute/DanglingCopperTest.java`.
  - [ ] Verify tests pass.

---

### Phase 4: Determinism & Clean Architecture Invariants

- [ ] **Task 4.1: Strict Time-Budget Agnostic Guard**
  - Confirm no wall-clock / elapsed-time decision heuristics are introduced.
- [ ] **Task 4.2: Settings System Nullability & Precedence Invariants**
  - Confirm all newly added fields in `RouterSettings`, `AutorouterSettings`, `FanoutSettings` are nullable reference types with defaults solely in `DefaultSettings.java`.

---

### Phase 5: Verification & Quality Gates

- [ ] **Task 5.1: Targeted Fast Unit Test Suite**
  - Run `./gradlew test --tests "Dac2020Bm01RoutingTest" -q` and all new tests.
- [ ] **Task 5.2: Full Code Quality Suite**
  - Run `./gradlew spotlessCheck checkstyleMain checkstyleTest`.
- [ ] **Task 5.3: Bounded Regression & Parity Verification**
  - Run `./gradlew test`.
