#!/usr/bin/env python3
"""Exercise real native interruption recovery."""

import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

import run_recovery as worker


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, required=True)
    p.add_argument('--source-commit', required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args(); repo = a.repo.resolve(strict=True); out = a.output.absolute()
    out.mkdir(parents=True, exist_ok=False)
    reg, reg_hash = worker.preflight(repo, a.source_commit)
    cases = []; runs = []
    def check(name, condition):
        cases.append({'name': name, 'passed': bool(condition)})
        if not condition:
            raise AssertionError(name)
    def argv(job, *extra):
        return [sys.executable, str(repo / worker.ROOT / 'run_recovery.py'), '--repo', str(repo),
                '--source-commit', a.source_commit, '--job', str(job), *extra]
    def invoke(name, job, *extra, expected=0):
        cmd = argv(job, *extra); start = time.monotonic()
        r = subprocess.run(cmd, capture_output=True, timeout=135)
        (out / (name + '.stdout.txt')).write_bytes(r.stdout)
        (out / (name + '.stderr.txt')).write_bytes(r.stderr)
        runs.append({'name': name, 'command': cmd, 'exitCode': r.returncode, 'elapsedSeconds': time.monotonic() - start})
        check(name + '_exit', r.returncode == expected)
        return json.loads(r.stdout.decode().splitlines()[-1])
    def head(job):
        return worker.completed_head(job, reg, a.source_commit, reg_hash)
    def update_lines(path):
        return [s for s in path.read_text().splitlines() if s.startswith('UPDATE\t')]
    def completed_updates(job):
        result = []
        for p in sorted((job / 'completed').glob('*.json')):
            item = json.loads(p.read_text())
            result += update_lines(job / 'attempts' / item['attempt'] / 'stdout.tsv')
        return result
    def all_updates(job):
        return sum(len(update_lines(p)) for p in (job / 'attempts').glob('*/stdout.tsv'))

    reference = out / 'reference'; (reference / 'states').mkdir(parents=True)
    cmd = worker.command(reg, reference, 'run', None, 0, 24)
    r = subprocess.run(cmd, capture_output=True, timeout=120)
    (reference / 'stdout.tsv').write_bytes(r.stdout); (reference / 'stderr.txt').write_bytes(r.stderr)
    runs.append({'name': 'uninterrupted_reference', 'command': cmd, 'exitCode': r.returncode})
    check('uninterrupted_reference_exit', r.returncode == 0)
    done = worker.validate_log(reference / 'stdout.tsv', reg, 0, 24, None)
    reference_state = done['state']; reference_updates = update_lines(reference / 'stdout.tsv')

    normal = out / 'normal'
    answer = invoke('normal', normal)
    check('three_processes_exact_state', answer['state'] == reference_state and answer['committedStep'] == 24)
    check('three_processes_exact_step_logs', completed_updates(normal) == reference_updates)
    count_before = len(list((normal / 'attempts').iterdir()))
    invoke('completed_job_again', normal)
    check('completed_job_no_new_training', len(list((normal / 'attempts').iterdir())) == count_before)

    for phase in ['before-checkpoint', 'after-checkpoint']:
        job = out / phase
        invoke(phase + '-prefix', job, '--segments', '1')
        previous = head(job)
        command = argv(job, '--segments', '1', '--fault', phase)
        stdout_path = out / (phase + '-killed.stdout.txt'); stderr_path = out / (phase + '-killed.stderr.txt')
        with stdout_path.open('xb') as stdout, stderr_path.open('xb') as stderr:
            proc = subprocess.Popen(command, stdout=stdout, stderr=stderr, start_new_session=True)
            attempt = job / 'attempts' / 'attempt-0002'; deadline = time.monotonic() + 15
            pid = None; native_killed = False
            try:
                while time.monotonic() < deadline:
                    log = attempt / 'stdout.tsv'; process = attempt / 'process.json'
                    if log.exists() and process.exists() and 'HOLD\t' in log.read_text():
                        pid = json.loads(process.read_text())['pid']; break
                    if proc.poll() is not None:
                        raise AssertionError('worker exited before injected interruption')
                    time.sleep(.02)
                check(phase + '_reached_real_pause', pid is not None)
                if phase == 'after-checkpoint':
                    second = invoke('concurrent_writer', job, expected=1)
                    check('concurrent_writer_blocked_by_lock', 'BUSY' in second['error'])
                    check('concurrent_writer_created_no_attempt', len(list((job / 'attempts').iterdir())) == 2)
                os.killpg(pid, signal.SIGKILL); native_killed = True
                code = proc.wait(timeout=10)
                runs.append({'name': phase + '-killed', 'command': command, 'exitCode': code,
                             'nativePid': pid, 'injectedSignal': 'SIGKILL'})
                check(phase + '_controller_reports_incomplete', code == 2)
            finally:
                if proc.poll() is None:
                    os.killpg(proc.pid, signal.SIGKILL); proc.wait(timeout=5)
                if pid is not None and not native_killed:
                    try:
                        os.killpg(pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
        receipt = json.loads((attempt / 'receipt.json').read_text())
        check(phase + '_actual_native_sigkill', receipt['exitCode'] == -signal.SIGKILL)
        check(phase + '_previous_commit_preserved', head(job) == previous)
        expected_files = 1 if phase == 'before-checkpoint' else 2
        check(phase + '_checkpoint_publication_boundary', len(list((job / 'states').glob('*.lmtrain'))) == expected_files)
        result = invoke(phase + '-recovered', job)
        check(phase + '_exact_recovered_state', result['state'] == reference_state)
        check(phase + '_exact_committed_step_logs', completed_updates(job) == reference_updates)
        check(phase + '_bounded_replay_32_actual_updates', all_updates(job) == 32)

    last = normal / 'completed' / '0024.json'; original = last.read_bytes()
    try:
        altered = json.loads(original); altered['inputState'] = '0' * 64
        last.write_bytes(worker.canonical(altered))
        answer = invoke('tampered_completed_receipt', normal, expected=1)
        check('tampered_receipt_rejected', 'binding mismatch' in answer['error'])
    finally:
        last.write_bytes(original)
    state_file = normal / 'states' / (reference_state + '.lmtrain'); original = state_file.read_bytes()
    try:
        altered = bytearray(original); altered[len(altered) // 2] ^= 1; state_file.write_bytes(altered)
        answer = invoke('damaged_completed_state', normal, expected=1)
        check('damaged_state_rejected', 'checkpoint damaged' in answer['error'])
    finally:
        state_file.write_bytes(original)
    job_file = normal / 'job.json'; original = job_file.read_bytes()
    try:
        altered = json.loads(original); altered['sourceDigest'] = '0' * 64
        job_file.write_bytes(worker.canonical(altered))
        answer = invoke('wrong_job_identity', normal, expected=1)
        check('wrong_job_identity_rejected', 'job identity differs' in answer['error'])
    finally:
        job_file.write_bytes(original)
    exhausted = out / 'exhausted'; (exhausted / 'attempts').mkdir(parents=True)
    for i in range(1, 5):
        (exhausted / 'attempts' / f'attempt-{i:04}').mkdir()
    answer = invoke('exhausted_budget', exhausted, expected=1)
    check('exhausted_budget_rejected', 'attempt budget exhausted' in answer['error'])
    check('invalid_jobs_created_no_new_training', len(list((normal / 'attempts').iterdir())) == count_before and
          len(list((exhausted / 'attempts').iterdir())) == 4)
    receipt = {'status': 'PASS_REAL_PROCESS_RECOVERY', 'sourceCommit': a.source_commit,
               'registrationSha256': reg_hash, 'sourceDigest': reg['sourceDigest'], 'cases': cases,
               'passed': len(cases), 'failed': 0, 'referenceState': reference_state,
               'referenceWeights': done['weights'], 'committedStepsPerScenario': 24,
               'actualUpdates': {'reference': 24, 'normal': all_updates(normal),
                                 'beforeCheckpointKill': all_updates(out / 'before-checkpoint'),
                                 'afterCheckpointKill': all_updates(out / 'after-checkpoint')},
               'realNativeProcessesKilled': 2, 'runs': runs, 'native16mUpdates': 0,
               'android': 'NOT_RUN', 'promotion': False}
    (out / 'receipt.json').write_bytes(worker.canonical(receipt))
    print(json.dumps({k: receipt[k] for k in ['status', 'passed', 'failed', 'actualUpdates', 'referenceState']}))


if __name__ == '__main__':
    main()
