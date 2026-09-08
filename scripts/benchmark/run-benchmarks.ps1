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
    [bool]    $OptimizerEnabled = $true,
    [int]     $MaxItems       = 0,
    [int]     $RunsPerConfig  = 1,
    [int]     $TimeoutGraceSeconds = 45,
    [int]     $DrcTimeoutSeconds = 300,
    [switch]  $Profile,
    [switch]  $RetainAutorouteDatabase,
    [switch]  $Cm5FirstPass,
    [switch]  $Force,
    [switch]  $ReportOnly,
    [switch]  $SkipWebsiteUpdate,
    [string]  $FilterFixture  = "*",
    [string]  $FilterBinary   = "*"
)

if ($Cm5FirstPass) {
    $MaxPasses = 1
    if ($MaxItems -le 0) {
        $MaxItems = 100
    }
    $OptimizerEnabled = $false
    $Profile = $true
    $FilterFixture = "CM5_MINIMA_3.dsn"
}

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
$ChartDataPath = Join-Path $ResultsDir "benchmarks-chart-data.json"

# Load current cache
$store = Load-BenchmarksJson $JsonPath
$rawJson = $store.RawData
$cache = $store.Cache

function Update-BenchmarkReports {
    param([Hashtable]$Cache)
    Export-MarkdownReport $Cache $MdPath $ChartDataPath
    if (-not $SkipWebsiteUpdate) {
        Update-BenchmarksHtml $Cache $WebsiteHtml
    }
}

if ($ReportOnly) {
    Write-Output "Report-only mode. Generating reports from cached data..."
    Update-BenchmarkReports $cache
    Write-Output "Done!"
    exit 0
}

# Discover files
$allBinaries = @(Get-ChildItem $BinariesDir -Filter "*.jar")
$binaries = @($allBinaries | Where-Object { $_.Name -like $FilterBinary })
$fixtures = @(Get-ChildItem $FixturesDir -Recurse -Filter "*.dsn" | Where-Object {
    ($_.Name -like $FilterFixture -or ($_.FullName -replace '\\', '/') -like "*$($FilterFixture -replace '\\', '/')*") -and (Test-IsActiveBenchmarkFixtureFile $_)
})

if ($binaries.Count -eq 0) {
    Write-Error "No Freerouting binaries found in: $BinariesDir"
    exit 1
}
if ($fixtures.Count -eq 0) {
    Write-Error "No DSN fixtures found in: $FixturesDir"
    exit 1
}

# Identify the explicit current binary for every DRC check. Historical route binaries must never
# silently become the DRC implementation used for comparison metrics.
$binaryCurrent = $allBinaries |
    Where-Object { $_.Name -match "current" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $binaryCurrent) {
    Write-Error "freerouting-current.jar is required for comparable post-route DRC checks."
    exit 1
}

# Gathers system info
$sysInfo = Get-SystemInfo
Write-Output "System: $($sysInfo.cpu_name) ($($sysInfo.cpu_physical_cores) Cores, $($sysInfo.total_ram_gb) GB RAM)"

$gitBranch = Get-GitBranchName
if ($gitBranch -and $gitBranch -notin @('master', 'main')) {
    Write-Output "Git branch: $gitBranch (snapshot/current JARs will be labeled with this branch name in reports)"
} elseif ($gitBranch) {
    Write-Output "Git branch: $gitBranch (snapshot/current JARs will use manifest build-date labels)"
}

