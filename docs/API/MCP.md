# Freerouting Model Context Protocol (MCP) Guide

This guide describes how to connect Freerouting to LLM-based coding assistants (like Cursor, Cline, or Claude Desktop) using the Model Context Protocol (MCP), and details the step-by-step API/tool workflows required to complete routing jobs.

---

## 1. Setup Options

Freerouting offers two setup options for MCP integration, depending on your environment, privacy needs, and use case:

### Option A: Public API via NPX (Online / Quick Start)

This is the simplest way to get started. It requires **no local Freerouting installation or Java JRE**. A tiny, lightweight local Node.js bridge ([@freerouting/freerouting-mcp-server](https://www.npmjs.com/package/@freerouting/freerouting-mcp-server)) is executed to forward your local MCP requests securely to the Freerouting Public API.

#### Client Configurations

##### Cursor (Settings > Features > MCP)
Click **+ New MCP Server** and enter:
- **Name**: `freerouting-mcp`
- **Type**: `command`
- **Command**: `npx -y @freerouting/freerouting-mcp-server`
- **Environment Variables**:
  - `FREEROUTING_API_KEY`: `your_public_api_key_here`

##### Cline / Roo Cline (`cline_mcp_settings.json`)
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

##### Claude Desktop Configuration (`claude_desktop_config.json`)
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

### Option B: Local Java Executable (Offline / Self-Hosted)

This option runs the entire routing engine **locally on your machine**. All PCB geometric calculations and trace generation are done locally; no data ever leaves your computer.

#### Client Configurations

##### Cursor (Settings > Features > MCP)
Click **+ New MCP Server** and enter:
- **Name**: `freerouting-mcp`
- **Type**: `command`
- **Command**: `java -jar C:/path/to/freerouting-current-executable.jar --api_server.enabled=true --api_server.authentication.enabled=false --mcp_server.enabled=true --mcp_server.authentication.enabled=false --mcp_server.stdio=true --gui.enabled=false`

> **Note:** `--mcp_server.stdio=true` must be passed as a CLI argument (or set via the `FREEROUTING__MCP_SERVER__STDIO=true` environment variable). Setting `mcp_server.stdio` only in `freerouting.json` has no effect because the stdout redirect must happen before logging is initialised — if you do so, a warning will be printed to the log.

##### Cline / Roo Cline (`cline_mcp_settings.json`)
```json
{
  "mcpServers": {
    "freerouting-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "C:/path/to/freerouting-current-executable.jar",
        "--api_server.enabled=true",
        "--api_server.authentication.enabled=false",
        "--mcp_server.enabled=true",
        "--mcp_server.authentication.enabled=false",
        "--mcp_server.stdio=true",
        "--gui.enabled=false"
      ]
    }
  }
}
```

---

## 2. MCP Routing Workflow

When executing routing tasks, LLMs must invoke the Freerouting MCP tools in a structured state-machine sequence to complete the routing job correctly.

### Workflow Sequence

```mermaid
graph TD
    A[create_session] -->|Step 1| B[enqueue_job]
    B -->|Step 2 (Recommended)| C[upload_job_input_from_local_file]
    B -->|Step 2 (Alternative)| D[encode_base64]
    D -->|Local Encode| E[upload_job_input_file]
    C -->|Step 3| F[update_job_settings]
    E -->|Step 3| F
    C -->|Step 3 (Optional)| G[start_job]
    E -->|Step 3 (Optional)| G
    F -->|Step 4| G
    G -->|Step 4| H[get_job_details]
    H -->|Poll: State != COMPLETED| H
    H -->|State == COMPLETED (Recommended)| I[download_job_output_to_local_file]
    H -->|State == COMPLETED (Alternative)| J[download_job_output_file]
    J -->|Step 5| K[decode_base64]
```

#### Step 1: Create Session (`create_session`)
- Call `create_session` to initialize a routing session.
- Returns a `sessionId` (e.g. `123e4567-e89b-12d3-a456-426614174000`).

#### Step 2: Enqueue Job (`enqueue_job`)
- Call `enqueue_job` using the `sessionId` from Step 1.
- Returns a `jobId`.

#### Step 3: Upload Design File

##### Option A: Direct Filesystem Upload (Highly Recommended)
- Call `upload_job_input_from_local_file` with the `jobId` and the absolute or relative `filePath` to the design file on your local machine (e.g., `C:/Work/board.dsn`).
- **Benefit**: The MCP server handles file system reading and Base64 encoding in-memory locally. The file content is never loaded into the LLM's context window, preserving tokens and improving speed.

##### Option B: Manual Base64 Upload (Alternative)
- **Encode**: It is recommended to use the local `encode_base64` tool to convert your plain-text board file (typically a `.dsn` Specctra file) into a Base64-encoded string. Prefer this over running external shell commands (like PowerShell or `base64`).
- **Upload**: Call `upload_job_input_file` with the `jobId` and the generated Base64 string under `body.data`.

#### Step 3.5 (Optional): Update Settings (`update_job_settings`)
- Call `update_job_settings` with the `jobId` if you need to override default clearance classes, passes, optimizer settings, or rules.

#### Step 4: Start and Poll the Job (`start_job` & `get_job_details`)
- **Start**: Call `start_job` with the `jobId`.
- **Poll**: Repeatedly call `get_job_details` to monitor the routing progress.
  - **Important Polling Guideline**: Call `get_job_details` with a **polling interval of 2 to 5 seconds** (e.g., sleep 3 seconds between calls). This prevents overloading the local or public server endpoints while maintaining responsive feedback.
  - **Real-Time Logs**: Alternatively, for instant, granular routing feedback, you can stream routing log events via the `stream_job_logs` endpoint.
  - Continue polling until the job state transitions to `COMPLETED` (or `FAILED`/`CANCELLED`).

#### Step 5: Retrieve Output

##### Option A: Direct Filesystem Download (Highly Recommended)
- Call `download_job_output_to_local_file` with the `jobId` and the `filePath` where the routed output Specctra SES layout should be saved on your local disk.
- **Benefit**: The MCP server downloads the Base64 result, decodes it in-memory, and writes it directly to disk. Large layout content never enters the LLM's context window.

##### Option B: Manual Base64 Download (Alternative)
- **Download**: Once status is `COMPLETED`, call `download_job_output_file` to retrieve the routed Specctra SES layout. It is returned as a Base64-encoded string.
- **Decode**: It is recommended to use the local `decode_base64` tool to convert the Base64 string back into a plain-text Specctra SES format to write to disk, rather than running external shell commands.

---

## 3. Customizing Environment Variables (Advanced)

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

---

## 4. Example User Prompts for LLMs

Below are several example prompts you can use to instruct an LLM connected via the Freerouting MCP server to perform auto-routing with varying parameters:

### Example 1: Basic Routing
> **Prompt**: "Could you route the board `fixtures/Issue313-FastTest.dsn` using Freerouting MCP and save the output as `fixtures/Issue313-FastTest.ses`?"
>
> *How the LLM executes this:* 
> 1. Call `create_session` and `enqueue_job`.
> 2. Call `upload_job_input_from_local_file` pointing to the absolute path of `fixtures/Issue313-FastTest.dsn`.
> 3. Call `start_job`.
> 4. Poll `get_job_details` every 3 seconds until completed.
> 5. Call `download_job_output_to_local_file` to save the results directly to `fixtures/Issue313-FastTest.ses`.

### Example 2: Bounded Passes, Timeout, and Optimizer Disabled
> **Prompt**: "Could you route the board `fixtures/Issue313-FastTest.dsn`? Limit the number of passes to 2, disable the optimizer, and set the job timeout to 5 minutes."
>
> *How the LLM executes this:*
> 1. Create a session and enqueue a job.
> 2. Call `upload_job_input_from_local_file` to upload the design.
> 3. Call `update_job_settings` with the following parameters:
>    ```json
>    {
>      "jobId": "<jobId>",
>      "settings": {
>        "maxPasses": 2,
>        "jobTimeoutString": "00:05:00",
>        "optimizerSettings": {
>          "enabled": false
>        }
>      }
>    }
>    ```
> 4. Start the job and poll `get_job_details` at 2-5 second intervals.
> 5. Download the result to local disk via `download_job_output_to_local_file`.

### Example 3: Customized Clearance and Multi-pass Optimization
> **Prompt**: "Route the board at `C:/Work/my_board.dsn`. Set the copper-to-edge clearance to 400 micrometers, run 10 passes, and save the routed output to `C:/Work/my_board.ses`."
>
> *How the LLM executes this:*
> 1. Create a session and enqueue a job.
> 2. Call `upload_job_input_from_local_file` with the path.
> 3. Call `update_job_settings` with:
>    ```json
>    {
>      "jobId": "<jobId>",
>      "settings": {
>        "copperToEdgeClearanceUm": 400.0,
>        "maxPasses": 10
>      }
>    }
>    ```
> 4. Start and poll the job.
> 5. Download output via `download_job_output_to_local_file` to `C:/Work/my_board.ses`.

