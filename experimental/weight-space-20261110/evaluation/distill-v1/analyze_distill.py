#!/usr/bin/env python3
"""Strict EXP-12 analysis with unchanged EXP-05 metrics."""

import argparse
import collections
import copy
import csv
import hashlib
import importlib.util
import json
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def parse_log(text, base):
    fields = dict(base.FIELDS)
    fields['CONFIG'] += ' teacherWeight experiment teacherScope'
    fields['UPDATE'] += ' teacherKl'
    fields['RESULT'] += ' teacherUnchanged teacherCacheUnchanged'
    fields['TEACHER'] = 'phase parameterSha256 cacheSha256 examples rows source originalCheckpointSha256'
    fields['SCHEDULE'] = 'step answerIds replayIndices'
    records = collections.defaultdict(list)
    for line in text.splitlines():
        pieces = line.split('\t')
        kind = pieces[0]
        require(kind in fields, 'unknown record type')
        pairs = [value.split('=', 1) for value in pieces[1:]]
        require(all(len(pair) == 2 for pair in pairs), 'malformed field')
        row = dict(pairs)
        require(len(row) == len(pairs), 'duplicate field')
        expected = set(fields[kind].split())
        if kind == 'TEACHER' and row.get('phase') == 'before':
            expected.add('elapsedMillis')
        require(set(row) == expected, 'unexpected fields')
        records[kind].append(row)
    return records


def analyze_records(records, expected, base, steps=128, vocab=512):
    original = copy.deepcopy(records)
    config = base.singleton(original, 'CONFIG')
    require(config['mode'] == 'distill', 'unexpected distillation mode')
    require(base.number(config.pop('teacherWeight')) == 1.0, 'unexpected teacher weight')
    require(config.pop('experiment') == 'EXP-12', 'unexpected experiment')
    require(config.pop('teacherScope') == 'replay.txt-only', 'unexpected teacher scope')
    config['mode'] = 'replay'
    kls = []
    for row in original['UPDATE']:
        value = base.number(row.pop('teacherKl'))
        require(value >= -1e-5, 'negative KL outside rounding tolerance')
        kls.append(value)
    original.pop('TEACHER', None)
    original.pop('SCHEDULE', None)
    result_record = base.singleton(original, 'RESULT')
    require(base.boolean(result_record.pop('teacherUnchanged')), 'teacher changed')
    require(base.boolean(result_record.pop('teacherCacheUnchanged')), 'teacher cache changed')
    result = base.analyze_log(original, expected, steps, vocab)
    result['arm'] = 'distill'
    result['exp05FrozenChecks'] = result.pop('frozenChecks')
    result['exp05FavorableDevSignal'] = result.pop('favorableDevSignal')
    prior = result['scores']['retention']['before']['restrictedCorrect']
    current = result['scores']['retention']['after']['restrictedCorrect']
    baseline_capacity = result['capacity']['before']
    current_capacity = result['capacity']['after']
    original_successes = [identity for identity, row in baseline_capacity.items() if row['tokens'] == 32]
    require(original_successes == [2, 3], 'unexpected baseline capacity identities')
    require(prior == 32, 'unexpected restricted retention baseline')
    frozen = result['exp05FrozenChecks']
    checks = {
        'devExactGainAtLeastEight': frozen['devExactGainAtLeastEight'],
        'retentionExactNoDecline': frozen['retentionExactNoDecline'],
        'textBpbIncreaseAtMostFivePercent': frozen['textBpbIncreaseAtMostFivePercent'],
        'restrictedRetentionAtLeastOriginal32': current >= prior,
        'originalCapacityIdsTwoAndThreePreserved': all(current_capacity[identity]['tokens'] == 32 for identity in original_successes),
    }
    result.update({
        'exp12DevelopmentChecks': checks,
        'exp12DevelopmentSignal': 'PASS' if all(checks.values()) else 'FAIL',
        'candidateAllFourCapacityProbes': result['capacity']['afterFullLength'] == 4,
        'teacherKl': {'first': kls[0], 'last': kls[-1], 'minimum': min(kls), 'maximum': max(kls)},
    })
    return result


