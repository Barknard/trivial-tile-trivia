---
name: jeopardy-clue-writing
description: How to write, phrase, and difficulty-rate Jeopardy-style clues (the "answer") and their responses (the "question") for a given value tier and audience — grounded in measurements of 554,000 real Jeopardy! clues. Use this whenever writing, reviewing, converting, or grading trivia content for a Jeopardy-style board: new question sets or categories, filling a theme or an age bracket, judging whether a clue belongs at $200 or $1000, adapting adult questions for kids, or fixing trivia that reads like a quiz instead of a game show. Also use it when someone says "write trivia questions", "add questions for <theme>", "these questions are too hard/easy", or hands you a question file to check — even if they never say the word "Jeopardy".
---

# Writing Jeopardy-style clues

Jeopardy inverts the usual quiz shape. The board shows an **answer** (a statement of
fact), and the player responds with the **question** ("What is…?"). Getting this
inversion right is most of the house style; getting difficulty right is the rest.

Everything below is measured from 554,131 real clues (Seasons 1–42), including
21,592 from Kids and Teen tournaments. See `references/corpus-findings.md` for the
numbers and how to re-run them.

## The one thing that is not obvious

**Difficulty lives in how famous the answer is, not in how the clue is worded.**

Real clues barely change shape as the money goes up — a $200 clue averages 14.1
words, a $1000 clue 15.0. What changes is the fame of the thing being asked about:

| tier | median times that answer appears across 42 seasons | answers appearing only once |
| --- | --- | --- |
| $200 | 18× | 9% |
| $600 | 11× | 13% |
| $1000 | 7× | 18% |
| $2000 (Double Jeopardy) | 6× | 20% |

From one real category, both clues the same length:

- **$200** — "GM" → *What is General Motors?* (that answer has come up 59 times)
- **$1000** — "DCX" → *What is Daimler Chrysler?* (twice)

And another:

- **$200** — "Americans thought the war would end by Christmas 1950 — until this country joined the fray" → *What is China?* (486 times)
- **$1000** — "In 1950 the first all-jet dogfight saw Russell Brown in an F-80 shoot down one of these Soviet planes" → *What is a MiG?* (4 times)

So when a clue needs to be harder, **reach for a less famous answer** — a supporting
character instead of the lead, the second person to do the thing, the specific year
instead of the decade. Do not pad the sentence, stack clauses, or bury the ask.
Long convoluted clues reliably feel unfair rather than hard, and they eat the
reading time that makes a buzzer game fun.

## The shape of a clue

A clue is a **statement**, 12–16 words, that describes the answer without naming it,
and hands the player a grammatical slot to fill:

> This Victorian novelist created Ebenezer Scrooge and Little Nell.
> → *Who is Charles Dickens?*

Four things make that work, all of them measured conventions:

1. **It is not a question.** No "What is…", no question mark. If a draft opens with
   What/Who/Which/When/Where/How/Why, it is a quiz question and needs inverting.
2. **A pointer word stands in for the answer** — *this, these, his, her, it.* 83% of
   real clues contain one. The pointer is what makes the response phrasable:
   "this novelist" → "Who is…", "these birds" → "What are…".
3. **The response is short** — 1.8 words on average. If the intended response
   is a sentence, a definition, or a list joined by semicolons, it is not a
   Jeopardy response. Ask for a name, a title, a place, a number, a thing.
4. **Exactly one response is defensible.** The host has to rule instantly, so the
   clue must contain enough to pin the answer down and exclude near-misses.

Common openings, in real clues: "In 1975…", "In this…", "It's the…", "This
author…". Roughly a fifth of clues name a year, and about a third quote a title or
a line of text.

## Rating and hitting a difficulty

To place a clue on the ladder, ask **how many people in the room could name this
thing at all** — not how complicated the sentence is.

| tier | the answer should be | test |
| --- | --- | --- |
| easiest (1st row) | something nearly everyone in the audience knows | almost everyone gets it; it exists to get people buzzing |
| lower-middle | common knowledge with one step of recall | most people get it |
| middle | known to anyone who follows the topic casually | about half get it |
| upper-middle | needs real familiarity with the topic | a minority get it |
| hardest (last row) | a specific, less-famous member of a familiar set | one person, or nobody |

The most reliable way to build a ladder is to **hold the subject constant and walk
the answer from famous to obscure**. Santa's reindeer: Rudolph (everyone) → Dasher
(most) → the one sharing a name with a weather word, Blitzen (some) → the original
1823 spellings Dunder and Blixem (few). Same category, same sentence shape, five
honest difficulty steps.

For per-audience ladders, worked rewrites of one fact across five levels, and how to
sanity-check a tier you are unsure about, read `references/difficulty.md`.

## Audience

Kids' clues in real Kids Week are not different in kind — they are the same
conventions with a smaller pool of famous things. Measured against adult clues they
run about one word shorter (13.9 vs 14.8) and name a year less often (17% vs 22%).
The reading level drops; the *form* does not.

What actually changes per audience is the **set of answers that count as famous**,
and it is worth being concrete about it before writing: for a four-year-old, famous
means things they can point at in a room or a picture book. For a ten-year-old, it
means school subjects and the media they watch. For teenagers, current culture plus
what school has covered. For adults, general knowledge. Each audience also needs its
own vocabulary ceiling — a clue for small children that uses "commonly recommended"
has already failed, whatever its subject.

`references/difficulty.md` has a per-audience table with vocabulary ceilings,
subject matter that lands, and traps to avoid — including the most common failure,
which is writing an adult clue and simply lowering its value number.

## Writing a category

Work category-first rather than clue-first, because a category is a promise about
what the next five clues will be about:

1. **Name the category** the way the board does: short (3 words is the median),
   all caps, concrete. Put it in quotation marks if it hides a wordplay gimmick —
   "FOOL" HOUSE means every answer contains *fool*. About 10% of real categories do
   this, and players love them.
2. **List candidate answers first, sorted by fame**, before writing a single clue.
   This is the step that makes the ladder honest — you are choosing difficulty by
   picking answers, so pick them as a set and see the spread.
3. **Write each clue to its answer**, 12–16 words, statement, pointer word.
4. **Check the set**: no answer given away by another clue in the category, no two
   clues with the same response, every response 1–3 words.

## Before you hand it over

Read each clue aloud as the host would, then check:

- Does it read as a statement a host announces, not a question on a worksheet?
- Could a player say "What is X?" and be unambiguously right?
- Is the answer itself absent from the clue text? (Easy to leak with a compound
  word or a shared root.)
- Is it the right *fame* for its tier — not just the right length?
- Would this audience know the words, quite apart from knowing the answer?

`scripts/check_clues.py` mechanises the checkable parts of that list and runs over
this project's `output/*.json` files:

```bash
python3 .claude/skills/jeopardy-clue-writing/scripts/check_clues.py output/beach_safety.json
python3 .claude/skills/jeopardy-clue-writing/scripts/check_clues.py output/          # whole bank
```

It flags clues phrased as questions, essay-length responses, answers leaked in the
clue, missing pointer words, out-of-range lengths, duplicate answers within a
category, and vocabulary above an audience's ceiling. It cannot judge whether an
answer is famous enough for its tier — that judgement is yours, and it is the part
that matters most.

## Reference files

- `references/difficulty.md` — the fame ladder in depth, per-audience calibration
  table, one fact rewritten across five levels, how to test an uncertain tier.
- `references/style.md` — phrasing conventions, category naming and wordplay,
  response rules, and the recurring ways clues go wrong (with fixes).
- `references/corpus-findings.md` — what was measured across 554,131 real clues,
  the numbers, and the script to reproduce them.
