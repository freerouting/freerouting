function Get-DrcReportMetrics {
    param($Report)

    $unconnectedFindings = @()
    if ($Report.unconnectedItems) {
        $unconnectedFindings = @($Report.unconnectedItems)
    } elseif ($Report.unconnected_items) {
        $unconnectedFindings = @($Report.unconnected_items)
    }

    $violations = @()
    if ($Report.violations) {
        $violations = @($Report.violations)
    }

    $clearanceViolations = 0
    foreach ($violation in $violations) {
        if ([string]$violation.type -match "clearance") {
            $clearanceViolations++
        }
    }

    $danglingTracks = 0
    $danglingVias = 0
    $unconnectedItems = 0
    foreach ($finding in $unconnectedFindings) {
        $type = [string]$finding.type
        if ($type -eq "track_dangling") {
            $danglingTracks++
        } elseif ($type -eq "via_dangling") {
            $danglingVias++
        } elseif ($type -eq "unconnectedItems" -or $type -eq "unconnected_items") {
            $unconnectedItems++
        }
    }

    return [PSCustomObject]@{
        raw_violations = $violations.Count + $unconnectedFindings.Count
        clearance_violations = $clearanceViolations
        summary_violations = $violations.Count
        dangling_tracks = $danglingTracks
        dangling_vias = $danglingVias
        unconnected_items = $unconnectedItems
        unconnected_findings = $unconnectedFindings.Count
    }
}

function Invoke-DrcCheck {
    param(
        [System.IO.FileInfo]$BinaryCurrent,
        [System.IO.FileInfo]$DsnFile,
        [System.IO.FileInfo]$SesFile,
        [string]$OutputsDir,
        [string]$BaseName,
        [int]$TimeoutSeconds = 300
    )

    $drcReportFile = Join-Path $OutputsDir "${BaseName}--drc.json"
    $runAt = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
    $binaryVersion = Get-BinaryVersionLabel $BinaryCurrent
    $binaryHash = (Get-FileHash $BinaryCurrent.FullName -Algorithm SHA256).Hash

    if (Test-Path $drcReportFile) {
        Remove-Item $drcReportFile -Force -ErrorAction SilentlyContinue
    }

    if ($null -eq $SesFile -or -not (Test-Path $SesFile.FullName)) {
        return [PSCustomObject]@{
            drc_binary_version  = $binaryVersion
            drc_binary_sha256   = $binaryHash
            drc_run_at          = $runAt
            report_file         = $drcReportFile
            status              = "missing_ses"
            unconnected_items   = $null
            unconnected_findings = $null
            raw_violations      = $null
            final_unrouted      = $null
            final_violations    = $null
            clearance_violations = $null
            summary_violations  = $null
            dangling_tracks     = $null
            dangling_vias       = $null
            final_quality_score = $null
            error               = "SES file not found"
        }
    }

    $jvmArgs = @(
        "-jar", ('"{0}"' -f $BinaryCurrent.FullName),
        "-de", ('"{0}+{1}"' -f $DsnFile.FullName, $SesFile.FullName),
        "-drc", ('"{0}"' -f $drcReportFile),
        "--gui.enabled=false"
    )

    try {
        $process = Start-Process -FilePath "java" -ArgumentList $jvmArgs -NoNewWindow -PassThru
        $completed = $process.WaitForExit($TimeoutSeconds * 1000)
        if (-not $completed) {
            try {
                $process.Kill()
                $process.WaitForExit(5000)
            } catch {}
            return [PSCustomObject]@{
                drc_binary_version   = $binaryVersion
                drc_binary_sha256    = $binaryHash
                drc_run_at           = $runAt
                report_file          = $drcReportFile
                status               = "timeout"
                unconnected_items    = $null
                unconnected_findings = $null
                raw_violations      = $null
                final_unrouted       = $null
                final_violations     = $null
                clearance_violations = $null
                summary_violations   = $null
                dangling_tracks      = $null
                dangling_vias        = $null
                final_quality_score  = $null
                error                = "DRC process exceeded timeout"
            }
        }

        if (Test-Path $drcReportFile) {
            $raw = Get-Content $drcReportFile -Raw
            $report = ConvertFrom-Json $raw

            $drcMetrics = Get-DrcReportMetrics $report

            $score = $null
            if ($report.qualityScore -ne $null) {
                $score = [double]$report.qualityScore
            } elseif ($report.quality_score -ne $null) {
                $score = [double]$report.quality_score
            }

            return [PSCustomObject]@{
                drc_binary_version   = $binaryVersion
                drc_binary_sha256    = $binaryHash
                drc_run_at           = $runAt
                report_file          = $drcReportFile
                status               = "completed"
                unconnected_items    = $drcMetrics.unconnected_items
                unconnected_findings = $drcMetrics.unconnected_findings
                raw_violations      = $drcMetrics.raw_violations
                final_unrouted       = $drcMetrics.unconnected_items
                final_violations     = $drcMetrics.raw_violations
                clearance_violations = $drcMetrics.clearance_violations
                summary_violations   = $drcMetrics.summary_violations
                dangling_tracks      = $drcMetrics.dangling_tracks
                dangling_vias        = $drcMetrics.dangling_vias
                final_quality_score  = $score
            }
        }
    } catch {
        Write-Warning "DRC run failed: $_"
    }

    return [PSCustomObject]@{
        drc_binary_version   = $binaryVersion
        drc_binary_sha256    = $binaryHash
        drc_run_at           = $runAt
        report_file          = $drcReportFile
        status               = "failed"
        unconnected_items    = $null
        unconnected_findings = $null
        raw_violations      = $null
        final_unrouted       = $null
        final_violations     = $null
        clearance_violations = $null
        summary_violations   = $null
        dangling_tracks      = $null
        dangling_vias        = $null
        final_quality_score  = $null
        error                = "DRC execution failed or did not generate report"
    }
}
