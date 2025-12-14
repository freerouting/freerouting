import glob
import json
import os
import shutil
import platform
import sys
import wx
import wx.aui
import time
import pcbnew
import textwrap
import threading
import subprocess
import configparser
import re
import urllib.request
import urllib.parse
import tempfile
from pathlib import Path
import urllib.error # Import specific exception for network errors
import json # Import json for specific exception

freerouting_jre_temp_folder = Path(tempfile.gettempdir()) / "freerouting" / "jre"

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

    
def check_latest_jre_version(os_name, architecture):
    try:
        latest_jre_info_request = urllib.request.Request(
            # Docs at https://api.adoptium.net/q/swagger-ui/
            f"https://api.adoptium.net/v3/assets/latest/25/hotspot?image_type=jre&os={os_name}&architecture={architecture}",
            headers={"User-Agent": ""}  # The server rejects requests with the default UA
        )
        with urllib.request.urlopen(latest_jre_info_request) as response:
            jre_version_info = json.loads(response.read())[0]
        latest_jre_version = jre_version_info["version"]["semver"]
        jre_url = jre_version_info['binary']["package"]["link"]
        return latest_jre_version, jre_url
    except (urllib.error.URLError, json.JSONDecodeError, IndexError) as e:
        print(f"Could not retrieve latest JRE info from Adoptium API: {e}")
        return None, None
    

def get_local_java_executable_path(os_name):
    # 1. Check standard system PATH first
    java_exe = shutil.which("java")
    if java_exe:
        javaVersion = get_java_version(java_exe)
        javaMajorVersion = int(javaVersion.split(".")[0])
        if javaMajorVersion >= 25:
            print(f"Found Java in system PATH ({java_exe}), using that.")
            return java_exe

    # 2. Find the latest Java JRE 21 in the temp folder (that we installed earlier)
    java_found_exes = sorted(
        [str(p) for p in freerouting_jre_temp_folder.glob("jdk-25.*.*+*-jre/bin/java*") if p.is_file() and re.search(r"jdk-25\.(\d+)\.(\d+)(\.\d+)?\+(\d+)-jre", str(p))],
        reverse=True,
        key=lambda p: re.search(r"jdk-25\.(\d+)\.(\d+)(\.\d+)?\+(\d+)-jre", p).groups() if re.search(r"jdk-25\.(\d+)\.(\d+)(\.\d+)?\+(\d+)-jre", p) else ()
    )

    if java_found_exes:
        java_exe_path = java_found_exes[0]
        print(f"Found a downloaded JRE ({java_exe_path}), using that.")
        return java_exe_path
    
    # 3. Check the Homebrew Java path on macOS and use it if the Java version is 21 or higher
    if os_name == "mac":
        homebrew_java_path = "/opt/homebrew/opt/openjdk/bin/java"
        if Path(homebrew_java_path).is_file():
            javaVersion = get_java_version(homebrew_java_path)
            javaMajorVersion = int(javaVersion.split(".")[0])
            if javaMajorVersion >= 25:
                print(f"Found Homebrew Java ({homebrew_java_path}), using that.")
                return homebrew_java_path
            
    # 4. Check $JAVA_HOME environment variable and use it if it's set and the Java version is 21 or higher
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        java_home_exe = Path(java_home) / "bin" / "java"
        if os_name == "windows":
            java_home_exe = java_home_exe.with_suffix(".exe")
        if java_home_exe.is_file():
            javaVersion = get_java_version(str(java_home_exe))
            javaMajorVersion = int(javaVersion.split(".")[0])
            if javaMajorVersion >= 25:
                print(f"Found Java via JAVA_HOME ({java_home_exe}), using that.")
                return str(java_home_exe)
            
    return ""

# Remove java offending characters
def search_n_strip(s):
    s = re.sub('[ΩµΦ]', '', s)
    return s