# Settings object
$gitSha = Get-GitShaShort (Join-Path $PSScriptRoot "..\..")
$settingsObj = [PSCustomObject]@{
    max_passes        = $MaxPasses
    max_time          = $MaxTime
    max_threads       = $MaxThreads
    heap_max          = $HeapMax
    log_level         = $LogLevel
    fanout_enabled    = $FanoutEnabled
    router_enabled    = $RouterEnabled
    optimizer_enabled = $OptimizerEnabled
    max_items         = $MaxItems
    runs_per_config   = $RunsPerConfig
    git_sha           = $gitSha
    fanout_timeout    = "00:15:00"
    optimizer_timeout = "00:10:00"
    timeout_grace_period_seconds = $TimeoutGraceSeconds
    drc_timeout_seconds = $DrcTimeoutSeconds
    profile_enabled = $Profile.IsPresent
    retain_autoroute_database = $RetainAutorouteDatabase.IsPresent
}
Write-Output "Git SHA: $gitSha"

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
        $cacheKey = Get-BenchmarkCacheKey $binary $fixture $settingsObj $gitSha
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
        $cacheKey = Get-BenchmarkCacheKey $binary $fixture $settingsObj $gitSha
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
        $fsVerLabel = Get-FilesystemSafeVersionLabel $verLabel
        $baseName = "${fixtureGroup}--${fixtureStem}--${fsVerLabel}--${timestamp}"

        if ($isCached) {
            Write-Output "[$runIdx/$totalCombinations] $verLabel x $fixtureGroup/$($fixture.Name) (Cache Hit)"
            $cachedRun = $cache[$cacheKey]
            $cachedOutputPath = $null
            if ($cachedRun.output_file) {
                $cachedOutputPath = [string]$cachedRun.output_file
                if (-not [System.IO.Path]::IsPathRooted($cachedOutputPath)) {
                    $cachedOutputPath = Join-Path $OutputsDir $cachedOutputPath
                }
            } elseif ($cachedRun.log_file) {
                $cachedLogName = [System.IO.Path]::GetFileName([string]$cachedRun.log_file)
                $cachedOutputPath = Join-Path $OutputsDir ($cachedLogName -replace '\.log$', '.ses')
            }

            if ($cachedOutputPath -and (Test-Path $cachedOutputPath)) {
                $cachedSesFile = Get-Item $cachedOutputPath
                $cachedBaseName = [System.IO.Path]::GetFileNameWithoutExtension($cachedSesFile.Name)
                Write-Output "  -> Refreshing DRC with $($binaryCurrent.Name)"
                $cachedDrc = Invoke-DrcCheck `
                    $binaryCurrent `
                    $fixture `
                    $cachedSesFile `
                    $OutputsDir `
                    $cachedBaseName `
                    $DrcTimeoutSeconds
                $cachedRun.drc = $cachedDrc
                $cachedRun.output_file = $cachedSesFile.FullName
                $cachedRun.drc_refresh_at = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
                $cache[$cacheKey] = $cachedRun
                Save-BenchmarksJson $rawJson $cache $JsonPath
                continue
            }

            Write-Warning "Cached route output is missing; rerunning route before DRC refresh."
            $isCached = $false
        }

        Write-Output "[$runIdx/$totalCombinations] $verLabel x $fixtureGroup/$($fixture.Name) (ETA: $etaStr)"

        # DSN text parse
        $fixtureMeta = Get-DsnMetadata $fixture.FullName

        $runStartTime = Get-Date

        $sampleCount = [math]::Max(1, [int]$RunsPerConfig)
        $sampleRecords = @()
        $runResult = $null
        $drcResult = $null
        $logMetrics = $null

        for ($sampleIdx = 1; $sampleIdx -le $sampleCount; $sampleIdx++) {
            $sampleBase = $baseName
            if ($sampleCount -gt 1) {
                $sampleBase = "${baseName}-r$sampleIdx"
                Write-Output "  -> Running sample $sampleIdx/$sampleCount..."
            } else {
                Write-Output "  -> Running..."
            }
            $runResult = Invoke-BenchmarkRun $binary $fixture $sampleBase $LogsDir $OutputsDir $settingsObj $supportsCli

            Write-Output "  -> Running DRC check..."
            $drcResult = Invoke-DrcCheck `
                $binaryCurrent `
                $fixture `
                (Get-Item $runResult.OutputFile -ErrorAction SilentlyContinue) `
                $OutputsDir `
                $sampleBase `
                $DrcTimeoutSeconds

            $logMetrics = Get-PhaseMetrics $runResult.LogFile $verLabel $runResult.TimedOut
            if ($runResult.ResultJsonFile -and (Test-Path $runResult.ResultJsonFile)) {
                $logMetrics = Import-ResultManifestMetrics $runResult.ResultJsonFile $logMetrics
            }
            $sampleRecords += [PSCustomObject]@{
                sample_index = $sampleIdx
                final_unrouted = $logMetrics.autorouter.final_unrouted
                clearance_violations = $logMetrics.autorouter.final_violations
                quality_score = $logMetrics.autorouter.final_score
                wall_clock_seconds = $runResult.WallClockSeconds
                peak_heap_mb = $logMetrics.autorouter.peak_heap_mb
                result_json = $runResult.ResultJsonFile
            }
        }

        $runEndTime = Get-Date
        $runSecs = ($runEndTime - $runStartTime).TotalSeconds

        $routingCompletionPct = $null
        if (($logMetrics.autorouter.initial_unrouted_count -ne $null) -and ($logMetrics.autorouter.final_unrouted -ne $null) -and ($logMetrics.autorouter.initial_unrouted_count -gt 0)) {
            $routingCompletionPct = [math]::Round((100.0 * ($logMetrics.autorouter.initial_unrouted_count - $logMetrics.autorouter.final_unrouted) / $logMetrics.autorouter.initial_unrouted_count), 1)
        }

        function Get-MedianValue($values) {
            $sorted = @($values | Where-Object { $_ -ne $null } | Sort-Object)
            if ($sorted.Count -eq 0) { return $null }
            return $sorted[[math]::Floor($sorted.Count / 2)]
        }

        if ($sampleCount -gt 1) {
            $logMetrics.autorouter.final_unrouted = Get-MedianValue ($sampleRecords.final_unrouted)
            $logMetrics.autorouter.final_violations = Get-MedianValue ($sampleRecords.clearance_violations)
            $logMetrics.autorouter.final_score = Get-MedianValue ($sampleRecords.quality_score)
        }

        $boardStats = $logMetrics.board_statistics
        $connectionStats = if ($boardStats) { $boardStats.connections } else { $null }
        $clearanceStats = if ($boardStats) { $boardStats.clearance_violations } else { $null }
        $itemStats = if ($boardStats) { $boardStats.items } else { $null }
        $layerStats = if ($boardStats) { $boardStats.layers } else { $null }
        $netStats = if ($boardStats) { $boardStats.nets } else { $null }
        $componentStats = if ($boardStats) { $boardStats.components } else { $null }
        $traceStats = if ($boardStats) { $boardStats.traces } else { $null }
        $viaStats = if ($boardStats) { $boardStats.vias } else { $null }
        $bendStats = if ($boardStats) { $boardStats.bends } else { $null }
        $boundsStats = if ($logMetrics.bounds) { $logMetrics.bounds } else { $null }
        $boardSize = if ($boardStats) { $boardStats.board.size } else { $null }
        $boardAreaMm2 = $null
        if ($boardSize -and $boardSize.width -ne $null -and $boardSize.height -ne $null) {
            $boardAreaMm2 = [math]::Abs([double]$boardSize.width * [double]$boardSize.height)
        }
        $pinCount = if ($itemStats -and $itemStats.pin_count -ne $null) {
            [int]$itemStats.pin_count
        } else {
            $null
        }
        $signalLayerCount = if ($layerStats -and $layerStats.signal_count -ne $null) {
            [int]$layerStats.signal_count
        } else {
            $null
        }
        $complexityC = if ($pinCount -ne $null -and $signalLayerCount -ne $null) {
            [math]::Max(1, $pinCount * $signalLayerCount)
        } else {
            $null
        }

        # Build run record
        $relativeLogFile = $runResult.LogFile
        try {
            $relativeLogFile = (Resolve-Path $runResult.LogFile -Relative)
        } catch {}

        $runObj = [PSCustomObject]@{
            cache_key = $cacheKey
            run_at    = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
            run_mode  = $runResult.RunMode
            system    = [PSCustomObject]@{
                cpu_name           = $sysInfo.cpu_name
                cpu_physical_cores = $sysInfo.cpu_physical_cores
                cpu_logical_cores  = $sysInfo.cpu_logical_cores
                total_ram_gb       = $sysInfo.total_ram_gb
                cpu_score          = $logMetrics.cpu_score
            }
            binary    = [PSCustomObject]@{
                filename      = $binary.Name
                version_label = $verLabel
                sha256        = (Get-FileHash $binary.FullName -Algorithm SHA256).Hash
                size_bytes    = $binary.Length
                git_sha       = $gitSha
            }
            fixture   = [PSCustomObject]@{
                filename            = $fixture.Name
                group               = $fixtureGroup
                relative_path       = "$fixtureGroup/$($fixture.Name)"
                size_bytes          = $fixture.Length
                sha256              = (Get-FileHash $fixture.FullName -Algorithm SHA256).Hash
                host_cad            = $fixtureMeta.host_cad
                host_version        = $fixtureMeta.host_version
                layer_count         = if ($layerStats) { $layerStats.total_count } else { $null }
                net_count           = if ($netStats) { $netStats.total_count } else { $null }
                component_count     = if ($componentStats) { $componentStats.total_count } else { $null }
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
                total_nets             = if ($netStats) { $netStats.total_count } else { $null }
                max_connections       = if ($connectionStats) { $connectionStats.maximum_count } else { $null }
                unrouted_connections  = if ($connectionStats) { $connectionStats.incomplete_count } else { $null }
                initial_unrouted       = $logMetrics.autorouter.initial_unrouted_count
                final_unrouted         = $logMetrics.autorouter.final_unrouted
                routing_completion_pct = $routingCompletionPct
                clearance_violations   = $logMetrics.autorouter.final_violations
                total_violation_um     = if ($clearanceStats) { $clearanceStats.total_violation_um } else { $null }
                optimizer_score        = $logMetrics.optimizer.final_score
                trace_length_mm        = if ($traceStats) { $traceStats.total_length_mm } else { $null }
                via_count              = if ($viaStats) { $viaStats.total_count } else { $null }
                bend_count             = if ($bendStats) { $bendStats.total_count } else { $null }
                pin_count              = $pinCount
                signal_layer_count     = $signalLayerCount
                quality_score          = $logMetrics.autorouter.final_score
                total_cpu_seconds      = [double]$logMetrics.autorouter.cpu_seconds + [double]$logMetrics.fanout.cpu_seconds + [double]$logMetrics.optimizer.cpu_seconds
                total_allocated_gb     = [double]$logMetrics.autorouter.total_allocated_gb + [double]$logMetrics.fanout.total_allocated_gb + [double]$logMetrics.optimizer.total_allocated_gb
                peak_heap_mb           = [math]::Max([double]$logMetrics.autorouter.peak_heap_mb, [math]::Max([double]$logMetrics.fanout.peak_heap_mb, [double]$logMetrics.optimizer.peak_heap_mb))
                wall_clock_seconds     = $runResult.WallClockSeconds
            }
            bounds    = [PSCustomObject]@{
                board_area_mm2 = $boardAreaMm2
                complexity_c   = $complexityC
                min_trace_length_mm = if ($boundsStats) { $boundsStats.min_trace_length_mm } else { $null }
                min_via_count       = if ($boundsStats) { $boundsStats.min_via_count } else { $null }
                min_bend_count      = if ($boundsStats) { $boundsStats.min_bend_count } else { $null }
            }
            drc       = $drcResult
            log_analysis = [PSCustomObject]@{
                warn_count  = $logMetrics.warn_count
                error_count = $logMetrics.error_count
                load_error  = $logMetrics.load_error
                exceptions  = $logMetrics.exceptions
                timed_out   = $logMetrics.timed_out
                metric_source = $logMetrics.metric_source
                last_checkpoint = $logMetrics.last_checkpoint
            }
            exit      = [PSCustomObject]@{
                code         = $runResult.ExitCode
                crashed      = $runResult.Crashed
                oom_detected = $runResult.OomDetected
                timed_out    = $runResult.TimedOut
            }
            log_file    = $relativeLogFile
            result_json = $runResult.ResultJsonFile
            output_file = $runResult.OutputFile
            samples     = $sampleRecords
            schema_version = 2
        }

        # Update cache
        $cache[$cacheKey] = $runObj

        # Save JSON atomically
        Save-BenchmarksJson $rawJson $cache $JsonPath

        # Update ETA tracking
        $completedPendingCount++
        $completedPendingTimeEstimated += $thisEstimate
        $actualTimeSpent += $runResult.WallClockSeconds

        Write-Output "  -> Done! [Unrouted: $($runObj.quality.final_unrouted) (DRC: $($runObj.drc.final_unrouted)), Clearance: $($runObj.drc.clearance_violations), Dangling: $($runObj.drc.dangling_tracks) tracks/$($runObj.drc.dangling_vias) vias, Score: $($runObj.quality.quality_score) (DRC: $($runObj.drc.final_quality_score))]"
    }
}

