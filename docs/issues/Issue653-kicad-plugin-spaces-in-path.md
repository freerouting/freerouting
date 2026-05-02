# Issue 653 — KiCad plugin fails when project path contains spaces

## Problem

When a KiCad project is stored in a directory whose path contains spaces
(e.g. `C:\users\me\Project 1\`), the Freerouting KiCad plugin fails with:

```
java.lang.NullPointerException: Cannot read field "format" because "routingJob.Input" is null
```

The reporter's workaround was to rename the folder to remove the space
(`Project_1`).  The root cause is that `pcbnew.ExportSpecctraDSN` and/or
the Java subprocess invocation can mishandle paths with embedded spaces on
certain Windows configurations (e.g. KiCad's embedded Python on Windows 11).

## Sub-issues

- ✅ **Subprocess paths with spaces** — All intermediate routing files
  (`freerouting.dsn`, `temp-freerouting.dsn`, `freerouting.ses`,
  `freerouting.rules`) are now placed in a guaranteed space-free temporary
  directory (via `tempfile.mkdtemp`) whenever the project directory path
  contains spaces.  When there are no spaces the files remain in the project
  directory (unchanged behaviour).
- ✅ **Error-message display** — `ProcessThread.show_error()` previously
  joined command tokens with plain spaces, producing an unquoted command
  string in error dialogs that was hard to copy-and-run.  Now uses
  `subprocess.list2cmdline()` on Windows and `shlex.quote` on POSIX so the
  displayed command is properly quoted.

## Implementation

**File changed:** `integrations/KiCad/kicad-freerouting/plugins/plugin.py`

### Key changes

1. **`prepare()`** — Detects spaces in `self.dirpath`.  When found, creates
   `self.routing_dir = Path(tempfile.mkdtemp(prefix="freerouting_"))` and
   uses it for all four routing files.  Otherwise `self.routing_dir` equals
   `self.dirpath` (no behavioural change).

2. **`RunImport()`** — Calls `self._cleanup_routing_dir()` on both the
   success and failure paths so the temp directory is removed after the SES
   file has been imported back into KiCad.

3. **`_cleanup_routing_dir()`** (new helper) — Removes `self.routing_dir`
   with `shutil.rmtree(..., ignore_errors=True)` when it differs from
   `self.dirpath`.

4. **`RunSteps()`** — Calls `self._cleanup_routing_dir()` when `RunRouter()`
   returns `False` (user cancelled or Java error) so no orphaned temp
   directory is left on disk.

5. **`ProcessThread.show_error()`** — Replaced `" ".join(self.command)`
   with proper platform quoting (`subprocess.list2cmdline` / `shlex.quote`).

6. **`import shlex`** added to the top-level imports.

### Edge case

If the system temp directory itself contains spaces (e.g. a username with a
space), the mitigation may not fully help.  This is documented in the code
comment.  The OS-level fix (short 8.3 paths, or `GetShortPathName` via
`ctypes`) is left for a future improvement if this edge case is reported.

## Acceptance criteria

- Users with project paths containing spaces (e.g. `C:\My Projects\board\`)
  can run the Freerouting plugin without encountering the NPE.
- Users without spaces in their project path observe no change in behaviour.
- Error dialogs display properly quoted commands that can be copy-pasted.

## Related zip artifacts

The release zips (`kicad-freerouting.zip`, `kicad-freerouting-2.2.0.zip`)
must be regenerated from the updated `kicad-freerouting/` folder following
the procedure in `docs/developer.md` before the next release.

