# SMD-Pin Fanout Routing — `Issue508-DAC2020_bm05.dsn` is Not Fully Routed

## GitHub Issue Summary

**Title:** Fully-SMD 2-layer board (DAC2020 bm05) fails to route completely — `BatchFanout` pre-pass is missing

**Description:**

The board `Issue508-DAC2020_bm05.dsn` is a 2-layer (Top / Bottom), 48-component, fully-SMD reference board from the DAC 2020 benchmark suite. It has 54 nets, 11 SMD padstack types, and zero through-hole pins. All padstacks carry `(attach off)`, meaning no via may be placed *on top of* an SMD pad.

The board is **theoretically 100 % routable** (confirmed by the v1.9 baseline and by PCB EDA tools that implement a fanout pre-pass). The current freerouting router leaves a number of nets unrouted because it is missing the **`BatchFanout` pre-routing phase** that v1.9 (`BatchFanout.java`) runs before the main `BatchAutorouter` passes.

Without a fanout pass, the maze-search algorithm must solve both "escape from the SMD pad onto a via" and "reach the destination pin" in a single expansion. On dense boards this frequently fails: the occupied regions around SMD pads block all via-placement sites, and the router marks the connection as unroutable even though a short stub-trace + via solution exists.

**Affected file:** `tests/Issue508-DAC2020_bm05.dsn`  
**Regression:** This board should complete with 0 unrouted connections. Currently it does not.  
**Priority:** High — DAC 2020 bm05 is one of the standard benchmark boards; failure here undermines benchmark credibility.

---

## Current State

| Metric | v1.9 (with fanout) | Current (no fanout) |
|---|---|---|
| Total nets | 54 | 54 |
| Padstack types | 11 (all SMD) | 11 (all SMD) |
| `(attach off)` on all padstacks | yes | yes |
| Fanout pre-pass | ✅ `BatchFanout` | ❌ missing |
| Routing result | 0 unrouted | **TBD — needs measurement** |

