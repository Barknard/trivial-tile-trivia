# Four-device test

Drives a real game across four isolated browser sessions — host, TV board, and
two players' phones — against any running server, and checks what each device
actually sees.

```bash
npm install playwright        # once
node four-device-test.js                              # server on localhost:5000
BASE=http://192.168.1.42:5000 node four-device-test.js  # the tablet, over WiFi
OFFLINE=1 node four-device-test.js                    # WiFi with no internet
```

`BASE` can point at the Android app while it is hosting — that is the closest
thing to a real party without four devices on the table.

What it checks:

1. The host starts a game and gets a session code, with a populated board grid.
2. The server reports that code, which is how the app builds its links.
3. The board opens from `/board?gameId=CODE` and joins itself — no code typing.
4. Both players open `/?gameId=CODE`, find the code already filled in, and join.
5. The host screen lists both players by name.
6. The host picks a clue and the board follows.
7. The host opens the buzzers, a player buzzes, and the host registers it.

`OFFLINE=1` makes everything except the game server hang instead of failing
fast, which is what a party WiFi with no internet does. It is how the
render-blocking Google Fonts stylesheet — which used to leave every screen
blank — was found.

# Question bank test

```bash
node question-bank-test.js            # every theme/age combination listed in the file
QUICK=1 node question-bank-test.js    # three of them
```

Picks a theme and an age in the host settings panel, presses **Generate New
Board**, and checks the board that comes back is built from
`public/runtime-questions.json` — not the built-in SANTA/REINDEER demo board,
and not the `Sample <theme> question N` filler the game substitutes when a
category holds fewer than five questions for the chosen age.

Worth knowing when reading its output: two tiles on every board are trial
tiles, which open a wager rather than a clue, so the test tries several tiles
before deciding.

# Board order test

```bash
node board-order-test.js
```

Opens a board and reads the tiles column by column: the cheap rows must hold
the easy clues. It exists because the generator used to shuffle a category's
clues and then hand them out in that order, so a $200 tile could be harder than
the $1000 above it.

# Reconnect test

```bash
node reconnect-test.js
```

A phone drops out mid-game — does it come back as the same player, with the
money it had won? Covers the three ways a phone disappears at a party (signal
drops, screen locks, tab reloaded), plus the borrowed-phone case: when nobody
is answering to a name any more, another device may claim that seat and inherit
its score, while a name still in active use is refused.

# Capacity tests

```bash
node capacity-test.js          # real browser tabs, the honest measurement
node relay-capacity-test.js    # raw sockets, for the far end of the curve
```

How many phones can be in one game before the host starts dropping buzzes.
`capacity-test.js` opens real player pages and is limited by the machine
running it; `relay-capacity-test.js` speaks the protocol directly, so it can
push past what a laptop can render.

# Full simulation

```bash
node full-simulation.js
SHOTS=/tmp/shots node full-simulation.js   # also writes screenshots
```

One test that plays a whole game across five devices: host tablet, TV board and
four phones. Scanning in, board control, picking a clue and watching it swell
and dissolve into the question, a buzz race, a right answer, a wrong answer and
its lockout, changing theme and age mid-game, round two, a phone reloading, a
borrowed phone taking over a seat, and the TV being power-cycled. Finishes by
asserting no screen threw an uncaught error.

This is the one to run before shipping.
