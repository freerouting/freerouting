# ---------------------------------------------------------------------------
# java_utils.py — Java detection, version checking, and JRE installation
# ---------------------------------------------------------------------------
# Everything related to finding, validating, and installing a Java
# runtime lives here.  The plugin needs Java 25+ to run Freerouting.
# This module implements a search order (system PATH → temp folder →
# Homebrew → JAVA_HOME) and can download a fresh JRE from Adoptium.
# ---------------------------------------------------------------------------

import json
import os
import platform
import re
import shutil
import subprocess
import textwrap
import urllib.error
import urllib.request
from pathlib import Path

from .config import (
    ADOPTIUM_API_URL,
    JAVA_MIN_MAJOR_VERSION,
    JRE_GLOB_PATTERN,
    JRE_TEMP_FOLDER,
    JRE_VERSION_REGEX,
    MAC_HOMEBREW_JAVA_PATH,
)
from .gui_helpers import wx_show_error, wx_show_warning


def detect_os_architecture():
    """Return normalized (os_name, architecture) strings.

    Used to select the correct JRE download from Adoptium and to
    locate platform-specific Java executable names.

    Returns:
        tuple: ``(os_name, architecture)`` where os_name is one of
        ``"windows"``, ``"linux"``, ``"mac"`` and architecture is
        ``"x64"`` or ``"x86"``.
    """
    os_name = platform.system().lower()
    architecture = platform.machine().lower()

    if architecture == "amd64":
        architecture = "x64"
    elif architecture == "i386":
        architecture = "x86"
    elif architecture == "x86_64":
        architecture = "x64"

    if os_name == "darwin":
        os_name = "mac"

    return os_name, architecture


