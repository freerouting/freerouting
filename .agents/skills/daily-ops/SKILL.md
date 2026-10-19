---
name: daily-ops
description: >
  End-to-end daily maintainer orchestration for Freerouting: (1) Daily Briefing
  that summarizes open issues and PRs grouped by topic (Routing Engine, GUI,
  API/MCP, Specs/I/O, CI/Build) and recommends the day's action plan; (2) Parallel
  PR Reviews using isolated shared workspaces (git worktrees) with GitHub Copilot
  review verification, rebase/conflict resolution, targeted tests, and a unified
  merge dashboard; and (3) Post-Merge Nightly PCBench Regression Benchmarking
  (compiling executableJar, versioning the next -RCx.jar in scripts/benchmark/binaries/,
  and running PCBench Tiers A & B) whenever routing-engine changes land on master.
  Triggers on: daily briefing, morning summary, summarize open PRs, batch review,
  parallel PR review, nightly benchmark, PCBench regression, RC jar benchmark.
metadata:
  project: freerouting
  version: "1.0"
user-invocable: true
---

# Daily Maintainer Operations & Orchestration (`daily-ops`)

This skill automates the maintainer's three core daily workflows while keeping token consumption minimal and preserving strict human-in-the-loop merge control.

---

## Workflow 1: Daily Briefing & Action Plan

When the user asks for a daily summary, open PR/issue overview, or "what should we work on today?":

### Step 1: Lightweight Metadata Fetch (Do NOT read diffs yet)
Run a single fast query to retrieve open PRs and recent open issues:
```powershell
gh pr list --limit 50 --json number,title,author,labels,isDraft,mergeStateStatus,reviewDecision,statusCheckRollup,updatedAt
gh issue list --limit 30 --json number,title,labels,updatedAt
```

### Step 2: Group by Topic Domain
Categorize all open PRs and high-priority issues into topic buckets:
1. **Routing Engine & DRC** (`routing-engine`, `autoroute`, `board`, `drc`, `geometry`, `rules`)
2. **GUI & Interactive Editor** (`GUI`, `a11y`, `rendering`, `workspace`)
3. **File I/O & EDA Integrations** (`KiCad`, `specctra`, `dsn`, `ses`)
4. **API, MCP & Management** (`API`, `MCP`, `analytics`)
5. **CI, Build & Documentation** (`CI`, `dependencies`, `docs`)

### Step 3: Recommend the Daily Course of Action
Present a concise **Daily Briefing Table** showing:
- PR `#`, Title, Topic Group, CI Status, Merge State (`CLEAN`, `BEHIND`, `CONFLICTING`), and whether Copilot has reviewed.
- **Recommended Batch Order:**
  1. *Quick Wins:* Clean, non-conflicting CI/Docs/GUI PRs ready for immediate review & merge.
  2. *Topic Batches:* Related PRs (e.g., 2-3 GUI PRs or 2 Routing Engine PRs) that should be reviewed together in parallel worktrees.
  3. *High-Priority Blockers:* Critical bugs or conflicting PRs needing surgical attention.
- Ask the user which PR numbers or topic group they want to review first.

---

## Workflow 2: Parallel Worktree PR Reviews (with GitHub Copilot Loop)

When the user selects a batch of PRs to review (e.g. *"Review PRs #1001, #1002, #1004"* or *"Review all GUI PRs"*):

### Step 1: Spawn Isolated `Workspace: "share"` Subagents
- Use `invoke_subagent` (`TypeName: "self"`, `Model: "inherit"`, `Workspace: "share"`) to launch parallel review workers.
- **Concurrency Cap:** Launch at most **3 to 4 subagents concurrently** (the 12-core reference machine needs headroom for parallel Gradle test JVMs). If more than 4 PRs are requested, process them in waves of 3–4.

### Step 2: Per-PR Subagent Checklist (7-Step Protocol + Copilot Loop)
Each subagent executes the following inside its isolated `share` workspace without polluting the main chat context:
1. **Checkout & Access Check:**
   - Check `gh pr view <number> --json maintainerCanModify,headRefName,baseRefName,mergeStateStatus`.
   - Checkout the PR (`gh pr checkout <number>`).
2. **Update with Base Branch:**
   - Fetch `origin/master` (`git fetch origin master`).
   - If behind or conflicting, merge `origin/master` (`git merge origin/master`), cleanly resolve conflicts, and verify compilation (`./gradlew compileJava compileTestJava -q`).
