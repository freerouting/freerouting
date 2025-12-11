# Freerouting Command Line Interface (CLI) Documentation

## Introduction

The Freerouting Command Line Interface (CLI) allows you to automate PCB routing tasks without using the graphical user interface (GUI). This is particularly useful for integrating Freerouting into scripts, build systems, or other software tools where automated routing is required.

This document provides detailed information on how to use Freerouting via the CLI, including available command-line options and how to adjust internal settings for advanced configurations.

## Usage

To run Freerouting from the command line, use the following syntax:

```bash
java -jar freerouting-2.0.0.jar [options]
```

Replace `freerouting-2.0.0.jar` with the actual filename of the Freerouting JAR file you are using.

## Command-Line Options

Below is a comprehensive list of command-line options available in Freerouting, organized by category.

### Input and Output Files

- **`-de [design input file]`**  
  Loads a Specctra design file (`.dsn`) at startup.

- **`-do [design output file]`**  
  Saves the routing results when the routing is finished. The output can be:
  - Specctra board (`.dsn`)
  - Specctra session file (`.ses`)
  - Eagle session script file (`.scr`)
  
  The output format is determined by the file extension provided.

- **`-di [design input directory]`**  
  Sets the default folder for the open design dialogs when using the GUI.

- **`-dr [design rules file]`**  
  Reads design rules from a previously saved `.rules` file.

- **`-drc [design rules check json file]`**
  Writes the design rules check report in KiCad JSON DRC schema format.

### Routing Parameters

- **`-mp [number of passes]`**  
  Sets the upper limit for the number of autorouter passes to perform. More passes may result in better optimization but will take longer.

- **`-mt [number of threads]`**  
  Sets the thread pool size for route optimization:
  - Default: One less than the number of logical processors on the system.
  - Set to `0` to disable route optimization.
  - Increasing the number may improve performance on multi-core systems.

- **`-oit [percentage]`**  
  Specifies the optimizer improvement threshold per pass:
  - Default: `0.1%`
  - The optimizer stops if the improvement falls below this threshold.
  - Setting `-oit 0` continues optimization until manually stopped or no further improvements are possible.

- **`-inc [net class names]`**  
  Lists net classes to ignore during autorouting:
  - Provide a comma-separated list (e.g., `-inc GND,VCC`).
  - The autorouter will not route nets belonging to these classes.

- **`-im`**  
  Enables saving of intermediate steps in a version-specific binary format:
  - Allows resuming interrupted optimizations from the last checkpoint.
  - Disabled by default.

- **`-random_seed [seed]`**
  Sets the random seed for the autorouter.
  - If a seed is provided, the autorouter will behave deterministically. This affects both the shuffling of items to route and the randomization in the maze search algorithm during rip-up.
  - If no seed is provided, the autorouter will use a different random seed for each run.

### Optimization Strategies

- **`-us [greedy | global | hybrid]`**  
  Sets the board updating strategy for route optimization:
  - `greedy` (default): Accepts any immediate improvement.
  - `global`: Only accepts changes that result in a global optimum.
  - `hybrid`: Combines both strategies; requires `-hr` to specify the ratio.

- **`-hr [m:n]`**  
  Specifies the hybrid ratio when using the `hybrid` update strategy:
  - Format: `#_global_optimal_passes:#_prioritized_passes` (e.g., `1:1`).
  - Only effective with `-us hybrid`.

- **`-is [sequential | random | prioritized]`**  
  Sets the item selection strategy for route optimization:
  - `sequential`: Processes items in order.
  - `random`: Processes items in a random order.
  - `prioritized` (default): Selects items based on calculated scores from previous rounds.

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

### Miscellaneous Options

- **`-dct [seconds]`**  
  Sets the dialog confirmation timeout:
  - Specifies the number of seconds before dialogs proceed with the default action.
  - Default is `20` seconds.

- **`-da`**  
  Disables the collection of anonymous analytics data.

- **`-dl`**  
  Disables logging.

- **`-ll [level]`**  
  Sets the console logging level:
  - Valid values:
    - `OFF` (0)
    - `FATAL` (1)
    - `ERROR` (2)
    - `WARN` (3)
    - `INFO` (4) (default)
    - `DEBUG` (5)
    - `TRACE` (6)
    - `ALL` (7)
  - Accepts both string names and numerical values.

- **`-help`**  
  Displays help information and exits.

## Adjusting Internal Settings

Freerouting allows you to fine-tune its internal settings beyond the standard command-line options. To modify these settings, use the double dash `--` prefix followed by the setting name and its value.

**Syntax:**

```bash
java -jar freerouting-2.0.0.jar --setting_name=value
```

**Examples:**

- **Disable the GUI:**

  ```bash
  java -jar freerouting-2.0.0.jar --gui.enabled=false
  ```

- **Adjust the via cost:**

  ```bash
  java -jar freerouting-2.0.0.jar --router.via_costs 150
  ```

These settings allow for granular control over the routing process, enabling you to optimize performance and outcomes according to your specific needs.

### Accessing the Full List of Settings

For a complete list of adjustable settings and detailed explanations, refer to the [Settings Documentation](/docs/settings.md).

## Examples

Below are some common usage examples to help you get started.

### Example 1: Basic Autorouting

Autoroute a design and save the results:

```bash
java -jar freerouting-2.0.0.jar -de MyBoard.dsn -do MyBoard.ses
```

- Loads `MyBoard.dsn`.
- Performs autorouting.
- Saves the routed design to `MyBoard.ses`.

### Example 2: Ignoring Specific Net Classes

Ignore the `GND` and `VCC` net classes during routing:

```bash
java -jar freerouting-2.0.0.jar -de MyBoard.dsn -do MyBoard.ses -inc GND,VCC
```

- Nets in the `GND` and `VCC` classes will not be routed.
- Useful when you plan to route these nets manually.

### Example 3: Limiting the Number of Passes and Threads

Limit the autorouter to 10 passes and use 4 threads:

```bash
java -jar freerouting-2.0.0.jar -de MyBoard.dsn -do MyBoard.ses -mp 10 -mt 4
```

- Sets a cap on routing time and resource usage.
- Adjust `-mt` based on your system's capabilities.

## Conclusion

By leveraging Freerouting's CLI, you can integrate advanced PCB routing into your automated workflows, scripts, or applications. The flexibility of command-line options and internal settings allows for customized routing solutions tailored to your project's requirements.

For further customization and advanced configurations, refer to the [Settings Documentation](/docs/settings.md) and other resources provided with Freerouting.