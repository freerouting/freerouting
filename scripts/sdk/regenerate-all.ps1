param(
    [string]$OpenApiSource = "https://api.freerouting.app/openapi/openapi.json",
    [string]$OutputRootDir = "build/sdk",
    [string]$SharedVersion = "0.1.0",
    [string]$PythonVersion = "",
    [string]$JavascriptVersion = "",
    [string]$CSharpVersion = "",
    [string]$CppVersion = "",
    [string]$PythonTemplateDir = "",
    [string]$JavascriptTemplateDir = "",
    [string]$CSharpTemplateDir = "",
    [string]$CppTemplateDir = "",
    [string]$CSharpTargetFramework = "net8.0",
    [string]$JavascriptNpmName = "freerouting-js-client",
    [string]$CSharpPackageName = "Freerouting.CSharp.Client",
    [string]$CppPackageName = "freerouting_cpp_client",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Get-VersionOrDefault([string]$Value, [string]$DefaultValue) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $DefaultValue
    }

    return $Value
}

$pythonVersion = Get-VersionOrDefault -Value $PythonVersion -DefaultValue $SharedVersion
$javascriptVersion = Get-VersionOrDefault -Value $JavascriptVersion -DefaultValue $SharedVersion
$csharpVersion = Get-VersionOrDefault -Value $CSharpVersion -DefaultValue $SharedVersion
$cppVersion = Get-VersionOrDefault -Value $CppVersion -DefaultValue $SharedVersion

$pythonScript = Join-Path $PSScriptRoot "generate-python-client.ps1"
$javascriptScript = Join-Path $PSScriptRoot "generate-javascript-client.ps1"
$csharpScript = Join-Path $PSScriptRoot "generate-csharp-client.ps1"
$cppScript = Join-Path $PSScriptRoot "generate-cpp-client.ps1"

$pythonArgs = @{
    OpenApiSource = $OpenApiSource
    OutputDir = (Join-Path $OutputRootDir "python-client")
    PackageVersion = $pythonVersion
}
if (-not [string]::IsNullOrWhiteSpace($PythonTemplateDir)) { $pythonArgs.TemplateDir = $PythonTemplateDir }
if ($DryRun) { $pythonArgs.DryRun = $true }

$javascriptArgs = @{
    OpenApiSource = $OpenApiSource
    OutputDir = (Join-Path $OutputRootDir "javascript-client")
    NpmName = $JavascriptNpmName
    NpmVersion = $javascriptVersion
}
if (-not [string]::IsNullOrWhiteSpace($JavascriptTemplateDir)) { $javascriptArgs.TemplateDir = $JavascriptTemplateDir }
if ($DryRun) { $javascriptArgs.DryRun = $true }

$csharpArgs = @{
    OpenApiSource = $OpenApiSource
    OutputDir = (Join-Path $OutputRootDir "csharp-client")
    PackageName = $CSharpPackageName
    PackageVersion = $csharpVersion
    TargetFramework = $CSharpTargetFramework
}
if (-not [string]::IsNullOrWhiteSpace($CSharpTemplateDir)) { $csharpArgs.TemplateDir = $CSharpTemplateDir }
if ($DryRun) { $csharpArgs.DryRun = $true }

$cppArgs = @{
    OpenApiSource = $OpenApiSource
    OutputDir = (Join-Path $OutputRootDir "cpp-client")
    PackageName = $CppPackageName
    PackageVersion = $cppVersion
}
if (-not [string]::IsNullOrWhiteSpace($CppTemplateDir)) { $cppArgs.TemplateDir = $CppTemplateDir }
if ($DryRun) { $cppArgs.DryRun = $true }

Write-Host "Regenerating SDKs from OpenAPI source: $OpenApiSource"
Write-Host "Output root: $OutputRootDir"
Write-Host "Versions: python=$pythonVersion javascript=$javascriptVersion csharp=$csharpVersion cpp=$cppVersion"

Write-Host "`n[1/4] Python"
& $pythonScript @pythonArgs

Write-Host "`n[2/4] JavaScript"
& $javascriptScript @javascriptArgs

Write-Host "`n[3/4] C#"
& $csharpScript @csharpArgs

Write-Host "`n[4/4] C++"
& $cppScript @cppArgs

Write-Host "`nAll SDK generation steps completed."