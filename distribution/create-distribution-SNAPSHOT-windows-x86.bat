@echo off

SET  APP_VERSION=%1
SET  APP_TYPE="msi"
SET  JPACKAGE_JVM="https://github.com/AdoptOpenJDK/openjdk14-binaries/releases/download/jdk-14+36/OpenJDK14U-jdk_x86-32_windows_hotspot_14_36.zip"

set DIR="%~dp0\"
cd %DIR%

for %%X in (7z.exe) do (set FOUND7Z=%%~$PATH:X)
for %%X in (curl.exe) do (set FOUNDCURL=%%~$PATH:X)

if not defined FOUND7Z (
  echo "ERROR: please make sure that 7Zip is installed and on the path."
)

if not defined FOUNDCURL (
  echo "ERROR: please make sure that Curl is installed and on the path."
)

if exist ".jdk14\jdk-14+36\" (
    echo "> jdk 14 for package generation already downloaded"
) else (
    mkdir .jdk14\
    cd .jdk14
    echo "> downloading jdk 14 x86"
    curl -L -o jdk14_x86.zip %JPACKAGE_JVM%
    echo "> unpacking jdk 14 x86"
    7z x jdk14_x86.zip
    echo "> creating runtime image"
    %JAVA_HOME%\bin\jlink -p "%JAVA_HOME%\jmods" --add-modules java.desktop --strip-debug --no-header-files --no-man-pages --strip-native-commands --vm=server --compress=2 --output runtime
)

cd %DIR%

set JPKG_HOME=.jdk14\jdk-14+36\
set JPKG_EXECUTABLE=%JPKG_HOME%\bin\jpackage

%JPKG_EXECUTABLE% --input ..\build\dist\ --name Freerouting --main-jar freerouting-executable.jar --type %APP_TYPE% --runtime-image .jdk14\runtime --app-version 0.0.0 --win-per-user-install --win-menu --win-menu-group Freerouting --license-file ..\LICENSE 

move Freerouting-0.0.0.msi freerouting-%APP_VERSION%-windows-x86.msi
