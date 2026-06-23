function ConvertFrom-FrDuration {
    param([string]$durationStr)
    if (-not $durationStr) { return 0.0 }
    $durationStr = $durationStr.Trim()
    
    $hours = 0.0
    $minutes = 0.0
    $seconds = 0.0

    if ($durationStr -match '(\d+(?:\.\d+)?)\s*hours?') {
        $hours = [double]$matches[1]
    }
    if ($durationStr -match '(\d+(?:\.\d+)?)\s*minutes?') {
        $minutes = [double]$matches[1]
    }
    if ($durationStr -match '(\d+(?:\.\d+)?)\s*seconds?') {
        $seconds = [double]$matches[1]
    }

    # Fallback to plain number if nothing matched
    if ($hours -eq 0 -and $minutes -eq 0 -and $seconds -eq 0 -and $durationStr -match '^[\d.]+$') {
        return [double]$durationStr
    }

    return ($hours * 3600) + ($minutes * 60) + $seconds
}

function ConvertFrom-FrScore {
    param([string]$scoreStr)
    $score = 0.0
    $unrouted = 0
    $violations = 0

    if (-not $scoreStr) {
        return [PSCustomObject]@{ Score = $score; Unrouted = $unrouted; Violations = $violations }
    }

    $scoreStr = $scoreStr.Trim()

    # Pattern: 500.00 (2 unrouted and 1 violation)
    if ($scoreStr -match '^([\d.]+)\s*\(\s*(\d+)\s+unrouted\s+and\s+(\d+)\s+violations?\s*\)') {
        $score = [double]$matches[1]
        $unrouted = [int]$matches[2]
        $violations = [int]$matches[3]
    }
    # Pattern: 0.00 (3 unrouted)
    elseif ($scoreStr -match '^([\d.]+)\s*\(\s*(\d+)\s+unrouted\s*\)') {
        $score = [double]$matches[1]
        $unrouted = [int]$matches[2]
    }
    # Pattern: 987.65
    elseif ($scoreStr -match '^([\d.]+)$') {
        $score = [double]$matches[1]
    }

    return [PSCustomObject]@{
        Score = $score
        Unrouted = $unrouted
        Violations = $violations
    }
}

