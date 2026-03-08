#!/bin/bash
# Trivial Tile Trivia - Android Auto-Setup Script
# Run in Termux: bash android-setup.sh

echo "==================================="
echo "  Trivial Tile Trivia Setup"
echo "==================================="
echo ""

# Install Node.js if needed
if command -v node &> /dev/null; then
    echo "[OK] Node.js: $(node --version)"
else
    echo "[...] Installing Node.js..."
    pkg update -y && pkg install nodejs -y
    echo "[OK] Node.js installed"
fi

# Install termux-api if needed (for IP detection and browser open)
if ! command -v termux-wifi-connectioninfo &> /dev/null; then
    echo "[...] Installing Termux API tools..."
    pkg install termux-api -y
fi

# Get IP address
echo "[...] Detecting IP address..."
IP=""

# Try termux-wifi-connectioninfo first (requires Termux:API app)
if command -v termux-wifi-connectioninfo &> /dev/null; then
    IP=$(termux-wifi-connectioninfo 2>/dev/null | grep -o '"ip":"[^"]*"' | cut -d'"' -f4)
fi

# Fallback methods
[ -z "$IP" ] && IP=$(getprop dhcp.wlan0.ipaddress 2>/dev/null)
[ -z "$IP" ] && IP=$(hostname -I 2>/dev/null | awk '{print $1}')
[ -z "$IP" ] && IP="CHECK_WIFI_SETTINGS"

URL="http://$IP:5000"
HOST_URL="http://$IP:5000/host"
BOARD_URL="http://$IP:5000/board"

echo ""
echo "==================================="
echo "  Server Starting..."
echo "  Host View: $HOST_URL"
echo "  Board View: $BOARD_URL - for TV"
echo "==================================="
echo ""
echo "Press Ctrl+C or Volume Down + C to stop"
echo ""

# Open in Chrome Incognito if possible
if [ "$IP" != "CHECK_WIFI_SETTINGS" ]; then
    (
        sleep 2
        # Try to open Chrome incognito first
        am start -a org.chromium.chrome.browser.incognito.OPEN_PRIVATE_TAB 2>/dev/null
        sleep 1
        # Open Host URL in Chrome (will open in existing incognito if available)
        am start -n com.android.chrome/com.google.android.apps.chrome.Main -a android.intent.action.VIEW -d "$HOST_URL" 2>/dev/null || termux-open-url "$HOST_URL" 2>/dev/null
    ) &
    (
        sleep 4
        # Open Board URL in Chrome
        am start -n com.android.chrome/com.google.android.apps.chrome.Main -a android.intent.action.VIEW -d "$BOARD_URL" 2>/dev/null || termux-open-url "$BOARD_URL" 2>/dev/null
    ) &
fi

# Start server
node server.cjs
