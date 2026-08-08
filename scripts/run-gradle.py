#!/usr/bin/env python3
import subprocess
import sys


def main() -> None:
    gradle_cmd = "gradlew.bat" if sys.platform == "win32" else "./gradlew"
    cmd = [gradle_cmd] + sys.argv[1:]
    result = subprocess.run(cmd)
    sys.exit(result.returncode)


if __name__ == "__main__":
    main()
