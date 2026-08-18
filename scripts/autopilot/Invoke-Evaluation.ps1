param(
    [Parameter(Mandatory = $true)]
    [string]$ExperimentId,
    [string]$RepoRoot = (Join-Path $PSScriptRoot "..\.."),
    [string]$BaselineManifest = (Join-Path $PSScriptRoot "..\benchmark\baselines\baseline-manifest.json"),
    [string]$NoiseFloorPath = (Join-Path $PSScriptRoot "..\benchmark\baselines\noise_floor.json")
)

$ErrorActionPreference = "Stop"
$expDir = Join-Path (Join-Path $RepoRoot "experiments") $ExperimentId
$candidateResults = Join-Path $expDir "g2-results\benchmarks.json"
if (-not (Test-Path $candidateResults)) {
    $candidateResults = Join-Path $expDir "g1-results\benchmarks.json"
}
if (-not (Test-Path $candidateResults)) {
    Write-Error "No benchmark results found for experiment $ExperimentId"
    exit 1
}

$baseline = Get-Content $BaselineManifest -Raw | ConvertFrom-Json
$baselineJson = $baseline.benchmarks_json
if (-not [System.IO.Path]::IsPathRooted($baselineJson)) {
    $baselineJson = Join-Path $RepoRoot $baselineJson
}

$noise = @{ fixtures = @{} }
if (Test-Path $NoiseFloorPath) {
    $noiseObj = Get-Content $NoiseFloorPath -Raw | ConvertFrom-Json
    if ($noiseObj.fixtures) {
        $noiseObj.fixtures.PSObject.Properties | ForEach-Object {
            $noise.fixtures[$_.Name] = $_.Value
        }
    }
}

$candidate = Get-Content $candidateResults -Raw | ConvertFrom-Json
$baselineStore = Get-Content $baselineJson -Raw | ConvertFrom-Json

$pythonScorer = Join-Path $PSScriptRoot "score_experiment.py"
$python = Get-Command python -ErrorAction SilentlyContinue
if ($python -and (Test-Path $pythonScorer) -and (Test-Path $baselineJson)) {
    $verdictPath = Join-Path $expDir "verdict.json"
    & python $pythonScorer `
        --candidate $candidateResults `
        --baseline $baselineJson `
        --noise $NoiseFloorPath `
        --out $verdictPath `
        --experiment-id $ExperimentId
    exit $LASTEXITCODE
}

function Get-LatestRun($runs, $fixturePath) {
    return $runs | Where-Object {
        $_.fixture.relative_path -eq $fixturePath -or $_.fixture.filename -eq (Split-Path $fixturePath -Leaf)
    } | Sort-Object run_at -Descending | Select-Object -First 1
}

$baselineRuns = @($baselineStore.runs)
if ($baselineStore.runs -is [System.Collections.IDictionary]) {
    $baselineRuns = @($baselineStore.runs.Values)
}

$reasons = @()
$deltas = @{}
$accept = $true

$candidateRuns = @($candidate.runs)
if ($candidate.runs -is [System.Collections.IDictionary]) {
    $candidateRuns = @($candidate.runs.Values)
}

$fixtures = $candidateRuns | ForEach-Object { $_.fixture.relative_path } | Select-Object -Unique

foreach ($fx in $fixtures) {
    $cRun = Get-LatestRun $candidateRuns $fx
    $bRun = Get-LatestRun $baselineRuns $fx
    if (-not $cRun) { continue }

    function Get-OrDefault($value, $fallback) {
        if ($null -ne $value) { return $value }
        return $fallback
    }

    $cViol = [int](Get-OrDefault $cRun.drc.final_violations (Get-OrDefault $cRun.quality.clearance_violations 0))
    $cUnr = [int](Get-OrDefault $cRun.drc.final_unrouted (Get-OrDefault $cRun.quality.final_unrouted 0))
    $cScore = [double](Get-OrDefault $cRun.quality.quality_score 0)
    $cTime = [double](Get-OrDefault $cRun.quality.wall_clock_seconds 0)

    $bViol = 0
    $bUnr = 9999
    $bScore = 0
    if ($bRun) {
        $bViol = [int](Get-OrDefault $bRun.drc.final_violations (Get-OrDefault $bRun.quality.clearance_violations 0))
        $bUnr = [int](Get-OrDefault $bRun.drc.final_unrouted (Get-OrDefault $bRun.quality.final_unrouted 9999))
        $bScore = [double](Get-OrDefault $bRun.quality.quality_score 0)
    }

    $noiseBand = 2.0
    if ($noise.fixtures.ContainsKey($fx)) {
        $nf = $noise.fixtures[$fx]
        if ($nf.unrouted_stddev -gt 0) { $noiseBand = [math]::Max($noiseBand, 2 * $nf.unrouted_stddev) }
    }

    $delta = [PSCustomObject]@{
        violations_delta = $cViol - $bViol
        unrouted_delta = $cUnr - $bUnr
        score_delta = $cScore - $bScore
        time_seconds = $cTime
    }
    $deltas[$fx] = $delta

    if ($cViol -gt $bViol) {
        $accept = $false
        $reasons += "REJECT $fx : violations increased ($bViol -> $cViol)"
    }
    if (($cUnr - $bUnr) -gt $noiseBand) {
        $accept = $false
        $reasons += "REJECT $fx : unrouted regression ($bUnr -> $cUnr, band=$noiseBand)"
    }
}

if ($accept -and $reasons.Count -eq 0) {
    $reasons += "ACCEPT: no hard gate violations detected"
}

$verdict = [PSCustomObject]@{
    accept = $accept
    experiment_id = $ExperimentId
    evaluated_at = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
    reasons = $reasons
    deltas = $deltas
}
$verdict | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $expDir "verdict.json") -Encoding UTF8
Write-Output ($verdict | ConvertTo-Json -Depth 4)
if (-not $accept) { exit 1 }
exit 0
