@echo off
setlocal
cd /d "%~dp0"
echo Launching Freerouting Altium Extension Uninstaller with Administrator privileges...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File \"\"%~dp0install.ps1\"\" -Uninstall' -Wait"
