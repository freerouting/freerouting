# @freerouting/freerouting-mcp-server

Model Context Protocol (MCP) server for the public [Freerouting API](https://www.freerouting.app).

This is a lightweight, zero-dependency bridge that lets LLM-based coding assistants (like Cursor, Cline, or Claude Desktop) communicate natively with the Freerouting autorouter over stdio.

---

## Prerequisites

- [Node.js](https://nodejs.org/) installed (version 16 or higher).
- A Freerouting API key. You can request access on the [Freerouting website](https://www.freerouting.app/).

---

## Quick Start (NPX)

You do not need to install this package manually. You can run it instantly using `npx`.

### 1. Cursor Setup
1. Open Cursor and go to **Settings > Features > MCP**.
2. Click **+ New MCP Server**.
3. Configure the following fields:
   - **Name**: `freerouting-mcp`
   - **Type**: `command`
   - **Command**:
     ```bash
     npx -y @freerouting/freerouting-mcp-server
     ```
4. Click **+ Add Env Var** and add:
   - **Name**: `FREEROUTING_API_KEY`
   - **Value**: `your_public_api_key_here`

---

### 2. Cline / Roo Cline Setup
Add this entry to your `cline_mcp_settings.json` configuration file:

```json
{
  "mcpServers": {
    "freerouting-mcp": {
      "command": "npx",
      "args": ["-y", "@freerouting/freerouting-mcp-server"],
      "env": {
        "FREEROUTING_API_KEY": "your_public_api_key_here"
      }
    }
  }
}
```

---

### 3. Claude Desktop Setup
Add this entry to your `claude_desktop_config.json` configuration file:

```json
{
  "mcpServers": {
    "freerouting-mcp": {
      "command": "npx",
      "args": ["-y", "@freerouting/freerouting-mcp-server"],
      "env": {
        "FREEROUTING_API_KEY": "your_public_api_key_here"
      }
    }
  }
}
```

---

## Environment Variables

You can customize the behavior of the bridge using the following environment variables:

| Variable | Description | Default |
| --- | --- | --- |
| `FREEROUTING_API_KEY` | Your Freerouting API authorization token (Bearer) | (Required) |
| `FREEROUTING_API_URL` | Endpoint to target | `https://api.freerouting.app/v1/mcp` |
| `FREEROUTING_PROFILE_ID` | Custom user profile UUID | (Generated dummy UUID) |
| `FREEROUTING_PROFILE_EMAIL` | Custom identifier email | (Optional; header omitted if not set) |
| `FREEROUTING_ENVIRONMENT_HOST` | Client identification string sent in the `Freerouting-Environment-Host` header | (Optional; the server auto-detects the value when the header is absent) |

---

## Programmatic Usage & Examples

An example script illustrating how to orchestrate a complete routing job programmatically using JSON-RPC calls over HTTP can be found in [examples/route-example.js](examples/route-example.js).

### Key Payload Naming Invariants

If you are developing custom MCP client logic or instructing coding assistants, pay close attention to the following casing/field mappings required by the underlying GSON serialization:
- **`enqueue_job`**: The session ID must be passed as `session_id` (snake_case) in the request body, not `sessionId` (camelCase).
- **`upload_job_input_file`**: The base64-encoded board file data must be put into the `data` field in the request body, not `dataBase64`.
- **`download_job_output_file`**: The returned payload contains the base64 output inside the `data` field, not `dataBase64`.

---

## License

GPL-3.0-only

