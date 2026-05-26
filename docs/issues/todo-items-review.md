# TODO Review — Pre-release v2.2 Audit

**Date:** 2026-04-25  
**Scope:** All `// TODO` comments in `src/main/java` (test sources are clean — zero TODOs found)  
**Purpose:** Determine which items are still relevant and whether it is safe to address any of them before the v2.2 release

---

## Summary

16 TODO comments were found across 11 files. None of them block the v2.2 release. A small subset
(3 items) carry low enough risk to address before the release if desired; the rest should be deferred
or tracked as post-release work.

### Quick Risk Table

| # | File | Line | Description | Risk | Recommendation |
|---|------|------|-------------|------|----------------|
| 1 | `BaseController.java` | 35 | Email → userId lookup missing | LOW | Defer post-v2.2 |
| 2 | `BaseController.java` | 42 | Caller auth not verified against auth endpoint | MEDIUM | Defer post-v2.2 |
| 3 | `BatchAutorouter.java` | 382 | Parallel routing (aspirational) | **CRITICAL** | Do not touch |
| 4 | `BatchAutorouter.java` | 862 | Fire `TIMED_OUT` task state instead of `CANCELLED` | LOW | ✅ Safe to fix now |
| 5 | `BatchOptimizer.java` | 210 | Move `ADDITIONAL_RIPUP_COST_FACTOR_AT_START=10` to settings | MEDIUM | Defer post-v2.2 |
| 6 | `BatchOptimizer.java` | 218 | Move `0.6` trace ripup factor to settings | MEDIUM | Defer post-v2.2 |
| 7 | `NamedAlgorithm.java` | 29 | Misleading comment — field IS already `transient` | LOW | ✅ Clarify comment now |
| 8 | `BoardFrame.java` | 280 | Reuse in-memory binary for FRB save | LOW-MEDIUM | Defer post-v2.2 |
| 9 | `AutorouterAndRouteOptimizerThread.java` | 113 | Deprecate class, adopt modern job scheduler | **CRITICAL** | Do not touch |
| 10 | `GuiBoardManager.java` | 1273 | Remove dead Version B of `remove_ratsnest()` | LOW | ✅ Safe to clean up now |
| 11 | `GuiBoardManager.java` | 1299 | Remove dead Version B of `get_ratsnest()` | LOW | ✅ Safe to clean up now |
| 12 | `GuiBoardManager.java` | 2689 | Thread should receive board+settings only (not full manager) | HIGH | Defer post-v2.2 |
| 13 | `Structure.java` | 890 | Move `create_board` out of specctra parser package | HIGH | Defer post-v2.2 |
| 14 | `RulesFileSettings.java` | 30 | Integrate with `RulesFile.read` logic (currently a stub) | MEDIUM | Defer post-v2.2 |
| 15 | `SesFileSettings.java` | 31 | Implement SES file settings parsing "if needed" | VERY LOW | No action needed |

---

## Detailed Analysis

### #1 — `BaseController.java:35`: Get userId from e-mail address

```java
if ((userEmailString != null) && (!userEmailString.isEmpty())) {
    // TODO: get userId from e-mail address
}
```

**Status:** Feature stub. The block is empty — if a caller supplies only
`Freerouting-Profile-Email` (and no UUID), authentication falls through to a null-check that throws an
`IllegalArgumentException`. There is **no silent failure** — the caller gets a clear error.

**Still relevant?** Yes. Email-based auth is a documented feature gap.

**Risk to implement now:** LOW in theory, but it requires a call to a remote lookup service that
maps email → UUID. Adding an external service call just before release introduces a new failure
mode (service unavailability) and needs proper timeout/retry handling. **Defer post-v2.2.**

---

### #2 — `BaseController.java:42`: Authenticate user against auth endpoint

```java
// TODO: authenticate the user by calling the auth endpoint
return userId;
```

**Status:** Security gap. The method currently trusts whatever UUID or email is supplied in the
request header without verifying it against an external authority. Any caller can pass any UUID and
get access.

**Still relevant?** Yes — this is a known architectural limitation.

**Risk to implement now:** MEDIUM-HIGH. Implementing this requires:
1. A stable auth endpoint to call
2. Reliable error handling (what if the endpoint is down?)
3. Caching/token-reuse logic to avoid a round-trip on every API call
4. Regression testing of all API auth flows

Introducing this just before release risks breaking API consumers. **Defer post-v2.2.**

