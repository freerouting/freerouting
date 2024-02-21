import subprocess
import re

# check the installed java version
def get_java_version():
    javaPath = 'java'

    javaInfo = subprocess.check_output(javaPath + ' -version', shell=True, stderr=subprocess.STDOUT)
    javaVersions = [re.search(r'([0-9\._]+)', v).group(1).replace('"', '') for v in javaInfo.decode().splitlines()]
    print(javaVersions)
    for v in javaVersions:
        if v.split(".")[0].isdigit():
            print(v)
            return

if __name__ == '__main__':
    get_java_version()
