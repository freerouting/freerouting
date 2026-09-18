# Open Backlog: Triage, Investigations & Decisions

**Date:** September 18, 2026  
**Repository:** `freerouting/freerouting`  
**Target Milestone:** Freerouting v2.5.0 / v2.5.1  
**Scope:** Active backlog triage with status tracking for recently merged/closed items

---

## 1. Executive Summary & Decision Matrix

### 1.1. Pull Requests (6 Active, 5 Merged/Closed)

| PR | Domain | Status | Title / Description | Action / Recommendation |
| :--- | :--- | :--- | :--- | :--- |
| **[PR #909](https://github.com/freerouting/freerouting/pull/909)** | KiCad / Python | `MERGED` | **Fixes Issue #908.** Check Specctra SES file existence before importing in KiCad plugin. Eliminates error dialogs on clean close without routing. | **Merged into master** (`a6e14c4b4`). Closed #908. |
| **[PR #891](https://github.com/freerouting/freerouting/pull/891)** | CI / Packaging | `MERGED` | Add Linux AppImage build support via `quick-sharun` for portability across distributions. | **Merged into master** (`96563db32`). |
| **[PR #819](https://github.com/freerouting/freerouting/pull/819)** | GUI / Java | `MERGED` | Fix GUI startup thread confinement (dispatch GUI startup synchronously onto Swing EDT). Prevents `ScreenMessages` race condition. | **Merged into master** (`8ce0f757e`). Closed #819. |
| **[PR #843](https://github.com/freerouting/freerouting/pull/843) / [PR #919](https://github.com/freerouting/freerouting/pull/919)** | API / Server | `MERGED` | Make scheduler maximum parallel jobs configurable via `--api_server.max_parallel_jobs` and environment variable with dynamic `cores-1` default. | **Superseded by PR #919 & merged into master** (`db16d292d`). Closed #843. |
| **[PR #810](https://github.com/freerouting/freerouting/pull/810)** | CI | `MERGEABLE` | Update pre-commit workflow cache configuration. | **Low-risk merge.** Improves CI caching. |
| **[PR #793](https://github.com/freerouting/freerouting/pull/793) / [PR #920](https://github.com/freerouting/freerouting/pull/920)** | Engine / CLI | `MERGED / CLOSED` | Headless fixes, Specctra type protect handling, and routing heuristics. Audited against master: 5 fixes already resolved/inapplicable; directory stream leak cherry-picked and tested. | **Superseded by PR #920 & merged into master.** Closed PR #793. Fixed stream leak in `RoutingJobScheduler`; experimental heuristics discarded per author advice. |
| **[PR #888](https://github.com/freerouting/freerouting/pull/888)** | GUI | `MERGEABLE` | Improve inspect mode GUI (canvas jump removal, toolbar layout, context menu width clamping). | **Defer to v2.6.** UI refinement. |
| **[PR #870](https://github.com/freerouting/freerouting/pull/870)** | GUI | `MERGEABLE` | Remove separate window for manual rule selection in exchange for inline panel in router parameters. | **Defer to v2.6.** UI cleanup. |
| **[PR #809](https://github.com/freerouting/freerouting/pull/809)** | GUI | `CONFLICTING` | Reimplemented trace-width editing for net classes in GUI table editor (addresses #796). | **Rebase & Review for v2.6.** |
| **[PR #890](https://github.com/freerouting/freerouting/pull/890)** | Web / Docs | `MERGEABLE` | Render EDA cards with brand logos and update legal disclaimers. | **Review for website repo/docs.** |
| **[PR #743](https://github.com/freerouting/freerouting/pull/743)** | GUI / Engine | `Draft / CONFLICTING` | Add `RoutingEtaCalculator` and improve ETA functionality in status bar. | **Defer.** Needs algorithmic rework across multi-pass stages. |

---

### 1.2. Issues (15 Active, 2 Closed)

| Issue | Domain / Tags | Priority / Milestone | Title & Summary | Status / Next Step |
| :--- | :--- | :--- | :--- | :--- |
| **[#909](https://github.com/freerouting/freerouting/pull/909) / [#908](https://github.com/freerouting/freerouting/issues/908)** | KiCad, GUI, Linux | Medium / Future | Closing Freerouting without starting routing tries to import non-existing `freerouting.ses` file in KiCad. | **CLOSED.** Fixed by PR #909. |
| **[#905](https://github.com/freerouting/freerouting/issues/905)** | CI, macOS | Medium / 2.5 | Package native Intel (`x86_64`) macOS DMG installer via `macos-15-intel` runner (supported through August 2027). | **CLOSED.** Implemented in release workflow. |
| **[#903](https://github.com/freerouting/freerouting/issues/903)** | GUI, UX | Low / Future | Micro Surveying within Freerouting: passive in-app user feedback & micro-surveys without modals/popups. | Needs architectural design for backend polling/privacy before any code. |
| **[#879](https://github.com/freerouting/freerouting/issues/879)** | routing-engine | High / Future | Padstack data structure in Freerouting does not support custom pad semantics (arbitrary non-convex padstacks converted to convex hulls). | Architectural refactoring of padstack geometry representation. |
| **[#856](https://github.com/freerouting/freerouting/issues/856)** | Integration | Low / Future | Altium Designer Integration for Freerouting (direct export/import workflow). | Third-party EDA integration script/plugin. |
| **[#802](https://github.com/freerouting/freerouting/issues/802)** | GUI | Medium / Future | Comprehensive UX overhaul (unit labeling, status bar, dialog scaling, UI tests). | Scope into milestone iterations. |
| **[#787](https://github.com/freerouting/freerouting/issues/787)** | KiCad, API, Python | Medium / Future | KiCad Plugin: Migrate from SWIG JSON bridge to Protocol Buffers IPC. | Future modernization of KiCad IPC bridge. |
| **[#758](https://github.com/freerouting/freerouting/issues/758)** | GUI, Windows | Low / Future | Add context menu action and keyboard shortcut to unfix fixed board items. | GUI context menu enhancement. |
| **[#750](https://github.com/freerouting/freerouting/issues/750)** | GUI | Low / Future | Fix ratsnest calculation and clearance checking during interactive component dragging; improve snap to grid. | Investigate repaint frequency and drag cursor offset. |
| **[#747](https://github.com/freerouting/freerouting/issues/747)** | GUI | Medium / Future | Unify unit conversions and labeling (mils, mm, um) across all dialogs and status bar. | Standardize formatting via coordinate/unit utility. |
| **[#733](https://github.com/freerouting/freerouting/issues/733)** | i18n, Docs | Low / Future | Clean up redundant localization keys and isolate English source bundles. | Run i18n extraction & bundle cleanup script. |
| **[#726](https://github.com/freerouting/freerouting/issues/726)** | GUI | Low / Future | Display estimated remaining routing time (ETA) in status bar. | Linked to PR #743 (deferred). |
| **[#718](https://github.com/freerouting/freerouting/issues/718)** | routing-engine, DRC | High / Future | Support net-ties and overlapping pads between different nets without clearance violations. | Clearance matrix and connectivity graph enhancement. |
| **[#716](https://github.com/freerouting/freerouting/issues/716)** | routing-engine | Medium / Future | Autoroute: Add automatic trace length tuning (serpentine / accordion patterns) for high-speed differential pairs. | Algorithmic routing extension. |
| **[#695](https://github.com/freerouting/freerouting/issues/695)** | GUI | Low / Future | Add Recent Files menu and router settings preset management. | GUI convenience feature. |
| **[#677](https://github.com/freerouting/freerouting/issues/677)** | GUI, Good First Issue | Low / Future | Remove redundant Mode indicator label from BoardFrame footer. | Simple UI cleanup task. |
| **[#383](https://github.com/freerouting/freerouting/issues/383)** | routing-engine | Medium / Future | Autoroute: Support star-ground routing topology to a single reference point. | Algorithmic routing topology expansion. |

---

## 2. Deep-Dive Technical Investigations & Release Decisions

### 2.1. KiCad Plugin SES Import Handling ([Issue #908](https://github.com/freerouting/freerouting/issues/908) & [PR #909](https://github.com/freerouting/freerouting/pull/909))
- **Problem:** When a user opens Freerouting from KiCad via the plugin, cancels the autoroute confirmation dialog, and closes the Freerouting GUI without performing any routing, Freerouting exits cleanly without writing a `.ses` file. The KiCad plugin unconditionally attempted to run `pcbnew.ImportSpecctraSES(...)`, triggering error dialogs:
  - *"Failed to invoke pcbnew.ImportSpecctraSES"*
  - *"Specctra SES file does not exist"*
- **Fix in PR #909:** Author `@joern-h` added an existence check before triggering the KiCad IPC import in `plugin.py`:
  ```python
  if self.module_output.is_file():
      logger.info("Importing Specctra SES file into KiCad (DSN mode)...")
      if not router.import_ses():
          logger.error("Failed to import Specctra SES file.")
  else:
      logger.warning("Specctra SES file does not exist.")
  ```
- **Evaluation:** Clean, safe, and zero-risk. It eliminates annoying modal errors for users who simply open and close Freerouting.
- **Status:** **Merged into master** (`a6e14c4b4`). Issue #908 is resolved and closed.

---

### 2.2. Native Intel macOS Packaging ([Issue #905](https://github.com/freerouting/freerouting/issues/905))
- **Context:** Modern Apple Silicon Macs are supported by `build-macos-arm64` on `macos-latest`, but Intel Mac users (`x86_64`) were left without native pre-packaged `.dmg` installers after GitHub Actions retired `macos-13`.
- **Solution:** GitHub Actions announced the **`macos-15-intel`** runner image (supported through August 2027) for x86_64 workloads.
- **Implementation Plan:**
  1. Add `scripts/build/create-distribution-macos-x64.sh` invoking JDK `jlink` + `jpackage` with architecture flags for `x86_64`.
  2. Add `build-macos-x64` in `.github/workflows/create-release.yml` targeting `runs-on: [ macos-15-intel ]`.
  3. Publish `freerouting-<version>-macos-x64.dmg` alongside `macos-arm64.dmg` in GitHub Releases.
- **Status:** **Implemented and closed.** Configured in `.github/workflows/create-release.yml`.

---

### 2.3. GUI Startup Thread Confinement ([PR #819](https://github.com/freerouting/freerouting/pull/819))
- **Problem:** Freerouting's main entry point historically called `GuiManager.initializeGUI` from the initial application thread rather than Swing's Event Dispatch Thread (EDT). When opening a board directly on launch, `ScreenMessages` mutations threw `IllegalStateException: ScreenMessages must only be mutated on the EDT`, freezing the startup process.
- **Fix:** Wraps GUI initialization synchronously via refined `invokeOnEdt`, guaranteeing thread confinement and adding regression tests for both on-EDT and off-EDT entry points, interrupt handling, and exception safety.
- **Status:** **Merged into master** (`8ce0f757e`). Closed PR #819.

---

### 2.4. Scheduler Parallel Jobs Configuration ([PR #843](https://github.com/freerouting/freerouting/pull/843) / [PR #919](https://github.com/freerouting/freerouting/pull/919))
- **Problem:** `RoutingJobScheduler` hardcodes concurrent routing jobs to `5`. High-spec self-hosted API servers cannot scale out job throughput without custom source patches.
- **Fix:** Exposes `--api_server.max_parallel_jobs` (with dynamic `cores-1` default) participating in the normal `SettingsMerger` priority ladder (CLI, environment variables `FREEROUTING__API_SERVER__MAX_PARALLEL_JOBS`, JSON file).
- **Status:** **Superseded by PR #919 & merged into master** (`db16d292d`). Closed PR #843.

---

### 2.5. Headless Fixes & Heuristic Audit ([PR #793](https://github.com/freerouting/freerouting/pull/793) & [PR #920](https://github.com/freerouting/freerouting/pull/920))
- **Audit Findings:** PR #793 by `@gbacskai` contains 27 commits. A comprehensive audit of the 6 identified bug fixes against current `master` was performed:
  1. **Destructive Stub Removal (`a21d7759`):** The experimental stub pass `minimize_stubs` was never present in `master`; `master` does not suffer from destructive stub deletion.
  2. **`IntPoint` Hash Contract (`0bc6276d`):** Already implemented in `master` via commit `845d8298b` with `PointEqualsHashCodeTest`.
  3. **`PriorityQueue` Non-Deterministic Iteration (`0bc6276d`):** `master` uses `TreeSet` (deterministic ordering), not `PriorityQueue`.
  4. **Stream File Descriptor Leak (`d6de64c9`):** Active bug found in `master` in `RoutingJobScheduler.java`! Two unclosed `Files.list(userFolderPath)` streams in `saveJobToDisk` leaked file descriptors under concurrent server/headless runs. **Fixed in `master` via [PR #920](https://github.com/freerouting/freerouting/pull/920) using try-with-resources and verified with regression tests in `RoutingJobSchedulerTest`.**
  5. **Impossible Outer Layer Condition (`0bc6276d`):** Inapplicable to `master`; belonged to the experimental Manhattan heuristic (`calculateFastHeuristic`) which was never adopted.
  6. **DSN Unit Resolution Scaling (`0f121a2e`):** Inapplicable to `master`; internal parameter within PR 793's experimental power-trunk pass.
  - **`(type protect)` Wiring Constraints (`f6ec06578`):** Already protected in `master` via `Trace.isRoutable()` and `Via.isRoutable()` (`!isUserFixed()`).
- **Status:** **Merged into master via [PR #920](https://github.com/freerouting/freerouting/pull/920).** Closed [PR #793](https://github.com/freerouting/freerouting/pull/793) as superseded.

---

### 2.6. Linux AppImage Support ([PR #891](https://github.com/freerouting/freerouting/pull/891))
- **Assessment & Enhancements:** Introduces `quick-sharun` AppImage generation. AppImages provide a self-contained executable with bundled JRE and runtime libraries, resolving Linux distribution fragmentation.
- **Repository Structure & Script Organization:**
  - Placed distribution scripts consistently in `scripts/build/` alongside other platform packages: `scripts/build/create-distribution-appimage.sh` and `scripts/build/create-distribution-SNAPSHOT-appimage.sh`.
  - Self-contained container setup and metadata are organized under `scripts/build/appimage/` (`install-dependencies.sh`, `freerouting.desktop`, and `app.freerouting.Freerouting.metainfo.xml`), keeping the repository root clean.
- **Capitalization & Desktop Entry Refinement:**
  - Corrected `StartupWMClass=app-freerouting-FreeRouting` to `StartupWMClass=app-freerouting-Freerouting` in `freerouting.desktop` to match Freerouting's main class (`app.freerouting.Freerouting`) and X11 window manager hints, strictly enforcing project product spelling.
  - Added Freedesktop AppStream metainfo specification (`scripts/build/appimage/app.freerouting.Freerouting.metainfo.xml`) for software centers and catalog tools (`appstreamcli`, AM, Portable Linux Apps).
- **Naming & Delta Updates (`.zsync`):**
  - **Artifact Naming:** Set `OUTNAME="freerouting-$VERSION-$ARCH.AppImage"`, producing standard `freerouting-<version>-x86_64.AppImage` (dropping redundant `linux`, using standard GNU `x86_64`, lowercase product prefix, and `.AppImage` extension).
  - **`UPINFO` Fix:** Fixed malformed GitHub release URL expansion in `UPINFO` so `zsync` correctly parses GitHub release assets matching `freerouting-*-x86_64.AppImage.zsync` (and `freerouting-SNAPSHOT-*-x86_64.AppImage.zsync` for SNAPSHOT builds).
- **Script Quality & CI Linters:**
  - Resolved ShellCheck warnings (SC2034 unused `ARCH`, SC2086 unquoted variables).
  - Dynamic `SCRIPT_DIR` resolution allows running build scripts from any working directory.
- **Release Asset Architecture Naming Evaluation:** Kept native `x64` / `arm64` conventions for other release packages (`.zip`, `.msi`, `.dmg`) to prevent breaking downstream third-party automation, package managers, and KiCad plugin auto-downloaders.
- **Status:** **Merged into master** (`96563db32`).

---

### 2.7. Dependency Updates & OpenRewrite Cleanup
- **Status:** OpenRewrite was completely removed from `master` in commits `54da21183`, `e348262aa`, and PR #912 (`chore/remove-openrewrite`).
- **PR #900 Resolution:** PR #900 (Dependabot 23-dependency bump) was automatically closed on September 17, 2026. Dependabot will regenerate a clean update PR reflecting the current build configuration without OpenRewrite artifacts.

---

### 2.8. Docker Build Performance & Release Asset Standardization
- **Docker Workflow Slowness Investigation:**
  - `docker-nightly.yml` and `docker-release.yml` jobs took 12–19 minutes to complete.
  - **Root Cause Analysis:**
    1. **Redundant Tests (2m 37s):** Native runner ran `./gradlew test`, duplicating the full test suite that already ran during CI push checks.
    2. **QEMU Emulation Penalty (8m–14m):** Multi-stage Docker build executed `./gradlew executableJar -x test` inside an ARM64 container under QEMU emulation. Compiling Java bytecode under emulation is 5–10x slower than native compilation. Because Java bytecode in a fat JAR is platform-independent, compiling it twice across architectures is completely unnecessary.
    3. **Missing Buildx Caching:** GitHub Actions Docker layer caching (`type=gha`) was not configured.
  - **Optimization Recommendations:**
    - Build the executable fat JAR once on the native runner via `./gradlew executableJar -x test` (~40s).
    - Multi-arch Docker build only needs `FROM eclipse-temurin:25-jre-jammy` and `COPY` the native JAR. This slashes the multi-arch Docker push duration from ~8–14 minutes to **under 60 seconds**.
- **Release Asset Synchronization & Ordering:**
  - Standardized `.github/workflows/create-snapshot.yml` to generate a single workflow-level timestamp (`SNAPSHOT-YYYYMMDD_HHMM00`) passed to all 6 build jobs (producing 7 distribution assets).
  - All distribution builders output workflow artifacts; a dedicated `publish-snapshot` job uploads them sequentially in the canonical order: `.jar`, `windows-x64.msi`, `linux-x64.zip`, `macos-arm64.dmg`, `macos-x64.dmg`, `x86_64.AppImage`, `x86_64.AppImage.zsync`.
- **Workflow & Agent Hardening:**
  - Hardened Docker invocation in `create-release.yml` by passing `RELEASE_TAG` as an environment variable (`-e RELEASE_TAG=...`) and tightened version tag validation regex across all jobs.
  - Enforced a minimum 15-second polling interval for GitHub status queries and added a strict rule: **Never merge PRs automatically without explicit user confirmation**.

---

## 3. Active Backlog Priority Matrix

### Tier 1: Release-Critical for v2.5.0
*(All Tier 1 items completed and merged for v2.5.0)*

### Completed for v2.5.0
- **[PR #909](https://github.com/freerouting/freerouting/pull/909) (Fixes #908):** Merged into master (`a6e14c4b4`).
- **[PR #891](https://github.com/freerouting/freerouting/pull/891):** Merged into master (`96563db32`).
- **[Issue #905](https://github.com/freerouting/freerouting/issues/905):** Implemented in release pipeline via `macos-15-intel`.
- **[PR #819](https://github.com/freerouting/freerouting/pull/819):** Merged into master (`8ce0f757e`).
- **[PR #843](https://github.com/freerouting/freerouting/pull/843) / [PR #919](https://github.com/freerouting/freerouting/pull/919):** Superseded by PR #919 & merged into master (`db16d292d`).
- **[PR #793](https://github.com/freerouting/freerouting/pull/793) / [PR #920](https://github.com/freerouting/freerouting/pull/920) (Audit & Stream Leak Fix):** Fixed stream leak in master via PR #920. Closed PR #793 as superseded.

### Tier 2: Post-v2.5 Release & Modernization
- **[PR #888](https://github.com/freerouting/freerouting/pull/888) & [PR #870](https://github.com/freerouting/freerouting/pull/870):** Inspect mode and inline manual rules panel polish.
- **[PR #809](https://github.com/freerouting/freerouting/pull/809):** Net-class trace width GUI editing table rebase.
- **[#787](https://github.com/freerouting/freerouting/issues/787):** KiCad Plugin Protocol Buffers IPC migration.
- **[#879](https://github.com/freerouting/freerouting/issues/879) & [#718](https://github.com/freerouting/freerouting/issues/718):** Architectural engine enhancements (custom padstacks, net-ties).

---

## 4. Workflow Roadmap

```mermaid
flowchart TD
    subgraph Done ["Completed in v2.5"]
        A["Merged PR #909 - Fixes #908 SES check"]
        D["Implemented #905 - macOS x86_64 on macos-15-intel"]
        E["Merged PR #891 - Linux AppImage Support"]
        B["Merged PR #819 - GUI EDT confinement"]
        C["Merged PR #919 (superseding #843) - Configurable parallel jobs"]
        F["Merged PR #920 (superseding #793) - Fixed Directory Stream Leak"]
    end
```
