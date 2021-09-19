@echo off

SET  APP_VERSION=%1

copy ..\build\dist\freerouting-executable.jar freerouting-%APP_VERSION%.jar
