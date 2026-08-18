<#
.SYNOPSIS
    Queries and filters the in-repo PCBench benchmark catalog.

.DESCRIPTION
    Allows fast filtering of 1,157 PCBench fixtures by tier, tag, layer count,
    net range, component range, area, or text search.

.PARAMETER Tier
    Filter by tier: "A", "B", "C", "D", "E", or "All".

.PARAMETER Tag
    Filter by tag (e.g. "canary", "plane", "2-layer", "small").

.PARAMETER Layers
    Filter by exact copper layer count (e.g. 2, 4, 6).

.PARAMETER MaxNets
    Filter boards with nets <= MaxNets.

.PARAMETER MinNets
    Filter boards with nets >= MinNets.

.PARAMETER Search
    Text search across board_id, display_name, author, license.

.PARAMETER Limit
    Maximum number of results to display (default: 25, 0 for all).

.PARAMETER AsJson
    Output matching results as formatted JSON.
#>
param(
    [string]$Tier,
    [string]$Tag,
    [int]$Layers = 0,
    [int]$MaxNets = 0,
    [int]$MinNets = 0,
    [string]$Search,
    [int]$Limit = 25,
    [switch]$AsJson
)

$ErrorActionPreference = "Stop"
$catalogPath = Join-Path $PSScriptRoot "..\benchmark\fixtures\PCBench\catalog.json"

if (-not (Test-Path $catalogPath)) {
    Write-Error "catalog.json not found at $catalogPath"
    exit 1
}

$catalog = Get-Content $catalogPath -Raw | ConvertFrom-Json
$boards = $catalog.boards

$matched = @()
foreach ($b in $boards) {
    if ($Tier -and $b.tier -ne $Tier) { continue }
    if ($Tag -and ($b.tags -notcontains $Tag -and ($b.tags -join " ") -notmatch $Tag)) { continue }
    if ($Layers -gt 0 -and $b.board.layers -ne $Layers) { continue }
    if ($MaxNets -gt 0 -and $b.board.nets -gt $MaxNets) { continue }
    if ($MinNets -gt 0 -and $b.board.nets -lt $MinNets) { continue }
    if ($Search) {
        $haystack = "$($b.board_id) $($b.display_name) $($b.author) $($b.license)"
        if ($haystack -notmatch $Search) { continue }
    }
    $matched += $b
}

if ($AsJson) {
    $slice = if ($Limit -gt 0) { @($matched | Select-Object -First $Limit) } else { $matched }
    $slice | ConvertTo-Json -Depth 5
    exit 0
}

Write-Host "Matched $($matched.Count) / $($boards.Count) boards in PCBench catalog." -ForegroundColor Cyan

$displayList = if ($Limit -gt 0 -and $matched.Count -gt $Limit) {
    @($matched | Select-Object -First $Limit)
} else {
    $matched
}

$tableRows = @()
foreach ($b in $displayList) {
    $tableRows += [PSCustomObject]@{
        Tier        = $b.tier
        BoardId     = $b.board_id
        Layers      = $b.board.layers
        Nets        = $b.board.nets
        Components  = $b.board.components
        Area_cm2    = $b.board.area_cm2
        Timeout     = $b.timeout_budget
        Tags        = ($b.tags -join ", ")
    }
}

$tableRows | Format-Table -AutoSize

if ($Limit -gt 0 -and $matched.Count -gt $Limit) {
    Write-Host "Showing first $Limit of $($matched.Count) matches. Use -Limit 0 to view all." -ForegroundColor DarkGray
}
