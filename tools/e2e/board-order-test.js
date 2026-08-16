/*
 * Does the generated board place easy clues on the cheap rows?
 * Generate a board, read it out of localStorage, and look each clue up in the
 * bank to recover the difficulty it was authored at.
 */
const { chromium } = require('playwright');
const fs = require('fs');

const BASE = process.env.BASE || 'http://127.0.0.1:5000';
const bank = JSON.parse(fs.readFileSync(process.env.BANK || '../../public/runtime-questions.json', 'utf8'));

// clue text -> the value it was written for
const authored = new Map();
for (const theme of Object.values(bank))
  for (const aud of Object.values(theme))
    for (const cat of Object.values(aud))
      for (const q of cat.questions || []) authored.set(q.question, q.value);

(async () => {
  const b = await chromium.launch(process.env.CHROME ? { executablePath: process.env.CHROME } : {});
  let checked = 0, ordered = 0;
  for (const audience of ['kids_4_under', 'adults']) {
    const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } });
    await ctx.addInitScript((aud) => {
      localStorage.setItem('tile-trivia-audience', aud);
      localStorage.removeItem('tile-trivia-categories');
      localStorage.removeItem('tile-trivia-used-questions');
    }, audience);
    const page = await ctx.newPage();
    await page.goto(BASE + '/host', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1800);
    await page.locator('button').nth(3).click({ timeout: 3000 }).catch(() => {});
    await page.waitForTimeout(600);
    await page.locator('[data-testid="button-generate-board"]').click({ timeout: 4000 }).catch(() => {});
    await page.waitForTimeout(1500);

    const cats = await page.evaluate(() => JSON.parse(localStorage.getItem('tile-trivia-categories') || '[]'));
    console.log(`\n=== ${audience}: ${cats.length} categories on the board ===`);
    for (const cat of cats.slice(0, 3)) {
      const vals = cat.clues.map((c) => authored.get(c.clue));
      const known = vals.filter((v) => v !== undefined);
      const nonDecreasing = known.every((v, i, a) => i === 0 || a[i - 1] <= v);
      checked++;
      if (nonDecreasing) ordered++;
      console.log(`  ${cat.name.slice(0, 26).padEnd(26)} rows authored at: [${vals.join(', ')}]  ${nonDecreasing ? 'in order' : '*** OUT OF ORDER ***'}`);
      console.log(`     row1: ${(cat.clues[0].clue || '').slice(0, 62)}`);
      console.log(`     row5: ${(cat.clues[4].clue || '').slice(0, 62)}`);
    }
    await ctx.close();
  }
  console.log(`\n${ordered}/${checked} categories run easiest-first`);
  process.exitCode = ordered === checked ? 0 : 1;
  await b.close();
})();
