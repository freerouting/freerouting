**Recipient:** {{email}}

**Subject:** Welcome to the Freerouting API - Your API Key & Getting Started Guide

**Body:**

Dear {{name}},

Welcome to the Freerouting API!

Thank you for applying — your account is now active and your API key will be ready to use in 15 minutes. Below you'll find everything you need to make your first routing request in minutes.

---

### Your credentials

| Item | Value |
|---|---|
| **API Key** | `{{apiKey}}` |
| **Profile ID** | `{{sampleUserId}}` |

Keep these safe. Your API key grants access to all protected endpoints. If you ever need it rotated, just reply to this email.

---

### 1. Required HTTP headers

Every request to a protected endpoint must include three headers:

```http
Authorization: Bearer {{apiKey}}
Freerouting-Profile-ID: {{sampleUserId}}
Freerouting-Environment-Host: <ToolName>/<Version>
```

**`Authorization`** authenticates your request. Use the Bearer scheme with your API key.

**`Freerouting-Profile-ID`** identifies you as the caller. Use the GUID above. If you build a multi-user integration, pass each end-user's own GUID here so usage is tracked per user.

**`Freerouting-Environment-Host`** tells the server which tool is making the call. Use the format `<ToolName>/<Version>`. Examples:

- `KiCad/8.0.6`
- `EasyEDA/2.1.0`
- `tscircuit/0.0.229`
- `MyApp/1.0.0`

---

### 2. Verify your connection

Before anything else, check that you can reach the API (this endpoint requires no authentication):

```http
GET https://api.freerouting.app/v1/system/status
```

Expected response:

```json
{
  "status": "OK",
  "cpu_load": 12.4,
  "ram_used": 210,
  "ram_available": 814,
  "storage_available": 18432,
  "session_count": 3
}
```

---

### 3. Explore the API interactively

The full API is documented in **Swagger UI** — you can browse every endpoint and try requests directly in the browser:

```
https://api.freerouting.app/swagger-ui
```

The raw OpenAPI specification is also available at:

```
https://api.freerouting.app/openapi/openapi.json
https://api.freerouting.app/openapi/openapi.yaml
```

---

### 4. Route your first PCB — step by step

The complete workflow from DSN file to SES result:

#### Step 1 — Create a session

```http
POST https://api.freerouting.app/v1/sessions/create
Authorization: Bearer {{apiKey}}
Freerouting-Profile-ID: {{sampleUserId}}
Freerouting-Environment-Host: MyApp/1.0.0
```

Save the `id` from the response — that is your `sessionId`.

#### Step 2 — Enqueue a routing job

```http
POST https://api.freerouting.app/v1/jobs/enqueue
Content-Type: application/json

{
  "session_id": "<sessionId>",
  "name": "my-board-v1",
  "priority": "NORMAL"
}
```

Save the `id` from the response — that is your `jobId`.

#### Step 3 — (Optional) Adjust router settings

```http
POST https://api.freerouting.app/v1/jobs/<jobId>/settings
Content-Type: application/json

{
  "max_passes": 20,
  "via_costs": 42
}
```

#### Step 4 — Upload your Specctra DSN file

Base64-encode your `.dsn` file and upload it:

```http
POST https://api.freerouting.app/v1/jobs/<jobId>/input
Content-Type: application/json

{
  "filename": "my-board-v1.dsn",
  "data": "<BASE64_ENCODED_DSN_CONTENT>"
}
```

**Shell one-liner to Base64-encode your file:**

```bash
# Linux / macOS
base64 -w 0 my-board.dsn

# PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-board.dsn"))
```

#### Step 5 — Start the job

```http
PUT https://api.freerouting.app/v1/jobs/<jobId>/start
```

#### Step 6 — Poll for progress

```http
GET https://api.freerouting.app/v1/jobs/<jobId>
```

The `state` field will transition through: `QUEUED` -> `READY_TO_START` -> `RUNNING` -> `COMPLETED`.

For real-time log streaming, open the SSE endpoint in a compatible client:

```http
GET https://api.freerouting.app/v1/jobs/<jobId>/logs/stream
```

#### Step 7 — Download the result

Once `state` is `COMPLETED`, fetch the Specctra SES output:

```http
GET https://api.freerouting.app/v1/jobs/<jobId>/output
```

The response contains the Base64-encoded `.ses` file in the `data` field. Decode it and import it back into your EDA tool.

> **Tip:** You can also poll `GET /v1/jobs/<jobId>/output` while routing is still in progress — it returns HTTP `202 Accepted` with the partial output generated so far, so you can display incremental results.

