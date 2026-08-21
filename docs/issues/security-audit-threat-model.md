# Security Audit Threat Model

**Status:** Not started  
**Audit plan:** [security-audit-plan.md](security-audit-plan.md)  
**Input inventory:** [security-audit-inventory.md](security-audit-inventory.md)  
**Required model:** Grok 4.6 Extra High

This document is intentionally a Phase 0 stub. Complete it before starting domain hunt passes.

## Assets and trust boundaries

| Asset | Trust boundary | Attacker | Impact if broken |
| --- | --- | --- | --- |
| REST `/v1/*` jobs and sessions | network ↔ API | remote client (key or none) | job theft/overwrite, resource DoS, data leak |
| MCP `/v1/mcp`, SSE, WS, agent card | network ↔ MCP ↔ REST | remote agent / misconfigured target URL | confused deputy, SSRF, tool abuse |
| Specctra parser + job files | file/upload ↔ engine | malicious `.dsn` / payload | DoS, path traversal, unexpected code paths |
| Analytics / Sheets / GCP | app ↔ cloud | leaked key or SSRF | credential theft, PII in logs/BigQuery |
| Defaults (bind, auth on/off) | operator ↔ config | “local plugin” vs cloud | accidental world exposure |
| Installers / Docker / CI | build ↔ users | supply-chain attacker | malicious artifact, secret leak |

## Required completion

- [ ] Identify principals and capabilities for each boundary.
- [ ] Identify security assumptions and fail-open/fail-closed behavior.
- [ ] Rank threats by impact and exposure.
- [ ] Link each threat to Pass A–H and concrete verification tests.

