# Security Audit Pass E — Settings, Docker, and Installers

**Status:** Confirmed (Grok 4.6 Medium, 2026-08-21)
**Hunter model:** GPT-5.6 Luna High; HTTPS design reviewed by GPT-5.6 Luna Max
**Confirmation model:** Grok 4.6 Medium
**Date:** 2026-08-21
**Scope:** settings, CLI startup, Dockerfile, installers/jlink, deployment documentation

This is a read-only hunt report. Findings remain in the candidate table for traceability; the
Confirmation section records Grok 4.6 Medium verdicts. No production code was changed and no
risk-register rows were added (Phase 3).

## Method

1. Compared API/MCP settings defaults with the actual Jetty connector construction in
   `Freerouting.initializeAPI` and `initializeMCP`.
2. Traced CLI/configuration examples and deployment documentation for bind, authentication,
   HTTP/TLS, CORS, persistence, and Docker behavior.
3. Reviewed the Docker build/runtime stages, user identity, writable volumes, default command,
   and image exposure.
4. Reviewed Linux, macOS, and Windows jlink/jpackage scripts for runtime-module coverage,
   installer signing/notarization, and secret handling.
5. Checked settings persistence for credential-bearing fields and file-permission hardening.
6. Excluded CI workflow and dependency provenance issues to Pass F, while noting overlaps where
   release/installer behavior crosses both passes.

## Confirmed findings

See Confirmation below. All six hunter candidates were accepted. The HTTPS silent HTTP
fallback remains High after Luna Max design review and this confirmation.

## Luna Max design review

**Result:** The HTTPS downgrade is a concrete High-severity design finding, pending Grok 4.6
Medium confirmation.

The finding is stronger than a documentation-only mismatch:

- In both startup paths, `https` passes the protocol validation, emits only a warning, and then
  constructs `new ServerConnector(...)` without an `SslContextFactory`, TLS connection factory,
  certificate, or key configuration.
- `http_allowed=false` rejects only the literal `http` protocol. An operator can therefore set
  `http_allowed=false` and an `https://...` endpoint, yet still receive a plaintext connector.
- `NetworkSettings` contains outbound proxy/custom truststore settings, but no inbound server TLS
  configuration. The repository has no alternate Jetty TLS connector or certificate-loading path.
- Existing embedded-server tests exercise `http://127.0.0.1:0`; no test asserts that an HTTPS
  endpoint either negotiates TLS or fails closed.

The default loopback bind limits remote exposure for an unmodified installation, so the impact is
conditional on a network-reachable deployment or a reverse-proxy/configuration misunderstanding.
Once an operator selects a network endpoint and expects `https` to protect API keys, board data,
job output, or MCP traffic, the listener is plaintext and vulnerable to passive capture or active
MITM. A TLS-terminating reverse proxy can remain a valid deployment pattern, but Freerouting must
then be configured with an explicit `http` upstream endpoint rather than silently treating an
`https` endpoint as HTTP.

## Candidates (unconfirmed)

