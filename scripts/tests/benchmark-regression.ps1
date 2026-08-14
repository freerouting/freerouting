Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$benchmarkLib = Join-Path $repositoryRoot "scripts\benchmark\lib"
Get-ChildItem $benchmarkLib -Filter "*.ps1" | ForEach-Object {
    . $_.FullName
}

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
    if ($Expected -ne $Actual) {
        throw "$Message. Expected '$Expected', got '$Actual'."
    }
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "freerouting-benchmark-regression-$([guid]::NewGuid())"
$null = New-Item -ItemType Directory -Force -Path $tempRoot

try {
    $logPath = Join-Path $tempRoot "timeout.log"
    @(
        "Auto-routing stage started on board 'CM5' for 10 unrouted nets"
        "Auto-routing pass #1 on board 'CM5' completed in 2.50 seconds with score 900.00 (2 unrouted and 1 violations)"
        "Auto-routing pass #1 on board 'CM5' completed in 2.50 seconds with score 900.00 (2 unrouted and 1 violations)"
    ) | Set-Content $logPath

    $metrics = Get-PhaseMetrics $logPath "test" $true
    Assert-Equal 1 $metrics.autorouter.passes_completed "Duplicate pass lines must count once"
    Assert-Equal 2.5 $metrics.autorouter.duration_seconds "Checkpoint duration must not be doubled"
    Assert-Equal "last_checkpoint" $metrics.metric_source "Timed-out runs must retain checkpoint provenance"
    Assert-Equal 2 $metrics.autorouter.final_unrouted "Checkpoint unrouted count was not retained"
    Assert-Equal $true $metrics.timed_out "Process timeout must be preserved"

    $emptyLog = Join-Path $tempRoot "no-metrics.log"
    "routing process stopped before a checkpoint" | Set-Content $emptyLog
    $emptyMetrics = Get-PhaseMetrics $emptyLog "test" $true
    Assert-Equal "none" $emptyMetrics.metric_source "Missing metrics must be marked unavailable"
    Assert-Equal $null $emptyMetrics.autorouter.final_unrouted "Missing metrics must not become zero"

    $report = ConvertFrom-Json @'
{
  "violations": [
    { "type": "clearance" },
    { "type": "clearance" }
  ],
  "unconnectedItems": [
    { "type": "track_dangling" },
    { "type": "via_dangling" },
    { "type": "unconnectedItems" }
  ]
}
'@
    $drcMetrics = Get-DrcReportMetrics $report
    Assert-Equal 5 $drcMetrics.raw_violations "Raw DRC findings must include dangling findings"
    Assert-Equal 2 $drcMetrics.clearance_violations "Clearance findings were not separated"
    Assert-Equal 1 $drcMetrics.dangling_tracks "Dangling-track findings were not counted"
    Assert-Equal 1 $drcMetrics.dangling_vias "Dangling-via findings were not counted"
    Assert-Equal 1 $drcMetrics.unconnected_items "Unconnected-item findings were not counted"

    $fixturePath = Join-Path $tempRoot "fixture.dsn"
    "dummy" | Set-Content $fixturePath
    $run = [PSCustomObject]@{
        fixture = [PSCustomObject]@{
            filename = "fixture.dsn"
            relative_path = "fixture.dsn"
            group = "test"
            size_bytes = 1
            layer_count = 2
            net_count = 1
            component_count = 0
            board_width_mm = 1
            board_height_mm = 1
            board_area_cm2 = 0.01
            host_cad = "test"
            host_version = "1"
        }
        binary = [PSCustomObject]@{ version_label = "current" }
        run_mode = "CLI"
        run_at = "2026-01-01T00:00:00Z"
        phases = [PSCustomObject]@{
            fanout = [PSCustomObject]@{ log_found = $false; duration_seconds = $null; passes_completed = $null }
            autorouter = [PSCustomObject]@{ log_found = $false; duration_seconds = $null; passes_completed = $null }
            optimizer = [PSCustomObject]@{ log_found = $false; duration_seconds = $null; passes_completed = $null }
        }
        quality = [PSCustomObject]@{
            final_unrouted = $null
            clearance_violations = $null
            quality_score = $null
            peak_heap_mb = $null
            total_allocated_gb = $null
            total_cpu_seconds = $null
            wall_clock_seconds = $null
        }
        drc = [PSCustomObject]@{
            final_unrouted = $null
            final_violations = 5
            summary_violations = $null
            clearance_violations = 2
            dangling_tracks = 1
            dangling_vias = 1
            unconnected_items = 1
            unconnected_findings = 3
            final_quality_score = $null
            drc_binary_version = "current"
            drc_binary_sha256 = "abc"
            report_file = "fixture--drc.json"
        }
        log_analysis = [PSCustomObject]@{
            warn_count = 0
            error_count = 0
            load_error = $false
            exceptions = @()
            timed_out = $true
            metric_source = "none"
        }
        exit = [PSCustomObject]@{ timed_out = $true }
        log_file = $null
    }
    $cache = @{ "test" = $run }
    $mdPath = Join-Path $tempRoot "report.md"
    $csvPath = Join-Path $tempRoot "report.csv"
    $chartPath = Join-Path $tempRoot "chart.json"
    Export-MarkdownReport $cache $mdPath $csvPath $chartPath $tempRoot
    $markdown = Get-Content $mdPath -Raw
    $csv = Get-Content $csvPath -Raw
    if ($markdown -notmatch "N/A") {
        throw "Markdown must display N/A when no final/checkpoint metric exists."
    }
    if ($csv -notmatch "drc_track_dangling" -or $csv -notmatch ",1,1,1,3,") {
        throw "CSV must preserve dangling and unconnected DRC categories."
    }

    Write-Output "Benchmark regression assertions passed."
} finally {
    Remove-Item $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