#### Step 8 — (Optional) Run a DRC check

After routing, validate the result against the design rules:

```http
GET https://api.freerouting.app/v1/jobs/<jobId>/drc
```

The response is a KiCad-compatible DRC report (JSON) listing any clearance violations or unconnected items.

Sample response (fully routed board with one unconnected net):

```json
{
  "$schema": "https://schemas.kicad.org/drc.v1.json",
  "coordinate_units": "mm",
  "date": "2025-05-02T14:32:10+00:00",
  "kicad_version": "N/A",
  "freerouting_version": "1.10.0",
  "source": "my-board-v1.dsn",
  "violations": [],
  "unconnected_items": [
    {
      "description": "Unconnected items: Pin [GND] and Pin [GND]",
      "severity": "error",
      "type": "unconnected_items",
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
            "x": 1201.5,
            "y": -1063.25
          },
          "uuid": "212"
        }
      ]
    }
  ],
  "schematic_parity": []
}
```

#### Cancelling a job

If you need to stop routing early:

```http
PUT https://api.freerouting.app/v1/jobs/<jobId>/cancel
```

Any partial output generated up to that point remains accessible.

---

### 5. Quick-reference: all endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/v1/system/status` | Server health check (public) |
| `GET` | `/v1/system/environment` | Runtime environment info (public) |
| `POST` | `/v1/sessions/create` | Create a new session |
| `GET` | `/v1/sessions/list` | List your sessions |
| `GET` | `/v1/sessions/{sessionId}` | Get session details |
| `GET` | `/v1/sessions/{sessionId}/logs` | Get session log entries |
| `POST` | `/v1/jobs/enqueue` | Create a new routing job |
| `GET` | `/v1/jobs/list/{sessionId}` | List jobs in a session (`all` for all jobs) |
| `GET` | `/v1/jobs/{jobId}` | Get job details & progress |
| `POST` | `/v1/jobs/{jobId}/settings` | Update router settings |
| `POST` | `/v1/jobs/{jobId}/input` | Upload DSN input file |
| `PUT` | `/v1/jobs/{jobId}/start` | Start the routing job |
| `PUT` | `/v1/jobs/{jobId}/cancel` | Cancel the routing job |
| `GET` | `/v1/jobs/{jobId}/output` | Download SES output file |
| `GET` | `/v1/jobs/{jobId}/output/stream` | Stream output updates (SSE) |
| `GET` | `/v1/jobs/{jobId}/logs` | Get job log entries |
| `GET` | `/v1/jobs/{jobId}/logs/stream` | Stream log entries (SSE) |
| `GET` | `/v1/jobs/{jobId}/drc` | Get DRC report |

---

### 6. Troubleshooting

| Problem | Likely cause | Fix |
|---|---|---|
| `401 Unauthorized` | Missing or invalid `Authorization` header | Make sure you send `Authorization: Bearer <API_KEY>` |
| `400 Bad Request` on `sessions/create` | Missing `Freerouting-Environment-Host` header | Add it in the format `<ToolName>/<Version>` |
| `204 No Content` on `/output` | Routing has started but not produced output yet | Wait and poll again |
| `400 Bad Request` on `/start` | Job is not in `QUEUED` state | Check job `state` via `GET /v1/jobs/{jobId}` |
| Output missing vias / incomplete | Default `max_passes` may be too low | Increase via `POST /v1/jobs/{jobId}/settings` before starting |

If you encounter anything not covered here, check the full reference docs (link below) or open an issue on GitHub.

---

### 7. Useful links

- **Full API reference:** [API_v1.md](https://github.com/freerouting/freerouting/blob/master/docs/API/API_v1.md)
- **Authentication guide:** [API_authentication.md](https://github.com/freerouting/freerouting/blob/master/docs/API/API_authentication.md)
- **Interactive docs (Swagger UI):** [https://api.freerouting.app/swagger-ui/index.html](https://api.freerouting.app/swagger-ui/index.html)
- **Bug reports & feature requests:** [GitHub Issues](https://github.com/freerouting/freerouting/issues)
- **Community & discussions:** [GitHub Discussions](https://github.com/freerouting/freerouting/discussions)

---

I'm genuinely excited to see what you build with Freerouting! If you have any questions, run into an issue, or just want to share what you're working on, please don't hesitate to reach out.

Happy routing,<br>
**Andras Fuchs**<br>
[www.freerouting.app](https://www.freerouting.app) | [info@freerouting.app](mailto:info@freerouting.app)
