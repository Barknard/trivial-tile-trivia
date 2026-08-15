#!/usr/bin/env node
/*
 * Builds public/runtime-questions.json from the question files in output/.
 *
 * The game looks a clue up as bank[theme][audience][categoryName], where the
 * theme key comes from its own map:
 *
 *   christmas -> "christmas"   newyear -> "new_year"
 *   birthday  -> "birthday"    valentines -> "valentines"   anything else -> "general"
 *
 * so the keys written here have to be exactly those. It also lists a theme's
 * categories by unioning them across every audience, and quietly substitutes
 * "Sample <theme> question 1..5" placeholders for any category with fewer than
 * five questions for the chosen age. That is why a category has to be complete
 * for all five ages before it earns a place in the bank - a category that only
 * has adult questions would otherwise show up on the kids' board full of
 * sample junk.
 */
const fs = require('fs');
const path = require('path');

const QUESTIONS_DIR = process.argv[2] || path.join(__dirname, 'questions');
const OUTPUT_FILE = process.argv[3] || path.join(__dirname, 'public', 'runtime-questions.json');

const AUDIENCES = ['kids_4_under', 'kids_10_under', 'teenagers', 'adults', 'no_humanity'];

const audienceMap = {
  'kids_4_under': 'kids_4_under',
  'kids_10_under': 'kids_10_under',
  'teenagers': 'teenagers',
  'adults': 'adults',
  'no_humanity': 'no_humanity',
  'experts': 'adults',
  'seniors': 'adults'
};

// The question files name their themes freely ("newyear", "new_year_review",
// "birthdays", "party_gifts", ...). Fold them onto the keys the game asks for.
const themeMap = {
  'christmas': 'christmas',

  'newyear': 'new_year',
  'new_year': 'new_year',
  'new_year_celebrations': 'new_year',
  'new_year_traditions': 'new_year',
  'new_year_review': 'new_year',

  'birthday': 'birthday',
  'birthdays': 'birthday',
  'celebrations': 'birthday',
  'event_planning': 'birthday',
  'party_gifts': 'birthday',
  'party_decorations': 'birthday',
  'party planning and celebration': 'birthday',

  'valentines': 'valentines',

  // Everything else lands in the game's catch-all bucket.
  'beach': 'general',
  'beach_colors': 'general',
  'outdoor_fire_gatherings': 'general',
  'spring': 'general',
  'general': 'general'
};

/** At least this many questions per category per audience, or the game fills in samples. */
const MIN_PER_CATEGORY = 5;
/** Below this many categories the game ignores the bank and uses its built-in list. */
const MIN_CATEGORIES = 6;
/** Where questions from categories that miss an age get pooled. */
const GRAB_BAG_NAME = 'Mixed Bag';

function mapTheme(theme) {
  const key = String(theme).trim().toLowerCase();
  return themeMap[key] || 'general';
}

function scanQuestionsFolder(dir) {
  if (!fs.existsSync(dir)) return null;
  const files = fs.readdirSync(dir).filter(f => f.endsWith('.json'));
  if (files.length === 0) return null;

  console.log('        Importing ' + files.length + ' question files...');

  // theme -> categoryKey -> { name, byAudience: { audience: [questions] } }
  const collected = {};
  const unknownThemes = new Set();
  let totalQuestions = 0;

  for (const file of files) {
    let data;
    try {
      data = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8'));
    } catch (e) {
      console.log('        Warning: Could not parse ' + file);
      continue;
    }
    if (!data || !data.theme || !data.audiences || !data.category) continue;

    const theme = mapTheme(data.theme);
    if (!themeMap[String(data.theme).trim().toLowerCase()]) unknownThemes.add(data.theme);

    const categoryName = String(data.category).trim();
    const categoryKey = categoryName.toLowerCase();
    if (!collected[theme]) collected[theme] = {};
    if (!collected[theme][categoryKey]) {
      collected[theme][categoryKey] = { name: categoryName, byAudience: {} };
    }
    const category = collected[theme][categoryKey];

    for (const [audience, questions] of Object.entries(data.audiences)) {
      const mapped = audienceMap[audience];
      if (!mapped || !Array.isArray(questions)) continue;
      if (!category.byAudience[mapped]) category.byAudience[mapped] = [];
      for (const q of questions) {
        if (!q || !q.clue || !q.correctQuestion) continue;
        category.byAudience[mapped].push({
          question: q.clue,
          answer: q.correctQuestion,
          value: q.value
        });
        totalQuestions++;
      }
    }
  }

  if (unknownThemes.size) {
    console.log('        Note: themes with no mapping went to "general": ' + [...unknownThemes].join(', '));
  }

  // Keep only categories that can fill a column for every age.
  const imported = {};
  for (const [theme, categories] of Object.entries(collected)) {
    const complete = [];
    const partial = [];
    for (const category of Object.values(categories)) {
      const short = AUDIENCES.filter(a => (category.byAudience[a] || []).length < MIN_PER_CATEGORY);
      if (short.length === 0) complete.push(category);
      else partial.push({ name: category.name, missing: short });
    }

    // Categories that miss an age can't be listed on their own, but their
    // questions are perfectly good - pool them into one extra category so they
    // still get played instead of being thrown away.
    const leftovers = {};
    for (const audience of AUDIENCES) leftovers[audience] = [];
    for (const category of Object.values(categories)) {
      if (complete.includes(category)) continue;
      for (const audience of AUDIENCES) {
        for (const q of category.byAudience[audience] || []) leftovers[audience].push(q);
      }
    }
    if (AUDIENCES.every(a => leftovers[a].length >= MIN_PER_CATEGORY)) {
      complete.push({ name: GRAB_BAG_NAME, byAudience: leftovers });
    }

    if (complete.length === 0) {
      console.log(`        ${theme}: no category covers all five ages - skipping`);
      continue;
    }

    imported[theme] = {};
    let kept = 0;
    for (const audience of AUDIENCES) {
      imported[theme][audience] = {};
      for (const category of complete) {
        imported[theme][audience][category.name] = {
          name: category.name,
          questions: category.byAudience[audience]
        };
        kept += category.byAudience[audience].length;
      }
    }

    const note = complete.length < MIN_CATEGORIES
      ? `  (under ${MIN_CATEGORIES}, the game will use its built-in categories instead)`
      : '';
    console.log(`        ${theme}: ${complete.length} categories x 5 ages, ${kept} questions` +
      (partial.length ? `, ${partial.length} categories left out for not covering every age` : '') + note);
  }

  const missingThemes = ['christmas', 'new_year', 'birthday', 'valentines'].filter(t => !imported[t]);
  if (missingThemes.length) {
    console.log('        Warning: no questions for theme(s): ' + missingThemes.join(', ') +
      ' - those boards will fall back to the built-in set');
  }
  console.log('        Total questions read: ' + totalQuestions);
  return imported;
}

const data = scanQuestionsFolder(QUESTIONS_DIR);
if (data) {
  fs.mkdirSync(path.dirname(OUTPUT_FILE), { recursive: true });
  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(data));
  console.log('        Saved to ' + path.basename(OUTPUT_FILE));
}
