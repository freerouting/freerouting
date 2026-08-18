function Load-BenchmarksJson {
    param([string]$JsonPath)
    if (Test-Path $JsonPath) {
        try {
            $raw = Get-Content $JsonPath -Raw
            $data = ConvertFrom-Json $raw
            if ($data) {
                # Convert runs array to a dictionary indexed by cache_key
                $cache = @{}
                if ($data.runs) {
                    foreach ($run in $data.runs) {
                        # Powershell converts json objects to PSCustomObject. Ensure we extract cache_key.
                        if ($run.cache_key) {
                            $cache[$run.cache_key] = $run
                        }
                    }
                } else {
                    $data.runs = @()
                }
                return @{
                    RawData = $data
                    Cache = $cache
                }
            }
        } catch {
            Write-Warning "Failed to parse benchmarks.json: $_"
        }
    }
    return @{
        RawData = [PSCustomObject]@{
            schema_version = 1
            generated_at = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")
            total_runs = 0
            runs = @()
        }
        Cache = @{}
    }
}

function Save-BenchmarksJson {
    param(
        $RawData,
        [Hashtable]$Cache,
        [string]$JsonPath
    )
    $runsList = New-Object System.Collections.ArrayList
    foreach ($key in $Cache.Keys) {
        [void]$runsList.Add($Cache[$key])
    }

    $RawData.runs = $runsList
    $RawData.total_runs = $runsList.Count
    $RawData.generated_at = (Get-Date -UFormat "%Y-%m-%dT%H:%M:%SZ")

    $tempPath = "$JsonPath.tmp"
    try {
        $rawJson = ConvertTo-Json $RawData -Depth 100 -Compress
        $pyFormatScript = @"
import json, sys
data = json.loads(sys.argv[1])
with open(sys.argv[2], 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2)
"@
        python -c $pyFormatScript $rawJson $tempPath
        if (Test-Path $tempPath) {
            Move-Item -Path $tempPath -Destination $JsonPath -Force
        } else {
            $json = ConvertTo-Json $RawData -Depth 100
            $utf8NoBom = New-Object System.Text.UTF8Encoding $false
            [System.IO.File]::WriteAllText($tempPath, $json, $utf8NoBom)
            Move-Item -Path $tempPath -Destination $JsonPath -Force
        }
    } catch {
        Write-Error "Failed to write benchmarks.json atomically: $_"
    }
}
