#!/usr/bin/env python3
"""Check question files against Jeopardy-style conventions.

Usage:
    python3 check_clues.py output/beach_safety.json      # one file
    python3 check_clues.py output/                       # every file in a folder
    python3 check_clues.py output/ --summary             # counts only
    python3 check_clues.py output/ --audience kids_4_under

Expects this project's format: {"category", "theme", "audiences": {<audience>: [
{"clue", "correctQuestion", "value"}]}}.

What it can and cannot see: everything here is mechanical - phrasing, length,
leaks, duplicates, vocabulary. Whether an answer is famous enough for its value is
the judgement that actually decides difficulty, and no script can make it. Treat a
clean run as "nothing is obviously broken", not "this is well calibrated".
"""
import argparse
import json
import pathlib
import re
import sys
from collections import Counter, defaultdict

INTERROGATIVE = re.compile(r'^\s*(what|who|which|when|where|how|why|whose|whom|name)\b', re.I)
# The response is spoken as a question - "What is red?", "Who are the elves?" -
# so its length is judged on the part after the question opener.
RESPONSE_OPENER = re.compile(r'^\s*(what|who|where|when)\s+(is|are|was|were)\s+', re.I)
POINTER = re.compile(r'\b(this|these|those|his|her|hers|its|their|theirs)\b', re.I)
WORD = re.compile(r"[A-Za-z0-9'’-]+")
# Abstract, adult register that small children will not follow even when the
# underlying fact is simple.
# Require a real stem before the suffix, or short everyday words like "fence"
# and "city" get flagged as abstract vocabulary.
ADULT_REGISTER = re.compile(
    r'\b\w{5,}(tion|sion|ment|ance|ence|ity|ology|ical|ically|ized|ised)\b', re.I)

# Real clues run 12-16 words; allow generous slack before complaining, since
# bare-term clues ("Iron pyrite") are legitimate.
MIN_CLUE_WORDS, MAX_CLUE_WORDS = 3, 30
MAX_RESPONSE_WORDS = 6

VALUE_LADDERS = {
    'kids_4_under': {100, 200, 300},
    'kids_10_under': {100, 200, 300, 400, 500},
    'teenagers': {200, 400, 600, 800, 1000},
    'adults': {200, 400, 600, 800, 1000},
    'no_humanity': {200, 400, 600, 800, 1000},
}
YOUNG = {'kids_4_under', 'kids_10_under'}
# A four-year-old's clue should stay in everyday words and stay short.
YOUNG_LIMITS = {'kids_4_under': (14, 3), 'kids_10_under': (20, 8)}


def words(text):
    return WORD.findall(text or '')


