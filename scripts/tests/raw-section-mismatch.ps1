param(
    [string]$CurrentLog = "logs\freerouting-current.log",
    [string]$BaselineLog = "logs\freerouting-v250.log"
)

$RepoRoot = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $RepoRoot

function Normalize-Raw([string]$path) {
    $i = 0
    Get-Content $path | ForEach-Object {
        $line = $_
        if ($line -match 'RAW_SECTION (assign|skip)') {
            $norm = $line -replace '^.*?RAW_SECTION', 'RAW_SECTION'
            $norm = $norm -replace 'expansion_value=[^,]+', 'expansion_value=*'
            $norm = $norm -replace 'sorting_value=[^,]+', 'sorting_value=*'
            [PSCustomObject]@{ idx = $i; norm = $norm; raw = $line }
            $i++
        }
    }
}

$curr = @(Normalize-Raw $CurrentLog)
$base = @(Normalize-Raw $BaselineLog)
$max = [Math]::Min($curr.Count, $base.Count)
$m = -1
for ($k = 0; $k -lt $max; $k++) {
    if ($curr[$k].norm -ne $base[$k].norm) { $m = $k; break }
}

$out = New-Object System.Collections.Generic.List[string]
if ($m -ge 0) {
    $out.Add("FIRST_RAW_MISMATCH_STREAM_INDEX=$m")
    $out.Add("CURRENT:  $($curr[$m].norm)")
    $out.Add("BASELINE: $($base[$m].norm)")
    $start = [Math]::Max(0, $m - 10)
    $end = [Math]::Min($max - 1, $m + 15)
    for ($j = $start; $j -le $end; $j++) {
        $out.Add("[$j] C: $($curr[$j].norm)")
        $out.Add("[$j] B: $($base[$j].norm)")
    }
} else {
    $out.Add('NO_RAW_MISMATCH_IN_OVERLAP')
}
$out.Add("CURRENT_RAW_COUNT=$($curr.Count) BASELINE_RAW_COUNT=$($base.Count)")
$out | Set-Content "logs\raw-mismatch-latest.txt"
Write-Output "WROTE logs/raw-mismatch-latest.txt"
