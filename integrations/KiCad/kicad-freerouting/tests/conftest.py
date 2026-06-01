# ---------------------------------------------------------------------------
# conftest.py — Pytest fixtures for KiCad plugin integration tests
# ---------------------------------------------------------------------------
# These fixtures handle the lifecycle of a KiCad process running inside a
# virtual display (Xvfb on Linux).  They are used by test_*.py files in
# this directory.
#
# Prerequisites:
#   * KiCad 10+ installed and ``pcbnew`` on PATH (or KICAD_PCBNEW set)
#   * Linux: Xvfb installed (``apt install xvfb``)
#   * Python packages: pytest, kicad-python, PyVirtualDisplay
#
# Usage:
#   pytest integrations/KiCad/kicad-freerouting/tests/ -v
# ---------------------------------------------------------------------------

import json
import os
import subprocess
import sys
import time
from pathlib import Path

import pytest

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

KICAD_PCBNEW = os.environ.get("KICAD_PCBNEW", "pcbnew")
KICAD_VERSION = os.environ.get("KICAD_VERSION", "10.0")
FIXTURES_DIR = Path(__file__).parent / "fixtures"
PLUGINS_DIR = Path(__file__).parent.parent / "plugins"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _find_kicad_config_dir() -> Path:
    """Return the KiCad user config directory for the active version."""
    if sys.platform == "win32":
        base = Path(os.environ.get("APPDATA", "")) / "KiCad" / KICAD_VERSION
    elif sys.platform == "darwin":
        base = Path.home() / "Library" / "Preferences" / "KiCad" / KICAD_VERSION
    else:
        base = Path.home() / ".config" / "kicad" / KICAD_VERSION
    return base


def _enable_ipc_api():
    """Ensure the IPC API server is enabled in KiCad's config.

    Creates a minimal ``kicad_common.json`` if one doesn't exist, or
    patches the existing file to set ``api.enable_server = true``.
    """
    config_dir = _find_kicad_config_dir()
    settings_file = config_dir / "kicad_common.json"
    config_dir.mkdir(parents=True, exist_ok=True)

    if not settings_file.is_file():
        settings = {"api": {"enable_server": True, "interpreter_path": ""}}
        with open(settings_file, "w") as f:
            json.dump(settings, f, indent=2)
    else:
        with open(settings_file, "r") as f:
            settings = json.load(f)
        if not settings.get("api", {}).get("enable_server", False):
            settings.setdefault("api", {})["enable_server"] = True
            with open(settings_file, "w") as f:
                json.dump(settings, f, indent=2)


def _suppress_first_run_dialogs():
    """Create mock config files to prevent popups on first run."""
    config_dir = _find_kicad_config_dir()
    config_dir.mkdir(parents=True, exist_ok=True)

    # Mock fp-lib-table to prevent library table dialog
    fp_lib_table = config_dir / "fp-lib-table"
    if not fp_lib_table.is_file():
        fp_lib_table.write_text("(fp_lib_table)\n")


# ---------------------------------------------------------------------------
# Pytest fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def kicad_process():
    """Start pcbnew inside a virtual display for the entire test session.

    Yields the subprocess handle.  The process is killed during teardown.
    On Windows, no virtual display is needed.
    """
    _enable_ipc_api()
    _suppress_first_run_dialogs()

    env = os.environ.copy()

    if sys.platform == "linux":
        # Start Xvfb on a free display number
        from pyvirtualdisplay.smartdisplay import SmartDisplay
        display = SmartDisplay(backend="xvfb", size=(1024, 768))
        display.start()
        env["DISPLAY"] = f":{display.display}"
    else:
        display = None

    proc = subprocess.Popen(
        [KICAD_PCBNEW],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    # Wait for KiCad to start and the IPC socket to appear
    time.sleep(5)

    yield proc

    proc.kill()
    proc.wait(timeout=10)
    if display is not None:
        display.stop()


@pytest.fixture(scope="session")
def kicad_api(kicad_process):
    """Return a connected ``kicad-python`` KiCad client instance.

    Requires the ``kicad-python`` package (``pip install kicad-python``).
    """
    try:
        import kipy
    except ImportError:
        pytest.skip("kicad-python not installed: pip install kicad-python")

    kicad = kipy.KiCad()

    # Wait up to 10 s for the IPC socket
    deadline = time.time() + 10
    while time.time() < deadline:
        try:
            kicad.ping()
            break
        except Exception:
            time.sleep(0.5)
    else:
        pytest.fail("Could not connect to KiCad IPC API within 10 seconds")

    return kicad


@pytest.fixture
def test_board(kicad_api, tmp_path):
    """Load a test fixture board into KiCad and return the board object.

    The fixture file is looked up in ``tests/fixtures/``.  After the test
    the board is closed.
    """
    # This is a placeholder — actual board loading depends on the test
    yield None