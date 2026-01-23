# Compare Freerouting Versions Script
# Runs both current and v1.9 executables with identical arguments
#
# Usage:
#   .\compare-versions.ps1
#   .\compare-versions.ps1 -de ".\tests\Issue508-DAC2020_bm01.dsn"

param(
    [string]$de = ".\tests\Issue508-DAC2020_bm01.dsn",
    [string]$do = ".\tests\Issue508-DAC2020_bm01.ses",
    [string]$LoggingLocation = ".\logs\",
    [string]$LoggingLevel = "DEBUG",
    [string]$LoggingPattern = "%msg%n",
    [int]$max_passes = 3,
    [int]$max_items = 3,
    [int]$max_threads = 1,
    [string]$job_timeout = "00:03:00"
)

# Colors for output
$ErrorColor = "Red"
$SuccessColor = "Green"
$InfoColor = "Cyan"
$WarningColor = "Yellow"

Write-Host "`n==================================================" -ForegroundColor $InfoColor
Write-Host "  Freerouting Version Comparison Test" -ForegroundColor $InfoColor
Write-Host "==================================================" -ForegroundColor $InfoColor

# Check if input file exists
if (-not (Test-Path $de)) {
    Write-Host "ERROR: Input file not found: $de" -ForegroundColor $ErrorColor
    exit 1
}

# Get absolute paths
$InputFileAbs = Resolve-Path $de
$OutputFileAbs = $do

# Rebuild executables
Write-Host "Building executables..." -ForegroundColor $InfoColor
& .\gradlew.bat buildBothVersions
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Gradle build failed." -ForegroundColor $ErrorColor
    exit 1
}

# JAR files
$CurrentJar = ".\build\libs\freerouting-current-executable.jar"
$V19Jar = ".\build\libs\freerouting-1.9.0-executable.jar"

# Check if JARs exist
if (-not (Test-Path $CurrentJar)) {
    Write-Host "ERROR: Current JAR not found. Run: .\gradlew.bat executableJar" -ForegroundColor $ErrorColor
    exit 1
}
if (-not (Test-Path $V19Jar)) {
    Write-Host "ERROR: V1.9 JAR not found. Run: .\gradlew.bat executableV19Jar" -ForegroundColor $ErrorColor
    exit 1
}

# Create log directory
$LogBaseDir = ".\logs"
New-Item -ItemType Directory -Force -Path $LogBaseDir | Out-Null

Write-Host "`nConfiguration:" -ForegroundColor $InfoColor
Write-Host "  Input File:  $de"         -ForegroundColor White
Write-Host "  Output File: $do"         -ForegroundColor White
Write-Host "  Log Level:   $LoggingLevel" -ForegroundColor White
Write-Host "  Max Passes:  $max_passes" -ForegroundColor White
Write-Host "  Max Items:   $max_items" -ForegroundColor White
Write-Host "  Max Threads: $max_threads" -ForegroundColor White
Write-Host "  Timeout:     $job_timeout" -ForegroundColor White

# Log Files
$CurrentLogFile = Join-Path $LogBaseDir "freerouting-current.log"
$V19LogFile = Join-Path $LogBaseDir "freerouting-v190.log"

# Clean up old logs
if (Test-Path $CurrentLogFile) { Remove-Item $CurrentLogFile -Force }
if (Test-Path $V19LogFile) { Remove-Item $V19LogFile -Force }

# Calculate Output Files
$OutputDirectory = Split-Path $OutputFileAbs -Parent
$OutputBaseName = [System.IO.Path]::GetFileNameWithoutExtension($OutputFileAbs)
$OutputExtension = [System.IO.Path]::GetExtension($OutputFileAbs)

$CurrentOutputFile = Join-Path $OutputDirectory "$($OutputBaseName)-current$($OutputExtension)"
$V19OutputFile = Join-Path $OutputDirectory "$($OutputBaseName)-v190$($OutputExtension)"

$BaseArgs = @(
    "-de", "`"$InputFileAbs`""
    "--router.optimizer.enabled=false"
    "--gui.enabled=false"
    "--api_server.enabled=false"
    "--router.job_timeout=`"$job_timeout`""
    "--router.max_passes=$max_passes"
    "--router.max_items=$max_items"
    "--router.max_threads=$max_threads"
    "--logging.file.level=$LoggingLevel"
    "--logging.file.pattern=$LoggingPattern"
    "--logging.console.level=INFO"
)

