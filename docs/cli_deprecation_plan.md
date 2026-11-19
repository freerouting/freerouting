# CLI Shorthand & Legacy Parameter Deprecation Plan

## Goal

Converge Freerouting's CLI onto the standard long-form `--<section>.<property>=<value>` setter syntax by:

1. Marking legacy shorthand CLI parameters as **deprecated** with a clear `FRLogger.warn(...)` message stating that the parameter will be removed in a future release and naming the exact long-form setter (or replacement) to use instead.
2. Warning whenever an adjacent `<boardname>.rules` file is automatically discovered and loaded, indicating that automatic `.rules` loading will be removed in a future release.
3. Removing unreleased v2.6 hyphenated scoring-version aliases (`-scoring-version`, `--scoring-version`, `-router-scoring-version`, `--router-scoring-version`, `-optimizer-scoring-version`, `--optimizer-scoring-version`) in favor of `--router.scoring.version` and `--optimizer.scoring.version`.
4. Retiring internal developer/benchmark utility flags (`--compare-boards=`, `--calculate-benchmark-scores.input=`, `--calculate-benchmark-scores.output=`) from the production user CLI and moving their callers to cleaner mechanisms.
5. Enforcing a **PR review policy** that new PRs are **not allowed to introduce new shorthand CLI parameters** unless the maintainer explicitly allows it — always preferring long-form `--<section>.<property>=<value>` parameters.

---

## 1. Flag-by-Flag Action Plan

### A. Deprecate with Warning (Keep Working for Now)

| Flag | Long-Form Setter / Replacement | Warning Behavior |
| :--- | :--- | :--- |
| `-di <dir>` | `--gui.input_directory=<dir>` | Warn that `-di` will be removed; use `--gui.input_directory=<dir>` |
| `-dr <file>` | **None** (`-de <design.dsn>+<rules.rules>`) | Warn that `-dr` will be removed; pass `.rules` via `-de <design.dsn>+<rules.rules>` |
| *(auto-discovered `<boardname>.rules`)* | **None** (`-de <design.dsn>+<rules.rules>`) | Warn whenever an adjacent `.rules` file is automatically discovered and loaded, stating automatic loading will be removed in a future release |
| `-mt <n>` | `--router.max_threads=<n>` (or `--router.optimizer.enabled=false` for `<= 0`) | Standardize warning to state it will be removed and point to `--router.max_threads=<n>` / `--router.optimizer.enabled=false` |
| `-oit <val>` | `--router.optimizer.improvement_threshold=<val>` | Standardize warning to state it will be removed and point to `--router.optimizer.improvement_threshold` |
| `-inc <list>` | `--router.autorouter.ignore_net_classes=<list>` | Standardize warning to state it will be removed (avoid duplicate warnings across `GlobalSettings` and `CliSettings`) |
| `-is <mode>` | `--router.optimizer.item_selection_strategy=<sequential\|prioritized>` | Standardize warning to state it will be removed and point to `--router.optimizer.item_selection_strategy` |
| `-spl <n>` | `--router.autorouter.stagnation_pass_limit=<n>` | Add deprecation warning pointing to `--router.autorouter.stagnation_pass_limit` |
| `-ll <level>` | `--logging.console.level=<level>` | Add deprecation warning pointing to `--logging.console.level` |
| `-dl` | `--logging.file.enabled=false` | Add deprecation warning pointing to `--logging.file.enabled=false` |
| `-da` | `--usage_and_diagnostic_data.disable_analytics=true` | Add deprecation warning pointing to `--usage_and_diagnostic_data.disable_analytics=true` |
| `-dct <sec>` | `--gui.dialog_confirmation_timeout=<sec>` | Add deprecation warning pointing to `--gui.dialog_confirmation_timeout` |

### B. Remove Directly (Unreleased v2.6 Aliases)

These hyphenated aliases have not shipped in a stable release and have zero script/workflow callers:

- `-scoring-version` / `--scoring-version=<v>` → use `--router.scoring.version=<v>` and `--optimizer.scoring.version=<v>`
- `-router-scoring-version` / `--router-scoring-version=<v>` → use `--router.scoring.version=<v>`
- `-optimizer-scoring-version` / `--optimizer-scoring-version=<v>` → use `--optimizer.scoring.version=<v>`

### C. Deprecate & Migrate Internal Utility Modes Out of the User CLI

- `--compare-boards=<a,b>`: Used only as a native-binary smoke test in `.github/workflows/create-release.yml` and `.github/workflows/create-snapshot.yml`. Migrate both workflows to a real `-de fixtures/Issue508-DAC2020_bm01.dsn -do smoke.ses --router.autorouter.max_passes=1 --router.optimizer.enabled=false` (or `-drc`) smoke test, and deprecate `--compare-boards=` with a removal warning.
- `--calculate-benchmark-scores.input=<path>` and `--calculate-benchmark-scores.output=<path>`: Unused anywhere in the codebase (`scripts/benchmark/lib/BenchmarkScoreRecalculator.ps1` uses `--calculate-benchmark-scores --input=... --output=...`). Deprecate with a removal warning, and migrate `BenchmarkScoreRecalculator.ps1` to invoke `BenchmarkScoreCalculator` via `java -cp ...` or a Gradle task so benchmark tooling does not live in the user CLI.

---

## 2. Codebase Usage Audit

