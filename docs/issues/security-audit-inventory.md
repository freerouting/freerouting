# Security Audit Inventory

**Status:** Phase 0 complete; ready for threat modeling  
**Audit plan:** [security-audit-plan.md](security-audit-plan.md)  
**Inventory model:** GPT-5.6 Luna High  
**Inventory date:** 2026-08-21

This is an attack-surface index for the full current tree. It is an inventory, not a vulnerability
assessment. Findings must be established in the domain passes and confirmed according to the plan.
The frozen `src_v19/` tree is excluded from the audit except as historical reference.

## 1. Network listeners and protocol surfaces

### REST API

- Startup and Jetty connector construction: `src/main/java/app/freerouting/Freerouting.java`
  (`initializeApiServer`).
- Jersey application registration: `src/main/java/app/freerouting/api/FreeroutingApplication.java`.
- REST settings: `src/main/java/app/freerouting/settings/ApiServerSettings.java`,
  `ApiAuthenticationSettings.java`, and `RateLimitSettings.java`.
- Default API endpoint observed in current settings: `http://127.0.0.1:37864`.
- Configurable endpoint list can be explicitly changed to a network bind, including
  `http://0.0.0.0:37864`; HTTPS is currently logged as unsupported and falls back to HTTP in
  `Freerouting.java`.
- CORS is configured in `Freerouting.initializeApiServer`; it permits credentials and uses
  configured origin patterns. The configured origins and `*` behavior require dedicated review.
- API HTTP/HTTPS policy is controlled by `ApiServerSettings.isHttpAllowed`.

### REST routes

- Sessions: `api/v1/SessionControllerV1.java` (`/v1/sessions/*`).
- Jobs (compatibility façade): `api/v1/JobControllerV1.java` (`/v1/jobs/*`).
- Jobs (split resources): `api/v1/JobInputResource.java`, `JobProgressResource.java`, and
  `JobOutputResource.java`.
- System and environment information: `api/v1/SystemControllerV1.java`
  (`/v1/system/*`).
- Analytics ingestion: `api/v1/AnalyticsControllerV1.java` (`/v1/analytics/*`).
- OpenAPI and Swagger: `api/OpenApiResource.java` and `api/SwaggerUIResource.java`.
- Development endpoints: `api/dev/JobControllerMocked.java`, `SessionControllerMocked.java`, and
  `SystemControllerMocked.java` (`/dev/*`).

### MCP

- Startup and Jetty connector construction: `Freerouting.initializeMCP`.
- MCP settings: `src/main/java/app/freerouting/settings/McpServerSettings.java`.
- JSON-RPC controller: `api/mcp/McpControllerV1.java` (`/v1/mcp`).
- Agent card: `api/mcp/AgentCardController.java` (`/.well-known/agent.json`).
- SSE events: `McpControllerV1.events` and `api/mcp/McpRealtimeBridge.java`
  (`/v1/mcp/events`).
- WebSocket endpoint: `api/mcp/McpWebSocketEndpoint.java` (`/v1/mcp/ws`) with
  `McpWebSocketConfigurator.java`.
- MCP Jersey registration: `api/mcp/McpApplication.java`.
- MCP lifecycle and target API validation: `api/mcp/McpContextListener.java`.
- MCP tool discovery and REST bridge: `api/mcp/OpenApiMcpToolRegistry.java` and
  `McpControllerV1.java`.
- MCP default endpoint observed in current settings: `http://127.0.0.1:37964`.
- MCP target REST API default observed in current settings:
  `http://127.0.0.1:37864`.
- MCP has independent authentication, HTTP policy, CORS, rate limiting, and endpoint settings.

## 2. Authentication, authorization, and request filters

### API authentication

- Registration and priority chain: `api/FreeroutingApplication.java`.
- API key filter: `api/security/ApiKeyValidationFilter.java`.
- Provider orchestration and global enable flag: `api/security/ApiKeyValidationService.java`.
- Provider contract/result: `ApiKeyProvider.java` and `ApiKeyValidationResult.java`.
- Google Sheets provider: `api/security/GoogleSheetsApiKeyProvider.java`.
- Shared auth settings: `settings/ApiAuthenticationSettings.java`.
- Current setting default observed: `authentication.enabled = true`.
- The API filter explicitly allows requests through when authentication is disabled. This is
  intentional local/plugin behavior and must be reviewed together with endpoint bind and operator
  configuration.
- Public API filter exclusions currently include system, analytics, development, OpenAPI, and
  Swagger paths. Verify that each exclusion is intentional and does not expose sensitive data or
  actions.

### MCP authentication

