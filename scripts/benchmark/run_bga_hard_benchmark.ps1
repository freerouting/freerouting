param(
    [string]$JarPath = "build\libs\freerouting-current-executable.jar",
    [string]$DsnPath = "fixtures\BGA_Escape_Test_HARD.dsn",
    [string]$SesPath = "fixtures\BGA_Escape_Test_HARD.ses",
    [string]$LogPath = "fixtures\BGA_Escape_Test_HARD_routing.log",
    [string]$MetricsPath = "fixtures\BGA_Escape_Test_HARD_hardware_metrics.csv"
)

$ErrorActionPreference = "Stop"

Write-Host "Starting Freerouting CLI process..."
$freeroutingProc = Start-Process -FilePath "java" `
    -ArgumentList "--enable-native-access=ALL-UNNAMED", "-jar", "`"$JarPath`"", `
                  "-de", "`"$DsnPath`"", `
                  "-do", "`"$SesPath`"", `
                  "--router.fanout.gpu_acceleration_enabled=true", `
                  "--router.fanout.gpu_device_preference=auto", `
                  "--router.max_passes=5", `
                  "--benchmark" `
    -NoNewWindow -PassThru -RedirectStandardOutput $LogPath -RedirectStandardError "$LogPath.err"

Write-Host "Process started with PID $($freeroutingProc.Id). Monitoring hardware utilization..."

"Timestamp,CpuTotalPercent,ProcessCpuPercent,ProcessWorkingSetMB,GpuPercent,GpuMemUsedMB,GpuMemTotalMB,GpuTempC" | Out-File -FilePath $MetricsPath -Encoding utf8

$cpuCounter = New-Object System.Diagnostics.PerformanceCounter("Processor", "% Processor Time", "_Total")
[void]$cpuCounter.NextValue()

while (-not $freeroutingProc.HasExited) {
    Start-Sleep -Milliseconds 500
    
    $cpuTotal = [math]::Round($cpuCounter.NextValue(), 1)
    
    $procWs = 0
    try {
        $p = Get-Process -Id $freeroutingProc.Id -ErrorAction SilentlyContinue
        if ($p) {
            $procWs = [math]::Round($p.WorkingSet64 / 1MB, 1)
        }
    } catch {}

    $gpuLine = ""
    try {
        $smi = & nvidia-smi.exe --query-gpu=utilization.gpu,memory.used,memory.total,temperature.gpu --format=csv,noheader,nounits 2>$null
        if ($smi) {
            $gpuLine = ($smi -split "," | ForEach-Object { $_.Trim() }) -join ","
        }
    } catch {}

    if (-not $gpuLine) {
        $gpuLine = "0,0,0,0"
    }

    $timeStr = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss.fff")
    "$timeStr,$cpuTotal,0,$procWs,$gpuLine" | Out-File -FilePath $MetricsPath -Append -Encoding utf8
}

$freeroutingProc.WaitForExit()
Write-Host "Freerouting process completed with exit code $($freeroutingProc.ExitCode)."
