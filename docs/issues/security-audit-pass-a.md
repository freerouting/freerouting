# Security Audit Pass A — REST Authentication and Authorization

**Status:** Hunter complete; Extra High confirmation complete
**Hunter model:** GPT-5.6 Luna Max
**Confirmation model:** Grok 4.6 Extra High
**Date:** 2026-08-21
**Scope:** `api/security`, REST filters/controllers, `BaseController`, API settings, session/job
ownership paths

This is a read-only hunt report. The items below are candidates until the assigned confirmer accepts
them or a maintainer reproduces the behavior with a regression test. No production code was
changed.

## Method

1. Traced the Jersey registration and request-filter order from
   `api/FreeroutingApplication.java`.
2. Traced API-key validation from `ApiKeyValidationFilter` through
   `ApiKeyValidationService` and `GoogleSheetsApiKeyProvider`.
3. Traced profile identity resolution through `BaseController.authenticateUser()`.
4. Audited session and job resource methods for `jobId/sessionId` ownership checks, including
   input, output, logs, DRC, monitoring, and SSE paths.
5. Checked existing authentication and API routing tests for cross-user coverage.

## Candidate findings (unconfirmed)

| Severity | Location | Finding | Exploit scenario | Fix sketch |
| --- | --- | --- | --- | --- |
| High | `src/main/java/app/freerouting/api/BaseController.java:47-81`; `src/main/java/app/freerouting/api/security/ApiKeyValidationService.java:56-79`; `src/main/java/app/freerouting/api/v1/SessionControllerV1.java:153-161` | **Caller API key is not visibly bound to the profile identity used for tenant ownership.** The API-key provider validates only whether a Bearer GUID is present in the configured Google Sheet; `authenticateUser()` accepts a client-supplied `Freerouting-Profile-ID` UUID and uses it as the session owner. | A holder of any valid API key can send another known profile UUID, create a session under that UUID, and then pass the same UUID to the owner checks on jobs and sessions. If profile IDs are intended to represent tenants rather than an untrusted client label, this permits cross-user access. The code contains TODOs for email-to-UUID resolution and an external auth call, so the intended binding is not implemented in this path. | Bind the authenticated API key/provider principal to a server-resolved profile identity; do not treat a caller-controlled profile header as the authorization principal. Add two-user tests covering session, job, output, logs, DRC, monitoring, and SSE access. |
| High | `src/main/java/app/freerouting/Freerouting.java:1201-1220`; `src/main/java/app/freerouting/api/v1/SystemControllerV1.java:130-152` | **Public environment diagnostics can disclose command-line arguments.** Startup stores the complete `args` array in `RuntimeEnvironment.commandLineArguments`; `/v1/system/environment` is explicitly excluded from API-key and environment-host validation. | An operator who supplies a Google key, service-account material, profile token, or another secret as a CLI argument can expose it to any network caller able to reach the public system endpoint. Even without a secret, arguments reveal deployment and filesystem details. | Never expose raw command-line arguments through a public endpoint; redact known secret-bearing options or return a safe allowlist of runtime metadata. Add a test asserting secrets and sensitive paths do not appear in the environment response. |
| Medium | `src/main/java/app/freerouting/api/security/ApiKeyValidationService.java:42-51`; `src/main/java/app/freerouting/api/mcp/McpApiKeyValidationService.java:24-31` | **Missing global settings fail open by disabling authentication.** Both services set `isEnabled = false` when global settings or the corresponding server settings are absent. | If a request can reach a server before settings initialization, or if an alternate startup path constructs the API without settings, protected routes may be treated as unauthenticated. The normal startup lifecycle may make this unreachable, so lifecycle evidence is required. | Fail closed for network-facing services when authentication configuration is unavailable; explicitly model a local-only bootstrap mode if one is required. Add startup-order and null-settings tests. |
| Medium | `src/main/java/app/freerouting/api/security/ApiKeyValidationFilter.java:85-99`; `src/main/java/app/freerouting/api/FreeroutingApplication.java:37-58` | **Broad public-path exclusions require runtime registration verification.** The API filter exempts `/dev/*`, OpenAPI, Swagger, analytics, and system paths. The explicit application class does not list the `api.dev` resources, but startup also sets Jersey package scanning to `app.freerouting.api`. | If package scanning registers development controllers, `/dev/*` is intentionally bypassed by authentication and environment-host validation. This could expose mock job/session operations. If the explicit application set is authoritative, this is not exploitable. | Verify the deployed resource set with an integration test. Remove development resources from production discovery or make the exclusion conditional on a development flag. Keep public documentation endpoints narrowly scoped. |

