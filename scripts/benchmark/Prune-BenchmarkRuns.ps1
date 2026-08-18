<#
.SYNOPSIS
    Prunes older, obsolete, or unwanted benchmark runs from benchmarks.json and regenerates reports.

.DESCRIPTION
    Allows filtering and removing benchmark runs by version label, age, or keeping only the latest run
    per version/fixture combination. Avoids manual JSON editing.

.PARAMETER RemoveVersions
    Array of version_label patterns to remove (e.g. "soc-gui-separation-and-accessibility", "refactor/*").

.PARAMETER OlderThanDays
    Removes runs older than the specified number of days.

.PARAMETER KeepLatestOnly
    Keeps only the most recent run for each (version_label, fixture) combination.

.PARAMETER ResultsDir
    Path to the directory containing benchmarks.json. Defaults to scripts/benchmark/results.

.PARAMETER RegenerateReports
    Automatically regenerates benchmarks.md, benchmarks.html, and benchmarks-chart-data.json after pruning.

.PARAMETER DryRun
    Previews which runs would be removed without writing any changes.
#>
param(
    [string[]]$RemoveVersions = @(),
    [int]$OlderThanDays = 0,
    [switch]$KeepLatestOnly,
    [string]$ResultsDir = "$PSScriptRoot\results",
    [switch]$RegenerateReports,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$jsonPath = Join-Path $ResultsDir "benchmarks.json"
if (-not (Test-Path $jsonPath)) {
    Write-Error "benchmarks.json not found at $jsonPath"
    exit 1
}

if ($RemoveVersions.Count -eq 1 -and $RemoveVersions[0] -match ",") {
    $RemoveVersions = @($RemoveVersions[0].Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

$pyPruneScript = @"
import json, sys, fnmatch
from datetime import datetime, timezone, timedelta
from pathlib import Path

json_path = Path(sys.argv[1])
remove_versions = [v.strip() for v in sys.argv[2].split(',') if v.strip()]
older_than_days = int(sys.argv[3])
keep_latest_only = sys.argv[4].lower() == 'true'
dry_run = sys.argv[5].lower() == 'true'

data = json.loads(json_path.read_text(encoding='utf-8'))
runs = data.get('runs', [])
original_count = len(runs)

now = datetime.now(timezone.utc)
cutoff_date = now - timedelta(days=older_than_days) if older_than_days > 0 else None

# Filter
filtered = []
removed = []

# Sort by run_at descending to facilitate keep_latest_only
runs.sort(key=lambda r: r.get('run_at', ''), reverse=True)
seen_keys = set()

for r in runs:
    ver = r.get('binary', {}).get('version_label', '')
    fixture = r.get('fixture', {}).get('relative_path', '')
    key = (ver, fixture)
    run_at_str = r.get('run_at', '')

    # 1. Version pattern match
    matched_ver = False
    for pat in remove_versions:
        if fnmatch.fnmatch(ver.lower(), pat.lower()):
            matched_ver = True
            break
    if matched_ver:
        removed.append((ver, fixture, 'matched RemoveVersions: ' + ver))
        continue

    # 2. Age cutoff
    if cutoff_date and run_at_str:
        try:
            # Handle ISO string
            cleaned_ts = run_at_str.replace('Z', '+00:00')
            dt = datetime.fromisoformat(cleaned_ts)
            if dt < cutoff_date:
                removed.append((ver, fixture, f'older than {older_than_days} days ({run_at_str})'))
                continue
        except Exception:
            pass

    # 3. Keep latest only
    if keep_latest_only:
        if key in seen_keys:
            removed.append((ver, fixture, 'duplicate older run'))
            continue
        seen_keys.add(key)

    filtered.append(r)

print(f'Total runs before: {original_count}')
print(f'Runs removed: {len(removed)}')
print(f'Total runs remaining: {len(filtered)}')

if removed:
    print('\nSample of removed runs:')
    for item in removed[:10]:
        print(f'  - [{item[0]}] {item[1]} -> {item[2]}')
    if len(removed) > 10:
        print(f'  ... and {len(removed) - 10} more.')

if not dry_run:
    # Save back in reverse order or preserved order
    filtered.sort(key=lambda r: r.get('run_at', ''))
    data['runs'] = filtered
    data['total_runs'] = len(filtered)
    data['generated_at'] = datetime.now(timezone.utc).isoformat()
    json_path.write_text(json.dumps(data, indent=2), encoding='utf-8')
    print(f'\nSaved updated {json_path}')
else:
    print('\nDryRun: No changes written.')
"@

$removeStr = ($RemoveVersions -join ",")
$isKeepLatest = if ($KeepLatestOnly) { "true" } else { "false" }
$isDryRun = if ($DryRun) { "true" } else { "false" }

python -c $pyPruneScript $jsonPath $removeStr $OlderThanDays $isKeepLatest $isDryRun

if (-not $DryRun -and $RegenerateReports) {
    Write-Host "`nRegenerating Markdown, HTML, and chart summaries from pruned benchmarks.json..." -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot "run-benchmarks.ps1") -ResultsDir $ResultsDir -ReportOnly
}
