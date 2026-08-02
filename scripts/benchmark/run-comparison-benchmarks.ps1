<#
.SYNOPSIS
    Runs freerouting-current benchmarks on two git branches and writes one combined report.

.DESCRIPTION
    Intended for overnight A/B runs (typically ~4 h per branch, ~8 h total).
    Builds freerouting-current on each branch, runs scripts/benchmark/run-benchmarks.ps1
    against the same isolated results folder, and regenerates a single benchmarks.md
    with both version labels side-by-side in every fixture table.

    On master/main, the current JAR is labeled sYYYY.MM.DD.
    On feature branches (e.g. pr/764), the current JAR is labeled with the branch name.

.PARAMETER BaselineBranch
    Reference branch (default: master).

.PARAMETER CompareBranch
    Local branch to compare (default: pr/764). Created via git fetch if missing.

.PARAMETER ComparePrNumber
    GitHub PR number used to fetch CompareBranch when it does not exist locally.

.PARAMETER Force
    Passed through to run-benchmarks.ps1 to ignore cached runs.

.PARAMETER AllowDirty
    Continue even when the working tree has uncommitted changes.

.PARAMETER Resume
    Keep an existing comparison results folder and skip phases whose version label
    already has at least one cached run.

.EXAMPLE
    powershell -File scripts/benchmark/run-comparison-benchmarks.ps1

.EXAMPLE
    powershell -File scripts/benchmark/run-comparison-benchmarks.ps1 -CompareBranch pr/764 -ComparePrNumber 764
#>
param(
    [string] $BaselineBranch = "master",
    [string] $CompareBranch = "pr/764",
    [int]    $ComparePrNumber = 764,
    [switch] $Force,
    [switch] $AllowDirty,
    [switch] $Resume
)

Set-StrictMode -Off
$ErrorActionPreference = "Stop"

$BenchmarkRoot = $PSScriptRoot
$RepoRoot = (Resolve-Path (Join-Path $BenchmarkRoot "../..")).Path
$RunBenchmarksScript = Join-Path $BenchmarkRoot "run-benchmarks.ps1"
$BinariesDir = Join-Path $BenchmarkRoot "binaries"
$ComparisonSlug = "$BaselineBranch-vs-$($CompareBranch -replace '[\\/:*?"<>|]', '-')"
$ResultsDir = Join-Path $BenchmarkRoot "results/comparison-$ComparisonSlug"
$ReportPath = Join-Path $ResultsDir "benchmarks.md"
$JsonPath = Join-Path $ResultsDir "benchmarks.json"
$OvernightLog = Join-Path $ResultsDir "overnight-run.log"

function Write-Log {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Write-Output $line
    Add-Content -Path $OvernightLog -Value $line -Encoding UTF8
}

function Invoke-Git {
    param([string[]]$GitArgs)

    Push-Location $RepoRoot
    try {
        # Git writes progress messages (e.g. "Already on 'master'") to stderr.
        # With ErrorAction Stop, PowerShell would treat those as terminating errors.
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $output = & git @GitArgs 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $prevEap
        }

        if ($exitCode -ne 0) {
            throw "git $($GitArgs -join ' ') failed (exit $exitCode): $output"
        }
        if ($output) {
            Write-Log ($output | Out-String).TrimEnd()
        }
        return $output
    } finally {
        Pop-Location
    }
}

function Get-CurrentGitBranch {
    Push-Location $RepoRoot
    try {
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $branch = (git rev-parse --abbrev-ref HEAD 2>&1)
            if ($LASTEXITCODE -ne 0) {
                throw "git rev-parse --abbrev-ref HEAD failed: $branch"
            }
            return $branch.ToString().Trim()
        } finally {
            $ErrorActionPreference = $prevEap
        }
    } finally {
        Pop-Location
    }
}

function Ensure-GitBranch {
    param([string]$Branch)

    $current = Get-CurrentGitBranch
    if ($current -eq $Branch) {
        Write-Log "Already on branch '$Branch'; skipping checkout."
        return
    }
    Invoke-Git @("checkout", $Branch)
}

function Get-BranchJarSlug {
    param([string]$Branch)
    return ($Branch -replace '[\\/:*?"<>|]', '-')
}

function Get-BranchJarPath {
    param([string]$Branch)
    $slug = Get-BranchJarSlug $Branch
    return Join-Path $BinariesDir "freerouting-current-$slug.jar"
}