def baseline_records(text):
    kinds = {'CASE', 'SCORE', 'RETENTION', 'CAPACITY'}
    return [line for line in text.splitlines()
            if line.split('\t')[0] in kinds and '\tstep=0\t' in line]


def parameter_digest(model):
    digest = hashlib.sha256()
    for name, rows, cols, bits, _ in model['tensors']:
        digest.update(f'{name}:{rows}:{cols}\n'.encode())
        digest.update(bits)
    return digest.hexdigest()


def validate_teacher(records, model, base):
    teachers = records['TEACHER']
    require(len(teachers) == 2, 'expected before/after teacher records')
    before, after = teachers
    require(before['phase'] == 'before' and after['phase'] == 'after', 'unexpected teacher phases')
    actual = parameter_digest(model)
    require(before['parameterSha256'] == after['parameterSha256'] == actual,
            'teacher does not match original weights')
    require(before['originalCheckpointSha256'] == after['originalCheckpointSha256'] == model['sha256'],
            'teacher original checkpoint mismatch')
    require(before['cacheSha256'] == after['cacheSha256'] and
            len(before['cacheSha256']) == 64 and all(c in '0123456789abcdef' for c in before['cacheSha256']),
            'teacher cache identity mismatch')
    for key in ('examples', 'rows', 'source'):
        require(before[key] == after[key], 'teacher cache layout changed')
    require(base.integer(before['examples']) == 16 and base.integer(before['rows']) > 0,
            'unexpected teacher cache size')
    require(before['source'] == 'replay.txt', 'unexpected teacher data')
    base.integer(before['elapsedMillis'])
    return {'parameterSha256': actual, 'cacheSha256': before['cacheSha256'],
            'examples': 16, 'rows': base.integer(before['rows']),
            'originalWeightsIndependentlyBound': True, 'cacheUnchanged': True}


class JavaRandom:
    def __init__(self, seed):
        self.seed = (seed ^ 0x5DEECE66D) & ((1 << 48) - 1)

    def next31(self):
        self.seed = (self.seed * 0x5DEECE66D + 0xB) & ((1 << 48) - 1)
        return self.seed >> 17

    def next_int(self, bound):
        require(0 < bound < 2**31, 'invalid RNG bound')
        if bound & (bound - 1) == 0:
            return (bound * self.next31()) >> 31
        while True:
            bits = self.next31()
            value = bits % bound
            if bits - value + bound - 1 < 2**31:
                return value


def validate_schedule(records, train_path, base):
    with Path(train_path).open(newline='') as stream:
        rows = list(csv.DictReader(stream, delimiter='\t'))
    groups = [[row['id'] for row in rows if row['label'] == label] for label in ('0', '3')]
    require([len(group) for group in groups] == [64, 64], 'unexpected training class sizes')
    schedule = records['SCHEDULE']
    require([base.integer(row['step']) for row in schedule] == list(range(1, 129)), 'invalid schedule steps')
    rng = JavaRandom(20261110)
    for step, row in enumerate(schedule):
        expected = [group[rng.next_int(len(group))] for group in groups for _ in range(2)]
        require(row['answerIds'].split(',') == expected, 'answer RNG schedule mismatch')
        require(row['replayIndices'] == f'{step*2 % 16},{(step*2+1) % 16}', 'replay schedule mismatch')
    return {'steps': 128, 'answerDraws': 512, 'replaySelections': 256,
            'javaRandomScheduleMatched': True, 'seed': 20261110}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('registration', 'evidence', 'baseline', 'checkpoint', 'data', 'weight-auditor',
                 'base-analyzer', 'reference-stdout', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists(), 'output already exists')
    raise SystemExit('Receipt schema must be frozen before enabling this command.')


if __name__ == '__main__':
    main()
