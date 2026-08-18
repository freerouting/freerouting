param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path,
    [string]$PCBenchRoot = "C:\Work\PCBench"
)

$ErrorActionPreference = "Stop"
. (Join-Path $RepoRoot "scripts\pcbench\lib\Paths.ps1")

$outDir = Join-Path $RepoRoot "experiments\spikes\pcbench-poc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$kicadCli = Get-KicadCliPath
$kicadPython = Get-KicadPythonPath
$kicadVersion = if ($kicadCli) { (& $kicadCli version 2>&1 | Out-String).Trim() } else { "not installed" }

$pocBoards = @(
    "1Bitsy_1bitsy",
    "1-Wire-Wing-pcb_1-Wire_Wing",
    "front-end-modules_LimeSDR_Sony"
)

$jarPath = Join-Path $RepoRoot "build\libs\freerouting-current-executable.jar"
if (-not (Test-Path $jarPath)) {
    Push-Location $RepoRoot
    & .\gradlew.bat executableJar --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "executableJar failed" }
    Pop-Location
}

& (Join-Path $RepoRoot "scripts\pcbench\Fetch-PCBench.ps1") -PCBenchRoot $PCBenchRoot
& (Join-Path $RepoRoot "scripts\pcbench\Convert-PCBenchBoards.ps1") `
    -PCBenchRoot $PCBenchRoot `
    -MaxBoards 3 `
    -IncludeBoards $pocBoards `
    -JarPath $jarPath `
    -Force
& (Join-Path $RepoRoot "scripts\pcbench\Build-GroundTruth.ps1") `
    -PCBenchRoot $PCBenchRoot `
    -IncludeBoards ($pocBoards -join ",")

$converted = @(Get-ChildItem (Join-Path $RepoRoot "scripts\benchmark\fixtures\PCBench") -Filter "*.dsn" -ErrorAction SilentlyContinue)
$gtDir = Join-Path $RepoRoot "scripts\pcbench\cache\ground_truth"
$groundTruth = @()
foreach ($name in $pocBoards) {
    $gtPath = Join-Path $gtDir "$name.json"
    if (Test-Path $gtPath) {
        $groundTruth += (Get-Content $gtPath -Raw | ConvertFrom-Json)
    }
}

$smokeDir = Join-Path $RepoRoot "scripts\pcbench\cache\smoke"
$smokeLoaded = @()
if (Test-Path $smokeDir) {
    $smokeLoaded = @(Get-ChildItem $smokeDir -Filter "*-drc.json" | Select-Object -ExpandProperty Name)
}

# Round-trip the smallest 2-layer board: route 1 pass, reimport SES, run KiCad DRC.
$roundTrip = $null
$smallBoard = "1-Wire-Wing-pcb_1-Wire_Wing"
$smallDsn = Join-Path (Join-Path $RepoRoot "scripts\benchmark\fixtures\PCBench") "$smallBoard.dsn"
$strippedPcb = Join-Path (Join-Path $RepoRoot "scripts\pcbench\cache\stripped") "$smallBoard.kicad_pcb"
if (-not (Test-Path $strippedPcb)) {
    $strippedPcb = Join-Path $PCBenchRoot "PCBs\$smallBoard\processed.kicad_pcb"
}
if ((Test-Path $smallDsn) -and $kicadPython -and $kicadCli) {
    $ses = Join-Path $outDir "$smallBoard.ses"
    $manifest = Join-Path $outDir "$smallBoard-result.json"
    $importedPcb = Join-Path $outDir "$smallBoard-imported.kicad_pcb"
    $kicadDrc = Join-Path $outDir "$smallBoard-kicad-drc.json"

    & java -jar $jarPath --gui.enabled=false --api_server.enabled=false --mcp_server.enabled=false `
        -de $smallDsn -do $ses `
        --router.max_passes=1 --router.job_timeout="00:03:00" `
        --router.result_json="$manifest"

    $importOk = $false
    $drcOk = $false
    $drcViolations = $null
    if ((Test-Path $ses) -and (Test-Path $strippedPcb)) {
        & $kicadPython (Join-Path $RepoRoot "scripts\pcbench\import_specctra_ses.py") $strippedPcb $ses $importedPcb
        $importOk = ($LASTEXITCODE -eq 0) -and (Test-Path $importedPcb)
    }
    if ($importOk) {
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & $kicadCli pcb drc --output $kicadDrc --format json $importedPcb 2>&1 | Out-Null
        $ErrorActionPreference = $prevEap
        $drcOk = Test-Path $kicadDrc
        if ($drcOk) {
            try {
                $drcDoc = Get-Content $kicadDrc -Raw | ConvertFrom-Json
                $drcViolations = @($drcDoc.violations).Count
            } catch {
                $drcViolations = $null
            }
        }
    }

    $roundTrip = [PSCustomObject]@{
        board = $smallBoard
        ses_written = (Test-Path $ses)
        ses_reimport_ok = $importOk
        kicad_drc_ran = $drcOk
        kicad_drc_violations = $drcViolations
        freerouting_manifest = if (Test-Path $manifest) { $manifest } else { $null }
    }
}

$report = [PSCustomObject]@{
    spike = "pcbench-conversion-poc"
    generated_at = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
    pcbench_root = $PCBenchRoot
    kicad_cli = $kicadCli
    kicad_python = $kicadPython
    kicad_version = $kicadVersion
    converted_dsn = @($converted | Select-Object -ExpandProperty Name)
    converted_count = $converted.Count
    smoke_loaded_drc = $smokeLoaded
    ground_truth = $groundTruth
    ses_round_trip = $roundTrip
    notes = @(
        "PCBench boards live in PCBs/<board>/{final.json,metadata.json,processed.kicad_pcb,raw.kicad_pcb}.",
        "KiCad 10 CLI has no pcb export specctra; DSN export uses pcbnew.ExportSpecctraDSN via KiCad python.exe.",
        "PCBench boards are KiCad 4-7 era; KiCad 10 may upgrade-on-load during specctra export.",
        "KiCad DSN export omits copper-to-edge clearance (Issue 558).",
        "Local clone contained 1182 board dirs with processed.kicad_pcb + metadata.json (README said 164).",
        "strip_kicad_routing.py must strip children of the kicad_pcb wrapper, not only top-level s-exprs.",
        "Headless pcbnew.ImportSpecctraSES on KiCad 10.0.2 returned false for Freerouting SES."
    )
}
$reportPath = Join-Path $outDir "report.json"
$report | ConvertTo-Json -Depth 8 | Set-Content $reportPath -Encoding UTF8
Write-Output "Wrote $reportPath"
$report | ConvertTo-Json -Depth 8
