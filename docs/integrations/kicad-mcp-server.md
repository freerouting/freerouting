# Using this Freerouting build from the KiCad MCP server

This page lists the changes the KiCad MCP server (`python/commands/freerouting.py`) needs to get
the full speed-up of this build. Profiling, measurements and the reasoning behind each item are in
[`docs/performance/headless-speedup.md`](../performance/headless-speedup.md).

The MCP server drives Freerouting as a command-line process (`java -jar freerouting.jar -de board.dsn
-do board.ses ...`). The command-line contract is unchanged, so the server keeps working with this
JAR as a drop-in replacement. Items 1 and 2 bring most of the gain. The rest are smaller.

## 1. Use this build instead of Freerouting v2.5.0

The server defaults to `~/.kicad-mcp/freerouting.jar`, which on the reference machine was
Freerouting **v2.5.0**. v2.6 is a large step on real boards before any of this fork's changes.

Measured on `BC_Player/route/top.dsn` (4 layers, 357 components, 1,071 SMD pins):

| Stage | v2.5.0 | v2.6 (this fork) |
|---|---|---|
| Fanout | 83.5 s, 58% pins escaped | 22.3 s, 76% pins escaped |
| Autoroute pass 1 | 245 s, 149 unrouted | 62.8 s, 130 unrouted |
| After 3 passes | 116 s/pass avg., 163 unrouted | ~70 s/pass, 55 unrouted |

Build and install:

```powershell
cd D:\freerouting
.\gradlew.bat executableJar
Copy-Item build\libs\freerouting-current-executable.jar $HOME\.kicad-mcp\freerouting.jar
```

The JAR targets Java 25 (class-file major 69). `_jar_required_java()` already reads that from the
JAR, and `_find_java()` already finds the installed JDK 26.

## 2. Stop running best-of-N attempts that differ only in `max_passes`

`_autoroute_staged()` runs `attempts` processes in parallel. Each gets a different
`--router.autorouter.max_passes` from `DEFAULT_PASS_SCHEDULE` (50, 60, 65, ...) and all use
`-mt 1`. Freerouting 2.x is **deterministic**, and `max_passes` is only a stop condition
(`AutorouteBatchLoop`: `currentPass > maxPasses` → stop). So:

* An attempt capped at *k* passes repeats exactly the first *k* passes of an attempt with a higher
  cap. In the logs, pass 1 of a 1-pass run and pass 1 of a 3-pass run give the same board hash and
  score.
* When routing stops earlier on its own (everything routed, stagnation, no improvement), all
  attempts produce **byte-identical** `.ses` files.
* The N copies run at the same time and compete for memory bandwidth (the router allocates
  ~0.5 GB/s per process). With 4 concurrent runs, every run was 15–50% slower than alone.
* `-mt 1` makes the optimizer single-threaded. In v2.6 the optimizer is deterministic at any thread
  count: 1 and 8 threads give a byte-identical `.ses`. Eight threads make the optimizer stage
  2.6× faster.

Recommended change: one attempt with all cores for the optimizer:

```python
# _autoroute_staged(): replace the pass-schedule fan-out
attempts = 1          # (or keep N only when the attempts vary something that changes routing)
single_thread = False # let the optimizer use every core
```

If diversity is wanted later, vary settings that change the search, never only `max_passes`.
Examples are `--router.scoring.via_costs`, `--router.scoring.start_ripup_costs`, and
`--router.optimizer.item_selection_strategy`. Cap parallel attempts at about half the physical
cores, and give each attempt `--router.optimizer.max_threads=<cores / attempts>`.

## 3. Use current long-form settings instead of the deprecated `-mp` / `-mt`

`-mp N` and `-mt N` still work, but each prints a deprecation warning. `-mt 0` also *disables
the optimizer*. Replace them in `_build_freerouting_cmd()`:

```python
args = [
    java_exe, *jvm_flags, "-jar", jar_path,
    "-de", dsn_path, *(design_files or []),
    "-do", ses_path,
    "--gui.enabled=false",
    "--api_server.enabled=false",
    "--usage_and_diagnostic_data.disable_analytics=true",   # see item 4
    f"--router.autorouter.max_passes={passes}",
    f"--router.max_threads={n}",
    f"--router.optimizer.max_threads={n}",
    *extra,
]
```

## 4. Disable analytics for automated runs

With analytics enabled, a CLI run sends telemetry at start and waits up to 1.5 s at exit to flush
it. This build also skips the analytics summary (a full-board statistics pass) when analytics are
disabled. Pass `--usage_and_diagnostic_data.disable_analytics=true`, or set
`FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__DISABLE_ANALYTICS=true` in the environment of the
subprocess.

## 5. Isolate the user-data directory per process

Freerouting rewrites `<user-data>/freerouting.json` on every start, and later runs read it back.
That is why the server staggers parallel starts by `PARALLEL_START_STAGGER_S`, and why the
thread-count workaround exists in `_build_freerouting_cmd()`. Give every process its own
directory, then drop the stagger:

```python
env = dict(os.environ, FREEROUTING__USER_DATA_PATH=os.path.join(work_dir, f"fr-userdata-{idx}"))
subprocess.run(cmd, env=env, ...)
```

## 6. JVM flags

* AOT cache (JDK 25+): cuts JVM start-up and warm-up. On a small board, wall time went from 4.1 s
  to 3.4 s, with the same `.ses`. Train once per JAR + JDK combination and reuse:

  ```python
  AOT = os.path.join(os.path.expanduser("~"), ".kicad-mcp", "freerouting.aot")
  # one-off training run (any representative board; takes as long as routing it):
  #   java -XX:AOTCacheOutput=<AOT> -jar freerouting.jar -de any.dsn -do out.ses --gui.enabled=false
  jvm_flags = [f"-XX:AOTCache={AOT}"] if os.path.exists(AOT) else []
  ```

  Delete and retrain the cache whenever the JAR or the JDK changes. A stale cache only prints a
  warning and is ignored.
* `-XX:+UseParallelGC -XX:+UseCompactObjectHeaders`: about 5% faster on the large board, same
  `.ses`.

## 7. Time limits

`--router.job_timeout` only stops work and never changes how it is done, so the results stay
deterministic. On large boards most of the time goes to the later autoroute passes, which recover
only a few connections each. For example, `top.dsn` went from 130 to 87 to 55 unrouted in passes
1–3, then improved slowly. A lower `--router.autorouter.max_passes`, or
`--router.autorouter.stagnation_pass_limit`, is the biggest remaining time lever. It trades away
completion, so it is a product decision for the MCP server, not something this build changes.
