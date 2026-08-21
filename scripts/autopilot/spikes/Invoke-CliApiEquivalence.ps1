param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path,
    [string]$JarPath = "",
    [string]$FixtureDir = "",
    [int]$ApiPort = 37900
)

$ErrorActionPreference = "Stop"
if (-not $FixtureDir) {
    $FixtureDir = Join-Path $RepoRoot "scripts\benchmark\fixtures"
}
$outDir = Join-Path $RepoRoot "experiments\spikes\cli-api-equiv"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $JarPath) {
    $JarPath = Join-Path $RepoRoot "build\libs\freerouting-current-executable.jar"
}
if (-not (Test-Path $JarPath)) {
    Push-Location $RepoRoot
    & .\gradlew.bat executableJar --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "executableJar failed" }
    Pop-Location
    $JarPath = Get-ChildItem (Join-Path $RepoRoot "build\libs") -Filter "*executable*.jar" |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}

$fixtures = @(
    "DAC2020_boards/DAC2020_bm01.dsn",
    "KiCad_10_demos/ecc83-pp.dsn"
)

$profileId = "00000000-0000-0000-0000-000000000001"
$hostHeader = "AutopilotSpike/1.0"
$baseUrl = "http://127.0.0.1:$ApiPort/v1"
$apiLog = Join-Path $outDir "api-server.log"

$apiProc = Start-Process -FilePath "java" -ArgumentList @(
    "-jar", $JarPath,
    "--gui.enabled=false",
    "--mcp_server.enabled=false",
    "--api_server.enabled=true",
    "--api_server.authentication.enabled=false",
    "--api_server.http_allowed=true",
    "--api_server.endpoints=http://127.0.0.1:$ApiPort"
) -NoNewWindow -PassThru -RedirectStandardOutput $apiLog -RedirectStandardError "$apiLog.err"

function Invoke-FrApi {
    param([string]$Method, [string]$Url, [string]$Body = $null, [string]$ContentType = "application/json")
    $headers = @{
        "Freerouting-Profile-ID" = $profileId
        "Freerouting-Environment-Host" = $hostHeader
    }
    $params = @{
        Method = $Method
        Uri = $Url
        Headers = $headers
        TimeoutSec = 30
    }
    if ($Body) {
        $params.Body = $Body
        $params.ContentType = $ContentType
    }
    return Invoke-RestMethod @params
}

