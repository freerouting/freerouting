param(
    [string]$TargetPackages = ""
)

$rootDir = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $rootDir

$mapPath = Join-Path $rootDir "build/field-renames.tsv"
$fallbackPath = Join-Path $rootDir "src/rewrite/.field-renames.tsv"

$mapDir = Split-Path $mapPath -Parent
if (-not (Test-Path $mapDir)) { New-Item -ItemType Directory -Path $mapDir -Force | Out-Null }
$fallbackDir = Split-Path $fallbackPath -Parent
if (-not (Test-Path $fallbackDir)) { New-Item -ItemType Directory -Path $fallbackDir -Force | Out-Null }

$pkgFilters = @()
if (-not [string]::IsNullOrWhiteSpace($TargetPackages)) {
    $pkgFilters = $TargetPackages.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 }
}

[Console]::WriteLine("Generating field rename map for target packages: $(if ($pkgFilters.Count -gt 0) { $pkgFilters -join ', ' } else { 'ALL' })")

$javaFiles = Get-ChildItem -Path "src/main/java" -Filter "*.java" -Recurse

function Convert-ToLowerCamelCase([string]$snake) {
    $parts = $snake.Split('_')
    $result = $parts[0].ToLower()
    for ($i = 1; $i -lt $parts.Length; $i++) {
        if ($parts[$i].Length -gt 0) {
            $result += $parts[$i].Substring(0, 1).ToUpper() + $parts[$i].Substring(1)
        }
    }
    return $result
}

$entries = [System.Collections.Generic.List[string]]::new()
$seen = [System.Collections.Generic.HashSet[string]]::new()

foreach ($file in $javaFiles) {
    $content = [IO.File]::ReadAllText($file.FullName)

    $pkg = if ($content -match 'package\s+([\w.]+);') { $Matches[1] } else { "" }
    $className = if ($content -match '(?:public\s+|protected\s+|private\s+)?(?:final\s+)?class\s+(\w+)') { $Matches[1] } else { "" }
    if (-not $pkg -or -not $className) { continue }

    if ($pkgFilters.Count -gt 0) {
        $matched = $false
        foreach ($pf in $pkgFilters) {
            if ($pkg -eq $pf -or $pkg.StartsWith("$pf.")) {
                $matched = $true
                break
            }
        }
        if (-not $matched) { continue }
    }

    $classFqn = "$pkg.$className"

    $lines = $content -split "`r?\n"
    $depth = 0
    foreach ($line in $lines) {
        $openBraces = 0
        $closeBraces = 0
        foreach ($ch in $line.ToCharArray()) {
            if ($ch -eq '{') { $openBraces++ }
            elseif ($ch -eq '}') { $closeBraces++ }
        }

        $depth += $openBraces
        $checkLine = if ($line.Contains('=')) { $line.Split('=')[0] } else { $line }
        if ($depth -ge 1 -and $checkLine -notmatch '\(' -and $line -notmatch 'static\s+final' -and $line -notmatch 'final\s+static') {
            if ($line -match '^\s*(?:public|protected|private)?\s*(?:final|transient|volatile|\s)*\s*[\w<>,.\[\]]+\s+([a-z][a-z0-9]*_[a-z0-9_]*)\s*(?:=|[;,])') {
                $fromName = $Matches[1]
                if (-not $fromName.StartsWith("p_") -and $fromName.Contains("_")) {
                    $toName = Convert-ToLowerCamelCase $fromName
                    if ($fromName -ne $toName) {
                        $key = "$classFqn`t$fromName`t$toName"
                        if ($seen.Add($key)) {
                            $entries.Add($key)
                        }
                    }
                }
            }
        }
        $depth -= $closeBraces
    }
}

[IO.File]::WriteAllLines($mapPath, $entries)
[IO.File]::WriteAllLines($fallbackPath, $entries)
[Console]::WriteLine("Wrote $($entries.Count) field renames to $mapPath")
