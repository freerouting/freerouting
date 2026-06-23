function Invoke-DrcCheck {
    param(
        [System.IO.FileInfo]$BinaryCurrent,
        [System.IO.FileInfo]$DsnFile,
        [System.IO.FileInfo]$SesFile,
        [string]$OutputsDir,
        [string]$BaseName
    )

    $drcReportFile = Join-Path $OutputsDir "${BaseName}--drc.json"

    if (-not (Test-Path $SesFile.FullName)) {
        return [PSCustomObject]@{
            drc_binary_version  = "Unknown"
            drc_run_at          = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
            final_unrouted      = $null
            final_violations    = $null
            final_quality_score = $null
            error               = "SES file not found"
        }
    }

    $jvmArgs = @(
        "-jar", $BinaryCurrent.FullName,
        "-de", $DsnFile.FullName, $SesFile.FullName,
        "-drc", $drcReportFile,
        "--gui.enabled=false"
    )

    $runAt = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
    
    try {
        $process = Start-Process -FilePath "java" -ArgumentList $jvmArgs -NoNewWindow -PassThru
        $process.WaitForExit()
        
        if (Test-Path $drcReportFile) {
            $raw = Get-Content $drcReportFile -Raw
            $report = ConvertFrom-Json $raw
            
            $unrouted = 0
            if ($report.unconnected_items) {
                $unrouted = $report.unconnected_items.Count
            }
            $violations = 0
            if ($report.violations) {
                $violations = $report.violations.Count
            }
            $score = $null
            if ($report.quality_score -ne $null) {
                $score = [double]$report.quality_score
            }

            return [PSCustomObject]@{
                drc_binary_version  = Get-BinaryVersionLabel $BinaryCurrent
                drc_run_at          = $runAt
                final_unrouted      = $unrouted
                final_violations    = $violations
                final_quality_score = $score
            }
        }
    } catch {
        Write-Warning "DRC run failed: $_"
    }

    return [PSCustomObject]@{
        drc_binary_version  = Get-BinaryVersionLabel $BinaryCurrent
        drc_run_at          = $runAt
        final_unrouted      = $null
        final_violations    = $null
        final_quality_score = $null
        error               = "DRC execution failed or did not generate report"
    }
}
