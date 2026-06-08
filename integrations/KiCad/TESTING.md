# Testing the KiCad Freerouting Plugin

## Overview

This document describes how to test the KiCad Freerouting plugin in an
automated fashion.  The testing approach follows the pattern established
by the KiCad community (see
[adamws.github.io](https://adamws.github.io/using-the-new-kicad-ipc-api-in-a-ci-environment/)).

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Test Runner (pytest)                 │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────┐    ┌─────────────────────────────┐    │
│  │  Unit Tests  │    │  Integration Tests          │    │
│  │  (no KiCad)  │    │  (requires KiCad 10+ + Xvfb)│    │
│  └──────────────┘    └─────────────────────────────┘    │
│                              │                          │
│                              ▼                          │
│                    ┌──────────────────┐                 │
│                    │  Xvfb (virtual   │                 │
│                    │  display :99)    │                 │
│                    └────────┬─────────┘                 │
│                             │                           │
│                             ▼                           │
│                    ┌──────────────────┐                 │
│                    │  pcbnew (KiCad   │                 │
│                    │  GUI process)    │                 │
│                    └────────┬─────────┘                 │
│                             │                           │
│                    IPC socket/pipe                      │
│                    (api.sock)                           │
│                             │                           │
│                             ▼                           │
│                    ┌──────────────────┐                 │
│                    │  kicad-python    │                 │
│                    │  (kipy client)   │                 │
│                    └──────────────────┘                 │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## Test Levels

### Level 1: Unit Tests (no KiCad required)

These tests verify the plugin's internal logic without needing KiCad:

- **Plugin structure**: All modules exist and compile
- **Config constants**: Required settings are present
- **IPC helpers**: Board serialization produces valid JSON
- **API client**: Can be instantiated, health check works
- **Java utils**: OS architecture detection, version parsing

```bash
# Run unit tests only
cd integrations/KiCad/kicad-freerouting
python -m pytest tests/ -v -m "not integration"
```

### Level 2: Integration Tests (KiCad 10+ required)

These tests verify the plugin works with a real KiCad instance:

1. **KiCad 10+ is installed** — `pcbnew` on PATH
2. **KiCad starts with IPC enabled** — via `kicad-cli` or config patching
3. **Test fixture loads** — a `.kicad_pcb` file is opened in pcbnew
4. **Plugin executes** — board is serialized, Freerouting API is called
5. **Results are verified** — output JSON has expected structure
6. **Pass/fail** — pytest exit code determines CI result

```bash
# Run integration tests
export RUN_INTEGRATION_TESTS=1
export KICAD_PCBNEW=pcbnew
python -m pytest tests/ -v --timeout=120
```

### Level 3: Full End-to-End (with Freerouting JAR)

The complete workflow including actual routing:

1. All of Level 2, plus:
2. Freerouting JAR is started as API server
3. Board JSON is uploaded to Freerouting
4. Routing job is started and polled
5. Result JSON is downloaded and applied to KiCad

## Prerequisites

### Linux (Ubuntu/Debian)

```bash
# Install KiCad 10+
sudo add-apt-repository ppa:kicad/kicad-10.0-releases
sudo apt-get update
sudo apt-get install kicad

# Install Xvfb (virtual display)
sudo apt-get install xvfb libegl1 libgl1-mesa-glx

# Install Python test dependencies
pip install pytest pytest-timeout kicad-python PyVirtualDisplay
```

### Windows

```powershell
# Install KiCad 10+ from https://www.kicad.org/download/
# Ensure pcbnew.exe is on PATH

# Install Python test dependencies
pip install pytest pytest-timeout kicad-python
# Note: PyVirtualDisplay / Xvfb not needed on Windows
```

### macOS

```bash
# Install KiCad 10+ from https://www.kicad.org/download/
brew install --cask kicad

# Install Python test dependencies
pip install pytest pytest-timeout kicad-python PyVirtualDisplay
```

## Running Tests

### Quick smoke test (no KiCad needed)

```bash
cd integrations/KiCad/kicad-freerouting
python -m pytest tests/test_plugin_ipc.py -v -k "TestPluginStructure or TestIpcHelpers or TestApiClient or TestJavaUtils"
```

### Full integration test (KiCad required)

```bash
cd integrations/KiCad/kicad-freerouting
RUN_INTEGRATION_TESTS=1 python -m pytest tests/ -v --timeout=120
```

### With custom KiCad path

```bash
KICAD_PCBNEW=/opt/kicad/10.0/bin/pcbnew \
RUN_INTEGRATION_TESTS=1 \
python -m pytest tests/ -v
```

## CI/CD

The GitHub Actions workflow (`.github/workflows/test-kicad-plugin.yml`)
runs on every push and PR:

1. **Unit tests** — fast, no KiCad needed
2. **Integration tests** — installs KiCad 10+ via PPA, runs Xvfb,
   executes the full test suite
3. **Structure check** — validates all modules compile

### Workflow steps:

```yaml
1. Checkout repository
2. Install Xvfb + OpenGL deps (Linux only)
3. Install KiCad 10+ (via apt PPA)
4. Install Python deps (pytest, kicad-python, PyVirtualDisplay)
5. Run: pytest tests/ -v --timeout=120
6. Upload test results as artifact
```

## How It Works

### KiCad IPC API

KiCad 9+ exposes an IPC API over a Unix domain socket (Linux/macOS) or
named pipe (Windows).  The `kicad-python` library (`kipy`) provides a
Python client for this API.

Key facts:
- **KiCad 9/10**: IPC requires a running GUI instance (pcbnew must be open)
- **KiCad 11+**: Headless mode via `kicad-cli api-server`
- **IPC must be enabled** in KiCad preferences (or config file)

### Virtual Display (Xvfb)

On headless CI servers, KiCad needs a display to run its GUI.  Xvfb
creates a virtual framebuffer:

```python
from pyvirtualdisplay.smartdisplay import SmartDisplay
display = SmartDisplay(backend="xvfb", size=(1024, 768))
display.start()
# Now pcbnew can run with DISPLAY=:99
```

### Test Fixture

A minimal `empty_board.kicad_pcb` fixture is provided with:
- 2 SMD resistors (R1, R2)
- 3 nets (Net-(U1-Pad1), Net-(U1-Pad2), GND)
- Board outline on Edge.Cuts layer

This is small enough to route quickly but exercises the full pipeline.

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `pcbnew: command not found` | Add KiCad bin dir to PATH |
| `Could not connect to KiCad IPC` | Ensure IPC is enabled in `kicad_common.json` |
| `Display not found` | Install Xvfb, check `DISPLAY` env var |
| `KiCad shows dialog on first run` | Pre-create `fp-lib-table` in config dir |
| `kicad-python not installed` | `pip install kicad-python` |
| `protobuf version mismatch` | `pip install protobuf==5.29.1` (match KiCad's version) |

## References

- [KiCad IPC API Documentation](https://dev-docs.kicad.org/en/apis-and-binding/ipc-api/)
- [Using KiCad IPC API in CI](https://adamws.github.io/using-the-new-kicad-ipc-api-in-a-ci-environment/)
- [kicad-python on PyPI](https://pypi.org/project/kicad-python/)
- [PyVirtualDisplay](https://github.com/ponty/pyvirtualdisplay)