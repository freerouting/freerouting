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

function Export-MarkdownReport {
    param(
        [Hashtable]$Cache,
        [string]$MdPath,
        [string]$CsvPath,
        [string]$ChartDataPath
    )

    $runs = @()
    foreach ($key in $Cache.Keys) {
        $runs += $Cache[$key]
    }

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

    # --- Summary Table ---
    [void]$sb.AppendLine("## Summary Table (Best Results per Fixture)")
    [void]$sb.AppendLine()

    $summaryHeaders = @("Fixture Group", "Fixture", "Best Version", "Unrouted", "Violations", "Score", "CPU Time (s)", "Peak Heap (MB)")
    $summaryAlignments = @("L", "L", "L", "R", "R", "R", "R", "R")
    $summaryRows = [System.Collections.ArrayList]::new()

    foreach ($g in $grouped) {
        $bestRun = $g.Group | Sort-Object -Property @{ Expression = {
            if ($_.drc.final_unrouted -ne $null) { $_.drc.final_unrouted } elseif ($_.quality.final_unrouted -ne $null) { $_.quality.final_unrouted } else { 99999 }
        }; Ascending = $true }, @{ Expression = {
            if ($_.drc.final_violations -ne $null) { $_.drc.final_violations } elseif ($_.quality.clearance_violations -ne $null) { $_.quality.clearance_violations } else { 99999 }
        }; Ascending = $true }, @{ Expression = {
            if ($_.drc.final_quality_score -ne $null) { $_.drc.final_quality_score } elseif ($_.quality.quality_score -ne $null) { $_.quality.quality_score } else { 0.0 }
        }; Descending = $true }, @{ Expression = {
            $ver = $_.binary.version_label
            if ($ver -match '^s(\d+)\.(\d+)\.(\d+)') {
                return 99999999 + [int]"$($matches[1])$($matches[2])$($matches[3])"
            }
            if ($ver -match '^(\d+)\.(\d+)\.(\d+)') {
                return ([int]$matches[1] * 10000) + ([int]$matches[2] * 100) + [int]$matches[3]
            }
            return 0
        }; Descending = $true } | Select-Object -First 1

        if ($bestRun) {
            $groupName = $bestRun.fixture.group
            $filename = $bestRun.fixture.filename
            $version = $bestRun.binary.version_label
            
            $unrouted = if ($bestRun.drc.final_unrouted -ne $null) { $bestRun.drc.final_unrouted } elseif ($bestRun.quality.final_unrouted -ne $null) { $bestRun.quality.final_unrouted } else { "N/A" }
            $violations = if ($bestRun.drc.final_violations -ne $null) { $bestRun.drc.final_violations } elseif ($bestRun.quality.clearance_violations -ne $null) { $bestRun.quality.clearance_violations } else { "N/A" }
            $score = if ($bestRun.drc.final_quality_score -ne $null) { $bestRun.drc.final_quality_score.ToString("F0", [System.Globalization.CultureInfo]::InvariantCulture) } elseif ($bestRun.quality.quality_score -ne $null) { $bestRun.quality.quality_score.ToString("F0", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }
            
            $cpu = if ($bestRun.quality.total_cpu_seconds -ne $null) { $bestRun.quality.total_cpu_seconds.ToString("F2", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }
            $heap = if ($bestRun.quality.peak_heap_mb -ne $null) { [math]::Round($bestRun.quality.peak_heap_mb).ToString("F0", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }

            # Create markdown links
            $groupLink = "[$groupName](../fixtures/$groupName)"
            $fixtureLink = "[$filename](../fixtures/$($bestRun.fixture.relative_path))"

            $null = $summaryRows.Add(@($groupLink, $fixtureLink, "**$version**", $unrouted, $violations, $score, $cpu, $heap))
        }
    }
    
    $sortedSummaryRows = $summaryRows | Sort-Object -Property { $_[0] }, { $_[1] }
    [void]$sb.AppendLine((Format-MarkdownTable $summaryHeaders $summaryAlignments $sortedSummaryRows))
    [void]$sb.AppendLine()

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

            $tableHeaders = @("Version", "Mode", "Fanout", "Fanout Time (s)", "Router Time (s)", "Optimizer Time (s)", "Total Time (s)", "Passes", "Unrouted", "Violations", "Score", "Peak Heap (MB)", "Total Alloc (GB)", "Warn/Err")
            $tableAlignments = @("L", "L", "R", "R", "R", "R", "R", "R", "R", "R", "R", "R", "R", "R")
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
                        $fanoutVal = "$esc/$tot ($pct%)"
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
                $fanoutPasses = if ($run.phases.fanout.passes_completed -ne $null) { $run.phases.fanout.passes_completed } else { 0 }
                $routerPasses = if ($run.phases.autorouter.passes_completed -ne $null) { $run.phases.autorouter.passes_completed } else { 0 }
                $optimizerPasses = if ($run.phases.optimizer.passes_completed -ne $null) { $run.phases.optimizer.passes_completed } else { 0 }
                $passes = "$fanoutPasses+$routerPasses+$optimizerPasses"

                $unroutedVal = if ($run.drc.final_unrouted -ne $null) { $run.drc.final_unrouted } elseif ($run.quality.final_unrouted -ne $null) { $run.quality.final_unrouted } else { 0 }
                $violationsVal = if ($run.drc.final_violations -ne $null) { $run.drc.final_violations } elseif ($run.quality.clearance_violations -ne $null) { $run.quality.clearance_violations } else { 0 }
                $scoreVal = if ($run.drc.final_quality_score -ne $null) { $run.drc.final_quality_score } elseif ($run.quality.quality_score -ne $null) { $run.quality.quality_score } else { $null }

                # Compute unrouted cell string
                $unroutedStr = "$unroutedVal"
                
                # Compute violations cell string
                $violationsStr = "$violationsVal"

                # Compute score cell string
                $scoreStr = if ($scoreVal -ne $null) { $scoreVal.ToString("F0", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }
 
                $heap = if ($run.quality.peak_heap_mb -ne $null) { [math]::Round($run.quality.peak_heap_mb).ToString("F0", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }
                $alloc = if ($run.quality.total_allocated_gb -ne $null) { $run.quality.total_allocated_gb.ToString("F1", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }

                $warns = if ($run.log_analysis.warn_count -ne $null) { $run.log_analysis.warn_count } else { 0 }
                $errs = if ($run.log_analysis.error_count -ne $null) { $run.log_analysis.error_count } else { 0 }
                $warnErrStr = "$warns / $errs"

                $null = $tableRows.Add(@($ver, $mode, $fanoutVal, $fanoutTime, $routerTime, $optTime, $totalTime, $passes, $unroutedStr, $violationsStr, $scoreStr, $heap, $alloc, $warnErrStr))

                $prevUnrouted = $unroutedVal
                $prevViolations = $violationsVal
                $prevScore = $scoreVal
            }

            [void]$sb.AppendLine((Format-MarkdownTable $tableHeaders $tableAlignments $tableRows))
            [void]$sb.AppendLine()
        }
    }

    [System.IO.File]::WriteAllText($MdPath, $sb.ToString(), [System.Text.Encoding]::UTF8)

    # --- 3. CSV Export ---
    $csvHeaders = "fixture_group,fixture_name,version,run_mode,fanout_success,router_passes,drc_unrouted,drc_violations,drc_score,wall_time,cpu_time,peak_heap_mb,warn_count,error_count"
    $csvLines = @($csvHeaders)
    foreach ($run in $runs) {
        $fanoutSuccess = ""
        if ($run.phases.fanout.log_found -and $run.phases.fanout.smd_pin_count -gt 0) {
            $fanoutSuccess = "$($run.phases.fanout.escaped_pin_count)/$($run.phases.fanout.smd_pin_count)"
        }
        $passes = if ($run.phases.autorouter.passes_completed -ne $null) { $run.phases.autorouter.passes_completed } else { "" }
        
        $drcUnrouted = if ($run.drc.final_unrouted -ne $null) { $run.drc.final_unrouted } elseif ($run.quality.final_unrouted -ne $null) { $run.quality.final_unrouted } else { "" }
        $drcViolations = if ($run.drc.final_violations -ne $null) { $run.drc.final_violations } elseif ($run.quality.clearance_violations -ne $null) { $run.quality.clearance_violations } else { "" }
        $drcScore = if ($run.drc.final_quality_score -ne $null) { $run.drc.final_quality_score } elseif ($run.quality.quality_score -ne $null) { $run.quality.quality_score } else { "" }
        
        $wall = if ($run.quality.wall_clock_seconds -ne $null) { $run.quality.wall_clock_seconds } else { "" }
        $cpu = if ($run.quality.total_cpu_seconds -ne $null) { $run.quality.total_cpu_seconds } else { "" }
        $heap = if ($run.quality.peak_heap_mb -ne $null) { $run.quality.peak_heap_mb } else { "" }
        $warns = if ($run.log_analysis.warn_count -ne $null) { $run.log_analysis.warn_count } else { "0" }
        $errs = if ($run.log_analysis.error_count -ne $null) { $run.log_analysis.error_count } else { "0" }

        $line = "$($run.fixture.group),$($run.fixture.filename),$($run.binary.version_label),$($run.run_mode),$fanoutSuccess,$passes,$drcUnrouted,$drcViolations,$drcScore,$wall,$cpu,$heap,$warns,$errs"
        $csvLines += $line
    }
    [System.IO.File]::WriteAllLines($CsvPath, $csvLines, [System.Text.Encoding]::UTF8)

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
    [System.IO.File]::WriteAllText($ChartDataPath, $chartJson, [System.Text.Encoding]::UTF8)
}