- Filter: `api/mcp/McpApiKeyValidationFilter.java`.
- Service: `api/mcp/McpApiKeyValidationService.java`.
- Agent card advertises auth state: `api/mcp/AgentCardController.java`.
- A process-level internal bridge token is checked by `McpApiKeyValidationFilter` using
  `Freerouting.bridgeToken`; its creation, lifetime, exposure, and comparison require dedicated
  review.
- MCP authentication is independently disabled/enabled through
  `McpServerSettings.authentication`.
- `/.well-known/*` is excluded by the MCP filter; review the full information exposed there.

### Identity and object ownership

- Common profile header resolution: `api/BaseController.java`.
- User/session lookup: `management/sessions/SessionManager.java` and `core/Session.java`.
- Job lookup and lifecycle: `management/jobs/RoutingJobScheduler.java` and `core/RoutingJob.java`.
- Job/session endpoints and resource-level ownership checks:
  `api/v1/SessionControllerV1.java`, `JobControllerV1.java`, `JobInputResource.java`,
  `JobProgressResource.java`, and `JobOutputResource.java`.
- Current code comments identify profile-email-to-UUID resolution and an auth-endpoint call as
  TODOs in `BaseController`; this is a high-priority review boundary, not a finding by itself.

### Other request/response filters

- `api/EnvironmentHostValidationFilter.java`: validates the environment-host header.
- `api/ApiRateLimitFilter.java` and `api/FixedWindowRateLimiter.java`: API fixed-window limits.
- `api/mcp/McpRateLimitFilter.java`: MCP fixed-window limits.
- `api/CorrelationIdFilter.java`: correlation IDs and propagation.
- `api/ApiUsageFilter.java`: usage telemetry and bearer-token hashing.
- `api/ApiAnalyticsFilter.java`: error-response analytics.
- `api/ApiExceptionMapper.java` and `api/NotFoundExceptionMapper.java`: error serialization.
- `api/GsonMessageBodyHandler.java` and `api/JsonStringMessageBodyWriter.java`: JSON body
  handling.

## 3. Untrusted input and file handling

### Board/design input

- REST Base64 upload and KiCad JSON upload:
  `api/v1/JobInputResource.java` and compatibility paths in `JobControllerV1.java`.
- MCP Base64 upload and SES operations: `api/mcp/McpControllerV1.java`.
- Payload DTO: `api/dto/BoardFilePayload.java`.
- Input format detection and storage: `core/RoutingJob.java`.
- Specctra DSN parsing entry points: `io/specctra/DsnReader.java`,
  `io/specctra/parser/Parser.java`, `SpecctraDsnStreamReader.java`, and `DsnFile.java`.
- Specctra grammar and scopes: all 45 current files under `io/specctra` and
  `io/specctra/parser`, particularly `IJFlexScanner`, `ReadScopeParameter`, `Structure`,
  `Library`, `Network`, `RulesReader`, `Wiring`, and polygon/path readers.
- KiCad JSON parsing: `io/kicad/KiCadJsonReader.java`.
- SES import/export: `io/specctra/SesReader.java`, `SesWriter.java`, and
  `SesImportSummary.java`.
- Rules parsing/writing: `io/specctra/RulesReader.java` and `RulesWriter.java`.

### Persistence and paths

- Job scheduling and persisted job state: `management/jobs/RoutingJobScheduler.java`.
- Session persistence and logs: `management/sessions/SessionManager.java`.
- Job output/input resources: `api/v1/JobOutputResource.java`, `JobInputResource.java`.
- GUI `.frb` and workspace state: `gui/board/BoardSavableSubWindow.java`,
  `gui/workspace/WorkspaceSettings.java`, and related GUI serialization classes.
- Serialization-based board snapshots and history:
  `board/facade/BoardSnapshotManager.java`, `board/facade/RoutingBoardUndoFacade.java`,
  `autoroute/BoardHistory.java`, and `board/facade/BasicBoard.java`.
- Filename and path policy is a required dedicated review item; inventory has not established
  whether all persisted names are constrained to an application-owned directory.

### Resource-exhaustion entry points

- Base64 decoding in REST and MCP controllers.
- JSON deserialization into `RoutingJob`, `RouterSettings`, and board payloads.
- DSN/SES/parser recursion and large geometry collections.
- Long-running routing jobs and scheduler concurrency:
  `management/jobs`, `management/sessions`, and `autoroute`.
- SSE/WS client registration:
  `api/mcp/McpRealtimeBridge.java` and job stream resources.
- Rate-limit configuration and request-size enforcement require dedicated review.

## 4. Serialization and reflection

Current `ObjectInputStream` sites in the current tree include:

