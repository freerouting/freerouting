# Freerouting 2.6.0-RC9 Regression Investigation: Tier B Slowdowns, Timeouts, and OOMs

Record of the November 2026 investigation into the performance collapse, unexpected job timeouts, and OutOfMemoryError crashes observed in Freerouting 2.6.0-RC9 relative to 2.6.0-RC5 and 2.6.0-RC6.

---

## 1. Executive Summary

During overnight PCBench benchmarking of release candidate **2.6.0-RC9**, I observed severe stability and performance regressions:
- Multiple Tier B boards that previously completed cleanly in 5–7 minutes ran for 4 to 6 hours or exceeded the 6-hour job timeout budget (21,600 s).
- Two fixtures suffered catastrophic JVM crashes with `OutOfMemoryError` (`exit code 3`) after accumulating over 3,000 GB of cumulative heap allocations.
- Tier B clean completion dropped from **86.0% (453/527 clean, 0 timeouts, 0 crashes)** in RC6 to **83.2% (139/167 clean, 4 timeouts, 2 OOM crashes)** in the partial RC9 run.
- Even simple, 100% routable boards with 0 open connections experienced artificial slowdowns during multi-worker execution due to host CPU saturation and garbage-collection thrashing caused by runaway sibling worker processes.

**Root cause identified:** The regression was introduced in commit `2e17fc758` (**PR #1037: `feat/configurable-stagnation-limit`**, merged into **2.6.0-RC8**). Inside `AutorouteBatchLoop.java`:
1. Ripping blockers resets the global stagnation pass anchor (`passOfBestScore = currentPass`).
2. Routing any connection on the ripped board improves score relative to the degraded state, resetting the blocker attempt counter (`lastMileAttempts = 0`).

Together, these two behaviors trap any board with 1 to 5 stubborn incomplete connections in an unbounded **livelock cycle** (Blocker Ripup $\rightarrow$ Score Drops $\rightarrow$ Partial Route $\rightarrow$ Score Rises $\rightarrow$ Attempts Reset $\rightarrow$ Stagnation $\rightarrow$ Blocker Ripup). Stagnation termination is rendered completely inoperative, keeping the autorouter spinning for hundreds or thousands of passes until a 6-hour timeout or fatal JVM heap exhaustion occurs.

---

## 2. Release Candidates Evaluated

| Build | Git SHA | Description & Merged Features |
|---|---|---|
| **2.6.0-RC5** | `0d658431e` | Baseline build prior to rational-junction overhaul. |
| **2.6.0-RC6** | `2eba6f7fb` | Rational junction connectivity and SES round-trip fixes (#1010 / #1011). Baseline for this release cycle. |
| **2.6.0-RC7** | `23092515a` | Intermediate candidate containing PRs #1009 (router max threads), #1045/#1046/#1043 (perf optimizations), #1068 (neck width), #1049 (canonical placement), #1065 (plated slot keepout), #1058 (API defaults), and #1057 (wire end in pad). |
| **2.6.0-RC8** | `88480e851` | Intermediate candidate containing PRs #1062 (pull tight budget), #1060 (partial board optimizer), #1055 (strict DRC rollback), #1051 (pin clearance class), #1021 (prepaid via credit), #1041 (routing without vias), #1040 (micro-neckdown), #1042 (via in pad), #1064 (octagons), #1044 (clearance baseline), #1070 (undo ledger), **#1069 (blocker memory)**, #1048 (net-tie clustering), **#1037 (configurable stagnation limit)**, and #1071 (decoupling capacitor identifier). |
| **2.6.0-RC9** | `ceb56c6f1` | Head release candidate containing PRs #1028 (donation logic), #1075 (star ground tests), #1073 (dependency updates), and #1047 (pad class-to-class clearance fixes). |

Corpus test invocation parameters matching PCBench:
```bash
java -Dsun.stdout.buffered=false -Xmx4g -XX:+ExitOnOutOfMemoryError \
     -Dfreerouting.log.file.level=INFO -Dfreerouting.log.console.level=INFO \
     -jar freerouting-<version>.jar \
     --gui.enabled=false --api_server.enabled=false --mcp_server.enabled=false \
     --router.max_threads=1 --router.optimizer.max_threads=1 \
     -dct 0 --router.fanout.enabled=true --router.job_timeout=06:00:00 \
     -de <unrouted.dsn> -do <output.ses> --router.result_json=<result.json>
```

---

## 3. Quick-Testing Fixtures from Tier B

To isolate the cause and measure the regression rapidly without running 6-hour corpus jobs, I selected three representative fixtures from Tier B:

1. **`PCBench/retroreflectors_RUMBAFLOCK/unrouted.dsn`** (Quickest diagnostic fixture):
   - **Properties:** Tier B, 3 unrouted connections, naturally triggers last-mile blocker ripup.
   - **Role:** Directly exposes the stagnation liveloop defect in under 1 minute.
2. **`PCBench/Helium_helium_1-6/unrouted.dsn`**:
   - **Properties:** Tier B, 1 unrouted connection, fast solving.
   - **Role:** Confirms pass count inflation and latency jump across intermediate release candidates.
3. **`PCBench/m2-electronics_m2pa-m3mount/unrouted.dsn`** & **`tinyFISH_tinyBRUSH/unrouted.dsn`**:
   - **Properties:** Tier B, 0 unrouted connections (100% routable in 2 passes).
   - **Role:** Control boards. When tested in isolation, execution time is constant across all versions (~5–6 s). During overnight multi-worker PCBench execution, however, they regressed up to **35.7x** (from 5.7 s to 204.1 s) due entirely to host CPU starvation and GC lock contention caused by runaway sibling worker processes.

---

## 4. Empirical Test Results Across RC5 through RC9

### A. Isolated Diagnostic Run: `retroreflectors_RUMBAFLOCK`

| Version | Wall Time | Exit Code | Total Passes | Blocker Ripup Invocations | Behavior Notes |
|---|---|---|---|---|---|
| **2.6.0-RC5** | 8.69 s | 0 | 108 | 12 | Normal stagnation termination |
| **2.6.0-RC6** | 9.48 s | 0 | 144 | 12 | Normal stagnation termination |
| **2.6.0-RC7** | 6.96 s | 0 | 148 | 11 | Normal stagnation termination |
| **2.6.0-RC8** | **50.06 s** | 0 | **2,963** | **671** | **Livelock: 20x pass explosion, 55x blocker ripups** |
| **2.6.0-RC9** | **49.78 s** | 0 | **2,963** | **671** | **Identical livelock as RC8** |

**Finding:** The pass explosion and blocker ripup runaway appeared abruptly between **RC7 and RC8**. RC8 and RC9 behave identically on this fixture.

### B. Isolated Run: `Helium_helium_1-6` and `tinyFISH_tinyBRUSH`

| Fixture | RC5 | RC6 | RC7 | RC8 | RC9 |
|---|---|---|---|---|---|
| **`Helium_helium_1-6`** (Wall Time) | 29.08 s | 16.11 s | 72.94 s | 75.63 s | 76.01 s |
| `Helium_helium_1-6` (Passes) | 18 | 5 | 76 | 11 | 11 |
| **`tinyFISH_tinyBRUSH`** (Wall Time) | 5.65 s | 5.08 s | 4.93 s | 4.77 s | 4.88 s |
| `tinyFISH_tinyBRUSH` (Passes) | 2 | 2 | 5 | 5 | 5 |

**Finding:** Fully routed boards without opens (`tinyFISH_tinyBRUSH`) show zero regression when run in an isolated environment. The regression only manifests when a design has incomplete connections that enter the blocker rip-up threshold.

---

## 5. Overnight Corpus Impact: Timeouts and OutOfMemory Crashes

In the overnight PCBench corpus run, Tier B boards with remaining incomplete connections entered this liveloop, running for thousands of passes until exhausting memory or timing out.

### A. Fatal Crashes (JVM `OutOfMemoryError`, Exit Code 3)

| Fixture | RC6 Result | RC9 Result | RC9 Wall Clock | RC9 Passes | Cumulative Memory Allocated |
|---|---|---|---|---|---|
| **`chip_lcd_dip_CHIP_LCD_DIP`** | Clean (313.4 s, 170 passes) | **FAILED (OOM)** | **17,110.0 s (4.75 h)** | **836** | **3,162.27 GB** |
| **`icehat_icehat`** | Clean (418.7 s) | **FAILED (OOM)** | **20,880.9 s (5.80 h)** | > 800 | **2,775.83 GB** |

Both jobs repeatedly allocated routing trees, board clones, and undo history over hundreds of passes. The cumulative churn of 3+ terabytes of allocation overwhelmed JVM garbage collection and triggered `-XX:+ExitOnOutOfMemoryError`.

### B. New 6-Hour Timeouts (`TIMED_OUT`, Exit Code 0 / Exceeded Budget)

| Fixture | RC5 Wall Time | RC6 Wall Time | RC9 Wall Time | Status in RC9 |
|---|---|---|---|---|
| **`Ttl_wlan_radar`** | 1,723.0 s | 2,159.8 s | **21,670.3 s (6.02 h)** | TIMED OUT |
| **`eeg_brainboard_batteryv0`** | 839.8 s | 708.7 s | **21,606.2 s (6.00 h)** | TIMED OUT |
| **`DoroidOscillo-Board_Android_Oscilloscope`** | 1,723.9 s | 985.7 s | **21,611.9 s (6.00 h)** | TIMED OUT |
| **`kitspace_USB-C-Screen-Adapter`** | 1,354.3 s | 997.9 s | **21,627.3 s (6.01 h)** | TIMED OUT |

### C. Severe Multi-Hour Slowdowns (Completed Just Before Timeout)

| Fixture | RC6 Wall Time | RC9 Wall Time | Slowdown Factor | Incompletes |
|---|---|---|---|---|
| **`LPC2148_Stick_LPC2148_stick`** | 361.1 s (~6 min) | **21,069.1 s (~5.85 h)** | **58.3x** | 0 (stuck during run) |
| **`HW-ESP8266_Roomba_ESP8266_Roomba`** | 386.8 s (~6.5 min) | **16,832.3 s (~4.68 h)** | **43.5x** | 2 unrouted |
| **`badge2015_badge2015`** | 293.9 s (~4.9 min) | **13,858.2 s (~3.85 h)** | **47.2x** | 4 unrouted |
| **`cnlohr_wiflier`** | 442.0 s (~7.4 min) | **13,603.1 s (~3.78 h)** | **30.8x** | 1 unrouted |
| **`HW-ESP8266_Roomba_panel_x_3`** | 514.6 s (~8.6 min) | **6,984.5 s (~1.94 h)** | **13.6x** | 3 unrouted |

---

## 6. Root Cause Mechanism in `AutorouteBatchLoop.java`

Inspection of commit `2e17fc758` (PR #1037) reveals the defect in `src/main/java/app/freerouting/autoroute/pipeline/AutorouteBatchLoop.java`:

### Defect 1: Unconditional Reset of `lastMileAttempts` on Relative Score Gains

```java
// AutorouteBatchLoop.java, lines 496-500
if (boardScoreAfter > lastBestScore + STAGNATION_SCORE_THRESHOLD) {
  consecutiveNoImprovementPasses = 0;
  lastBestScore = boardScoreAfter;
  lastMileAttempts = 0;  // <--- DEFECT 1: Clears the attempt budget!
}
```

- In Freerouting v2.5.0 through v2.6.0-RC7, `lastMileAttempts` was a strict job ceiling capped at `LAST_MILE_MAX_ATTEMPTS = 2`. Once blocker ripup executed twice, it was permanently disabled.
- In PR #1037, `lastMileAttempts` is set to `0` whenever `boardScoreAfter > lastBestScore`.
- When blocker rip-up executes, it rips up 10–25 traces and vias, temporarily dropping the board score.
- In subsequent passes, the router re-routes even a single connection into the newly cleared area. Because the score improves relative to the degraded post-rip board, line 499 fires and resets `lastMileAttempts = 0`.
- This completely nullifies the `lastMileAttempts < LAST_MILE_MAX_ATTEMPTS` guard. Blocker ripup can now trigger an infinite number of times.

### Defect 2: Reset of `passOfBestScore` on Blocker Rip-up

```java
// AutorouteBatchLoop.java, lines 533-543
if (incompleteNow > 0
    && incompleteNow <= LAST_MILE_INCOMPLETE_LIMIT
    && lastMileAttempts < LAST_MILE_MAX_ATTEMPTS
    && consecutiveNoImprovementPasses >= LAST_MILE_STAGNATION_PASSES) {
  int removed = LastMileBlockerRipup.ripBlockers(router.board, router.ripupMemory);
  lastMileAttempts++;
  if (removed > 0) {
    boardStatisticsAfter = new BoardStatistics(router.board);
    boardScoreAfter = boardStatisticsAfter.getRouterScore(job.routerSettings);
    lastBestScore = boardScoreAfter;
    consecutiveNoImprovementPasses = 0;
    passOfBestScore = currentPass;  // <--- DEFECT 2: Resets global stagnation timer!
    alreadyRoutedBoardHashes.clear();
    job.logInfo(...);
  }
}
```

- The autorouter's ultimate fallback stagnation guard is the global tracker:
  ```java
  else if (stagnationPassLimit > 0 && (currentPass - passOfBestScore) >= stagnationPassLimit) {
    stopFor(BatchAutorouter.StopReason.STAGNATION);
    break;
  }
  ```
- By resetting `passOfBestScore = currentPass` when blockers are ripped, `(currentPass - passOfBestScore)` is reset to `0`.
- Because blocker rip-up triggers every `LAST_MILE_STAGNATION_PASSES` (3 passes), `(currentPass - passOfBestScore)` never reaches `stagnationPassLimit` (20 passes).
- **Result:** The global stagnation detector never fires. The local stagnation detector never fires. Cycle detection (`alreadyRoutedBoardHashes`) is cleared on each ripup. The batch loop spins without bounds.

---

## 7. Recommended Next Steps

### Step 1: Immediate Bugfix in `AutorouteBatchLoop.java`

Revert the two problematic reset statements introduced in PR #1037:

1. **Delete `lastMileAttempts = 0;` on line 499.**
   `lastMileAttempts` must remain a monotonic counter capped by `LAST_MILE_MAX_ATTEMPTS = 2` for the lifetime of the routing stage. It must not reset when intermediate routing decisions improve upon a ripped-up board.
2. **Delete `passOfBestScore = currentPass;` on line 541.**
   Blocker rip-up is an obstacle removal tactic that lowers board score; it is not a new global best score. `passOfBestScore` must strictly track the pass where `globalBestScore` was achieved.

### Step 2: Verification (Completed)

1. **`retroreflectors_RUMBAFLOCK` Diagnostic Run:**
   - **Pre-fix (2.6.0-RC9):** 49.78 s, 2,963 passes, 671 blocker rip-up invocations.
   - **Post-fix (Patched):** **12.80 s, 187 passes, 14 blocker rip-up invocations (93.7% reduction in passes, 97.9% reduction in rip-ups)**.
2. **`chip_lcd_dip_CHIP_LCD_DIP` Crash Prevention Run:**
   - **Pre-fix (2.6.0-RC9):** FAILED at 17,110.0 s (4.75 hours) with `OutOfMemoryError` after 836 passes and 3,162 GB cumulative heap allocation.
   - **Post-fix (Patched):** **Completed cleanly in 398.25 s (~6.6 minutes), 190 passes, 16 blocker rip-ups, zero OOM errors or memory leaks**.
3. **Automated Unit & Smoke Tests:**
   - `StagnationLimitSettingsTest`: PASSED.
   - `Issue163PicProgrammerRoutingTest`: PASSED.
   - `Dac2020Bm01RoutingTest`: PASSED.
   - Code quality verification (`spotlessCheck checkstyleMain checkstyleTest`): PASSED.

### Step 3: Next Actions

1. Commit and push the fix in `AutorouteBatchLoop.java`.
2. Build and version `freerouting-2.6.0-RC10.jar`.
3. Restart the PCBench Tier A and Tier B regression runs on master to update benchmark baselines.
