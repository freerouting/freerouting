# Security Audit Threat Model

**Status:** Complete (Phase 0 threat model)
**Audit plan:** [security-audit-plan.md](security-audit-plan.md)
**Input inventory:** [security-audit-inventory.md](security-audit-inventory.md)  
**Model:** Grok 4.6 Extra High
**Date:** 2026-08-21

This document ranks **what can go wrong** and **which pass must verify it**. It is not a
vulnerability report. Items here are hypotheses until a hunt pass cites file:line and a confirmer
accepts or rejects them. Do not implement fixes from this file.

The frozen `src_v19/` tree is out of scope.

---

## 1. Executive summary

Freerouting is a PCB autorouter that can run as a desktop GUI, a headless CLI, an embedded REST
API, a separate MCP server, or a Docker service. Routing geometry is not the security problem.
The security problem is that **untrusted design files and untrusted HTTP clients** can drive a
long-running Java process that stores jobs, talks to Google APIs, and (in MCP mode) **calls its
own REST API as a privileged HTTP client**.

The two highest-leverage questions for later passes:

1. **Who is the user?** API keys authenticate *a caller*. `Freerouting-Profile-ID` is a
   client-supplied UUID used as the owner of sessions and jobs. If a valid key is not bound to a
   profile, any authenticated client may act as any user (IDOR). `BaseController` currently
   documents that email-to-UUID lookup and an auth-endpoint call are TODOs.
2. **What can MCP reach?** MCP tools are generated from OpenAPI `/v1/*` and forwarded to
   `mcp_server.target_api_base_url`. A mis-set or attacker-influenced base URL is SSRF / confused
   deputy. The internal `X-Internal-Bridge-Token` bypasses MCP API-key checks for the stdio
   bridge.

Default code settings observed in inventory: API bind `http://127.0.0.1:37864`, MCP bind
`http://127.0.0.1:37964`, `authentication.enabled = true`, rate limiting **off**. HTTPS in an
endpoint URL is logged as unimplemented and falls back to HTTP. Docker enables the API with GUI
off and does **not** disable authentication in the image `CMD`.

---

## 2. Deployment personas (exposure multiplier)

Severity in later passes must be scored as **impact × this persona**. The same bug is Critical on
a public host and often Low on loopback with auth on.

| ID | Persona | Typical bind | Auth | Who can reach it |
| --- | --- | --- | --- | --- |
| P1 | Desktop GUI only | no listener | n/a | local user and files they open |
| P2 | Local EDA plugin (KiCad / EasyEDA) | `127.0.0.1` | often **disabled** (documented) | any local process |
| P3 | Local MCP for an LLM agent | `127.0.0.1` API + MCP | often **disabled** | local agent + other local processes |
| P4 | Self-hosted Docker / LAN | often `0.0.0.0` | should be **enabled** | LAN or internet if published |
| P5 | Public cloud API | public HTTP | **enabled**, Google Sheets keys | anyone with or without a key |

**Assumed worst case for ranking:** P4/P5 unless a threat is GUI-file-only (then P1).

Accepted residual (must stay listed, not silently dropped): operators who set
`--api_server.endpoints=http://0.0.0.0:37864` and `--api_server.authentication.enabled=false`
for plugin convenience. That combination is an **operator choice**, but defaults, docs, Docker
`CMD`, and fail-open paths must not *accidentally* produce it.

---

## 3. Principals and capabilities

| Principal | How they authenticate | What they should be able to do | What they must not do |
| --- | --- | --- | --- |
| Anonymous client | none | `GET /v1/system/*`, `POST /v1/analytics/*`, OpenAPI/Swagger, `GET /.well-known/agent.json` | read/write jobs, sessions, logs, DRC, MCP tools |
| API key holder | `Authorization: Bearer` | operate **their** sessions and jobs | access another profile’s jobs/sessions; change server config; read cloud credentials |
| Profile identity | `Freerouting-Profile-ID` (UUID) or email header | ownership tag for sessions/jobs | be spoofable independently of the API key |
| MCP client / LLM | MCP bearer (independent of REST) + profile headers | call MCP tools that proxy REST | point the proxy at arbitrary URLs; skip REST auth; subscribe to other users’ streams |
| Stdio MCP bridge | `X-Internal-Bridge-Token` == process `Freerouting.bridgeToken` | forward stdin JSON-RPC to local MCP | be guessable or forgeable from another process without the token |
| Operator | files, env, CLI | bind, auth on/off, providers, Docker volume | have secrets written into images, logs, or `/v1/system/environment` |
| Cloud services | Google Sheets API key; GCP service account | validate keys; write analytics | be callable as an SSRF gadget; accept unauthenticated write floods |
| Local user (GUI) | OS session | open DSN/SES/`.frb` | have a malicious `.frb` execute code via Java deserialization |

