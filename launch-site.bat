@echo off
title Forge Stuck!! Dev Server

:: Refresh PATH from registry so Node is found even right after install
for /f "tokens=2*" %%a in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v PATH 2^>nul') do set "SysPATH=%%b"
for /f "tokens=2*" %%a in ('reg query "HKCU\Environment" /v PATH 2^>nul') do set "UserPATH=%%b"
set "PATH=%SysPATH%;%UserPATH%"

echo ================================================
echo  Forge Stuck!! - Local Dev Server
echo ================================================
echo.

where node >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Node.js not found. Download it from https://nodejs.org
    pause
    exit /b 1
)

if not exist "node_modules\" (
    echo [1/2] Installing dependencies ^(first run only^)...
    npm install --legacy-peer-deps
    echo.
)

echo Starting dev server...
echo.
echo  Open in your browser: http://localhost:3000
echo  Press Ctrl+C to stop.
echo.
npm run dev
pause
