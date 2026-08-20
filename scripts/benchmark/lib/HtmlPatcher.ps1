function Update-BenchmarksHtml {
    param(
        [Hashtable]$Cache,
        [string]$HtmlPath,
        [string]$FixturesDir = (Get-BenchmarkFixturesDir)
    )

    if (-not (Test-Path $HtmlPath)) {
        Write-Warning "Website HTML path not found: $HtmlPath"
        return
    }

    $runs = Get-ActiveBenchmarkRuns $Cache $FixturesDir
    $grouped = $runs | Group-Object -Property { $_.fixture.relative_path } | Sort-Object -Property Name

    # Build premium styled HTML
    $sb = [System.Text.StringBuilder]::new()
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    [void]$sb.AppendLine("<div class='benchmark-container'>")
    [void]$sb.AppendLine("  <div class='benchmark-report-info'>Generated on: $ts &middot; System: $($sysInfo.cpu_name) ($($sysInfo.cpu_physical_cores) Cores, $($sysInfo.total_ram_gb) GB RAM)</div>")

    # --- Multi-Tier Summary Tables in HTML ---
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

    $buildHtmlSummaryTableFn = {
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

                $isTimeout = $latestRun.exit.timed_out -eq $true
                if ($isTimeout) { $timeouts++ }
                if ($failed) { $failures++ }

                $unrouted = if ($latestRun.quality.unrouted_connections -ne $null) {
                    [int]$latestRun.quality.unrouted_connections
                } elseif ($latestRun.quality.final_unrouted -ne $null) {
                    [int]$latestRun.quality.final_unrouted
                } else { $null }

                $violations = if ($latestRun.quality.clearance_violations -ne $null) {
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

        $hsb = [System.Text.StringBuilder]::new()
        [void]$hsb.AppendLine("  <div class='summary-section' style='margin-bottom: 24px;'>")
        [void]$hsb.AppendLine("    <h3 class='fixture-title' style='margin-bottom: 4px;'>$Title</h3>")
        if ($Description) {
            [void]$hsb.AppendLine("    <p class='fixture-meta' style='margin-bottom: 12px;'>$Description</p>")
        }
        [void]$hsb.AppendLine("    <table class='benchmark-table'>")
        [void]$hsb.AppendLine("      <thead>")
        [void]$hsb.AppendLine("        <tr class='header-row'>")
        [void]$hsb.AppendLine("          <th>Version</th>")
        [void]$hsb.AppendLine("          <th>Fixtures</th>")
        [void]$hsb.AppendLine("          <th>Clean (0 DRC)</th>")
        [void]$hsb.AppendLine("          <th>Fully-Routed</th>")
        [void]$hsb.AppendLine("          <th>Timeouts</th>")
        [void]$hsb.AppendLine("          <th>Failures</th>")
        [void]$hsb.AppendLine("          <th>Avg. Score</th>")
        [void]$hsb.AppendLine("        </tr>")
        [void]$hsb.AppendLine("      </thead>")
        [void]$hsb.AppendLine("      <tbody>")

        foreach ($stat in ($versionStats.Values | Sort-Object -Property Version)) {
            $tot = $stat.FixtureCount
            $perfPct = if ($tot -gt 0) { [math]::Round(($stat.Perfects / $tot) * 100, 1) } else { 0.0 }
            $allPct  = if ($tot -gt 0) { [math]::Round(($stat.AllRouted / $tot) * 100, 1) } else { 0.0 }
            $toPct   = if ($tot -gt 0) { [math]::Round(($stat.Timeouts / $tot) * 100, 1) } else { 0.0 }
            $failPct = if ($tot -gt 0) { [math]::Round(($stat.Failures / $tot) * 100, 1) } else { 0.0 }

            $perfStr = "$($stat.Perfects)/$tot ($perfPct%)"
            $allStr  = "$($stat.AllRouted)/$tot ($allPct%)"
            $toStr   = "$($stat.Timeouts)/$tot ($toPct%)"
            $failStr = "$($stat.Failures)/$tot ($failPct%)"

            $avgScoreStr = if ($stat.AvgScore -ne $null) {
                $formatted = $stat.AvgScore.ToString("F1", [System.Globalization.CultureInfo]::InvariantCulture)
                if ($maxAvgScore -ne $null -and $stat.AvgScore -eq $maxAvgScore) { "<strong>$formatted</strong>" } else { $formatted }
            } else {
                "N/A"
            }

            [void]$hsb.AppendLine("        <tr>")
            [void]$hsb.AppendLine("          <td><strong>$($stat.Version)</strong></td>")
            [void]$hsb.AppendLine("          <td>$($stat.FixtureCount)</td>")
            [void]$hsb.AppendLine("          <td>$perfStr</td>")
            [void]$hsb.AppendLine("          <td>$allStr</td>")
            [void]$hsb.AppendLine("          <td>$toStr</td>")
            [void]$hsb.AppendLine("          <td>$failStr</td>")
            [void]$hsb.AppendLine("          <td>$avgScoreStr</td>")
            [void]$hsb.AppendLine("        </tr>")
        }

        [void]$hsb.AppendLine("      </tbody>")
        [void]$hsb.AppendLine("    </table>")
        [void]$hsb.AppendLine("  </div>")
        return $hsb.ToString()
    }

    # 1. Overall Summary Table
    [void]$sb.Append((& $buildHtmlSummaryTableFn "Summary Table (All Tiers Combined)" $runs "Comprehensive performance across all benchmark fixtures."))

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
            [void]$sb.Append((& $buildHtmlSummaryTableFn $b.Title $matchingRuns $b.Desc))
        }
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
