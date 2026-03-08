#!/usr/bin/env node
/**
 * Fix Questions - Normalizes all output/*.json files to server-compatible format.
 *
 * Server expects ONE of:
 *   Format A (combined): { category, theme, audiences: { kids_4_under: [{clue, correctQuestion, value}] } }
 *   Format B (per-audience filename): flat array, filename ends with _AUDIENCE.json
 *
 * This script converts everything to Format A.
 */

const fs = require('fs');
const path = require('path');

const OUTPUT_DIR = path.join(__dirname, 'output');
const STANDARD_AUDIENCES = ['kids_4_under', 'kids_10_under', 'teenagers', 'adults', 'no_humanity'];

// Map non-standard audience keys to standard ones
const AUDIENCE_MAP = {
  // Level-based (from generate_beach_ice_cream.py style)
  'level_1_easy_200': 'kids_4_under',
  'level_2_easy_400': 'kids_10_under',
  'level_3_medium_600': 'teenagers',
  'level_4_medium_800': 'adults',
  'level_5_hard_1000': 'no_humanity',
  'level_6_expert_1200': 'no_humanity',
  // Age-based
  'kids_5_8': 'kids_4_under',
  'kids_5_9': 'kids_4_under',
  'kids_9_12': 'kids_10_under',
  'kids_10_13': 'kids_10_under',
  'teens_15_under': 'teenagers',
  '7-9': 'kids_10_under',
  '10-12': 'kids_10_under',
  '13-17': 'teenagers',
  '18+': 'adults',
  'K-6': 'kids_4_under',
  // Demographic
  'all_ages': 'kids_10_under',
  'families': 'kids_10_under',
  'general_knowledge': 'adults',
  'experts': 'no_humanity',
  'seniors': 'adults',
  'high_school': 'teenagers',
  'teens': 'teenagers',
  'teens_adults': 'adults',
  'mixed_audiences': 'adults',
  // CAH variants -> no_humanity
  'cah_style': 'no_humanity',
  'CAH_style': 'no_humanity',
  'cah_style_clues': 'no_humanity',
  'cah_trivia': 'no_humanity',
  'cah_no_humanity_clues': 'no_humanity',
  'cards_against_humanity': 'no_humanity',
  'adults_CAH_style': 'no_humanity',
  // Generic
  'regular': 'adults',
  'regular_clues': 'adults',
  'regular_trivia': 'adults',
  // Named difficulty
  'elementary': 'kids_4_under',
  'middle_school': 'kids_10_under',
  'high_school_plus': 'teenagers',
  'adult': 'adults',
  'expert': 'no_humanity',
};

function normalizeQuestion(q) {
  const clue = q.clue || q.question || q.text || '';
  const answer = q.correctQuestion || q.answer || q.correct_answer || '';
  const value = q.value || q.points || 200;
  if (!clue || !answer) return null;
  return { clue, correctQuestion: answer, value: Number(value) };
}

function inferThemeFromFilename(filename) {
  const base = filename.replace('.json', '');
  const parts = base.split('_');
  return parts[0]; // beach, birthday, christmas, newyear, spring
}

function inferCategoryFromFilename(filename) {
  const base = filename.replace('.json', '');
  // Remove _complete suffix
  const clean = base.replace(/_complete$/, '');
  // Convert underscores to title case
  return clean.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
}

function distributeByValue(questions) {
  // Distribute a flat list of questions into audiences based on point value
  const audiences = {
    kids_4_under: [],
    kids_10_under: [],
    teenagers: [],
    adults: [],
    no_humanity: []
  };

  for (const q of questions) {
    const norm = normalizeQuestion(q);
    if (!norm) continue;

    const v = norm.value;
    if (v <= 200) audiences.kids_4_under.push(norm);
    else if (v <= 400) audiences.kids_10_under.push(norm);
    else if (v <= 600) audiences.teenagers.push(norm);
    else if (v <= 800) audiences.adults.push(norm);
    else audiences.no_humanity.push(norm);
  }

  return audiences;
}

