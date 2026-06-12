# Escape Congestion Zones & Fanout Redesign — Research & Plan
> **Status:** Planning document — no code changes yet.  
> **Author:** Antigravity (AI assistant), 2026-06-12

---

## TL;DR

The current Freerouting fanout is a component-sorted, pin-by-pin maze-router with no awareness of spatial congestion pressure. This works fine for sparsely placed boards but struggles with dense SMD/BGA designs. To make it robust, we need:

1. **Pre-route congestion estimation** (detect escape bottlenecks before routing begins)
2. **Congestion-aware pin ordering** (process the hardest pins while routes are still clear)
3. **Visualization** (user-facing heatmap or zone overlay)
4. **Dead-pin early exit** (stop wasting passes on permanently unroutable pins)

The key design question you raised — **rectangular zones vs heatmap** — has a clear answer from both industry practice and algorithmic theory: **heatmap is the right long-term architecture, but zones are faster to build and good enough for Phase 1.**

---

## 1. Current Fanout — Deep Code Analysis

### 1.1 Architecture Map

| Class | Role |
|---|---|
| `BatchFanout` | Orchestrates multi-pass fanout; owns `sorted_components` |
| `BatchFanout.Component` | Wraps a board component; sorts by **SMD pin count, descending** |
| `BatchFanout.Pin` | Wraps a board pin; sorts by **distance from component centroid, descending** (outermost first) |
| `RoutingBoard.fanout()` | Single-pin escape attempt via `AutorouteEngine` (maze router) |
| `AutorouteControl` | Per-attempt control: `is_fanout=true`, `remove_unconnected_vias=false`, ripup |
| `BatchFanout.EscapeStatistics` | Post-pass escape count: connected trace/via found, no clearance violations |
| `ShapeSearchTree` (MinAreaTree) | Spatial obstacle/clearance query tree — used *inside* maze routing, not for fanout ordering |

### 1.2 Pin Ordering — Current Strategy

```
sorted_components: TreeSet<Component>
  → Components ordered: most SMD pins → fewest SMD pins (biggest ICs first)
  
  For each Component:
    smd_pins: TreeSet<Pin>
      → Pins ordered: farthest from component gravity center → closest (outer-ring first)
```

**This is correct for a single isolated BGA** — outer rows first preserves inner escape channels.  
**It breaks down for adjacent dense components** — there's no cross-component spatial awareness. Two side-by-side QFPs each process their outermost pins first, pointing at each other, and they mutually block escape.

### 1.3 Key Gaps Identified in the Codebase

| Gap | Evidence | Impact |
|---|---|---|
| No congestion analysis of any kind | Grep for 'congestion', 'density', 'heatmap', 'cluster' returns zero results | Fundamental |
| No spatial indexing for pin ordering | `get_smd_pins()` is O(N) linear scan; called 3× at startup | Performance |
| No dead-pin detection | Permanently blocked pins retry every pass until `maxPasses` (20) | Wasted time |
| Static global time budget | `maxMillisPerPin * (passNo + 1)` — no adaptation to pin difficulty | Sub-optimal |
| Linear ripup cost escalation | `ripup_costs = startRipupCosts * (passNo + 1)` — grows globally not locally | Sub-optimal |
| No via placement guidance | Router places via at first feasible location | Poor via spread |
| `lastNotRoutedCount == 0` exit | Does not break if 1 pin is permanently blocked (seen in test logs) | Known bug (partially fixed by board-hash check) |
| `MazeSearchAlgo` fanout exit | Stops at first `ExpansionDrill` hit — minimal stub, no directional guidance | Blind to congestion |

### 1.4 What Fanout Actually Does (MazeSearchAlgo)

When `is_fanout = true`, the maze router:
1. Starts from the SMD pin's connected set.
2. Searches omnidirectionally (no directional bias).
3. **Stops the instant it finds one via (an `ExpansionDrill`)** — this is the escape.
4. The via is placed wherever the maze search happens to reach first.
5. A short stub trace connects pin → via.

This means the routing result is entirely determined by the maze cost function and obstacle layout — no strategic guidance toward lower-congestion areas.

> **Key insight:** The fanout has no directional preference. It routes "wherever fits first." Adding a congestion-aware directional bias is the highest-impact single algorithmic improvement.

