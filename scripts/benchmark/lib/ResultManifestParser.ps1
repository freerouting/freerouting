function Import-ResultManifestMetrics {
    param(
        [string]$ManifestPath,
        [hashtable]$LogMetrics
    )

    if (-not $ManifestPath -or -not (Test-Path $ManifestPath)) {
        return $LogMetrics
    }

    try {
        $manifest = Get-Content $ManifestPath -Raw | ConvertFrom-Json
    } catch {
        Write-Warning "Failed to parse result manifest: $ManifestPath"
        return $LogMetrics
    }

    $stats = $manifest.board_statistics
    if ($stats) {
        if ($stats.connections -and $null -ne $stats.connections.incomplete_count) {
            $LogMetrics.autorouter.final_unrouted = [int]$stats.connections.incomplete_count
        }
        if ($stats.clearance_violations -and $null -ne $stats.clearance_violations.total_count) {
            $LogMetrics.autorouter.final_violations = [int]$stats.clearance_violations.total_count
        }
        if ($null -ne $manifest.normalized_score) {
            $LogMetrics.autorouter.final_score = [double]$manifest.normalized_score
        }
        $LogMetrics.autorouter.log_found = $true
    }

    if ($manifest.phases) {
        if ($manifest.phases.fanout -and $null -ne $manifest.phases.fanout.duration_seconds) {
            $LogMetrics.fanout.duration_seconds = [double]$manifest.phases.fanout.duration_seconds
            $LogMetrics.fanout.log_found = $true
        }
        if ($manifest.phases.autorouter) {
            if ($null -ne $manifest.phases.autorouter.duration_seconds) {
                $LogMetrics.autorouter.duration_seconds = [double]$manifest.phases.autorouter.duration_seconds
            }
            if ($null -ne $manifest.phases.autorouter.passes_completed) {
                $LogMetrics.autorouter.passes_completed = [string]$manifest.phases.autorouter.passes_completed
            }
        }
        if ($manifest.phases.optimizer -and $null -ne $manifest.phases.optimizer.duration_seconds) {
            $LogMetrics.optimizer.duration_seconds = [double]$manifest.phases.optimizer.duration_seconds
            $LogMetrics.optimizer.log_found = $true
        }
    }

    if ($manifest.resource_usage) {
        if ($null -ne $manifest.resource_usage.cpu_time) {
            $LogMetrics.autorouter.cpu_seconds = [double]$manifest.resource_usage.cpu_time
        }
        if ($null -ne $manifest.resource_usage.peak_memory) {
            $peak = [double]$manifest.resource_usage.peak_memory
            $LogMetrics.autorouter.peak_heap_mb = $peak
        }
    }

    $LogMetrics.metric_source = "result_json"
    return $LogMetrics
}

function Get-GitShaShort {
    param([string]$RepoRoot = (Join-Path $PSScriptRoot "..\..\.."))
    try {
        Push-Location $RepoRoot
        $sha = git rev-parse --short HEAD 2>$null
        if ($sha) { return $sha.Trim() }
    } catch {
    } finally {
        Pop-Location
    }
    return "unknown"
}