function Stop-FreeroutingJavaProcesses {
    $procs = @(
        Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -and $_.CommandLine -match 'freerouting' }
    )
    if (@($procs).Count -eq 0) {
        return
    }
    foreach ($proc in $procs) {
        Write-Log ('Stopping Freerouting Java process PID {0}...' -f $proc.ProcessId)
        Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
}

function Copy-JarWithRetry {
    param(
        [string]$Source,
        [string]$Destination,
        [int]$MaxAttempts = 6
    )

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            Stop-FreeroutingJavaProcesses
            if (Test-Path $Destination) {
                Remove-Item $Destination -Force -ErrorAction Stop
            }
            Copy-Item $Source $Destination -Force -ErrorAction Stop
            return
        } catch {
            if ($attempt -eq $MaxAttempts) {
                throw "Failed to copy JAR to '$Destination' after $MaxAttempts attempts: $($_.Exception.Message)"
            }
            Write-Log ('JAR copy attempt {0}/{1} failed ({2}); retrying in 5s...' -f $attempt, $MaxAttempts, $_.Exception.Message)
            Start-Sleep -Seconds 5
        }
    }
}

function Get-CachedVersionLabels {
    if (-not (Test-Path $JsonPath)) {
        return @()
    }
    . (Join-Path $BenchmarkRoot "lib/JsonStore.ps1")
    $store = Load-BenchmarksJson $JsonPath
    return @($store.Cache.Values | ForEach-Object { $_.binary.version_label } | Select-Object -Unique)
}

function Test-PhaseAlreadyCached {
    param([string]$Branch)
    $cached = Get-CachedVersionLabels
    if ($Branch -notin @('master', 'main')) {
        return $cached -contains $Branch
    }
    return (@($cached | Where-Object { $_ -like 's*' })).Count -gt 0
}

function Get-CurrentJarVersionLabel {
    param([string]$JarPath)

    . (Join-Path $BenchmarkRoot "lib/BinaryInfo.ps1")
    if (-not (Test-Path $JarPath)) {
        throw "JAR not found: $JarPath"
    }
    return Get-BinaryVersionLabel (Get-Item $JarPath)
}

function Invoke-BenchmarkPhase {
    param(
        [string]$Branch,
        [int]$PrNumber = 0
    )

    Write-Log "========== Phase start: $Branch =========="

    if ($PrNumber -gt 0) {
        Write-Log ('Fetching PR #{0} into local branch ''{1}''...' -f $PrNumber, $Branch)
        Invoke-Git @("fetch", "origin", "pull/$PrNumber/head:$Branch")
    }

    if ($Resume -and (Test-PhaseAlreadyCached $Branch)) {
        $cached = Get-CachedVersionLabels
        if ($Branch -notin @('master', 'main')) {
            $label = $Branch
        } else {
            $label = @($cached | Where-Object { $_ -like 's*' } | Select-Object -Last 1)
        }
        Write-Log "Resume: cached runs already exist for '$Branch' (label '$label'); skipping phase."
        return $label
    }

    Ensure-GitBranch $Branch

    if ($Branch -eq $BaselineBranch) {
        Write-Log "Updating $BaselineBranch from origin..."
        Invoke-Git @("pull", "--ff-only", "origin", $BaselineBranch)
    } else {
        Write-Log "Rebasing $Branch onto origin/$BaselineBranch..."
        Invoke-Git @("rebase", "origin/$BaselineBranch")
    }

    Write-Log "Building freerouting-current executable JAR..."
    Push-Location $RepoRoot
    try {
        & .\gradlew.bat executableJar --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed on branch '$Branch' (exit code $LASTEXITCODE)."
        }

        $srcJar = Join-Path $RepoRoot "build\libs\freerouting-current-executable.jar"
        if (-not (Test-Path $srcJar)) {
            throw "Expected JAR not found after build: $srcJar"
        }

        $destJar = Get-BranchJarPath $Branch
        Copy-JarWithRetry -Source $srcJar -Destination $destJar
        Write-Log "Copied JAR to $destJar"
    } finally {
        Pop-Location
    }

    $versionLabel = Get-CurrentJarVersionLabel -JarPath $destJar
    Write-Log "Running benchmarks for version label '$versionLabel'..."
    Write-Log 'Benchmark suite starting - live output from run-benchmarks.ps1 follows (several hours for all fixtures).'

    $jarFileName = Split-Path $destJar -Leaf
    Push-Location $BenchmarkRoot
    try {
        $benchParams = @{
            ResultsDir   = $ResultsDir
            BinariesDir  = $BinariesDir
            FilterBinary = $jarFileName
        }
        if ($Force) {
            $benchParams.Force = $true
        }

        & $RunBenchmarksScript @benchParams
        if ($LASTEXITCODE -ne 0) {
            throw "run-benchmarks.ps1 failed on branch '$Branch' (exit code $LASTEXITCODE)."
        }
    } finally {
        Pop-Location
    }

    Write-Log "Phase complete: $Branch ($versionLabel)"
    return $versionLabel
}

