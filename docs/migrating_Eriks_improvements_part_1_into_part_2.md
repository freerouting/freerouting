# Integrate migrate-eriks-improvements-part-1 into part-2

Part 1 (`migrate-eriks-improvements-part-1`, formerly `feat/port-csharp-fixes`, tip `9ad0be83b`) is an earlier port of Erik Gavriluk's freerouting.net work. It was benchmarked and left unmerged. Part 2 (`migrate-eriks-improvements-part-2`) ports a later slice of the same C# history and already sits on current `master`.

> [!NOTE]
> **Attribution:** The C# changes were written by **Erik Gavriluk of Experimental Television Center**. The Java ports on both branches should keep that credit.

## Why this is a merge, not a replay

Part 1 forked at `321e98f56`, which is 19 commits behind current `master`. Part 2 is current `master` plus its own 7 commits. A dry-run merge of part 1 into part 2 auto-merges the geometry kernel, Specctra, board/DRC, API/CLI/MCP, and optimizer/shove commits. It conflicts in 21 files.

Both branches independently added `RoutingFallback.java`, `ClearanceBaseline.java`, and `RoutingFallbackTest.java`. Part 1 also stops further fallback attempts when `fallback_max_seconds` elapses. Part 2 does not: a timeout may only stop the job, it must not choose or skip attempts. Part 2's fallback is the one that stays.

## Status

| Phase | Description | Status |
|---|---|---|
| **Phase 1** | Benchmark tooling only (`--timeout` for every tier, `+`/`-` workers, timeout summary table). Leave generated benchmark results and the RC2 jar behind. | **Completed** |
| **Phase 2** | Merge part 1. Accept auto-merged defect and performance commits. | **Completed** |
| **Phase 3** | Fallback cluster: keep part 2, then take only part 1 behavior that part 2 does not already have. Do not restore `fallback_max_seconds`. | **Completed** |
| **Phase 4** | Hand-review the other content conflicts. | **Completed** |
| **Phase 5** | Compile, unit tests, and a bounded compare. Part 1's old benchmark numbers do not carry over. | **Completed** |

## Phase 1 — Benchmark tooling

Source commits: `49bae0471`, `9ad0be83b`.

- [x] `scripts/pcbench/run_corpus_benchmark.py`: `--timeout` overrides every board's catalog budget. The merge brought the part 1 default `08:00:00`; this branch later sets it to `06:00:00`. `+` and `-` change the worker count while a run is in progress. Lowering the count drains a worker before the next task starts. A graceful stop exits once the in-flight workers finish, even when boards remain queued. The journal, lock, and tier order from current master stayed.
- [x] `scripts/benchmark/lib/MarkdownExporter.ps1`: `### Timeout Summary` table (version, total timeouts, fanout, auto-router, optimizer, job timeout budget).
- [x] `scripts/benchmark/run-benchmarks.ps1`: `$MaxTime` default `06:00:00`.
- [x] `scripts/pcbench/test_benchmark_store.py`: tests that arrived with the worker-scaling commit.
- [x] Generated `scripts/benchmark/results/*`, `website/benchmarks.html`, and `freerouting-2.6.0-RC2.jar` were not taken. `9d9e56581` is the nightly result snapshot and was left behind with them.

## Phase 2 — Merge the non-overlapping ports

Part 1 commits to accept when they auto-merge:

| Commit | What it carries |
|---|---|
| `45fc9fbe4` | Geometry kernel defects (FR-001..013, FR-065, FR-098) |
| `62e8d542d` | Specctra DSN/SES and via-rule defects (FR-030..FR-041, FR-066..FR-075) |
| `c4e6605bb` | Board model, connectivity, and DRC integrity |
| `05b4f3dfa` | Headless CLI, API, and MCP defects |
| `d55b9d1a0` | Maze search, parser, and optimizer lifecycle |
| `0e144b798` | Optimizer fruitless-pass abort, violation retention, chamfer staircase, SMD drill candidate |
| `5d640c8ac` | Item indexing, outline caching, and queries |
| `26b7039fa` | Spatial tree query buffering and trace shover cache |
| `54bf8e9e5` | Lightweight `OptimizerMetrics` |
| `9b2d29473` | Inactive target doors and net clearance on borrowed fanout vias (FR-080, FR-081) |

