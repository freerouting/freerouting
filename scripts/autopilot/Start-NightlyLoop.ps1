param(
    [string]$RepoRoot = (Join-Path $PSScriptRoot "..\.."),
    [int]$MaxExperiments = 3,
    [string]$BaseBranch = "autopilot/main"
)

$ErrorActionPreference = "Stop"
Push-Location $RepoRoot

git fetch origin 2>$null
git checkout $BaseBranch 2>$null
git pull --ff-only 2>$null

$completed = 0
$pending = Get-ChildItem (Join-Path $RepoRoot "experiments") -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path (Join-Path $_.FullName "meta.json") } |
    ForEach-Object {
        $m = Get-Content (Join-Path $_.FullName "meta.json") -Raw | ConvertFrom-Json
        if ($m.status -eq "pending") { $m }
    }

foreach ($exp in $pending) {
    if ($completed -ge $MaxExperiments) { break }
    Write-Output "Running gates for $($exp.experiment_id)"
    & "$PSScriptRoot\Invoke-Gates.ps1" -ExperimentId $exp.experiment_id -RepoRoot $RepoRoot
    if ($LASTEXITCODE -ne 0) {
        & "$PSScriptRoot\Close-Experiment.ps1" -ExperimentId $exp.experiment_id -Reject
        continue
    }
    & "$PSScriptRoot\Invoke-Evaluation.ps1" -ExperimentId $exp.experiment_id -RepoRoot $RepoRoot
    if ($LASTEXITCODE -eq 0) {
        & "$PSScriptRoot\Close-Experiment.ps1" -ExperimentId $exp.experiment_id -Accept
    } else {
        & "$PSScriptRoot\Close-Experiment.ps1" -ExperimentId $exp.experiment_id -Reject
    }
    $completed++
}

# Refresh website benchmarks if results exist
$benchJson = Join-Path $RepoRoot "scripts\benchmark\results\benchmarks.json"
if (Test-Path $benchJson) {
    & "$RepoRoot\scripts\benchmark\run-benchmarks.ps1" -ReportOnly -SkipWebsiteUpdate:$false 2>$null
}

Pop-Location
Write-Output "Nightly loop finished ($completed experiments processed)"

# Promotion hint: open PR when >=3 accepted experiments with no open regressions
$ledger = Join-Path $RepoRoot "experiments\experiments.jsonl"
if (Test-Path $ledger) {
    $accepts = (Get-Content $ledger | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object { $_.verdict -eq "accept" }).Count
    if ($accepts -ge 3) {
        Write-Output "PROMOTION: $accepts accepted experiments — consider: gh pr create --base master --head $BaseBranch"
    }
}
