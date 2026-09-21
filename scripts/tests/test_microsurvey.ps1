<#
.SYNOPSIS
    Interactive and automated testing utility for Freerouting micro-surveys.

.DESCRIPTION
    Supports both keyless local mock testing (no server, no admin key needed)
    and full API server lifecycle testing (publish, verify, retire) with an admin key.

.PARAMETER Mode
    The testing operation to execute:
    - LocalMock   : (Default) Launches Freerouting with a local survey mock and cache-bypass enabled.
    - ClearCache  : Clears the local surveys.json cache file on Windows.
    - StartServer : Starts Freerouting with the embedded REST API server and an admin key.
    - ApiPublish  : Sends a POST /v1/surveys/active request to publish a survey.
    - ApiGet      : Sends a GET /v1/surveys/active request to inspect the current active survey.
    - ApiRetire   : Sends a DELETE /v1/surveys/active request to retire the active survey.

.PARAMETER AdminKey
    Admin key for authenticating with the API server. Defaults to "test-admin-secret".

.PARAMETER ApiUrl
    Base URL of the Freerouting API server. Defaults to "http://localhost:37864/v1".

.PARAMETER SurveyFile
    Path to a JSON file containing the survey definition to test.
    Defaults to "fixtures/surveys/sample-survey.json".

.EXAMPLE
    # 1. Test the UI directly without any server or admin key:
    .\scripts\tests\test_microsurvey.ps1 -Mode LocalMock

.EXAMPLE
    # 2. Clear previous survey answer cache:
    .\scripts\tests\test_microsurvey.ps1 -Mode ClearCache

.EXAMPLE
    # 3. Publish a survey to the local API server:
    .\scripts\tests\test_microsurvey.ps1 -Mode ApiPublish -AdminKey "test-admin-secret"
#>

[CmdletBinding()]
param (
    [ValidateSet("LocalMock", "ClearCache", "StartServer", "ApiPublish", "ApiGet", "ApiRetire")]
    [string]$Mode = "LocalMock",

    [string]$AdminKey = "test-admin-secret",

    [string]$ApiUrl = "http://localhost:37864/v1",

    [string]$SurveyFile = "fixtures/surveys/sample-survey.json"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

switch ($Mode) {
    "ClearCache" {
        $cachePath = Join-Path $env:APPDATA "freerouting\data\surveys.json"
        if (Test-Path $cachePath) {
            Remove-Item $cachePath -Force
            Write-Host "Cleared survey cache at: $cachePath" -ForegroundColor Green
        } else {
            Write-Host "No existing survey cache found at: $cachePath" -ForegroundColor Yellow
        }
    }

    "LocalMock" {
        Write-Host "Configuring local micro-survey mock environment..." -ForegroundColor Cyan

        # Resolve survey definition from file or fallback
        $resolvedFile = Resolve-Path $SurveyFile -ErrorAction SilentlyContinue
        if ($resolvedFile) {
            $env:FREEROUTING__SURVEYS__ACTIVE_SURVEY = $resolvedFile.Path
            Write-Host "Using survey definition file: $resolvedFile" -ForegroundColor Cyan
        } else {
            $env:FREEROUTING__SURVEYS__ACTIVE_SURVEY = '{"schema_version":1,"id":"test-ui-1","topic":"Opinion on Feature X","question":"How do you like the new status bar button?","options":["Looks great!","Works well","Needs improvement"]}'
            Write-Host "Using default inline mock survey definition" -ForegroundColor Cyan
        }

        # Bypass cache so the survey displays on every launch during UI iteration
        $env:FREEROUTING__SURVEYS__IGNORE_CACHE = "true"
        Write-Host "FREEROUTING__SURVEYS__IGNORE_CACHE set to true (no manual cache clearing needed)" -ForegroundColor Green

        Write-Host "Launching Freerouting..." -ForegroundColor Green
        .\gradlew.bat run
    }

    "StartServer" {
        Write-Host "Starting Freerouting with API server enabled and survey admin key..." -ForegroundColor Cyan
        $env:FREEROUTING__SURVEYS__ADMIN_KEY = $AdminKey
        Write-Host "Admin key configured: $AdminKey" -ForegroundColor Green
        .\gradlew.bat run --args="--api.enabled=true"
    }

    "ApiPublish" {
        $endpoint = "$ApiUrl/surveys/active"
        Write-Host "Publishing survey to $endpoint using admin key..." -ForegroundColor Cyan

        $surveyJson = Get-Content -Path $SurveyFile -Raw
        $headers = @{
            "Content-Type"       = "application/json"
            "X-Survey-Admin-Key" = $AdminKey
        }

        try {
            $response = Invoke-RestMethod -Uri $endpoint -Method POST -Headers $headers -Body $surveyJson
            Write-Host "Survey published successfully!" -ForegroundColor Green
            $response | ConvertTo-Json -Depth 5
        } catch {
            Write-Host "Failed to publish survey: $_" -ForegroundColor Red
            if ($_.Exception.Response) {
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                Write-Host "Server response: $($reader.ReadToEnd())" -ForegroundColor Red
            }
        }
    }

    "ApiGet" {
        $endpoint = "$ApiUrl/surveys/active"
        Write-Host "Querying active survey from $endpoint..." -ForegroundColor Cyan

        try {
            $response = Invoke-WebRequest -Uri $endpoint -Method GET -SkipHttpErrorCheck
            if ($response.StatusCode -eq 204) {
                Write-Host "HTTP 204 No Content (no active or unexpired survey configured)" -ForegroundColor Yellow
            } elseif ($response.StatusCode -eq 200) {
                Write-Host "HTTP 200 OK - Active survey found:" -ForegroundColor Green
                $response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 5
            } else {
                Write-Host "HTTP $($response.StatusCode): $($response.Content)" -ForegroundColor Red
            }
        } catch {
            Write-Host "Request failed: $_" -ForegroundColor Red
        }
    }

    "ApiRetire" {
        $endpoint = "$ApiUrl/surveys/active"
        Write-Host "Retiring active survey at $endpoint using admin key..." -ForegroundColor Cyan

        $headers = @{
            "X-Survey-Admin-Key" = $AdminKey
        }

        try {
            $response = Invoke-RestMethod -Uri $endpoint -Method DELETE -Headers $headers
            Write-Host "Survey retired successfully!" -ForegroundColor Green
            $response | ConvertTo-Json -Depth 5
        } catch {
            Write-Host "Failed to retire survey: $_" -ForegroundColor Red
        }
    }
}
