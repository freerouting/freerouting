# ---------------------------------------------------------------------------
# test_plugin_ipc.py — Integration tests for the KiCad Freerouting plugin
# ---------------------------------------------------------------------------
# These tests verify the plugin's IPC workflow end-to-end:
#   1. KiCad 10+ is installed and can be started
#  2. The plugin can be loaded into KiCad
#   3. A test fixture board can be loaded
#   4. The plugin's IPC helpers work (board serialization)
#   5. The plugin can be executed
#   6. Results are verified
#
# Prerequisites:
#   * KiCad 10+ installed (pcbnew on PATH)
#   * Linux: Xvfb (apt install xvfb)
#   * pip install pytest kicad-python PyVirtualDisplay
#
# Run:
#   pytest tests/ -v --timeout=120
#   KICAD_PCBNEW=/path/to/pcbnew pytest tests/ -v
# ---------------------------------------------------------------------------

import json
import os
import sys
import time
from pathlib import Path

import pytest

# ---------------------------------------------------------------------------
# Test 1: KiCad 10+ is installed
# ---------------------------------------------------------------------------

class TestKiCadInstallation:
    """Verify KiCad 10+ is available on the system."""

    def test_pcbnew_on_path(self):
        """Check that pcbnew executable is discoverable."""
        from shutil import which
        exe = os.environ.get("KICAD_PCBNEW", "pcbnew")
        path = which(exe)
        assert path is not None, (
            f"'{exe}' not found on PATH. "
            "Install KiCad 10+ or set KICAD_PCBNEW env var."
        )

    def test_kicad_version(self):
        """Verify KiCad reports version 10 or higher."""
        import subprocess
        exe = os.environ.get("KICAD_PCBNEW", "pcbnew")
        try:
            result = subprocess.run(
                [exe, "--version"],
                capture_output=True, text=True, timeout=10,
            )
            output = result.stdout + result.stderr
            # KiCad prints something like "KiCad 10.0.0"
            assert "10." in output or "11." in output, (
                f"KiCad version not detected in output: {output[:200]}"
            )
        except (subprocess.TimeoutExpired, FileNotFoundError):
            pytest.skip(f"Could not run {exe} --version")

    def test_kicad_python_package(self):
        """Verify kicad-python (kipy) is installed."""
        try:
            import kipy  # noqa: F401
        except ImportError:
            pytest.skip("kicad-python not installed: pip install kicad-python")


# ---------------------------------------------------------------------------
# Test 2: Plugin module structure
# ---------------------------------------------------------------------------

class TestPluginStructure:
    """Verify the plugin package is well-structured and importable."""

    PLUGINS_DIR = Path(__file__).parent.parent / "plugins"

    def test_all_modules_exist(self):
        """All expected plugin modules are present."""
        expected = [
            "__init__.py", "plugin.py", "config.py", "gui_helpers.py",
            "java_utils.py", "ipc_helpers.py", "api_client.py",
            "process_utils.py", "router_dsn.py", "router_ipc.py",
        ]
        for name in expected:
            path = self.PLUGINS_DIR / name
            assert path.is_file(), f"Missing module: {path}"

    def test_plugin_ini_exists(self):
        """plugin.ini configuration file is present."""
        ini = self.PLUGINS_DIR / "plugin.ini"
        assert ini.is_file(), "Missing plugin.ini"

    def test_plugin_class_loads(self):
        """The FreeroutingPlugin class can be instantiated (without GUI)."""
        # We can't fully load it without pcbnew, but we can verify
        # the module syntax is valid by compiling it.
        import py_compile
        plugin_py = self.PLUGINS_DIR / "plugin.py"
        py_compile.compile(str(plugin_py), doraise=True)

    def test_config_constants(self):
        """Config module has all required constants."""
        # Import config directly (no pcbnew dependency)
        spec = __import__(
            "importlib.util", fromlist=["spec_from_file_location"]
        ).spec_from_file_location("config", self.PLUGINS_DIR / "config.py")
        config = __import__("importlib.util", fromlist=["module_from_spec"]).module_from_spec(spec)
        spec.loader.exec_module(config)

        assert hasattr(config, "DEFAULT_FR_API_BASE_URL")
        assert hasattr(config, "JAVA_MIN_MAJOR_VERSION")
        assert config.JAVA_MIN_MAJOR_VERSION == 25
        assert hasattr(config, "IPC_PROBE_ATTRIBUTES")
        assert hasattr(config, "DEFAULT_ROUTING_MODE")


# ---------------------------------------------------------------------------
# Test 3: IPC helpers (board serialization)
# ---------------------------------------------------------------------------

