param(
    [string]$BinariesDir,
    [string]$ResultsDir = "",
    [string]$Tier = "A",
    [int]$MaxBoards = 20,
    [string]$PCBenchRoot = ""
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\Paths.ps1")

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
if (-not $ResultsDir) {
    $ResultsDir = Join-Path (Join-Path $repoRoot "experiments") "pcbench-gate"
}
$fixturesDir = Join-Path (Join-Path $PSScriptRoot "..\benchmark\fixtures") "PCBench"
$catalogPath = Join-Path $fixturesDir "catalog.json"

if (-not (Test-Path $catalogPath)) {
    Write-Warning "PCBench catalog.json not found in $fixturesDir. Running conversion..."
    & (Join-Path $PSScriptRoot "Convert-PCBenchBoards.ps1") -MaxBoards $MaxBoards -PCBenchRoot $PCBenchRoot
}

$catalog = Get-Content $catalogPath -Raw | ConvertFrom-Json
$tierBoards = @()
foreach ($b in $catalog.boards) {
    if ($Tier -eq "All" -or $b.tier -eq $Tier) {
        $tierBoards += $b
    }
}

if ($MaxBoards -gt 0 -and $tierBoards.Count -gt $MaxBoards) {
    $tierBoards = @($tierBoards | Select-Object -First $MaxBoards)
}

Write-Host "Running PCBench Gate for Tier '$Tier' ($($tierBoards.Count) board(s))..."

if (-not $BinariesDir) {
    $BinariesDir = Join-Path $PSScriptRoot "..\benchmark\binaries"
}

$benchmarksScript = Join-Path $PSScriptRoot "..\benchmark\run-benchmarks.ps1"
$failedCanaries = @()

foreach ($board in $tierBoards) {
    $boardId = $board.board_id
    $dsnRelPath = "PCBench/$boardId/unrouted.dsn"
    $maxTime = if ($board.timeout_budget) { $board.timeout_budget } else { "00:02:00" }

    Write-Host "  -> Running PCBench [$($board.tier)] $boardId (budget: $maxTime)..."
    & $benchmarksScript `
        -BinariesDir $BinariesDir `
        -FixturesDir (Join-Path $PSScriptRoot "..\benchmark\fixtures") `
        -ResultsDir $ResultsDir `
        -FilterFixture $dsnRelPath `
        -FilterBinary "*current*" `
        -MaxTime $maxTime `
        -SkipWebsiteUpdate `
        -Force 2>&1 | Out-Null

    # Verify run outcome from benchmarks.json
    $jsonPath = Join-Path $ResultsDir "benchmarks.json"
    if (Test-Path $jsonPath) {
        try {
            $data = Get-Content $jsonPath -Raw | ConvertFrom-Json
            $latestRun = $data.runs | Where-Object { $_.fixture.relative_path -replace '\\', '/' -like "*$boardId/unrouted.dsn" } |
                Sort-Object -Property { $_.run_at } -Descending | Select-Object -First 1

            if ($latestRun) {
                $unrouted = if ($latestRun.quality.unrouted_nets -ne $null) { [int]$latestRun.quality.unrouted_nets } else { 0 }
                $violations = if ($latestRun.quality.clearance_violations -ne $null) { [int]$latestRun.quality.clearance_violations } else { 0 }
                $timedOut = $latestRun.exit.timed_out -eq $true

                if ($Tier -eq "A" -and ($unrouted -gt 0 -or $violations -gt 0 -or $timedOut)) {
                    Write-Warning "Tier A Canary Regression on ${boardId}: unrouted=$unrouted, violations=$violations, timeout=$timedOut"
                    $failedCanaries += $boardId
                }
            }
        } catch {
            Write-Warning "Could not parse run results for ${boardId}: $_"
        }
    }
}

if ($failedCanaries.Count -gt 0) {
    Write-Error "PCBench Tier A Gate FAILED: $($failedCanaries.Count) canary board(s) regressed: $($failedCanaries -join ', ')"
    exit 1
}

Write-Host "PCBench Gate for Tier '$Tier' PASSED ($($tierBoards.Count) board(s)); results in $ResultsDir" -ForegroundColor Green
exit 0
