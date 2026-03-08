TRIVIAL TILE TRIVIA - Portable Edition
======================================

A local network multiplayer trivia game. Host from any device,
players join with their phones as buzzers!

QUICK START BY PLATFORM
=======================

WINDOWS:
  1. Double-click "start-windows.bat"
  2. If prompted, install Node.js from nodejs.org
  3. Browser opens automatically with Host and Board views

MAC:
  1. Double-click "start-mac.command"
  2. If prompted, allow it to run in System Preferences > Security
  3. It will install Node.js via Homebrew if needed
  4. Browser opens automatically with Host and Board views

LINUX:
  1. Ensure Node.js is installed: sudo apt install nodejs
  2. Run: bash start-mac.command (works on Linux too)

========================================
ANDROID SETUP (Prerequisites Required)
========================================

*** IMPORTANT: Android requires manual setup first ***
Due to Android security, you must install these apps BEFORE
the game can run. This is a one-time setup.

STEP 1: Install Termux (Required)
---------------------------------
Download from F-Droid (NOT Google Play Store):
https://f-droid.org/packages/com.termux/

STEP 2: Install Termux:API (Required)  
--------------------------------------
Download from F-Droid:
https://f-droid.org/packages/com.termux.api/

STEP 3: First-Time Termux Setup
-------------------------------
Open Termux and run this command to allow file access:
  termux-setup-storage

Press "Allow" when prompted.

STEP 4: Run the Game
--------------------
In Termux, run:
  bash /sdcard/trivial-tile-trivia-portable/start-android.sh

The script will auto-install Node.js and open the game!

ONE-TAP SHORTCUT (Optional)
---------------------------
For future launches, create a home screen shortcut:

1. Install Termux:Widget from F-Droid:
   https://f-droid.org/packages/com.termux.widget/

2. Create shortcut folder:
   mkdir -p ~/.shortcuts

3. Create the shortcut script:
   echo 'bash /sdcard/trivial-tile-trivia-portable/start-android.sh' > ~/.shortcuts/Trivia
   chmod +x ~/.shortcuts/Trivia

4. Add Termux:Widget to your home screen
5. Tap "Trivia" to launch the game with one tap!

HOW TO PLAY
===========

1. HOST VIEW (your control panel):
   - Create/load trivia categories
   - Select questions, control buzzers
   - Mark answers correct/incorrect

2. BOARD VIEW (show on TV/projector):
   - Shows the game board to all players
   - Enter the Game ID from Host screen
   - IMPORTANT: Open the Board in its own Chrome WINDOW
     (not just a separate tab — a completely separate window!)
   - This lets you mirror just the Board while keeping
     the Host controls on a different window

3. PLAYER VIEW (phones):
   - Players scan QR code or go to http://[YOUR-IP]:5000
   - Enter name and Game ID to join
   - Tap to buzz in!

NETWORK SETUP
=============

All devices must be on the same WiFi network.
The server shows its IP address when it starts.
Players connect to: http://[IP]:5000

SCREEN SHARING / MIRRORING SETUP
=================================

To host the game on a tablet while sharing the Board on a TV:

1. Open the BOARD view in its own Chrome WINDOW (not a tab)
2. Open the HOST view in a separate Chrome WINDOW
3. Select the Board window, then go to:
   Mirror > Mirror Chrome Only
4. The Board appears on your TV/external display
   - You can minimize the Board's mini-view on the tablet
5. Switch to the Host window to control the game
   - You now have full host controls on your tablet
     while the Board is shared to the TV!

NOTE: This must be two separate Chrome WINDOWS, not two
tabs in the same window. Mirroring works per-window,
so keeping them in separate windows lets you mirror
only the Board while the Host stays private.

TIPS
====

- Board URL without Game ID shows a text box to enter the Game ID
- Phone screens stay awake automatically during the game
- Trial Tiles are like Daily Doubles - players can wager!

FILES
=====

start-windows.bat  - Windows launcher (double-click)
start-mac.command  - Mac/Linux launcher (double-click)
start-android.sh   - Android/Termux launcher
android-setup.sh   - Alternative Android setup script
server.cjs         - The game server (Node.js)
public/            - Game interface files

TROUBLESHOOTING
===============

"Node.js not found":
  Download from https://nodejs.org and install

"Can't connect from other devices":
  - Check all devices are on same WiFi
  - Try disabling firewall temporarily
  - Use the IP shown in terminal, not "localhost"

"Board shows QR code instead of game":
  - Enter the Game ID in the text box
  - Or add ?gameId=XXXX to the URL

Enjoy your trivia night!
