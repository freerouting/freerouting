# Research & Evaluation Plan: Evaluating `-oit` as Potential Router Default on PCBench

**Document Status:** Draft / Proposed Investigation  
**Date:** September 2026  
**Author:** Freerouting AI Assistant & Core Engineering Team  
**Corpus Target:** PCBench KiCad Ground-Truth Corpus (`C:\Work\PCBench` / `scripts/pcbench/`) + 24 Internal DSN Fixtures  

---

## 1. Executive Summary & Context

Analytics collected from headless agentic and automated batch executions reveal recurring usage of the optimizer tuning flag:
- `-oit 8.0` (frequently passed alongside reduced max-pass budgets in headless batch runs)

Before considering changes to the Freerouting defaults, we must:
1. **Analyze algorithmic impact:** Formulate hypotheses on how setting `-oit 8.0%` (vs current default `0.01` = `1.0%`) affects routing quality, completion rate, runtime, and via count.
2. **Establish an empirical verification plan:** Design a benchmark experiment using the [PCBench](https://github.com/PCBench/PCBench) ground-truth corpus and the automated benchmark gate (`scripts/pcbench/Run-PCBenchGate.ps1` / `Run-PCBenchCorpusBenchmark.ps1`) to confirm or deny whether adjusting this default improves routing outcomes.

---

## 2. Analysis of the Target Setting: `-oit` (`optimizer_improvement_threshold`)

#### Code Location & Semantics:
- Code reference: [`BatchOptimizer.java`](../../src/main/java/app/freerouting/autoroute/pipeline/BatchOptimizer.java#L183-L233), [`DefaultSettings.java`](../../src/main/java/app/freerouting/settings/sources/DefaultSettings.java#L135).
- Internal setting: `routerSettings.optimizer.optimizationImprovementThreshold` (type `Float`).
- CLI argument parsing:
  ```java
  routerSettings.optimizer.optimizationImprovementThreshold = Float.parseFloat(args[i + 1]) / 100;
  ```
  *(Passing `-oit 8.0` results in `0.08` internally; default is `0.01` which corresponds to `1.0%`).*

#### Algorithmic Mechanism in `BatchOptimizer`:
During route optimization passes (ripup-and-reroute passes after 100% completion or maximum autoroute stage), the optimizer evaluates the normalized board score before and after each pass:
```java
float scoreBeforePass = board.getStatistics().getNormalizedScore(job.routerSettings.scoring);
// ... optRoutePass(currentPass, withPreferredDirections); ...
float scoreAfterPass = board.getStatistics().getNormalizedScore(job.routerSettings.scoring);
double passImprovement = (scoreAfterPass - scoreBeforePass) / scoreBeforePass;

if (scoreImprovement != -1 && scoreImprovement < this.settings.optimizer.optimizationImprovementThreshold) {
    job.logInfo("Stopping optimizer because the improvement in this pass is below threshold...");
    break;
}
```

Furthermore, there is a boundary check against reaching near-maximum score (1000):
```java
if (scoreBeforePass * (1 + this.settings.optimizer.optimizationImprovementThreshold) >= 1000.0f) {
    break; // Current board score is already within threshold of perfection
}
```

#### Comparison: Default (`1.0%`) vs. Agentic User Pattern (`8.0%`):
| Metric / Characteristic | Default (`-oit 1.0` / `0.01`) | Aggressive Threshold (`-oit 8.0` / `0.08`) |
| :--- | :--- | :--- |
| **Early Termination** | Tolerates modest per-pass gains (1–7% improvement keeps optimizer running). | Exits early as soon as per-pass gain drops below 8%. |
| **Total Runtime / Wall Time** | Longer. Can run up to `maxPasses` (default 100) if each pass squeaks out 1.1% improvement. | Much shorter (often 50–80% reduction in optimization time). |
| **Via Count Reduction** | Maximized. Later passes shave off isolated vias. | Suboptimal via reduction. Leaves 5–15% more vias on dense boards. |
| **Trace Length Minimization** | Tighter pull-tight and trace straightening across multiple passes. | Slightly longer traces because ripup passes terminate early. |
| **DRC / Clearance Violations** | Identical (both preserve valid DRC state; passes that worsen DRC are rejected). | Identical (DRC safety is maintained). |
| **Use Case Fit** | Production / Final PCB manufacturing runs where quality & via count dominate compute cost. | Quick agentic feedback loops, CI sanity checks, rapid iterative prototyping. |

---

## 3. Hypotheses to Test on PCBench

* **Hypothesis 1 (Runtime vs. Quality Trade-off):** Raising `-oit` from `1.0%` to `8.0%` saves significant CPU time (~40–70% optimizer runtime) with minimal trace quality degradation (<3% wire length increase), but causes a measurable increase in via count (~5–12% more vias).
* **Hypothesis 2 (Diminishing Returns on Dense Boards):** On high-complexity boards (PCBench Tier 2 & Tier 3: 4-layer and 6-layer designs), passes beyond the first 5 passes rarely produce >8% score improvement per pass. Thus, `-oit 8.0` stops the optimizer prematurely before it can perform global ripup passes that resolve pin congestion.
* **Hypothesis 3 (Default Feasibility):** Setting default `-oit` to `8.0%` is too aggressive for an EDA production tool default, but an intermediate default (e.g. `2.0%` or `3.0%`) or an adaptive tier strategy might offer the optimal Pareto efficiency.

---

## 4. Empirical Evaluation Methodology (PCBench Corpus)

Freerouting has a dedicated benchmark harness integrated with the real-world KiCad [PCBench](https://github.com/PCBench/PCBench) dataset in `scripts/pcbench/`.

### 4.1 Test Corpus Composition
We evaluate across 3 stratified tiers of PCBench boards:
1. **Tier 1 (Smoke / Fast Gate - 2 to 4 layers, <100 nets):**
   - e.g., `1Bitsy_1bitsy`, `Dac2020Bm01`, simple microcontroller breakout boards.
2. **Tier 2 (Medium Complexity - 2 to 4 layers, 100–400 nets):**
   - e.g., IoT dev boards, audio amplifiers, motor controllers.
3. **Tier 3 (High Complexity - 4 to 8 layers, 400+ nets, BGA/dense SMD):**
   - e.g., multi-layer compute modules, dense FPGA carrier boards.

### 4.2 Benchmark Configurations to Compare
Run 4 automated matrix sweeps over the golden board set:
* **Run A (Baseline):** `-oit 1.0` (current default: `0.01`)
* **Run B (User Analytics Setting):** `-oit 8.0` (`0.08`)
* **Run C (Intermediate Candidate):** `-oit 3.0` (`0.03`)
* **Run D (Extended Optimization):** `-oit 0.1` (`0.001` - legacy precision mode)

*(All runs use fixed random seed / deterministic thread pool where applicable, with constant `maxPasses = 50`).*

### 4.3 Evaluation Metrics

For each run on each board, record:
1. **Routing Completion:** Net completion rate (Must be 100% or equal to baseline; no unrouted connections).
2. **Clearance Violations (DRC):** Must be 0 violations.
3. **Via Count:** Total vias on completed board compared to ground-truth original (`raw.kicad_pcb`).
4. **Total Wire Length (mm):** Total copper length compared to ground-truth original.
5. **Optimizer Passes Completed:** Number of passes executed before `-oit` triggered break.
6. **Execution Time (seconds):** Total wall-clock duration and optimizer-specific duration.
7. **Peak Heap Allocation (MB):** Live peak memory usage.

---

## 5. Execution Plan & Commands

The benchmark will be executed using the existing PowerShell scripts in `scripts/pcbench/`:

```powershell
# 1. Build current executable JAR
./gradlew.bat executableJar

# 2. Run PCBench Comparison Suite across candidate settings
pwsh -File scripts/pcbench/Run-PCBenchComparisonSuite.ps1 `
    -CandidateSettings @{ "optimizer.optimizationImprovementThreshold" = 0.08 } `
    -BaselineSettings  @{ "optimizer.optimizationImprovementThreshold" = 0.01 } `
    -MaxBoards 15 `
    -OutputDir "scripts/benchmark/results/oit_investigation"
```

### Automated Acceptance Criteria:
* To recommend `-oit 8.0` as the **new global default**:
  - Via count increase must be **< 2.0%** across the corpus.
  - Wire length increase must be **< 1.0%**.
  - Wall-clock runtime reduction must be **> 30%**.
* If via count increases by **> 5%**, `-oit 8.0` will **not** be made default, but may be documented or offered as a preset (e.g. `--preset=fast` or `--quick-route`).

---

## 6. Next Steps & Timeline

1. **Phase 1 (Harness Prep):** Verify PCBench fixture cache and ensure `pcbnew` export/import tools are ready on the runner.
2. **Phase 2 (Matrix Run):** Execute the 4 benchmark sweeps across the 15 stratified boards.
3. **Phase 3 (Data Synthesis):** Generate Pareto frontier charts (Runtime vs. Via Count) in markdown.
4. **Phase 4 (Decision):** Present empirical findings to maintainers to approve either:
   - Keeping `1.0%` as default and adding a `--fast` CLI flag / profile.
   - Adjusting default to a balanced value (e.g. `3.0%`).
