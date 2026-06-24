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

    $grouped = $runs | Group-Object -Property { $_.fixture.relative_path }

    # Build premium styled HTML
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.AppendLine("<div class='benchmark-container'>")
    [void]$sb.AppendLine("  <h2 class='benchmark-title'>Freerouting Benchmark Results</h2>")
    
    foreach ($g in $grouped) {
        $first = $g.Group[0]
        $fixName = $first.fixture.filename
        $fixGroup = $first.fixture.group

        [void]$sb.AppendLine("  <div class='fixture-section'>")
        [void]$sb.AppendLine("    <h3 class='fixture-title'>$fixGroup / $fixName</h3>")
        [void]$sb.AppendLine("    <p class='fixture-meta'>Layers: $($first.fixture.layer_count) | Nets: $($first.fixture.net_count) | Components: $($first.fixture.component_count) | Area: $($first.fixture.board_area_cm2) cm²</p>")
        
        [void]$sb.AppendLine("    <table class='benchmark-table'>")
        [void]$sb.AppendLine("      <thead>")
        [void]$sb.AppendLine("        <tr class='header-row'>")
        [void]$sb.AppendLine("          <th>Version</th>")
        [void]$sb.AppendLine("          <th>Mode</th>")
        [void]$sb.AppendLine("          <th>Fanout</th>")
        [void]$sb.AppendLine("          <th>Router Time</th>")
        [void]$sb.AppendLine("          <th>Passes</th>")
        [void]$sb.AppendLine("          <th>Unrouted (DRC)</th>")
        [void]$sb.AppendLine("          <th>Violations (DRC)</th>")
        [void]$sb.AppendLine("          <th>Score (DRC)</th>")
        [void]$sb.AppendLine("          <th>Peak Heap</th>")
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
            $routerTime = if ($run.phases.autorouter.duration_seconds -ne $null) { "$($run.phases.autorouter.duration_seconds)s" } else { "N/A" }
            $passes = if ($run.phases.autorouter.passes_completed -ne $null) { $run.phases.autorouter.passes_completed } else { "N/A" }
            $unrouted = if ($run.drc.final_unrouted -ne $null) { $run.drc.final_unrouted } elseif ($run.quality.final_unrouted -ne $null) { $run.quality.final_unrouted } else { 0 }
            $violations = if ($run.drc.final_violations -ne $null) { $run.drc.final_violations } elseif ($run.quality.clearance_violations -ne $null) { $run.quality.clearance_violations } else { 0 }
            $scoreVal = if ($run.drc.final_quality_score -ne $null) { $run.drc.final_quality_score } elseif ($run.quality.quality_score -ne $null) { $run.quality.quality_score } else { $null }
            $score = if ($scoreVal -ne $null) { $scoreVal.ToString("F2") } else { "N/A" }
            $heap = if ($run.quality.peak_heap_mb -ne $null) { "$($run.quality.peak_heap_mb) MB" } else { "N/A" }
            $warns = if ($run.log_analysis.warn_count -ne $null) { $run.log_analysis.warn_count } else { 0 }
            $errs = if ($run.log_analysis.error_count -ne $null) { $run.log_analysis.error_count } else { 0 }

            [void]$sb.AppendLine("        <tr class='data-row'>")
            [void]$sb.AppendLine("          <td>$ver</td>")
            [void]$sb.AppendLine("          <td>$mode</td>")
            [void]$sb.AppendLine("          <td class='text-right'>$fanoutVal</td>")
            [void]$sb.AppendLine("          <td class='text-right'>$routerTime</td>")
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

    $htmlContent = Get-Content $HtmlPath -Raw
    
    $pattern = '(?s)<!-- BENCHMARK_TABLE_START -->.*?<!-- BENCHMARK_TABLE_END -->'
    $replacement = "<!-- BENCHMARK_TABLE_START -->`r`n$($sb.ToString())`r`n<!-- BENCHMARK_TABLE_END -->"
    
    $patchedContent = [regex]::Replace($htmlContent, $pattern, $replacement)

    $tempHtml = "$HtmlPath.tmp"
    [System.IO.File]::WriteAllText($tempHtml, $patchedContent)
    if (Test-Path $tempHtml) {
        if (Test-Path $HtmlPath) {
            Remove-Item $HtmlPath -Force
        }
        Move-Item -Path $tempHtml -Destination $HtmlPath -Force
    }
}
