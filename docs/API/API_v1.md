# Freerouting API Documentation

The Freerouting API provides auto-routing functionality through standard HTTP RESTful endpoints.

You can test the GET endpoints directly in your browser, but we highly recommend using tools like [Postman](https://www.postman.com/) or [Swagger UI](https://swagger.io/tools/swagger-ui/) for more extensive testing.

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

The Freerouting API allows administrators to optionally enable endpoint protection through different validation providers (e.g., Google Sheets). 

By default, or if disabled, **the API does not require authentication**, which is the typical configuration when running Freerouting locally as a plugin for a PCB editor (like KiCad or Altium). 

However, for the public endpoint (**https://api.freerouting.app**), authentication is **always enabled**. Users of the public API must register at [www.freerouting.app](https://www.freerouting.app) to get an API key.

If authentication is enabled, requests to protected endpoints must include a valid API key under the `Authorization` header:

```http
Authorization: Bearer <API_KEY>
```

For complete details on how to configure and enable authentication on your own server, please see the [API Authentication System Documentation](API_authentication.md).

## Required headers

All protected endpoints (i.e. everything except `/v1/system/*`, `/v1/analytics/*`, `/openapi/*`, and `/swagger-ui`) **require** the following header on every request:

| Header | Required | Format | Description |
|--------|----------|--------|-------------|
| `Freerouting-Environment-Host` | **Yes** | `<ToolName>/<Version>` | Identifies the calling EDA tool and its version. |

**Examples:**

```http
Freerouting-Environment-Host: KiCad/10.0
Freerouting-Environment-Host: EasyEDA/1.0
Freerouting-Environment-Host: Postman/11.14
```

If the header is absent or does not match the `<ToolName>/<Version>` format, the server returns **HTTP 400 Bad Request**:

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

  **Description:** Returns the current status of the system.

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

  **Description:** Returns information about the system environment.

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

  **Description:** Retrieves a list of all active sessions.

- **Retrieve a Specific Session**

  ```http
  GET /sessions/{sessionId}
  ```

  **Parameters:**
    - `sessionId` *(required)*: The unique identifier of the session.

  **Description:** Retrieves detailed information about a specific session.

- **Get log entries of a session**

  ```http
  GET /sessions/{sessionId}/logs/{timestamp}
  ```

  **Parameters:**
    - `sessionId` *(required)*: The unique identifier of the session.
    - 'timestamp' *(optional)*: The timestamp from which the log entries will be listed.'

  **Description:** Retrieves a JSON array of log entries for a specific session.

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

  **Description:** Submits a new routing job to be processed.

- **List Jobs for a Session**

  ```http
  GET /jobs/list/{sessionId}
  ```

  **Parameters:**
    - `sessionId` *(required)*: The unique identifier of the session.

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

  **Description:** Updates the settings for a specific routing job.

- **Start a Job**

  ```http
  PUT /jobs/{jobId}/start
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Starts processing the specified routing job.

- **Get log entries of a job**

  ```http
  GET /jobs/{jobId}/logs/{timestamp}
  ```

  **Parameters:**
    - `JobId` *(required)*: The unique identifier of the job.
    - 'timestamp' *(optional)*: The timestamp from which the log entries will be listed.'

  **Description:** Retrieves a JSON array of log entries for a specific job.

- **Get log entries in real-time as a stream**

  ```http
  GET /jobs/{jobId}/logs/stream
  ```

  **Parameters:**
    - `JobId` *(required)*: The unique identifier of the job.

  **Description:** Opens a stream channel with the server and pushes JSON objects of log entries for a specific job.

---

### Inputs and Outputs

- **Submit Input for a Job**

  ```http
  POST /jobs/{jobId}/input
  ```

  **Parameters:**
    - `JobId` *(required)*: The unique identifier of the job.
    - `filename` *(required)*: The name of the file.
    - `data` *(required)*: The base-64 encoded data of the file.

  **Description:** Submits input data for a routing job.

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

  **Description:** Opens a stream channel with the server and pushes JSON objects of updated output for a specific job.

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

  **Description:** Retrieves the DRC report of a routing job.

  **Response body:** Contains the DRC report of the job.

  ```json
  {
    "$schema": "https://schemas.kicad.org/drc.v1.json",
    "coordinate_units": "mm",
    "date": "2025-12-11T16:15:54.777901+01:00",
    "kicad_version": "N/A",
    "freerouting_version": "Freerouting 2.1.2-SNAPSHOT",
    "source": "Issue575-drc_Natural_Tone_Preamp_7_unconnected_items.dsn",
    "unconnected_items": [
      {
        "description": "Unconnected items: Pin [GND] and Pin [GND]",
        "items": [
          {
            "description": "Pin [GND]",
            "pos": {
              "x": 1183.875,
              "y": -1075.0
            },
            "uuid": "148"
          },
          {
            "description": "Pin [GND]",
            "pos": {
              "x": 1183.875,
              "y": -1075.0
            },
            "uuid": "636"
          }
        ],
        "severity": "warning",
        "type": "unconnected"
      },
      {
        "description": "Unconnected items: Trace [GND] and Trace [GND]",
        "items": [
          {
            "description": "Trace [GND]",
            "pos": {
              "x": 1521.75,
              "y": -1030.3945
            },
            "uuid": "2396"
          },
          {
            "description": "Trace [GND]",
            "pos": {
              "x": 1521.75,
              "y": -1030.464
            },
            "uuid": "2410"
          }
        ],
        "severity": "warning",
        "type": "unconnected"
      }
    ],
    "violations": [],
    "schematic_parity": []
  }
  ```
  
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