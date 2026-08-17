# Freerouting Optimizer Unification Plan

**Document status:** Deferred follow-up to `refactor/restructure`  
**Date:** August 2026  
**Related roadmap:** [`code_structure_recommendations.md`](code_structure_recommendations.md)  
**Related settings:** [`../settings.md`](../settings.md)

## Purpose

Unify optimizer behavior across CLI, API, and GUI without accepting correctness, determinism,
logging, or performance regressions.

The restructuring roadmap deliberately preserves the current GUI/headless optimizer construction
drift. This document contains the larger follow-up required before the multi-threaded optimizer can
become the common implementation.

## Current baseline

- `DefaultSettings` enables the optimizer by default.
- CLI and API jobs use the single-threaded `BatchOptimizer`.
- GUI jobs may use `BatchOptimizerMultiThreaded` when the multithreading feature flag is enabled and
  `optimizer.maxThreads > 1`.
- `BatchOptimizerMultiThreaded` does not currently provide a proven equivalent of the
  single-threaded optimizer's ordering, tie-breaking, logging, or DRC behavior.
- `BatchOptimizer` is the correctness and performance baseline until this plan's gate passes.

## Goals

- Use the multi-threaded optimizer in CLI, API, and GUI.
- Make multi-threaded optimization deterministic for every supported settings combination.
- Preserve the single-threaded optimizer's user-facing log messages and formats.
- Demonstrate that `maxThreads = 1` does not regress performance relative to the single-threaded
  baseline.
- Remove or fold the single-threaded implementation only after the multi-threaded implementation
  has passed all parity and performance gates.

## Non-goals

- Do not turn on multi-threaded optimization for API or CLI jobs before the acceptance gate.
- Do not change router heuristics, geometry algorithms, or design-rule semantics as part of the
  optimizer migration.
- Do not retain two different production optimizer policies after the migration is accepted.

## Actionable work

### 1. Establish the reference contract

- [ ] Capture the single-threaded optimizer's current behavior on representative fixtures,
  including completion, via count, trace metrics, pass counts, timeout handling, and full
  `DesignRulesChecker.getAllClearanceViolations()` results.
- [ ] Inventory every single-threaded optimizer log message and format, including stage lifecycle
  messages, pass summaries, warnings, timeout messages, trace messages, counters, and timing
  fields.
- [ ] Treat the captured behavior and log output as the compatibility contract for the migration.

### 2. Make multi-threaded execution deterministic

- [ ] Define the deterministic contract: identical board state, resolved settings, and deterministic
  seed must produce identical board state, counters, metrics, ordered decisions, and logs
  regardless of worker scheduling or pool size.
- [ ] Add or derive a stable seed for settings that permit random item selection.
- [ ] Use stable item ordering, task creation, candidate reduction, tie-breaking, and result
  application. Never let worker completion order decide the winner.
- [ ] Add repeated determinism tests at `maxThreads` 1, 2, 4, and 8.
- [ ] Cover every supported board-update strategy and item-selection strategy, including random
  selection with its stable seed.

### 3. Preserve the logging contract

- [ ] Port every single-threaded optimizer log message and format to the multi-threaded path.
- [ ] Prevent worker scheduling from changing the order of user-facing log messages.
- [ ] Aggregate or serialize multi-threaded events before emitting them when necessary.
- [ ] Add golden or normalized log assertions covering wording, ordering, identifiers, counters,
  timing fields, and severity.

### 4. Unify construction and settings

- [ ] Replace GUI/headless optimizer selection with one canonical factory policy used by CLI, API,
  and GUI.
- [ ] Ensure every entry point resolves `DefaultSettings`, file settings, CLI/environment settings,
  GUI settings, and API job overrides through the same settings-merging contract after board load.
- [ ] Preserve the default optimizer policy: `optimizer.enabled = true`, with identical defaults for
  `maxPasses`, `maxItems`, scoring, ripup, selection, and update settings.
- [ ] Make null-settings behavior explicit so a directly constructed or partially initialized
  `RoutingJob` cannot silently disable optimization instead of receiving merged defaults.
- [ ] Ensure `optimizer.maxThreads` does not select a different implementation in one mode.
  Autorouter pass parallelism must remain distinct from optimizer worker parallelism.
- [ ] Keep GUI progress/rendering/SES callbacks and headless timeout/resource monitoring as adapter
  responsibilities. Verify that they do not change optimizer eligibility or settings.

### 5. Benchmark before and after migration

- [ ] Extend the existing `scripts/benchmark` harness where practical so runs are repeatable rather
  than manual.
- [ ] Benchmark CLI, API, and GUI with identical fixtures, resolved settings, JVM options, and
  environment metadata.
- [ ] Capture the single-threaded baseline before changing the default implementation.
- [ ] Compare the multi-threaded implementation at `maxThreads` 1, 2, 4, and 8.
- [ ] Record wall time, CPU time, peak heap, cumulative allocation, completion, trace/via metrics,
  pass counts, log output, and full DRC results.
- [ ] At `maxThreads = 1`, require no performance regression beyond a documented measurement-noise
  tolerance relative to the single-threaded baseline.
- [ ] At higher thread counts, require the intended throughput benefit or explicitly reject that
  configuration as unsupported.

### 6. Migrate and remove the duplicate implementation

- [ ] Extend cross-adapter fixture tests to compare CLI/API headless execution with GUI execution
  using the same resolved settings and the multi-threaded optimizer.
- [ ] Verify matching completion, via count, trace metrics, ordered decisions, log output, and
  complete DRC results.
- [ ] Make the multi-threaded implementation the default in all three modes only after all
  determinism, logging, correctness, and benchmark tests pass.
- [ ] Remove or fold the single-threaded implementation into the multi-threaded implementation
  after the gate passes.
- [ ] Leave one public optimizer behavior and one construction policy.

## Acceptance gate

This plan is complete only when:

- CLI, API, and GUI use the same multi-threaded optimizer implementation.
- The optimizer remains enabled by default.
- Repeated runs are identical for every supported settings combination and tested thread count.
- Single-threaded log wording and formatting are preserved, with deterministic ordering.
- No additional full-DRC violations are introduced.
- Routing completion and quality metrics match the reference behavior.
- `maxThreads = 1` is not slower than the single-threaded baseline beyond documented measurement
  noise.
- Higher thread counts meet the recorded performance target.
- The single-threaded implementation has been removed or folded into the canonical implementation.