#
# Freerouting round trip invocation:
# * export board.dsn file from pcbnew
# * auto route by invoking freerouting.jar
# * import generated board.ses file into pcbnew
#
class FreeroutingPlugin(pcbnew.ActionPlugin):

    # init in place of constructor
    def defaults(self):
        self.host = "KiCad"
        self.here_path = Path(__file__).parent
        self.filename = Path(__file__).name
        self.name = "Freerouting"
        self.category = "PCB auto routing"
        self.description = "Freerouting for PCB auto routing"
        self.show_toolbar_button = True
        self.icon_file_name = str(self.here_path / 'icon_24x24.png')
        
        # Controls KiCAD session file imports (works only in KiCAD nigthly or 6)
        self.SPECCTRA=True

    # setup execution context
    def update_module_command(self):
        # Run freerouting with input (-de) and output (-do) file definition
        # Convert Path objects to strings for the command list
        self.module_command = [
            str(self.java_path),
            "-jar",
            str(self.module_path),
            "-de",
            str(self.module_input),
            "-do",
            str(self.module_output),
            "-host",
            str(self.host)
        ]

    # setup execution context
    def prepare(self):

        self.board = pcbnew.GetBoard()
        board_path = Path(self.board.GetFileName())
        self.dirpath = board_path.parent
        self.board_name = board_path.name
        self.board_prefix = self.dirpath / board_path.stem

        config = configparser.ConfigParser()
        config_path = self.here_path / 'plugin.ini'
        config.read(config_path)

        os_name, architecture = detect_os_architecture()

        self.java_path = config['java']['path']
        local_java_exe_path = get_local_java_executable_path(os_name)
        if Path(local_java_exe_path).is_file():
            self.java_path = local_java_exe_path

        self.module_file = config['artifact']['location']
        self.module_path = self.here_path / self.module_file

        # Set temp filename using pathlib
        self.module_input = self.dirpath / 'freerouting.dsn'
        self.temp_input = self.dirpath / 'temp-freerouting.dsn'
        self.module_output = self.dirpath / 'freerouting.ses'
        self.module_rules = self.dirpath / 'freerouting.rules'
        
        # Remove previous temp files using unlink with missing_ok
        self.temp_input.unlink(missing_ok=True)
        self.module_output.unlink(missing_ok=True)
        self.module_rules.unlink(missing_ok=True)
        
        # Create DSN file and remove java offending characters
        if not self.RunExport() :
            raise Exception("Failed to generate DSN file!") # Consider more specific exception

        self.bFirstLine = True
        self.bEatNextLine = False
        
        # Use with open for file handling
        with open(self.module_input, "w") as fw:
             with open(self.temp_input , "r", encoding="utf-8") as fr:
                for l in fr:
                    if self.bFirstLine:
                        fw.writelines(f'(pcb {self.module_input.name}\n') # Use f-string
                        self.bFirstLine = False
                    elif self.bEatNextLine:
                        self.bEatNextLine = l.rstrip()[-2:]!="))"
                        print(l)
                        print(self.bEatNextLine)
                        
                    # Optional: remove one or both copper-pours before run freerouting
                    #elif l[:28] == "    (plane GND (polygon F.Cu":
                    #    self.bEatNextLine = True
                    #elif l[:28] == "    (plane GND (polygon B.Cu":
                    #    self.bEatNextLine = True
                    else:
                        fw.writelines(search_n_strip(l))
                                        
        self.update_module_command()
                        

    # export board.dsn file from pcbnew
    def RunExport(self):
        if self.SPECCTRA:
            ok = pcbnew.ExportSpecctraDSN(str(self.temp_input)) # Ensure path is string
            if ok and self.temp_input.is_file(): # Use is_file()
                return True
            else:
                wx_show_error("""
                Failed to invoke:
                * pcbnew.ExportSpecctraDSN
                """)
                return False
        else:
            return True

    # auto route by invoking freerouting.jar
    def RunRouter(self):
        # Check if the freerouting temp folder exists, if not create it
        freerouting_jre_temp_folder.mkdir(parents=True, exist_ok=True) # Use mkdir with parents and exist_ok

        # Check if Java is installed and if it's version 25 or higher
        javaVersion = get_java_version(self.java_path)
        try:
            javaMajorVersion = int(javaVersion.split(".")[0])
        except ValueError: # Handle case where split might not return a digit
            javaMajorVersion = 0


        javaInstallNow = wx.ID_NO

        if (javaMajorVersion == 0):
            # No Java installation found
            flatpakNote = " If you believe that you have a working Java installation, double-check if you installed KiCad Flatpak. If you did that could be a reason why we can't access the Java runtime as plugins run in a very limited environment."
            os_name, architecture = detect_os_architecture()
            flatpak_message = flatpakNote if os_name == "linux" else ""

            javaInstallationWarningMessage = textwrap.dedent(f"""
            Java JRE version 25 or higher is required, but no Java installation was found.{flatpak_message}
            Would you like to install it now?
            (This can take up to a few minutes.)
            """)

            # Ask the user if they want to install Java
            javaInstallNow = wx_show_warning(javaInstallationWarningMessage)
            
            # If the user doesn't want to install Java, return False
            if (javaInstallNow != wx.ID_YES):
                return False
        else:
            if (javaMajorVersion < 25):
                javaInstallationWarningMessage = textwrap.dedent(f"""
                Java JRE version 25 or higher is required, but you have Java version {javaVersion} installed.
                Would you like to install a newer one now?
                (This can take up to a few minutes.)
                """)
                javaInstallNow = wx_show_warning(javaInstallationWarningMessage)
                if (javaInstallNow != wx.ID_YES):
                    return False
            
        if (javaInstallNow == wx.ID_YES):
            # If the user wants to install Java, clean up the previous JRE installations in the temp folder first
            for path in freerouting_jre_temp_folder.glob("jdk-*+*-jre"): # Use glob from Path
                if path.is_dir():
                    shutil.rmtree(path)
                else:
                    path.unlink(missing_ok=True) # Use unlink with missing_ok

            # Install Java JRE 25
            self.java_path = install_java_jre_25()
            
        javaVersion = get_java_version(self.java_path)
        try:
            javaMajorVersion = int(javaVersion.split(".")[0])
        except ValueError:
            javaMajorVersion = 0
            
        if javaMajorVersion < 25:
            wx_show_error(textwrap.dedent("""
            Java JRE installation failed, so we can't run Freerouting at the moment.
            You can download the latest Java JRE from https://adoptium.net/temurin/releases and install it manually. KiCad must be restarted after the installation.
            """))
            return False
    
        dialog = ProcessDialog(None, textwrap.dedent("""
        Complete or Terminate Freerouting:
        * to complete, close Java window
        * to terminate, press Terminate here
        """))

        def on_complete():
            wx_safe_invoke(dialog.terminate)

        self.update_module_command()
        invoker = ProcessThread(self.module_command, on_complete)

        dialog.Show()  # dialog first
        invoker.start()  # run java process
        result = dialog.ShowModal()  # block pcbnew here
        dialog.Destroy()

        try:
            if result == dialog.result_button:  # return via terminate button
                invoker.terminate()
                return False
            elif result == dialog.result_terminate:  # return via dialog.terminate()
                if invoker.has_ok():
                    return True
                else:
                    invoker.show_error()
                    return False
            else:
                return False  # should not happen
        finally:
            invoker.join(10)  # prevent thread resource leak

    # import generated board.ses file into pcbnew
    def RunImport(self):
        if self.SPECCTRA:
            ok = pcbnew.ImportSpecctraSES(str(self.module_output)) # Ensure path is string
            if ok and self.module_output.is_file(): # Use is_file()
                self.module_input.unlink(missing_ok=True) # Use unlink
                self.module_output.unlink(missing_ok=True) # Use unlink
                return True
            else:
                wx_show_error(textwrap.dedent("""
                Failed to invoke:
                * pcbnew.ImportSpecctraSES
                """))
                return False
        else:
            return True

    # invoke chain of dependent methods
    def RunSteps(self):

        self.prepare()

        if not self.RunRouter() :
            return

        # Remove temp DSN file
        self.temp_input.unlink(missing_ok=True) # Use unlink


        wx_safe_invoke(self.RunImport)
        

    # kicad plugin action entry
    def Run(self):
        if self.SPECCTRA:
            if has_pcbnew_api():
                self.RunSteps()
            else:
                wx_show_error(textwrap.dedent("""
                Missing required python API:
                * pcbnew.ExportSpecctraDSN
                * pcbnew.ImportSpecctraSES
                ---
                Try development nightly build:
                * http://kicad-pcb.org/download/
                """))
        else:
            self.RunSteps()


