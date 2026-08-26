# Security Audit Pass H — Resource Exhaustion and Rate Limits

**Status:** Confirmed (Grok 4.6 Medium, 2026-08-21)
**Hunter model:** GPT-5.6 Luna Max
**Confirmation model:** Grok 4.6 Medium
**Date:** 2026-08-21
**Scope:** parser bounds, upload/body limits, scheduler, routing jobs, rate-limit filters, SSE/WS cleanup

This is a read-only hunt report. Findings remain candidates until Grok 4.6 Medium confirms them or
a maintainer reproduces the behavior with a regression test. No production code was changed and no
risk-register rows were added (Phase 3).

## Method

1. Traced REST and MCP rate-limit filter ordering, configuration defaults, identity-key
   construction, and fixed-window counter cleanup.
2. Traced session creation, job enqueue, scheduler admission, running-job caps, terminal-state
   handling, and the APIs available for removing retained state.
3. Audited all job-specific SSE streams for disconnect callbacks, sink failure handling, executor
   shutdown, listener removal, per-client caps, and repeated serialization work.
4. Reviewed the process-wide in-memory log collection for retention limits and remotely triggerable
   log paths.
5. Rechecked DSN/KiCad input parsing and body materialization, while avoiding duplication of the
   already-confirmed Pass C upload/parser finding.
6. Excluded frozen `src_v19/` and did not modify production code or write exploit scripts.

## Confirmed findings

See Confirmation below. All five hunter candidates were accepted as Medium.

## Candidates (unconfirmed)

