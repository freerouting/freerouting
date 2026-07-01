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
    $liveLogFile = if ($isV19) { $stdoutFile } else { $logFile }

    # Clean previous output
    if (Test-Path $outputFile) { Remove-Item $outputFile -Force -ErrorAction SilentlyContinue }
    if (Test-Path $logFile) { Remove-Item $logFile -Force -ErrorAction SilentlyContinue }
    if (Test-Path $stdoutFile) { Remove-Item $stdoutFile -Force -ErrorAction SilentlyContinue }
    if (Test-Path $errFile) { Remove-Item $errFile -Force -ErrorAction SilentlyContinue }

    $jvmArgs = @(
        "-Dsun.stdout.buffered=false",
        "-Xmx$($Settings.heap_max)",
        "-Xms256m",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=`"$LogsDir`"",
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
    
    # Logging flags
    $jvmArgs += "--logging.file.level=$($Settings.log_level)"
    $jvmArgs += "--logging.file.location=`"$logFile`""
    $jvmArgs += "--logging.console.level=INFO"

    if ($Binary.Name -notmatch 'freerouting-1.9.0.jar') {
        $jvmArgs += "--api_server.enabled=false"
        if ($SupportsCliMode) {
            $jvmArgs += "--gui.enabled=false"
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

    $endTime = Get-Date
    
    # Stop memory sampler
    Stop-Job $memJob -ErrorAction SilentlyContinue
    Remove-Job $memJob -ErrorAction SilentlyContinue

    $timedOut = -not $completed
    if ($timedOut) {
        # Force terminate hung process
        try {
            $process.Kill()
        } catch {}
    }

    # Copy/merge stdout to logFile
    if ($isV19) {
        if (Test-Path $stdoutFile) {
            Move-Item -Path $stdoutFile -Destination $logFile -Force -ErrorAction SilentlyContinue
        }
    } else {
        if (Test-Path $stdoutFile) {
            try {
                $stdoutLines = Get-Content $stdoutFile -ErrorAction SilentlyContinue
                if ($stdoutLines) {
                    $stdoutLines | Add-Content $logFile -ErrorAction SilentlyContinue
                }
                Remove-Item $stdoutFile -Force -ErrorAction SilentlyContinue
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
        Crashed           = $crashed
        OomDetected       = $oomDetected
        TimedOut          = $timedOut
        RunMode           = if ($SupportsCliMode) { "CLI" } else { "GUI" }
    }
}
