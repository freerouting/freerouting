# Security Audit Pass D — Analytics and Cloud Credentials

**Status:** Confirmed (Grok 4.6 Medium, 2026-08-21)
**Hunter model:** GPT-5.6 Luna Max
**Confirmation model:** Grok 4.6 Medium  
**Scope:** `analytics`, Google Sheets provider, BigQuery, analytics API/filter paths

**Date:** 2026-08-21

## Confirmed findings

None recorded.

## Candidates (unconfirmed)

| Severity | Location | Finding | Exploit scenario | Fix sketch |
| --- | --- | --- | --- | --- |
| High | `src/main/java/app/freerouting/api/v1/AnalyticsControllerV1.java:37-39,104-146,220-269`; `src/main/java/app/freerouting/api/security/ApiKeyValidationFilter.java:93-99`; `src/main/java/app/freerouting/api/EnvironmentHostValidationFilter.java:73-85`; `src/main/java/app/freerouting/analytics/BigQueryClient.java:220-275` | **Public analytics endpoints permit arbitrary BigQuery ingestion using the server service account.** Analytics routes are deliberately excluded from API-key and environment-host validation. The track event name selects a BigQuery table, while user-controlled traits/properties become row fields. | A network caller posts arbitrary `track` or `identify` payloads to the analytics service without a key. The server accepts them and asynchronously uses its configured service-account identity to insert rows into known tables in `freerouting-analytics.freerouting_application`, allowing telemetry poisoning and potentially consuming BigQuery streaming quota/cost. The default API bind is loopback, but the production analytics endpoint is intended to be externally reachable. | Authenticate ingestion with a scoped write credential or signed client envelope, allowlist event/table names and accepted fields, validate identity fields, and apply an enabled rate/size quota at this route. |
| Medium | `src/main/java/app/freerouting/analytics/BigQueryClient.java:220-275`; `src/main/java/app/freerouting/api/v1/AnalyticsControllerV1.java:104-146,220-269`; `src/main/java/app/freerouting/settings/RateLimitSettings.java:7-19` | **Public analytics ingestion creates an unbounded sender thread per event and has no default size/rate guard.** `track` starts one daemon thread and `identify` starts two; the default fixed-window limiter is disabled. | Repeated unauthenticated analytics requests can create large numbers of threads and concurrent GCP calls before the server responds, causing heap/thread exhaustion and additional cloud ingestion cost. This overlaps the resource-DoS scope of Pass H. | Use a bounded executor/queue with rejection or coalescing, cap payload size and pending work, and enable a route-specific limit independent of spoofable profile headers. |
| High when Google Sheets authentication is configured | `src/main/java/app/freerouting/analytics/FRAnalytics.java:463-472,516-523`; `src/main/java/app/freerouting/management/jobs/RoutingJobSchedulerActionThread.java:92-97,157-165`; `src/main/java/app/freerouting/settings/GoogleSheetsProviderSettings.java:9-15`; `src/main/java/app/freerouting/analytics/FreeroutingAnalyticsClient.java:158-185` | **Telemetry serializes and uploads the full global settings object, including the Google Sheets API key.** The key is a normal public field and is not transient. The same event path is used by headless scheduled jobs. | When telemetry is enabled and a server has Google Sheets API-key authentication configured, starting routing or optimization serializes `globalSettings`; the resulting payload includes `apiServer.authentication.googleSheets.googleApiKey` and is sent to `api.freerouting.app` and stored by the analytics service. `bigqueryServiceAccountKey` is transient and was not treated as exposed by this path. `Freerouting.appStarted` also sends the unredacted command-line argument string (`Freerouting.java:1311-1327`), which can contain operator-supplied secrets or sensitive paths. | Build an explicit allowlisted telemetry DTO, redact all credentials and command-line values, and avoid sending complete settings or request/response bodies to a third-party analytics service. Rotate any key exposed in existing telemetry. |
| Medium | `src/main/java/app/freerouting/analytics/FRAnalytics.java:628-649`; `src/main/java/app/freerouting/api/v1/JobProgressResource.java:98-101,160-162`; `src/main/java/app/freerouting/api/v1/SessionControllerV1.java:261-266`; `src/main/java/app/freerouting/api/v1/JobOutputResource.java:175-186` | **REST analytics captures user-controlled request/response data and forwards it to the external analytics backend.** Some large design bodies are shortened, but full job/session responses and other endpoint payloads are passed through unchanged. | An API user’s job metadata, routing settings, file names/paths, session logs, and response content are included in `api_response` or `api_request`, then transmitted to and persisted by the analytics service. A client or operator may reasonably expect board/job data to remain within the routing service. | Do not record bodies by default; use fixed metadata fields and strict length/redaction rules, with an explicit telemetry-consent/configuration gate for any diagnostic samples. |
| Medium | `src/main/java/app/freerouting/api/security/GoogleSheetsApiKeyProvider.java:163-182,261-264`; `src/main/java/app/freerouting/logger/Log4j2ConfigurationFactory.java:63-109` | **Google Sheets provider logs full bearer API keys at DEBUG.** Validation success, denial, unknown-key, malformed-key, and invalid-sheet-row messages concatenate the complete key. File logging defaults to DEBUG. | A valid API key sent in `Authorization: Bearer` or a key read from the configured sheet is written to the configured log file. Anyone with log access, log aggregation access, or a later log disclosure can replay the bearer key. | Never log raw credentials; log a short hash or last-four marker and audit/rotate keys already written to logs. |
| Medium | `src/main/java/app/freerouting/api/security/GoogleSheetsApiKeyProvider.java:207-231,277-281` | **A successful Google Sheets refresh can retain an indefinitely stale authorization cache when the sheet becomes empty, contains only headers, or loses required columns.** Those early returns do not clear the cache or mark the provider unhealthy. | After an operator revokes or removes keys by emptying/malformed-ing the sheet, the previous cached keys remain accepted because the scheduled refresh returns before replacing the cache. A revoked key can continue to authorize requests until a valid refresh or process restart. | Fail closed on malformed/empty refreshes, clear or expire the cache, record a bounded maximum staleness period, and test revocation behavior. |
| Low | `src/main/java/app/freerouting/api/v1/AnalyticsControllerV1.java:122-143,248-265` | **Unauthenticated analytics error responses expose raw BigQuery/client exception messages.** | A malformed event, unavailable table, or service-account/configuration failure can return implementation details such as table/schema or cloud-client diagnostics to an unauthenticated caller. The exact secret content was not established. | Return a fixed correlation-safe error to clients and keep detailed diagnostics in protected server logs. |

