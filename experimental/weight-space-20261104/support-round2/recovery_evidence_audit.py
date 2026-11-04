#!/usr/bin/env python3
"""Offline, read-only PR508 evidence audit. Never executes study code or reads sealed data."""
import argparse
import base64
import csv
import hashlib
import io
import json
import math
import os
import sys
import subprocess
from pathlib import PurePosixPath

SNAPSHOT = '7131c1a50a9c697e481a6debe259758dbf422eef'
EXPERIMENT = 'experimental/lm1-training-recovery-20261104/'
EVIDENCE = 'docs/evidence/lm1-training-recovery-20261104/study-v1/'


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def finite(value, name):
    number = float(value)
    require(math.isfinite(number), name + ': non-finite value')
    return number


def records(data):
    result = {}
    for line in data.decode().splitlines():
        fields = line.split('\t')
        if len(fields) < 2 or not all('=' in field for field in fields[1:]):
            continue
        row = {}
        for field in fields[1:]:
            key, value = field.split('=', 1)
            require(key not in row, 'duplicate field: ' + key)
            row[key] = value
        result.setdefault(fields[0], []).append(row)
    return result


def one(rows, tag):
    require(len(rows.get(tag, [])) == 1, 'expected exactly one ' + tag)
    return rows[tag][0]


class GitReader:
    def __init__(self, repo, commit):
        self.repo = repo
        self.commit = commit
        self.cache = {}
        self.env = dict(os.environ, GIT_NO_LAZY_FETCH='1', GIT_TERMINAL_PROMPT='0')
        resolved = subprocess.check_output(['git', '-C', repo, 'rev-parse', commit + '^{commit}'], text=True).strip()
        require(resolved == commit, 'use an immutable full commit SHA')

    def read(self, path):
        normalized = PurePosixPath(path)
        require(not normalized.is_absolute() and '..' not in normalized.parts, 'unsafe path')
        require('sealed' not in path.lower(), 'sealed input is prohibited')
        if path not in self.cache:
            print('READ ' + path, file=sys.stderr, flush=True)
            self.cache[path] = subprocess.check_output(['git', '-C', self.repo, 'show', self.commit + ':' + path], env=self.env, timeout=8)
        return self.cache[path]

    def json(self, path):
        return json.loads(self.read(path))


def check_receipt(receipt, raw, registration_hash, jar_hash, source, capacity=False):
    require(receipt['logSha256'] == sha(raw), 'receipt/log hash mismatch')
    require(receipt['registrationSha256'] == registration_hash, 'registration identity mismatch')
    require(receipt['jarSha256'] == jar_hash, 'JAR identity mismatch')
    require(receipt['sourceCommit'] == source, 'source identity mismatch')
    require(receipt['registeredBytesUnchanged'] is True and receipt['timeout'] is False, 'receipt not completed unchanged')
    require(receipt['returncode'] in ([0, 1] if capacity else [0]), 'unexpected exit code')
    require(finite(receipt['wallSeconds'], 'wallSeconds') >= 0, 'negative wall time')
    require(receipt['timeoutSeconds'] == (120 if capacity else 600), 'process timeout budget mismatch')


def choose(scores):
    require([int(s['step']) for s in scores] == [0, 32, 64, 128], 'unexpected development checkpoints')
    for s in scores:
        require(s['split'] == 'dev' and int(s['total']) == 24, 'unexpected score population')
        require(0 <= int(s['correct']) <= 24, 'invalid correctness count')
        require(finite(s['answerNll'], 'answerNll') >= 0, 'negative answer loss')
    return min(scores[1:], key=lambda s: (-int(s['correct']), float(s['answerNll']), int(s['step'])))


