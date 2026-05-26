param(
    [string]$McpBaseUrl = "http://127.0.0.1:37964",
    [string]$ProfileId = "00000000-0000-0000-0000-000000000001",
    [string]$EnvironmentHost = "McpSmokeTest/1.0",
    [string]$AuthorizationToken = "",
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-RequestHeaders {
    $headers = @{
        "Content-Type" = "application/json"
        "Freerouting-Profile-ID" = $ProfileId
        "Freerouting-Environment-Host" = $EnvironmentHost
    }

    if (-not [string]::IsNullOrWhiteSpace($AuthorizationToken)) {
        $headers["Authorization"] = "Bearer $AuthorizationToken"
    }

    return $headers
}

function Invoke-JsonPost {
    param(
        [string]$Url,
        [string]$Body,
        [hashtable]$Headers
    )

    if ($DryRun) {
        Write-Host "[DRY-RUN] POST $Url"
        Write-Host "[DRY-RUN] Body: $Body"
        return $null
    }

    return Invoke-RestMethod -Method Post -Uri $Url -Headers $Headers -Body $Body
}

function Invoke-JsonGet {
    param(
        [string]$Url,
        [hashtable]$Headers
    )

    if ($DryRun) {
        Write-Host "[DRY-RUN] GET  $Url"
        return $null
    }

    return Invoke-RestMethod -Method Get -Uri $Url -Headers $Headers
}

function Assert-Condition {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw "Assertion failed: $Message"
    }
}

$headers = Get-RequestHeaders
$rpcUrl = "$McpBaseUrl/v1/mcp"
$agentCardUrl = "$McpBaseUrl/.well-known/agent.json"

Write-Host "MCP smoke test target: $McpBaseUrl"

# 1) Agent card (public)
$agentCard = Invoke-JsonGet -Url $agentCardUrl -Headers $headers
if (-not $DryRun) {
    Assert-Condition ($agentCard.name -eq "Freerouting MCP") "Agent card name should be 'Freerouting MCP'."
    Assert-Condition ($agentCard.endpoints.Count -ge 1) "Agent card should expose at least one endpoint."
}

# 2) initialize
$initializeBody = '{"jsonrpc":"2.0","id":1,"method":"initialize"}'
$initializeResult = Invoke-JsonPost -Url $rpcUrl -Body $initializeBody -Headers $headers
if (-not $DryRun) {
    Assert-Condition ($initializeResult.result.serverName -eq "Freerouting MCP") "Initialize should return Freerouting MCP server name."
}

# 3) tools/list
$toolsListBody = '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
$toolsListResult = Invoke-JsonPost -Url $rpcUrl -Body $toolsListBody -Headers $headers
if (-not $DryRun) {
    Assert-Condition ($toolsListResult.result.tools.Count -gt 0) "tools/list should return at least one tool."
}

# 4) tools/call -> get_v1_system_status
$toolsCallBody = '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_v1_system_status","arguments":{}}}'
$toolsCallResult = Invoke-JsonPost -Url $rpcUrl -Body $toolsCallBody -Headers $headers
if (-not $DryRun) {
    Assert-Condition ($toolsCallResult.result.isError -eq $false) "tools/call should not return an MCP error for get_v1_system_status."
}

Write-Host "MCP smoke test completed successfully."