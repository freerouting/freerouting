# Security Audit Pass C — Design I/O and Job Files

**Status:** Confirmed (Grok 4.6 Medium, 2026-08-21)
**Hunter model:** GPT-5.6 Luna Max
**Confirmation model:** Grok 4.6 Medium
**Date:** 2026-08-21
**Scope:** `io/specctra`, `io/specctra/parser`, KiCad JSON, job input/output and persistence paths

This is a read-only hunt report. Findings remain candidates until Grok 4.6 Medium confirms them or
a maintainer reproduces the behavior with a regression test. No production code was changed.

## Method

1. Traced REST and MCP design upload paths through Base64/raw-JSON decoding, `BoardFileDetails`,
   format detection, statistics extraction, and the scheduler's board loaders.
2. Reviewed the Specctra DSN/SES scanner and scope readers for malformed-input termination,
   token/collection bounds, and stream ownership.
3. Reviewed KiCad JSON DTO deserialization and board/session construction for attacker-controlled
   collection sizes, coordinates, layer indexes, and geometry.
4. Traced job JSON deserialization and persistence from `POST /v1/jobs/enqueue` through
   `RoutingJobScheduler.saveJobToDisk`, including filename normalization and the `save_jobs` flag.
5. Checked the current API server for application-level request-size limits, queue limits, and
   relevant rate limiting.
6. Kept Java `.frb` object deserialization in Pass G: it is a separate serialization boundary and
   has not been promoted as a Pass C network finding.

## Candidate findings (unconfirmed)

| Severity | Location | Finding | Exploit scenario | Fix sketch |
| --- | --- | --- | --- | --- |
| Medium | `src/main/java/app/freerouting/core/RoutingJob.java:146-214`; `src/main/java/app/freerouting/api/v1/JobInputResource.java:289-306` | **Six leading line-ending bytes make format detection loop forever.** `getFileFormat(byte[])` reads six bytes and shifts them in a `while` loop while the first byte is CR/LF. If all six bytes are CR/LF, the buffer never changes and the loop has no exit condition. The Base64 upload endpoint calls this path through `job.setInput()` and `BoardFileDetails.setData()`. | An authenticated caller sends a small Base64 payload whose decoded bytes begin with at least six CR/LF bytes. The request thread never returns from format detection. Repeating the request can exhaust Jetty request workers and deny service. The default API bind is loopback and authentication is enabled, so exposure depends on deployment configuration. | Replace the shift loop with a bounded scan over the available prefix (or inspect the first non-line-ending byte with an index limit). Add a regression test for six CR/LF bytes and a request-level timeout/termination assertion. |
| High when persistence is enabled; otherwise Medium | `src/main/java/app/freerouting/api/v1/JobInputResource.java:92-130`; `src/main/java/app/freerouting/management/jobs/RoutingJobScheduler.java:268-368`; `src/main/java/app/freerouting/core/BoardFileDetails.java:139-197` | **Job persistence resolves attacker-controlled filenames outside the job directory.** The enqueue endpoint deserializes a complete client-supplied `RoutingJob`. Gson reflectively populates `BoardFileDetails.filename` without invoking `setFilename()`. `saveJobToDisk` passes that raw value to `sessionFolderPath.resolve(...)` for both input and output files, without normalization or a containment check. | With `feature_flags.save_jobs=true`, an authenticated API caller can submit an input/output filename containing an absolute path or traversal segments. The immediate persistence callback can create or truncate a file outside the Freerouting data directory as the service account. The current deserialized `dataBytes` field is transient and starts empty, so the directly demonstrated impact is arbitrary empty-file creation/truncation; later persistence of populated output would increase impact. | Treat filenames as untrusted at the API boundary; accept a basename only, normalize and verify `resolved.startsWith(sessionFolderPath)`, reject absolute paths and symlinks, and construct server-owned filenames instead of accepting `RoutingJob.input`/`output` persistence metadata from clients. |
| Medium | `src/main/java/app/freerouting/api/v1/JobInputResource.java:289-306`, `:361-416`; `src/main/java/app/freerouting/core/BoardFileDetails.java:104-119`; `src/main/java/app/freerouting/io/specctra/parser/SpecctraDsnStreamReader.java:39-40`, `:724-754` | **Design uploads and parser work have no application size/complexity bound.** Base64 input is decoded into one byte array, `BoardFileDetails.setData()` scans/parses the whole payload for statistics, and the scheduler retains it for later parsing. Raw KiCad JSON is also accepted as an unbounded `String`; the JSON DTO and DSN scanner grow lists/buffers from attacker-controlled input. No Jetty/Jersey body limit or per-job input limit was found, and request rate limiting defaults to disabled. | A valid caller sends oversized DSN/KiCad JSON or many queued jobs. Memory is amplified by the request string, decoded bytes, statistics strings/JSON, parser collections, board objects, and retained job data; CPU and disk pressure follow when routing or `save_jobs` is enabled. This is a resource-exhaustion candidate owned jointly by Pass C and Pass H. | Enforce a configured maximum request and decoded design size before materializing it; cap queue/job counts and parser collection/token sizes; reject designs exceeding geometry/layer/net limits; apply route-specific cost limits and ensure rate limiting is enabled for exposed deployments. |
| Medium | `src/main/java/app/freerouting/management/jobs/RoutingJobScheduler.java:297-328` | **Job persistence leaks directory streams.** Both `Files.list(userFolderPath)` calls in `saveJobToDisk` are consumed without try-with-resources. Each save can retain a directory stream/file handle until cleanup, and save callbacks run on enqueue and job updates. | When `save_jobs` is enabled, repeated API-created jobs or updates can accumulate directory handles and eventually cause file-descriptor or Windows directory-handle exhaustion. The issue is conditional on persistence and is primarily an availability concern. | Wrap every `Files.list` stream in try-with-resources, avoid repeated full-directory scans, and add a persistence stress test that checks handles/streams are closed. |