---

### #3 — `BatchAutorouter.java:382`: Parallel routing (aspirational)

```java
// TODO: Start multiple instances of the following part in parallel, wait for
// the results and keep the best one
```

**Status:** Early design note for a future optimisation. The comment immediately below notes that
deterministic sort was **disabled in v2.3 because it negatively impacts convergence** — meaning this
area is already known to be sensitive to ordering changes.

**Still relevant?** Yes as a long-term goal. Not relevant for v2.2.

**Risk to implement now:** CRITICAL. Parallel routing would be a fundamental architectural change
to the innermost routing loop. Even minor concurrency bugs in this area would cause non-deterministic
clearance violations that are extremely difficult to diagnose. **Do not touch before v2.2.**

---

### #4 — `BatchAutorouter.java:862`: Fire `TIMED_OUT` task state on timeout

```java
} else {
    // TODO: set it to TIMED_OUT if it was interrupted because of timeout
    this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.CANCELLED, ...));
}
```

**Status:** State-reporting inconsistency. `TaskState.TIMED_OUT` already exists in the enum.
`RoutingJobState.TIMED_OUT` is correctly set by `RoutingJobSchedulerActionThread` when the job
deadline passes. Only the `TaskState` event fired from `BatchAutorouter` is wrong — it always
fires `CANCELLED`, even when the stop was triggered by a timeout.

**Still relevant?** Yes. API consumers listening to `TaskStateChangedEvent` cannot distinguish a
user-cancellation from a timeout.

**Risk to implement now:** LOW. The fix is a one-line conditional. It does not touch any routing
logic:

```java
boolean isTimedOut = (job != null) && (job.state == RoutingJobState.TIMED_OUT);
TaskState finalState = isTimedOut ? TaskState.TIMED_OUT : TaskState.CANCELLED;
this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, finalState, currentPass, ...));
```

**✅ Safe and recommended to fix before v2.2.**

---

### #5 — `BatchOptimizer.java:210`: Move `ADDITIONAL_RIPUP_COST_FACTOR_AT_START=10` to settings

```java
// TODO: move this fixed parameter (ADDITIONAL_RIPUP_COST_FACTOR_AT_START=10) to
// the router optimizer settings
ripup_costs *= ADDITIONAL_RIPUP_COST_FACTOR_AT_START;
```

**Status:** A well-named constant already exists (`ADDITIONAL_RIPUP_COST_FACTOR_AT_START = 10` as
a class-level `private static final int`). The TODO asks to expose it as a configurable optimizer
setting.

**Still relevant?** Yes — advanced users cannot tune this without recompilation.

**Risk to implement now:** MEDIUM. The constant has been at value 10 for a long time and is part
of the v1.9 baseline. Exposing it as a `RouterOptimizerSettings` field requires:
- Adding a nullable field to `RouterOptimizerSettings`
- Updating `DefaultSettings` with the hardcoded value of 10
- Wiring throughout all settings sources

This is low-risk from a routing-correctness standpoint (default value unchanged), but it expands
the configuration surface right before release. **Defer post-v2.2** unless there is a specific user
request driving this.

---

### #6 — `BatchOptimizer.java:218`: Move `0.6` trace ripup factor to settings

```java
// TODO: move this fixed parameter (0.6) to the router optimizer settings
ripup_costs = (int) Math.round(0.6 * (double) ripup_costs);
```

**Status:** Same class of issue as #5 — a magic number that is well-understood empirically
("taking less ripup costs seems to produce better results") but not user-configurable.

**Risk to implement now:** MEDIUM — same reasoning as #5. **Defer post-v2.2.**

---

### #7 — `NamedAlgorithm.java:29`: Misleading comment about `transient`

```java
// TODO: This should be a transient field, but it is not possible to serialize the board
// with the JSON serializer.
protected transient RoutingBoard board;
```

**Status:** The comment is **factually incorrect as written** — the field IS already declared
`transient`. What the comment actually means is: *"Ideally the JSON serializer would skip this
field during JSON serialization as well, but since `RoutingBoard` is not JSON-serialisable, the
`transient` keyword only helps Java object serialization; for Gson we rely on the fact that
transient fields are skipped by default."* This is already the correct behaviour — Gson respects
`transient`.

**Still relevant?** The comment should be reworded to avoid confusion for future maintainers.
It is not an actionable TODO.

**Risk to implement now:** NONE for routing. Just a Javadoc/comment fix.