- `board/facade/BoardSnapshotManager.java` — board snapshot deserialization.
- `board/facade/BasicBoard.java` — custom board state deserialization.
- `board/facade/RoutingBoardUndoFacade.java` — undo state deserialization.
- `autoroute/BoardHistory.java` — historical board state deserialization.
- `gui/workspace/WorkspaceSettings.java` — custom workspace state deserialization.
- `gui/rendering/ColorTableModel.java` and `OtherColorTableModel.java` — GUI state readers.
- `rules/BoardRules.java` — serialized rules state.

Other sensitive mechanisms to inspect:

- Gson reflection and field copying: `util/gson/*`, `util/ReflectionUtil.java`, and settings
  merger classes.
- Java serialization markers and `Serializable` settings classes across `settings/*`.
- No current-tree `Runtime.exec`, `ProcessBuilder`, `XMLDecoder`, or `ScriptEngine` call was
  identified by the initial inventory search. The matching v1.9 `ProcessBuilder` result was
  explicitly excluded.

## 5. Secrets, external services, and telemetry

- API provider configuration: `settings/GoogleSheetsProviderSettings.java`.
- Google Sheets key retrieval/validation and refresh: `api/security/GoogleSheetsApiKeyProvider.java`.
- BigQuery writer and service-account handling: `analytics/BigQueryClient.java`.
- Analytics facade and user/request identifiers: `analytics/FRAnalytics.java` and
  `AnalyticsRequestContext.java`.
- Analytics HTTP client: `analytics/FreeroutingAnalyticsClient.java`, `SegmentClient.java`,
  `NetworkProxyConfig.java`, and `AnalyticsErrorAggregator.java`.
- API analytics controllers/filters: `api/v1/AnalyticsControllerV1.java`,
  `ApiAnalyticsFilter.java`, and `ApiUsageFilter.java`.
- Network proxy settings: `settings/NetworkSettings.java`.
- Sensitive values and payload redaction/logging require a dedicated review. Inventory does not
  treat the presence of a field named `googleApiKey` as proof of leakage.

## 6. Configuration and deployment

- Global configuration and command-line/environment merge:
  `settings/GlobalSettings.java`, `settings/SettingsMerger.java`,
  `settings/sources/*`, and `Freerouting.java`.
- API/MCP defaults and HTTP/CORS/rate limits:
  `settings/ApiServerSettings.java`, `McpServerSettings.java`,
  `ApiAuthenticationSettings.java`, and `RateLimitSettings.java`.
- User-facing settings documentation: `docs/settings.md`, `docs/self-hosting.md`,
  `docs/API/API_authentication.md`, and `docs/API/MCP.md`.
- Container build: `Dockerfile`.
- GitHub Actions workflows:
  `.github/workflows/gradle-build-on-pr.yml`,
  `create-release.yml`, `create-snapshot.yml`,
  `docker-release.yml`, `docker-nightly.yml`,
  `deploy-pages.yml`, `stale.yml`, `pre-commit.yml`, and `gui-a11y.yml`.
- Build/dependency definitions: `build.gradle`, `settings.gradle`, `gradle.properties`,
  `gradle/libs.versions.toml`, `gradle/wrapper/*`, and `gradlew*`.
- Installer and jlink logic is located under `scripts/` and platform packaging definitions;
  search results should be narrowed to installer files during Pass E.
- Integration clients and scripts:
  `integrations/KiCad`, `integrations/Eagle`, `integrations/Target3001`,
  `integrations/EasyEDA`, and `integrations/mcp-server`.

## 7. Known prior work to verify, not blindly reopen

- Issue 650 API authentication-disabled bypass was documented as fixed. Verify current behavior
  in `ApiKeyValidationFilter` and its tests before reporting it.
- Current API and MCP defaults are documented as localhost binds with authentication enabled.
  Verify both settings and actual startup behavior; documentation alone is not evidence.
- Analytics API calls were recently changed to pass request user IDs; verify all authenticated
  controller call sites and the error filter.
- MCP has separate settings and filters from REST; do not assume API auth changes protect MCP.
- `src_v19/` is a historical compatibility tree and is excluded from routine security review.

## 8. Review priorities and handoff

### Priority 1

1. REST authentication and per-user object authorization (Pass A).
2. MCP bridge target URL, internal bridge token, tool allowlist, and WS/SSE auth (Pass B).
3. Network defaults, HTTPS fallback, CORS credentials, and explicit auth-disabled deployments
   (Pass E).

### Priority 2

4. Deserialization and GUI/API reachability of board snapshots (Pass G).
5. Upload/parser limits, path handling, scheduler exhaustion, and stream cleanup (Pass C/H).
6. Google Sheets/BigQuery/analytics secret and PII boundaries (Pass D).

### Priority 3

7. GitHub Actions permissions, dependency provenance, Docker image/runtime, and installers (Pass
   F/E).

The next phase is the threat model. It must use this inventory as input and must not implement
fixes.
