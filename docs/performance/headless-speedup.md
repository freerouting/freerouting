# Headless speed-up: profiling results, Rust/GPU feasibility, and changes

This page records why headless Freerouting runs are slow, what was changed to make them faster, how
the changes were verified, and whether rewriting parts in Rust (or on a GPU) is worth it. The goal
is faster routing for a command-line caller (the KiCad MCP server), which needs no GUI, REST API or
MCP endpoint. Changes the MCP server should make are in
[`docs/integrations/kicad-mcp-server.md`](../integrations/kicad-mcp-server.md).

## Where the time goes

### Across the benchmark corpus (v2.6.0 nightly results)

Phase share of wall time, from `scripts/benchmark/results/benchmarks.json`:

| Tier | Boards | Optimizer | Autorouter | Fanout | Other (start-up, I/O, statistics) |
|---|---:|---:|---:|---:|---:|
| A (simple 2-layer) | 452 | 67% | 8% | 3% | 22% |
| B (routine 2–4 layer) | 527 | 51% | 7% | 1% | 41% |
| C (complex) | 141 | 51% | 5% | 1% | 43% |

### On a real board from the KiCad MCP server

`BC_Player/route/top.dsn` has 4 layers, 357 components and 1,071 SMD pins. It was profiled with
JFR (`scripts/perf/jfr_inclusive.py`) over fanout and three autoroute passes:

| Inclusive CPU | Code |
|---:|---|
| 83% | `MazeSearchEngine.findConnection`: the maze search, one connection at a time |
| 37% | `AutorouteEngine.completeExpansionRoom`: building free-space rooms |
| 22% | `ShapeSearchTree` overlap queries (`MinAreaTree.collectOverlapsUnlocked`: 16% self) |
| 10% | `HoleSpacing` (hole-to-hole checks for every via candidate) |
| 9% | fanout |
| 2% | board statistics / hashing |

The optimizer is the same engine: 87% of optimizer CPU is
`BatchAutorouter.autoroutePassesForOptimizingItem` (re-routing one item at a time). The engine
allocates about 0.5 GB/s (`IntOctagon`, `IntPoint`, `Line`, `FloatPoint`...), and GC pauses are
negligible. The time is spent in pointer-chasing over the board's object graph.

### Fixed cost of every CLI run

Before this change, a headless run on a small board took about 6.3 s of process wall time for 3 s
of routing. The difference was:

* `Thread.sleep(1000)` in `AnalyticsBootstrap`. `Freerouting.main` always passed
  `synchronousDelay=true`, although the Javadoc says the delay is skipped in CLI mode.
* A synthetic CPU benchmark (`RuntimeEnvironment.measureCpuScore`, ~0.3 s) run at every start, only
  for logs and telemetry.
* The version check: creating an HTTPS client on the main thread, then a GitHub API call.
* At exit, the analytics job summary recomputes the board statistics (a full-board clearance check)
  and then waits up to 1.5 s for delivery.
* JVM start-up and class loading of the 66 MB JAR.

## Is a Rust (or GPU) rewrite the best use of effort?

**Not as a partial port, and not on a GPU.** The reasons are measurable:

1. **No isolated kernel is big enough.** The hottest method takes 16% of CPU, and it is a tree walk
   over Java objects that the rest of the engine mutates thousands of times per second. Moving it to
   native code means mirroring the search tree in native memory and syncing every insert and
   delete. Each query does only microseconds of work, so the JNI/FFM call and marshalling of shapes
   would cost as much as the query saves. The largest self-contained kernel
   (`FastPlaneConnectivityGrid`, plane fracture analysis) is about 5–15% on plane-heavy boards. Even
   an infinitely fast replacement would save at most that.
2. **The engine is one tightly coupled unit.** Maze search, expansion rooms, doors, drills, the
   shape search tree, exact integer/rational geometry, push-and-shove insertion, the trace tightener
   and the optimizer all work on the same mutable `RoutingBoard`. That is roughly 70k lines
   (`autoroute` + `board` + `geometry`). A Rust speed-up therefore means porting the whole engine and
   then re-establishing result parity, which this project has spent years tuning (see the parity
   rules in `AGENTS.md`).
