/*
 * A whole game, played end to end, on the real server.
 *
 *   node full-simulation.js
 *   BASE=http://192.168.1.42:5000 node full-simulation.js
 *
 * Five browser contexts stand in for five devices: the host tablet, the TV
 * board, and four phones. Everything a party actually does happens here -
 * scanning in, picking clues, racing to the buzzer, being right, being wrong,
 * changing the theme mid-game, a phone dying and coming back, a borrowed phone
 * taking over a seat, and the TV being switched off and on again.
 */
const { chromium } = require('playwright');

const BASE = process.env.BASE || 'http://127.0.0.1:5000';
const CHROME = process.env.CHROME || undefined;
const SHOTS = process.env.SHOTS || null;

const results = [];
let failures = 0;
let scene = '';
const check = (name, ok, detail) => {
  results.push(`${ok ? 'PASS' : 'FAIL'}  ${scene} :: ${name}${detail ? '  [' + String(detail).slice(0, 100) + ']' : ''}`);
  if (!ok) failures++;
  return ok;
};
const note = (msg) => results.push(`....  ${scene} :: ${msg}`);
const text = (page) => page.evaluate(() => document.body.innerText.replace(/\n{2,}/g, '\n'));
const money = (body, name) => {
  const m = body.match(new RegExp(name + '\\s*\\n?\\s*(-?)\\$(\\d+)'));
  return m ? Number(m[1] + m[2]) : null;
};
const tap = async (locator, ms = 800) => {
  // force: some of these buttons animate forever, so they are never "stable"
  await locator.click({ timeout: 8000, force: true }).catch(() => {});
  await locator.page().waitForTimeout(ms);
};
// The host parks on the answer for three seconds after scoring before the
// board comes back. Wait that out rather than racing it.
const backToBoard = async (page) => {
  await page.waitForFunction(() => Array.from(document.querySelectorAll('div'))
    .filter((d) => /^\$\d+$/.test(d.innerText.trim())).length >= 20,
  null, { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(600);
};
// A missed answer reopens the buzzers for everyone else, so the clue is only
// really over once the host can move on.
const finishClue = async (host, board) => {
  const next = host.getByRole('button', { name: /Next Clue/i });
  await next.waitFor({ state: 'visible', timeout: 25000 }).catch(() => {});
  await tap(next, 600);
  await backToBoard(board);
};
const shot = async (page, name) => { if (SHOTS) await page.screenshot({ path: `${SHOTS}/${name}.png` }); };

const pageErrors = [];

(async () => {
  const browser = await chromium.launch(CHROME ? { executablePath: CHROME } : {});
  const watch = (page, who) => {
    page.on('pageerror', (e) => pageErrors.push(`${who}: ${e.message}`));
    return page;
  };
  const phone = async () => {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    return { ctx, page: watch(await ctx.newPage(), 'phone') };
  };

  // ---------------------------------------------------------- 1. host boots
  scene = '1 host';
  const hostCtx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const host = watch(await hostCtx.newPage(), 'host');
  await host.goto(BASE + '/host', { waitUntil: 'domcontentloaded' });
  await host.waitForTimeout(2200);
  const gameId = (await text(host)).match(/SESSION\s*\n?\s*([A-Z0-9]{4,6})/)?.[1];
  check('the host opens a session on its own', Boolean(gameId), gameId);
  if (!gameId) { console.log(results.join('\n')); process.exit(1); }

  // ------------------------------------------------------- 2. the TV board
  scene = '2 board';
  const boardCtx = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const board = watch(await boardCtx.newPage(), 'board');
  await board.goto(`${BASE}/board?gameId=${gameId}`, { waitUntil: 'domcontentloaded' });
  await board.waitForTimeout(2500);
  const overlay = await text(board);
  check('the board connects straight from the link', /JOIN THE GAME/i.test(overlay), overlay.split('\n')[0]);
  check('the join screen shows the game code', overlay.includes(gameId), gameId);
  const joinQr = await board.locator('img[alt="Join Game QR"]').getAttribute('src').catch(() => null);
  check('the join screen shows a QR', Boolean(joinQr && joinQr.startsWith('data:image')));

  // ------------------------------------------- 3. the QR carries the game id
  scene = '3 qr';
  const qrLogs = [];
  const { ctx: qrCtx, page: qrPage } = await phone();
  qrPage.on('console', (m) => { if (m.text().includes('[QR] Using URL:')) qrLogs.push(m.text()); });
  await qrPage.goto(`${BASE}/?gameId=${gameId}`, { waitUntil: 'domcontentloaded' });
  await qrPage.waitForTimeout(2500);
  check('the share QR encodes the game code, so nothing is typed',
    qrLogs.some((l) => l.includes(`gameId=${gameId}`)), qrLogs[qrLogs.length - 1] || 'no [QR] log');
  await qrCtx.close();

  // ------------------------------------------------------ 4. phones join in
  scene = '4 players';
  const names = ['Ellie', 'Marcus', 'Priya', 'Dev'];
  const phones = {};
  for (const name of names) {
    const { ctx, page } = await phone();
    await page.goto(`${BASE}/?gameId=${gameId}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(700);
    await page.getByPlaceholder(/TriviaMaster/i).fill(name);
    await page.getByRole('button', { name: /ENTER GAME/i }).click();
    await page.waitForTimeout(1400);
    phones[name] = { ctx, page };
  }
  await host.waitForTimeout(1500);
  const hostBody = await text(host);
  check('the host lists every phone', /Players\s*\(4\)/.test(hostBody), hostBody.match(/Players\s*\(\d+\)/)?.[0]);
  const boardNames = await text(board);
  check('every name is on the TV', names.every((n) => boardNames.toUpperCase().includes(n.toUpperCase())));

  // -------------------------------------------------- 5. the board goes live
  scene = '5 board live';
  await tap(host.getByRole('button', { name: /^QR$/i }), 1200);
  const liveBoard = await text(board);
  check('the join screen steps aside for the game board', !/JOIN THE GAME/i.test(liveBoard));
  const tiles = await board.$$eval('div', (ds) => ds.filter((d) => /^\$\d+$/.test(d.innerText.trim())).length);
  check('the board is full of clues', tiles >= 30, `${tiles} tiles`);
  await shot(board, 'sim-board-idle');

  // ----------------------------------------------- 6. who controls the board
  scene = '6 control';
  await tap(host.getByRole('button', { name: /randomize who goes first/i }), 1600);
  const chooserBoard = await text(board);
  check('the TV says out loud who picks the clue', /PICKS THE CLUE/i.test(chooserBoard),
    chooserBoard.split('\n').slice(-8).join(' | '));
  const chooser = names.find((n) => new RegExp(`PICKS THE CLUE\\s*\\n?\\s*${n}`, 'i').test(chooserBoard));
  note(`board control: ${chooser || 'unnamed'}`);
  const controlSize = await board.evaluate(() => {
    const el = Array.from(document.querySelectorAll('div'))
      .find((d) => /^picks the clue$/i.test(d.innerText.trim()));
    return el ? parseFloat(getComputedStyle(el).fontSize) : 0;
  });
  check('the control badge is readable from the sofa', controlSize >= 15, `${controlSize}px`);

  // ---------------------------------------------- 7. picking a clue, on show
  scene = '7 the pick';
  await host.locator('.grid.grid-cols-6 > button').nth(6).click({ timeout: 8000 });
  await board.waitForTimeout(420);
  const zoom = await board.evaluate(() => {
    const scaled = Array.from(document.querySelectorAll('div'))
      .filter((d) => /^\$\d+$/.test(d.innerText.trim()))
      .map((d) => ({ v: d.innerText.trim(), t: getComputedStyle(d).transform }))
      .filter((x) => x.t && x.t !== 'none' && parseFloat(x.t.split('(')[1]) > 1.2);
    return scaled;
  });
  check('the chosen tile swells on the big screen', zoom.length === 1, JSON.stringify(zoom));
  const stillBoard = await text(board);
  check('the question waits its turn', !/THE ANSWER IS/i.test(stillBoard));
  await shot(board, 'sim-board-zoom');
  await board.waitForTimeout(1600);
  const clueBoard = await text(board);
  check('the tile then becomes the question', /THE ANSWER IS/i.test(clueBoard),
    clueBoard.split('\n').slice(0, 4).join(' | '));
  await shot(board, 'sim-board-clue');

  // ------------------------------------------------------- 8. the buzz race
  scene = '8 buzzers';
  await tap(host.getByRole('button', { name: /OPEN BUZZERS/i }), 1200);
  const sawBuzz = [];
  for (const n of names) {
    const body = await text(phones[n].page);
    sawBuzz.push(/BUZZ/i.test(body));
  }
  check('every phone lights up', sawBuzz.every(Boolean), sawBuzz.join(','));
  await phones.Marcus.page.locator('button').filter({ hasText: /BUZZ/i }).first().click({ timeout: 8000 });
  await host.waitForTimeout(400);
  await phones.Priya.page.locator('button').filter({ hasText: /BUZZ/i }).first().click({ timeout: 8000 }).catch(() => {});
  await host.waitForTimeout(1400);
  const buzzed = await text(board);
  check('the TV shows who got in first', /BUZZED IN|ANSWERING/i.test(buzzed),
    buzzed.split('\n').slice(-10).join(' | '));
  await shot(board, 'sim-board-buzzed');

  // ---------------------------------------------------------- 9. a right answer
  scene = '9 correct';
  const before = money(await text(board), 'MARCUS');
  await tap(host.getByTestId('button-correct'), 1600);
  await backToBoard(board);
  const afterBody = await text(board);
  const after = money(afterBody, 'MARCUS');
  check('the score goes up on the TV', after !== null && before !== null && after > before, `${before} -> ${after}`);
  check('the winner now controls the board', /PICKS THE CLUE\s*\n?\s*MARCUS/i.test(afterBody),
    afterBody.split('\n').slice(-8).join(' | '));

  // -------------------------------------------------------- 10. a wrong answer
  scene = '10 wrong';
  await finishClue(host, board);
  await host.locator('.grid.grid-cols-6 > button:not([disabled])').nth(12)
    .click({ timeout: 8000, force: true }).catch(() => {});
  await board.waitForTimeout(1800);
  await tap(host.getByRole('button', { name: /OPEN BUZZERS/i }), 1000);
  await phones.Dev.page.locator('button').filter({ hasText: /BUZZ/i }).first().click({ timeout: 8000 }).catch(() => {});
  await host.waitForTimeout(1200);
  const devBefore = money(await text(board), 'DEV');
  await tap(host.getByTestId('button-incorrect'), 1600);
  const devAfter = money(await text(board), 'DEV');
  check('a wrong answer costs money', devAfter !== null && devAfter < devBefore, `${devBefore} -> ${devAfter}`);
  const devPhone = await text(phones.Dev.page);
  check('the phone that missed is locked out', /LOCKED|WAIT|WRONG/i.test(devPhone), devPhone.split('\n')[0]);

  // ------------------------------------------------- 11. scores read big
  scene = '11 legibility';
  const sizes = await board.evaluate(() => {
    const cards = Array.from(document.querySelectorAll('footer > div'));
    return cards.slice(0, 1).map((c) => {
      const kids = Array.from(c.querySelectorAll('div'));
      const name = kids.find((k) => /^[A-Z][A-Za-z]*$/.test(k.innerText.trim()));
      const score = kids.find((k) => /^-?\$\d+$/.test(k.innerText.trim()));
      return {
        name: name ? parseFloat(getComputedStyle(name).fontSize) : 0,
        score: score ? parseFloat(getComputedStyle(score).fontSize) : 0,
      };
    })[0];
  });
  check('player names are big', sizes && sizes.name >= 24, `${sizes && sizes.name}px`);
  check('scores are bigger still', sizes && sizes.score >= 40, `${sizes && sizes.score}px`);

  // ------------------------------------------ 12. new theme, new age, new board
  scene = '12 theme';
  await finishClue(host, board);
  const catsBefore = await board.$$eval('span', (ss) => ss.map((s) => s.innerText.trim())
    .filter((t) => /^[A-Z][A-Z &'!-]{2,}$/.test(t)).slice(0, 6).join(','));
  await tap(host.locator('header button').last(), 1200);
  await tap(host.getByTestId('theme-button-birthday'), 900);
  await tap(host.getByTestId('audience-button-kids'), 900);
  await tap(host.getByTestId('button-generate-board'), 2500);
  await host.keyboard.press('Escape');
  await board.waitForTimeout(1500);
  const catsAfter = await board.$$eval('span', (ss) => ss.map((s) => s.innerText.trim())
    .filter((t) => /^[A-Z][A-Z &'!-]{2,}$/.test(t)).slice(0, 6).join(','));
  check('changing theme and age rebuilds the board', catsBefore !== catsAfter, `${catsBefore} -> ${catsAfter}`);
  check('the new board still has questions', catsAfter.length > 3, catsAfter);
  await shot(board, 'sim-board-birthday');

  // ---------------------------------------------------------- 13. round two
  scene = '13 round 2';
  await tap(host.locator('header button').last(), 1200);
  await tap(host.getByRole('button', { name: /^Round 2$/ }), 1500);
  await host.keyboard.press('Escape');
  await board.waitForTimeout(1200);
  const r2 = await host.locator('.grid.grid-cols-6 > button:not([disabled])').count();
  check('round two puts every clue back in play', r2 >= 30, `${r2} playable tiles`);
  await tap(host.locator('header button').last(), 1000);
  await tap(host.getByRole('button', { name: /^Round 1$/ }), 1200);
  await host.keyboard.press('Escape');

  // ------------------------------------------------- 14. a phone comes back
  scene = '14 rejoin';
  const marcusScore = money(await text(board), 'MARCUS');
  await phones.Marcus.page.reload({ waitUntil: 'domcontentloaded' });
  await phones.Marcus.page.waitForFunction(
    () => !/Connecting to Host/i.test(document.body.innerText), null, { timeout: 25000 }).catch(() => {});
  await host.waitForTimeout(1500);
  const rejoinBody = await text(board);
  check('a reloaded phone is still the same player', /Players\s*\(4\)/.test(await text(host)),
    (await text(host)).match(/Players\s*\(\d+\)/)?.[0]);
  check('and keeps the money it won', money(rejoinBody, 'MARCUS') === marcusScore,
    `${marcusScore} -> ${money(rejoinBody, 'MARCUS')}`);

  // -------------------------------------------- 15. a borrowed phone takes over
  scene = '15 borrowed phone';
  await phones.Priya.ctx.close();
  await host.waitForTimeout(2500);
  const priyaScore = money(await text(board), 'PRIYA');
  const { ctx: borrowedCtx, page: borrowed } = await phone();
  await borrowed.goto(`${BASE}/?gameId=${gameId}`, { waitUntil: 'domcontentloaded' });
  await borrowed.waitForTimeout(900);
  await borrowed.getByPlaceholder(/TriviaMaster/i).fill('Priya');
  await borrowed.getByRole('button', { name: /ENTER GAME/i }).click();
  await borrowed.waitForTimeout(3000);
  check('a spare phone can pick up an empty seat', !/Player Entry/i.test(await text(borrowed)),
    (await text(borrowed)).split('\n')[0]);
  const claimed = await text(board);
  check('no duplicate player appears', /Players\s*\(4\)/.test(await text(host)),
    (await text(host)).match(/Players\s*\(\d+\)/)?.[0]);
  check('the seat keeps its score', money(claimed, 'PRIYA') === priyaScore,
    `${priyaScore} -> ${money(claimed, 'PRIYA')}`);
  phones.Priya = { ctx: borrowedCtx, page: borrowed };

  // ------------------------------------------------- 16. the TV is power cycled
  scene = '16 board reload';
  await board.reload({ waitUntil: 'domcontentloaded' });
  await board.waitForTimeout(4000);
  const rebooted = await text(board);
  check('the TV comes back with the game still on it',
    names.every((n) => rebooted.toUpperCase().includes(n.toUpperCase())), rebooted.split('\n').slice(-6).join(' | '));
  check('and the scores survived', money(rebooted, 'MARCUS') === marcusScore,
    `${marcusScore} -> ${money(rebooted, 'MARCUS')}`);

  // -------------------------------------------------------- 17. nothing broke
  scene = '17 health';
  check('no uncaught errors on any screen', pageErrors.length === 0, pageErrors.slice(0, 3).join(' / '));

  console.log(results.join('\n'));
  console.log(failures === 0 ? '\nFULL GAME OK' : `\n${failures} FAILURE(S)`);
  await browser.close();
  process.exit(failures === 0 ? 0 : 1);
})();
