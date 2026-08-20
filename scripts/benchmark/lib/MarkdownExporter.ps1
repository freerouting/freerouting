function Format-MarkdownTable {
    param(
        [string[]]$Headers,
        [string[]]$Alignments, # 'L' or 'R'
        [System.Collections.ArrayList]$Rows
    )

    $colCount = $Headers.Count
    $widths = New-Object int[] $colCount

    for ($i = 0; $i -lt $colCount; $i++) {
        $widths[$i] = $Headers[$i].Length
    }

    foreach ($row in $Rows) {
        for ($i = 0; $i -lt $colCount; $i++) {
            $cellLen = if ($row[$i] -ne $null) { $row[$i].ToString().Length } else { 0 }
            if ($cellLen -gt $widths[$i]) {
                $widths[$i] = $cellLen
            }
        }
    }

    $ratioCols = @("Fanout", "Clean (0 DRC)", "Fully-Routed", "Timeouts", "Failures")
    for ($i = 0; $i -lt $colCount; $i++) {
        if ($ratioCols -contains $Headers[$i] -and $widths[$i] -lt 18) {
            $widths[$i] = 18
        }
    }

    $sb = [System.Text.StringBuilder]::new()

    # Headers
    [void]$sb.Append("|")
    for ($i = 0; $i -lt $colCount; $i++) {
        $pad = $Headers[$i].PadRight($widths[$i])
        [void]$sb.Append(" $pad |")
    }
    [void]$sb.AppendLine()

    # Separators
    [void]$sb.Append("|")
    for ($i = 0; $i -lt $colCount; $i++) {
        $align = $Alignments[$i]
        if ($align -eq 'R') {
            $cell = ([string]::new('-', $widths[$i] - 1) + ":")
        } else {
            $cell = (":" + [string]::new('-', $widths[$i] - 1))
        }
        [void]$sb.Append(" $cell |")
    }
    [void]$sb.AppendLine()

    # Data Rows
    foreach ($row in $Rows) {
        [void]$sb.Append("|")
        for ($i = 0; $i -lt $colCount; $i++) {
            $align = $Alignments[$i]
            $val = if ($row[$i] -ne $null) { $row[$i].ToString() } else { "" }
            if ($align -eq 'R') {
                $padded = $val.PadLeft($widths[$i])
            } else {
                $padded = $val.PadRight($widths[$i])
            }
            [void]$sb.Append(" $padded |")
        }
        [void]$sb.AppendLine()
    }

    return $sb.ToString()
}

function Get-RunScoreValue {
    param($Run)

    if ($Run.drc.final_quality_score -ne $null) { return [double]$Run.drc.final_quality_score }
    if ($Run.quality.quality_score -ne $null) { return [double]$Run.quality.quality_score }
    return $null
}

function Test-RunIsFailed {
    param($Run)

    $isTimeout = $Run.exit.timed_out -eq $true
    $isLoadError = $false

    $loadErrorVal = $Run.log_analysis.load_error
    $timedOutVal = $Run.log_analysis.timed_out
    if ($Run.log_file -and (Test-Path $Run.log_file) -and ($loadErrorVal -eq $null -or $timedOutVal -eq $null)) {
        $logMetrics = Get-PhaseMetrics $Run.log_file $Run.binary.version_label
        $loadErrorVal = $logMetrics.load_error
        $timedOutVal = $logMetrics.timed_out
    }

    if ($timedOutVal -eq $true) { $isTimeout = $true }
    if ($loadErrorVal -eq $true) { $isLoadError = $true }

    $hasTime = $false
    if ($Run.phases.fanout.duration_seconds -ne $null) { $hasTime = $true }
    if ($Run.phases.autorouter.duration_seconds -ne $null) { $hasTime = $true }
    if ($Run.phases.optimizer.duration_seconds -ne $null) { $hasTime = $true }
    if (-not $hasTime) { $isLoadError = $true }

    if ($isTimeout -or $isLoadError) { return $true }

    $score = Get-RunScoreValue $Run
    if ($score -eq $null -or $score -eq 0) { return $true }

    return $false
}

