# Packaging & JRE Module Invariants (jlink & Docker)

## Installer / jlink Module Requirements

The Windows, Linux, and macOS installers use `jlink` to build a minimal bundled JRE. The `--add-modules` list must include every JDK module that the application (or its bundled libraries) accesses at runtime — `jlink` does **not** auto-detect usages from inside a fat jar.

### Correct `--add-modules` list per platform

| Platform | Required modules |
|---|---|
| **Windows** | `java.desktop,java.logging,java.management,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.crypto.mscapi,jdk.management` |
| **Linux** | `java.desktop,java.logging,java.management,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.management` |
| **macOS** | `java.desktop,java.logging,java.management,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.management` |

> `jdk.crypto.mscapi` is **Windows-only** (provides `SunMSCAPI` for the Windows OS certificate store). Do not add it to Linux/macOS scripts.

### Why each module is needed

- **`java.management`** — `ManagementFactory` (used in `SystemControllerV1`, `RoutingJobSchedulerActionThread`, Log4j2 startup, Jersey-server). Missing this module causes `NoClassDefFoundError: java/lang/management/ManagementFactory` on startup and crashes the `/v1/system/status` endpoint with HTTP 500.
- **`jdk.management`** — `com.sun.management.ThreadMXBean.getThreadAllocatedBytes()` and `com.sun.management.OperatingSystemMXBean.getCpuLoad()`. Transitively brings in `java.management`.
- **`jdk.crypto.ec`** — EC crypto provider (`SunEC`) required for TLS 1.3 / ECDHE key exchange. Without it, all HTTPS connections to Google APIs (OAuth2, BigQuery, Sheets) fail at the SSL handshake.
- **`jdk.crypto.mscapi`** — Windows native certificate store access. Without it, Java uses only its own bundled `cacerts`, which is usually sufficient but may miss system-level certificates.

### Modules explicitly NOT needed

- `java.instrument` — javassist references it but HK2/Jersey proxy generation uses `ClassLoader.defineClass()`, not bytecode redefining. Do not add.
- `jdk.attach` / `jdk.jdi` — javassist debugging tools. Not needed for runtime proxy generation. Do not add.
- `jdk.httpserver` — referenced in jersey-server module-info but Freerouting uses Jetty, not the JDK HTTP server. Do not add.
- `java.naming` — log4j-core requires it `static` (optional) for JNDI lookups; Freerouting does not use JNDI lookups. Do not add. Instead, `System.setProperty("log4j2.disableJndi", "true")` is set at the very start of the Log4j2 property-setup block in `Freerouting.main()` (before `LogManager.getContext()` is first called) so Log4j2 skips its JNDI plugin registration entirely and emits no WARN.
- `java.rmi` — log4j-core requires it for remote JMX; not needed for local embedded logging. Do not add.

### Defensive coding pattern for `ManagementFactory`

All `ManagementFactory` call sites are wrapped in `try/catch (Throwable)` so the application degrades gracefully (returns -1 / skips stats) rather than crashing when running on a custom or stripped JRE:

```java
try {
    ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    // ...use threadMXBean...
} catch (Throwable t) {
    // java.management or jdk.management module not available in this JRE build
}
```

Affected files: `SystemControllerV1.getCpuLoad()`, `RoutingJobSchedulerActionThread.monitorCpuAndMemoryUsage()`.

### Analysing module requirements

To check what JDK modules a jar requires, use:
```bash
jdeps --ignore-missing-deps -q --print-module-deps <jar-file>
```
For the project's own thin jar (without bundled deps):
```bash
jdeps --ignore-missing-deps -q --print-module-deps build/libs/freerouting.jar
```
To inspect a named module's `requires` declarations:
```bash
jar --describe-module --file <jar-file> | grep requires
```

## Docker Multi-Platform Build

The Docker images are built and published by two GitHub Actions workflows:

- `.github/workflows/docker-release.yml` — triggered on every GitHub release; tags the image with the semver version and `latest`.
- `.github/workflows/docker-nightly.yml` — triggered on every push to `master`; tags the image as `nightly`.

Both workflows build a multi-platform manifest image using Docker Buildx + QEMU. The supported platforms are:

| Platform | Hardware |
|---|---|
| `linux/amd64` | x86-64 servers, PCs, most cloud VMs |
| `linux/arm64` | Apple Silicon, AWS Graviton, Raspberry Pi 4/5 (64-bit OS) |

> **Note:** `linux/arm/v7` (32-bit ARM) support was dropped when the project moved to Java 25, because `eclipse-temurin:25` no longer publishes an `arm/v7` image. Raspberry Pi users running a 32-bit OS should switch to a 64-bit OS image to use the official Docker image.

`provenance: false` is set on both workflows so that the manifest does not contain build-attestation entries (which show up as `"architecture": "unknown"` in `docker manifest inspect`).

The Gradle tests are run on the native `ubuntu-latest` runner **before** the multi-platform build step to avoid QEMU network/loopback unreliability inside the cross-compiled build container.

For end-user self-hosting instructions see `docs/self-hosting.md`.
