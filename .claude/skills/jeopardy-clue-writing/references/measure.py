"""Reproduce the measurements in corpus-findings.md.

Download the two TSVs into this directory first:

    curl -sSLO https://raw.githubusercontent.com/jwolle1/jeopardy_clue_dataset/master/combined_season1-42.tsv
    curl -sSLO https://raw.githubusercontent.com/jwolle1/jeopardy_clue_dataset/master/kids_teen_matches.tsv
    mv combined_season1-42.tsv combined.tsv && mv kids_teen_matches.tsv kids_teen.tsv
"""
import csv, re, statistics, sys, collections

csv.field_size_limit(10_000_000)

def load(path):
    rows = []
    with open(path, encoding='utf-8', errors='replace') as f:
        for r in csv.DictReader(f, delimiter='\t'):
            try:
                r['clue_value'] = int(r['clue_value'] or 0)
                r['round'] = int(r['round'] or 0)
            except ValueError:
                continue
            rows.append(r)
    return rows

WORD = re.compile(r"[A-Za-z'’-]+")
POINTER = re.compile(r'\b(this|these|his|her|hers|its|their|he|she|they|it)\b', re.I)
YEAR = re.compile(r'\b(1[0-9]{3}|20[0-2][0-9])\b')

def stats(rows, label):
    if not rows:
        return
    clue_len = [len(WORD.findall(r['answer'] or '')) for r in rows]
    resp_len = [len(WORD.findall(r['question'] or '')) for r in rows]
    pointer = sum(1 for r in rows if POINTER.search(r['answer'] or ''))
    year = sum(1 for r in rows if YEAR.search(r['answer'] or ''))
    quoted = sum(1 for r in rows if '"' in (r['answer'] or ''))
    proper = sum(1 for r in rows if (r['question'] or '')[:1].isupper())
    n = len(rows)
    print(f"{label:22s} n={n:6d}  clue words: mean {statistics.mean(clue_len):5.1f} "
          f"median {statistics.median(clue_len):4.0f}  p90 {sorted(clue_len)[int(n*0.9)]:3d}  "
          f"| response words: mean {statistics.mean(resp_len):4.2f}  "
          f"| has this/these/his/her: {100*pointer/n:5.1f}%  "
          f"| names a year: {100*year/n:5.1f}%  | quoted text: {100*quoted/n:4.1f}%  "
          f"| proper-noun answer: {100*proper/n:5.1f}%")

modern = [r for r in load('combined.tsv') if r['air_date'] >= '2001-11-26' and r['daily_double_value'] == '0']
kids = [r for r in load('kids_teen.tsv') if r['daily_double_value'] == '0']

print("=" * 150)
print("ADULT SYNDICATED, single Jeopardy round (post-2001 values) - the difficulty ladder")
print("=" * 150)
for v in (200, 400, 600, 800, 1000):
    stats([r for r in modern if r['round'] == 1 and r['clue_value'] == v], f"  ${v}")

print()
print("Double Jeopardy round (harder half of the same show)")
for v in (400, 800, 1200, 1600, 2000):
    stats([r for r in modern if r['round'] == 2 and r['clue_value'] == v], f"  ${v}")

print()
print("=" * 150)
print("KIDS & TEEN TOURNAMENTS - same game, younger audience")
print("=" * 150)
for v in sorted({r['clue_value'] for r in kids if r['round'] == 1 and r['clue_value'] > 0}):
    stats([r for r in kids if r['round'] == 1 and r['clue_value'] == v], f"  ${v}")

print()
print("Adult vs kids/teen, whole rounds compared:")
stats([r for r in modern if r['round'] in (1, 2)], "  adult (all values)")
stats([r for r in kids if r['round'] in (1, 2)], "  kids/teen (all)")

print()
print("=" * 150)
print("CATEGORY TITLE CONVENTIONS (adult, modern)")
print("=" * 150)
cats = [r['category'] for r in modern if r['category']]
uniq = len(set(cats))
quoted_cat = sum(1 for c in set(cats) if '"' in c)
blank_cat = sum(1 for c in set(cats) if '___' in c or '_' * 3 in c)
amp = sum(1 for c in set(cats) if '&' in c)
upper = sum(1 for c in set(cats) if c == c.upper())
words = [len(WORD.findall(c)) for c in set(cats)]
print(f"  {uniq} distinct categories | all-caps {100*upper/uniq:.1f}% | "
      f"contains \" {100*quoted_cat/uniq:.1f}% | fill-in-the-blank {100*blank_cat/uniq:.1f}% | "
      f"uses & {100*amp/uniq:.1f}% | words per title: mean {statistics.mean(words):.1f}, median {statistics.median(words):.0f}")
print("  most common category titles:", ', '.join(f'"{c}" ({n})' for c, n in collections.Counter(cats).most_common(8)))