function Export-MarkdownReport {
    param(
        [Hashtable]$Cache,
        [string]$MdPath,
        [string]$CsvPath,
        [string]$ChartDataPath,
        [string]$FixturesDir = (Get-BenchmarkFixturesDir)
    )

    $runs = Get-ActiveBenchmarkRuns $Cache $FixturesDir

    $grouped = $runs | Group-Object -Property { $_.fixture.relative_path } | Sort-Object -Property Name
    $groupedByFolder = $runs | Group-Object -Property { $_.fixture.group } | Sort-Object -Property Name

    $sb = [System.Text.StringBuilder]::new()
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $sysInfo = Get-SystemInfo
    [void]$sb.AppendLine("# Freerouting Nightly Benchmarks Report")
    [void]$sb.AppendLine("Generated on: $ts")
    [void]$sb.AppendLine("System: $($sysInfo.cpu_name) ($($sysInfo.cpu_physical_cores) Cores, $($sysInfo.total_ram_gb) GB RAM)")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("This report lists the latest benchmark run results for each Freerouting version and fixture combination.")
    [void]$sb.AppendLine()

    # --- Multi-Tier Summary Tables ---
    $catalogLookup = @{}
    $catalogPath = Join-Path $FixturesDir "PCBench\catalog.json"
    if (Test-Path $catalogPath) {
        try {
            $cat = Get-Content $catalogPath -Raw | ConvertFrom-Json
            foreach ($b in $cat.boards) {
                $catalogLookup[$b.board_id] = $b.tier
            }
        } catch {}
    }

    $getTierFn = {
        param($r)
        if ($r.fixture.tier) { return [string]$r.fixture.tier }
        $rel = [string]$r.fixture.relative_path
        if ($rel -match 'PCBench[/\\]([^/\\]+)') {
            $boardId = $Matches[1]
            if ($catalogLookup.ContainsKey($boardId)) {
                return [string]$catalogLookup[$boardId]
            }
        }
        return "Other"
    }

    $buildSummaryTableFn = {
        param(
            [string]$Title,
            [System.Collections.IEnumerable]$TargetRuns,
            [string]$Description = ""
        )

        $runsList = @($TargetRuns)
        if ($runsList.Count -eq 0) { return "" }

        $groupedByFixture = $runsList | Group-Object -Property { $_.fixture.relative_path }
        $versionStats = @{}

        foreach ($verGroup in ($runsList | Group-Object -Property { $_.binary.version_label })) {
            $version = $verGroup.Name
            $fixtureCount = 0
            $failures = 0
            $timeouts = 0
            $perfects = 0
            $allRouted = 0
            $avgScoreValues = [System.Collections.ArrayList]::new()

            foreach ($fixtureGroup in $groupedByFixture) {
                $versionRuns = $fixtureGroup.Group | Where-Object { $_.binary.version_label -eq $version }
                if (-not $versionRuns) { continue }

                $latestRun = $versionRuns | Sort-Object -Property { $_.run_at } -Descending | Select-Object -First 1
                $fixtureCount++

                $failed = Test-RunIsFailed $latestRun
                $isTimeout = $latestRun.exit.timed_out -eq $true -or $latestRun.log_analysis.timed_out -eq $true
                if ($isTimeout) { $timeouts++ }
                if ($failed) { $failures++ }

                $unrouted = if ($latestRun.drc.final_unrouted -ne $null) {
                    [int]$latestRun.drc.final_unrouted
                } elseif ($latestRun.quality.final_unrouted -ne $null) {
                    [int]$latestRun.quality.final_unrouted
                } else { $null }

                $violations = if ($latestRun.drc.summary_violations -ne $null) {
                    [int]$latestRun.drc.summary_violations
                } elseif ($latestRun.quality.clearance_violations -ne $null) {
                    [int]$latestRun.quality.clearance_violations
                } else { $null }

                $score = Get-RunScoreValue $latestRun

                if (-not $failed -and $unrouted -ne $null -and $unrouted -eq 0) {
                    $allRouted++
                    if ($violations -ne $null -and $violations -eq 0) {
                        $perfects++
                    }
                }

                if (-not $failed -and $score -ne $null) {
                    [void]$avgScoreValues.Add($score)
                }
            }

            $avgScore = $null
            if ($avgScoreValues.Count -gt 0) {
                $avgScore = (($avgScoreValues | Measure-Object -Average).Average)
            }

            $versionStats[$version] = [PSCustomObject]@{
                Version      = $version
                FixtureCount = $fixtureCount
                Perfects     = $perfects
                AllRouted    = $allRouted
                Timeouts     = $timeouts
                Failures     = $failures
                AvgScore     = $avgScore
            }
        }

        $avgScoreStats = @($versionStats.Values | Where-Object { $_.AvgScore -ne $null })
        $maxAvgScore = $null
        if ($avgScoreStats.Count -gt 0) {
            $maxAvgScore = ($avgScoreStats | Measure-Object -Property AvgScore -Maximum).Maximum
        }

        $summaryHeaders = @("Version", "Fixtures", "Clean (0 DRC)", "Fully-Routed", "Timeouts", "Failures", "Avg. Score")
        $summaryAlignments = @("L", "R", "R", "R", "R", "R", "R")
        $summaryRows = [System.Collections.ArrayList]::new()

        $formatRatioPctFn = {
            param([int]$c, [int]$t)
            if ($t -le 0) { return "N/A" }
            $cStr = "{0,4}" -f $c
            $tStr = "{0,4}" -f $t
            $pct = ([double]$c / [double]$t) * 100.0
            $pStr = $pct.ToString("F1", [System.Globalization.CultureInfo]::InvariantCulture).PadLeft(5)
            return "$cStr/$tStr ($pStr%)"
        }

        foreach ($stat in ($versionStats.Values | Sort-Object -Property Version)) {
            $tot = $stat.FixtureCount
            $perfStr = & $formatRatioPctFn $stat.Perfects $tot
            $allStr  = & $formatRatioPctFn $stat.AllRouted $tot
            $toStr   = & $formatRatioPctFn $stat.Timeouts $tot
            $failStr = & $formatRatioPctFn $stat.Failures $tot

            $avgScoreStr = if ($stat.AvgScore -ne $null) {
                $formatted = $stat.AvgScore.ToString("F1", [System.Globalization.CultureInfo]::InvariantCulture)
                if ($maxAvgScore -ne $null -and $stat.AvgScore -eq $maxAvgScore) { "**$formatted**" } else { $formatted }
            } else {
                "N/A"
            }

            [void]$summaryRows.Add(@(
                $stat.Version,
                $stat.FixtureCount,
                $perfStr,
                $allStr,
                $toStr,
                $failStr,
                $avgScoreStr
            ))
        }

        $tableSb = [System.Text.StringBuilder]::new()
        [void]$tableSb.AppendLine("### $Title")
        if ($Description) {
            [void]$tableSb.AppendLine($Description)
        }
        [void]$tableSb.AppendLine()
        [void]$tableSb.AppendLine((Format-MarkdownTable $summaryHeaders $summaryAlignments $summaryRows))
        [void]$tableSb.AppendLine()
        return $tableSb.ToString()
    }

    [void]$sb.AppendLine("## Summary")
    [void]$sb.AppendLine()

    # 1. Overall Summary Table
    [void]$sb.Append((& $buildSummaryTableFn "Summary Table (All Tiers Combined)" $runs "Comprehensive performance across all benchmark fixtures."))

    # Segment by Tier
    $tierBuckets = [ordered]@{
        "A"     = @{ Title = "Tier A: Canary Gate"; Desc = "Fast-solving 2-layer boards (0 unrouted, 0 clearance violations expected)." }
        "B"     = @{ Title = "Tier B: Routine Benchmarks"; Desc = "Standard 2-4 layer boards evaluated for routine optimization progress." }
        "C"     = @{ Title = "Tier C: Complex / Multi-Layer"; Desc = "Dense and 6+ layer boards requiring deeper pathfinding." }
        "D"     = @{ Title = "Tier D: Extreme Stress / Diagnostic"; Desc = "High net-count and large surface-area stress boards." }
        "Other" = @{ Title = "General / Legacy Golden Fixtures"; Desc = "In-repo regression and golden fixture benchmark suite." }
    }

    foreach ($tKey in $tierBuckets.Keys) {
        $matchingRuns = @($runs | Where-Object { (& $getTierFn $_) -eq $tKey })
        if ($matchingRuns.Count -gt 0) {
            $b = $tierBuckets[$tKey]
            [void]$sb.Append((& $buildSummaryTableFn $b.Title $matchingRuns $b.Desc))
        }
    }

    $upArrowGreen = "$([char]0x2191)$([char]::ConvertFromUtf32(0x1F7E2))" # ↑🟢
    $downArrowRed = "$([char]0x2193)$([char]::ConvertFromUtf32(0x1F53B))" # ↓🔻

    # --- Per-Group and Per-Fixture sections ---
    foreach ($folderGroup in $groupedByFolder) {
        $folderName = $folderGroup.Name
        [void]$sb.AppendLine("## Group: [$folderName](../fixtures/$folderName)")
        [void]$sb.AppendLine()

        $fixturesInFolder = $folderGroup.Group | Group-Object -Property { $_.fixture.relative_path } | Sort-Object -Property Name
        foreach ($fixGroup in $fixturesInFolder) {
            $first = $fixGroup.Group[0]
            $metadata = $first.fixture

            [void]$sb.AppendLine("### Fixture: [$($metadata.filename)](../fixtures/$($metadata.relative_path))")
            [void]$sb.AppendLine()
            $dot = [char]183
            $sup2 = [char]178
            [void]$sb.AppendLine("Size: $([math]::Round($metadata.size_bytes / 1KB, 1)) kB $dot Layers: $($metadata.layer_count) $dot Nets: $($metadata.net_count) $dot Components: $($metadata.component_count) $dot Dimensions: $($metadata.board_width_mm) x $($metadata.board_height_mm) mm ($($metadata.board_area_cm2) cm$sup2) $dot CAD: $($metadata.host_cad) (v$($metadata.host_version))")
            [void]$sb.AppendLine()

            $latestRuns = @()
            $runsByVersion = $fixGroup.Group | Group-Object -Property { $_.binary.version_label }
            foreach ($verGroup in $runsByVersion) {
                $latest = $verGroup.Group | Sort-Object -Property { $_.run_at } -Descending | Select-Object -First 1
                $latestRuns += $latest
            }

            $sortedRuns = $latestRuns | Sort-Object -Property { $_.binary.version_label }

            $tableHeaders = @("Version", "Mode", "Fanout", "Fanout (s)", "Router (s)", "Opt. (s)", "Total (s)", "Passes", "Unrouted", "Violations", "Score", "Heap (MB)", "Alloc (GB)", "Warn/Err", "Notes")
            $tableAlignments = @("L", "L", "R", "R", "R", "R", "R", "R", "R", "R", "R", "R", "R", "R", "L")
            $tableRows = [System.Collections.ArrayList]::new()

            $prevUnrouted = $null
            $prevViolations = $null
            $prevScore = $null

            foreach ($run in $sortedRuns) {
                $ver = $run.binary.version_label
                $mode = if ($run.run_mode) { $run.run_mode } else { "N/A" }

                $fanoutVal = "N/A"
                if ($run.phases.fanout.log_found) {
                    $esc = $run.phases.fanout.escaped_pin_count
                    $tot = $run.phases.fanout.smd_pin_count
                    $pct = $run.phases.fanout.escape_rate_pct
                    if ($tot -gt 0 -and $esc -ne $null) {
                        $escStr = "{0,4}" -f $esc
                        $totStr = "{0,4}" -f $tot
                        $pctStr = ([double]$pct).ToString("F1", [System.Globalization.CultureInfo]::InvariantCulture).PadLeft(5)
                        $fanoutVal = "$escStr/$totStr ($pctStr%)"
                    }
                }

                $fanoutTime = if ($run.phases.fanout.duration_seconds -ne $null) { $run.phases.fanout.duration_seconds.ToString("F2", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }
                $routerTime = if ($run.phases.autorouter.duration_seconds -ne $null) { $run.phases.autorouter.duration_seconds.ToString("F2", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }
                $optTime = if ($run.phases.optimizer.duration_seconds -ne $null) { $run.phases.optimizer.duration_seconds.ToString("F2", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }

                # Compute total time
                $hasTime = $false
                $totalTimeVal = 0.0
                if ($run.phases.fanout.duration_seconds -ne $null) { $totalTimeVal += $run.phases.fanout.duration_seconds; $hasTime = $true }
                if ($run.phases.autorouter.duration_seconds -ne $null) { $totalTimeVal += $run.phases.autorouter.duration_seconds; $hasTime = $true }
                if ($run.phases.optimizer.duration_seconds -ne $null) { $totalTimeVal += $run.phases.optimizer.duration_seconds; $hasTime = $true }
                $totalTime = if ($hasTime) { $totalTimeVal.ToString("F2", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }

                # Passes formatted as fanout+router+optimizer
                $fanoutPassesVal = if ($run.phases.fanout.passes_completed -ne $null) { $run.phases.fanout.passes_completed } else { 0 }
                $routerPassesVal = if ($run.phases.autorouter.passes_completed -ne $null) { $run.phases.autorouter.passes_completed } else { 0 }
                $optimizerPassesVal = if ($run.phases.optimizer.passes_completed -ne $null) { $run.phases.optimizer.passes_completed } else { 0 }
                $fPass = "{0,3}" -f $fanoutPassesVal
                $rPass = "{0,3}" -f $routerPassesVal
                $oPass = "{0,3}" -f $optimizerPassesVal
                $passes = "$fPass+$rPass+$oPass"

                $hasCheckpointMetrics = $run.log_analysis.metric_source -and $run.log_analysis.metric_source -ne "none"
                $unroutedVal = if ($run.drc.final_unrouted -ne $null) {
                    $run.drc.final_unrouted
                } elseif ($hasCheckpointMetrics -and $run.quality.final_unrouted -ne $null) {
                    $run.quality.final_unrouted
                } else {
                    $null
                }
                $violationsVal = if ($run.drc.summary_violations -ne $null) {
                    $run.drc.summary_violations
                } elseif ($hasCheckpointMetrics -and $run.quality.clearance_violations -ne $null) {
                    $run.quality.clearance_violations
                } else {
                    $null
                }
                $scoreVal = if ($run.drc.final_quality_score -ne $null) { $run.drc.final_quality_score } elseif ($run.quality.quality_score -ne $null) { $run.quality.quality_score } else { $null }

                # Compute unrouted cell string
                $unroutedStr = if ($unroutedVal -ne $null) { "$unroutedVal" } else { "N/A" }

                # Compute violations cell string
                $violationsStr = if ($violationsVal -ne $null) { "$violationsVal" } else { "N/A" }

                # Compute score cell string
                $scoreStr = if ($scoreVal -ne $null) { $scoreVal.ToString("F0", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }

                $heap = if ($run.quality.peak_heap_mb -ne $null) { [math]::Round($run.quality.peak_heap_mb).ToString("F0", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }
                $alloc = if ($run.quality.total_allocated_gb -ne $null) { $run.quality.total_allocated_gb.ToString("F1", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }

                $warns = if ($run.log_analysis.warn_count -ne $null) { $run.log_analysis.warn_count } else { 0 }
                $errs = if ($run.log_analysis.error_count -ne $null) { $run.log_analysis.error_count } else { 0 }
                $warnErrStr = "$warns / $errs"

                # Check / parse notes from cache, fallback to log if not cached yet
                $loadError = $null
                $exceptions = $null
                $logTimedOut = $null
                if ($run.log_analysis.PSObject.Properties['load_error'] -ne $null) {
                    $loadError = $run.log_analysis.load_error
                }
                if ($run.log_analysis.PSObject.Properties['exceptions'] -ne $null) {
                    $exceptions = $run.log_analysis.exceptions
                }
                if ($run.log_analysis.PSObject.Properties['timed_out'] -ne $null) {
                    $logTimedOut = $run.log_analysis.timed_out
                }

                if (($loadError -eq $null -or $exceptions -eq $null -or $logTimedOut -eq $null) -and $run.log_file -and (Test-Path $run.log_file)) {
                    $logMetrics = Get-PhaseMetrics $run.log_file $run.binary.version_label
                    $loadError = $logMetrics.load_error
                    $exceptions = $logMetrics.exceptions
                    $logTimedOut = $logMetrics.timed_out
                    # Cache in-memory for the duration of this report run
                    if ($run.log_analysis.PSObject.Properties['load_error'] -eq $null) {
                        $run.log_analysis | Add-Member -NotePropertyName "load_error" -NotePropertyValue $loadError
                    } else {
                        $run.log_analysis.load_error = $loadError
                    }
                    if ($run.log_analysis.PSObject.Properties['exceptions'] -eq $null) {
                        $run.log_analysis | Add-Member -NotePropertyName "exceptions" -NotePropertyValue $exceptions
                    } else {
                        $run.log_analysis.exceptions = $exceptions
                    }
                    if ($run.log_analysis.PSObject.Properties['timed_out'] -eq $null) {
                        $run.log_analysis | Add-Member -NotePropertyName "timed_out" -NotePropertyValue $logTimedOut
                    } else {
                        $run.log_analysis.timed_out = $logTimedOut
                    }
                }

                $notes = @()
                if (-not $hasTime) {
                    $notes += "LOAD ERROR"
                } else {
                    if ($run.exit.timed_out -eq $true -or $logTimedOut -eq $true) {
                        $notes += "TIMEOUT"
                    }
                    if ($loadError -eq $true) {
                        $notes += "LOAD ERROR"
                    }
                }
                if ($exceptions) {
                    foreach ($exc in $exceptions) {
                        if ($notes -notcontains $exc) {
                            $notes += $exc
                        }
                    }
                }
                $notesStr = if ($notes.Count -gt 0) { $notes -join ", " } else { "" }

                $null = $tableRows.Add(@($ver, $mode, $fanoutVal, $fanoutTime, $routerTime, $optTime, $totalTime, $passes, $unroutedStr, $violationsStr, $scoreStr, $heap, $alloc, $warnErrStr, $notesStr))

                $prevUnrouted = $unroutedVal
                $prevViolations = $violationsVal
                $prevScore = $scoreVal
            }

            [void]$sb.AppendLine((Format-MarkdownTable $tableHeaders $tableAlignments $tableRows))
            [void]$sb.AppendLine()
        }
    }

    [System.IO.File]::WriteAllText($MdPath, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))

    # --- 3. CSV Export ---
    $csvHeaders = "fixture_group,fixture_name,version,run_mode,fanout_success,router_passes,drc_unrouted,drc_raw_violations,drc_clearance_violations,drc_summary_violations,drc_track_dangling,drc_via_dangling,drc_unconnected_items,drc_unconnected_findings,drc_score,drc_binary_version,drc_binary_sha256,drc_report_file,wall_time,cpu_time,peak_heap_mb,warn_count,error_count"
    $csvLines = @($csvHeaders)
    foreach ($run in $runs) {
        $fanoutSuccess = ""
        if ($run.phases.fanout.log_found -and $run.phases.fanout.smd_pin_count -gt 0) {
            $fanoutSuccess = "$($run.phases.fanout.escaped_pin_count)/$($run.phases.fanout.smd_pin_count)"
        }
        $passes = if ($run.phases.autorouter.passes_completed -ne $null) { $run.phases.autorouter.passes_completed } else { "" }

        $drcUnrouted = if ($run.drc.final_unrouted -ne $null) { $run.drc.final_unrouted } elseif ($run.quality.final_unrouted -ne $null) { $run.quality.final_unrouted } else { "" }
        $drcRawViolations = if ($run.drc.final_violations -ne $null) { $run.drc.final_violations } else { "" }
        $drcClearanceViolations = if ($run.drc.clearance_violations -ne $null) { $run.drc.clearance_violations } else { "" }
        $drcSummaryViolations = if ($run.drc.summary_violations -ne $null) { $run.drc.summary_violations } else { "" }
        $drcTrackDangling = if ($run.drc.dangling_tracks -ne $null) { $run.drc.dangling_tracks } else { "" }
        $drcViaDangling = if ($run.drc.dangling_vias -ne $null) { $run.drc.dangling_vias } else { "" }
        $drcUnconnectedItems = if ($run.drc.unconnected_items -ne $null) { $run.drc.unconnected_items } else { "" }
        $drcUnconnectedFindings = if ($run.drc.unconnected_findings -ne $null) { $run.drc.unconnected_findings } else { "" }
        $drcScore = if ($run.drc.final_quality_score -ne $null) { $run.drc.final_quality_score } elseif ($run.quality.quality_score -ne $null) { $run.quality.quality_score } else { "" }

        $wall = if ($run.quality.wall_clock_seconds -ne $null) { $run.quality.wall_clock_seconds } else { "" }
        $cpu = if ($run.quality.total_cpu_seconds -ne $null) { $run.quality.total_cpu_seconds } else { "" }
        $heap = if ($run.quality.peak_heap_mb -ne $null) { $run.quality.peak_heap_mb } else { "" }
        $warns = if ($run.log_analysis.warn_count -ne $null) { $run.log_analysis.warn_count } else { "0" }
        $errs = if ($run.log_analysis.error_count -ne $null) { $run.log_analysis.error_count } else { "0" }

        $line = "$($run.fixture.group),$($run.fixture.filename),$($run.binary.version_label),$($run.run_mode),$fanoutSuccess,$passes,$drcUnrouted,$drcRawViolations,$drcClearanceViolations,$drcSummaryViolations,$drcTrackDangling,$drcViaDangling,$drcUnconnectedItems,$drcUnconnectedFindings,$drcScore,$($run.drc.drc_binary_version),$($run.drc.drc_binary_sha256),$($run.drc.report_file),$wall,$cpu,$heap,$warns,$errs"
        $csvLines += $line
    }
    [System.IO.File]::WriteAllLines($CsvPath, $csvLines, [System.Text.UTF8Encoding]::new($false))

    # --- 4. Chart Data JSON Export ---
    $chartData = @()
    foreach ($run in $runs) {
        $chartScore = if ($run.drc.final_quality_score -ne $null) { $run.drc.final_quality_score } elseif ($run.quality.quality_score -ne $null) { $run.quality.quality_score } else { 0.0 }
        $chartUnrouted = if ($run.drc.final_unrouted -ne $null) { $run.drc.final_unrouted } elseif ($run.quality.final_unrouted -ne $null) { $run.quality.final_unrouted } else { 0 }

        $chartData += @{
            fixture  = $run.fixture.filename
            version  = $run.binary.version_label
            date     = $run.run_at
            score    = [double]$chartScore
            unrouted = [int]$chartUnrouted
            cpu_time = if ($run.quality.total_cpu_seconds -ne $null) { [double]$run.quality.total_cpu_seconds } else { 0.0 }
        }
    }
    $chartJson = ConvertTo-Json $chartData -Depth 5
    [System.IO.File]::WriteAllText($ChartDataPath, $chartJson, [System.Text.UTF8Encoding]::new($false))
}
