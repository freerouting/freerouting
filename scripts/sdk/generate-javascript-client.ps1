param(
    [string]$OpenApiSource = "https://api.freerouting.app/openapi/openapi.json",
    [string]$OutputDir = "build/sdk/javascript-client",
    [string]$NpmName = "freerouting-js-client",
    [string]$NpmVersion = "0.1.0",
    [string]$TemplateDir = "",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..\.." )).Path
}

function Resolve-AbsolutePath([string]$BasePath, [string]$PathValue) {
    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return (Resolve-Path $PathValue).Path
    }

    $combined = Join-Path $BasePath $PathValue
    return [System.IO.Path]::GetFullPath($combined)
}

function Convert-ToContainerPath([string]$RepoRoot, [string]$AbsolutePath) {
    $repoPrefix = [System.IO.Path]::GetFullPath($RepoRoot).TrimEnd('\') + '\'
    $absoluteNormalized = [System.IO.Path]::GetFullPath($AbsolutePath)
    if (-not $absoluteNormalized.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path '$AbsolutePath' must be inside repository root '$RepoRoot'."
    }

    $relative = $absoluteNormalized.Substring($repoPrefix.Length) -replace "\\", "/"
    return "/local/$relative"
}

$repoRoot = Resolve-RepoRoot
$workDir = Join-Path $repoRoot "build\sdk"
$specDir = Join-Path $workDir "_spec"
$specFile = Join-Path $specDir "openapi.json"

New-Item -ItemType Directory -Path $specDir -Force | Out-Null

if ($OpenApiSource -match '^https?://') {
    Write-Host "Downloading OpenAPI spec from $OpenApiSource"
    Invoke-WebRequest -Uri $OpenApiSource -OutFile $specFile
}
elseif (Test-Path $OpenApiSource) {
    $specSource = Resolve-AbsolutePath $repoRoot $OpenApiSource
    Write-Host "Copying OpenAPI spec from $specSource"
    Copy-Item -Path $specSource -Destination $specFile -Force
}
else {
    throw "OpenApiSource must be a URL or existing file path. Received '$OpenApiSource'."
}

$outputAbsolute = if ([System.IO.Path]::IsPathRooted($OutputDir)) {
    [System.IO.Path]::GetFullPath($OutputDir)
}
else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $OutputDir))
}

New-Item -ItemType Directory -Path $outputAbsolute -Force | Out-Null

$specContainerPath = Convert-ToContainerPath -RepoRoot $repoRoot -AbsolutePath $specFile
$outputContainerPath = Convert-ToContainerPath -RepoRoot $repoRoot -AbsolutePath $outputAbsolute

$dockerArgs = @(
    "run", "--rm",
    "-v", "${repoRoot}:/local",
    "openapitools/openapi-generator-cli:v7.8.0",
    "generate",
    "-g", "javascript",
    "-i", $specContainerPath,
    "-o", $outputContainerPath,
    "--skip-validate-spec",
    "--additional-properties", "projectName=freerouting-js-client,usePromises=true,emitJSDoc=true,npmName=$NpmName,npmVersion=$NpmVersion"
)

if (-not [string]::IsNullOrWhiteSpace($TemplateDir)) {
    $templateAbsolute = Resolve-AbsolutePath $repoRoot $TemplateDir
    $templateContainerPath = Convert-ToContainerPath -RepoRoot $repoRoot -AbsolutePath $templateAbsolute
    $dockerArgs += @("-t", $templateContainerPath)
}

Write-Host "Generating JavaScript SDK into: $outputAbsolute"
Write-Host "Command: docker $($dockerArgs -join ' ')"

if ($DryRun) {
    Write-Host "DryRun enabled. Skipping generator execution."
    exit 0
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is required. Install Docker Desktop or run with -DryRun to preview the command."
}

& docker @dockerArgs
if ($LASTEXITCODE -ne 0) {
    throw "JavaScript SDK generation failed with exit code $LASTEXITCODE."
}

Write-Host "JavaScript SDK generation completed."