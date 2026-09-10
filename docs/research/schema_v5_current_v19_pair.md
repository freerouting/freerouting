# Schema v5 current vs v1.9 pair walkthrough

No v1.9 rows remain in `benchmarks.json` (stale `1.9.0` / `v1.9.0` cache
entries were removed so overnight PCBench can route those binaries again).
The table below is one schema-v5 **current** extract; the v1.9 column is empty
until those runs are recached.

New records use `schema_version: 5` and a top-level `bounds` object.
Join key: fixture path with a leading `PCBench/` prefix stripped.

Fixture: `4N35-TTL-Serial-Optoisolator_4N35-TTL-Serial-Optoisolator/unrouted.dsn`

## Flattened fields

| Field | Current | v1.9 |
|---|---:|---:|
| `schema_version` | 5 | None |
| `version_label` | scoring-revision | None |
| `max_connections` | 20 | None |
| `unrouted_connections` | 0 | None |
| `clearance_violations` | 0 | None |
| `trace_length_mm` | 219.91 | None |
| `via_count` | 0 | None |
| `bend_count` | 31 | None |
| `pin_count` | 38 | None |
| `min_trace_length_mm` | 231.14 | None |
| `min_via_count` | 0 | None |
| `min_bend_count` | 18 | None |
| `complexity_c` | 76 | None |
| `native_score` | 999.99 | None |
| `current_router_score` | 999.99 | None |
| `current_optimizer_score` | 999.99 | None |
| `exit_code` | None | None |
