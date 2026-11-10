#!/usr/bin/env python3
"""High-performance Python benchmark report generator.

Generates:
1. scripts/benchmark/results/benchmarks.md
2. scripts/benchmark/results/benchmarks-chart-data.json
3. Updates website/benchmarks.html

Replaces the legacy, slow PowerShell MarkdownExporter.ps1 and HtmlPatcher.ps1
with a single-pass, sub-second Python pipeline.
"""

from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime
import json
import os
from pathlib import Path
import re
import sys
from typing import Any

from benchmark_store import load_all_runs, replace_file_retrying


def get_system_info() -> dict[str, Any]:
    """Returns CPU name, physical cores, logical cores, and RAM GB."""
    cpu_name = "Unknown"
    physical_cores = 0
    logical_cores = os.cpu_count() or 1
    total_ram_gb = 0.0

    if sys.platform == "win32":
        try:
            import winreg

            key = winreg.OpenKey(
                winreg.HKEY_LOCAL_MACHINE,
                r"HARDWARE\DESCRIPTION\System\CentralProcessor\0",
            )
            cpu_name = str(winreg.QueryValueEx(key, "ProcessorNameString")[0]).strip()
        except Exception:
            pass

    try:
        import psutil

        p_cores = psutil.cpu_count(logical=False)
        if p_cores:
            physical_cores = p_cores
        total_ram_gb = round(psutil.virtual_memory().total / (1024**3), 1)
    except Exception:
        pass

    if physical_cores == 0:
        physical_cores = logical_cores

    return {
        "cpu_name": cpu_name,
        "cpu_physical_cores": physical_cores,
        "cpu_logical_cores": logical_cores,
        "total_ram_gb": total_ram_gb,
    }


def format_markdown_table(
    headers: list[str],
    alignments: list[str],
    rows: list[list[Any]],
) -> str:
    col_count = len(headers)
    widths = [len(h) for h in headers]

    for row in rows:
        for i in range(col_count):
            val_str = str(row[i]) if row[i] is not None else ""
            if len(val_str) > widths[i]:
                widths[i] = len(val_str)

    ratio_cols = {"Fanout", "Clean (0 DRC)", "Fully-Routed", "Timeouts", "Failures"}
    for i in range(col_count):
        if headers[i] in ratio_cols and widths[i] < 18:
            widths[i] = 18

    lines: list[str] = []

    # Headers
    h_cells = [f" {headers[i].ljust(widths[i])} " for i in range(col_count)]
    lines.append("|" + "|".join(h_cells) + "|")

    # Separators
    s_cells = []
    for i in range(col_count):
        if alignments[i] == "R":
            s_cells.append(f" {'-' * (widths[i] - 1)}: ")
        else:
            s_cells.append(f" :{'-' * (widths[i] - 1)} ")
    lines.append("|" + "|".join(s_cells) + "|")

    # Rows
    for row in rows:
        r_cells = []
        for i in range(col_count):
            val_str = str(row[i]) if row[i] is not None else ""
            if alignments[i] == "R":
                r_cells.append(f" {val_str.rjust(widths[i])} ")
            else:
                r_cells.append(f" {val_str.ljust(widths[i])} ")
        lines.append("|" + "|".join(r_cells) + "|")

    return "\n".join(lines)


def is_active_fixture(run: dict[str, Any], fixtures_dir: Path) -> bool:
    fixture = run.get("fixture")
    if not fixture:
        return False

    filename = str(fixture.get("filename") or "")
    rel_path = str(fixture.get("relative_path") or "")

    if filename.endswith(".dsn_disabled") or rel_path.endswith(".dsn_disabled"):
        return False
    if filename.endswith("reference-routed.dsn") or rel_path.endswith("reference-routed.dsn"):
        return False

    if rel_path:
        full_fixture_path = fixtures_dir / rel_path.replace("/", os.sep)
        if not full_fixture_path.exists():
            return False

    return True


def get_normalized_score_entry(
    run: dict[str, Any],
    normalized_scores: dict[str, Any],
) -> dict[str, Any] | None:
    cache_key = run.get("cache_key")
    if cache_key and cache_key in normalized_scores:
        return normalized_scores[cache_key]
    return None


def get_run_score_value(
    run: dict[str, Any],
    normalized_scores: dict[str, Any],
) -> float | None:
    entry = get_normalized_score_entry(run, normalized_scores)
    if entry and entry.get("router_score") is not None:
        try:
            return float(entry["router_score"])
        except (ValueError, TypeError):
            pass
    quality = run.get("quality", {})
    if quality.get("quality_score") is not None:
        try:
            return float(quality["quality_score"])
        except (ValueError, TypeError):
            pass
    return None