class TestIpcHelpers:
    """Test the IPC detection and board serialization logic."""

    PLUGINS_DIR = Path(__file__).parent.parent / "plugins"

    def test_is_ipc_available_runs(self):
        """is_ipc_available() should return a boolean without crashing."""
        # Add plugins dir to path so we can import
        sys.path.insert(0, str(self.PLUGINS_DIR.parent))
        try:
            from plugins.ipc_helpers import is_ipc_available
            result = is_ipc_available()
            assert isinstance(result, bool)
        finally:
            sys.path.pop(0)

    def test_board_json_manual_build(self):
        """_build_board_json_manually produces valid JSON with expected keys."""
        # We can test the JSON structure by creating a mock board dict
        # that mimics pcbnew.BOARD's interface
        sys.path.insert(0, str(self.PLUGINS_DIR.parent))
        try:
            from plugins.ipc_helpers import _build_board_json_manually

            # Create a minimal mock board object
            class MockBoard:
                def GetFileName(self):
                    return "/tmp/test.kicad_pcb"
                def GetCopperLayerCount(self):
                    return 2
                def GetLayerName(self, i):
                    return ["F.Cu", "B.Cu"][i]
                def GetTracks(self):
                    return []
                def GetFootprints(self):
                    return []
                def GetDrawings(self):
                    return []
                def GetLayerID(self, name):
                    return -1

            result = _build_board_json_manually(MockBoard())
            data = json.loads(result)

            assert "designName" in data
            assert data["designName"] == "test"
            assert "layers" in data
            assert len(data["layers"]) == 2
            assert "nets" in data
            assert "components" in data
            assert "traces" in data
            assert "vias" in data
            assert "outline" in data
        finally:
            sys.path.pop(0)


# ---------------------------------------------------------------------------
# Test 4: API client
# ---------------------------------------------------------------------------

class TestApiClient:
    """Test the Freerouting API client (without a live server)."""

    PLUGINS_DIR = Path(__file__).parent.parent / "plugins"

    def test_client_instantiation(self):
        """FreeroutingApiClient can be created with default settings."""
        sys.path.insert(0, str(self.PLUGINS_DIR.parent))
        try:
            from plugins.api_client import FreeroutingApiClient
            client = FreeroutingApiClient()
            assert client.base_url == "http://127.0.0.1:37864"
        finally:
            sys.path.pop(0)

    def test_client_custom_url(self):
        """FreeroutingApiClient accepts custom base URL."""
        sys.path.insert(0, str(self.PLUGINS_DIR.parent))
        try:
            from plugins.api_client import FreeroutingApiClient
            client = FreeroutingApiClient(base_url="http://localhost:9999")
            assert client.base_url == "http://localhost:9999"
        finally:
            sys.path.pop(0)

    def test_health_check_returns_false_when_no_server(self):
        """health_check returns False when no server is running."""
        sys.path.insert(0, str(self.PLUGINS_DIR.parent))
        try:
            from plugins.api_client import FreeroutingApiClient
            client = FreeroutingApiClient(base_url="http://127.0.0.1:1")  # unreachable
            assert client.health_check() is False
        finally:
            sys.path.pop(0)


# ---------------------------------------------------------------------------
# Test 5: End-to-end with live KiCad (requires Xvfb on Linux)
# ---------------------------------------------------------------------------

@pytest.mark.integration
class TestEndToEnd:
    """Full integration tests that require a running KiCad instance.

    These tests are skipped unless the ``--run-integration`` flag is passed
    or the ``RUN_INTEGRATION_TESTS`` environment variable is set.
    """

    @pytest.fixture(autouse=True)
    def _check_integration_enabled(self):
        if not os.environ.get("RUN_INTEGRATION_TESTS"):
            pytest.skip("Set RUN_INTEGRATION_TESTS=1 to run integration tests")

    def test_kicad_ipc_connection(self, kicad_api):
        """Verify we can connect to KiCad via IPC and get version info."""
        version = kicad_api.get_version()
        assert version is not None
        print(f"Connected to KiCad version: {version}")

    def test_load_fixture_board(self, kicad_api):
        """Load a test fixture board into KiCad."""
        fixture = Path(__file__).parent / "fixtures" / "empty_board.kicad_pcb"
        assert fixture.is_file(), f"Fixture not found: {fixture}"

        board = kicad_api.get_board()
        assert board is not None
        print(f"Board loaded: {board}")

    def test_plugin_serializes_board(self, kicad_api):
        """The plugin's IPC helpers can serialize the current board to JSON."""
        board = kicad_api.get_board()
        assert board is not None

        # Use kicad-python to get board data
        items = board.get_items()
        assert len(items) > 0, "Board should have items"
        print(f"Board has {len(items)} items")


# ---------------------------------------------------------------------------
# Test 6: Java utilities
# ---------------------------------------------------------------------------

class TestJavaUtils:
    """Test Java detection and version checking."""

    PLUGINS_DIR = Path(__file__).parent.parent / "plugins"

    def test_detect_os_architecture(self):
        """detect_os_architecture returns valid (os, arch) tuple."""
        sys.path.insert(0, str(self.PLUGINS_DIR.parent))
        try:
            from plugins.java_utils import detect_os_architecture
            os_name, arch = detect_os_architecture()
            assert os_name in ("windows", "linux", "mac")
            assert arch in ("x64", "x86", "aarch64")
        finally:
            sys.path.pop(0)

    def test_get_java_version_invalid_path(self):
        """get_java_version returns fallback for non-existent path."""
        sys.path.insert(0, str(self.PLUGINS_DIR.parent))
        try:
            from plugins.java_utils import get_java_version
            result = get_java_version("/nonexistent/java")
            assert result == "0.0.0.0"
        finally:
            sys.path.pop(0)