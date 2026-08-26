# Security Audit Remediation Log (Phase 4)

**Status:** Completed (2026-08-21)  
**Branch:** `security-audit-plan`  
**Quality Gates:** Spotless, Checkstyle, and Unit Test suites all passing.

---

## Remediation Summary by Batch

### Quick Win 1: Dependency Hardening (`GH-AUDIT-QUICK-NETTY`)
* **Findings Addressed:** `AUDIT-034`
* **Commit:** `d4310fa6`
* **Changes:** Pinned `io.netty:netty-common` transitive constraint to `4.1.118.Final` in `build.gradle` to resolve Windows DoS vulnerability advisories.

### Quick Win 2: Transport & CORS Security (`GH-AUDIT-TRANSPORT`)
* **Findings Addressed:** `AUDIT-009`, `AUDIT-020`
* **Commit:** `45978fc2`
* **Changes:**
  * Enforced fail-closed behavior on `https://` connectors when TLS key store is missing or misconfigured in `JettyServer.java`.
  * Hardened CORS filter in `CorsHeaderFilter.java` to reject wildcard (`*`) origins when credentials (`Access-Control-Allow-Credentials: true`) are enabled.

### Batch 1: Authentication & Authorization (`GH-AUDIT-AUTH`)
* **Findings Addressed:** `AUDIT-001`, `AUDIT-012`, `AUDIT-013`, `AUDIT-019`
* **Commit:** `2bd8d865`
* **Changes:**
  * Bound API keys to authenticated `Principal` identity across `SessionControllerV1`, `JobControllerV1`, `SessionManager`, and `RoutingJobScheduler`.
  * Ensured `ApiAuthFilter` fails closed (returns 401 Unauthorized) when global settings or API key configurations are uninitialized.
  * Isolated dev endpoints and Swagger docs from unauthenticated package scans.
  * Enforced authorization cache invalidation when Google Sheets provider data refresh fails or returns empty records.

### Batch 2: Secrets, Telemetry & File Permissions (`GH-AUDIT-SECRETS`)
* **Findings Addressed:** `AUDIT-002`, `AUDIT-005`, `AUDIT-006`, `AUDIT-011`, `AUDIT-022`
* **Commit:** `60adde84`
* **Changes:**
  * Sanitized CLI arguments in `RuntimeEnvironment.java` to redact `--google-api-key`, `--token`, `--secret`, and `--password` flags from `/environment` responses and logs.
  * Marked `GoogleSheetsProviderSettings.googleApiKey` as `transient` to prevent accidental serialization into JSON or telemetry payloads.
  * Masked API keys at debug log points in `GoogleSheetsProvider.java`.
  * Enforced POSIX file permissions (`0600` for files, `0700` for directories) on settings persistence in `GlobalSettings.java`.

### Batch 3: MCP Server & Realtime Security (`GH-AUDIT-MCP`)
* **Findings Addressed:** `AUDIT-003`, `AUDIT-004`, `AUDIT-014`, `AUDIT-015`
* **Commit:** `7017f707`
* **Changes:**
  * Implemented path boundary sandboxing (`validateSandboxPath`) in `McpControllerV1.java` rejecting access to sensitive operating system paths (`/etc`, `/proc`, `/sys`, `/root`, `C:\Windows`).
  * Enforced loopback target URL restriction and prohibited cloud metadata service endpoints (`169.254.169.254`, `metadata.google.internal`, `instance-data`).
  * Hardened MCP WebSocket and SSE session authentication and lifecycle cleanup.

### Batch 4: Parsing, I/O & Path Traversal (`GH-AUDIT-IO`)
* **Findings Addressed:** `AUDIT-008`, `AUDIT-017`, `AUDIT-018`
* **Commit:** `137dfa2e`
* **Changes:**
  * Enforced `MAX_INPUT_PAYLOAD_BYTES = 100MB` limit on job input uploads and validated Base64 encoding in `JobInputResource.java`.
  * Hardened Specctra DSN lexer loops against leading CR/LF byte hangs in `DsnReader.java`.
  * Sanitized persisted job filenames using `Path.of(filename).getFileName()` in `BoardFileDetails.java`.

### Batch 5: Rate Limiting, Capacity Bounds, & Safe Deserialization (`GH-AUDIT-DOS` & `GH-AUDIT-DESER`)
* **Findings Addressed:** `AUDIT-010`, `AUDIT-016`, `AUDIT-023`, `AUDIT-024`, `AUDIT-025`, `AUDIT-026`
* **Commit:** `b5282e72`
* **Changes:**
  * Keyed request throttling in `ApiRateLimitFilter.java` and `McpRateLimitFilter.java` by authenticated `SecurityContext.getUserPrincipal()` to prevent IP/header spoofing.
  * Implemented capacity bounds and automatic eviction in `SessionManager.java` (`MAX_SESSIONS = 5,000`) and `RoutingJobScheduler.java` (`MAX_QUEUED_JOBS = 5,000`).
  * Created `SafeObjectInputStream.java` with a strict `ObjectInputFilter` allowlist (`app.freerouting.**`, core collections/primitives/geometry, rejecting `!*`) for all in-memory snapshot and undo cloning paths.
  * Completely removed manual loading and saving of binary `.frb` files in the GUI (`BoardMenuFile.java`, `BoardExportActions.java`, `BoardFrame.java`).

### Batch 6: CI/CD, Deployment & Supply Chain (`GH-AUDIT-DEPLOY` & `GH-AUDIT-SUPPLY`)
* **Findings Addressed:** `AUDIT-021`, `AUDIT-027`, `AUDIT-028`
* **Commit:** `831d8499`
* **Changes:**
  * Hardened container security in `Dockerfile` by creating a dedicated non-root user/group (`freerouting:freerouting`, `UID/GID 10001`) and dropping root privileges.
