param(
    [string]  $BinariesDir    = "$PSScriptRoot\binaries",
    [string]  $FixturesDir    = "$PSScriptRoot\fixtures",
    [string]  $ResultsDir     = "$PSScriptRoot\results",
    [string]  $LogsDir        = "$PSScriptRoot\logs",
    [string]  $OutputsDir     = "$PSScriptRoot\outputs",
    [string]  $WebsiteHtml    = "$PSScriptRoot\..\..\website\benchmarks.html",
    [int]     $MaxPasses      = 500,
    [string]  $MaxTime        = "00:30:00",
    [int]     $MaxThreads     = 1,
    [string]  $HeapMax        = "8g",
    [string]  $LogLevel       = "INFO",
    [bool]    $FanoutEnabled  = $true,
    [bool]    $RouterEnabled  = $true,
    [switch]  $Force,
    [switch]  $ReportOnly,
    [string]  $FilterFixture  = "*",
    [string]  $FilterBinary   = "*"
)

# 1. Force Pager cat
$env:PAGER = "cat"

# 2. Imports all modular scripts
$libDir = Join-Path $PSScriptRoot "lib"
Get-ChildItem $libDir -Filter "*.ps1" | ForEach-Object {
    Write-Output "Importing module: $($_.Name)"
    . $_.FullName
}

# Ensure folders exist
$null = New-Item -ItemType Directory -Force -Path $ResultsDir -ErrorAction SilentlyContinue
$null = New-Item -ItemType Directory -Force -Path $LogsDir -ErrorAction SilentlyContinue
$null = New-Item -ItemType Directory -Force -Path $OutputsDir -ErrorAction SilentlyContinue

$JsonPath = Join-Path $ResultsDir "benchmarks.json"
$MdPath = Join-Path $ResultsDir "benchmarks.md"
$CsvPath = Join-Path $ResultsDir "benchmarks.csv"
$ChartDataPath = Join-Path $ResultsDir "benchmarks-chart-data.json"

# Load current cache
$store = Load-BenchmarksJson $JsonPath
$rawJson = $store.RawData
$cache = $store.Cache

if ($ReportOnly) {
    Write-Output "Report-only mode. Generating reports from cached data..."
    Export-MarkdownReport $cache $MdPath $CsvPath $ChartDataPath
    Update-BenchmarksHtml $cache $WebsiteHtml
    Write-Output "Done!"
    exit 0
}

# Discover files
$binaries = Get-ChildItem $BinariesDir -Filter "*.jar" | Where-Object { $_.Name -like $FilterBinary }
$fixtures = Get-ChildItem $FixturesDir -Recurse -Filter "*.dsn" | Where-Object { $_.Name -like $FilterFixture }

if ($binaries.Count -eq 0) {
    Write-Error "No Freerouting binaries found in: $BinariesDir"
    exit 1
}
if ($fixtures.Count -eq 0) {
    Write-Error "No DSN fixtures found in: $FixturesDir"
    exit 1
}

# Identify current binary for DRC check (use current jar)
$binaryCurrent = $binaries | Where-Object { $_.Name -match "current" } | Select-Object -First 1
if (-not $binaryCurrent) {
    $binaryCurrent = $binaries | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    Write-Warning "freerouting-current.jar not found. Using $($binaryCurrent.Name) for post-route DRC checks."
}

# Gathers system info
$sysInfo = Get-SystemInfo
Write-Output "System: $($sysInfo.cpu_name) ($($sysInfo.cpu_physical_cores) Cores, $($sysInfo.total_ram_gb) GB RAM)"

# Settings object
$settingsObj = [PSCustomObject]@{
    max_passes        = $MaxPasses
    max_time          = $MaxTime
    max_threads       = $MaxThreads
    heap_max          = $HeapMax
    log_level         = $LogLevel
    fanout_enabled    = $FanoutEnabled
    router_enabled    = $RouterEnabled
    optimizer_enabled = $true
    fanout_timeout    = "00:05:00"
    optimizer_timeout = "00:05:00"
}

# CLI Probe Cache
$cliSupportCache = @{}

$totalCombinations = $binaries.Count * $fixtures.Count
$runIdx = 0

Write-Output "Starting benchmark runs ($totalCombinations combinations)..."

# Pre-calculate which runs will actually be executed and their estimates
$fixtureHistory = @{}
foreach ($key in $cache.Keys) {
    $run = $cache[$key]
    if ($run.fixture -and $run.fixture.relative_path) {
        $fPath = $run.fixture.relative_path
        if (-not $fixtureHistory.ContainsKey($fPath)) {
            $fixtureHistory[$fPath] = @()
        }
        $fixtureHistory[$fPath] += $run
    }
}