| Severity | Location | Finding | Exploit scenario | Fix sketch |
| --- | --- | --- | --- | --- |
| Medium | `src/main/java/app/freerouting/api/ApiRateLimitFilter.java:22-60`; `src/main/java/app/freerouting/api/mcp/McpRateLimitFilter.java:28-82`; `src/main/java/app/freerouting/api/FixedWindowRateLimiter.java:13-65`; `src/main/java/app/freerouting/settings/RateLimitSettings.java:9-19` | **Request throttling is disabled by default, and enabled limits use caller-controlled, unbounded identity keys.** Both filters return when `rate_limit.enabled` is false. When enabled, the key is built from the raw profile ID/email headers rather than a server-resolved principal. The counter map's 10,000-entry constant is only a cleanup trigger; fresh keys are not evicted and cleanup is performed under the synchronized `check` method. | A caller with a valid API key can submit different profile UUIDs or emails on successive requests because the profile identity is not bound to the key (the binding issue is confirmed in Pass A). Each value gets a separate window and creates another map entry, bypassing per-caller throttling and making the synchronized cleanup scan increasingly expensive. Exposed deployments also have no limiter unless an operator opts in. | Derive the limiter key from the authenticated principal, apply route-specific cost/concurrency limits, enable safe defaults for exposed listeners, and enforce a hard bounded map with deterministic eviction/expiry that does not scan the whole map while holding the request lock. |
| Medium | `src/main/java/app/freerouting/management/jobs/RoutingJobScheduler.java:40-76`, `:235-260`, `:446-460`; `src/main/java/app/freerouting/core/RoutingJob.java:87-112`; `src/main/java/app/freerouting/api/v1/JobInputResource.java:88-131` | **The process-wide job registry has no queue quota and does not evict terminal jobs.** The scheduler caps simultaneously running jobs at five, but every enqueue appends to a global `LinkedList`. The scheduler only removes null entries; completed, cancelled, invalid, terminated, and timed-out jobs remain unless the GUI calls `clearJobs`. Each retained job can hold input/output/DRC data, router settings, resource statistics, and a transient board. | An authenticated caller creates one session and repeatedly enqueues small jobs, including jobs that become `INVALID` because no input was supplied, or submits more jobs than the five running slots can process. The queue and retained terminal objects grow without a server-side limit or REST deletion/TTL path. The scheduler continues sorting and scanning the full list every polling cycle, so memory use and scheduler CPU both grow. Large uploaded designs amplify the retained heap. | Enforce total and per-principal queue limits before admission, reject or expire excess work, remove terminal jobs after a bounded retention period while preserving explicitly persisted summaries, and avoid sorting/scanning historical jobs in the live scheduler. |
| Medium | `src/main/java/app/freerouting/management/sessions/SessionManager.java:20-21`, `:82-100`; `src/main/java/app/freerouting/api/v1/SessionControllerV1.java:149-167` | **API-created sessions are retained in an unbounded process-wide registry without expiry or a public deletion path.** `createSession` inserts every new session into a static `HashMap`; the only removal method is not exposed by the REST session controller. | A caller repeatedly invokes `POST /v1/sessions/create`. Even without routing a board, the session map and the user's session-list response grow indefinitely. Combining this with the unbounded job registry gives a cheap way to accumulate process state. Concurrent creation/listing also accesses the plain `HashMap` without synchronization, which may turn load into request failures or registry corruption. | Add per-principal and global session quotas, idle/maximum lifetimes, an authenticated delete/revoke operation, and a concurrency-safe bounded registry. Avoid returning an unbounded session list in one response. |
| Medium | `src/main/java/app/freerouting/api/v1/JobOutputResource.java:392-449`, `:503-557`, `:58-59`; `src/main/java/app/freerouting/api/v1/JobProgressResource.java:394-434`; `src/main/java/app/freerouting/core/RoutingJob.java:45-53` | **Job-specific SSE streams retain per-connection executors/listeners after client disconnects and can repeat expensive board serialization.** The output streams create a new scheduled executor per connection, ignore the asynchronous send result, do not check `SseEventSink.isClosed()`, and only shut down for `COMPLETED`/`CANCELLED` or a thrown exception. The log stream adds a job listener but never removes it. `previousOutputChecksums` is static and has no removal path. | A caller with access to one own queued or long-running job opens many output/log streams and disconnects without allowing the job to finish. Each output stream can leave a scheduled worker and sink reference alive; each log stream leaves a listener attached to the job. A queued/invalid/terminated job is especially problematic because the documented terminal states are not all handled. The JSON stream runs `KiCadJsonWriter.write` and computes a CRC every 500 ms per subscriber, while the SES stream repeatedly reads and Base64-encodes the whole output when it changes. | Register disconnect/close callbacks, remove listeners and checksum entries in all terminal/error paths, handle failed asynchronous sends, cap concurrent streams per principal and globally, and share/coalesce board snapshots instead of serializing the board independently for every subscriber. |
| Medium | `src/main/java/app/freerouting/logger/LogEntries.java:12-13`, `:62-80`; `src/main/java/app/freerouting/logger/FRLogger.java:233-338`; `src/main/java/app/freerouting/api/security/ApiKeyValidationFilter.java:131-145` | **Global in-memory log history has no size or age bound.** Every INFO, WARNING, and ERROR entry is appended to an `ArrayList`; there is no automatic eviction. The API-key filter logs a warning for each missing or invalid request, and other API/MCP paths can retain request-derived messages in the same collection. | Repeated unauthenticated malformed requests or authenticated requests that trigger parser/API errors can grow the process-wide log list indefinitely. Since entries are also exposed through session/job log retrieval paths, large history increases synchronization, filtering, serialization, and response costs. MCP's full-body logging is a separate Pass B confidentiality finding but also makes individual retained messages potentially large. | Use a bounded ring buffer with configurable count/byte limits, rate-limit or sample repeated failures, cap message sizes before retention, and keep raw request bodies out of in-memory/application logs. |

## Cross-pass findings and controls

- Pass C already **confirmed** the lack of application-level request/body and decoded-design
  limits, unbounded DSN/KiCad parser work, and the six-leading-CR/LF format-detection hang.
  H independently verified the same boundary: `JobInputResource` materializes a complete request
  string or Base64 string before decoding, and the scheduler retains the resulting job data. These
  are not duplicated as new H rows; the queue and stream findings above are the H-specific
  amplifiers.
- `RoutingJobScheduler` does cap simultaneously running jobs at five
  (`RoutingJobScheduler.java:68-76`), but this is not a queue or memory quota.
- `RateLimitSettings.enabled` is `false` by default (`RateLimitSettings.java:9-19`) for both API
  and MCP. The defaults bind both listeners to loopback, but an operator can expose them and
  disable authentication for local/plugin operation.
- The MCP global SSE sink-retention issue was already **confirmed** in Pass B and is not repeated
  here. The job-specific streams are separate endpoints with their own executor/listener lifecycle.
- WebSocket clients do remove themselves on `@OnClose`/`@OnError`
  (`McpWebSocketEndpoint.java:55-65`); no new WS-only leak was established in this pass.
