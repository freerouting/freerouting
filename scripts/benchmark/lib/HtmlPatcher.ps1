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
    [void]$sb.AppendLine("<div class='benchmark-container' style='font-family: sans-serif; max-width: 1200px; margin: 0 auto; padding: 20px; color: rgb(232, 204, 135); background-color: rgb(1, 58, 32); border-radius: 8px;'>")
    [void]$sb.AppendLine("  <h2 style='text-align: center; border-bottom: 2px solid rgb(232, 204, 135); padding-bottom: 10px;'>Freerouting Benchmark Results</h2>")
    
    foreach ($g in $grouped) {
        $first = $g.Group[0]
        $fixName = $first.fixture.filename
        $fixGroup = $first.fixture.group

        [void]$sb.AppendLine("  <div class='fixture-section' style='margin-top: 30px; margin-bottom: 30px;'>")
        [void]$sb.AppendLine("    <h3 style='color: rgb(232, 204, 135); border-bottom: 1px solid rgba(232,204,135,0.3); padding-bottom: 5px;'>$fixGroup / $fixName</h3>")
        [void]$sb.AppendLine("    <p style='font-size: 0.9em; opacity: 0.8;'>Layers: $($first.fixture.layer_count) | Nets: $($first.fixture.net_count) | Components: $($first.fixture.component_count) | Area: $($first.fixture.board_area_cm2) cm²</p>")
        
        [void]$sb.AppendLine("    <table style='width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 0.9em;'>")
        [void]$sb.AppendLine("      <thead>")
        [void]$sb.AppendLine("        <tr style='background-color: rgba(232, 204, 135, 0.1); text-align: left;'>")
        [void]$sb.AppendLine("          <th style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.3);'>Version</th>")
        [void]$sb.AppendLine("          <th style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.3);'>Mode</th>")
        [void]$sb.AppendLine("          <th style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.3);'>Fanout</th>")
        [void]$sb.AppendLine("          <th style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.3);'>Router Time</th>")
        [void]$sb.AppendLine("          <th style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.3);'>Passes</th>")
        [void]$sb.AppendLine("          <th style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.3);'>Unrouted (DRC)</th>")
        [void]$sb.AppendLine("          <th style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.3);'>Violations (DRC)</th>")
        [void]$sb.AppendLine("          <th style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.3);'>Score (DRC)</th>")
        [void]$sb.AppendLine("          <th style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.3);'>Peak Heap</th>")
        [void]$sb.AppendLine("          <th style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.3);'>Warn/Err</th>")
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

            [void]$sb.AppendLine("        <tr style='border-bottom: 1px solid rgba(232, 204, 135, 0.15);'>")
            [void]$sb.AppendLine("          <td style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.15);'>$ver</td>")
            [void]$sb.AppendLine("          <td style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.15);'>$mode</td>")
            [void]$sb.AppendLine("          <td style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.15);'>$fanoutVal</td>")
            [void]$sb.AppendLine("          <td style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.15);'>$routerTime</td>")
            [void]$sb.AppendLine("          <td style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.15);'>$passes</td>")
            [void]$sb.AppendLine("          <td style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.15);'>$unrouted</td>")
            [void]$sb.AppendLine("          <td style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.15);'>$violations</td>")
            [void]$sb.AppendLine("          <td style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.15);'>$score</td>")
            [void]$sb.AppendLine("          <td style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.15);'>$heap</td>")
            [void]$sb.AppendLine("          <td style='padding: 8px; border: 1px solid rgba(232, 204, 135, 0.15);'>$warns / $errs</td>")
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
