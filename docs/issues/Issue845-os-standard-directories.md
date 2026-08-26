# Issue 845: Store Configuration, Data, Logs, and Cache in OS-Standard Directories

**GitHub Issue:** https://github.com/freerouting/freerouting/issues/845  
**Status:** Implemented ✅  
**Priority:** Medium (usability, multi-user reliability, persistent configuration)

---

## 1. Executive Summary

Freerouting previously stored user settings (`freerouting.json`), saved jobs (`data/`), and logs in the operating system's temporary directory (`java.io.tmpdir`, e.g., `%TEMP%\freerouting` or `/tmp/freerouting`).

This caused several issues:
1. **Configuration Loss (Ephemeral Storage):** Periodic OS disk cleanup or rebooting when `/tmp` is a `tmpfs` RAM disk wiped user preferences and custom router settings.
2. **Multi-User Permission Conflicts:** On shared Linux/macOS systems, if User A created `/tmp/freerouting`, User B encountered `AccessDeniedException`.
3. **Repeated JRE Downloads in Integrations:** The KiCad integration plugin cached downloaded JRE runtimes in `%TEMP%\freerouting\jre`, causing unnecessary re-downloads whenever temp files were cleaned.

---

## 2. Solution & Platform Path Mappings

Standard platform path resolution is implemented in `app.freerouting.settings.AppPaths` in Java, and in `integrations/KiCad/kicad-freerouting/plugins/config.py` and `integrations/KiCad/installjava/installjava.py` in Python:

| Platform | Configuration (`freerouting.json`) & Data (`data/`) | Logs (`freerouting.log`) | Cache (JREs, artifacts) |
|---|---|---|---|
| **Windows** | `%APPDATA%\freerouting` (e.g. `C:\Users\<User>\AppData\Roaming\freerouting`) | `%LOCALAPPDATA%\freerouting\logs` | `%LOCALAPPDATA%\freerouting\cache` |
| **macOS** | `~/Library/Application Support/freerouting` | `~/Library/Logs/freerouting` | `~/Library/Caches/freerouting` |
| **Linux / POSIX (XDG)** | `$XDG_CONFIG_HOME/freerouting` (`~/.config/freerouting`) | `$XDG_STATE_HOME/freerouting/logs` (`~/.local/state/freerouting/logs`) | `$XDG_CACHE_HOME/freerouting` (`~/.cache/freerouting`) |

---

## 3. Precedence Hierarchy

1. CLI argument `--user_data_path=<dir>`
2. Environment variable `FREEROUTING__USER_DATA_PATH`
3. Environment variable `FREEROUTING__LOGGING__FILE__LOCATION` (deprecated fallback)
4. OS-standard default directories (`AppPaths`)

---

## 4. Migration & Backward Compatibility

- **Automatic One-Time Migration:** When `GlobalSettings.load()` runs, if `freerouting.json` is not found at the destination user data directory, it checks for `<tmpdir>/freerouting/freerouting.json`. If found, it copies `freerouting.json` (and `data/` if present) to the new destination and performs a best-effort cleanup of the obsolete temporary folder.
- **Master Overrides:** Portable deployments and containerized environments (such as Docker `--user_data_path=/mnt/freerouting`) continue to work without modification.
