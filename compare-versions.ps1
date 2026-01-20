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
    [int]$max_passes = 5,
    [int]$max_threads = 1,
    [string]$job_timeout = "00:01:30"
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
Write-Host "  Max Threads: $max_threads" -ForegroundColor White
Write-Host "  Timeout:     $job_timeout" -ForegroundColor White

# Log Files
$CurrentLogFile = Join-Path $LogBaseDir "freerouting-current.log"
$V19LogFile = Join-Path $LogBaseDir "freerouting-v190.log"

$BaseArgs = @(
    "-de", "`"$InputFileAbs`""
    "-do", "`"$OutputFileAbs`""
    "--router.optimizer.enabled=false"
    "--gui.enabled=false"
    "--api_server.enabled=false"
    "--router.job_timeout=`"$job_timeout`""
    "--router.max_passes=$max_passes"
    "--router.max_threads=$max_threads"
    "--logging.file.level=$LoggingLevel"
    "--logging.console.level=INFO"
)

# Function to run version and capture results
function Invoke-Version {
    param(
        [string]$VersionName,
        [string]$JarPath,
        [string]$LogPath,
        [string]$Color
    )

    Write-Host "`n--------------------------------------------------" -ForegroundColor $Color
    Write-Host "  Running $VersionName" -ForegroundColor $Color
    Write-Host "--------------------------------------------------" -ForegroundColor $Color

    # Clean previous log if exists
    if (Test-Path $LogPath) { Remove-Item $LogPath -Force }

    $env:FREEROUTING_LOG_DIR = $LogBaseDir

    # Construct flat argument list with specific log location
    $ProcessArgs = @("-jar", $JarPath) + $BaseArgs + @("--logging.file.location=$LogPath")

    Write-Host "Command: java $ProcessArgs" -ForegroundColor Gray
    Write-Host "Log Target: $LogPath"       -ForegroundColor Gray

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

        return @{
            ExitCode = $Process.ExitCode
            Duration = $Duration
            LogFile  = $LogPath
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
    -Color $InfoColor

# Run V1.9 Version
$V19Result = Invoke-Version -VersionName "V1.9 Version" `
    -JarPath $V19Jar `
    -LogPath $V19LogFile `
    -Color $SuccessColor

# Summary
Write-Host "`n==================================================" -ForegroundColor $InfoColor
Write-Host "  Comparison Summary" -ForegroundColor $InfoColor
Write-Host "==================================================" -ForegroundColor $InfoColor

if ($CurrentResult -and $V19Result) {
    Write-Host "`nExecution Times:" -ForegroundColor White
    Write-Host "  Current: $($CurrentResult.Duration.ToString('mm\:ss\.fff'))" -ForegroundColor White
    Write-Host "  V1.9:    $($V19Result.Duration.ToString('mm\:ss\.fff'))"     -ForegroundColor White

    $TimeDiff = $CurrentResult.Duration - $V19Result.Duration
    if ($TimeDiff.TotalMilliseconds -gt 0) {
        Write-Host "  V1.9 was $([math]::Abs($TimeDiff.TotalSeconds)) s faster" -ForegroundColor $SuccessColor
    }
    else {
        Write-Host "  Current was $([math]::Abs($TimeDiff.TotalSeconds)) s faster" -ForegroundColor $SuccessColor
    }

    Write-Host "`nCompare logs:" -ForegroundColor $InfoColor
    Write-Host "  code --diff `"$($CurrentResult.LogFile)`" `"$($V19Result.LogFile)`"" -ForegroundColor Yellow
}