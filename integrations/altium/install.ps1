<#
.SYNOPSIS
    Automated Installer & Uninstaller for Freerouting Altium Designer Extension.
.DESCRIPTION
    Detects Altium Designer installations, checks for active X2.exe processes,
    and deploys the Freerouting extension DLLs and configuration files into
    the Altium System directory.
.PARAMETER Uninstall
    Removes Freerouting extension files from the detected Altium System directory.
.PARAMETER Silent
    Runs without interactive prompts (ideal for CI/CD or automated scripts).
.PARAMETER AltiumPath
    Custom path to Altium Designer installation folder (e.g. "C:\Program Files\Altium\AD26").
.PARAMETER Force
    Automatically closes running Altium Designer (X2.exe) processes without prompting.
#>
[CmdletBinding()]
param(
    [switch]$Uninstall,
    [switch]$Silent,
    [string]$AltiumPath,
    [switch]$Force
)

# 1. Ensure Administrator Elevation
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin -and -not $Silent) {
    Write-Host "Requesting Administrator privileges for installation into Program Files..." -ForegroundColor Yellow
    $argsList = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    if ($Uninstall) { $argsList += " -Uninstall" }
    if ($Force) { $argsList += " -Force" }
    if ($AltiumPath) { $argsList += " -AltiumPath `"$AltiumPath`"" }

    try {
        $p = Start-Process powershell -Verb RunAs -ArgumentList $argsList -Wait -PassThru
        exit $p.ExitCode
    } catch {
        Write-Warning "Automatic UAC elevation could not be initiated: $($_.Exception.Message)"
        Write-Host "Continuing in current session..." -ForegroundColor Yellow
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   Freerouting PCB Auto-Router - Altium Designer Setup    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

# 2. Locate built extension source files
$scriptDir = Split-Path -Parent $PSCommandPath
$possibleBinDirs = @(
    (Join-Path $scriptDir "Freerouting.Altium.Extension\bin\x64\Release"),
    (Join-Path $scriptDir "bin\x64\Release"),
    $scriptDir,
    (Join-Path (Get-Location) "integrations\altium\Freerouting.Altium.Extension\bin\x64\Release")
)

$binDir = $null
foreach ($cand in $possibleBinDirs) {
    if (Test-Path (Join-Path $cand "Freerouting.Altium.Extension.dll")) {
        $binDir = $cand
        break
    }
}
if (-not $binDir) {
    $binDir = $possibleBinDirs[0]
}

$extensionFiles = @(
    "Freerouting.Altium.Extension.dll",
    "Freerouting.Altium.Core.dll",
    "Freerouting.ins",
    "Freerouting.rcs"
)

if (-not $Uninstall) {
    foreach ($file in $extensionFiles) {
        $sourcePath = Join-Path $binDir $file
        if (-not (Test-Path $sourcePath)) {
            Write-Host "Error: Required payload file not found: $sourcePath" -ForegroundColor Red
            Write-Host "Please build the solution before running the installer." -ForegroundColor Red
            if (-not $Silent) { Read-Host "Press Enter to exit..." }
            exit 1
        }
    }
}

# 3. Detect Altium Designer Installations
function Get-AltiumInstallations {
    param([string]$explicitPath)

    $targets = @()
    if ($explicitPath) {
        if (Test-Path $explicitPath) {
            $sys = Join-Path $explicitPath "System"
            $targets += [PSCustomObject]@{
                DisplayName = "Custom Path"
                Version     = "Custom"
                InstallPath = $explicitPath
                SystemPath  = if (Test-Path $sys) { $sys } else { $explicitPath }
            }
        }
        return $targets
    }

    $regRoots = @(
        "HKLM:\SOFTWARE\Altium\Builds\*",
        "HKLM:\SOFTWARE\WOW6432Node\Altium\Builds\*"
    )
    foreach ($reg in $regRoots) {
        if (Test-Path $reg) {
            Get-ItemProperty $reg | ForEach-Object {
                if ($_.ProgramsInstallPath -and (Test-Path $_.ProgramsInstallPath)) {
                    $sys = Join-Path $_.ProgramsInstallPath "System"
                    $disp = if ($_.DisplayName) { $_.DisplayName } else { $_.PSChildName }
                    $targets += [PSCustomObject]@{
                        DisplayName = $disp
                        Version     = $_.Version
                        InstallPath = $_.ProgramsInstallPath
                        SystemPath  = if (Test-Path $sys) { $sys } else { $_.ProgramsInstallPath }
                    }
                }
            }
        }
    }

    if ($targets.Count -eq 0) {
        $commonDirs = @(
            "C:\Program Files\Altium",
            "C:\Program Files (x86)\Altium"
        )
        foreach ($cDir in $commonDirs) {
            if (Test-Path $cDir) {
                Get-ChildItem $cDir -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                    $sys = Join-Path $_.FullName "System"
                    if (Test-Path $sys) {
                        $targets += [PSCustomObject]@{
                            DisplayName = $_.Name
                            Version     = "Detected"
                            InstallPath = $_.FullName
                            SystemPath  = $sys
                        }
                    }
                }
            }
        }
    }

    return $targets
}

$installations = Get-AltiumInstallations -explicitPath $AltiumPath

if ($installations.Count -eq 0) {
    Write-Host "Error: No Altium Designer installation detected on this system." -ForegroundColor Red
    Write-Host "You can specify the path manually with: .\install.ps1 -AltiumPath 'C:\Path\To\Altium'" -ForegroundColor Yellow
    if (-not $Silent) { Read-Host "Press Enter to exit..." }
    exit 1
}

Write-Host "Detected Altium Designer Installation(s):" -ForegroundColor Green
for ($i = 0; $i -lt $installations.Count; $i++) {
    Write-Host "  [$($i + 1)] $($installations[$i].DisplayName) (v$($installations[$i].Version))" -ForegroundColor White
    Write-Host "      Location: $($installations[$i].SystemPath)" -ForegroundColor Gray
}
Write-Host ""

# 4. Check for running Altium Designer (X2.exe)
$runningX2 = Get-Process -Name "X2" -ErrorAction SilentlyContinue
if ($runningX2) {
    Write-Host "Warning: Altium Designer (X2.exe) is currently running." -ForegroundColor Yellow
    Write-Host "Windows file locking will prevent updating extension DLLs while Altium is active." -ForegroundColor Yellow

    if ($Force) {
        Write-Host "Closing Altium Designer (X2.exe)..." -ForegroundColor Yellow
        Stop-Process -Name "X2" -Force
        Start-Sleep -Seconds 1
    } elseif ($Silent) {
        Write-Host "Error: Cannot update files while Altium Designer is running in silent mode without -Force." -ForegroundColor Red
        exit 2
    } else {
        $response = Read-Host "Would you like the installer to close Altium Designer now? (y/N)"
        if ($response -match "^[yY]") {
            Write-Host "Closing Altium Designer..." -ForegroundColor Yellow
            Stop-Process -Name "X2" -Force
            Start-Sleep -Seconds 2
        } else {
            Write-Host "Please save your work, close Altium Designer, and rerun this installer." -ForegroundColor Red
            Read-Host "Press Enter to exit..."
            exit 2
        }
    }
}

# 5. Perform Install or Uninstall
foreach ($inst in $installations) {
    $targetDir = $inst.SystemPath

    if ($Uninstall) {
        Write-Host "Uninstalling Freerouting from: $targetDir..." -ForegroundColor Yellow
        foreach ($file in $extensionFiles) {
            $dest = Join-Path $targetDir $file
            if (Test-Path $dest) {
                Remove-Item -Path $dest -Force -ErrorAction SilentlyContinue
                Write-Host "  [-] Removed: $file" -ForegroundColor Gray
            }
        }
        $legacyExt = Join-Path $targetDir "Freerouting.ext"
        if (Test-Path $legacyExt) {
            Remove-Item -Path $legacyExt -Force -ErrorAction SilentlyContinue
        }
        Write-Host "Uninstallation complete for $($inst.DisplayName)." -ForegroundColor Green
    } else {
        Write-Host "Installing Freerouting into: $targetDir..." -ForegroundColor Cyan
        if (-not (Test-Path $targetDir)) {
            New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        }

        foreach ($file in $extensionFiles) {
            $src = Join-Path $binDir $file
            $dest = Join-Path $targetDir $file
            try {
                Copy-Item -Path $src -Destination $dest -Force -ErrorAction Stop
                Write-Host "  [+] Copied: $file" -ForegroundColor Gray
            } catch {
                Write-Error "Failed to copy '$file' to '$dest': $($_.Exception.Message)"
                Write-Host "Please re-run this script in an elevated PowerShell console (Run as Administrator):" -ForegroundColor Yellow
                Write-Host "  powershell -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Force" -ForegroundColor Yellow
                exit 1
            }
        }

        # Clean up legacy Freerouting.ext if present in System directory
        $legacyExt = Join-Path $targetDir "Freerouting.ext"
        if (Test-Path $legacyExt) {
            Remove-Item -Path $legacyExt -Force -ErrorAction SilentlyContinue
            Write-Host "  [-] Cleaned up legacy: Freerouting.ext" -ForegroundColor Gray
        }

        Write-Host ""
        Write-Host "SUCCESS: Freerouting extension installed successfully for $($inst.DisplayName)!" -ForegroundColor Green
    }
}

Write-Host ""
if (-not $Uninstall) {
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "  1. Launch Altium Designer." -ForegroundColor White
    Write-Host "  2. Open any PCB document (*.PcbDoc)." -ForegroundColor White
    Write-Host "  3. Access the menu: Route -> Freerouting Auto-Router" -ForegroundColor White
}

Write-Host ""
if (-not $Silent) {
    Read-Host "Press Enter to finish..."
}
