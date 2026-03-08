# Question File Format - MANDATORY SPECIFICATION

All trivia question files MUST follow this exact format to be loaded by the server.

## Required Format

Every JSON file in `output/` MUST use this structure:

```json
{
  "category": "Display Name for the Category",
  "theme": "theme_id",
  "category_id": "unique_snake_case_id",
  "audiences": {
    "kids_4_under": [
      { "clue": "The question text", "correctQuestion": "What is the answer?", "value": 200 }
    ],
    "kids_10_under": [
      { "clue": "...", "correctQuestion": "...", "value": 400 }
    ],
    "teenagers": [
      { "clue": "...", "correctQuestion": "...", "value": 600 }
    ],
    "adults": [
      { "clue": "...", "correctQuestion": "...", "value": 800 }
    ],
    "no_humanity": [
      { "clue": "...", "correctQuestion": "...", "value": 1000 }
    ]
  }
}
```

## Rules

### File Location
- All files go in `output/` directory
- Filename: `{theme}_{category_id}_complete.json`
- Example: `beach_sandcastles_complete.json`

### Theme ID (the `theme` field)
Must be one of the existing themes or a new single-word lowercase ID:
- `beach`
- `birthday`
- `christmas`
- `newyear`
- `spring`
- `general`

New themes are auto-detected. Just use a new theme ID and it appears in the dropdown.

### Audience Keys (EXACTLY these 5)
| Key | Target | Typical Value |
|-----|--------|---------------|
| `kids_4_under` | Ages 3-6, very simple | 200 |
| `kids_10_under` | Ages 7-10, basic knowledge | 400 |
| `teenagers` | Ages 11-17, moderate | 600 |
| `adults` | Ages 18+, standard trivia | 800 |
| `no_humanity` | Expert/edgy/Cards Against Humanity style | 1000 |

### Question Fields (REQUIRED)
| Field | Type | Description |
|-------|------|-------------|
| `clue` | string | The Jeopardy-style clue shown to players |
| `correctQuestion` | string | The answer in "What is...?" format |
| `value` | number | Point value (200, 400, 600, 800, 1000) |

### DO NOT
- Use any other audience key names (no `level_1`, `regular_clues`, `7-9`, etc.)
- Put questions in arrays named `questions`, `trivia`, `clues`, `regular_clues`
- Use `answer` instead of `correctQuestion`
- Use `question` instead of `clue`
- Use `points` instead of `value`
- Create per-audience separate files (combine all audiences in one file)
- Use difficulty/level objects instead of the flat audience structure

### Recommended Questions Per Audience
- 5 minimum (for a game board column)
- 30 ideal (allows variety across multiple games)
- 180 maximum per audience in one file

## Adding New Questions

1. Create your JSON file following the format above
2. Place it in `output/`
3. The server auto-detects new files every 30 seconds
4. Or hit the API: `POST /api/quiz-rescan` to force reload

## Pushing Updates (for tablet auto-sync)

```bash
git add output/your_new_file.json
git commit -m "Added new questions: your category"
git push
```

The Android tablet pulls updates from GitHub automatically on every launch.

## Validation

Run the fixer script to verify and normalize files:
```bash
node fix-questions.js
```

This converts non-standard formats and reports issues.
