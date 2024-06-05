import subprocess
import re
import os
import platform

# check the installed java version
def get_java_version():
    
    # get the JAVA_HOME environment variable
    javaHome = os.environ.get('JAVA_HOME')

    # check if the operating system is macOS
    if not javaHome and platform.system() == 'Darwin':
        # get the JAVA_HOME environment variable using the java_home command
        javaHomeCommand = '/usr/libexec/java_home'
        javaHome = subprocess.check_output(javaHomeCommand, shell=True).decode().strip()
        
    if javaHome:
        print(f"JAVA_HOME={javaHome}")
        javaPath = os.path.join(javaHome, 'bin')
        
        # check if the java directory exists
        if not os.path.exists(javaPath):
            print(f"WARNING javaPath={javaPath} doesn't exist!")
            javaPath = '.'
    else:    
        javaPath = '.'
        
    # get the java version
    javaInfo = subprocess.check_output('java -version', shell=True, stderr=subprocess.STDOUT, cwd=javaPath)
    javaVersions = [re.search(r'([0-9\._]+)', v).group(1).replace('"', '') for v in javaInfo.decode().splitlines()]
    print(f"javaVersions={javaVersions}")
    
    # get the first version number
    for v in javaVersions:
        if v.split(".")[0].isdigit():
            print(v)
            return

if __name__ == '__main__':
    get_java_version()