def check_entry(clue, response, value, audience, seen_responses):
    """Return a list of (severity, code, detail) for one clue/response pair."""
    issues = []
    clue = (clue or '').strip()
    response = (response or '').strip()

    if not clue:
        issues.append(('error', 'empty-clue', ''))
        return issues
    if not response:
        issues.append(('error', 'empty-response', clue[:60]))
        return issues

    # The core inversion: a clue is a statement, not a question.
    if clue.endswith('?') or INTERROGATIVE.match(clue):
        issues.append(('error', 'phrased-as-a-question', clue[:70]))

    if not POINTER.search(clue) and len(words(clue)) > 4:
        issues.append(('warn', 'no-pointer-word', clue[:70]))

    n = len(words(clue))
    if n < MIN_CLUE_WORDS:
        issues.append(('warn', 'clue-too-short', f'{n} words: {clue[:50]}'))
    elif n > MAX_CLUE_WORDS:
        issues.append(('warn', 'clue-too-long', f'{n} words: {clue[:50]}'))

    # Players say the response as a question; that is the format the game ships
    # and displays, so a bare noun is incomplete rather than wrong.
    if not RESPONSE_OPENER.match(response):
        issues.append(('warn', 'response-not-phrased-as-a-question', response[:60]))
    core = RESPONSE_OPENER.sub('', response).rstrip('?').strip()

    rn = len(words(core))
    if rn > MAX_RESPONSE_WORDS or ';' in core:
        issues.append(('error', 'response-is-an-essay', f'{rn} words: {core[:70]}'))

    # The answer must not appear in the clue - check whole words, and stems for
    # compounds like sunscreen/sun.
    clue_words = {w.lower().rstrip('s') for w in words(clue)}
    response_words = [w.lower().rstrip('s') for w in words(core)
                      if len(w) > 3 and w.lower() not in
                      {'what', 'who', 'the', 'and', 'this', 'that', 'with', 'from', 'your'}]
    leaked = [w for w in response_words if w in clue_words]
    if leaked and len(leaked) == len(response_words):
        issues.append(('error', 'answer-appears-in-clue', f'"{", ".join(leaked)}" | {clue[:50]}'))

    ladder = VALUE_LADDERS.get(audience)
    if ladder and value not in ladder:
        issues.append(('warn', 'value-off-ladder',
                       f'{value} not in {sorted(ladder)} for {audience}'))

    key = re.sub(r'[^a-z0-9]', '', response.lower())
    if key in seen_responses:
        issues.append(('warn', 'duplicate-response-in-category', response[:50]))
    seen_responses.add(key)

    if audience in YOUNG:
        max_words, max_long = YOUNG_LIMITS[audience]
        if n > max_words:
            issues.append(('warn', 'too-wordy-for-age', f'{n} words for {audience}: {clue[:45]}'))
        heavy = [w for w in words(clue) if ADULT_REGISTER.fullmatch(w)]
        if len(heavy) > (0 if audience == 'kids_4_under' else 1):
            issues.append(('warn', 'adult-vocabulary-for-age',
                           f'{", ".join(heavy[:3])} | {clue[:45]}'))
        if audience == 'kids_4_under':
            long_words = [w for w in words(clue) if len(w) > 9]
            if len(long_words) > max_long:
                issues.append(('warn', 'long-words-for-age', ', '.join(long_words[:4])))
    return issues


def check_file(path, only_audience=None):
    try:
        data = json.loads(path.read_text(encoding='utf-8'))
    except Exception as exc:
        return [('error', 'unreadable-file', f'{path.name}: {exc}')], 0
    audiences = data.get('audiences') if isinstance(data, dict) else None
    if not isinstance(audiences, dict):
        # Not a question file (summary reports and the like live alongside them).
        return [], 0

    issues, checked = [], 0
    for audience, entries in audiences.items():
        if only_audience and audience != only_audience:
            continue
        seen = set()
        for entry in entries or []:
            if not isinstance(entry, dict):
                continue
            checked += 1
            for sev, code, detail in check_entry(
                    entry.get('clue'), entry.get('correctQuestion'),
                    entry.get('value'), audience, seen):
                issues.append((sev, code, f'[{data.get("category", path.stem)} / {audience}] {detail}'))
    return issues, checked


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('target', help='a question .json file or a folder of them')
    ap.add_argument('--summary', action='store_true', help='counts only, no examples')
    ap.add_argument('--audience', help='check just one audience')
    ap.add_argument('--examples', type=int, default=3, help='examples per issue type (default 3)')
    args = ap.parse_args()

    target = pathlib.Path(args.target)
    files = sorted(target.glob('*.json')) if target.is_dir() else [target]
    if not files:
        print(f'No .json files found in {target}')
        return 1

    all_issues, total_checked = [], 0
    per_file = defaultdict(int)
    for path in files:
        issues, checked = check_file(path, args.audience)
        total_checked += checked
        for issue in issues:
            all_issues.append(issue)
            per_file[path.name] += 1

    errors = [i for i in all_issues if i[0] == 'error']
    warnings = [i for i in all_issues if i[0] == 'warn']
    print(f'Checked {total_checked} clues across {len(files)} file(s): '
          f'{len(errors)} error(s), {len(warnings)} warning(s)\n')

    for label, group in (('ERRORS', errors), ('WARNINGS', warnings)):
        if not group:
            continue
        print(label)
        for code, count in Counter(c for _, c, _ in group).most_common():
            share = 100 * count / total_checked if total_checked else 0
            print(f'  {code:<32} {count:>6}  ({share:.1f}% of clues)')
            if not args.summary:
                for _, c, detail in [g for g in group if g[1] == code][:args.examples]:
                    print(f'       e.g. {detail[:150]}')
        print()

    if per_file and not args.summary and len(files) > 1:
        print('Files with the most issues:')
        for name, count in Counter(per_file).most_common(5):
            print(f'  {count:>5}  {name}')

    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
