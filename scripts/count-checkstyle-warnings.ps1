[CmdletBinding()]
param (
    [string]$TargetFolder = "app/freerouting/api",
    [string]$LogFile = "logs/checkstyle.log"
)

if (-not (Test-Path $LogFile)) {
    Write-Error "Log file '$LogFile' not found."
    exit 1
}

# Normalize target folder path separators for regex matching (handling both / and \)
$normalizedTarget = [regex]::Escape($TargetFolder) -replace '/|\\', '[/\\]'
$pattern = "\[ant:checkstyle\] \[WARN\].*$normalizedTarget"

$warnings = Get-Content $LogFile | Select-String -Pattern $pattern

if ($warnings) {
    Write-Host "Found $($warnings.Count) Checkstyle warning(s) for '$TargetFolder':"
    Write-Host ""
    foreach ($w in $warnings) {
        Write-Host $w.Line
    }
    Write-Host ""
    Write-Host "Total Checkstyle Warnings: $($warnings.Count)"
    exit $warnings.Count
} else {
    Write-Host "No Checkstyle warnings found for '$TargetFolder'."
    exit 0
}
