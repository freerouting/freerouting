<#
.SYNOPSIS
    Runs comprehensive headless benchmarks across PCBench in-repo fixtures.

.DESCRIPTION
    Executes Freerouting headlessly across PCBench fixtures (filtered by Tier or All),
    collects fine-grained DRC clearance violation distances (min/max/avg mm),
    writes records to benchmarks.json, and regenerates Markdown & HTML summaries.

.PARAMETER Tier
    Filter by tier: "A", "B", "C", "D", or "All" (default: "All").

.PARAMETER Workers
    Number of parallel routing workers (default: 4).

.PARAMETER MaxBoards
    Limit execution to first N boards (default: 0 for all).

.PARAMETER VersionLabel
    Version label for the binary (default: "v2.3.1-SNAPSHOT").
#>
param(
    [string]$Tier = "All",
    [int]$Workers = 4,
    [int]$MaxBoards = 0,
    [string]$VersionLabel = "v2.3.1-SNAPSHOT",
    [string]$JarPath = "",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

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