$fixtureEstimates = @{}
foreach ($fixture in $fixtures) {
    $fGroup = Split-Path (Split-Path $fixture.FullName -Parent) -Leaf
    $fPath = "$fGroup/$($fixture.Name)"
    
    $estimate = 60.0
    if ($fixtureHistory.ContainsKey($fPath)) {
        $latestRuns = $fixtureHistory[$fPath] | Sort-Object -Property run_at -Descending
        $bestEstimateRun = $latestRuns | Select-Object -First 1
        if ($bestEstimateRun.quality -and $bestEstimateRun.quality.wall_clock_seconds -ne $null) {
            $estimate = [double]$bestEstimateRun.quality.wall_clock_seconds
        }
    }
    $fixtureEstimates[$fPath] = $estimate
}

$pendingRuns = @()
foreach ($binary in $binaries) {
    foreach ($fixture in $fixtures) {
        $cacheKey = Get-BenchmarkCacheKey $binary $fixture $settingsObj
        if (-not $cache.ContainsKey($cacheKey) -or $Force) {
            $fGroup = Split-Path (Split-Path $fixture.FullName -Parent) -Leaf
            $fPath = "$fGroup/$($fixture.Name)"
            $pendingRuns += [PSCustomObject]@{
                CacheKey   = $cacheKey
                EstimatedS = $fixtureEstimates[$fPath]
            }
        }
    }
}

$totalPendingCount = $pendingRuns.Count
$completedPendingCount = 0
$totalPendingTimeEstimated = ($pendingRuns | Measure-Object -Property EstimatedS -Sum).Sum
$completedPendingTimeEstimated = 0.0
$actualTimeSpent = 0.0

