# KiCad IPC API Research for Freerouting

## Purpose

This document summarizes what changed with KiCad 10, what the KiCad IPC API is, and how Freerouting should approach it.

## Verified facts

- KiCad 10 is officially released. The KiCad project announced Version 10.0.0 on 2026-03-20.
- The official IPC developer documentation covers KiCad 9 and 10 as the main plugin targets.
- The IPC API is not a gRPC-over-TCP service.
- KiCad uses a per-instance IPC endpoint:
  - Unix domain socket on Linux and macOS
  - Named pipe on Windows
- KiCad 9 and 10 IPC support is GUI connected only.
- Official docs state that headless IPC support through kicad-cli arrives in KiCad 11.
- The IPC API is disabled by default in KiCad 10.
- Forum reports from March and April 2026 show plugin discovery and attachment friction in KiCad 10.x.
- KiCad team guidance to Freerouting confirms the SWIG runtime is deprecated and that SWIG-based plugins will not work in KiCad nightly or 11.0.
- KiCad recommends plugin authors migrate to IPC during the KiCad 11 development cycle.

## Recommendation summary

- Keep the DSN and IPC paths in parallel.
- Do not replace DSN support in Freerouting core.
- Use IPC as a KiCad-native integration path for supported versions.
- Treat IPC as a bridge around the running KiCad session, not as a standalone file loader.
- Start migration now because the current SWIG-based KiCad plugin path is end-of-life for nightly and 11.0.
- Keep the first implementation small and defensive.

## Immediate impact of the KiCad team message

The message from the KiCad team changes the urgency and clarifies scope.

- The current plugin implementation that calls SWIG APIs (for example `pcbnew.ExportSpecctraDSN` and `pcbnew.ImportSpecctraSES`) is on a deprecating runtime and cannot be the long-term integration path.
- DSN as a file format is still useful and should remain supported in Freerouting.
- The main migration is not "remove DSN". The migration is "replace SWIG plugin execution path with an IPC-based plugin path".
- For KiCad users, IPC is now a required strategic path, not an optional enhancement.

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

There is no absolute guarantee across all locked-down environments, but we can make Java availability non-blocking for most users.

Current state in plugin:

- The plugin already detects local Java.
- If needed, it can download and extract JRE 25 from Adoptium into a temp directory.

Recommended hardening plan:

1. Keep current auto-download logic as fallback.
2. Add release-packaged runtime option so users can run without network access:
   - ship platform-specific Freerouting bundles that include a vetted JRE 25 runtime
   - default plugin config points to bundled runtime first
3. Add better fallback order in plugin messaging:
   - bundled runtime
   - system Java or JAVA_HOME
   - temp downloaded runtime
4. Add explicit diagnostics in error dialogs:
   - show exact path attempted
   - show whether network download failed, extraction failed, or version check failed
5. Add an "offline install" help link in dialog text for enterprise environments.

Result:

- Most users are no longer blocked by manual Java setup.
- Locked-down users still get a clear manual path.

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

- check for Java 25 first
- install it if needed
- launch Freerouting as a separate process
- keep KiCad responsive

Additional recommendation:

- offer a bundled runtime in release artifacts so first run works even without internet access

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

## What to build first

### Phase 1: Read only IPC bridge & JSON Loader

Goal: prove that Freerouting can read a KiCad board from the live session via the Python bridge and JSON serialization.

Tasks:
- Define the **KiCad JSON schema** for board data (layers, nets, pads, tracks, vias, zones, rules).
- Implement `KiCadJsonReader` in Freerouting to deserialize the JSON stream into a `RoutingBoard`.
- Measure and log the performance penalty of JSON serialization/deserialization.
- Implement the **"currently monitored" session API endpoint** to bind the API session to the active GUI visualizer.
- Create tests with a mocked JSON payload.

Exit criteria:
- Board load works via JSON POST.
- If GUI is enabled, the loaded board is displayed and progress is visible.
- Existing tests still pass.

### Phase 2: Write back support & Streaming API

Goal: push routed traces and vias back to KiCad via the Python bridge.

Tasks:
- Expose routed traces and vias in a JSON format via the REST API.
- Use streaming API endpoints (SSE/WebSockets) to send real-time progress and incremental updates.
- Python bridge receives updates and writes them back to KiCad via KiCad IPC.

Exit criteria:
- Routed traces appear in KiCad.
- The board remains consistent after updates.

### Phase 3: Plugin integration

Goal: make IPC the normal path when available.

Tasks:

- update the Python plugin flow
- detect KiCad IPC availability
- choose IPC or DSN automatically
- add a clear settings dialog if needed

Exit criteria:

- the user can route from KiCad without manual file export
- DSN fallback still works

### Phase 4: Testing and parity

Goal: verify that IPC and DSN produce equivalent results.

Tasks:

- compare routing completion rate
- compare via count
- compare trace length
- compare clearance violation count
- compare runtime and memory use

Exit criteria:

- no regressions in DSN mode
- IPC mode behaves predictably
- any differences are understood and documented

## Issue 558 and copper to edge clearance

KiCad IPC is important because it can expose rule data that DSN may lose.
That makes IPC the better path for copper to edge clearance.

For DSN users, keep the explicit copper to edge clearance setting in Freerouting so the gap can still be closed.

## Risks

- IPC is only available in the running GUI session in KiCad 9 and 10.
- API coverage can still change between KiCad releases.
- The IPC API is disabled by default, so users may need clearer guidance.
- A KiCad version mismatch may break the bridge until it is updated.
- Live update timing may need tuning to avoid UI churn.
- Some users run in environments where Java download is blocked by policy or proxy rules.

## Testing strategy

Use three levels of tests:

1. Unit tests with a mocked IPC bridge
2. Integration tests with a running KiCad session
3. Parity tests comparing DSN and IPC results

Prefer a small and repeatable first test case.
Do not start with a large board.

## Practical next step list

1. Freeze the SWIG-based path as legacy and avoid new feature work there.
2. Confirm the KiCad IPC model in the plugin layer.
3. Keep DSN support unchanged in Freerouting core.
4. Add a DSN compatibility check against current KiCad releases in CI.
5. Add a small IPC bridge that only reads board data first.
6. Write tests before adding write-back support.
7. Add live update support only after the read path is stable.
8. Add bundled Java runtime option to release packaging and plugin config.
9. Update user documentation after bridge and runtime flow are stable.

## Conclusion

KiCad 10 is released and the IPC API is real.
With KiCad signaling SWIG runtime deprecation for nightly and 11.0, IPC migration is now a required compatibility track.
The safest plan is to keep DSN data support, move KiCad plugin execution to IPC, and implement in small steps.

That gives us:

- compatibility with older KiCad and other tools
- a clean path to live integration in KiCad 10
- a lower risk rollout
- a better base for future KiCad 11 headless support