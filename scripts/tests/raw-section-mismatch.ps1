Set-Location "C:\Work\freerouting"

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

$curr = @(Normalize-Raw "logs\freerouting-current.log")
$v19 = @(Normalize-Raw "logs\freerouting-v190.log")
$max = [Math]::Min($curr.Count, $v19.Count)
$m = -1
for ($k = 0; $k -lt $max; $k++) {
    if ($curr[$k].norm -ne $v19[$k].norm) { $m = $k; break }
}

$out = New-Object System.Collections.Generic.List[string]
if ($m -ge 0) {
    $out.Add("FIRST_RAW_MISMATCH_STREAM_INDEX=$m")
    $out.Add("CURRENT: $($curr[$m].norm)")
    $out.Add("V19:     $($v19[$m].norm)")
    $start = [Math]::Max(0, $m - 10)
    $end = [Math]::Min($max - 1, $m + 15)
    for ($j = $start; $j -le $end; $j++) {
        $out.Add("[$j] C: $($curr[$j].norm)")
        $out.Add("[$j] V: $($v19[$j].norm)")
    }
} else {
    $out.Add('NO_RAW_MISMATCH_IN_OVERLAP')
}
$out.Add("CURRENT_RAW_COUNT=$($curr.Count) V19_RAW_COUNT=$($v19.Count)")
$out | Set-Content "logs\raw-mismatch-latest.txt"
Write-Output "WROTE logs/raw-mismatch-latest.txt"

