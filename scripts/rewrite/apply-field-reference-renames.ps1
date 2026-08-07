# Applies cross-file field reference renames using build/field-renames.tsv
# produced by RenameInstanceFieldsToCamelCase.
param(
    [string[]]$TargetPackages = @(),
    [string[]]$SourceRoots = @("src/main/java", "src/test/java"),
    [string]$RenameMapPath = "build/field-renames.tsv"
)

$ErrorActionPreference = "Stop"
$rootDir = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $rootDir

[Console]::WriteLine("Script Root: $PSScriptRoot")
[Console]::WriteLine("Working Dir: $rootDir")
[Console]::WriteLine("RenameMapPath input: $RenameMapPath")

$fullMapPath = Join-Path $rootDir $RenameMapPath
if (-not (Test-Path $fullMapPath)) {
    [Console]::WriteLine("No rename map at $fullMapPath - skipping field reference cleanup.")
    exit 0
}

[Console]::WriteLine("Reading map from: $fullMapPath")
$lines = [IO.File]::ReadAllLines($fullMapPath)
[Console]::WriteLine("Read $($lines.Length) lines.")

$renames = @{}
$tabChar = [char]9
foreach ($line in $lines) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $parts = $line.Split($tabChar)
    if ($parts.Length -ge 3) {
        $classFqn = $parts[0].Trim()
        $oldName  = $parts[1].Trim()
        $newName  = $parts[2].Trim()
        if ($TargetPackages.Length -gt 0) {
            $matched = $false
            foreach ($pkg in $TargetPackages) {
                if ($classFqn.StartsWith($pkg)) { $matched = $true; break }
            }
            if (-not $matched) { continue }
        }
        if ($oldName -and $newName -and ($oldName -cne $newName)) {
            $renames[$oldName] = $newName
        }
    } elseif ($parts.Length -ge 2 -and $TargetPackages.Length -eq 0) {
        $oldName = $parts[0].Trim()
        $newName = $parts[1].Trim()
        if ($oldName -and $newName -and ($oldName -cne $newName)) {
            $renames[$oldName] = $newName
        }
    }
}

[Console]::WriteLine("Loaded $($renames.Count) renames into hashtable (TargetPackages: '$($TargetPackages -join ', ')').")

if ($renames.Count -eq 0) {
    [Console]::WriteLine("Rename map is empty for target packages - nothing to do.")
    exit 0
}

$memberRegex = [regex]::new("\.(?<name>[A-Za-z0-9_]+)(?!\s*\()(?![A-Za-z0-9_])")
$thisRegex = [regex]::new("(?<![.\w])this\.(?<name>[A-Za-z0-9_]+)\b(?!\s*\()(?![A-Za-z0-9_])")

function Apply-FieldRenames {
    param(
        [string]$Content,
        [hashtable]$Renames,
        [regex]$MemberRegex,
        [regex]$ThisRegex
    )
    $updated = $MemberRegex.Replace($Content, [System.Text.RegularExpressions.MatchEvaluator]{
        param($m)
        $old = $m.Groups['name'].Value
        if ($Renames.ContainsKey($old)) {
            return "." + $Renames[$old]
        }
        return $m.Value
    })
    $updated = $ThisRegex.Replace($updated, [System.Text.RegularExpressions.MatchEvaluator]{
        param($m)
        $old = $m.Groups['name'].Value
        if ($Renames.ContainsKey($old)) {
            return "this." + $Renames[$old]
        }
        return $m.Value
    })
    return $updated
}

[Console]::WriteLine("Applying $($renames.Count) field reference renames with fast single-pass regex...")

$files = foreach ($root in $SourceRoots) {
    Get-ChildItem -Path (Join-Path $rootDir $root) -Filter *.java -Recurse
}

$changedFiles = 0
foreach ($file in $files) {
    $content = [IO.File]::ReadAllText($file.FullName)
    $updated = Apply-FieldRenames -Content $content -Renames $renames -MemberRegex $memberRegex -ThisRegex $thisRegex
    if ($updated -ne $content) {
        [IO.File]::WriteAllText($file.FullName, $updated)
        $changedFiles++
    }
}

[Console]::WriteLine("Updated $changedFiles files with field reference renames.")
