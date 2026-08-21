# Security Audit Pass B — MCP Server and Tool Bridge

**Status:** Hunter complete; confirmation complete
**Hunter model:** GPT-5.6 Luna Max
**Confirmation model:** Grok 4.6 Extra High
**Date:** 2026-08-21
**Scope:** `api/mcp`, MCP settings, MCP startup/stdio bridge, MCP documentation

This is a read-only hunt report. Findings remain candidates until Grok 4.6 Extra High confirms
them or a maintainer reproduces the behavior. No production code was changed.

## Method

1. Traced MCP HTTP JSON-RPC authentication and `BaseController.authenticateUser()`.
2. Traced OpenAPI-derived tool discovery and the full `tools/call` forwarding path.
3. Reviewed `target_api_base_url` parsing, path/host guards, redirects, and forwarded headers.
4. Reviewed the internal stdio bridge and `X-Internal-Bridge-Token` bypass.
5. Reviewed SSE/WebSocket authentication and whether realtime events are tenant-scoped.
6. Reviewed custom local-file tools and request/response logging/analytics.
7. Compared the implementation with the existing MCP endpoint and WebSocket tests.

## Candidate findings (unconfirmed)

| Severity | Location | Finding | Exploit scenario | Fix sketch |
| --- | --- | --- | --- | --- |
| High | `src/main/java/app/freerouting/api/mcp/McpControllerV1.java:438-485`; `:384-435` | **MCP can act as a credential-forwarding HTTP client to an arbitrary configured host.** `target_api_base_url` accepts any `http`/`https` URI; the guard rejects MCP paths and preserves host/port after path resolution, but does not restrict the host/IP or scheme to loopback/explicit allowlist. The bridge forwards `Authorization`, profile headers, and environment metadata to that target. | An untrusted or compromised configuration can point MCP at an attacker-controlled or sensitive internal endpoint. A remote MCP caller then causes the server to send the caller's Bearer token/profile identity to that host, or uses the server as an SSRF/confused-deputy client. There is no request field that changes the setting; exploitability depends on configuration control or config injection. | Validate target against an explicit allowlist (normally the local REST listener), reject loopback/link-local/private/metadata destinations unless explicitly opted in, use HTTPS for non-loopback targets, disable redirects, and forward credentials only to a verified same-origin REST endpoint. |
| High | `src/main/java/app/freerouting/api/mcp/OpenApiMcpToolRegistry.java:203-301`; `src/main/java/app/freerouting/api/mcp/McpControllerV1.java:523-604` | **Network-reachable MCP exposes unrestricted local file read-to-upload and output-to-file tools.** `upload_job_input_from_local_file` accepts an arbitrary `filePath` and calls `Files.readAllBytes`; `download_job_output_to_local_file` accepts an arbitrary `filePath`, creates parent directories, and writes decoded job output. No sandbox, root restriction, or confirmation exists. | A caller that can reach MCP (especially when MCP auth is disabled or a key is compromised) can make the MCP process read sensitive local files and transmit them to the configured REST target, or write attacker-controlled routed output to arbitrary writable paths. The custom tool is an intentional local integration feature, but its current authority is unsafe for a network-exposed MCP listener. | Separate local-only tools from network MCP; enforce an operator-configured workspace root and `Path.normalize().startsWith(root)` checks; reject symlinks and sensitive roots; require a capability/consent gate for file tools. |
| High | `src/main/java/app/freerouting/api/mcp/McpControllerV1.java:144`, `:199-203`, `:222`; `src/main/java/app/freerouting/analytics/FRAnalytics.java:387-425` | **MCP request and response bodies are logged and sent to analytics without content redaction.** JSON-RPC requests can contain Base64 PCB designs, local-file-derived data, API payloads, and secrets; the controller logs the full request/response and passes both to `FRAnalytics.apiEndpointCalled`. | A routed board or credential placed in a tool body is copied into local logs and, when analytics is enabled, the external analytics pipeline. Anyone with log access or analytics access can recover sensitive design data. This overlaps Pass D but the MCP path is a direct producer. | Log only method, tool name, correlation ID, and sizes; redact body/response fields; never send board payloads or arbitrary API bodies through analytics. Add a test that a sentinel secret/board payload is absent from logs and analytics properties. |
| Medium | `src/main/java/app/freerouting/api/mcp/McpRealtimeBridge.java:14-69`; `src/main/java/app/freerouting/api/mcp/McpControllerV1.java:252-261`, `:341-344` | **MCP SSE and WebSocket events are global, not tenant-scoped.** Every authenticated SSE/WS subscriber receives `mcp.tool.called` and connection events from every other client. | A user with a valid MCP connection can observe that another tenant called a tool and its status, creating a cross-tenant activity side channel. Current payloads omit job IDs and response bodies, which limits impact but does not establish isolation. | Associate subscribers with the authenticated profile/principal and broadcast only to that principal, or make realtime events explicitly operator-only. Add two-client isolation tests. |
| Medium | `src/main/java/app/freerouting/api/mcp/McpApiKeyValidationFilter.java:47-55`; `src/main/java/app/freerouting/Freerouting.java:652-707` | **A process-wide internal bridge token bypasses MCP API-key validation.** The token is accepted on every JAX-RS `/v1/mcp*` request before MCP authentication, while the stdio bridge sends it on all forwarded requests. | Any process that obtains the in-memory token can invoke MCP without a bearer key. The token is random and intended for the same-process stdio boundary, so this is not an Internet exploit as currently designed; it becomes High if the token is logged, exposed through diagnostics, or accepted across a shared/untrusted local boundary. | Keep the bypass bound to a private loopback channel or authenticated IPC, rotate per bridge instance, avoid a public static token, and add tests proving random/external requests cannot use it. |
| Medium | `src/main/java/app/freerouting/api/mcp/McpWebSocketEndpoint.java:76-100` | **WebSocket identity validation is weaker than JSON-RPC.** With MCP auth enabled, the WS path validates the Bearer key but only requires a nonblank profile ID or email; it does not parse/resolve the UUID through `BaseController.authenticateUser()`. | A valid MCP key can attach an arbitrary profile label to a WS connection. Current WS messages are only an echo/hint and global events contain no profile, so impact is presently a telemetry/isolation inconsistency rather than direct job access. | Resolve the same authenticated principal for WS as for JSON-RPC, validate UUID/email semantics, and use the resolved principal for event filtering. |
| Medium | `src/main/java/app/freerouting/api/mcp/McpRealtimeBridge.java:14-26`, `:46-60`; `src/main/java/app/freerouting/api/mcp/McpControllerV1.java:252-261` | **SSE sinks are not removed on disconnect until a later broadcast observes `sink.isClosed()`.** There is no SSE close callback, and a quiet connection remains in the static map. | Repeated connections that never receive an event can accumulate references and cause memory/broadcast work growth. This is primarily an availability issue and belongs in Pass H confirmation. | Register lifecycle cleanup, remove on send failure/close, cap subscribers, and expose per-client limits. |

