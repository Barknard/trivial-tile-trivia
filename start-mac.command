#!/bin/bash
# Trivial Tile Trivia - macOS Launcher
# Double-click this file to start the game

cd "$(dirname "$0")"

echo ""
echo "==================================="
echo "   TRIVIAL TILE TRIVIA"
echo "==================================="
echo ""

# Ensure Homebrew is in PATH (for Apple Silicon and Intel Macs)
if [ -f /opt/homebrew/bin/brew ]; then
    eval "$(/opt/homebrew/bin/brew shellenv)"
elif [ -f /usr/local/bin/brew ]; then
    eval "$(/usr/local/bin/brew shellenv)"
fi

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "Node.js not found! Installing via Homebrew..."
    
    # Check if Homebrew is installed
    if ! command -v brew &> /dev/null; then
        echo "Installing Homebrew first..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        
        # Re-source Homebrew after installation
        if [ -f /opt/homebrew/bin/brew ]; then
            eval "$(/opt/homebrew/bin/brew shellenv)"
        elif [ -f /usr/local/bin/brew ]; then
            eval "$(/usr/local/bin/brew shellenv)"
        fi
    fi
    
    brew install node
    
    # Ensure node is in PATH after install
    if [ -f /opt/homebrew/bin/brew ]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
fi

echo "[OK] Node.js: \$(node --version)"

# Check for new questions to import
QUESTIONS_DIR="\$(dirname "\$0")/questions"
if [ -d "\$QUESTIONS_DIR" ]; then
    JSON_COUNT=\$(find "\$QUESTIONS_DIR" -name "*.json" 2>/dev/null | wc -l)
    if [ "\$JSON_COUNT" -gt 0 ]; then
        echo ""
        echo "[...] Found \$JSON_COUNT new question files to import"
        node "\$(dirname "\$0")/import-questions.cjs" "\$QUESTIONS_DIR" "\$(dirname "\$0")/public/runtime-questions.json"
        echo "[OK] Questions imported!"
        echo ""
    fi
fi

# Get local IP address
IP=\$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null)
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
echo "$BOARD_URL" | pbcopy
echo "Board URL copied to clipboard!"
echo ""

# Open browser windows
sleep 2
open "$HOST_URL"
sleep 2
open "$BOARD_URL"

echo "Press Ctrl+C to stop the server"
echo ""

# Start the server
node server.cjs
