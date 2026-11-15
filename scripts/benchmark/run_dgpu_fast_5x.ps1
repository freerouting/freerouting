param(
    [int]$Iterations = 5,
    [int]$TimeoutSeconds = 30
)

$JarPath = "build\libs\freerouting-current-executable.jar"
$DsnPath = "fixtures\BGA_Escape_Test_HARD_v2.dsn"
$SesPath = "fixtures\BGA_Escape_Test_HARD_v2_repeat.ses"

$times = @()

for ($i = 1; $i -le $Iterations; $i++) {
    Write-Host "`n=======================================================" -ForegroundColor Cyan
    Write-Host ">>> RUN ${i} OF ${Iterations} - NVIDIA RTX 5070 Ti (BGA Escape) <<<" -ForegroundColor Yellow
    Write-Host "=======================================================" -ForegroundColor Cyan

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $logFile = "fixtures\dgpu_repeat_run_$i.log"

    $args = @(
        "--enable-native-access=ALL-UNNAMED",
        "-jar", $JarPath,
        "-de", $DsnPath,
        "-do", $SesPath,
        "--router.fanout.gpu_acceleration_enabled=true",
        "--router.fanout.gpu_device_preference=dgpu"
    )

    $proc = Start-Process -FilePath "java" -ArgumentList $args -NoNewWindow -PassThru -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err"
    
    # Loop until GPU kernel completes or timeout hits
    $gpuDone = $false
    while (-not $proc.HasExited -and $sw.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        Start-Sleep -Milliseconds 250
        if (Test-Path $logFile) {
            $content = Get-Content $logFile -ErrorAction SilentlyContinue | Out-String
            if ($content -match "BGA escape committer: committed") {
                $gpuDone = $true
                break
            }
        }
    }

    # Stop process immediately once GPU escape phase is done
    if (-not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    }
    $sw.Stop()

    $elapsedSec = [math]::Round($sw.Elapsed.TotalSeconds, 2)
    $times += $elapsedSec
    Write-Host ">>> RUN ${i} COMPLETED in ${elapsedSec} seconds <<<" -ForegroundColor Green

    # Output log lines
    $gpuLines = Get-Content $logFile | Select-String "GPU|BGA escape committer"
    foreach ($line in $gpuLines) {
        Write-Host "   $line" -ForegroundColor DarkGray
    }
}

$avg = [math]::Round(($times | Measure-Object -Average).Average, 2)
Write-Host "`n=======================================================" -ForegroundColor Cyan
Write-Host "ALL ${Iterations} RUNS COMPLETED. Average Duration: ${avg} seconds" -ForegroundColor Yellow
Write-Host "Run Durations: $($times -join ', ') seconds" -ForegroundColor Yellow
Write-Host "=======================================================" -ForegroundColor Cyan