## Controls verified during the hunt

- `McpApiKeyValidationFilter` protects `/v1/mcp*` except `/.well-known/*`; it has an explicit
  authentication-disabled short circuit and a separate bridge-token bypass
  (`McpApiKeyValidationFilter.java:20-73`).
- The JSON-RPC endpoint calls `authenticateUser()` before `initialize`, `tools/list`, or
  `tools/call` (`McpControllerV1.java:165-183`). Profile/API-key binding remains the Pass A
  finding and is not fixed by this second check.
- WebSocket authentication is implemented independently because the upgrade path is not ordinary
  JAX-RS request processing. Existing tests cover missing profile, missing environment host, and
  missing authorization when enabled (`McpWebSocketEndpointTest.java:38-107`).
- Target URI construction rejects non-HTTP(S), MCP self-target paths, and host/port changes caused
  by path/query arguments (`McpControllerV1.java:447-485`).
- Java `HttpClient` is created with the default redirect policy, which does not follow redirects;
  this reduces but does not eliminate the configured-host SSRF risk.
- MCP settings default to loopback endpoint and independent authentication enabled
  (`McpServerSettings.java:9-45`).
- MCP tools are intentionally broad: `isMcpEligiblePath` includes every `/v1/*` path except
  `/v1/mcp` (`OpenApiMcpToolRegistry.java:339-340`). Underlying REST authentication is still
  relied on for protected target routes; the registry itself is not an authorization layer.

