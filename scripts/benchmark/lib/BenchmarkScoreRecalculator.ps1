function Invoke-BenchmarkScoreRecalculator {
    param(
        [string]$JsonPath,
        [string]$BinariesDir = (Join-Path $PSScriptRoot "..\binaries"),
        [string]$ScoresPath = $null,
        [switch]$Force
    )

    if (-not $ScoresPath) {
        $ScoresPath = Join-Path (Split-Path -Parent $JsonPath) "normalized-scores.json"
    }

    if (-not (Test-Path $JsonPath)) {
        Write-Warning "Benchmark JSON file not found: $JsonPath"
        return @{}
    }

    $jarPath = Join-Path $BinariesDir "freerouting-current.jar"
    if (-not (Test-Path $jarPath)) {
        Write-Warning "Freerouting current jar not found: $jarPath"
        return @{}
    }

    $needsRecalculation = $Force.IsPresent
    if (-not $needsRecalculation) {
        if (-not (Test-Path $ScoresPath)) {
            $needsRecalculation = $true
        } else {
            $jsonTime = (Get-Item $JsonPath).LastWriteTimeUtc
            $scoresTime = (Get-Item $ScoresPath).LastWriteTimeUtc
            if ($jsonTime -gt $scoresTime) {
                $needsRecalculation = $true
            }
        }
    }

    if ($needsRecalculation) {
        Write-Output "Recalculating normalized V2 scores in bulk via Freerouting app..."
        $process = Start-Process -FilePath "java" -ArgumentList @(
            "-jar",
            $jarPath,
            "--calculate-benchmark-scores",
            "--input=$JsonPath",
            "--output=$ScoresPath"
        ) -NoNewWindow -Wait -PassThru

        if ($process.ExitCode -ne 0) {
            Write-Error "Score recalculation failed with exit code $($process.ExitCode)"
            return @{}
        }
    }

    if (Test-Path $ScoresPath) {
        try {
            $rawScores = Get-Content -LiteralPath $ScoresPath -Raw | ConvertFrom-Json
            $scoresMap = @{}
            foreach ($prop in $rawScores.PSObject.Properties) {
                $scoresMap[$prop.Name] = $prop.Value
            }
            return $scoresMap
        } catch {
            Write-Warning "Failed to parse normalized scores: $_"
            return @{}
        }
    }

    return @{}
}
