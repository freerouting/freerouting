param(
    [string]$PCBenchRoot = "",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\Paths.ps1")

if (-not $PCBenchRoot) {
    $PCBenchRoot = Get-PCBenchRoot
}

$boards = Join-Path $PCBenchRoot "PCBs"
if (-not (Test-Path $boards)) {
    Write-Error @"
PCBench boards not found at $boards.
Clone https://github.com/PCBench/PCBench to C:\Work\PCBench (or set FREEROUTING_PCBENCH)
so that PCBs\<board>\ contains processed.kicad_pcb, raw.kicad_pcb, metadata.json, and final.json.
"@
    exit 1
}

$count = @(Get-ChildItem $boards -Directory | Where-Object {
    (Test-Path (Join-Path $_.FullName "processed.kicad_pcb")) -and
    (Test-Path (Join-Path $_.FullName "metadata.json"))
}).Count

Write-Output "PCBench root: $PCBenchRoot"
Write-Output "Boards with processed.kicad_pcb + metadata.json: $count"
exit 0
