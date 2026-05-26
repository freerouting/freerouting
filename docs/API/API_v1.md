# Freerouting API Documentation

The Freerouting API provides auto-routing functionality through standard HTTP RESTful endpoints.

You can test the GET endpoints directly in your browser, but we highly recommend using tools like [Postman](https://www.postman.com/) or the built-in **Swagger UI** (see [Developer Tools](#developer-tools) below) for more extensive testing.

## Base URL

The base URL for the API is:

```
{{root_url}}/{{version}}
```

Where `{{root_url}}` is your server's address and `{{version}}` refers to the API version (`v1`, `v2`, etc.).

### Example Base URL

```
https://api.freerouting.app/v1
```

---

## Typical User Workflow

1. Register at [www.freerouting.app](https://www.freerouting.app) and get an API key.
2. [Include](#authentication) the API key in your requests under the `Authorization` header.
3. Check if you can reach the API by calling the [service status endpoint](#service-status).
4. Create a [session](#sessions) to start a new routing job.
5. [Enqueue](#routing-jobs) a new routing job with the desired settings.
6. (optional) [Change](#routing-jobs) the default settings.
7. [Submit](#inputs-and-outputs) the input for the job.
8. [Start](#routing-jobs) the job.
9. (optional) [Get progress reports](#progress-reports) of the job.
10. Retrieve the [output](#inputs-and-outputs) of the job when it is completed.

## Authentication

The Freerouting API protects sensitive endpoints through a configurable API key validation system.

**Default behaviour:** authentication is **enabled** by default. Users of the public API (**https://api.freerouting.app**) must register at [www.freerouting.app](https://www.freerouting.app) to obtain a free API key.

**Local / plugin use:** when running Freerouting locally as a plugin for a PCB editor (e.g. KiCad or EasyEDA), operators can disable authentication so that the plugin integrates without an API key:

```
java -jar freerouting-executable.jar --gui.enabled=false --api_server.enabled=true --api_server.authentication.enabled=false
```

If authentication is enabled, requests to protected endpoints must include a valid API key under the `Authorization` header:

```http
Authorization: Bearer <API_KEY>
```

For complete details on how to configure and enable authentication on your own server, please see the [API Authentication System Documentation](API_authentication.md).

## Required headers

All protected endpoints (i.e. everything except `/v1/system/*`, `/v1/analytics/*`, `/openapi/*`, and `/swagger-ui`) **require** the following headers on every request:

| Header | Required | Format | Description |
|--------|----------|--------|-------------|
| `Freerouting-Profile-ID` | **Yes** | RFC 4122 UUID | Identifies the calling user. Used for session ownership, job access control, and analytics. |
| `Freerouting-Environment-Host` | **Yes** | `<ToolName>/<Version>` | Identifies the calling EDA tool and its version. |

**Examples:**

```http
Freerouting-Profile-ID: 550e8400-e29b-41d4-a716-446655440000
Freerouting-Environment-Host: KiCad/10.0
Freerouting-Environment-Host: EasyEDA/1.0
Freerouting-Environment-Host: Postman/11.14
```

If `Freerouting-Environment-Host` is absent or does not match the `<ToolName>/<Version>` format, the server returns **HTTP 400 Bad Request**:

```json
{
  "error": "The 'Freerouting-Environment-Host' request header is required. It must identify the calling EDA tool and its version using the format '<ToolName>/<Version>'. Examples: 'KiCad/10.0', 'EasyEDA/1.0', 'Postman/11.14'. See https://github.com/freerouting/freerouting/blob/master/docs/API/API_v1.md for details."
}
```

---

## Endpoints

### Service Status

- **Get Service Status**

  ```http
  GET /system/status
  ```

  **Description:** Returns the current status of the system. This endpoint is **publicly accessible** (no API key or profile header required).

  **Response body:**

  ```json
  {
    "status": "OK",
    "cpu_load": 5.234029795633588,
    "ram_used": 101,
    "ram_available": 130,
    "storage_available": 229,
    "session_count": 2
  }
  ```

- **Get Environment Information**

  ```http
  GET /system/environment
  ```

  **Description:** Returns information about the system environment. This endpoint is **publicly accessible** (no API key or profile header required).

---

### Sessions

- **Create a Session**

  ```http
  POST /sessions/create
  ```

  **Description:** Creates a new routing session for the authenticated user.

  **Request headers:**

  | Header | Required | Format | Description |
  |--------|----------|--------|-------------|
  | `Freerouting-Profile-ID` | **Yes** | RFC 4122 UUID | The caller's user ID. |
  | `Freerouting-Environment-Host` | **Yes** | `<ToolName>/<Version>` | Identifies the calling EDA tool and its version (e.g. `KiCad/10.0`, `EasyEDA/1.0`). |

  **Response body:** Contains the new session ID and the resolved host value.

  ```json
  {
    "id": "13f4f1b4-29a1-48a5-8efb-8cec519d8bd3",
    "user_id": "d0071163-7ba3-46b3-b3af-bc2ebfd4d1a0",
    "host": "KiCad/10.0"
  }
  ```

- **List All Sessions**

  ```http
  GET /sessions/list
  ```

  **Description:** Retrieves a list of all active sessions owned by the authenticated user.

- **Retrieve a Specific Session**

  ```http
  GET /sessions/{sessionId}
  ```

  **Parameters:**
    - `sessionId` *(required)*: The unique identifier of the session.

  **Description:** Retrieves detailed information about a specific session.

- **Get log entries of a session**

  ```http
  GET /sessions/{sessionId}/logs
  ```

  **Parameters:**
    - `sessionId` *(required)*: The unique identifier of the session.

  **Description:** Retrieves a JSON array of all log entries recorded for a specific session.

---

### Routing Jobs

- **Enqueue a Job**

  ```http
  POST /jobs/enqueue
  ```

  **Request body:** Contains the session ID and the name of the job.

  ```json
  {  
    "session_id": "2703e30e-e891-422d-ad4e-efefd6d4a3ce",
    "name": "BBD-Mars-64-revE",
    "priority": "NORMAL"
  }
  ```

  **Description:** Submits a new routing job (in `QUEUED` state) to be processed. Both an input file and router settings must be uploaded before the job can be started.

- **List Jobs for a Session**

  ```http
  GET /jobs/list/{sessionId}
  ```

  **Parameters:**
    - `sessionId` *(required)*: The unique identifier of the session. Pass `"all"` (or any value that does not resolve to a known session) to retrieve all jobs belonging to the authenticated user regardless of session.

  **Description:** Retrieves a list of routing jobs associated with a specific session.

- **Update Job Settings**

  ```http
  POST /jobs/{jobId}/settings
  ```

  **Request body:** Contains the desired new settings.

  ```json
  {
    "max_passes": 5,
    "via_costs": 42
  }
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Updates the router settings for a specific routing job. The job must still be in `QUEUED` state.

- **Start a Job**

  ```http
  PUT /jobs/{jobId}/start
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Transitions the job from `QUEUED` to `READY_TO_START`, signalling the scheduler to begin routing.

- **Cancel a Job**

  ```http
  PUT /jobs/{jobId}/cancel
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Cancels a running or queued routing job. The job state is set to `CANCELLED` and any in-progress routing pass is interrupted. Partial output (if any) remains accessible via `GET /jobs/{jobId}/output`.

- **Get log entries of a job**

  ```http
  GET /jobs/{jobId}/logs
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Retrieves a JSON array of all log entries recorded for a specific job.

- **Get log entries in real-time as a stream**

  ```http
  GET /jobs/{jobId}/logs/stream
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Opens a Server-Sent Events (SSE) stream and pushes JSON log entry objects as they are generated by the routing process.

---

### Inputs and Outputs

- **Submit Input for a Job**

  ```http
  POST /jobs/{jobId}/input
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Submits input data for a routing job. The job must still be in `QUEUED` state.

  **Request body:** Contains the filename and the [Base64-encoded](https://en.wikipedia.org/wiki/Base64) Specctra DSN data.

  ```json
  {
    "filename": "BBD-Mars-64-revE.dsn",
    "data": "KGFyZHdhcmVcU...W1hdGY5OC4zMTcgLKQ0K"
  }
  ```

- **Get Output for a Job**

  ```http
  GET /jobs/{jobId}/output
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Retrieves the output data for a routing job in Specctra SES format.

  - If the job is **completed**, returns the final output with HTTP **200 OK**.
  - If the job is **running, paused, or stopping**, returns the partial output generated so far with HTTP **202 Accepted**. The output will be updated as routing progresses; use `GET /jobs/{jobId}/output/stream` for real-time SSE updates.
  - If the job is in progress but **no output data is available yet**, returns HTTP **204 No Content**.
  - If the job **failed, was cancelled, timed out, or is invalid**, returns HTTP **400 Bad Request** with an error message.

  **Response body (200 or 202):** Contains some details and the [Base64-encoded](https://en.wikipedia.org/wiki/Base64) Specctra SES data.

  ```json
  {
    "job_id": "a4155510-4db2-412d-ad58-70b7c58c031d",
    "data": "KHNlc3Npb24gI...QogICAgKQogICkKKQ==",
    "size": 13150,
    "crc32": 264089660,
    "format": "SES",
    "statistics": {
        "host": null,
        "layer_count": 2,
        "component_count": 15,
        "netclass_count": null,
        "total_net_count": null,
        "unrouted_net_count": 75,
        "routed_net_count": 267,
        "routed_net_length": null,
        "clearance_violation_count": null,
        "via_count": 133
    },
    "filename": "BBD-Mars-64-revE.ses",
    "path": ""
  }
  ```

- **Get updated output for a Job in real-time**

  ```http
  GET /jobs/{jobId}/output/stream
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Opens a Server-Sent Events (SSE) stream and pushes updated output objects as the routing progresses. Each event payload is a JSON-serialized `BoardFilePayload` with the latest Base64-encoded SES data.

---

### Progress Reports

- **Get Progress Report for a Job**

  ```http
  GET /jobs/{jobId}
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Retrieves the current progress of a routing job.

  **Response body:** Contains the details of the job.

  ```json
  {
    "id": "a4155510-4db2-412d-ad58-70b7c58c031d",
    "created_at": "2024-10-22T08:28:59.106759042Z",
    "started_at": "2024-10-22T08:29:47.985542527Z",
    "input": {
        "size": 50853,
        "crc32": 4085067588,
        "format": "DSN",
        "statistics": {
            "host": "KiCad's Pcbnew,(5.1.5)-3",
            "layer_count": 2,
            "component_count": 16,
            "netclass_count": 1,
            "total_net_count": 160,
            "unrouted_net_count": null,
            "routed_net_count": 0,
            "routed_net_length": null,
            "clearance_violation_count": null,
            "via_count": 66
        },
        "filename": "BBD-Mars-64-revE.dsn",
        "path": ""
    },
    "session_id": "2703e30e-e891-422d-ad4e-efefd6d4a3ce",
    "name": "BBD-Mars-64-revE",
    "state": "RUNNING",
    "priority": "NORMAL",
    "stage": "ROUTING",
    "router_settings": {
        "default_preferred_direction_trace_cost": 1.0,
        "default_undesired_direction_trace_cost": 1.0,
        "max_passes": 100,
        "fanout_max_passes": 20,
        "max_threads": 1,
        "improvement_threshold": 0.01,
        "trace_pull_tight_accuracy": 500,
        "allowed_via_types": true,
        "via_costs": 42,
        "plane_via_costs": 5,
        "start_ripup_costs": 100,
        "automatic_neckdown": true
    }
  }
  ```

- **Get DRC (design rules check) Report for a Job**

  ```http
  GET /jobs/{jobId}/drc
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Generates and returns a KiCad-compatible DRC report for a routing job. The board is loaded from the stored DSN input on demand if not already in memory. Returns **HTTP 500** if the board cannot be loaded.

  **Response body:** Contains the DRC report in KiCad DRC v1 JSON format.

  ```json
  {
    "$schema": "https://schemas.kicad.org/drc.v1.json",
    "coordinate_units": "mm",
    "date": "2025-12-11T16:15:54.777901+01:00",
    "kicad_version": "N/A",
    "freerouting_version": "Freerouting 2.2.2",
    "source": "Issue575-drc_Natural_Tone_Preamp_7_unconnected_items.dsn",
    "unconnected_items": [],
    "violations": [],
    "schematic_parity": []
  }
  ```

---

### Developer Tools

The following endpoints are **publicly accessible** (no API key or profile header required):

| Endpoint | Description |
|----------|-------------|
| `GET /openapi/openapi.json` | OpenAPI 3.0 specification in JSON format |
| `GET /openapi/openapi.yaml` | OpenAPI 3.0 specification in YAML format |
| `GET /swagger-ui/index.html` | Interactive Swagger UI documentation |

The Swagger UI link is also printed to the server log on startup:

```
API web server started successfully at http://localhost:37864. ... Swagger UI is available at http://localhost:37864/swagger-ui.
```

### MCP Server Endpoints

The MCP server is configured independently from the REST API server (`mcp_server` settings block).
MCP and A2A routes are explicitly wired into the OpenAPI generator so they appear in Swagger UI.

| Endpoint | Description |
|----------|-------------|
| `GET /.well-known/agent.json` | Public A2A Agent Card for MCP discovery |
| `POST /v1/mcp` | MCP JSON-RPC entry point (`initialize`, `tools/list`, `tools/call`) |
| `GET /v1/mcp/events` | MCP activity stream over SSE |
| `WS /v1/mcp/ws` | MCP activity stream over WebSocket (`ws://` or `wss://`) |

`mcp_server.target_api_base_url` must point to the REST API base URL (for example
`http://127.0.0.1:37864`) and must not target MCP endpoints (`/v1/mcp*` or `/.well-known/*`).

The A2A card includes protocol/version metadata, auth scheme hints, contact and docs URLs,
and tool-source/category metadata to help MCP/A2A clients configure themselves automatically.

Operational controls:

- API throttling is controlled by `api_server.rate_limit`.
- MCP throttling is controlled by `mcp_server.rate_limit`.
- Responses include `X-Correlation-ID`; MCP forwards this ID to bridged REST calls for cross-layer tracing.

For complete setup instructions (configuration, startup commands, verification, and troubleshooting), see:

- [`docs/API/MCP_setup.md`](MCP_setup.md)

---

## Notes

- Make sure to replace `{{base_url}}` with the actual API base URL and `{{version}}` with the version you're using.
- Endpoints that include parameters like `{sessionId}` or `{jobId}` need to be replaced with actual values.

## Developer Use Case: {{version}} = "dev"

For developers, the Freerouting API offers a special "dev" version designed for testing and development purposes. In
this case, the endpoints do not require authentication, and they return structurally correct, mocked data to facilitate
integration and testing.

### Key Features

- **No Authentication Required:** All endpoints in the "dev" version can be accessed without needing a Personal Access
  Token.
- **Mocked Data:** The API responses provide realistic, but mocked data that is structurally correct to simulate
  real-world API interactions.

### Example

To get the service status in the "dev" environment:

```http
GET https://api.freerouting.app/dev/system/status
```

Response (mocked data)

```json
{
  "status": "OK",
  "cpu_load": 62.73316938805016,
  "ram_used": 67,
  "ram_available": 32,
  "storage_available": 1706,
  "session_count": 1
}
```

The "dev" version is useful for testing the API integration without relying on live data or requiring authentication.

### Applying for an API key

To get an API key for the Freerouting API, please visit [www.freerouting.app](https://www.freerouting.app) and register.

You will receive a unique API key that you can use to authenticate your requests to the API.

I also send you a detailed getting started guide to help you integrate the API into your workflows.

If you have any questions or need further assistance, please don't hesitate to contact me at [info@freerouting.app](mailto:info@freerouting.app).