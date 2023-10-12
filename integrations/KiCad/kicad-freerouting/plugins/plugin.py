import glob
import json
import os
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
    latest_jre_info_request = urllib.request.Request(
        # Docs at https://api.adoptium.net/q/swagger-ui/
        f"https://api.adoptium.net/v3/assets/latest/17/hotspot?image_type=jre&os={os_name}&architecture={architecture}",
        headers={"User-Agent": ""}  # The server rejects requests with the default UA
    )
    jre_version_info = json.loads(urllib.request.urlopen(latest_jre_info_request).read())[0]
    latest_jre_version = jre_version_info["version"]["semver"]
    jre_url = jre_version_info['binary']["package"]["link"]
    return latest_jre_version, jre_url
    

def get_local_java_executable_path(os_name):
    java_exe_path = os.path.join(tempfile.gettempdir(), f"jdk-17.*.*+*-jre", "bin", "java")
    if os_name == "windows":
        java_exe_path += ".exe"
        
    java_found_exes = sorted(
        [p for p in filter(lambda p: os.path.isfile(p), glob.glob(java_exe_path)) if re.search(r"jdk-17\.(\d+)\.(\d+)(\.\d+)?\+(\d+)-jre", p)],
        reverse=True,
        key=lambda p: re.search(r"jdk-17\.(\d+)\.(\d+)(\.\d+)?\+(\d+)-jre", p).groups() if re.search(r"jdk-17\.(\d+)\.(\d+)(\.\d+)?\+(\d+)-jre", p) else ()
    )

    if len(java_found_exes) >= 1:
        java_exe_path = java_found_exes[0]
        print(f"You already have a downloaded JRE ({java_exe_path}), we are going to use that.")        
    else:
        java_exe_path = ""
        
    return java_exe_path

# Remove java offending characters
def search_n_strip(s):
    s = re.sub('[Ωµ]', '', s)
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
        self.here_path, self.filename = os.path.split(os.path.abspath(__file__))
        self.name = "Freerouting"
        self.category = "PCB auto routing"
        self.description = "Freerouting for PCB auto routing"
        self.show_toolbar_button = True
        self.icon_file_name = os.path.join(self.here_path, 'icon_24x24.png')        
        
        # Controls KiCAD session file imports (works only in KiCAD nigthly or 6)
        self.SPECCTRA=True

    # setup execution context
    def update_module_command(self):
        # Run freerouting with logging disabled (-dl) and input (-de) and output (-do) file definition
        self.module_command = [self.java_path, "-jar", self.module_path, "-dl", "-de", self.module_input, "-do", self.module_output, "-host", self.host]

    # setup execution context
    def prepare(self):

        self.board = pcbnew.GetBoard()
        self.dirpath, self.board_name = os.path.split(self.board.GetFileName())
        self.path_tuple = os.path.splitext(os.path.abspath(self.board.GetFileName()))
        self.board_prefix = self.path_tuple[0]

        config = configparser.ConfigParser()
        config_path = os.path.join(self.here_path, 'plugin.ini')
        config.read(config_path)

        os_name, architecture = detect_os_architecture()

        self.java_path = config['java']['path']
        local_java_exe_path = get_local_java_executable_path(os_name)
        if (os.path.exists(local_java_exe_path)):
            self.java_path = local_java_exe_path

        self.module_file = config['artifact']['location']
        self.module_path = os.path.join(self.here_path, self.module_file)
        
        # Set temp filename
        self.module_input = os.path.join(self.dirpath,'freerouting.dsn')
        self.temp_input =  os.path.join(self.dirpath,'temp-freerouting.dsn')
        self.module_output =  os.path.join(self.dirpath,'freerouting.ses')
        self.module_rules =  os.path.join(self.dirpath,'freerouting.rules')
       
        # Remove previous temp files
        try:
            os.remove(self.temp_input)
        except:
            pass
        
        try:
            os.remove(self.module_output)
        except:
            pass
        
        try:
            os.remove(self.module_rules)
        except:
            pass
        # Create DSN file and remove java offending characters
        if not self.RunExport() :
            raise Exception("Failed to generate DSN file!")
        self.bFirstLine = True
        self.bEatNextLine = False
        fw = open(self.module_input, "w")
        fr = open(self.temp_input , "r", encoding="utf-8")
        for l in fr:
            if self.bFirstLine:
                fw.writelines('(pcb ' + self.module_input + '\n')
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
        fr.close()
        fw.close()
                        
        self.update_module_command()
                       

    # export board.dsn file from pcbnew
    def RunExport(self):
        if self.SPECCTRA:
            ok = pcbnew.ExportSpecctraDSN(self.temp_input)
            if ok and os.path.isfile(self.temp_input):
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
        javaVersion = get_java_version(self.java_path)
        javaMajorVersion = int(javaVersion.split(".")[0])

        javaInstallNow = wx.ID_NO

        if (javaMajorVersion == 0):
            javaInstallNow = wx_show_warning("""
            Java JRE version 17 or higher is required, but you have no Java installed or you have no access to it because you used Flatpak to install KiCad.
            Would you like to install it now?
            (This can take up to a few minutes.)
            """)
            if (javaInstallNow != wx.ID_YES):
                return False            
        else:
            if (javaMajorVersion < 17):
                javaInstallNow = wx_show_warning("""
                Java JRE version 17 or higher is required, but you have Java version {0} installed.
                Would you like to install a newer one now?
                (This can take up to a few minutes.)
                """.format(javaVersion))
                if (javaInstallNow != wx.ID_YES):
                    return False
            
        if (javaInstallNow == wx.ID_YES):
            self.java_path = install_java_jre_17()
            
        javaVersion = get_java_version(self.java_path)
        javaMajorVersion = int(javaVersion.split(".")[0])            
        
        if javaMajorVersion < 17:
            wx_show_error("""
            Java JRE installation failed, so we can't run Freerouting at the moment.
            You can download the latest Java JRE from https://adoptium.net/temurin/releases and install it manually. KiCad must be restarted after the installation.
            """)
            return False
   
        dialog = ProcessDialog(None, """
        Complete or Terminate Freerouting:
        * to complete, close Java window
        * to terminate, press Terminate here
        """)

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
            ok = pcbnew.ImportSpecctraSES(self.module_output)
            if ok and os.path.isfile(self.module_output):
                os.remove(self.module_input)
                os.remove(self.module_output)               
                return True
            else:
                wx_show_error("""
                Failed to invoke:
                * pcbnew.ImportSpecctraSES
                """)
                return False
        else:
            return True

    # invoke chain of dependent methods
    def RunSteps(self):

        self.prepare()

        if not self.RunRouter() :
            return

        # Remove temp DSN file
        os.remove(self.temp_input)


        wx_safe_invoke(self.RunImport)
        

    # kicad plugin action entry
    def Run(self):
        if self.SPECCTRA:
            if has_pcbnew_api():
                self.RunSteps()
            else:
                wx_show_error("""
                Missing required python API:
                * pcbnew.ExportSpecctraDSN
                * pcbnew.ImportSpecctraSES
                ---
                Try development nightly build:
                * http://kicad-pcb.org/download/
                """)
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
    message = textwrap.dedent(text)
    style = wx.YES_NO | wx.ICON_WARNING
    dialog = wx.MessageDialog(None, message=message, caption=wx_caption, style=style)
    return dialog.ShowModal()