def get_java_version(java_path):
    """Query a Java executable for its version string.

    Java prints version info to *stderr*, so we capture that and
    extract the first dotted-number token.

    Args:
        java_path: Path to the ``java`` executable.

    Returns:
        Version string like ``"25.0.1"`` or ``"0.0.0.0"`` on failure.
    """
    try:
        result = subprocess.run(
            [java_path, "-version"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        java_versions = [
            re.search(r"([0-9\._]+)", v).group(1).replace('"', "")
            for v in result.stderr.splitlines()
        ]
        for v in java_versions:
            if v.split(".")[0].isdigit():
                return v
    except (FileNotFoundError, subprocess.CalledProcessError) as e:
        print(f"Error getting Java version from {java_path}: {e}")
    except Exception as e:
        print(f"Unexpected error getting Java version: {e}")
    return "0.0.0.0"


def get_local_java_executable_path(os_name):
    """Search for a suitable Java executable on the local machine.

    Search order:
        1. System PATH (``shutil.which``)
        2. Previously downloaded JRE in the temp folder
        3. macOS Homebrew OpenJDK (macOS only)
        4. ``JAVA_HOME`` environment variable

    Args:
        os_name: Normalized OS name from ``detect_os_architecture()``.

    Returns:
        Absolute path to a Java executable, or ``""`` if none found.
    """
    # 1. System PATH
    java_exe = shutil.which("java")
    if java_exe:
        major = int(get_java_version(java_exe).split(".")[0])
        if major >= JAVA_MIN_MAJOR_VERSION:
            print(f"Found Java in system PATH ({java_exe}).")
            return java_exe

    # 2. Temp folder (previously downloaded)
    java_found_exes = sorted(
        [
            str(p)
            for p in JRE_TEMP_FOLDER.glob(JRE_GLOB_PATTERN)
            if p.is_file() and re.search(JRE_VERSION_REGEX, str(p))
        ],
        reverse=True,
        key=lambda p: re.search(JRE_VERSION_REGEX, p).groups()
        if re.search(JRE_VERSION_REGEX, p)
        else (),
    )
    if java_found_exes:
        print(f"Found a downloaded JRE ({java_found_exes[0]}).")
        return java_found_exes[0]

    # 3. macOS Homebrew
    if os_name == "mac":
        hb = Path(MAC_HOMEBREW_JAVA_PATH)
        if hb.is_file():
            major = int(get_java_version(str(hb)).split(".")[0])
            if major >= JAVA_MIN_MAJOR_VERSION:
                print(f"Found Homebrew Java ({hb}).")
                return str(hb)

    # 4. JAVA_HOME
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        exe = Path(java_home) / "bin" / "java"
        if os_name == "windows":
            exe = exe.with_suffix(".exe")
        if exe.is_file():
            major = int(get_java_version(str(exe)).split(".")[0])
            if major >= JAVA_MIN_MAJOR_VERSION:
                print(f"Found Java via JAVA_HOME ({exe}).")
                return str(exe)

    return ""


def check_latest_jre_version(os_name, architecture):
    """Query the Adoptium API for the latest JRE 25 release.

    Args:
        os_name: Normalized OS name.
        architecture: Normalized architecture string.

    Returns:
        ``(semver, download_url)`` or ``(None, None)`` on failure.
    """
    url = ADOPTIUM_API_URL.format(os=os_name, arch=architecture)
    try:
        req = urllib.request.Request(url, headers={"User-Agent": ""})
        with urllib.request.urlopen(req) as response:
            info = json.loads(response.read())[0]
        return info["version"]["semver"], info["binary"]["package"]["link"]
    except (urllib.error.URLError, json.JSONDecodeError, IndexError) as e:
        print(f"Could not retrieve latest JRE info from Adoptium API: {e}")
        return None, None


def download_with_progress_hook(count, block_size, total_size):
    """Simple download progress callback (currently silent)."""
    _ = count * block_size * 100 // total_size  # percent, unused


def install_java_jre_25():
    """Download and extract the latest JRE 25 from Adoptium.

    Returns:
        Path to the installed ``java`` executable, or ``""`` on failure.
    """
    os_name, arch = detect_os_architecture()
    print(f"Operating System: {os_name}, Architecture: {arch}")

    local_java = get_local_java_executable_path(os_name)
    jre_ver, jre_url = check_latest_jre_version(os_name, arch)

    if jre_ver is None or jre_url is None:
        print("Could not get latest JRE version info.")
        return local_java

    expected = JRE_TEMP_FOLDER / f"jdk-{jre_ver}-jre" / "bin" / "java"
    if os_name == "windows":
        expected = expected.with_suffix(".exe")

    if Path(local_java) == expected:
        print(f"Already have the latest JRE ({jre_ver}).")
        return local_java

    if local_java and int(get_java_version(local_java).split(".")[0]) >= JAVA_MIN_MAJOR_VERSION:
        print(f"Found suitable Java ({local_java}), skipping download.")
        return local_java

    JRE_TEMP_FOLDER.mkdir(parents=True, exist_ok=True)

    print(f"Downloading Java JRE from {jre_url}")
    try:
        file_name = urllib.request.urlretrieve(
            jre_url, reporthook=download_with_progress_hook
        )[0]
        print("\nDownload complete.")
    except urllib.error.URLError as e:
        print(f"\nFailed to download: {e}")
        wx_show_error(textwrap.dedent(f"""
            Failed to download Java JRE from:
            {jre_url}

            Please download and install Java JRE 25+ manually from
            https://adoptium.net/temurin/releases.
        """))
        return ""

    print("Extracting...")
    try:
        subprocess.run(
            ["tar", "-xf", file_name, "-C", str(JRE_TEMP_FOLDER)],
            check=True,
        )
        print("Extraction complete.")
    except (FileNotFoundError, subprocess.CalledProcessError) as e:
        print(f"Failed to extract: {e}")
        wx_show_error(textwrap.dedent(f"""
            Failed to extract the downloaded JRE:
            {Path(file_name).name}

            Please ensure you have a tar extractor installed.
        """))
        return ""
    finally:
        Path(file_name).unlink(missing_ok=True)

    result = get_local_java_executable_path(os_name)
    if Path(result).is_file():
        print(f"Java JRE installed at {result}")
    else:
        print(f"JRE installation path not found: {result}")
        wx_show_error("JRE extraction seemed to succeed but the executable was not found.")
        return ""

    return result