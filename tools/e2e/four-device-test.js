/*
 * Four isolated browser contexts standing in for four devices: the host
 * tablet, the TV board, and two players' phones. Point it at any running
 * server - the Android app included, over the network:
 *
 *   node four-device-test.js                       # localhost:5000
 *   BASE=http://192.168.1.42:5000 node four-device-test.js
 *   OFFLINE=1 node four-device-test.js             # pretend WiFi has no internet
 *
 * Needs `npm install playwright` (and a Chromium; set CHROME to point at one).
 */
const { chromium } = require('playwright');

const BASE = (process.env.BASE || `http://127.0.0.1:${process.env.PORT || 5000}`).replace(/\/$/, '');
const LAN_HOST = new URL(BASE).hostname;
const CHROME = process.env.CHROME || undefined;

const results = [];
let failures = 0;
function check(name, ok, detail) {
  results.push(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ' :: ' + String(detail).slice(0, 220) : ''}`);
  if (!ok) failures++;
}

async function device(browser, label, size) {
  const context = await browser.newContext({ viewport: size });
  const page = await context.newPage();
  if (process.env.OFFLINE) {
    // No working internet: anything off-LAN hangs rather than failing fast.
    await page.route((url) => url.hostname !== LAN_HOST, async (route) => {
      await new Promise((r) => setTimeout(r, 120000));
      await route.abort().catch(() => {});
    });
  }
  page.on('pageerror', (e) => console.log(`[${label}] PAGEERROR ${String(e).slice(0, 160)}`));
  page.on('console', (m) => {
    const t = m.text();
    if (m.type() === 'error' && !t.includes('fonts.googleapis')) console.log(`[${label}] console.error ${t.slice(0, 160)}`);
  });
  return { context, page, label };
}

const text = (page) => page.evaluate(() => document.body.innerText.replace(/\n{2,}/g, '\n'));

(async () => {
  const browser = await chromium.launch(CHROME ? { executablePath: CHROME } : {});

  const host = await device(browser, 'host', { width: 1280, height: 800 });
  const board = await device(browser, 'board', { width: 1920, height: 1080 });
  const p1 = await device(browser, 'player1', { width: 390, height: 844 });
  const p2 = await device(browser, 'player2', { width: 412, height: 915 });

  // ---- Device 1: the host starts a game -----------------------------------
  await host.page.goto(BASE + '/', { waitUntil: 'domcontentloaded' });
  await host.page.waitForTimeout(1200);
  await host.page.getByRole('tab', { name: /HOST GAME/i }).click();
  await host.page.getByRole('button', { name: /START HOSTING/i }).click();
  await host.page.waitForTimeout(3000);
  const hostText = await text(host.page);
  const gameId = (hostText.match(/SESSION\s*\n?\s*([A-Z0-9]{4,6})/) || [])[1];
  check('host lands on a live game with a session code', Boolean(gameId), gameId || hostText.slice(0, 200));
  check('host URL is the host view', host.page.url().includes('/host'), host.page.url());
  check('host board grid is populated', /\b(200|400|600)\b/.test(hostText), hostText.slice(0, 160));
  await host.page.screenshot({ path: 'dev1-host.png' });
  if (!gameId) {
    console.log(results.join('\n'));
    process.exit(1);
  }

  // ---- The app's own link building (same source the Android UI uses) -------
  const live = await fetch(`${BASE}/api/status`).then((r) => r.json());
  check('server reports the live game code to the app', live.gameId === gameId, JSON.stringify(live));
  const boardLink = `${BASE}/board?gameId=${live.gameId}`;
  const joinLink = `${BASE}/?gameId=${live.gameId}`;

  // ---- Device 2: the TV board joins by link --------------------------------
  await board.page.goto(boardLink, { waitUntil: 'domcontentloaded' });
  await board.page.waitForTimeout(3000);
  const boardText = await text(board.page);
  check('board auto-joins from the linked URL (no code typing)',
    !/Enter the Game ID/i.test(boardText), boardText.slice(0, 160));
  check('board shows the game grid', /\b(200|400|600)\b/.test(boardText), boardText.slice(0, 160));
  await board.page.screenshot({ path: 'dev2-board.png' });

  // ---- Devices 3 and 4: players join --------------------------------------
  async function joinAsPlayer(dev, name) {
    await dev.page.goto(joinLink, { waitUntil: 'domcontentloaded' });
    await dev.page.waitForTimeout(900);
    const prefilled = await dev.page.getByPlaceholder('ABCD').inputValue();
    check(`${dev.label}: join link pre-filled the game code`, prefilled === gameId, prefilled);
    await dev.page.getByPlaceholder(/TriviaMaster/i).fill(name);
    await dev.page.getByRole('button', { name: /ENTER GAME/i }).click();
    await dev.page.waitForTimeout(2500);
    return text(dev.page);
  }

  const p1Text = await joinAsPlayer(p1, 'Ellie');
  check('player 1 joined the game', !/Player Entry/i.test(p1Text), p1Text.slice(0, 160));
  await p1.page.screenshot({ path: 'dev3-player1.png' });

  const p2Text = await joinAsPlayer(p2, 'Sam');
  check('player 2 joined the game', !/Player Entry/i.test(p2Text), p2Text.slice(0, 160));
  await p2.page.screenshot({ path: 'dev4-player2.png' });

  // ---- The host should see both players ------------------------------------
  await host.page.waitForTimeout(1500);
  const hostAfterJoins = await text(host.page);
  check('host sees player 1 by name', /Ellie/i.test(hostAfterJoins), hostAfterJoins.slice(0, 300));
  check('host sees player 2 by name', /Sam/i.test(hostAfterJoins), hostAfterJoins.slice(0, 300));

  // ---- Server-side view ----------------------------------------------------
  const status = await fetch(`${BASE}/api/status`).then((r) => r.json());
  check('server reports one room with two players', status.rooms === 1 && status.players >= 2, JSON.stringify(status));
  const room = await fetch(`${BASE}/api/room/${gameId}`).then((r) => r.json());
  check('room lookup finds the game', room.exists === true, JSON.stringify(room));

  // ---- Play a clue: host picks a tile, everyone follows ---------------------
  const tile = host.page.locator('button', { hasText: /^200$/ }).first();
  await tile.click().catch(() => {});
  await host.page.waitForTimeout(2000);
  const boardDuringClue = await text(board.page);
  const hostDuringClue = await text(host.page);
  check('board follows the host into the clue',
    boardDuringClue !== boardText && boardDuringClue.length > 0, boardDuringClue.slice(0, 200));
  await board.page.screenshot({ path: 'dev2-board-clue.png' });
  await host.page.screenshot({ path: 'dev1-host-clue.png' });

  // ---- Host opens the buzzers, player 1 buzzes ------------------------------
  await host.page.getByRole('button', { name: /OPEN BUZZERS/i }).click({ timeout: 5000 }).catch(() => {});
  await host.page.waitForTimeout(1500);
  const hostBuzzersOpen = await text(host.page);
  check('host opened the buzzers', /LOCK|BUZZ/i.test(hostBuzzersOpen), hostBuzzersOpen.slice(0, 120));
  const p1Buzzing = await text(p1.page);
  check('player 1 sees the buzzer go live', /BUZZ/i.test(p1Buzzing), p1Buzzing.slice(0, 160));
  await p1.page.locator('button').filter({ hasText: /BUZZ/i }).first().click({ timeout: 5000 })
    .catch(() => p1.page.locator('button').first().click({ timeout: 5000 }).catch(() => {}));
  await host.page.waitForTimeout(1800);
  const hostAfterBuzz = await text(host.page);
  check('host registers the buzz from player 1',
    /Ellie/i.test(hostAfterBuzz) && hostAfterBuzz !== hostDuringClue, hostAfterBuzz.slice(0, 220));
  await p1.page.screenshot({ path: 'dev3-player1-buzz.png' });
  await host.page.screenshot({ path: 'dev1-host-buzz.png' });

  console.log('\n' + results.join('\n'));
  console.log(failures === 0 ? '\nALL 4-DEVICE CHECKS PASSED' : `\n${failures} FAILURE(S)`);
  await browser.close();
  process.exit(failures === 0 ? 0 : 1);
})().catch((e) => {
  console.log(results.join('\n'));
  console.error('TEST CRASH:', e);
  process.exit(1);
});