# display error text to the user
def wx_show_error(text):
    message = textwrap.dedent(text)
    style = wx.OK | wx.ICON_ERROR
    dialog = wx.MessageDialog(None, message=message, caption=wx_caption, style=style)
    dialog.ShowModal()
    dialog.Destroy()


# check the installed java version
def get_java_version(javaPath):
    try:
        javaInfo = subprocess.check_output(javaPath + ' -version', shell=True, stderr=subprocess.STDOUT)
        javaVersions = [re.search(r'([0-9\._]+)', v).group(1).replace('"', '') for v in javaInfo.decode().splitlines()]

        for v in javaVersions:
            if v.split(".")[0].isdigit():
                return v
    except:
        pass
    return "0.0.0.0"
    

def download_progress_hook(count, block_size, total_size):
    percent = count * block_size * 100 // total_size
    #sys.stdout.write(f"\rDownloading: {percent}%")
    #sys.stdout.flush()


def download_with_progress_bar(url):
    # Return temp filename
    return urllib.request.urlretrieve(url, reporthook=download_progress_hook)[0]

def install_java_jre_17():
    # Get platform information and the appropriate URL
    os_name, architecture = detect_os_architecture()
    print(f"Operating System: {os_name}")
    print(f"Architecture: {architecture}")

    local_java_exe = get_local_java_executable_path(os_name)

    try:
        jre_version, jre_url = check_latest_jre_version(os_name, architecture)
    except Exception:
        print("Couldn't connect to the server")
        # Find all matching JRE 17
        jre_version = "17.*.*+*"
        jre_url = None
        return local_java_exe
        
    java_exe_path = os.path.join(tempfile.gettempdir(), f"jdk-{jre_version}-jre", "bin", "java")
    if os_name == "windows":
        java_exe_path += ".exe"      
 
    if (local_java_exe >= java_exe_path):
        print(f"You already have the latest Java JRE ({jre_version})")
        return java_exe_path
 
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

    java_exe_path = get_local_java_executable_path(os_name)

    # Verify the installation
    #java_version_command = f"{java_exe_path} -version"
    #result = subprocess.check_output(java_version_command, shell=True, stderr=subprocess.STDOUT)
    #print("Installed Java version:", result)
    
    return java_exe_path


# prompt user to cancel pending action; allow to cancel programmatically
class ProcessDialog (wx.Dialog):

    def __init__(self, parent, text):

        message = textwrap.dedent(text)

        self.result_button = wx.NewId()
        self.result_terminate = wx.NewId()

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

    # thread runner
    def run(self):
        try:
            self.process = subprocess.Popen(self.command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            self.stdout, self.stderr = self.process.communicate()
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
        return hasattr(self, "error")

    def has_process(self):
        return hasattr(self, "process")

    def terminate(self):
        if self.has_process():
            self.process.kill()
        else:
            pass

    def show_error(self):
        command = " ".join(self.command)
        if self.has_error() :
            wx_show_error("""
            Process failure:
            ---
            command:
            %s
            ---
            error:
            %s""" % (command, str(self.error)))
        elif self.has_code():
            wx_show_error("""
            Program failure:
            ---
            command:
            %s
            ---
            exit code: %d
            --- stdout ---
            %s
            --- stderr  ---
            %s
            """ % (command, self.process.returncode, self.stdout, self.stderr))
        else:
            pass


# register plugin with kicad backend
FreeroutingPlugin().register()