| Severity | Location | Finding | Exploit scenario | Fix sketch |
| --- | --- | --- | --- | --- |
| High | `src/main/java/app/freerouting/Freerouting.java:395-408`; `:532-544` | **Configured HTTPS endpoints silently start plaintext HTTP connectors.** Both startup paths accept the `https` protocol, warn that HTTPS is not implemented, and then create a plain `ServerConnector` with only host and port. | An operator configures `https://host:port` believing API keys, board designs, job output, and MCP credentials are protected by TLS. A network observer or active MITM can read or alter the traffic. The warning is not a fail-closed security control, and `http_allowed` does not make the `https` spelling secure. | Reject `https` until a TLS connector is configured, or require explicit certificate/key settings and construct an HTTPS connector. Never advertise or bind a plaintext connector for an `https` URL. |
| Medium | `src/main/java/app/freerouting/Freerouting.java:417-435`, `:552-568`; `docs/self-hosting.md:266-274` | **CORS permits credentialed requests when operators configure a wildcard origin.** The API and MCP handlers set `allowCredentials(true)` while accepting configured origin patterns, including the documented `*` value. | A deployment that sets `cors_origins=*` can grant arbitrary websites credentialed cross-origin access to the service. Current API authentication is header-based rather than cookie-based, which limits exploitability, but browser clients that deliberately supply credentials or future cookie/session state would be exposed. | Reject `*` when credentials are enabled, default credentials to false, and require an explicit origin allowlist for authenticated deployments. Add integration tests for wildcard and credentialed browser requests. |
| Medium | `Dockerfile:26-41`; `docs/self-hosting.md:50-60`, `:108-131` | **The Docker runtime runs as root with a writable persistent volume and enables the API in its default command.** No `USER` is set in the final image; `/mnt/freerouting` is declared writable and the command enables API mode and job persistence. | If a reachable API/parser or future execution flaw is exploited, the process has root privileges inside the container and can modify all writable container paths and mounted data. The risk increases when the documented port publishing and authentication-disabled examples are used. This is container hardening, not proof of a host escape. | Create and use a non-root runtime user, set ownership/permissions for the data volume, avoid enabling job persistence by default, and make exposure/authentication explicit in the container entrypoint and documentation. |
| Medium | `src/main/java/app/freerouting/settings/GlobalSettings.java:28-31`, `:378-408`; `src/main/java/app/freerouting/settings/GoogleSheetsProviderSettings.java:9-15` | **The persisted settings file can contain the Google Sheets API key without explicit restrictive file permissions.** The default path is a predictable directory under `java.io.tmpdir`, and `saveAsJson` creates/writes the file using ordinary filesystem defaults. | On shared Unix hosts with a permissive umask or a pre-existing accessible temp directory, another local user may read `freerouting.json` and recover the configured Google Sheets API key. This is a local-user exposure and overlaps Pass D's credential findings. | Store credentials in an OS credential store or environment-only secret, avoid serializing the raw key, create the directory/file with owner-only permissions, and verify permissions after save. |
| Medium | `scripts/build/create-distribution-macos-arm64.sh:20-39`; `scripts/build/create-distribution-windows-x64.bat:15-21`; `.github/workflows/create-release.yml:125-157`; `.github/workflows/create-snapshot.yml:125-151` | **Platform installers are not code-signed or notarized, and release assets have no published checksum/signature step.** The jpackage invocations create DMG/MSI/app-image packages without signing options. The snapshot workflow passes `APPLE_DEVELOPER_ID` as a second argument, but the script only consumes `$1`; the release workflow does not pass it. | Users cannot authenticate a downloaded installer through the platform trust chain. A tampered mirror, release asset, or compromised distribution path can be presented as an unsigned package, and users may be trained to bypass Gatekeeper or Windows warnings. | Sign Windows installers, sign and notarize/staple macOS packages, publish detached signatures and checksums for every release asset, and fail release workflows when signing material is unavailable. |
| Low | `src/main/java/app/freerouting/settings/ApiServerSettings.java:17-23`; `docs/settings.md:59-70`; `docs/self-hosting.md:50-60`, `:124-131`; `Dockerfile:40-41` | **Deployment documentation does not match the secure runtime defaults.** The code defaults the API endpoint to `http://127.0.0.1:37864` and authentication to enabled, while the settings example shows `0.0.0.0`; the Docker guide says the image defaults to `0.0.0.0` with authentication disabled, but the Docker command does not set either override. | Operators may copy the insecure `0.0.0.0`/auth-disabled examples or incorrectly assume the container is protected/exposed as documented. The mismatch can produce either accidental exposure or an unreachable service, and obscures the actual security boundary. | Generate or test documentation examples from the effective defaults, put localhost+auth examples first, label every network-exposure example as an explicit exception, and make the Docker command and prose agree. |

## Controls verified during the hunt

- `ApiServerSettings` defaults to `http://127.0.0.1:37864` and
  `ApiAuthenticationSettings` defaults to enabled (`ApiServerSettings.java:17-27`,
  `ApiAuthenticationSettings.java:9-15`).
- MCP defaults are also loopback with authentication enabled
  (`McpServerSettings.java:21-31`).
- `jlink` module lists match the documented platform requirements: Windows includes
  `jdk.crypto.mscapi`, while Linux/macOS omit the Windows-only module
  (`scripts/build/create-distribution-windows-x64.bat:16`,
  `create-distribution-linux-x64.sh:23-30`, `create-distribution-macos-arm64.sh:21-28`).
- Authentication-disabled mode is an explicit operator setting and is not itself a finding when
  paired with a loopback bind; the network-exposed examples are the risk combination.
- The public environment/CLI-argument disclosure is already confirmed in Pass A and is not
  duplicated here. The settings-file key exposure above is a separate local persistence boundary.

## Candidates not promoted

- `provenance: false` in the Docker workflows is intentional for the project's multi-platform
  manifest compatibility and is not counted as a separate E finding; related artifact provenance
  is reviewed in Pass F.
- The absence of Windows/macOS installer signing is recorded as an artifact-integrity candidate,
  not as code execution in the application. Exploitability depends on a tampered distribution
  path and user installation behavior.
