param(
    [Parameter(Mandatory = $true)]
    [string]$Hypothesis,
    [string]$RepoRoot = (Join-Path $PSScriptRoot "..\.."),
    [string]$ExperimentsDir = (Join-Path $PSScriptRoot "..\..\experiments"),
    [string]$BaseBranch = "autopilot/main"
)

$ErrorActionPreference = "Stop"
Push-Location $RepoRoot

$expDir = Join-Path $ExperimentsDir $id
$branch = "experiment/$id"
$worktreeDir = Join-Path (Join-Path $RepoRoot ".worktrees") $id

if (-not (Test-Path $ExperimentsDir)) {
    New-Item -ItemType Directory -Path $ExperimentsDir -Force | Out-Null
}

# Ensure autopilot base branch exists
$baseExists = git rev-parse --verify $BaseBranch 2>$null
if (-not $baseExists) {
    $current = git rev-parse --abbrev-ref HEAD
    git branch $BaseBranch 2>$null
    Write-Output "Created branch $BaseBranch from $current"
}

git worktree add -B $branch $worktreeDir $BaseBranch 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    git worktree add $worktreeDir $BaseBranch
}

New-Item -ItemType Directory -Path $expDir -Force | Out-Null
@"
# Experiment $id

## Hypothesis

$Hypothesis

## Branch

``$branch`` in worktree ``.worktrees/$id``

## Status

pending
"@ | Set-Content (Join-Path $expDir "hypothesis.md") -Encoding UTF8

@{
    experiment_id = $id
    branch = $branch
    worktree = ".worktrees/$id"
    hypothesis = $Hypothesis
    status = "pending"
    created_at = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
} | ConvertTo-Json | Set-Content (Join-Path $expDir "meta.json") -Encoding UTF8

@{
    accept = $false
    reasons = @()
} | ConvertTo-Json | Set-Content (Join-Path $expDir "verdict.json") -Encoding UTF8

Pop-Location
Write-Output "Created experiment $id"
Write-Output "Worktree: $RepoRoot/.worktrees/$id"
Write-Output "Docs: $expDir"
