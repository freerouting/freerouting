@echo off

SET  APP_VERSION=%1

echo "> JAVA_HOME="%JAVA_HOME%

set DIR="%~dp0\"
echo "> Distribution directory=%DIR%
cd %DIR%

copy ..\build\dist\freerouting-executable.jar freerouting-%APP_VERSION%.jar

echo "JAR created at \distribution\freerouting-%APP_VERSION%.jar"
