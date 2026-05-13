# Self-Hosting the Freerouting API

This guide explains how to run the Freerouting REST API on your own machine or server — either with Docker (recommended) or by running the JAR directly with a Java runtime.

The REST API lets EDA tools and scripts submit PCB designs for routing and retrieve the results programmatically. It is the same interface used by the public `https://api.freerouting.app` cloud service.

---

## Table of Contents

1. [Supported Platforms](#supported-platforms)
2. [Option A — Docker (recommended)](#option-a--docker-recommended)
   - [Quick start](#quick-start)
   - [Persistent data volume](#persistent-data-volume)
   - [Docker Compose](#docker-compose)
   - [Custom endpoint / network exposure](#custom-endpoint--network-exposure)
3. [Option B — JAR with Java JRE](#option-b--jar-with-java-jre)
   - [Quick start](#quick-start-1)
   - [Running as a background service](#running-as-a-background-service)
4. [Configuration Reference](#configuration-reference)
   - [Key settings for API mode](#key-settings-for-api-mode)
   - [All configuration methods](#all-configuration-methods)
5. [Authentication](#authentication)
6. [Verifying the server is running](#verifying-the-server-is-running)
7. [Connecting EDA tools to your local server](#connecting-eda-tools-to-your-local-server)
8. [Troubleshooting](#troubleshooting)

---

## Supported Platforms

The official Docker image is a multi-platform image published to [ghcr.io/freerouting/freerouting](https://ghcr.io/freerouting/freerouting). Docker automatically selects the correct variant for your hardware.

| Architecture | Typical hardware |
|---|---|
| `linux/amd64` | Standard x86-64 servers, PCs, most cloud VMs |
| `linux/arm64` | Apple Silicon Macs (via Rosetta/native), AWS Graviton, Raspberry Pi 4/5 running a **64-bit** OS |
| `linux/arm/v7` | Raspberry Pi 2/3/4 running a **32-bit** OS (e.g. Raspberry Pi OS Legacy 32-bit), Umbrel, CasaOS, and other self-hosted home-server stacks on ARMv7 hardware |

> **Umbrel users:** Umbrel often runs a 32-bit OS on 64-bit Raspberry Pi hardware. The `linux/arm/v7` image covers that case. No extra configuration is needed — Docker selects the right image automatically.

---

## Option A — Docker (recommended)

Docker handles the Java runtime, permissions, and restarts for you. This is the easiest deployment path.

### Quick start

```bash
docker run -d \
  --name freerouting \
  --restart unless-stopped \
  -p 37864:37864 \
  ghcr.io/freerouting/freerouting:latest
```

The API will be accessible at `http://localhost:37864/v1`.

> **Default behaviour:** By default the server binds to `0.0.0.0:37864` inside the container (all container-internal interfaces), and Docker maps that to the host via `-p 37864:37864`. Authentication is disabled in the image's default `CMD` so local tools can connect without an API key.

### Persistent data volume

Mount a host directory to preserve logs and saved routing jobs between container restarts:

```bash
docker run -d \
  --name freerouting \
  --restart unless-stopped \
  -p 37864:37864 \
  -v /path/on/host:/mnt/freerouting \
  ghcr.io/freerouting/freerouting:latest
```

Replace `/path/on/host` with any writable directory on your host (e.g. `~/freerouting-data`).

### Docker Compose

Save the following as `docker-compose.yml` and run `docker compose up -d`:

```yaml
services:
  freerouting:
    image: ghcr.io/freerouting/freerouting:latest
    container_name: freerouting
    restart: unless-stopped
    ports:
      - "37864:37864"
    volumes:
      - freerouting-data:/mnt/freerouting

volumes:
  freerouting-data:
```

To override any setting, add an `environment` block or a custom `command`:

```yaml
services:
  freerouting:
    image: ghcr.io/freerouting/freerouting:latest
    container_name: freerouting
    restart: unless-stopped
    ports:
      - "37864:37864"
    volumes:
      - freerouting-data:/mnt/freerouting
    # Example: expose to all host interfaces, disable auth, enable job saving
    command: >
      java -jar /app/freerouting-executable.jar
      --api_server.enabled=true
      --gui.enabled=false
      --api_server.authentication.enabled=false
      --api_server-endpoints=http://0.0.0.0:37864
      --feature_flags.save_jobs=true
      --user_data_path=/mnt/freerouting

volumes:
  freerouting-data:
```

### Custom endpoint / network exposure

By default the Docker CMD already passes `--api_server-enabled=true` and binds to `0.0.0.0:37864` inside the container. To restrict which host interface is exposed, change the `-p` mapping:

```bash
# Only accessible from the local machine (most secure)
docker run -d -p 127.0.0.1:37864:37864 ghcr.io/freerouting/freerouting:latest

# Accessible from any host on the network
docker run -d -p 0.0.0.0:37864:37864 ghcr.io/freerouting/freerouting:latest
```

---

## Option B — JAR with Java JRE

Use this approach when you cannot or do not want to use Docker.

### Prerequisites

- [Java JRE 25+](https://adoptium.net/temurin/releases/) (select your OS, `JRE` package type, version `25`)
- The Freerouting executable JAR from the [Releases page](https://github.com/freerouting/freerouting/releases)
  (file name: `freerouting-2.2.4.jar` or `freerouting-executable.jar`)

### Quick start

Run Freerouting in headless API-server mode:

```bash
java -jar freerouting-2.2.4.jar \
  --gui.enabled=false \
  --api_server.enabled=true \
  --api_server.authentication.enabled=false
```

This starts the API server on `http://127.0.0.1:37864` (localhost only).

To accept connections from other machines on your network:

```bash
java -jar freerouting-2.2.4.jar \
  --gui.enabled=false \
  --api_server.enabled=true \
  --api_server.authentication.enabled=false \
  --api_server-endpoints=http://0.0.0.0:37864
```

> ⚠️ **Security note:** Binding to `0.0.0.0` exposes the API to every network interface on the host. Only do this on a trusted local network or behind a firewall / reverse proxy.

To persist logs and data to a specific directory:

```bash
java -jar freerouting-2.2.4.jar \
  --gui.enabled=false \
  --api_server.enabled=true \
  --api_server.authentication.enabled=false \
  --user_data_path=/var/lib/freerouting
```

### Running as a background service

#### Linux — systemd unit

Create `/etc/systemd/system/freerouting.service`:

```ini
[Unit]
Description=Freerouting API Server
After=network.target

[Service]
Type=simple
User=freerouting
ExecStart=java -jar /opt/freerouting/freerouting-2.2.4.jar \
  --gui.enabled=false \
  --api_server.enabled=true \
  --api_server.authentication.enabled=false \
  --user_data_path=/var/lib/freerouting
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Then enable and start it:

```bash
sudo systemctl daemon-reload
sudo systemctl enable freerouting
sudo systemctl start freerouting
```

#### macOS — launchd plist

Create `~/Library/LaunchAgents/app.freerouting.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>app.freerouting</string>
  <key>ProgramArguments</key>
  <array>
    <string>/usr/bin/java</string>
    <string>-jar</string>
    <string>/opt/freerouting/freerouting-2.2.4.jar</string>
    <string>--gui.enabled=false</string>
    <string>--api_server.enabled=true</string>
    <string>--api_server.authentication.enabled=false</string>
    <string>--user_data_path=/var/lib/freerouting</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
</dict>
</plist>
```

Load it with:

```bash
launchctl load ~/Library/LaunchAgents/app.freerouting.plist
```

#### Windows — NSSM (Non-Sucking Service Manager)

```powershell
nssm install Freerouting "java" "-jar C:\freerouting\freerouting-2.2.4.jar --gui.enabled=false --api_server.enabled=true --api_server.authentication.enabled=false --user_data_path=C:\freerouting\data"
nssm start Freerouting
```

---

## Configuration Reference

### Key settings for API mode

| Setting | CLI argument | Default | Description |
|---|---|---|---|
| Disable GUI | `--gui.enabled=false` | `true` | Required for headless/server operation. |
| Enable API server | `--api_server.enabled=true` | `false` | Starts the embedded REST API server. |
| Disable authentication | `--api_server.authentication.enabled=false` | `true` | Allows requests without an API key. Recommended for local use. |
| Bind address | `--api_server-endpoints=http://0.0.0.0:37864` | `http://127.0.0.1:37864` | Address and port the server listens on. |
| CORS origins | `--api_server.cors_origins=*` | _(none)_ | Allows browser-based clients to call the API. Set to `*` or a specific origin. |
| Data directory | `--user_data_path=/path/to/data` | OS temp dir | Where logs and saved jobs are stored. |
| Save jobs to disk | `--feature_flags.save_jobs=true` | `false` | Persists routing jobs (input/output files + metadata) under `user_data_path`. |
| Max routing passes | `--router.max_passes=100` | `100` | Upper limit on autorouting passes per job. |
| Thread count | `-mt 4` or `--router.max_threads=4` | CPU count − 1 | Worker threads for route optimisation. |

### All configuration methods

Settings can be provided in three ways (higher in the list = higher priority):

1. **CLI arguments** — `--setting_name=value` (dot, dash, or colon as level separator)
2. **Environment variables** — `FREEROUTING__SECTION__KEY=value` (double underscores between levels)
3. **JSON config file** — `freerouting.json` in the `user_data_path` directory

**Example using environment variables (useful in Docker Compose):**

```yaml
environment:
  FREEROUTING__GUI__ENABLED: "false"
  FREEROUTING__API_SERVER__ENABLED: "true"
  FREEROUTING__API_SERVER__AUTHENTICATION__ENABLED: "false"
  FREEROUTING__API_SERVER__ENDPOINTS: "http://0.0.0.0:37864"
```

For the full list of all available settings, see [settings.md](settings.md).  
For all CLI arguments (including legacy short-form flags), see [command_line_arguments.md](command_line_arguments.md).

---

## Authentication

By default, authentication is **enabled** (`api_server.authentication.enabled=true`). This means every API request must include an `Authorization: Bearer <api-key>` header.

For **local self-hosting** (where the server is only reachable from the same machine or a trusted LAN), it is safe and convenient to disable authentication:

```bash
--api_server.authentication.enabled=false
```

For **public or shared deployments**, leave authentication enabled and configure an API key provider. See the [API Authentication documentation](API/API_authentication.md) for details.

---

## Verifying the server is running

Once the server starts you should see a log line similar to:

```
INFO  Freerouting API server started at http://0.0.0.0:37864
```

Check the status endpoint:

```bash
curl http://localhost:37864/v1/system/status
```

A healthy response looks like:

```json
{
  "version": "2.2.4",
  "status": "healthy"
}
```

You can also open `http://localhost:37864/v1/system/status` in a browser.

---

## Connecting EDA tools to your local server

Most EDA integrations that support Freerouting accept a custom API base URL. Point them at your local server instead of the cloud endpoint:

| Tool | Where to set the URL |
|---|---|
| **KiCad plugin** | Plugin settings → *Freerouting server URL* |
| **EasyEDA** | Freerouting settings → *API endpoint* |
| **Python client** | `FreeroutingClient(base_url="http://localhost:37864/v1")` |
| **Direct REST calls** | Replace `https://api.freerouting.app/v1` with `http://localhost:37864/v1` |

---

## Troubleshooting

### Port already in use

```
Address already in use: bind
```

Another process is using port 37864. Either stop the other process or change the port:

```bash
--api_server-endpoints=http://0.0.0.0:38000
```

and update your `-p` mapping accordingly if using Docker.

### Cannot connect from another machine

- Make sure you bound to `0.0.0.0` and not `127.0.0.1`.
- Check that your firewall allows inbound connections on port 37864.
- If using Docker, verify the `-p` publish argument maps to `0.0.0.0:37864` on the host.

### `401 Unauthorized` responses

Authentication is enabled. Either:
- Add `--api_server.authentication.enabled=false` for local use, or
- Include the header `Authorization: Bearer <your-api-key>` in all requests.

### Out-of-memory on large boards (Raspberry Pi / ARMv7)

The `linux/arm/v7` image runs a 32-bit JVM, which has a 4 GB address-space ceiling. Large boards with hundreds of nets can approach this. Mitigation options:

- Use a 64-bit OS and the `linux/arm64` image instead (Raspberry Pi OS 64-bit is available for Pi 3 and later).
- Limit routing passes with `--router.max_passes=20` to reduce peak memory usage.
- Add a JVM heap flag: `-Xmx1g` (adjust to your available RAM).

```bash
docker run -d -p 37864:37864 \
  ghcr.io/freerouting/freerouting:latest \
  java -Xmx1g -jar /app/freerouting-executable.jar \
  --api_server.enabled=true \
  --gui.enabled=false \
  --api_server.authentication.enabled=false
```
