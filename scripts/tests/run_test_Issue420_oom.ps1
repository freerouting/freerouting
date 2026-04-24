<#
.SYNOPSIS
    Full-board routing + optimizer stress test for Issue #420
    (OutOfMemoryError during post-route optimization).

.DESCRIPTION
    Builds the current executable JAR, then runs the router with the
    optimizer enabled on the Issue420 fixture board.  JVM heap is capped
    at $HeapMax so any unbounded memory leak will surface relatively
    quickly.  A heap dump is written automatically if the JVM runs out of
    memory (-XX:+HeapDumpOnOutOfMemoryError).

    Memory (working set) is sampled every 30 seconds and written to a
    separate log file so you can confirm whether the heap grows without
    limit.

    Results land in  logs\Issue420\  under the repository root.

.PARAMETER MaxPasses
    Maximum autorouter passes (default: 100).

.PARAMETER MaxThreads
    Router thread count (default: 1 – single-thread matches original reporter).

.PARAMETER JobTimeout
    Wall-clock timeout for the routing job, e.g. "06:00:00" (default: 12:00:00).

.PARAMETER HeapMax
    JVM maximum heap size, e.g. "4g" or "8g" (default: 4g – low cap to
    expose leaks faster).

.PARAMETER SkipBuild
    Skip the Gradle build step if the JAR already exists.

.EXAMPLE
    .\run_test_Issue420_oom.ps1
    .\run_test_Issue420_oom.ps1 -HeapMax 8g -MaxThreads 4
    .\run_test_Issue420_oom.ps1 -SkipBuild