def is_timed_out(run: dict[str, Any]) -> bool:
    exit_info = run.get("exit") or {}
    if exit_info.get("timed_out") is True:
        return True
    if exit_info.get("state") == "TIMED_OUT":
        return True
    log_analysis = run.get("log_analysis") or {}
    if log_analysis.get("timed_out") is True:
        return True
    return False


def is_failed(run: dict[str, Any], normalized_scores: dict[str, Any]) -> bool:
    entry = get_normalized_score_entry(run, normalized_scores)
    if entry and entry.get("is_failed") is True:
        return True
    exit_info = run.get("exit") or {}
    if exit_info.get("crashed") is True:
        return True
    code = exit_info.get("code")
    if code is not None and code != 0:
        return True
    if exit_info.get("state") == "FAILED":
        return True
    if get_run_score_value(run, normalized_scores) is None:
        return True
    return False


def format_ratio_pct(count: int, total: int) -> str:
    if total <= 0:
        return "N/A"
    pct = (count / total) * 100.0
    return f"{count:4d}/{total:4d} ({pct:5.1f}%)"


def format_ratio_pct_html(count: int, total: int) -> str:
    if total <= 0:
        return "N/A"
    pct = (count / total) * 100.0
    # PowerShell uses round/F1, but integer percentages drop decimal if 0 or 100: e.g. 0% or 100% or 72.8%
    # In HtmlPatcher.ps1:
    # $pStr = if ($pct -eq 0 -or $pct -eq 100) { "$([int]$pct)%" } else { "$($pct.ToString('F1', ...))%" }
    if pct == 0.0 or pct == 100.0:
        p_str = f"{int(pct)}%"
    else:
        p_str = f"{pct:.1f}%"
    return f"{count}/{total} ({p_str})"


def resolve_stored_path(path_str: str, base_benchmark_dir: Path) -> Path | None:
    if not path_str:
        return None
    p = Path(path_str)
    if p.is_absolute():
        return p
    norm = path_str.replace("/", "\\").lstrip("\\/")
    if norm.lower().startswith("scripts\\benchmark\\"):
        norm = norm[len("scripts\\benchmark\\") :]
    elif norm.lower().startswith("benchmark\\"):
        norm = norm[len("benchmark\\") :]
    full = base_benchmark_dir / norm
    return full if full.exists() else None


def inspect_log_for_errors(
    run: dict[str, Any],
    base_benchmark_dir: Path,
) -> tuple[bool, list[str], bool]:
    """Inspects log file tail for exceptions, timeouts, or load errors."""
    log_analysis = run.get("log_analysis") or {}
    load_error = log_analysis.get("load_error")
    exceptions = log_analysis.get("exceptions")
    log_timed_out = log_analysis.get("timed_out")

    if load_error is not None and exceptions is not None and log_timed_out is not None:
        exc_list = list(exceptions) if isinstance(exceptions, (list, tuple, set)) else []
        return bool(load_error), exc_list, bool(log_timed_out)

    log_file_str = run.get("log_file")
    if not log_file_str:
        return False, [], False

    resolved = resolve_stored_path(str(log_file_str), base_benchmark_dir)
    if not resolved or not resolved.exists():
        return False, [], False

    try:
        file_size = resolved.stat().st_size
        if file_size <= 2 * 1024 * 1024:
            tail_text = resolved.read_text(encoding="utf-8", errors="ignore")
        else:
            with open(resolved, "rb") as f:
                f.seek(max(0, file_size - 262144))
                tail_text = f.read().decode("utf-8", errors="ignore")

        tail_lines = tail_text.splitlines()[-500:]

        found_load_error = False
        found_timed_out = False
        found_exceptions: set[str] = set()

        exc_pattern = re.compile(r"\b([A-Z]\w*(?:Exception|Error))\b")
        load_pattern = re.compile(
            r"Failed to load board|Couldn't read the input file|Couldn't load the input file|Cannot load board"
        )
        to_pattern = re.compile(r"timed_out|timed out|with timeout:|TIMED_OUT")

        for line in tail_lines:
            if load_pattern.search(line):
                found_load_error = True
            if to_pattern.search(line):
                found_timed_out = True
            for m in exc_pattern.finditer(line):
                exc = m.group(1)
                if exc not in {"Exception", "Error", "StandardError", "ExecutionError"}:
                    found_exceptions.add(exc)

        return found_load_error, sorted(found_exceptions), found_timed_out
    except Exception:
        return False, [], False