3. **GitHub Copilot (Lite) Review Verification & Wait Loop:**
   - Inspect `gh pr view <number> --json reviews,reviewRequests,comments`.
   - If GitHub Copilot (`copilot-pull-request-reviewer[bot]`) has **not** reviewed the PR yet and is not currently requested:
     - Trigger Copilot review: `gh api --method POST repos/freerouting/freerouting/pulls/<number>/requested_reviewers -f "reviewers[]=copilot-pull-request-reviewer[bot]"` (or `gh pr edit <number> --add-reviewer "@copilot"`).
   - **Wait for Copilot:** Poll `gh pr view <number> --json reviews,reviewRequests` every 20 seconds (up to 5 minutes) until Copilot's review is posted.
   - Read all review comments (`gh api repos/freerouting/freerouting/pulls/<number>/comments`), evaluate every remark objectively, fix all valid issues, and reply to/resolve the review threads.
4. **Diff-First Code Review & Unit Test Verification:**
   - Inspect `gh pr diff <number>` and only the affected line ranges in source files.
   - Ensure unit tests exist and pass using **targeted, quiet execution**:
     ```powershell
     ./gradlew.bat test --tests "<AffectedTestClass>" -q
     ```
5. **Quality Gates:**
   - Run `./gradlew.bat spotlessCheck checkstyleMain checkstyleTest -q` and `pre-commit run --all-files`.
   - If any fixes, conflict resolutions, or Copilot refinements were made, commit and push directly to the PR branch.
6. **Return Compact Report:**
   - Return a concise 10-line summary to the parent orchestrator: PR `#`, Title, Files Changed, Copilot Findings Fixed, Test & Gate Status, and Merge Readiness (`READY` / `NEEDS_ATTENTION`).

### Step 3: Present Unified Merge Dashboard & Wait for Confirmation
- The main agent aggregates all subagent reports into a single **Merge Readiness Table**.
- **Never merge automatically.** Ask the user: *"Which of these ready PRs should I merge now?"*
- Once confirmed by the user, merge the selected PRs sequentially (`gh pr merge <number> --merge` or `--squash` per repo convention) and track whether any merged PR touched the **Routing Engine**.

---

## Workflow 3: Post-Merge Nightly PCBench Regression Benchmark

Whenever one or more PRs touching the **Routing Engine** (`src/main/java/app/freerouting/{autoroute,board,geometry,drc,core,rules}`) are merged into `master` (or when the user triggers the nightly/post-merge benchmark):

### Step 1: Sync `master` and Build the Executable JAR
```powershell
git checkout master
git pull origin master
./gradlew.bat executableJar -q
```
Verify that `build\libs\freerouting-current-executable.jar` was freshly generated.

### Step 2: Determine Next `-RCx.jar` Version & Copy to `scripts/benchmark/binaries/`
1. Inspect existing RC binaries in `c:\Work\freerouting\scripts\benchmark\binaries\`:
   ```powershell
   Get-ChildItem "scripts/benchmark/binaries/freerouting-*-RC*.jar" | Sort-Object Name
   ```
2. Determine the next sequential RC number (for example, if `freerouting-2.6.0-RC3.jar` is the latest, the new version label is `2.6.0-RC4` and filename is `freerouting-2.6.0-RC4.jar`).
3. Copy the newly built executable JAR to both the new `-RCx.jar` and `freerouting-current.jar`:
   ```powershell
   $nextLabel = "2.6.0-RC4" # Increment from highest existing RCx
   $destJar = "scripts/benchmark/binaries/freerouting-$nextLabel.jar"
   Copy-Item "build/libs/freerouting-current-executable.jar" $destJar -Force
   Copy-Item "build/libs/freerouting-current-executable.jar" "scripts/benchmark/binaries/freerouting-current.jar" -Force
   ```

### Step 3: Run PCBench Corpus Benchmark on Tiers A and B
Execute the PCBench benchmark for the new `-RCx.jar` across **Tiers A and B** (`-Tier "A,B"`), piping verbose output to a log file so the conversation context stays clean:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/pcbench/Run-PCBenchCorpusBenchmark.ps1 `
  -Tier "A,B" `
  -VersionLabel $nextLabel `
  -JarPath $destJar `
  -Workers 8 > "logs/pcbench-$nextLabel.log" 2>&1
```
*(Note: Run this as a background task or via a scheduled nightly job; do not poll in a tight loop.)*

### Step 4: Regenerate Reports & Check for Regressions
1. Regenerate the Markdown and HTML benchmark summaries:
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/benchmark/run-benchmarks.ps1 -ReportOnly > "logs/benchmark-report-$nextLabel.log" 2>&1
   ```
2. Compare the new `$nextLabel` results against the previous RC and `2.5.0` baseline:
   - Check **Clearance Violations** (critical priority: must be 0 new router-introduced violations).
   - Check **Unrouted Connections / Completion Rate**.
   - Check **Normalized Score** and **Execution Time / Peak Heap**.
3. Present a concise **PCBench Regression Summary Table** (`$nextLabel` vs previous `RC` vs `2.5.0`) to the maintainer.
