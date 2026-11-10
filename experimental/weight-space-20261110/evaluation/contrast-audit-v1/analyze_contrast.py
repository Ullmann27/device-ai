#!/usr/bin/env python3
"""Audit registered contrast outputs without running a model."""

import argparse
import collections
import csv
import hashlib
import importlib.util
import json
import math
from pathlib import Path


FIELDS = {
    'INPUT': 'file sha256',
    'CONFIG': 'mode steps parameters seed rate answerBatch replayBatch replayWeight',
    'CASE': 'split step id expected predictedRestricted tokens ended exact firstEos',
    'SCORE': 'split step exact total restrictedCorrect answerNll prematureEos',
    'UPDATE': 'step answerLoss replayWeightedLoss gradientNorm',
    'RETENTION': 'step bpb bytes',
    'CAPACITY': 'step id tokens eos',
    'CHECKPOINT': 'sha256 bytes',
    'RESULT': 'updates inputUnchanged elapsedMillis',
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def number(value):
    result = float(value)
    require(math.isfinite(result), 'nonfinite metric')
    return result


def integer(value):
    require(str(value).isdigit(), 'invalid nonnegative integer')
    return int(value)


def boolean(value):
    require(value in ('true', 'false'), 'invalid boolean')
    return value == 'true'


def parse_log(text):
    records = collections.defaultdict(list)
    for line in text.splitlines():
        fields = line.split('\t')
        kind = fields[0]
        require(kind in FIELDS, 'unknown record type')
        pairs = [field.split('=', 1) for field in fields[1:]]
        require(all(len(pair) == 2 for pair in pairs), 'malformed field')
        record = dict(pairs)
        require(len(record) == len(pairs), 'duplicate field')
        require(set(record) == set(FIELDS[kind].split()), 'unexpected fields')
        records[kind].append(record)
    require(records, 'empty log')
    return records


def singleton(records, kind):
    require(len(records[kind]) == 1, 'expected single ' + kind)
    return records[kind][0]


def read_rows(path):
    with Path(path).open(newline='') as stream:
        rows = list(csv.DictReader(stream, delimiter='\t'))
    require(rows and all(set(row) == {'id', 'label', 'prompt'} for row in rows), 'invalid data rows')
    require(len({row['id'] for row in rows}) == len(rows), 'duplicate data id')
    require(all(row['label'] in '0123' and len(row['label']) == 1 for row in rows), 'invalid data label')
    return {row['id']: row['label'] for row in rows}


def analyze_log(records, expected, steps, vocab):
    config = singleton(records, 'CONFIG')
    require(integer(config['steps']) == steps, 'configured update mismatch')
    require(config['mode'] in ('answer', 'replay'), 'invalid training mode')
    require(integer(config['seed']) == 20261110, 'unexpected seed')
    require(number(config['rate']) == 0.0001, 'unexpected rate')
    require(integer(config['answerBatch']) == 4, 'unexpected answer batch')
    replay = config['mode'] == 'replay'
    require(integer(config['replayBatch']) == (2 if replay else 0), 'unexpected replay batch')
    require(number(config['replayWeight']) == (0.1 if replay else 0), 'unexpected replay weight')
    updates = records['UPDATE']
    require([integer(row['step']) for row in updates] == list(range(1, steps + 1)), 'missing or duplicated updates')
    for row in updates:
        require(all(number(row[key]) >= 0 for key in ('answerLoss', 'replayWeightedLoss', 'gradientNorm')), 'negative update metric')
        if not replay:
            require(number(row['replayWeightedLoss']) == 0, 'unexpected replay loss')
    result = singleton(records, 'RESULT')
    require(integer(result['updates']) == steps and boolean(result['inputUnchanged']), 'run did not complete unchanged')
    require(integer(result['elapsedMillis']) < 660000, 'run exceeded external deadline')
    grouped = collections.defaultdict(dict)
    for row in records['CASE']:
        key = (row['split'], integer(row['step']))
        require(key[0] in expected and key[1] in (0, steps), 'unexpected case group')
        require(row['id'] not in grouped[key], 'duplicate case')
        require(expected[key[0]].get(row['id']) == row['expected'], 'case label or id mismatch')
        label = integer(row['expected'])
        predicted = integer(row['predictedRestricted'])
        require(predicted <= 3, 'invalid restricted prediction')
        tokens = [integer(token) for token in row['tokens'].split(',')] if row['tokens'] else []
        require(len(tokens) <= 4 and all(token < vocab - 1 for token in tokens), 'invalid generated tokens')
        ended = boolean(row['ended'])
        require(ended or len(tokens) == 4, 'incomplete greedy trace')
        exact = ended and tokens == [48 + label]
        require(boolean(row['exact']) == exact, 'exact score disagrees with tokens')
        require(0 <= number(row['firstEos']) <= 1, 'invalid EOS probability')
        grouped[key][row['id']] = {'label': label, 'restricted': predicted, 'exact': exact,
                                   'prematureEos': ended and not tokens, 'tokens': tokens}
    groups = {(split, step) for split in expected for step in (0, steps)}
    require(set(grouped) == groups, 'missing case group')
    scores = {}
    for row in records['SCORE']:
        key = (row['split'], integer(row['step']))
        require(key in groups and key not in scores, 'unexpected or duplicate score')
        require(set(grouped[key]) == set(expected[key[0]]), 'missing expected case')
        cases = list(grouped[key].values())
        exact = sum(case['exact'] for case in cases)
        restricted = sum(case['restricted'] == case['label'] for case in cases)
        premature = sum(case['prematureEos'] for case in cases)
        require(integer(row['total']) == len(cases) and integer(row['exact']) == exact,
                'aggregate exact mismatch')
        require(integer(row['restrictedCorrect']) == restricted and integer(row['prematureEos']) == premature,
                'aggregate diagnostics mismatch')
        require(number(row['answerNll']) >= 0, 'negative NLL')
        per_class = {}
        for label in sorted({case['label'] for case in cases}):
            selected = [case for case in cases if case['label'] == label]
            per_class[str(label)] = {'total': len(selected), 'exact': sum(case['exact'] for case in selected),
                                     'restrictedCorrect': sum(case['restricted'] == label for case in selected)}
        scores[key] = {'total': len(cases), 'exact': exact, 'restrictedCorrect': restricted,
                       'prematureEos': premature, 'answerNll': number(row['answerNll']),
                       'restrictedPredictionCounts': dict(sorted(collections.Counter(str(case['restricted']) for case in cases).items())),
                       'perClass': per_class}
    require(set(scores) == groups, 'missing aggregate score')
    retention = {}
    for row in records['RETENTION']:
        step = integer(row['step'])
        require(step in (0, steps) and step not in retention, 'unexpected retention record')
        require(number(row['bpb']) > 0 and integer(row['bytes']) > 0, 'invalid retention metric')
        retention[step] = {'bpb': number(row['bpb']), 'bytes': integer(row['bytes'])}
    require(set(retention) == {0, steps}, 'missing retention record')
    require(retention[0]['bytes'] == retention[steps]['bytes'], 'retention denominator changed')
    capacity = {0: {}, steps: {}}
    for row in records['CAPACITY']:
        step, identity = integer(row['step']), integer(row['id'])
        require(step in capacity and identity in range(4) and identity not in capacity[step], 'unexpected capacity record')
        count, ended = integer(row['tokens']), boolean(row['eos'])
        require(count <= 32, 'invalid capacity count')
        capacity[step][identity] = {'tokens': count, 'eos': ended}
    require(all(set(rows) == set(range(4)) for rows in capacity.values()), 'missing capacity probe')
    before_full = sum(row['tokens'] == 32 for row in capacity[0].values())
    after_full = sum(row['tokens'] == 32 for row in capacity[steps].values())
    bpb_change = retention[steps]['bpb'] / retention[0]['bpb'] - 1
    checks = {
        'devExactGainAtLeastEight': scores['dev', steps]['exact'] - scores['dev', 0]['exact'] >= 8,
        'retentionExactNoDecline': scores['retention', steps]['exact'] >= scores['retention', 0]['exact'],
        'textBpbIncreaseAtMostFivePercent': bpb_change <= 0.05,
        'capacityCountNoDecline': after_full >= before_full,
        'baselineAllFourCapacityProbes': before_full == 4,
    }
    warnings = []
    if scores['retention', 0]['exact'] == 0:
        warnings.append('Retention exact-match floor: unchanged zero is not evidence of preserved ability.')
    if scores['retention', steps]['restrictedCorrect'] < scores['retention', 0]['restrictedCorrect']:
        warnings.append('Restricted retention classification regressed; this is separate from the preregistered exact criterion.')
    if before_full != 4:
        warnings.append('Baseline capacity prerequisite fails; long-generation capacity is not established.')
    warnings.append('All data are open DEV diagnostics; no independent generalization or coherent-language claim.')
    return {
        'arm': config['mode'], 'updates': steps,
        'scores': {split: {'before': scores[split, 0], 'after': scores[split, steps]} for split in expected},
        'textRetention': {'before': retention[0], 'after': retention[steps], 'relativeBpbChange': bpb_change},
        'capacity': {'before': capacity[0], 'after': capacity[steps],
                     'beforeFullLength': before_full, 'afterFullLength': after_full},
        'frozenChecks': checks, 'favorableDevSignal': 'PASS' if all(checks.values()) else 'FAIL',
        'warnings': warnings,
    }


def audit(registration_path, evidence, baseline, checkpoint, data, auditor_path):
    spec = json.loads(Path(registration_path).read_text())
    receipt = json.loads((evidence / 'receipt.json').read_text())
    require(spec['schema'] == 'lm1-contrast-dev-registration-v1', 'unsupported registration')
    require(receipt['schema'] == 'lm1-contrast-dev-run-v1', 'unsupported receipt')
    require(sha(registration_path) == receipt['registrationSha256'], 'registration identity mismatch')
    require(receipt['exitCode'] == 0 and receipt['timedOut'] is False and receipt['inputsUnchanged'] is True,
            'unsuccessful run receipt')
    arm = receipt['arm']
    require(arm in ('answer', 'replay') and receipt['command'] == spec['commands'][arm], 'command mismatch')
    require(receipt['productionPromotion'] is False, 'unexpected promotion flag')
    require(sha(evidence / 'stdout.tsv') == receipt['stdoutSha256'], 'stdout identity mismatch')
    require(sha(evidence / 'stderr.txt') == receipt['stderrSha256'], 'stderr identity mismatch')
    for path, digest in spec['fileHashes'].items():
        require(sha(path) == digest, 'registered input changed: ' + path)
    command = spec['commands'][arm]
    require(sha(baseline) == spec['fileHashes'][command[-4]], 'baseline binding mismatch')
    registered_data = Path(command[-2])
    for relative in ('note-contrasts-v1/train.tsv', 'note-contrasts-v1/dev.tsv', 'retention.tsv', 'replay.txt', 'retention_text.txt'):
        require(sha(data / relative) == spec['fileHashes'][str(registered_data / relative)], 'data binding mismatch')
    artifact = receipt['artifacts']['final.lm1']
    require(sha(checkpoint) == artifact['sha256'] and checkpoint.stat().st_size == artifact['bytes'], 'checkpoint binding mismatch')
    module_spec = importlib.util.spec_from_file_location('independent_weight_auditor', auditor_path)
    module = importlib.util.module_from_spec(module_spec)
    module_spec.loader.exec_module(module)
    before, after = module.read(baseline), module.read(checkpoint)
    comparison = module.compare(before, after)
    require(before['parameters'] == 132160, 'unexpected parameter count')
    require(comparison['changedNumericWeights'] > 0, 'no numeric weight update')
    require(before['tokenizer'] == spec['fileHashes'][command[-3]], 'checkpoint tokenizer mismatch')
    expected = {'dev': read_rows(data / 'note-contrasts-v1/dev.tsv'), 'retention': read_rows(data / 'retention.tsv')}
    records = parse_log((evidence / 'stdout.tsv').read_text())
    expected_inputs = {Path(path).name: digest for path, digest in spec['fileHashes'].items()
                       if path in (command[-4], command[-3]) or path.startswith(str(registered_data) + '/')}
    logged_inputs = {row['file']: row['sha256'] for row in records['INPUT']}
    require(len(logged_inputs) == len(records['INPUT']) and logged_inputs == expected_inputs, 'logged input mismatch')
    logged_checkpoint = singleton(records, 'CHECKPOINT')
    require(logged_checkpoint['sha256'] == artifact['sha256'] and integer(logged_checkpoint['bytes']) == artifact['bytes'],
            'logged checkpoint mismatch')
    require(integer(singleton(records, 'CONFIG')['parameters']) == before['parameters'], 'logged parameter mismatch')
    analysis = analyze_log(records, expected, spec['maxUpdatesPerArm'], before['config'][1])
    require(analysis['arm'] == arm, 'logged arm mismatch')
    return {
        'schema': 'lm1-contrast-independent-analysis-v1', 'integrityStatus': 'PASS',
        'sourceRegistrationSha256': sha(registration_path),
        'receiptSha256': sha(evidence / 'receipt.json'), 'stdoutSha256': sha(evidence / 'stdout.tsv'),
        'analyzerSha256': sha(Path(__file__)), 'weightAuditorSha256': sha(auditor_path),
        'weights': {'beforeSha256': before['sha256'], 'afterSha256': after['sha256'], **comparison},
        **analysis, 'modelInvocations': 0, 'optimizerUpdatesPerformedByAnalysis': 0,
        'productionPromotion': False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('registration', 'evidence', 'baseline', 'checkpoint', 'data', 'weight-auditor', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists(), 'output already exists')
    result = audit(args.registration, args.evidence, args.baseline, args.checkpoint, args.data, args.weight_auditor)
    with args.output.open('x') as stream:
        json.dump(result, stream, indent=2, sort_keys=True)
        stream.write('\n')
    print(json.dumps({'integrityStatus': result['integrityStatus'], 'favorableDevSignal': result['favorableDevSignal'],
                      'changedWeights': result['weights']['changedNumericWeights'], 'arm': result['arm']}))


if __name__ == '__main__':
    main()
