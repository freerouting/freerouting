# ---------------------------------------------------------------------------
# config.py — Configuration constants for the KiCad Freerouting plugin
# ---------------------------------------------------------------------------
# This module centralizes all configurable values: API server defaults,
# Java version requirements, JRE download/probe settings, and IPC detection
# parameters.  Keeping them here makes it easy to tune behaviour without
# touching business-logic code.
# ---------------------------------------------------------------------------

import tempfile
from pathlib import Path

# ------------------------------------------------------------------
# Freerouting API server settings (used when running in IPC/API mode)
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
JRE_TEMP_FOLDER = Path(tempfile.gettempdir()) / "freerouting" / "jre"

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
# KiCad IPC API detection
# ------------------------------------------------------------------
# Attribute names we look for on the ``pcbnew`` module to determine
# whether the running KiCad instance exposes the IPC API.
IPC_PROBE_ATTRIBUTES = (
    "ipc",
    "IpcApi",
    "GetIpcApi",
    "board_to_json",
    "GetBoardAsJson",
)

# Method names tried when probing for a JSON-export capability.
IPC_JSON_EXPORT_METHODS = (
    "GetBoardAsJson",
    "board_to_json",
    "ExportBoardJson",
)

# Minimum KiCad major version that supports IPC.
IPC_MIN_KICAD_MAJOR = 9

# ------------------------------------------------------------------
# API client defaults
# ------------------------------------------------------------------
# Timeout (seconds) for individual HTTP requests to the Freerouting API.
API_REQUEST_TIMEOUT = 30

# How long (seconds) to wait for the Freerouting API server to start.
API_SERVER_STARTUP_TIMEOUT = 30

# Interval (seconds) between job-status polls while waiting for completion.
API_POLL_INTERVAL = 3.0

# Maximum time (seconds) to wait for a job before giving up.
API_JOB_TIMEOUT = 600

# ------------------------------------------------------------------
# Routing modes
# ------------------------------------------------------------------
# "IPC" — use KiCad IPC + Freerouting REST API (preferred, requires KiCad 9+).
# "DSN" — legacy Specctra DSN file exchange (works with any KiCad version).
DEFAULT_ROUTING_MODE = "IPC"