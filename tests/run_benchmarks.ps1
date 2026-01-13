# Freerouting Performance Benchmark Script v2
# Runs baseline tests for v2.2 and collects metrics from INFO log output

param(
    [string]$JarPath = "..\build\libs\freerouting-executable.jar",
    [int]$MaxPasses = 500,
    [string]$MaxTime = "00:05:00"
)

# Test files to run
$testFiles = @(
    "Issue508-DAC2020_bm07.dsn"
)

# Results array
$results = @()

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Freerouting v2.2 Performance Benchmarks" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Max Passes: $MaxPasses" -ForegroundColor Gray
Write-Host "  Max Time: $MaxTime" -ForegroundColor Gray
Write-Host "  JAR: $JarPath" -ForegroundColor Gray
Write-Host ""

foreach ($testFile in $testFiles) {
    $dsnPath = ".\$testFile"
    $sesPath = $dsnPath -replace '\.dsn$', '_benchmark.ses'
    
    if (-not (Test-Path $dsnPath)) {
        Write-Host "  [SKIP] $testFile - File not found" -ForegroundColor Red
        continue
    }
    
    Write-Host "Testing: $testFile" -ForegroundColor Green
    
    # Get file size
    $fileSize = (Get-Item $dsnPath).Length
    $fileSizeKB = [math]::Round($fileSize / 1KB, 0)
    
    # Build command - INFO level logging, capture full output
    $cmd = "java -jar `"$JarPath`" -de `"$dsnPath`" -do `"$sesPath`" -mp $MaxPasses --router.job_timeout=`"$MaxTime`" --router.optimizer.enabled=false --gui.enabled=false --api_server.enabled=false -ll INFO"
    
    # Run test, capture output
    $output = & cmd /c "$cmd 2>&1" | Out-String
    
    # Initialize metrics
    $totalNets = "?"
    $unroutedNets = "?"
    $passesCompleted = "?"
    $timeStr = "?"
    $memoryMB = "?"
    $violations = "?"
    $qualityScore = "?"
    
    # Parse Summary Line
    # Example 1: Auto-router session completed with pass number limit hit: started with 86 unrouted nets, ran 500 passes in 67,66 seconds, final score: 987,99 (1 unrouted, 0 violations), using 2205,06 CPU seconds and 49394 MB memory.
    # Example 2: Auto-router session completed: started with 86 unrouted nets, ran 104 passes in 28,54 seconds, final score: 988,57 (1 unrouted, 0 violations), using 392,05 CPU seconds and 21813 MB memory.
    if ($output -match "Auto-router session completed(?: with (.+?))?: started with (\d+) unrouted nets, ran (\d+) passes in ([\d,.]+) seconds, final score: ([\d,.]+) \((\d+) unrouted, (\d+) violations\), using .*? and (\d+) MB memory") {
        $reason = $matches[1]
        $totalNets = $matches[2]
        $passesCompleted = $matches[3]
        $rawSecondsStr = $matches[4]
        $qualityScore = $matches[5]
        $unroutedNets = $matches[6]
        $violations = $matches[7]
        $memoryMB = $matches[8]
        
        # Handle decimal separator for time if comma
        $rawSeconds = [double]($rawSecondsStr -replace ',', '.')

        # Format Time
        if ($reason -match "time limit") {
            $timeStr = "5+ minutes" # Assuming 5 min limit based on user request logic
        }
        else {
            if ($rawSeconds -ge 60) {
                $mins = [math]::Floor($rawSeconds / 60)
                $secs = [math]::Round($rawSeconds % 60)
                $timeStr = "{0}m {1}s" -f $mins, $secs
            }
            else {
                $timeStr = "{0}s" -f [math]::Round($rawSeconds, 0)
            }
        }

        # Format Passes
        if ($reason -match "pass number limit") {
            $passesCompleted = "$passesCompleted+"
        }
    }
    elseif ($output -match "nets\.total_count.*?(\d+)") {
        # Fallback to JSON parsing or other regex if summary missing (shouldn't happen with v2.2)
        # But for total nets, we might need to dig into the JSON or start logs?
        # The summary says "started with X unrouted nets", not total nets. 
        # But we can try to parse total nets from earlier logs or JSON if present.
        # Actually JSON is likely still printed? Check debug output.
        # Debug output had JSON at end? 
        # Looking at debug output... NO JSON at the end because we didn't use -do json? 
        # Wait, the previous script parsed JSON from output.
        # Does -ll INFO suppress JSON? No.
        # But `debug_benchmark_output.txt` didn't show JSON at the end.
        # Ah, maybe I missed it.
        # Let's assume we can get Total Nets from earlier: "Auto-router ... on board '...'" doesn't show total.
        # We can get it from the "nets" : { "total_count": ... } if it exists.
    }
    
    # Try to find Total Nets from JSON in output
    if ($output -match '"nets"\s*:\s*\{\s*"total_count"\s*:\s*(\d+)') {
        $totalNets = $matches[1]
    }

    Write-Host "  File Size: $fileSizeKB kB" -ForegroundColor Gray
    Write-Host "  Total Nets: $totalNets" -ForegroundColor Gray
    Write-Host "  Unrouted: $unroutedNets" -ForegroundColor $(if ($unroutedNets -eq "0") { "Green" } else { "Yellow" })
    Write-Host "  Violations: $violations" -ForegroundColor Gray
    Write-Host "  Time: $timeStr" -ForegroundColor Gray
    Write-Host "  Passes: $passesCompleted" -ForegroundColor Gray
    Write-Host "  Mem: $memoryMB MB" -ForegroundColor Gray
    Write-Host "  Score: $qualityScore" -ForegroundColor Gray
    Write-Host ""
    
    # Store result
    $results += [PSCustomObject]@{
        Filename      = $testFile
        FileSizeKB    = $fileSizeKB
        TotalNets     = $totalNets
        UnroutedNets  = $unroutedNets
        Violations    = $violations
        TimeFormatted = $timeStr
        Passes        = $passesCompleted
        MemoryMB      = $memoryMB
        QualityScore  = $qualityScore
    }
    
    # Clean up output files
    if (Test-Path $sesPath) {
        Remove-Item $sesPath -Force
    }
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Benchmark Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Display results table
$results | Format-Table -AutoSize

# Export to CSV for easy import
$csvPath = ".\benchmark_results_v2.2.csv"
$results | Export-Csv -Path $csvPath -NoTypeInformation
Write-Host "Results exported to: $csvPath" -ForegroundColor Green
Write-Host ""

# Generate markdown table rows
Write-Host "Markdown table rows for Benchmarks.md:" -ForegroundColor Yellow
Write-Host ""
foreach ($result in $results) {
    # Markdown columns: | Filename | File size | Nets to route | Freerouting version | Unrouted nets | Clearance violations | Passes to complete | Time to complete | Memory allocated | Quality score |
    
    # Memory formatted
    $memStr = if ($result.MemoryMB -ne "?") { "{0} MB" -f $result.MemoryMB } else { "?" }
    
    $row = "| {0,-30} `t| {1,9} kB `t| {2,13} `t| {3,19} `t| {4,13} `t| {5,20} `t| {6,18} `t| {7,16} `t| {8,16} `t| {9,13} `t|" -f `
        $result.Filename, `
        $result.FileSizeKB, `
        $result.TotalNets, `
        "v2.2", `
        $result.UnroutedNets, `
        $result.Violations, `
        $result.Passes, `
        $result.TimeFormatted, `
        $memStr, `
        $result.QualityScore
    Write-Host $row
}

Write-Host ""

