function Get-OptionalManifestProperty {
    param(
        $Object,
        [string]$Name
    )

    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($property) {
        return $property.Value
    }
    return $null
}

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

    $stats = Get-OptionalManifestProperty $manifest "board_statistics"
    $settingsSnapshot = Get-OptionalManifestProperty $manifest "settings_snapshot"
    if ($settingsSnapshot) {
        $LogMetrics.settings_snapshot = $settingsSnapshot
    }
    if ($stats) {
        $LogMetrics.board_statistics = $stats
        $manifestBounds = Get-OptionalManifestProperty $manifest "bounds"
        $LogMetrics.bounds =
            if ($manifestBounds) {
                $manifestBounds
            } else {
                Get-OptionalManifestProperty $stats "bounds"
            }
        if ($stats.connections -and $null -ne $stats.connections.incomplete_count) {
            $LogMetrics.autorouter.final_unrouted = [int]$stats.connections.incomplete_count
        }
        if ($stats.clearance_violations -and $null -ne $stats.clearance_violations.total_count) {
            $LogMetrics.autorouter.final_violations = [int]$stats.clearance_violations.total_count
        }
        $normalizedScore = Get-OptionalManifestProperty $manifest "normalized_score"
        if ($null -ne $normalizedScore) {
            $LogMetrics.autorouter.final_score = [double]$normalizedScore
        }
        $optimizerScore = Get-OptionalManifestProperty $manifest "optimizer_score"
        if ($null -ne $optimizerScore -and $null -eq $LogMetrics.optimizer.score_after) {
            $LogMetrics.optimizer.final_score = [double]$optimizerScore
        }
        $LogMetrics.autorouter.log_found = $true
    }

    $manifestPhases = Get-OptionalManifestProperty $manifest "phases"
    if ($manifestPhases) {
        foreach ($phaseName in @("fanout", "autorouter", "optimizer")) {
            $phase = Get-OptionalManifestProperty $manifestPhases $phaseName
            if ($phase) {
                $phaseBefore = Get-OptionalManifestProperty $phase "before"
                $phaseAfter = Get-OptionalManifestProperty $phase "after"
                if ($phaseBefore) {
                    $beforeScore = Get-OptionalManifestProperty $phaseBefore "score"
                    if ($phaseName -eq "optimizer") {
                        $optimizerBeforeScore =
                            Get-OptionalManifestProperty $phaseBefore "optimizer_score"
                        if ($null -ne $optimizerBeforeScore) {
                            $beforeScore = $optimizerBeforeScore
                        }
                    }
                    $LogMetrics.$phaseName.score_before =
                        $beforeScore
                    $LogMetrics.$phaseName.before = $phaseBefore
                }
                if ($phaseAfter) {
                    $afterScore = Get-OptionalManifestProperty $phaseAfter "score"
                    if ($phaseName -eq "optimizer") {
                        $optimizerAfterScore =
                            Get-OptionalManifestProperty $phaseAfter "optimizer_score"
                        if ($null -ne $optimizerAfterScore) {
                            $afterScore = $optimizerAfterScore
                        }
                    }
                    $LogMetrics.$phaseName.score_after =
                        $afterScore
                    $LogMetrics.$phaseName.after = $phaseAfter
                }
                $phaseCpuSeconds = Get-OptionalManifestProperty $phase "cpu_seconds"
                if ($null -ne $phaseCpuSeconds) {
                    $LogMetrics.$phaseName.cpu_seconds = [double]$phaseCpuSeconds
                }
                $phaseDuration = Get-OptionalManifestProperty $phase "duration_seconds"
                if ($null -ne $phaseDuration) {
                    $LogMetrics.$phaseName.duration_seconds = [double]$phaseDuration
                }
                $phaseAllocatedGb = Get-OptionalManifestProperty $phase "total_allocated_gb"
                if ($null -ne $phaseAllocatedGb) {
                    $LogMetrics.$phaseName.total_allocated_gb = [double]$phaseAllocatedGb
                }
                $phasePeakHeapMb = Get-OptionalManifestProperty $phase "peak_heap_mb"
                if ($null -ne $phasePeakHeapMb) {
                    $LogMetrics.$phaseName.peak_heap_mb = [double]$phasePeakHeapMb
                }
                $phasePasses = Get-OptionalManifestProperty $phase "passes_completed"
                if ($null -ne $phasePasses) {
                    $LogMetrics.$phaseName.passes_completed = [int]$phasePasses
                }
            }
        }
    }

    if ($manifestPhases) {
        $fanoutPhase = Get-OptionalManifestProperty $manifestPhases "fanout"
        if ($fanoutPhase -and
            $null -ne (Get-OptionalManifestProperty $fanoutPhase "duration_seconds")) {
            $LogMetrics.fanout.duration_seconds =
                [double](Get-OptionalManifestProperty $fanoutPhase "duration_seconds")
            $LogMetrics.fanout.log_found = $true
        }
        $autorouterPhase = Get-OptionalManifestProperty $manifestPhases "autorouter"
        if ($autorouterPhase) {
            $autorouterDuration = Get-OptionalManifestProperty $autorouterPhase "duration_seconds"
            if ($null -ne $autorouterDuration) {
                $LogMetrics.autorouter.duration_seconds = [double]$autorouterDuration
            }
            $autorouterPasses = Get-OptionalManifestProperty $autorouterPhase "passes_completed"
            if ($null -ne $autorouterPasses) {
                $LogMetrics.autorouter.passes_completed = [int]$autorouterPasses
            }
        }
        $optimizerPhase = Get-OptionalManifestProperty $manifestPhases "optimizer"
        $optimizerDuration =
            if ($optimizerPhase) {
                Get-OptionalManifestProperty $optimizerPhase "duration_seconds"
            } else {
                $null
            }
        if ($null -ne $optimizerDuration) {
            $LogMetrics.optimizer.duration_seconds = [double]$optimizerDuration
            $LogMetrics.optimizer.log_found = $true
        }
    }
    if ($null -ne $LogMetrics.optimizer.score_after) {
        $LogMetrics.optimizer.final_score = [double]$LogMetrics.optimizer.score_after
    }

    $resourceUsage = Get-OptionalManifestProperty $manifest "resource_usage"
    if ($resourceUsage) {
        $cpuTime = Get-OptionalManifestProperty $resourceUsage "cpu_time"
        if ($null -ne $cpuTime) {
            $LogMetrics.autorouter.cpu_seconds = [double]$cpuTime
        }
        $peakMemory = Get-OptionalManifestProperty $resourceUsage "peak_memory"
        if ($null -ne $peakMemory) {
            $peak = [double]$peakMemory
            $LogMetrics.autorouter.peak_heap_mb = $peak
        }
    }

    $manifestCpuScore = Get-OptionalManifestProperty $manifest "cpu_score"
    if ($null -ne $manifestCpuScore) {
        $LogMetrics.cpu_score = ConvertTo-ScaledCpuScore ([int]$manifestCpuScore)
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
