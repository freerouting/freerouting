# API Analytics Architecture & BigQuery Verification

Key facts about how API usage analytics are tracked — established during the analytics tracking investigation.

## Pipeline Overview (Three Hops)

1. **API controller** (`JobControllerV1`, `SessionControllerV1`, `SystemControllerV1`) calls `FRAnalytics.apiEndpointCalled(method, request, response, userId)` on every **successful (2xx) response**.
2. `FRAnalytics.trackAnonymousAction` builds a `Properties` map and calls `FreeroutingAnalyticsClient.track(...)`, which **POSTs** a JSON `Payload` asynchronously (background thread) to `https://api.freerouting.app/v1/analytics/track`.
3. `AnalyticsControllerV1.trackAction` on the server receives that POST and writes the row to **BigQuery** via the singleton `BigQueryClient`, into the table `freerouting-analytics.freerouting_application.api_endpoint_called`. The table name is derived from the event name by lowercasing and replacing spaces/hyphens with underscores.

## Key Invariants

- **`FRAnalytics.permanentUserId` is always `null` in headless/API mode.** `FRAnalytics.setUserId()` is only called from the GUI startup path. Always pass the per-request `userId` UUID from `AuthenticateUser()` to the 4-arg overload `FRAnalytics.apiEndpointCalled(method, request, response, userId)` so that callers can be identified in BigQuery. Never call `FRAnalytics.setUserId()` from a request-handling thread — it writes static state shared across all concurrent requests (race condition).
- **`FRAnalytics.apiEndpointCalled` has two overloads.** The 3-arg overload (no userId) is kept for unauthenticated endpoints (e.g. `GET /v1/system/status`) where no caller UUID is available. All authenticated controller methods must use the 4-arg overload, passing the return value of `AuthenticateUser()`.
- **Error responses (4xx/5xx) are tracked by `ApiAnalyticsFilter`**, not by the controller methods. This JAX-RS `ContainerResponseFilter` (registered in `FreeroutingApplication`) fires for every response with status >= 400 — including 401 Unauthorized responses aborted by `ApiKeyValidationFilter` before the controller runs. Successful (2xx) responses are intentionally skipped by the filter to avoid double-tracking with the richer controller-level calls.
- **`BigQueryClient` is a singleton.** Use `BigQueryClient.getInstance(libraryVersion, serviceAccountKey)` — never `new BigQueryClient(...)` — in `AnalyticsControllerV1`. Constructing a new instance on every request re-authenticates against GCP's token endpoint (network I/O). The singleton is recreated automatically if the service-account key value changes (key rotation).
- **`FreeroutingAnalyticsClient` delivery failures are aggregated, not logged per-failure.** Calling `FRLogger.debug/warn` inside the async send thread floods logs when the analytics endpoint is unreachable. Instead, failures are routed to `AnalyticsErrorAggregator.recordFailure(endpoint, e)`. The first failure in each window is logged immediately at WARN; subsequent failures are counted silently. A daemon thread (`"analytics-error-reporter"`) flushes a per-error-type summary every 60 minutes: at WARN if total <= 50, at ERROR if > 50 (sustained outage).

## BigQuery Table & Column Conventions

- All analytics events land in `freerouting-analytics.freerouting_application.<event_name>` where the table name is the event string lowercased with spaces/hyphens replaced by underscores (e.g. `"API Endpoint Called"` -> `api_endpoint_called`).
- Standard columns present on all rows: `id`, `received_at`, `sent_at`, `timestamp`, `loaded_at`, `uuid_ts`, `user_id`, `anonymous_id`, `event`, `event_text`, `context_library_name`, `context_library_version`.
- API-call-specific columns: `api_method` (e.g. `"GET v1/jobs/..."`), `api_request`, `api_response`.
- `user_id` and `anonymous_id` are both set to the caller's `Freerouting-Profile-ID` UUID for authenticated API calls. Both will be `null` for unauthenticated endpoints (system status, environment) and for any legacy events fired before fix #1 was applied.

## Verifying Analytics in BigQuery

```sql
-- Check recent API events are arriving
SELECT received_at, anonymous_id, api_method, context_library_version
FROM `freerouting-analytics.freerouting_application.api_endpoint_called`
WHERE received_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR)
ORDER BY received_at DESC LIMIT 50;

-- Call count per endpoint (last 24 h)
SELECT api_method, COUNT(*) AS calls
FROM `freerouting-analytics.freerouting_application.api_endpoint_called`
WHERE received_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 24 HOUR)
GROUP BY api_method ORDER BY calls DESC;

-- Confirm null-user-id volume (should be ~0 after fix #1)
SELECT
  COUNTIF(user_id IS NULL OR user_id = '') AS null_user_ids,
  COUNT(*) AS total
FROM `freerouting-analytics.freerouting_application.api_endpoint_called`
WHERE received_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY);

-- Error-response breakdown by endpoint (tracked by ApiAnalyticsFilter)
SELECT api_method, COUNT(*) AS error_count
FROM `freerouting-analytics.freerouting_application.api_endpoint_called`
WHERE received_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY)
  AND (api_response LIKE '%error%' OR api_response LIKE '%not found%')
GROUP BY api_method ORDER BY error_count DESC;
```

## Relevant Source Files

| File | Role |
|---|---|
| `analytics/FRAnalytics.java` | Static facade; `apiEndpointCalled(..., UUID userId)` is the primary call site |
| `analytics/FreeroutingAnalyticsClient.java` | HTTP client posting to `api.freerouting.app/v1/analytics/track` |
| `analytics/AnalyticsErrorAggregator.java` | Aggregates delivery failures; emits first-failure WARN + hourly summary |
| `analytics/BigQueryClient.java` | Singleton GCP BigQuery writer; `getInstance()` avoids per-request re-auth |
| `api/ApiAnalyticsFilter.java` | JAX-RS dual filter; tracks all >= 400 responses centrally |
| `api/FreeroutingApplication.java` | Registers `ApiAnalyticsFilter` alongside existing filters |
