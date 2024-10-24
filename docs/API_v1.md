# Freerouting API Documentation

The Freerouting API provides auto-routing functionality through standard HTTP RESTful endpoints.

You can test the GET endpoints directly in your browser, but we highly recommend using tools like [Postman](https://www.postman.com/) or [Swagger UI](https://swagger.io/tools/swagger-ui/) for more extensive
testing.

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

1. (optional) Register at [www.freerouting.app](https://www.freerouting.app) and get an API key.
2. (optional) [Include](#authentication) the API key in your requests under the `Authorization` header.
3. Check if you can reach the API by calling the [service status endpoint](#service-status).
4. Create a [session](#sessions) to start a new routing job.
5. [Enqueue](#routing-jobs) a new routing job with the desired settings.
6. (optional) [Change](#routing-jobs) the default settings.
7. [Submit](#inputs-and-outputs) the input for the job.
8. [Start](#routing-jobs) the job.
9. (optional) [Get progress reports](#progress-reports) of the job.
10. Retrieve the [output](#inputs-and-outputs) of the job when it is completed.

## Authentication

Some endpoints require authentication via a **API Key**. To get a API key, register
at [www.freerouting.app](https://www.freerouting.app) and include it in your requests under the `Authorization`
header:

```http
Authorization: Bearer <API_KEY>
```

---

## Endpoints

### Service Status

- **Get Service Status**

  ```http
  GET /system/status
  ```

  **Description:** Returns the current status of the system.

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

  **Description:** Creates a new session.

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

---

### Inputs and Outputs

- **Submit Input for a Job**

  ```http
  POST /jobs/{jobId}/input
  ```

  **Parameters:**
  - `jobId` *(required)*: The unique identifier of the job.

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

  **Description:** Retrieves the output data for a completed routing job.

  **Response body:** Contains some details and the [Base64-encoded](https://en.wikipedia.org/wiki/Base64) Specctra SES data.

  ```json
  {
    "job_id": "a4155510-4db2-412d-ad58-70b7c58c031d",
    "data": "KHNlc3Npb24gI...QogICAgKQogICkKKQ==",
    "size": 13150,
    "crc32": 264089660,
    "format": "SES",
    "layer_count": 4,
    "component_count": 12,
    "netclass_count": 3,
    "net_count": 20,
    "track_count": 25,
    "trace_count": 40,
    "via_count": 28,
    "filename": "BBD-Mars-64-revE.ses",
    "path": ""
  }
  ```

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
        "layer_count": 4,
        "component_count": 12,
        "netclass_count": 3,
        "net_count": 20,
        "track_count": 5,
        "trace_count": 10,
        "via_count": 18,
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
