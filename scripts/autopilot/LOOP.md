# Autopilot loop — agent operating manual

This document defines how any LLM coding agent runs the self-improving routing optimization loop for Freerouting.

## Hard rules (from AGENTS.md)

- Never relax ArchUnit strict rules in `ModuleBoundariesArchTest` or `SpecctraPackageArchTest`.
- Zero new clearance violations vs baseline (hard reject).
- Do not run `spotlessApply` automatically; use `spotlessCheck`.
- Do not stage or commit unless `Close-Experiment.ps1 -Accept` succeeds.
- v1.9 (`src_v19/`) is reference only — do not refactor it.
- Use `DesignRulesChecker.getAllClearanceViolations()` for violation counts, not incomplete outline counts alone.

## Lifecycle

1. `New-Experiment.ps1 -Hypothesis "..."` — worktree + experiment directory.
2. Implement hypothesis (code and/or `RouterSettings` changes).
3. `Invoke-Gates.ps1 -ExperimentId <id>` — G0 → G1 → G2 → (optional G3).
4. `Invoke-Evaluation.ps1 -ExperimentId <id>` — compare vs baseline → `verdict.json`.
5. `Close-Experiment.ps1 -ExperimentId <id> -Accept` or `-Reject`.

## Gates

| Gate | Command | Pass criteria |
|------|---------|---------------|
| G0 | `gradlew.bat test` + `executableJar` | All fast tests green, ArchUnit green |
| G1 | Canary fixtures ×3 | No violation regression; unrouted within 2× noise floor |
| G2 | Full 24-fixture benchmark | Lexicographic objective vs baseline |
| G3 | PCBench subset | KiCad DRC + wire metrics vs ground truth |

## Objective (lexicographic, median of n≥3 where configured)

1. Clearance violations — zero new (hard reject).
2. Unrouted — not worse than noise band.
3. Normalized score — higher is better.
4. CPU time — lower is better.
5. Peak heap — lower is better.

Single-fixture regression beyond 2× noise floor → reject even if average improves.

## Budgets

- Max experiment wall-clock: 4 hours (configurable via `-MaxWallClockHours`).
- Max experiments per nightly run: 3.
- Large boards (>500 nets): use `-MaxItems` from `fixtures/metadata.yaml`.

## Forbidden actions

- Disabling fanout/router/DRC to game metrics.
- Relaxing test assertions to force green.
- Committing directly to `master`.
- Running `spotlessApply` as cleanup.

## Parity debugging

If G1/G2 fails with behavioral divergence:

```powershell
.\scripts\tests\compare-versions.ps1 -de fixtures\Issue508-DAC2020_bm01.dsn
.\scripts\tests\raw-section-mismatch.ps1
```

## Failure triage

- OOM on a large fixture: halve the fixture set / apply `max_items` from `metadata.yaml` and retry.
- Repeated G0 failure: stop the nightly loop and file an entry under `docs/issues/`.
- PCBench conversion failure: log and skip that board; do not fail the whole corpus import.

## PCBench / KiCad

- PCBench clone: `C:\Work\PCBench` (`FREEROUTING_PCBENCH` override)
- KiCad 10 CLI: `C:\Program Files\KiCad\10.0\bin\kicad-cli.exe` (`FREEROUTING_KICAD_CLI` override)

## Files

- Baseline: `scripts/benchmark/baselines/baseline-manifest.json`
- Noise floor: `scripts/benchmark/baselines/noise_floor.json`
- Ledger: `experiments/experiments.jsonl`
- Report: `experiments/REPORT.md`
