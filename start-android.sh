#!/bin/bash
# Trivial Tile Trivia - Android/Termux Launcher
# Run in Termux: bash start-android.sh

cd "$(dirname "$0")"

echo ""
echo "==================================="
echo "   TRIVIAL TILE TRIVIA"
echo "==================================="
echo ""

# Check if running in Termux
if [ -z "$TERMUX_VERSION" ] && ! command -v pkg &> /dev/null; then
    echo "ERROR: This script must run inside Termux!"
    echo ""
    echo "Please install Termux first:"
    echo ""
    echo "1. Download Termux from F-Droid (NOT Play Store):"
    echo "   https://f-droid.org/packages/com.termux/"
    echo ""
    echo "2. Also install Termux:API from F-Droid:"
    echo "   https://f-droid.org/packages/com.termux.api/"
    echo ""
    echo "3. Open Termux and run:"
    echo "   termux-setup-storage"
    echo ""
    echo "4. Then run this script again:"
    echo "   bash /sdcard/trivial-tile-trivia-portable/start-android.sh"
    echo ""
    exit 1
fi

echo "[OK] Running in Termux"

# Install Node.js if needed
if ! command -v node &> /dev/null; then
    echo "[...] Installing Node.js..."
    pkg update -y && pkg install -y nodejs
fi

echo "[OK] Node.js: $(node --version)"

# Install termux-api if needed
if ! command -v termux-wifi-connectioninfo &> /dev/null; then
    echo "[...] Installing Termux API tools..."
    pkg install -y termux-api
fi

# Auto-create Termux:Widget shortcut for one-tap launching
SCRIPT_PATH="$(cd "$(dirname "$0")" && pwd)/start-android.sh"
SHORTCUT_DIR="$HOME/.shortcuts"
SHORTCUT_FILE="$SHORTCUT_DIR/Trivia Game"

if [ ! -f "$SHORTCUT_FILE" ]; then
    echo "[...] Creating home screen shortcut..."
    mkdir -p "$SHORTCUT_DIR"
    echo "#!/bin/bash" > "$SHORTCUT_FILE"
    echo "cd \"$(dirname "$0")\"" >> "$SHORTCUT_FILE"
    echo "bash \"$SCRIPT_PATH\"" >> "$SHORTCUT_FILE"
    chmod +x "$SHORTCUT_FILE"
    echo "[OK] Shortcut created!"
    echo ""
    echo "*** TIP: Install Termux:Widget from F-Droid to get a ***"
    echo "*** home screen button for one-tap game launching!   ***"
    echo "*** https://f-droid.org/packages/com.termux.widget/  ***"
    echo ""
fi

# Get IP address
IP=""
if command -v termux-wifi-connectioninfo &> /dev/null; then
    IP=$(termux-wifi-connectioninfo 2>/dev/null | grep -o '"ip":"[^"]*"' | cut -d'"' -f4)
fi
[ -z "$IP" ] && IP=$(getprop dhcp.wlan0.ipaddress 2>/dev/null)
[ -z "$IP" ] && IP=$(hostname -I 2>/dev/null | awk '{print $1}')
[ -z "$IP" ] && IP="localhost"

HOST_URL="http://$IP:5000"
BOARD_URL="http://$IP:5000/board"

echo ""
echo "==================================="
echo "   Starting Server..."
echo "   Host:  $HOST_URL"
echo "   Board: $BOARD_URL"
echo "==================================="
echo ""

# Copy board URL to clipboard
if command -v termux-clipboard-set &> /dev/null; then
    termux-clipboard-set "$BOARD_URL"
    echo "Board URL copied to clipboard!"
fi
echo ""

# Open browser windows with delays
echo "[...] Opening Host view in 3 seconds..."
(sleep 3 && am start -a android.intent.action.VIEW -d "$HOST_URL" 2>/dev/null && echo "[OK] Host window opened") &

echo "[...] Opening Incognito for Board in 8 seconds..."
echo "     Board URL is in clipboard - just paste it!"
(sleep 8 && am start -n com.android.chrome/com.google.android.apps.chrome.incognito.IncognitoTabLauncher 2>/dev/null && echo "[OK] Incognito window opened - paste URL from clipboard!") &

echo ""
echo "==================================="
echo "   INCOGNITO NOTE"
echo "   Chrome blocks opening URLs in incognito."
echo "   Board URL is copied to clipboard."
echo "   Just PASTE in the incognito window!"
echo "==================================="
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# Start server
node server.cjs
