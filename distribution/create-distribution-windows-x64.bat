@echo off

echo "Run this script as ADMINISTRATOR"
echo "Pass the version number as a parameter e.g. create-distribution-windows-x64.bat 2.3.4"

SET APP_VERSION=%1
SET APP_TYPE="msi"

echo "> JAVA_HOME="%JAVA_HOME%

set DIR="%~dp0\"
echo "> Distribution directory="%DIR%
cd %DIR%

echo "> Building the Java runtime"
"%JAVA_HOME%\bin\jlink.exe" -p "%JAVA_HOME%\jmods" --add-modules java.desktop,java.logging,java.net.http,java.sql,java.xml --strip-debug --no-header-files --no-man-pages --strip-native-commands --vm=server --output "%JAVA_HOME%\runtime"

echo "> Creating the installer package"
"%JAVA_HOME%\bin\jpackage.exe" --input "..\build\dist" --main-jar "freerouting-executable.jar" --name "freerouting" --type %APP_TYPE% --runtime-image "%JAVA_HOME%\runtime" --app-version %APP_VERSION% --win-per-user-install --win-menu --win-menu-group freerouting --license-file "..\LICENSE" --icon "..\design\icon\freerouting_icon_256x256_v3.ico"

move freerouting-%APP_VERSION%.msi freerouting-%APP_VERSION%-windows-x64.msi

:exit