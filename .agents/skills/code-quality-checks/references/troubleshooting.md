# Troubleshooting Guide

Common issues and their resolutions for code quality automation in Freerouting.

---

## Issue: Non-English resources still being checked

- **Cause:** Missing language-specific exclusions in SKIP pattern
- **Fix:** Add `_xx.properties` patterns and helpset directory exclusions to both `pyproject.toml` and pre-commit hook configurations

---

## Issue: Binary files causing decode warnings

- **Cause:** codespell attempts to decode non-UTF-8 files
- **Fix:** Add file extensions to SKIP pattern in `pyproject.toml` (e.g., `*.TAB`, `OFFSETS`, `*.ulp`, `*.dsn`, `*.ses`, `*.frb`)

---

## Issue: GitHub Actions not showing file-level errors

- **Cause:** Missing logToCheckStyle step in workflow
- **Fix:** Add the logToCheckStyle action with proper configuration to `.github/workflows/pre-commit.yml`

---

## Issue: codespell not respecting pyproject.toml

- **Cause:** Using codespell version < 2.4.2
- **Fix:** Update to codespell v2.4.2+ or use `--toml pyproject.toml` argument in pre-commit hook

---

## Issue: Exclude patterns not working in pre-commit

- **Cause:** Using `--skip` arguments instead of `exclude` field
- **Fix:** Move all exclusions to the `exclude` field in hook configuration; do not pass file-filtering args to the tool itself

---

## Issue: Shellcheck configuration error

- **Cause:** Missing or invalid args in shellcheck hook
- **Fix:** Ensure shellcheck has valid exclusion patterns and disabled check codes in `.pre-commit-config.yaml`