function distributeByLevel(questions) {
  // Distribute questions that have a "level" or "difficulty" field
  const audiences = {
    kids_4_under: [],
    kids_10_under: [],
    teenagers: [],
    adults: [],
    no_humanity: []
  };

  const levelMap = { 1: 'kids_4_under', 2: 'kids_10_under', 3: 'teenagers', 4: 'adults', 5: 'no_humanity', 6: 'no_humanity' };
  const diffMap = { 'easy': 'kids_4_under', 'medium': 'teenagers', 'hard': 'adults', 'expert': 'no_humanity' };

  for (const q of questions) {
    const norm = normalizeQuestion(q);
    if (!norm) continue;

    let aud = 'adults'; // default
    if (q.level && levelMap[q.level]) aud = levelMap[q.level];
    else if (q.difficulty && diffMap[q.difficulty]) aud = diffMap[q.difficulty];
    else {
      // Fall back to value-based
      const v = norm.value;
      if (v <= 200) aud = 'kids_4_under';
      else if (v <= 400) aud = 'kids_10_under';
      else if (v <= 600) aud = 'teenagers';
      else if (v <= 800) aud = 'adults';
      else aud = 'no_humanity';
    }

    audiences[aud].push(norm);
  }

  return audiences;
}

function assignValues(questions, audienceKey) {
  // Assign values to questions that are missing them based on audience
  const baseValues = {
    kids_4_under: 200,
    kids_10_under: 400,
    teenagers: 600,
    adults: 800,
    no_humanity: 1000
  };
  const base = baseValues[audienceKey] || 200;

  return questions.map((q, i) => {
    const norm = normalizeQuestion(q);
    if (!norm) return null;
    if (!q.value && !q.points) norm.value = base;
    return norm;
  }).filter(Boolean);
}

