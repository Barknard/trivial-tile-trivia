# Trivial Tile Trivia — Android app

<img src="../docs/icon-512.png" width="120" align="right" alt="App icon" />

Open the app, and the tablet is already hosting the game on whatever WiFi it is
connected to. No Termux, no Node.js, no shell scripts, no `termux-setup-storage`.

The app shows the address players type into their phones (plus a QR code they
can scan), and has buttons to open the host controls and the board.

## Getting it onto the tablet

1. Go to the repo's [Releases](https://github.com/Barknard/trivial-tile-trivia/releases)
   page on the tablet.
2. Download `trivial-tile-trivia-v<number>.apk` from the newest release.
3. Open the downloaded file, and allow Chrome (or your file manager) to install
   apps when Android asks.

Every push to `master` that touches `android/` or `public/` builds a new APK and
publishes it as a release, so the newest build is always at the top of that page.

## How updates work

Two separate things update, for two different reasons:

| What changed | How it reaches the tablet |
| --- | --- |
| Questions, board layout, sounds, anything in `public/` | The app checks GitHub each time it starts and downloads just the changed files. Nothing to install. |
| The app itself (`android/`) | The app notices a newer release and offers an **Install app update** button. Android asks you to confirm the install. |

Content updates apply straight away when no game is running, otherwise they are
staged and applied the next time the app starts — a game in progress is never
disturbed. If the tablet is offline, the app simply hosts the copy it already
has.

Adding questions is unchanged: drop JSON files in `output/`, commit, push. A
GitHub Action merges them into `public/runtime-questions.json`, and the app picks
that up on its next launch.

## What's inside

```
android/app/src/main/java/com/barknard/trivialtile/
  MainActivity.java          the single screen: address, QR code, buttons
  HostService.java           foreground service that keeps the server alive
  HostState.java             shared state between the two
  server/GameServer.java     HTTP server: static files, ranges, /api endpoints
  server/WsConnection.java   WebSocket framing (RFC 6455)
  server/RelayHub.java       rooms and message relay - a port of server/routes.ts
  server/NetUtils.java       picks the WiFi address players should use
  update/ContentStore.java   unpacks bundled content, applies downloaded updates
  update/ContentUpdater.java diffs public/ against GitHub, downloads what changed
  update/ApkUpdater.java     installs newer APKs from GitHub Releases
```

The web app in `public/` is bundled into the APK unchanged, so a fresh install
works with no network at all. The Java server speaks exactly the same protocol
the old Node server did (`CREATE_ROOM`, `JOIN_ROOM`, `TO_HOST`, `TO_CLIENT`,
`BROADCAST`, `PING`), so the browser code did not need a single change.

## Building it yourself

Needs JDK 17 and the Android SDK (compileSdk 35):

```bash
cd android
gradle assembleRelease      # or ./gradlew if you add a wrapper
# APK lands in app/build/outputs/apk/release/
```

`gradle syncWebContent` copies `../public` into the APK's assets; it runs
automatically as part of the build.

### Signing

Builds are signed with `keystore/trivia-signing.jks` (password `trivialtile`) so
that a downloaded APK always installs over the previous one. That key is not a
secret — it exists purely so sideloaded updates keep working. To use your own
key instead, set `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD` in the environment (or as GitHub secrets wired into the workflow)
and the build will prefer those.

Note that switching keys means the next APK will not install over an app signed
with the old one — uninstall first if you ever change it.

### The icon

`tools/IconGen.java` draws the launcher icon set:

```bash
cd android && java tools/IconGen.java app/src/main/res
```

## Notes

- The server listens on port 5000; if something else has it, the app walks up to
  5011 and shows whichever port it got.
- The app holds a WiFi lock and a CPU wake lock while hosting so the server
  doesn't drop when the screen turns off, and keeps the screen awake while its
  own screen is in front.
- Casting the board: tap **Open board**, which opens it in its own Chrome window
  and copies the link. Cast that window to the TV and switch back to the host
  window on the tablet.