## Controls verified during the hunt

- `ApiKeyValidationFilter` requires a Bearer token on non-excluded paths when authentication is
  enabled (`ApiKeyValidationFilter.java:112-149`).
- Authentication defaults to enabled in `ApiAuthenticationSettings`; an enabled service with no
  configured provider denies access (`ApiAuthenticationSettings.java:9-15`,
  `ApiKeyValidationService.java:126-159`).
- Issue 650's specific regression appears fixed: when authentication is explicitly disabled, the
  filter returns before inspecting the Authorization header (`ApiKeyValidationFilter.java:112-120`);
  dedicated tests cover missing and empty Bearer headers.
- Session detail and session-log endpoints use `SessionManager.getSession(sessionId, userId)`
  (`SessionControllerV1.java:206-216`, `:252-266`).
- Job input/settings, progress, output, DRC, monitoring, and SSE resources generally resolve the
  job first and then require its session to match the supplied `userId`. Representative paths:
  `JobInputResource.java:174-209`, `JobProgressResource.java:141-158`,
  `JobOutputResource.java:122-141`, and `JobOutputResource.java:598-617`.
- The `listJobs` fallback for an unknown or foreign session returns the authenticated caller's
  own jobs through `listJobs(null, userId)` rather than directly returning the foreign session's
  jobs (`JobProgressResource.java:84-96`). This behavior still needs an API-contract decision but
  was not counted as a confirmed disclosure.
- No cross-user REST integration test was found. Existing `ApiRoutingTest` uses one fixed profile
  and explicitly disables authentication; `ApiKeyValidationFilterTest` covers filter behavior,
  not tenant isolation.

## Candidates not promoted to findings

- The authentication-disabled mode is an explicit operator setting and is documented for local
  plugin use. It is not itself a defect; Pass E must verify that defaults and Docker do not combine
  it with a non-loopback bind.
- The current explicit `FreeroutingApplication.getClasses()` set omits `api.dev` controllers.
  Runtime package scanning must be tested before treating the public `/dev/*` exclusion as a
  vulnerability.
- `RoutingJobScheduler.getJob(jobId)` is globally keyed, but the audited controller methods add a
  session-owner check before returning or mutating the job. The method itself is not a public
  authorization boundary.

## Confirmation

**Confirmer:** Grok 4.6 Extra High (2026-08-21). No new domains hunted. No risk-register rows
added (Phase 3). Hunter “controls verified” block is accepted.

| Candidate | Verdict | Severity after confirm | Reason |
| --- | --- | --- | --- |
| API key not bound to `Freerouting-Profile-ID` | **Confirmed** | High | See below |
| Public `/v1/system/environment` dumps CLI args | **Confirmed** | High | See below |
| Null `globalSettings` disables auth | **Confirmed** | Medium | Reachable as a latching constructor bug; current `main()` usually avoids it |
| Package scan vs `getClasses()` / `/dev/*` | **Confirmed** | Medium | Runtime registration is package scan, not `FreeroutingApplication` |

### Confirmed — API key is not bound to profile identity (High)

`ApiKeyValidationFilter` only decides whether a Bearer token is an allowed *caller*.
`GoogleSheetsApiKeyProvider.validateApiKey` looks the GUID up in a sheet with columns “API Key”
and “Access granted?”. There is no user, tenant, or profile column, and the filter never stores
the key on the request as a principal.

`BaseController.authenticateUser()` then *defines* the tenant as the client-supplied
`Freerouting-Profile-ID` UUID. The email header is a no-op (`TODO: get userId from e-mail`).
The following line is `TODO: authenticate the user by calling the auth endpoint`. The method
returns that UUID unchanged.

`SessionControllerV1.createSession` passes that UUID to `SessionManager.createSession`. Later
owner checks (`getSession(sessionId, userId)`, job then session+user) compare against the same
client-chosen UUID. They prevent access *without* the victim’s profile header; they do not bind
a key to a profile. Any valid key plus a guessed or leaked profile UUID is enough.

