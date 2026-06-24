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

    $logFile = Join-Path $LogsDir "${BaseName}.log"
    $errFile = Join-Path $LogsDir "${BaseName}.err"
    $memLog = Join-Path $LogsDir "${BaseName}-memory.log"
    $outputFile = Join-Path $OutputsDir "${BaseName}.ses"

    # Clean previous output
    if (Test-Path $outputFile) {
        Remove-Item $outputFile -Force -ErrorAction SilentlyContinue
    }

    $jvmArgs = @(
        "-Xmx$($Settings.heap_max)",
        "-Xms256m",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=$LogsDir",
        "-jar", $Binary.FullName,
        "-de", $Fixture.FullName
    )

    # CLI Output Target
    $jvmArgs += "-do"
    $jvmArgs += $outputFile

    # Apply router/logger options only if it's not v1.9
    if ($Binary.Name -match 'freerouting-1.9.0.jar') {
        # v1.9 supports only -de and -do basic args
    } else {
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
        $jvmArgs += "--api_server.enabled=false"
    }

    if ($SupportsCliMode) {
        $jvmArgs += "--gui.enabled=false"
    }

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "java"
    
    # Process arguments to handle spacing
    $argsList = @()
    foreach ($arg in $jvmArgs) {
        if ($arg -match '\s') {
            $argsList += "`"$arg`""
        } else {
            $argsList += $arg
        }
    }
    $psi.Arguments = $argsList -join " "
    
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    
    $startTime = Get-Date
    
    # Start the process
    $null = $process.Start()
    
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

    # Create the log file writer
    $logStream = [System.IO.File]::CreateText($logFile)
    
    # Read output streams line-by-line in real-time
    $timeoutElapsed = 0
    $timeoutMs = ([timespan]::Parse($Settings.max_time)).TotalMilliseconds
    $sleepIntervalMs = 100
    
    $completed = $false
    while ($timeoutElapsed -lt $timeoutMs) {
        if ($process.HasExited) {
            $completed = $true
            break
        }
        
        # Read standard output lines
        while ($process.StandardOutput.Peek() -ne -1) {
            $line = $process.StandardOutput.ReadLine()
            Write-Output "    $line"
            $logStream.WriteLine($line)
        }
        
        # Read standard error lines
        while ($process.StandardError.Peek() -ne -1) {
            $line = $process.StandardError.ReadLine()
            Write-Output "    $line"
            $logStream.WriteLine($line)
        }
        
        Start-Sleep -Milliseconds $sleepIntervalMs
        $timeoutElapsed += $sleepIntervalMs
    }

    # Process remaining output
    while ($process.StandardOutput.Peek() -ne -1) {
        $line = $process.StandardOutput.ReadLine()
        Write-Output "    $line"
        $logStream.WriteLine($line)
    }
    while ($process.StandardError.Peek() -ne -1) {
        $line = $process.StandardError.ReadLine()
        Write-Output "    $line"
        $logStream.WriteLine($line)
    }
    
    $logStream.Close()
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