# provision gui-thread-safe execution context
# https://git.launchpad.net/kicad/tree/pcbnew/python/kicad_pyshell/__init__.py#n89
if 'phoenix' in wx.PlatformInfo:
    if not wx.GetApp():
        theApp = wx.App()
    else:
        theApp = wx.GetApp()


# run functon inside gui-thread-safe context, requires wx.App on phoenix
def wx_safe_invoke(function, *args, **kwargs):
    wx.CallAfter(function, *args, **kwargs)


# verify required pcbnew api is present
def has_pcbnew_api():
    return hasattr(pcbnew, 'ExportSpecctraDSN') and hasattr(pcbnew, 'ImportSpecctraSES')


# message dialog style
wx_caption = "KiCad Freerouting Plugin"


# display warning text with a question to the user
def wx_show_warning(text):
    message = text
    style = wx.YES_NO | wx.ICON_WARNING
    dialog = wx.MessageDialog(None, message=message, caption=wx_caption, style=style)
    result = dialog.ShowModal()
    dialog.Destroy()
    return result

# display error text to the user
def wx_show_error(text):
    message = text
    style = wx.OK | wx.ICON_ERROR
    dialog = wx.MessageDialog(None, message=message, caption=wx_caption, style=style)
    dialog.ShowModal()
    dialog.Destroy()