# Function to parse log results
function Parse-LogResults {
    param (
        [string]$LogPath
    )
    
    $Result = @{
        AutoRouterTime = "N/A"
        Unrouted       = "0"
        PeakHeap       = "N/A"
        Passes         = "0"
        FoundSummary   = $false
    }
    
    if (Test-Path $LogPath) {
        $Content = Get-Content $LogPath -Tail 1000
        # Look for the session summary line
        # Pattern: Auto-router session.*completed.*
        $SummaryLine = $Content | Where-Object { $_ -match "Auto-router session.*completed" } | Select-Object -Last 1
        
        # Extract max pass number
        # Look for lines like "Pass 1  :" or "Pass #2"
        # Since we only read the last 1000 lines, we might miss early passes, but we should see the final ones.
        $PassLines = $Content | Where-Object { $_ -match "Pass (\d+)\s+:" }
        if ($PassLines) {
            $LastPassLine = $PassLines | Select-Object -Last 1
            if ($LastPassLine -match "Pass (\d+)\s+:") {
                $Result.Passes = $matches[1]
            }
        }
        
        if ($SummaryLine) {
            $Result.FoundSummary = $true
            
            # Extract Duration: completed in (.*?), final score
            if ($SummaryLine -match "completed in (.*?), final score") {
                $Result.AutoRouterTime = $matches[1]
            }
            
            # Extract Unrouted: (\d+) unrouted
            if ($SummaryLine -match "\((\d+) unrouted\)") {
                $Result.Unrouted = $matches[1]
            }
            
            # Extract Peak Heap: and (.*?) MB peak heap usage
            if ($SummaryLine -match "and (.*?) MB peak heap usage") {
                $Result.PeakHeap = $matches[1]
            }
        }
    }
    
    return $Result
}

# Function to run version and capture results
function Invoke-Version {
    param(
        [string]$VersionName,
        [string]$JarPath,
        [string]$LogPath,
        [string]$OutputFile,
        [string]$Color
    )

    Write-Host "`n--------------------------------------------------" -ForegroundColor $Color
    Write-Host "  Running $VersionName" -ForegroundColor $Color
    Write-Host "--------------------------------------------------" -ForegroundColor $Color

    # Clean previous log if exists
    if (Test-Path $LogPath) { Remove-Item $LogPath -Force }

    $env:FREEROUTING_LOG_DIR = $LogBaseDir

    # Construct flat argument list with specific log location and output file
    $ProcessArgs = @("-jar", $JarPath) + $BaseArgs + @("--logging.file.location=$LogPath", "-do", "`"$OutputFile`"")

    Write-Host "Command: java $ProcessArgs" -ForegroundColor Gray
    Write-Host "Log Target:    $LogPath"    -ForegroundColor Gray
    Write-Host "Output Target: $OutputFile" -ForegroundColor Gray

    $StartTime = Get-Date

    try {
        $Process = Start-Process -FilePath "java" `
            -ArgumentList $ProcessArgs `
            -Wait -PassThru -NoNewWindow

        $EndTime = Get-Date
        $Duration = $EndTime - $StartTime

        if ($Process.ExitCode -eq 0) {
            Write-Host "$VersionName completed successfully" -ForegroundColor $SuccessColor
        }
        else {
            Write-Host "✗ $VersionName exited with code $($Process.ExitCode)" -ForegroundColor $WarningColor
        }

        Write-Host "  Duration: $($Duration.ToString('mm\:ss\.fff'))" -ForegroundColor White

        # Check if log file exists (Java should have written it directly)
        if (Test-Path $LogPath) {
            $LogSize = (Get-Item $LogPath).Length
            $FormattedSize = [math]::Round($LogSize / 1MB, 2)
            Write-Host "  Log Saved: $LogPath ($FormattedSize MB)" -ForegroundColor White
        }
        else {
            Write-Host "  WARNING: Log file not found at $LogPath" -ForegroundColor $WarningColor
        }
        
        $LogResults = Parse-LogResults -LogPath $LogPath

        return @{
            VersionName    = $VersionName
            ExitCode       = $Process.ExitCode
            Duration       = $Duration
            LogFile        = $LogPath
            AutoRouterTime = $LogResults.AutoRouterTime
            Unrouted       = $LogResults.Unrouted
            PeakHeap       = $LogResults.PeakHeap
            Passes         = $LogResults.Passes
        }
    }
    catch {
        Write-Host "✗ Error running $VersionName : $_" -ForegroundColor $ErrorColor
        return $null
    }
}

# Run Current Version
$CurrentResult = Invoke-Version -VersionName "Current Version" `
    -JarPath $CurrentJar `
    -LogPath $CurrentLogFile `
    -OutputFile $CurrentOutputFile `
    -Color $InfoColor

# Run V1.9 Version
$V19Result = Invoke-Version -VersionName "V1.9 Version" `
    -JarPath $V19Jar `
    -LogPath $V19LogFile `
    -OutputFile $V19OutputFile `
    -Color $SuccessColor

# Summary
Write-Host "`n==================================================" -ForegroundColor $InfoColor
Write-Host "  Comparison Summary" -ForegroundColor $InfoColor
Write-Host "==================================================" -ForegroundColor $InfoColor

if ($CurrentResult -and $V19Result) {
    $Results = @($CurrentResult, $V19Result)

    foreach ($Res in $Results) {
        if ($Res) {
            Write-Host "`n$($Res.VersionName)" -ForegroundColor Cyan
            Write-Host "  Router Time:    $($Res.AutoRouterTime)"
            Write-Host "  Process Time:   $($Res.Duration.ToString('mm\:ss\.fff'))"
            Write-Host "  Passes:         $($Res.Passes)"
            Write-Host "  Unrouted Items: $($Res.Unrouted)"
            Write-Host "  Peak Heap:      $($Res.PeakHeap) MB"
        }
    }

    Write-Host "`nCompare logs:" -ForegroundColor $InfoColor
    Write-Host "  code --diff `"$($CurrentResult.LogFile)`" `"$($V19Result.LogFile)`"" -ForegroundColor Yellow
}