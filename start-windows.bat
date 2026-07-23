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
echo   Host:   http://%IP%:5000        (control panel - opens automatically)
echo   Board:  http://%IP%:5000/board  (open in a SEPARATE window for casting)
echo   Player: http://%IP%:5000        (players scan the QR on Host screen)
echo ===================================
echo.

:: Copy board URL to clipboard so the user can paste it into a new window
echo http://%IP%:5000/board | clip
echo Board URL copied to clipboard.
echo To cast the board, paste it into a NEW Chrome window (Ctrl+N), then cast that window.
echo.

:: Open ONLY the Host (control) view on launch. Do not auto-open the Board:
:: the user opens the Board in a separate Chrome window when ready to cast.
start "" "http://%IP%:5000"

echo Press Ctrl+C to stop the server.
echo.

:: Start the server
node server.cjs
