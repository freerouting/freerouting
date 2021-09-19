@echo off

SET  APP_VERSION=%1

set DIR="%~dp0\"
cd %DIR%

copy ..\build\dist\freerouting-executable.jar freerouting-%APP_VERSION%.jar
