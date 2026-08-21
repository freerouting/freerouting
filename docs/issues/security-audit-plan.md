# Complete Codebase Security Audit Plan

**Status:** Phase 0 complete; threat-model phase next
**Owner:** maintainers (agent-assisted)
**Scope:** full Freerouting tree except frozen `src_v19/` (reference only; do not refactor)
**Tracking folder:** `docs/issues/`
**Eval source:** [CursorBench 3.2](https://cursor.com/evals) (retrieved 2026-08-21)

This document is the execution plan for a complete security audit. Findings, pass reports, and the living risk register live as sibling files in `docs/issues/` (see §8). Temporary log extracts and PoC notes belong in git-ignored `logs/security-audit/`.

Do **not** use Cursor `/review-security` as the primary audit. That subagent reviews a **git diff** (branch or uncommitted changes), not the whole tree. Use it only in Phase 5 after hardening PRs exist.

---

## 1. Why these models

Allowed models for this audit (user constraint):

| Model (select in Cursor) | CursorBench 3.2 score | Avg cost / task | Tokens / task | Steps / task | Role in this audit |
| --- | ---: | ---: | ---: | ---: | --- |
| **Grok 4.6 Extra High** | 70.8% | $2.81 | 41,136 | 46 | Highest-stakes reasoning: threat model, authz/IDOR, MCP confused deputy, critical-finding confirmation, final register |
| **Grok 4.6 Medium** | 67.1% | $1.28 | 17,942 | 29 | Independent confirmation of medium-risk passes; cheaper than Extra High with still-strong scores |
| **GPT-5.6 Luna Max** | 61.1% | $0.39 | 87,973 | 61 | Primary hunter: long tool-use traces, wide file walks, first-pass deep dives |
| **GPT-5.6 Luna High** | 56.8% | $0.16 | 15,141 | 40 | Inventory, checklists, mechanical/config/CI scans, duplicate merge, tracking-doc updates |

CursorBench scores measure edit/bugfix/instruction-following tasks, not security skill. Treat them as **relative reasoning quality vs cost**. Security judgment still requires dual-model consensus on high-severity claims (§4).

**Do not use** Composer Auto, Luna Medium/Low, or a single chat for the whole tree. One chat that “audits everything” will skip control-flow and invent findings.

Cost figures are CursorBench averages. Real audit chats will often cost more (larger context). Use the table for **relative** budgeting: Extra High ≈ 7× Luna Max ≈ 18× Luna High.

---

## 2. Audit principles

1. **Partition by attack surface**, not by package alphabet. Routing geometry (`autoroute`, `geometry`) is low security ROI except for resource-exhaustion DoS.
2. **Hunt then confirm.** Luna Max (or Luna High) produces candidate findings; Grok Medium or Extra High confirms or rejects with a file:line exploit scenario.
3. **Keep only confirmed findings** in the risk register. Unconfirmed items stay in the pass report under “Candidates (unconfirmed)”.
4. **Deterministic scanners run in parallel** with AI (Phase 1). AI does not replace SCA, secret scanning, or CI permission review.
5. **No silent fixes during audit passes.** Pass agents report only. Remediation is Phase 4, on a dedicated branch, one theme per PR where practical.
6. **Do not write exploit PoCs that are weaponizable against third-party systems.** For confirmed issues, describe the scenario and write a regression **test** that asserts the secure behavior (missing auth → 401, path stay inside job dir, etc.).
7. **Respect quality gates.** Remediation PRs still run `gradlew.bat spotlessCheck checkstyleMain checkstyleTest checkstyleRewriteRecipes`. Do not run `spotlessApply` as a cleanup hammer.

---

## 3. Threat model (must exist before hunting)

**Model:** Grok 4.6 Extra High (Plan mode)

**Input inventory (cheap, run first):** GPT-5.6 Luna High produces `docs/issues/security-audit-inventory.md` listing:

- Network listeners (Jetty REST, MCP HTTP/SSE/WS), default bind, CORS, rate limits
- Auth filters and “auth disabled” short-circuits
- Untrusted inputs: DSN/SES/rules JSON, job file upload, OpenAPI-generated MCP tools
- Secrets: API keys, Google Sheets, GCP service-account, analytics
- Serialization: `ObjectInputStream` / `.frb` / board snapshots
- Supply chain: Gradle, Docker, GitHub Actions `permissions`

**Threat-model output:** `docs/issues/security-audit-threat-model.md`

Must include:

| Asset | Trust boundary | Attacker | Impact if broken |
| --- | --- | --- | --- |
| REST `/v1/*` jobs and sessions | network ↔ API | remote client (key or none) | job theft/overwrite, resource DoS, data leak |
| MCP `/v1/mcp`, SSE, WS, agent card | network ↔ MCP ↔ REST | remote agent / misconfigured target URL | confused deputy, SSRF, tool abuse |
| Specctra parser + job files | file/upload ↔ engine | malicious `.dsn` / payload | DoS, path traversal, unexpected code paths |
| Analytics / Sheets / GCP | app ↔ cloud | leaked key or SSRF | credential theft, PII in logs/BigQuery |
| Defaults (bind, auth on/off) | operator ↔ config | “local plugin” vs cloud | accidental world exposure |
| Installers / Docker / CI | build ↔ users | supply-chain | malicious artifact, secret leak |

**Out of scope for “complete” but still listed:** academic crypto review of routing; physical GUI clickjacking; social engineering of operators.

---

## 4. Dual-review protocol

Every finding that might be **High or Critical** must be reviewed by two different model families:

| First pass (hunter) | Confirmation | When |
| --- | --- | --- |
| GPT-5.6 Luna Max | Grok 4.6 Extra High | Auth, MCP, IDOR, SSRF, RCE, insecure defaults that expose the network |
| GPT-5.6 Luna Max | Grok 4.6 Medium | Parsers, file handling, analytics, deserialization, settings |
| GPT-5.6 Luna High | Grok 4.6 Medium | CI YAML, Docker, dependency/config hygiene — only if Luna High flags a real issue |
| GPT-5.6 Luna High | *(none)* | Pure inventory, spelling of file lists, copy-edit of tracking docs |

Keep a finding only if:

- confirmation agrees, **or**
- confirmation disagrees but a human reproduces it (test or traced control flow).

Disagreements go in the pass report as **Disputed**, not in the register as confirmed.

---

## 5. Execution phases

### Phase 0 — Tracking skeleton and inventory ✅

**Model:** GPT-5.6 Luna High

**Actions:**

1. Create the tracking files listed in §8 (stubs with Status: not started).
2. Produce `security-audit-inventory.md` (listeners, filters, controllers, secret touchpoints, `ObjectInputStream` sites, workflow files).
3. Note known prior work (e.g. Issue 650 API key filter bypass, auth default `true`, bind `127.0.0.1`) so hunters do not re-open fixed items without checking current code.

**Done when:** inventory exists and threat-model chat can start without discovering the tree from scratch.

---

### Phase 1 — Deterministic scanners (human or Luna High to drive commands)

**Model:** GPT-5.6 Luna High (orchestrate commands; do not interpret SCA as “no vulns”)

Run from repo root (Windows):

- Secret scan: gitleaks or trufflehog on working tree **and** history if available
- Dependency advisories: Gradle dependency report + OSV/Snyk/OWASP Dependency-Check as available
- Optional: SpotBugs/FindSecBugs or Semgrep Java ruleset
- Manual grep checklist (also in inventory):
  - `ObjectInputStream`, `XMLDecoder`, `ScriptEngine`, `Runtime.exec`, `ProcessBuilder`
  - `setAccessible`, `allowAllHostname`, `TrustAll`, disabled hostname verification
  - `0.0.0.0`, `authentication.enabled`, CORS `*`, `http_allowed`
  - path concatenation on job output / user filenames

Store command output under `logs/security-audit/scanners/` (git-ignored). Summarize confirmed scanner hits into `docs/issues/security-audit-scanner-summary.md` using **Grok 4.6 Medium** (filter false positives).

---

### Phase 2 — Domain hunt passes (parallel chats)

Start **one Cursor Agent chat per pass**. Paste the pass prompt from §7. Do not mix domains in one chat.

Use **cloud agents** only if the user explicitly wants them; default is local Agent chats with the model selected in the UI.

| ID | Domain | Primary hunter | Confirmer | Source roots (start here, follow calls) |
| --- | --- | --- | --- | --- |
| **A** | REST authn/authz, IDOR, insecure defaults | GPT-5.6 Luna Max | Grok 4.6 Extra High | `api/security`, `ApiKeyValidationFilter`, `BaseController`, `api/v1/*`, `settings/ApiServerSettings`, `ApiAuthenticationSettings` |
| **B** | MCP server, tool bridge, WS/SSE, agent card | GPT-5.6 Luna Max | Grok 4.6 Extra High | `api/mcp/*`, `McpServerSettings`, `docs/API/MCP.md` vs code |
| **C** | Untrusted design I/O and job files | GPT-5.6 Luna Max | Grok 4.6 Medium | `io/specctra`, `io/specctra/parser`, `JobInputResource`, job output paths, `management` file handling |
| **D** | Analytics, GCP, Google Sheets keys | GPT-5.6 Luna Max | Grok 4.6 Medium | `analytics/*`, `AnalyticsControllerV1`, `GoogleSheetsApiKeyProvider`, `BigQueryClient` |
| **E** | Settings, CLI, Docker, installers/jlink | GPT-5.6 Luna High (config volume) then Luna Max if E flags design issues | Grok 4.6 Medium | `settings/*`, `Freerouting.java`, Dockerfiles, installer scripts, `docs/self-hosting.md` |
| **F** | CI, GitHub Actions, supply chain | GPT-5.6 Luna High | Grok 4.6 Medium (only on flagged workflows) | `.github/workflows/*`, Gradle plugins, published artifacts |
| **G** | Java deserialization and `.frb` / snapshots | GPT-5.6 Luna Max | Grok 4.6 Extra High if any path is network-reachable; else Grok 4.6 Medium | `ObjectInputStream` call sites, `GuiBoardManager` load, `BoardSnapshotManager`, `WorkspaceSettings.readObject` |
| **H** | Resource DoS (parser bombs, unbounded jobs, rate-limit gaps) | GPT-5.6 Luna Max | Grok 4.6 Medium | job scheduler, rate limit filters, parser recursion, max body size |

Passes A and B are **serial with Extra High confirmation** (limited Extra High budget). C–F can run in parallel after Phase 0 inventory exists.

**Do not deep-audit** `autoroute`, `geometry`, `drc` for “vulnerabilities” except H (DoS) and accidental execution of untrusted data.

Each pass writes `docs/issues/security-audit-pass-<id>.md` (see §8.3).

---

### Phase 3 — Triage and risk register

**Model:** Grok 4.6 Extra High

**Inputs:** all pass reports + scanner summary + threat model.

**Actions:**

1. Merge duplicates; drop disputed/unreproduced items.
2. Assign CVSS-like severity (Critical / High / Medium / Low / Info) using **impact × exposure**:
   - Critical: unauthenticated RCE, auth bypass on jobs, SSRF to cloud metadata, world bind + auth off as default in a release artifact
   - High: authenticated IDOR across users, secret in logs/images, MCP tool bridge to unintended URL
   - Medium: DoS, missing rate limit on expensive route, verbose errors
   - Low: defense-in-depth, docs mismatch
3. Write `docs/issues/security-audit-risk-register.md`.
4. Map each confirmed item to a proposed GitHub issue or a subsection in the register (do not open GitHub issues until a human agrees).

**Cheap copy-edit / table formatting:** GPT-5.6 Luna High after Extra High content is stable.

---

### Phase 4 — Remediation

**Models:**

| Work | Model |
| --- | --- |
| Design of authz/MCP fixes | Grok 4.6 Extra High (Plan), then implement with GPT-5.6 Luna Max |
| Parser bounds, path checks, settings defaults | GPT-5.6 Luna Max; Grok 4.6 Medium reviews the diff in a second chat |
| CI `permissions`, Docker user, docs | GPT-5.6 Luna High |
| Regression tests for auth and IDOR | GPT-5.6 Luna Max (tests), Grok 4.6 Medium (test-gap review) |

Rules:

- One theme per PR when possible (auth, MCP, parser, CI).
- After each PR is ready locally: run `/review-security` (diff-scoped) **and** Grok 4.6 Medium on the same diff if the PR is High/Critical.
- Do not stage files automatically. Keep formatting-only changes separate.

---

### Phase 5 — Close-out

**Model:** Grok 4.6 Extra High for residual-risk statement; GPT-5.6 Luna High to mark checkboxes in this plan.

- Update this plan’s Status and the risk register.
- Residual risks (accepted): e.g. operators who set `0.0.0.0` and disable auth for plugins.
- Optional: schedule a yearly re-audit of A+B only (Extra High + Luna Max).

---

## 6. Cost and sequencing budget

Illustrative **CursorBench-equivalent** task counts (not invoices):

| Phase | Tasks (approx.) | Models | Est. relative cost |
| --- | --- | --- | --- |
| 0 Inventory | 1 | Luna High | $0.16 |
| 1 Scanners + summary | 1 + 1 | Luna High + Grok Medium | $1.44 |
| Threat model | 1 | Grok Extra High | $2.81 |
| Pass A hunt + confirm | 2 | Luna Max + Extra High | $3.20 |
| Pass B hunt + confirm | 2 | Luna Max + Extra High | $3.20 |
| Pass C–H hunt | 6 | Luna Max (4) + Luna High (2) | ~$1.88 |
| Pass C–H confirm | 5 | Grok Medium (G uses Extra High if network-reachable) | ~$6.40–$7.93 |
| Phase 3 register | 1 | Extra High | $2.81 |
| Phase 4 (per High fix, review) | 2–4 | Luna Max + Grok Medium | $1.67 each cycle |
| Phase 5 | 1 | Extra High or Luna High | $0.16–$2.81 |

**Hunt-heavy Extra High on every pass would roughly double cost** with little gain: Extra High is reserved for A, B, threat model, G-if-exposed, and final register.

If budget is tight: skip parallel H as a separate pass (fold DoS into C and A), and confirm E/F only when Luna High reports a concrete issue.

---

## 7. Pass prompt template

Paste into a **new Agent chat** after selecting the hunter model. Confirmation chats use the same template with “You are the confirmer. Do not hunt new domains. Accept, reject, or mark disputed each hunter finding.”

```text
You are performing pass <ID> of the Freerouting security audit.
Read docs/issues/security-audit-plan.md and docs/issues/security-audit-inventory.md.
Read docs/issues/security-audit-threat-model.md if it exists.

Scope (do not leave this scope): <roots from the table>

Product: Freerouting (PCB autorouter), Java 25, Gradle, Jetty+Jersey REST, separate MCP server.
Known prior fix: ApiKeyValidationFilter must skip validation when authentication is disabled;
defaults are authentication.enabled=true and bind 127.0.0.1 — verify current code, do not assume.

Produce docs/issues/security-audit-pass-<id>.md with:
- Scope and model used
- Method (which files, which control flows)
- Confirmed findings table: Severity | Location (file:line) | Finding | Exploit scenario | Fix sketch
- Candidates (unconfirmed)
- Explicitly out of scope items you noticed but did not pursue

Rules:
- Cite file:line. No finding without a location.
- Do not implement fixes in this pass.
- Do not write weaponized exploits or attack scripts.
- Do not audit src_v19/.
- Prefer authorization bugs, auth bypass, SSRF, path traversal, insecure defaults,
  secret leakage, and unbounded resource use over style nits.
```

Confirmation extra line:

```text
Hunter report: docs/issues/security-audit-pass-<id>.md
You are Grok 4.6 <Medium|Extra High>. For each finding: Confirmed / Rejected / Disputed,
with a one-paragraph control-flow reason. Append a "## Confirmation" section to the same file.
Do not add new High/Critical findings unless the control flow is already in the hunter scope;
if you must, mark them Candidates.
```

---

## 8. Tracking documents (`docs/issues/`)

| File | Created in | Purpose |
| --- | --- | --- |
| `security-audit-plan.md` | now | This plan |
| `security-audit-inventory.md` | Phase 0 | Attack-surface index |
| `security-audit-threat-model.md` | Phase 0 | Assets, attackers, boundaries |
| `security-audit-scanner-summary.md` | Phase 1 | SCA/secrets/grep, false-positive filter |
| `security-audit-pass-a.md` … `pass-h.md` | Phase 2 | Per-domain hunt + confirmation |
| `security-audit-risk-register.md` | Phase 3 | Living confirmed issues, severity, status, links to PRs |
| `security-audit-remediation-log.md` | Phase 4 | What shipped, residual risk |

Update the risk register whenever a finding is confirmed, rejected after fix, or accepted as residual.

Issue-spec style (match other `docs/issues/` files): GitHub link when an issue exists, status checkboxes, file:line, acceptance criteria.

Temporary artifacts (heap dumps, raw scanner XML, draft replies): `logs/security-audit/` only.

---

## 9. Finding record (risk register row)

```markdown
### AUDIT-NNN — short title
- **Severity:** Critical | High | Medium | Low | Info
- **Status:** Open | Confirmed | Fix in progress | Fixed | Accepted residual | Rejected
- **Pass:** A–H
- **Location:** `path:line`
- **Models:** hunter → confirmer
- **Scenario:** who can do what, with what access
- **Fix:** sketch or PR link
- **Tests:** regression test path or “needed”
```

---

## 10. Suggested calendar (one maintainer)

| Day | Work | Model |
| --- | --- | --- |
| 1 | Phase 0 inventory + Phase 1 scanners | Luna High; Grok Medium for scanner summary |
| 1–2 | Threat model | Grok Extra High |
| 2–3 | Pass A then B (hunt ∥ next day confirm) | Luna Max → Extra High |
| 3–4 | Passes C, D, G, H in parallel chats | Luna Max → Grok Medium |
| 4 | Passes E, F | Luna High → Grok Medium if flagged |
| 5 | Phase 3 register | Extra High |
| following | Phase 4 PRs | Luna Max + Grok Medium/Extra High on auth/MCP diffs |

---

## 11. Operator checklist (start next session)

1. Create Phase 0 stubs with **GPT-5.6 Luna High**.
2. Open a **Plan** chat as **Grok 4.6 Extra High**: *“Write `docs/issues/security-audit-threat-model.md` from the inventory. No code changes.”*
3. Open **Agent** chat as **GPT-5.6 Luna Max** with the Pass A prompt.
4. After Pass A hunter file exists, new chat **Grok 4.6 Extra High** confirmation.
5. Repeat for B (Extra High confirm), then C–H as in the table.
6. Extra High: risk register.
7. Only then implement fixes.

---

## 12. Success criteria

The audit is complete when:

- [ ] Threat model and inventory exist
- [ ] Passes A–H have hunter reports; A, B (and G if network-reachable) have Extra High confirmation
- [ ] Scanner summary exists
- [ ] Risk register lists every confirmed finding with severity and status
- [ ] Critical/High items have either a fix PR, a scheduled issue, or an explicit accepted residual
- [ ] This plan’s Status is updated to **Complete** with the date
