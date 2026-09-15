# Shared KiCad CLI and PCBench path helpers for the autopilot / PCBench pipeline.

function Get-KicadCliPath {
    param(
        [string]$PreferredBin = "C:\Program Files\KiCad\10.0\bin"
    )

    $candidates = @()
    if ($env:FREEROUTING_KICAD_CLI) {
        $candidates += $env:FREEROUTING_KICAD_CLI
    }
    $candidates += (Join-Path $PreferredBin "kicad-cli.exe")
    $candidates += (Join-Path $PreferredBin "kicad-cli")

    $onPath = Get-Command kicad-cli -ErrorAction SilentlyContinue
    if ($onPath) {
        $candidates += $onPath.Source
    }

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return (Get-Item $candidate).FullName
        }
    }
    return $null
}

function Get-KicadPythonPath {
    param(
        [string]$PreferredBin = "C:\Program Files\KiCad\10.0\bin"
    )

    $candidates = @()
    if ($env:FREEROUTING_KICAD_PYTHON) {
        $candidates += $env:FREEROUTING_KICAD_PYTHON
    }
    $cli = Get-KicadCliPath -PreferredBin $PreferredBin
    if ($cli) {
        $binDir = Split-Path $cli -Parent
        $candidates += (Join-Path $binDir "python.exe")
        $candidates += (Join-Path $binDir "python")
    }
    $candidates += (Join-Path $PreferredBin "python.exe")

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return (Get-Item $candidate).FullName
        }
    }
    return $null
}

function Get-PCBenchRoot {
    param(
        [string]$DefaultRoot = "C:\Work\PCBench"
    )

    if ($env:FREEROUTING_PCBENCH) {
        return $env:FREEROUTING_PCBENCH
    }
    return $DefaultRoot
}

function Get-PCBenchBoardsDir {
    param([string]$PCBenchRoot = (Get-PCBenchRoot))
    return (Join-Path $PCBenchRoot "PCBs")
}

function Join-RepoPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [Parameter(Mandatory = $true)]
        [string[]]$Parts
    )
    $path = $Root
    foreach ($part in $Parts) {
        $path = Join-Path $path $part
    }
    return $path
}
