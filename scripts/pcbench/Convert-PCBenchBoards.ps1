param(
    [string]$PCBenchRoot = "",
    [string]$OutputFixturesDir = "$PSScriptRoot\..\benchmark\fixtures\PCBench",
    [string]$StrippedDir = "$PSScriptRoot\cache\stripped",
    [int]$MaxBoards = 30,
    [string[]]$IncludeBoards = @(),
    [string]$JarPath = "",
    [switch]$SkipDrc,
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
    Write-Host "PCBench directory not found at $PCBenchDir. Running Fetch-PCBench.ps1..."
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

Write-Host "PCBench Root: $PCBenchRoot"
Write-Host "Output Fixtures: $OutputFixturesDir"

if ($IncludeBoards.Count -eq 1 -and $IncludeBoards[0] -match ",") {
    $IncludeBoards = @($IncludeBoards[0].Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

$selected = @()
if ($IncludeBoards.Count -gt 0) {
    Write-Host "Looking up $($IncludeBoards.Count) requested board(s)..."
    foreach ($name in $IncludeBoards) {
        $dir = Join-Path $PCBenchDir $name
        if (Test-Path $dir) {
            $selected += [PSCustomObject]@{ Name = $name; FullName = $dir }
        } else {
            Write-Warning "Board not found in PCBench: $name"
        }
    }
} else {
    Write-Host "Scanning PCBench boards in $PCBenchDir..."
    $allDirs = Get-ChildItem $PCBenchDir -Directory
    $parsedBoards = [System.Collections.ArrayList]::new()
    foreach ($d in $allDirs) {
        $rawPath = Join-Path $d.FullName "raw.kicad_pcb"
        $procPath = Join-Path $d.FullName "processed.kicad_pcb"
        $metaPath = Join-Path $d.FullName "metadata.json"
        if ((Test-Path $procPath) -and (Test-Path $metaPath)) {
            $layers = 0
            try {
                $meta = Get-Content $metaPath -Raw | ConvertFrom-Json
                if ($null -ne $meta.layers) { $layers = [int]$meta.layers }
            } catch {}
            [void]$parsedBoards.Add([PSCustomObject]@{
                Name = $d.Name
                FullName = $d.FullName
                Layers = $layers
            })
        }
    }
    Write-Host "Found $($parsedBoards.Count) valid PCBench boards."

    $twoLayer = @($parsedBoards | Where-Object { $_.Layers -eq 2 })
    $midLayer = @($parsedBoards | Where-Object { $_.Layers -ge 3 -and $_.Layers -le 4 })
    $highLayer = @($parsedBoards | Where-Object { $_.Layers -ge 6 })
    $rest = @($parsedBoards | Where-Object { $_.Layers -ne 2 -and -not ($_.Layers -ge 3 -and $_.Layers -le 4) -and $_.Layers -lt 6 })

    $oneBitsy = $parsedBoards | Where-Object { $_.Name -eq "1Bitsy_1bitsy" } | Select-Object -First 1
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

Write-Host "Selected $($selected.Count) board(s) for conversion:"
foreach ($s in $selected) {
    Write-Host "  - $($s.Name)"
}

$converted = @()
$failed = @()
$boardIndex = 0

foreach ($board in $selected) {
    $boardIndex++
    $name = $board.Name
    $boardDir = $board.FullName
    $rawPcb = Join-Path $boardDir "raw.kicad_pcb"
    $sourcePcb = Join-Path $boardDir "processed.kicad_pcb"
    $boardFixtureDir = Join-Path $OutputFixturesDir $name
    New-Item -ItemType Directory -Force -Path $boardFixtureDir | Out-Null

    $stripped = Join-Path $StrippedDir "$name.kicad_pcb"
    $unroutedDsn = Join-Path $boardFixtureDir "unrouted.dsn"
    $refDsn = Join-Path $boardFixtureDir "reference-routed.dsn"

    $hasAllFiles = (Test-Path $unroutedDsn) -and (Test-Path $refDsn) -and
                   (Test-Path (Join-Path $boardFixtureDir "ground_truth.json")) -and
                   (Test-Path (Join-Path $boardFixtureDir "metadata.normalized.json")) -and
                   (Test-Path (Join-Path $boardFixtureDir "board-manifest.json"))

    if ($hasAllFiles -and -not $Force) {
        Write-Host "[$boardIndex/$($selected.Count)] $name : Already converted (use -Force to re-convert)."
        $converted += $name
        continue
    }

    Write-Host "[$boardIndex/$($selected.Count)] $name : Starting conversion..."
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    # 1. Export reference-routed.dsn from raw.kicad_pcb (with fallback to processed.kicad_pcb)
    if (Test-Path $rawPcb) {
        Write-Host "  [$name] Exporting reference-routed.dsn..."
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & $kicadPython $exportScript $rawPcb $refDsn --fallback $sourcePcb 2>&1 | Out-Null
        $ErrorActionPreference = $prevEap
        if (-not (Test-Path $refDsn)) {
            Write-Warning "Reference DSN export failed for $name"
        }
    }

    # 2. Strip processed.kicad_pcb and export unrouted.dsn
    Write-Host "  [$name] Stripping routing elements & zone fills..."
    python $stripScript $sourcePcb $stripped
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $stripped)) {
        Write-Warning "Strip failed for $name"
        $failed += $name
        continue
    }

    Write-Host "  [$name] Exporting unrouted.dsn..."
    & $kicadPython $exportScript $stripped $unroutedDsn 2>&1 | Out-Null
    if (-not (Test-Path $unroutedDsn)) {
        Write-Warning "Unrouted DSN export failed for $name (pcbnew.ExportSpecctraDSN)"
        $failed += $name
        continue
    }

    $dsnText = Get-Content $unroutedDsn -Raw -ErrorAction SilentlyContinue
    $looksLikeDsn = $dsnText -and ($dsnText -match '\(pcb') -and ($dsnText -match '\(parser')
    if (-not $looksLikeDsn) {
        Write-Warning "Smoke-load (text) failed for $name - DSN missing pcb/parser scopes"
        $failed += $name
        continue
    }

    # 3. KiCad DRC on raw.kicad_pcb if available
    $drcViolations = $null
    if (-not $SkipDrc -and $kicadCli -and (Test-Path $rawPcb)) {
        Write-Host "  [$name] Running KiCad DRC on raw.kicad_pcb..."
        $drcJson = Join-Path $StrippedDir "$name-kicad-drc.json"
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & $kicadCli pcb drc --format json --output $drcJson $rawPcb 2>&1 | Out-Null
        $ErrorActionPreference = $prevEap
        if (Test-Path $drcJson) {
            try {
                $drcData = Get-Content $drcJson -Raw | ConvertFrom-Json
                if ($drcData.violations) {
                    $drcViolations = @($drcData.violations).Count
                } elseif ($drcData.errors) {
                    $drcViolations = @($drcData.errors).Count
                }
            } catch {}
        }
    }

    # 4. Generate normalized metadata, ground truth, and manifest
    Write-Host "  [$name] Generating metadata, ground truth, and manifest..."
    $metaArgs = @(
        (Join-Path $PSScriptRoot "generate_board_metadata.py"),
        "--board-dir", $boardDir,
        "--output-dir", $boardFixtureDir,
        "--kicad-version", "10.0.2"
    )
    if ($drcJson -and (Test-Path $drcJson)) {
        $metaArgs += @("--kicad-drc-json", $drcJson)
    } elseif ($null -ne $drcViolations) {
        $metaArgs += @("--kicad-drc-violations", [string]$drcViolations)
    }
    python @metaArgs

    $sw.Stop()
    $converted += $name
    Write-Host "  [$name] SUCCESS in $([math]::Round($sw.Elapsed.TotalSeconds, 1))s (drc_violations=$drcViolations)" -ForegroundColor Green
}

if ($JarPath -and (Test-Path $JarPath) -and $converted.Count -gt 0) {
    $smokeDir = Join-Path $PSScriptRoot "cache\smoke"
    New-Item -ItemType Directory -Force -Path $smokeDir | Out-Null
    $sample = $converted | Select-Object -First 3
    foreach ($name in $sample) {
        $dsn = Join-Path (Join-Path $OutputFixturesDir $name) "unrouted.dsn"
        $drcJson = Join-Path $smokeDir "$name-drc.json"
        Write-Host "Smoke-loading $name via Freerouting -drc..."
        & java -jar $JarPath --gui.enabled=false --api_server.enabled=false --mcp_server.enabled=false `
            -de $dsn -drc $drcJson 2>&1 | Out-Null
        if (-not (Test-Path $drcJson)) {
            Write-Warning "DsnReader smoke-load failed for $name"
        }
    }
}

# 5. Generate catalog.json for all converted boards (with 2-space indentation)
Write-Host "Generating catalog.json for all converted boards..."
$catalogPath = Join-Path $OutputFixturesDir "catalog.json"
$pyCatalogScript = @"
import json, sys
from pathlib import Path
from datetime import datetime, timezone

out_dir = Path(sys.argv[1])
boards = []
for p in sorted(out_dir.iterdir()):
    if p.is_dir():
        meta = p / 'metadata.normalized.json'
        if meta.exists():
            try:
                boards.append(json.loads(meta.read_text(encoding='utf-8')))
            except Exception:
                pass

catalog = {
    'schema_version': 1,
    'total_boards': len(boards),
    'generated_at': datetime.now(timezone.utc).isoformat(),
    'boards': boards
}
(out_dir / 'catalog.json').write_text(json.dumps(catalog, indent=2), encoding='utf-8')
"@
python -c $pyCatalogScript $OutputFixturesDir

Write-Host "Converted $($converted.Count) / $($selected.Count) boards into $OutputFixturesDir" -ForegroundColor Cyan
if ($failed.Count -gt 0) {
    Write-Warning ("Failed boards: " + ($failed -join ", "))
}
