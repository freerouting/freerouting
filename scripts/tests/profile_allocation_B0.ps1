<#
.SYNOPSIS
    Frozen B0 / B0-maze / B0-optN allocation profiles with JFR.

.DESCRIPTION
    Builds freerouting-current-executable.jar, copies it to
    scripts/benchmark/binaries/freerouting-current.jar, and runs the DiscoDongle
    allocation baselines:

      B0        full pipeline, autorouter.max_threads=1, optimizer.max_threads=1
      B0-maze   maze only (fanout/optimizer off), 1 autorouter pass
      B0-optN   full pipeline, autorouter.max_threads=1, optimizer.max_threads=4
      B0-heap   PowerGlove full pipeline confirmation fixture, 1+1 threads
      B0-4L     minisumo 4-layer full pipeline confirmation fixture, 1+1 threads

    JFR (settings=profile, dumponexit, no duration cap) is enabled on every run.
    Logs, SES, result JSON, GC logs, and JFR land under logs/<profile>/ (gitignored).

.PARAMETER Profile
    B0 | B0-maze | B0-optN | B0-heap | B0-4L | All  (default All)

.PARAMETER Repeats
    Repeat count for B0 (default 3). B0-maze and B0-optN default to 1 unless
    this is passed together with an explicit single profile.

.PARAMETER SkipBuild
    Skip Gradle executableJar if the JAR already exists.

.PARAMETER SkipCopy
    Skip copying the JAR into scripts/benchmark/binaries.

.EXAMPLE
    ./scripts/tests/profile_allocation_B0.ps1
    ./scripts/tests/profile_allocation_B0.ps1 -Profile B0-maze
    ./scripts/tests/profile_allocation_B0.ps1 -SkipBuild