3. **The algorithm has no data parallelism for a GPU.** Each connection is a best-first search with
   a priority queue over irregular free-space rooms, which are built on demand from the current
   board. The next connection depends on the copper the previous one inserted. GPU routers exist,
   but they are grid-based Lee/PathFinder designs: a different router with different quality, not a
   port of this one.

**What a Rust rewrite would take.** A standalone Rust router that reads DSN and writes SES would fit
the MCP server's use (it needs only the CLI). Expected gains come from a data-oriented design:
arena-allocated geometry instead of 0.5 GB/s of short-lived objects, a structure-of-arrays spatial
index, and `rayon` for the optimizer's independent candidates. A 2–3× single-thread gain plus
parallel optimizer scaling is plausible. The cost is a multi-month port of the ~70k-line engine, plus
a parity harness comparing SES output and DRC on the benchmark corpus. Without the existing tuning,
quality on hard boards would regress at first. The measured ceiling of the cheaper options below
should be used up before starting that project.

**What gives the most speed per hour of work, in order:**

1. Use v2.6 instead of v2.5 (3–4× per pass on the real board, with better completion).
2. Do not run redundant parallel copies of a deterministic computation (the MCP server's best-of-N).
   Spend those cores on the optimizer, which is deterministic at any thread count.
3. Remove the fixed start-up and exit cost (about 2 s per run).
4. Optimize the engine's hot loops in place, keeping output byte-identical (−13% per autoroute pass so far).

## Changes in this build

Every change keeps the routing result **byte-identical**: the same `.ses` file (SHA-256) as the
unchanged build, on every benchmark board, at every thread count tested.

| Change | Where | Effect |
|---|---|---|
| No 1 s analytics start-up sleep and no version check in headless batch runs (`-de` given, GUI off) | `Freerouting.java` | −1.0 s per run |
| CPU score measured lazily in headless batch runs | `GlobalSettingsBootstrap` | −0.3 s per run |
| Analytics job summary (statistics + 1.5 s flush) only when analytics are enabled | `CliRunner`, `FRAnalytics.isEnabled()` | −0.1–1.5 s per run |
| Direct-dispatch octagon/box intersection for tree walks; octagon bounds inlined into tree nodes | `ShapeTree`, `MinAreaTree`, `ShapeSearchTree45Degree` | autoroute pass −13% with the two rows below (−9% before the bounds were inlined) |
| Same-layer / ignored-object test before the obstacle test in room completion | `ShapeSearchTree45Degree.completeShapeUnlocked` | (in the −13%) |
| Incomplete expansion rooms in an insertion-ordered set (O(1) removal, same order) | `AutorouteEngine` | (in the −13%) |
| Fanout: unconnected-set walk for a TRACE-only value done only when TRACE is on | `BatchFanout` | fanout −3% |
| Optimizer: baseline board serialized once per pass, each candidate only deserializes it | `RoutingBoard.prepareCopies/copyOf`, `BatchOptimizer` | removes a lock all optimizer workers queued on |

Rejected after measurement:

* Retaining the autoroute database between connections (`-Dfreerouting.benchmark.retain_autoroute_database=true`):
  pass 1 is 1.5× faster on `top.dsn`, but leaves 173 instead of 130 connections unrouted.
* Replacing the maze `TreeSet` with a heap, and caching `Padstacks.getMaxDrillRadius()`: neither
  can be shown to be result-identical (duplicate handling; mutable drill radii) for gains of 1–3%.

## Results

All runs were on the reference machine (Ryzen 7 3700X, 8 cores / 16 threads, JDK 26). Another
Freerouting job was running at the same time, so the absolute times carry some noise.

### Code changes only (`scripts/perf/bench.py`, 14 boards, 4 workers, router and optimizer on 1 thread)

| Board | Base (s) | This build (s) | Speed-up | `.ses` |
|---|---:|---:|---:|---|
| DAC2020_bm01 | 328.3 | 309.1 | 1.06× | identical |
| rgb-led-v2 | 13.6 | 10.7 | 1.27× | identical |
| SmartLaserMiniShield | 13.7 | 10.7 | 1.28× | identical |
| RASPmini | 17.0 | 19.8 | 0.86× (noise) | identical |
| bldc-gimbal | 35.0 | 31.9 | 1.10× | identical |
| two-shift-registers | 16.7 | 12.0 | 1.39× | identical |
| linklayer_contact | 32.2 | 27.2 | 1.18× | identical |
| stm32-lpc | 61.6 | 61.2 | 1.01× | identical |
| 1-Wire_Wing | 18.8 | 16.0 | 1.17× | identical |
| I2C_Servo | 20.4 | 15.7 | 1.31× | identical |
| AmpOne | 54.7 | 49.7 | 1.10× | identical |
| arduino-led-driver | 114.8 | 103.3 | 1.11× | identical |
| guitar_fret | 72.7 | 66.3 | 1.10× | identical |
| Curryboard | 129.2 | 119.6 | 1.08× | identical |
| **Total** | **928.6** | **853.1** | **1.09× (geo-mean 1.14×)** | **14/14 identical, 0 unrouted, 0 DRC** |

### Large real board (`top.dsn`, fanout + 1 autoroute pass, paired runs with `scripts/perf/ab.sh`)

| | Fanout | Autoroute pass 1 | `.ses` |
|---|---:|---:|---|
| v2.5.0 (what the MCP server used) | 83.5 s | 245 s | (different router) |
| v2.6 unchanged | 23.4 s | 72.4 s | `49f944b8…` |
| This build | 22.3 s | 62.8 s (−13%) | `49f944b8…` (identical) |

### Settings and JVM options on top of the code changes

| Measure | Before | After | `.ses` |
|---|---:|---:|---|
| Small board (SmartLaserMiniShield), process wall time, start-up fixes | 6.3 s | 4.1 s | identical |
| Same, plus AOT cache (`-XX:AOTCache`) | 4.1 s | 3.4 s | identical |
| bldc-gimbal optimizer stage, 1 → 8 optimizer threads | 13.4 s | 5.1 s | identical |
| `top.dsn` pass, `-XX:+UseParallelGC -XX:+UseCompactObjectHeaders` | 75.9 s | 72.4 s | identical |
| `top.dsn` fanout, 1 vs 4 concurrent Freerouting processes | 20 s | 31 s | identical |

The last row shows why the MCP server's parallel best-of-N attempts, which here are identical
deterministic runs, slow every attempt down.

## Reproducing

```bash
# build
./gradlew executableJar
# whole board set, baseline vs candidate, same settings and worker count
python scripts/perf/bench.py run --jar base.jar --label base --out logs/perf/base.json --workers 4 -- --router.max_threads=1 --router.optimizer.max_threads=1
python scripts/perf/bench.py run --jar build/libs/freerouting-current-executable.jar --label cand --out logs/perf/cand.json --workers 4 -- --router.max_threads=1 --router.optimizer.max_threads=1
python scripts/perf/bench.py compare logs/perf/base.json logs/perf/cand.json
# one board, both JARs started together (same machine load)
bash scripts/perf/ab.sh base.jar cand.jar board.dsn logs/perf/ab --router.autorouter.max_passes=1 --router.optimizer.enabled=false
# where the CPU goes
java -XX:StartFlightRecording=filename=run.jfr,settings=profile -jar ...
python scripts/perf/jfr_inclusive.py run.jfr --top 60
```

The board set is `scripts/perf/boards.txt`: 2–4 layer PCBench boards that v2.6.0 routes cleanly,
spread over the tier A–C wall-time distribution.
