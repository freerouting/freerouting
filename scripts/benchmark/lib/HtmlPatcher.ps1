function Update-BenchmarksHtml {
    param(
        [Hashtable]$Cache,
        [string]$HtmlPath
    )

    if (-not (Test-Path $HtmlPath)) {
        Write-Warning "Website HTML path not found: $HtmlPath"
        return
    }

    $runs = @()
    foreach ($key in $Cache.Keys) {
        $runs += $Cache[$key]
    }

    $grouped = $runs | Group-Object -Property { $_.fixture.relative_path } | Sort-Object -Property Name

    # Build premium styled HTML
    $sb = [System.Text.StringBuilder]::new()
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $sysInfo = Get-SystemInfo
    [void]$sb.AppendLine("<div class='benchmark-container'>")
    [void]$sb.AppendLine("  <div class='benchmark-report-info'>Generated on: $ts &middot; System: $($sysInfo.cpu_name) ($($sysInfo.cpu_physical_cores) Cores, $($sysInfo.total_ram_gb) GB RAM)</div>")
    
    foreach ($g in $grouped) {
        $first = $g.Group[0]
        $fixName = $first.fixture.filename
        $fixGroup = $first.fixture.group

        [void]$sb.AppendLine("  <div class='fixture-section'>")
        [void]$sb.AppendLine("    <h3 class='fixture-title'>$fixGroup / $fixName</h3>")
        $sizeKb = [math]::Round($first.fixture.size_bytes / 1KB, 1)
        $dot = " &middot; "
        [void]$sb.AppendLine("    <p class='fixture-meta'>Size: $($sizeKb) kB $dot Layers: $($first.fixture.layer_count) $dot Nets: $($first.fixture.net_count) $dot Components: $($first.fixture.component_count) $dot Dimensions: $($first.fixture.board_width_mm) x $($first.fixture.board_height_mm) mm ($($first.fixture.board_area_cm2) cm$([char]178)) $dot CAD: $($first.fixture.host_cad) (v$($first.fixture.host_version))</p>")
        
        [void]$sb.AppendLine("    <table class='benchmark-table'>")
        [void]$sb.AppendLine("      <thead>")
        [void]$sb.AppendLine("        <tr class='header-row'>")
        [void]$sb.AppendLine("          <th>Version</th>")
        [void]$sb.AppendLine("          <th>Mode</th>")
        [void]$sb.AppendLine("          <th>Total Time (s)</th>")
        [void]$sb.AppendLine("          <th>Passes</th>")
        [void]$sb.AppendLine("          <th>Unrouted</th>")
        [void]$sb.AppendLine("          <th>Violations</th>")
        [void]$sb.AppendLine("          <th>Score</th>")
        [void]$sb.AppendLine("          <th>Peak Heap (MB)</th>")
        [void]$sb.AppendLine("          <th>Warn/Err</th>")
        [void]$sb.AppendLine("        </tr>")
        [void]$sb.AppendLine("      </thead>")
        [void]$sb.AppendLine("      <tbody>")

        # Filter to keep only the latest run for each version
        $latestRuns = @()
        $runsByVersion = $g.Group | Group-Object -Property { $_.binary.version_label }
        foreach ($verGroup in $runsByVersion) {
            $latest = $verGroup.Group | Sort-Object -Property { $_.run_at } -Descending | Select-Object -First 1
            $latestRuns += $latest
        }

        $sortedRuns = $latestRuns | Sort-Object -Property { $_.binary.version_label }

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

            $unrouted = if ($run.drc.final_unrouted -ne $null) { $run.drc.final_unrouted } elseif ($run.quality.final_unrouted -ne $null) { $run.quality.final_unrouted } else { 0 }
            $violations = if ($run.drc.final_violations -ne $null) { $run.drc.final_violations } elseif ($run.quality.clearance_violations -ne $null) { $run.quality.clearance_violations } else { 0 }
            $scoreVal = if ($run.drc.final_quality_score -ne $null) { $run.drc.final_quality_score } elseif ($run.quality.quality_score -ne $null) { $run.quality.quality_score } else { $null }
            $score = if ($scoreVal -ne $null) { $scoreVal.ToString("F0", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }
            $heap = if ($run.quality.peak_heap_mb -ne $null) { [math]::Round($run.quality.peak_heap_mb).ToString("F0", [System.Globalization.CultureInfo]::InvariantCulture) } else { "N/A" }
            $warns = if ($run.log_analysis.warn_count -ne $null) { $run.log_analysis.warn_count } else { 0 }
            $errs = if ($run.log_analysis.error_count -ne $null) { $run.log_analysis.error_count } else { 0 }

            [void]$sb.AppendLine("        <tr class='data-row'>")
            [void]$sb.AppendLine("          <td>$ver</td>")
            [void]$sb.AppendLine("          <td>$mode</td>")
            [void]$sb.AppendLine("          <td class='text-right'>$totalTime</td>")
            [void]$sb.AppendLine("          <td class='text-right'>$passes</td>")
            [void]$sb.AppendLine("          <td class='text-right'>$unrouted</td>")
            [void]$sb.AppendLine("          <td class='text-right'>$violations</td>")
            [void]$sb.AppendLine("          <td class='text-right'>$score</td>")
            [void]$sb.AppendLine("          <td class='text-right'>$heap</td>")
            [void]$sb.AppendLine("          <td class='text-right'>$warns / $errs</td>")
            [void]$sb.AppendLine("        </tr>")
        }
        [void]$sb.AppendLine("      </tbody>")
        [void]$sb.AppendLine("    </table>")
        [void]$sb.AppendLine("  </div>")
    }
    [void]$sb.AppendLine("</div>")

    $indentedHtml = ""
    foreach ($line in $sb.ToString().Split("`n")) {
        $trimmed = $line.TrimEnd("`r")
        if ($trimmed.Length -gt 0) {
            $indentedHtml += "        $trimmed`r`n"
        }
    }

    $htmlContent = [System.IO.File]::ReadAllText($HtmlPath, [System.Text.UTF8Encoding]::new($false))
    
    $pattern = '(?s)<!-- BENCHMARK_TABLE_START -->.*?<!-- BENCHMARK_TABLE_END -->'
    $replacement = "<!-- BENCHMARK_TABLE_START -->`r`n$indentedHtml        <!-- BENCHMARK_TABLE_END -->"
    
    $patchedContent = [regex]::Replace($htmlContent, $pattern, $replacement)
 
    $tempHtml = "$HtmlPath.tmp"
    [System.IO.File]::WriteAllText($tempHtml, $patchedContent, [System.Text.UTF8Encoding]::new($false))
    if (Test-Path $tempHtml) {
        if (Test-Path $HtmlPath) {
            Remove-Item $HtmlPath -Force
        }
        Move-Item -Path $tempHtml -Destination $HtmlPath -Force
    }
}