---

## 2. Industry Research Findings

### 2.1 What Professional Tools Do

**Cadence Allegro X:**
- "Route Vision" real-time density overlay — computed *before* routing starts, updated live
- AI-driven fanout automation explores placement/fanout options
- Constraint-driven: high-speed nets locked first, remaining capacity allocated to others

**Siemens Xpedition:**
- "XtremePCB" — probabilistic density maps before full routing
- Identifies congested areas at placement stage, not routing stage

**Altium Designer:**
- Real-time DRC clearance envelopes while routing
- Interactive "hug-and-shove" with visual feasibility feedback
- Less sophisticated congestion scoring than Allegro/Xpedition

**Key takeaway:** All top tools treat congestion detection as a *pre-routing* problem. They flag bottlenecks during placement, before any traces are routed.

### 2.2 The RUDY Algorithm (Industry Standard for Pre-Route Congestion)

**RUDY = Rectangular Uniform Wire Density**

The dominant algorithm for fast pre-routing congestion estimation:
1. Divide the board into a uniform grid of "tiles" (g-cells).
2. For each net with bounding box area `A`: assume wire demand is uniformly distributed over `A`.
3. Each tile's demand = sum of all overlapping net bounding box contributions.
4. `congestion_score[tile] = demand[tile] / supply[tile]`
5. `supply[tile]` = number of routing tracks that fit given DRC rules: `floor((tile_width - clearance) / (trace_width + clearance))`
6. Tiles where score > 1.0 are "overflow" = congested.

RUDY is used in OpenROAD's `gpl` placer module (open source), Cadence's internal routers, and is the reference model in all modern congestion academic papers.

**For fanout specifically:** Instead of net bounding boxes, we can use SMD pin bounding boxes per component + cross-component proximity zones.

### 2.3 Academic Algorithms for Escape Routing

Evolution of academic approaches:

```
1. Lee / Maze routing (1961) — simple, O(N²), no congestion awareness
2. Max-flow / Min-cost flow (2000s) — models escape capacity as a flow problem
3. MMCF (Multi-commodity Min-cost Flow, 2009–2016) — ordered escape, prevents crossings
4. SAT-solver (ICCAD 2016) — hard constraints, multiple via types simultaneously
5. Genetic Algorithm (2020s) — non-crossing, capacity satisfaction
6. RL / MCTS (emerging) — pad-focused learning-based routing agents
```

The **Yan & Wong DAC 2009** paper showed earlier flow models were wrong for diagonal routing capacity — an important correctness note for our ShapeSearchTree which supports 45° routing.

**MCMCF-Router (ACM TODAES 2024):** Multi-capacity multi-commodity flow for grid/staggered pin arrays — scales to 2000+ pins with formal capacity guarantees.

### 2.4 G-Cell Overflow Metrics (from VLSI, directly applicable)

The canonical congestion metric:

$$\text{overflow}(c) = \max(0,\ D(c) - S(c))$$

Where:
- $D(c)$ = routing demand in cell $c$ (nets passing through)
- $S(c)$ = routing supply/capacity (tracks available)

Used metrics:
- **Total overflow** = $\sum_c \max(0, D(c) - S(c))$
- **Max overflow** = worst single cell (identifies critical hotspot)
- **Overflow ratio** = total overflow / total supply (normalized 0–∞)
- **Congested cell count** = number of cells where $D(c) > S(c)$

---

## 3. Rectangular Zones vs. Heatmap — The Debate

### 3.1 What Are We Choosing Between?

**Rectangular Zones:**
- A set of axis-aligned bounding rectangles, each labeled with a severity score.
- Derived from a simple spatial clustering of nearby SMD pins.

**Heatmap (G-Cell Grid):**
- A 2D grid of cells covering the board.
- Each cell has a numeric `congestion_score = demand / supply`.
- Rendered visually as a color gradient overlay.

### 3.2 Pros & Cons

