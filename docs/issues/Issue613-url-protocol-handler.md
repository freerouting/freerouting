# Issue 613: Register a Custom URL Protocol to Launch Freerouting

**GitHub Issue:** https://github.com/freerouting/freerouting/issues/613  
**Related PR:** https://github.com/freerouting/freerouting/pull/651  
**Requested by:** EasyEDA / LCEDA Pro team (`@L1uTongweiNewAccount`)  
**Status:** Declined in current form. Idle timeout sub-feature accepted in principle.  
**Decision date:** 2026-04-30

---

## 1. What Was Requested

EasyEDA Pro is an Electron-based application. Their Freerouting integration extension
(`eext-freerouting-intergration`) runs inside the Electron **renderer process** (a sandboxed
browser context). Because a renderer process cannot spawn OS processes directly, the
extension uses `window.location = "freerouting://..."` to trigger the OS URL protocol
dispatch and launch Freerouting.

PR #651 proposed adding to Freerouting:
1. A custom `freerouting://` URL protocol handler (URL parser + platform registration).
2. An API server idle timeout.

---

## 2. Decision and Rationale

### 2.1 URL Protocol Handler — Declined

The URL protocol handler was declined for two primary reasons:

**Security.** The PR's URL schema (`freerouting://open?--gui.enabled=false&--api_server.enabled=true`)
passes CLI arguments directly through the URL. Once the protocol is registered OS-wide,
any webpage — not just EasyEDA — can silently trigger it via `<a href>` or
`window.location`. A malicious actor could construct:

```
freerouting://open?--api_server.enabled=true&--api_server.authentication.enabled=false&--api_server.endpoints=http://0.0.0.0:37864&--api_server.cors_origins=*
```

This would open an unauthenticated, LAN-visible routing API without any user confirmation.
The PR provides no parameter allowlist, no confirmation dialog, and no security boundary
between "parameters EasyEDA needs" and "the full CLI attack surface."

**Maintenance burden.** Registering a URL protocol handler requires three completely
different mechanisms across platforms (Windows registry, Linux `.desktop`/`xdg-mime`,
macOS `Info.plist`), each with distinct failure modes, security scanner implications,
uninstall/upgrade edge cases, and CI-untestable behavior. This is ~10 ongoing maintenance
surfaces introduced by ~250 lines of code that work around an architectural constraint in
EasyEDA, not in Freerouting.

See §4 for the full analysis of alternatives.

### 2.2 API Idle Timeout — Accepted in Principle

The `idle_timeout` setting for the headless API server is independently useful, has clear
semantics, and introduces no security surface. It should be re-submitted as a standalone PR
with the architectural fixes noted in §3.

### 2.3 `UrlProtocolHandler.java` Parsing Logic — Accepted in Principle

The URL-to-args parser and its unit tests are clean, dependency-free, and may be useful if
Freerouting ever adds protocol registration in its installer scripts. The class can be
included without `ProtocolRegistrar.java`.

---

## 3. Issues in PR #651 That Must Be Fixed if the Feature Is Ever Revisited

| # | Issue | Severity |
|---|---|---|
| 1 | URL schema exposes full CLI surface — no parameter allowlist | 🔴 Critical |
| 2 | `FRLogger.warn` called before log4j2 is initialized | 🔴 Critical |
| 3 | macOS build: `hdiutil`-created DMG bypasses `jpackage` code signing | 🟠 High |
| 4 | `IdleTimeoutFilter` static state + backwards layer dependency | 🟠 High |
| 5 | `IdleTimeoutFilter` not confirmed auto-registered by Jersey | 🟡 Medium |
| 6 | URL schema couples public param names to internal CLI naming | 🟡 Medium |
| 7 | `idleTimeout = 0` field default violates nullable-settings invariant | 🟡 Medium |
| 8 | `ProtocolRegistrar` fragile executable detection via `startsWith("java")` | 🟢 Low |
| 9 | Health-check requests (`/v1/system/status`) reset idle timer — timeout never fires | 🟢 Low |
| 10 | No graceful drain of in-flight routing jobs before idle-timeout shutdown | 🟢 Low |
| 11 | No protocol unregistration on uninstall — stale registry/desktop entries remain | 🟢 Low |

---

## 4. Alternatives to the URL Protocol Handler

All alternatives below work with the current Freerouting JAR **without any code changes**.

### Option 1 (Recommended): EasyEDA adds a `launchExternalApp()` IPC bridge

EasyEDA already gates their Freerouting extension behind an explicit **"External
Interaction"** (`允许外部交互`) user permission. This is architecturally the correct place
for user consent. EasyEDA's main process team could expose:

```typescript
// EasyEDA extension API (hypothetical):
await EasyEDA.launchExternalApp('/path/to/freerouting', [
  '--gui.enabled=false',
  '--api_server.enabled=true',
  '--api_server.authentication.enabled=false'
]);
```

This routes through Electron's `ipcRenderer` → `ipcMain` → `child_process.spawn()`.
This is **exactly how KiCad's Python plugin launches Freerouting** (`subprocess.Popen`),
and requires no security compromise on Freerouting's side.

### Option 2: Freerouting as an autostart system service (installer-level)

The Freerouting installer (not the JAR) can register a per-user autostart service:

- **Windows:** `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
- **Linux:** `~/.config/systemd/user/freerouting-api.service`
- **macOS:** `~/Library/LaunchAgents/app.freerouting.plist`

EasyEDA's extension then only needs to check if `http://127.0.0.1:37864` is reachable.
The `idle_timeout` setting (from PR #651) makes this resource-efficient — Freerouting exits
automatically after inactivity. Installer-level registration is already maintained by the
platform-specific build scripts; it does not pollute the JAR's `main()` method.

### Option 3: "Start Freerouting first" guidance prompt

If `GET http://127.0.0.1:37864/v1/system/status` returns a connection error, the EasyEDA
extension shows: *"Freerouting is not running. Please launch it from your applications menu,
then click Retry."* This matches the legacy EasyEDA Launcher behavior and gives users
explicit awareness of what's happening.

### Option 4: Freerouting bundled as a sidecar inside EasyEDA

EasyEDA could bundle the Freerouting binary and manage its lifecycle with
`child_process.spawn()` from their Electron main process, similar to how VS Code,
Cursor, and other Electron apps bundle language servers. Trade-off: EasyEDA controls the
Freerouting version, which complicates update propagation.

---

## 5. If URL Protocol Is Accepted in a Future PR — Schema Design Guidance

### 5.1 Reject the CLI-Passthrough Schema

Do **not** use `freerouting://open?--param=value`. The `--` prefix is a CLI convention
that has no meaning in URLs. More importantly, this schema has no security boundary: every
CLI argument becomes a URL attack surface by default.

### 5.2 Adopt the Versioned Semantic Schema (Option D)

```
freerouting://v1/start-api
    ?endpoint=http%3A%2F%2F127.0.0.1%3A37864
    &auth_enabled=false
    &cors_origins=https%3A%2F%2Fpro.lceda.cn
    &idle_timeout=3600

freerouting://v1/open-gui

freerouting://v1/route
    ?dsn=...
    &max_passes=200
    &max_threads=4
```

Key principles:
- **`v1/` in the path** provides versioning without breaking existing URLs when `v2/` is added.
- **Named actions** (`start-api`, `open-gui`, `route`) define explicit parameter allowlists
  by construction.
- **No `--` prefix** — URL parameters are URL parameters, not CLI arguments.
- **Unrecognized parameters are logged and ignored**, never forwarded to the routing engine.
- **Filesystem paths require explicit user confirmation** before being acted on (security).

### 5.3 Integrate as a `SettingsSource` at Priority 65

Add `UrlProtocolSettings implements SettingsSource` at priority 65 (between CLI at 60 and
REST API at 70) in `SettingsMerger`. Populate it from the parsed URL. This replaces the
hacky `args` reassignment in `main()` with a proper settings-pipeline integration.

### 5.4 Move Registration to Installer Scripts

Do not auto-register the protocol from `main()`. The installer is the correct place for
OS-level integration. This eliminates `ProtocolRegistrar.java` from the JAR entirely and
the 10 maintenance surfaces it creates.

---

## 6. Acceptance Criteria for a Revised PR

A revised PR would be accepted if it satisfies all of the following:

- [ ] Uses the versioned semantic schema (`freerouting://v1/<action>?param=value`).
- [ ] Defines and enforces an explicit parameter allowlist per action.
- [ ] Does **not** include `ProtocolRegistrar.java` — registration is installer-only.
- [ ] `UrlProtocolSettings` is a `SettingsSource` at priority 65, not a raw `args` replacement.
- [ ] The `idle_timeout` layer violation is fixed (`Freerouting.java` must not import `IdleTimeoutFilter`).
- [ ] `IdleTimeoutFilter` only resets on meaningful requests, not health/status pings.
- [ ] macOS build scripts preserve code signing.
- [ ] All checklist items in §5 of the PR #651 analysis are addressed.