#>
param(
    [ValidateSet("B0", "B0-maze", "B0-optN", "B0-heap", "B0-4L", "All")]
    [string] $Profile = "All",
    [int]    $Repeats = 3,
    [switch] $SkipBuild,
    [switch] $SkipCopy
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path "$PSScriptRoot\..\.."
$FixtureRel = switch ($Profile) {
    "B0-heap" { "scripts/benchmark/fixtures/PCBench/PowerGloveUHID_main_board/unrouted.dsn" }
    "B0-4L" { "scripts/benchmark/fixtures/PCBench/kitspace_minisumo_v3/unrouted.dsn" }
    default { "scripts/benchmark/fixtures/PCBench/disco-dongle_DiscoDongle/unrouted.dsn" }
}
$Fixture = Join-Path $RepoRoot ($FixtureRel -replace "/", "\")
$JarBuild = Join-Path $RepoRoot "build\libs\freerouting-current-executable.jar"
$JarCopy = Join-Path $RepoRoot "scripts\benchmark\binaries\freerouting-current.jar"
$HeapMax = "4g"
$HeapMin = "256m"

if (-not (Test-Path $Fixture)) {
    Write-Error "Fixture not found: $Fixture"
    exit 1
}

function Get-GitSha {
    Push-Location $RepoRoot
    try {
        return (git rev-parse --short HEAD).Trim()
    } finally {
        Pop-Location
    }
}

function Get-JavaVersionLine {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $info = & java -version 2>&1 | ForEach-Object { $_.ToString() }
        return (($info | Select-Object -First 1) -replace "`r", "").Trim()
    } finally {
        $ErrorActionPreference = $previous
    }
}

function ConvertTo-JfrPath([string] $WindowsPath) {
    return ($WindowsPath -replace "\\", "/")
}

function Get-Median([double[]] $Values) {
    $list = @($Values | Where-Object { $null -ne $_ })
    if ($list.Count -eq 0) {
        return $null
    }
    $sorted = @($list | Sort-Object)
    $mid = [int][math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) {
        return [math]::Round([double]$sorted[$mid], 2)
    }
    return [math]::Round(([double]$sorted[$mid - 1] + [double]$sorted[$mid]) / 2.0, 2)
}

function Parse-StageMetrics([string] $LogPath) {
    $result = [ordered]@{
        FanWallS        = $null
        FanPeakMb       = $null
        FanAllocGb      = $null
        AutWallS        = $null
        AutPeakMb       = $null
        AutAllocGb      = $null
        OptWallS        = $null
        OptPeakMb       = $null
        OptAllocGb      = $null
        JobPeakMb       = $null
        JobAllocGb      = $null
        Unrouted        = $null
        Violations      = $null
        Score           = $null
        AutorouterThreads = $null
        OptimizerThreads  = $null
        UnknownProperty   = $false
    }
    if (-not (Test-Path $LogPath)) {
        return $result
    }
    $text = Get-Content -Path $LogPath -Raw -ErrorAction SilentlyContinue
    if ([string]::IsNullOrEmpty($text)) {
        return $result
    }
    if ($text -match "Unknown settings property: router\.autorouter\.max_threads") {
        $result.UnknownProperty = $true
    }
    if ($text -match "Pipeline thread limits: autorouter\.max_threads=(\d+), optimizer\.max_threads=(\d+)") {
        $result.AutorouterThreads = [int]$Matches[1]
        $result.OptimizerThreads = [int]$Matches[2]
    }
    if ($text -match "Fanout stage .* completed in ([0-9.]+) seconds,.* ([0-9.]+) GB total allocated, and ([0-9.]+) MB peak heap usage") {
        $result.FanWallS = [double]$Matches[1]
        $result.FanAllocGb = [double]$Matches[2]
        $result.FanPeakMb = [double]$Matches[3]
    }
    if ($text -match "Auto-routing stage .* completed in ([0-9.]+) seconds,.* ([0-9.]+) GB total allocated, and ([0-9.]+) MB peak heap usage") {
        $result.AutWallS = [double]$Matches[1]
        $result.AutAllocGb = [double]$Matches[2]
        $result.AutPeakMb = [double]$Matches[3]
    }
    if ($text -match "Optimization stage .* completed in ([0-9.]+) seconds,.* ([0-9.]+) GB total allocated, and ([0-9.]+) MB peak heap usage") {
        $result.OptWallS = [double]$Matches[1]
        $result.OptAllocGb = [double]$Matches[2]
        $result.OptPeakMb = [double]$Matches[3]
    }
    if ($text -match "the job allocated ([0-9.]+) GB of memory so far") {
        $result.JobAllocGb = [double]$Matches[1]
    }
    if ($text -match "peak heap usage: ([0-9.]+) MB") {
        $result.JobPeakMb = [double]$Matches[1]
    }
    if ($text -match "final score: ([0-9.]+) \((\d+) unrouted and (\d+) violations") {
        $result.Score = [double]$Matches[1]
        $result.Unrouted = [int]$Matches[2]
        $result.Violations = [int]$Matches[3]
    }
    if ($text -match "and ([0-9.]+) MB peak heap usage") {
        $result.JobPeakMb = [double]$Matches[1]
    }
    return $result
}

function Get-JsonProperty($Object, [string] $Name) {
    if ($null -eq $Object) {
        return $null
    }
    $prop = $Object.PSObject.Properties[$Name]
    if ($null -eq $prop) {
        return $null
    }
    return $prop.Value
}

function Merge-ResultJson($Metrics, [string] $JsonPath) {
    if (-not (Test-Path $JsonPath)) {
        return
    }
    $json = Get-Content -Path $JsonPath -Raw | ConvertFrom-Json
    $phases = Get-JsonProperty $json "phases"
    $fan = Get-JsonProperty $phases "fanout"
    $aut = Get-JsonProperty $phases "autorouter"
    $opt = Get-JsonProperty $phases "optimizer"
    $fanDur = Get-JsonProperty $fan "duration_seconds"
    if ($null -ne $fanDur -and [double]$fanDur -gt 0) {
        $Metrics.FanWallS = [double]$fanDur
        $fanPeak = Get-JsonProperty $fan "peak_heap_mb"
        $fanAlloc = Get-JsonProperty $fan "total_allocated_gb"
        if ($null -ne $fanPeak -and [double]$fanPeak -gt 0) { $Metrics.FanPeakMb = [double]$fanPeak }
        if ($null -ne $fanAlloc -and [double]$fanAlloc -gt 0) { $Metrics.FanAllocGb = [double]$fanAlloc }
    }
    $autDur = Get-JsonProperty $aut "duration_seconds"
    if ($null -ne $autDur) {
        $Metrics.AutWallS = [double]$autDur
        $autPeak = Get-JsonProperty $aut "peak_heap_mb"
        $autAlloc = Get-JsonProperty $aut "total_allocated_gb"
        if ($null -ne $autPeak -and [double]$autPeak -gt 0) { $Metrics.AutPeakMb = [double]$autPeak }
        if ($null -ne $autAlloc -and [double]$autAlloc -gt 0) { $Metrics.AutAllocGb = [double]$autAlloc }
    }
    $optDur = Get-JsonProperty $opt "duration_seconds"
    if ($null -ne $optDur -and [double]$optDur -gt 0) {
        $Metrics.OptWallS = [double]$optDur
        $optPeak = Get-JsonProperty $opt "peak_heap_mb"
        $optAlloc = Get-JsonProperty $opt "total_allocated_gb"
        if ($null -ne $optPeak -and [double]$optPeak -gt 0) { $Metrics.OptPeakMb = [double]$optPeak }
        if ($null -ne $optAlloc -and [double]$optAlloc -gt 0) { $Metrics.OptAllocGb = [double]$optAlloc }
    }
    $usage = Get-JsonProperty $json "resource_usage"
    if ($null -ne $usage) {
        $peak = Get-JsonProperty $usage "peak_memory"
        $alloc = Get-JsonProperty $usage "max_memory"
        if ($null -ne $peak) { $Metrics.JobPeakMb = [double]$peak }
        if ($null -ne $alloc) { $Metrics.JobAllocGb = [math]::Round([double]$alloc / 1024.0, 2) }
    }
    $score = Get-JsonProperty $json "normalized_score"
    if ($null -ne $score) { $Metrics.Score = [double]$score }
    $stats = Get-JsonProperty $json "board_statistics"
    $connections = Get-JsonProperty $stats "connections"
    $violations = Get-JsonProperty $stats "clearance_violations"
    $incomplete = Get-JsonProperty $connections "incomplete_count"
    $vioCount = Get-JsonProperty $violations "total_count"
    if ($null -ne $incomplete) { $Metrics.Unrouted = [int]$incomplete }
    if ($null -ne $vioCount) { $Metrics.Violations = [int]$vioCount }
}

function Write-JfrTopTypes([string] $JfrPath, [string] $OutPath, [int] $TopN = 15) {
    if (-not (Test-Path $JfrPath)) {
        Set-Content -Path $OutPath -Value "JFR file missing: $JfrPath"
        return
    }
    $previousEa = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $print = & jfr print --events jdk.ObjectAllocationSample --stack-depth 0 $JfrPath 2>&1 | Out-String
    } finally {
        $ErrorActionPreference = $previousEa
    }
    $counts = @{}
    foreach ($line in ($print -split "`r?`n")) {
        if ($line -match "objectClass\s*=\s*([A-Za-z0-9_\$\.]+)") {
            $name = $Matches[1]
            if ($counts.ContainsKey($name)) {
                $counts[$name]++
            } else {
                $counts[$name] = 1
            }
        }
    }
    $lines = @("# Top $TopN jdk.ObjectAllocationSample types", "")
    if ($counts.Count -eq 0) {
        $lines += "_No ObjectAllocationSample events. Check that settings=profile was used._"
    } else {
        $ranked = $counts.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First $TopN
        $total = ($counts.Values | Measure-Object -Sum).Sum
        $lines += "| Rank | Type | Samples | Share |"
        $lines += "| ---: | :--- | ------: | ----: |"
        $i = 1
        foreach ($row in $ranked) {
            $share = if ($total -gt 0) { [math]::Round(100.0 * $row.Value / $total, 1) } else { 0 }
            $lines += "| $i | ``$($row.Key)`` | $($row.Value) | $share% |"
            $i++
        }
        $lines += ""
        $lines += "Sampled events counted: $total"
    }
    Set-Content -Path $OutPath -Value ($lines -join "`n")
}

