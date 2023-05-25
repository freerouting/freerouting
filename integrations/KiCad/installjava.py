import glob
import json
import os
import platform
import re
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request


def detect_os_architecture():
    os_name = platform.system().lower()
    architecture = platform.machine().lower()

    # Windows 64-bit
    if (architecture == "amd64"):
        architecture = "x64"

    # Windows 32-bit
    if (architecture == "i386"):
        architecture = "x86"

    # Linux 64-bit
    if (architecture == "x86_64"):
        architecture = "x64"

    # macOS 64-bit
    if (os_name == "darwin"):
        os_name = "mac"

    return os_name, architecture


def download_progress_hook(count, block_size, total_size):
    percent = count * block_size * 100 // total_size
    sys.stdout.write(f"\rDownloading: {percent}%")
    sys.stdout.flush()


def download_with_progress_bar(url):
    # Return temp filename
    return urllib.request.urlretrieve(url, reporthook=download_progress_hook)[0]


def check_latest_jre_version(os_name, architecture):
    latest_jre_info_request = urllib.request.Request(
        # Docs at https://api.adoptium.net/q/swagger-ui/
        f"https://api.adoptium.net/v3/assets/latest/17/hotspot?image_type=jre&os={os_name}&architecture={architecture}",
        headers={"User-Agent": ""}  # The server rejects requests with the default UA
    )
    jre_version_info = json.loads(urllib.request.urlopen(latest_jre_info_request).read())[0]
    latest_jre_version = jre_version_info["version"]["semver"]
    jre_url = jre_version_info['binary']["package"]["link"]
    return latest_jre_version, jre_url


def install_java_jre_17():
    # Get platform information and the appropriate URL
    os_name, architecture = detect_os_architecture()
    print(f"Operating System: {os_name}")
    print(f"Architecture: {architecture}")

    try:
        jre_version, jre_url = check_latest_jre_version(os_name, architecture)
    except Exception:
        print("Couldn't connect to the server")
        # Find all matching JRE 17
        jre_version = "17.*.*+*"
        jre_url = None

    jre_folder = f"jdk-{jre_version}-jre"
    java_exe_path = os.path.join(tempfile.gettempdir(), jre_folder, "bin", "java")
    # java_exe_path = os.path.abspath(java_exe_path)
    if os_name == "windows":
        java_exe_path += ".exe"

    # Don't do anything if we already have the necessary Java executable
    java_found_exes = sorted(
        glob.glob(java_exe_path),
        # Find the latest JRE
        reverse=True,
        key=lambda p: re.search(r"jdk-17\.(\d+)\.(\d+)\+(\d+)-jre", p).groups()
    )
    if java_found_exes:
        print(f"You already have a downloaded JRE, we are going to use that.")
        return java_found_exes[0]
    if jre_url is None:
        raise FileNotFoundError("Couldn't find a downloaded JRE")

    print("Downloading Java JRE from " + jre_url)
    file_name = download_with_progress_bar(jre_url)
    print()

    # Unzip the downloaded file
    print("Extracting the downloaded file...")
    unzip_command = f"tar -xf {file_name} -C {tempfile.gettempdir()}"
    os.system(unzip_command)

    # Remove the downloaded zip file
    os.remove(file_name)

    return java_exe_path

    # Verify the installation
    #java_version_command = f"{java_exe_path} -version"
    #result = subprocess.check_output(java_version_command, shell=True, stderr=subprocess.STDOUT)
    #print("Installed Java version:", result)


if __name__ == "__main__":
    if (os.name != 'posix') or (os.geteuid() == 0):  # Check if script is running as administrator/root
        print(install_java_jre_17())
    else:
        print("This script needs to be run as administrator to install Java JRE 17.")