- Local operator-controlled paths such as `user_data_path`, CLI input/output paths, and custom CA
  paths were not treated as attacker-controlled network inputs.
- The default HTTP listener on loopback is not equivalent to a world-wide insecure bind. The
  concern is the silent HTTPS downgrade and documentation/configuration combinations that expose
  the listener.

## Confirmation

**Confirmer:** Grok 4.6 Medium (2026-08-21). No new domains hunted. No production code changed.
No risk-register rows added (Phase 3). Luna Max's HTTPS design review is accepted. Dual-family
coverage for the High finding is Luna (hunt + design) and Grok (this confirmation).

| Candidate | Verdict | Severity after confirm | Reason |
| --- | --- | --- | --- |
| Configured HTTPS endpoints silently start plaintext HTTP connectors | **Confirm** | High | `initializeAPI` and `initializeMCP` accept `https`, warn, and then always construct `new ServerConnector(server)` with host/port only (`Freerouting.java:395-408`, `:532-544`). `isHttpAllowed` rejects only the `http` scheme, so `http_allowed=false` plus an `https://` endpoint still binds plaintext. There is no inbound `SslContextFactory` or certificate setting; `NetworkSettings` is outbound-only. Default loopback reduces unconfigured exposure, but a network bind plus expected TLS is a silent MITM/confidentiality failure for API keys, designs, and MCP traffic. |
| CORS permits credentialed requests when operators configure a wildcard origin | **Confirm** | Medium | Both servers call `setAllowCredentials(true)` and pass the configured origin string through `splitCommaSeparated` into `setAllowedOriginPatterns` (`Freerouting.java:417-435`, `:552-568`). Docs recommend `cors_origins=*` (`docs/self-hosting.md:272`). CORS is off when `corsOrigins` is empty, and REST auth is header-based rather than cookie-based, so this is not a default remote auth bypass. It is still a real operator-misconfig path: Jetty origin patterns plus credentials can reflect an arbitrary browser origin and allow credentialed cross-origin calls. |
| Docker runtime runs as root with a writable volume and API enabled | **Confirm** | Medium | Final image has no `USER`, declares `VOLUME /mnt/freerouting`, and the default `CMD` starts the API with `--feature_flags-save_jobs=1` and `--user_data_path=/mnt/freerouting` (`Dockerfile:26-41`). This is container hardening, not a demonstrated host escape. Combined with published ports it raises the impact of any later API/parser RCE. |
| Persisted settings file can contain the Google Sheets API key without restrictive permissions | **Confirm** | Medium | `googleApiKey` is a normal public field (`GoogleSheetsProviderSettings.java:9-15`). `saveAsJson` writes the whole `GlobalSettings` object with `Files.newBufferedWriter` after `createDirectories`, with no POSIX/ACL owner-only mode (`GlobalSettings.java:378-408`). Default path is `java.io.tmpdir/freerouting/freerouting.json`. Distinct from Pass D telemetry upload of the same key: this is local filesystem exposure on shared hosts or a world-readable umask. |
| Platform installers are not code-signed or notarized | **Confirm** | Medium | Linux/macOS/Windows `jpackage` invocations set `--java-options "-Dlog4j2.disableJndi=true"` but no `--mac-sign`, notarize, or Authenticode options. Snapshot macOS packaging is called with `"${{ secrets.APPLE_DEVELOPER_ID }}"` as `$2`, and the script never reads `$2` (`create-distribution-SNAPSHOT-macos-arm64.sh` uses only `$1`; release workflow does not pass the secret). GitHub release/snapshot uploads have no checksum or detached-signature step. Integrity depends on GitHub/GHCR transport, not a project-published signature. Overlaps Pass F artifact-assurance. |
| Deployment documentation does not match the secure runtime defaults | **Confirm** | Low | Code defaults: API `http://127.0.0.1:37864` and `authentication.enabled=true` (`ApiServerSettings.java:17-27`, `ApiAuthenticationSettings.java:9-15`). Docker `CMD` enables the API but does **not** set `authentication.enabled=false` or override endpoints, yet `docs/self-hosting.md:60` claims a `0.0.0.0` bind and auth-disabled image default. `docs/settings.md:59-63` still shows `"http://0.0.0.0:37864"`. Operators can copy the insecure examples or assume the published container port is reachable/authenticated in ways the image does not actually configure. |

Hunter “controls verified” and “candidates not promoted” blocks are accepted: loopback+auth-on defaults are real; Docker `provenance: false` stays in Pass F; installer signing is distribution integrity, not in-app RCE. Do not add Pass E findings to `security-audit-risk-register.md` until Phase 3.

