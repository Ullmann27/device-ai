#!/usr/bin/env python3
"""Bounded native training recovery."""

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import math
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
import uuid

ROOT = 'experimental/weight-space-20261104/support-round3/'
REGISTRATION = ROOT + 'recovery-registration.json'
WORKER = 'at.heinz.deviceai.learning.lm.LmSegmentWorker'
HEX = re.compile(r'[0-9a-f]{64}')


def sha(path):
    if Path(path).is_symlink() or not Path(path).is_file():
        raise ValueError('regular file required: ' + str(path))
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''):
            h.update(block)
    return h.hexdigest()


def canonical(value):
    return (json.dumps(value, sort_keys=True, indent=2, allow_nan=False) + '\n').encode()


def publish(path, value):
    raw = canonical(value)
    temp = path.parent / ('.json-' + uuid.uuid4().hex + '.partial')
    try:
        with temp.open('xb') as stream:
            stream.write(raw); stream.flush(); os.fsync(stream.fileno())
        os.link(temp, path)
        fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    finally:
        temp.unlink(missing_ok=True)


def regular_directory(path):
    if path.is_symlink():
        raise ValueError('symlink directory rejected')
    path.mkdir(parents=True, exist_ok=True)
    if not path.is_dir():
        raise ValueError('directory required')


