function Get-ContentShared {
    param([string]$path)
    try {
        $resolvedPath = (Get-Item $path -ErrorAction SilentlyContinue).FullName
        if (-not $resolvedPath) {
            $resolvedPath = [System.IO.Path]::GetFullPath($path)
        }
        if (-not (Test-Path $resolvedPath)) { return $null }
        $fileStream = New-Object System.IO.FileStream($resolvedPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        $reader = New-Object System.IO.StreamReader($fileStream)
        $lines = [System.Collections.ArrayList]::new()
        while (($line = $reader.ReadLine()) -ne $null) {
            [void]$lines.Add($line)
        }
        $reader.Close()
        $fileStream.Close()
        return $lines
    } catch {
        Write-Warning "Get-ContentShared failed for $path : $_"
        return $null
    }
}

function Invoke-BenchmarkRun {
    param(
        [System.IO.FileInfo]$Binary,
        [System.IO.FileInfo]$Fixture,
        [string]$BaseName,
        [string]$LogsDir,
        [string]$OutputsDir,
        $Settings,
        [bool]$SupportsCliMode
    )

    $isV19 = $Binary.Name -match 'freerouting-1.9.0.jar'
    $logFile = Join-Path $LogsDir "${BaseName}.log"
    $stdoutFile = Join-Path $LogsDir "${BaseName}.stdout"
    $errFile = Join-Path $LogsDir "${BaseName}.err"
    $memLog = Join-Path $LogsDir "${BaseName}-memory.log"
    $outputFile = Join-Path $OutputsDir "${BaseName}.ses"
    $resultJsonFile = Join-Path $OutputsDir "${BaseName}-result.json"
    $liveLogFile = if ($isV19) { $stdoutFile } else { $logFile }

    # Clean previous output
    if (Test-Path $outputFile) { Remove-Item $outputFile -Force -ErrorAction SilentlyContinue }
    if (Test-Path $resultJsonFile) { Remove-Item $resultJsonFile -Force -ErrorAction SilentlyContinue }
    if (Test-Path $logFile) { Remove-Item $logFile -Force -ErrorAction SilentlyContinue }
    if (Test-Path $stdoutFile) { Remove-Item $stdoutFile -Force -ErrorAction SilentlyContinue }
    if (Test-Path $errFile) { Remove-Item $errFile -Force -ErrorAction SilentlyContinue }

    $jvmArgs = @(
        "-Dsun.stdout.buffered=false",
        "-Xmx$($Settings.heap_max)",
        "-Xms256m",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=`"$LogsDir`""
    )
    if ($Settings.profile_enabled) {
        $jvmArgs += "-Dfreerouting.benchmark.profile=true"
    }
    if ($Settings.retain_autoroute_database) {
        $jvmArgs += "-Dfreerouting.benchmark.retain_autoroute_database=true"
    }
    $jvmArgs += @(
        "-jar", ('"{0}"' -f $Binary.FullName),
        "-de", ('"{0}"' -f $Fixture.FullName)
    )

    # CLI Output Target
    $jvmArgs += "-do"
    $jvmArgs += ('"{0}"' -f $outputFile)

    # Router and logger options (supported by both v1.9 and current builds)
    $jvmArgs += "--router.max_passes=$($Settings.max_passes)"
    $jvmArgs += "--router.max_threads=$($Settings.max_threads)"
    $jvmArgs += "--router.job_timeout=`"$($Settings.max_time)`""
    $jvmArgs += "--router.optimizer.enabled=$($Settings.optimizer_enabled.ToString().ToLower())"
    $jvmArgs += "--router.fanout.enabled=$($Settings.fanout_enabled.ToString().ToLower())"
    $jvmArgs += "--router.enabled=$($Settings.router_enabled.ToString().ToLower())"
    if ($Settings.fanout_timeout) {
        $jvmArgs += "--router.fanout.timeout=`"$($Settings.fanout_timeout)`""
    }
    if ($Settings.optimizer_timeout) {
        $jvmArgs += "--router.optimizer.timeout=`"$($Settings.optimizer_timeout)`""
    }
    if ($Settings.max_items -and [int]$Settings.max_items -gt 0) {
        $jvmArgs += "--router.max_items=$($Settings.max_items)"
    }

    # Logging flags
    $jvmArgs += "--logging.file.level=$($Settings.log_level)"
    $jvmArgs += ('--logging.file.location="{0}"' -f $logFile)
    $jvmArgs += "--logging.console.level=INFO"

    $envGitSha = $Settings.git_sha
    if ($envGitSha -and $SupportsCliMode -and ($Binary.Name -notmatch 'freerouting-1.9.0.jar')) {
        $jvmArgs += "-Dfreerouting.git.sha=$envGitSha"
    }

    if ($Binary.Name -notmatch 'freerouting-1.9.0.jar') {
        $jvmArgs += "--api_server.enabled=false"
        if ($SupportsCliMode) {
            $jvmArgs += "--gui.enabled=false"
            $jvmArgs += ('--router.result_json="{0}"' -f $resultJsonFile)
        }
    }

    $startTime = Get-Date

    # Start process with standard output and error redirected separately to avoid PowerShell conflicts
    $process = Start-Process -FilePath "java" -ArgumentList $jvmArgs -NoNewWindow -PassThru `
        -RedirectStandardOutput $stdoutFile -RedirectStandardError $errFile

    # Launch background memory sampler
    $memJob = Start-Job -ScriptBlock {
        param($logFile, $pidVal)
        while ($true) {
            $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
            $p = Get-Process -Id $pidVal -ErrorAction SilentlyContinue
            if (-not $p) { break }
            try {
                $wsMB = [math]::Round($p.WorkingSet64 / 1MB, 1)
                Add-Content $logFile "$ts  WorkingSet=${wsMB}MB"
            } catch {}
            Start-Sleep 1
        }
    } -ArgumentList $memLog, $process.Id

    # Parse max time to milliseconds
    $maxTs = [timespan]::Parse($Settings.max_time)
    $timeoutMs = $maxTs.TotalMilliseconds
    $gracePeriodSeconds = if ($Settings.timeout_grace_period_seconds -ne $null) {
        [int]$Settings.timeout_grace_period_seconds
    } else {
        45
    }

    # Wait for completion with timeout while printing log lines dynamically
    $lastLineCount = 0
    $completed = $false
    $timeoutElapsed = 0
    $sleepIntervalMs = 200

    while ($timeoutElapsed -lt $timeoutMs) {
        if ($process.HasExited) {
            $completed = $true
            break
        }
        Start-Sleep -Milliseconds $sleepIntervalMs
        $timeoutElapsed += $sleepIntervalMs

        # Read and display new log lines
        if (Test-Path $liveLogFile) {
            try {
                $currentLines = Get-ContentShared $liveLogFile
                if ($currentLines) {
                    $newLineCount = $currentLines.Count
                    if ($newLineCount -gt $lastLineCount) {
                        for ($i = $lastLineCount; $i -lt $newLineCount; $i++) {
                            Write-Host "    $($currentLines[$i])"
                        }
                        $lastLineCount = $newLineCount
                    }
                }
            } catch {
                # Ignore concurrent access issues
            }
        }
    }

    # Print any remaining lines
    if (Test-Path $liveLogFile) {
        try {
            $currentLines = Get-ContentShared $liveLogFile
            if ($currentLines) {
                $newLineCount = $currentLines.Count
                if ($newLineCount -gt $lastLineCount) {
                    for ($i = $lastLineCount; $i -lt $newLineCount; $i++) {
                        Write-Host "    $($currentLines[$i])"
                    }
                }
            }
        } catch {}
    }

    # Stop memory sampler
    Stop-Job $memJob -ErrorAction SilentlyContinue
    Remove-Job $memJob -ErrorAction SilentlyContinue

    if (-not $completed -and $process.HasExited) {
        $completed = $true
    }
    $timedOut = -not $completed
    if ($timedOut) {
        # Give Freerouting's internal timeout monitor time to request a graceful stop and
        # write its final checkpoint before force termination.
        $graceDeadline = (Get-Date).AddSeconds($gracePeriodSeconds)
        while (-not $process.HasExited -and (Get-Date) -lt $graceDeadline) {
            Start-Sleep -Milliseconds $sleepIntervalMs
        }
        if (-not $process.HasExited) {
            try {
                $process.Kill()
                $process.WaitForExit(5000)
            } catch {}
        }
    }
    $endTime = Get-Date

    # The file logger is canonical for current versions. Appending stdout to it duplicates every
    # INFO line because console logging is enabled for live progress output. Keep stdout separate
    # unless file logging produced no content at all.
    if ($isV19) {
        if (Test-Path $stdoutFile) {
            Move-Item -Path $stdoutFile -Destination $logFile -Force -ErrorAction SilentlyContinue
        }
    } else {
        if (Test-Path $stdoutFile) {
            try {
                $logHasContent =
                    (Test-Path $logFile) -and ((Get-Item $logFile -ErrorAction SilentlyContinue).Length -gt 0)
                if (-not $logHasContent) {
                    Move-Item -Path $stdoutFile -Destination $logFile -Force -ErrorAction SilentlyContinue
                } else {
                    Remove-Item $stdoutFile -Force -ErrorAction SilentlyContinue
                }
            } catch {}
        }
    }

    # Append standard error log to standard output log file
    if (Test-Path $errFile) {
        try {
            Get-Content $errFile -ErrorAction SilentlyContinue | Add-Content $logFile -ErrorAction SilentlyContinue
            Remove-Item $errFile -Force -ErrorAction SilentlyContinue
        } catch {}
    }

    $crashed = ($process.ExitCode -ne 0 -and -not $timedOut)

    # Check for OOM
    $oomDetected = $false
    if (Test-Path $logFile) {
        $logContent = Get-Content $logFile -ErrorAction SilentlyContinue
        if ($logContent -match "OutOfMemoryError|java.lang.OutOfMemoryError") {
            $oomDetected = $true
        }
    }

    return [PSCustomObject]@{
        ExitCode          = if ($timedOut) { -1 } else { $process.ExitCode }
        WallClockSeconds  = ($endTime - $startTime).TotalSeconds
        LogFile           = $logFile
        OutputFile        = $outputFile
        ResultJsonFile    = $resultJsonFile
        Crashed           = $crashed
        OomDetected       = $oomDetected
        TimedOut          = $timedOut
        RunMode           = if ($SupportsCliMode) { "CLI" } else { "GUI" }
    }
}
