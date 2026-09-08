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
    [string]  $FilterBinary   = "*",
    [ValidateSet("A", "B", "C", "D")]
    [string]  $FilterTier     = ""
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

function New-BenchmarkPhaseSnapshot {
    param(
        $BoardStatistics,
        $Score,
        [string]$ScoreSource
    )

    return [PSCustomObject]@{
        board_statistics       = $BoardStatistics
        score                  = $Score
        score_source           = $ScoreSource
        current_router_score   = $null
        current_optimizer_score = $null
        current_score_source   = $null
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
$tierByBoardId = @{}
$catalogPath = Join-Path $FixturesDir "PCBench\catalog.json"
if ($FilterTier -and (Test-Path $catalogPath)) {
    try {
        $catalog = Get-Content $catalogPath -Raw | ConvertFrom-Json
        foreach ($board in @($catalog.boards)) {
            $tierByBoardId[[string]$board.board_id] = [string]$board.tier
        }
    } catch {
        Write-Error "Failed to read PCBench catalog: $catalogPath"
        exit 1
    }
}
$fixtures = @(Get-ChildItem $FixturesDir -Recurse -Filter "*.dsn" | Where-Object {
    $fixtureMatchesName =
        $_.Name -like $FilterFixture -or
        ($_.FullName -replace '\\', '/') -like "*$($FilterFixture -replace '\\', '/')*"
    $fixtureGroup = Split-Path (Split-Path $_.FullName -Parent) -Leaf
    $fixtureMatchesTier =
        (-not $FilterTier) -or
        ($tierByBoardId.ContainsKey($fixtureGroup) -and $tierByBoardId[$fixtureGroup] -eq $FilterTier)
    $fixtureMatchesName -and $fixtureMatchesTier -and (Test-IsActiveBenchmarkFixtureFile $_)
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
        $nativeScoreSource = if ($binary.Name -match "freerouting-1\.9\.0\.jar") { "v19_native" } else { "current_native" }
        foreach ($phaseName in @("fanout", "autorouter", "optimizer")) {
            $phaseMetrics = $logMetrics.$phaseName
            if ($null -eq $phaseMetrics.before) {
                $phaseMetrics.before = New-BenchmarkPhaseSnapshot $null $phaseMetrics.score_before $nativeScoreSource
            }
            if ($null -eq $phaseMetrics.after) {
                $phaseMetrics.after = New-BenchmarkPhaseSnapshot $boardStats $phaseMetrics.score_after $nativeScoreSource
            }
        }
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

        $currentRouterScore = $drcResult.final_quality_score
        $currentOptimizerScore = $drcResult.final_optimizer_score
        if ($logMetrics.optimizer.after) {
            $snapshot = $logMetrics.optimizer.after
            if ($snapshot.PSObject.Properties["current_router_score"]) {
                $snapshot.current_router_score = $currentRouterScore
            } else {
                Add-Member -InputObject $snapshot -NotePropertyName "current_router_score" `
                    -NotePropertyValue $currentRouterScore
            }
            if ($snapshot.PSObject.Properties["current_optimizer_score"]) {
                $snapshot.current_optimizer_score = $currentOptimizerScore
            } else {
                Add-Member -InputObject $snapshot -NotePropertyName "current_optimizer_score" `
                    -NotePropertyValue $currentOptimizerScore
            }
            if ($snapshot.PSObject.Properties["current_score_source"]) {
                $snapshot.current_score_source = "current_drc_replay"
            } else {
                Add-Member -InputObject $snapshot -NotePropertyName "current_score_source" `
                    -NotePropertyValue "current_drc_replay"
            }
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
            settings_snapshot = $logMetrics.settings_snapshot
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
                optimizer_score        = if ($null -ne $logMetrics.optimizer.score_after) {
                    $logMetrics.optimizer.score_after
                } else {
                    $logMetrics.optimizer.final_score
                }
                trace_length_mm        = if ($traceStats) { $traceStats.total_length_mm } else { $null }
                via_count              = if ($viaStats) { $viaStats.total_count } else { $null }
                bend_count             = if ($bendStats) { $bendStats.total_count } else { $null }
                pin_count              = $pinCount
                signal_layer_count     = $signalLayerCount
                quality_score          = $logMetrics.autorouter.final_score
                current_router_score   = $currentRouterScore
                current_optimizer_score = $currentOptimizerScore
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
            schema_version = 5
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
$currentBoundsCompletenessByFixture = @{}
$currentSettingsByFixture = @{}
$currentBinaryName = if ($binaryCurrent) { $binaryCurrent.Name } else { $null }
$currentMachineCpuScore = $null
if ($currentBinaryName) {
    $currentRuns = @($cache.Values | Where-Object {
            $_.binary -and $_.binary.filename -eq $currentBinaryName
        })
    $currentCpuRun = $currentRuns |
        Where-Object { $_.system -and $null -ne $_.system.cpu_score } |
        Sort-Object run_at -Descending |
        Select-Object -First 1
    if ($currentCpuRun) {
        $currentMachineCpuScore =
            if ([int]$currentCpuRun.system.cpu_score -gt 10000) {
                [int][math]::Round([double]$currentCpuRun.system.cpu_score / 1000.0)
            } else {
                [int]$currentCpuRun.system.cpu_score
            }
    }

    foreach ($run in $currentRuns) {
        if ($run.fixture -and $run.fixture.relative_path -and $run.bounds) {
            $fixturePath = [string]$run.fixture.relative_path
            if ($run.PSObject.Properties["settings_snapshot"] -and $run.settings_snapshot) {
                $currentSettingsByFixture[$fixturePath] = $run.settings_snapshot
            }
            $hasBounds =
                $null -ne $run.bounds.board_area_mm2 -or
                $null -ne $run.bounds.complexity_c -or
                $null -ne $run.bounds.min_trace_length_mm -or
                $null -ne $run.bounds.min_via_count -or
                $null -ne $run.bounds.min_bend_count
            if ($hasBounds) {
                $boundCount = @(
                    "board_area_mm2",
                    "complexity_c",
                    "min_trace_length_mm",
                    "min_via_count",
                    "min_bend_count"
                ) | Where-Object { $null -ne $run.bounds.$_ } | Measure-Object | Select-Object -ExpandProperty Count
                if (-not $currentBoundsCompletenessByFixture.ContainsKey($fixturePath) -or
                    $boundCount -gt $currentBoundsCompletenessByFixture[$fixturePath]) {
                    $currentBoundsByFixture[$fixturePath] = $run.bounds
                    $currentBoundsCompletenessByFixture[$fixturePath] = $boundCount
                }
            }
        }
    }
}

$boundsPatched = $false
$effectiveCpuScorePatched = $false
$cpuScoreScaledPatched = $false
$hostVersionPatched = $false
$settingsPatched = $false
$fixtureMetadataCache = @{}
foreach ($key in @($cache.Keys)) {
    $run = $cache[$key]
    if ($run.system) {
        if ($run.system.PSObject.Properties["cpu_score"] -and
            $null -ne $run.system.cpu_score -and
            [int]$run.system.cpu_score -gt 10000) {
            $run.system.cpu_score = [int][math]::Round([double]$run.system.cpu_score / 1000.0)
            $cache[$key] = $run
            $cpuScoreScaledPatched = $true
        }
        if ($run.system.PSObject.Properties["cpu_score_effective"] -and
            $null -ne $run.system.cpu_score_effective -and
            [int]$run.system.cpu_score_effective -gt 10000) {
            $run.system.cpu_score_effective =
                [int][math]::Round([double]$run.system.cpu_score_effective / 1000.0)
            $cache[$key] = $run
            $cpuScoreScaledPatched = $true
        }
        $cpuScoreProperty = $run.system.PSObject.Properties["cpu_score"]
        $effectiveCpuScore =
            if ($cpuScoreProperty -and $null -ne $cpuScoreProperty.Value) {
                [int]$cpuScoreProperty.Value
            } else {
                $currentMachineCpuScore
            }
        $effectiveProperty = $run.system.PSObject.Properties["cpu_score_effective"]
        if ($null -ne $effectiveCpuScore -and
            ($null -eq $effectiveProperty -or
                $effectiveProperty.Value -ne $effectiveCpuScore)) {
            if ($effectiveProperty) {
                $effectiveProperty.Value = $effectiveCpuScore
            } else {
                Add-Member -InputObject $run.system -NotePropertyName "cpu_score_effective" `
                    -NotePropertyValue $effectiveCpuScore
            }
            $cache[$key] = $run
            $effectiveCpuScorePatched = $true
        }
    }
    $fixturePath = if ($run.fixture) { $run.fixture.relative_path } else { $null }
    if ($fixturePath -and $run.fixture -and
        [string]::IsNullOrWhiteSpace([string]$run.fixture.host_version)) {
        $dsnPath = Join-Path $FixturesDir $fixturePath
        if (-not (Test-Path $dsnPath)) {
            $dsnPath = Join-Path $FixturesDir "PCBench\$fixturePath"
        }
        if (Test-Path $dsnPath) {
            if (-not $fixtureMetadataCache.ContainsKey($dsnPath)) {
                $fixtureMetadataCache[$dsnPath] = Get-DsnMetadata $dsnPath
            }
            $detectedHostVersion = $fixtureMetadataCache[$dsnPath].host_version
            if (-not [string]::IsNullOrWhiteSpace([string]$detectedHostVersion)) {
                $hostVersionProperty = $run.fixture.PSObject.Properties["host_version"]
                if ($hostVersionProperty) {
                    $hostVersionProperty.Value = $detectedHostVersion
                } else {
                    Add-Member -InputObject $run.fixture -NotePropertyName "host_version" `
                        -NotePropertyValue $detectedHostVersion
                }
                $cache[$key] = $run
                $hostVersionPatched = $true
            }
        }
    }
    if ($fixturePath -and $currentBoundsByFixture.ContainsKey($fixturePath)) {
        $sourceBounds = $currentBoundsByFixture[$fixturePath]
        $boundsProperty = $run.PSObject.Properties["bounds"]
        if ($null -eq $boundsProperty -or $null -eq $boundsProperty.Value) {
            $newBounds = [PSCustomObject]@{}
            if ($boundsProperty) {
                $boundsProperty.Value = $newBounds
            } else {
                Add-Member -InputObject $run -NotePropertyName "bounds" -NotePropertyValue $newBounds
            }
        }
        $runBoundsChanged = $false
        foreach ($field in @(
                "board_area_mm2",
                "complexity_c",
                "min_trace_length_mm",
                "min_via_count",
                "min_bend_count"
            )) {
            if ($null -eq $run.bounds.$field -and $null -ne $sourceBounds.$field) {
                $boundProperty = $run.bounds.PSObject.Properties[$field]
                if ($boundProperty) {
                    $boundProperty.Value = $sourceBounds.$field
                } else {
                    Add-Member -InputObject $run.bounds -NotePropertyName $field `
                        -NotePropertyValue $sourceBounds.$field
                }
                $runBoundsChanged = $true
            }
        }
        if ($runBoundsChanged) {
            $cache[$key] = $run
            $boundsPatched = $true
        }
    }
    if ($fixturePath -and
        $currentSettingsByFixture.ContainsKey($fixturePath) -and
        (-not $run.PSObject.Properties["settings_snapshot"] -or
            $null -eq $run.settings_snapshot) -and
        [string]$run.binary.version_label -match "(?i)(1[._-]?9|v190)") {
        $settingsProperty = $run.PSObject.Properties["settings_snapshot"]
        if ($settingsProperty) {
            $settingsProperty.Value = $currentSettingsByFixture[$fixturePath]
        } else {
            Add-Member -InputObject $run -NotePropertyName "settings_snapshot" `
                -NotePropertyValue $currentSettingsByFixture[$fixturePath]
        }
        $cache[$key] = $run
        $settingsPatched = $true
    }
}
if ($boundsPatched -or $effectiveCpuScorePatched -or $cpuScoreScaledPatched -or
    $hostVersionPatched -or $settingsPatched) {
    Save-BenchmarksJson $rawJson $cache $JsonPath
}

# 6. Generate final reports
Write-Output "Regenerating benchmark reports..."
Update-BenchmarkReports $cache
Write-Output "Benchmark Suite Execution Complete!"
