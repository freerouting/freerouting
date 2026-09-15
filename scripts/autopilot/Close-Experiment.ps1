param(
    [Parameter(Mandatory = $true)]
    [string]$ExperimentId,
    [switch]$Accept,
    [switch]$Reject,
    [string]$RepoRoot = (Join-Path $PSScriptRoot "..\.."),
    [string]$BaseBranch = "autopilot/main"
)

$ErrorActionPreference = "Stop"
if ($Accept -and $Reject) {
    Write-Error "Specify -Accept or -Reject, not both"
    exit 1
}
if (-not $Accept -and -not $Reject) {
    Write-Error "Specify -Accept or -Reject"
    exit 1
}

$expDir = Join-Path (Join-Path $RepoRoot "experiments") $ExperimentId
$meta = Get-Content (Join-Path $expDir "meta.json") -Raw | ConvertFrom-Json
$verdict = Get-Content (Join-Path $expDir "verdict.json") -Raw | ConvertFrom-Json
$worktree = Join-Path $RepoRoot $meta.worktree

Push-Location $RepoRoot

if ($Reject) {
    Write-Output "Rejecting experiment $ExperimentId"
    if (Test-Path $worktree) {
        git worktree remove $worktree --force 2>$null
    }
    git branch -D $meta.branch 2>$null
    $meta.status = "rejected"
    $meta | ConvertTo-Json | Set-Content (Join-Path $expDir "meta.json") -Encoding UTF8
    @{
        ts = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
        experiment_id = $ExperimentId
        verdict = "reject"
        reasons = $verdict.reasons
    } | ConvertTo-Json -Compress | Add-Content (Join-Path $RepoRoot "experiments\experiments.jsonl")
    Pop-Location
    exit 0
}

if (-not $verdict.accept) {
    Write-Error "Verdict is reject; run Invoke-Evaluation first or use -Reject"
    exit 1
}

Write-Output "Accepting experiment $ExperimentId — merging into $BaseBranch"
Push-Location $worktree
git add -A
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
    git commit -m "autopilot: accept experiment $ExperimentId"
}
Pop-Location

git checkout $BaseBranch 2>$null
if ($LASTEXITCODE -ne 0) {
    git checkout -b $BaseBranch
}
git merge --ff-only $meta.branch
if ($LASTEXITCODE -ne 0) {
    Write-Error "Fast-forward merge failed; resolve manually"
    exit 1
}

# Refresh baseline jar and manifest
& .\gradlew.bat executableJar --no-daemon
$jar = Get-ChildItem "build\libs" -Filter "*executable*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$benchBin = Join-Path $RepoRoot "scripts\benchmark\binaries"
New-Item -ItemType Directory -Force -Path $benchBin | Out-Null
Copy-Item $jar.FullName (Join-Path $benchBin "freerouting-current.jar") -Force

$gitSha = git rev-parse --short HEAD
$baselinePath = Join-Path $RepoRoot "scripts\benchmark\baselines\baseline-manifest.json"
$bm = Get-Content $baselinePath -Raw | ConvertFrom-Json
$bm.git_sha = $gitSha
$bm.created_at = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
$bm | ConvertTo-Json -Depth 4 | Set-Content $baselinePath -Encoding UTF8

git worktree remove $worktree --force 2>$null
git branch -d $meta.branch 2>$null

$meta.status = "accepted"
$meta | ConvertTo-Json | Set-Content (Join-Path $expDir "meta.json") -Encoding UTF8

@{
    ts = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
    experiment_id = $ExperimentId
    verdict = "accept"
    git_sha = $gitSha
    reasons = $verdict.reasons
} | ConvertTo-Json -Compress | Add-Content (Join-Path $RepoRoot "experiments\experiments.jsonl")

# Update REPORT.md
$reportPath = Join-Path $RepoRoot "experiments\REPORT.md"
$entry = @"

## $ExperimentId (accepted $(Get-Date -Format yyyy-MM-dd))

- Git SHA: ``$gitSha``
- Hypothesis: see ``experiments/$ExperimentId/hypothesis.md``
- Reasons: $($verdict.reasons -join '; ')

"@
if (-not (Test-Path $reportPath)) {
    "# Autopilot experiment report`n" | Set-Content $reportPath -Encoding UTF8
}
Add-Content $reportPath $entry

Pop-Location
Write-Output "Accepted and merged to $BaseBranch"