# v1.9 does not calculate V2 lower bounds in the frozen compatibility tree. When a matching
# current-tree run exists, copy its board-only bounds into the v1.9 benchmark record so replay
# consumers receive the same schema without changing v1.9 routing behavior.
$currentBoundsByFixture = @{}
$currentBinaryName = if ($binaryCurrent) { $binaryCurrent.Name } else { $null }
$currentMachineCpuScore = $null
if ($currentBinaryName) {
    foreach ($run in @($cache.Values)) {
        if ($run.binary -and $run.binary.filename -eq $currentBinaryName -and
            $run.system -and $null -ne $run.system.cpu_score) {
            $currentMachineCpuScore = [int]$run.system.cpu_score
        }
        if ($run.binary -and $run.binary.filename -eq $currentBinaryName -and
            $run.fixture -and $run.fixture.relative_path -and $run.bounds) {
            if ($null -ne $run.bounds.min_trace_length_mm -or
                $null -ne $run.bounds.min_via_count -or
                $null -ne $run.bounds.min_bend_count) {
                $currentBoundsByFixture[$run.fixture.relative_path] = $run.bounds
            }
        }
    }
}

$boundsPatched = $false
$effectiveCpuScorePatched = $false
foreach ($key in @($cache.Keys)) {
    $run = $cache[$key]
    if ($run.system) {
        $effectiveCpuScore =
            if ($null -ne $run.system.cpu_score) {
                [int]$run.system.cpu_score
            } else {
                $currentMachineCpuScore
            }
        if ($null -ne $effectiveCpuScore -and
            $run.system.cpu_score_effective -ne $effectiveCpuScore) {
            $run.system.cpu_score_effective = $effectiveCpuScore
            $cache[$key] = $run
            $effectiveCpuScorePatched = $true
        }
    }
    $fixturePath = if ($run.fixture) { $run.fixture.relative_path } else { $null }
    if ($fixturePath -and $currentBoundsByFixture.ContainsKey($fixturePath) -and
        ($null -eq $run.bounds -or
        ($null -eq $run.bounds.min_trace_length_mm -and
         $null -eq $run.bounds.min_via_count -and
         $null -eq $run.bounds.min_bend_count))) {
        $sourceBounds = $currentBoundsByFixture[$fixturePath]
        if ($null -eq $run.bounds) {
            $run.bounds = [PSCustomObject]@{}
        }
        $run.bounds.min_trace_length_mm = $sourceBounds.min_trace_length_mm
        $run.bounds.min_via_count = $sourceBounds.min_via_count
        $run.bounds.min_bend_count = $sourceBounds.min_bend_count
        $cache[$key] = $run
        $boundsPatched = $true
    }
}
if ($boundsPatched -or $effectiveCpuScorePatched) {
    Save-BenchmarksJson $rawJson $cache $JsonPath
}

# 6. Generate final reports
Write-Output "Regenerating benchmark reports..."
Update-BenchmarkReports $cache
Write-Output "Benchmark Suite Execution Complete!"
