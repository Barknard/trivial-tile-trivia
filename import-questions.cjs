#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const QUESTIONS_DIR = process.argv[2] || path.join(__dirname, 'questions');
const OUTPUT_FILE = process.argv[3] || path.join(__dirname, 'public', 'runtime-questions.json');

const audienceMap = {
  'kids_4_under': 'kids_4_under',
  'kids_10_under': 'kids_10_under',
  'teenagers': 'teenagers',
  'adults': 'adults',
  'no_humanity': 'no_humanity',
  'experts': 'adults',
  'seniors': 'adults'
};

function scanQuestionsFolder(dir) {
  const imported = {};
  if (!fs.existsSync(dir)) return null;
  const files = fs.readdirSync(dir).filter(f => f.endsWith('.json'));
  if (files.length === 0) return null;
  
  console.log('        Importing ' + files.length + ' question files...');
  let totalQuestions = 0;
  let categories = new Set();
  
  for (const file of files) {
    try {
      const content = fs.readFileSync(path.join(dir, file), 'utf8');
      const data = JSON.parse(content);
      if (!data.theme || !data.audiences || !data.category) continue;
      
      const theme = data.theme;
      if (!imported[theme]) imported[theme] = {};
      const categoryName = data.category;
      categories.add(categoryName);
      
      for (const [audience, questions] of Object.entries(data.audiences)) {
        const mappedAudience = audienceMap[audience];
        if (!mappedAudience) continue;
        if (!imported[theme][mappedAudience]) imported[theme][mappedAudience] = {};
        if (!imported[theme][mappedAudience][categoryName]) {
          imported[theme][mappedAudience][categoryName] = { name: categoryName, questions: [] };
        }
        for (const q of questions) {
          imported[theme][mappedAudience][categoryName].questions.push({
            question: q.clue, answer: q.correctQuestion, value: q.value
          });
          totalQuestions++;
        }
      }
    } catch (e) { console.log('        Warning: Could not parse ' + file); }
  }
  console.log('        Added ' + totalQuestions + ' questions in ' + categories.size + ' categories');
  return imported;
}

const data = scanQuestionsFolder(QUESTIONS_DIR);
if (data) {
  fs.mkdirSync(path.dirname(OUTPUT_FILE), { recursive: true });
  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(data));
  console.log('        Saved to runtime-questions.json');
}
