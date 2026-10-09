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

- **PR Review & Processing Workflow:** When reviewing, refining, and preparing a Pull Request for merge, follow this strict step-by-step checklist:
  1. **Maintainer Edit Access & Checkout:** Check whether `maintainerCanModify` is `true` (via `gh pr view <number> --json maintainerCanModify`). If `true` and the author's fork is not archived/read-only, check out the PR branch (via `gh pr checkout <number>`), use direct push access, and commit changes directly on the PR's branch rather than creating a separate branch or PR. If the fork repository has been archived or deleted, work on a dedicated branch in `origin` instead.
  2. **Base Branch Up-to-Date & Conflict Resolution:** Always check if the branch needs to be updated with the base branch (e.g. `origin/master`). Fetch the latest base branch (`git fetch origin <base-branch>`), check if the branch is behind (`git log HEAD..origin/<base-branch> --oneline` or `gh pr view <number> --json mergeable`), and if so, merge or rebase the base branch into the current branch (`git merge origin/<base-branch>`), cleanly resolve any conflicts, and verify that the build compiles.
  3. **Review Comments & Feedback Check:** Check if there are already comments or reviews from reviewers and review bots (`gh pr view <number> --json comments,reviews`). Verify whether they have been addressed. Address any valid outstanding issues and ensure conversation threads are acknowledged and formally resolved.
  4. **Issue Validity on Current Codebase:** Verify whether the issue or feature the PR addresses is still valid on the current codebase. It is possible that the bug was already fixed or the area refactored elsewhere in the codebase since the PR was created.
  5. **Unit Test Demonstration & Full Coverage:** Make sure that the PR has unit tests that demonstrate the issue correctly. Add more unit tests if needed to ensure:
     - (a) we have the right unit test demonstrating the exact issue that the PR fixes (failing before the fix, passing after), and
     - (b) we have full test coverage over the modified logic and edge cases.
  6. **Critical Code Review & Refinement:** Always be critical of the PR's code. Review and refine it in every way possible: adhere strictly to Clean Code principles, naming conventions, and architectural boundaries (e.g. `ModuleBoundariesArchTest`), eliminate dead code or suboptimal patterns, optimize performance where appropriate, and ensure quality gates pass (`./gradlew spotlessCheck checkstyleMain checkstyleTest` and `pre-commit run --all-files`).
  7. **Pre-Merge Summary for Maintainer:** After completing all review steps, conflict resolution, refinements, and testing, write a brief, structured summary of the findings, refinements, and test results for the maintainer so they can review and decide if we can proceed with the merge.
- **PR Merge Rule:** **Never merge PRs automatically without explicit user confirmation.** Always present the PR link, summary of changes, and check status to the user and wait for their explicit confirmation to merge.
- **Quality Gates:** Before creating a PR or requesting review, ensure the local verification passes:
  ```powershell
  ./gradlew spotlessCheck checkstyleMain checkstyleTest
  pre-commit run --all-files
  ```
- **Reviewer Feedback Loop:** After creating a PR, wait until all automated reviewers (e.g. GitHub Code Quality bot, GitHub Copilot) have completed their reviews before addressing comments or pushing changes (`gh pr view <pr-number> --json comments,reviews`). Do not push fixes prematurely when only the first reviewer (such as the code quality bot) has responded; wait for GitHub Copilot and all other active reviewer bots to finish. Read all remarks, evaluate them objectively, address all valid issues together in local commits, verify quality gates, and push the fixes to the PR branch. For each conversation thread raised by reviewers, reply to the thread and formally resolve it according to the action taken (e.g., mark as addressed with a summary of the fix, won't fix with technical justification, or incorrect with clarifying context).

## 3. Standard Label Taxonomy

When creating PRs or issues, apply relevant labels:
- `routing-engine`: Core routing pipeline, algorithms, optimizer, DRC.
- `bug` / `enhancement`: Type classification.
- `Java`: Core codebase and engine logic.
- `GUI`: Swing interface, rendering, user interaction.
- `KiCad`: KiCad DSN/SES format and integration concerns.