function Get-PhaseMetrics {
    param([string]$LogPath, [string]$VersionLabel)

    $fanout = @{
        log_found = $false
        smd_pin_count = $null
        escaped_pin_count = $null
        not_routed_pin_count = $null
        escape_rate_pct = $null
        duration_seconds = $null
        cpu_seconds = $null
        total_allocated_gb = $null
        peak_heap_mb = $null
    }

    $autorouter = @{
        log_found = $false
        initial_unrouted_count = $null
        passes_completed = $null
        completion_reason = $null
        duration_seconds = $null
        cpu_seconds = $null
        total_allocated_gb = $null
        peak_heap_mb = $null
        final_unrouted = $null
        final_violations = $null
        final_score = $null
    }

    $optimizer = @{
        log_found = $false
        passes_completed = $null
        duration_seconds = $null
        cpu_seconds = $null
        total_allocated_gb = $null
        peak_heap_mb = $null
    }

    $warnCount = 0
    $errorCount = 0

    if (-not (Test-Path $LogPath)) {
        return @{
            fanout = $fanout
            autorouter = $autorouter
            optimizer = $optimizer
            warn_count = 0
            error_count = 0
        }
    }

    $lines = Get-Content $LogPath

    # 1. Warn / Error count
    foreach ($line in $lines) {
        if ($line -match '\[WARN\]|WARN |\[warning\]') {
            $warnCount++
        }
        if ($line -match '\[ERROR\]|ERROR |\[severe\]|Exception\b') {
            $errorCount++
        }
    }

    # 2. Extract Phase Info
    # Fanout Start
    foreach ($line in $lines) {
        if ($line -match "Starting fanout pre-pass on board '([^']+)' for (\d+) SMD pins?") {
            $fanout.smd_pin_count = [int]$matches[2]
            $fanout.log_found = $true
        }
    }

    # Fanout session summary
    $RE_FANOUT = 'Fanout session ([\w ]+): started with (\d+) total SMD pins, completed in ([^,]+), escaped pins: (\d+)/(\d+) \(([\d.]+)%\), using ([\d.]+) total CPU seconds, ([\d.]+) GB total allocated, and ([\d.]+) MB peak heap usage\.'
    foreach ($line in $lines) {
        if ($line -match $RE_FANOUT) {
            $fanout.log_found = $true
            $fanout.smd_pin_count = [int]$matches[2]
            $fanout.duration_seconds = ConvertFrom-FrDuration $matches[3]
            $fanout.escaped_pin_count = [int]$matches[4]
            $fanout.not_routed_pin_count = [int]$matches[5] - [int]$matches[4]
            $fanout.escape_rate_pct = [double]$matches[6]
            $fanout.cpu_seconds = [double]$matches[7]
            $fanout.total_allocated_gb = [double]$matches[8]
            $fanout.peak_heap_mb = [double]$matches[9]
        }
    }

    # Autorouter session summary
    $RE_ROUTER = 'Auto-router session ([\w ]+): started with (\d+) unrouted nets, completed in ([^,]+), final score: ([^,]+), using ([\d.]+) total CPU seconds, ([\d.]+) GB total allocated, and ([\d.]+) MB peak heap usage\.'
    foreach ($line in $lines) {
        if ($line -match $RE_ROUTER) {
            $autorouter.log_found = $true
            $autorouter.initial_unrouted_count = [int]$matches[2]
            $autorouter.completion_reason = $matches[1].Trim()
            $autorouter.duration_seconds = ConvertFrom-FrDuration $matches[3]
            
            $scoreParsed = ConvertFrom-FrScore $matches[4]
            $autorouter.final_score = $scoreParsed.Score
            $autorouter.final_unrouted = $scoreParsed.Unrouted
            $autorouter.final_violations = $scoreParsed.Violations

            $autorouter.cpu_seconds = [double]$matches[5]
            $autorouter.total_allocated_gb = [double]$matches[6]
            $autorouter.peak_heap_mb = [double]$matches[7]
        }
    }

    # Autorouter passes completed
    $maxPass = 0
    foreach ($line in $lines) {
        if ($line -match 'Auto-router pass #(\d+) on board') {
            $passNum = [int]$matches[1]
            if ($passNum -gt $maxPass) { $maxPass = $passNum }
        }
    }
    if ($maxPass -gt 0) {
        $autorouter.passes_completed = $maxPass
    }

    # Optimizer passes completed
    $maxOptPass = 0
    foreach ($line in $lines) {
        if ($line -match 'Optimizer pass #(\d+) was completed') {
            $optPassNum = [int]$matches[1]
            if ($optPassNum -gt $maxOptPass) { $maxOptPass = $optPassNum }
            $optimizer.log_found = $true
        }
    }
    if ($maxOptPass -gt 0) {
        $optimizer.passes_completed = $maxOptPass
    }

    # Fallback for older versions (2.0.1/2.1.0/2.2.4) that don't emit structured summaries
    if (-not $autorouter.log_found) {
        foreach ($line in $lines) {
            if ($line -match 'Auto-routing was completed in ([\d.,]+)\s*seconds(?: with the score of ([\d.,]+))?(?:\s*\(\s*(\d+)\s+unrouted\s*\))?') {
                $autorouter.log_found = $true
                $durStr = $matches[1].TrimEnd('.', ',').Replace(',', '.')
                $autorouter.duration_seconds = [double]$durStr
                if ($matches[2]) {
                    $scoreStr = $matches[2].TrimEnd('.', ',').Replace(',', '.')
                    $autorouter.final_score = [double]$scoreStr
                }
                if ($matches[3]) {
                    $autorouter.final_unrouted = [int]$matches[3]
                } else {
                    $autorouter.final_unrouted = 0
                }
                $autorouter.final_violations = 0
            }
        }
    }

    if (-not $optimizer.log_found) {
        foreach ($line in $lines) {
            if ($line -match '(?:Route optimization|Optimization) was completed in ([\d.,]+)\s*seconds') {
                $optimizer.log_found = $true
                $durStr = $matches[1].TrimEnd('.', ',').Replace(',', '.')
                $optimizer.duration_seconds = [double]$durStr
            }
        }
    }

    # Fallback memory extraction from the background sampler
    $memLogPath = $LogPath -replace '\.log$', '-memory.log'
    $fallbackPeakHeap = 0.0
    if (Test-Path $memLogPath) {
        $memLines = Get-Content $memLogPath
        foreach ($mLine in $memLines) {
            if ($mLine -match 'WorkingSet=([\d.]+)\s*MB') {
                $wsVal = [double]$matches[1]
                if ($wsVal -gt $fallbackPeakHeap) {
                    $fallbackPeakHeap = $wsVal
                }
            }
        }
    }

    if ($fallbackPeakHeap -gt 0) {
        if ($autorouter.log_found -and $autorouter.peak_heap_mb -eq $null) {
            $autorouter.peak_heap_mb = $fallbackPeakHeap
        }
        if ($fanout.log_found -and $fanout.peak_heap_mb -eq $null) {
            $fanout.peak_heap_mb = $fallbackPeakHeap
        }
        if ($optimizer.log_found -and $optimizer.peak_heap_mb -eq $null) {
            $optimizer.peak_heap_mb = $fallbackPeakHeap
        }
    }

    return @{
        fanout = $fanout
        autorouter = $autorouter
        optimizer = $optimizer
        warn_count = $warnCount
        error_count = $errorCount
    }
}

