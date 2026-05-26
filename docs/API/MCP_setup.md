# Freerouting MCP Setup Guide (LLM Clients)

This guide shows how to run the dedicated Freerouting MCP server and connect it to LLM clients.

## 1) What You Are Starting

Freerouting now has two independent HTTP services:

- REST API server (`api_server`) - regular `/v1/...` endpoints.
- MCP server (`mcp_server`) - JSON-RPC endpoint at `/v1/mcp`, plus SSE and WebSocket streams.

The MCP server calls the REST API server internally using `mcp_server.target_api_base_url`.

## 2) Minimum Prerequisites

- Java 25 runtime.
- Freerouting executable JAR (or development run via Gradle).
- If authentication is enabled: at least one MCP auth provider configured.

## 3) Configure `freerouting.json`

Add or update both server sections:

```json
{
  "api_server": {
    "enabled": true,
    "http_allowed": true,
    "endpoints": ["http://127.0.0.1:37864"],
    "authentication": {
      "enabled": false,
      "providers": ""
    },
    "cors_origins": ""
  },
  "mcp_server": {
    "enabled": true,
    "http_allowed": true,
    "endpoints": ["http://127.0.0.1:37964"],
    "authentication": {
      "enabled": false,
      "providers": ""
    },
    "cors_origins": "",
    "target_api_base_url": "http://127.0.0.1:37864"
  }
}
```

Notes:

- Keep both servers on localhost unless you intentionally expose them.
- In production, enable authentication for both servers and configure providers.

## 4) Start Freerouting

PowerShell example:

```powershell
Set-Location "C:\Work\freerouting"
.\gradlew.bat run --args="--api_server.enabled=true --api_server.endpoints=http://127.0.0.1:37864 --mcp_server.enabled=true --mcp_server.endpoints=http://127.0.0.1:37964 --mcp_server.target_api_base_url=http://127.0.0.1:37864 --gui.enabled=false"
```

Executable JAR example:

```powershell
java -jar build\libs\freerouting-current-executable.jar --api_server.enabled=true --api_server.endpoints=http://127.0.0.1:37864 --mcp_server.enabled=true --mcp_server.endpoints=http://127.0.0.1:37964 --mcp_server.target_api_base_url=http://127.0.0.1:37864 --gui.enabled=false
```

## 5) Verify MCP Endpoints

### 5.1 Initialize (JSON-RPC)

```powershell
$headers = @{
  "Content-Type" = "application/json"
  "Freerouting-Profile-ID" = "00000000-0000-0000-0000-000000000001"
  "Freerouting-Environment-Host" = "ManualTest/1.0"
}
$body = '{"jsonrpc":"2.0","id":1,"method":"initialize"}'
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:37964/v1/mcp" -Headers $headers -Body $body
```

### 5.1b Verify A2A agent card (public)

```powershell
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:37964/.well-known/agent.json"
```

The card includes richer metadata for client discovery and policy handling:

- protocol details (`protocols.mcp.version`, transport list)
- auth metadata (`auth.schemes`, required identity headers)
- docs/contact links (`documentation`, `contact`)
- tool metadata (`tools.source`, `tools.coverage`, `tools.categories`)

### 5.2 List tools

```powershell
$body = '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:37964/v1/mcp" -Headers $headers -Body $body
```

### 5.3 Call a tool

```powershell
$body = '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_v1_system_status","arguments":{}}}'
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:37964/v1/mcp" -Headers $headers -Body $body
```

### 5.4 Run the bundled MCP smoke test script

```powershell
Set-Location "C:\Work\freerouting"
.\scripts\tests\run_test_mcp_smoke.ps1
```

Optional dry-run mode (prints requests only):

```powershell
Set-Location "C:\Work\freerouting"
.\scripts\tests\run_test_mcp_smoke.ps1 -DryRun
```

## 6) Realtime Streams

- SSE endpoint: `GET /v1/mcp/events`
- WebSocket endpoint: `ws://127.0.0.1:37964/v1/mcp/ws`

Required headers for both channels:

- `Freerouting-Profile-ID` (or `Freerouting-Profile-Email`)
- `Freerouting-Environment-Host`
- `Authorization: Bearer <API_KEY>` when MCP authentication is enabled

## 7) LLM Client Wiring Checklist

1. Set MCP server URL to `http://127.0.0.1:37964/v1/mcp`.
2. Send JSON-RPC 2.0 requests.
3. Always include profile and environment headers.
4. Call `initialize` once, then `tools/list`.
5. Use `tools/call` with `name` and `arguments`.
6. Optionally subscribe to SSE/WS for MCP activity events.

## 8) Troubleshooting

- `401 Unauthorized` on MCP calls:
  - MCP auth is enabled and `Authorization` header is missing/invalid.
  - Or MCP providers are enabled but not configured correctly.
- `400 Bad Request` on MCP calls:
  - `Freerouting-Environment-Host` missing or malformed.
- `tools/call` returns HTTP status >= 400 inside MCP response body:
  - Underlying REST API call failed; inspect `status` and `body` fields in MCP result.
- No tools listed:
  - OpenAPI scan failed; verify server startup logs and API package visibility.
- MCP error `-32602` mentioning `target_api_base_url`:
  - `mcp_server.target_api_base_url` points to MCP endpoints (for example `/v1/mcp` or `/.well-known`).
  - Set it to the REST API base URL, for example `http://127.0.0.1:37864`.

## 9) Security Notes

- For local plugin workflows, `127.0.0.1` + auth disabled is acceptable.
- For network exposure, enable auth on both servers and restrict `cors_origins`.
- Prefer reverse proxy/TLS termination if exposing beyond localhost.

## 10) Operational Hardening

- MCP supports configurable fixed-window throttling via `mcp_server.rate_limit`.
- REST API supports configurable fixed-window throttling via `api_server.rate_limit`.
- Set both limits explicitly for production to prevent high-frequency polling loops.
- Every MCP request now carries/returns `X-Correlation-ID`, and tool bridge calls forward this
  header to REST so logs can be cross-linked across MCP and API layers.