import os
import platform
import sys
import subprocess
import urllib.request
import urllib.parse

jre_version = "17.0.7+7"

def detect_os_architecture():
    os_name = platform.system().lower()
    architecture = platform.machine().lower()
              
    # Windows 64-bit
    if (architecture == "amd64"):
        architecture = "x64"

    # Windows 32-bit
    if (architecture == "i386"):
        architecture = "x86-32"

    # Linux 64-bit
    if (architecture == "x86_64"):
        architecture = "x64"
        
    # macOS 64-bit
    if (os_name == "darwin"):
        os_name = "mac"        

    return os_name, architecture

def download_progress_hook(count, block_size, total_size):
    percent = int(count * block_size * 100 / total_size)
    sys.stdout.write(f"\rDownloading: {percent}%")
    sys.stdout.flush()

def download_with_progress_bar(url, output_path):
    urllib.request.urlretrieve(url, output_path, reporthook=download_progress_hook)


def install_java_jre_17():
    # Add the extracted JRE to the system's PATH variable
    jre_folder = "jdk-"+jre_version+"-jre"  # Change this to the correct extracted folder name
    jre_path = os.path.abspath(jre_folder)
    java_exe_path = os.path.join(jre_path, "bin", "java.exe")
    
    if (os.path.exists(java_exe_path)):
        print(f"You already have a downloaded JRE, we are going to use that.")
        return java_exe_path

    # Get platform information and the appropriate URL
    os_name, architecture = detect_os_architecture()
    print(f"Operating System: {os_name}")
    print(f"Architecture: {architecture}")
    
    jre_url = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-"+jre_version+"/OpenJDK17U-jre_"+architecture+"_"+os_name+"_hotspot_"+jre_version.replace("+", "_")
    
    if (os_name == "windows"):
        # Download JRE 17 for Windows
        jre_url = jre_url + ".zip"
        file_name = "jre_17.zip"
        
    if (os_name == "linux"):
        # Download JRE 17 for Linux
        jre_url = jre_url + ".tag.gz"
        file_name = "jre_17.tar.gz"
    
    if (os_name == "mac"):
        # Download JRE 17 for Mac
        jre_url = jre_url + ".tag.gz"        
        file_name = "jre_17.tar.gz"

    if os.path.exists(file_name):
        print("We already have the Java JRE archive.")
    else:
        jre_url = urllib.parse.quote(jre_url, safe=":/?=")
        print("Downloading Java JRE from " + jre_url)
        download_with_progress_bar(jre_url, file_name)
        print()

    # Unzip the downloaded file
    print("Extracting the downloaded file...")
    unzip_command = f"tar -xf {file_name}"
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