def check_cases(scores, cases):
    require(len(cases) == 96, 'expected 96 development case records')
    for score in scores:
        subset = [c for c in cases if c['step'] == score['step'] and c['split'] == 'dev']
        require(len(subset) == 24 and len({c['id'] for c in subset}) == 24, 'duplicate/missing cases')
        correct = [0] * 4
        totals = [0] * 4
        for c in subset:
            text = base64.b64decode(c['textBase64'], validate=True).decode()
            expected = int(c['expected'])
            require(0 <= expected < 4, 'invalid class')
            ok = text == c['expected'] and int(c['tokens']) == 1 and c['stop'] == 'EOS'
            require(c['correct'] == str(ok).lower(), 'CASE exact-match decision mismatch')
            totals[expected] += 1
            correct[expected] += ok
            require(0 <= finite(c['firstEosProbability'], 'first EOS probability') <= 1, 'invalid EOS probability')
            for field in ('answerNll', 'eosNll'):
                require(finite(c[field], field) >= 0, 'negative NLL')
        counts = {'correct': sum(correct), 'total': len(subset),
                  'prematureEos': sum(c['stop'] == 'EOS' and int(c['tokens']) == 0 for c in subset),
                  'missingEos': sum(c['stop'] != 'EOS' for c in subset),
                  'generatedTokens': sum(int(c['tokens']) for c in subset)}
        for field, value in counts.items():
            require(int(score[field]) == value, 'CASE/SCORE mismatch: ' + field)
        require(score['classCorrect'] == ','.join(map(str, correct)), 'class correctness mismatch')
        require(score['classTotal'] == ','.join(map(str, totals)), 'class denominator mismatch')
        for output, field in [('answerNll', 'answerNll'), ('eosNll', 'eosNll'), ('meanFirstEosProbability', 'firstEosProbability')]:
            # JVM Float strings lose up to several decimal places when printed; tolerate only that representation rounding.
            average = sum(float(c[field]) for c in subset) / 24
            require(math.isclose(float(score[output]), average, rel_tol=1e-7, abs_tol=1e-9), 'CASE/SCORE mean mismatch: ' + output)


def check_capacity(rows, reported, checkpoint, receipt):
    result = one(rows, 'CAPACITY_RESULT')
    require(result == reported, 'capacity summary differs from raw record')
    require(result['checkpointSha256'] == checkpoint, 'capacity checkpoint mismatch')
    require(result['pass'] == 'false' and receipt['returncode'] == 1, 'expected rejected capacity outcome')
    require(set(result['reasons'].split(',')) == {'NO_FIRST_TOKEN_OBSERVATION', 'INCOMPLETE_32_TOKEN_WORKLOAD'}, 'unexpected capacity reasons')
    trials = [r for r in rows['CAPACITY_TRIAL'] if r['phase'] == 'measured']
    require(len(trials) == 6, 'expected six measured capacity calls')
    require(all(t['stop'] == 'EOS' and int(t['actualNewTokens']) == 0 for t in trials), 'capacity EOS/zero-token evidence mismatch')
    require(sum(t['requestedNewTokens'] == '1' for t in trials) == 3, 'missing one-token trials')
    require(sum(t['requestedNewTokens'] == '32' for t in trials) == 3, 'missing 32-token trials')
    require(result['firstTokenObservations'] == result['full32TokenObservations'] == '0', 'observed-token counters mismatch')
    for field in ('loadMs', 'maxFirstTokenCallMs', 'maxGeneration32CallMs', 'vmHwmKiB'):
        require(finite(result[field], field) >= 0, 'invalid capacity metric')
    model = one(rows, 'CAPACITY_MODEL')
    require(model['checkpointSha256'] == checkpoint and model['finiteWeights'] == 'true', 'capacity model identity/finite flag mismatch')