try {
    $ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        try {
            Invoke-RestMethod -Uri "$baseUrl/system/status" -TimeoutSec 2 | Out-Null
            $ready = $true
            break
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    if (-not $ready) {
        throw "API server did not become ready on $baseUrl"
    }

    $results = @()
    foreach ($rel in $fixtures) {
        $parts = $rel -split '/'
        $dsn = Join-Path (Join-Path $FixtureDir $parts[0]) $parts[1]
        if (-not (Test-Path $dsn)) { throw "Fixture missing: $dsn" }
        $base = [System.IO.Path]::GetFileNameWithoutExtension($dsn)
        $ses = Join-Path $outDir "$base-cli.ses"
        $manifest = Join-Path $outDir "$base-cli-result.json"

        $cliSw = [System.Diagnostics.Stopwatch]::StartNew()
        & java -jar $JarPath --gui.enabled=false --api_server.enabled=false --mcp_server.enabled=false `
            -de $dsn -do $ses `
            --router.max_passes=5 --router.job_timeout="00:05:00" `
            --router.result_json="$manifest"
        $cliSw.Stop()

        $cli = $null
        if (Test-Path $manifest) {
            $cli = Get-Content $manifest -Raw | ConvertFrom-Json
        }

        $bytes = [System.IO.File]::ReadAllBytes($dsn)
        $b64 = [Convert]::ToBase64String($bytes)
        $fileName = [System.IO.Path]::GetFileName($dsn)

        $apiSw = [System.Diagnostics.Stopwatch]::StartNew()
        $session = Invoke-FrApi -Method POST -Url "$baseUrl/sessions/create"
        $job = Invoke-FrApi -Method POST -Url "$baseUrl/jobs/enqueue" -Body (@{
            session_id = $session.id
            name = $base
        } | ConvertTo-Json)
        Invoke-FrApi -Method POST -Url "$baseUrl/jobs/$($job.id)/settings" -Body (@{
            max_passes = 5
            job_timeout = "00:05:00"
        } | ConvertTo-Json) | Out-Null
        Invoke-FrApi -Method POST -Url "$baseUrl/jobs/$($job.id)/input" -Body (@{
            filename = $fileName
            data = $b64
        } | ConvertTo-Json) | Out-Null
        Invoke-FrApi -Method PUT -Url "$baseUrl/jobs/$($job.id)/start" | Out-Null

        $jobDetails = $null
        $deadline = (Get-Date).AddMinutes(6)
        do {
            Start-Sleep -Seconds 2
            $jobDetails = Invoke-FrApi -Method GET -Url "$baseUrl/jobs/$($job.id)"
        } while ($jobDetails.state -notin @("COMPLETED", "TIMED_OUT", "CANCELLED", "TERMINATED") -and (Get-Date) -lt $deadline)
        $apiSw.Stop()

        $apiUnrouted = $null
        $apiViol = $null
        if ($jobDetails.output -and $jobDetails.output.statistics) {
            $apiUnrouted = $jobDetails.output.statistics.connections.incomplete_count
            $apiViol = $jobDetails.output.statistics.clearance_violations.total_count
        } elseif ($jobDetails.input -and $jobDetails.input.statistics) {
            $apiUnrouted = $jobDetails.input.statistics.connections.incomplete_count
            $apiViol = $jobDetails.input.statistics.clearance_violations.total_count
        }

        $cliUnrouted = $null
        $cliViol = $null
        $cliScore = $null
        if ($cli) {
            $cliUnrouted = $cli.board_statistics.connections.incomplete_count
            $cliViol = $cli.board_statistics.clearance_violations.total_count
            $cliScore = $cli.normalized_score
        }

        # GET /jobs/{id} output.statistics is rebuilt from SES text (no incompletes).
        # Pull live metrics from job logs: "final score: 892.29 (21 unrouted and 0 violations)"
        $apiLogs = Invoke-FrApi -Method GET -Url "$baseUrl/jobs/$($job.id)/logs"
        $logBlob = ($apiLogs | ConvertTo-Json -Depth 8)
        $scoreMatch = [regex]::Match(
            $logBlob,
            "final score:\s*([\d.]+)\s*\((\d+) unrouted and (\d+) violation"
        )
        $apiScore = $null
        if ($scoreMatch.Success) {
            $apiScore = [double]$scoreMatch.Groups[1].Value
            $apiUnrouted = [int]$scoreMatch.Groups[2].Value
            $apiViol = [int]$scoreMatch.Groups[3].Value
        }

        $jobDump = Join-Path $outDir "$base-api-job.json"
        $jobDetails | ConvertTo-Json -Depth 8 | Set-Content $jobDump -Encoding UTF8

        $results += [PSCustomObject]@{
            fixture = $rel
            cli_wall_seconds = [math]::Round($cliSw.Elapsed.TotalSeconds, 2)
            api_wall_seconds = [math]::Round($apiSw.Elapsed.TotalSeconds, 2)
            cli_overhead_vs_api_seconds = [math]::Round($cliSw.Elapsed.TotalSeconds - $apiSw.Elapsed.TotalSeconds, 2)
            cli_unrouted = $cliUnrouted
            api_unrouted = $apiUnrouted
            cli_violations = $cliViol
            api_violations = $apiViol
            cli_score = $cliScore
            api_score = $apiScore
            api_state = $jobDetails.state
            metrics_match = ($cliUnrouted -eq $apiUnrouted) -and ($cliViol -eq $apiViol)
            note = "API GET job output.statistics.connections.incomplete_count is null after SES setData; metrics taken from job logs."
        }
    }
} finally {
    if ($apiProc -and -not $apiProc.HasExited) {
        Stop-Process -Id $apiProc.Id -Force -ErrorAction SilentlyContinue
    }
}

$report = [PSCustomObject]@{
    spike = "cli-api-equivalence"
    generated_at = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
    jar = $JarPath
    api_base = $baseUrl
    results = $results
}
$reportPath = Join-Path $outDir "report.json"
$report | ConvertTo-Json -Depth 6 | Set-Content $reportPath -Encoding UTF8
Write-Output "Wrote $reportPath"
$results | Format-Table -AutoSize