foreach ($binary in $binaries) {
    $verLabel = Get-BinaryVersionLabel $binary
    
    # CLI support probe
    if (-not $cliSupportCache.ContainsKey($binary.FullName)) {
        $cliSupportCache[$binary.FullName] = Test-BinaryCliSupport $binary.FullName
    }
    $supportsCli = $cliSupportCache[$binary.FullName]

    foreach ($fixture in $fixtures) {
        $runIdx++
        $fixtureGroup = Split-Path (Split-Path $fixture.FullName -Parent) -Leaf
        $fixtureStem = $fixture.BaseName
        $cacheKey = Get-BenchmarkCacheKey $binary $fixture $settingsObj
        $isCached = $cache.ContainsKey($cacheKey) -and -not $Force

        # Calculate ETA
        $etaStr = "N/A"
        $thisEstimate = 60.0
        if (-not $isCached) {
            $foundPending = $pendingRuns | Where-Object { $_.CacheKey -eq $cacheKey } | Select-Object -First 1
            if ($foundPending) {
                $thisEstimate = $foundPending.EstimatedS
            }

            # Remaining estimated time before executing this one
            $remainingEst = $totalPendingTimeEstimated - $completedPendingTimeEstimated
            if ($completedPendingCount -gt 0 -and $completedPendingTimeEstimated -gt 0) {
                $speedFactor = $actualTimeSpent / $completedPendingTimeEstimated
                $remainingSec = $remainingEst * $speedFactor
            } else {
                $remainingSec = $remainingEst
            }

            $ts = [timespan]::FromSeconds($remainingSec)
            $etaStr = "$([math]::Floor($ts.TotalHours))h $($ts.Minutes)m $($ts.Seconds)s remaining"
        }

        # Form unique base name with folder name, fixture file name, version
        $timestamp = (Get-Date -Format "yyyyMMdd-HHmmss")
        $baseName = "${fixtureGroup}--${fixtureStem}--${verLabel}--${timestamp}"

        if ($isCached) {
            Write-Output "[$runIdx/$totalCombinations] $verLabel x $fixtureGroup/$($fixture.Name) (Cache Hit)"
            Write-Output "  -> [SKIP] Cache hit found"
            continue
        }

        Write-Output "[$runIdx/$totalCombinations] $verLabel x $fixtureGroup/$($fixture.Name) (ETA: $etaStr)"

        # DSN text parse
        $fixtureMeta = Get-DsnMetadata $fixture.FullName
        
        $runStartTime = Get-Date
        
        # Invoke benchmark
        Write-Output "  -> Running..."
        $runResult = Invoke-BenchmarkRun $binary $fixture $baseName $LogsDir $OutputsDir $settingsObj $supportsCli
        
        $runEndTime = Get-Date
        $runSecs = ($runEndTime - $runStartTime).TotalSeconds

        # Invoke DRC using current version on outputs
        Write-Output "  -> Running DRC check..."
        $drcResult = Invoke-DrcCheck $binaryCurrent $fixture $runResult.OutputFile $OutputsDir $baseName

        # Parse metrics from logs
        $logMetrics = Get-PhaseMetrics $runResult.LogFile $verLabel

        # Build run record
        $runObj = [PSCustomObject]@{
            cache_key = $cacheKey
            run_at    = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
            run_mode  = $runResult.RunMode
            system    = $sysInfo
            binary    = [PSCustomObject]@{
                filename      = $binary.Name
                version_label = $verLabel
                sha256        = (Get-FileHash $binary.FullName -Algorithm SHA256).Hash
                size_bytes    = $binary.Length
            }
            fixture   = [PSCustomObject]@{
                filename            = $fixture.Name
                group               = $fixtureGroup
                relative_path       = "$fixtureGroup/$($fixture.Name)"
                size_bytes          = $fixture.Length
                sha256              = (Get-FileHash $fixture.FullName -Algorithm SHA256).Hash
                host_cad            = $fixtureMeta.host_cad
                host_version        = $fixtureMeta.host_version
                layer_count         = $fixtureMeta.layer_count
                net_count           = $fixtureMeta.net_count
                component_count     = $fixtureMeta.component_count
                smd_pin_count       = if ($logMetrics.fanout.smd_pin_count -ne $null) { $logMetrics.fanout.smd_pin_count } else { 0 }
                board_width_mm      = $fixtureMeta.board_width_mm
                board_height_mm     = $fixtureMeta.board_height_mm
                board_area_cm2      = $fixtureMeta.board_area_cm2
            }
            settings  = $settingsObj
            phases    = [PSCustomObject]@{
                fanout     = $logMetrics.fanout
                autorouter = $logMetrics.autorouter
                optimizer  = $logMetrics.optimizer
            }
            quality   = [PSCustomObject]@{
                total_nets             = $fixtureMeta.net_count
                initial_unrouted       = $logMetrics.autorouter.initial_unrouted_count
                final_unrouted         = $logMetrics.autorouter.final_unrouted
                routing_completion_pct = if ($logMetrics.autorouter.initial_unrouted_count -gt 0) { [math]::Round(100.0 * ($logMetrics.autorouter.initial_unrouted_count - $logMetrics.autorouter.final_unrouted) / $logMetrics.autorouter.initial_unrouted_count, 1) } else { 100.0 }
                clearance_violations   = $logMetrics.autorouter.final_violations
                quality_score          = $logMetrics.autorouter.final_score
                total_cpu_seconds      = [double]$logMetrics.autorouter.cpu_seconds + [double]$logMetrics.fanout.cpu_seconds + [double]$logMetrics.optimizer.cpu_seconds
                total_allocated_gb     = [double]$logMetrics.autorouter.total_allocated_gb + [double]$logMetrics.fanout.total_allocated_gb + [double]$logMetrics.optimizer.total_allocated_gb
                peak_heap_mb           = [math]::Max([double]$logMetrics.autorouter.peak_heap_mb, [math]::Max([double]$logMetrics.fanout.peak_heap_mb, [double]$logMetrics.optimizer.peak_heap_mb))
                wall_clock_seconds     = $runResult.WallClockSeconds
            }
            drc       = $drcResult
            log_analysis = [PSCustomObject]@{
                warn_count  = $logMetrics.warn_count
                error_count = $logMetrics.error_count
                load_error  = $logMetrics.load_error
                exceptions  = $logMetrics.exceptions
                timed_out   = $logMetrics.timed_out
            }
            exit      = [PSCustomObject]@{
                code         = $runResult.ExitCode
                crashed      = $runResult.Crashed
                oom_detected = $runResult.OomDetected
                timed_out    = $runResult.TimedOut
            }
            log_file  = $runResult.LogFile
        }

        # Update cache
        $cache[$cacheKey] = $runObj

        # Save JSON atomically
        Save-BenchmarksJson $rawJson $cache $JsonPath

        # Update ETA tracking
        $completedPendingCount++
        $completedPendingTimeEstimated += $thisEstimate
        $actualTimeSpent += $runResult.WallClockSeconds
        
        Write-Output "  -> Done! [Unrouted: $($runObj.quality.final_unrouted) (DRC: $($runObj.drc.final_unrouted)), Violations: $($runObj.quality.clearance_violations) (DRC: $($runObj.drc.final_violations)), Score: $($runObj.quality.quality_score) (DRC: $($runObj.drc.final_quality_score))]"
    }
}

# 6. Generate final reports
Write-Output "Regenerating benchmark reports..."
Export-MarkdownReport $cache $MdPath $CsvPath $ChartDataPath
Update-BenchmarksHtml $cache $WebsiteHtml
Write-Output "Benchmark Suite Execution Complete!"