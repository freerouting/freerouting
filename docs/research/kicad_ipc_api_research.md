# KiCad IPC API Research for Freerouting

## Purpose

This document provides the authoritative technical reference and implementation strategy for integrating Freerouting with KiCad via the official **KiCad Protocol Buffers IPC API**. It outlines the transport, protocol schemas, plugin execution model, debugging playbook, risks, benefits, and the roadmap to ship a testable alpha version in the upcoming release.

## Verified Facts & Architecture Details

- **Official Status & Roadmap:**
  - KiCad 10 is officially released (10.0.0 on 2026-03-20). The IPC API is supported on KiCad 9 and 10.
  - The KiCad core team has formally deprecated the in-process SWIG Python bindings (`import pcbnew`). SWIG support will be removed completely in KiCad 11.
  - IPC is the sole supported forward-compatible integration architecture for KiCad plugins.
- **Transport Layer:**
  - Communication uses **Nanomsg Next Generation (NNG / `pynng`)** messaging.
  - Endpoint mechanism:
    - **Unix domain sockets** (`api.sock`) on Linux and macOS.
    - **Named pipes** (`\\.\pipe\kicad-api-...`) on Windows.
  - Endpoints are per-instance: if multiple KiCad sessions run, KiCad appends the process ID (`PID`) to ensure uniqueness.
- **Protocol & Serialization:**
  - Messages are strictly defined using **Protocol Buffers (Protobuf v3)** schemas (`api.proto`, `board.proto`, `board_types.proto`).
  - Backward compatibility is guaranteed by Protobuf field numbering rules across minor KiCad releases.
- **Plugin Manifest & Runtime Isolation:**
  - IPC plugins declare their metadata and actions via `plugin.json` following the schema at `https://go.kicad.org/api/schemas/v1`.
  - Runtime type is `"type": "python"` or `"type": "exec"`.
  - When `"python"` is specified, KiCad **launches the plugin out-of-process in a managed virtual environment** located at `${KICAD_CACHE_HOME}/python-environments/<plugin_identifier>`.
  - KiCad provisions this environment automatically using dependencies declared in `requirements.txt` (e.g. `kicad-python`).
  - When executing an action, KiCad injects two environment variables:
    - `KICAD_API_SOCKET`: Path to the active socket or named pipe.
    - `KICAD_API_TOKEN`: Per-instance authentication/session token.
- **Official Python Client Library:**
  - The official bindings library is `kicad-python` (package namespace `kipy`, available on PyPI).
  - High-level client `kipy.KiCad(socket_path=..., token=...)` manages the NNG socket and Protobuf serialization.
  - Direct board access is provided via `board = kicad.get_board()`.
- **Transactional Board Updates:**
  - Modifications use atomic transactions: `commit = board.begin_commit()`, followed by `board.create_items(...)`, and `board.push_commit(commit)` (or `board.drop_commit(commit)` on failure).
  - This collapses all autorouting changes into a single undo/redo step in KiCad.
