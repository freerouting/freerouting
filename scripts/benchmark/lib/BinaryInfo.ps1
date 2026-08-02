function Get-JarManifestAttribute {
    param([string]$JarPath, [string]$AttributeName)
    try {
        [void][System.Reflection.Assembly]::LoadWithPartialName("System.IO.Compression.FileSystem")
        $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
        $entry = $zip.GetEntry("META-INF/MANIFEST.MF")
        if ($entry) {
            $stream = $entry.Open()
            $reader = New-Object System.IO.StreamReader($stream)
            while ($line = $reader.ReadLine()) {
                if ($line -match "^${AttributeName}\s*:\s*(.*)") {
                    $val = $matches[1].Trim()
                    $reader.Dispose()
                    $zip.Dispose()
                    return $val
                }
            }
            $reader.Dispose()
        }
        $zip.Dispose()
    } catch {}
    return $null
}

function Get-GitRepoRoot {
    $dir = $PSScriptRoot
    while ($dir) {
        if (Test-Path (Join-Path $dir ".git")) {
            return $dir
        }
        $parent = Split-Path $dir -Parent
        if ($parent -eq $dir) {
            break
        }
        $dir = $parent
    }
    return $null
}

function Get-GitBranchName {
    $repoRoot = Get-GitRepoRoot
    if (-not $repoRoot) {
        return $null
    }
    try {
        $branch = git -C $repoRoot rev-parse --abbrev-ref HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and $branch -and $branch -ne 'HEAD') {
            return $branch.Trim()
        }
    } catch {}
    return $null
}

function Get-FilesystemSafeVersionLabel {
    param([string]$VersionLabel)
    return ($VersionLabel -replace '[\\/:*?"<>|]', '-')
}

function Get-BinaryVersionLabel {
    param([System.IO.FileInfo]$Binary)

    # 1. Stable versioned filename: freerouting-2.2.4.jar -> "2.2.4"
    if ($Binary.Name -match 'freerouting-(\d+\.\d+\.\d+)\.jar') {
        return $matches[1]
    }

    # 2. Snapshot/current build on a feature branch: use branch name (e.g. pr/764)
    $branch = Get-GitBranchName
    if ($branch -and $branch -notin @('master', 'main')) {
        return $branch
    }

    # 3. Snapshot/current build on main: try JAR manifest Build-Date attribute
    $buildDate = Get-JarManifestAttribute $Binary.FullName 'Build-Date'
    if (-not $buildDate) {
        $buildDate = Get-JarManifestAttribute $Binary.FullName 'Built-Date'
    }
    if (-not $buildDate) {
        $implVersion = Get-JarManifestAttribute $Binary.FullName 'Implementation-Version'
        if ($implVersion -and ($implVersion -match '(\d{4})[-.]?(\d{2})[-.]?(\d{2})')) {
            $buildDate = "$($matches[1])-$($matches[2])-$($matches[3])"
        }
    }

    # 4. Fallback: file last-write date
    if (-not $buildDate) {
        $buildDate = $Binary.LastWriteTime.ToString('yyyy-MM-dd')
    }

    # Format: sYYYY.MM.DD
    if ($buildDate -match '(\d{4})[-./](\d{2})[-./](\d{2})') {
        return "s$($matches[1]).$($matches[2]).$($matches[3])"
    }
    return "s$buildDate"
}

function Test-BinaryCliSupport {
    param([string]$JarPath)
    if ($JarPath -match 'freerouting-1.9\.0\.jar') {
        return $false
    }
    return $true
}
