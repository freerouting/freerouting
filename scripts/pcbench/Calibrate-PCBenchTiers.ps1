<#
.SYNOPSIS
    Runs empirical routing calibration across PCBench Tier A candidates.

.DESCRIPTION
    Executes headless routing on candidate canary boards to verify 100% clean solves
    (0 unrouted nets, 0 clearance violations) and locks them into Tier A.

.PARAMETER Workers
    Number of parallel routing workers (default: 4).

.PARAMETER MaxBoards
    Limit calibration to first N boards (0 for all candidates).

.PARAMETER TimeoutSeconds
    Per-board routing timeout budget in seconds (default: 30).
#>
param(
    [int]$Workers = 4,
    [int]$MaxBoards = 0,
    [int]$TimeoutSeconds = 30
)

$ErrorActionPreference = "Stop"

$script = Join-Path $PSScriptRoot "calibrate_tiers.py"
$pyArgs = @(
    $script,
    "--workers", [string]$Workers,
    "--timeout", [string]$TimeoutSeconds
)
if ($MaxBoards -gt 0) {
    $pyArgs += @("--max-boards", [string]$MaxBoards)
}

python @pyArgs