function processFile(filepath) {
  const filename = path.basename(filepath);

  // Skip non-question files
  if (filename.endsWith('.txt') || filename.endsWith('.md')) return null;
  if (filename === 'temp_validate.json') return null;
  if (filename === 'general_categories.json') return null;
  if (filename === 'christmas_categories_research_based.json') return null;

  let raw;
  try {
    raw = JSON.parse(fs.readFileSync(filepath, 'utf-8'));
  } catch (e) {
    console.log(`  SKIP (invalid JSON): ${filename}`);
    return null;
  }

  const theme = raw.theme || inferThemeFromFilename(filename);
  const category = raw.category || inferCategoryFromFilename(filename);

  // Already compatible Format A?
  if (raw.audiences && raw.category) {
    // Check if audience keys are standard
    const keys = Object.keys(raw.audiences);
    const allStandard = keys.every(k => STANDARD_AUDIENCES.includes(k));
    if (allStandard) {
      // Still normalize questions for missing values
      let changed = false;
      for (const aud of keys) {
        let arr = raw.audiences[aud];
        if (!Array.isArray(arr)) {
          // Extract questions from nested object
          if (arr && Array.isArray(arr.questions)) arr = arr.questions;
          else continue;
          changed = true;
        }
        const normalized = arr.map(q => normalizeQuestion(q)).filter(Boolean);
        if (normalized.length !== arr.length) changed = true;
        raw.audiences[aud] = normalized;
      }
      return { data: raw, changed, filename };
    }

    // Map non-standard audience keys
    const newAudiences = {};
    for (const [key, val] of Object.entries(raw.audiences)) {
      const mappedKey = AUDIENCE_MAP[key] || key;
      const stdKey = STANDARD_AUDIENCES.includes(mappedKey) ? mappedKey : 'adults';
      if (!newAudiences[stdKey]) newAudiences[stdKey] = [];
      // val might be an array of questions, or an object with a questions array
      let questions = [];
      if (Array.isArray(val)) {
        questions = val;
      } else if (val && Array.isArray(val.questions)) {
        questions = val.questions;
      } else if (val && typeof val === 'object') {
        for (const v of Object.values(val)) {
          if (Array.isArray(v)) { questions = v; break; }
        }
      }
      if (Array.isArray(questions)) {
        const normalized = questions.map(q => normalizeQuestion(q)).filter(Boolean);
        newAudiences[stdKey].push(...normalized);
      }
    }

    return {
      data: { category: typeof category === 'string' ? category : category.name || filename, theme, category_id: raw.category_id || filename.replace('.json', ''), audiences: newAudiences },
      changed: true,
      filename
    };
  }

  // Has audience_levels (like birthday_candles_lights_complete.json)
  if (raw.audience_levels) {
    const newAudiences = {};
    for (const [key, levelData] of Object.entries(raw.audience_levels)) {
      const mappedKey = AUDIENCE_MAP[key] || 'adults';
      const stdKey = STANDARD_AUDIENCES.includes(mappedKey) ? mappedKey : 'adults';
      if (!newAudiences[stdKey]) newAudiences[stdKey] = [];
      let questions = [];
      if (Array.isArray(levelData)) {
        questions = levelData;
      } else if (levelData && Array.isArray(levelData.questions)) {
        questions = levelData.questions;
      } else if (levelData && typeof levelData === 'object') {
        for (const v of Object.values(levelData)) {
          if (Array.isArray(v)) { questions = v; break; }
        }
      }
      if (Array.isArray(questions) && questions.length > 0) {
        const normalized = assignValues(questions, stdKey);
        newAudiences[stdKey].push(...normalized);
      }
    }

    return {
      data: { category: typeof category === 'string' ? category : String(category), theme, category_id: filename.replace('.json', ''), audiences: newAudiences },
      changed: true,
      filename
    };
  }

  // Flat array (per-audience filename format) - already handled by server, but let's convert anyway
  if (Array.isArray(raw)) {
    const audiences = distributeByValue(raw);
    return {
      data: { category: typeof category === 'string' ? category : String(category), theme, category_id: filename.replace('.json', ''), audiences },
      changed: true,
      filename
    };
  }

  // Has "difficulty" array (like beach_birds.json)
  if (raw.difficulty && Array.isArray(raw.difficulty)) {
    const levelToAud = { 1: 'kids_4_under', 2: 'kids_10_under', 3: 'teenagers', 4: 'adults', 5: 'no_humanity', 6: 'no_humanity' };
    const newAudiences = {};
    for (const level of raw.difficulty) {
      const aud = levelToAud[level.level] || 'adults';
      if (!newAudiences[aud]) newAudiences[aud] = [];
      if (Array.isArray(level.questions)) {
        const pts = level.pointsPerQuestion || level.level * 200;
        const normalized = level.questions.map(q => {
          const n = normalizeQuestion(q);
          if (n && !q.value && !q.points) n.value = pts;
          return n;
        }).filter(Boolean);
        newAudiences[aud].push(...normalized);
      }
    }
    return {
      data: { category: raw.title || (typeof category === 'string' ? category : String(category)), theme, category_id: filename.replace('.json', ''), audiences: newAudiences },
      changed: true,
      filename
    };
  }

  // Has "levels" array (similar structure)
  if (raw.levels && Array.isArray(raw.levels) && raw.levels[0] && raw.levels[0].questions) {
    const levelToAud = { 1: 'kids_4_under', 2: 'kids_10_under', 3: 'teenagers', 4: 'adults', 5: 'no_humanity', 6: 'no_humanity' };
    const newAudiences = {};
    for (const level of raw.levels) {
      const aud = levelToAud[level.level] || 'adults';
      if (!newAudiences[aud]) newAudiences[aud] = [];
      if (Array.isArray(level.questions)) {
        const pts = level.pointsPerQuestion || (level.level || 1) * 200;
        const normalized = level.questions.map(q => {
          const n = normalizeQuestion(q);
          if (n && !q.value && !q.points) n.value = pts;
          return n;
        }).filter(Boolean);
        newAudiences[aud].push(...normalized);
      }
    }
    return {
      data: { category: raw.title || (typeof category === 'string' ? category : String(category)), theme, category_id: filename.replace('.json', ''), audiences: newAudiences },
      changed: true,
      filename
    };
  }

  // Has "levels" as object with string keys (like beach_flip_flops.json)
  if (raw.levels && typeof raw.levels === 'object' && !Array.isArray(raw.levels)) {
    const newAudiences = {};
    const levelToAud = { '1': 'kids_4_under', '2': 'kids_10_under', '3': 'teenagers', '4': 'adults', '5': 'no_humanity', '6': 'no_humanity' };
    for (const [key, levelData] of Object.entries(raw.levels)) {
      const aud = levelToAud[key] || 'adults';
      if (!newAudiences[aud]) newAudiences[aud] = [];
      if (levelData && Array.isArray(levelData.questions)) {
        const pts = levelData.point_value || Number(key) * 200;
        const normalized = levelData.questions.map(q => {
          const n = normalizeQuestion(q);
          if (n) n.value = q.value || q.points || pts;
          return n;
        }).filter(Boolean);
        newAudiences[aud].push(...normalized);
      }
    }
    const totalQ = Object.values(newAudiences).reduce((s, a) => s + a.length, 0);
    if (totalQ > 0) {
      return {
        data: { category: raw.title || (typeof category === 'string' ? category : String(category)), theme, category_id: filename.replace('.json', ''), audiences: newAudiences },
        changed: true,
        filename
      };
    }
  }

  // Has "data" as object with level keys (like beach_volleyball.json)
  if (raw.data && typeof raw.data === 'object' && !Array.isArray(raw.data)) {
    const newAudiences = {};
    const entries = Object.entries(raw.data);
    // Sort by level number if possible
    entries.sort((a, b) => {
      const aLevel = a[1] && a[1].level ? a[1].level : 0;
      const bLevel = b[1] && b[1].level ? b[1].level : 0;
      return aLevel - bLevel;
    });
    const levelToAud = { 1: 'kids_4_under', 2: 'kids_10_under', 3: 'teenagers', 4: 'adults', 5: 'no_humanity', 6: 'no_humanity' };
    for (const [key, levelData] of entries) {
      if (!levelData || typeof levelData !== 'object') continue;
      const lvl = levelData.level || 3;
      const aud = levelToAud[lvl] || 'adults';
      if (!newAudiences[aud]) newAudiences[aud] = [];
      if (Array.isArray(levelData.questions)) {
        const pts = levelData.point_value || lvl * 200;
        const normalized = levelData.questions.map(q => {
          const n = normalizeQuestion(q);
          if (n) n.value = q.value || q.points || pts;
          return n;
        }).filter(Boolean);
        newAudiences[aud].push(...normalized);
      }
    }
    const totalQ = Object.values(newAudiences).reduce((s, a) => s + a.length, 0);
    if (totalQ > 0) {
      return {
        data: { category: raw.title || (typeof category === 'string' ? category : String(category)), theme, category_id: filename.replace('.json', ''), audiences: newAudiences },
        changed: true,
        filename
      };
    }
  }

  // Has "categories" array (like beach_basics.json)
  if (raw.categories && Array.isArray(raw.categories)) {
    const allQuestions = [];
    for (const cat of raw.categories) {
      if (cat.clues && Array.isArray(cat.clues)) {
        for (const q of cat.clues) {
          allQuestions.push({ ...q, value: q.value || q.points || cat.points || 200 });
        }
      }
      if (cat.questions && Array.isArray(cat.questions)) {
        for (const q of cat.questions) {
          allQuestions.push({ ...q, value: q.value || q.points || cat.points || 200 });
        }
      }
    }
    if (allQuestions.length > 0) {
      const audiences = distributeByValue(allQuestions);
      return {
        data: { category: raw.title || typeof category === 'string' ? category : String(category), theme, category_id: filename.replace('.json', ''), audiences },
        changed: true,
        filename
      };
    }
  }

  // Has "regular_clues" array (like beach_music.json, birthday_cake.json)
  if (raw.regular_clues && Array.isArray(raw.regular_clues)) {
    const audiences = distributeByValue(raw.regular_clues);
    return {
      data: { category: typeof category === 'string' ? category : String(category), theme, category_id: filename.replace('.json', ''), audiences },
      changed: true,
      filename
    };
  }

  // Has "questions" array (like beach_kites.json)
  if (raw.questions && Array.isArray(raw.questions)) {
    const audiences = distributeByLevel(raw.questions);
    return {
      data: { category: typeof category === 'string' ? category : String(category), theme, category_id: filename.replace('.json', ''), audiences },
      changed: true,
      filename
    };
  }

  // Has "trivia" array (like newyear_fireworks.json)
  if (raw.trivia && Array.isArray(raw.trivia)) {
    const audiences = distributeByValue(raw.trivia);
    return {
      data: { category: typeof category === 'string' ? category : String(category), theme, category_id: filename.replace('.json', ''), audiences },
      changed: true,
      filename
    };
  }

  // Has "sample_questions" array (like christmas_santa_CAH_v4)
  if (raw.sample_questions && Array.isArray(raw.sample_questions)) {
    const normalized = raw.sample_questions.map(q => normalizeQuestion(q)).filter(Boolean);
    return {
      data: { category: typeof category === 'string' ? category : String(category), theme, category_id: filename.replace('.json', ''), audiences: { no_humanity: normalized } },
      changed: true,
      filename
    };
  }

  // Has "revised_no_humanity_section" (like christmas_santa_REVISED)
  if (raw.revised_no_humanity_section && Array.isArray(raw.revised_no_humanity_section)) {
    const normalized = raw.revised_no_humanity_section.map(q => normalizeQuestion(q)).filter(Boolean);
    return {
      data: { category: typeof category === 'string' ? category : String(category), theme, category_id: filename.replace('.json', ''), audiences: { no_humanity: normalized } },
      changed: true,
      filename
    };
  }

  // Has "clues" at top level
  if (raw.clues && Array.isArray(raw.clues)) {
    const audiences = distributeByValue(raw.clues);
    return {
      data: { category: typeof category === 'string' ? category : String(category), theme, category_id: filename.replace('.json', ''), audiences },
      changed: true,
      filename
    };
  }

  // Has "data" array
  if (raw.data && Array.isArray(raw.data)) {
    const audiences = distributeByValue(raw.data);
    return {
      data: { category: typeof category === 'string' ? category : String(category), theme, category_id: filename.replace('.json', ''), audiences },
      changed: true,
      filename
    };
  }

  // Last resort: look for any array of objects with clue/question fields
  for (const [key, val] of Object.entries(raw)) {
    if (Array.isArray(val) && val.length > 0 && (val[0].clue || val[0].question || val[0].text)) {
      const audiences = distributeByValue(val);
      const totalQ = Object.values(audiences).reduce((sum, arr) => sum + arr.length, 0);
      if (totalQ > 0) {
        return {
          data: { category: typeof category === 'string' ? category : String(category), theme, category_id: filename.replace('.json', ''), audiences },
          changed: true,
          filename
        };
      }
    }
  }

  // Check if it's a category listing (no questions)
  console.log(`  SKIP (no questions found): ${filename}`);
  return null;
}

