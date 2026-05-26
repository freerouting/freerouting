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

## Recommendation summary

- Keep the DSN and IPC paths in parallel.
- Do not replace DSN support.
- Use IPC as a KiCad-native integration path for supported versions.
- Treat IPC as a bridge around the running KiCad session, not as a standalone file loader.
- Keep the first implementation small and defensive.

## Direct answers to current product questions

### Will the classic KiCad to Freerouting DSN path still work?

Most likely yes in the near to medium term, but it should be treated as "supported legacy" rather than "strategic future".

What we can reasonably expect:

- KiCad still has the Specctra DSN and SES import and export path in current releases.
- There is no clear signal that this path will be removed immediately.
- KiCad development direction is clearly moving toward IPC for plugin workflows.

Practical interpretation for Freerouting:

- Keep DSN as a first-class compatibility path.
- Expect slower feature parity on DSN compared to IPC, especially for newer rule details.
- Add a regression check in CI that validates DSN export plus import still works against current KiCad release channels.

### Is IPC worth the implementation and maintenance burden?

Yes, if we keep scope tight and phase it.

Why users benefit enough:

- Fewer failure points from manual export and import steps.
- Better access to live design-rule context.
- Better long-term alignment with KiCad plugin architecture.
- Better path to richer UX such as progress updates and interactive control.

Why burden stays manageable:

- Routing core stays the same.
- Most new work is in adapter code and plugin flow.
- DSN remains fallback, so IPC rollout does not need to be all-or-nothing.

Decision rule:

- IPC is justified if Phase 1 can be delivered without touching core routing algorithms and without regressing DSN workflow.

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

The best approach is a two layer design.

### Layer 1: KiCad side bridge

Use a small KiCad plugin or executable plugin that:

- detects whether IPC is available
- connects to the running KiCad session
- reads board data and rules
- starts Freerouting
- relays progress and updates

This is the most practical first step because the official KiCad IPC docs are centered on add-on development, not on a standalone Java client.

### Layer 2: Freerouting core integration

Keep the Java routing engine focused on routing.
Add a small IPC aware input and output layer that:

- converts KiCad session data into RoutingBoard
- converts routed traces and vias back into KiCad updates
- handles errors and reconnects cleanly

## Should DSN and IPC run in parallel?

Yes.

Reasons:

- DSN is still needed for other EDA tools.
- Older KiCad versions still need DSN.
- DSN remains useful for offline workflows.
- Parallel support is the safest way to test parity and avoid regressions.

Do not remove the legacy plugin path yet.

## Best user experience

The most intuitive user experience is:

- If KiCad 10 or later is available, use IPC automatically.
- If IPC is not available, fall back to DSN.
- Show a clear message about which mode is active.
- Keep the Java installation check and Java launcher logic that already exists in the Python plugin.
- Let the user adjust routing settings before routing starts.
- Never fail silently when runtime discovery fails.

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

Yes, mostly.

Reuse the existing Python plugin as the entry point if it already handles:

- Java discovery
- Java installation
- user settings UI
- launching Freerouting

The plugin should be adapted, not replaced.

What should be new:

- IPC detection
- IPC connection handling
- IPC board data extraction
- IPC update sending

## What to build first

### Phase 1: Read only IPC bridge

Goal: prove that Freerouting can read a KiCad board from the live session.

Tasks:

- add IPC bridge scaffolding
- connect to the live KiCad session
- read board layers, nets, pads, tracks, vias, zones, and rules
- map KiCad data into RoutingBoard
- create tests with a mocked bridge

Exit criteria:

- board load works
- routing core sees the same board data as DSN mode
- existing tests still pass

### Phase 2: Write back support

Goal: push routed traces and vias back to KiCad.

Tasks:

- send route results back into KiCad
- batch updates to reduce churn
- keep progress updates simple and stable
- handle reconnect and error cases

Exit criteria:

- routed traces appear in KiCad
- the board remains consistent after updates

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

1. Confirm the KiCad IPC model in the plugin layer.
2. Keep DSN support unchanged.
3. Add a DSN compatibility check against current KiCad releases in CI.
4. Add a small IPC bridge that only reads board data first.
5. Write tests before adding write-back support.
6. Add live update support only after the read path is stable.
7. Add bundled Java runtime option to release packaging and plugin config.
8. Update user documentation after bridge and runtime flow are stable.

## Conclusion

KiCad 10 is released and the IPC API is real, but the best approach for Freerouting is still gradual.
The safest and most intuitive plan is to keep DSN, add IPC in parallel, and implement IPC in small steps.

That gives us:

- compatibility with older KiCad and other tools
- a clean path to live integration in KiCad 10
- a lower risk rollout
- a better base for future KiCad 11 headless support