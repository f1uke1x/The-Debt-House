@echo off
cd /d "%~dp0"
call mvn -q javafx:run
if errorlevel 1 pause