def generate_reports(
    results_dir: Path,
    fixtures_dir: Path,
    website_html_path: Path | None,
    md_output_path: Path,
    chart_data_output_path: Path,
    skip_website_update: bool = False,
) -> None:
    t0 = datetime.now()
    benchmark_dir = results_dir.parent

    # 1. Load active runs
    all_runs_map = load_all_runs(results_dir)
    active_runs = [
        r for r in all_runs_map.values() if is_active_fixture(r, fixtures_dir)
    ]

    # 2. Load normalized scores
    normalized_scores_path = results_dir / "normalized-scores.json"
    normalized_scores: dict[str, Any] = {}
    if normalized_scores_path.exists():
        try:
            with open(normalized_scores_path, "r", encoding="utf-8") as f:
                normalized_scores = json.load(f)
        except Exception:
            pass

    # 3. Load catalog
    catalog_path = fixtures_dir / "PCBench" / "catalog.json"
    catalog_lookup: dict[str, str] = {}
    catalog_budget_lookup: dict[str, str] = {}
    if catalog_path.exists():
        try:
            with open(catalog_path, "r", encoding="utf-8") as f:
                cat = json.load(f)
                for b in cat.get("boards", []):
                    b_id = b.get("board_id")
                    if b_id:
                        if b.get("tier"):
                            catalog_lookup[b_id] = b["tier"]
                        if b.get("timeout_budget"):
                            catalog_budget_lookup[b_id] = b["timeout_budget"]
        except Exception:
            pass

    def get_tier(run: dict[str, Any]) -> str:
        fix = run.get("fixture", {})
        if fix.get("tier"):
            return str(fix["tier"])
        rel = str(fix.get("relative_path") or "")
        m = re.search(r"PCBench[/\\]([^/\\]+)", rel)
        if m:
            b_id = m.group(1)
            if b_id in catalog_lookup:
                return catalog_lookup[b_id]
        return "Other"

    # Group runs by fixture relative_path -> version -> list of runs
    fixture_version_runs: dict[str, dict[str, list[dict[str, Any]]]] = defaultdict(
        lambda: defaultdict(list)
    )
    fixture_metadata: dict[str, dict[str, Any]] = {}
    for r in active_runs:
        rel = r["fixture"]["relative_path"]
        ver = r["binary"]["version_label"]
        fixture_version_runs[rel][ver].append(r)
        if rel not in fixture_metadata:
            fixture_metadata[rel] = r["fixture"]

    # Deduplicate to pick latest run for each (fixture, version)
    # latest_runs_by_fixture: rel_path -> version -> latest_run
    latest_runs_by_fixture: dict[str, dict[str, dict[str, Any]]] = {}
    for rel, v_map in fixture_version_runs.items():
        latest_runs_by_fixture[rel] = {}
        for ver, runs in v_map.items():
            latest = max(runs, key=lambda x: str(x.get("run_at") or ""))
            latest_runs_by_fixture[rel][ver] = latest

    # Summary table builder
    def build_summary_table(
        title: str,
        target_runs: list[dict[str, Any]],
        description: str = "",
    ) -> tuple[str, str]:
        """Builds both markdown table and HTML table for a given set of runs."""
        if not target_runs:
            return "", ""

        # Group target runs by version and fixture
        rel_paths = sorted({r["fixture"]["relative_path"] for r in target_runs})
        versions = sorted({r["binary"]["version_label"] for r in target_runs})

        version_stats: dict[str, dict[str, Any]] = {}
        for ver in versions:
            fixture_count = 0
            failures = 0
            timeouts = 0
            perfects = 0
            all_routed = 0
            total_seconds = 0.0
            score_values: list[float] = []

            for rel in rel_paths:
                latest = latest_runs_by_fixture.get(rel, {}).get(ver)
                if not latest:
                    continue

                fixture_count += 1
                failed = is_failed(latest, normalized_scores)
                to = is_timed_out(latest)
                if to:
                    timeouts += 1
                if failed:
                    failures += 1

                quality = latest.get("quality", {})
                unrouted = quality.get("unrouted_connections")
                if unrouted is None:
                    unrouted = quality.get("final_unrouted")

                violations = quality.get("clearance_violations")
                score = get_run_score_value(latest, normalized_scores)

                unfixable = quality.get("unfixable_clearance_violations") or 0
                router_viol = quality.get("router_introduced_violations")

                if router_viol is not None:
                    is_clean = router_viol == 0
                else:
                    is_clean = (
                        violations is not None and violations <= unfixable
                    )

                if not failed and unrouted is not None and unrouted == 0:
                    all_routed += 1
                    if is_clean:
                        perfects += 1

                effective_score = score if (not failed and score is not None) else 0.0
                score_values.append(effective_score)

                wall_clock = quality.get("wall_clock_seconds")
                if wall_clock is not None:
                    total_seconds += float(wall_clock)

            avg_score = (
                sum(score_values) / len(score_values) if score_values else None
            )
            version_stats[ver] = {
                "version": ver,
                "fixture_count": fixture_count,
                "perfects": perfects,
                "all_routed": all_routed,
                "timeouts": timeouts,
                "failures": failures,
                "total_minutes": total_seconds / 60.0,
                "avg_score": avg_score,
            }

        valid_scores = [
            s["avg_score"] for s in version_stats.values() if s["avg_score"] is not None
        ]
        max_avg_score = max(valid_scores) if valid_scores else None

        # Format Markdown
        headers = [
            "Version",
            "Fixtures",
            "Clean (0 DRC)",
            "Fully-Routed",
            "Timeouts",
            "Failures",
            "Total Time",
            "Avg. Score",
        ]
        alignments = ["L", "R", "R", "R", "R", "R", "R", "R"]
        md_rows: list[list[Any]] = []
        html_rows: list[str] = []

        for ver in sorted(version_stats.keys()):
            stat = version_stats[ver]
            tot = stat["fixture_count"]
            perf_str = format_ratio_pct(stat["perfects"], tot)
            all_str = format_ratio_pct(stat["all_routed"], tot)
            to_str = format_ratio_pct(stat["timeouts"], tot)
            fail_str = format_ratio_pct(stat["failures"], tot)
            time_str = f"{stat['total_minutes']:.1f}"

            if stat["avg_score"] is not None:
                score_formatted = f"{stat['avg_score']:.1f}"
                if max_avg_score is not None and stat["avg_score"] == max_avg_score:
                    md_score_str = f"**{score_formatted}**"
                    html_score_str = f"<strong>{score_formatted}</strong>"
                else:
                    md_score_str = score_formatted
                    html_score_str = score_formatted
            else:
                md_score_str = "N/A"
                html_score_str = "N/A"

            md_rows.append(
                [
                    ver,
                    tot,
                    perf_str,
                    all_str,
                    to_str,
                    fail_str,
                    time_str,
                    md_score_str,
                ]
            )

            # HTML row
            h_perf = format_ratio_pct_html(stat["perfects"], tot)
            h_all = format_ratio_pct_html(stat["all_routed"], tot)
            h_to = format_ratio_pct_html(stat["timeouts"], tot)
            h_fail = format_ratio_pct_html(stat["failures"], tot)
            html_rows.append(
                "                <tr>\n"
                f"                  <td><strong>{ver}</strong></td>\n"
                f"                  <td>{tot}</td>\n"
                f"                  <td>{h_perf}</td>\n"
                f"                  <td>{h_all}</td>\n"
                f"                  <td>{h_to}</td>\n"
                f"                  <td>{h_fail}</td>\n"
                f"                  <td style='text-align: right;'>{time_str}</td>\n"
                f"                  <td>{html_score_str}</td>\n"
                "                </tr>"
            )

        # Build Markdown section
        md_table = format_markdown_table(headers, alignments, md_rows)
        md_text = f"### {title}\n"
        if description:
            md_text += f"{description}\n"
        md_text += f"\n{md_table}\n\n"

        # Build HTML section
        html_text = (
            "          <div class='summary-section' style='margin-bottom: 24px;'>\n"
            f"            <h3 class='fixture-title' style='margin-bottom: 4px;'>{title}</h3>\n"
        )
        if description:
            html_text += f"            <p class='fixture-meta' style='margin-bottom: 12px;'>{description}</p>\n"
        html_text += (
            "            <table class='benchmark-table'>\n"
            "              <thead>\n"
            "                <tr class='header-row'>\n"
            "                  <th>Version</th>\n"
            "                  <th>Fixtures</th>\n"
            "                  <th>Clean (0 DRC)</th>\n"
            "                  <th>Fully-Routed</th>\n"
            "                  <th>Timeouts</th>\n"
            "                  <th>Failures</th>\n"
            "                  <th style='text-align: right;'>Total Time</th>\n"
            "                  <th>Avg. Score</th>\n"
            "                </tr>\n"
            "              </thead>\n"
            "              <tbody>\n"
            + "\n".join(html_rows)
            + "\n"
            "              </tbody>\n"
            "            </table>\n"
            "          </div>\n"
        )

        return md_text, html_text

    # Timeout summary builder
    def build_timeout_summary(target_runs: list[dict[str, Any]]) -> str:
        versions = sorted({r["binary"]["version_label"] for r in target_runs})
        rel_paths = sorted({r["fixture"]["relative_path"] for r in target_runs})

        version_data: dict[str, dict[str, Any]] = {}
        total_timeouts_across_all = 0

        manifest_budget_cache: dict[str, str] = {}

        def get_budget(r: dict[str, Any]) -> str:
            if r.get("job_timeout"):
                return str(r["job_timeout"])
            snap = r.get("settings_snapshot") or {}
            if snap.get("job_timeout"):
                return str(snap["job_timeout"])
            rj_rel = r.get("result_json")
            if rj_rel:
                if rj_rel in manifest_budget_cache:
                    return manifest_budget_cache[rj_rel]
                full_rj = resolve_stored_path(str(rj_rel), benchmark_dir)
                if full_rj and full_rj.exists():
                    try:
                        content = full_rj.read_text(encoding="utf-8", errors="ignore")
                        m = re.search(r'job_timeout["\']?:\s*["\']?([^"\'`\s,]+)', content)
                        if m:
                            found = m.group(1)
                            manifest_budget_cache[rj_rel] = found
                            return found
                    except Exception:
                        pass
            rel = r.get("fixture", {}).get("relative_path", "")
            m_pcb = re.search(r"PCBench[/\\]([^/\\]+)", rel)
            if m_pcb:
                b_id = m_pcb.group(1)
                if b_id in catalog_budget_lookup:
                    return catalog_budget_lookup[b_id]
            return "00:30:00"

        for ver in versions:
            v_data = {
                "total": 0,
                "autorouter": 0,
                "optimizer": 0,
                "fanout": 0,
                "budgets": set(),
            }

            for rel in rel_paths:
                latest = latest_runs_by_fixture.get(rel, {}).get(ver)
                if not latest or not is_timed_out(latest):
                    continue

                v_data["total"] += 1
                total_timeouts_across_all += 1
                v_data["budgets"].add(get_budget(latest))

                phases = latest.get("phases") or {}
                opt = phases.get("optimizer") or {}
                opt_dur = opt.get("duration_seconds") or 0.0

                quality = latest.get("quality") or {}
                unrouted = quality.get("unrouted_connections")
                if unrouted is None:
                    unrouted = quality.get("final_unrouted", -1)

                viol = quality.get("router_introduced_violations")
                if viol is None:
                    viol = quality.get("clearance_violations", 0)

                exit_info = latest.get("exit") or {}
                exit_code = exit_info.get("code")

                if opt_dur > 0 or (exit_code == 0 and unrouted == 0 and viol <= 0):
                    v_data["optimizer"] += 1
                elif phases.get("fanout", {}).get("timed_out") is True:
                    v_data["fanout"] += 1
                else:
                    v_data["autorouter"] += 1

            if not v_data["budgets"]:
                for rel in rel_paths:
                    latest = latest_runs_by_fixture.get(rel, {}).get(ver)
                    if latest:
                        v_data["budgets"].add(get_budget(latest))
                        break

            version_data[ver] = v_data

        if total_timeouts_across_all == 0:
            return ""

        def format_budget_range(budgets: set[str]) -> str:
            if not budgets:
                return "-"
            minutes: list[int] = []
            for b in budgets:
                try:
                    parts = [int(p) for p in b.split(":")]
                    if len(parts) == 3:
                        minutes.append(parts[0] * 60 + parts[1])
                    elif len(parts) == 2:
                        minutes.append(parts[0])
                except Exception:
                    pass
            if not minutes:
                return "-"
            min_m = min(minutes)
            max_m = max(minutes)
            return f"{min_m}m" if min_m == max_m else f"{min_m}m-{max_m}m"

        headers = [
            "Version",
            "Total Timeouts",
            "Fanout",
            "Auto-router",
            "Optimizer",
            "Job Timeout Budget",
        ]
        alignments = ["L", "R", "R", "R", "R", "R"]
        rows = []
        for ver in versions:
            vd = version_data[ver]
            rows.append(
                [
                    ver,
                    vd["total"],
                    vd["fanout"],
                    vd["autorouter"],
                    vd["optimizer"],
                    format_budget_range(vd["budgets"]),
                ]
            )

        table_md = format_markdown_table(headers, alignments, rows)
        return (
            "### Timeout Summary\n"
            "Breakdown of timeouts by routing stage and configured job timeout budget across all benchmark fixtures.\n\n"
            f"{table_md}\n\n"
        )

    # 4. Generate summary sections
    tier_buckets = [
        (
            "A",
            "Tier A: Canary Gate",
            "Fast-solving 2-layer boards (0 unrouted, 0 clearance violations expected).",
        ),
        (
            "B",
            "Tier B: Routine Benchmarks",
            "Standard 2-4 layer boards evaluated for routine optimization progress.",
        ),
        (
            "C",
            "Tier C: Complex / Multi-Layer",
            "Dense and 6+ layer boards requiring deeper pathfinding.",
        ),
        (
            "D",
            "Tier D: Extreme Stress / Diagnostic",
            "High net-count and large surface-area stress boards.",
        ),
        (
            "Other",
            "General / Legacy Golden Fixtures",
            "In-repo regression and golden fixture benchmark suite.",
        ),
    ]

    all_md_summary, all_html_summary = build_summary_table(
        "Summary Table (All Tiers Combined)",
        active_runs,
        "Comprehensive performance across all benchmark fixtures.",
    )
    timeout_md_summary = build_timeout_summary(active_runs)

    tier_md_sections = []
    tier_html_sections = []
    for t_key, t_title, t_desc in tier_buckets:
        matching = [r for r in active_runs if get_tier(r) == t_key]
        if matching:
            m_md, m_html = build_summary_table(t_title, matching, t_desc)
            tier_md_sections.append(m_md)
            tier_html_sections.append(m_html)

    # 5. Build Markdown detail tables
    sys_info = get_system_info()
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    md_lines: list[str] = [
        "# Freerouting Nightly Benchmarks Report",
        f"Generated on: {ts}",
        f"System: {sys_info['cpu_name']} ({sys_info['cpu_physical_cores']} Cores, {sys_info['total_ram_gb']} GB RAM)",
        "",
        "This report lists the latest benchmark run results for each Freerouting version and fixture combination.",
        "",
        "## Summary",
        "",
        all_md_summary.strip(),
        "",
    ]
    if timeout_md_summary:
        md_lines.extend([timeout_md_summary.strip(), ""])
    for t_md in tier_md_sections:
        md_lines.extend([t_md.strip(), ""])

    # Detail tables grouped by folder, then fixture
    fixtures_by_folder: dict[str, list[str]] = defaultdict(list)
    for rel in sorted(latest_runs_by_fixture.keys()):
        grp = fixture_metadata[rel].get("group") or "fixtures"
        fixtures_by_folder[grp].append(rel)

    detail_headers = [
        "Version",
        "Mode",
        "Fanout",
        "Fanout (s)",
        "Router (s)",
        "Opt. (s)",
        "Total (s)",
        "Passes",
        "Unrouted",
        "Violations",
        "Score",
        "Heap (MB)",
        "Alloc (GB)",
        "Warn/Err",
        "Notes",
    ]
    detail_alignments = [
        "L",
        "L",
        "R",
        "R",
        "R",
        "R",
        "R",
        "R",
        "R",
        "R",
        "R",
        "R",
        "R",
        "R",
        "L",
    ]

    for folder in sorted(fixtures_by_folder.keys()):
        md_lines.append(f"## Group: [{folder}](../fixtures/{folder})")
        md_lines.append("")

        for rel in fixtures_by_folder[folder]:
            meta = fixture_metadata[rel]
            fname = meta.get("filename") or Path(rel).name
            md_lines.append(f"### Fixture: [{fname}](../fixtures/{rel})")
            md_lines.append("")

            size_bytes = meta.get("size_bytes") or 0
            size_kb = round(size_bytes / 1024.0, 1)
            layers = meta.get("layer_count", "")
            nets = meta.get("net_count", "")
            comps = meta.get("component_count", "")
            w = meta.get("board_width_mm", "")
            h = meta.get("board_height_mm", "")
            area = meta.get("board_area_cm2", "")
            cad = meta.get("host_cad", "")
            cad_ver = meta.get("host_version", "")

            md_lines.append(
                f"Size: {size_kb} kB \u00b7 Layers: {layers} \u00b7 Nets: {nets} \u00b7 Components: {comps} \u00b7 Dimensions: {w} x {h} mm ({area} cm\u00b2) \u00b7 CAD: {cad} (v{cad_ver})"
            )
            md_lines.append("")

            # Runs for this fixture
            runs_map = latest_runs_by_fixture[rel]
            d_rows = []
            for ver in sorted(runs_map.keys()):
                run = runs_map[ver]
                mode = run.get("run_mode") or "N/A"

                phases = run.get("phases") or {}
                fanout = phases.get("fanout") or {}
                autorouter = phases.get("autorouter") or {}
                optimizer = phases.get("optimizer") or {}

                # Fanout pin escape string
                fanout_val = "N/A"
                if fanout.get("log_found"):
                    esc = fanout.get("escaped_pin_count")
                    tot = fanout.get("smd_pin_count") or 0
                    pct = fanout.get("escape_rate_pct")
                    if tot > 0 and esc is not None:
                        pct_f = float(pct) if pct is not None else 0.0
                        fanout_val = f"{esc:4d}/{tot:4d} ({pct_f:5.1f}%)"

                # Durations
                f_dur = fanout.get("duration_seconds")
                r_dur = autorouter.get("duration_seconds")
                o_dur = optimizer.get("duration_seconds")

                f_time_str = f"{float(f_dur):.2f}" if f_dur is not None else "N/A"
                r_time_str = f"{float(r_dur):.2f}" if r_dur is not None else "N/A"
                o_time_str = f"{float(o_dur):.2f}" if o_dur is not None else "N/A"

                tot_time = 0.0
                has_time = False
                for d in (f_dur, r_dur, o_dur):
                    if d is not None:
                        tot_time += float(d)
                        has_time = True
                total_time_str = f"{tot_time:.2f}" if has_time else "N/A"

                # Passes
                f_pass = fanout.get("passes_completed") or 0
                r_pass = autorouter.get("passes_completed") or 0
                o_pass = optimizer.get("passes_completed") or 0
                passes_str = f"{f_pass:3d}+{r_pass:3d}+{o_pass:3d}"

                quality = run.get("quality") or {}
                unrouted = quality.get("unrouted_connections")
                if unrouted is None:
                    unrouted = quality.get("final_unrouted")
                unrouted_str = str(unrouted) if unrouted is not None else "N/A"

                viol = quality.get("clearance_violations")
                viol_str = str(viol) if viol is not None else "N/A"

                score = get_run_score_value(run, normalized_scores)
                score_str = f"{score:.0f}" if score is not None else "N/A"

                heap = quality.get("peak_heap_mb")
                heap_str = f"{round(float(heap)):.0f}" if heap is not None else "N/A"

                alloc = quality.get("total_allocated_gb")
                alloc_str = f"{float(alloc):.1f}" if alloc is not None else "N/A"

                log_analysis = run.get("log_analysis") or {}
                warns = log_analysis.get("warn_count") or 0
                errs = log_analysis.get("error_count") or 0
                warn_err_str = f"{warns} / {errs}"

                # Check if there is an error signal before doing disk I/O
                log_analysis = run.get("log_analysis") or {}
                load_error = log_analysis.get("load_error")
                exceptions = log_analysis.get("exceptions")
                log_to = log_analysis.get("timed_out")

                exit_info = run.get("exit") or {}
                has_error_signal = (
                    exit_info.get("crashed") is True
                    or (exit_info.get("code") not in (None, 0))
                    or exit_info.get("state") == "FAILED"
                    or is_timed_out(run)
                    or (log_analysis.get("error_count") or 0) > 0
                )

                if has_error_signal and (load_error is None or exceptions is None or log_to is None):
                    load_error, exceptions, log_to = inspect_log_for_errors(
                        run, benchmark_dir
                    )
                else:
                    load_error = bool(load_error) if load_error is not None else False
                    exceptions = list(exceptions) if isinstance(exceptions, (list, tuple, set)) else []
                    log_to = bool(log_to) if log_to is not None else False

                notes: list[str] = []
                if exit_info.get("crashed") is True or (
                    exit_info.get("code") not in (None, 0)
                    and exit_info.get("state") == "FAILED"
                ):
                    notes.append("FAILED")
                if is_timed_out(run) or log_to:
                    notes.append("TIMEOUT")
                if load_error:
                    notes.append("LOAD ERROR")
                for exc in exceptions:
                    if exc not in notes:
                        notes.append(exc)
                notes_str = ", ".join(notes)

                d_rows.append(
                    [
                        ver,
                        mode,
                        fanout_val,
                        f_time_str,
                        r_time_str,
                        o_time_str,
                        total_time_str,
                        passes_str,
                        unrouted_str,
                        viol_str,
                        score_str,
                        heap_str,
                        alloc_str,
                        warn_err_str,
                        notes_str,
                    ]
                )

            md_lines.append(format_markdown_table(detail_headers, detail_alignments, d_rows))
            md_lines.append("")

    # 6. Write Markdown report
    md_output_path.parent.mkdir(parents=True, exist_ok=True)
    tmp_md = md_output_path.with_name(f".{md_output_path.name}.{os.getpid()}.tmp")
    with open(tmp_md, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(md_lines).rstrip() + "\n")
    replace_file_retrying(tmp_md, md_output_path)

    # 7. Write Chart Data JSON
    chart_data = []
    for run in active_runs:
        score_val = get_run_score_value(run, normalized_scores)
        quality = run.get("quality") or {}
        unrouted = quality.get("unrouted_connections")
        if unrouted is None:
            unrouted = quality.get("final_unrouted")

        chart_data.append(
            {
                "fixture": run["fixture"]["filename"],
                "version": run["binary"]["version_label"],
                "date": run.get("run_at"),
                "score": float(score_val) if score_val is not None else 0.0,
                "unrouted": int(unrouted) if unrouted is not None else 0,
                "cpu_time": float(quality.get("cpu_seconds") or 0.0),
            }
        )

    chart_data_output_path.parent.mkdir(parents=True, exist_ok=True)
    tmp_chart = chart_data_output_path.with_name(f".{chart_data_output_path.name}.{os.getpid()}.tmp")
    with open(tmp_chart, "w", encoding="utf-8", newline="\n") as f:
        json.dump(chart_data, f, indent=2, ensure_ascii=False)
    replace_file_retrying(tmp_chart, chart_data_output_path)

    # 8. Update website HTML
    if website_html_path and not skip_website_update and website_html_path.exists():
        html_container = (
            "<div class='benchmark-container'>\n"
            f"  <div class='benchmark-report-info'>Generated on: {ts} &middot; System: {sys_info['cpu_name']} ({sys_info['cpu_physical_cores']} Cores, {sys_info['total_ram_gb']} GB RAM)</div>\n"
            + all_html_summary
            + "".join(tier_html_sections)
            + "</div>"
        )

        indented_html = "".join(
            f"        {line}\n" if line.strip() else "\n"
            for line in html_container.splitlines()
        )

        with open(website_html_path, "r", encoding="utf-8") as f:
            raw_html = f.read()

        # Normalize CRLF in raw_html to LF
        raw_html = raw_html.replace("\r\n", "\n")

        pattern = re.compile(
            r"<!-- BENCHMARK_TABLE_START -->.*?<!-- BENCHMARK_TABLE_END -->",
            re.DOTALL,
        )
        replacement = f"<!-- BENCHMARK_TABLE_START -->\n{indented_html}        <!-- BENCHMARK_TABLE_END -->"
        patched_html = pattern.sub(replacement, raw_html)

        tmp_html = website_html_path.with_name(f"{website_html_path.name}.tmp")
        with open(tmp_html, "w", encoding="utf-8", newline="\n") as f:
            f.write(patched_html)
        os.replace(tmp_html, website_html_path)

    t1 = datetime.now()
    duration_ms = (t1 - t0).total_seconds() * 1000
    print(f"Exported benchmark reports in {duration_ms:.1f} ms")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate Freerouting benchmark reports.")
    parser.add_argument(
        "--results-dir",
        type=Path,
        default=Path("scripts/benchmark/results"),
        help="Path to benchmark results directory",
    )
    parser.add_argument(
        "--fixtures-dir",
        type=Path,
        default=Path("scripts/benchmark/fixtures"),
        help="Path to fixtures directory",
    )
    parser.add_argument(
        "--website-html",
        type=Path,
        default=Path("website/benchmarks.html"),
        help="Path to website benchmarks.html file",
    )
    parser.add_argument(
        "--md-path",
        type=Path,
        default=Path("scripts/benchmark/results/benchmarks.md"),
        help="Output path for Markdown report",
    )
    parser.add_argument(
        "--chart-data-path",
        type=Path,
        default=Path("scripts/benchmark/results/benchmarks-chart-data.json"),
        help="Output path for chart data JSON",
    )
    parser.add_argument(
        "--skip-website-update",
        action="store_true",
        help="Skip updating website HTML",
    )

    args = parser.parse_args()
    generate_reports(
        results_dir=args.results_dir.resolve(),
        fixtures_dir=args.fixtures_dir.resolve(),
        website_html_path=args.website_html.resolve() if args.website_html else None,
        md_output_path=args.md_path.resolve(),
        chart_data_output_path=args.chart_data_path.resolve(),
        skip_website_update=args.skip_website_update,
    )


if __name__ == "__main__":
    main()