### A. Production Java Source (`src/main/java/`)
- `src/main/java/app/freerouting/settings/GlobalSettings.java` — Parses `-di`, `-dr`, `--compare-boards=`, `--calculate-benchmark-scores.*`, `-mt`, `-oit`, `-inc`, `-is`, `-spl`, `-ll`, `-dl`, `-da`, `-dct`.
- `src/main/java/app/freerouting/settings/sources/CliSettings.java` — Parses `-mt`, `-oit`, `-inc`, `-is`, `-spl`, and the `-scoring-version` / `--scoring-version` aliases.
- `src/main/java/app/freerouting/startup/LoggingBootstrap.java` — Reads `-ll` and `-dl` early at startup; must also recognize `--logging.console.level=` and `--logging.file.enabled=`.
- `src/main/java/app/freerouting/analytics/ProcessEnvironmentDetector.java` — Checks args for `-dct`; must also recognize `--gui.dialog_confirmation_timeout=`.
- `src/main/java/app/freerouting/core/RoutingJob.java` (and board input loader) — Auto-discovers adjacent `<boardname>.rules`; add deprecation warning when triggered.
- `src/main/java/app/freerouting/cli/BenchmarkAndCompareCommands.java` — Houses `--compare-boards=` and `--calculate-benchmark-scores`.

### B. Scripts & GitHub Workflows
- `scripts/pcbench/pcbench_runner.py` — Migrate `-dct` → `--gui.dialog_confirmation_timeout=0`.
- `.github/workflows/create-release.yml` & `.github/workflows/create-snapshot.yml` — Migrate `--compare-boards=` smoke test to `-de ... -do ...`.
- `scripts/benchmark/lib/BenchmarkScoreRecalculator.ps1` — Migrate `--calculate-benchmark-scores` to a direct `java -cp` / Gradle invocation.

### C. Documentation
- `docs/command_line_arguments.md` — Mark deprecated flags clearly, document long-form replacements, and remove `-scoring-version` aliases.
- `docs/scoring.md` & `docs/settings.md` — Replace `--scoring-version` / `--router-scoring-version` / `--optimizer-scoring-version` with `--router.scoring.version` and `--optimizer.scoring.version`.
- `README.md` — Replace `-inc` example with `--router.autorouter.ignore_net_classes=...`.

### D. PR Review Process & Agent Guidelines
- `AGENTS.md` — Add an explicit rule under CLI / PR review standards: **new PRs are not allowed to introduce new shorthand CLI parameters unless the maintainer explicitly allows it; always prefer long-form `--<section>.<property>=<value>` parameters.**
- `.agents/skills/github-automation/SKILL.md` & `.agents/skills/daily-ops/SKILL.md` — Add a PR review checklist item to reject unapproved new shorthand CLI flags during code review.

### E. Unit Tests (`src/test/java/`)
- `src/test/java/app/freerouting/settings/LegacyCliFlagsTest.java` — Extend to cover all deprecated flags and assert warning messages.
- `src/test/java/app/freerouting/settings/ScoringVersionSettingsTest.java` — Update to use `--router.scoring.version` and `--optimizer.scoring.version`.
- `src/test/java/app/freerouting/settings/GlobalSettingsTest.java`, `RouterMaxThreadsMergeTest.java`, `SettingsMergerTest.java`, `Issue885SettingsInitializationTest.java`, `StagnationLimitSettingsTest.java`, `ProcessEnvironmentDetectorTest.java` — Verify backward-compatibility warnings and long-form parity.

---

## 3. Implementation Steps

1. **Centralize Deprecation Warnings:** Add a consistent warning helper (`"The '<flag>' command-line flag is deprecated and will be removed in a future release; use '<long-form>' instead."`) and ensure each flag warns once per invocation (no duplicate warnings across `GlobalSettings`, `CliSettings`, and `LoggingBootstrap`).
2. **Wire Warnings & Long-Form Parity:**
   - Update `GlobalSettings.java`, `CliSettings.java`, `LoggingBootstrap.java` (add `--logging.console.level=` / `--logging.file.enabled=`), `ProcessEnvironmentDetector.java` (add `--gui.dialog_confirmation_timeout=`), and adjacent `.rules` auto-discovery.
   - Remove the unreleased `-scoring-version` / `--scoring-version` / `-router-scoring-version` / `-optimizer-scoring-version` aliases from `CliSettings.java`.
3. **Update Scripts, Workflows & Docs:**
   - Update `scripts/pcbench/pcbench_runner.py`, `.github/workflows/create-release.yml`, `.github/workflows/create-snapshot.yml`, `scripts/benchmark/lib/BenchmarkScoreRecalculator.ps1`, `README.md`, `docs/command_line_arguments.md`, `docs/scoring.md`, and `docs/settings.md`.
4. **Update PR Review Policy:**
   - Add the "no new shorthand CLI parameters without explicit maintainer approval" rule to `AGENTS.md`, `.agents/skills/github-automation/SKILL.md`, and `.agents/skills/daily-ops/SKILL.md`.
5. **Add & Update Unit Tests:**
   - Extend `LegacyCliFlagsTest.java` for all deprecated flags + `.rules` auto-discovery warning.
   - Update `ScoringVersionSettingsTest.java` and add `LoggingBootstrap` / `ProcessEnvironmentDetector` long-form tests.

---

## 4. Verification Checklist

- `./gradlew.bat test --tests "LegacyCliFlagsTest" --tests "GlobalSettingsTest" --tests "RouterMaxThreadsMergeTest" --tests "SettingsMergerTest" --tests "StagnationLimitSettingsTest" --tests "ScoringVersionSettingsTest" --tests "ProcessEnvironmentDetectorTest" --tests "Dac2020Bm01RoutingTest" -q`
- `./gradlew.bat spotlessCheck checkstyleMain checkstyleTest -q`
- `pre-commit run --all-files`
