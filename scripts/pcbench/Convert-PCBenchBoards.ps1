param(
    [string]$PCBenchRoot = "",
    [string]$OutputFixturesDir = "$PSScriptRoot\..\benchmark\fixtures\PCBench",
    [string]$StrippedDir = "$PSScriptRoot\cache\stripped",
    [int]$MaxBoards = 30,
    [string[]]$IncludeBoards = @(),
    [string]$JarPath = "",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\Paths.ps1")

if (-not $PCBenchRoot) {
    $PCBenchRoot = Get-PCBenchRoot
}
$PCBenchDir = Get-PCBenchBoardsDir $PCBenchRoot
$stripScript = Join-Path $PSScriptRoot "strip_kicad_routing.py"

if (-not (Test-Path $PCBenchDir)) {
    & (Join-Path $PSScriptRoot "Fetch-PCBench.ps1") -PCBenchRoot $PCBenchRoot
}

$kicadCli = Get-KicadCliPath
$kicadPython = Get-KicadPythonPath
if (-not $kicadPython) {
    Write-Error "KiCad python.exe not found. Expected at C:\Program Files\KiCad\10.0\bin\python.exe (pcbnew.ExportSpecctraDSN). KiCad 10 CLI has no specctra export subcommand."
    exit 1
}
$exportScript = Join-Path $PSScriptRoot "export_specctra_dsn.py"

New-Item -ItemType Directory -Force -Path $OutputFixturesDir | Out-Null
New-Item -ItemType Directory -Force -Path $StrippedDir | Out-Null

function Get-BoardLayers([string]$boardDir) {
    $metaPath = Join-Path $boardDir "metadata.json"
    if (-not (Test-Path $metaPath)) { return 0 }
    try {
        $meta = Get-Content $metaPath -Raw | ConvertFrom-Json
        if ($null -ne $meta.layers) { return [int]$meta.layers }
    } catch {}
    return 0
}

$allBoards = @(Get-ChildItem $PCBenchDir -Directory | Where-Object {
    (Test-Path (Join-Path $_.FullName "processed.kicad_pcb")) -and
    (Test-Path (Join-Path $_.FullName "metadata.json"))
})

if ($IncludeBoards.Count -eq 1 -and $IncludeBoards[0] -match ",") {
    $IncludeBoards = @($IncludeBoards[0].Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}
if ($IncludeBoards.Count -gt 0) {
    $selected = @($allBoards | Where-Object { $IncludeBoards -contains $_.Name })
} else {
    $twoLayer = @($allBoards | Where-Object { (Get-BoardLayers $_.FullName) -eq 2 })
    $midLayer = @($allBoards | Where-Object { $l = Get-BoardLayers $_.FullName; $l -ge 3 -and $l -le 4 })
    $highLayer = @($allBoards | Where-Object { (Get-BoardLayers $_.FullName) -ge 6 })
    $rest = @($allBoards | Where-Object {
        $l = Get-BoardLayers $_.FullName
        $l -ne 2 -and -not ($l -ge 3 -and $l -le 4) -and $l -lt 6
    })

    $selected = @()
    $oneBitsy = $allBoards | Where-Object { $_.Name -eq "1Bitsy_1bitsy" } | Select-Object -First 1
    if ($oneBitsy) { $selected += $oneBitsy }
    if ($twoLayer.Count -gt 0) { $selected += $twoLayer[0] }
    if ($highLayer.Count -gt 0) { $selected += $highLayer[0] }
    foreach ($bucket in @($midLayer, $twoLayer, $highLayer, $rest)) {
        foreach ($b in $bucket) {
            if ($selected.Count -ge $MaxBoards) { break }
            if ($selected.Name -notcontains $b.Name) { $selected += $b }
        }
        if ($selected.Count -ge $MaxBoards) { break }
    }
    $selected = @($selected | Select-Object -First $MaxBoards)
}

$converted = @()
$failed = @()

foreach ($board in $selected) {
    $name = $board.Name
    $sourcePcb = Join-Path $board.FullName "processed.kicad_pcb"
    $stripped = Join-Path $StrippedDir "$name.kicad_pcb"
    $dsnOut = Join-Path $OutputFixturesDir "$name.dsn"

    if ((Test-Path $dsnOut) -and -not $Force) {
        $converted += $name
        continue
    }

    python $stripScript $sourcePcb $stripped
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $stripped)) {
        Write-Warning "Strip failed for $name"
        $failed += $name
        continue
    }

    & $kicadPython $exportScript $stripped $dsnOut 2>&1 | Out-Null
    if (-not (Test-Path $dsnOut)) {
        Write-Warning "DSN export failed for $name (pcbnew.ExportSpecctraDSN)"
        $failed += $name
        continue
    }

    $dsnText = Get-Content $dsnOut -Raw -ErrorAction SilentlyContinue
    $looksLikeDsn = $dsnText -and ($dsnText -match '\(pcb') -and ($dsnText -match '\(parser')
    if (-not $looksLikeDsn) {
        Write-Warning "Smoke-load (text) failed for $name - DSN missing pcb/parser scopes"
        $failed += $name
        continue
    }

    $converted += $name
    $layers = Get-BoardLayers $board.FullName
    Write-Output "Converted $name (layers=$layers)"
}

if ($JarPath -and (Test-Path $JarPath) -and $converted.Count -gt 0) {
    $smokeDir = Join-Path $PSScriptRoot "cache\smoke"
    New-Item -ItemType Directory -Force -Path $smokeDir | Out-Null
    $sample = $converted | Select-Object -First 3
    foreach ($name in $sample) {
        $dsn = Join-Path $OutputFixturesDir "$name.dsn"
        $drcJson = Join-Path $smokeDir "$name-drc.json"
        Write-Output "Smoke-loading $name via Freerouting -drc"
        & java -jar $JarPath --gui.enabled=false --api_server.enabled=false --mcp_server.enabled=false `
            -de $dsn -drc $drcJson 2>&1 | Out-Null
        if (-not (Test-Path $drcJson)) {
            Write-Warning "DsnReader smoke-load failed for $name"
        }
    }
}

Write-Output "Converted $($converted.Count) / $($selected.Count) boards to $OutputFixturesDir"
if ($failed.Count -gt 0) {
    Write-Output ("Failed: " + ($failed -join ", "))
}
