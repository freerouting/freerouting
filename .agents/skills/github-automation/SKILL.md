---
name: github-automation
description: >
  Best practices and rules for GitHub CLI operations (gh pr, gh issue) in Freerouting.
  Covers markdown description integrity via --body-file, label taxonomy, and PR lifecycle
  management. Triggers on: github, gh, pr, pull request, issue, gh pr create, gh pr edit.
metadata:
  project: freerouting
  version: "1.0"
user-invocable: false
---

# GitHub Automation & CLI Best Practices

This skill outlines mandatory procedures and best practices when interacting with GitHub via the `gh` CLI in Freerouting.

## 1. Markdown Body Integrity (Critical)

### Rule: Always use `--body-file <path>`
When creating or editing pull requests or issues (`gh pr create`, `gh pr edit`, `gh issue create`, `gh issue edit`), **always write the markdown description to a dedicated file and pass it with `--body-file`**:

```bash
# Correct Pattern:
gh pr create --title "..." --body-file "path/to/pr_body.md" --base master --head <branch>
gh pr edit <pr-number> --body-file "path/to/pr_body.md"
```

### Anti-Pattern: Never pass inline text via `--body "..."`
**Never** pass inline markdown text through command-line arguments (e.g. `--body "..."`) or string interpolation in PowerShell, Bash, or Python scripts.

**Why this fails:**
- Shell parsers and language escape evaluators interpret backslashes inside markdown backticks, code identifiers, and file paths:
  - `\this` &rarr; `\t` (tab character)
  - `\fixtures` &rarr; `\f` (form feed character, rendering as `\^L` or `\^ixtures`)
  - `\routing` &rarr; `\r` (carriage return, dropping characters)
  - `\BatchOptimizer` &rarr; `\b` (backspace character)
- This produces mangled, unreadable descriptions on GitHub containing missing letters, stray slashes, and broken links.
- Passing through `--body-file` completely bypasses shell quoting and escape evaluation, preserving backticks, paths, and formatting with 100% fidelity.

## 2. PR Lifecycle & Push Discipline

- **PR Direct Commit Rule (Maintainer Edit Access):** When starting to work on an existing Pull Request, check whether `maintainerCanModify` is `true` (via `gh pr view <number> --json maintainerCanModify`). If `true` and the author's fork is not archived/read-only, check out the PR branch (via `gh pr checkout <number>`), use direct push access, and commit changes directly on the PR's branch rather than creating a separate branch or PR. If the fork repository has been archived or deleted, work on a dedicated branch in `origin` instead.
- **PR Push Rule:** If a Pull Request is already open and new commits are made locally, **do not push the commits to the remote branch without explicit confirmation from the user** (pushing triggers remote CI workflows on GitHub Actions).
- **GitHub Actions Polling Rate Rule:** When polling GitHub Actions status checks (e.g. `gh pr checks`, `gh run view`), wait at least 15 seconds between queries to avoid spamming the GitHub API and exhausting rate limits.
- **PR Merge Rule:** **Never merge PRs automatically without explicit user confirmation.** Always present the PR link and check status to the user and wait for their confirmation to merge.
- **Quality Gates:** Before creating a PR or requesting review, ensure the local verification passes:
  ```powershell
  ./gradlew spotlessCheck checkstyleMain checkstyleTest
  pre-commit run --all-files
  ```
- **Reviewer Feedback Loop:** After creating a PR, wait until all automated reviewers (e.g. GitHub Code Quality bot, GitHub Copilot) have completed their reviews before addressing comments or pushing changes (`gh pr view <pr-number> --json comments,reviews`). Do not push fixes prematurely when only the first reviewer (such as the code quality bot) has responded; wait for GitHub Copilot and all other active reviewer bots to finish. Read all remarks, evaluate them objectively, address all valid issues together in local commits, verify quality gates, and only then push the fixes to the PR branch.

## 3. Standard Label Taxonomy

When creating PRs or issues, apply relevant labels:
- `routing-engine`: Core routing pipeline, algorithms, optimizer, DRC.
- `bug` / `enhancement`: Type classification.
- `Java`: Core codebase and engine logic.
- `GUI`: Swing interface, rendering, user interaction.
- `KiCad`: KiCad DSN/SES format and integration concerns.