| | Rectangular Zones | Heatmap |
|---|---|---|
| **Accuracy** | Coarse — rectangular borders create false in/out decisions | Fine — captures gradients, hotspot shape, directionality |
| **Directionality** | None — zones are just "congested" or not | Yes — you can read "go west to escape congestion" |
| **Implementation** | Simple — O(n log n) clustering | Moderate — grid setup + demand estimation + rendering |
| **Visualization** | Colored rectangles (easy to render) | Color gradient (requires rendering pipeline) |
| **API/Logging** | Simple list of `{x, y, w, h, score}` | 2D array with coordinate mapping — harder to serialize |
| **Routing use** | Binary "am I in a zone?" | Continuous cost function input to maze routing |
| **Industry standard** | Not primary — used for constraints/blockages | Yes — all major EDA tools use heatmaps as primary |
| **False positives** | Yes — zone boundaries are arbitrary | Rare — cells reflect actual pin/wire density |
| **Cross-component** | Possible but awkward (merge zones) | Natural — gap cells just have no pins |
| **Composability** | Zones can be *derived from* heatmap | Cannot derive a heatmap from zones alone |
| **Development time** | Days | 1–2 weeks |

### 3.3 The Argument for Rectangular Zones

> "Get something useful working fast. A zone is better than nothing."

- A zone-based ECZ detector can be built in **a few days** with no GUI changes.
- It immediately solves the logging problem: before fanout, log "3 critical escape congestion zones detected" with coordinates.
- It gives the routing algorithm **a binary hint**: "this pin is in a congested zone → use more time and ripup."
- It's familiar to users: "the QFP area is congested" is intuitive.

### 3.4 The Argument for Heatmap

> "Build the right architecture once. Zones are a derived output, not the ground truth."

- The heatmap is strictly more expressive — you can derive zones from it, but not vice versa.
- For routing algorithm decisions (which direction to route?), you need a gradient, not a binary in/out.
- For visualization, a color gradient overlay looks far more professional than colored rectangles.
- The RUDY algorithm is well-understood, well-tested, and can be implemented correctly.
- Industry tools without heatmaps (pre-2010 era) are the tools users complain about; the ones with them are industry-leading.

### 3.5 Recommendation: Hybrid (Heatmap as Core, Zones as Derived Output)

```
                    ┌─────────────────────────┐
                    │   EscapeCongestionGrid  │ ← computed once, pre-fanout
                    │   (g-cell heatmap)      │
                    └───────────┬─────────────┘
                                │
              ┌─────────────────┼────────────────────┐
              ▼                 ▼                    ▼
    ┌──────────────┐   ┌───────────────┐   ┌─────────────────────┐
    │ Zone overlay │   │  API/logging  │   │ Routing cost bias   │
    │ (GUI canvas) │   │  (JSON list)  │   │ (directional hints) │
    └──────────────┘   └───────────────┘   └─────────────────────┘
```

**For Phase 1 (quick win):** Skip the full heatmap. Build a simple pin-density zone detector using component bounding boxes and pin-count / estimated-channels scoring. This is fast to build, immediately useful, and lays the groundwork.

**For Phase 2 and beyond:** Replace the simple zone detector with a proper RUDY-style heatmap. The zone derivation code doesn't change.

---

## 4. Proposed Fanout Algorithm Improvements

### 4.1 Dead-Pin Early Detection (Highest ROI)

**Problem:** 1 permanently unroutable pin causes 17 extra passes (as seen in test logs).

**Solution:** After each pass, for every pin that failed, estimate whether the failure was due to permanent geometric impossibility:
- Count the number of routing tracks available around the pin (using `ShapeSearchTree.overlapping_items_with_clearance` in a window around the pin).
- If `available_tracks == 0`, mark the pin as `PERMANENTLY_BLOCKED`.
- Skip it in all future passes.
- Log: `"Pin U27-A1 cannot be escaped: 0 routing channels available within 2× pitch. Consider adjusting clearances or trace width."`

This is the **lowest-risk, highest-impact** change. Zero routing behavior change for escapable pins.

### 4.2 Congestion-Aware Zone Pre-Analysis (ECZ Detection)

Before the first fanout pass:
1. For each component with SMD pins, compute its bounding box (padded by 1× via pitch).
2. Estimate escape channel count: `channels = perimeter / (trace_width + clearance) × layer_count`
3. Compute: `zone_score = smd_pin_count / channels`
4. If `zone_score > 0.8`: ECZ detected.
5. Log structured report + optionally visualize.

This is pure analysis — no routing behavior change in Phase 1.

### 4.3 Congestion-Aware Pin Ordering

