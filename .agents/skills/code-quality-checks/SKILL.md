---
name: code-quality-automation
description: >
  Best practices for configuring code quality automation in Freerouting.
  Covers pre-commit hooks, linter configuration (codespell, yamllint, shellcheck,
  Spotless), and GitHub Actions integration. Use when maintaining or debugging
  code quality checks, when adding new pre-commit hooks, or when CI quality gates
  fail unexpectedly. Triggers on: pre-commit, codespell, yamllint, GitHub Actions,
  quality gates, CI failures.
metadata:
  author: mdeweerd
  version: "1.0"
  project: freerouting
user-invocable: false
includes:
  - references/exclusion-patterns.md
  - references/troubleshooting.md
---

# Code Quality Automation Skill

## 1. Overview

This skill documents best practices for configuring and maintaining code quality automation in the Freerouting project. It covers pre-commit hooks, linter configuration patterns, and GitHub Actions integration for consistent code quality enforcement.

## 2. Core Principles

### 2.1 Centralize Tool Configuration

**Best Practice:** Concentrate configuration for each tool in its canonical configuration file rather than scattering settings across multiple locations.

- **codespell:** Use `pyproject.toml` (`[tool.codespell]`) as the single source of truth
- **yamllint:** Use `.yamllint.yaml` for all YAML linting rules
- **shellcheck:** Use `.shellcheckrc` or inline args in pre-commit config
- **Spotless:** Use `build.gradle` for Java formatting rules

**Benefits:**
- Single location to update settings
- Consistent behavior between CLI and pre-commit execution
- Easier maintenance and auditing
- Avoids configuration drift between environments

### 2.2 Pre-commit Hook Pattern

**Best Practice:** For file-filtering tools (codespell, yamllint, shellcheck), use the `exclude` field in pre-commit hook configuration rather than passing exclusion arguments to the underlying tool.

This approach is more efficient because:
- Pre-commit filters files before invoking the tool
- Reduces the workload on each hook
- Keeps hook definitions self-contained and readable
- Works consistently across all hook types

**Example Pattern:**
```yaml
- repo: https://github.com/owner/tool
  rev: vX.Y.Z
  hooks:
    - id: tool-id
      exclude: ^(excluded_dir/|excluded_files\.ext$|pattern_to_skip)
      # Tool-specific args go here (non-filtering only)
```

**NOT Recommended:**
```yaml
- repo: https://github.com/owner/tool
  rev: vX.Y.Z
  hooks:
    - id: tool-id
      args:
        - --skip=*.ext
        - --exclude=dir/
        - --ignore=pattern
```

## 3. Tool-Specific Configuration

### 3.1 Codespell (Spell Checking)

**Configuration File:** `pyproject.toml`

**Key Settings:**
- `SKIP`: File/directory exclusion patterns
- `ignore-words-list`: Project-specific technical terms and abbreviations
- `ignore-regex`: Regex patterns for false positives
- `builtin`: Dictionary sets to enable
- `check-hidden`: Check hidden files
- `quiet-level`: Verbosity control

**Freerouting-Specific Requirements:** See [references/exclusion-patterns.md](references/exclusion-patterns.md) for complete exclusion patterns, ignore words list, and regex patterns.

### 3.2 Yamllint (YAML Validation)

**Configuration File:** `.yamllint.yaml`

**Best Practices:**
- Define explicit rules for line length, indentation, and document structure
- Exclude generated files and test data from validation
- Use `extends: default` as a base and override as needed

**Freerouting-Specific Exclusions:**
- Exclude workflow files from some checks if they have specific formatting requirements
- Exclude fixture YAML files that may have intentional formatting

### 3.3 Shellcheck (Shell Script Analysis)

**Configuration:** Inline args in `.pre-commit-config.yaml`

**Freerouting-Specific:**
- Exclude shell scripts that use non-standard patterns
- Disable specific check codes that conflict with project conventions:
  ```yaml
  args: [-e, SC1090, -e, SC1091, -e, SC2148]
  ```
- Exclude integration-specific shell scripts

### 3.4 Spotless (Java Formatting)

**Configuration File:** `build.gradle`

**Integration:**
- Run via Gradle task: `./gradlew spotlessApply --no-daemon`
- Configured as a manual-stage hook in pre-commit
- Uses project-specific formatting rules for Java code

## 4. GitHub Actions Integration

### 4.1 Critical Requirement: logToCheckStyle

**Mandatory:** The `logToCheckStyle` action **must** be maintained in the pre-commit workflow to convert pre-commit errors into GitHub Actions annotations.

Without this step:
- Pre-commit failures appear as generic errors in the GitHub UI
- No file-level annotations or line numbers are shown
- Developers cannot easily navigate to problematic files

**Implementation:**
```yaml
- name: Convert Raw Log to Checkstyle format
  uses: mdeweerd/logToCheckStyle@v2025.1.1
  if: ${{ failure() }}
  with:
    in: ${{ env.RAW_LOG }}
```

**Prerequisites:**
- Set environment variable: `RAW_LOG: pre-commit.log`
- Pipe pre-commit output to log file: `pre-commit run ... | tee ${RAW_LOG}`

### 4.2 Workflow Structure

