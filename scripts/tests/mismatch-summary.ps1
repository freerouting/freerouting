Set-Location "C:\Work\freerouting"

$regex = 'RAW_SECTION (assign|skip)|ROOM_DOOR (context|candidate)|ROOM_COMPLETE_SYNC|COMPLETE_ROOM (input|initial_candidate|first_candidate|added)|ROOM_EDGE_REMOVE (start|enlarged|contained|complete_shape|applied)|COMPLETE_SHAPE_(FILTER|STEP|OBS|DECISION)'

$normalize = {
    param([string]$line)
    $line = $line -replace '^.*?INFO\s+', ''
    $line = $line -replace 'expansion_value=[^,]+', 'expansion_value=*'
    $line = $line -replace 'sorting_value=[^,]+', 'sorting_value=*'
    # Keep obstacle_id stable in parity reports so first divergence can be tied to concrete board items.
    return $line
}

$curr = Get-Content "logs\freerouting-current.log" | Select-String -Pattern $regex | ForEach-Object {
    [PSCustomObject]@{ n = (& $normalize $_.Line); raw = $_.Line; lineNo = $_.LineNumber }
}
$v19  = Get-Content "logs\freerouting-v190.log" | Select-String -Pattern $regex | ForEach-Object {
    [PSCustomObject]@{ n = (& $normalize $_.Line); raw = $_.Line; lineNo = $_.LineNumber }
}

$max = [Math]::Min($curr.Count, $v19.Count)
$mismatch = -1
for ($i = 0; $i -lt $max; $i++) {
    if ($curr[$i].n -ne $v19[$i].n) {
        $mismatch = $i
        break
    }
}

$out = New-Object System.Collections.Generic.List[string]
if ($mismatch -ge 0) {
    $out.Add("FIRST_MISMATCH_INDEX=$mismatch")
    $out.Add("CURRENT_LINE_NO=$($curr[$mismatch].lineNo)")
    $out.Add("V19_LINE_NO=$($v19[$mismatch].lineNo)")
    $out.Add("CURRENT: $($curr[$mismatch].n)")
    $out.Add("V19:     $($v19[$mismatch].n)")
    $out.Add("CURRENT_RAW: $($curr[$mismatch].raw)")
    $out.Add("V19_RAW:     $($v19[$mismatch].raw)")
    $start = [Math]::Max(0, $mismatch - 8)
    $end = [Math]::Min($max - 1, $mismatch + 12)
    for ($j = $start; $j -le $end; $j++) {
        $out.Add("[$j] C(line=$($curr[$j].lineNo)): $($curr[$j].n)")
        $out.Add("[$j] V(line=$($v19[$j].lineNo)): $($v19[$j].n)")
    }
} else {
    $out.Add('NO_MISMATCH_IN_OVERLAP')
}
$out.Add("CURRENT_LINES=$($curr.Count) V19_LINES=$($v19.Count)")

$out | Set-Content "logs\mismatch-summary-latest.txt"
Write-Output "WROTE logs/mismatch-summary-latest.txt"

