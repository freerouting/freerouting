@echo off

SET APP_VERSION=%1
SET APP_TYPE="msi"

echo "> JAVA_HOME="%JAVA_HOME%

set DIR="%~dp0\"
echo "> Distribution directory="%DIR%
cd %DIR%

echo "> Building the Java runtime"
"%JAVA_HOME%\bin\jlink.exe" -p "%JAVA_HOME%\jmods" --add-modules java.desktop --strip-debug --no-header-files --no-man-pages --strip-native-commands --vm=server --compress=2 --output "%JAVA_HOME%\runtime"

echo "> Creating the installer package"
"%JAVA_HOME%\bin\jpackage.exe" --input "..\build\dist" --main-jar "freerouting-executable.jar" --name "freerouting" --type %APP_TYPE% --runtime-image "%JAVA_HOME%\runtime" --app-version 0.0.0 --win-per-user-install --win-menu --win-menu-group freerouting --license-file "..\LICENSE"

move freerouting-0.0.0.msi freerouting-%APP_VERSION%-windows-x64.msi

:exit