@echo off
cd /d "%~dp0"

:: Check if running as admin
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [INFO] Solicitando privilegios de administrador...
    powershell -Command "Start-Process -FilePath 'cmd.exe' -ArgumentList '/c \"%~f0\"' -Verb RunAs"
    exit /b
)

echo [OK] Executando como administrador.
set /p CP=<cp.txt
java -cp "target/classes;%CP%" com.bot.Main
pause
