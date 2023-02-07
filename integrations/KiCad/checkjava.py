import subprocess
import re

# check the installed java version
def get_java_version():
    javaPath = 'java'

    javaInfo = subprocess.check_output(javaPath + ' -version', shell=True, stderr=subprocess.STDOUT)
    print(javaInfo.decode().splitlines()[0])
    javaVersion = re.search(r'([0-9\._]+)', javaInfo.decode().splitlines()[0]).group(1).replace('"', '')
    print(javaVersion)
