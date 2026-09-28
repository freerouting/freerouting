<#
.SYNOPSIS
    Runs comprehensive headless benchmarks across PCBench in-repo fixtures.

.DESCRIPTION
    Executes Freerouting headlessly across PCBench fixtures (filtered by Tier or All),
    collects fine-grained DRC clearance violation distances (min/max/avg mm),
    writes records to benchmarks.json, and regenerates Markdown & HTML summaries.

.PARAMETER Tier
    Filter by tier. Default "All" runs A, then D, then C, then B.
    A comma-separated list keeps that order, for example "C,A".

.PARAMETER Workers
    Number of parallel routing workers (default: 4).

.PARAMETER MaxBoards
    Limit execution to first N boards (default: 0 for all).

.PARAMETER VersionLabel
    Version label for the binary (default: "2.5.0-RC10").
#>
param(
    [string]$Tier = "All",
    [int]$Workers = 8,
    [int]$MaxBoards = 0,
    [string]$VersionLabel = "2.5.0-RC10",
    [string]$JarPath = "",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not $JarPath) {
    $JarPath = Join-Path $PSScriptRoot "..\benchmark\binaries\freerouting-2.5.0-RC10.jar"
}

$script = Join-Path $PSScriptRoot "run_corpus_benchmark.py"
$pyArgs = @(
    $script,
    "--tier", $Tier,
    "--workers", [string]$Workers,
    "--version-label", $VersionLabel
)
if ($JarPath) {
    $pyArgs += @("--jar", $JarPath)
}
if ($MaxBoards -gt 0) {
    $pyArgs += @("--max-boards", [string]$MaxBoards)
}
if ($Force) {
    $pyArgs += @("--force")
}

python @pyArgs
