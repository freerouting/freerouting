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
| `FREEROUTING_PROFILE_EMAIL` | Custom identifier email | `mcp-npx-client@local.freerouting.app` |
| `FREEROUTING_ENVIRONMENT_HOST` | Client identification | `MCP-NPX-Client/1.0` |

---

## License

GPL-3.0-only
