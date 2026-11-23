# Freerouting Altium Test Runner
$ErrorActionPreference = "Stop"

Write-Host "Running Freerouting Altium Core & Extension test suite (.NET 8)..." -ForegroundColor Cyan
$testProject = Join-Path $PSScriptRoot "Freerouting.Altium.Core.Tests\Freerouting.Altium.Core.Tests.csproj"
& dotnet run --project "$testProject"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Tests failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}