@contextmanager
def job_lock(job):
    fd = os.open(job / 'worker.lock', os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    try:
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError('BUSY: another worker holds this job') from error
        yield fd
    finally:
        os.close(fd)


def read_json(path):
    sha(path)
    return json.loads(path.read_bytes())


def preflight(repo, commit):
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('full source commit required')
    env = dict(os.environ, GIT_NO_LAZY_FETCH='1', GIT_TERMINAL_PROMPT='0')
    def blob(path):
        return subprocess.check_output(['git', '-C', str(repo), 'show', commit + ':' + path],
                                       env=env, timeout=8, stderr=subprocess.PIPE)
    raw = blob(REGISTRATION)
    if (repo / REGISTRATION).read_bytes() != raw:
        raise ValueError('registration differs from committed bytes')
    reg = json.loads(raw)
    if reg['schema'] != 'lm1-bounded-recovery-v1' or reg['limits'] != {
            'totalSteps': 24, 'chunkSteps': 8, 'maxAttempts': 4, 'timeoutSeconds': 120, 'heapMiB': 1024}:
        raise ValueError('unexpected recovery budget')
    for name, digest in reg['sources'].items():
        if sha(repo / name) != digest or hashlib.sha256(blob(name)).hexdigest() != digest:
            raise ValueError('source identity changed: ' + name)
    if hashlib.sha256(canonical(reg['sources'])).hexdigest() != reg['sourceDigest']:
        raise ValueError('source manifest mismatch')
    for key in ['java', 'jar', 'stdlib']:
        item = reg[key]
        if sha(item['path']) != item['sha256']:
            raise ValueError('executable identity changed: ' + key)
    d = reg['nativeDescription']
    if (d['bos'], d['eos'], d['vocab'], d['total'], d['chunk']) != ('256', '257', '258', '24', '8'):
        raise ValueError('native sequence contract mismatch')
    if not HEX.fullmatch(d['data']) or len(d['targets'].split(',')) != 24 or len(d['rates'].split(',')) != 24:
        raise ValueError('invalid native schedule')
    return reg, hashlib.sha256(raw).hexdigest()


def command(reg, job, mode, state, start, end):
    return [reg['java']['path'], '-Xmx1g', '-XX:ActiveProcessorCount=1', '-cp',
            reg['jar']['path'] + ':' + reg['stdlib']['path'], WORKER, mode,
            reg['sourceDigest'], reg['nativeDescription']['data'], str(job / 'states'),
            state or 'NEW', str(start), str(end)]


def fields(line, tag):
    parts = line.split('\t')
    if parts[0] != tag:
        raise ValueError('wrong native record')
    pairs = [p.split('=', 1) for p in parts[1:]]
    if any(len(p) != 2 for p in pairs) or len({p[0] for p in pairs}) != len(pairs):
        raise ValueError('malformed native record')
    return dict(pairs)


def validate_log(path, reg, start, end, input_state):
    lines = path.read_text().splitlines()
    if len(lines) != end - start + 3:
        raise ValueError('incomplete or extra native records')
    if fields(lines[0], 'START') != {'start': str(start), 'end': str(end), 'input': input_state or 'NEW'}:
        raise ValueError('wrong native start')
    d = reg['nativeDescription']
    for step, line in enumerate(lines[1:-2], start):
        v = fields(line, 'UPDATE')
        if (set(v) != {'step', 'targets', 'rate', 'loss'} or v['step'] != str(step + 1) or
                v['targets'] != d['targets'].split(',')[step] or v['rate'] != d['rates'].split(',')[step] or
                not math.isfinite(float(v['loss']))):
            raise ValueError('invalid native update evidence')
    saved = fields(lines[-2], 'SAVED'); done = fields(lines[-1], 'DONE')
    if (set(done) != {'start', 'end', 'state', 'weights', 'data', 'parameters'} or
            done['start'] != str(start) or done['end'] != str(end) or
            done['data'] != d['data'] or done['parameters'] != d['parameters'] or
            not HEX.fullmatch(done['state']) or not HEX.fullmatch(done['weights']) or
            saved != {'step': str(end), 'state': done['state'], 'weights': done['weights']}):
        raise ValueError('invalid native completion')
    return done


def completed_head(job, reg, commit, reg_hash):
    paths = sorted((job / 'completed').glob('*.json'))
    if [p.name for p in paths] != [f'{8 * i:04}.json' for i in range(1, len(paths) + 1)] or len(paths) > 3:
        raise ValueError('completed checkpoints are not a valid prefix')
    start, state, weights = 0, None, None
    for path in paths:
        item = read_json(path); end = start + 8
        if (item['registrationSha256'] != reg_hash or item['sourceCommit'] != commit or
                item['start'] != start or item['end'] != end or item['inputState'] != state or
                not re.fullmatch(r'attempt-[0-9]{4}', item['attempt'])):
            raise ValueError('completed receipt binding mismatch')
        attempt = job / 'attempts' / item['attempt']
        receipt = read_json(attempt / 'receipt.json'); before = read_json(attempt / 'start.json')
        if (sha(attempt / 'receipt.json') != item['attemptReceiptSha256'] or
                type(receipt['exitCode']) is not int or receipt['exitCode'] != 0 or
                receipt['timedOut'] is not False or receipt['startSha256'] != sha(attempt / 'start.json') or
                before['start'] != start or before['end'] != end or before['inputState'] != state or
                before['command'] != command(reg, job, 'run', state, start, end) or
                before['sourceCommit'] != commit or before['registrationSha256'] != reg_hash or
                receipt['stdoutSha256'] != sha(attempt / 'stdout.tsv') or
                receipt['stderrSha256'] != sha(attempt / 'stderr.txt')):
            raise ValueError('attempt evidence mismatch')
        done = validate_log(attempt / 'stdout.tsv', reg, start, end, state)
        if done['state'] != item['state'] or done['weights'] != item['weights']:
            raise ValueError('checkpoint completion mismatch')
        if sha(job / 'states' / (done['state'] + '.lmtrain')) != done['state']:
            raise ValueError('completed checkpoint damaged')
        start, state, weights = end, done['state'], done['weights']
    return start, state, weights


def run(repo, commit, job, segments, fault):
    if segments not in range(1, 4) or fault not in {'none', 'before-checkpoint', 'after-checkpoint'}:
        raise ValueError('invalid bounded invocation')
    if fault != 'none' and segments != 1:
        raise ValueError('fault injection needs one segment')
    reg, reg_hash = preflight(repo, commit)
    regular_directory(job)
    with job_lock(job) as lock_fd:
        for name in ['states', 'attempts', 'completed']:
            regular_directory(job / name)
        identity = {'sourceCommit': commit, 'registrationSha256': reg_hash,
                    'sourceDigest': reg['sourceDigest'], 'dataDigest': reg['nativeDescription']['data']}
        job_file = job / 'job.json'
        if job_file.exists():
            if read_json(job_file) != identity:
                raise ValueError('job identity differs')
        else:
            publish(job_file, identity)
        start, state, weights = completed_head(job, reg, commit, reg_hash)
        if state:
            check = subprocess.run(command(reg, job, 'verify', state, start, start),
                                   capture_output=True, timeout=120, pass_fds=(lock_fd,))
            check.check_returncode()
            expected = {'step': str(start), 'state': state, 'weights': weights}
            if fields(check.stdout.decode().strip(), 'VERIFIED') != expected:
                raise ValueError('native checkpoint verification differs')
        for _ in range(segments):
            if start == 24:
                break
            attempts = sorted((job / 'attempts').iterdir())
            if any(not re.fullmatch(r'attempt-[0-9]{4}', p.name) for p in attempts):
                raise ValueError('unknown attempt path')
            if len(attempts) >= 4:
                raise ValueError('attempt budget exhausted')
            attempt = job / 'attempts' / f'attempt-{len(attempts) + 1:04}'
            attempt.mkdir()
            end = start + 8
            mode = 'run' if fault == 'none' else 'hold-' + fault
            argv = command(reg, job, mode, state, start, end)
            publish(attempt / 'start.json', dict(identity, command=argv, start=start, end=end,
                    inputState=state, startedUtc=datetime.now(timezone.utc).isoformat()))
            began = time.monotonic(); timed_out = False
            with (attempt / 'stdout.tsv').open('xb') as stdout, (attempt / 'stderr.txt').open('xb') as stderr:
                proc = subprocess.Popen(argv, stdout=stdout, stderr=stderr, start_new_session=True,
                                        stdin=subprocess.PIPE if fault != 'none' else subprocess.DEVNULL,
                                        pass_fds=(lock_fd,))
                publish(attempt / 'process.json', {'pid': proc.pid, 'processGroup': proc.pid})
                try:
                    proc.wait(timeout=120)
                except subprocess.TimeoutExpired:
                    timed_out = True; os.killpg(proc.pid, signal.SIGKILL); proc.wait(timeout=5)
                finally:
                    if proc.stdin:
                        proc.stdin.close()
            receipt = {'exitCode': proc.returncode, 'timedOut': timed_out,
                       'elapsedSeconds': time.monotonic() - began, 'startSha256': sha(attempt / 'start.json'),
                       'stdoutSha256': sha(attempt / 'stdout.tsv'), 'stderrSha256': sha(attempt / 'stderr.txt')}
            publish(attempt / 'receipt.json', receipt)
            if proc.returncode != 0 or timed_out or fault != 'none':
                print(json.dumps({'status': 'INCOMPLETE_SEGMENT', 'committedStep': start, 'attempt': attempt.name}))
                return 2
            done = validate_log(attempt / 'stdout.tsv', reg, start, end, state)
            if sha(job / 'states' / (done['state'] + '.lmtrain')) != done['state']:
                raise ValueError('new checkpoint identity mismatch')
            publish(job / 'completed' / f'{end:04}.json', {
                'sourceCommit': commit, 'registrationSha256': reg_hash, 'start': start, 'end': end,
                'inputState': state, 'state': done['state'], 'weights': done['weights'], 'attempt': attempt.name,
                'attemptReceiptSha256': sha(attempt / 'receipt.json')})
            start, state, weights = end, done['state'], done['weights']
        print(json.dumps({'status': 'COMPLETED' if start == 24 else 'CHECKPOINTED',
                          'committedStep': start, 'state': state, 'weights': weights}))
        return 0


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, required=True)
    p.add_argument('--source-commit', required=True)
    p.add_argument('--job', type=Path, required=True)
    p.add_argument('--segments', type=int, default=3)
    p.add_argument('--fault', choices=['none', 'before-checkpoint', 'after-checkpoint'], default='none')
    a = p.parse_args()
    try:
        return run(a.repo.resolve(strict=True), a.source_commit, a.job.absolute(), a.segments, a.fault)
    except Exception as error:
        print(json.dumps({'status': 'BLOCKED', 'error': type(error).__name__ + ': ' + str(error)}))
        return 1


if __name__ == '__main__':
    sys.exit(main())
