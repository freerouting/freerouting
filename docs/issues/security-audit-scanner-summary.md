# Security Audit Scanner Summary

**Status:** Phase 1 Complete (Raw scans executed & verified)
**Audit plan:** [security-audit-plan.md](security-audit-plan.md)
**Driver model:** Gemini 3.7 Flash
**Interpretation model:** Grok 4.6 Medium (with dual-review cross-reference across Passes A–H)
**Scan Date:** 2026-08-21

Raw scanner outputs are stored in git-ignored `logs/security-audit/scanners/`:
- `secrets-scan.txt` (Working tree & git commit history secret scans)
- `osv-dependencies-scan.txt` (OSV vulnerability database scan of 167 resolved Maven packages)
- `code-patterns-scan.txt` (Static search for `ObjectInputStream`, `setAccessible`, process execution, TLS bypass, path manipulation)
- `ci-config-scan.txt` (GitHub Actions workflow and Dockerfile configuration analysis)
- `gradle-runtime-dependencies.txt` (Full resolved Gradle runtimeClasspath tree)

---

## 1. Scanner Status Checklist

- [x] **Secret scan on working tree:** Clean (0 hardcoded credentials found).
- [x] **Secret scan on repository history (recent commits):** Clean (no leaked keys in git log).
- [x] **Dependency advisory scan (OSV database):** 1 transitive vulnerability identified (`io.netty:netty-common`).
- [x] **Static code & mechanical pattern analysis:** Verified 106 deserialization sites, 4 reflection sites, 0 process execution / script engine calls, and 87 path concatenation locations.
- [x] **GitHub Actions permissions review:** Verified all 8 workflow files declare explicit `permissions`; identified mutable action references and inline secret interpolation in `create-snapshot.yml`.
- [x] **Docker and installer configuration review:** Dockerfile runs as `root` (missing `USER` directive).

---

## 2. Summary of Filtered Findings

### A. Transitive Dependency Vulnerabilities (OSV)

| Advisory | Package & Resolved Version | Severity | Details & Impact | Remediation |
| :--- | :--- | :--- | :--- | :--- |
| **CVE-2024-47535** / GHSA-xq3w-v528-46rv<br>**CVE-2025-25193** / GHSA-389x-839f-4rhx | `io.netty:netty-common:4.1.110.Final`<br>*(Transitive via Apache Arrow / Google Cloud BigQuery Storage)* | **Moderate (CVSS 7.5)** | **Denial of Service on Windows applications.** Unsafe reading of environment file creates an unbounded loop when processing large or malformed files containing null bytes. | Add dependency constraint in `build.gradle.kts` enforcing `io.netty:netty-common:4.1.118.Final` (or newer `4.1.115.Final+`). |

*Note on direct dependencies:* Core dependencies (Jetty `12.0.16`, Jersey `3.1.10`, Log4j `2.24.3`, Gson `2.12.1`, SnakeYAML `2.6`, Jackson `2.22.1`) have no active unpatched high/critical advisories in the OSV database. However, Gradle dependency verification metadata / checksum lockfile is currently absent.

---

### B. Secret Scanning

| Surface | Result | Notes & Correlation |
| :--- | :--- | :--- |
| **Working Tree** | **Clean (0 hits)** | No AWS keys, Google API keys, GCP service-account JSON, private keys, or Slack tokens committed in tracked files. |
| **Git History** | **Clean (0 hits)** | No accidental commit of production secrets detected in recent commit history. |
| **Runtime Secret Flow Note** | *Correlated with Pass D & Pass E* | While static code is clean, Pass D confirmed that `GoogleSheetsProviderSettings.googleApiKey` is non-transient and serialized into telemetry payloads uploaded to `api.freerouting.app` when Sheets auth and telemetry are enabled. Pass E confirmed that `GlobalSettings.saveAsJson()` writes `googleApiKey` to `freerouting.json` without restrictive OS file permissions. |

---

### C. Mechanical Static Code Patterns

| Pattern | Hits | Finding / Context | Correlation |
| :--- | :---: | :--- | :--- |
| **`ObjectInputStream`** | 106 | Deserialization used extensively across `.frb` loading, board snapshots (`BoardSnapshotManager.java:49`), and undo facades (`RoutingBoardUndoFacade.java:52`). | Evaluated in **Pass G**. |
| **`Runtime.exec` / `ProcessBuilder`** | 0 | No process execution in current codebase (frozen `src_v19` excluded). | Clean. |
| **`ScriptEngine` / `XMLDecoder`** | 0 | No dynamic script evaluation or XMLDecoder deserialization. | Clean. |
| **`setAccessible(true)`** | 4 | Used in `ReflectionUtil.java:36, 192` for settings merge, and in test harness. | Accepted internal reflection. |
| **TLS / Hostname Verification Bypass** | 12 | `NetworkProxyConfig.java` defines a `CompositeX509TrustManager` combining system/custom trust stores. | False positive: verified legitimate composite trust store, not a `TrustAll` bypass. |
| **Path Concatenation (`.resolve()`, `new File()`)** | 87 | `RoutingJobScheduler.java:344, 357, 367` concatenates unvalidated filenames from `RoutingJob.input` into `sessionFolderPath`. | Evaluated in **Pass C** (High when persistence is enabled). |

---

### D. CI/CD & Container Configuration

| File | Issue | Severity | Fix |
| :--- | :--- | :---: | :--- |
| `.github/workflows/create-snapshot.yml:42` | Secret `${{ secrets.GITHUB_TOKEN }}` directly interpolated into inline `run:` shell command instead of environment variable. | **Low / Hygiene** | Pass `GITHUB_TOKEN` through `env:` block to avoid shell injection / process disclosure. |
| `.github/workflows/*.yml` | Action references use mutable tags (e.g. `@v4`, `@v5`, `@v7`) instead of pinned 40-character commit SHAs. | **Low / Hygiene** | Pin third-party GitHub Actions to immutable full commit SHAs. |
| `Dockerfile` | Missing `USER` directive; container runs as `root` by default inside Docker image. | **Medium** | Add dedicated non-root user (e.g., `USER 10001:10001` or `appuser`) in runtime stage before `EXPOSE 37864`. |
