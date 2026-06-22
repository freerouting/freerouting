# Freerouting Model Context Protocol (MCP) Setup Guide

This guide describes how to connect Freerouting to LLM-based coding assistants (like Cursor, Cline, or Claude Desktop) using the Model Context Protocol (MCP).

Freerouting offers two setup options for MCP integration, depending on your environment, privacy needs, and use case:

---

## 1. Option A: Public API via NPX (Online / Quick Start)

This is the simplest way to get started. It requires **no local Freerouting installation or Java JRE**. A tiny, lightweight local Node.js bridge ([@freerouting/freerouting-mcp-server](https://www.npmjs.com/package/@freerouting/freerouting-mcp-server)) is executed to forward your local MCP requests securely to the Freerouting Public API.

### Use Cases & Target Audience
- **Ideal for**: Quick setup, laptop or thin-client environments, standard developers using Cursor or Cline who already have Node.js installed.
- **Prerequisites**: Node.js installed on your machine.
- **API Access**: Requires an API key from [freerouting.app](https://www.freerouting.app).

### Setup Configuration

#### Cursor (Settings > Features > MCP)
Click **+ New MCP Server** and enter:
- **Name**: `freerouting-mcp`
- **Type**: `command`
- **Command**:
  ```bash
  npx -y @freerouting/freerouting-mcp-server
  ```
- **Environment Variables**:
  - `FREEROUTING_API_KEY`: `your_public_api_key_here`

#### Cline / Roo Cline (`cline_mcp_settings.json`)
Add the following entry to your MCP configuration:
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

#### Claude Desktop Configuration (`claude_desktop_config.json`)
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

## 2. Option B: Local Java Executable (Offline / Self-Hosted)

This option runs the entire routing engine **locally on your machine**. All PCB geometric calculations and trace generation are done locally; no data ever leaves your computer.

### Use Cases & Target Audience
- **Ideal for**: Offline usage, high privacy/proprietary designs, unlimited routing without rate limit constraints, and custom routing configurations.
- **Prerequisites**:
  - [Java JRE 25](https://adoptium.net/temurin/releases/) installed.
  - The [Freerouting Executable JAR](https://github.com/freerouting/freerouting/releases) downloaded.

### Setup Configuration

#### Cursor (Settings > Features > MCP)
Click **+ New MCP Server** and enter:
- **Name**: `freerouting-mcp`
- **Type**: `command`
- **Command**:
  ```bash
  java -jar C:/path/to/freerouting-current-executable.jar --mcp_server.enabled=true --mcp_server.stdio=true --gui.enabled=false
  ```
  *(Be sure to replace `C:/path/to/freerouting-current-executable.jar` with the actual absolute path to your downloaded JAR file, using forward slashes `/` even on Windows.)*

#### Cline / Roo Cline (`cline_mcp_settings.json`)
Add the following entry to your MCP configuration:
```json
{
  "mcpServers": {
    "freerouting-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "C:/path/to/freerouting-2.3.4.jar",
        "--api_server.enabled=true",
        "--api_server.authentication.enabled=false",
        "--mcp_server.enabled=true",
        "--mcp_server.stdio=true",
        "--gui.enabled=false"
      ]
    }
  }
}
```

#### Claude Desktop Configuration (`claude_desktop_config.json`)
```json
{
  "mcpServers": {
    "freerouting-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "C:/path/to/freerouting-2.3.4.jar",
        "--api_server.enabled=true",
        "--api_server.authentication.enabled=false",
        "--mcp_server.enabled=true",
        "--mcp_server.stdio=true",
        "--gui.enabled=false"
      ]
    }
  }
}
```

---

## 3. Verify MCP Capabilities
Once connected, the MCP client should automatically discover the Freerouting tools. You can test the connection by asking your LLM client:
> *"List the available Freerouting tools and retrieve the system status."*

The LLM should call the `get_system_status` tool and display system memory, CPU load, and routing capability details.

---

## 4. Customizing Environment Variables (Advanced)

Both the NPX bridge and the Local Java MCP server support environment variables to configure authentication and user profile details.

### Underscore Configurations
To prevent confusion, both **single underscore** (convention in Python CLI) and **double underscore** (standard configuration structure hierarchy within the Java application) variables are supported:
- Profile ID: `FREEROUTING_PROFILE_ID` or `FREEROUTING__PROFILE__ID`
- Profile Email: `FREEROUTING_PROFILE_EMAIL` or `FREEROUTING__PROFILE__EMAIL`
- Environment Host: `FREEROUTING_ENVIRONMENT_HOST` or `FREEROUTING__ENVIRONMENT__HOST`
- API Key: `FREEROUTING_API_KEY` (NPX mode only)
- API URL: `FREEROUTING_API_URL` (NPX mode only, default is `https://api.freerouting.app/v1/mcp`)

### Precedence and Default Behaviors

#### Profile ID (`Freerouting-Profile-ID`)
1. Resolves first to environment variables if provided.
2. If not defined:
   - **NPX Mode**: Checks `~/.freerouting/profile_id` on the user's home directory. If it doesn't exist, a new GUID is generated once and saved there to persist across sessions.
   - **Local Self-Hosted Mode**: Resolves to the local `userId` stored in `freerouting.json`.
3. Defaults to `00000000-0000-0000-0000-000000000000` if resolving fails.

#### Profile Email (`Freerouting-Profile-Email`)
1. Resolves first to environment variables if provided.
2. If not defined:
   - **Local Self-Hosted Mode**: Resolves to the local `userEmail` stored in `freerouting.json`.
   - **NPX Mode**: The header is omitted.

#### Environment Host (`Freerouting-Environment-Host`)
1. Resolves first to environment variables if provided.
2. If not defined, it is **dynamically detected** from the MCP client's `initialize` handshake details (e.g. `Cursor/0.45.0` or `Roo-Cline/3.2.0`), working seamlessly in both modes.
3. Falls back to `MCP-Client/1.0`.