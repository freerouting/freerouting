# Security Audit Risk Register

**Status:** Phase 3 triage complete (2026-08-21)
**Audit plan:** [security-audit-plan.md](security-audit-plan.md)
**Threat model:** [security-audit-threat-model.md](security-audit-threat-model.md)
**Triage model:** Grok 4.6 Extra High
**Inputs:** confirmed Pass A–H reports; threat model; [scanner summary](security-audit-scanner-summary.md) (executed 2026-08-21)

Only **confirmed** pass findings belong here. Unpromoted, disputed, and unreproduced items stay
in the pass reports. No GitHub issues were opened. No production code was changed. Remediation
is Phase 4.

## Summary

- Critical: 0
- High: 11
- Medium: 19
- Low: 4
- Informational: 0 (accepted residuals are listed separately, not as open findings)

| ID | Severity | Pass | Title |
| --- | --- | --- | --- |
| [AUDIT-001](#audit-001--api-key-is-not-bound-to-profile-identity) | High | A | API key is not bound to profile identity |
| [AUDIT-002](#audit-002--unauthenticated-environment-endpoint-exposes-command-line-arguments) | High | A | Unauthenticated `/environment` exposes CLI arguments |
| [AUDIT-003](#audit-003--mcp-forwards-credentials-to-any-configured-http-host) | High | B | MCP forwards credentials to any configured HTTP host |
| [AUDIT-004](#audit-004--network-mcp-can-read-and-write-arbitrary-local-paths) | High | B | Network MCP can read and write arbitrary local paths |
| [AUDIT-005](#audit-005--unredacted-request-and-response-bodies-in-logs-and-analytics) | High | B, D | Unredacted request/response bodies in logs and analytics |
| [AUDIT-006](#audit-006--telemetry-uploads-global-settings-including-the-google-sheets-api-key) | High | D | Telemetry uploads global settings including the Sheets API key |
| [AUDIT-007](#audit-007--unauthenticated-analytics-ingestion-writes-to-bigquery) | High | D | Unauthenticated analytics ingestion writes to BigQuery |
| [AUDIT-008](#audit-008--job-persistence-uses-attacker-controlled-filenames) | High | C | Job persistence uses attacker-controlled filenames |
| [AUDIT-009](#audit-009--configured-https-endpoints-silently-speak-plaintext-http) | High | E | Configured HTTPS endpoints silently speak plaintext HTTP |
| [AUDIT-010](#audit-010--gui-frb-loading-uses-unrestricted-java-deserialization) | High | G | GUI `.frb` loading uses unrestricted Java deserialization |
| [AUDIT-011](#audit-011--google-sheets-provider-logs-full-api-keys-at-default-debug) | High | D | Google Sheets provider logs full API keys at default DEBUG |
| [AUDIT-012](#audit-012--missing-global-settings-fail-open-and-disable-authentication) | Medium | A | Missing global settings fail open and disable authentication |
| [AUDIT-013](#audit-013--jersey-package-scan-registers-unauthenticated-dev-and-docs-routes) | Medium | A | Jersey package scan registers unauthenticated `/dev` and docs routes |
| [AUDIT-014](#audit-014--mcp-realtime-events-are-global-and-websocket-identity-is-weaker) | Medium | B | MCP realtime events are global and WebSocket identity is weaker |
| [AUDIT-015](#audit-015--process-wide-bridge-token-bypasses-mcp-api-key-checks) | Medium | B | Process-wide bridge token bypasses MCP API-key checks |
| [AUDIT-016](#audit-016--sse-connections-are-not-cleaned-up-on-disconnect) | Medium | B, H | SSE connections are not cleaned up on disconnect |
| [AUDIT-017](#audit-017--six-leading-crlf-bytes-hang-design-format-detection) | Medium | C | Six leading CR/LF bytes hang design format detection |
| [AUDIT-018](#audit-018--design-uploads-and-parser-work-have-no-application-size-bound) | Medium | C, H | Design uploads and parser work have no application size bound |
| [AUDIT-019](#audit-019--empty-or-malformed-sheets-refresh-keeps-a-stale-authorization-cache) | Medium | D | Empty or malformed Sheets refresh keeps a stale authorization cache |
| [AUDIT-020](#audit-020--cors-allows-credentials-with-a-wildcard-origin) | Medium | E | CORS allows credentials with a wildcard origin |
| [AUDIT-021](#audit-021--docker-image-runs-as-root-with-api-and-job-persistence-enabled) | Medium | E | Docker image runs as root with API and job persistence enabled |
| [AUDIT-022](#audit-022--persisted-settings-file-can-store-the-sheets-api-key-without-owner-only-permissions) | Medium | E | Persisted settings file can store the Sheets API key without owner-only permissions |
| [AUDIT-023](#audit-023--request-throttling-is-off-by-default-and-uses-spoofable-keys) | Medium | H | Request throttling is off by default and uses spoofable keys |
| [AUDIT-024](#audit-024--job-and-session-registries-are-unbounded-and-have-no-public-eviction-path) | Medium | H | Job and session registries are unbounded and have no public eviction path |
| [AUDIT-025](#audit-025--in-memory-log-history-has-no-size-or-age-bound) | Medium | H | In-memory log history has no size or age bound |
| [AUDIT-026](#audit-026--internal-board-snapshots-use-unfiltered-java-deserialization) | Medium | G | Internal board snapshots use unfiltered Java deserialization |
| [AUDIT-027](#audit-027--privileged-release-workflows-run-mutable-third-party-actions-by-tag) | Medium | F | Privileged release workflows run mutable third-party actions by tag |
| [AUDIT-028](#audit-028--published-docker-images-use-mutable-base-image-tags) | Medium | F | Published Docker images use mutable base-image tags |
| [AUDIT-029](#audit-029--release-installers-and-assets-are-unsigned-and-have-no-published-checksums) | Medium | E, F | Release installers and assets are unsigned and have no published checksums |
| [AUDIT-030](#audit-030--unauthenticated-analytics-errors-return-raw-exception-messages) | Low | D | Unauthenticated analytics errors return raw exception messages |
| [AUDIT-031](#audit-031--deployment-documentation-does-not-match-secure-runtime-defaults) | Low | E | Deployment documentation does not match secure runtime defaults |
| [AUDIT-032](#audit-032--ci-pre-commit-bootstrap-installs-unpinned-pypi-packages) | Low | F | CI pre-commit bootstrap installs unpinned PyPI packages |
| [AUDIT-033](#audit-033--gradle-has-no-dependency-verification-metadata-or-lockfile) | Low | F | Gradle has no dependency verification metadata or lockfile |
| [AUDIT-034](#audit-034--transitive-netty-common-has-windows-dos-advisories) | Medium | F | Transitive `netty-common` has Windows DoS advisories |

## Triage notes

Severity uses **impact × exposure**. Confirmed pass severities were kept unless the Phase 3
High/Critical bar required an adjustment. The only Extra High severity change:

- **AUDIT-011** was confirmed Medium in Pass D. Phase 3 promotes it to **High** because the
  default file logger is DEBUG and the provider writes complete Bearer keys. That matches the
  plan’s High example “secret in logs.”

No finding is **Critical**. There is no confirmed unauthenticated RCE, no request-driven
cloud-metadata SSRF, and no release artifact that defaults to a world bind **and** authentication
disabled. Docker `CMD` enables the API and `save_jobs` but does **not** disable authentication or
override the loopback bind.

Threat-model T-A1 remains **High**, not Critical: a valid API key is required. It becomes
Critical-adjacent on a shared-key public host (persona P5).

Phase 1 scanners **were run** (secret scan clean; OSV found transitive `netty-common`
advisories — AUDIT-034). Direct Jetty/Jersey/Gson CVE claims from version strings alone are
still not in this register.

## Findings

### AUDIT-001 — API key is not bound to profile identity

- **Severity:** High
- **Status:** Confirmed
- **Pass:** A
- **Threat:** T-A1
- **Location:** `src/main/java/app/freerouting/api/BaseController.java:47-81`;
  `src/main/java/app/freerouting/api/security/ApiKeyValidationService.java:56-79`;
  `src/main/java/app/freerouting/api/v1/SessionControllerV1.java:153-161`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Extra High
- **Scenario:** A holder of any valid API key supplies another known `Freerouting-Profile-ID`.
  Owner checks compare that client-chosen UUID, so the caller can create and use sessions/jobs as
  that profile. Email mapping and an auth-endpoint call are TODOs. Not unauthenticated bypass.
- **Fix:** Bind the authenticated key to a server-resolved principal. Do not treat the profile
  header as the authorization identity.
- **Tests:** Needed: two-profile tests covering session, job, output, logs, DRC, monitoring, and
  SSE. Owner mismatch must be 404/401, never 200.
- **Proposed GitHub issue:** GH-AUDIT-AUTH — Bind API keys to profile identity

### AUDIT-002 — Unauthenticated environment endpoint exposes command-line arguments

- **Severity:** High
- **Status:** Confirmed
- **Pass:** A
- **Threat:** T-A6, T-D2
- **Location:** `src/main/java/app/freerouting/Freerouting.java:1201-1220`;
  `src/main/java/app/freerouting/api/v1/SystemControllerV1.java:130-152`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Extra High
- **Scenario:** `/v1/system/environment` is excluded from API-key and environment-host filters and
  returns `RuntimeEnvironment.commandLineArguments`. CLI-passed Sheets keys, paths, or profile
  values are readable by anyone who can reach the API port.
- **Fix:** Stop returning raw argv. Redact secret-bearing flags or publish an allowlisted metadata
  object.
- **Tests:** Needed: assert a sentinel CLI secret is absent from the environment JSON.
- **Proposed GitHub issue:** GH-AUDIT-SECRETS — Redact credentials from endpoints, logs, and telemetry

### AUDIT-003 — MCP forwards credentials to any configured HTTP host

- **Severity:** High
- **Status:** Confirmed
- **Pass:** B
- **Threat:** T-B1
- **Location:** `src/main/java/app/freerouting/api/mcp/McpControllerV1.java:438-485`, `:384-435`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Extra High
- **Scenario:** `mcp_server.target_api_base_url` accepts any `http`/`https` host. Path guards stop
  request-parameter host injection and redirects are not followed, but the bridge still forwards
  `Authorization` and profile headers to the configured host. Default is loopback; exploitability
  is operator/config control, not an unauthenticated URL rewrite.
- **Fix:** Allowlist the local REST listener; deny private/metadata destinations unless opted in;
  forward credentials only to a verified same-origin REST base.
- **Tests:** Needed: non-allowlisted host rejected; credentials not sent off-origin.
- **Proposed GitHub issue:** GH-AUDIT-MCP — Constrain MCP target, file tools, realtime, and bridge token

### AUDIT-004 — Network MCP can read and write arbitrary local paths

- **Severity:** High
- **Status:** Confirmed
- **Pass:** B
- **Threat:** (file-capability; related to T-B3 breadth)
- **Location:** `src/main/java/app/freerouting/api/mcp/McpControllerV1.java:523-605`;
  `src/main/java/app/freerouting/api/mcp/OpenApiMcpToolRegistry.java:203-301`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Extra High
- **Scenario:** `upload_job_input_from_local_file` calls `Files.readAllBytes` on a caller path.
  `download_job_output_to_local_file` creates parent directories and writes decoded output. No
  workspace root. Reachable with a valid MCP key, auth-disabled MCP, or the stdio bridge. Default
  bind is loopback; remote file read/write if MCP is exposed without auth.
- **Fix:** Keep these tools local-only or capability-gated; enforce
  `toRealPath().startsWith(root)`; reject symlinks and sensitive roots.
- **Tests:** Needed: path outside workspace is rejected; symlink escape is rejected.
- **Proposed GitHub issue:** GH-AUDIT-MCP

### AUDIT-005 — Unredacted request and response bodies in logs and analytics

- **Severity:** High
- **Status:** Confirmed
- **Pass:** B, D (merged producers)
- **Threat:** T-D2
- **Location:** `src/main/java/app/freerouting/api/mcp/McpControllerV1.java:144`, `:199-222`;
  `src/main/java/app/freerouting/analytics/FRAnalytics.java:628-649`; REST call sites in
  `JobProgressResource`, `SessionControllerV1`, `JobOutputResource`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Extra High (B) and Grok 4.6 Medium (D)
- **Scenario:** MCP logs full JSON-RPC request/response at INFO and posts them to analytics.
  REST `apiEndpointCalled` forwards request/response strings; some design bodies are shortened,
  but job/session objects and logs are not. PCB designs, paths, and secrets in tool/API bodies
  leave the process when telemetry or shared logs are on.
- **Fix:** Log method, tool name, correlation ID, and sizes only. Do not send board payloads or
  arbitrary bodies to analytics.
- **Tests:** Needed: sentinel in a tool/API body is absent from logs and analytics properties.
- **Proposed GitHub issue:** GH-AUDIT-SECRETS

### AUDIT-006 — Telemetry uploads global settings including the Google Sheets API key

- **Severity:** High (when Sheets authentication is configured; otherwise settings/PII telemetry)
- **Status:** Confirmed
- **Pass:** D
- **Threat:** T-D2
- **Location:** `src/main/java/app/freerouting/analytics/FRAnalytics.java:463-523`;
  `src/main/java/app/freerouting/settings/GoogleSheetsProviderSettings.java:9-15`;
  `src/main/java/app/freerouting/management/jobs/RoutingJobSchedulerActionThread.java:92-165`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium
- **Scenario:** Autorouter/optimizer telemetry serializes `globalSettings` with Gson.
  `googleApiKey` is public and not `transient`. Delivery goes to `api.freerouting.app` when
  telemetry is allowed (default). `bigqueryServiceAccountKey` is transient and was not included.
  `appStarted` also sends raw argv (supporting evidence for AUDIT-002, not a second High row).
- **Fix:** Allowlisted telemetry DTO; redact credentials. Rotate any key already uploaded.
- **Tests:** Needed: serialized telemetry JSON must not contain `google_api_key` or argv secrets.
- **Proposed GitHub issue:** GH-AUDIT-SECRETS

### AUDIT-007 — Unauthenticated analytics ingestion writes to BigQuery

- **Severity:** High
- **Status:** Confirmed
- **Pass:** D
- **Threat:** T-D1
- **Location:** `src/main/java/app/freerouting/api/v1/AnalyticsControllerV1.java:37-39`,
  `:104-146`, `:220-269`; `src/main/java/app/freerouting/analytics/BigQueryClient.java:220-275`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium
- **Scenario:** `/v1/analytics/*` is excluded from API-key and environment-host filters. Track
  event names select BigQuery tables; attacker fields populate known tables in
  `freerouting-analytics.freerouting_application`. Default self-host bind is loopback; the
  production analytics host is intended to be public. Related DoS: `sendPayloadAsync` starts a
  new daemon thread per event with rate limiting off (not a separate row).
- **Fix:** Authenticate ingestion; allowlist event/table names and fields; bounded executor and
  route-specific size/rate limits.
- **Tests:** Needed: unauthenticated track/identify rejected on a locked-down deployment; event
  names outside the allowlist rejected.
- **Proposed GitHub issue:** GH-AUDIT-ANALYTICS — Authenticate and bound analytics ingestion

### AUDIT-008 — Job persistence uses attacker-controlled filenames

- **Severity:** High when `feature_flags.save_jobs=true` (Docker `CMD` sets this); Medium as a
  latent defect when the flag is false
- **Status:** Confirmed
- **Pass:** C
- **Threat:** T-C1
- **Location:** `src/main/java/app/freerouting/api/v1/JobInputResource.java:92-130`;
  `src/main/java/app/freerouting/management/jobs/RoutingJobScheduler.java:268-368`;
  `src/main/java/app/freerouting/core/BoardFileDetails.java:139-197`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium
- **Scenario:** `POST /v1/jobs/enqueue` deserializes `RoutingJob` with Gson, which sets
  `BoardFileDetails.filename` without `setFilename()` basename stripping. `saveJobToDisk` does
  `sessionFolderPath.resolve(clientFilename)`. Absolute paths and `..` escape the session folder.
  Enqueue `dataBytes` is transient, so the demonstrated write is empty-file create/truncate as
  the service user. Related: two unclosed `Files.list` streams per save
  (`RoutingJobScheduler.java:297-328`).
- **Fix:** Ignore client persistence paths; server-owned basenames; `resolved.startsWith(root)`;
  try-with-resources on `Files.list`.
- **Tests:** Needed: `../` and absolute filenames stay inside the session directory; directory
  streams close.
- **Proposed GitHub issue:** GH-AUDIT-IO — Bound and sandbox design I/O and job files

### AUDIT-009 — Configured HTTPS endpoints silently speak plaintext HTTP

- **Severity:** High
- **Status:** Confirmed
- **Pass:** E (Luna Max design review accepted)
- **Threat:** T-E1
- **Location:** `src/main/java/app/freerouting/Freerouting.java:395-408`, `:532-544`
- **Models:** GPT-5.6 Luna High → GPT-5.6 Luna Max (design) → Grok 4.6 Medium
- **Scenario:** Both API and MCP accept `https://`, warn that TLS is unimplemented, and construct
  a plain `ServerConnector`. `http_allowed=false` rejects only the `http` scheme, so an operator
  who believes TLS is on still serves plaintext. Default bind is loopback HTTP, which is not this
  bug; the bug is fail-open when `https` is configured.
- **Fix:** Reject `https` until a TLS connector and certificates exist. Never bind plaintext for
  an `https` URL. Reverse proxies must use an explicit `http` upstream.
- **Tests:** Needed: `https://127.0.0.1:…` fails closed; no plaintext connector is added.
- **Proposed GitHub issue:** GH-AUDIT-TRANSPORT — Fail closed on HTTPS and lock down CORS

### AUDIT-010 — GUI `.frb` loading uses unrestricted Java deserialization

- **Severity:** High (for desktop persona P1; CVSS: AV:L/AC:L/PR:N/UI:R). Not Critical: no network path, no gadget proven.
- **Status:** Confirmed
- **Pass:** G
- **Threat:** T-G1
- **Location:** `src/main/java/app/freerouting/gui/board/BoardFrame.java:807-843`;
  `src/main/java/app/freerouting/gui/workspace/ports/GuiBoardPersistence.java:31-67`;
  `src/main/java/app/freerouting/gui/board/BoardFrameFileActions.java:31-103`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium (Extra High not required: REST/MCP/CLI reject FRB)
- **Scenario:** A user opens a hostile `.frb` through the GUI file chooser (requires user interaction).
  `ObjectInputStream.readObject()` runs with no `ObjectInputFilter`. Casts happen after construction.
  Drag-and-drop, REST, MCP, and CLI do not take this path.
- **Fix:** Install a strict filter; bound depth/arrays; migrate off Java serialization.
- **Tests:** Needed: unexpected class rejected; network loaders still reject FRB.
- **Proposed GitHub issue:** GH-AUDIT-DESER — Constrain Java deserialization

### AUDIT-011 — Google Sheets provider logs full API keys at default DEBUG

- **Severity:** High (promoted in Phase 3 from Pass D Medium)
- **Status:** Confirmed
- **Pass:** D
- **Threat:** T-D2
- **Location:** `src/main/java/app/freerouting/api/security/GoogleSheetsApiKeyProvider.java:163-182`,
  `:261-264`; `src/main/java/app/freerouting/logger/Log4j2ConfigurationFactory.java:63-109`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium; Extra High triage promotion
- **Scenario:** Success, denial, unknown-key, and invalid-row messages concatenate the complete
  Bearer GUID. The file appender defaults to DEBUG, so a valid key used against a healthy
  provider is persisted on disk and is replayable.
- **Fix:** Never log raw credentials; log a short hash. Audit/rotate keys already written to logs.
- **Tests:** Needed: sentinel Bearer string absent from captured log output at DEBUG.
- **Proposed GitHub issue:** GH-AUDIT-SECRETS

### AUDIT-012 — Missing global settings fail open and disable authentication

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** A (MCP constructor is the same latch; not a second finding)
- **Threat:** T-A4
- **Location:** `src/main/java/app/freerouting/api/security/ApiKeyValidationService.java:42-51`;
  `src/main/java/app/freerouting/api/mcp/McpApiKeyValidationService.java:24-31`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Extra High
- **Scenario:** Null `globalSettings` sets `isEnabled = false` and caches it for the process.
  `validateApiKey` then returns true. Normal `main()` assigns settings before Jetty start, so
  this is a latching constructor/embedder bug rather than the usual GUI launch.
- **Fix:** Fail closed when authentication configuration is unavailable.
- **Tests:** Needed: `getInstance()` with null settings must not authorize protected routes.
- **Proposed GitHub issue:** GH-AUDIT-AUTH

### AUDIT-013 — Jersey package scan registers unauthenticated `/dev` and docs routes

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** A
- **Threat:** T-A3
- **Location:** `src/main/java/app/freerouting/api/security/ApiKeyValidationFilter.java:85-99`;
  `src/main/java/app/freerouting/Freerouting.java` (`jersey.config.server.provider.packages`);
  `src/main/java/app/freerouting/api/dev/JobControllerMocked.java`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Extra High
- **Scenario:** REST uses package scanning of `app.freerouting.api`, not
  `FreeroutingApplication.getClasses()`. `/dev/*`, OpenAPI, and Swagger are auth-excluded.
  Mock controllers return static JSON and do not drive the live scheduler.
- **Fix:** Register an explicit resource set in production; gate `/dev` on a development flag.
- **Tests:** Needed: production-like start returns 404 for `/dev/jobs`, not 200.
- **Proposed GitHub issue:** GH-AUDIT-AUTH

### AUDIT-014 — MCP realtime events are global and WebSocket identity is weaker

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** B (merged: global broadcasts + WS principal)
- **Threat:** T-B4; activity side channel
- **Location:** `src/main/java/app/freerouting/api/mcp/McpRealtimeBridge.java:14-69`;
  `src/main/java/app/freerouting/api/mcp/McpWebSocketEndpoint.java:76-100`;
  `src/main/java/app/freerouting/api/mcp/McpControllerV1.java:252-261`, `:341-344`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Extra High
- **Scenario:** Every SSE/WS subscriber receives `mcp.tool.called` from every client. Payloads
  currently omit job IDs and bodies. WS validates Bearer but accepts any non-blank profile
  label and never calls `authenticateUser()`.
- **Fix:** Key subscribers by the JSON-RPC principal; resolve the same principal on WS; isolate
  broadcasts.
- **Tests:** Needed: two-client isolation; WS with a non-UUID profile is rejected when auth is on.
- **Proposed GitHub issue:** GH-AUDIT-MCP

### AUDIT-015 — Process-wide bridge token bypasses MCP API-key checks

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** B
- **Threat:** T-B2
- **Location:** `src/main/java/app/freerouting/api/mcp/McpApiKeyValidationFilter.java:47-55`;
  `src/main/java/app/freerouting/Freerouting.java:74`, `:652-707`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Extra High
- **Scenario:** `X-Internal-Bridge-Token` equal to the process UUID skips MCP API-key checks on
  every JAX-RS `/v1/mcp*` request. JSON-RPC still calls `authenticateUser()`. The token is random
  and not logged in reviewed code. Medium unless a dump/diagnostic leak is shown.
- **Fix:** Bind the bypass to stdio/loopback IPC; do not use a public static; prove external
  requests cannot use it.
- **Tests:** Needed: random external token rejected; REST does not honor the header.
- **Proposed GitHub issue:** GH-AUDIT-MCP

### AUDIT-016 — SSE connections are not cleaned up on disconnect

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** B, H (merged MCP global sinks + job-specific streams)
- **Threat:** T-H3
- **Location:** `src/main/java/app/freerouting/api/mcp/McpRealtimeBridge.java:14-60`;
  `src/main/java/app/freerouting/api/v1/JobOutputResource.java:392-557`;
  `src/main/java/app/freerouting/api/v1/JobProgressResource.java:394-434`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Extra High (B) and Grok 4.6 Medium (H)
- **Scenario:** MCP SSE sinks remain until a later broadcast sees `isClosed()`. Job output
  streams leave per-connection executors after disconnect unless `COMPLETED`/`CANCELLED`; log
  streams add listeners that are never removed. WebSocket `@OnClose` removal is a control and
  is not this finding.
- **Fix:** Close callbacks; remove listeners/checksums; cap streams; handle send failure.
- **Tests:** Needed: disconnect drops sinks/executors/listeners without waiting for another event.
- **Proposed GitHub issue:** GH-AUDIT-DOS — Bound jobs, sessions, streams, logs, and rate limits

### AUDIT-017 — Six leading CR/LF bytes hang design format detection

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** C
- **Threat:** T-C2
- **Location:** `src/main/java/app/freerouting/core/RoutingJob.java:146-214`;
  `src/main/java/app/freerouting/api/v1/JobInputResource.java:289-306`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium
- **Scenario:** `getFileFormat(byte[])` shifts a six-byte prefix while `buffer[0]` is CR/LF and
  never refills. Six CR/LF bytes loop forever on the request thread. Authenticated; default
  loopback.
- **Fix:** Bounded scan; regression for six CR/LF bytes.
- **Tests:** Needed: payload of six `0x0D`/`0x0A` bytes returns a format or error, never hangs.
- **Proposed GitHub issue:** GH-AUDIT-IO

### AUDIT-018 — Design uploads and parser work have no application size bound

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** C, H
- **Threat:** T-H1
- **Location:** `src/main/java/app/freerouting/api/v1/JobInputResource.java:289-416`;
  `src/main/java/app/freerouting/core/BoardFileDetails.java:104-119`;
  `src/main/java/app/freerouting/io/specctra/parser/SpecctraDsnStreamReader.java:39-40`, `:724-754`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium
- **Scenario:** No Jetty/Jersey max entity size. Base64 and KiCad JSON are fully materialized.
  The DSN scanner starts at 16 MiB and can grow. Rate limiting is off by default. Authenticated
  (or auth-disabled) resource exhaustion.
- **Fix:** Max request and decoded-design size; parser collection caps; enable limits on exposed
  listeners.
- **Tests:** Needed: oversized body rejected before parse; concurrent small jobs still admitted.
- **Proposed GitHub issue:** GH-AUDIT-IO

### AUDIT-019 — Empty or malformed Sheets refresh keeps a stale authorization cache

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** D
- **Threat:** (revocation integrity; related to T-A1 provider)
- **Location:** `src/main/java/app/freerouting/api/security/GoogleSheetsApiKeyProvider.java:207-231`,
  `:277-281`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium
- **Scenario:** After a successful load, an empty, header-only, or column-missing sheet returns
  without clearing `apiKeyCache` or `isHealthy`. Transport errors do fail closed. Emptying the
  sheet is not an effective revocation method.
- **Fix:** Fail closed on malformed/empty refresh; expire the cache; test revocation.
- **Tests:** Needed: emptying the sheet rejects previously granted keys after refresh.
- **Proposed GitHub issue:** GH-AUDIT-AUTH

### AUDIT-020 — CORS allows credentials with a wildcard origin

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** E
- **Threat:** T-E2
- **Location:** `src/main/java/app/freerouting/Freerouting.java:417-435`, `:552-568`;
  `docs/self-hosting.md:266-274`
- **Models:** GPT-5.6 Luna High → Grok 4.6 Medium
- **Scenario:** CORS is off by default. If an operator sets `cors_origins=*`, both servers set
  `allowCredentials(true)` and pass `*` as an origin pattern. REST auth is header-based, which
  limits cookie theft, but a reflected origin plus credentials is still a browser confused-deputy
  path.
- **Fix:** Reject `*` when credentials are enabled; default credentials to false; explicit
  allowlist.
- **Tests:** Needed: `cors_origins=*` does not emit `Access-Control-Allow-Credentials: true`.
- **Proposed GitHub issue:** GH-AUDIT-TRANSPORT

### AUDIT-021 — Docker image runs as root with API and job persistence enabled

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** E
- **Threat:** T-E3
- **Location:** `Dockerfile:26-41`
- **Models:** GPT-5.6 Luna High → Grok 4.6 Medium
- **Scenario:** No `USER`; `VOLUME /mnt/freerouting`; `CMD` enables API and
  `--feature_flags-save_jobs=1`. Not a host escape. Raises impact of AUDIT-004/008/010-class bugs
  inside the container. Authentication remains enabled unless overridden.
- **Fix:** Non-root user; volume ownership; do not enable `save_jobs` unless requested.
- **Tests:** Needed: image process UID is non-root; documented compose examples stay consistent.
- **Proposed GitHub issue:** GH-AUDIT-DEPLOY — Non-root Docker and truthful self-hosting docs

### AUDIT-022 — Persisted settings file can store the Sheets API key without owner-only permissions

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** E
- **Threat:** T-D2 (local persistence)
- **Location:** `src/main/java/app/freerouting/settings/GlobalSettings.java:28-31`, `:378-408`;
  `src/main/java/app/freerouting/settings/GoogleSheetsProviderSettings.java:9-15`
- **Models:** GPT-5.6 Luna High → Grok 4.6 Medium
- **Scenario:** `saveAsJson` writes the whole settings object to
  `java.io.tmpdir/freerouting/freerouting.json` with default filesystem mode. Distinct from
  AUDIT-006 (off-box telemetry).
- **Fix:** Do not serialize the raw key; POSIX 0600/0700; prefer env/OS secret store.
- **Tests:** Needed: after save, file mode is owner-read/write only on POSIX.
- **Proposed GitHub issue:** GH-AUDIT-SECRETS

### AUDIT-023 — Request throttling is off by default and uses spoofable keys

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** H
- **Threat:** T-H2
- **Location:** `src/main/java/app/freerouting/api/ApiRateLimitFilter.java:22-60`;
  `src/main/java/app/freerouting/api/mcp/McpRateLimitFilter.java:28-82`;
  `src/main/java/app/freerouting/api/FixedWindowRateLimiter.java:13-65`;
  `src/main/java/app/freerouting/settings/RateLimitSettings.java:9-19`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium
- **Scenario:** Limiters return when disabled (default). When enabled, the key is the raw profile
  header, which AUDIT-001 does not bind to the API key. Fresh rotating keys are not evicted.
- **Fix:** Key from authenticated principal; hard bounded map; default-on for non-loopback binds.
- **Tests:** Needed: two profile headers with one API key share one bucket.
- **Proposed GitHub issue:** GH-AUDIT-DOS

### AUDIT-024 — Job and session registries are unbounded and have no public eviction path

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** H (merged job queue + session map)
- **Threat:** T-H4
- **Location:** `src/main/java/app/freerouting/management/jobs/RoutingJobScheduler.java:40-76`,
  `:235-260`, `:446-460`;
  `src/main/java/app/freerouting/management/sessions/SessionManager.java:20-21`, `:82-100`;
  `src/main/java/app/freerouting/api/v1/SessionControllerV1.java:149-167`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium
- **Scenario:** Five jobs may run at once, but the in-memory list keeps every job including
  terminal/invalid ones. REST has no delete/TTL (`clearJobs` is GUI-only). Sessions accumulate
  in a process-wide `HashMap` with no REST delete. Authenticated (or auth-disabled) process-state
  growth.
- **Fix:** Admission quotas, terminal TTL, authenticated delete, concurrent bounded maps.
- **Tests:** Needed: enqueue beyond quota is 429; terminal jobs expire; session delete exists.
- **Proposed GitHub issue:** GH-AUDIT-DOS

### AUDIT-025 — In-memory log history has no size or age bound

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** H
- **Threat:** (availability; related to T-H1)
- **Location:** `src/main/java/app/freerouting/logger/LogEntries.java:12-13`, `:62-80`;
  `src/main/java/app/freerouting/logger/FRLogger.java:233-338`;
  `src/main/java/app/freerouting/api/security/ApiKeyValidationFilter.java:131-145`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium
- **Scenario:** Every INFO/WARN/ERROR appends to an `ArrayList`. Invalid API-key warnings are
  reachable without a valid key on non-excluded paths.
- **Fix:** Ring buffer; cap message size; sample repeated auth failures.
- **Tests:** Needed: history stays within a configured maximum after many warnings.
- **Proposed GitHub issue:** GH-AUDIT-DOS

### AUDIT-026 — Internal board snapshots use unfiltered Java deserialization

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** G
- **Threat:** T-G2
- **Location:** `src/main/java/app/freerouting/board/facade/BoardSnapshotManager.java:25-55`;
  `src/main/java/app/freerouting/board/facade/RoutingBoardUndoFacade.java:41-70`;
  `src/main/java/app/freerouting/autoroute/BoardHistory.java:134-155`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium
- **Scenario:** Snapshot bytes are produced and consumed in-process. Not an external RCE
  boundary. A huge untrusted DSN/JSON board can still be serialized/deserialized again (DoS
  amplifier of AUDIT-018).
- **Fix:** Same `ObjectInputFilter` / bounded snapshot format as AUDIT-010, as defense in depth.
- **Tests:** Needed: snapshot restore rejects an unexpected class if a filter is installed.
- **Proposed GitHub issue:** GH-AUDIT-DESER

### AUDIT-027 — Privileged release workflows run mutable third-party actions by tag

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** F
- **Threat:** T-F1
- **Location:** `.github/workflows/create-release.yml`; `.github/workflows/create-snapshot.yml`
  (`olegtarasov/get-tag@v2.1`, `AButler/upload-release-assets@v3.0`,
  `mknejp/delete-release-assets@v1`)
- **Models:** GPT-5.6 Luna High → Grok 4.6 Medium
- **Scenario:** Tag-pinned third-party actions run with `contents: write` and `GITHUB_TOKEN`.
  Triggers are `v*` / `master` / `workflow_dispatch`, not `pull_request_target`. Official
  `actions/*` major tags are **not** this row (`actions/stale@v9` remains required).
- **Fix:** Pin third-party actions to reviewed commit SHAs; minimize token scope.
- **Tests:** Needed: workflow review checklist; no floating third-party tags in release jobs.
- **Proposed GitHub issue:** GH-AUDIT-SUPPLY — Pin Actions, base images, and sign release assets

### AUDIT-028 — Published Docker images use mutable base-image tags

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** F
- **Threat:** T-F1 (image provenance)
- **Location:** `Dockerfile:2`, `:26`; `.github/workflows/docker-release.yml:65-75`;
  `.github/workflows/docker-nightly.yml:58-68`
- **Models:** GPT-5.6 Luna High → Grok 4.6 Medium
- **Scenario:** `eclipse-temurin:25-*-jammy` is not digest-pinned. Rebuilds can silently change
  the base image. Not proof the current tags are malicious.
- **Fix:** Pin by digest; record resolved digests.
- **Tests:** Needed: Dockerfile references `@sha256:…`.
- **Proposed GitHub issue:** GH-AUDIT-SUPPLY

### AUDIT-029 — Release installers and assets are unsigned and have no published checksums

- **Severity:** Medium
- **Status:** Confirmed
- **Pass:** E, F (merged unsigned jpackage + missing checksums)
- **Threat:** T-F1
- **Location:** `scripts/build/create-distribution-*.sh` / `.bat`;
  `.github/workflows/create-release.yml`; `.github/workflows/create-snapshot.yml:146`
- **Models:** GPT-5.6 Luna High → Grok 4.6 Medium
- **Scenario:** jpackage produces DMG/MSI/app-image without signing. Snapshot macOS is invoked
  with `APPLE_DEVELOPER_ID` as `$2`, which the script ignores. GitHub uploads have no checksum
  or detached signature. Docker `provenance: false` is an accepted compatibility choice and is
  not a separate row; consumers still lack a project-published digest to compare.
- **Fix:** Sign/notarize platform packages; publish checksums; fail the workflow if signing
  material is missing when a release is cut.
- **Tests:** Needed: release workflow produces SHA256SUMS; unsigned local script documents the gap.
- **Proposed GitHub issue:** GH-AUDIT-SUPPLY

### AUDIT-030 — Unauthenticated analytics errors return raw exception messages

- **Severity:** Low
- **Status:** Confirmed
- **Pass:** D
- **Threat:** T-D2 (verbose errors)
- **Location:** `src/main/java/app/freerouting/api/v1/AnalyticsControllerV1.java:122-143`,
  `:248-265`
- **Models:** GPT-5.6 Luna Max → Grok 4.6 Medium
- **Scenario:** Constructor/config failures interpolate `e.getMessage()` on public analytics
  routes. Async insert failures are not in the HTTP body. Secret content was not established.
- **Fix:** Fixed client error plus correlation ID; details in server logs only.
- **Tests:** Needed: forced config error body contains no exception message.
- **Proposed GitHub issue:** GH-AUDIT-ANALYTICS

### AUDIT-031 — Deployment documentation does not match secure runtime defaults

- **Severity:** Low
- **Status:** Confirmed
- **Pass:** E
- **Threat:** T-E4
- **Location:** `docs/self-hosting.md:50-60`, `:124-131`; `docs/settings.md:59-70`;
  `Dockerfile:40-41`; `src/main/java/app/freerouting/settings/ApiServerSettings.java:17-23`
- **Models:** GPT-5.6 Luna High → Grok 4.6 Medium
- **Scenario:** Code defaults to `127.0.0.1` and authentication enabled. Self-hosting text says
  the image binds `0.0.0.0` and disables auth in `CMD`; `CMD` does neither. Operators can copy
  insecure examples or misread reachability.
- **Fix:** Generate examples from effective defaults; label network-exposure as an explicit
  exception.
- **Tests:** Needed: doc snippets or a checked-in compose file match `CMD` and settings defaults.
- **Proposed GitHub issue:** GH-AUDIT-DEPLOY

### AUDIT-032 — CI pre-commit bootstrap installs unpinned PyPI packages

- **Severity:** Low
- **Status:** Confirmed
- **Pass:** F
- **Threat:** T-F1
- **Location:** `.github/workflows/pre-commit.yml:23-30`; `.pre-commit-config.yaml:19-22`, `:70-72`
- **Models:** GPT-5.6 Luna High → Grok 4.6 Medium
- **Scenario:** `pip install pre-commit` and some hook extras are unpinned. Job is
  `contents: read` only. Hook **revs** are pinned.
- **Fix:** Pin versions/hashes.
- **Tests:** Needed: workflow installs a pinned requirement file.
- **Proposed GitHub issue:** GH-AUDIT-SUPPLY

### AUDIT-033 — Gradle has no dependency verification metadata or lockfile

- **Severity:** Low
- **Status:** Confirmed
- **Pass:** F
- **Threat:** T-F2 (hygiene only; no named CVE)
- **Location:** `build.gradle:103-235`; no `gradle/verification-metadata.xml`
- **Models:** GPT-5.6 Luna High → Grok 4.6 Medium
- **Scenario:** Direct versions are pinned; wrapper zip has `distributionSha256Sum`. Transitive
  artifacts have no checksum policy. Phase 1 OSV still found drifting Netty (AUDIT-034).
- **Fix:** Enable Gradle verification and lockfiles; add dependency-review/OSV in CI after
  scanners exist.
- **Tests:** Needed: `gradle/verification-metadata.xml` present; CI fails on unsigned/changed
  artifacts.
- **Proposed GitHub issue:** GH-AUDIT-SUPPLY

### AUDIT-034 — Transitive `netty-common` has Windows DoS advisories

- **Severity:** Medium
- **Status:** Confirmed (scanner); verify the resolved version at remediation time
- **Pass:** F (Phase 1 OSV)
- **Threat:** T-F2
- **Location:** Transitive `io.netty:netty-common:4.1.110.Final` via Apache Arrow / Google Cloud
  BigQuery Storage (`build.gradle` Google Cloud BigQuery dependencies)
- **Models:** Phase 1 scanner summary → Extra High triage
- **Scenario:** OSV reports CVE-2024-47535 / CVE-2025-25193: unsafe reading of an environment
  file on Windows can loop on large or malformed input containing null bytes. This is local
  availability, not network RCE through REST/MCP. Direct Jetty/Jersey/Gson/Log4j were not
  reported as unpatched High/Critical by the same scan; version numbers in the scanner summary
  may lag `build.gradle` and must not be treated as findings without a fresh resolve.
- **Fix:** Add a Gradle constraint to `io.netty:netty-common` ≥ the patched 4.1.x line and
  re-run OSV. Keep verification metadata (AUDIT-033) so the pin cannot drift.
- **Tests:** Needed: `dependencyInsight` shows the constrained version; OSV re-scan is clean for
  these GHSA IDs.
- **Proposed GitHub issue:** GH-AUDIT-SUPPLY

## Merged duplicates

| Combined into | Dropped as a standalone register row |
| --- | --- |
| AUDIT-005 | Pass D “REST bodies to analytics” and Pass B “MCP JSON-RPC bodies” |
| AUDIT-007 | Pass D unbounded BigQuery sender threads |
| AUDIT-008 | Pass C unclosed `Files.list` streams |
| AUDIT-014 | Pass B global SSE/WS events and weaker WS identity |
| AUDIT-016 | Pass B MCP SSE sink leak and Pass H job SSE executor/listener leak |
| AUDIT-024 | Pass H unbounded jobs and unbounded sessions |
| AUDIT-029 | Pass E unsigned installers and Pass F missing checksums/signatures |
| AUDIT-001 | T-A7 unimplemented email mapping (same missing principal binding) |
| AUDIT-012 | MCP null-settings fail-open (same latch as REST) |

## Not in the register

Confirmed **rejected / not promoted** by hunters or confirmers, or threat-model items with no
confirmed exploit:

| Item | Disposition |
| --- | --- |
| T-A2 default world bind + auth off | **Rejected as a code default.** Runtime defaults are loopback + auth on. Docker `CMD` does not disable auth. Residual operator risk if docs examples are copied (AUDIT-031). |
| T-A5 skipped owner check on a live job/session/SSE path | **Not found.** Controllers generally `getJob` then session+user. |
| Issue 650 auth-disabled Bearer bypass | **Fixed.** Do not reopen. |
| T-B3 unrestricted tool catalog | **Not promoted.** Tools are REST-filter-gated; file tools are AUDIT-004. |
| T-B5 public agent card | **Accepted residual** (reconnaissance). |
| T-D3 Sheets `sheet_url` SSRF | **Not promoted.** Operator config; fetch is Google Sheets API. |
| T-G1 network `ObjectInputStream` | **Rejected.** REST/MCP/CLI reject FRB. |
| T-F2 known vulnerable Jetty/Jersey/Gson (direct) | **Not confirmed.** OSV did not report unpatched High/Critical on those direct coords. |
| Scanner “GITHUB_TOKEN in `run:` at create-snapshot.yml:42” | **Rejected.** Line 42 is `with: token:` on `delete-release-assets`, not a `run:` shell interpolation. |
| Official `actions/*` major-version tags | **Not promoted**; `actions/stale@v9` remains required. |
| Docker `provenance: false` | **Accepted residual** (multi-arch manifest compatibility). |
| `HttpClient` redirect following | **Control.** Default is `NEVER`. |
| WebSocket `@OnClose` leak | **Control.** WS clients unregister. |

## Accepted residuals

These remain after a complete Phase 4 that fixes the register rows:

1. Operators who bind `0.0.0.0` and set `authentication.enabled=false` for LAN plugins.
2. Public MCP agent card disclosing endpoints and auth scheme.
3. Docker Buildx `provenance: false` for multi-platform manifests, until a compatible
   attestation format is adopted.
4. HTTPS not implemented; after AUDIT-009, `https://` must fail closed rather than pretend TLS.

## Proposed GitHub issues (do not open until a maintainer agrees)

One theme per issue, aligned with Phase 4 PR slicing. Titles are proposals, not live issues.

| Proposed ID | Title | Register rows | Suggested Phase 4 model | Implementation Nuance |
| --- | --- | --- | --- | --- |
| GH-AUDIT-QUICK-NETTY | Enforce Netty dependency constraint against Windows DoS | 034 | Luna High / Flash | Immediate 1-line constraint in `build.gradle.kts` (`4.1.118.Final`) |
| GH-AUDIT-TRANSPORT | Fail closed on `https://`; CORS credentials vs wildcard | 009, 020 | Luna Max | Reject `https://` connectors while TLS is unimplemented |
| GH-AUDIT-AUTH | Bind API keys to profile identity; fail closed; hide `/dev` | 001, 012, 013, 019 | Extra High design, Luna Max implement | Must export authenticated `Principal` into request context for rate limiting |
| GH-AUDIT-SECRETS | Redact credentials from endpoints, logs, telemetry, and settings files | 002, 005, 006, 011, 022 | Luna Max; Grok Medium review | Use `transient` or dedicated `Sensitive<String>` wrapper to block serialization leaks |
| GH-AUDIT-MCP | Constrain MCP target URL, file tools, realtime isolation, and bridge token | 003, 004, 014, 015 | Extra High design, Luna Max implement | Isolate SSE/WS broadcasts by resolved tenant principal |
| GH-AUDIT-IO | Sandbox job filenames; bound uploads; fix CR/LF hang | 008, 017, 018 | Luna Max; Grok Medium review | Strict path normalization and boundary checks |
| GH-AUDIT-ANALYTICS | Authenticate and bound analytics ingestion | 007, 030 | Luna Max; Grok Medium review | Authenticate ingestion; bounded thread executor |
| GH-AUDIT-DOS | Bound jobs, sessions, SSE, logs, and rate limits | 016, 023, 024, 025 | Luna Max; Grok Medium review | Key rate limiters by authenticated `Principal` from GH-AUDIT-AUTH |
| GH-AUDIT-DESER | `ObjectInputFilter` for `.frb` and snapshots | 010, 026 | Luna Max; Grok Medium review | Strict class allowlists and max depth/graph limits |
| GH-AUDIT-DEPLOY | Non-root Docker and truthful self-hosting docs | 021, 031 | Luna High | Add dedicated runtime `USER` in Dockerfile |
| GH-AUDIT-SUPPLY | Pin Actions and base images; sign/checksum release assets | 027, 028, 029, 032, 033 | Luna High | Commit-SHA pinning, lockfiles, verification metadata |

Do not open these until a human agrees. After an issue exists, replace the proposed ID in each
finding with the GitHub URL.

## Recommended Phase 4 order

### Immediate Quick Wins
1. **GH-AUDIT-QUICK-NETTY** (`AUDIT-034`): Add `io.netty:netty-common:4.1.118.Final` constraint in `build.gradle.kts` to immediately resolve the only active CVE.
2. **GH-AUDIT-TRANSPORT** (`AUDIT-009`, `AUDIT-020`): Fail closed immediately when `https://` is configured without TLS; restrict wildcard CORS with credentials.

### Core Identity & Secret Boundaries
3. **GH-AUDIT-AUTH** (`AUDIT-001`, `AUDIT-012`, `AUDIT-013`, `AUDIT-019`): Bind API key to server-resolved tenant identity; export `Principal` into request context.
4. **GH-AUDIT-SECRETS** (`AUDIT-002`, `AUDIT-005`, `AUDIT-006`, `AUDIT-011`, `AUDIT-022`): Redact credentials from argv/endpoints; sanitize logs; mark secret fields `transient` or use `Sensitive<String>` wrappers to prevent Gson telemetry leakage.

### Tool & Input Sandboxing
5. **GH-AUDIT-MCP** (`AUDIT-003`, `AUDIT-004`, `AUDIT-014`, `AUDIT-015`): Restrict MCP target URLs to local allowlist; sandbox local file tools; isolate realtime events.
6. **GH-AUDIT-IO** (`AUDIT-008`, `AUDIT-017`, `AUDIT-018`): Fix CR/LF infinite loop; enforce path boundary checks on job persistence filenames.

### Availability & Hardening
7. **GH-AUDIT-DOS** (`AUDIT-016`, `AUDIT-023`, `AUDIT-024`, `AUDIT-025`): Consume authenticated `Principal` for rate limiting; bound job/session registries and log buffers.
8. **GH-AUDIT-ANALYTICS** (`AUDIT-007`, `AUDIT-030`) & **GH-AUDIT-DESER** (`AUDIT-010`, `AUDIT-026`): Authenticate analytics ingestion; enforce strict `ObjectInputFilter` on deserialization.
9. **GH-AUDIT-DEPLOY** (`AUDIT-021`, `AUDIT-031`) & **GH-AUDIT-SUPPLY** (`AUDIT-027`, `AUDIT-028`, `AUDIT-029`, `AUDIT-032`, `AUDIT-033`): Non-root Docker, action SHA pinning, and verification metadata.

## Phase 1 status

Secret scan of the working tree and recent history was clean. OSV reported transitive
`netty-common` Windows DoS advisories (AUDIT-034). Mechanical greps correlated with Passes C and
G rather than adding new High rows. Re-run OSV after the Netty constraint lands. Optional
SpotBugs/FindSecBugs or Semgrep was not required to close Phase 3.
