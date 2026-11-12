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
  2. **Base Branch Up-to-Date & Conflict Resolution:** Always check if the branch needs to be updated with the base branch (e.g. `origin/master`). Fetch the latest base branch (`git fetch origin <base-branch>`), check if the branch is behind (`git log HEAD..origin/<base-branch> --oneline` or `gh pr view <number> --json mergeStateStatus` (checking if `mergeStateStatus` is `BEHIND`)), and if so, merge or rebase the base branch into the current branch (`git merge origin/<base-branch>`), cleanly resolve any conflicts, and verify that the build compiles.
  3. **GitHub Copilot Review & Feedback Loop:**
     - Always trigger Copilot Lite review at least once before considering a PR ready to merge (e.g. `gh pr edit <number> --add-reviewer "@copilot"` or posting a comment `@copilot review`).
     - **Wait for Copilot to finish:** Poll at ≥15-second intervals until the Copilot review appears in `reviews`. Do not mark the PR ready or push fixes prematurely while Copilot is still analyzing.
     - Fetch all inline review threads and comments (`gh api repos/freerouting/freerouting/pulls/<number>/comments`), evaluate every remark objectively, fix all valid issues in local commits, reply to each thread explaining the action taken, and formally resolve all review threads via the GitHub GraphQL API (`resolveReviewThread`). All comments on a PR must be resolved before marking that PR ready to merge.
  4. **Approve and Run CI Checks & Monitor Progress:**
     - If workflow runs on the PR require approval (e.g., first-time contributors or external fork runs awaiting maintainer authorization), approve and trigger the checks immediately (`gh run list --commit <head_sha>`, `gh run approve <run-id>`, or `gh pr checks`).
     - Always check if CI checks/actions are running and monitor their progress while waiting (`gh pr checks <number> --watch` or polling `gh pr view <number> --json statusCheckRollup`). Do not proceed to merge readiness while CI checks are pending, failing, or unapproved.
  5. **Issue Validity on Current Codebase:** Verify whether the issue or feature the PR addresses is still valid on the current codebase. It is possible that the bug was already fixed or the area refactored elsewhere in the codebase since the PR was created.
  6. **Unit Test Demonstration & Full Coverage:** For functional or executable code changes, make sure that the PR has unit tests that demonstrate the issue correctly. Add more unit tests if needed to ensure:
     - (a) we have the right unit test demonstrating the exact issue that the PR fixes (failing before the fix, passing after), and
     - (b) we have full test coverage over the modified logic and edge cases.
     (For documentation, asset, or non-executable changes, verify that the relevant linters and quality checks pass instead.)
  7. **Critical Code Review & Refinement:** Always be critical of the PR's code. Review and refine it in every way possible: adhere strictly to Clean Code principles, naming conventions, and architectural boundaries (e.g. `ModuleBoundariesArchTest`), eliminate dead code or suboptimal patterns, optimize performance where appropriate, and ensure quality gates pass (`./gradlew spotlessCheck checkstyleMain checkstyleTest -q` and `pre-commit run --all-files`).
  8. **Security & Privacy Audit:** Conduct a strict security and privacy review of all changes:
     - **Secrets & Credentials:** Ensure no API keys, tokens, credentials, or private internal hostnames/URLs are introduced in code, tests, or documentation.
     - **Privacy & Telemetry:** Ensure no unsanctioned tracking, telemetry, phone-home beacons, or unauthorized external network requests are added. Maintain strict user privacy.
     - **Input Validation & Boundaries:** Guard against path traversal (e.g., unchecked relative file paths or `../`), command injection, unsafe reflection, or insecure deserialization in file parsers (DSN/SES/rules), CLI flags, and API endpoints.
     - **Dependency Safety:** Verify that any new or bumped dependencies are trusted, actively maintained, and free of known vulnerabilities.
  9. **Pre-Merge Summary & Milestone Verification:**
     - Ensure all review comment threads on the PR are formally resolved (`isResolved: true`). Never mark a PR ready to merge if unresolved comments remain.
     - Verify that Copilot Lite was triggered at least once, its review has completed, and all valid remarks have been addressed and verified.
     - Check the PR milestone (`gh pr view <number> --json milestone`).
     - If the milestone is unset or set to `"Future"`, update it to the upcoming target release version (typically the highest version-numbered open milestone, e.g. `2.6`, `2.7`):
       ```powershell
       gh pr edit <number> --milestone "<next-release-milestone>"
       ```
     - Write a brief, structured summary of findings, refinements, security audit results, resolved comment threads, and test verification for the maintainer so they can review and decide on merging.
- **PR Merge Rule:** **Never merge PRs automatically without explicit user confirmation.** Before marking a PR ready to merge, ensure that: (1) Copilot Lite was triggered at least once, its remarks were analyzed, and all valid issues were fixed; (2) all PR comments and review threads are formally resolved; (3) CI workflow checks have been approved, executed, and passed (monitor progress while waiting); and (4) the Milestone is set to the correct upcoming release version. Present the PR link and check status to the user, and wait for their explicit confirmation before merging.
- **Quality Gates:** Before creating a PR or requesting review, ensure the local verification passes:
  ```powershell
  ./gradlew spotlessCheck checkstyleMain checkstyleTest -q
  pre-commit run --all-files
  ```
- **Reviewer Feedback Loop (Newly Created PRs):** After creating a PR, request/wait until all automated reviewers (GitHub Code Quality bot, GitHub Copilot) have completed their reviews before addressing comments or pushing changes (`gh pr view <pr-number> --json comments,reviews`). Read all remarks, evaluate them objectively, address all valid issues together in local commits, verify quality gates, and push the fixes to the PR branch. Reply to and formally resolve each conversation thread.

## 3. Standard Label Taxonomy

When creating PRs or issues, apply relevant labels:
- `routing-engine`: Core routing pipeline, algorithms, optimizer, DRC.
- `bug` / `enhancement`: Type classification.
- `Java`: Core codebase and engine logic.
- `GUI`: Swing interface, rendering, user interaction.
- `KiCad`: KiCad DSN/SES format and integration concerns.
