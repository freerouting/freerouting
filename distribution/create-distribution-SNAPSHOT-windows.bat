@echo off

SET APP_VERSION=%1
SET APP_TYPE="msi"
SET JPACKAGE_JVM="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.3+7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.3_7.zip"
SET JPACKAGE_HOME=.jdk\jdk-17.0.3+7

set DIR="%~dp0\"
cd %DIR%

for %%X in (7z.exe) do (set FOUND7Z=%%~$PATH:X)
for %%X in (curl.exe) do (set FOUNDCURL=%%~$PATH:X)

if not defined FOUND7Z (
  echo "ERROR: please make sure that 7Zip is installed and on the path."
  goto exit
)

if not defined FOUNDCURL (
  echo "ERROR: please make sure that Curl is installed and on the path."
  goto exit
)

if exist ".jdk\" (
    echo "> JDK for package generation already downloaded"
) else (
    mkdir .jdk\
    cd .jdk
    echo "> downloading JDK"
    curl -L -o jdk.zip %JPACKAGE_JVM%
    echo "> unpacking JDK"
    7z x jdk.zip
	cd ..
    echo "> creating runtime image"
    "%JPACKAGE_HOME%\bin\jlink.exe" -p "%JPACKAGE_HOME%\jmods" --add-modules java.desktop --strip-debug --no-header-files --no-man-pages --strip-native-commands --vm=server --compress=2 --output "%JPACKAGE_HOME%\runtime"
)

cd %DIR%

echo "> creating installer .msi"
"%JPACKAGE_HOME%\bin\jpackage.exe" --input "..\build\libs" --main-jar "freerouting-executable.jar" --name "freerouting" --type %APP_TYPE% --runtime-image "%JPACKAGE_HOME%\runtime" --app-version 0.0.0 --win-per-user-install --win-menu --win-menu-group freerouting --license-file "..\LICENSE"

move freerouting-0.0.0.msi freerouting-%APP_VERSION%-windows-x64.msi

:exit