# check the installed java version
def get_java_version(javaPath):
    try:
        # Use subprocess.run for better control and capturing output
        result = subprocess.run(
            [javaPath, '-version'],
            check=True, # Raise CalledProcessError for non-zero exit codes
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True # Decode stdout/stderr as text
        )
        # Java version info is often on stderr
        java_info = result.stderr
        javaVersions = [re.search(r'([0-9\._]+)', v).group(1).replace('"', '') for v in java_info.splitlines()]

        for v in javaVersions:
            if v.split(".")[0].isdigit():
                return v
    except (FileNotFoundError, subprocess.CalledProcessError) as e:
        print(f"Error getting Java version from {javaPath}: {e}")
    except Exception as e:
        print(f"An unexpected error occurred getting Java version: {e}")
    return "0.0.0.0"
    

def download_progress_hook(count, block_size, total_size):
    percent = count * block_size * 100 // total_size
    #sys.stdout.write(f"\rDownloading: {percent}%")
    #sys.stdout.flush()


def download_with_progress_bar(url):
    # Return temp filename
    return urllib.request.urlretrieve(url, reporthook=download_progress_hook)[0]

def install_java_jre_25():
    # Get platform information and the appropriate URL
    os_name, architecture = detect_os_architecture()
    print(f"Operating System: {os_name}")
    print(f"Architecture: {architecture}")

    local_java_exe = get_local_java_executable_path(os_name)

    jre_version, jre_url = check_latest_jre_version(os_name, architecture)
    
    if jre_version is None or jre_url is None:
         print("Could not get latest JRE version info, skipping download check.")
         return local_java_exe # Return whatever local java was found (if any)

    java_exe_path_expected = freerouting_jre_temp_folder / f"jdk-{jre_version}-jre" / "bin" / "java"
    if os_name == "windows":
        java_exe_path_expected = java_exe_path_expected.with_suffix(".exe")
    
    if Path(local_java_exe) == java_exe_path_expected:
        print(f"You already have the latest Java JRE ({jre_version}) downloaded.")
        return local_java_exe
    
    if local_java_exe and get_java_version(local_java_exe).split(".")[0].isdigit() and int(get_java_version(local_java_exe).split(".")[0]) >= 25:
         print(f"Found a suitable Java installation ({local_java_exe}), no need to download.")
         return local_java_exe

    # Double-check if the temp folder exists
    freerouting_jre_temp_folder.mkdir(parents=True, exist_ok=True)

    # Download the Java JRE
    print("Downloading Java JRE from " + jre_url)
    try:
        file_name, _ = download_with_progress_bar(jre_url)
        print("\nDownload complete.")
    except urllib.error.URLError as e:
        print(f"\nFailed to download Java JRE: {e}")
        wx_show_error(textwrap.dedent(f"""
        Failed to download Java JRE from:
        {jre_url}

        Please check your internet connection or try downloading and installing Java JRE 25 or higher manually from https://adoptium.net/temurin/releases.
        """))
        return "" # Return empty string to indicate failure

    # Unzip the downloaded file
    print("Extracting the downloaded file...")
    try:
        # Use subprocess.run for better control and security
        subprocess.run(
            ["tar", "-xf", file_name, "-C", str(freerouting_jre_temp_folder)], # Convert path to string
            check=True # Raise CalledProcessError for non-zero exit codes
        )
        print("Extraction complete.")
    except (FileNotFoundError, subprocess.CalledProcessError) as e:
        print(f"Failed to extract Java JRE: {e}")
        wx_show_error(textwrap.dedent(f"""
        Failed to extract the downloaded Java JRE file:
        {Path(file_name).name}

        Please check if you have a program to extract .tar.gz files installed (like 7-Zip on Windows).
        """))
        return "" # Return empty string to indicate failure
    finally:
        # Remove the downloaded zip file
        Path(file_name).unlink(missing_ok=True) # Use unlink

    java_exe_path = get_local_java_executable_path(os_name)

    # Verify the installation
    if Path(java_exe_path).is_file():
        print(f"Java JRE installed successfully at {java_exe_path}")
    else:
        print(f"Java JRE installation path not found after extraction: {java_exe_path}")
        wx_show_error(textwrap.dedent(f"""
        Java JRE extraction seemed to complete, but the executable was not found at:
        {java_exe_path}

        Something might have gone wrong during the extraction. Please try downloading and installing Java JRE 25 or higher manually from https://adoptium.net/temurin/releases.
        """))
        return ""


    return java_exe_path


