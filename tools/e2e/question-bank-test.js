/*
 * Pick a theme and age in the settings panel, press "Generate New Board", and
 * check the board is built from the question bank rather than the built-in
 * demo categories or "Sample <theme> question N" filler.
 *
 *   node question-bank-test.js            # every combination below
 *   QUICK=1 node question-bank-test.js    # three of them
 *   BASE=http://192.168.1.42:5000 node question-bank-test.js
 */
const { chromium } = require('playwright');

const BASE = process.env.BASE || 'http://127.0.0.1:5099';
const CHROME = process.env.CHROME || undefined;
const SETTINGS_BUTTON = 3;
const DEMO = ['SANTA', 'REINDEER', 'SNOWMEN', 'SONGS', 'CHRISTMAS', 'WINTER'];

const COMBOS = (process.env.QUICK ? [
  ['Christmas', 'kids_4_under', 'Little Ones'],
  ['New Year', 'teenagers', 'Teenagers'],
  ['Birthday', 'no_humanity', 'No Humanity'],
  ['Beach', 'kids_4_under', 'Little Ones'],
] : [
  ['Christmas', 'kids_4_under', 'Little Ones'],
  ['Christmas', 'no_humanity', 'No Humanity'],
  ['New Year', 'teenagers', 'Teenagers'],
  ['Birthday', 'kids_10_under', 'Kids'],
  ['Birthday', 'adults', 'Adults'],
  ["Valentine's", 'adults', 'Adults'],
  ['Beach', 'kids_4_under', 'Little Ones'],
  ['Beach', 'teenagers', 'Teenagers'],
  ['Beach', 'no_humanity', 'No Humanity'],
]);

let bad = 0;
(async () => {
  const browser = await chromium.launch(CHROME ? { executablePath: CHROME } : {});
  const rows = [];
  for (const [theme, audience, ageLabel] of COMBOS) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    await ctx.addInitScript((aud) => {
      localStorage.setItem('tile-trivia-audience', aud);
      localStorage.removeItem('tile-trivia-categories');
      localStorage.removeItem('tile-trivia-used-questions');
    }, audience);
    const page = await ctx.newPage();
    const vaultLog = [];
    page.on('console', (m) => {
      const t = m.text();
      if (t.includes('generateBoardFromVault') || t.includes('[Vault]')) vaultLog.push(t.slice(0, 150));
    });

    await page.goto(BASE + '/host', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1700);
    await page.locator('button').nth(SETTINGS_BUTTON).click({ timeout: 3000 }).catch(() => {});
    await page.waitForTimeout(600);
    await page.getByText(theme, { exact: true }).first().click({ timeout: 3000 }).catch(() => {});
    await page.waitForTimeout(400);
    await page.locator('[data-testid="button-generate-board"]').click({ timeout: 4000 }).catch((e) => {
      rows.push({ theme, ageLabel, err: 'could not press Generate New Board' });
    });
    await page.waitForTimeout(1200);
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(900);

    const cats = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.grid.grid-cols-6 > div:not(button)'))
        .map((d) => (d.innerText || '').trim()).filter(Boolean).slice(0, 6));

    // Two tiles per board are trial tiles, which open a wager instead of a
    // clue - try a few until a real clue comes up.
    let clue = '';
    for (const idx of [0, 1, 2, 6, 7]) {
      const tile = page.locator('.grid.grid-cols-6 > button').nth(idx);
      if (!(await tile.count())) continue;
      await tile.click({ timeout: 3000 }).catch(() => {});
      await page.waitForFunction(() => /READ THIS ANSWER ALOUD/i.test(document.body.innerText),
        null, { timeout: 4000 }).catch(() => {});
      clue = await page.evaluate(() => {
        const body = document.body.innerText;
        const m = body.match(/READ THIS ANSWER ALOUD:?\s*\n+([^\n]+)/i);
        const a = body.match(/CORRECT QUESTION[^\n]*:?\s*\n+([^\n]+)/i);
        return ((m ? m[1] : '') + (a ? '   >>> ' + a[1] : '')).trim();
      });
      if (clue.length > 10) break;
      await page.keyboard.press('Escape').catch(() => {});
      await page.waitForTimeout(400);
    }

    const isDemo = cats.length > 0 && cats.every((c) => DEMO.includes(c.toUpperCase()));
    const isSample = /Sample .*question \d/i.test(clue);
    const good = cats.length >= 6 && !isDemo && !isSample && clue.length > 10;
    if (!good) bad++;
    rows.push({ theme, ageLabel, cats, clue: clue.slice(0, 58), isDemo, isSample, good,
                counts: vaultLog.filter((l) => l.includes('Total questions')).slice(0, 2) });
    await ctx.close();
  }

  for (const r of rows) {
    if (r.err) { console.log(`${r.theme} / ${r.ageLabel}: ${r.err}`); continue; }
    console.log(`${String(r.theme).padEnd(12)} ${String(r.ageLabel).padEnd(12)} ${r.good ? 'REAL' : (r.isDemo ? 'demo board' : r.isSample ? 'SAMPLE FILLER' : 'unclear')}`);
    console.log(`   categories: ${r.cats.join(', ').slice(0, 88)}`);
    console.log(`   clue: ${r.clue}`);
    if (r.counts && r.counts.length) console.log(`   vault: ${r.counts[0]}`);
  }
  console.log(bad === 0 ? '\nALL COMBINATIONS BUILD A REAL BOARD FROM THE BANK'
                        : `\n${bad} of ${rows.length} combinations did not`);
  await browser.close();
})();
