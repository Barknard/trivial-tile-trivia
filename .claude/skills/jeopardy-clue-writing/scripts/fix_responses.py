#!/usr/bin/env python3
"""Put bare responses into the spoken question form the game displays.

    python3 fix_responses.py output/            # show what would change
    python3 fix_responses.py output/ --write    # apply

The game shows the response as "CORRECT QUESTION (player says): What is red?",
and its own built-in clues are stored that way, so a bare "red" is incomplete
rather than wrong. This adds the opener and nothing else - it never touches clue
text, because deciding whether a clue is well written is a judgement call and
this script has none.

Choosing "Who" over "What": driven by the clue naming a person ("this author",
"this president") or the response looking like a personal name. Getting it wrong
is cosmetic - the host reads it aloud and players are not marked down for the
question word - so the heuristic errs toward "What".
"""
import argparse
import json
import pathlib
import re
import sys

RESPONSE_OPENER = re.compile(r'^\s*(what|who|where|when)\s+(is|are|was|were)\b', re.I)
PERSON_IN_CLUE = re.compile(
    r'\bthis\s+(man|woman|boy|girl|author|writer|poet|singer|artist|actor|actress|'
    r'president|king|queen|player|composer|director|scientist|inventor|leader|'
    r'saint|comedian|chef|designer|explorer|athlete|star)\b', re.I)
PEOPLE_POINTER = re.compile(r'\bthese\s+(men|women|brothers|sisters|people|players|singers|twins)\b', re.I)
PLURAL_POINTER = re.compile(r'\b(these|those)\b', re.I)
PERSON_NAME = re.compile(r'^[A-Z][a-z]+(?:\s+[A-Z][a-z\'’.]+)+$')


def opener_for(clue, response):
    people = bool(PERSON_IN_CLUE.search(clue) or PEOPLE_POINTER.search(clue)
                  or PERSON_NAME.match(response.strip()))
    plural = bool(PLURAL_POINTER.search(clue))
    return f"{'Who' if people else 'What'} {'are' if plural else 'is'}"


def fix_file(path, write):
    try:
        data = json.loads(path.read_text(encoding='utf-8'))
    except Exception:
        return 0, []
    audiences = data.get('audiences') if isinstance(data, dict) else None
    if not isinstance(audiences, dict):
        return 0, []

    changed, samples = 0, []
    for entries in audiences.values():
        for entry in entries or []:
            if not isinstance(entry, dict):
                continue
            response = (entry.get('correctQuestion') or '').strip()
            clue = (entry.get('clue') or '').strip()
            if not response or RESPONSE_OPENER.match(response):
                continue
            core = response.rstrip('?.').strip()
            # "What is A rescue buoy?" reads wrong when the host says it aloud;
            # a leading article is never part of a proper name, so lowercase it.
            core = re.sub(r'^(A|An|The)\b', lambda m: m.group(1).lower(), core)
            fixed = f"{opener_for(clue, response)} {core}?"
            if len(samples) < 3:
                samples.append(f'{response[:34]!r} -> {fixed[:52]!r}')
            entry['correctQuestion'] = fixed
            changed += 1
    if changed and write:
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return changed, samples


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('target')
    ap.add_argument('--write', action='store_true', help='write the files (otherwise dry run)')
    args = ap.parse_args()

    target = pathlib.Path(args.target)
    files = sorted(target.glob('*.json')) if target.is_dir() else [target]
    total, shown = 0, 0
    for path in files:
        changed, samples = fix_file(path, args.write)
        total += changed
        if changed and shown < 6:
            print(f'{path.name}: {changed}')
            for s in samples:
                print(f'    {s}')
            shown += 1
    print(f'\n{"Rewrote" if args.write else "Would rewrite"} {total} responses across {len(files)} file(s)')
    if not args.write and total:
        print('Re-run with --write to apply.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
