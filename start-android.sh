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

# Install git if needed
if ! command -v git &> /dev/null; then
    echo "[...] Installing git..."
    pkg install -y git
fi

# ============================================
# AUTO-UPDATE: Pull latest from GitHub
# ============================================
REPO_URL="https://github.com/Barknard/trivial-tile-trivia.git"
GAME_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -d "$GAME_DIR/.git" ]; then
    echo "[...] Checking for updates..."
    cd "$GAME_DIR"
    git fetch origin 2>/dev/null
    LOCAL=$(git rev-parse HEAD 2>/dev/null)
    REMOTE=$(git rev-parse origin/master 2>/dev/null)
    if [ "$LOCAL" != "$REMOTE" ] && [ -n "$REMOTE" ]; then
        echo "[...] New version found! Updating..."
        git pull origin master 2>&1
        echo "[OK] Updated to latest version!"
    else
        echo "[OK] Already up to date"
    fi
else
    # First time: clone the repo to a temp dir and copy game files
    echo "[...] Setting up GitHub sync..."
    TEMP_DIR="$HOME/trivia-update-tmp"
    rm -rf "$TEMP_DIR"
    git clone --depth 1 "$REPO_URL" "$TEMP_DIR" 2>&1
    if [ -d "$TEMP_DIR/.git" ]; then
        # Copy everything from clone into game dir
        cp -rf "$TEMP_DIR"/* "$GAME_DIR"/ 2>/dev/null
        cp -rf "$TEMP_DIR"/.[!.]* "$GAME_DIR"/ 2>/dev/null
        rm -rf "$TEMP_DIR"
        echo "[OK] GitHub sync initialized!"
    else
        echo "[WARN] Could not reach GitHub - using local files"
    fi
fi
cd "$GAME_DIR"

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