## Controls verified during the hunt

- The network scheduler accepts only DSN and KiCad design JSON for routing; FRB and SES are not
  routed by the API scheduler (`RoutingJobScheduler.java:83-95`, `:183-186`).
- `DsnReader.readBoard` and `SesReader.read` close their input streams on success and failure.
  The scanner's nested-scope skipping returns on EOF rather than recursively opening an unbounded
  scope.
- `KiCadJsonReader.readBoard` catches parse/build failures and returns a typed parse error. No
  dynamic class loading, expression evaluation, or process execution was found in the reviewed
  design readers.
- `BoardFileDetails.setFilename()` strips directory components when called through the setter.
  That control does not protect the enqueue path because Gson deserializes the `filename` field
  reflectively and the scheduler later reads the field through `getFilename()`.
- `feature_flags.save_jobs` defaults to `false`; the persistence path is still reachable when an
  operator enables it, and the API does not impose a filename policy at the job boundary.
- The scheduler limits simultaneously running jobs to five, but `RoutingJobScheduler.jobs` has no
  queue-size or per-user quota. The API and MCP fixed-window limiters are separately configurable
  and disabled by default; neither limits request bytes.
- Java object-stream board snapshots and GUI `.frb` loading were observed but are reserved for Pass
  G, where reachability and deserialization filters will be assessed together.

## Candidates not promoted

- `RoutingJob.setInput(File)` uses `FileInputStream.readAllBytes()` without a try-with-resources
  close. This is a local GUI/CLI descriptor leak, not a network-reachable boundary in the reviewed
  API path; retain as low-priority cleanup unless Pass H finds a reachable repetition.
- `BoardFileDetails.getAbsolutePath()` and CLI result/DRC output paths accept operator-selected
  local paths. They are not attacker-controlled through the reviewed REST job upload flow.
- Parser static fields (`scopeIdentifier` and `NumberFormat`) are shared between concurrent
  scanner instances. This can corrupt diagnostics or parsing under concurrency, but no direct
  confidentiality or integrity impact was established; keep it with parser robustness work.

## Confirmation

**Confirmer:** Grok 4.6 Medium (2026-08-21). No new domains hunted. No risk-register rows
added (Phase 3). Hunter “controls verified” and “candidates not promoted” blocks are
accepted. `.frb` / `ObjectInputStream` remains Pass G.

| Candidate | Verdict | Severity after confirm | Reason |
| --- | --- | --- | --- |
| Six leading CR/LF bytes hang format detection | **Confirmed** | Medium | Infinite `while` with no refill; reachable from `POST …/input` |
| Persistence resolves attacker filenames outside the job directory | **Confirmed** | High when `save_jobs=true`; Medium otherwise | Gson writes `filename` without `setFilename()`; `Path.resolve` accepts absolute/traversal names |
| Unbounded design upload / parser work | **Confirmed** | Medium | No app body-size limit; default rate limit off; Pass H overlap |
| `Files.list` directory streams not closed | **Confirmed** | Medium | Two unclosed streams per save; gated on `save_jobs` |

### Confirmed — six leading line-ending bytes hang format detection (Medium)