- No network path to Java object deserialization was added to H; `.frb` and internal snapshot
  findings remain in Pass G.

## Candidates not promoted

- The `SpecctraDsnStreamReader` starts with a 16 MiB buffer and grows it, and several parser
  collections are unbounded. This is included in Pass C's confirmed upload/parser-work finding,
  not promoted as an independent parser bug here.
- `SessionManager.sessions` is a plain `HashMap` accessed by concurrent API calls. The race is
  credible, but this report does not claim a specific corruption result without a reproducer;
  the unbounded retention is independently observable.
- Jetty/Jersey's container defaults may impose deployment-specific limits, but no explicit
  application-level maximum body size or stream-count policy is configured in
  `Freerouting.initializeAPI` or `initializeMCP`.
- `McpRealtimeBridge` removes closed SSE sinks only when a later broadcast sees them; this is the
  confirmed Pass B finding T-B/H3, not a new H candidate.

## Confirmation

**Confirmer:** Grok 4.6 Medium (2026-08-21). No new domains hunted. No production code changed.
No risk-register rows added (Phase 3). Hunter “cross-pass findings and controls” and “candidates
not promoted” blocks are accepted, including: do not re-open Pass C upload/parser/CR-LF hang as
new H rows; do not re-open Pass B MCP SSE sink retention; WebSocket `@OnClose`/`@OnError` removal
is a control; HashMap concurrency is not a confirmed availability finding without a reproducer.

| Candidate | Verdict | Severity after confirm | Reason |
| --- | --- | --- | --- |
| Rate limit off by default; spoofable/unbounded keys when on | **Confirmed** | Medium | T-H2; see below |
| Unbounded job registry / no terminal eviction | **Confirmed** | Medium | T-H4; see below |
| Unbounded session registry / no REST delete | **Confirmed** | Medium | See below |
| Job SSE executor/listener leak and repeated serialization | **Confirmed** | Medium | T-H3 job-path; see below |
| Unbounded in-memory `LogEntries` | **Confirmed** | Medium | See below |

### Confirmed — request throttling is opt-in and keyed by client profile headers (Medium)

`RateLimitSettings.enabled` defaults to `false` (`RateLimitSettings.java:9-11`). Both
`ApiRateLimitFilter.filter` (`:27-29`) and `McpRateLimitFilter.filter` (`:33-35`) return before
any check when the setting is null or not `TRUE`. That is T-H2’s first half: expensive REST/MCP
routes have no application throttle unless an operator opts in.

When enabled, both filters build the key as `METHOD:` plus the raw
`Freerouting-Profile-ID` or `Freerouting-Profile-Email` header, else `"anonymous"`
(`ApiRateLimitFilter.java:52-60`; `McpRateLimitFilter.java:74-82`). They do not use a
server-resolved principal or the Bearer token. Combined with the already-confirmed Pass A
key-to-profile unbound identity, a holder of one valid key (or auth-disabled local mode) can
rotate profile labels and receive a separate window per label.

`FixedWindowRateLimiter.MAX_TRACKED_KEYS` (`:13`) is only a cleanup *trigger*.
`cleanupIfNeeded` (`:54-65`) removes entries whose window started more than `2 * windowMs` ago.
Fresh rotating keys never meet that age, so they are never evicted. Cleanup runs only on an
*allowed* increment (`:49-51`); a denied check returns earlier. `check` is `synchronized`, so a
full map scan holds the request lock. Medium stands: default loopback bind plus auth-on is not
unauthenticated Internet DoS. Do not promote to High.

Needed: key from authenticated principal or API key, hard bounded map with eviction of newest
overflow keys, and an explicit default-on policy for non-loopback listeners (Pass E overlap).

### Confirmed — job queue is uncapped and terminal jobs stay in the live list (Medium)

`enqueueJob` (`RoutingJobScheduler.java:252-256`) appends every job to `jobs` with no size or
per-session quota. The daemon loop only starts work while `RUNNING` count is below
`maxParallelJobs = 5` (`:41`, `:70-76`). That is a concurrency cap, not a queue cap.

