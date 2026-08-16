# What was measured, and how

Everything asserted in this skill comes from the public
[jeopardy_clue_dataset](https://github.com/jwolle1/jeopardy_clue_dataset) —
554,131 clues from Seasons 1–42 (1984–2026), plus a `kids_teen_matches.tsv` file of
21,592 clues from Kids and Teen tournaments. Daily Doubles were excluded (their
value reflects a wager, not the board) and the difficulty tables use only clues
aired after the 2001-11-26 value doubling, so the tiers mean one thing throughout.

Reproduce with:

```bash
curl -sSLO https://raw.githubusercontent.com/jwolle1/jeopardy_clue_dataset/master/combined_season1-42.tsv
curl -sSLO https://raw.githubusercontent.com/jwolle1/jeopardy_clue_dataset/master/kids_teen_matches.tsv
python3 measure.py           # in this directory
```

## Clue shape barely changes with difficulty

Single Jeopardy round, post-2001, Daily Doubles excluded:

| tier | n | clue words (mean) | response words | has this/these/his/her | names a year | quotes text |
| --- | --- | --- | --- | --- | --- | --- |
| $200 | 33,004 | 14.1 | 1.80 | 81.6% | 21.4% | 29.3% |
| $400 | 32,344 | 14.5 | 1.84 | 82.0% | 21.3% | 29.5% |
| $600 | 31,308 | 14.8 | 1.87 | 82.3% | 21.6% | 31.5% |
| $800 | 30,605 | 14.8 | 1.88 | 82.3% | 21.5% | 31.2% |
| $1000 | 30,740 | 15.0 | 1.90 | 82.6% | 21.2% | 32.5% |

Double Jeopardy behaves the same way, one notch along: $400 → 14.5 words, $2000 →
15.5 words. Across a fivefold price increase the clue grows by about one word.

## Difficulty is carried by the answer's fame

Using how often a response recurs across all 42 seasons as a proxy for how famous
it is:

| tier | median recurrences of that answer | answers used exactly once | answers used 10+ times |
| --- | --- | --- | --- |
| R1 $200 | 18 | 9.1% | 64.2% |
| R1 $400 | 14 | 11.0% | 58.7% |
| R1 $600 | 11 | 12.6% | 54.3% |
| R1 $800 | 10 | 13.7% | 51.0% |
| R1 $1000 | 7 | 17.5% | 44.3% |
| R2 $400 | 20 | 8.9% | 65.4% |
| R2 $2000 | 6 | 20.0% | 38.7% |

Monotonic across all ten tiers. This is the single most useful fact in the skill:
to make a clue harder, choose a less famous answer.

Independent analyses of contestant performance agree that the ladder is real — in
the Jeopardy round, $200 clues are answered correctly by someone roughly 97% of the
time versus roughly 75–84% for $1000 clues.

## Kids and Teen tournaments

| corpus | clue words | response words | pointer word | names a year |
| --- | --- | --- | --- | --- |
| adult, all values | 14.8 | 1.87 | 83.4% | 22.3% |
| kids/teen, all values | 13.9 | 1.76 | 85.4% | 17.3% |

The conventions are identical; the clues are marginally shorter and lean less on
dates. Whatever makes kids' trivia different, it is not the sentence structure — it
is which answers are famous to a child.

## Category titles

Across 48,133 distinct categories in the modern era: 96.5% all caps, mean 3.0 words
(median 3), 10.3% contain quotation marks (the wordplay-gimmick convention), 4.6%
use an ampersand, 0.2% use a fill-in-the-blank. Most reused titles: `BEFORE & AFTER`
(571), `SCIENCE` (493), `POTPOURRI` (479), `AMERICAN HISTORY` (463),
`WORD ORIGINS` (384), `RHYME TIME` (362).

## How clues open

Most common two-word openings in the modern adult corpus: "in the" (1.9%),
"in this" (1.5%), "it's the" (1.0%), "in a" (0.9%), "the name" (0.5%),
"one of" (0.4%), "the first" (0.4%). No single opening dominates — the variety is
itself the convention, and a set of clues that all start the same way reads as
mechanical.

## Sources

- Clue corpus: <https://github.com/jwolle1/jeopardy_clue_dataset>
- Get-rate figures by clue value: ["Jeopardy!" Sabermetrics](https://nycdatascience.com/blog/student-works/jeopardy-sabermetrics/),
  [Calculating the expectation value of knowing Jeopardy! answers](https://blog.danslimmon.com/2012/12/13/calculating-the-expectation-value-of-knowing-jeopardy-answers/)
- Response conventions: [Jeopardy! official rules](https://www.jeopardy.com/jbuzz/behind-scenes/5-jeopardy-rules-every-contestant-should-know)
