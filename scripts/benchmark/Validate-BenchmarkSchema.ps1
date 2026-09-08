param(
    [string]$JsonPath = "$PSScriptRoot\results\benchmarks.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Test-PropertyPath {
    param(
        $Object,
        [string]$Path
    )

    $current = $Object
    foreach ($segment in ($Path -split "\.")) {
        if ($null -eq $current -or
            -not ($current.PSObject.Properties.Name -contains $segment)) {
            return $false
        }
        $current = $current.$segment
    }
    return $true
}

if (-not (Test-Path $JsonPath)) {
    throw "Benchmark JSON was not found: $JsonPath"
}

$data = Get-Content $JsonPath -Raw | ConvertFrom-Json
$runs = if (Test-PropertyPath $data "runs") { @($data.runs) } else { @() }
if ($runs.Count -eq 0) {
    Write-Warning "No benchmark runs found in $JsonPath; schema validation skipped."
    exit 0
}

$requiredPaths = @(
    "cache_key",
    "run_at",
    "system",
    "system.cpu_score",
    "binary",
    "binary.version_label",
    "fixture",
    "fixture.relative_path",
    "settings",
    "phases",
    "quality",
    "bounds",
    "bounds.board_area_mm2",
    "bounds.complexity_c",
    "bounds.min_trace_length_mm",
    "bounds.min_via_count",
    "bounds.min_bend_count",
    "drc",
    "exit",
    "schema_version"
)

$errors = [System.Collections.Generic.List[string]]::new()
foreach ($run in $runs) {
    $identity =
        if (Test-PropertyPath $run "fixture.relative_path") {
            [string]$run.fixture.relative_path
        } else {
            "<unknown fixture>"
        }
    foreach ($path in $requiredPaths) {
        if (-not (Test-PropertyPath $run $path)) {
            [void]$errors.Add("$identity is missing '$path'")
        }
    }
}

$currentRuns = @($runs | Where-Object {
        (Test-PropertyPath $_ "binary") -and (
            ((Test-PropertyPath $_ "binary.filename") -and
                ([string]$_.binary.filename -match "(?i)current")) -or
            ((Test-PropertyPath $_ "binary.version_label") -and
                ([string]$_.binary.version_label -notmatch "(?i)(1[._-]?9|v190)"))
        )
    })
$v19Runs = @($runs | Where-Object {
        (Test-PropertyPath $_ "binary") -and (
            ((Test-PropertyPath $_ "binary.filename") -and
                ([string]$_.binary.filename -match "(?i)(1[._-]?9|v190)")) -or
            ((Test-PropertyPath $_ "binary.version_label") -and
                ([string]$_.binary.version_label -match "(?i)(1[._-]?9|v190)"))
        )
    })

$currentByFixture = @{}
foreach ($run in $currentRuns) {
    if (Test-PropertyPath $run "fixture.relative_path") {
        $currentByFixture[[string]$run.fixture.relative_path] = $run
    }
}

$parityPaths = @(
    "system.cpu_score",
    "binary.version_label",
    "fixture.relative_path",
    "bounds.board_area_mm2",
    "bounds.complexity_c",
    "bounds.min_trace_length_mm",
    "bounds.min_via_count",
    "bounds.min_bend_count"
)
$pairedCount = 0
foreach ($run in $v19Runs) {
    if (-not (Test-PropertyPath $run "fixture.relative_path")) {
        continue
    }
    $fixturePath = [string]$run.fixture.relative_path
    if (-not $currentByFixture.ContainsKey($fixturePath)) {
        continue
    }
    $pairedCount++
    $currentRun = $currentByFixture[$fixturePath]
    foreach ($path in $parityPaths) {
        if ((Test-PropertyPath $currentRun $path) -ne (Test-PropertyPath $run $path)) {
            [void]$errors.Add(
                "$fixturePath has unequal current/v1.9 presence for '$path'")
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

if ($v19Runs.Count -gt 0 -and $pairedCount -eq 0) {
    Write-Warning "No current/v1.9 fixture pairs were found; only per-run schema was validated."
}

$message = "Benchmark schema valid: {0} runs, {1} current/v1.9 fixture pairs." `
    -f $runs.Count, $pairedCount
Write-Output $message
