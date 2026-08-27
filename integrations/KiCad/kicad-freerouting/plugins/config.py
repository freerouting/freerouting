# ---------------------------------------------------------------------------
# config.py — Configuration constants for the KiCad Freerouting plugin
# ---------------------------------------------------------------------------
# This module centralizes all configurable values: API server defaults,
# Java version requirements, JRE download/probe settings, and IPC detection
# parameters.  Keeping them here makes it easy to tune behaviour without
# touching business-logic code.
# ---------------------------------------------------------------------------

import os
import platform
import tempfile
from pathlib import Path


def get_cache_dir():
    """Return the platform-specific cache directory for Freerouting."""
    system = platform.system().lower()
    if "windows" in system:
        local_app_data = os.environ.get("LOCALAPPDATA")
        if local_app_data:
            return Path(local_app_data) / "freerouting" / "cache"
        return Path.home() / "AppData" / "Local" / "freerouting" / "cache"
    elif "darwin" in system:
        return Path.home() / "Library" / "Caches" / "freerouting"
    else:
        xdg_cache = os.environ.get("XDG_CACHE_HOME")
        if xdg_cache:
            return Path(xdg_cache) / "freerouting"
        return Path.home() / ".cache" / "freerouting"


def get_log_dir():
    """Return the platform-specific log directory for Freerouting."""
    system = platform.system().lower()
    if "windows" in system:
        local_app_data = os.environ.get("LOCALAPPDATA")
        if local_app_data:
            return Path(local_app_data) / "freerouting" / "logs" / "kicad"
        return Path.home() / "AppData" / "Local" / "freerouting" / "logs" / "kicad"
    elif "darwin" in system:
        return Path.home() / "Library" / "Logs" / "freerouting" / "kicad"
    else:
        xdg_state = os.environ.get("XDG_STATE_HOME")
        if xdg_state:
            return Path(xdg_state) / "freerouting" / "logs" / "kicad"
        return Path.home() / ".local" / "state" / "freerouting" / "logs" / "kicad"


# ------------------------------------------------------------------
# Freerouting API server settings (used when running in JSON/API mode)
# ------------------------------------------------------------------
# The plugin starts Freerouting as a local headless API server.  These
# values control where it binds and how the plugin connects to it.
DEFAULT_FR_API_HOST = "127.0.0.1"
DEFAULT_FR_API_PORT = 37864
DEFAULT_FR_API_BASE_URL = f"http://{DEFAULT_FR_API_HOST}:{DEFAULT_FR_API_PORT}"

# ------------------------------------------------------------------
# Java / JRE settings
# ------------------------------------------------------------------
# Minimum major version of Java required to run Freerouting.
JAVA_MIN_MAJOR_VERSION = 25

# Folder where downloaded JREs are cached between sessions.
JRE_CACHE_FOLDER = get_cache_dir() / "jre"
JRE_TEMP_FOLDER = JRE_CACHE_FOLDER

# Glob pattern used to discover previously-downloaded JRE 25 builds.
JRE_GLOB_PATTERN = "jdk-25.*.*+*-jre/bin/java*"

# Regex to extract sortable version components from a JRE directory name.
JRE_VERSION_REGEX = r"jdk-25\.(\d+)\.(\d+)(\.\d+)?\+(\d+)-jre"

# Adoptium API endpoint for fetching the latest JRE 25 download URL.
ADOPTIUM_API_URL = (
    "https://api.adoptium.net/v3/assets/latest/25/hotspot"
    "?image_type=jre&os={os}&architecture={arch}"
)

# macOS Homebrew OpenJDK path (checked as a last resort).
MAC_HOMEBREW_JAVA_PATH = "/opt/homebrew/opt/openjdk/bin/java"

# ------------------------------------------------------------------
# JSON/API mode — board serialization prerequisites
# ------------------------------------------------------------------
# Attribute names we look for on the ``pcbnew`` module when probing for
# native JSON export helpers (external KiCad IPC clients only).
JSON_API_PROBE_ATTRIBUTES = (
    "ipc",
    "IpcApi",
    "GetIpcApi",
    "board_to_json",
    "GetBoardAsJson",
)

# Method names tried when probing for a JSON-export capability.
JSON_API_EXPORT_METHODS = (
    "GetBoardAsJson",
    "board_to_json",
    "ExportBoardJson",
)

# Minimum KiCad major version for the JSON/API bridge (SWIG walk + REST).
JSON_API_MIN_KICAD_MAJOR = 9

# ------------------------------------------------------------------
# API client defaults
# ------------------------------------------------------------------
# Timeout (seconds) for individual HTTP requests to the Freerouting API.
API_REQUEST_TIMEOUT = 30

# How long (seconds) to wait for the Freerouting API server to start.
API_SERVER_STARTUP_TIMEOUT = 30

# Interval (seconds) between job-status polls while waiting for completion.
API_POLL_INTERVAL = 1.0

# Maximum time (seconds) to wait for a job before giving up.
API_JOB_TIMEOUT = 600

# ------------------------------------------------------------------
# Debug output
# ------------------------------------------------------------------
# When ``True``, the plugin saves the serialized board JSON and the
# routing result JSON to the temp folder for debugging purposes.
SAVE_DEBUG_JSON = True

# Folder and filenames for debug JSON output.
LOG_DIR = get_log_dir()
DEBUG_JSON_DIR = LOG_DIR
DEBUG_INPUT_JSON_FILENAME = "freerouting_input_board.json"
DEBUG_OUTPUT_JSON_FILENAME = "freerouting_output_board.json"

# ------------------------------------------------------------------
# Routing modes
# ------------------------------------------------------------------
# "DSN" — legacy Specctra DSN file exchange (default; works with all KiCad versions).
# "JSON" — experimental live JSON/API bridge via SWIG serialization + localhost REST
#          (requires KiCad 9+).  Not KiCad's official protobuf IPC API.
ROUTING_MODE_DSN = "DSN"
ROUTING_MODE_JSON = "JSON"
DEFAULT_ROUTING_MODE = ROUTING_MODE_DSN

# Legacy alias — "IPC" was the pre-v2.3 name for JSON/API mode.
_ROUTING_MODE_ALIASES = {"IPC": ROUTING_MODE_JSON}


def normalize_routing_mode(mode):
    """Return a canonical routing mode string."""
    if mode is None:
        return DEFAULT_ROUTING_MODE
    return _ROUTING_MODE_ALIASES.get(mode, mode)