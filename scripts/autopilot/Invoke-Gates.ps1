param(
    [Parameter(Mandatory = $true)]
    [string]$ExperimentId,
    [string]$RepoRoot = (Join-Path $PSScriptRoot "..\.."),
    [switch]$SkipG2,
    [switch]$IncludeG3,
    [int]$G1Runs = 3
)

$ErrorActionPreference = "Stop"
$expDir = Join-Path (Join-Path $RepoRoot "experiments") $ExperimentId
$metaPath = Join-Path $expDir "meta.json"
if (-not (Test-Path $metaPath)) {
    Write-Error "Experiment not found: $ExperimentId"
    exit 1
}
$meta = Get-Content $metaPath -Raw | ConvertFrom-Json
$worktree = Join-Path $RepoRoot $meta.worktree
if (-not (Test-Path $worktree)) {
    Write-Error "Worktree missing: $worktree"
    exit 1
}

$gateLog = Join-Path $expDir "gates.log"
function Write-GateLog([string]$msg) {
    $line = "$(Get-Date -Format o) $msg"
    Add-Content $gateLog $line
    Write-Output $line
}

Push-Location $worktree

# G0 — unit tests + jar
Write-GateLog "G0: running fast tests"
& .\gradlew.bat test --no-daemon 2>&1 | Tee-Object -FilePath (Join-Path $expDir "g0-test.log")
if ($LASTEXITCODE -ne 0) {
    Write-GateLog "G0 FAILED: unit tests"
    Pop-Location
    exit 1
}

Write-GateLog "G0: building executableJar"
& .\gradlew.bat executableJar --no-daemon 2>&1 | Tee-Object -FilePath (Join-Path $expDir "g0-build.log")
if ($LASTEXITCODE -ne 0) {
    Write-GateLog "G0 FAILED: executableJar"
    Pop-Location
    exit 1
}

$jar = Get-ChildItem "build\libs" -Filter "*executable*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
    Write-GateLog "G0 FAILED: no executable jar"
    Pop-Location
    exit 1
}

$benchBin = Join-Path $worktree "scripts\benchmark\binaries"
New-Item -ItemType Directory -Force -Path $benchBin | Out-Null
Copy-Item $jar.FullName (Join-Path $benchBin "freerouting-current.jar") -Force

# G1 — canary fixtures
Write-GateLog "G1: canary benchmarks ($G1Runs runs each)"
$canaries = @("DAC2020_bm02.dsn", "DAC2020_bm07.dsn", "DAC2020_bm08.dsn", "ecc83-pp.dsn")
foreach ($canary in $canaries) {
    & "$RepoRoot\scripts\benchmark\run-benchmarks.ps1" `
        -BinariesDir $benchBin `
        -FixturesDir "$RepoRoot\scripts\benchmark\fixtures" `
        -ResultsDir (Join-Path $expDir "g1-results") `
        -FilterFixture $canary `
        -FilterBinary "freerouting-current.jar" `
        -MaxPasses 20 `
        -MaxTime "00:10:00" `
        -RunsPerConfig $G1Runs `
        -Force 2>&1 | Add-Content $gateLog
    if ($LASTEXITCODE -ne 0) {
        Write-GateLog "G1 FAILED: $canary"
        Pop-Location
        exit 1
    }
}
Write-GateLog "G1 PASSED"

if (-not $SkipG2) {
    Write-GateLog "G2: full fixture suite"
    & "$RepoRoot\scripts\benchmark\run-benchmarks.ps1" `
        -BinariesDir $benchBin `
        -FixturesDir "$RepoRoot\scripts\benchmark\fixtures" `
        -ResultsDir (Join-Path $expDir "g2-results") `
        -FilterBinary "freerouting-current.jar" `
        -SkipWebsiteUpdate `
        -Force 2>&1 | Add-Content $gateLog
    if ($LASTEXITCODE -ne 0) {
        Write-GateLog "G2 FAILED"
        Pop-Location
        exit 1
    }
    Write-GateLog "G2 PASSED"
}

if ($IncludeG3) {
    Write-GateLog "G3: PCBench subset"
    $pcbenchScript = Join-Path $RepoRoot "scripts\pcbench\Run-PCBenchGate.ps1"
    if (Test-Path $pcbenchScript) {
        & $pcbenchScript -BinariesDir $benchBin -ResultsDir (Join-Path $expDir "g3-results") 2>&1 | Add-Content $gateLog
        if ($LASTEXITCODE -ne 0) {
            Write-GateLog "G3 FAILED"
            Pop-Location
            exit 1
        }
        Write-GateLog "G3 PASSED"
    } else {
        Write-GateLog "G3 SKIPPED: Run-PCBenchGate.ps1 not found"
    }
}

Pop-Location
Write-GateLog "ALL GATES PASSED"
exit 0
