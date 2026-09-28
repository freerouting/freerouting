param(
    [string]$BinariesDir = "$PSScriptRoot\binaries",
    [string]$FixturesDir = "$PSScriptRoot\fixtures",
    [string]$ResultsDir = "$PSScriptRoot\results",
    [string]$OutputPath = "$PSScriptRoot\baselines\noise_floor.json",
    [int]$RunsPerFixture = 5,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$libDir = Join-Path $PSScriptRoot "lib"
Get-ChildItem $libDir -Filter "*.ps1" | ForEach-Object { . $_.FullName }

$metadataPath = Join-Path $FixturesDir "metadata.yaml"
$fixtureList = @(
    "DAC2020_boards/DAC2020_bm02.dsn",
    "DAC2020_boards/DAC2020_bm07.dsn",
    "DAC2020_boards/DAC2020_bm08.dsn",
    "KiCad_10_demos/ecc83-pp.dsn",
    "KiCad_10_demos/sonde xilinx.dsn",
    "DAC2020_boards/DAC2020_bm01.dsn"
)

$binary = Get-ChildItem $BinariesDir -Filter "*current*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $binary) {
    Write-Error "Build freerouting-current.jar first: gradlew.bat executableJar"
    exit 1
}

Write-Output "Measuring noise floor with $($binary.Name), $RunsPerFixture runs per fixture..."

$fixtureStats = @{}

foreach ($relPath in $fixtureList) {
    $fixture = Get-ChildItem $FixturesDir -Recurse -Filter ([System.IO.Path]::GetFileName($relPath)) |
        Where-Object { $_.FullName -replace '\\', '/' -match [regex]::Escape($relPath.Replace('/', '\\')) -or $_.FullName -like "*$([System.IO.Path]::GetFileName($relPath))" } |
        Select-Object -First 1
    if (-not $fixture) {
        $parts = $relPath -split '/'
        $fixture = Join-Path (Join-Path $FixturesDir $parts[0]) $parts[1]
        if (-not (Test-Path $fixture)) {
            Write-Warning "Fixture not found: $relPath"
            continue
        }
        $fixture = Get-Item $fixture
    }

    $scores = @()
    $unrouted = @()
    $violations = @()
    $times = @()

    for ($i = 1; $i -le $RunsPerFixture; $i++) {
        Write-Output "  $relPath run $i/$RunsPerFixture"
        & "$PSScriptRoot\run-benchmarks.ps1" `
            -BinariesDir $BinariesDir `
            -FixturesDir $FixturesDir `
            -ResultsDir $ResultsDir `
            -FilterFixture $fixture.Name `
            -FilterBinary $binary.Name `
            -MaxPasses 20 `
            -MaxTime "00:10:00" `
            -Force:$Force | Out-Null

        $store = Load-BenchmarksJson (Join-Path $ResultsDir "benchmarks.json")
        $latest = $store.Cache.Values |
            Where-Object { $_.fixture.relative_path -eq $relPath -or $_.fixture.filename -eq $fixture.Name } |
            Sort-Object run_at -Descending |
            Select-Object -First 1
        if ($latest) {
            if ($latest.quality.quality_score -ne $null) { $scores += [double]$latest.quality.quality_score }
            if ($latest.quality.final_unrouted -ne $null) { $unrouted += [int]$latest.quality.final_unrouted }
            if ($latest.drc.final_violations -ne $null) { $violations += [int]$latest.drc.final_violations }
            elseif ($latest.quality.clearance_violations -ne $null) { $violations += [int]$latest.quality.clearance_violations }
            if ($latest.quality.wall_clock_seconds -ne $null) { $times += [double]$latest.quality.wall_clock_seconds }
        }
    }

    function Get-StdDev([double[]]$values) {
        if ($values.Count -lt 2) { return 0.0 }
        $mean = ($values | Measure-Object -Average).Average
        $sumSq = 0.0
        foreach ($v in $values) { $sumSq += [math]::Pow($v - $mean, 2) }
        return [math]::Sqrt($sumSq / ($values.Count - 1))
    }

    $fixtureStats[$relPath] = [PSCustomObject]@{
        runs = $RunsPerFixture
        score_stddev = [math]::Round((Get-StdDev $scores), 4)
        unrouted_stddev = [math]::Round((Get-StdDev ($unrouted | ForEach-Object { [double]$_ })), 4)
        violations_stddev = [math]::Round((Get-StdDev ($violations | ForEach-Object { [double]$_ })), 4)
        time_stddev_seconds = [math]::Round((Get-StdDev $times), 2)
        score_median = if ($scores.Count -gt 0) { ($scores | Sort-Object)[([math]::Floor($scores.Count / 2))] } else { $null }
        unrouted_median = if ($unrouted.Count -gt 0) { ($unrouted | Sort-Object)[([math]::Floor($unrouted.Count / 2))] } else { $null }
        violations_median = if ($violations.Count -gt 0) { ($violations | Sort-Object)[([math]::Floor($violations.Count / 2))] } else { $null }
    }
}

$out = [PSCustomObject]@{
    schema_version = 1
    generated_at = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
    runs_per_fixture = $RunsPerFixture
    binary = $binary.Name
    fixtures = $fixtureStats
}
$out | ConvertTo-Json -Depth 6 | Set-Content $OutputPath -Encoding UTF8
Write-Output "Wrote noise floor to $OutputPath"
