# Issue 652 — Linux ZIP file only contains the license (v2.2.2)

**GitHub Issue:** https://github.com/freerouting/freerouting/issues/652  
**Status:** ✅ Fixed in `scripts/build/create-distribution-linux-x64.sh` and `create-distribution-SNAPSHOT-linux-x64.sh`

## Problem

The `freerouting-2.2.2-linux-x64.zip` release asset was reported as nearly empty — containing only the `LICENSE` file instead of the expected ~87 MB app-image with a bundled JRE.

The macOS `freerouting-2.2.2-macos-arm64.dmg` was ~80 MB and worked correctly, which is the key diagnostic clue that narrows the root cause.

## Root Cause

### Bug 1 (primary): Wrong icon file format for Linux (`--icon .ico`)

Both Linux scripts used:
```bash
--icon ../../assets/icon/freerouting_icon_256x256_v3.ico
```

`jpackage` requires platform-specific icon formats:
- **Linux:** `.png`
- **Windows:** `.ico`
- **macOS:** `.icns`

Passing a `.ico` file to `jpackage` on Linux (especially Java 25 with stricter validation) causes `jpackage` to **fail with an error**. The macOS build was unaffected because it used the correct `.icns` format.

The correct PNG file already exists: `assets/icon/freerouting_icon_256x256_v3.png`.

**Fix:** Changed to `freerouting_icon_256x256_v3.png` in both Linux scripts.

### Bug 2: No `set -e` (fail-fast mode)

Without `set -e`, the `jpackage` failure was silently swallowed. The script continued past the failed `jpackage`, tried to `mv` a non-existent `freerouting/` directory, and ultimately produced an empty or minimal ZIP. This masked the real error in CI logs.

**Fix:** Added `set -e` at the top of both Linux scripts. Also added to macOS scripts for consistency.

## What Was NOT the Cause

**`--strip-native-commands`** was initially suspected because it removes the `java` binary from the runtime image. However, the v2.2.2 macOS DMG (~80 MB, confirmed working) also uses `--strip-native-commands`. This proves the flag is safe for jpackage's native binary launcher (which links directly against the JVM library rather than calling the `java` CLI). The flag was therefore **left in place** in all scripts.

## Fix Applied

Changes made to the Linux build scripts only:

| Script | Changes |
|--------|---------|
| `scripts/build/create-distribution-linux-x64.sh` | `set -e`, icon `.ico` → `.png` |
| `scripts/build/create-distribution-SNAPSHOT-linux-x64.sh` | `set -e`, icon `.ico` → `.png` |
| `scripts/build/create-distribution-macos-arm64.sh` | `set -e` (defensive, no functional change) |
| `scripts/build/create-distribution-SNAPSHOT-macos-arm64.sh` | `set -e` (defensive, no functional change) |
| `scripts/build/create-distribution-macos-x64.sh` | **Deleted** — unused (no CI job references it; only `arm64` is supported on GitHub-hosted macOS runners) |
| `scripts/build/create-distribution-SNAPSHOT-macos-x64.sh` | **Deleted** — unused (same reason) |
| `scripts/build/create-distribution-macos-x64-signed.sh` | **Deleted** — obsolete (hardcoded JDK 14 x64 download URL, predates Java 25 requirement) |

## Expected Impact on ZIP Size

The fix is minimal (icon format only). The resulting Linux ZIP should be approximately the same size as the v2.1.0 release (~87 MB), matching the macOS DMG (~80 MB).

## Acceptance Criteria

- ✅ The Linux ZIP contains a working app-image with a bundled JRE, launchable via `freerouting/bin/freerouting`
- ✅ The ZIP size is in the same order of magnitude as v2.1.0 (~80–100 MB)
- ✅ CI build fails loudly (due to `set -e`) if `jpackage` or any step errors out
- ✅ macOS DMG builds are unaffected (icon and `--strip-native-commands` unchanged)