// Main
console.log('=== Trivial Tile Trivia - Question Fixer ===\n');

const files = fs.readdirSync(OUTPUT_DIR).filter(f => f.endsWith('.json'));
let fixed = 0, skipped = 0, alreadyOk = 0, totalQuestions = 0;
const themeStats = {};

for (const file of files) {
  const filepath = path.join(OUTPUT_DIR, file);
  const result = processFile(filepath);

  if (!result) {
    skipped++;
    continue;
  }

  // Count questions
  const qCount = Object.values(result.data.audiences).reduce((sum, arr) => sum + arr.length, 0);
  totalQuestions += qCount;

  // Track theme stats
  const t = result.data.theme;
  if (!themeStats[t]) themeStats[t] = { categories: 0, questions: 0, audiences: new Set() };
  themeStats[t].categories++;
  themeStats[t].questions += qCount;
  for (const aud of Object.keys(result.data.audiences)) {
    if (result.data.audiences[aud].length > 0) themeStats[t].audiences.add(aud);
  }

  if (result.changed) {
    fs.writeFileSync(filepath, JSON.stringify(result.data, null, 2), 'utf-8');
    console.log(`  FIXED: ${file} (${qCount} questions)`);
    fixed++;
  } else {
    alreadyOk++;
  }
}

console.log('\n=== RESULTS ===');
console.log(`Files processed: ${files.length}`);
console.log(`Already OK:      ${alreadyOk}`);
console.log(`Fixed:           ${fixed}`);
console.log(`Skipped:         ${skipped}`);
console.log(`Total questions: ${totalQuestions}`);

console.log('\n=== THEME BREAKDOWN ===');
for (const [theme, stats] of Object.entries(themeStats).sort()) {
  console.log(`  ${theme}: ${stats.categories} categories, ${stats.questions} questions, audiences: [${[...stats.audiences].join(', ')}]`);
}
