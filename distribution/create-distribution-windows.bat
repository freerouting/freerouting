@echo off

SET  APP_VERSION=%1
SET  APP_TYPE="msi"
REM SET  JAVA_HOME="C:\Program Files\OpenJDK\jdk-13.0.2\"
SET  JPACKAGE_JVM="https://download.java.net/java/GA/jdk14/076bab302c7b4508975440c56f6cc26a/36/GPL/openjdk-14_windows-x64_bin.zip"

set DIR="%~dp0\"
echo "ABC:%DIR%"
cd %DIR%

for %%X in (7z.exe) do (set FOUND7Z=%%~$PATH:X)
for %%X in (curl.exe) do (set FOUNDCURL=%%~$PATH:X)

if not defined FOUND7Z (
  echo "ERROR: please make sure that 7Zip is installed and on the path."
)

if not defined FOUNDCURL (
  echo "ERROR: please make sure that Curl is installed and on the path."
)

if exist ".jdk14\jdk-14\" (
    echo "> jdk 14 for package generation already downloaded"
) else (
    mkdir .jdk14\
    cd .jdk14
    echo "> downloading jdk 14"
    curl -o jdk14.zip %JPACKAGE_JVM%
    echo "> unpacking jdk 14"
    7z x jdk14.zip
    echo "> creating runtime image"
    %JAVA_HOME%\bin\jlink -p "%JAVA_HOME%\jmods" --add-modules java.desktop --strip-debug --no-header-files --no-man-pages --strip-native-commands --vm=server --compress=2 --output runtime
)

cd %DIR%

set JPKG_HOME=.jdk14\jdk-14\
set JPKG_EXECUTABLE=%JPKG_HOME%\bin\jpackage

%JPKG_EXECUTABLE% --input ..\build\dist\ --name Freerouting --main-jar freerouting-executable.jar --type %APP_TYPE% --runtime-image .jdk14\runtime --app-version %APP_VERSION% --win-per-user-install --win-menu --win-menu-group Freerouting 

move Freerouting-%APP_VERSION%.msi freerouting-%APP_VERSION%-windows-x64.msi
