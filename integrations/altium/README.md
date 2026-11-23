# Freerouting Altium Designer Integration

The Freerouting Altium Designer integration provides bidirectional, native auto-routing and Specctra DSN/SES exchange for Altium Designer (`.PcbDoc`) users on Windows (.NET 8 / x64).

---

## Architecture Overview

The integration is built in three modular layers:

1. **`Freerouting.Altium.Core`** (`net8.0-windows`):
   - Standalone C# library with zero dependencies on Altium SDK assemblies.
   - Intermediate board model (`BoardModel`) representing boundaries, cutouts, layer stackups, padstack shapes, components, and net connectivity.
   - Specctra DSN stream writer (`DsnStreamWriter`) using a pluggable scope emitter architecture (`IScopeEmitter`).
   - High-precision Specctra SES parser (`SesStreamParser`) with dynamic unit/resolution scaling (`mil`, `um`, `mm`) and in-flight collinear micro-segment snapping ($< 100\text{ internal units} = 0.01\text{ mil} = 254\text{ nm}$) to prevent zero-length track artifacts.

2. **`Freerouting.Altium.Extension`** (`net8.0-windows`, `x64`):
   - DXP Platform C# extension implementing Altium's native `CSharpPlugin.PluginFactory` and inheriting from `DXP.ServerModule`.
   - Altium Server installation script (`Freerouting.ins`) and PCB menu integration (`Freerouting.rcs`).
   - Direct native access to Altium's `PCB.GlobalVars.PCBServer` and active board (`RT_PCB.IPCB_Board`).
   - PCB Document extractor (`PcbDocExtractor`): Traverses `IPCB_Board`, `IPCB_BoardOutline`, `IPCB_Region` (cutouts), `IPCB_LayerStack`, rules (`IPCB_BoardOutlineClearanceConstraint`, etc.), components, and pads.
   - Signal copper pour management: Automatically shelves `IPCB_Polygon` signal pours pre-export and repours/rebuilds them post-routing.
   - Atomic back-annotator (`PcbDocBackAnnotator`): Safe pre-cleanup of unlocked tracks and vias on routed nets, atomic additions inside `PreProcess` / `PostProcess`, correct `SendMessageToRobots` registration, `ConnectivelyValidateNets()`, and viewport refresh.
   - Headless process runner (`FreeroutingProcessRunner`) and live WinForms progress dialog (`RoutingProgressDialog`) with real-time pass tracking and user cancellation.

3. **`Freerouting.Altium.Core.Tests`** (`net8.0-windows`):
   - Comprehensive unit and integration test harness validating coordinate scaling, rotated pad polygon emission, special identifier escaping, SES snapping, dynamic mock board extraction, and back-annotation.
   - Programmatic golden benchmark generator for [`fixtures/Altium-TwoLayerBenchmark.dsn`](../../fixtures/Altium-TwoLayerBenchmark.dsn).

---

## Features

- **1-Click Auto-Routing:** Accessible directly from Altium Designer's PCB menu: `Route > Freerouting Auto-Router > Auto-route Active Board...`.
- **Standalone DSN / SES I/O:** Export active layouts to standard Specctra DSN or import external SES routing session files.
- **Lossless Coordinates:** Strict integer arithmetic matching Altium's internal coordinate system ($1\text{ mil} = 10,000\text{ units}$, $1\text{ unit} = 2.54\text{ nm}$).
- **Rotated Pads:** Automatically converts rotated rectangular SMD pads into pre-rotated 4-vertex `(polygon ...)` scopes to preserve Specctra grammar compliance.
- **Negative Planes:** Automatically identifies internal dedicated power/ground planes (`eInternalPlane1..16`) and ignores them during obstacle polygon generation.
- **Live Progress Dialog:** Shows pass numbers, unrouted connections count, clearance violations, live log output, and provides an immediate "Cancel" button.

---

## Installation & Setup

### Prerequisites
- Windows 10 / 11 (x64)
- Altium Designer 20 through 26+ (DXP Platform)
- Java Runtime Environment (JRE/JDK 21 or newer)

---

### Automated 1-Click Installation (Recommended)

Choose any of the following convenient installation methods:

#### Option 1: Standalone Setup GUI (`Freerouting.Altium.Installer.exe`)
1. Run `integrations\altium\bin\Freerouting.Altium.Installer.exe`.
2. The installer automatically requests elevation via UAC and detects installed Altium Designer versions from the Windows Registry (`HKLM\SOFTWARE\Altium\Builds`).
3. Click **Install Extension**.
4. The installer automatically closes any running Altium Designer instance (to release locked DLL files) and installs all 4 payload components into `\System`.

#### Option 2: 1-Click Batch Script (`Install.cmd`)
1. In File Explorer, right-click `Install.cmd` and select **Run as administrator** (or double-click to accept the UAC prompt).
2. The script detects your Altium Designer installation, terminates `X2.exe` if running, and copies all required files automatically.

#### Option 3: PowerShell CLI (`install.ps1`)
Run from an elevated PowerShell console:
```powershell
powershell -ExecutionPolicy Bypass -File integrations/altium/install.ps1
```
For automated CI or headless deployments:
```powershell
powershell -ExecutionPolicy Bypass -File integrations/altium/install.ps1 -Silent -Force
```

---

### Uninstallation

To cleanly remove the Freerouting extension from Altium Designer:
- Run `Freerouting.Altium.Installer.exe` and click **Uninstall**, or
- Double-click `Uninstall.cmd`, or
- Run `powershell -ExecutionPolicy Bypass -File integrations/altium/install.ps1 -Uninstall`

---

### Manual Installation
If you prefer manual deployment:
1. Copy the following 4 files from `Freerouting.Altium.Extension\bin\x64\Release\` into your Altium Designer `\System` directory (e.g. `C:\Program Files\Altium\AD26\System\`):
   - `Freerouting.Altium.Extension.dll`
   - `Freerouting.Altium.Core.dll`
   - `Freerouting.ins`
   - `Freerouting.rcs`
2. Launch Altium Designer. The new menu `Freerouting Auto-Router` will appear under **Route > Freerouting Auto-Router**.

---

## Running the Automated Test Suite

To run all automated C# tests and regenerate the golden DSN benchmark fixture:

```powershell
powershell -ExecutionPolicy Bypass -File integrations/altium/run-tests.ps1
```

To run the end-to-end Java routing regression test in Freerouting:

```powershell
./gradlew.bat test --tests "AltiumRoutingTest"
```
