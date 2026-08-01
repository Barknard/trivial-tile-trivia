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