function Invoke-B0Profile {
    param(
        [string] $Name,
        [int] $RunCount,
        [int] $AutorouterThreads,
        [int] $OptimizerThreads,
        [bool] $FanoutEnabled,
        [bool] $OptimizerEnabled,
        [int] $MaxPasses = 0
    )

    $outDir = Join-Path $RepoRoot "logs\$Name"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $sha = Get-GitSha
    $javaVer = Get-JavaVersionLine
    $rows = @()

    Write-Host "`n==================================================" -ForegroundColor Cyan
    Write-Host "  Profile $Name  repeats=$RunCount  SHA=$sha" -ForegroundColor Cyan
    Write-Host "==================================================" -ForegroundColor Cyan

    for ($n = 1; $n -le $RunCount; $n++) {
        $runDir = Join-Path $outDir ("run-{0:00}" -f $n)
        New-Item -ItemType Directory -Force -Path $runDir | Out-Null
        $routeLog = Join-Path $runDir "route.log"
        $gcLog = Join-Path $runDir "gc.log"
        $jfrFile = Join-Path $runDir "profile.jfr"
        $sesFile = Join-Path $runDir "out.ses"
        $jsonFile = Join-Path $runDir "result.json"
        $jfrArg = ConvertTo-JfrPath $jfrFile
        $gcArg = ConvertTo-JfrPath $gcLog

        $frArgs = @(
            "-Xms$HeapMin",
            "-Xmx$HeapMax",
            "-XX:StartFlightRecording=dumponexit=true,filename=$jfrArg,settings=profile,maxsize=512m",
            "-Xlog:gc*:file=$gcArg`:time,uptime:filecount=3,filesize=20M",
            "-jar", $JarBuild,
            "-de", $Fixture,
            "-do", $sesFile,
            "--gui.enabled=false",
            "--api_server.enabled=false",
            "--mcp_server.enabled=false",
            "--router.fanout.enabled=$($FanoutEnabled.ToString().ToLowerInvariant())",
            "--router.optimizer.enabled=$($OptimizerEnabled.ToString().ToLowerInvariant())",
            "--router.autorouter.max_threads=$AutorouterThreads",
            "--router.optimizer.max_threads=$OptimizerThreads",
            "--router.result_json=$jsonFile"
        )
        if ($MaxPasses -gt 0) {
            $frArgs += "--router.autorouter.max_passes=$MaxPasses"
        }

        Write-Host "`n--- $Name run $n / $RunCount ---" -ForegroundColor Yellow
        $started = Get-Date
        Push-Location $RepoRoot
        $previousEa = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $stderrFile = "$routeLog.stderr"
            & java @frArgs 1>$routeLog 2>$stderrFile
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousEa
            Pop-Location
        }
        $elapsed = ((Get-Date) - $started).TotalSeconds
        if ($null -eq $exitCode) { $exitCode = -1 }

        if (Test-Path "$routeLog.stderr") {
            $stderr = Get-Content "$routeLog.stderr" -ErrorAction SilentlyContinue
            if ($stderr) {
                Add-Content -Path $routeLog -Value "`n--- STDERR ---"
                Add-Content -Path $routeLog -Value $stderr
            }
            Remove-Item "$routeLog.stderr" -ErrorAction SilentlyContinue
        }

        $metrics = Parse-StageMetrics $routeLog
        Merge-ResultJson $metrics $jsonFile
        Write-JfrTopTypes -JfrPath $jfrFile -OutPath (Join-Path $runDir "jfr-top-types.md")
        $row = [pscustomobject]@{
            Profile = $Name
            N = $n
            Exit = $exitCode
            WallS = [math]::Round($elapsed, 2)
            FanS = $metrics.FanWallS
            AutS = $metrics.AutWallS
            OptS = $metrics.OptWallS
            FanPeak = $metrics.FanPeakMb
            AutPeak = $metrics.AutPeakMb
            OptPeak = $metrics.OptPeakMb
            JobPeak = $metrics.JobPeakMb
            FanAlloc = $metrics.FanAllocGb
            AutAlloc = $metrics.AutAllocGb
            OptAlloc = $metrics.OptAllocGb
            JobAlloc = $metrics.JobAllocGb
            Unr = $metrics.Unrouted
            Vio = $metrics.Violations
            Score = $metrics.Score
            AutT = $metrics.AutorouterThreads
            OptT = $metrics.OptimizerThreads
            UnknownFlag = $metrics.UnknownProperty
        }
        $rows += $row
        Write-Host ("  exit={0} wall={1}s jobPeak={2} MB autT={3} optT={4} score={5} unknownFlag={6}" -f `
            $row.Exit, $row.WallS, $row.JobPeak, $row.AutT, $row.OptT, $row.Score, $row.UnknownFlag)
        if ($row.UnknownFlag) {
            Write-Host "  ERROR: --router.autorouter.max_threads was not accepted." -ForegroundColor Red
        }
        if ($null -ne $row.AutT -and $row.AutT -ne $AutorouterThreads) {
            Write-Host "  ERROR: autorouter threads $($row.AutT) != requested $AutorouterThreads" -ForegroundColor Red
        }
        if ($OptimizerEnabled -and $null -ne $row.OptT -and $row.OptT -ne $OptimizerThreads) {
            Write-Host "  ERROR: optimizer threads $($row.OptT) != requested $OptimizerThreads" -ForegroundColor Red
        }
    }

    $md = @()
    $md += "# $Name allocation profile"
    $md += ""
    $md += "- git SHA: $sha"
    $md += "- Java: $javaVer"
    $md += "- Fixture: ``$FixtureRel``"
    $md += "- JAR: ``build/libs/freerouting-current-executable.jar``"
    $md += "- Threads: autorouter=$AutorouterThreads optimizer=$OptimizerThreads"
    $md += "- Fanout=$FanoutEnabled optimizer=$OptimizerEnabled max_passes=$(if ($MaxPasses -gt 0) { $MaxPasses } else { 'unlimited' })"
    $md += "- JVM: -Xms$HeapMin -Xmx$HeapMax JFR settings=profile dumponexit"
    $md += ""
    $md += "| n | exit | wall s | fan s | aut s | opt s | fan peak | aut peak | opt peak | job peak | fan GB | aut GB | opt GB | job GB | unr | vio | score | aut T | opt T |"
    $md += "| -: | ---: | -----: | ----: | ----: | ----: | -------: | -------: | -------: | -------: | -----: | -----: | -----: | -----: | --: | --: | ----: | ----: | ----: |"
    foreach ($row in $rows) {
        $md += ("| {0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} | {8} | {9} | {10} | {11} | {12} | {13} | {14} | {15} | {16} | {17} | {18} |" -f `
            $row.N, $row.Exit, $row.WallS, $row.FanS, $row.AutS, $row.OptS, `
            $row.FanPeak, $row.AutPeak, $row.OptPeak, $row.JobPeak, `
            $row.FanAlloc, $row.AutAlloc, $row.OptAlloc, $row.JobAlloc, `
            $row.Unr, $row.Vio, $row.Score, $row.AutT, $row.OptT)
    }

    $jobPeaks = @($rows | Where-Object { $null -ne $_.JobPeak } | ForEach-Object { [double]$_.JobPeak })
    $walls = @($rows | ForEach-Object { [double]$_.WallS })
    $md += ""
    $md += "Median job peak heap: $(Get-Median $jobPeaks) MB"
    $md += "Median wall: $(Get-Median $walls) s"
    $metricsPath = Join-Path $outDir "metrics.md"
    Set-Content -Path $metricsPath -Value ($md -join "`n")
    Write-Host "`n  Wrote $metricsPath" -ForegroundColor Green
    return $rows
}

if (-not $SkipBuild) {
    Write-Host "`nBuilding executableJar ..." -ForegroundColor Cyan
    Push-Location $RepoRoot
    try {
        & .\gradlew.bat executableJar
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Gradle executableJar failed (exit $LASTEXITCODE)."
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $JarBuild)) {
    Write-Error "JAR not found: $JarBuild"
    exit 1
}

if (-not $SkipCopy) {
    New-Item -ItemType Directory -Force -Path (Split-Path $JarCopy) | Out-Null
    Copy-Item -Force $JarBuild $JarCopy
    Write-Host "Copied JAR to $JarCopy" -ForegroundColor Green
}

$runB0 = $Profile -eq "All" -or $Profile -eq "B0"
$runMaze = $Profile -eq "All" -or $Profile -eq "B0-maze"
$runOptN = $Profile -eq "All" -or $Profile -eq "B0-optN"
$runConfirmation = $Profile -eq "B0-heap" -or $Profile -eq "B0-4L"
$mazeRepeats = if ($Profile -eq "B0-maze") { $Repeats } else { 1 }
$optRepeats = if ($Profile -eq "B0-optN") { $Repeats } else { 1 }
$b0Repeats = $Repeats

if ($runMaze) {
    Invoke-B0Profile -Name "B0-maze" -RunCount $mazeRepeats -AutorouterThreads 1 -OptimizerThreads 1 `
        -FanoutEnabled $false -OptimizerEnabled $false -MaxPasses 1 | Out-Null
}
if ($runB0) {
    Invoke-B0Profile -Name "B0" -RunCount $b0Repeats -AutorouterThreads 1 -OptimizerThreads 1 `
        -FanoutEnabled $true -OptimizerEnabled $true | Out-Null
}
if ($runOptN) {
    Invoke-B0Profile -Name "B0-optN" -RunCount $optRepeats -AutorouterThreads 1 -OptimizerThreads 4 `
        -FanoutEnabled $true -OptimizerEnabled $true | Out-Null
}
if ($runConfirmation) {
    Invoke-B0Profile -Name $Profile -RunCount $Repeats -AutorouterThreads 1 -OptimizerThreads 1 `
        -FanoutEnabled $true -OptimizerEnabled $true | Out-Null
}

Write-Host "`nAll requested profiles finished." -ForegroundColor Green
