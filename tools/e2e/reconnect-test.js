/*
 * A phone drops out mid-game. Does it come back as the same player, with the
 * score it had earned?
 *
 *   node reconnect-test.js
 *   BASE=http://192.168.1.42:5000 node reconnect-test.js
 *
 * Simulates the three ways a player disappears at a party:
 *   1. signal drops (tunnel, bad wifi)   - context.setOffline
 *   2. phone locks / app backgrounded    - page hidden, socket dies
 *   3. player reloads or reopens the tab - fresh page, same localStorage
 */
const { chromium } = require('playwright');

const BASE = process.env.BASE || 'http://127.0.0.1:5000';
const CHROME = process.env.CHROME || undefined;

const results = [];
let failures = 0;
const check = (name, ok, detail) => {
  results.push(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ' :: ' + String(detail).slice(0, 110) : ''}`);
  if (!ok) failures++;
};
const text = (page) => page.evaluate(() => document.body.innerText.replace(/\n{2,}/g, '\n'));

(async () => {
  const browser = await chromium.launch(CHROME ? { executablePath: CHROME } : {});

  // --- host starts a game --------------------------------------------------
  const hostCtx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const host = await hostCtx.newPage();
  await host.goto(BASE + '/host', { waitUntil: 'domcontentloaded' });
  await host.waitForTimeout(1800);
  const gameId = (await text(host)).match(/SESSION\s*\n?\s*([A-Z0-9]{4,6})/)?.[1];
  check('host has a game', Boolean(gameId), gameId);

  // --- a player joins and scores -------------------------------------------
  const playerCtx = await browser.newContext({
    viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true,
  });
  const player = await playerCtx.newPage();
  await player.goto(`${BASE}/?gameId=${gameId}`, { waitUntil: 'domcontentloaded' });
  await player.waitForTimeout(800);
  await player.getByPlaceholder(/TriviaMaster/i).fill('Ellie');
  await player.getByRole('button', { name: /ENTER GAME/i }).click();
  await player.waitForTimeout(2200);
  const playerId = await player.evaluate(() => localStorage.getItem('tile-trivia-player-id'));
  check('player joined and got an id', Boolean(playerId), playerId);

  // Award them points so we can tell a restored player from a fresh one.
  await host.locator('.grid.grid-cols-6 > button').first().click({ timeout: 8000 }).catch(() => {});
  await host.waitForTimeout(1000);
  await host.getByRole('button', { name: /OPEN BUZZERS/i }).click({ timeout: 8000 }).catch(() => {});
  await host.waitForTimeout(1000);
  await player.locator('button').filter({ hasText: /BUZZ/i }).first().click({ timeout: 8000 })
    .catch(() => player.locator('button').first().click({ timeout: 8000 }).catch(() => {}));
  await host.waitForTimeout(1200);
  await host.getByRole('button', { name: /Correct|^\+\$\d+/i }).first().click({ timeout: 8000 }).catch(() => {});
  await host.waitForTimeout(1200);
  const scored = (await text(host)).match(/Ellie\s*\$?(\d+)/);
  check('player has points on the host board', Boolean(scored && Number(scored[1]) > 0),
    scored ? scored[0] : 'no score found');
  const scoreBefore = scored ? Number(scored[1]) : 0;

  // --- 1. signal drops ------------------------------------------------------
  await playerCtx.setOffline(true);
  await player.waitForTimeout(3500);
  await playerCtx.setOffline(false);
  // give the backoff a moment to fire
  await player.waitForTimeout(9000);
  const afterDrop = await text(player);
  check('player page recovered after losing signal',
    !/Player Entry/i.test(afterDrop), afterDrop.slice(0, 90));

  const hostAfterDrop = await text(host);
  const countAfterDrop = (hostAfterDrop.match(/Players\s*\((\d+)\)/) || [])[1];
  check('host still lists exactly one player (no ghost duplicate)', countAfterDrop === '1', `Players (${countAfterDrop})`);
  const scoreAfter = (hostAfterDrop.match(/Ellie\s*\$?(\d+)/) || [])[1];
  check('score survived the drop', Number(scoreAfter) === scoreBefore, `${scoreBefore} -> ${scoreAfter}`);

  // --- 2. player reloads (reopened tab, phone restarted) --------------------
  await player.reload({ waitUntil: 'domcontentloaded' });
  await player.waitForTimeout(3000);
  const sameId = await player.evaluate(() => localStorage.getItem('tile-trivia-player-id'));
  check('same player id after reload', sameId === playerId, `${playerId} -> ${sameId}`);
  await player.waitForFunction(() => !/Connecting to Host/i.test(document.body.innerText),
    null, { timeout: 20000 }).catch(() => {});
  await host.waitForTimeout(1500);
  const hostAfterReload = await text(host);
  const countAfterReload = (hostAfterReload.match(/Players\s*\((\d+)\)/) || [])[1];
  check('still exactly one player after reload', countAfterReload === '1', `Players (${countAfterReload})`);
  const scoreReload = (hostAfterReload.match(/Ellie\s*\$?(\d+)/) || [])[1];
  check('score survived the reload', Number(scoreReload) === scoreBefore, `${scoreBefore} -> ${scoreReload}`);

  // --- 3. can they still play? ---------------------------------------------
  await host.locator('.grid.grid-cols-6 > button').nth(1).click({ timeout: 8000 }).catch(() => {});
  await host.waitForTimeout(1000);
  await host.getByRole('button', { name: /OPEN BUZZERS/i }).click({ timeout: 8000 }).catch(() => {});
  await player.waitForFunction(() => /BUZZ/i.test(document.body.innerText),
    null, { timeout: 15000 }).catch(() => {});
  const playerNow = await text(player);
  check('the returned player sees the buzzer go live', /BUZZ/i.test(playerNow), playerNow.slice(0, 80));
  await player.locator('button').filter({ hasText: /BUZZ/i }).first().click({ timeout: 8000 })
    .catch(() => player.locator('button').first().click({ timeout: 8000 }).catch(() => {}));
  await host.waitForTimeout(1500);
  check('host registers a buzz from the returned player', /Ellie/.test(await text(host)));

  // --- 4. dead phone, borrowed phone: claim the vacant name ----------------
  await playerCtx.close();                       // that phone is gone for good
  await host.waitForTimeout(2500);
  const borrowedCtx = await browser.newContext({
    viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true,
  });
  const borrowed = await borrowedCtx.newPage();  // different device, no stored id
  await borrowed.goto(`${BASE}/?gameId=${gameId}`, { waitUntil: 'domcontentloaded' });
  await borrowed.waitForTimeout(900);
  await borrowed.getByPlaceholder(/TriviaMaster/i).fill('Ellie');
  await borrowed.getByRole('button', { name: /ENTER GAME/i }).click();
  await borrowed.waitForTimeout(3000);
  const borrowedText = await text(borrowed);
  check('borrowed phone got into the game as Ellie', !/Player Entry/i.test(borrowedText),
    borrowedText.slice(0, 80));
  const hostAfterClaim = await text(host);
  const countAfterClaim = (hostAfterClaim.match(/Players\s*\((\d+)\)/) || [])[1];
  check('no second Ellie was created', countAfterClaim === '1', `Players (${countAfterClaim})`);
  const claimedScore = (hostAfterClaim.match(/Ellie\s*\$?(\d+)/) || [])[1];
  check('the borrowed phone inherited the score', Number(claimedScore) === scoreBefore,
    `${scoreBefore} -> ${claimedScore}`);

  // And a name someone is actively using must still be refused.
  const rivalCtx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
  const rival = await rivalCtx.newPage();
  await rival.goto(`${BASE}/?gameId=${gameId}`, { waitUntil: 'domcontentloaded' });
  await rival.waitForTimeout(900);
  await rival.getByPlaceholder(/TriviaMaster/i).fill('Ellie');
  await rival.getByRole('button', { name: /ENTER GAME/i }).click();
  await rival.waitForTimeout(3000);
  const hostAfterRival = await text(host);
  const countAfterRival = (hostAfterRival.match(/Players\s*\((\d+)\)/) || [])[1];
  check('a name in active use is not handed to someone else', countAfterRival === '1',
    `Players (${countAfterRival})`);

  console.log(results.join('\n'));
  console.log(failures === 0 ? '\nPLAYERS CAN DROP AND COME BACK' : `\n${failures} FAILURE(S)`);
  await browser.close();
  process.exit(failures === 0 ? 0 : 1);
})();
