function Export-MarkdownReport {
    param(
        [Hashtable]$Cache,
        [string]$MdPath,
        [string]$CsvPath,
        [string]$ChartDataPath
    )

    # 1. Sort and group runs by fixture
    $runs = @()
    foreach ($key in $Cache.Keys) {
        $runs += $Cache[$key]
    }

    $grouped = $runs | Group-Object -Property { $_.fixture.relative_path }
    $groupedByFolder = $runs | Group-Object -Property { $_.fixture.group }

    # 2. Build Markdown content
    $sb = [System.Text.StringBuilder]::new()
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    [void]$sb.AppendLine("# Freerouting Nightly Benchmarks Report")
    [void]$sb.AppendLine("Generated on: $ts")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("This report lists the latest benchmark run results for each Freerouting version and fixture combination.")
    [void]$sb.AppendLine()

    # --- Summary Table ---
    [void]$sb.AppendLine("## Summary Table (Best Results per Fixture)")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("| Fixture Group | Fixture | Best Version | Unrouted (DRC) | Violations (DRC) | Score (DRC) | CPU Time | Peak Heap |")
    [void]$sb.AppendLine("|---------------|---------|--------------|----------------|------------------|-------------|----------|-----------|")

    foreach ($g in $grouped) {
        $bestRun = $g.Group | Sort-Object -Property {
            $unrouted = if ($_.drc.final_unrouted -ne $null) { $_.drc.final_unrouted } else { 99999 }
            $violations = if ($_.drc.final_violations -ne $null) { $_.drc.final_violations } else { 99999 }
            $score = if ($_.drc.final_quality_score -ne $null) { $_.drc.final_quality_score } else { 0.0 }
            return ($unrouted * 1000000) + ($violations * 1000) - $score
        } | Select-Object -First 1

        if ($bestRun) {
            $groupName = $bestRun.fixture.group
            $filename = $bestRun.fixture.filename
            $version = $bestRun.binary.version_label
            $unrouted = if ($bestRun.drc.final_unrouted -ne $null) { $bestRun.drc.final_unrouted } else { "N/A" }
            $violations = if ($bestRun.drc.final_violations -ne $null) { $bestRun.drc.final_violations } else { "N/A" }
            $score = if ($bestRun.drc.final_quality_score -ne $null) { $bestRun.drc.final_quality_score.ToString("F2") } else { "N/A" }
            $cpu = if ($bestRun.quality.total_cpu_seconds -ne $null) { "$($bestRun.quality.total_cpu_seconds)s" } else { "N/A" }
            $heap = if ($bestRun.quality.peak_heap_mb -ne $null) { "$($bestRun.quality.peak_heap_mb) MB" } else { "N/A" }

            [void]$sb.AppendLine("| $groupName | $filename | **$version** | $unrouted | $violations | $score | $cpu | $heap |")
        }
    }
    [void]$sb.AppendLine()

    # Define trend arrows using Unicode hex escapes (Windows-safe)
    $upArrowGreen = "$([char]0x2191)$([char]::ConvertFromUtf32(0x1F7E2))" # ↑🟢
    $downArrowRed = "$([char]0x2193)$([char]::ConvertFromUtf32(0x1F53B))" # ↓🔻

    # --- Per-Group and Per-Fixture sections ---
    foreach ($folderGroup in $groupedByFolder) {
        $folderName = $folderGroup.Name
        [void]$sb.AppendLine("## Group: $folderName")
        [void]$sb.AppendLine()

        $fixturesInFolder = $folderGroup.Group | Group-Object -Property { $_.fixture.relative_path }
        foreach ($fixGroup in $fixturesInFolder) {
            $first = $fixGroup.Group[0]
            $metadata = $first.fixture

            [void]$sb.AppendLine("### Fixture: $($metadata.filename)")
            [void]$sb.AppendLine()
            [void]$sb.AppendLine("Size: $([math]::Round($metadata.size_bytes / 1KB, 1)) kB · Layers: $($metadata.layer_count) · Nets: $($metadata.net_count) · Components: $($metadata.component_count)")
            [void]$sb.AppendLine("Dimensions: $($metadata.board_width_mm) x $($metadata.board_height_mm) mm ($($metadata.board_area_cm2) cm²)")
            [void]$sb.AppendLine("CAD: $($metadata.host_cad) (v$($metadata.host_version))")
            [void]$sb.AppendLine()

            # Version Table
            [void]$sb.AppendLine("| Version | Mode | Fanout | Fanout Time | Router Time | Optimizer Time | Passes | Unrouted (DRC) | Violations (DRC) | Score (DRC) | Peak Heap | Total Alloc | Warn/Err |")
            [void]$sb.AppendLine("|---------|------|--------|-------------|-------------|----------------|--------|----------------|------------------|-------------|-----------|-------------|----------|")

            # Filter to keep only the latest run for each version
            $latestRuns = @()
            $runsByVersion = $fixGroup.Group | Group-Object -Property { $_.binary.version_label }
            foreach ($verGroup in $runsByVersion) {
                $latest = $verGroup.Group | Sort-Object -Property { $_.run_at } -Descending | Select-Object -First 1
                $latestRuns += $latest
            }

            $sortedRuns = $latestRuns | Sort-Object -Property {
                $ver = $_.binary.version_label
                if ($ver -match '^s(\d+)\.(\d+)\.(\d+)') {
                    return 99999999 + [int]"$($matches[1])$($matches[2])$($matches[3])"
                }
                if ($ver -match '^(\d+)\.(\d+)\.(\d+)') {
                    return ([int]$matches[1] * 10000) + ([int]$matches[2] * 100) + [int]$matches[3]
                }
                return 0
            }

            $prevUnrouted = $null
            $prevViolations = $null
            $prevScore = $null

            foreach ($run in $sortedRuns) {
                $ver = $run.binary.version_label
                $mode = $run.run_mode
                
                $fanoutVal = "N/A"
                if ($run.phases.fanout.log_found) {
                    $esc = $run.phases.fanout.escaped_pin_count
                    $tot = $run.phases.fanout.smd_pin_count
                    $pct = $run.phases.fanout.escape_rate_pct
                    if ($tot -gt 0) {
                        $fanoutVal = "$esc/$tot ($pct%)"
                    }
                }

                $fanoutTime = if ($run.phases.fanout.duration_seconds -ne $null) { "$($run.phases.fanout.duration_seconds)s" } else { "N/A" }
                $routerTime = if ($run.phases.autorouter.duration_seconds -ne $null) { "$($run.phases.autorouter.duration_seconds)s" } else { "N/A" }
                $optTime = if ($run.phases.optimizer.duration_seconds -ne $null) { "$($run.phases.optimizer.duration_seconds)s" } else { "N/A" }
                $passes = if ($run.phases.autorouter.passes_completed -ne $null) { $run.phases.autorouter.passes_completed } else { "N/A" }

                $unroutedVal = if ($run.drc.final_unrouted -ne $null) { $run.drc.final_unrouted } else { 0 }
                $violationsVal = if ($run.drc.final_violations -ne $null) { $run.drc.final_violations } else { 0 }
                $scoreVal = if ($run.drc.final_quality_score -ne $null) { $run.drc.final_quality_score } else { 0.0 }

                # Compute unrouted cell string
                $unroutedStr = "$unroutedVal"
                if ($prevUnrouted -ne $null) {
                    if ($unroutedVal -lt $prevUnrouted) { $unroutedStr += " ($upArrowGreen)" }
                    elseif ($unroutedVal -gt $prevUnrouted) { $unroutedStr += " ($downArrowRed)" }
                }
                
                # Compute violations cell string
                $violationsStr = "$violationsVal"
                if ($prevViolations -ne $null) {
                    if ($violationsVal -lt $prevViolations) { $violationsStr += " ($upArrowGreen)" }
                    elseif ($violationsVal -gt $prevViolations) { $violationsStr += " ($downArrowRed)" }
                }

                # Compute score cell string
                $scoreStr = if ($run.drc.final_quality_score -ne $null) { $scoreVal.ToString("F2") } else { "N/A" }
                if ($run.drc.final_quality_score -ne $null -and $prevScore -ne $null) {
                    if ($scoreVal -gt $prevScore) { $scoreStr += " ($upArrowGreen)" }
                    elseif ($scoreVal -lt $prevScore) { $scoreStr += " ($downArrowRed)" }
                }

                $heap = if ($run.quality.peak_heap_mb -ne $null) { "$($run.quality.peak_heap_mb) MB" } else { "N/A" }
                $alloc = if ($run.quality.total_allocated_gb -ne $null) { "$($run.quality.total_allocated_gb) GB" } else { "N/A" }

                $warns = if ($run.log_analysis.warn_count -ne $null) { $run.log_analysis.warn_count } else { 0 }
                $errs = if ($run.log_analysis.error_count -ne $null) { $run.log_analysis.error_count } else { 0 }
                $warnErrStr = "$warns / $errs"

                [void]$sb.AppendLine("| $ver | $mode | $fanoutVal | $fanoutTime | $routerTime | $optTime | $passes | $unroutedStr | $violationsStr | $scoreStr | $heap | $alloc | $warnErrStr |")

                $prevUnrouted = $unroutedVal
                $prevViolations = $violationsVal
                $prevScore = $scoreVal
            }
            [void]$sb.AppendLine()
        }
    }

    [System.IO.File]::WriteAllText($MdPath, $sb.ToString())

    # --- 3. CSV Export ---
    $csvHeaders = "fixture_group,fixture_name,version,run_mode,fanout_success,router_passes,drc_unrouted,drc_violations,drc_score,wall_time,cpu_time,peak_heap_mb,warn_count,error_count"
    $csvLines = @($csvHeaders)
    foreach ($run in $runs) {
        $fanoutSuccess = ""
        if ($run.phases.fanout.log_found -and $run.phases.fanout.smd_pin_count -gt 0) {
            $fanoutSuccess = "$($run.phases.fanout.escaped_pin_count)/$($run.phases.fanout.smd_pin_count)"
        }
        $passes = if ($run.phases.autorouter.passes_completed -ne $null) { $run.phases.autorouter.passes_completed } else { "" }
        $drcUnrouted = if ($run.drc.final_unrouted -ne $null) { $run.drc.final_unrouted } else { "" }
        $drcViolations = if ($run.drc.final_violations -ne $null) { $run.drc.final_violations } else { "" }
        $drcScore = if ($run.drc.final_quality_score -ne $null) { $run.drc.final_quality_score } else { "" }
        $wall = if ($run.quality.wall_clock_seconds -ne $null) { $run.quality.wall_clock_seconds } else { "" }
        $cpu = if ($run.quality.total_cpu_seconds -ne $null) { $run.quality.total_cpu_seconds } else { "" }
        $heap = if ($run.quality.peak_heap_mb -ne $null) { $run.quality.peak_heap_mb } else { "" }
        $warns = if ($run.log_analysis.warn_count -ne $null) { $run.log_analysis.warn_count } else { "0" }
        $errs = if ($run.log_analysis.error_count -ne $null) { $run.log_analysis.error_count } else { "0" }

        $line = "$($run.fixture.group),$($run.fixture.filename),$($run.binary.version_label),$($run.run_mode),$fanoutSuccess,$passes,$drcUnrouted,$drcViolations,$drcScore,$wall,$cpu,$heap,$warns,$errs"
        $csvLines += $line
    }
    [System.IO.File]::WriteAllLines($CsvPath, $csvLines)

    # --- 4. Chart Data JSON Export ---
    $chartData = @()
    foreach ($run in $runs) {
        $chartData += @{
            fixture  = $run.fixture.filename
            version  = $run.binary.version_label
            date     = $run.run_at
            score    = if ($run.drc.final_quality_score -ne $null) { [double]$run.drc.final_quality_score } else { 0.0 }
            unrouted = if ($run.drc.final_unrouted -ne $null) { [int]$run.drc.final_unrouted } else { 0 }
            cpu_time = if ($run.quality.total_cpu_seconds -ne $null) { [double]$run.quality.total_cpu_seconds } else { 0.0 }
        }
    }
    $chartJson = ConvertTo-Json $chartData -Depth 5
    [System.IO.File]::WriteAllText($ChartDataPath, $chartJson)
}