**✅ Safe to clarify the comment now** (cosmetic change, no functional impact).

---

### #8 — `BoardFrame.java:280`: Reuse in-memory binary for FRB save

```java
// TODO: it should be enough to save the binary data that we already have in the
// routingJob.output.data
this.saveAsBinary(this.routingJob.output.getFile());
```

**Status:** Performance optimisation for GUI FRB file save. Currently the board is serialised
fresh on every save. If `routingJob.output.data` is already populated with the current board's
binary, this re-serialisation is redundant.

**Still relevant?** Yes — a valid optimisation, but minor.

**Risk to implement now:** LOW-MEDIUM. Would require confirming that `routingJob.output.data` is
always up-to-date with the current board state at the time of save. If there is any condition
where the in-memory data is stale (e.g., manual interactive edits after routing completes), using
it would silently save an outdated board. Needs careful validation. **Defer post-v2.2.**

---

### #9 — `AutorouterAndRouteOptimizerThread.java:113`: Deprecate class

```
TODO: This class should be deprecated in favor of a more modern job scheduler architecture
for better job management.
```

**Status:** Architectural long-term goal. This class is the central `Thread` that drives the
entire interactive routing session (autorouter + optimizer + event firing). It is tightly coupled
to `GuiBoardManager` and `InteractiveActionThread`.

**Still relevant?** Yes — the architecture is acknowledged as technical debt.

**Risk to implement now:** CRITICAL. This class is the heartbeat of all GUI-driven routing sessions.
Any refactoring here could break the GUI routing loop, interrupt event propagation, or corrupt board
state mid-session. **Do not touch before v2.2.**

---

### #10 & #11 — `GuiBoardManager.java:1273/1299`: Remove dead "Version B" code

```java
public void remove_ratsnest() {
    // TODO: test these two versions combined with get_ratsnest() method
    // Version A
    ratsnest = null;
    // Version B
    // do nothing as we create a new instance of ratsnest every time
}

public RatsNest get_ratsnest() {
    // TODO: test these two versions combined with remove_ratsnest() method
    // Version A
    if (ratsnest == null) { ratsnest = new RatsNest(this.board); }
    return this.ratsnest;
    // Version B
    // return new RatsNest(this.board);
}
```

**Status:** Two competing implementations were sketched during a refactoring, and Version A was
chosen for both methods. The commented-out Version B code and the "TODO: test" markers were
left behind. **The current behavior (Version A for both) is correct and consistent** — Version B
in `remove_ratsnest` (do nothing) would be semantically wrong when paired with Version A in
`get_ratsnest` (reuse cached instance).

**Still relevant?** Version A is clearly the right choice. The TODO markers and dead code should
be removed.

**Risk to implement now:** NONE — purely cosmetic. Delete the Version B comments and the
`// TODO: test` lines.

**✅ Safe to clean up now.**

---

### #12 — `GuiBoardManager.java:2689`: Thread should receive board+settings only

```java
// TODO: ideally we should only pass the board and the routerSettings to the
// thread, and let the thread create the router and optimizer
this.interactive_action_thread = InteractiveActionThread.get_autorouter_and_route_optimizer_instance(this, job);
```

**Status:** Architecture improvement. The thread currently receives the full `GuiBoardManager`
(`this`) — a >3000-line class — instead of just the board and routing settings it needs. This
tightly couples the routing thread to the GUI manager.

**Still relevant?** Yes — this is a separation-of-concerns violation.

**Risk to implement now:** HIGH. `InteractiveActionThread` and `AutorouterAndRouteOptimizerThread`
use `GuiBoardManager` references throughout their implementation. Decoupling them requires
understanding every use of that reference and potentially introducing new interfaces. This work
is closely related to TODO #9. **Defer post-v2.2.**

---

### #13 — `Structure.java:890`: Move `create_board` out of specctra parser

```java
// let's create a board based on the data we read (TODO: move this method
// somewhere outside of the designforms.specctra package)
result = create_board(p_par, board_construction_info);
```

**Status:** Package-boundary violation. `Structure.java` (a parser class in `io.specctra.parser`)
contains the `create_board` method, which constructs the live `RoutingBoard`. Board construction
belongs in the domain layer, not in the file-format parser.

**Still relevant?** Yes — this is a known architectural boundary violation documented in AGENTS.md.