function Add-ComparisonHeader {
    param(
        [string]$BaselineLabel,
        [string]$CompareLabel,
        [datetime]$StartedAt
    )

    if (-not (Test-Path $ReportPath)) {
        throw "Expected report not found: $ReportPath"
    }

    $completedAt = Get-Date
    $elapsed = $completedAt - $StartedAt
    $header = (
        @(
            '# Freerouting Branch Comparison Report'
            ''
            '| | |'
            '| --- | --- |'
            ('| **Baseline branch** | ``{0}`` -> version **{1}** |' -f $BaselineBranch, $BaselineLabel)
            ('| **Compare branch** | ``{0}`` -> version **{1}** |' -f $CompareBranch, $CompareLabel)
            ('| **Started** | {0} |' -f $StartedAt.ToString('yyyy-MM-dd HH:mm:ss'))
            ('| **Completed** | {0} |' -f $completedAt.ToString('yyyy-MM-dd HH:mm:ss'))
            ('| **Elapsed** | {0}h {1}m |' -f [math]::Floor($elapsed.TotalHours), $elapsed.Minutes)
            ('| **Results JSON** | ``results/comparison-{0}/benchmarks.json`` |' -f $ComparisonSlug)
            ''
            'Each fixture section below lists both versions in one table so you can compare unrouted nets,'
            'violations, score, and runtime side-by-side.'
            ''
            '---'
            ''
        ) -join "`n"
    )

    $existing = Get-Content $ReportPath -Raw -Encoding UTF8
    if ($existing -notmatch '^# Freerouting Branch Comparison Report') {
        Set-Content -Path $ReportPath -Value ($header + $existing) -Encoding UTF8
    }
}

# --- Main ---
$startedAt = Get-Date
$null = New-Item -ItemType Directory -Force -Path $ResultsDir
$null = New-Item -ItemType Directory -Force -Path $BinariesDir

if (-not $Resume) {
    Write-Log "Starting fresh comparison in $ResultsDir"
    Get-ChildItem $ResultsDir -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne "overnight-run.log" } |
        Remove-Item -Force
} else {
    Write-Log "Resume mode: keeping existing results in $ResultsDir"
}

Write-Log "Repository: $RepoRoot"
Write-Log ('Comparison: {0} vs {1} (PR #{2})' -f $BaselineBranch, $CompareBranch, $ComparePrNumber)
Write-Log "Report will be written to: $ReportPath"

Stop-FreeroutingJavaProcesses

$originalBranch = $null
Push-Location $RepoRoot
try {
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $status = git status --porcelain 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "git status failed: $status"
        }
    } finally {
        $ErrorActionPreference = $prevEap
    }
    if ($status -and -not $AllowDirty) {
        throw "Working tree has uncommitted changes. Commit/stash them or pass -AllowDirty."
    }

    $originalBranch = Get-CurrentGitBranch
    Write-Log "Saved current branch: $originalBranch"

    Invoke-Git @("fetch", "origin")
    Write-Log "Fetch complete. Starting benchmark phases..."

    $baselineLabel = Invoke-BenchmarkPhase -Branch $BaselineBranch
    $compareLabel = Invoke-BenchmarkPhase -Branch $CompareBranch -PrNumber $ComparePrNumber

    Add-ComparisonHeader -BaselineLabel $baselineLabel -CompareLabel $compareLabel -StartedAt $startedAt

    Write-Log "Comparison complete."
    Write-Log "Open report: $ReportPath"
    Write-Output ""
    Write-Output "Done. Side-by-side report:"
    Write-Output "  $ReportPath"
    Write-Output "Overnight log:"
    Write-Output "  $OvernightLog"
}
catch {
    Write-Log "ERROR: $($_.Exception.Message)"
    Write-Error $_
    exit 1
}
finally {
    if ($originalBranch) {
        try {
            Ensure-GitBranch $originalBranch
            Write-Log "Restored branch: $originalBranch"
        } catch {
            Write-Log "WARNING: Could not restore branch '$originalBranch': $($_.Exception.Message)"
        }
    }
    Pop-Location
}
