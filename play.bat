@echo off
cd /d "%~dp0"
echo Launching Minecraft 1.21 (Fabric Dev Client) with Quantum Atom Explorer...
call gradlew.bat runClient --no-daemon
pause
