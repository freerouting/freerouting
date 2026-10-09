param(
    [string]$CurrentLog = "logs\freerouting-current.log",
    [string]$BaselineLog = "logs\freerouting-v250.log"
)

$RepoRoot = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $RepoRoot

$regex = 'RAW_SECTION (assign|skip)|ROOM_DOOR (context|candidate)|ROOM_COMPLETE_SYNC|COMPLETE_ROOM (input|initial_candidate|first_candidate|added)|ROOM_EDGE_REMOVE (start|enlarged|contained|complete_shape|applied)|COMPLETE_SHAPE_(FILTER|STEP|OBS|DECISION)'

$normalize = {
    param([string]$line)
    $line = $line -replace '^.*?INFO\s+', ''
    $line = $line -replace 'expansion_value=[^,]+', 'expansion_value=*'
    $line = $line -replace 'sorting_value=[^,]+', 'sorting_value=*'
    # Keep obstacle_id stable in parity reports so first divergence can be tied to concrete board items.
    return $line
}

$curr = Get-Content $CurrentLog | Select-String -Pattern $regex | ForEach-Object {
    [PSCustomObject]@{ n = (& $normalize $_.Line); raw = $_.Line; lineNo = $_.LineNumber }
}
$base = Get-Content $BaselineLog | Select-String -Pattern $regex | ForEach-Object {
    [PSCustomObject]@{ n = (& $normalize $_.Line); raw = $_.Line; lineNo = $_.LineNumber }
}

$max = [Math]::Min($curr.Count, $base.Count)
$mismatch = -1
for ($i = 0; $i -lt $max; $i++) {
    if ($curr[$i].n -ne $base[$i].n) {
        $mismatch = $i
        break
    }
}

$out = New-Object System.Collections.Generic.List[string]
if ($mismatch -ge 0) {
    $out.Add("FIRST_MISMATCH_INDEX=$mismatch")
    $out.Add("CURRENT_LINE_NO=$($curr[$mismatch].lineNo)")
    $out.Add("BASELINE_LINE_NO=$($base[$mismatch].lineNo)")
    $out.Add("CURRENT:  $($curr[$mismatch].n)")
    $out.Add("BASELINE: $($base[$mismatch].n)")
    $out.Add("CURRENT_RAW:  $($curr[$mismatch].raw)")
    $out.Add("BASELINE_RAW: $($base[$mismatch].raw)")
    $start = [Math]::Max(0, $mismatch - 8)
    $end = [Math]::Min($max - 1, $mismatch + 12)
    for ($j = $start; $j -le $end; $j++) {
        $out.Add("[$j] C(line=$($curr[$j].lineNo)): $($curr[$j].n)")
        $out.Add("[$j] B(line=$($base[$j].lineNo)): $($base[$j].n)")
    }
} else {
    $out.Add('NO_MISMATCH_IN_OVERLAP')
}
$out.Add("CURRENT_LINES=$($curr.Count) BASELINE_LINES=$($base.Count)")

$out | Set-Content "logs\mismatch-summary-latest.txt"
Write-Output "WROTE logs/mismatch-summary-latest.txt"