Replace the current `component_pin_count DESC` ordering with a composite ordering:
```
Primary:   zone_congestion_score DESC   (hardest escape zone first)
Secondary: component_smd_pin_count DESC (within zone, biggest component first)
Tertiary:  pin_distance_to_centroid DESC (within component, outer pins first)
```

**Why hardest zone first?** When routes are still clear (early passes), the most congested pins have the most escape options. By the time you get to less congested areas, the board is partially routed but those areas are simpler.

**Risk level:** Medium. This changes routing order and will affect results. Requires regression testing vs v1.9 baseline using `compare-versions.ps1`.

### 4.4 Directional Escape Bias

The most powerful algorithmic improvement — but also the most complex:

For each pin being fanouted, compute which direction has lower congestion using the heatmap:
```java
double[] dirWeights = heatmap.getEscapeDirectionWeights(pin); // [N, NE, E, SE, S, SW, W, NW]
```

Pass these weights to `AutorouteControl` as modified preferred-direction costs:
- Low congestion direction → lower cost → maze router prefers routing there
- High congestion direction → higher cost → maze router avoids routing there

This requires a new field in `AutorouteControl` (e.g., `double[] spatialDirectionCostFactors`) and modification of `MazeSearchAlgo` to apply these factors when computing expansion costs.

**Risk level:** High. Requires careful testing. Implement in Phase 5.

### 4.5 Zone-Based Time Budget

Instead of `timeLimit = baseMillisPerPin * (passNo + 1)` uniformly:
```java
double zoneFactor = zone != null ? zone.congestionScore() : 1.0;
long timeLimit = (long)(baseMillisPerPin * (passNo + 1) * Math.max(1.0, zoneFactor));
```

Pins in highly congested zones get more time; pins outside zones get the standard budget.

### 4.6 Localized Ripup Strategy

Instead of `ripup_costs = startRipupCosts * (passNo + 1)` globally:
- In congested zones: allow higher ripup costs *earlier* (pass 1 already allows some ripup).
- Outside congested zones: keep ripup costs high (protect already-good routes).

```java
int baseRipup = settings.get_start_ripup_costs() * (passNo + 1);
int effectiveRipup = inCongestionZone ? baseRipup / 2 : baseRipup * 2;
```

(Lower value = cheaper ripup = more aggressive rerouting.)

### 4.7 Via Placement Guidance (Phase 4+)

After initial stubs are placed, run a cleanup pass:
- For each fanout via in an ECZ, try to move it further from the component (toward lower-congestion cells).
- Reuse existing `OptViaAlgo.opt_plane_or_fanout_via()` with zone-aware target positions.
- Mark fanout-placed vias with a special tag so the optimizer knows they're candidates.

---

## 5. Open Questions for Your Decision

> [!IMPORTANT]
> These are the key design decisions that need your input before implementation starts.

### Q1: Phase 1 Scope
**Option A — Minimal (detection + logging only):**  
Build `EscapeCongestionAnalyzer` that logs ECZ warnings before fanout. No GUI, no routing change. Safe to ship quickly.

**Option B — Medium (detection + dead-pin fix + congestion-aware ordering):**  
Build analyzer AND apply the improved ordering AND add dead-pin detection. Routing changes, needs regression testing.

**Option C — Full (detection + all improvements + visualization):**  
Build everything including heatmap and GUI overlay. Multi-week effort.

### Q2: Zone vs Heatmap for Phase 1

**Option A — Start with simple rectangular zones derived from component bounding boxes.** Faster. Can graduate to heatmap later.

**Option B — Build RUDY-style heatmap from the start.** More upfront work but the right long-term architecture.

### Q3: Inside-Out vs Outside-In within ECZ

This is actually a subtle correctness question:

Current behavior: "farthest from component center" pins route first.  
For a single BGA: this = outer row first = correct.  
For two adjacent components: each routes its outermost pins toward the other, creating mutual blockage.

**Should we change the ordering to "toward the nearest board edge, not toward the component center"?** This would naturally route pins toward the board perimeter rather than toward adjacent components.

### Q4: Cross-Component Zone Grouping

**Option A — Keep zones per-component** (simpler, each component has its own ECZ if it meets the threshold).

**Option B — Merge nearby component zones** into shared ECZs (matches reality — two adjacent QFPs share one escape corridor).

