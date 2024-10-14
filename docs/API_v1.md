# Freerouting API Documentation

The Freerouting API provides auto-routing functionality through standard HTTP RESTful endpoints.

You can test the GET endpoints directly in your browser, but we highly recommend using tools
like [Postman](https://www.postman.com/) or [Swagger UI](https://swagger.io/tools/swagger-ui/) for more extensive
testing.

## Base URL

The base URL for the API is:

```
{{base_url}}/{{version}}
```

Where `{{base_url}}` is your server's address and `{{version}}` refers to the API version (`v1`, `v2`, etc.).

### Example Base URL:

```
https://api.freerouting.app/v1
```

---

## Authentication

Some endpoints require authentication via a **Personal Access Token**. To get a token, register
at [auth.freerouting.app](https://auth.freerouting.app) and include it in your requests under the `Authorization`
header:

```
Authorization: Bearer <token>
```

---

## Endpoints

### Service Status

- **Get Service Status**
  ```
  GET /system/status
  ```

  **Description:** Returns the current status of the system.

- **Get Environment Information**
  ```
  GET /system/environment
  ```

  **Description:** Returns information about the system environment.

---

### Sessions

- **Create a Session**
  ```
  POST /sessions/create
  ```

  **Description:** Creates a new session.

- **List All Sessions**
  ```
  GET /sessions/list
  ```

  **Description:** Retrieves a list of all active sessions.

- **Retrieve a Specific Session**
  ```
  GET /sessions/{sessionId}
  ```

  **Parameters:**
    - `sessionId` *(required)*: The unique identifier of the session.

  **Description:** Retrieves detailed information about a specific session.

- **Get log entries of a session**
  ```
  GET /sessions/{sessionId}/logs/{timestamp}
  ```

  **Parameters:**
    - `sessionId` *(required)*: The unique identifier of the session.
    - 'timestamp' *(optional)*: The timestamp from which the log entries will be listed.'

  **Description:** Retrieves a JSON array of log entries for a specific session.

---

### Routing Jobs

- **Enqueue a Job**
  ```
  POST /jobs/enqueue
  ```

  **Description:** Submits a new routing job to be processed.

- **List Jobs for a Session**
  ```
  GET /jobs/list/{sessionId}
  ```

  **Parameters:**
    - `sessionId` *(required)*: The unique identifier of the session.

  **Description:** Retrieves a list of routing jobs associated with a specific session.

- **Update Job Settings**
  ```
  POST /jobs/{jobId}/settings
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Updates the settings for a specific routing job.

- **Start a Job**
  ```
  PUT /jobs/{jobId}/start
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Starts processing the specified routing job.

- **Get log entries of a job**
  ```
  GET /jobs/{jobId}/logs/{timestamp}
  ```

  **Parameters:**
    - `JobId` *(required)*: The unique identifier of the job.
    - 'timestamp' *(optional)*: The timestamp from which the log entries will be listed.'

  **Description:** Retrieves a JSON array of log entries for a specific job.

---

### Inputs and Outputs

- **Submit Input for a Job**
  ```
  POST /jobs/{jobId}/input
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Submits input data for a routing job.

- **Get Output for a Job**
  ```
  GET /jobs/{jobId}/output
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Retrieves the output data for a completed routing job.

---

### Progress Reports

- **Get Progress Report for a Job**
  ```
  GET /jobs/{jobId}
  ```

  **Parameters:**
    - `jobId` *(required)*: The unique identifier of the job.

  **Description:** Retrieves the current progress of a routing job.

---

## Notes

- Make sure to replace `{{base_url}}` with the actual API base URL and `{{version}}` with the version you're using.
- Endpoints that include parameters like `{sessionId}` or `{jobId}` need to be replaced with actual values.

## Developer Use Case: {{version}} = "dev"

For developers, the Freerouting API offers a special "dev" version designed for testing and development purposes. In
this case, the endpoints do not require authentication, and they return structurally correct, mocked data to facilitate
integration and testing.

### Base URL:

```
{{base_url}}/dev
```

### Key Features:

- **No Authentication Required:** All endpoints in the "dev" version can be accessed without needing a Personal Access
  Token.
- **Mocked Data:** The API responses provide realistic, but mocked data that is structurally correct to simulate
  real-world API interactions.

### Example:

To get the service status in the "dev" environment:

```
GET /system/status
```

Response (mocked data):

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