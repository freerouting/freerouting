# Clearance Violations Reduction Plan (Tier B Multi-Layer Focus)

**Document Status:** Approved Research & Implementation Plan  
**Date:** September 10, 2026  
**Primary Test Fixture:** `PCBench/FMCW_RADAR_Radar MCU/unrouted.dsn`  
**Secondary Test Fixtures:** `PCBench/1Bitsy_1bitsy/unrouted.dsn`, `PCBench/vhf-radio_exp-1/unrouted.dsn`, `PCBench/Vento_Vento/unrouted.dsn`  
**Target:** Eliminate the -10.9% clean-rate regression in Tier B, align copper-to-edge clearance with industry EDA/manufacturing standards, and deploy a Nudge-Before-Ripup local repair engine.

---

## 1. Executive Summary & Problem Context

In our overnight benchmark suite ([`scripts/benchmark/results/benchmarks.md`](file:///c:/Work/freerouting/scripts/benchmark/results/benchmarks.md)), Tier B clean (0 DRC) completion dropped significantly between historical v1.9 and current v2.5.0-RC1:

* **v1.9.0 Baseline:** 259 / 844 Clean (0 DRC) = **30.7%** (Average Score: 962.7)
* **v2.5.0-RC1 (Current):** 167 / 844 Clean (0 DRC) = **19.8%** (Average Score: 924.0)
* **Net Regression:** **-92 clean boards (-10.9%)**.

### Root Cause Diagnosis
Our investigations identified two distinct drivers of clearance violations:

1. **Pre-Existing Board Pad Violations (Edge Clearance Inflation):**
   In Freerouting, [`RouterSettings.copperToEdgeClearanceUm`](file:///c:/Work/freerouting/src/main/java/app/freerouting/settings/RouterSettings.java) was defaulted to **500.0 µm (0.5 mm)** in [`DefaultSettings.java`](file:///c:/Work/freerouting/src/main/java/app/freerouting/settings/sources/DefaultSettings.java). When [`DesignRulesChecker.getAllClearanceViolations()`](file:///c:/Work/freerouting/src/main/java/app/freerouting/drc/DesignRulesChecker.java) runs, it checks **all** board copper items against the `BoardOutline`.
   Consequently, pre-existing SMD connector pads, header pins, and edge-mount components placed by the board designer within 0.5 mm of the board edge are flagged as clearance violations **before routing even begins**.
   v1.9.0 never applied this 500 µm override (it respected DSN conductor clearance), creating an artificial regression in the benchmark scorecards.

2. **Algorithmic Routing & Fanout Violations:**
   True routing-introduced clearance violations occur when [`strictDrc`](file:///c:/Work/freerouting/src/main/java/app/freerouting/autoroute/pipeline/AutorouteConnectionRouter.java) is disabled (`false` by default). When maze ripup limits are reached in dense corridors, routes are accepted with violations. Furthermore, [`BatchFanout.java`](file:///c:/Work/freerouting/src/main/java/app/freerouting/autoroute/pipeline/BatchFanout.java) drops escape vias without a post-placement DRC check.

---

## 2. Hardware Engineering Practices: Copper-to-Edge Clearance

### Why Copper-to-Edge Clearance Matters in Fabrication
When a PCB panel is depanelized, the factory cuts along the board outline using:
* **CNC Routing (Milling):** A spinning milling bit (typically 1.6 mm to 2.4 mm diameter) cuts along the board edge. If copper is too close, the mechanical bit can cause burring, copper lift, tears, or exposed bare copper along the edge, risking oxidation or shorts to chassis/enclosures.
* **V-Scoring (V-Cut):** Circular scoring blades cut V-grooves on both sides. Due to mechanical blade runout and depth tolerances, copper near the groove risks internal delamination and copper smear.

### Industry Standard Benchmarks

| Source / Fabricator | CNC Routed Edge Minimum | V-Scored Edge Minimum | Recommended Standard |
| :--- | :---: | :---: | :---: |
| **IPC-2221 (Table 10-1)** | **0.25 mm (250 µm / 10 mil)** (Precision) | **0.50 mm (500 µm / 20 mil)** | 0.50 mm general, 0.25 mm tight |
| **JLCPCB** | **0.20 mm (200 µm / ~8 mil)** (2-4L) | **0.40 mm (400 µm)** traces / **0.50 mm** pours | 0.30 mm |
| **PCBWay** | **0.20 mm (200 µm)** (Std) / **0.15 mm** (Adv) | **0.40 mm (400 µm)** | 0.25 mm |
| **Eurocircuits** | **0.25 mm (250 µm)** | **0.45 mm (450 µm)** | 0.25 mm |
| **OSH Park** | **0.25 mm (250 µm)** | N/A | **0.38 mm (15 mil)** |
| **Standard KiCad / Altium Default DRC** | **0.25 mm – 0.30 mm** | **0.50 mm** | 0.25 mm |

### Conclusions for Freerouting:
1. **500.0 µm is justifiable ONLY for V-Scored panels**, which is rarely the default for individual prototypes or complex Tier B boards.
2. For CNC-routed boards (the vast majority of open-source KiCad designs in PCBench), **0.25 mm (250 µm) or 0.20 mm (200 µm)** is the true industry standard.
3. **Pads vs. Traces:** Real boards frequently have edge connectors (USB, SMA, PCIe, headers, castellations) where pads are placed between 0.10 mm and 0.25 mm from the edge (or 0 mm for card edges). A router must **never** report pre-placed designer pads as routing errors.

---

## 3. Empirical Findings on Primary & Secondary Fixtures

Using our fast headless DRC inspector on unrouted DSN fixtures, we determined the **exact threshold** of `router.copper_to_edge_clearance_um` where clearance violations drop to **0**:

### Fixture 1: `PCBench/FMCW_RADAR_Radar MCU/unrouted.dsn` (4 Layers, 0 Unrouted)
* **At 500 µm (Current Default):** **48 violations** (all BoardOutline vs. fixed SMD pads).
* **At 250 µm:** **12 violations**.
* **At 150 µm:** **4 violations**.
* **At 134 µm:** **4 violations** (min violation: 0.1 µm).
* **At $\le$ 133.9 µm (133 µm integer):** **EXACTLY 0 VIOLATIONS**.
* **Shortfall Range at 500 µm:** Minimum shortfall = 162.9 µm, Maximum shortfall = 366.1 µm ($500.0 - 366.1 = \mathbf{133.9\ \mu m}$).
* **Hardware Insight:** The MCU board has edge-mounted decoupling/filtering components whose pad edges are situated **133.9 µm (~0.134 mm)** from the board edge.

### Fixture 2: `PCBench/1Bitsy_1bitsy/unrouted.dsn` (4 Layers, 0 Unrouted)
* **At 500 µm (Current Default):** **45 violations**.
* **At 400 µm:** **18 violations**.
* **At 366 µm:** **5 violations** (min violation: 0.9 µm).
* **At $\le$ 365.1 µm (365 µm integer):** **EXACTLY 0 VIOLATIONS**.
* **Shortfall Range at 500 µm:** Minimum shortfall = 1.91 µm, Maximum shortfall = 134.9 µm ($500.0 - 134.9 = \mathbf{365.1\ \mu m}$).
* **Hardware Insight:** 1Bitsy is a compact dev-board with standard 2.54 mm pin headers along the edge. The header pads sit **365.1 µm (~0.365 mm)** from the board outline. Setting edge clearance to $\le 365$ µm eliminates all 45 violations.

---

## 4. Architectural Analysis: Strict DRC vs. Nudge-Before-Ripup

### 4.1 Why Not Always Use `strictDrc` from Pass 1?
Enforcing `strictDrc` unconditionally from the start of routing creates severe failure modes:
* **The "Pathfinder" / Negotiated Congestion Principle:** In Pass 1 (exploration), connections do not yet know the optimal global distribution of channels. If early connections strictly reject any clearance pinch and roll back, subsequent nets starve and fail to route.
* **Loss of Topological Memory:** Destroying a newly placed route leaves no congestion gradient for subsequent passes to avoid or push against.
* **The "Poisoned Footprint":** Connecting to an edge-mounted pin that carries pre-existing clearance tightness causes the wire to immediately fail strict DRC and be destroyed, making that net unroutable.

### 4.2 The "Nudge-Before-Ripup" Paradigm
Instead of treating strict DRC as an all-or-nothing guillotine that destroys entire 150 mm connections over a 5 µm clearance pinch, we introduce a **local repair hierarchy**:

```
[Route Placed with Clearance Violation]
                   │
                   ▼
       Step 1: Channel Width Feasibility Check
       - Is available_gap >= trace_width + 2 * clearance?
       - If NO: Cannot fit geometrically -> ABORT NUDGE, ESCALATE.
                   │
                   ▼ (YES)
       Step 2: Local Segment Nudge (Spring Relaxation)
       - Calculate shortfall vector: d_shortfall = expected - actual.
       - Translate violating segment away by d_shortfall + epsilon.
       - Recompute 45-degree corner intersections.
       - Verify no new DRC violations created.
                   │
                   ├──► SUCCESS: Keep Clean Route
                   │
                   ▼ (FAILED)
       Step 3: Via Wiggle (OptViaAlgo)
       - If violation involves a via, attempt local repositioning within padstay room.
                   │
                   ├──► SUCCESS: Keep Clean Route
                   │
                   ▼ (FAILED)
       Step 4: Bounded Micro-Reroute (Sub-Segment Ripup)
       - Rip up ONLY the 2 segments adjacent to the violation point.
       - Run local bounded A* search to bridge the gap through alternate room or layer.
                   │
                   ├──► SUCCESS: Keep Clean Route
                   │
                   ▼ (FAILED)
       Step 5: Full Net Ripup (Late Passes Only)
       - Rip up entire net and increment ripup costs for the next pass.
```

### 4.3 Practical Impact and Engineering Trade-Offs

#### Practical Wins:
1. **Dramatic Completion Rate Boost:** Long, complex traces are preserved instead of being discarded over minor corner pinches.
2. **Elimination of Maze Search False-Work:** Sub-millisecond $O(1)$ local repair replaces expensive $O(N \log N)$ global A* maze reroutes.
3. **Continuous Enforcement:** Nudging allows DRC checks to run safely at the end of **every pass**, massaging the board into legal geometry without stalling completion.

#### Engineering Challenges & Mitigations:
1. **Cascading Shoves (Domino Effect):** Bounded to **single-track non-cascading nudges**. If nudging Track A would push it into Track B, the nudge fails immediately and escalates to a micro-reroute rather than triggering recursive shoving.
2. **45-Degree Octilinear Rigidity:** Freerouting enforces `SnapAngle = 45`. Nudging a segment requires adjusting adjacent 45° diagonal segments and recomputing corners. If a segment is shorter than the shortfall, corner-normalization or micro-rerouting is used.
3. **Wasted CPU in Blocked Channels:** Guarded by an **Early-Abort Feasibility Check**; if physical channel room does not exist, nudging is bypassed instantly.
4. **Pre-Existing Pad Violations:** Nudging moves wires, not fixed pads. Fixed pad violations are solved separately by the Phase 2 boundary exemption.

---

## 5. Comprehensive Implementation Architecture

```
+-----------------------------------------------------------------------------------+
| Phase 1: Metric Separation & Telemetry Tracking                                   |
| - Record pre_existing_clearance_violations on DSN load.                           |
| - Track router_introduced_clearance_violations during and after routing.          |
+-----------------------------------------------------------------------------------+
                                          │
+-----------------------------------------------------------------------------------+
| Phase 2: Edge-Clearance Exemption for Fixed Items                                 |
| - Dynamic routing (new traces & vias) strictly obeys copperToEdgeClearanceUm.    |
| - Fixed DSN items (pads, pins) do not trigger outline DRC violations.             |
+-----------------------------------------------------------------------------------+
                                          │
+-----------------------------------------------------------------------------------+
| Phase 3: Default Edge-Clearance Calibration & Adaptive Fallback                   |
| - Recalibrate DEFAULT_COPPER_TO_EDGE_CLEARANCE_UM from 500 um to 250 um (or 200).|
| - If pre-existing pads violate edge clearance, clamp outline clearance adaptively.|
+-----------------------------------------------------------------------------------+
                                          │
+-----------------------------------------------------------------------------------+
| Phase 4: Nudge-Before-Ripup Engine & 2-Tiered Strict DRC                          |
| - Implement NudgeRepair: channel-check -> segment shift -> corner recompute.      |
| - Implement ViaWiggle: local via repositioning.                                  |
| - 2-Tiered Loop: Pass 1-2 soft DRC + nudge; Pass 3+ strict DRC + nudge-or-ripup.  |
+-----------------------------------------------------------------------------------+
                                          │
+-----------------------------------------------------------------------------------+
| Phase 5: Fanout DRC Verification & Full Benchmark Sign-Off                        |
| - Post-placement DRC checks in BatchFanout to revert bad escape vias.             |
| - Run full Tier B benchmarks (844 boards) to confirm clean rate >= 30.7%.         |
+-----------------------------------------------------------------------------------+
```

---

## 6. Automated Fast Testing Procedure (Agent / LLM Guide)

To ensure maximum testing throughput, all test commands must explicitly:
* **Turn ON:** `--router.fanout.enabled=true`, `--router.autorouter.enabled=true`
* **Turn OFF:** `--router.optimizer.enabled=false`, `--api_server.enabled=false`, `--mcp_server.enabled=false`, `--gui.enabled=false`

### Step 1: Instant DRC & Baseline Check (0 Passes, Runtime ~2s)
```powershell
java -jar build/libs/freerouting-current-executable.jar `
  --gui.enabled=false --api_server.enabled=false --mcp_server.enabled=false `
  --router.fanout.enabled=false --router.autorouter.enabled=false --router.optimizer.enabled=false `
  -de "scripts/benchmark/fixtures/PCBench/FMCW_RADAR_Radar MCU/unrouted.dsn" `
  -do "test_baseline.ses" `
  --router.copper_to_edge_clearance_um=500 `
  --router.result_json="test_fmcw_500um.json"
```

Parse the result:
```powershell
python -c "import json; d = json.load(open('test_fmcw_500um.json')); print(d['board_statistics']['clearance_violations'])"
```

### Step 2: Calibrated Clearance Verification (Runtime ~2s)
```powershell
java -jar build/libs/freerouting-current-executable.jar `
  --gui.enabled=false --api_server.enabled=false --mcp_server.enabled=false `
  --router.fanout.enabled=false --router.autorouter.enabled=false --router.optimizer.enabled=false `
  -de "scripts/benchmark/fixtures/PCBench/FMCW_RADAR_Radar MCU/unrouted.dsn" `
  -do "test_calibrated.ses" `
  --router.copper_to_edge_clearance_um=133 `
  --router.result_json="test_fmcw_133um.json"
```

Parse the result:
```powershell
python -c "import json; d = json.load(open('test_fmcw_133um.json')); print(d['board_statistics']['clearance_violations'])"
```

### Step 3: Fast Fanout + Autorouter Execution (Optimizer OFF, Runtime ~20–25s)
```powershell
java -jar build/libs/freerouting-current-executable.jar `
  --gui.enabled=false --api_server.enabled=false --mcp_server.enabled=false `
  --router.fanout.enabled=true --router.autorouter.enabled=true --router.optimizer.enabled=false `
  -de "scripts/benchmark/fixtures/PCBench/FMCW_RADAR_Radar MCU/unrouted.dsn" `
  -do "test_fmcw_ar.ses" `
  --router.autorouter.max_passes=2 `
  --router.copper_to_edge_clearance_um=133 `
  --router.result_json="test_fmcw_ar.json" `
  --logging.file.location="test_fmcw_ar.log"
```

Parse both routing completion and violations:
```powershell
python -c "
import json
d = json.load(open('test_fmcw_ar.json'))
ar = d['phases']['autorouter']['after']['board_statistics']
dur = d['phases']['autorouter']['duration_seconds']
print(f'Duration: {dur:.2f}s | Incompletes: {ar[\"connections\"][\"incomplete_count\"]} | Violations: {ar[\"clearance_violations\"][\"total_count\"]}')
"
```

### Step 4: Batch Evaluation Across Tier B Candidates (Runtime ~3–5 minutes)
```powershell
python scripts/pcbench/run_corpus_benchmark.py `
  --tier B `
  --max-boards 10 `
  --workers 4 `
  --version-label test-clearance-eval
```

---

## 7. Actionable Task List

- [ ] **Phase 1: Metric Separation & Telemetry Tracking**
  - [ ] Update `HeadlessBoardManager` to record `initialClearanceViolations` immediately after DSN parsing.
  - [ ] Add `preExistingViolations` and `routerIntroducedViolations` to `BoardStatisticsClearanceViolations.java`.
  - [ ] Update `run_corpus_benchmark.py` and `benchmarks.json` to expose both metrics.

- [ ] **Phase 2: Edge Clearance Exemption for Fixed DSN Pads/Pins**
  - [ ] Modify `Item.clearanceViolations()`: when one item is `BoardOutline` and the other is a pre-placed `Pin` or fixed copper pad, suppress the clearance violation if the pad is fully inside the board boundary.
  - [ ] Verify on `FMCW_RADAR_Radar MCU` (must drop from 48 to 0 at 500 µm).
  - [ ] Verify on `1Bitsy_1bitsy` (must drop from 45 to 0 at 500 µm).
  - [ ] Ensure that autorouted traces and vias still strictly observe the 500 µm clearance to the board outline.

- [ ] **Phase 3: Default Edge Clearance Recalibration**
  - [ ] Update `DefaultSettings.DEFAULT_COPPER_TO_EDGE_CLEARANCE_UM` from 500.0 µm to 250.0 µm (0.25 mm) to align with standard PCB CNC milling capabilities.
  - [ ] Update `docs/settings.md` and related tests to reflect the 250 µm default.

- [ ] **Phase 4: Nudge-Before-Ripup Engine & 2-Tiered Strict DRC**
  - [ ] Create `NudgeRepair` helper: early-abort width check, perpendicular shortfall translation, 45° corner adjustment.
  - [ ] Connect `NudgeRepair` to `AutorouteConnectionRouter`: invoke after connection placement before full net ripup.
  - [ ] Implement 2-tiered pass strategy in `AutorouteBatchLoop`: soft DRC + nudge in passes 1–2; strict DRC + nudge-or-ripup in passes 3+.

- [ ] **Phase 5: Fanout DRC Validation & Benchmark Verification**
  - [ ] Add post-placement DRC verification in `BatchFanout.java` to revert escape vias that create clearance violations.
  - [ ] Run `./gradlew test` to verify unit test suite.
  - [ ] Run `python scripts/pcbench/run_corpus_benchmark.py --tier B --workers 4` across all 844 Tier B boards.
  - [ ] Verify Tier B Clean (0 DRC) completion rate restores to $\ge 30.7\%$ (surpassing v1.9 baseline).
  - [ ] Run `./gradlew spotlessCheck checkstyleMain checkstyleTest` before final merge.
