## Freerouting Settings Documentation

Freerouting uses a flexible settings system that allows users to customize various aspects of the application's behavior. These settings can be managed through a JSON configuration file, command-line arguments, or environment variables.

### Settings File (JSON)

The primary way to configure Freerouting is through a JSON settings file. This file contains key-value pairs for different settings and is usually located in the `%temp%\freerouting` directory (`%temp%` refers to your system's temporary folder). The file is created during Freerouting's first run if it doesn't already exist.

```json
{
  "version": "2.0.0",
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
    ]
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

### Command Line Arguments

Freerouting can also be configured using command-line arguments. These arguments override the settings specified in the JSON configuration file. You must use `--{property-name}={property-value}` format, where `property-name` is the hierarchical definition of the property you want to change and the `property-value` is its desired value. You can use `.`, `-` and `:` charapters to separate the hierarchical levels in the `property-name` parameter.

**Example:**

```bash
java -jar freerouting.jar --gui.enabled=false --router.max_passes=200
```

### Environment Variables

Environment variables provide another way to override settings. The environment variable names correspond to the keys in the JSON settings file, starting with `FREEROUTING__` and periods replaced by double underscores.

**Example:**

```bash
FREEROUTING__GUI__ENABLED=false
FREEROUTING__ROUTER__MAX_PASSES=200
java -jar freerouting.jar
```

## Settings Precedence

The settings are applied in the following order of precedence (highest to lowest):

1. **Command Line Arguments**
2. **Environment Variables**
3. **JSON Configuration File**
4. **Default Settings** (hardcoded in the application)
   This means that command-line arguments take precedence over environment variables, which in turn take precedence over
   the settings specified in the JSON configuration file. If a setting is not defined in any of these sources, the
   default value hardcoded in the application will be used.