`RoutingJob.getFileFormat(byte[])` first skips whitespace looking for `{`. If the payload is
not JSON, it reads a six-byte prefix and then:

```177:183:src/main/java/app/freerouting/core/RoutingJob.java
        while (buffer[0] == (byte) 0x0A || buffer[0] == (byte) 0x0D) {
          buffer[0] = buffer[1];
          buffer[1] = buffer[2];
          buffer[2] = buffer[3];
          buffer[3] = buffer[4];
          buffer[4] = buffer[5];
        }
```

The loop never shifts in a terminator, never advances an index, and never reads more bytes.
If all six prefix bytes are CR/LF, `buffer[0]` stays CR/LF forever. Content shorter than six
bytes exits (`bytesRead != 6`). An all-whitespace payload of length ≥ 6 that is not JSON
therefore never returns.

`JobInputResource.uploadInput` Base64-decodes into `job.setInput(byte[])`, which calls
`getFileFormat` and then `BoardFileDetails.setData()`, which calls it again. That path is
authenticated and on the default loopback bind, so this is availability DoS of a request
thread, not unauthenticated remote hang. Keep **Medium**. (Comment text says `0x13`; the
code correctly uses `0x0D`.)

### Confirmed — job persistence uses attacker-controlled filenames (High if `save_jobs`)

`POST /v1/jobs/enqueue` does `GSON.fromJson(requestBody, RoutingJob.class)`.
`BoardFileDetails.filename` is a `@SerializedName("filename")` field. Gson sets it by
reflection and does not call `setFilename()`, so the basename-only stripping in
`BoardFileDetails.java:149-172` never runs.

`enqueueJob` then `saveJob`. That method writes only when
`globalSettings.featureFlags.saveJobs` is true (`FeatureFlagsSettings.saveJobs` defaults to
`false`). `saveJobToDisk` then:

```353:368:src/main/java/app/freerouting/management/jobs/RoutingJobScheduler.java
    if (job.input != null
        && job.input.getFilename() != null
        && !job.input.getFilename().isEmpty()
        && job.input.getData() != null) {
      Path inputFilePath = sessionFolderPath.resolve(job.input.getFilename());
      Files.write(inputFilePath, job.input.getData().readAllBytes());
    }
```

`getData()` always returns a `ByteArrayInputStream` over `dataBytes`; it is never null.
`dataBytes` is `transient`, so default Gson does not deserialize client Base64 into it at
enqueue. The write is therefore **empty-file create/truncate** at
`sessionFolderPath.resolve(clientFilename)`. `Path.resolve` returns an absolute second
argument unchanged, and `../` segments escape the session folder. The same pattern applies
to `job.output`.

Later `uploadInput` replaces `job.input` and, if the new filename is empty, calls
`setFilename(job.name)`, which *does* strip directories. The escape window is the enqueue
`saveJob` (and any later save that still carries the deserialized filename). Impact is
integrity/availability of files writable by the service account, not arbitrary content write
from this path. **High** when operators enable `save_jobs`; **Medium** as a latent defect in
the default (`false`) configuration.

### Confirmed — unbounded design upload and parser work (Medium)

No Jetty/Jersey max-entity-size was found on the API server. Base64 design bytes become a
single `byte[]`; KiCad JSON is an unbounded request `String`. `BoardFileDetails.setData`
CRC-scans the whole buffer and builds `BoardStatistics`. The DSN scanner starts with
`ZZ_BUFFERSIZE = 16 * 1024 * 1024` and can grow. Concurrent jobs are capped at five running,
but the in-memory queue has no size or per-user quota. `FixedWindowRateLimiter` exists but
is off unless configured.

This is resource exhaustion for an authenticated (or auth-disabled) caller, overlapping Pass
H. Keep **Medium**; do not promote to High without evidence of unauthenticated reachability
or a cheaper amplification than “send a large body.”

### Confirmed — `Files.list` directory streams not closed (Medium)

`saveJobToDisk` calls `Files.list(userFolderPath)` twice (`RoutingJobScheduler.java:309` and
`:320`) and never closes the `Stream`. `Files.list` holds a `DirectoryStream` until `close()`.
Each enqueue/update `saveJob` can leak two handles. Gated on `save_jobs`; primarily
availability. Keep **Medium**.

Do not add Pass C findings to the risk register until Phase 3. Next hunter: **GPT-5.6 Luna
Max** for Pass D (analytics/GCP/Sheets) or a Pass G reachability skim to decide Extra High vs
Medium confirmation.