### Q5: Where Should ECZ Data Live?

**Option A — Computed fresh each time fanout starts** (no caching, always accurate).

**Option B — Computed once per board, cached in `BasicBoard`** (faster, but must be invalidated when board changes).

---

## 6. Phased Implementation Roadmap

### Phase 1: ECZ Detection & Logging
**Effort:** 1–2 days | **Risk:** None  
- New class `EscapeCongestionAnalyzer`
- New record `EscapeCongestionZone`
- Call from `BatchFanout.fanout_board()` before first pass
- Log: "N escape congestion zones detected (M critical, K high)"
- Add zone info to `FanoutRunSummary`

### Phase 2: Dead-Pin Early Exit
**Effort:** 1 day | **Risk:** Low  
- Detect permanently blocked pins after each pass
- Skip in future passes
- Log: "Pin X permanently blocked — skipping in future passes"
- Fixes the "1 unrouted pin × 17 wasted passes" problem

### Phase 3: Congestion-Aware Pin Ordering
**Effort:** 2–3 days | **Risk:** Medium (routing behavior change)  
- Sort `sorted_components` by `zone_congestion_score DESC`
- Regression test vs v1.9 baseline using `compare-versions.ps1`
- Gate behind a `FanoutSettings.orderByCongestion` flag initially

### Phase 4: Visualization (GUI Overlay)
**Effort:** 1 week | **Risk:** Low (no routing change)  
- Add heatmap or zone overlay to board canvas
- Toggle in a "Fanout Analysis" panel
- Show ECZ boundaries and severity colors

### Phase 5: Directional Escape Bias
**Effort:** 1–2 weeks | **Risk:** High (core algorithm change)  
- Build full RUDY heatmap
- Extend `AutorouteControl` with directional cost hints
- Modify `MazeSearchAlgo` to apply directional weights
- Extensive regression testing

---

## 7. Proposed Data Structures

```java
// Phase 1 record
public record EscapeCongestionZone(
    IntBox bounds,              // Bounding box in board coordinates
    int pinCount,               // SMD pins in this zone
    int estimatedChannels,      // Estimated escape channels
    double congestionScore,     // pinCount / estimatedChannels
    ZoneSeverity severity,      // CRITICAL (>2.0), HIGH (>1.0), MEDIUM (>0.5), LOW
    List<Pin> pins,             // All SMD pins in this zone
    String componentName        // Primary component (or "multi-component")
) {}

public enum ZoneSeverity { LOW, MEDIUM, HIGH, CRITICAL }

// Phase 2 heatmap
public class EscapeCongestionGrid {
    private final int cols, rows;
    private final double cellSize;       // in board units
    private final double[][] score;      // [col][row] = demand/supply
    private final IntBox boardBounds;

    public double getScore(int boardX, int boardY) { ... }
    public ZoneSeverity getSeverity(int boardX, int boardY) { ... }
    public double[] getDirectionWeights(int boardX, int boardY) { ... } // N,NE,E,...,NW
    public List<EscapeCongestionZone> deriveZones(double threshold) { ... }
}
```

---

## 8. Summary

### The Debate Verdict

**Use heatmaps.** Here's why:

1. The heatmap is the *generalization* — zones can always be derived from it.
2. For routing algorithm decisions (directional escape bias), only a heatmap provides the gradient needed.
3. Every serious EDA tool that handles dense boards uses heatmaps.
4. The RUDY algorithm is simple enough to implement in 2–3 days.
5. The visual result looks professional and directly actionable.

**But start with zones for Phase 1.** They're faster to build, immediately useful for logging and dead-pin detection, and the Phase 1 zones become the derived output of the Phase 2 heatmap anyway.

### The Most Important Single Fix

**Dead-pin early detection** (Phase 2) is the highest ROI change:
- Zero risk to routing quality for escapable pins.
- Eliminates the visible symptom (20 identical board-hash passes).
- Provides actionable user feedback: *which pin is stuck and why.*

### The Most Transformative Change

**Directional escape bias** (Phase 5) is what will make Freerouting genuinely competitive for dense BGA boards:
- Gives the maze router a "sense of direction" — route toward open space, not toward more congestion.
- This is what separates algorithms that "try and fail" from algorithms that "plan and route."
