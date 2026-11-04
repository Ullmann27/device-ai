#!/usr/bin/env python3
"""Read-only independent audit of identity, cardinality and raw study scores."""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import hashlib
import json
import math
from pathlib import Path


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path: Path):
    return json.loads(path.read_text())


def require(value: bool, message: str) -> None:
    if not value:
        raise ValueError(message)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--language', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--original', type=Path)
    args = parser.parse_args()
    root = args.language.resolve(strict=True)
    run = root / 'run-v1'
    freeze, completed, analysis = [read(run / name) for name in ('freeze.json', 'completed.json', 'analysis.json')]
    registration = read(root / 'registration.json')
    expected = {'baseline': ('baseline', 'none')}
    expected.update({f'{arm}-{seed}': (arm, str(seed)) for arm in ('mixed', 'curriculum') for seed in (202611041, 202611042)})
    require(len(freeze['models']) == 5 and {m['name'] for m in freeze['models']} == set(expected), 'Model cardinality')
    require(completed['models'] == freeze['models'], 'Completed/frozen model drift')
    require(digest(run / 'freeze.json') == completed['freezeSha256'], 'Freeze hash')
    require(digest(run / 'completed.json') == analysis['completedSha256'], 'Completion hash')
    require(freeze['registrationCommit'] == completed['registrationCommit'] == '93b43fa2a80109c64c30335149227ccfcebe0f0a', 'Prospective commit')
    require(digest(root / 'registration.json') == freeze['registrationSha256'] == completed['registrationSha256'], 'Registration hash')
    for path, value in registration['files'].items():
        require(digest(root / path) == value, 'Registered file drift: ' + path)
    frozen_at = datetime.fromisoformat(freeze['frozenUtc'])
    truth = {split: {r['id']: r for r in (json.loads(line) for line in (root / 'data' / (stem + '.jsonl')).read_text().splitlines())}
             for split, stem in [('dev', 'dev'), ('test', 'test.sealed')]}
    require(len(truth['dev']) == 48 and len(truth['test']) == 96, 'Truth cardinality')
    receipt_by_label = {r['label']: r for r in completed['evaluations']}
    require(len(completed['evaluations']) == len(receipt_by_label) == 10, 'Evaluation receipt cardinality')
    require(set(receipt_by_label) == {f'{name}-{split}' for name in expected for split in truth}, 'Evaluation receipt labels')
    require(len(analysis['summaries']) == 10, 'Summary cardinality')
    summary_by_key = {(s['model'], s['split']): s for s in analysis['summaries']}
    require(len(summary_by_key) == 10, 'Duplicate summaries')
    actual = {}
    for model in freeze['models']:
        name = model['name']
        arm, seed = expected[name]
        require((model['arm'], model['seed']) == (arm, seed), 'Model arm/seed identity')
        checkpoint = (args.original or Path(model['checkpoint'])) if name == 'baseline' else run / (name + '.lm1')
        require(digest(checkpoint) == model['checkpointSha256'], 'Checkpoint bytes')
        capacity = model['capacity']
        receipt_path = run / (name + '-capacity.receipt.json')
        require(digest(receipt_path) == capacity['receiptSha256'] and read(receipt_path) == capacity['receipt'], 'Capacity receipt')
        cr = capacity['receipt']
        require(cr['label'] == name + '-capacity', 'Capacity label')
        require(not cr['timedOut'] and cr['exitCode'] == (0 if capacity['pass'] else 1), 'Capacity completion')
        require(datetime.fromisoformat(cr['completedUtc']) < frozen_at, 'Capacity follows freeze')
        for stream in ('stdout', 'stderr'):
            require(digest(run / cr[stream]) == cr[stream + 'Sha256'], 'Capacity log hash')
        final = [line for line in (run / cr['stdout']).read_text().splitlines() if line.startswith('CAPACITY_RESULT\t')]
        require(len(final) == 1, 'Capacity verdict count')
        fields = dict(field.split('=', 1) for field in final[0].split('\t')[1:])
        require(fields == capacity['verdict'] and fields['checkpointSha256'] == model['checkpointSha256'], 'Capacity verdict binding')
        if name != 'baseline':
            tr = read(run / (name + '-train.receipt.json'))
            require(datetime.fromisoformat(tr['completedUtc']) < frozen_at and tr['exitCode'] == 0 and not tr['timedOut'], 'Training completion/freeze')
            events = [json.loads(line) for line in (run / tr['stdout']).read_text().splitlines()]
            require(events[-1] == model['training'], 'Training result binding')
            require(model['training']['steps'] == 256 and model['training']['tokens'] == 59274, 'Training budget')
            for stream in ('stdout', 'stderr'):
                require(digest(run / tr[stream]) == tr[stream + 'Sha256'], 'Training log hash')
        for split in truth:
            label = f'{name}-{split}'
            er = receipt_by_label[label]
            require(er == read(run / (label + '.receipt.json')), 'Evaluation receipt structure')
            require(er['exitCode'] == 0 and not er['timedOut'], 'Evaluation completion')
            require(datetime.fromisoformat(er['completedUtc']) - timedelta(seconds=er['wallSeconds']) >= frozen_at, 'Evaluation began before freeze')
            for stream in ('stdout', 'stderr'):
                require(digest(run / er[stream]) == er[stream + 'Sha256'], 'Evaluation log hash')
            events = [json.loads(line) for line in (run / er['stdout']).read_text().splitlines()]
            require(events[0]['kind'] == 'evaluation_start' and events[-1]['kind'] == 'evaluation_complete', 'Evaluation framing')
            require(events[0]['checkpointSha256'] == model['checkpointSha256'], 'Evaluation checkpoint')
            stem = 'dev' if split == 'dev' else 'test.sealed'
            require(events[0]['datasetSha256'] == digest(root / 'data' / (stem + '.tsv')), 'Evaluation dataset')
            rows = [e for e in events if e['kind'] == 'case']
            require(len(rows) == len(truth[split]) and {r['id'] for r in rows} == set(truth[split]), 'Case set')
            for e in events:
                require((e['arm'], str(e['seed']), e['split']) == (arm, seed, split), 'Event arm/seed/split identity')
            for row in rows:
                gold = truth[split][row['id']]
                for key in ('prompt', 'answer', 'family', 'depth'):
                    require(row[key] == gold[key], 'Case truth binding')
                require(row['freeCorrect'] == (row['text'].strip() == gold['answer']), 'Free score')
                for nll, choice, score in [('choiceNll', 'choice', 'choiceCorrect'), ('choiceNllWithEos', 'choiceWithEos', 'choiceWithEosCorrect')]:
                    require(set(row[nll]) == set('ABCD') and all(math.isfinite(v) for v in row[nll].values()), 'NLL support')
                    winner = min(row[nll], key=row[nll].get)
                    require(row[choice] == winner and row[score] == (winner == gold['answer']), 'Choice score')
            summary = summary_by_key[(name, split)]
            require((summary['arm'], summary['seed'], summary['capacityPass']) == (arm, seed, capacity['pass']), 'Summary identity')
            critical = [r for r in rows if r['family'] in {'move_other', 'query_then_other'}]
            for scope, subset in [('overall', rows), ('critical', critical)]:
                require(summary[scope]['n'] == len(subset), 'Summary denominator')
                for score in ('freeCorrect', 'choiceCorrect', 'choiceWithEosCorrect'):
                    require(summary[scope][score] == sum(r[score] for r in subset), 'Summary score')
            actual[(name, split)] = {'n': len(rows), 'freeCorrect': sum(r['freeCorrect'] for r in rows),
                'choiceCorrect': sum(r['choiceCorrect'] for r in rows), 'criticalN': len(critical),
                'criticalFree': sum(r['freeCorrect'] for r in critical), 'criticalChoice': sum(r['choiceCorrect'] for r in critical),
                'freeOutputs': dict(Counter(r['text'].strip() for r in rows)), 'capacityPass': capacity['pass']}
    decisions = analysis['predeclaredPerSeedDecisions']
    require(len(decisions) == 8, 'Decision cardinality')
    decision_keys = {(d['model'], d['score']) for d in decisions}
    require(decision_keys == {(n, s) for n in expected if n != 'baseline' for s in ('freeCorrect', 'choiceCorrect')}, 'Decision identity set')
    recomputed = {}
    for score, critical_key in [('freeCorrect', 'criticalFree'), ('choiceCorrect', 'criticalChoice')]:
        per_score = [d for d in decisions if d['score'] == score]
        require(len(per_score) == 4 and sum(d['arm'] == 'mixed' for d in per_score) == 2, 'Non-vacuous mixed cardinality')
        passes = []
        for d in per_score:
            name = d['model']
            require(d['arm'] == expected[name][0], 'Decision arm')
            a = actual[(name, 'test')]
            delta = (a[score] - actual[('baseline', 'test')][score]) / a['n']
            critical_pass = a[critical_key] / a['criticalN'] >= .5
            heuristic_counts = [sum(r['oracle'][h] == r['answer'] for r in truth['test'].values()) for h in ('initialOwner', 'lastMentionedPerson')]
            beats = all(a[score] > count for count in heuristic_counts)
            passed = delta >= .1 and critical_pass and beats
            require((d['pass'], d['deltaVsBaseline'], d['criticalAtLeastHalf'], d['beatsBothHeuristics']) == (passed, delta, critical_pass, beats), 'Decision recomputation')
            if d['arm'] == 'mixed':
                passes.append(passed)
        require(len(passes) == 2, 'Missing mixed seed')
        recomputed[score] = all(passes)
    require(recomputed == analysis['primaryMixedBothSeedsPass'], 'Primary decision')
    output = {'checkedUtc': datetime.now(timezone.utc).isoformat(), 'method': 'independent metadata and raw-score audit; no training or model inference',
              'modelCount': 5, 'evaluationCount': 10, 'caseCount': 720, 'decisionCount': 8,
              'mixedDecisionsPerScore': 2, 'registrationCommit': freeze['registrationCommit'],
              'completedSha256': digest(run / 'completed.json'), 'analysisSha256': digest(run / 'analysis.json'),
              'testResults': {name: actual[(name, 'test')] for name in expected},
              'recomputedPrimaryMixedBothSeedsPass': recomputed, 'passed': True}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x') as stream:
        json.dump(output, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