**Risk to implement now:** HIGH. `Structure.java` is 1171 lines, and `create_board` is deeply
entangled with the parser's context objects (e.g., `p_par.board_handling` which is a parser
parameter). Moving this method would require introducing a clean handoff interface between the
parser output and the board factory. **Defer post-v2.2.**

---

### #14 — `RulesFileSettings.java:30`: Integrate with `RulesFile.read` logic

```java
private RouterSettings loadSettings() {
    // TODO: Integrate with existing RulesFile.read logic
    return new RouterSettings();  // ← always empty
}
```

**Status:** `RulesFileSettings` is a confirmed stub (documented as BUG-1 in
`docs/issues/clearance-loading-and-settings-integration.md`). It always returns an empty
`RouterSettings`, meaning any **behavioral router settings** that might be in a `.rules` file
are not applied via the `SettingsMerger` pipeline.

**Important nuance:** The `.rules` file's **clearance and netclass data** ARE applied correctly
through a separate direct-mutation path (`RulesFile.read()` → `ClearanceMatrix`). The stub only
affects behavioral settings like `maxPasses`, routing strategy, etc. — and `.rules` files
do not conventionally carry those anyway. In practice the impact is minimal.

**Still relevant?** Yes — the stub logs `"Loaded router settings from RULES file"` even though
nothing was loaded, which is misleading.

**Risk to implement now:** MEDIUM. Implementing this properly requires answering: *"What
behavioral router settings can a `.rules` file carry?"* If the answer is "none beyond what
`ClearanceMatrix` already handles via the direct path," then the right fix is simply to
remove the misleading log message and document the stub. A full integration would require
extracting a `readBehaviouralSettings()` method from `RulesFile.read()`. **Defer post-v2.2**
(or fix the misleading log message now as a cosmetic patch).

---

### #15 — `SesFileSettings.java:31`: Implement SES file settings parsing

```java
// TODO: Implement SES file parsing if needed
return new RouterSettings();  // ← always empty
```

**Status:** Speculative placeholder. SES (Specctra Session) files contain routing **results**
(completed traces, via placements), not router *configuration*. There is no established convention
for SES files to carry behavioral router settings. The "if needed" qualifier confirms this was
added as a structural placeholder.

**Still relevant?** No meaningful action required. This is very unlikely to ever need
implementation unless a non-standard SES variant is encountered.

**Risk to implement now:** NONE — but there is also nothing to implement.

**Recommendation:** The misleading log message `"Loaded router settings from SES file"` (when
nothing was loaded) should optionally be removed or downgraded to `TRACE`. Otherwise, leave as-is.

---

## Items Safe to Address Before v2.2

The following four TODOs carry near-zero routing risk and can be resolved cleanly before the
v2.2 release:

| # | Item | Action |
|---|------|--------|
| 4 | `BatchAutorouter.java:862` | Fire `TaskState.TIMED_OUT` when `job.state == TIMED_OUT`, else `CANCELLED` |
| 7 | `NamedAlgorithm.java:29` | Rewrite the misleading comment — field is already `transient` as intended |
| 10 | `GuiBoardManager.java:1273` | Remove the `// TODO` marker and dead Version B comment |
| 11 | `GuiBoardManager.java:1299` | Remove the `// TODO` marker and dead Version B comment |

## Items to Track as Post-v2.2 Work

| Priority | Item | Effort | Notes |
|----------|------|--------|-------|
| Medium | `BaseController.java:35/42` | Medium | Auth service integration; needs design |
| Medium | `BatchOptimizer.java:210/218` | Low | Move two constants to `DefaultSettings`; safe but expands config surface |
| Low | `BoardFrame.java:280` | Low | FRB save optimization; verify data freshness first |
| Low | `RulesFileSettings.java:30` | Low-Medium | Clarify stub log or implement if `.rules` behavioral settings are needed |
| Very Low | `SesFileSettings.java:31` | None | Remove misleading log, leave as structural placeholder |

## Items NOT to Touch Before v2.2

| Item | Reason |
|------|--------|
| `BatchAutorouter.java:382` — parallel routing | Core routing loop; regression risk is extreme |
| `AutorouterAndRouteOptimizerThread.java:113` — deprecate class | Central GUI routing thread; cannot be refactored safely in isolation |
| `GuiBoardManager.java:2689` — thread dependency injection | Architecturally coupled to #9 above |
| `Structure.java:890` — move `create_board` | Deep parser refactoring; requires new interface design |