#>
param(
    [int]    $MaxPasses   = 100,
    [int]    $MaxThreads  = 1,
    [string] $JobTimeout  = "12:00:00",
    [string] $HeapMax     = "4g",
    [switch] $SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Paths ──────────────────────────────────────────────────────────────────
$RepoRoot  = Resolve-Path "$PSScriptRoot\..\.."
$Fixture   = Join-Path $RepoRoot "fixtures\Issue420-contribution-board.dsn"
$Output    = Join-Path $RepoRoot "fixtures\Issue420-contribution-board.ses"
$LogDir    = Join-Path $RepoRoot "logs\Issue420"
$RouteLog  = Join-Path $LogDir   "freerouting-Issue420-route.log"
$MemLog    = Join-Path $LogDir   "freerouting-Issue420-memory.log"
$Jar       = Join-Path $RepoRoot "build\libs\freerouting-current-executable.jar"

# ── Sanity check ──────────────────────────────────────────────────────────
if (-not (Test-Path $Fixture)) {
    Write-Error "Fixture not found: $Fixture"
    exit 1
}

# ── Build ──────────────────────────────────────────────────────────────────
if (-not $SkipBuild) {
    Write-Host "`n==================================================" -ForegroundColor Cyan
    Write-Host "  Building freerouting-current-executable.jar ..." -ForegroundColor Cyan
    Write-Host "==================================================" -ForegroundColor Cyan
    Push-Location $RepoRoot
    & .\gradlew.bat executableJar
    $GradleExit = $LASTEXITCODE
    Pop-Location
    if ($GradleExit -ne 0) { Write-Error "Gradle build failed (exit $GradleExit)."; exit 1 }
}

if (-not (Test-Path $Jar)) {
    Write-Error "JAR not found: $Jar"
    exit 1
}

# ── Prepare log directory ──────────────────────────────────────────────────
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
Remove-Item $RouteLog -ErrorAction SilentlyContinue
Remove-Item $MemLog   -ErrorAction SilentlyContinue

# ── Print configuration ────────────────────────────────────────────────────
$StartTime = Get-Date
Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "  Issue #420 - Full-board OOM stress test"         -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Fixture    : $Fixture"
Write-Host "  Max passes : $MaxPasses"
Write-Host "  Max threads: $MaxThreads"
Write-Host "  Job timeout: $JobTimeout"
Write-Host "  Heap max   : $HeapMax"
Write-Host "  Route log  : $RouteLog"
Write-Host "  Memory log : $MemLog"
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Started at : $StartTime"
Write-Host "==================================================" -ForegroundColor Cyan

# ── Run the router ─────────────────────────────────────────────────────────
$jvmArgs = @(
    "-Xmx$HeapMax",
    "-Xms256m",
    "-XX:+HeapDumpOnOutOfMemoryError",
    "-XX:HeapDumpPath=$LogDir\Issue420-heapdump.hprof",
    "-jar", $Jar,
    "-de", $Fixture,
    "-do", $Output,
    "--gui.enabled=false",
    "--router.max_passes=$MaxPasses",
    "--router.max_threads=$MaxThreads",
    "--router.optimizer.enabled=true",
    "--router.optimizer.max_passes=100",
    "--router.job_timeout=$JobTimeout"
)

Write-Host "`nRunning router + optimizer (this may take many hours) ..." -ForegroundColor Yellow
Write-Host "Route log : $RouteLog" -ForegroundColor Yellow
Write-Host "Memory log: $MemLog`n"  -ForegroundColor Yellow

$process = Start-Process -FilePath "java" -ArgumentList $jvmArgs `
    -RedirectStandardOutput $RouteLog -RedirectStandardError "$RouteLog.stderr" `
    -NoNewWindow -PassThru

# ── Start memory-sampling background job (now that we have a PID) ─────────
$MemJob = Start-Job -Name "Issue420-MemSampler" -ScriptBlock {
    param($logFile, $targetPid)
    while ($true) {
        $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        $p  = Get-Process -Id $targetPid -ErrorAction SilentlyContinue
        if ($p) {
            $wsMB = [math]::Round($p.WorkingSet64 / 1MB, 1)
            Add-Content -Path $logFile -Value "$ts  PID=$targetPid  WorkingSet=${wsMB} MB"
        } else {
            Add-Content -Path $logFile -Value "$ts  (process $targetPid no longer running)"
            break
        }
        Start-Sleep -Seconds 30
    }
} -ArgumentList $MemLog, $process.Id

# Mirror the log file to console while the process runs
$lastLine = 0
while (-not $process.HasExited) {
    Start-Sleep -Seconds 5
    if (Test-Path $RouteLog) {
        $lines = Get-Content $RouteLog -ErrorAction SilentlyContinue
        if ($lines -and $lines.Count -gt $lastLine) {
            $lines[$lastLine..($lines.Count - 1)] | ForEach-Object { Write-Host $_ }
            $lastLine = $lines.Count
        }
    }
}
$process.WaitForExit()
$ExitCode = if ($process.ExitCode -ne $null) { $process.ExitCode } else { 0 }

# Flush any remaining log lines
if (Test-Path $RouteLog) {
    $lines = Get-Content $RouteLog -ErrorAction SilentlyContinue
    if ($lines -and $lines.Count -gt $lastLine) {
        $lines[$lastLine..($lines.Count - 1)] | ForEach-Object { Write-Host $_ }
    }
}

# Merge stderr into the main log
if (Test-Path "$RouteLog.stderr") {
    $stderr = Get-Content "$RouteLog.stderr" -ErrorAction SilentlyContinue
    if ($stderr) {
        Add-Content -Path $RouteLog -Value "`n--- STDERR ---"
        Add-Content -Path $RouteLog -Value $stderr
    }
    Remove-Item "$RouteLog.stderr" -ErrorAction SilentlyContinue
}

# ── Stop memory sampler ────────────────────────────────────────────────────
Stop-Job  $MemJob -ErrorAction SilentlyContinue
Remove-Job $MemJob -ErrorAction SilentlyContinue

# ── Final report ───────────────────────────────────────────────────────────
$EndTime  = Get-Date
$Elapsed  = $EndTime - $StartTime

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "  Finished at : $EndTime"
Write-Host "  Elapsed     : $([math]::Round($Elapsed.TotalMinutes, 1)) minutes"
Write-Host "  Exit code   : $ExitCode"
Write-Host "==================================================" -ForegroundColor Cyan

if ($ExitCode -eq 0) {
    Write-Host "  Result : PASSED - router exited cleanly." -ForegroundColor Green
} else {
    Write-Host "  Result : FAILED - see route log for details." -ForegroundColor Red
}

# Check for OOM in the log
if (Test-Path $RouteLog) {
    $oomLines = Select-String -Path $RouteLog -Pattern "OutOfMemoryError" -ErrorAction SilentlyContinue
    if ($oomLines) {
        Write-Host "`n  *** OutOfMemoryError DETECTED ***" -ForegroundColor Red
        $oomLines | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    } else {
        Write-Host "`n  No OutOfMemoryError found in route log." -ForegroundColor Green
    }
}

# Check for heap dump
$heapDump = Join-Path $LogDir "Issue420-heapdump.hprof"
if (Test-Path $heapDump) {
    $sizeMB = [math]::Round((Get-Item $heapDump).Length / 1MB, 0)
    Write-Host "`n  Heap dump written: $heapDump ($sizeMB MB)" -ForegroundColor Yellow
    Write-Host "  Open with Eclipse MAT and run 'Leak Suspects Report' to find the GC root."
}

# Print memory trend
Write-Host "`n  --- Memory samples (WorkingSet over time) ---" -ForegroundColor Cyan
if (Test-Path $MemLog) {
    Get-Content $MemLog | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "  (no memory samples captured)"
}

Write-Host "`n  Route log : $RouteLog"
Write-Host "  Memory log: $MemLog`n"

