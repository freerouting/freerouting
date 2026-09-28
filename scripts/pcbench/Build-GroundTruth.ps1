param(
    [string]$PCBenchRoot = "",
    [string]$GroundTruthDir = "$PSScriptRoot\cache\ground_truth",
    [int]$MaxBoards = 40,
    [string[]]$IncludeBoards = @()
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\Paths.ps1")

if (-not $PCBenchRoot) {
    $PCBenchRoot = Get-PCBenchRoot
}
$PCBenchDir = Get-PCBenchBoardsDir $PCBenchRoot
$kicadCli = Get-KicadCliPath

New-Item -ItemType Directory -Force -Path $GroundTruthDir | Out-Null

$boards = @(Get-ChildItem $PCBenchDir -Directory | Where-Object {
    Test-Path (Join-Path $_.FullName "raw.kicad_pcb")
})
if ($IncludeBoards.Count -eq 1 -and $IncludeBoards[0] -match ",") {
    $IncludeBoards = @($IncludeBoards[0].Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}
if ($IncludeBoards.Count -gt 0) {
    $boards = @($boards | Where-Object { $IncludeBoards -contains $_.Name })
}
$boards = @($boards | Select-Object -First $MaxBoards)

foreach ($board in $boards) {
    $raw = Join-Path $board.FullName "raw.kicad_pcb"
    $text = Get-Content $raw -Raw
    $viaCount = ([regex]::Matches($text, '\(\s*via\b')).Count
    $segCount = ([regex]::Matches($text, '\(\s*segment\b')).Count
    $trackLen = 0.0
    foreach ($m in [regex]::Matches($text, '\(\s*segment[\s\S]*?\(start\s+([\d.-]+)\s+([\d.-]+)\)[\s\S]*?\(end\s+([\d.-]+)\s+([\d.-]+)\)')) {
        $x1 = [double]$m.Groups[1].Value
        $y1 = [double]$m.Groups[2].Value
        $x2 = [double]$m.Groups[3].Value
        $y2 = [double]$m.Groups[4].Value
        $trackLen += [math]::Sqrt(($x2 - $x1) * ($x2 - $x1) + ($y2 - $y1) * ($y2 - $y1))
    }

    $drcViolations = $null
    if ($kicadCli) {
        $drcJson = Join-Path $GroundTruthDir "$($board.Name)-kicad-drc.json"
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & $kicadCli pcb drc --format json --output $drcJson $raw 2>&1 | Out-Null
        $ErrorActionPreference = $prevEap
        if (Test-Path $drcJson) {
            try {
                $drc = Get-Content $drcJson -Raw | ConvertFrom-Json
                if ($drc.violations) {
                    $drcViolations = @($drc.violations).Count
                } elseif ($drc.errors) {
                    $drcViolations = @($drc.errors).Count
                }
            } catch {}
        }
    }

    $gt = [PSCustomObject]@{
        board = $board.Name
        source_pcb = $raw
        via_count = $viaCount
        segment_count = $segCount
        approximate_track_length_mm = [math]::Round($trackLen, 2)
        kicad_drc_violations = $drcViolations
        generated_at = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
        note = "KiCad DSN export omits copper-to-edge clearance (Issue 558); KiCad DRC on raw.kicad_pcb is the external arbiter."
    }
    $gt | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $GroundTruthDir "$($board.Name).json") -Encoding UTF8
    Write-Output "Ground truth: $($board.Name) vias=$viaCount segments=$segCount drc=$drcViolations"
}

Write-Output "Ground truth written to $GroundTruthDir ($($boards.Count) boards)"
