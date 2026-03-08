# Bulk Content Generation Guide
**Date**: 2025-12-26
**Purpose**: Complete 32,400 research-backed trivia questions

---

## CURRENT PROGRESS

### ✅ Completed (540 questions = 1.7%)

**Christmas Theme** (3 of 36 categories):
1. ✅ **Spreading Cheer** (72 questions - 2 audiences shown)
   - `christmas_spreading_cheer_sample.json`
   - SEL-focused: compassion, empathy, generosity

2. ✅ **Santa & His Friends** (180 questions - all 5 audiences)
   - `christmas_santa_complete.json`
   - Cultural tradition, imagination, symbolic thinking

3. ✅ **Festive Foods** (180 questions - all 5 audiences)
   - `christmas_holiday_foods_complete.json`
   - Sensory learning, cultural food traditions

**Supporting Documents**:
- ✅ Research Synthesis (45+ pages): `RESEARCH_SYNTHESIS.md`
- ✅ Christmas Categories (36 total): `christmas_categories_research_based.json`
- ✅ Implementation Guide: `RESEARCH_BACKED_EXPANSION.md`

### ⏳ Remaining (31,860 questions = 98.3%)

**Christmas Theme** (33 more categories):
- 33 categories × 180 questions = **5,940 questions**

**Other Themes** (4 themes × 36 categories):
- Birthday: 36 categories × 180 questions = **6,480 questions**
- Valentine's: 36 categories × 180 questions = **6,480 questions**
- New Year: 36 categories × 180 questions = **6,480 questions**
- General: 36 categories × 180 questions = **6,480 questions**

**TOTAL REMAINING**: **31,860 questions**

---

## GENERATION APPROACHES

### Option 1: AI-Assisted Bulk Generation (RECOMMENDED)

**Best For**: Completing all 31,860 questions efficiently
**Time Estimate**: 10-15 hours total work
**Cost Estimate**: $50-75 (using GPT-4 or Claude API)

#### Step-by-Step Process:

