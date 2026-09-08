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

function ConvertTo-BenchmarkRelativePath {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $Path
    }

    $scriptRoot = [System.IO.Path]::GetFullPath($PSScriptRoot).TrimEnd('\')
    $normalizedPath = $Path.Replace('/', '\')
    $benchmarkMarker = 'scripts\benchmark\'
    $markerIndex = $normalizedPath.IndexOf($benchmarkMarker, [System.StringComparison]::OrdinalIgnoreCase)
    if ($markerIndex -ge 0) {
        $relativePath = $normalizedPath.Substring($markerIndex + $benchmarkMarker.Length)
        return $relativePath.Replace('\', '/')
    }
    if ($normalizedPath.StartsWith('benchmark\', [System.StringComparison]::OrdinalIgnoreCase)) {
        return $normalizedPath.Substring('benchmark\'.Length).Replace('\', '/')
    }

    if ([System.IO.Path]::IsPathRooted($normalizedPath)) {
        $fullPath = [System.IO.Path]::GetFullPath($normalizedPath)
    } elseif ($normalizedPath -match '^(?:\.[\\/])?(?:logs|outputs|results|fixtures)[\\/]') {
        $relativePath = $normalizedPath -replace '^\.?[\\/]+', ''
        return $relativePath.Replace('\', '/')
    } else {
        $fullPath = [System.IO.Path]::GetFullPath($normalizedPath)
    }

    $rootWithSeparator = "$scriptRoot\"
    if ($fullPath.StartsWith($rootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $fullPath.Substring($rootWithSeparator.Length).Replace('\', '/')
    }
    return $Path.Replace('\', '/')
}

function Resolve-BenchmarkStoredPath {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $Path
    }
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }

    $normalizedPath = $Path.Replace('/', '\') -replace '^\.?[\\/]+', ''
    if ($normalizedPath.StartsWith('scripts\benchmark\', [System.StringComparison]::OrdinalIgnoreCase)) {
        return Join-Path $PSScriptRoot ($normalizedPath.Substring('scripts\benchmark\'.Length))
    }
    if ($normalizedPath.StartsWith('benchmark\', [System.StringComparison]::OrdinalIgnoreCase)) {
        return Join-Path $PSScriptRoot ($normalizedPath.Substring('benchmark\'.Length))
    }
    return Join-Path $PSScriptRoot $normalizedPath
}

function Convert-BenchmarkObjectPaths {
    param($Object)

    if ($null -eq $Object) {
        return 0
    }
    if ($Object -is [string] -or $Object.GetType().IsValueType) {
        return 0
    }
    $changed = 0
    $pathPropertyNames = @('log_file', 'result_json', 'output_file', 'report_file')
    foreach ($property in @($Object.PSObject.Properties)) {
        if ($property.Name -in $pathPropertyNames -and $property.Value -is [string]) {
            $relativePath = ConvertTo-BenchmarkRelativePath $property.Value
            if ($property.Value -ne $relativePath) {
                $property.Value = $relativePath
                $changed++
            }
        } elseif ($property.Value -is [System.Collections.IEnumerable] -and
            $property.Value -isnot [string]) {
            foreach ($item in $property.Value) {
                $changed += Convert-BenchmarkObjectPaths $item
            }
        } elseif ($property.Value -is [PSObject]) {
            $changed += Convert-BenchmarkObjectPaths $property.Value
        }
    }
    return $changed
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
        $json = ConvertTo-Json $RawData -Depth 100 -Compress
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($tempPath, $json, $utf8NoBom)

        $pyFormatScript = @"
import json, math, sys

FLOAT_KEYS = {
    'score',
    'score_before',
    'score_after',
    'final_score',
    'quality_score',
    'optimizer_score',
    'router_score',
    'current_router_score',
    'current_optimizer_score',
    'final_quality_score',
    'final_optimizer_score',
    'normalized_score',
}

def render(value, level=0, key=None):
    indent = '  ' * level
    child_indent = '  ' * (level + 1)
    if value is None:
        return 'null'
    if isinstance(value, bool):
        return 'true' if value else 'false'
    if key in FLOAT_KEYS and isinstance(value, (int, float)):
        return 'null' if not math.isfinite(float(value)) else format(float(value), '.2f')
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return 'null' if not math.isfinite(value) else format(value, '.2f')
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, list):
        if not value:
            return '[]'
        return '[\n' + ',\n'.join(
            child_indent + render(item, level + 1) for item in value
        ) + '\n' + indent + ']'
    if isinstance(value, dict):
        if not value:
            return '{}'
        return '{\n' + ',\n'.join(
            child_indent + json.dumps(str(key), ensure_ascii=False) + ': ' +
            render(item, level + 1, key)
            for key, item in value.items()
        ) + '\n' + indent + '}'
    raise TypeError(type(value).__name__)

p = sys.argv[1]
with open(p, 'r', encoding='utf-8') as f:
    data = json.load(f)
with open(p, 'w', encoding='utf-8', newline='\n') as f:
    f.write(render(data) + '\n')
"@
        python -c $pyFormatScript $tempPath
        Move-Item -Path $tempPath -Destination $JsonPath -Force
    } catch {
        Write-Error "Failed to write benchmarks.json atomically: $_"
    }
}
