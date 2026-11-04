#!/usr/bin/env python3
"""Synthetic source-bound gate checks; never execute a model process."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
from unittest.mock import patch


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def record(kind, **values):
    return kind + '\t' + '\t'.join(f'{key}={value}' for key, value in values.items()) + '\n'


class Fixture:
    def __init__(self, module, root, measured_fail=False):
        self.m = module
        self.root = root
        module.ROOT = root
        module.EXPERIMENT = root / 'experimental/lm1-training-recovery-20261104'
        module.EXPERIMENT.mkdir(parents=True)
        self.inputs = root / 'inputs'
        self.inputs.mkdir()
        self.out = root / 'output'
        self.out.mkdir()
        self.logs = self.out / 'logs'
        self.logs.mkdir()
        self.tokenizer = self.inputs / 'lm1-tokenizer.lmbp'
        self.tokenizer.write_bytes(b'SYNTHETIC tokenizer, not a model input')
        self.jar = root / 'unexecutable.jar'
        self.stdlib = root / 'unexecutable-stdlib.jar'
        self.jar.write_bytes(b'SYNTHETIC ONLY')
        self.stdlib.write_bytes(b'SYNTHETIC ONLY')
        self.data = module.EXPERIMENT / 'intent-data/test.sealed.tsv'
        self.data.parent.mkdir()
        self.data.write_bytes(b'SYNTHETIC SEAL ONLY\n')
        self.reg_path = module.EXPERIMENT / 'registration.json'
        runner = root / 'runner.py'
        runner.write_bytes(Path(module.__file__).read_bytes())
        self.registration = {
            'files': {str(self.data.relative_to(root)): digest(self.data), 'runner.py': digest(runner)},
            'inputs': {'lm1-tokenizer.lmbp': digest(self.tokenizer)},
            'jarSha256': digest(self.jar), 'stdlibSha256': digest(self.stdlib)
        }
        self.reg_path.write_text(json.dumps(self.registration))
        self.args = SimpleNamespace(output=self.out, jar=self.jar, stdlib=self.stdlib,
                                    inputs=self.inputs, source_commit='a' * 40)
        self.study = module.Study(self.args)
        self.calls = []
        for variant, parameters in [('small', 132160), ('wide', 6562304)]:
            directory = self.out / ('intent-' + variant)
            checkpoints = directory / 'checkpoints'
            checkpoints.mkdir(parents=True)
            gate = directory / 'capacity-before.tsv'
            gate.write_bytes(b'SYNTHETIC initial gate\n')
            entries = []
            for step in [0, 32, 64, 128]:
                data = f'SYNTHETIC {variant} checkpoint {step}'.encode()
                model_sha = hashlib.sha256(data).hexdigest()
                model = checkpoints / (model_sha + '.lm1')
                model.write_bytes(data)
                entries.append((step, model, model_sha))
            selected = entries[1]
            log = record('MANIFEST', variant=variant, parameters=parameters,
                         tokenScheduleSha256='b' * 64)
            log += record('INITIAL_CAPACITY_GATE', **{'pass': 'true',
                          'checkpointSha256': entries[0][2], 'logSha256': digest(gate)})
            for step, model, model_sha in entries:
                log += record('CHECKPOINT', step=step, path=model, sha256=model_sha)
                correct = 0 if step == 0 else 14 if step == 32 else 12
                log += record('SCORE', step=step, split='dev', total=24,
                              correct=correct, answerNll='1.0')
            log += record('SELECTION', step=32, checkpointSha256=selected[2], testRead='false',
                          inputsUnchanged='true', devTotal=24, devCorrect=14, devAnswerNll='1.0')
            training_log = self.logs / f'intent-{variant}.tsv'
            training_log.write_text(log)
            receipt = {'returncode': 0, 'timeout': False, 'registeredBytesUnchanged': True,
                       'logSha256': digest(training_log), 'registrationSha256': digest(self.reg_path),
                       'sourceCommit': self.args.source_commit, 'jarSha256': digest(self.jar)}
            (self.logs / f'intent-{variant}.receipt.json').write_text(json.dumps(receipt))
            self.capacity(variant, parameters, selected[1], measured_fail)
        self.study.run = self.fake_run

    def capacity(self, variant, parameters, checkpoint, failed):
        protocol = dict(firstTokenLimitMs='1500.0', generation32LimitMs='5000.0', vmHwmLimitKiB='786432',
                        requestMaxMillis='30000', warmups=1, measuredRepeats=3,
                        ttftMethod='SEPARATE_ONE_TOKEN_CALL_UPPER_BOUND', processTimeoutRequired='true',
                        promptSha256='9c7bfc2a17f395c0219273f50652181d825c4712715339083daa71717e6ff495')
        def memory(phase):
            return record('CAPACITY_MEMORY', phase=phase, vmHwmKiB=100, vmRssKiB=100,
                          heapUsedBytes=100, heapCommittedBytes=200, heapMaxBytes=1024)
        log = record('CAPACITY_PROTOCOL', **protocol) + memory('before_load')
        log += record('CAPACITY_MODEL', checkpointPath=checkpoint, checkpointSha256=digest(checkpoint),
                      checkpointBytes=checkpoint.stat().st_size, tokenizerPath=self.tokenizer,
                      tokenizerSha256=digest(self.tokenizer), tokenizerBytes=self.tokenizer.stat().st_size,
                      parameters=parameters, parameterBytes=4 * parameters, arch='MINGRU', vocab=512,
                      dim=64, layers=2, heads=1, context=128, mlp=2, loadMs='1.0', promptTokens=64,
                      promptIncludesBos='false', fullLayoutLoaded='true', finiteWeights='true')
        log += memory('after_load')
        for phase, count in [('warmup', 1), ('measured', 3)]:
            for trial in range(1, count + 1):
                for requested in [1, 32]:
                    actual = 0 if failed else requested
                    log += record('CAPACITY_TRIAL', phase=phase, trial=trial,
                                  requestedNewTokens=requested, actualNewTokens=actual, promptTokens=64,
                                  promptTruncated='false', stop='EOS' if failed else 'MAX_TOKENS',
                                  elapsedMs='2.0', runtimeMillis=1,
                                  ttftUpperBoundMs='2.0' if requested == 1 and not failed else 'UNAVAILABLE',
                                  textSha256='c' * 64)
                    log += memory(f"{phase}_{trial}_{'first' if requested == 1 else 'generation'}")
        log += memory('final')
        log += record('CAPACITY_RESULT', **{'pass': 'false' if failed else 'true',
                      'reasons': 'NO_FIRST_TOKEN_OBSERVATION,INCOMPLETE_32_TOKEN_WORKLOAD' if failed else 'NONE',
                      'checkpointSha256': digest(checkpoint), 'parameters': parameters, 'loadMs': '1.0',
                      'measuredRepeats': 3, 'maxFirstTokenCallMs': '2.0', 'maxGeneration32CallMs': '2.0',
                      'firstTokenObservations': 0 if failed else 3, 'full32TokenObservations': 0 if failed else 3,
                      'vmHwmKiB': 100, 'finalVmHwmKiB': 100, 'rssMethod': 'MAXIMUM_OBSERVED_VMHWM',
                      'failure': 'UNAVAILABLE'})
        path = self.logs / f'intent-{variant}-capacity-after.tsv'
        path.write_text(log)
        receipt = {'label': f'intent-{variant}-capacity-after',
                   'command': ['java', '-Xms64m', '-Xmx1g', '-cp', str(self.jar) + os.pathsep + str(self.stdlib),
                               self.m.PACKAGE + 'LmCapacityBenchmark', str(checkpoint), str(self.tokenizer)],
                   'startedUtc': '2026-11-04T00:00:00+00:00', 'wallSeconds': 1,
                   'returncode': 1 if failed else 0, 'timeout': False, 'timeoutSeconds': 120,
                   'sourceCommit': self.args.source_commit, 'registrationSha256': digest(self.reg_path),
                   'jarSha256': digest(self.jar), 'logSha256': digest(path), 'registeredBytesUnchanged': True}
        (self.logs / f'intent-{variant}-capacity-after.receipt.json').write_text(json.dumps(receipt))

    def freeze(self):
        value = self.study.freeze_evidence()
        (self.out / 'selection-freeze.json').write_text(json.dumps(value))
        return value

    def fake_run(self, label, main, arguments, **kwargs):
        self.study.verify()
        self.calls.append(label)
        checkpoint = Path(arguments[1])
        log = record('TEST_INPUT', checkpointSha256=digest(checkpoint), testSha256=digest(self.data))
        log += record('SCORE', total=48, correct=0, classCorrect='0,0,0,0')
        (self.logs / (label + '.tsv')).write_text(log)
        return SimpleNamespace(returncode=0)

    def receipt_change(self, key, value):
        path = self.logs / 'intent-small-capacity-after.receipt.json'
        data = json.loads(path.read_text())
        data[key] = value
        path.write_text(json.dumps(data))


def run(module_path, destination):
    spec = importlib.util.spec_from_file_location('tested_runner', module_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    checks = []
    def check(name, action, measured_fail=False):
        with tempfile.TemporaryDirectory(prefix='lm1-r6-synthetic-') as temporary:
            fixture = Fixture(module, Path(temporary), measured_fail)
            try:
                with patch.object(subprocess, 'Popen', side_effect=AssertionError('MODEL_PROCESS_FORBIDDEN')):
                    action(fixture)
                passed, error = True, None
            except Exception as exc:
                passed, error = False, f'{type(exc).__name__}: {exc}'
            checks.append({'name': name, 'passed': passed, 'error': error, 'synthetic_dispatches': len(fixture.calls)})
            print(('PASS ' if passed else 'FAIL ') + name + ('' if error is None else ': ' + error))
    def rejected(fixture, mutate, stage):
        if stage == 'test': fixture.freeze()
        mutate(fixture)
        try:
            (fixture.study.test if stage == 'test' else fixture.study.freeze_evidence)()
        except (RuntimeError, OSError, ValueError):
            assert not fixture.calls, 'dispatch occurred before rejection'
        else:
            raise AssertionError('invalid evidence accepted')
    def valid(fixture):
        frozen = fixture.freeze()
        wanted = 'FAIL' if 'false' in (fixture.logs / 'intent-small-capacity-after.tsv').read_text().splitlines()[-1].split('\t')[1] else 'PASS'
        assert frozen['postTrainingCapacity']['S1']['status'] == wanted
        fixture.study.test()
        summary = json.loads((fixture.out / 'intent-summary.json').read_text())
        assert summary['postTrainingCapacity'] == frozen['postTrainingCapacity']
        assert summary['modelPromotion'] is False
        assert fixture.calls == ['test-S0', 'test-W0', 'test-S1', 'test-W1']
    check('valid_pass_kept_in_freeze_and_summary', valid)
    check('valid_fail_kept_in_freeze_and_summary', valid, True)
    mutations = {
        'missing_log': lambda f: (f.logs / 'intent-small-capacity-after.tsv').unlink(),
        'missing_receipt': lambda f: (f.logs / 'intent-small-capacity-after.receipt.json').unlink(),
        'corrupt_receipt': lambda f: (f.logs / 'intent-small-capacity-after.receipt.json').write_text('{'),
        'foreign_source': lambda f: f.receipt_change('sourceCommit', 'f' * 40),
        'foreign_registration': lambda f: f.receipt_change('registrationSha256', 'f' * 64),
        'foreign_jar': lambda f: f.receipt_change('jarSha256', 'f' * 64),
        'false_log_hash': lambda f: f.receipt_change('logSha256', 'f' * 64),
        'untrusted_command': lambda f: f.receipt_change('command', ['sh', '-c', 'false']),
        'timeout': lambda f: f.receipt_change('timeout', True),
        'false_exit': lambda f: f.receipt_change('returncode', 1),
        'truncated_log': lambda f: (f.logs / 'intent-small-capacity-after.tsv').write_text('CAPACITY_RESULT\tpass=true\n'),
    }
    for stage in ['freeze', 'test']:
        for name, mutate in mutations.items():
            check(stage + '_' + name, lambda f, m=mutate, s=stage: rejected(f, m, s))
    check('sealed_hash_changed_before_dispatch', lambda f: rejected(f, lambda x: x.data.write_bytes(b'changed synthetic seal'), 'test'))
    check('selected_checkpoint_changed_before_dispatch', lambda f: rejected(f, lambda x: Path(x.study.selection('small')['path']).write_bytes(b'changed synthetic checkpoint'), 'test'))
    result = {'runner_sha256': digest(module_path), 'model_processes': 0, 'real_data_read': False,
              'scope': 'complete v1 module; real verify/selection/capacity/freeze/test; only JVM dispatch replaced',
              'checks': checks, 'passed': sum(c['passed'] for c in checks), 'failed': sum(not c['passed'] for c in checks)}
    destination.write_text(json.dumps(result, indent=2) + '\n')
    print(f"checks={len(checks)} passed={result['passed']} failed={result['failed']}")
    return 0 if result['failed'] == 0 else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('runner', type=Path)
    parser.add_argument('result', type=Path)
    args = parser.parse_args()
    raise SystemExit(run(args.runner, args.result))