---

## 4. Assets

| Asset | Sensitivity | Where it lives |
| --- | --- | --- |
| Session and job objects (IDs, state, logs) | tenant data | in-memory `SessionManager` / `RoutingJobScheduler`; optional disk under user data |
| Board input/output (DSN, SES, JSON, `.frb`) | IP / design | job payload, user-data `data/U-…/S-…` when `save_jobs` is on |
| API keys (caller secrets) | credential | `Authorization` header; hashed in usage telemetry |
| Google Sheets API key + sheet URL | credential | `ApiAuthenticationSettings.googleSheets` / env |
| BigQuery service-account JSON | credential | `FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY` |
| `Freerouting.bridgeToken` | process secret | static field, sent as `X-Internal-Bridge-Token` |
| MCP tool surface | authority | OpenAPI-derived `/v1/*` except `/v1/mcp` |
| Runtime metrics and `RuntimeEnvironment` | recon | unauthenticated `/v1/system/status` and `/environment` |
| GitHub release artifacts / Docker images | supply chain | Actions + GHCR/Docker Hub |
| Analytics event stream | integrity / PII | BigQuery via unauthenticated ingest on the *server* role |

---

## 5. Trust boundaries

```text
[EDA plugin / browser / LLM]
        |  HTTP (often cleartext)
        v
[Jetty REST :37864] ---- Jersey filters ----> controllers ----> SessionManager / Scheduler
        ^                                              |
        |  HttpClient to target_api_base_url           |  parse DSN / JSON
[Jetty MCP :37964] -- tools/call ----------------------+
        ^
        |  stdio + X-Internal-Bridge-Token
[MCP stdio adapter in Freerouting.main]
        |
[Google Sheets]  [api.freerouting.app analytics]  [BigQuery]
```

| Boundary | Crossing | Controls (as designed) | Failure mode to verify |
| --- | --- | --- | --- |
| Network → REST | Jetty connector | bind host; API key filter; env-host filter; optional rate limit | world bind; auth off; filter exclusions |
| Network → MCP | Jetty + WS + SSE | independent MCP auth; agent card public | WS handshake vs JAX-RS filter mismatch |
| MCP → REST | `HttpClient` | `target_api_base_url` scheme/path/host-port guard; forwarded `Authorization` | SSRF; header injection; tool over-exposure |
| Client → identity | Profile headers | `SessionManager.getSession(id, userId)` | header spoof / key not bound to user |
| Upload → parser | Base64 / JSON / DSN | format detection in `RoutingJob.setInput` | DoS; path traversal on filenames |
| Process → disk | `saveJobToDisk` | folders from UUID short codes | traversal if names are user-controlled |
| GUI → deser | `.frb` / snapshots | Java `ObjectInputStream` | gadget RCE if file is hostile |
| App → Google | Sheets + BigQuery | env-configured secrets | leak in logs/settings dump; unauth ingest |
| CI → users | Actions + Docker | job `permissions`, image `CMD` | secret leak; insecure default container |

---

## 6. Security assumptions and fail-open / fail-closed

Hunters must treat these as **invariants to prove or disprove**, not as facts.

