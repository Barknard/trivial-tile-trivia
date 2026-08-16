/*
 * How many players can actually play at once?
 *
 * Runs a real game and ramps the number of player devices up, each one its own
 * browser context (its own storage, so its own player identity - pages sharing
 * a context would share a player id and collide). At each level it makes every
 * player buzz at the same moment and measures what the host actually receives.
 *
 *   node capacity-test.js                       # default ramp
 *   LEVELS=5,10,20,40 node capacity-test.js
 *   BASE=http://192.168.1.42:5000 node capacity-test.js   # against the tablet
 *
 * What "capacity" means here: every player joined, every buzz reached the host,
 * and the board kept up. The first level where that stops being true is the
 * ceiling.
 */
const { chromium } = require('playwright');

const BASE = process.env.BASE || 'http://127.0.0.1:5000';
const CHROME = process.env.CHROME || undefined;
const LEVELS = (process.env.LEVELS || '5,10,20,30').split(',').map(Number);
const SETTINGS_BUTTON = 3;

const pct = (a, b) => (b ? Math.round((100 * a) / b) : 0);

async function runLevel(browser, players) {
  const started = Date.now();
  const hostCtx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const host = await hostCtx.newPage();
  await host.goto(BASE + '/host', { waitUntil: 'domcontentloaded' });
  await host.waitForTimeout(1800);
  const gameId = (await host.evaluate(() => document.body.innerText))
    .match(/SESSION\s*\n?\s*([A-Z0-9]{4,6})/)?.[1];
  if (!gameId) throw new Error('host never produced a session code');

  // Every player is a separate device.
  const contexts = [];
  const pages = [];
  for (let i = 0; i < players; i++) {
    const ctx = await browser.newContext({
      viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true,
    });
    const page = await ctx.newPage();
    contexts.push(ctx);
    pages.push(page);
  }

  const joinStart = Date.now();
  const joined = await Promise.all(pages.map(async (page, i) => {
    try {
      await page.goto(`${BASE}/?gameId=${gameId}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
      await page.waitForTimeout(600);
      await page.getByPlaceholder(/TriviaMaster/i).fill(`P${i + 1}`, { timeout: 15000 });
      await page.getByRole('button', { name: /ENTER GAME/i }).click({ timeout: 15000 });
      await page.waitForFunction(() => !/Player Entry/i.test(document.body.innerText),
        null, { timeout: 20000 });
      return true;
    } catch {
      return false;
    }
  }));
  const joinMs = Date.now() - joinStart;
  const joinedCount = joined.filter(Boolean).length;

  // What the server thinks is connected (the board counts as a client too).
  const status = await fetch(`${BASE}/api/status`).then((r) => r.json()).catch(() => ({}));

  // Host picks a clue and opens the buzzers.
  await host.locator('.grid.grid-cols-6 > button').first().click({ timeout: 10000 }).catch(() => {});
  await host.waitForTimeout(1200);
  await host.getByRole('button', { name: /OPEN BUZZERS/i }).click({ timeout: 10000 }).catch(() => {});
  await host.waitForTimeout(1500);

  // Everyone buzzes at once - the moment that matters.
  const buzzStart = Date.now();
  await Promise.all(pages.map((page) =>
    page.locator('button').filter({ hasText: /BUZZ/i }).first().click({ timeout: 12000 })
      .catch(() => page.locator('button').first().click({ timeout: 12000 }).catch(() => {}))));
  const buzzSendMs = Date.now() - buzzStart;

  // Give the relay a moment, then see how many the host actually registered.
  await host.waitForTimeout(2500);
  const hostText = await host.evaluate(() => document.body.innerText);
  const registered = Array.from({ length: players }, (_, i) => `P${i + 1}`)
    .filter((name) => new RegExp(`\\b${name}\\b`).test(hostText)).length;

  const memory = await host.evaluate(() => (performance.memory
    ? Math.round(performance.memory.usedJSHeapSize / 1048576) : null)).catch(() => null);

  for (const ctx of contexts) await ctx.close();
  await hostCtx.close();

  return {
    players,
    joined: joinedCount,
    joinMs,
    serverPlayers: status.players ?? null,
    registered,
    buzzSendMs,
    hostHeapMb: memory,
    totalMs: Date.now() - started,
  };
}

(async () => {
  const browser = await chromium.launch(CHROME ? { executablePath: CHROME } : {});
  const rows = [];
  console.log(`Ramping player count against ${BASE}\n`);
  for (const players of LEVELS) {
    process.stdout.write(`  ${String(players).padStart(3)} players... `);
    try {
      const r = await runLevel(browser, players);
      rows.push(r);
      const ok = r.joined === players && r.registered === players;
      console.log(`joined ${r.joined}/${players} in ${(r.joinMs / 1000).toFixed(1)}s, ` +
        `host saw ${r.registered}/${players} buzz  ${ok ? 'OK' : '<-- degraded'}`);
      if (!ok) break;
    } catch (err) {
      console.log(`FAILED: ${String(err.message).slice(0, 80)}`);
      rows.push({ players, failed: String(err.message).slice(0, 60) });
      break;
    }
  }

  console.log('\n' + '='.repeat(96));
  console.log('players  joined   join time   server saw   buzzes host registered   host heap');
  console.log('='.repeat(96));
  for (const r of rows) {
    if (r.failed) { console.log(`${String(r.players).padStart(7)}  ${r.failed}`); continue; }
    console.log(
      `${String(r.players).padStart(7)}  ${String(r.joined).padStart(3)}/${String(r.players).padEnd(3)}` +
      ` ${((r.joinMs / 1000).toFixed(1) + 's').padStart(10)}` +
      ` ${String(r.serverPlayers ?? '-').padStart(12)}` +
      ` ${(r.registered + '/' + r.players).padStart(20)} (${pct(r.registered, r.players)}%)` +
      ` ${(r.hostHeapMb ? r.hostHeapMb + ' MB' : '-').padStart(10)}`);
  }
  const best = rows.filter((r) => !r.failed && r.joined === r.players && r.registered === r.players).pop();
  console.log('\n' + (best
    ? `Clean play verified up to ${best.players} simultaneous players.`
    : 'No level completed cleanly.'));
  await browser.close();
})();