# prompt user to cancel pending action; allow to cancel programmatically
class ProcessDialog (wx.Dialog):

    def __init__(self, parent, text):

        message = text

        self.result_button = wx.NewIdRef() # Use NewIdRef
        self.result_terminate = wx.NewIdRef() # Use NewIdRef

        wx.Dialog.__init__ (self, parent, id=wx.ID_ANY, title=wx_caption, pos=wx.DefaultPosition, size=wx.Size(-1, -1), style=wx.CAPTION)

        self.SetSizeHints(wx.DefaultSize, wx.DefaultSize)

        sizer = wx.BoxSizer(wx.VERTICAL)

        self.text = wx.StaticText(self, wx.ID_ANY, message, wx.DefaultPosition, wx.DefaultSize, 0)
        self.text.Wrap(-1)
        sizer.Add(self.text, 0, wx.ALIGN_CENTER_HORIZONTAL | wx.ALL, 10)

        self.line = wx.StaticLine(self, wx.ID_ANY, wx.DefaultPosition, wx.DefaultSize, wx.LI_HORIZONTAL)
        sizer.Add(self.line, 0, wx.EXPAND | wx.ALL, 5)

        self.bttn = wx.Button(self, wx.ID_ANY, "Terminate", wx.DefaultPosition, wx.DefaultSize, 0)
        self.bttn.SetDefault()
        sizer.Add(self.bttn, 0, wx.ALIGN_CENTER_HORIZONTAL | wx.ALL, 5)

        self.SetSizer(sizer)
        self.Layout()
        sizer.Fit(self)

        self.Centre(wx.BOTH)

        self.bttn.Bind(wx.EVT_BUTTON, self.bttn_on_click)

    def __del__(self):
        pass

    def bttn_on_click(self, event):
        self.EndModal(self.result_button)

    def terminate(self):
        self.EndModal(self.result_terminate)


# cancelable external process invoker with completion notification
class ProcessThread(threading.Thread):

    def __init__(self, command, on_complete=None):
        self.command = command
        self.on_complete = on_complete
        threading.Thread.__init__(self)
        self.setDaemon(True)
        self.process = None # Initialize process attribute
        self.error = None # Initialize error attribute
        self.stdout = None # Initialize stdout attribute
        self.stderr = None # Initialize stderr attribute


    # thread runner
    def run(self):
        try:
            # Use subprocess.Popen for non-blocking process start
            self.process = subprocess.Popen(
                self.command,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True # Decode stdout/stderr as text
            )
            self.stdout, self.stderr = self.process.communicate()
            # Check for non-zero exit code after communicate if not using check=True
            # if self.process.returncode != 0:
            #     # Handle non-zero exit code if needed
            #     pass # The show_error method already handles this based on returncode
        except FileNotFoundError:
             self.error = f"Command not found: {self.command[0]}. Make sure the executable is in your PATH or the correct path is configured."
        except Exception as error:
            self.error = error
        finally:
            if self.on_complete is not None:
                self.on_complete()

    def has_ok(self):
        return self.has_process() and self.process.returncode == 0

    def has_code(self):
        return self.has_process() and self.process.returncode != 0

    def has_error(self):
        return self.error is not None

    def has_process(self):
        return self.process is not None

    def terminate(self):
        if self.has_process() and self.process.poll() is None: # Check if process is still running
            try:
                self.process.terminate() # Send termination signal
                self.process.wait(timeout=5) # Wait for process to terminate
            except subprocess.TimeoutExpired:
                self.process.kill() # Force kill if termination signal is ignored
            except Exception as e:
                 print(f"Error terminating process: {e}")
        else:
            pass

    def show_error(self):
        command_str = " ".join(self.command) # command is already list of strings
        if self.has_error() :
            wx_show_error(textwrap.dedent(f"""
            Process failure:
            ---
            command:
            {command_str}
            ---
            error:
            {self.error}"""))
        elif self.has_code():
            wx_show_error(textwrap.dedent(f"""
            Program failure:
            ---
            command:
            {command_str}
            ---
            exit code: {self.process.returncode}
            --- stdout ---
            {self.stdout if self.stdout else 'N/A'}
            --- stderr  ---
            {self.stderr if self.stderr else 'N/A'}
            """))
        else:
            pass


# register plugin with kicad backend
FreeroutingPlugin().register()