**Recommended pre-commit workflow pattern:**
1. Checkout code
2. Install pre-commit
3. Run pre-commit hooks with output captured to log file
4. On failure, run logToCheckStyle to create annotations
5. Upload log artifacts for debugging

## 5. Complete Configuration Examples

### 5.1 pyproject.toml (codespell)

```toml
[tool.codespell]
SKIP="doxygen,doc,.git,.cache,dist,build,gradlew,gradlew.bat,libs,codespell,Makefile,*.pdf,*.dsn,*.ses,*.frb,*.flex,*.bin,*.exe,*.dll,*.so,*.class,*.jar,*.brd,.DS_Store,*.iml,*.ipr,*.iws,.idea/,./fixtures,./build,./.gradle,.gitignore,./logs,./docs/reference,./src_v19,./examples,./results,./scripts/benchmark/results,./src/main/resources/app/freerouting/helpset/de,./src/main/resources/app/freerouting/helpset/es,./src/main/resources/app/freerouting/helpset/fr,./src_v19/main/resources/app/freerouting/helpset/de,./src_v19/main/resources/app/freerouting/helpset/es,./src_v19/main/resources/app/freerouting/helpset/fr,*_ar.properties,*_bn.properties,*_de.properties,*_es.properties,*_fr.properties,*_hi.properties,*_it.properties,*_ja.properties,*_ko.properties,*_pt.properties,*_ru.properties,*_zh.properties,*_zh_tw.properties,*.TAB,OFFSETS,.run,*.ulp"

check-hidden = true
quiet-level = 2
ignore-regex = '\[fnrstv]'
builtin = "clear,rare,informal,code,names"

ignore-words-list = "slave,master,ue,wan,nto,abd,te,fo,cas,ro,inout,snd,ser,weerd,deweerd,thead,ws,stdio,ois,highlight,curNet,onText,cripts"
```

### 5.2 .pre-commit-config.yaml (codespell hook)

```yaml
- repo: https://github.com/codespell-project/codespell
  rev: v2.4.2
  hooks:
    - id: codespell
      name: CodeSpell
      exclude: ^(fixtures/|\.git/|\.gitignore$|build/|\.gradle/|logs/|docs/reference/|src_v19/|\.DS_Store$|examples/|results/|scripts/benchmark/results/|^\.run/|.*\.ulp$|src/main/resources/app/freerouting/helpset/de/|src/main/resources/app/freerouting/helpset/es/|src/main/resources/app/freerouting/helpset/fr/|src_v19/main/resources/app/freerouting/helpset/de/|src_v19/main/resources/app/freerouting/helpset/es/|src_v19/main/resources/app/freerouting/helpset/fr/|.*\.dsn$|.*\.ses$|.*\.pdf$|.*\.flex$|.*\.TAB$|^OFFSETS$)
      additional_dependencies:
        - tomli
```

**Note:** No `args` are passed to the codespell hook - all configuration is in `pyproject.toml`.

### 5.3 .github/workflows/pre-commit.yml (critical section)

```yaml
- name: Run pre-commit hooks
  env:
    RAW_LOG: pre-commit.log
    CS_XML: pre-commit.xml
  run: |
    set -o pipefail
    pre-commit run --show-diff-on-failure --color=always --all-files | tee ${RAW_LOG}

- name: Convert Raw Log to Checkstyle format (launch action)
  uses: mdeweerd/logToCheckStyle@v2025.1.1
  if: ${{ failure() }}
  with:
    in: ${{ env.RAW_LOG }}
```

## 6. Troubleshooting Guide

See [references/troubleshooting.md](references/troubleshooting.md) for common issues and their resolutions.

## 7. Validation Checklist

Before committing code quality configuration changes:

- [ ] All codespell configuration is in `pyproject.toml` (not in `.codespellignore` or `args`)
- [ ] Pre-commit hooks use `exclude` field for file filtering
- [ ] Non-English resources are excluded from codespell
- [ ] English resources are **NOT** excluded
- [ ] Binary files are excluded from all analysis
- [ ] Generated directories (build, fixtures, logs, etc.) are excluded
- [ ] `.github/workflows/pre-commit.yml` contains logToCheckStyle step
- [ ] `RAW_LOG` environment variable is set in workflow
- [ ] Pre-commit output is piped to the log file
- [ ] All hooks can run successfully locally (`pre-commit run -a`)
- [ ] CI workflow passes with new configuration

## 8. Maintenance Notes

### 8.1 Adding a New Hook

1. Add hook to `.pre-commit-config.yaml` with proper exclusions
2. Add tool-specific configuration to appropriate file
3. Test locally with `pre-commit run <hook-id> --all-files`
4. Verify in CI workflow
5. Update this skill document with new tool requirements

### 8.2 Updating Exclusions

When adding new file types or directories:
1. Add to `pyproject.toml` SKIP pattern (codespell)
2. Add to `.pre-commit-config.yaml` exclude patterns for all hooks
3. Add to `.yamllint.yaml` exclude if applicable
4. Verify no regressions in existing checks

### 8.3 Handling False Positives

For codespell false positives:
1. If project-specific word: Add to `ignore-words-list` in `pyproject.toml`
2. If regex pattern: Add to `ignore-regex` in `pyproject.toml`
3. If file type: Add to SKIP pattern in `pyproject.toml`
4. **Do not** add to pre-commit args - keep configuration centralized