| Assumption | Fail behavior observed in inventory | Pass |
| --- | --- | --- |
| Auth enabled ⇒ deny if no providers | `ApiKeyValidationService` / MCP twin deny when `providers` is empty | A, B |
| Auth disabled ⇒ allow all | both filters return early; both `validateApiKey` return `true` | A, B, E |
| `globalSettings == null` ⇒ auth **disabled** | constructors set `isEnabled = false` | A, B |
| Issue 650 (missing Bearer while auth off) is fixed | filter skips validation when auth is off; tests exist | A (verify, do not reopen blindly) |
| REST and MCP auth are independent | two settings objects, two services, two filters | B |
| Job access is owner-checked | controllers call `getJob(jobId)` **then** `getSession(sessionId, userId)` | A |
| `getJob` itself is not tenant-scoped | `RoutingJobScheduler.getJob` matches id only | A |
| Rate limit default is off | `RateLimitSettings.enabled = false` | H, E |
| Rate-limit key is client identity headers | spoofable `Freerouting-Profile-ID` | H |
| MCP must not target MCP URLs | path prefix `/v1/mcp` and `/.well-known` rejected | B |
| Final MCP URI host/port must match base | extra guard after `UriBuilder` | B |
| HTTPS endpoint URL means TLS | `Freerouting` warns and still opens HTTP | E |
| `/dev/*` is public if registered | excluded from API-key and env-host filters; package scan may register mocks | A, E |
| Analytics ingest is intentionally public | `/v1/analytics/*` excluded from auth | D |
| `.frb` is not a network format | REST uploads DSN/JSON/Base64, not obviously Java serialization | G |
| Docker image is not an open proxy | `CMD` enables API, GUI off, `save_jobs=1`; bind still default loopback unless overridden | E |

---

## 7. Ranked threat catalog

Severity here is **for P4/P5** unless noted. Confirmation still required.

### Critical (break tenant isolation, RCE, or unauthenticated control)

| ID | Threat | STRIDE | Pass | Why it is Critical |
| --- | --- | --- | --- | --- |
| T-A1 | **IDOR / confused identity:** a valid API key plus an arbitrary `Freerouting-Profile-ID` owns or reads another user’s sessions/jobs | Elevation | **A** | Profile UUID is not proven bound to the key. `authenticateUser()` parses the header and has TODOs for email lookup and an auth endpoint. |
| T-A2 | **Auth-off + non-loopback bind** (default, Docker, docs, or fail-open) | Spoofing | **A, E** | Unauthenticated job create/upload/start on a reachable port. |
| T-B1 | **MCP SSRF / confused deputy:** `target_api_base_url` points at metadata, intranet, or a second Freerouting; tools replay caller or env credentials | Information / Elevation | **B** | MCP is an HTTP client. Guards exist; hunters must try redirects, DNS rebinding, IPv6, `file:`, userinfo, and path-only tricks. |
| T-G1 | **Java deserialization RCE** if `ObjectInputStream` is reachable from a network upload or unsandboxed `.frb` | Elevation | **G** | Classic Java gadget risk. Likely GUI/file-only (P1); becomes Critical if any API path deserializes attacker bytes. |

### High (authenticated cross-user, secret leak, unintended authority)

| ID | Threat | STRIDE | Pass | Why it is High |
| --- | --- | --- | --- | --- |
| T-A3 | **Filter exclusions too wide:** `/v1/analytics/*`, `/dev/*`, `/openapi/*`, `/swagger-ui`, `/v1/system/*` | Information / Tampering | **A** | Jersey `provider.packages=app.freerouting.api` may load `api.dev` mocks even if `FreeroutingApplication.getClasses()` omits them. |
| T-A4 | **Fail-open auth** when settings are missing | Spoofing | **A** | `globalSettings == null` disables auth in both API and MCP services. |
| T-A5 | **Missing owner check on a job/session/SSE path** | Elevation | **A** | Pattern is `getJob` then session+user. Any skipped call is IDOR even if T-A1 is false. |
| T-B2 | **`bridgeToken` bypass of MCP auth** | Spoofing | **B** | Equality check on `X-Internal-Bridge-Token`. Verify randomness, logs, stdio-only use, timing, and that REST does not honor the header. |
| T-B3 | **MCP tool catalog includes analytics, system, or destructive job ops** without REST auth forwarding | Elevation | **B** | Registry includes all `/v1/*` except `/v1/mcp`. Forwarding copies `Authorization` if present; env fallbacks for profile headers. |
| T-B4 | **WebSocket/SSE auth weaker than JSON-RPC** | Spoofing | **B** | WS uses handshake headers + `McpWebSocketEndpoint.isAuthorized`; JAX-RS filter may not run on the upgrade. SSE is JAX-RS. |
| T-C1 | **Path traversal / arbitrary write** via job filename or save path | Tampering | **C** | Disk folders use UUID short codes; `RoutingJob.setFilename` and input names need a containment proof. |
| T-D1 | **Unauthenticated BigQuery write** (`POST /v1/analytics/track|identify`) | Tampering | **D** | Intentional for client telemetry; on a public host this is event injection and cost/DoS against GCP. |
| T-D2 | **Secret in logs, error bodies, settings JSON, or `/environment`** | Information | **D, A, E** | `RuntimeEnvironment` includes `commandLineArguments`; Sheets key lives in settings objects. |
| T-E1 | **Operators believe HTTPS is on** while Jetty speaks HTTP | Spoofing | **E** | Explicit fallback in `Freerouting.initializeApiServer` / `initializeMCP`. |

