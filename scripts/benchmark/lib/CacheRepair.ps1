function Invoke-BenchmarkCacheRepair {
    param(
        $RawJson,
        [hashtable]$Cache,
        [string]$JsonPath,
        $BinaryCurrent,
        [string]$FixturesDir
    )

    $currentBoundsByFixture = @{}
    $currentBoundsCompletenessByFixture = @{}
    $currentSettingsByFixture = @{}
    $currentBinaryName = if ($BinaryCurrent) { $BinaryCurrent.Name } else { $null }
    $currentMachineCpuScore = $null

    if ($currentBinaryName) {
        $currentRuns = @($Cache.Values | Where-Object {
            $_.binary -and $_.binary.filename -eq $currentBinaryName
        })
        $currentCpuRun = $currentRuns |
            Where-Object { $_.system -and $null -ne $_.system.cpu_score } |
            Sort-Object run_at -Descending |
            Select-Object -First 1
        if ($currentCpuRun) {
            $currentMachineCpuScore =
                if ([int]$currentCpuRun.system.cpu_score -gt 10000) {
                    [int][math]::Round([double]$currentCpuRun.system.cpu_score / 1000.0)
                } else {
                    [int]$currentCpuRun.system.cpu_score
                }
        }

        foreach ($run in $currentRuns) {
            if ($run.fixture -and $run.fixture.relative_path -and $run.bounds) {
                $fixturePath = [string]$run.fixture.relative_path
                if ($run.PSObject.Properties["settings_snapshot"] -and $run.settings_snapshot) {
                    $currentSettingsByFixture[$fixturePath] = $run.settings_snapshot
                }
                $hasBounds =
                    $null -ne $run.bounds.board_area_mm2 -or
                    $null -ne $run.bounds.complexity_c -or
                    $null -ne $run.bounds.min_trace_length_mm -or
                    $null -ne $run.bounds.min_via_count -or
                    $null -ne $run.bounds.min_bend_count
                if ($hasBounds) {
                    $boundCount = @(
                        "board_area_mm2",
                        "complexity_c",
                        "min_trace_length_mm",
                        "min_via_count",
                        "min_bend_count"
                    ) | Where-Object { $null -ne $run.bounds.$_ } | Measure-Object | Select-Object -ExpandProperty Count
                    if (-not $currentBoundsCompletenessByFixture.ContainsKey($fixturePath) -or
                        $boundCount -gt $currentBoundsCompletenessByFixture[$fixturePath]) {
                        $currentBoundsByFixture[$fixturePath] = $run.bounds
                        $currentBoundsCompletenessByFixture[$fixturePath] = $boundCount
                    }
                }
            }
        }
    }

    $boundsPatched = $false
    $effectiveCpuScorePatched = $false
    $cpuScoreScaledPatched = $false
    $hostVersionPatched = $false
    $settingsPatched = $false
    $fixtureMetadataCache = @{}

    foreach ($key in @($Cache.Keys)) {
        $run = $Cache[$key]
        if ($run.system) {
            if ($run.system.PSObject.Properties["cpu_score"] -and
                $null -ne $run.system.cpu_score -and
                [int]$run.system.cpu_score -gt 10000) {
                $run.system.cpu_score = [int][math]::Round([double]$run.system.cpu_score / 1000.0)
                $Cache[$key] = $run
                $cpuScoreScaledPatched = $true
            }
            if ($run.system.PSObject.Properties["cpu_score_effective"] -and
                $null -ne $run.system.cpu_score_effective -and
                [int]$run.system.cpu_score_effective -gt 10000) {
                $run.system.cpu_score_effective =
                    [int][math]::Round([double]$run.system.cpu_score_effective / 1000.0)
                $Cache[$key] = $run
                $cpuScoreScaledPatched = $true
            }
            $cpuScoreProperty = $run.system.PSObject.Properties["cpu_score"]
            $effectiveCpuScore =
                if ($cpuScoreProperty -and $null -ne $cpuScoreProperty.Value) {
                    [int]$cpuScoreProperty.Value
                } else {
                    $currentMachineCpuScore
                }
            $effectiveProperty = $run.system.PSObject.Properties["cpu_score_effective"]
            if ($null -ne $effectiveCpuScore -and
                ($null -eq $effectiveProperty -or
                    $effectiveProperty.Value -ne $effectiveCpuScore)) {
                if ($effectiveProperty) {
                    $effectiveProperty.Value = $effectiveCpuScore
                } else {
                    Add-Member -InputObject $run.system -NotePropertyName "cpu_score_effective" `
                        -NotePropertyValue $effectiveCpuScore
                }
                $Cache[$key] = $run
                $effectiveCpuScorePatched = $true
            }
        }
        $fixturePath = if ($run.fixture) { $run.fixture.relative_path } else { $null }
        if ($fixturePath -and $run.fixture -and
            [string]::IsNullOrWhiteSpace([string]$run.fixture.host_version)) {
            $dsnPath = Join-Path $FixturesDir $fixturePath
            if (-not (Test-Path $dsnPath)) {
                $dsnPath = Join-Path $FixturesDir "PCBench\$fixturePath"
            }
            if (Test-Path $dsnPath) {
                if (-not $fixtureMetadataCache.ContainsKey($dsnPath)) {
                    $fixtureMetadataCache[$dsnPath] = Get-DsnMetadata $dsnPath
                }
                $detectedHostVersion = $fixtureMetadataCache[$dsnPath].host_version
                if (-not [string]::IsNullOrWhiteSpace([string]$detectedHostVersion)) {
                    $hostVersionProperty = $run.fixture.PSObject.Properties["host_version"]
                    if ($hostVersionProperty) {
                        $hostVersionProperty.Value = $detectedHostVersion
                    } else {
                        Add-Member -InputObject $run.fixture -NotePropertyName "host_version" `
                            -NotePropertyValue $detectedHostVersion
                    }
                    $Cache[$key] = $run
                    $hostVersionPatched = $true
                }
            }
        }
        if ($fixturePath -and $currentBoundsByFixture.ContainsKey($fixturePath)) {
            $sourceBounds = $currentBoundsByFixture[$fixturePath]
            $boundsProperty = $run.PSObject.Properties["bounds"]
            if ($null -eq $boundsProperty -or $null -eq $boundsProperty.Value) {
                $newBounds = [PSCustomObject]@{}
                if ($boundsProperty) {
                    $boundsProperty.Value = $newBounds
                } else {
                    Add-Member -InputObject $run -NotePropertyName "bounds" -NotePropertyValue $newBounds
                }
            }
            $runBoundsChanged = $false
            foreach ($field in @(
                    "board_area_mm2",
                    "complexity_c",
                    "min_trace_length_mm",
                    "min_via_count",
                    "min_bend_count"
                )) {
                if ($null -eq $run.bounds.$field -and $null -ne $sourceBounds.$field) {
                    $boundProperty = $run.bounds.PSObject.Properties[$field]
                    if ($boundProperty) {
                        $boundProperty.Value = $sourceBounds.$field
                    } else {
                        Add-Member -InputObject $run.bounds -NotePropertyName $field `
                            -NotePropertyValue $sourceBounds.$field
                    }
                    $runBoundsChanged = $true
                }
            }
            if ($runBoundsChanged) {
                $Cache[$key] = $run
                $boundsPatched = $true
            }
        }
        if ($fixturePath -and
            $currentSettingsByFixture.ContainsKey($fixturePath) -and
            (-not $run.PSObject.Properties["settings_snapshot"] -or
                $null -eq $run.settings_snapshot) -and
            [string]$run.binary.version_label -match "(?i)(1[._-]?9|v190)") {
            $settingsProperty = $run.PSObject.Properties["settings_snapshot"]
            if ($settingsProperty) {
                $settingsProperty.Value = $currentSettingsByFixture[$fixturePath]
            } else {
                Add-Member -InputObject $run -NotePropertyName "settings_snapshot" `
                    -NotePropertyValue $currentSettingsByFixture[$fixturePath]
            }
            $Cache[$key] = $run
            $settingsPatched = $true
        }
    }
    if ($boundsPatched -or $effectiveCpuScorePatched -or $cpuScoreScaledPatched -or
        $hostVersionPatched -or $settingsPatched) {
        Save-BenchmarksJson $RawJson $Cache $JsonPath
    }
}