## Method

1. Traced the desktop/headless telemetry flow from `FRAnalytics` through
   `FreeroutingAnalyticsClient`, including the payload fields emitted for API calls, routing
   starts, and optimizer starts.
2. Traced the analytics ingestion endpoints and their filter exclusions, then followed
   `AnalyticsControllerV1` into `BigQueryClient` table selection, row-field construction, and
   asynchronous delivery.
3. Reviewed `GoogleSheetsApiKeyProvider` initialization, refresh, cache, validation, logging, and
   URL-to-spreadsheet-ID handling.
4. Checked API rate-limit defaults and the external analytics client’s credential/transport
   handling.
5. Compared the findings with the threat-model items T-D1, T-D2, and T-D3. No production code was
   changed and no risk-register rows were added.

## Confirmation

**Confirmer:** Grok 4.6 Medium (2026-08-21). No new domains hunted. No risk-register rows
added (Phase 3). Threat-model items T-D1 and T-D2 are supported by the candidates below.
T-D3 (Sheets `sheet_url` SSRF) was not promoted by the hunter: `sheet_url` is operator
config, `extractSpreadsheetId` only parses an id, and fetches go to the Google Sheets API
with the configured key. That non-promotion is accepted.

| Candidate | Verdict | Severity after confirm | Reason |
| --- | --- | --- | --- |
| Public analytics → BigQuery as the service account | **Confirmed** | High | Unauthenticated write; event name selects table |
| Unbounded sender thread per event | **Confirmed** | Medium | New daemon thread; rate limit off; Pass H overlap |
| Telemetry serializes `globalSettings` including Sheets key | **Confirmed** | High when Sheets auth is configured | Gson includes non-transient `googleApiKey` |
| REST request/response bodies sent to analytics | **Confirmed** | Medium | `apiEndpointCalled` forwards bodies; some shortening only |
| Sheets provider logs full API keys at DEBUG | **Confirmed** | Medium | File logger default is DEBUG |
| Empty/malformed sheet refresh keeps stale cache | **Confirmed** | Medium | Early `return` leaves `apiKeyCache` and `isHealthy` |
| Analytics errors return `e.getMessage()` | **Confirmed** | Low | Unauthenticated 500; async insert errors are not in the HTTP body |

### Confirmed — public analytics endpoints write to BigQuery (High)

`ApiKeyValidationFilter` and `EnvironmentHostValidationFilter` both skip
`v1/analytics/`. `AnalyticsControllerV1` documents that this is intentional so remote
clients can post without a caller key. `POST /track` and `POST /identify` never inspect
the client's `Authorization: Basic` write key that `FreeroutingAnalyticsClient` sends.