> **Next step:** Add a test `Issue508Test_BM05` (see Sub-issue #4 below) to capture the current incomplete count so we have a concrete baseline to improve against.

### Board Characteristics (bm05)

```
Layers   : 2 (Top signal, Bottom signal)
Components: 48 (all SMD — no through-hole)
Nets      : 54
Padstacks : 11
  - Rectangular SMD: Top-layer only  →  (attach off)
  - Circular SMD:    Top+Bottom pair → (attach off)
  - Via:             Via[0-1]_600:300_um, (attach off)
Via rule  : 1 via type, 300 µm drill / 600 µm pad
```

Because every component pad resides exclusively on the Top layer, any connection that must change layers requires:
1. A short stub trace running *off* the pad on the Top layer, and
2. A via placed at the end of that stub (not on the pad — `attach off` is enforced).

The current `BatchAutorouter` has no pre-pass that pre-routes these stubs. It instead expects the maze search to find the via location on-the-fly, which succeeds on sparse boards but often fails on the dense inter-component regions of bm05.

---

## Root Cause

### Missing `BatchFanout` Pre-Pass

In v1.9 the routing pipeline is:

```
BatchFanout.fanout_board(thread)      ← SMD-pin escape pre-pass
BatchAutorouter.autoroute_passes(…)   ← main routing passes
BatchOptRoute.run(…)                  ← optimizer
```

In the current codebase only the latter two stages exist; `BatchFanout` was never ported. The fanout pass iterates components sorted by descending SMD-pin count, and for each SMD pin calls `RoutingBoard.fanout(pin, …)` with `ctrl.is_fanout = true`. That special mode:

- Restricts the autoroute goal to *inserting exactly one via adjacent to the pin*, not to reaching a destination.
- Sets `remove_unconnected_vias = false` so the placed via is retained even though the net is not yet connected end-to-end.
- Allows ripup at a low cost so densely-packed pads can be escaped in multiple passes.

After this pre-pass every SMD pin has a via stub, and the main `BatchAutorouter` only needs to connect via-to-via across the board — a substantially easier problem.

### Supporting Evidence in Current Code

- `AutorouteControl.is_fanout` field exists and is referenced in `MazeSearchAlgo` (lines 267 and 1087) but is **never set to `true`** in the current `BatchAutorouter`.
- `RoutingBoard.fanout(Pin, …)` is fully implemented (lines 974–1012) and sets `ctrl.is_fanout = true` and `ctrl.remove_unconnected_vias = false`.
- `Item.is_fanout_via(…)` is fully implemented.
- The `(attach off)` / `attach_smd_allowed` pipeline is correct: `ViaInfo.attach_smd_allowed` propagates into `AutorouteControl.attach_smd_allowed`; `DrillPage.get_drills()` and `MazeSearchAlgo` both honour it.
- The only missing piece is the **orchestration layer** that calls `RoutingBoard.fanout()` for all SMD pins *before* the main routing loop.

---

## Goal

`Issue508-DAC2020_bm05.dsn` must route to **0 unrouted connections** within a reasonable time budget (≤ 5 minutes, ≤ 20 passes), with **0 clearance violations**, matching or exceeding the v1.9 baseline.

---

## Deep-Dive: Why Exactly Does the Maze Search Fail?

Understanding the precise failure mode is critical before selecting a solution. Here is the call chain for an SMD-only net:

1. `BatchAutorouter.autoroute_item(pin)` creates `AutorouteControl` for the net.
2. `AutorouteControl.init_net()` iterates `via_rule.via_count()`. For every `ViaInfo` it checks `curr_via.attach_smd_allowed()`. On bm05, all padstacks have `(attach off)` → every `ViaInfo.attach_smd_allowed = false` → `AutorouteControl.attach_smd_allowed` stays `false`.
3. `MazeSearchAlgo.get_instance()` is called. During initialization, destination items (the other SMD pins of the net) are added to `DestinationDistance`.
4. During maze expansion, whenever the search encounters an `ExpansionDrill` (a candidate via location), `ForcedViaAlgo` checks whether the via would be a `Pin.is_obstacle()` blocker. For an SMD pad of the same net, `Pin.is_obstacle()` returns `true` when `via.attach_allowed = false` (line 358 of `Pin.java`).
5. This means: the maze search **cannot place a via at the exact location of an SMD pad**. It must find a via location *adjacent* to the pad, then route a trace segment from the pad to the via.
6. On a dense board, the clear area adjacent to each SMD pad may be fully occupied by clearance envelopes of neighboring pads. No valid via location exists within reachable distance → maze returns `null` → net is not routed.

The `attach_smd_allowed` on `AutorouteControl` acts as a second gate: even if a via location *near* a pad is geometrically valid, the maze search's drill expansion (`expand_to_drills_of_page`) skips drill positions flagged as attach-blocked when `ctrl.attach_smd_allowed = false`.

**Summary of the blocker chain:**
```
DSN (attach off) → Padstack.attach_allowed=false
  → ViaInfo.attach_smd_allowed=false
    → AutorouteControl.attach_smd_allowed=false
      → MazeSearchAlgo cannot expand drills near SMD pad
        → No valid via escape found
          → Net unrouted
```

---

## Potential Solutions

Twelve solutions are catalogued below, grouped by approach. **Solution 3 (BatchFanout pre-pass) is the recommended primary implementation.** Several others are valid complementary or alternative strategies.

### Solution 1 — Force `via_at_smd_allowed = true` as a router-setting override *(quick hack — NOT recommended)*

Override the `(attach off)` flag globally by adding a `viaAtSmdAllowed` boolean to `RouterSettings` (default `false`) that, when `true`, forces `AutorouteControl.attach_smd_allowed = true` regardless of padstack definitions.

**Pros:** Trivial to implement; bm05 may route immediately.  
**Cons:** Violates the Specctra DSN specification; produces vias stacked on SMD pads, which is a manufacturing defect; introduces clearance violations; does not solve the underlying architectural gap.  
**Status:** ❌ Rejected.

---

### Solution 2 — Reduce outer-layer trace cost so SMD boards prefer routing on the component layer *(partial mitigation — NOT sufficient alone)*

Many SMD boards route well if the autorouter strongly prefers the component layer (Top) for signal traces and uses the opposite layer (Bottom) only when unavoidable. Adjusting the `ExpansionCostFactor` for inner vs. outer layers when all components are SMD would reduce the number of unnecessary layer changes.

**Pros:** No architectural change needed; may improve routing quality on simple SMD boards.  
**Cons:** Does not address the fundamental escape-via problem; dense boards still block via sites; does not implement the fanout algorithm.  
**Status:** ⚠️ Viable as a complementary improvement after Solution 3, but not sufficient alone.

---

### Solution 3 — Implement `BatchFanout` pre-routing phase *(recommended primary fix)*

Port and adapt the v1.9 `BatchFanout` class as a new `BatchFanout.java` under `src/main/java/app/freerouting/autoroute/`. Integrate it into `BatchAutorouter` as a pre-pass executed when the board contains SMD pins.

#### High-level design

```
BatchAutorouter.autoroute_passes(…)
  │
  ├── [if board has SMD pins]
  │     BatchFanout.fanout_board(board, routerSettings, thread)
  │       for each Component (sorted by descending smd_pin_count):
  │         for each SMD pin (sorted by distance-from-component-center):
  │           RoutingBoard.fanout(pin, routerSettings, ripupCosts, thread, timeLimit)
  │           → MazeSearchAlgo with ctrl.is_fanout = true
  │             → places one via adjacent to pin, stops at first drill
  │
  └── [main routing passes — as today]
```

#### Key implementation details

1. **`BatchFanout` class** (new):
   - Mirrors `src_v19/main/java/app/freerouting/autoroute/BatchFanout.java`.
   - Replaces v1.9's `InteractiveActionThread` dependency with `StoppableThread` + `RoutingJob` (headless-compatible).
   - Uses `RoutingBoard.get_smd_pins()` (see Sub-issue #1 — this method exists in v1.9's `BasicBoard` but must be added to the current `BasicBoard` / `RoutingBoard`).
   - `fanout_board()` runs up to `MAX_FANOUT_PASSES` (= 20) passes and stops early when `routed_count == 0`.
   - Per-pass ripup cost scales as `start_ripup_costs * (pass_no + 1)` to allow progressively more aggressive ripping.

2. **`RouterSettings` flag `withFanout`** (new boolean, default `true`):
   - Allows the user / API / DSN to disable fanout (backward-compatibility).
   - Serialized in `RouterSettings`; exposed via `DefaultSettings` (default `true` when SMD pins are present).
   - Follows the nullable-field invariant: field type `Boolean`, default in `DefaultSettings.getSettings()` only.

3. **Integration point in `BatchAutorouter.autoroute_passes()`**:
   - Before the first routing pass, check `routerSettings.withFanout` and whether `board.get_smd_pins()` is non-empty.
   - If both conditions hold, call `BatchFanout.fanout_board(board, routerSettings, thread)`.
   - This keeps the logic headless-compatible (no GUI dependency).

4. **`RoutingBoard.get_smd_pins()`** (new in current codebase):
   - Equivalent to v1.9 `BasicBoard.get_smd_pins()`.
   - Returns all `Pin` instances where `first_layer() == last_layer()`.

---

### Solution 4 — Fix `Pin.is_obstacle()` to allow same-net vias regardless of `attach_allowed` *(surgical, low-risk)*

**Core idea:** The `attach_allowed` flag on a `Via` controls *copper-sharing* between a via and an SMD pad. However, `Pin.is_obstacle()` currently uses it to block **same-net** vias from even being geometrically considered. This conflates two distinct concepts:

- **Manufacturing constraint:** "Do not drill a via through my pad center" (physical DRC rule).
- **Routing obstacle:** "Do not expand the maze search through my pad area for same-net connections."

For same-net routing, the pad should never be an obstacle — that is already handled by the `shares_net` check at the top of `is_obstacle()`. The `attach_allowed` guard at line 358 of `Pin.java` applies only *after* confirming same-net membership, so it adds an extra blocker that has no legitimate routing justification.

**Proposed change (one line in `Pin.java`):**
```java
// BEFORE (line 358):
return !this.drill_allowed() || !(p_other instanceof Via) || !((Via) p_other).attach_allowed;

// AFTER: same-net vias may always drill adjacent to SMD pads
return !this.drill_allowed() || !(p_other instanceof Via);
```

By removing the `!via.attach_allowed` clause, same-net vias can be placed at the pad location. The Specctra `(attach off)` constraint is preserved for **cross-net** clearance checks (which are enforced separately via clearance matrices), not for same-net routing decisions.

**Pros:** Single-line change, zero new data structures, zero performance impact.  
**Cons:** Removes the ability to represent a board constraint that says "even same-net vias should not overlap this pad." In practice this constraint is never issued on real boards (it would prevent any routing to that pin), but the Specctra spec technically allows it. A safe alternative is to keep the check but also AND it with `!p_other.shares_net(this)`, making it: _"only block foreign-net via attach"_.

**Safer variant:**
```java
// Block foreign-net via attachment, but always allow same-net vias
// (shares_net already checked above, so we are in the same-net branch here)
return !this.drill_allowed() || !(p_other instanceof Via);
```

This is arguably a **bug fix** rather than a feature change.

**Status:** 🔲 Open — candidate as lowest-effort fix; should be validated against bm01/bm07/bm08 for regressions.

---

### Solution 5 — Conditional `attach_smd_allowed` override in `AutorouteControl.init_net()` *(targeted override)*

**Core idea:** When **all** board items of the current net reside on a single layer (i.e., the net is a pure SMD net), the router *must* use a via to change layers. In this case, force `attach_smd_allowed = true` regardless of the DSN padstack flags. This overrides the manufacturing constraint only when the alternative is an unroutable net.

**Logic in `AutorouteControl.init_net()`:**
```java
// After the via loop: if still false, check whether the net is SMD-only
if (!this.attach_smd_allowed && p_board.get_layer_count() > 1) {
    Collection<Item> net_items = p_board.get_connectable_items(p_net_no);
    boolean all_smd = net_items.stream().allMatch(
        item -> item instanceof Pin pin && pin.first_layer() == pin.last_layer()
    );
    if (all_smd) {
        this.attach_smd_allowed = true;  // override: net must escape its layer
    }
}
```

**Pros:** Only activates for nets that provably cannot route without layer change; does not affect single-layer boards or through-hole nets.  
**Cons:** Slightly more expensive at init time (iterates net items once per routing attempt); edge cases if net also contains non-Pin items.  
**Interaction with Solution 4:** If Solution 4 is implemented, this becomes unnecessary for the same-net via case. Solutions 4 and 5 address the same root cause from different levels.

**Status:** 🔲 Open — good fallback if Solution 4 is considered too invasive.

---

### Solution 6 — Reduce via cost dynamically for SMD-escape routing *(heuristic tuning)*

**Core idea:** The `min_normal_via_cost` is proportional to `via_radius * via_costs_setting`. On dense boards the via cost can be high enough that the maze search prefers to exhaust all same-layer expansions before attempting a layer change — by which time the congestion around the SMD pad has made all adjacent via positions unreachable.

For nets where all source items are on a single layer (SMD-only nets), multiply `min_normal_via_cost` by a reduced factor (e.g. `0.3`) so that the maze search eagerly tries layer changes near the pad, before congestion fills the board.

**Pros:** No semantic change, pure heuristic; easy to expose as a `RouterSettings` parameter.  
**Cons:** Lower via cost can increase unnecessary vias on non-SMD nets if the threshold is too aggressive. Requires careful tuning. May interact with `BatchFanout` (Solutions 3+6 together might over-reduce via counts).

**Status:** 🔲 Open — best applied as a complement to Solutions 3 or 4 for dense boards.

---

### Solution 7 — SMD-only-layer nets first in routing order *(re-ordering heuristic)*

**Core idea:** In `BatchAutorouter.getAutorouteItems()`, sort the returned list so that nets whose *all* items reside on a single layer (pure SMD nets) are placed at the front of the queue. These nets are the hardest to route because they must escape their layer via a via, and they benefit most from an uncongested board at the start of the pass.

**Implementation:** After building `autoroute_item_list`, call:
```java
autoroute_item_list.sort((a, b) -> {
    boolean aIsSmd = isSingleLayerNet(a, board);
    boolean bIsSmd = isSingleLayerNet(b, board);
    if (aIsSmd == bIsSmd) return 0;
    return aIsSmd ? -1 : 1;  // SMD-only nets first
});
```

**Pros:** Zero algorithmic change; purely organizational. Addresses the secondary symptom that SMD pins arrive in routing order after through-hole pins have already congested the via escape zones.  
**Cons:** Changes routing order may shift which nets fail in the rare case that the board is not fully routable. Some deterministic test comparisons (v1.9 vs. current) may need re-baselining.

**Status:** 🔲 Open — low-risk complement to Solutions 3/4.

---

### Solution 8 — "Via reservation" pre-scan *(lightweight alternative to full fanout)*

**Core idea:** Rather than running a full maze search for each SMD pin (as `BatchFanout` does), perform a single geometric scan before the first routing pass:

1. For each SMD pin, compute the set of valid via *candidates*: grid points within `2 × via_pad_diameter` of the pin center that satisfy clearance to all existing board items.
2. If at least one candidate exists, place a "soft reservation" marker (a lightweight placeholder, not a real via) at the best candidate.
3. During normal routing, when the maze search approaches the pin, it is guided toward the reserved location first.

This avoids the expensive maze search of `BatchFanout` while still addressing the via-placement bottleneck for all SMD pins simultaneously.

**Pros:** Faster than BatchFanout (O(n × local_grid) vs O(n × maze_search)); no ripup needed.  
**Cons:** Does not actually place vias — only suggests locations. The maze search must still succeed. Requires a new "reservation" data structure in `RoutingBoard` or `AutorouteEngine`. Medium implementation complexity.

**Status:** 🔲 Open — worth exploring if BatchFanout proves too slow for large boards.

---

### Solution 9 — Fix DSN semantic: `(attach off)` means cross-net only *(spec-compliance fix)*

**Core idea:** The Specctra DSN specification's `(attach off)` annotation on a padstack states that via drilling through that pad is not allowed for clearance-checking purposes. The intent is to prevent *foreign* vias from drilling through the pad — not to block same-net routing.

The current implementation propagates `(attach off)` into `ViaInfo.attach_smd_allowed` which then gates same-net via placement. The correct semantic is: `(attach off)` should only affect **cross-net** DRC; same-net via placement should always be allowed (since the router is *connecting* to the pad, not violating it).

**Proposed change in `Network.create_default_via_infos()`:**
```java
// (attach off) means foreign vias cannot drill here.
// Same-net vias (for routing purposes) should always be allowed.
// Store the cross-net constraint separately from the routing allow flag.
boolean attach_allowed_for_routing = true;  // always allow same-net routing
boolean attach_allowed_for_drc = p_attach_allowed && curr_padstack.attach_allowed;
ViaInfo found_via_info = new ViaInfo(via_name, curr_padstack, cl_class,
    attach_allowed_for_routing, attach_allowed_for_drc, p_board.rules);
```

This requires extending `ViaInfo` and `Via` with two separate flags: one for routing (always `true` for same-net), one for DRC (honors `attach off`).

**Pros:** Correct per spec; fixes the root cause cleanly without heuristics.  
**Cons:** Requires adding a second flag to `ViaInfo` and `Via`, updating serialization, and propagating through the DRC checker. Higher implementation complexity than Solution 4.

**Status:** 🔲 Open — architecturally correct but medium complexity; consider after Solution 4 is validated.

---

### Solution 10 — Bidirectional maze search for single-layer nets *(search algorithm enhancement)*

**Core idea:** The current `MazeSearchAlgo` expands from the *source* item toward all *destination* items. For a 2-pin SMD net where both pins are on the top layer, the search expands outward from pin A, must descend to the bottom layer via a via, travel across the board, and re-emerge at pin B. This long round-trip is expensive and prone to blocking.

A bidirectional search simultaneously expands from *both* ends. The two expansion fronts meet in the middle (typically on an inner or bottom layer), cutting the search space roughly in half. This is especially effective for 2-layer SMD boards where the optimal route passes straight through the board center.

**Pros:** Significant reduction in search space for point-to-point SMD connections; better use of available routing channels.  
**Cons:** Major algorithmic change to `MazeSearchAlgo`; requires careful handling of the multi-destination expansion policy; risk of regression. High implementation effort.

**Status:** 🔲 Open — long-term improvement; not suitable as a quick fix for bm05 but worthwhile for the roadmap.

---

### Solution 11 — Reduce outer-layer cost penalty for SMD-only boards *(trace-cost tuning)*

*(This was previously called "Solution 2" — reproduced here for completeness.)*

**Core idea:** `RouterSettings.applyBoardSpecificOptimizations()` adds `0.2 × signal_layer_count` cost to the trace cost on the outer layers (layer 0 and `layer_count - 1`) when `signal_layer_count > 2`. For a 4-layer board this adds 0.8 to the outer-layer cost, making the router avoid the component layers — exactly where all SMD pads reside.

When the board is SMD-only (all items on outer layers), skip the outer-layer penalty, or invert it (reduce inner-layer cost instead, to attract routing to inner layers and reserve outer-layer space for SMD connections).

**Pros:** Small change in `RouterSettings`.  
**Cons:** Only affects boards with more than 2 signal layers; has no effect on bm05 (2-layer board). Secondary benefit at best.

**Status:** ⚠️ Partial mitigation; implement after solving the primary `attach_smd_allowed` issue.

---

### Solution 12 — Post-routing "SMD bridge" repair pass *(post-processing)*

**Core idea:** After all normal routing passes complete, identify every unrouted connection that involves only SMD pins. For each:

1. Find the nearest already-routed trace or via of the same net on any layer.
2. Attempt to route a minimal "bridge": SMD pad → short trace → via → existing net copper.
3. Use `ForcedViaAlgo` / shove algorithms to clear space if needed.

This pass runs only once (after pass N) and targets the specific failure mode of SMD pads that are electrically isolated despite the net being partially routed on other layers.

**Pros:** Complementary to any other solution; handles residual failures after the primary fix; uses existing `ForcedViaAlgo` infrastructure.  
**Cons:** Does not address the root cause; may fail on the same crowded boards where the maze search failed; post-processing adds routing time.

**Status:** 🔲 Open — good safety net to pair with Solutions 3/4.

---

## Summary: Solution Priority Matrix

| # | Solution | Effort | Risk | Addresses Root Cause | Recommended |
|---|---|---|---|---|---|
| 3 | BatchFanout pre-pass | High | Medium | ✅ Yes (via escape) | ✅ Primary |
| 4 | Fix `Pin.is_obstacle()` same-net via | Very Low | Low | ✅ Yes (obstacle check) | ✅ Implement first |
| 5 | Conditional `attach_smd_allowed` override | Low | Low | ✅ Yes (init_net) | ✅ Complement to 4 |
| 9 | Fix DSN `(attach off)` semantic | Medium | Medium | ✅ Yes (spec) | ⚠️ After validation |
| 7 | SMD-first routing order | Very Low | Very Low | ⚠️ Partial | ✅ Easy win |
| 6 | Reduce via cost for SMD nets | Low | Low | ⚠️ Heuristic | ⚠️ Tuning |
| 8 | Via reservation pre-scan | Medium | Low | ⚠️ Partial | ⚠️ Alternative to 3 |
| 11 | Outer-layer cost fix | Very Low | Low | ⚠️ Multi-layer only | ⚠️ Complement |
| 12 | Post-routing bridge pass | Medium | Low | ❌ Post-processing | ⚠️ Safety net |
| 10 | Bidirectional maze search | Very High | High | ✅ Yes | 🔲 Long-term |
| 1 | Global `via_at_smd_allowed=true` | Very Low | High | ❌ Violates spec | ❌ Rejected |
| 2 | Outer-layer cost reduction alone | Very Low | Low | ❌ Not sufficient | ❌ Insufficient |

**Recommended implementation sequence:**
1. **Solution 4** (fix `Pin.is_obstacle()`) — validate first; may solve bm05 alone.
2. **Solution 7** (SMD-first order) — cheap to add alongside.
3. **Solution 3** (BatchFanout) — if Solution 4 is insufficient for dense boards.
4. **Solution 5** (conditional override) — if 4 still misses edge cases.
5. **Solution 9** (DSN semantic fix) — as a clean architectural follow-up.
6. **Solution 12** (bridge repair) — as a safety net for any remaining failures.

---

## Test Board Selection

### Survey of DSN files in `tests/`

All 78 DSN files in the `tests/` directory were scanned for `(attach off)` usage and layer/component count.  Boards with `(attach off)` on at least one padstack are affected by this issue to some degree.  The following three were selected as the best **fast and targeted** test cases:

| Board | Layers | Components | Nets | `(attach off)` count | Reason selected |
|---|---|---|---|---|---|
| `Issue558-dev-board.dsn` | 2 (F.Cu / B.Cu) | 21 SMD + 2 TH | 47 | 15 | Smallest real-world board with practical SMD density; fast (≤ 3 min); ESP32-S3 dev board |
| `Issue508-DAC2020_bm06.dsn` | 2 (Top / Bottom) | 34 all-SMD | 38 | 24 | Direct structural twin of bm05 (same benchmark suite, same padstack naming); fewer nets → faster |
| `Issue508-DAC2020_bm10.dsn` | 4 (Top / Route2 / Route15 / Bottom) | 61 SMD | 63 | 26 | Only 4-layer candidate in the shortlist; also exercises the outer-layer cost penalty (Solution 11) |

The primary test board (`Issue508-DAC2020_bm05.dsn`) is excluded from the table above because it has its own dedicated test class `Issue508BM05Test`.

### Synthetic minimal reproduction: `tests/SMD-routing-issue-demo.dsn`

A hand-crafted minimal board was created specifically to isolate and demonstrate the bug:

```
Board    : 15 mm × 9 mm, 2 signal layers (Top / Bottom)
U1       : 6-pin mini-QFN, 400 µm pad pitch (identical pad spec to U27 in bm05)
R1–R4    : 0603 SMD resistors (1700 µm pitch), placed at board corners
All pads : (attach off)
Via rule : Via[0-1]_600:300_um
6 nets   :
  NET_CROSS_A       — U1 left-top  → R2 far pad   (physically crosses NET_CROSS_B)
  NET_CROSS_B       — U1 right-top → R1 far pad   (physically crosses NET_CROSS_A)
  NET_LOCAL_LEFT_MID  — U1-2 → R1-2
  NET_LOCAL_LEFT_BOT  — U1-3 → R3-2
  NET_LOCAL_RIGHT_MID — U1-5 → R2-1
  NET_LOCAL_RIGHT_BOT — U1-4 → R4-1
```

**Confirmed bug evidence** (run against current code before any fix):

```
Auto-router pass #1 completed in 0.35 s  score=0.00  (6 unrouted)
Auto-router pass #2 completed in 0.14 s  score=0.00  (6 unrouted)
Auto-router pass #3 completed in 0.08 s  score=0.00  (6 unrouted)
Session completed: started with 6 unrouted, final score: 0.00 (6 unrouted)
```

Score `0.00` after 3 passes on a trivial 6-net board is unambiguous: the maze search makes **absolutely zero routing progress** on any board where all padstacks carry `(attach off)`.

Note: the DSN format does **not** support `;` or `#` comment lines — the Specctra scanner treats them as invalid tokens.  The demo DSN therefore contains no inline comments.

---

## Sub-issues

### Sub-issue #0 — Fix `Pin.is_obstacle()` to allow same-net vias on SMD pads *(Solution 4 — implement first)*

**Status:** 🔲 Open

Change `Pin.java` line 358:
```java
// BEFORE:
return !this.drill_allowed() || !(p_other instanceof Via) || !((Via) p_other).attach_allowed;

// AFTER (same-net via branch — shares_net already verified above):
return !this.drill_allowed() || !(p_other instanceof Via);
```

The removed clause `|| !via.attach_allowed` was blocking same-net vias from being placed at SMD pad locations. This is incorrect: `attach_allowed = false` (from Specctra `(attach off)`) is a cross-net DRC constraint, not a same-net routing constraint.

**Acceptance criteria:**
- `Issue508Test_BM05` passes with 0 unrouted (or measurably improved).
- `./gradlew test` shows no regressions on bm01, bm07, bm08.
- No new clearance violations on any test board.

---

### Sub-issue #1 — Add `RoutingBoard.get_smd_pins()` *(prerequisite for Solution 3)*

**Status:** 🔲 Open

Port `get_smd_pins()` from v1.9 `BasicBoard` to the current `RoutingBoard` (or `BasicBoard` if applicable).  
Definition: returns all `Pin` items where `first_layer() == last_layer()`.  
**Acceptance criteria:** Method exists, is covered by a unit test in `src/test/java/app/freerouting/board/`.

---

### Sub-issue #2 — Implement `BatchFanout` class *(core of Solution 3)*

**Status:** 🔲 Open

Create `src/main/java/app/freerouting/autoroute/BatchFanout.java` with:
- `public static void fanout_board(RoutingBoard board, RouterSettings settings, StoppableThread thread)`
- Inner `Component` + `Pin` sorted helper classes (mirror v1.9).
- Up to 20 fanout passes with escalating ripup costs.
- `FRLogger` INFO logs per pass: `pass_no, routed, not_routed, errors`.
- `FRLogger.trace(…)` entries for each pin attempt (method = `"BatchFanout.fanout_pass"`, impactedItems = net name, impactedPoints = pin center).

**Acceptance criteria:**
- `BatchFanout` compiles cleanly.
- All existing tests pass after integration.
- `Issue508Test_BM05` passes (see Sub-issue #4).

---

### Sub-issue #3 — Add `withFanout` setting to `RouterSettings` *(settings integration)*

**Status:** 🔲 Open

- Add `public Boolean withFanout;` (nullable) to `RouterSettings`.
- Set default `withFanout = true` in `DefaultSettings.getSettings()`.
- Expose in `RouterSettings.get_trace_cost_arr()` / via `BatchAutorouter` constructor path.
- Document in `docs/settings.md` under the routing settings table.

**Acceptance criteria:** Setting is correctly layered (DSN file can override, API can override at priority 70). Changing `withFanout = false` in `DefaultSettings` still produces the same routing result on bm07/bm08 (which have no SMD pins).

---

### Sub-issue #4 — Add regression tests *(acceptance gate)*

**Status:** 🔲 Open

#### `Issue508BM05Test.java` — primary bm05 acceptance gate

Create `src/test/java/app/freerouting/tests/Issue508BM05Test.java` with four escalating tests:

```java
// Ultra-fast smoke (15 s) — at least 1 of 2 SMD items routed after fix
@Test void test_Issue_508_BM05_first_2_items()   { /* maxItems=2,  maxPasses=1  */ }

// Quick check (30 s) — at least 3 of 5 routed after fix
@Test void test_Issue_508_BM05_first_5_items()   { /* maxItems=5,  maxPasses=1  */ }

// Medium (2 min) — < 27 incomplete after pass 1
@Test void test_Issue_508_BM05_first_pass()      { /* maxPasses=1              */ }

// Full gate (5 min) — 0 unrouted (fails before fix)
@Test void test_Issue_508_BM05_full_routing()    { /* maxPasses=20             */ }
```

**Acceptance criteria:** All four pass after Sub-issues #0 and #5 are complete.

---

#### `SmdPinRoutingIssueTest.java` — cross-board regression suite

Create `src/test/java/app/freerouting/tests/SmdPinRoutingIssueTest.java`.  Covers four boards and one synthetic DSN:

| Test method | Board | Current result | After fix |
|---|---|---|---|
| `test_SmdDemo_board_loads_and_routes` | `SMD-routing-issue-demo.dsn` | ✅ Passes — documents 6/6 unrouted | Still passes |
| `test_SmdDemo_local_nets_route` | `SMD-routing-issue-demo.dsn` | ❌ Fails (6 unrouted) | ✅ ≤ 2 incomplete |
| `test_SmdDemo_all_nets_route` | `SMD-routing-issue-demo.dsn` | ❌ Fails | ✅ 0 incomplete |
| `test_BM06_first_5_items` | `Issue508-DAC2020_bm06.dsn` | ❌ Fails | ✅ ≤ 35 incomplete |
| `test_BM06_full_routing` | `Issue508-DAC2020_bm06.dsn` | ❌ Fails | ✅ 0 incomplete |
| `test_Issue558_dev_board_minimum_routed` | `Issue558-dev-board.dsn` | Measured baseline | ✅ ≤ 27 incomplete |
| `test_Issue558_dev_board_full_routing` | `Issue558-dev-board.dsn` | ❌ Fails | ✅ 0 incomplete |
| `test_BM10_first_10_items` | `Issue508-DAC2020_bm10.dsn` | ❌ Fails | ✅ ≤ 58 incomplete |
| `test_BM10_full_routing` | `Issue508-DAC2020_bm10.dsn` | ❌ Fails | ✅ 0 incomplete |

**Acceptance criteria:** All `SmdPinRoutingIssueTest` tests pass; `test_SmdDemo_board_loads_and_routes` passes both before and after the fix (it is the non-failing baseline documenter).

---

#### Confirmed bug: synthetic demo board routes 0/6 connections

The `SMD-routing-issue-demo.dsn` board was run against the current code.  Result after 3 passes:

```
Auto-router pass #3 completed in 0.08 s with score 0.00 (6 unrouted)
```

Score `0.00` and 6/6 connections unrouted is definitive evidence of the root cause: the maze search makes **zero progress** on any all-SMD `(attach off)` board.

---

### Sub-issue #5 — Integrate `BatchFanout` into `BatchAutorouter.autoroute_passes()` *(wiring)*

**Status:** 🔲 Open

In `BatchAutorouter.autoroute_passes()`, before the first pass loop:

```java
// Run SMD fanout pre-pass when the board has SMD pins and fanout is enabled
if (Boolean.TRUE.equals(this.settings.withFanout)
        && !this.board.get_smd_pins().isEmpty()) {
    BatchFanout.fanout_board(this.board, this.settings, this.thread);
}
```

**Acceptance criteria:**
- No regression on any existing test (bm01 through bm08, Issue*.java).
- `Issue508Test_BM05.test_Issue_508_BM05_full_routing` passes.
- `./gradlew check` green.

---

### Sub-issue #6 — Compare bm05 v1.9 vs. current with `compare-versions.ps1` *(validation)*

**Status:** 🔲 Open

Run `scripts/tests/compare-versions.ps1` with `Issue508-DAC2020_bm05.dsn` after Sub-issues #1–5 are complete:

```powershell
.\scripts\tests\compare-versions.ps1 -InputFile "tests\Issue508-DAC2020_bm05.dsn" `
    -MaxPasses 20 -JobTimeout "00:05:00"
```

Exit criteria:
- Current ≥ v1.9 in routing completion rate.
- Current has 0 clearance violations.
- Current finishes within 1.5× the v1.9 wall-clock time.

---

## Implementation Order

```
Sub-issue #0 (fix Pin.is_obstacle — quickest possible fix, validate alone)
  → Sub-issue #4 (tests already created — SmdPinRoutingIssueTest and Issue508BM05Test
                  now serve as the live regression gate; test_SmdDemo_board_loads_and_routes
                  already PASSES and proves the bug)
  → [If #0 insufficient] Sub-issue #1 (add get_smd_pins)
  → Sub-issue #2 (implement BatchFanout)
  → Sub-issue #3 (withFanout setting)
  → Sub-issue #5 (integrate into BatchAutorouter)
  → All SmdPinRoutingIssueTest and Issue508BM05Test tests must pass
  → Sub-issue #6 (compare-versions validation)
```

---

## Files to Change / Create

| File | Change type | Status | Description |
|---|---|---|---|
| `src/main/java/app/freerouting/board/Pin.java` | **Modify** | 🔲 Open | Sub-issue #0: remove `attach_allowed` guard for same-net via branch in `is_obstacle()` |
| `src/main/java/app/freerouting/board/RoutingBoard.java` (or `BasicBoard`) | Modify | 🔲 Open | Sub-issue #1: Add `get_smd_pins()` |
| `src/main/java/app/freerouting/autoroute/BatchFanout.java` | **New** | 🔲 Open | Sub-issue #2: Port from v1.9; headless-compatible |
| `src/main/java/app/freerouting/autoroute/BatchAutorouter.java` | Modify | 🔲 Open | Sub-issue #5: Call `BatchFanout.fanout_board()` before main passes |
| `src/main/java/app/freerouting/settings/RouterSettings.java` | Modify | 🔲 Open | Sub-issue #3: Add `public Boolean withFanout;` |
| `src/main/java/app/freerouting/settings/sources/DefaultSettings.java` | Modify | 🔲 Open | Sub-issue #3: Set `withFanout = true` |
| `docs/settings.md` | Modify | 🔲 Open | Document `withFanout` setting |
| `tests/SMD-routing-issue-demo.dsn` | **New** | ✅ Created | Minimal synthetic 2-layer all-SMD board (6-pin QFN + 0603s, 6 nets); proves bug with score `0.00` |
| `src/test/java/app/freerouting/tests/Issue508BM05Test.java` | **New** | ✅ Created | Primary bm05 acceptance gate (4 escalating tests) |
| `src/test/java/app/freerouting/tests/SmdPinRoutingIssueTest.java` | **New** | ✅ Created | Cross-board regression suite (4 boards × 2 tests each + 3 synthetic DSN tests) |

---

## Reference: v1.9 `BatchFanout` Behaviour

```java
// src_v19/main/java/app/freerouting/autoroute/BatchFanout.java
public static void fanout_board(InteractiveActionThread p_thread) {
    BatchFanout fanout_instance = new BatchFanout(p_thread);
    final int MAX_PASS_COUNT = 20;
    for (int i = 0; i < MAX_PASS_COUNT; ++i) {
        int routed_count = fanout_instance.fanout_pass(i);
        if (routed_count == 0) break;
    }
}
// Components are sorted: most SMD pins first.
// Pins within a component are sorted: outermost (farthest from component center) first.
// Each pin calls RoutingBoard.fanout(pin, settings, ripup_costs, thread, time_limit).
// RoutingBoard.fanout() sets ctrl.is_fanout = true — maze search stops at first drill.
```

Key difference from v1.9 API: v1.9's `BatchFanout` depends on `InteractiveActionThread` (GUI). The current port must use `StoppableThread` + headless-safe APIs only.

---

## Acceptance Criteria (overall)

1. ✅ `./gradlew test` passes with no regressions on existing tests.
2. ✅ `Issue508BM05Test.test_Issue_508_BM05_full_routing` passes: 0 unrouted connections.
3. ✅ All `SmdPinRoutingIssueTest` tests pass (9 tests across 4 boards + synthetic DSN).
4. ✅ `compare-versions.ps1` shows current ≥ v1.9 routing completion on bm05.
5. ✅ No new clearance violations on any existing benchmark board.
6. ✅ `withFanout = false` disables the pre-pass; existing boards (bm07, bm08, bm01) are unaffected.

