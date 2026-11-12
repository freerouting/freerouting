# Freerouting Command Line Interface (CLI) Documentation

## Introduction

The Freerouting Command Line Interface (CLI) allows you to automate PCB routing tasks without using the graphical user interface (GUI). This is particularly useful for integrating Freerouting into scripts, build systems, or other software tools where automated routing is required.

This document provides detailed information on how to use Freerouting via the CLI, including available command-line options and how to adjust internal settings for advanced configurations.

## Usage

To run Freerouting from the command line, use the following syntax:

```bash
java -jar path/to/freerouting.jar [options]
```

Download the latest `freerouting-<version>.jar` from the [Releases page](https://github.com/freerouting/freerouting/releases) and substitute its path above.

## Command-Line Options

Below is a comprehensive list of command-line options available in Freerouting, organized by category.

### Input and Output Files

- **`-de [design input file]`**
  Loads a design file at startup. The input can be:
    - Specctra board (`.dsn`)
    - Specctra session file (`.ses` - optional)
    - Freerouting design rules file (`.rules` - optional)
    - KiCad custom design rules file (`.kicad_dru` - optional)
    - KiCad project file (`.kicad_pro` - optional)

  The DSN file is mandatory, while the SES, RULES and KiCad design rules files are optional.
  They can be provided in any order, separately or combined with the `+` delimiter (e.g. `-de myboard.dsn+myboard.ses+myboard.rules` or `-de myboard.dsn+myboard.rules`). When a `.rules` file is loaded, its design rules, net classes, clearances, via definitions, and `(autoroute_settings ...)` / `(layer_rule ...)` parameters are applied to the board and autorouter configuration. If no rules file is explicitly specified, Freerouting will automatically look for an adjacent `<boardname>.rules` file in the same directory as the DSN file.

  KiCad writes only the clearances of its net classes into a DSN file: the custom rules of the board (`.kicad_dru`) are lost. When a `.kicad_dru` file is explicitly given (e.g. `-de myboard.dsn+myboard.kicad_dru` or `-de myboard.dsn myboard.kicad_dru`), Freerouting translates its minimum clearance rules into clearance classes of the board, as KiCad evaluates them: the last matching rule wins over the net class clearance, conditions on `NetClass`, `NetName`, `Type`, `Pad_Type`, `Reference`, `Parent`, `Pad_Number`, `memberOfFootprint()`, `hasNetclass()` and `isPlated()` are evaluated, and `(layer ...)` clauses are honoured. A part of a condition Freerouting cannot evaluate (for example a geometric function such as `intersectsArea()`) makes the rule apply wherever it may match, so the board is never less strict than KiCad. Every rule, constraint or condition that is not translated (creepage, track widths, ...) is reported as a warning in the log. A `.kicad_dru` file given with `-de` that cannot be read stops a command-line run. Unlike `.rules` files, an adjacent `<boardname>.kicad_dru` file is not loaded automatically unless explicitly passed as an input.

  KiCad does not write the minimum track width of the board (`Board Setup > Constraints`) into a DSN file either, and its DRC reports every narrower trace as a `track_width` error. When a `.kicad_pro` file is explicitly given (e.g. `-de myboard.dsn+myboard.kicad_pro`), its `board.design_settings.rules.min_track_width` becomes the default of `--router.neck_width_um`, the narrowest trace the router may use. A `--router.neck_width_um` given on the command line, in an environment variable, in the GUI or through the API still wins. A `.kicad_pro` file given with `-de` that cannot be read stops a command-line run; an adjacent `<boardname>.kicad_pro` file is not loaded automatically.

- **`-do [design output file]`**
  Saves the routing results when the routing is finished. The output can be:
  - Specctra board (`.dsn`)
  - Specctra session file (`.ses`)
  - Autodesk Fusion script file (`.scr`)

  The output format is determined by the file extension provided.

- **`-di [design input directory]`**
  Sets the default folder for the open design dialogs when using the GUI.

- **`-dr [design rules file]`**
  Reads design rules from an explicit `.rules` file. Supports design rules, net classes, clearances, via definitions, and `(autoroute_settings ...)` / `(layer_rule ...)` blocks.

- **`-drc [design rules check json file]`**
  Writes the design rules check report in KiCad JSON DRC schema format.

### Routing Parameters

- **`--router.autorouter.max_passes=[number of passes]`**
  Sets the upper limit for the number of autorouter passes to perform. More passes may result in better optimization but will take longer.

- **`-mp [number of passes]`** (Deprecated)
  Legacy shorthand for `--router.autorouter.max_passes`. A warning is logged when it is used.

- **`--router.max_threads=[number of threads]`**
  Sets the global maximum thread ceiling across all routing stages (fanout, autorouter, and optimizer):
  - Default: One less than the number of logical processors on the system (`cores - 1`).
  - Set to `0` to use all available logical processors.
  - Acts as an upper bound: no individual stage will exceed this limit.

- **`--router.autorouter.max_threads=[number of threads]`**
  Sets the thread limit specifically for the autorouter stage (bounded by `--router.max_threads`).
  *Note:* The autorouter currently runs single-threaded; this setting is reserved for future multi-threaded passes.

- **`--router.optimizer.max_threads=[number of threads]`**
  Sets the thread limit specifically for the optimizer stage (bounded by `--router.max_threads`).

- **`-mt [number of threads]`** (Deprecated)
  Legacy shorthand for `--router.max_threads`. A warning is logged when it is used:
  - Setting `0` or less as a legacy shorthand to disable route optimization is deprecated; use `--router.optimizer.enabled=false` instead.
  - Increasing the number may improve performance on multi-core systems.

- **`--router.optimizer.enabled=[true|false]`**
  Runs the route optimizer after autorouting. Default is `true`.

- **`--router.optimizer.improvement_threshold=[percentage]`**
  Specifies the relative optimizer pass-improvement stopping threshold directly as a percentage (e.g. `2.5` = 2.5%, `5.5` = 5.5%):
  - Default: `2.5` (2.5%)
  - Practical ranges and tradeoffs:
    - `0.5 – 1.0` (0.5% – 1.0%): Precision mode. Maximizes via elimination on complex boards, but runs significantly longer with diminishing-return tail passes.
    - `2.0 – 2.5` (2.0% – 2.5%): Balanced default. Retains >80% of via reductions while cutting optimizer runtime by ~25%.
    - `3.5 – 5.0` (3.5% – 5.0%): Fast mode. Cuts optimizer runtime by ~45%, retaining ~65% of via reductions.
    - `> 5.5` (> 5.5%): Rapid prototyping. Stops after 1–2 passes; not recommended for production boards where via minimization matters.
  - Setting `0.0` continues optimization until `max_passes` is reached or no further improvements are possible.
  - *Note:* The legacy `-oit [percentage]` flag is deprecated but still applied. A warning is logged when it is used.

- **`-inc [net class names]`**
  Lists net classes to ignore during autorouting:
  - Provide a comma-separated list (e.g., `-inc GND,VCC`).
  - The autorouter will not route nets belonging to these classes.

- **`-im`** (Removed)
  No longer has any effect. Intermediate steps are not saved, and routing cannot be resumed
  from a checkpoint. A warning is logged when it is used.


### Optimization Strategies

- **`--router.optimizer.item_selection_strategy=[sequential|prioritized]`**
  Sets the item selection strategy for route optimization:
  - `sequential` (default): Processes items in order.
  - `prioritized`: Selects items based on calculated scores from previous rounds.

- **`-is [sequential | prioritized]`** (Deprecated)
  Legacy shorthand for `--router.optimizer.item_selection_strategy`. A warning is logged when it is used.

- **`-us [greedy | global | hybrid]`** and **`-hr [m:n]`** (Removed)
  No longer have any effect. The optimizer only uses the global optimal board update strategy.
  A warning is logged when they are used.

### Language and Localization

- **`-l [language code]`**
  Sets the language for the user interface:
  - Supported codes:
    - `en`: English
    - `de`: German
    - `zh`: Simplified Chinese
  - If unsupported, defaults to the system language or English.

### Host Integration

- **`-host [host_name host_version]`**
  Specifies the name and version of the host application if Freerouting is run as an external library or plugin.

### Debugging Options

- **`--debug.enable_detailed_logging=[true|false]`**
  Enables detailed trace logging to the log file. Default is `false`.
  - Effect: Sets the file logging level to TRACE. Note that this can generate very large log files.

- **`--debug.single_step_execution=[true|false]`**
  Enables single-step execution mode. Default is `false`.
  - Effect: Shows "Play", "Pause", "Next", and "Previous" buttons in the toolbar. The autorouter will start valid pauses at breakpoints (e.g. trace insertion).

- **`--debug.trace_insertion_delay=[milliseconds]`**
  Adds a delay in milliseconds after each trace insertion or major routing step. Default is `0`.
  - Effect: Slows down the routing process for visual debugging.

- **`--debug.filter_by_net=[net1,net2,...]`**
  Restricts debug actions (stepping, delays) to specific nets.
  - Format: Comma-separated list of net names or numbers (e.g. `Net1,Net #2,3`).
  - Effect: Other nets are routed at full speed.

### Miscellaneous Options

- **`-dct [seconds]`**
  Sets the dialog confirmation timeout:
  - Specifies the number of seconds before dialogs proceed with the default action.
  - Default is `20` seconds.

- **`-da`**
  Disables the collection of anonymous analytics data.

- **`--logging.console.enabled=[true|false]`**
  Enables or disables console logging. Default is `true`.

- **`--logging.console.level=[level]`**
  Sets the console logging level.
  - Valid values: `OFF`, `FATAL`, `ERROR`, `WARN`, `INFO` (default), `DEBUG`, `TRACE`, `ALL`.

- **`--logging.file.enabled=[true|false]`**
  Enables or disables file logging. Default is `true`.

- **`--logging.file.level=[level]`**
  Sets the file logging level. Default is `INFO`.

- **`--logging.file.location=[directory]`**
  Defines the directory where `freerouting.log` is stored.

- **`-dl`** (Legacy)
  Disables **file** logging. Equivalent to `--logging.file.enabled=false`.

- **`-ll [level]`** (Legacy)
  Sets the **console** logging level. Equivalent to `--logging.console.level=[level]`.

- **`--user_data_path=[directory]`**
  Defines the directory where configuration and user data files are stored.
  - Purpose:
    - `freerouting.json` (settings) will be read from this directory if it exists, or created there if it doesn't.
    - Saved routing jobs (`data/`) will be stored under this directory.
    - When `--logging.file.location` is not specified, `freerouting.log` will be created in this directory if `--user_data_path` is explicitly set, or in the platform default log directory if left at default.
  - Default locations:
    - **Windows:** `%APPDATA%\freerouting` (e.g. `C:\Users\<User>\AppData\Roaming\freerouting`)
    - **macOS:** `~/Library/Application Support/freerouting`
    - **Linux / POSIX:** `$XDG_CONFIG_HOME/freerouting` (default `~/.config/freerouting`)
  - Format constraint: Must use the `--user_data_path=path` syntax with an equals sign.
  - If the directory does not exist when Freerouting starts, it is created automatically on the first write (e.g. when `freerouting.json` is saved for the first time). A warning is printed to stderr if the initial `mkdirs()` attempt fails; the path is still registered and the directory will be created later.
  - This option takes priority over the `FREEROUTING__USER_DATA_PATH` environment variable and over `FREEROUTING__LOGGING__FILE__LOCATION`.

- **`-help`**
  Displays help information and exits.

## Adjusting Internal Settings

Freerouting allows you to fine-tune its internal settings beyond the standard command-line options. To modify these settings, use the double dash `--` prefix followed by the setting name and its value.

**Syntax:**

```bash
java -jar freerouting.jar --setting_name=value
```

**Examples:**

- **Disable the GUI:**

  ```bash
  java -jar freerouting.jar --gui.enabled=false
  ```

- **Adjust the via cost:**

  ```bash
  java -jar freerouting.jar --router.via_costs=150
  ```

### List-valued settings

Settings whose value is a list (e.g. `api_server.endpoints`) accept a **comma-separated string** when set via CLI argument or environment variable. Whitespace around each comma is stripped automatically.

```bash
# Single endpoint
java -jar freerouting.jar --api_server.endpoints=http://0.0.0.0:37864

# Multiple endpoints (comma-separated)
java -jar freerouting.jar --api_server.endpoints=http://0.0.0.0:37864,http://127.0.0.1:37864
```

The equivalent environment-variable syntax is:

```bash
FREEROUTING__API_SERVER__ENDPOINTS=http://0.0.0.0:37864,http://127.0.0.1:37864
```

> **Note:** Commas are the chosen delimiter because URL characters that would normally include a comma are percent-encoded by browsers/tools, so plain commas in the string unambiguously mark element boundaries.

### Layer-specific settings (Arrays)

Settings for individual board layers can be specified as a comma-separated list of values, where each value corresponds to a layer in order (from the top-most layer to the bottom-most):

- **`--router.layers.routable=false,true`**: Sets which layers are active/routable (e.g. `false,true` disables layer 1 and enables layer 2).
- **`--router.layers.preferred_direction_horizontal=true,false`**: Sets if the preferred direction on each layer is horizontal (`true`) or vertical (`false`).

For example, to route a board using only the second (bottom) layer:

```bash
java -jar freerouting.jar -de MyBoard.dsn -do MyBoard.ses --router.layers.routable=false,true
```

The equivalent environment-variable syntax is:

```bash
FREEROUTING__ROUTER__LAYERS__ROUTABLE=false,true
FREEROUTING__ROUTER__LAYERS__PREFERRED_DIRECTION_HORIZONTAL=true,false
```

### API Server Settings

| Setting | Type | Description |
|---------|------|-------------|
| `api_server.enabled` | Boolean | Enable or disable the built-in REST API server. |
| `api_server.http_allowed` | Boolean | Allow plain HTTP connections (in addition to HTTPS). |
| `api_server.endpoints` | String list | Comma-separated list of `protocol://host:port` endpoints the server will bind to. Default: `http://127.0.0.1:37864`. |
| `api_server.authentication.enabled` | Boolean | Require API-key authentication. Default: `true`. |
| `api_server.cors_origins` | String | Comma-separated CORS origin allowlist (use `*` for all origins). |

**Example — expose to all network interfaces without authentication (Docker or LAN):**

```bash
java -jar freerouting-executable.jar \
  --gui.enabled=false \
  --api_server.enabled=true \
  --api_server.authentication.enabled=false \
  --api_server.endpoints=http://0.0.0.0:37864
```

For a complete self-hosting walkthrough — including Docker Compose, systemd, and platform-specific notes — see the [Self-Hosting Guide](self-hosting.md).

## Examples

Below are some common usage examples to help you get started.

### Example 1: Basic Autorouting

Autoroute a design and save the results:

```bash
java -jar freerouting.jar -de MyBoard.dsn -do MyBoard.ses
```

- Loads `MyBoard.dsn`.
- Performs autorouting.
- Saves the routed design to `MyBoard.ses`.

### Example 2: Ignoring Specific Net Classes

Ignore the `GND` and `VCC` net classes during routing:

```bash
java -jar freerouting.jar -de MyBoard.dsn -do MyBoard.ses -inc GND,VCC
```

- Nets in the `GND` and `VCC` classes will not be routed.
- Useful when you plan to route these nets manually.

### Example 3: Limiting the Number of Passes and Threads

Limit the autorouter to 10 passes and use 4 threads:

```bash
java -jar freerouting.jar -de MyBoard.dsn -do MyBoard.ses -mp 10 -mt 4
```

- Sets a cap on routing time and resource usage.
- Adjust `-mt` based on your system's capabilities.

## Conclusion

By leveraging Freerouting's CLI, you can integrate advanced PCB routing into your automated workflows, scripts, or applications. The flexibility of command-line options and internal settings allows for customized routing solutions tailored to your project's requirements.

For further customization and advanced configurations, refer to the [Settings Documentation](settings.md) and other resources provided with Freerouting.
