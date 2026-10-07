<#
.SYNOPSIS
    Builds a baseline Freerouting executable from a git tag with optional trace instrumentation.

.DESCRIPTION
    Checks out a specific release tag (by default from baseline-manifest.json) into a temporary
    git worktree, optionally applies an instrumentation patch, compiles the executable JAR,
    and copies it to the specified destination.

.PARAMETER GitTag
    The git tag to check out. Defaults to release_baseline.git_tag in baseline-manifest.json.

.PARAMETER PatchPath
    Optional path to a .patch file to apply to the checked-out baseline for diagnostic parity.

.PARAMETER OutputJarPath
    Path where the compiled baseline JAR should be saved.
    Defaults to scripts/benchmark/binaries/freerouting-baseline-instrumented.jar.

.EXAMPLE
    .\Build-Baseline.ps1
    .\Build-Baseline.ps1 -PatchPath "..\baselines\trace-v2.5.0.patch"
#>
param(
    [string]$GitTag = "",
    [string]$PatchPath = "",
    [string]$OutputJarPath = ""
)

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$ManifestPath = Join-Path $PSScriptRoot "baselines\baseline-manifest.json"

if (Test-Path $ManifestPath) {
    $manifest = Get-Content $ManifestPath -Raw | ConvertFrom-Json
    if (-not $GitTag -and $manifest.release_baseline -and $manifest.release_baseline.git_tag) {
        $GitTag = $manifest.release_baseline.git_tag
    }
}

if (-not $GitTag) {
    $GitTag = "v2.5.0"
}

if (-not $OutputJarPath) {
    $OutputJarPath = Join-Path $PSScriptRoot "binaries\freerouting-baseline-instrumented.jar"
}

Write-Output "Building baseline from git tag: $GitTag"

$worktreeDir = Join-Path $RepoRoot "build\worktree-baseline-$($GitTag.Replace('/', '_'))"
if (Test-Path $worktreeDir) {
    git -C $RepoRoot worktree remove $worktreeDir --force 2>$null
    if (Test-Path $worktreeDir) {
        Remove-Item -Recurse -Force $worktreeDir
    }
}

try {
    Write-Output "Creating worktree at $worktreeDir..."
    git -C $RepoRoot worktree add --detach $worktreeDir $GitTag
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create worktree for tag $GitTag"
    }

    if ($PatchPath -and (Test-Path $PatchPath)) {
        Write-Output "Applying diagnostic patch: $PatchPath"
        $resolvedPatch = (Resolve-Path $PatchPath).Path
        git -C $worktreeDir apply $resolvedPatch
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to apply patch $PatchPath"
        }
    }

    Write-Output "Compiling executableJar in worktree..."
    Push-Location $worktreeDir
    try {
        & .\gradlew.bat executableJar --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed in baseline worktree"
        }
    } finally {
        Pop-Location
    }

    $builtJar = Get-ChildItem (Join-Path $worktreeDir "build\libs") -Filter "*executable*.jar" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $builtJar) {
        throw "No executable JAR found in $worktreeDir\build\libs"
    }

    $outDir = Split-Path $OutputJarPath -Parent
    if (-not (Test-Path $outDir)) {
        New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    }

    Copy-Item $builtJar.FullName $OutputJarPath -Force
    Write-Output "Successfully built baseline JAR: $OutputJarPath"
} finally {
    if (Test-Path $worktreeDir) {
        Write-Output "Cleaning up worktree..."
        git -C $RepoRoot worktree remove $worktreeDir --force 2>$null
        if (Test-Path $worktreeDir) {
            Remove-Item -Recurse -Force $worktreeDir
        }
    }
}
