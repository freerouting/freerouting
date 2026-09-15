function Get-BenchmarkFixturesDir {
    return Join-Path (Split-Path $PSScriptRoot -Parent) "fixtures"
}

function Test-IsActiveBenchmarkFixtureFile {
    param([System.IO.FileInfo]$FixtureFile)

    if ($FixtureFile.Name -match '\.dsn_disabled$' -or $FixtureFile.Name -match 'reference-routed\.dsn$') {
        return $false
    }
    return $true
}

function Test-IsActiveBenchmarkFixture {
    param(
        $Run,
        [string]$FixturesDir = (Get-BenchmarkFixturesDir)
    )

    if (-not $Run -or -not $Run.fixture) {
        return $false
    }

    $filename = [string]$Run.fixture.filename
    $relativePath = [string]$Run.fixture.relative_path
    if ($filename -match '\.dsn_disabled$' -or $relativePath -match '\.dsn_disabled$' -or
        $filename -match 'reference-routed\.dsn$' -or $relativePath -match 'reference-routed\.dsn$') {
        return $false
    }

    if ($relativePath) {
        $fixturePath = Join-Path $FixturesDir ($relativePath -replace '/', [System.IO.Path]::DirectorySeparatorChar)
        if (-not (Test-Path $fixturePath)) {
            return $false
        }
    }

    return $true
}

function Get-ActiveBenchmarkRuns {
    param(
        [Hashtable]$Cache,
        [string]$FixturesDir = (Get-BenchmarkFixturesDir)
    )

    $runs = [System.Collections.ArrayList]::new()
    foreach ($key in $Cache.Keys) {
        $run = $Cache[$key]
        if (Test-IsActiveBenchmarkFixture $run $FixturesDir) {
            [void]$runs.Add($run)
        }
    }
    return @($runs)
}