def audit(reader):
    registration_bytes = reader.read(EXPERIMENT + 'registration.json')
    registration = json.loads(registration_bytes)
    registration_hash = sha(registration_bytes)
    diagnostic = reader.json(EVIDENCE + 'diagnostic-summary.json')
    intent = reader.json(EVIDENCE + 'intent-summary.json')
    no_signal = reader.json(EVIDENCE + 'no-development-signal.json')
    source = intent['sourceCommit']
    require(diagnostic['registrationSha256'] == no_signal['registrationSha256'] == registration_hash, 'summary registration mismatch')
    verified = []
    skipped = []
    unavailable = []
    for path, expected in registration['files'].items():
        if 'sealed' in path.lower():
            skipped.append(path)
            continue
        try:
            content = reader.read(path)
        except (subprocess.TimeoutExpired, subprocess.CalledProcessError):
            unavailable.append(path)
            continue
        require(sha(content) == expected, 'registered source/data bytes mismatch: ' + path)
        verified.append(path)
    require(not unavailable, 'registered source/data bytes unavailable: ' + ', '.join(unavailable))
    results = {'snapshot': reader.commit, 'recordedRunSourceCommit': source,
               'registrationSha256': registration_hash, 'verifiedRegisteredFiles': verified,
               'sealedFilesNotRead': skipped, 'registeredFilesUnavailable': unavailable, 'diagnostic': {}, 'intent': {}}

    def run(label, capacity=False):
        raw = reader.read(EVIDENCE + 'logs/' + label + '.tsv')
        receipt = reader.json(EVIDENCE + 'logs/' + label + '.receipt.json')
        check_receipt(receipt, raw, registration_hash, registration['jarSha256'], source, capacity)
        parsed = records(raw)
        for row in parsed.get('INPUT', []):
            path = row['path']
            expected = registration['inputs'].get(PurePosixPath(path).name)
            if expected is None:
                matches = [value for key, value in registration['files'].items() if path.endswith('/' + key) or path == key]
                require(len(matches) == 1, 'unregistered logged input: ' + path)
                expected = matches[0]
            require(row['sha256'] == expected, 'logged input hash mismatch: ' + path)
        for row in parsed.get('SOURCE', []):
            require(registration['files'].get(row['path']) == row['sha256'], 'logged source identity mismatch')
        for row in parsed.get('CAPACITY_MODEL', []):
            require(row['tokenizerSha256'] == registration['inputs']['lm1-tokenizer.lmbp'], 'capacity tokenizer identity mismatch')
        return parsed, receipt

    expected_arms = {'small-standard', 'wide-standard', 'wide-rate-scaled', 'wide-nf-scaled'}
    require(set(diagnostic['variants']) == expected_arms, 'unexpected diagnostic arms')
    schedules = set()
    for name, summary in diagnostic['variants'].items():
        label = 'diagnostic-' + name
        rows, receipt = run(label)
        compact = records(reader.read(EVIDENCE + label + '/diagnostic.tsv'))
        require(rows == compact, 'published diagnostic log differs from captured stdout')
        require(summary['status'] == 'COMPLETED', 'diagnostic did not complete')
        manifest = one(rows, 'MANIFEST')
        schedules.add(manifest['tokenScheduleSha256'])
        require(manifest['steps'] == '16' and manifest['targetTokens'] == '3255', 'diagnostic training budget mismatch')
        require([int(r['step']) for r in rows['TRAIN_STEP']] == list(range(1, 17)), 'missing training step')
        for row in rows['TRAIN_STEP']:
            require(finite(row['nll'], 'training NLL') >= 0, 'negative training loss')
        dev = rows['FIXED_DEV']
        require([int(r['step']) for r in dev] == [0, 1, 4, 16], 'unexpected diagnostic measurement schedule')
        require(summary['before'] == dev[0] and summary['after'] == dev[-1], 'diagnostic loss summary mismatch')
        for row in dev:
            for key in ('contentNll', 'eosNll', 'contentBpb'):
                require(finite(row[key], key) >= 0, 'invalid development loss')
        require(summary['fixedFirstBatch'] == rows['FIXED_FIRST_BATCH'], 'first-batch summary mismatch')
        for row in rows['FIXED_FIRST_BATCH']:
            require(finite(row['nll'], 'fixed-first-batch NLL') >= 0, 'negative fixed-first-batch loss')
        require(summary['eosProbes'] == rows['EOS_PROBE'] and len(rows['EOS_PROBE']) == 28, 'EOS probe summary mismatch')
        require({(int(row['step']), int(row['probe'])) for row in rows['EOS_PROBE']} == {(step, probe) for step in (0, 1, 4, 16) for probe in range(7)}, 'EOS probe schedule mismatch')
        for row in rows['EOS_PROBE']:
            require(0 <= finite(row['eosProbability'], 'EOS probability') <= 1, 'invalid EOS probability')
            delta = finite(row['eosLogit'], 'EOS logit') - finite(row['bestNonEosLogit'], 'non-EOS logit')
            require(math.isclose(delta, finite(row['eosMargin'], 'EOS margin'), abs_tol=2e-6), 'EOS margin mismatch')
        final = one(rows, 'TRAIN_RESULT')
        require(final['checkpointSha256'] == summary['checkpointSha256'] and final['steps'] == '16', 'final diagnostic identity mismatch')
        cap, cap_receipt = run(label + '-capacity-after', True)
        check_capacity(cap, summary['capacity'], summary['checkpointSha256'], cap_receipt)
        expected = summary['capacity']['pass'] == 'true' and float(dev[-1]['contentBpb']) < float(dev[0]['contentBpb']) - 1e-5
        require(summary['limitedContinuationSuccess'] is expected, 'limited continuation decision mismatch')
        results['diagnostic'][name] = {'completed': True, 'beforeContentBpb': float(dev[0]['contentBpb']), 'afterContentBpb': float(dev[-1]['contentBpb']), 'capacityPass': False, 'limitedContinuationSuccess': expected, 'checkpointSha256': summary['checkpointSha256']}
    require(len(schedules) == 1, 'diagnostic arms used different token schedules')
    require(diagnostic['independentHeldOutGeneralizationClaim'] is False, 'unsupported held-out claim')
    schedules = set()
    require(set(intent['arms']) == {'small', 'wide'}, 'unexpected intent arms')
    for name, summary in intent['arms'].items():
        rows, receipt = run('intent-' + name)
        scores = rows['SCORE']
        require(summary['scores'] == scores, 'intent scores differ from raw logs')
        check_cases(scores, rows['CASE'])
        best = choose(scores)
        selection = one(rows, 'SELECTION')
        require(selection == summary['selection'], 'selection summary mismatch')
        require(selection['step'] == best['step'] and selection['devCorrect'] == best['correct'] and selection['devAnswerNll'] == best['answerNll'], 'selection does not follow registered ranking')
        require(selection['testRead'] == 'false' and selection['inputsUnchanged'] == 'true', 'selection boundary mismatch')
        table = list(csv.DictReader(io.StringIO(reader.read(EVIDENCE + 'intent-' + name + '/selection.tsv').decode()), delimiter='\t'))
        require(len(table) == 1 and table[0]['variant'] == name, 'invalid selection table')
        for key in ('step', 'checkpointSha256', 'devCorrect', 'devTotal', 'devAnswerNll'):
            require(table[0][key] == selection[key], 'selection table mismatch: ' + key)
        checkpoints = {r['step']: r['sha256'] for r in rows['CHECKPOINT']}
        require(len(rows['CHECKPOINT']) == 4 and set(checkpoints) == {'0', '32', '64', '128'}, 'checkpoint schedule mismatch')
        require(checkpoints[selection['step']] == selection['checkpointSha256'] and checkpoints['0'] == table[0]['initialSha256'], 'checkpoint selection identity mismatch')
        gate = one(rows, 'INITIAL_CAPACITY_GATE')
        require(gate['pass'] == 'true' and gate['checkpointSha256'] == checkpoints['0'], 'initial capacity gate mismatch')
        require(gate['logSha256'] == sha(reader.read(EVIDENCE + 'intent-' + name + '/capacity-before.tsv')), 'initial capacity log identity mismatch')
        manifest = one(rows, 'MANIFEST')
        require(manifest['steps'] == '128' and manifest['testReadDuringTraining'] == 'false', 'intent manifest mismatch')
        require([int(r['step']) for r in rows['TRAIN_STEP']] == list(range(1, 129)), 'intent training-step sequence mismatch')
        schedules.add(manifest['tokenScheduleSha256'])
        cap, cap_receipt = run('intent-' + name + '-capacity-after', True)
        check_capacity(cap, summary['capacity'], selection['checkpointSha256'], cap_receipt)
        results['intent'][name] = {'selectedStep': int(best['step']), 'correct': int(best['correct']), 'total': 24, 'baselineCorrect': int(scores[0]['correct']), 'capacityPass': False, 'checkpointSha256': selection['checkpointSha256']}
    require(len(schedules) == 1, 'intent arms used different token schedules')
    wide = results['intent']['wide']
    signal = wide['correct'] >= 12 and wide['correct'] >= wide['baselineCorrect'] + 4
    require(signal is False and intent['status'] == no_signal['status'] == 'NO_DEVELOPMENT_SIGNAL', 'development-signal decision mismatch')
    require(intent['testEvaluated'] is False and no_signal['testEvaluated'] is False and intent['modelPromotion'] is False, 'test/promotion status mismatch')
    results.update(status='PASS', developmentSignal=signal, recordedTestEvaluated=False,
                   interpretation='Partial development improvement; preregistered 12/24 threshold missed. All separate post-training capacity gates failed.',
                   limits=['Evidence consistency only; no independent training or inference.', 'No sealed split bytes read. Logs cannot prove a negative about all activity outside these records.', 'Recorded checkpoint, JAR, tokenizer and external baseline identities checked for consistency; unavailable binary bytes were not independently rehashed.', 'Registration studyTrainingStarted=false is a prospective declaration, not evidence that the later logged training did not run.', 'No Android result, held-out accuracy, larger-model causal advantage or 16M/64M validity established.'])
    results['auditedBlobSha256'] = {p: sha(b) for p, b in sorted(reader.cache.items())}
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', required=True)
    parser.add_argument('--commit', default=SNAPSHOT)
    args = parser.parse_args()
    try:
        result = audit(GitReader(args.repo, args.commit))
    except (ValueError, KeyError, TypeError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        print(json.dumps({'status': 'FAIL', 'error': str(error)}, indent=2))
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