### Medium (DoS, recon, defense-in-depth)

| ID | Threat | STRIDE | Pass |
| --- | --- | --- | --- |
| T-H1 | Unbounded Base64/JSON/DSN → heap/CPU (parser bombs, huge jobs) | DoS | **H, C** |
| T-H2 | Rate limit off; when on, key is spoofable profile header | DoS | **H** |
| T-H3 | SSE/WS client leak (no unregister / unbounded subscribers) | DoS | **H, B** |
| T-H4 | Scheduler accepts unlimited concurrent routing jobs | DoS | **H** |
| T-A6 | Unauthenticated `/v1/system/status` and `/environment` (CPU, RAM, session count, CLI args) | Information | **A, E** |
| T-B5 | Public agent card discloses auth=none and endpoint map | Information | **B** |
| T-E2 | CORS `*` with `allowCredentials(true)` | Elevation (browser) | **E** |
| T-E3 | Docker runs as root, writable `/mnt/freerouting`, API on | Elevation | **E** |
| T-D3 | Google Sheets provider fetches attacker-controlled `sheet_url` (SSRF) | Information | **D** |
| T-C2 | Specctra parser crash / infinite recursion as availability issue | DoS | **C, H** |

### Low / informational

| ID | Threat | Pass |
| --- | --- | --- |
| T-F1 | Over-broad Actions `permissions`; unpinned actions; secret in workflow logs | **F** |
| T-F2 | Vulnerable Gradle/Jetty/Jersey/Gson versions | **F** |
| T-E4 | Docs still show `0.0.0.0` examples more prominently than localhost+auth | **E** |
| T-A7 | `Freerouting-Profile-Email` without implemented mapping | **A** |
| T-G2 | In-process snapshots (`BoardHistory`) — not attacker-controlled if only internal | **G** (likely reject) |

---

## 8. Pass mapping and verification tests

Each pass should try to **confirm or reject** the IDs listed. Tests are behavioral assertions, not
exploit scripts.

### Pass A — REST authn/authz (Luna Max → Grok Extra High)

Threats: T-A1 … T-A7, T-E1 (overlap).

| Check | Suggested test |
| --- | --- |
| T-A1 | Two API keys (or one shared key) + two profile UUIDs: user B must get 404/400 on A’s `jobId`/`sessionId`, not data. |
| T-A2 | Default process: confirm bind is loopback and auth is on. Docker `CMD` and docs must not silently disable both. |
| T-A3 | `GET/POST /dev/*` on a production-like start: 404 (unregistered) or 401 (registered). Same for swagger/openapi. |
| T-A4 | Auth service with null settings: must not serve jobs. |
| T-A5 | Every job/session/SSE method: owner mismatch ⇒ empty 404/400, never 200. |
| T-A6 | `/v1/system/environment` must not contain secrets; decide if CLI args are acceptable recon. |
| Issue 650 | Auth off + no `Authorization` ⇒ 2xx on a protected route **only** when auth is off (regression, not a new bug). |

### Pass B — MCP (Luna Max → Grok Extra High)

Threats: T-B1 … T-B5, T-H3.

| Check | Suggested test |
| --- | --- |
| T-B1 | `target_api_base_url` to `http://127.0.0.1:9`, `http://169.254.169.254/`, MCP self-URL, URL with `@host`, redirect. Must fail closed. |
| T-B2 | Request without bearer but with random `X-Internal-Bridge-Token` ⇒ 401. Token not in logs. REST ignores the header. |
| T-B3 | `tools/list` must not expose an unauthenticated path to mutate jobs; forwarded `Authorization` required when REST auth is on. |
| T-B4 | WS connect with no bearer when MCP auth on ⇒ close; SSE same. |
| T-B5 | Agent card: auth scheme matches live `McpApiKeyValidationService`. |

