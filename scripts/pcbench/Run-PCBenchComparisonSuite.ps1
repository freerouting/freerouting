<#
.SYNOPSIS
    Runs PCBench suite across multiple versions sequentially: 2.4.0-RC1, 2.2.4, and 2.3.0.

.DESCRIPTION
    Compiles or verifies binaries, executes run_corpus_benchmark.py for the current branch
    as 2.4.0-RC1, followed by freerouting-2.2.4.jar, followed by freerouting-2.3.0.jar.
    Regenerates reports after each version and on completion.
#>
param(
    [string]$Tier = "All",
    [int]$Workers = 8,
    [int]$MaxBoards = 0,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $scriptDir "../..")).Path
$benchmarkBinDir = Join-Path $repoRoot "scripts/benchmark/binaries"
$runScript = Join-Path $scriptDir "run_corpus_benchmark.py"

$versions = @(
    @{
        Label = "2.4.0-RC1"
        Jar   = (Join-Path $benchmarkBinDir "freerouting-current.jar")
    },
    @{
        Label = "2.2.4"
        Jar   = (Join-Path $benchmarkBinDir "freerouting-2.2.4.jar")
    },
    @{
        Label = "2.3.0"
        Jar   = (Join-Path $benchmarkBinDir "freerouting-2.3.0.jar")
    }
)

foreach ($v in $versions) {
    if (-not (Test-Path $v.Jar)) {
        Write-Error "Required JAR binary not found: $($v.Jar)"
        exit 1
    }
}

$phaseNum = 0
foreach ($v in $versions) {
    $phaseNum++
    Write-Host "`n========================================================================" -ForegroundColor Cyan
    Write-Host " [Phase $phaseNum/3] Starting PCBench for $($v.Label)..." -ForegroundColor Cyan
    Write-Host " JAR: $($v.Jar)" -ForegroundColor Gray
    Write-Host "========================================================================`n" -ForegroundColor Cyan

    $pyArgs = @(
        $runScript,
        "--tier", $Tier,
        "--workers", [string]$Workers,
        "--version-label", $v.Label,
        "--jar", $v.Jar
    )
    if ($MaxBoards -gt 0) {
        $pyArgs += @("--max-boards", [string]$MaxBoards)
    }
    if ($Force) {
        $pyArgs += @("--force")
    }

    python @pyArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Phase $phaseNum ($($v.Label)) exited with code $LASTEXITCODE"
    }
}

Write-Host "`nAll PCBench versions completed! Regenerating final Markdown & HTML reports..." -ForegroundColor Green
powershell -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts/benchmark/run-benchmarks.ps1") -ReportOnly
Write-Host "Finished successfully!" -ForegroundColor Green