The only `jobs.removeIf` besides null cleanup is `clearJobs(sessionId)` (`:457-460`). Callers
are GUI only (`BoardFrame.java:1174`, `BoardFrameFileActions.java:57`). REST has no delete/TTL.
`startJob` (`JobProgressResource.java:224`) moves `QUEUED` to `READY_TO_START` without requiring
input; the scheduler then marks missing/unsupported input `INVALID` (`RoutingJobScheduler.java:77-80`,
`:183-185`) and leaves the object in the list. `COMPLETED` / `CANCELLED` / `TERMINATED` /
`TIMED_OUT` likewise remain. Each `RoutingJob` can retain `input`/`output`/`drc` byte arrays and
a transient `board` (`RoutingJob.java:87-112`). The loop copies and `Collections.sort`s the full
list every 250 ms while any job is present (`:53-62`, `:199-200`).

This is T-H4. Medium, not High: authenticated (or auth-disabled) and default loopback. Large
designs amplify heap via the already-confirmed Pass C unbounded upload, which is an amplifier
not a second H row.

Needed: admission quotas, terminal eviction/TTL, and a live queue that does not scan history.

### Confirmed — sessions accumulate with no REST removal or quota (Medium)

`SessionManager.createSession` (`:82-86`) puts every session in a process-wide `HashMap` and
increments statistics. `POST /v1/sessions/create` (`SessionControllerV1.java:152-167`) always
creates. `removeSession` exists (`SessionManager.java:94-96`) but has **no** REST/MCP controller
call site; `SessionControllerV1` has no DELETE. `getActiveSessionsCount` only reports size.

A caller can cheaply grow the map and `GET /v1/sessions/list` responses without enqueueing a
board. Combined with the job-registry finding this is process-state accumulation. Keep Medium.
The unsynchronized `HashMap` remains in “not promoted” as the hunter wrote.

Needed: quotas, idle expiry, authenticated delete, concurrent map.

### Confirmed — job SSE streams leak workers/listeners and redo board serialization (Medium)

`streamOutput` / `streamOutputJson` each allocate `Executors.newSingleThreadScheduledExecutor`
per connection (`JobOutputResource.java:392-449`, `:503-557`). `eventSink.send` is not observed;
`isClosed()` is not checked. Shutdown runs only on `COMPLETED`/`CANCELLED` or a thrown
exception. `INVALID`, `TERMINATED`, `TIMED_OUT`, and a client that disconnects while the job
stays `QUEUED`/`RUNNING` do not stop the executor. `previousOutputChecksums` (`:58-59`) is
static and never cleared.

`streamLogs` (`JobProgressResource.java:394-434`) creates an unused executor and
`job.addLogEntryAddedEventListener` with no matching remove (`RoutingJob.java:475`). Cleanup
depends on a later log event seeing `COMPLETED`/`CANCELLED` or a send exception. Disconnect
without further logs leaves the listener (and idle executor) on the job.

The JSON stream calls `KiCadJsonWriter.write` and CRC every 500 ms per subscriber; the SES
stream re-reads and Base64-encodes changed output every 200 ms. This is the job-side T-H3
analogue. MCP global SSE leak stays Pass B. Medium: owner-checked, not unauthenticated.

Needed: close callbacks, listener/checksum removal, send-failure handling, stream caps,
shared snapshots.

### Confirmed — process-wide in-memory logs are unbounded (Medium)

`LogEntries.entries` is an `ArrayList` (`:12`). `add` (`:68-73`) appends with no max count or
byte budget. `FRLogger.info`/`warn`/`error` all call `logEntries.add`; `debug` does **not**
(`FRLogger.java:293-303` returns null). `clear()` exists but no production API/scheduler path
calls it.

`ApiKeyValidationFilter` logs WARN on missing/invalid Bearer for every non-excluded path
(`:131-145`). That is reachable without a valid key on the API port (excluded `/v1/system/*`
and `/v1/analytics/*` skip the filter). Authenticated parser/API errors add more INFO/WARN/ERROR
rows. Session/job log endpoints scan the whole list (`getEntries`). MCP full-body INFO remains
Pass B confidentiality; here the issue is retained volume.

Medium: default bind is loopback; file logging is separate. Not High.

Needed: ring buffer, cap message size, sample repeated auth failures.

---

Do not add Pass H findings to the risk register until Phase 3. Next hunter: **GPT-5.6 Luna High**
for Passes E (settings/CLI/Docker/installers) and F (CI/supply chain). Confirm those only if Luna
High flags a concrete issue, using **Grok 4.6 Medium**.