If `bigqueryServiceAccountKey` is set, the controller calls
`BigQueryClient.getInstance(...).track/identify` with attacker-chosen `userId`,
`anonymousId`, `event`, `properties`, and `traits`. `sendPayloadAsync` derives
`tableName` from `event` (`toLowerCase`, spaces/hyphens to `_`) and inserts into
`freerouting-analytics.freerouting_application.<tableName>`. `setIgnoreUnknownValues(true)`
prevents creating arbitrary columns, but known tables (`api_endpoint_called`,
`identifies`, `user_snapshots`, `application_started`, …) can be poisoned. Identify always
writes `identifies` plus `user_snapshots`.

Default self-hosted bind is loopback, so a local plugin API is not the primary exposure.
The production analytics host (`api.freerouting.app`) is meant to be reachable, matching
T-D1. Keep **High** for that deployment. Do not treat this as RCE or cross-project GCP
write: project and dataset ids are hardcoded.

### Confirmed — unbounded BigQuery sender threads (Medium)

`BigQueryClient.sendPayloadAsync` starts a new daemon thread per payload. `track` starts
one; `identify` starts two (`identify` + `upsertUserSnapshot`). The HTTP handler returns
200 after `Thread.start()`, so Jetty is not back-pressured by GCP latency. Default
`RateLimitSettings.enabled` is `false`. Keep **Medium**; Pass H owns the broader
rate-limit/body-size picture.

### Confirmed — telemetry includes the Google Sheets API key (High if configured)

`FRAnalytics.autorouterStarted`, `routeOptimizerStarted`, and `routeOptimizerFinished`
put `GsonProvider.GSON.toJson(globalSettings)` into the event. `GoogleSheetsProviderSettings.googleApiKey`
is a public non-transient field, so it is in that JSON. Headless
`RoutingJobSchedulerActionThread` calls those methods, not only the GUI. Delivery goes
to `https://api.freerouting.app/v1/analytics/track` when telemetry is enabled
(`disableAnalytics` false and `isTelemetryAllowed` default **true**).

`bigqueryServiceAccountKey` and `loggerKey` are `transient` and were not observed in this
Gson path. `appStarted` separately sends the raw argv string (`Freerouting.java:1311-1327`);
that overlaps Pass A’s `/environment` dump and is supporting evidence, not a second High
row. Keep **High when Sheets auth is configured**; otherwise this is settings/PII
telemetry, not a cloud credential leak.

### Confirmed — REST bodies forwarded to analytics (Medium)

`FRAnalytics.apiEndpointCalled` stores `api_request` and `api_response` and posts them
through the same client. `GET /v1/jobs/{id}`, job list, enqueue, and session/job logs
serialize full objects. SES output Base64 is shortened; KiCad JSON upload is shortened to
200 characters. Board bytes are not generally dumped, but job metadata, settings, names,
and log text leave the routing process. Same sink as Pass B’s MCP JSON-RPC logging. Keep
**Medium**.

### Confirmed — Sheets provider logs full API keys at DEBUG (Medium)

`validateApiKey` concatenates the presented key on success, denial, unknown, and invalid
GUID. Refresh logs invalid sheet-row GUIDs the same way. `FRLogger.debug` writes to
Log4j, and `Log4j2ConfigurationFactory` attaches the file appender at **DEBUG** by
default. A valid bearer token used against a healthy provider is therefore persisted in
the log file. Keep **Medium**.

### Confirmed — empty/malformed sheet refresh keeps the previous cache (Medium)

After a successful load, `isHealthy` is true and `apiKeyCache` holds granted keys. A later
refresh that finds no rows, header-only data, or missing required columns logs and
**returns** without replacing the cache or clearing `isHealthy`. `IOException` does set
`isHealthy = false` (fail closed on transport errors). Emptying or breaking the sheet as a
revocation method therefore leaves previously granted keys valid until a well-formed
refresh or process restart. Keep **Medium**.

### Confirmed — analytics 500 responses include `e.getMessage()` (Low)

The catch around `getInstance`/`track`/`identify` interpolates `e.getMessage()` into JSON
on an unauthenticated route. Streaming-insert failures happen on the sender thread and
are not returned to the caller. Typical HTTP leak is constructor/credential/config
failure text, not the service-account JSON. Keep **Low**.

Do not add Pass D findings to the risk register until Phase 3. Next hunter: **GPT-5.6
Luna Max** for Pass G reachability (to choose Extra High vs Medium confirmation) or Pass
H; **GPT-5.6 Luna High** for Passes E and F.