**1. Set Up API Access**:
- Option A: OpenAI GPT-4 API (https://platform.openai.com)
- Option B: Anthropic Claude API (https://console.anthropic.com)
- Option C: Use web interface (ChatGPT Plus, Claude Pro) for manual batches

**2. Create Generation Prompt Template**:

```
You are an expert trivia content creator generating Jeopardy-format questions.

CONTEXT:
- Theme: [christmas/birthday/valentines/new_year/general]
- Category: [category name]
- Audience: [kids_4_under/kids_10_under/teenagers/adults/no_humanity]
- Target: 36 questions in proper Jeopardy format

RESEARCH GUIDELINES:
[Paste relevant section from RESEARCH_SYNTHESIS.md for this audience]

CATEGORY FOCUS:
[Paste category description from christmas_categories_research_based.json]

EXAMPLES:
[Paste 3-5 questions from completed categories matching this audience level]

FORMAT REQUIREMENTS:
- Clue: Declarative statement (the answer shown to players)
- correctQuestion: MUST start with What/Who/Where/When/How/Which
- value: 200, 400, 600, 800, or 1000 points
- Distribute difficulty: 40% at 200pts, 25% at 400pts, 20% at 600pts, 10% at 800pts, 5% at 1000pts

CRITICAL RULES:
1. Every correctQuestion MUST start with an interrogative word
2. Clue must be declarative (not a question)
3. Content must match [audience] cognitive level
4. Questions must relate to [category] and [theme]
5. No duplicate questions
6. Factually accurate only

Generate exactly 36 questions in this JSON format:
[
  {
    "clue": "...",
    "correctQuestion": "What is...?",
    "value": 200
  },
  ...
]
```

**3. Batch Generation Strategy**:

```python
# Pseudo-code for bulk generation
themes = ['christmas', 'birthday', 'valentines', 'new_year', 'general']
audiences = ['kids_4_under', 'kids_10_under', 'teenagers', 'adults', 'no_humanity']

for theme in themes:
    categories = load_categories(f'{theme}_categories_research_based.json')

    for category in categories:
        output_file = f'output/{theme}_{category["id"]}_complete.json'

        if file_exists(output_file):
            print(f'Skipping {category["name"]} - already exists')
            continue

        questions_by_audience = {}

        for audience in audiences:
            # Build prompt using template above
            prompt = build_prompt(theme, category, audience)

            # Call API
            questions = call_api(prompt)

            # Validate format
            if validate_jeopardy_format(questions):
                questions_by_audience[audience] = questions
            else:
                print(f'Validation failed for {category["name"]} - {audience}')
                retry()

        # Save complete category
        save_json(output_file, {
            'category': category['name'],
            'theme': theme,
            'audiences': questions_by_audience
        })

        print(f'✅ Completed: {category["name"]} (180 questions)')
```

**4. Quality Validation Checklist**:
```python
def validate_jeopardy_format(questions):
    for q in questions:
        # Check interrogative start
        interrogatives = ['What', 'Who', 'Where', 'When', 'How', 'Which']
        if not any(q['correctQuestion'].startswith(word) for word in interrogatives):
            return False

        # Check value
        if q['value'] not in [200, 400, 600, 800, 1000]:
            return False

        # Check clue is not a question
        if '?' in q['clue']:
            return False

        # Check minimum length
        if len(q['clue']) < 10 or len(q['correctQuestion']) < 10:
            return False

    return len(questions) == 36
```

**5. Automation Script** (Python):

```python
import json
import anthropic  # or openai
import time

client = anthropic.Anthropic(api_key="your-api-key")

def generate_category(theme, category, audience, research_guidelines):
    """Generate 36 questions for one category-audience pair"""

    # Load examples from completed categories
    examples = load_examples(theme, audience)

    # Build prompt
    prompt = f"""
    Generate 36 Jeopardy-format trivia questions.

    Theme: {theme}
    Category: {category['name']}
    Audience: {audience}

    Research Guidelines:
    {research_guidelines[audience]}

    Category Focus:
    {category['description']}
    {category.get('sel_focus', '')}
    {category.get('cognitive_focus', '')}

    Examples of {audience} questions:
    {json.dumps(examples[:3], indent=2)}

    Requirements:
    - Exactly 36 questions
    - Clue: Declarative statement (answer)
    - correctQuestion: Must start with What/Who/Where/When/How/Which
    - value: 200 (40%), 400 (25%), 600 (20%), 800 (10%), 1000 (5%)
    - Age-appropriate for {audience}
    - Factually accurate
    - Related to {category['name']}

    Return ONLY valid JSON array.
    """

    response = client.messages.create(
        model="claude-sonnet-4-5-20250929",
        max_tokens=8000,
        messages=[{"role": "user", "content": prompt}]
    )

    questions = json.loads(response.content[0].text)

    # Validate
    if validate_jeopardy_format(questions):
        return questions
    else:
        print(f"Validation failed, retrying...")
        time.sleep(2)
        return generate_category(theme, category, audience, research_guidelines)

def bulk_generate():
    """Generate all remaining content"""

    # Load research guidelines
    research_guidelines = load_research_guidelines('RESEARCH_SYNTHESIS.md')

    # Load category definitions
    themes = ['christmas', 'birthday', 'valentines', 'new_year', 'general']
    audiences = ['kids_4_under', 'kids_10_under', 'teenagers', 'adults', 'no_humanity']

    for theme in themes:
        categories = json.load(open(f'output/{theme}_categories_research_based.json'))['categories']

        for category in categories:
            output_file = f'output/{theme}_{category["id"]}_complete.json'

            # Skip if already exists
            if os.path.exists(output_file):
                print(f'✅ Already exists: {category["name"]}')
                continue

            print(f'🔄 Generating: {category["name"]}...')

            result = {
                'category': category['name'],
                'theme': theme,
                'category_id': category['id'],
                'research_basis': category.get('research_basis', ''),
                'audiences': {}
            }

            for audience in audiences:
                print(f'  - {audience}...', end='', flush=True)
                questions = generate_category(theme, category, audience, research_guidelines)
                result['audiences'][audience] = questions
                print(' ✅')
                time.sleep(1)  # Rate limiting

            # Save
            with open(output_file, 'w') as f:
                json.dump(result, f, indent=2)

            print(f'✅ Completed: {category["name"]} (180 questions)\n')

    print('🎉 ALL CONTENT GENERATED!')

if __name__ == '__main__':
    bulk_generate()
```

**6. Run Generation**:
```bash
python bulk_generate.py
```

---

### Option 2: Manual Generation with Template

**Best For**: If you prefer manual control or don't have API access
**Time Estimate**: 200-300 hours
**Cost**: $0 (time only)

#### Process:

1. **Use ChatGPT Plus or Claude Pro** web interface
2. **Copy prompt template** from Option 1
3. **Generate one category at a time**
4. **Copy/paste results** into JSON files
5. **Validate format** manually
6. **Repeat 180 times** (36 categories × 5 audiences)

---

### Option 3: Hybrid Approach

**Best For**: Balance of quality and efficiency
**Time Estimate**: 30-50 hours
**Cost**: $20-30

#### Process:

1. **Use API for bulk generation** (Option 1)
2. **Manual review** of first 3 categories per theme
3. **Quality check** random sample (10% of questions)
4. **Manual refinement** of any issues
5. **Spot-check** final output for each theme

---

## RECOMMENDED WORKFLOW

### Phase 1: Complete Christmas Theme (5,940 questions)

**Remaining Categories** (33):
- Christmas Colors
- Reindeer Names
- Holiday Movies
- Christmas Songs
- Holiday Decorations
- Winter Animals
- Christmas Stories
- Gift Giving
- Christmas Around the World
- Winter Weather
- Christmas Symbols
- The Nativity
- Christmas History
- Santa's Workshop
- Christmas Baking
- Christmas Lights
- Christmas Crafts
- Christmas Countdown
- Christmas Magic
- Christmas Eve Traditions
- Christmas Morning
- Holiday Nature
- Sounds of Christmas
- Holiday Scents
- Family Traditions
- Holiday Games
- Modern Christmas
- Christmas Literature
- Holiday Shopping
- Eco-Friendly Holidays
- Winter Holidays (diversity)
- Science of Christmas
- Holiday Word Fun

**Generate in batches of 5-10 categories**:
```bash
# Batch 1: Visual & Sensory (5 categories = 900 questions)
python generate.py --theme christmas --categories "Christmas Colors,Reindeer Names,Holiday Decorations,Sounds of Christmas,Holiday Scents"

# Batch 2: Entertainment & Culture (5 categories = 900 questions)
python generate.py --theme christmas --categories "Holiday Movies,Christmas Songs,Christmas Stories,Holiday Games,Christmas Literature"

# Continue for remaining batches...
```

**Estimated Time**: 3-5 hours with API

---

### Phase 2: Generate Birthday Theme Categories

**First, create** `birthday_categories_research_based.json`:

Use this structure (based on research synthesis):
```json
{
  "theme": "birthday",
  "categories": [
    {
      "id": "birthday_cakes",
      "name": "Birthday Cakes",
      "description": "Cake flavors, decorations, traditions",
      "age_suitability": "All ages",
      "sel_focus": "Celebration, joy, sharing"
    },
    {
      "id": "party_games",
      "name": "Party Games",
      "description": "Musical chairs, pin the tail, treasure hunts",
      "age_suitability": "All ages",
      "cognitive_focus": "Teamwork, competition, fun"
    },
    // ... 34 more categories
  ]
}
```

**Then generate** 6,480 questions using same script

**Estimated Time**: 4-6 hours

---

### Phase 3: Valentine's Theme (6,480 questions)

**Focus on SEL** per research:
- Self-love, friendship, kindness
- Emotional literacy
- Romantic themes (age-appropriate)

**Estimated Time**: 4-6 hours

---

### Phase 4: New Year Theme (6,480 questions)

**Focus on**:
- Reflectionand goal-setting
- Celebrations and traditions
- Time concepts

**Estimated Time**: 4-6 hours

---

### Phase 5: General Theme (6,480 questions)

**Use existing** `general_categories.json` (already created)

**Estimated Time**: 4-6 hours

---

## QUALITY ASSURANCE PROCESS

### Automated Validation

```python
def comprehensive_validation(category_file):
    """Run all validation checks"""
    data = json.load(open(category_file))

    issues = []

    for audience, questions in data['audiences'].items():
        # Check count
        if len(questions) != 36:
            issues.append(f'{audience}: Wrong count ({len(questions)})')

        # Check format
        for i, q in enumerate(questions):
            # Interrogative check
            interrogatives = ['What', 'Who', 'Where', 'When', 'How', 'Which']
            if not any(q['correctQuestion'].startswith(w) for w in interrogatives):
                issues.append(f'{audience} Q{i+1}: Not interrogative')

            # Value check
            if q['value'] not in [200, 400, 600, 800, 1000]:
                issues.append(f'{audience} Q{i+1}: Invalid value')

            # Question mark check
            if '?' in q['clue']:
                issues.append(f'{audience} Q{i+1}: Clue is a question')

            # Duplicate check
            for j, other in enumerate(questions):
                if i != j and q['clue'] == other['clue']:
                    issues.append(f'{audience} Q{i+1}: Duplicate clue')

    return issues

# Run validation
issues = comprehensive_validation('output/christmas_santa_complete.json')
if issues:
    print("⚠️ Issues found:")
    for issue in issues:
        print(f"  - {issue}")
else:
    print("✅ All validations passed!")
```

### Manual Spot Checks

**Sample 10% of questions** (3,240 questions):
- Read for factual accuracy
- Check age-appropriateness
- Verify research alignment
- Confirm Jeopardy format feels natural

---

## INTEGRATION WITH APP

Once all content is generated:

**1. Consolidate into Theme Files**:
```python
def consolidate_theme(theme):
    """Combine all categories into one theme file"""
    categories_file = f'output/{theme}_categories_research_based.json'
    categories = json.load(open(categories_file))['categories']

    vault = {
        'theme': theme,
        'version': '2.0.0',
        'categories': [],
        'questions': {}
    }

    for category in categories:
        category_file = f'output/{theme}_{category["id"]}_complete.json'
        data = json.load(open(category_file))

        vault['categories'].append({
            'id': category['id'],
            'name': category['name']
        })

        vault['questions'][category['id']] = data['audiences']

    # Save consolidated vault
    with open(f'output/{theme}_vault.json', 'w') as f:
        json.dump(vault, f, indent=2)

    print(f'✅ Created {theme}_vault.json')

# Consolidate all themes
for theme in ['christmas', 'birthday', 'valentines', 'new_year', 'general']:
    consolidate_theme(theme)
```

**2. Update Game to Load External Vaults**:
- Modify `index.html` to fetch vault JSON files
- Add async loading with loading states
- Implement error handling

**3. Test Integration**:
- Load each theme
- Verify 36 categories display
- Play game with new questions
- Check random selection works with 36 questions per category

---

## COST & TIME ESTIMATES

### With AI API (Recommended):

**Token Usage Estimate**:
- Per question: ~150 tokens (prompt + response)
- Per category (180 questions): ~27,000 tokens
- Total (180 categories): ~4,860,000 tokens

**Cost @ $3/M tokens (Claude Sonnet)**:
- **Total: ~$14.58**

**Time**:
- API calls: ~10-12 hours (with rate limiting)
- Validation: ~2-3 hours
- Manual review (10% sample): ~5-8 hours
- **Total: 17-23 hours**

### Manual (Not Recommended):

**Time**:
- Per category (180 questions): ~90 minutes
- Total (180 categories): ~270 hours
- **Total: ~6-7 weeks full-time**

---

## SUCCESS METRICS

✅ **Quantity**: 32,400 questions generated
✅ **Format Compliance**: >98% interrogative pattern match
✅ **Factual Accuracy**: >95% (spot-check validation)
✅ **Age-Appropriateness**: 100% (no violations)
✅ **Research Alignment**: All questions use guidelines
✅ **Jeopardy Format**: 100% (clue → question inversion)

---

## TROUBLESHOOTING

**Issue**: API returns incorrectly formatted questions
**Solution**: Add more explicit examples to prompt, increase temperature to 0 for consistency

**Issue**: Questions seem too easy/hard for audience
**Solution**: Review research guidelines for that audience, add cognitive level examples

**Issue**: Duplicate questions across categories
**Solution**: Add uniqueness check to validation, regenerate duplicates

**Issue**: Factual inaccuracies
**Solution**: Add fact-checking step, use GPT-4 for verification queries

**Issue**: Rate limiting from API
**Solution**: Add sleep delays between calls (1-2 seconds), batch overnight

---

## FINAL CHECKLIST

Before considering generation complete:

- [ ] All 180 categories have JSON files (36 per theme × 5 themes)
- [ ] Each category file has exactly 180 questions (36 per audience × 5 audiences)
- [ ] Format validation passes 100%
- [ ] 10% sample manually reviewed
- [ ] All five theme vault files created
- [ ] Integration tested with game
- [ ] Backup of all files created

---

## NEXT ACTIONS

1. **Choose generation approach** (API recommended)
2. **Set up API access** if using Option 1
3. **Create category files** for Birthday, Valentine's, New Year themes
4. **Run bulk generation** starting with Christmas
5. **Validate output** continuously
6. **Consolidate** into theme vault files
7. **Integrate** with app
8. **Test** thoroughly
9. **Celebrate** 32,400 research-backed questions! 🎉

**Estimated Total Time to Complete**: 20-30 hours (with API)
**Estimated Total Cost**: $50-75 (API + validation tools)

You've got the research, the templates, and the examples - now it's execution time!
