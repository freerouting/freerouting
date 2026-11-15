$rows = Import-Csv 'fixtures\BGA_Escape_Test_HARD_hardware_metrics.csv'
Write-Host "Total Telemetry Samples: $($rows.Count)"

$cpuVals = $rows | ForEach-Object { [double]$_.CpuTotalPercent }
$cpuStats = $cpuVals | Measure-Object -Average -Maximum -Minimum
Write-Host "CPU Total %: Avg=$([math]::Round($cpuStats.Average, 1))%, Max=$($cpuStats.Maximum)%"

$memVals = $rows | ForEach-Object { [double]$_.ProcessWorkingSetMB }
$memStats = $memVals | Measure-Object -Average -Maximum -Minimum
Write-Host "Process Working Set (RAM): Avg=$([math]::Round($memStats.Average, 1)) MB, Peak=$($memStats.Maximum) MB"

$gpuVals = $rows | ForEach-Object { [double]$_.GpuPercent }
$gpuStats = $gpuVals | Measure-Object -Average -Maximum -Minimum
Write-Host "dGPU %: Avg=$([math]::Round($gpuStats.Average, 1))%, Max=$($gpuStats.Maximum)%"
