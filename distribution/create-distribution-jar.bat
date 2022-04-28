@echo off

SET  APP_VERSION=%1

set DIR="%~dp0\"
cd %DIR%

copy ..\build\libs\freerouting-executable.jar freerouting-%APP_VERSION%.jar

echo "JAR created at \distribution\freerouting-%APP_VERSION%.jar"
