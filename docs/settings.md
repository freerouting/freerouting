## Freerouting Settings Documentation

Freerouting uses a flexible settings system that allows users to customize various aspects of the application's behavior. These settings can be managed through a JSON configuration file, command-line arguments, or environment variables.

### Settings File (JSON)

The primary way to configure Freerouting is through a JSON settings file. This file contains key-value pairs for different settings and is usually located in the `%temp%\freerouting` directory (`%temp%` refers to your system's temporary folder). The file is created during Freerouting's first run if it doesn't already exist.

```json
{
  "version": "2.2.4",
  "profile": {
    "id": "09730e5f-4886-49f0-afba-76f459408907",
    "email": "info@freerouting.app"
  },
  "logging": {
    "console": {
      "enabled": true,
      "level": "INFO"
    },
    "file": {
      "enabled": true,
      "level": "INFO",
      "location": ""
    }
  },
  "gui": {
    "enabled": true,
    "input_directory": "C:\\Work\\freerouting\\tests",
    "dialog_confirmation_timeout": 5
  },
  "router": {
    "default_preferred_direction_trace_cost": 1.0,
    "default_undesired_direction_trace_cost": 2.5,
    "max_passes": 100,
    "fanout_max_passes": 20,
    "max_threads": 11,
    "improvement_threshold": 0.01,
    "trace_pull_tight_accuracy": 500,
    "allowed_via_types": true,
    "via_costs": 50,
    "plane_via_costs": 5,
    "start_ripup_costs": 100,
    "automatic_neckdown": true
  },
  "usage_and_diagnostic_data": {
    "disable_analytics": false,
    "analytics_modulo": 16
  },
  "feature_flags": {
    "multi_threading": false,
    "select_mode": false,
    "macros": false,
    "other_menu": false,
    "snapshots": false,
    "file_load_dialog_at_startup": false,
    "save_jobs": false
  },
  "api_server": {
    "enabled": false,
    "http_allowed": true,
    "endpoints": [
      "http://0.0.0.0:37864"
    ],
    "cors_origins": "",
    "rate_limit": {
      "enabled": false,
      "requests_per_window": 120,
      "window_seconds": 60
    }
  },
  "mcp_server": {
    "enabled": false,
    "http_allowed": true,
    "endpoints": [
      "http://127.0.0.1:37964"
    ],
    "authentication": {
      "enabled": true,
      "providers": ""
    },
    "cors_origins": "",
    "rate_limit": {
      "enabled": false,
      "requests_per_window": 120,
      "window_seconds": 60
    },
    "target_api_base_url": "http://127.0.0.1:37864"
  }
}
```

#### **`version` Section**

- **`version`**: Specifies the version of the settings file.

#### **`profile` Section**

- **`id`**: A unique identifier for the user's profile. This is typically a UUID (Universally Unique Identifier).
- **`email`**: The user's email address (optional).

#### **`logging` Section**

- **`console`**:
    - **`enabled`**: Enables or disables console logging. Default is `true`.
    - **`level`**: Sets the console log level (OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL). Default is `INFO`.
- **`file`**:
    - **`enabled`**: Enables or disables file logging. Default is `true`.
    - **`level`**: Sets the file log level. Default is `INFO`.
    - **`location`**: Directory path for the log file. If empty, defaults to the user data path.

#### **`gui` Section**

- **`enabled`**: Enables or disables the graphical user interface (GUI). If set to `false`, Freerouting will run in
  headless mode.
- **`input_directory`**: Specifies the default directory for opening design files.
- **`dialog_confirmation_timeout`**: Sets the timeout in seconds for dialog confirmations.

#### **`router` Section**

- **`default_preferred_direction_trace_cost`**: Cost factor for routing traces in the preferred direction.
- **`default_undesired_direction_trace_cost`**: Cost factor for routing traces in undesired directions.
- **`max_passes`**: Maximum number of routing passes.
- **`fanout_max_passes`**: Maximum number of passes for fanout routing.
- **`max_threads`**: Maximum number of threads to use for routing.
- **`improvement_threshold`**: Minimum improvement required to continue routing.
- **`trace_pull_tight_accuracy`**: Accuracy for pulling traces tight.
- **`allowed_via_types`**: Enables or disables the use of different via types.
- **`via_costs`**: Cost factor for using vias.
- **`plane_via_costs`**: Cost factor for using vias on plane layers.
- **`start_ripup_costs`**: Cost factor for ripping up existing traces.
- **`automatic_neckdown`**: Enables or disables automatic neckdown of traces.

#### **`usage_and_diagnostic_data` Section**

- **`disable_analytics`**: Disables sending anonymous usage and diagnostic data.
- **`analytics_modulo`**: Sends usage data after every Nth run, where N is the value of `analytics_modulo`.

#### **`feature_flags` Section**

- **`multi_threading`**: Enables or disables multi-threaded routing.
- **`inspection_mode`**: Enables or disables inspection mode in the GUI.
- **`other_menu`**: Enables or disables the "Other" menu in the GUI.
- **`save_jobs`**: Enables or disables saving routing jobs to disk.

#### **`api_server` Section**

- **`enabled`**: Enables or disables the built-in API server.
- **`http_allowed`**: Allows or disallows HTTP connections to the API server.
- **`endpoints`**: A list of endpoints that the API server will listen on. Each endpoint is specified as
  `[protocol]://[host]:[port]`.  
  When set via CLI or environment variable, provide a **comma-separated string** of endpoint URLs:
  - CLI: `--api_server-endpoints=http://0.0.0.0:37864,http://127.0.0.1:37864`
  - Env var: `FREEROUTING__API_SERVER__ENDPOINTS=http://0.0.0.0:37864,http://127.0.0.1:37864`
- *`cors_origins`*: A comma-separated list of origins for the `Access-Control-Allow-Origin` CORS header. Set to `*` to accept all origins (this can be a security risk). When CORS is enabled, the server automatically allows the following request headers in preflight responses: `Content-Type`, `Accept`, `Origin`, `X-Requested-With`, `Authorization`, `Freerouting-Profile-ID`, `Freerouting-Profile-Email`, and `Freerouting-Environment-Host`. This ensures browser-based clients (e.g. EasyEDA at `https://pro.lceda.cn`) can authenticate successfully without being blocked by CORS preflight checks.
- **`rate_limit`**: Fixed-window throttling for API requests.
  - `enabled`: Enable/disable API-side rate limiting.
  - `requests_per_window`: Maximum accepted requests per identity in each window.
  - `window_seconds`: Window duration in seconds.

#### **`mcp_server` Section**

- **`enabled`**: Enables or disables the dedicated MCP server.
- **`http_allowed`**: Allows or disallows HTTP connections to the MCP server.
- **`endpoints`**: A list of MCP listen endpoints (`[protocol]://[host]:[port]`).
- **`authentication`**: API-key authentication settings specific to MCP (`enabled`, `providers`, provider credentials).
- **`cors_origins`**: Optional CORS allowlist for browser-hosted MCP clients.
- **`target_api_base_url`**: Base URL of the REST API server used by MCP tools to execute operations.
  Must point to the REST API base URL (for example `http://127.0.0.1:37864`) and not to MCP paths such as `/v1/mcp` or `/.well-known/*`.
- **`rate_limit`**: Fixed-window throttling for MCP HTTP requests (for example `/v1/mcp`).
  - `enabled`: Enable/disable MCP-side rate limiting.
  - `requests_per_window`: Maximum accepted requests per identity in each window.
  - `window_seconds`: Window duration in seconds.

#### Recommended Rate-Limit Presets

Use these as practical starting points, then tune based on observed traffic and client retry behavior.

| Environment | API (`api_server.rate_limit`) | MCP (`mcp_server.rate_limit`) | Notes |
|---|---|---|---|
| Local development | `enabled=false` | `enabled=false` | Fast feedback loop, no throttling noise while debugging. |
| Staging / internal QA | `enabled=true`, `requests_per_window=120`, `window_seconds=60` | `enabled=true`, `requests_per_window=60`, `window_seconds=60` | Catches runaway polling while staying permissive for tests. |
| Production (default baseline) | `enabled=true`, `requests_per_window=180`, `window_seconds=60` | `enabled=true`, `requests_per_window=90`, `window_seconds=60` | Balanced baseline for mixed interactive + automation traffic. |
| Production (strict) | `enabled=true`, `requests_per_window=120`, `window_seconds=60` | `enabled=true`, `requests_per_window=45`, `window_seconds=60` | For public exposure or when abuse pressure is expected. |

Tuning guidance:

- If legitimate clients hit HTTP `429` frequently, raise `requests_per_window` first.
- Keep `window_seconds` at `60` unless you have a clear reason to use shorter bursts.
- MCP generally needs lower limits than REST because tool loops can burst quickly.
- Pair rate limits with authentication and correlation-ID logging for reliable incident analysis.

### Command Line Arguments

Freerouting can also be configured using command-line arguments. These arguments override the settings specified in the JSON configuration file. You must use `--{property-name}={property-value}` format, where `property-name` is the hierarchical definition of the property you want to change and the `property-value` is its desired value. You can use `.`, `-` and `:` characters to separate the hierarchical levels in the `property-name` parameter.

**Scalar example:**

```bash
java -jar freerouting.jar --gui.enabled=false --router.max_passes=200
```

**List-valued settings** (e.g. `api_server.endpoints`, `mcp_server.endpoints`) must be passed as a **comma-separated string**; whitespace around commas is ignored:

```bash
java -jar freerouting.jar --api_server-endpoints=http://0.0.0.0:37864
java -jar freerouting.jar --api_server-endpoints=http://0.0.0.0:37864,http://127.0.0.1:37864
java -jar freerouting.jar --mcp_server-enabled=true --mcp_server-endpoints=http://127.0.0.1:37964 --mcp_server-target_api_base_url=http://127.0.0.1:37864
java -jar freerouting.jar --api_server.rate_limit.enabled=true --api_server.rate_limit.requests_per_window=120 --api_server.rate_limit.window_seconds=60
java -jar freerouting.jar --mcp_server.rate_limit.enabled=true --mcp_server.rate_limit.requests_per_window=60 --mcp_server.rate_limit.window_seconds=60
```

### Environment Variables

Environment variables provide another way to override settings. The environment variable names correspond to the keys in the JSON settings file, starting with `FREEROUTING__` and periods replaced by double underscores.

**Scalar example:**

```bash
FREEROUTING__GUI__ENABLED=false
FREEROUTING__ROUTER__MAX_PASSES=200
java -jar freerouting.jar
```

**List-valued settings** use the same comma-separated format as CLI arguments:

```bash
FREEROUTING__API_SERVER__ENDPOINTS=http://0.0.0.0:37864,http://127.0.0.1:37864
FREEROUTING__MCP_SERVER__ENABLED=true
FREEROUTING__MCP_SERVER__ENDPOINTS=http://127.0.0.1:37964
FREEROUTING__MCP_SERVER__TARGET_API_BASE_URL=http://127.0.0.1:37864
java -jar freerouting.jar
```

## Settings Precedence

Settings are resolved by the `SettingsMerger` class, which collects all active `SettingsSource` implementations, sorts them by ascending priority, and applies each source on top of the previously accumulated result. The full priority ladder (lowest → highest) is:

| Priority | Source | Class |
|----------|--------|-------|
| 0 | Default Settings (hardcoded baseline) | `DefaultSettings` |
| 10 | JSON configuration file (`freerouting.json`) | `JsonFileSettings` |
| 20 | DSN file metadata | `DsnFileSettings` |
| 30 | SES file metadata | `SesFileSettings` |
| 40 | RULES file overrides | `RulesFileSettings` |
| 50 | GUI (interactive user changes) | `GuiSettings` |
| 55 | Environment variables (`FREEROUTING__ROUTER__*`) | `EnvironmentVariablesSource` |
| 60 | CLI arguments (`--router.*`) | `CliSettings` |
| 70 | REST API caller — highest priority | `ApiSettings` |

If a setting is not defined in any source, the hardcoded default from `DefaultSettings` is used.

## Settings Architecture — Why Fields Must Be Nullable

`RouterSettings` intentionally declares all its fields as nullable reference types (e.g. `Integer`, `Boolean`, `String`) **with no default initializers**. This is a deliberate architectural constraint required by the merge mechanism:

`SettingsMerger.merge()` calls `RouterSettings.applyNewValuesFrom(source)`, which delegates to `ReflectionUtil.copyFields()`. That method copies a field from the incoming source into the accumulated result **only when the source field is non-null and differs from the Java language default for that type**.

If any field were initialised to a non-null value inside the `RouterSettings` constructor (e.g. `public Integer maxPasses = 9999;`), every source's settings object would carry that value, and the merger would incorrectly treat it as an explicit override. A low-priority source (such as the JSON file) would then silently win over a higher-priority source (such as the API) whenever the user left that field unspecified in the high-priority source.

**Keep all `RouterSettings` fields null-initialised.** Concrete defaults belong exclusively in `DefaultSettings.getSettings()`, which is always the first source applied and therefore acts as the safe fallback for every field