## Candidates not promoted to findings

- The MCP agent card is public by design; it advertises endpoint locations and the current auth
  scheme. Treat as reconnaissance only unless it exposes a secret or an unreachable internal URL.
- The internal bridge token is not currently logged or returned by the reviewed code. Its
  same-process stdio use is an accepted design boundary pending a test that the token cannot cross
  that boundary.
- `tools/list` exposing system and analytics routes is broad but intentional and still subject to
  the target REST filter. Confirm per-tool authorization in the final MCP review.

## Confirmation

**Confirmer:** Grok 4.6 Extra High (2026-08-21). No new domains hunted. No risk-register rows
added (Phase 3). Hunter “controls verified” block is accepted, including Java `HttpClient`
default `Redirect.NEVER` (no follow of 3xx). MCP constructor fail-open is the same latch as
Pass A Medium; **do not re-open as a new Pass B High.**

| Candidate | Verdict | Severity after confirm | Reason |
| --- | --- | --- | --- |
| `target_api_base_url` credential-forwarding HTTP client | **Confirmed** | High | See below |
| Unrestricted local file read/write MCP tools | **Confirmed** | High | See below |
| Full JSON-RPC bodies in logs and analytics | **Confirmed** | High | See below; analytics citation is `FRAnalytics.java:617-649`, not `:387-425` |
| SSE/WS events are global, not tenant-scoped | **Confirmed** | Medium | See below |
| `X-Internal-Bridge-Token` bypasses MCP API key | **Confirmed** | Medium | See below |
| WebSocket identity weaker than JSON-RPC | **Confirmed** | Medium | See below |
| SSE sinks leak until a later `isClosed()` broadcast | **Confirmed** | Medium | Availability; Pass H overlap |

### Confirmed — MCP forwards caller credentials to any configured HTTP(S) host (High)

`buildUriWithQuery` (`McpControllerV1.java:438-485`) takes `mcp_server.target_api_base_url` (or
the hardcoded `http://127.0.0.1:37864` fallback). It requires `http`/`https`, rejects a base
path that starts with `/v1/mcp` or contains `/.well-known`, and rejects a resolved URI whose
host or port differs from the configured base. Those guards stop **request-parameter** host
injection via `path`/`query`. They do **not** constrain the configured host.

`invokeTool` / custom file tools then call `HttpClient.newHttpClient().send(...)`.
`forwardHeaders` (`:384-412`) copies `Authorization`, profile ID/email (header or process env),
and `Freerouting-Environment-Host` onto that request. JSON-RPC has no field that changes the
target; REST has no settings-mutation API. Exploitability is therefore **operator/config
control** (CLI, settings JSON, compromised host), not an unauthenticated caller rewriting the
URL.

Default `McpServerSettings.targetApiBaseUrl` is loopback (`:44-45`); `Freerouting.main` can
rewrite it to the live REST port (`Freerouting.java:1364-1374`). That is the intended local
bridge. High still holds for T-B1: a non-loopback or attacker-controlled target turns MCP into a
confused-deputy that sends the caller’s Bearer token and profile to that host, or probes an
internal HTTP service as the Freerouting process. Not Critical: not request-driven SSRF, not the
shipping default, and redirects are not followed.

