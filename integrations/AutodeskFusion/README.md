# Autodesk Fusion Integration for Freerouting

This directory contains the official integration assets for **Autodesk Fusion Electronics** (replacing standalone Autodesk EAGLE).

## Installation & Download

Download the latest version of the plugin package:
* **[`freerouting_fusion_plugin.zip`](https://github.com/freerouting/freerouting/raw/master/integrations/AutodeskFusion/freerouting_fusion_plugin.zip)**

Extract the contents of the zip archive (including `freerouting_fusion_plugin.ulp` and the `include/` directory) into your Autodesk Fusion ULP directory or any folder on your machine.

## Workflow Overview

The integration follows a seamless two-step workflow between Autodesk Fusion Electronics and Freerouting:

```text
Autodesk Fusion PCB Layout  ---> [ Run freerouting_fusion_plugin.ulp ] ---> <board>.dsn
                                                                          |
                                                                          v
                                                                 Freerouting Autorouter
                                                                          |
                                                                          v
Autodesk Fusion PCB Layout  <--- [ Run Script: <board>.scr ]   <--- <board>.scr
```

---

## Step-by-Step Guide

### Step 1: Export DSN from Autodesk Fusion

1. Open your PCB document in **Autodesk Fusion Electronics**.
2. In the top toolbar, navigate to the **Automate** tab and click **Run ULP** (or type `run` in the command line at the bottom).
3. Select `freerouting_fusion_plugin.ulp`.
4. Click **Export DSN** and choose where to save the generated `<board>.dsn` file (defaults to the board's directory).

### Step 2: Autoroute in Freerouting

1. Open **Freerouting** (via GUI or CLI).
2. Open `<board>.dsn`.
3. Run the autorouter.
4. Save the result as an Autodesk Fusion script:
   - **GUI:** Click **File > Save as...** and choose **Autodesk Fusion Script (*.scr)**.
   - **CLI:** Run `freerouting -de <board>.dsn -do <board>.scr`.

### Step 3: Import Routed Script into Autodesk Fusion

1. Return to your PCB layout in **Autodesk Fusion Electronics**.
2. In the **Automate** tab, click **Run Script** (or type `script` in the command line).
3. Select `<board>.scr`.
4. Fusion automatically clears unrouted traces, places the newly routed tracks and vias, and recalculates the ratsnest.

## Guided & Automated Workflow

The ULP provides an integrated bridge dialog with automatic environment detection and 1-click execution:

1. **Step 1 — Java Runtime Detection:** Auto-probes your system for Java / JRE 25.
2. **Step 2 — Freerouting JAR Locator:** Auto-locates the Freerouting executable JAR (or lets you browse and saves the path for future sessions).
3. **1-Click Auto-Route Button:** Exports the DSN, runs the autorouter, and automatically imports the routed script (`<board>.scr`) back into Autodesk Fusion without manual intervention.
4. **Manual Step Fallbacks:** Independent buttons for `Step 3: Export DSN`, `Step 4: Run Router`, and `Step 5: Import Script`.

---

## Detailed Logging

Logs are automatically written to:
- **Windows:** `%LOCALAPPDATA%\freerouting\logs\fusion\fusion2freerouting.log`
- The ULP dialog features an **Open Log Folder** button to quickly open the log directory in Windows Explorer.

---

## Features & Fixes in `freerouting_fusion_plugin.ulp`

- **1-Click Automation:** End-to-end export, route, and auto-import via Fusion's command queue (`exit("SCRIPT ...")`).
- **Resilient Fallback Design Rules:** Gracefully handles in-memory, unsaved, or cloud-hosted boards without failing on `fileread`.
- **Modern Fusion API Support:** Uses `polyShapes` and `polyPours` with `.contours()` instead of deprecated `.polygons()` and `.wires()`.
- **Multi-Layer Support:** Seamlessly supports multi-layer designs (up to 999 layers) including Fusion's internal layer IDs (e.g. 303, 304).
- **Ratsnest Filter:** Skips layer 19 (Unrouted airwires) during copper export.
- **Multi-Island Pours:** Correctly structures and closes DSN syntax for disjoint copper pour islands.
