<#
.SYNOPSIS
    Assigns evaluation tiers (A-E) to PCBench fixtures.

.DESCRIPTION
    Runs the heuristic tier classifier across scripts/benchmark/fixtures/PCBench/
    updating catalog.json and each board's metadata.normalized.json.

.PARAMETER DryRun
    Preview tier assignments without writing changes.
#>
param(
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$script = Join-Path $PSScriptRoot "classify_pcbench_tiers.py"
$pyArgs = @($script)
if ($DryRun) {
    $pyArgs += "--dry-run"
}

python @pyArgs