This is T-A1. It is High on P4/P5 (shared allowlist keys). It is not unauthenticated auth bypass
(T-A2), so it is not Critical under the threat-model table. Needed: two-profile tests; do not
treat the existing session-owner checks as a complete IDOR defense.

### Confirmed — unauthenticated environment dumps `commandLineArguments` (High)

`Freerouting.main` assigns `runtimeEnvironment.commandLineArguments = String.join(" ", args)`
*before* `applyCommandLineArguments`. `RuntimeEnvironment.commandLineArguments` is a public
serialized field (`host` is the only `transient` field). `SystemControllerV1.getEnvironment`
returns `GSON.toJson(Freerouting.globalSettings.runtimeEnvironment)` with no redaction.

`ApiKeyValidationFilter.isExcludedPath` and `EnvironmentHostValidationFilter` both skip
`v1/system/*`. Any network client who can hit the API port can read the full argv, including
CLI-passed Sheets keys, profile values, or paths. Env-only secrets are not in this object, but
CLI-equivalent flags are.

This is T-A6 / T-D2 overlap. High is correct for secret-bearing argv on a public listener;
otherwise it is still unauthenticated recon.

### Confirmed — missing settings fail open (Medium)

`ApiKeyValidationService` (and the MCP twin) set `isEnabled = false` when
`Freerouting.globalSettings` or the server settings object is null. `getInstance()` caches that
boolean for the process. `validateApiKey` then returns `true`.

`Freerouting.main` currently assigns `globalSettings` before `initializeAPI` (~1358), and
`AppContextListener` calls `getInstance()` only after Jetty start. A normal GUI/API launch
therefore sees real settings. The defect is still real: any earlier `getInstance()` call, a test
that leaves `globalSettings` null, or an embedder that starts Jetty without the static settings
object **latches auth off** until `resetForTesting()` / process restart. Fail-closed is the
correct network default. Keep Medium, not High, until a production start path is shown to hit
null settings.

MCP constructor is the same pattern; Pass B should not re-open it as a new High.

### Confirmed — REST runtime is package scan, not `getClasses()` (Medium)

`initializeAPI` sets `jersey.config.server.provider.packages=app.freerouting.api` and does **not**
set `jakarta.ws.rs.Application` (MCP does). Jersey therefore scans `app.freerouting.api` and
subpackages. `FreeroutingApplication.getClasses()` omits `api.dev`, `OpenApiResource`,
`SwaggerUIResource`, and MCP types, but that set is **not** what Jetty uses.

Evidence already in-tree: `OpenApiMcpVisibilityTest` starts `initializeAPI` and gets HTTP 200 from
`/openapi/openapi.json` containing `/v1/mcp` and `/.well-known/agent.json`. Those classes are
absent from `getClasses()`. `JobResourceContractTest` only asserts the unused Application set, so
it cannot prove `/dev` is unregistered.

`JobControllerMocked` is `@Path("/dev/jobs")` under `app.freerouting.api.dev`. Combined with the
auth/env-host `/dev/` exclusion, mock job/session routes are unauthenticated. They return static
JSON and do not call `RoutingJobScheduler`, so this is not live tenant data. Medium stands
(attack-surface / impersonation of the real API / Swagger of MCP on the REST port). T-A3.

Public `/v1/analytics/*` and `/v1/system/*` remain intentional exclusions (Pass D / T-A6), not
extra Pass A High items.

### Candidates noted, not promoted

- REST package scan also loads `McpControllerV1` on the API port (`OpenApiMcpVisibilityTest`).
  REST `ApiKeyValidationFilter` does not exclude `v1/mcp`, so those routes still need a Bearer
  when API auth is on. Full confused-deputy review stays Pass B.
- `authenticateUser()` throwing `IllegalArgumentException` is mapped by `ApiExceptionMapper` to
  HTTP 500 with `exception.getMessage()`. Wrong-profile-header is a 500, not 401. Informational
  for Pass A; not a new High.

### Issue 650

Re-checked: auth-disabled short-circuit before header inspection is present; tests cover missing
and empty Bearer. **Do not reopen.**

---

**Next:** Pass B hunter on **GPT-5.6 Luna Max**, or commit this confirmation first. Phase 3
(Grok Extra High) should copy only **Confirmed** rows into `security-audit-risk-register.md`.