- **Design Rules & Clearance Integration (Issue #558):**
  - Unlike Specctra DSN export, the IPC API exposes full live design rules in `BoardDesignRules`.
  - Copper-to-edge clearance is directly available via `constraints.copper_edge_clearance().value_nm()`.
- **Operating Constraints:**
  - In KiCad 9 and 10, the IPC API requires a running KiCad GUI instance; headless operation via `kicad-cli api-server` arrives in KiCad 11.
  - IPC API server is disabled by default in KiCad preferences (**Preferences > Common > Enable API**).
  - KiCad processes API requests on its main event loop; calls are synchronous and can block if a modal dialog is open in KiCad.

## Three Generations of KiCad Integration in Freerouting

To avoid architectural confusion between legacy SWIG approaches and true IPC, Freerouting defines three distinct tiers:

| Tier | Name | Transport | Board Access | Status in Freerouting |
|---|---|---|---|---|
| **Gen 1** | **DSN Mode** | File exchange (`.dsn`/`.ses`) | `pcbnew.ExportSpecctraDSN` / `ImportSpecctraSES` | **Current Stable Default** (Legacy SWIG, works on all versions) |
| **Gen 2** | **JSON/API Mode** | HTTP REST (`127.0.0.1:37864`) | In-process SWIG object walker (`board_json_helpers.py`) | **Transitional Bridge** (v2.3 experimental, opt-in, deprecated) |
| **Gen 3** | **Protobuf IPC Mode** | NNG Socket/Pipe + HTTP REST | Out-of-process `kicad-python` (`kipy`) client | **Target Architecture** (Alpha in next release, replaces Gen 2) |

---

## Direct answers to current product questions

### Will the classic KiCad to Freerouting DSN path still work?

We need to separate two different things:

- DSN file support in Freerouting
- SWIG-based KiCad plugin execution path

Updated expectation:

- Freerouting should keep DSN read and write support.
- The current SWIG-based plugin path should be considered legacy and near end-of-life for KiCad nightly and 11.0.
- IPC should become the default KiCad integration path.

What we can reasonably expect:

- KiCad still has Specctra DSN and SES support in current releases.
- KiCad has explicitly signaled that SWIG plugin runtime is deprecated.
- KiCad development direction for plugins is IPC.

Practical interpretation for Freerouting:

- Keep DSN as a compatibility data path.
- Replace SWIG-based plugin calls with IPC-based plugin flow.
- Expect slower feature parity on DSN compared to IPC, especially for newer rule details.
- Add a regression check in CI that validates DSN export plus import still works against current KiCad release channels.

### Is IPC worth the implementation and maintenance burden?

Yes, if we keep scope tight and phase it.

Why users benefit enough:

- Fewer failure points from manual export and import steps.
- Better access to live design-rule context.
- Better long-term alignment with KiCad plugin architecture.
- Better path to richer UX such as progress updates and interactive control.
- Avoids breakage when users move to KiCad nightly and 11.0 where SWIG-based plugin runtime is deprecated.

Why burden stays manageable:

- Routing core stays the same.
- Most new work is in adapter code and plugin flow.
- DSN remains fallback, so IPC rollout does not need to be all-or-nothing.

Decision rule:

- IPC is now mandatory for KiCad forward compatibility.
- Keep Phase 1 scoped so it avoids core routing changes and does not regress DSN workflow.

### How do we make Java 25 not block usage?

There is no absolute guarantee across all locked-down environments, but we can make Java availability smooth and transparent for users without maintaining OS-specific runtime-bundled executables.

Current state in plugin:
- The plugin probes for local Java in system `PATH`, `JAVA_HOME`, and standard platform directories.
- If Java 25+ is not detected, it automatically attempts to download and extract a lightweight JRE 25 from Adoptium Temurin into the user cache directory (`%LOCALAPPDATA%\freerouting\cache\jre\` on Windows).

Hardening plan:
1. **Prioritize local installation:** Always check system Java and `JAVA_HOME` first.
2. **Automated cache download:** If missing, attempt the automatic Adoptium Temurin JRE 25 download and extraction into the cache folder.
3. **Explicit manual installation fallback:** If both local detection and automatic download/extraction fail (due to offline environments, corporate firewalls, proxy authentication, or permission restrictions), **do not fail silently**. Immediately present a clear dialog stating:
   > *"Freerouting requires Java JRE 25 or higher. Automatic download could not be completed. Please install Java JRE 25+ manually from https://adoptium.net/temurin/releases/ and restart the plugin."*
4. Include the direct Adoptium URL and show exact diagnostics (e.g. download failed vs. extraction failed vs. permission denied).

Result:
- Zero maintenance overhead for building and distributing OS-specific bundled runtimes.
- Automated for users with normal internet access.
- Clear, immediate, actionable guidance for users in restricted or offline environments.

## What IPC changes for Freerouting

IPC removes the need for DSN export and import when KiCad supports the live session.
This improves the integration in three ways:

1. Freerouting can read the current board state directly from KiCad.
2. Freerouting can push updates back while the board session remains open.
3. KiCad rules such as copper to edge clearance can be read from the live rule model instead of being lost in DSN export.

## What should stay the same

The routing engine should stay unchanged where possible.
These parts are still reusable:

- RoutingBoard
- BatchAutorouter
- DesignRulesChecker
- RouterSettings
- Most routing and optimization logic

The new work should be limited to IPC integration and the KiCad side bridge.

## Best implementation approach

The best approach is a two layer design using a **Hybrid Local Loopback Bridge** to avoid the complexity of native Unix Domain Sockets (UDS) or Named Pipes in Java.

### Layer 1: KiCad side bridge (Python Plugin)

Use a small KiCad plugin or executable plugin that:
- Detects whether KiCad IPC is available.
- Connects to the running KiCad session via native IPC (Unix Domain Sockets on Linux/macOS, Named Pipes on Windows).
- Reads board data and rules from KiCad.
- Serializes the KiCad board data into a standardized **KiCad JSON format**.
- Starts Freerouting with the API server enabled (binding to `127.0.0.1` with authentication disabled for seamless local operation).
- POSTs the KiCad JSON board data to Freerouting's REST API.
- Listens to Freerouting's streaming API endpoints (SSE/WebSockets) for real-time routing progress and updates.
- Relays routed traces and vias back to KiCad via KiCad's IPC API.

### Layer 2: Freerouting core integration (Java REST API)

Keep the Java routing engine focused on routing and decoupled from KiCad's specific IPC transport layer:
- Implement a **KiCad JSON parser/loader** in Freerouting to deserialize the KiCad JSON board data directly into a `RoutingBoard`.
- Measure and log the performance penalty of JSON serialization/deserialization to evaluate overhead and aid in debugging.
- Expose a new API endpoint (e.g., `PUT /v1/sessions/{sessionId}/monitor`) to set an API session as the **"currently monitored" session**.
- If the Freerouting GUI is enabled, the "currently monitored" session's board and real-time routing progress will be displayed on the GUI, giving plugin users the same level of visual feedback they are used to.
- Stream route results and progress updates back to the Python bridge via existing or new streaming API endpoints.

## Should DSN and IPC run in parallel?

Yes.

Reasons:

- DSN is still needed for other EDA tools.
- Older KiCad versions still need DSN.
- DSN remains useful for offline workflows.
- Parallel support is the safest way to test parity and avoid regressions.

Important clarification:

- Keep DSN format support.
- Do not depend on the legacy SWIG plugin path for future KiCad compatibility.

## Best user experience

The most intuitive user experience is:

- If KiCad supports IPC and the plugin is attached, use IPC automatically.
- If IPC is not available, fall back to DSN.
- Show a clear message about which mode is active.
- Keep the Java installation check and Java launcher logic that already exists in the Python plugin.
- Let the user adjust routing settings before routing starts.
- Never fail silently when runtime discovery fails.
- If a SWIG-only path is detected, show a deprecation warning and suggest upgrading to IPC mode.

## Java installation and launch

The current Python plugin already handles Java detection and runtime download.
That should be reused and hardened.

Recommended behavior:

- Check system PATH, JAVA_HOME, and known install directories for Java 25+ first.
- If missing, attempt automatic download and extraction of Adoptium Temurin JRE 25 to user cache.
- If auto-download/extraction fails, notify the user immediately with the manual download URL (do not bundle platform executables).
- Launch Freerouting as a separate process.
- Keep KiCad responsive.

## Can we keep the current Python code?

Yes, partially.

Reuse the existing Python plugin as the entry point if it already handles:

- Java discovery
- Java installation
- user settings UI
- launching Freerouting

The plugin should be adapted with a focused migration from SWIG calls to IPC calls.

What should be retired over time:

- direct dependence on SWIG-only APIs for export and import in the main routing flow

What should be new:

- IPC detection
- IPC connection handling
- IPC board data extraction
- IPC update sending

## What to build next: Protobuf IPC Migration

### Status of Prior Phases

- **Phase 1: Java JSON Reader/Writer & API Core** — ✅ **Completed in Freerouting Core**
  - Schema: `KiCadBoardJson` DTO defining layers, netclasses, nets, components, pads, traces, vias, zones, and outline.
  - Core Reader/Writer: `KiCadJsonReader` and `KiCadJsonWriter` deserializing/serializing `RoutingBoard`.
  - REST API endpoints:
    - `POST /v1/jobs/{jobId}/input/json` — upload raw KiCad JSON board data.
    - `GET /v1/jobs/{jobId}/output/json` — download routed board as raw KiCad JSON.
    - `GET /v1/jobs/{jobId}/output/json/stream` — real-time SSE stream with CRC32 change detection.
    - `PUT /v1/sessions/{sessionId}/monitor` — binds session board to GUI visualizer.
  - Round-trip unit tests in `KiCadJsonReaderTest`.

- **Phase 2: Transitional SWIG JSON Bridge** — ✅ **Shipped in v2.3 (Deprecated)**
  - Shipped in `integrations/KiCad/kicad-freerouting/plugins/router_json_api.py` and `board_json_helpers.py`.
  - Proved the REST API workflow, but relied on internal `import pcbnew` SWIG iteration.
  - Must be replaced before KiCad 11 removes SWIG.

---

## The Protobuf IPC Architecture (Target Alpha)

```
┌─────────────────────────────────────────────────────────────┐
│ KiCad 10+ Process (PCB Editor GUI)                          │
│   • NNG IPC Server (api.sock / \\.\pipe\kicad-api-...)       │
│   • Protocol Buffers API handlers                           │
│   • Live Board, Footprints, Nets, Tracks, Vias              │
│   • BoardDesignRules (includes copper_edge_clearance)       │
└───────────────────────────┬─────────────────────────────────┘
                            │ Protobuf over NNG (kicad-python / kipy)
                            │ Socket / Pipe: KICAD_API_SOCKET
┌───────────────────────────▼─────────────────────────────────┐
│ KiCad IPC Bridge (Python out-of-process plugin)             │
│   • Discovered via plugin.json manifest                     │
│   • Runs in KiCad-managed venv (isolated dependencies)      │
│   • Reads board + design rules via kipy.KiCad()             │
│   • Maps Protobuf Board model -> KiCadBoardJson schema      │
│   • Starts/attaches Freerouting JAR (localhost REST API)    │
│   • POST /v1/jobs/{id}/input/json                           │
│   • Polls / streams routing result JSON                     │
│   • Maps result JSON -> kipy commit transaction:            │
│       commit = board.begin_commit()                         │
│       board.create_items(tracks, vias)                      │
│       board.push_commit(commit)                             │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP JSON (127.0.0.1:37864)
┌───────────────────────────▼─────────────────────────────────┐
│ Freerouting Application (Java 25)                           │
│   • Headless API Server or Monitored GUI Session            │
│   • KiCadJsonReader -> BatchAutorouter -> KiCadJsonWriter   │
└─────────────────────────────────────────────────────────────┘
```

---

---

## Multi-Version Local Testing Environment (Windows Setup)

The local development environment has three active KiCad installations installed in parallel under `C:\Program Files\KiCad\`:

| Version | Installation Path | Config Directory (`%APPDATA%`) | Cache & Venv Directory (`%LOCALAPPDATA%`) | Plugin Directory (`%USERPROFILE%\Documents`) | Primary Testing Role |
|---|---|---|---|---|---|
| **KiCad 9.0.7** | `C:\Program Files\KiCad\9.0\` | `%APPDATA%\kicad\9.0\` | `%LOCALAPPDATA%\KiCad\9.0\` | `...\Documents\KiCad\9.0\plugins\` | Legacy DSN baseline & early IPC backward-compatibility |
| **KiCad 10.0.2** | `C:\Program Files\KiCad\10.0\` | `%APPDATA%\kicad\10.0\` | `%LOCALAPPDATA%\KiCad\10.0\` | `...\Documents\KiCad\10.0\plugins\` | **Primary Alpha Target**: Stable GUI IPC API over Named Pipes |
| **KiCad 10.99 Nightly** (2026-09-18 build 4083) | `C:\Program Files\KiCad\10.99\` | `%APPDATA%\kicad\10.99\` | `%LOCALAPPDATA%\KiCad\10.99\` | `...\Documents\KiCad\10.99\plugins\` | **Future Target**: Pure SWIG-free validation & headless `kicad-cli api-server` |

---

## Comprehensive Debugging Playbook

To make the IPC feature easy to develop, troubleshoot, and support across all three versions, debugging is organized across six distinct levels:

### Level 1: Version-Specific Diagnostic Console & Tracing

When running on Windows, KiCad suppresses console logs from GUI processes by default. Launch the desired KiCad version with trace logging enabled:

```powershell
# 1. Debugging KiCad 10.0.2 (Primary Alpha Target)
$env:KICAD_ALLOC_CONSOLE = "1"     # Forces KiCad to allocate a console window
$env:KICAD_ENABLE_WXTRACE = "1"    # Enables wxTrace subsystem in release builds
$env:WXTRACE = "KICAD_API"         # Filters trace messages specifically to the API
& "C:\Program Files\KiCad\10.0\bin\kicad.exe"

# 2. Debugging KiCad 10.99 Nightly (KiCad 11 Preview)
$env:KICAD_ALLOC_CONSOLE = "1"
$env:KICAD_ENABLE_WXTRACE = "1"
$env:WXTRACE = "KICAD_API"
& "C:\Program Files\KiCad\10.99\bin\kicad.exe"

# 3. Debugging KiCad 9.0.7 (Legacy Baseline)
$env:KICAD_ALLOC_CONSOLE = "1"
$env:KICAD_ENABLE_WXTRACE = "1"
$env:WXTRACE = "KICAD_API"
& "C:\Program Files\KiCad\9.0\bin\kicad.exe"
```

*Status bar warnings:* In KiCad 10.0+ and 10.99, any unhandled exception or `stderr` print from an IPC plugin action is automatically surfaced in the warning icon at the bottom right of the PCB Editor status bar.

### Level 2: Headless API Server Testing with KiCad 10.99 `kicad-cli`

KiCad 10.99 provides native headless IPC server execution without opening the GUI. This is invaluable for rapid script and bridge testing:

```powershell
# Start headless IPC server for a specific board with custom named pipe:
& "C:\Program Files\KiCad\10.99\bin\kicad-cli.exe" api-server `
    --socket "\\.\pipe\kicad-freerouting-test" `
    "fixtures\bm01.kicad_pcb"
```
In another terminal, connect the Python bridge directly to `\\.\pipe\kicad-freerouting-test`!

### Level 3: Wire-Level Protobuf Message Logging (`kicad_advanced`)

KiCad stores normal user configuration in JSON files (`kicad_common.json`, `kicad.json`, `pcbnew.json`). However, internal developer flags and debug overrides are read from a special **extension-less plain text file** named `kicad_advanced`.

> [!IMPORTANT]
> **File Name & Extension:** The file must be named exactly `kicad_advanced` with **NO extension** (not `kicad_advanced.json`, not `kicad_advanced.ini`, not `kicad_advanced.txt`).
> When creating this file with Windows Notepad or text editors, ensure the editor does not silently append `.txt`. You can create it in PowerShell via:
> ```powershell
> "EnableAPILogging=1" | Out-File -FilePath "$env:APPDATA\kicad\10.0\kicad_advanced" -Encoding ascii -NoNewline
> ```

1. **File Location per Version:**
   - KiCad 10: `%APPDATA%\kicad\10.0\kicad_advanced` (e.g. `C:\Users\<username>\AppData\Roaming\kicad\10.0\kicad_advanced`)
   - KiCad 10.99: `%APPDATA%\kicad\10.99\kicad_advanced`
   - KiCad 9: `%APPDATA%\kicad\9.0\kicad_advanced`
2. **File Contents:**
   A simple key-value assignment:
   ```
   EnableAPILogging=1
   ```
3. **Log Output Location:**
   Upon launch, KiCad detects this flag and streams every serialized Protobuf request and response payload to:
   - KiCad 10: `%USERPROFILE%\Documents\KiCad\10.0\logs\api.log`
   - KiCad 10.99: `%USERPROFILE%\Documents\KiCad\10.99\logs\api.log`
   - KiCad 9: `%USERPROFILE%\Documents\KiCad\9.0\logs\api.log`
   *(Note: Remember to delete or disable this after debugging because the log grows rapidly with board size).*

### Level 4: Standalone External Debugging (Outside KiCad)

You do **not** need to click the toolbar button inside KiCad to debug the Python IPC bridge:
1. Start KiCad (e.g. KiCad 10.0.2) and open the target PCB.
2. In KiCad, ensure **Preferences > Plugins > Enable KiCad API** is checked.
3. Open a terminal or IDE (VS Code, PyCharm) and run the bridge script directly:
   ```powershell
   python -m integrations.KiCad.kicad_freerouting.ipc_bridge.standalone_runner `
       --board-name "my_board" `
       --dump-json `
       --debug
   ```
4. If running outside KiCad without `KICAD_API_SOCKET`, the script automatically probes the active Windows named pipes (`\\.\pipe\kicad-api*`).
5. You can set standard Python breakpoints (`pdb.set_trace()` or IDE visual breakpoints) to inspect the Protobuf board hierarchy interactively.

### Level 5: KiCad-Managed Virtual Environment Inspection

KiCad manages the Python virtual environment for the plugin per version:
- KiCad 10: `%LOCALAPPDATA%\KiCad\10.0\python-environments\app.freerouting.kicad-plugin`
- KiCad 10.99: `%LOCALAPPDATA%\KiCad\10.99\python-environments\app.freerouting.kicad-plugin`
- KiCad 9: `%LOCALAPPDATA%\KiCad\9.0\python-environments\app.freerouting.kicad-plugin`

**Manual activation:** Activate the virtual environment directly in a terminal to check installed versions (`pip list`) or manually install test packages:
```powershell
# Activate KiCad 10 plugin venv
& "$env:LOCALAPPDATA\KiCad\10.0\python-environments\app.freerouting.kicad-plugin\Scripts\Activate.ps1"
python -c "import kipy; print(kipy.__file__)"
```
**Force Environment Reset:** If dependencies become corrupted, right-click the plugin action in KiCad's **Preferences > Action Plugins** and select **"Recreate Plugin Environment"**.

### Level 6: Payload Diffing & Artifact Verification

Whenever routing runs in IPC mode, the bridge automatically dumps debug artifacts to the Freerouting log folder (`%LOCALAPPDATA%\freerouting\logs\kicad\`):
- `freerouting_ipc_input.json`: Board and rule model extracted via IPC.
- `freerouting_ipc_output.json`: Routed traces and vias received from Freerouting.
- A built-in diff utility compares `freerouting_ipc_input.json` against the DSN export from `HeadlessBoardManager` to flag any discrepancies in coordinate scaling, pad offsets, or clearance classes before routing starts.

---

## KiCad API Enablement & User Onboarding

### Is Manual API Enablement Required for Users?

**Yes, for KiCad 9 and KiCad 10.**
In KiCad 9 and 10, the IPC API server is **disabled by default**. The setting is located in:
**Preferences > Plugins > Enable KiCad API** (checkbox).

When this checkbox is unchecked:
- KiCad does not open the named pipe (`\\.\pipe\kicad-api-...`) or Unix domain socket (`api.sock`).
- Any attempt by the plugin to connect to KiCad IPC fails immediately with a pipe/socket connection error.

### Why DSN Must Remain the Default

Because the KiCad API is disabled by default, **Freerouting cannot make IPC the default routing mode in KiCad 9/10 without breaking out-of-the-box operation for new users**.
- **DSN Mode** requires zero checkboxes, zero configuration, and works immediately upon plugin installation.
- **IPC Mode** is an opt-in Alpha feature for users who enable the API server.

### User Guidance & Non-Blocking Auto-Fallback

If a user selects IPC mode in Freerouting settings but the API server is not running:
1. The plugin probes for the socket/pipe.
2. If connection is refused, it shows a clear, non-fatal dialog:
   > *"KiCad IPC API is not enabled.*
   >
   > *To use high-speed IPC mode:*
   > *1. Open KiCad **Preferences > Plugins**.*
   > *2. Check **'Enable KiCad API'**.*
   > *3. Restart KiCad.*
   >
   > *Would you like to route using standard DSN mode for this session?"*
3. Clicking **"Use DSN Mode"** routes the board immediately via DSN without failing or cancelling the user's workflow.

---

## Shipping Strategy: Testable Alpha in Next Release

The next release will ship KiCad Protobuf IPC as an **opt-in Alpha feature** alongside the rock-solid DSN production default.

### Routing Mode Priority & Settings

In `config.py` (and the plugin configuration dialog):
```python
ROUTING_MODE_DSN = "DSN"       # Default: Stable Specctra DSN export/import
ROUTING_MODE_IPC = "IPC"       # New Alpha: Official Protobuf IPC API (KiCad 9/10+)
ROUTING_MODE_JSON = "JSON"     # Transitional: v2.3 SWIG bridge (fallback)
```

1. **Default Mode:** `ROUTING_MODE_DSN` remains default for all users.
2. **Opt-in Alpha:** Users can switch to `ROUTING_MODE_IPC` in the plugin UI or `config.py`.
3. **Graceful Auto-Fallback:** If `ROUTING_MODE_IPC` is selected but the IPC socket is unavailable (e.g. user has not enabled API in preferences), the plugin displays a non-fatal warning and offers an instant one-click fallback to DSN mode.
4. **Visual Mode Indicator:** The routing progress dialog clearly displays the active engine:
   - `[Mode: Protobuf IPC (Alpha)]`
   - `[Mode: Specctra DSN (Standard)]`

---

## Benefits and Risks Assessment

### Benefits Matrix

| Benefit | Description | Impact |
|---|---|---|
| **Future-Proof Against SWIG EOL** | SWIG Python bindings will be removed in KiCad 11. Protobuf IPC ensures Freerouting continues working on future KiCad versions. | **Critical** |
| **Complete Design Rule Fidelity (#558)** | Directly extracts `copper_edge_clearance` via `constraints.copper_edge_clearance().value_nm()` and custom DRU constraints that DSN discards. | **High** |
| **Atomic Undo/Redo Transactions** | `board.begin_commit()` / `push_commit()` wraps all newly routed tracks and vias into a single undo step in KiCad. | **High** |
| **Process & Memory Isolation** | The plugin and Java router run in dedicated external processes. Out-of-memory or routing timeouts cannot crash KiCad. | **High** |
| **No File System Intermediate Churn** | Avoids writing temporary `.dsn` and `.ses` files to the user's project directory. | **Medium** |
| **Independent Python Runtime** | Plugin runs in an isolated virtual environment (`requirements.txt`), avoiding DLL conflicts with KiCad's bundled Python. | **Medium** |

### Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation Strategy |
|---|---|---|---|
| **IPC Server Disabled by Default** | High | High | Plugin actively checks socket connectivity. If unavailable, guides the user with step-by-step instructions to enable it, and falls back to DSN. |
| **Initial Venv Creation Latency** | High (first run) | Low | First launch can take 10–20 seconds while KiCad installs `kicad-python`. Document this in release notes; toolbar icon appears once venv is built. |
| **GUI-Only in KiCad 9 & 10** | 100% | Medium | Accepted constraint for KiCad 9/10. Automated CI uses recorded mock fixtures. Full headless CI arrives with KiCad 11 `kicad-cli api-server`. |
| **Main UI Thread Blocking in KiCad** | Medium | Medium | KiCad IPC is synchronous. If a modal dialog is open in KiCad, requests timeout. Implement retry loops with clear timeouts and user abort buttons. |
| **Java 25 Dependency Availability** | Medium | Medium | Hardened `java_utils.py` auto-downloads Temurin JRE 25 to user cache. If auto-download/extraction fails, displays a clear dialog with the download link for manual installation (no OS-bundled executables). |

---

## Comprehensive Implementation Plan & Task Lists

This implementation plan details the phases, component touchpoints, task lists, and exit criteria to bring the KiCad Protobuf IPC integration from prototype to a testable Alpha in the next release, and ultimately to the production default for KiCad 11.

```
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 1: Standalone IPC Spike & Read-Path Prototype (Spike / Immediate)│
│   • Standalone script using kipy                                       │
│   • Board & Design Rule extraction (#558 copper_edge_clearance)        │
│   • KiCadJsonReader outline clearance class mapping                    │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│ Phase 2: Write-Back Transaction & Undo/Redo Engine                     │
│   • Map routed traces/vias -> kipy objects                             │
│   • board.begin_commit() / push_commit() transactional write           │
│   • Single-step Ctrl+Z undo validation                                 │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│ Phase 3: Plugin Integration & Alpha Release Packaging (Target Release) │
│   • plugin.json manifest & requirements.txt for KiCad venv             │
│   • ipc_entry.py out-of-process runner                                 │
│   • Tri-mode config (DSN default, IPC alpha, JSON transitional)        │
│   • Socket connectivity check & graceful auto-fallback to DSN          │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│ Phase 4: CI Automation & Parity Testing                                │
│   • Recorded mock IPC session fixtures (no KiCad/Xvfb needed in CI)    │
│   • Automated Python & Java unit test suites                           │
│   • DSN vs. IPC parity metrics (completion, vias, DRC)                 │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│ Phase 5: KiCad 11 Headless Readiness & Deprecation of SWIG             │
│   • kicad-cli api-server validation                                    │
│   • Promote IPC to recommended default                                 │
│   • Remove legacy SWIG bridge (board_json_helpers.py)                  │
└────────────────────────────────────────────────────────────────────────┘
```

---

### Phase 1: Standalone IPC Spike & Read-Path Prototype

**Goal:** Establish an external socket/pipe connection to a running KiCad 10 instance, extract the complete board structure and design rules, serialize to `KiCadBoardJson`, and verify loading into Freerouting.

#### Tasks:
- [ ] **Python Bridge Core (`ipc_bridge/`):**
  - [ ] Add `integrations/KiCad/kicad-freerouting/ipc_bridge/` package structure.
  - [ ] Implement `ipc_connection.py`: Connect to KiCad using `kicad-python` (`kipy.KiCad`). Automatically detect `KICAD_API_SOCKET` or fallback to OS default socket paths (`api.sock` on Linux/macOS, `\\.\pipe\kicad-api` on Windows).
  - [ ] Implement `ipc_board_reader.py`:
    - Extract board outline from `board.get_shapes()` on `Edge.Cuts`.
    - Extract layer stack, copper layers, and planes.
    - Extract nets and netclasses from `board.get_net_classes()` and `board.get_nets()`.
    - Extract footprints and pads (positions, shapes, drills, rotations, layers).
    - Extract pre-existing tracks, vias, and keepouts/zones.
  - [ ] **Design Rules & Clearance Extraction (Issue #558):**
    - Query `board.get_design_rules()`.
    - Extract `constraints.copper_edge_clearance().value_nm()` and map to `KiCadBoardJson.outline.clearance` (converted to microns).
    - Extract netclass clearance and custom rule clearances into `KiCadBoardJson.clearanceRules`.
  - [ ] Implement `standalone_runner.py`: CLI script to run against an active KiCad board, dumping `freerouting_ipc_input.json` for validation.
- [ ] **Freerouting Java Core (`io.kicad`):**
  - [ ] In `KiCadJsonReader.java`, check if `boardJson.outline.clearance > 0`.
  - [ ] When outline clearance is present, dynamically append a dedicated `board_edge` clearance class to `ClearanceMatrix` (mirroring `HeadlessBoardManager.applyCopperToEdgeClearance()`).
  - [ ] Set clearance matrix distances between `board_edge` and conductor classes to `outline.clearance`.
  - [ ] Assign `outlineShapes` the `board_edge` clearance class number instead of default class 1.
  - [ ] Add unit test in `KiCadJsonReaderTest` verifying that outline clearance creates the expected clearance class and matrix entries.

**Deliverables:**
- Standalone CLI runner capable of extracting board JSON from running KiCad 10.
- `KiCadJsonReader` natively respecting `outline.clearance`.

**Exit Criteria:**
- One real PCB open in KiCad 10 is extracted via IPC, serialized to JSON, and loaded into Freerouting with identical outline dimensions and correct edge clearance without CLI `--router.copper_to_edge_clearance_um` overrides.

---

### Phase 2: Write-Back Transaction & Undo/Redo Engine

**Goal:** Receive the routed JSON output from Freerouting and write the new tracks and vias back into the active KiCad session as a single atomic transaction.

#### Tasks:
- [ ] **Python IPC Writer (`ipc_bridge/ipc_board_writer.py`):**
  - [ ] Parse `KiCadBoardJson.traces` and `KiCadBoardJson.vias` from Freerouting API output (`GET /v1/jobs/{id}/output/json`).
  - [ ] Convert trace segments to `kipy` track primitives with correct layer IDs, start/end coordinates, and widths.
  - [ ] Convert vias to `kipy` via primitives with correct coordinates, diameters, drill sizes, and layer ranges.
  - [ ] Implement atomic commit transaction:
    ```python
    commit = board.begin_commit()
    try:
        board.create_items(new_tracks + new_vias)
        board.push_commit(commit)
    except Exception as e:
        board.drop_commit(commit)
        raise
    ```
- [ ] **Transactional Safety & Cleanup:**
  - [ ] If routing existing partially routed nets, optionally remove unstitched or replaced tracks in the same commit.
  - [ ] Refresh KiCad view / trigger canvas redraw if required by `kipy`.
- [ ] **Interactive Validation:**
  - [ ] Route a sample board, observe tracks appearing immediately in KiCad PCB editor.
  - [ ] Press `Ctrl+Z` in KiCad: verify that all routed tracks and vias disappear in a single undo step.
  - [ ] Press `Ctrl+Y` in KiCad: verify that all routed tracks and vias reappear cleanly.

**Deliverables:**
- Full bidirectional IPC bridge supporting read and atomic transactional write-back.

**Exit Criteria:**
- Routed tracks appear in KiCad without restarting the application or reloading the board, and can be completely undone with a single `Ctrl+Z`.

---

### Phase 3: Plugin Integration & Alpha Release Packaging

**Goal:** Package the IPC bridge as a standard KiCad action plugin that ships with the next Freerouting release, allowing users to opt in and test the Alpha version safely.

#### Tasks:
- [ ] **Plugin Manifest & Runtime Configuration:**
  - [ ] Create `integrations/KiCad/kicad-freerouting/plugin.json`:
    ```json
    {
      "$schema": "https://go.kicad.org/api/schemas/v1",
      "identifier": "app.freerouting.kicad-plugin",
      "name": "Freerouting",
      "description": "Advanced PCB autorouter for KiCad",
      "runtime": { "type": "python" },
      "actions": [
        {
          "identifier": "route_board",
          "name": "Auto-route Board",
          "description": "Route active PCB with Freerouting",
          "entrypoint": "ipc_entry.py",
          "scopes": ["pcb"],
          "show-button": true,
          "icons-light": ["resources/icon_24x24.png"],
          "icons-dark": ["resources/icon_24x24.png"]
        }
      ]
    }
    ```
  - [ ] Create `integrations/KiCad/kicad-freerouting/requirements.txt` with pinned dependency:
    ```
    kicad-python>=0.1.0
    ```
  - [ ] Create `ipc_entry.py`: Out-of-process entry point invoked by KiCad when the toolbar button is clicked.
- [ ] **Routing Mode Management & Auto-Fallback:**
  - [ ] Update `config.py`:
    - `ROUTING_MODE_DSN = "DSN"` (Default)
    - `ROUTING_MODE_IPC = "IPC"` (Alpha)
    - `ROUTING_MODE_JSON = "JSON"` (Transitional SWIG bridge)
  - [ ] In `ipc_entry.py`, test IPC connectivity:
    - If `ROUTING_MODE_IPC` is active and KiCad IPC is connected, proceed via IPC bridge.
    - If IPC connection fails (e.g. API disabled in preferences), present user-friendly dialog:
      *"Cannot connect to KiCad IPC. To use IPC mode, please enable the API server under Preferences > Plugins > Enable KiCad API, and restart KiCad. Would you like to fall back to standard DSN mode for now?"*
    - On user confirmation, fall back smoothly to DSN routing without failing the operation.
  - [ ] Validate Java 25+ detection in `java_utils.py`: probe system `PATH` and `JAVA_HOME`, attempt automatic Adoptium JRE 25 download into user cache (`%LOCALAPPDATA%\freerouting\cache\jre\`), and if download/extraction fails, display an explicit manual installation dialog with direct download URL (no bundled OS executables).
- [ ] **UI Progress Dialog Updates:**
  - [ ] Show active mode tag: `[Mode: Protobuf IPC (Alpha)]`.
  - [ ] Display real-time progress polled from Freerouting API.
  - [ ] Support cancel / terminate button.
- [ ] **Release Packaging:**
  - [ ] Update plugin zip build scripts to package `plugin.json`, `requirements.txt`, and the `ipc_bridge` module.
  - [ ] Document the Alpha test instructions in release notes and `README.md`.

**Deliverables:**
- Distributable KiCad plugin package featuring opt-in IPC Alpha mode alongside production DSN mode.

**Exit Criteria:**
- The plugin can be installed in KiCad 10, executes via the toolbar icon, routes successfully in IPC mode when enabled, and falls back gracefully to DSN when IPC is disabled.

---

### Phase 4: CI Automation & Parity Testing

**Goal:** Establish regression testing for the IPC bridge in CI without requiring a GUI KiCad instance, and benchmark IPC routing quality against DSN.

#### Tasks:
- [ ] **Offline Mocking & Session Recording:**
  - [ ] Add session recording flag (`--record-ipc-session <file.json>`) to capture raw protobuf structures from live KiCad runs.
  - [ ] Implement `MockIpcClient` that serves recorded responses for `kipy.KiCad`.
  - [ ] Add automated pytest suite in `integrations/KiCad/` running on GitHub Actions.
- [ ] **Java Parity Test Suite:**
  - [ ] Re-enable `Issue733DsnJsonParityTest` in Gradle test suite.
  - [ ] Verify that `RoutingBoard` constructed from IPC JSON has identical:
    - Net counts and connectivity.
    - Conductor clearances.
    - Board outline dimensions.
    - Via padstack geometry.
- [ ] **Benchmark Matrix:**
  - [ ] Run benchmark boards (e.g. `bm01`, `bm02`) through both DSN and IPC pipelines.
  - [ ] Confirm zero new clearance violations, equivalent completion rate, and identical or improved via counts.

**Deliverables:**
- Headless CI test suite for IPC bridge.
- Parity report comparing DSN vs. IPC across standard benchmark boards.

**Exit Criteria:**
- Zero test regressions; automated CI passes reliably on Windows, macOS, and Linux runners.

---

### Phase 5: KiCad 11 Headless Readiness & SWIG Deprecation

**Goal:** Ensure readiness for KiCad 11 where SWIG is permanently removed, support headless `kicad-cli api-server`, and promote IPC to the default routing mode.

#### Tasks:
- [ ] **KiCad 11 Preview Testing:**
  - [ ] Test IPC bridge against KiCad 11 nightly builds.
  - [ ] Validate headless IPC integration via `kicad-cli api-server`.
- [ ] **Retirement of SWIG Code Paths:**
  - [ ] Remove `board_json_helpers.py` (the manual SWIG walker).
  - [ ] Mark SWIG DSN export in the plugin as legacy/deprecated.
- [ ] **Promotion:**
  - [ ] Switch `DEFAULT_ROUTING_MODE` from `DSN` to `IPC` for KiCad 10+ installations.
  - [ ] Update all user documentation, tutorials, and integration guides.

**Deliverables:**
- Fully modernized, native KiCad IPC autorouting integration ready for KiCad 11.

**Exit Criteria:**
- Freerouting functions seamlessly on KiCad 11 nightly without any SWIG dependencies.

---

## Conclusion

Migrating to KiCad's official Protobuf IPC API is the strategic, long-term foundation for Freerouting's KiCad integration. By maintaining the DSN workflow as the rock-solid fallback and reusing Freerouting's existing JSON REST API architecture, we achieve zero risk of regression while unlocking full design rule fidelity (Issue #558) and forward compatibility with KiCad 11. Shipping this as an opt-in Alpha in the upcoming release allows enthusiastic community testing while preserving production stability.
