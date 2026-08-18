param(
    [string]$BinariesDir,
    [string]$ResultsDir = "",
    [int]$MaxBoards = 10,
    [string]$PCBenchRoot = ""
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\Paths.ps1")

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
if (-not $ResultsDir) {
    $ResultsDir = Join-Path (Join-Path $repoRoot "experiments") "pcbench-gate"
}
$fixtures = Join-Path (Join-Path $PSScriptRoot "..\benchmark\fixtures") "PCBench"

if (-not (Test-Path $fixtures) -or -not (Get-ChildItem $fixtures -Filter "*.dsn" -ErrorAction SilentlyContinue)) {
    & (Join-Path $PSScriptRoot "Convert-PCBenchBoards.ps1") -MaxBoards $MaxBoards -PCBenchRoot $PCBenchRoot
}

if (-not $BinariesDir) {
    $BinariesDir = Join-Path $PSScriptRoot "..\benchmark\binaries"
}

& (Join-Path $PSScriptRoot "..\benchmark\run-benchmarks.ps1") `
    -BinariesDir $BinariesDir `
    -FixturesDir $fixtures `
    -ResultsDir $ResultsDir `
    -FilterBinary "*current*" `
    -MaxTime "00:20:00" `
    -SkipWebsiteUpdate `
    -Force

Write-Output "PCBench gate complete; results in $ResultsDir"