`9d9e56581` is a nightly benchmark result snapshot. Do not keep its generated reports.

## Phase 3 — Fallback cluster (keep part 2)

Conflicted files:

- `RoutingFallback.java` (add/add)
- `RoutingPipeline.java`
- `AutorouteBatchLoop.java`
- `BatchAutorouter.java`
- `AutorouteConnectionRouter.java`
- `AutorouteControl.java`
- `AutorouterSettings.java`
- `DefaultSettings.java`
- `RoutingFallbackTest.java` (add/add)

- [x] Resolved by keeping part 2. Part 1's copies of these files are the older pipeline: they drop start-necked routing, the repeated-pass-loop skip, ignored-pin fanout accounting, and the stop-reason classification.
- [x] Part 1 behavior that is only a wall-clock budget (`fallback_max_seconds`) was not restored. `via_in_pad` and `via_centre_of_gravity` are set by part 1's fallback list and read nowhere else, so they were not added.
- [x] `MazeSearchEngine.java` and `BatchFanout.java` auto-merged. `FanoutSettings.retryWithoutEscapeWindow` was declared twice after the merge; one declaration was removed. No `fallback_max_seconds` remains.

## Phase 4 — Other content conflicts

- [x] `BasicBoard.java`: kept `ruleBlockedPins` and `clearanceBaseline`.
- [x] `RoutingBoard.java`: kept part 2's net-tie and KiCad netless-pad paths. The pin scan copies `getPins()` first, from part 1, so assigning a net cannot modify the list being scanned. Kept part 2's `ClearanceBaseline` (border lines, not `TileShape.corner`).
- [x] `ShapeSearchTree.java`: kept the obstacle filter and part 1's query-buffer `finally` clear.
- [x] `StoppableThread.java`: `resetAutoRouterStopRequest` remains; `resetStopAutoRouterRequest` delegates to it so part 1's optimizer and tests compile.
- [x] `HeadlessBoardManager.java`: kept part 2's `connectNetlessPads` load path. Part 1's post-load code still stores `clearanceBaseline`.
- [x] `BoardStatisticsConnections.java`: kept the design-blocked count and part 1's comment.
- [x] `ClearanceBaseline.java`: kept part 2. Part 1 calls `TileShape.corner`, which this tree does not use.
- [x] Duplicate methods that auto-merge inserted twice were removed: `Pin.getNormalContacts`, `Pin.overlappingNettedPins`, `Item.assignNetNumbers`. The copies that record a net-assignment change and route through `copperOverlappingPins` stayed.

Generated files, taken from part 2:

- [x] `scripts/benchmark/results/benchmarks.json`
- [x] `scripts/benchmark/results/benchmarks.md`
- [x] `scripts/benchmark/results/benchmarks-chart-data.json`
- [x] `scripts/benchmark/results/normalized-scores.json`
- [x] `website/benchmarks.html`

## Phase 5 — Verification

- [x] `gradlew.bat test spotlessCheck checkstyleMain checkstyleTest` passed. Checkstyle reported existing warnings only.
- [x] `python scripts/pcbench/test_benchmark_store.py` passed (7 tests). `python scripts/i18n/extract-context.py --check` reported context up to date.
- [x] Bounded `scripts/tests/compare-versions.ps1` against v2.5.0 on `Issue508-DAC2020_bm01.dsn` (`max_passes` 1, `max_items` 40, optimizer off): both sides 120 unrouted, score 256.41, 0 clearance violations. Fallback did not run because the item limit is not a retryable stop. This does not replace a full corpus run, and part 1's earlier benchmark numbers do not carry over.
