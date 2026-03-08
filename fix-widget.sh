#!/bin/bash
# Fix Termux Widget - clears old shortcuts and creates a fresh one
# Run in Termux: bash /sdcard/trivial-tile-trivia-portable/fix-widget.sh

echo ""
echo "==================================="
echo "   FIXING TRIVIA WIDGET"
echo "==================================="
echo ""

# Remove ALL old shortcuts
echo "[...] Removing old shortcuts..."
rm -f ~/.shortcuts/Trivia* 2>/dev/null
rm -f ~/.shortcuts/trivia* 2>/dev/null
rm -f "$HOME/.shortcuts/Trivia Game" 2>/dev/null
ls ~/.shortcuts/ 2>/dev/null && echo "[OK] Remaining shortcuts above" || echo "[OK] Shortcuts folder empty"

echo ""

# Create fresh shortcut pointing to the correct script
GAME_DIR="/sdcard/trivial-tile-trivia-portable"
SHORTCUT_DIR="$HOME/.shortcuts"
mkdir -p "$SHORTCUT_DIR"

cat > "$SHORTCUT_DIR/Trivia Game" << 'SCRIPT'
#!/bin/bash
cd /sdcard/trivial-tile-trivia-portable
bash start-android.sh
SCRIPT

chmod +x "$SHORTCUT_DIR/Trivia Game"

echo "[OK] New widget created!"
echo ""
echo "Contents of shortcut:"
echo "---"
cat "$SHORTCUT_DIR/Trivia Game"
echo "---"
echo ""
echo "Now go to your home screen and tap"
echo "the Trivia Game widget. It should"
echo "show [OK] messages when it launches."
echo ""