### Pass C — I/O and files (Luna Max → Grok Medium)

Threats: T-C1, T-C2, T-H1 (overlap).

| Check | Suggested test |
| --- | --- |
| T-C1 | Filenames `../`, absolute paths, `..\\` stay under `GlobalSettings.getUserDataPath()/data`. |
| T-C2 | Deeply nested DSN / huge coordinate lists fail with 400, not hang. |

### Pass D — Analytics and cloud (Luna Max → Grok Medium)

Threats: T-D1 … T-D3.

| Check | Suggested test |
| --- | --- |
| T-D1 | On a self-hosted instance **without** BigQuery key, endpoint must not leak whether other secrets exist; with key, unauthenticated write is an accepted or mitigated residual. |
| T-D2 | Logs and 500 bodies must not include service-account JSON or Sheets keys. |
| T-D3 | Sheets `sheet_url` restricted to Google hosts or a documented allowlist. |

### Pass E — Settings / Docker / installers (Luna High → Grok Medium)

Threats: T-A2, T-E1 … T-E4.

| Check | Suggested test |
| --- | --- |
| Defaults | Fresh `ApiServerSettings` / `McpServerSettings` / `ApiAuthenticationSettings` match localhost + auth on. |
| HTTPS | `https://` endpoint does not claim TLS. |
| Docker | Image user, published ports, `CMD` flags, volume permissions. |
| CORS | `*` + credentials rejected or documented as unsafe. |

### Pass F — CI / supply chain (Luna High → Grok Medium if flagged)

Threats: T-F1, T-F2.

| Check | Suggested test |
| --- | --- |
| Every workflow job has `permissions` and `timeout-minutes`. | |
| Release jobs need `contents: write` only. | |
| Dependency advisory scan (Phase 1) feeds this pass. | |

### Pass G — Deserialization (Luna Max → Extra High iff network-reachable)

Threats: T-G1, T-G2.

| Check | Suggested test |
| --- | --- |
| T-G1 | No REST/MCP path passes attacker bytes to `ObjectInputStream`. `.frb` load is GUI/file only. |
| T-G2 | Internal snapshots only consume bytes the process just wrote. |

If T-G1 is GUI-only, severity drops to High/Medium for P1 (malicious file). Do not inflate to
Critical without a network path.

### Pass H — DoS / limits (Luna Max → Grok Medium)

Threats: T-H1 … T-H4.

| Check | Suggested test |
| --- | --- |
| Body size cap on `/input` and `/input/json`. | |
| Rate limit can be enabled; key should not be a free-form header alone. | |
| Job/thread caps; SSE disconnect unregisters. | |

---

## 9. Out of scope (still listed)

- Cryptanalysis of the autorouter; geometric integrity except as DoS.
- Physical access, malicious operator, social engineering of API-key issuance.
- KiCad/EasyEDA plugin stores **outside** this repo, except in-repo `integrations/` clients.
- `src_v19/`.
- Writing weaponized gadgets or public exploit PoCs.

---

## 10. What hunters must not reopen blindly

- **Issue 650** (auth-off + missing Bearer ⇒ 401) is documented as fixed. Re-test; only file if
  current code regresses.
- Localhost + `authentication.enabled=false` for plugins is **documented operator practice**, not
  automatically a product bug. It becomes a finding if defaults, Docker, or fail-open produce it
  without an explicit flag.
- Analytics client → `api.freerouting.app` is a different trust zone from self-hosted
  `AnalyticsControllerV1` ingest.

---

## 11. Recommended hunt order (after this document)

1. **Pass A** then **Pass B** (highest impact; Extra High confirmation).
2. **Pass G** enough to decide network vs GUI-only (gates whether Extra High is needed).
3. **Passes C, D, H** in parallel.
4. **Passes E, F** (mechanical; Luna High).
5. Phase 1 scanners can run in parallel with A/B but must not block them.

Phase 1 (deterministic scanners) is still outstanding and uses **GPT-5.6 Luna High**. Domain
hunting starts with **Pass A** on **GPT-5.6 Luna Max**.
