#!/usr/bin/env python3
"""Measure registered state-only roundtrip."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import resource
import shutil
import signal
import subprocess
import time

from run_recovery import canonical, sha

REG = 'experimental/weight-space-20261104/support-round3/large-state-registration.json'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, required=True)
    p.add_argument('--source-commit', required=True)
    a = p.parse_args(); repo = a.repo.resolve(strict=True); commit = a.source_commit
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('full source commit required')
    env = dict(os.environ, GIT_NO_LAZY_FETCH='1', GIT_TERMINAL_PROMPT='0')
    def blob(path):
        return subprocess.check_output(['git', '-C', str(repo), 'show', commit + ':' + path], env=env, timeout=8)
    raw = blob(REG); reg = json.loads(raw)
    if (repo / REG).read_bytes() != raw or reg['schema'] != 'lm1-large-state-only-v1':
        raise ValueError('registration changed')
    if reg['optimizerUpdates'] != 0 or reg['timeoutSeconds'] != 120 or reg['heapMiB'] != 1024:
        raise ValueError('budget changed')
    for name, digest in reg['sources'].items():
        if sha(repo / name) != digest or hashlib.sha256(blob(name)).hexdigest() != digest:
            raise ValueError('source mismatch: ' + name)
    if hashlib.sha256(canonical(reg['sources'])).hexdigest() != reg['sourceDigest']:
        raise ValueError('source manifest changed')
    for item in reg['files'].values():
        if sha(item['path']) != item['sha256']:
            raise ValueError('input identity changed')
    output = Path(reg['output'])
    if output.exists() or output.is_symlink():
        raise ValueError('measurement output must be new')
    if shutil.disk_usage(output.parent).free < 768 * 1024 * 1024:
        raise ValueError('insufficient disk reserve')
    f = reg['files']
    command = [f['java']['path'], '-Xmx1g', '-XX:ActiveProcessorCount=1', '-cp',
               f['jar']['path'] + ':' + f['stdlib']['path'],
               'at.heinz.deviceai.learning.lm.LmLargeStateProbe', f['model']['path'],
               f['tokenizer']['path'], str(output / 'states'), reg['sourceDigest']]
    if command != reg['command']:
        raise ValueError('command differs from registration')
    output.mkdir()
    (output / 'registration.json').write_bytes(raw)
    started = datetime.now(timezone.utc).isoformat(); before = resource.getrusage(resource.RUSAGE_CHILDREN)
    (output / 'attempt.json').write_bytes(canonical({'sourceCommit': commit, 'startedUtc': started,
                                                   'command': command, 'optimizerUpdatesAllowed': 0}))
    began = time.monotonic(); timed_out = False
    with (output / 'native.tsv').open('xb') as stdout, (output / 'stderr.txt').open('xb') as stderr:
        proc = subprocess.Popen(command, stdout=stdout, stderr=stderr, start_new_session=True)
        try:
            proc.wait(timeout=120)
        except subprocess.TimeoutExpired:
            timed_out = True; os.killpg(proc.pid, signal.SIGKILL); proc.wait(timeout=5)
    usage = resource.getrusage(resource.RUSAGE_CHILDREN)
    after = {key: sha(f[key]['path']) for key in ['model', 'tokenizer']}
    unchanged = all(after[key] == f[key]['sha256'] for key in after)
    lines = (output / 'native.tsv').read_text().splitlines()
    pairs = [line.split('\t', 1) for line in lines]
    valid = all(len(x) == 2 for x in pairs) and len({x[0] for x in pairs}) == len(pairs)
    records = dict(pairs) if valid else {}
    expected = {'status': 'PASS_16M_STATE_ONLY', 'optimizer_updates': '0', 'inference_calls': '0',
                'optimizer_steps_after': '0', 'full_state_roundtrip_byte_identical': 'true',
                'original_inputs_unchanged': 'true', 'parameters': '16011776',
                'moment_values_checked': '32023552', 'moment_values_zero': 'true'}
    native_pass = valid and all(records.get(k) == v for k, v in expected.items())
    passed = proc.returncode == 0 and not timed_out and unchanged and native_pass
    receipt = {'status': 'PASS_16M_STATE_ONLY' if passed else 'FAIL_STATE_PROBE', 'sourceCommit': commit,
               'registrationSha256': hashlib.sha256(raw).hexdigest(), 'startedUtc': started,
               'finishedUtc': datetime.now(timezone.utc).isoformat(), 'exitCode': proc.returncode,
               'timedOut': timed_out, 'elapsedSeconds': time.monotonic() - began,
               'maxRssKiB': usage.ru_maxrss, 'preflightChildrenMaxRssKiB': before.ru_maxrss,
               'rssMethod': 'LINUX_RUSAGE_CHILDREN_MAXIMUM_WITH_PREFLIGHT_BASELINE',
               'rssScope': 'Maximum child RSS; not sum of concurrent RSS or optimizer-only peak',
               'inputHashesAfter': after, 'inputsUnchanged': unchanged, 'nativeRecords': records,
               'stdoutSha256': sha(output / 'native.tsv'), 'stderrSha256': sha(output / 'stderr.txt'),
               'optimizerUpdates': 0, 'historicalOptimizerStateRestored': False,
               'android': 'NOT_RUN', 'quality': 'NOT_EVALUATED', 'promotion': False}
    (output / 'receipt.json').write_bytes(canonical(receipt))
    print(json.dumps(receipt))
    if not passed:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
