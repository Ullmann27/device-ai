#!/usr/bin/env python3
"""Deterministic new one-binding data and paired schedules. No model outputs are used."""
from __future__ import annotations

import base64
from collections import Counter
import hashlib
import json
from pathlib import Path
import random

from oracle import self_check, verify

ROOT = Path(__file__).resolve().parent
OBJECTS = {
    'train': ['Tasse', 'Buch', 'Mantel', 'Flasche', 'Hut', 'Teller', 'Schuh', 'Koffer',
              'Lampe', 'Gabel', 'Löffel', 'Tuch', 'Kamm', 'Kerze', 'Seife', 'Topf'],
    'dev': ['Becher', 'Jacke', 'Dose', 'Pinsel', 'Schale', 'Besen', 'Mappe', 'Kiste'],
    'test': ['Wecker', 'Spiegel', 'Eimer', 'Decke', 'Hammer', 'Korb', 'Gürtel', 'Würfel'],
}
SEEDS = [202611043, 202611044]
TEMPLATES = {
    'owner_late': 'Der Gegenstand {object} gehört Person {owner}.\nBesitzer:',
    'owner_early': 'Person {owner} besitzt den Gegenstand {object}.\nBesitzer:',
}


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def main() -> None:
    out = ROOT / 'data'
    out.mkdir(exist_ok=False)
    checks = self_check()
    all_rows = {}
    for split, objects in OBJECTS.items():
        rows = []
        for obj in objects:
            for owner in 'ABCD':
                for template, fmt in TEMPLATES.items():
                    prompt = fmt.format(object=obj, owner=owner)
                    parsed = verify(prompt, owner)
                    if parsed['object'] != obj or parsed['template'] != template:
                        raise ValueError('independent interpretation mismatch')
                    rows.append({'id': sha(prompt.encode())[:20], 'object': obj, 'template': template,
                                 'prompt': prompt, 'answer': owner, 'oracle': parsed})
        rows.sort(key=lambda row: sha(('round3-single-binding-v1|' + row['id']).encode()))
        all_rows[split] = rows
        stem = 'test.sealed' if split == 'test' else split
        (out / (stem + '.jsonl')).write_text(''.join(json.dumps(r, ensure_ascii=False) + '\n' for r in rows))
        (out / (stem + '.tsv')).write_text(''.join('\t'.join([r['id'], r['template'], r['object'], r['answer'],
                base64.b64encode(r['prompt'].encode()).decode()]) + '\n' for r in rows))
    for left, right in [('train', 'dev'), ('train', 'test'), ('dev', 'test')]:
        if set(OBJECTS[left]) & set(OBJECTS[right]):
            raise ValueError('object overlap')
        if {r['id'] for r in all_rows[left]} & {r['id'] for r in all_rows[right]}:
            raise ValueError('prompt overlap')
    for seed in SEEDS:
        order = list(range(128)) * 8
        random.Random(seed).shuffle(order)
        if Counter(order) != Counter({i: 8 for i in range(128)}):
            raise ValueError('schedule multiset mismatch')
        (out / f'schedule-{seed}.txt').write_text(''.join(str(i) + '\n' for i in order))
    records = {p.name: {'sha256': sha(p.read_bytes()), 'bytes': p.stat().st_size}
               for p in sorted(out.iterdir()) if p.is_file()}
    manifest = {'schema': 'lm1-single-binding-objective-v1', 'source': 'new deterministic synthetic German data',
                'noPreviousStudyDatasetImported': True, 'objects': OBJECTS, 'templates': TEMPLATES,
                'counts': {s: len(rows) for s, rows in all_rows.items()},
                'answerCounts': {s: dict(Counter(r['answer'] for r in rows)) for s, rows in all_rows.items()},
                'templateCounts': {s: dict(Counter(r['template'] for r in rows)) for s, rows in all_rows.items()},
                'verifierChecks': checks, 'allLabelsVerified': True,
                'scheduleSeeds': SEEDS, 'examplesPerArm': 1024, 'eachTrainExampleOccurrences': 8,
                'splitScope': 'disjoint object words, shared grammar/labels; single binding copy only',
                'sealedMeaning': 'no model evaluation before all candidates and receipts are frozen; not encryption',
                'files': records}
    (out / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'counts': manifest['counts'], 'checks': checks, 'dataManifestSha256': sha((out / 'manifest.json').read_bytes())}, indent=2))


if __name__ == '__main__':
    main()
