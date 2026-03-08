@echo off
title Trivial Tile Trivia
echo.
echo ===================================
echo   TRIVIAL TILE TRIVIA
echo ===================================
echo.

:: Check if Node.js is installed
where node >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Node.js not found! Installing...
    echo.
    echo Please download and install Node.js from:
    echo https://nodejs.org/
    echo.
    echo After installing, run this script again.
    pause
    start https://nodejs.org/
    exit /b
)

echo [OK] Node.js found: 
node --version

:: Check for new questions to import
if exist "questions\*.json" (
    echo.
    echo [...] Importing new questions...
    node import-questions.cjs questions public\runtime-questions.json
    echo [OK] Questions imported!
    echo.
)

:: Get local IP address
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
    set IP=%%a
    goto :gotip
)
:gotip
set IP=%IP:~1%

echo.
echo ===================================
echo   Starting Server...
echo   Host: http://%IP%:5000
echo   Board: http://%IP%:5000/board
echo ===================================
echo.

:: Copy board URL to clipboard
echo http://%IP%:5000/board | clip
echo Board URL copied to clipboard!
echo.

:: Open browser windows
start "" "http://%IP%:5000"
timeout /t 3 /nobreak >nul
start "" "http://%IP%:5000/board"

echo Press Ctrl+C to stop the server
echo.

:: Start the server
node server.cjs
