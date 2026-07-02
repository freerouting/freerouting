# Run fanout benchmarks in parallel
$strats = @('inner_first', 'outer_first', 'distance_to_closest_on_net', 'surroundings_density', 'unsorted')
New-Item -ItemType Directory -Force -Path logs | Out-Null

$projectRoot = (Get-Item .).FullName

$jobs = @()
foreach ($strat in $strats) {
    Write-Host "Starting background job for strategy: $strat"
    $logFile = Join-Path $projectRoot "logs/run-$strat.log"
    $job = Start-Job -ScriptBlock {
        param($strat, $logFile, $workDir)
        Set-Location $workDir
        & java -jar build/libs/freerouting-current-executable.jar `
            -de scripts/benchmark/fixtures/KiCad_10_demos/CM5_MINIMA_3.dsn `
            -do "logs/test_out-$strat.ses" `
            --router.fanout.enabled=true `
            --router.optimizer.enabled=false `
            --gui.enabled=false `
            --api_server.enabled=false `
            --router.max_passes=10 `
            --router.fanout.pin_sorting_order=$strat `
            --logging.console.level=INFO `
            *>&1 | Out-File -FilePath $logFile -Encoding utf8
    } -ArgumentList $strat, $logFile, $projectRoot
    $jobs += $job
}

Write-Host "Waiting for all benchmark jobs to finish..."
$jobs | Wait-Job
Write-Host "All jobs completed."
