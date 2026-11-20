# Zone-Enabled Benchmark Regression — Investigation & Fix Plan

**Status:** In Progress  
**Branch:** `fix/zone-regression-fixes`  
**Baseline:** Freerouting 2.6.0-RC16 (post-zone-grafting PR #1129) vs. 2.6.0-RC15 (pre-zone)

## 1. Regression Summary (RC15 → RC16, 1,142 Common Boards)

| Subset | Boards | Clean (RC15 → RC16) | Total Unrouted (RC15 → RC16) | Total Clearance Violations |
|---|---:|---:|---:|---:|
| All common | 1,142 | 967 → 623 (-344) | 988 → 5,852 (+4,864) | 1,671 → 1,605 (-66) |
| Zones added (`conduction_area_count` increased) | 969 | 822 → 479 (-343) | 889 → 5,758 (+4,869) | 1,335 → 1,265 (-70) |
| No zones added | 173 | 145 → 144 (-1) | 99 → 94 (-5) | 336 → 340 (+4) |

Note: `router.plane_as_obstacle` is already `false` by default; setting it to `true` turns every copper pour on signal layers into a hard obstacle, making routing dramatically worse (e.g., `kitspace_DIY_detector`: 1 unrouted / 0 violations → 32 unrouted / 47 pre-existing pin-to-obstacle violations).

## 2. Root-Cause Buckets (347 Clean → Unrouted Regressions)

| Bucket | Boards | Unrouted | Primary Mechanism |
|---|---:|---:|---|
| 1. 2L signal-pour fracturing | 215 | 1,229 | `PolylineTrace.normalize()` deletes same-layer bridge traces; `Item.getConnectedSetRecu()` never re-enters fractured `ConductionArea` |
| 2. KiCad copper-pour-only keepouts | 71 | 1,398 | `(keepout (tracks allowed) (vias allowed) (copperpour not_allowed))` exported as generic `(keepout ...)` blocking pads |
| 3. Power layer forced `routable = false` | 26 (+7 overlap = 33) | 261 | `(type power)` layer disabled by `applyBoardSpecificOptimizations()` |
| 4. 4L+ zone regressions | 19 | 83 | Fractured-plane / plane-routing issues on 4–6 layer boards |
| 5. Floating plane islands (early stop) | 8 (+1 overlap = 9) | 27 | `BatchAutorouter` skips pinless islands while `NetIncompletes` counts them |
| 6. Multi-small-zone (`<30%`) | 8 | 40 | Fractured small conduction areas hit Bugs #1 & #2 |
| **Total** | **347** | **3,038** | |

## 3. Tier T Canary Suite (22 Boards)

Target: < 30 min wall-clock (RC16 baseline: ~20.7 min single-threaded, ~5 min at 8 workers).

- **2L-fracture (6):** `cat-trainer_teensy_base_pcb`, `raspberrypi-3-usb-hub_usb_hub`, `kicad-guitar-preamp_Preamp-Instructables`, `kitspace_training_board_v02`, `uC3Moy_uC3Moy`, `atmel-programmer_atmel_programmer`
- **copperpour-keepout (4):** `LED_port-status_LED_port-status`, `LED_port-status_LED_port-status-CA-CC-combo`, `TPS63001-Breakout_TPS63001 Breakout`, `kitspace_flypi_v2`
- **floating-island (4):** `kitspace_DIY_detector`, `BB-PWR-8009_BB-PWR-8009_revA`, `miniboard-opamp_miniboard-opamp`, `PGA2311_pga2311`
- **power-layer-disabled (3):** `Kefersender_UKW TX`, `antdroid-board_antdroid-board`, `jadonk_PocketBone`
- **4L-zone (2):** `LoRaPP_loramod`, `arf154_arf154`
- **multi-small-zone (1):** `modular_bus_board_bus`
- **control-clean (2):** `1-Wire-Wing-pcb_1-Wire_Wing`, `boatcontrol_NonLatchingNO30A`

## 4. Fix Checklist & Progress Tracking

### Step 0: Tier T Update & Baseline
- [x] Update `"tier-t"` tags in `scripts/benchmark/fixtures/PCBench/catalog.json` and `metadata.normalized.json`
- [x] Record RC16 baseline on the 22-board Tier T (2/22 clean, 78 unrouted, 0 violations)

### Fix 1: Fractured-`ConductionArea` Graph Traversal & Bridge-Trace Preservation (Strongly Recommended)
- [x] `Item.getConnectedSetRecu()` re-enters fractured `ConductionArea`s so bridged islands expand transitively
- [x] `PolylineTrace.normalize()` preserves bridge traces across fractured `ConductionArea`s (only removes same-island cycles)
- [ ] Unit tests added and passing
- [x] Measured on Tier T: **2 → 12 clean (+10), 78 → 51 unrouted (-27), 0 violations, 231.5s wall-clock**

### Fix 2: KiCad Copper-Pour-Only Keepouts Exported as Generic `(keepout ...)` (Strongly Recommended)
- [x] `DsnFile.java`: detect unnamed board-level `(keepout "" ...)` enclosing routable SMD pin centers on that layer and remove with a `WARN` log
- [ ] Unit tests added and passing
- [x] Measured on Tier T (+ Fix 1): **2 → 14 clean (+12), 78 → 11 unrouted (-67), 0 violations, 232.8s wall-clock**

### Fix 3: Unify Floating-Island / Dead-Copper Semantics (Strongly Recommended)
- [x] Align `ConductionArea.isDeadCopper()` with `BatchAutorouter.coversAllPlanesOfNet()` so pinless floating copper fragments with no normal contacts are ignored consistently on both signal and power layers
- [ ] Unit tests added and passing
- [x] Measured on Tier T (combined with Fixes 4 & 5)

### Fix 4: Guard Against Disabling Layers on 2-Layer Boards / Outer Power Layers (Recommended)
- [x] `RouterSettings.applyBoardSpecificOptimizations()`: only force `!isSignal` layers to `routable = false` on inner layers (`0 < i < layerCount - 1`), preventing 2-layer boards from losing a routing layer
- [ ] Unit tests added and passing
- [x] Measured on Tier T (combined with Fixes 3 & 5)

### Fix 5: Guard `ViaOptimizer` Against Fractured Pours (Conditional / Secondary)
- [x] `ViaOptimizer.java`: do not treat a fractured `ConductionArea` as an unconstrained plane target during via optimization
- [ ] Unit tests added and passing
- [x] Measured on Tier T (combined with Fixes 3 & 4): **2 → 18 clean (+16), 78 → 6 unrouted (-72, -92.3%), 0 violations, 233.3s wall-clock**

## 5. Measurement Log (Tier T, 22 Boards, 8 Workers)

| Run | Clean (/22) | Total Unrouted | Total Violations | Wall-Clock | Notes |
|---|---:|---:|---:|---:|---|
| RC16 Baseline | 2 / 22 | 78 | 0 | ~5 min | From `benchmarks-2.6.0-RC16.jsonl` |
| + Fix 1 | 12 / 22 (+10) | 51 (-27) | 0 | 231.5 s | Fixed 10 boards incl. `cat-trainer`, `modular_bus_board`, `jadonk_PocketBone`, `LoRaPP_loramod`, `arf154` |
| + Fix 2 | 14 / 22 (+12) | 11 (-67) | 0 | 232.8 s | Fixed both `LED_port-status` boards (24→0, 16→0) |
| + Fixes 3, 4, 5 | **18 / 22 (+16)** | **6 (-72)** | **0** | **233.3 s** | Fixed `kitspace_DIY_detector`, `PGA2311`, `miniboard-opamp`, `kicad-guitar-preamp`; 0 control regressions |