Needed: allowlist (normally the local REST listener), optional private/metadata denylist unless
opted in, and credential forwarding only to a verified same-origin REST base.

### Confirmed — network MCP can read and write arbitrary local paths (High)

`upload_job_input_from_local_file` (`McpControllerV1.java:523-561`) takes `filePath` as a string,
calls `Files.readAllBytes(Path.of(filePath))` with no workspace root, `normalize`/`startsWith`
check, or symlink rejection, Base64-encodes the bytes, and POSTs them to
`/v1/jobs/{jobId}/input` with forwarded auth headers.

`download_job_output_to_local_file` (`:562-605`) GETs job output, Base64-decodes `data`,
`createDirectories` on the parent of `Path.of(filePath)`, and `Files.write`s there. Same lack of
sandbox.

`OpenApiMcpToolRegistry` advertises both tools to any `tools/list` caller (`:203-301`).
`authenticateUser()` runs first (`:165-183`), so this is not unauthenticated when MCP auth and
profile headers are required. It **is** process-user filesystem access for anyone who can call
`tools/call`: valid MCP key, auth-disabled MCP (common local-agent setup), or the stdio bridge.
Default MCP bind is loopback; bind/auth misconfig (T-A2/T-A4 analogue on the MCP listener) makes
this a remote file read/exfil and arbitrary write as the Freerouting user. High, not Critical,
because the shipping default is loopback + auth on. Treat as Critical-adjacent if a deployment
disables MCP auth and binds beyond loopback.

Needed: keep these tools on a local-only / capability-gated path; enforce an operator workspace
root after `toRealPath()`.

### Confirmed — MCP JSON-RPC request and response are logged and sent to analytics (High)

`rpc` logs the raw request body at INFO before parse (`:144`), logs the full JSON-RPC response
at INFO (`:199`, `:203`), then calls `FRAnalytics.apiEndpointCalled(apiMethodTag, requestBody,
response.toString(), userId)` (`:222`).

`FRAnalytics.apiEndpointCalled` (`FRAnalytics.java:628-649`) puts those strings into
`api_request` and `api_response` and posts them on the analytics pipeline. Hunter citation
`:387-425` is the wrong overload region; the live path is `:617-649`. The defect is the same.

OpenAPI-derived `tools/call` arguments include REST `body` (job input Base64, session payloads).
`handleToolsCall` (`McpControllerV1.java:338-349`) embeds the REST response body in the JSON-RPC
result that is then logged and tracked. Custom file-upload JSON-RPC arguments carry `filePath`
not file bytes; the bytes still hit the REST job-input path and any REST-side logging. PCB
designs, profile identifiers, and secrets placed in tool bodies are therefore copied to local
logs and, when analytics is enabled, off-box.

This is T-D2 produced on the MCP path. High is correct when analytics or shared logs are on;
otherwise it is still a local secret/design leak. Pass D should treat this as a confirmed
producer, not a new independent Critical.

Needed: log method, tool name, correlation ID, and sizes only; redact bodies; never send board
payloads through analytics. Regression: a sentinel in a tool body must be absent from log and
analytics properties.

### Confirmed — SSE and WebSocket broadcasts are process-global (Medium)

`McpRealtimeBridge.broadcast` (`:39-70`) iterates every registered `SseEventSink` and every open
`Session`. `events` (`McpControllerV1.java:252-261`) authenticates then registers with no
principal key. `McpWebSocketEndpoint.onOpen` (`:48-52`) registers the same way and immediately
broadcasts `mcp.websocket.connected` to **all** clients.

Tool events (`:341-344`, custom tools `:617-619`) send `{tool, status}` only. No job IDs, no
response bodies, no profile. Cross-tenant impact is activity/timing side channel, not job theft.
Medium stands. Isolation becomes High if later payloads grow or if this is combined with Pass A
shared-key IDOR (any key can subscribe and watch every tenant’s tool names).

