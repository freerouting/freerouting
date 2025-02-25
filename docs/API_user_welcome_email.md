**Subject:** Welcome to the Freerouting API – Getting Started Guide

**Body:**

Dear ...,

Welcome to the Freerouting API! We are thrilled to have you onboard as part of our growing community of PCB design enthusiasts and developers.

The Freerouting API provides advanced auto-routing capabilities for PCB layouts through a RESTful service, making it easy to integrate into your workflows.

Here’s a quick guide to help you get started:

### 1. Set HTTP headers for API Access

Make sure you have set the required HTTP headers: your **API key, Profile ID and Host** for each API call.

Include your API key in all requests using the `Authorization` header:

```
Authorization: Bearer <API_KEY>
Freerouting-Profile-ID: <PROFILE_ID>
Freerouting-Environment-Host: <HOST_NAME>/<HOST_VERSION>
```

**API Key:**
Your API key is a unique identifier that grants access to Freerouting API endpoints. Include it in the `Authorization`
header as follows:

```
Authorization: Bearer <YOUR_API_KEY>
```

***Your API key is:*** `aabbccdd-0011-2233-4455-66778899aabb`

**Profile ID:**
The Profile ID identifies the user making the API call. This should be a valid GUID (e.g., `4f93609a-cf64-41cf-a046-6b8486ad85ec`).

- Use a unique Profile ID for each user if you have multiple users accessing the API.
- This helps organize and monitor API usage per user.

**Host Name and Version:**
The host name and version represent the software making the API call. Use the format:

```
Freerouting-Environment-Host: <SOFTWARE_NAME>/<VERSION>
```

Examples:

- `KiCad/8.0.6`
- `Firechip/3.1.3`
- `tscirciut/0.0.229`

### 2. Check the API Status

Test your connection by checking the system status endpoint:

```http
GET https://api.freerouting.app/v1/system/status
```

### 3. Start Your First Routing Job

The basic steps include:

- **Create a session:**
  ```http
  POST /sessions/create
  ```
- **Enqueue a routing job:**
  ```http
  POST /jobs/enqueue
  ```
- **Submit your input (DSN file):**
  ```http
  POST /jobs/{jobId}/input
  ```
- **Start the job:**
  ```http
  PUT /jobs/{jobId}/start
  ```
- **Retrieve the results (SES file):**
  ```http
  GET /jobs/{jobId}/output
  ```

For more detailed documentation, visit the [Freerouting API Docs](https://www.freerouting.app/docs/API_v1.md).

### 4. Need Help?

- Join our community or submit feedback via GitHub: [Freerouting GitHub](https://github.com/freerouting/freerouting).
- Reach out to us at [info@freerouting.app](mailto\:info@freerouting.app).

I can’t wait to see how you integrate Freerouting into your projects! If you have questions or feedback, don’t hesitate to get in touch.

Happy routing,
**Andras Fuchs**
[Freerouting Website](https://www.freerouting.app)