Needed: key subscribers by `authenticateUser()` principal; two-client isolation tests.

### Confirmed — process-wide bridge token skips MCP API-key checks (Medium)

`Freerouting.bridgeToken` is a public static `UUID.randomUUID()` (`Freerouting.java:74`).
`McpApiKeyValidationFilter` (`:47-51`) returns before `isAuthenticationEnabled` / Bearer checks
when `X-Internal-Bridge-Token` equals that value. The comparison is on **every** JAX-RS
`/v1/mcp*` request, not only the stdio loopback client.

The stdio bridge (`Freerouting.java:680-708`) POSTs only to `http://127.0.0.1:{localPort}/v1/mcp`
with that header and a resolved profile (env, user profile, or the all-zero UUID). JSON-RPC still
calls `authenticateUser()` after the filter (`McpControllerV1.java:165-183`), so the token is not
a full authz bypass. It is an API-key bypass for anyone who can present the in-memory token on a
reachable MCP port.

The token is not logged or returned in the reviewed code (hunter “not promoted” note is
correct). UUID is not guessable. Medium is correct as a local/confused-boundary finding. Promote
only if a dump, diagnostic endpoint, or shared-host leak is shown (Pass D/E). WebSocket
`isAuthorized` does **not** honor this header (Bearer or auth-off only).

Needed: bind the bypass to the stdio loopback channel or authenticated IPC; stop using a public
static; prove random external requests cannot use it.

### Confirmed — WebSocket identity is weaker than JSON-RPC (Medium)

With MCP auth enabled, `McpWebSocketEndpoint.isAuthorized` (`:76-94`) validates Bearer via
`McpApiKeyValidationService` only. `hasProfile` (`:96-101`) accepts any non-blank profile ID
**or** email; it does not parse a UUID and never calls `BaseController.authenticateUser()`.
`onMessage` (`:68-74`) only echoes a hint. Broadcast payloads do not include the WS profile.

Impact today is telemetry/isolation inconsistency (wrong label; cannot use WS principal to
filter events that are already global). Not direct job access. Medium stands. Same principal
resolution as JSON-RPC is required before SSE/WS can be tenant-scoped.

### Confirmed — SSE sinks are not unregistered on disconnect (Medium)

`registerSseClient` has a matching `removeSseClient`, but `events` never registers a close
callback and never calls remove. Cleanup is only `sink.isClosed()` inside `broadcast`
(`McpRealtimeBridge.java:46-50`). A subscriber that never receives a later event stays in the
static `ConcurrentHashMap`. WebSocket path does remove on `@OnClose`/`@OnError`.

This is connection-retention / broadcast-work growth (availability), not data disclosure.
Medium is acceptable; Pass H should own caps and per-client limits. Not High.

### Candidates noted, not promoted

- Static `McpControllerV1.detectedClientInfo` (`:51`, `:265-271`) is last-`initialize` wins and
  is used as `Freerouting-Environment-Host` fallback for analytics (`:206-211`) and forwarded
  REST calls (`:403-408`). Cross-connection client-name mix-up when the header is omitted.
  Defense-in-depth / telemetry integrity; not a new High.
- `tools/list` exposing system and analytics routes remains intentional and REST-filter-gated,
  as the hunter said. Do not promote without a per-tool authz hole on the REST side (Pass A).
- Public agent card remains reconnaissance only.
- MCP `McpApiKeyValidationService` null-settings fail-open: already Confirmed Medium in Pass A.

---

**Next:** Pass C hunter on **GPT-5.6 Luna Max** (untrusted design I/O / job files). Pass G can
start in parallel enough to decide whether Extra High is needed (network-reachable
`ObjectInputStream` or not). Phase 3 should copy only **Confirmed** rows into
`security-audit-risk-register.md`. Do not commit unless asked.

