#!/usr/bin/env python3
"""Register exact native recovery inputs."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

from build_native import PINS, SOURCE
from run_recovery import ROOT, REGISTRATION, WORKER, canonical, fields, sha


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, required=True)
    p.add_argument('--build', type=Path, required=True)
    p.add_argument('--lib', type=Path, required=True)
    a = p.parse_args()
    repo = a.repo.resolve(strict=True); build = a.build.resolve(strict=True)
    java = Path(shutil.which('java')).resolve(strict=True)
    jar = build / 'checks.jar'; stdlib = a.lib.resolve(strict=True) / 'kotlin-stdlib-2.0.20.jar'
    manifest = json.loads((build / 'build.json').read_text())
    if manifest['compileExitCode'] != 0 or manifest['jarSha256'] != sha(jar):
        raise ValueError('compiled artifact mismatch')
    sources = {SOURCE + name: digest for name, digest in PINS.items()}
    for name in ['training-continuity.patch', 'LmTrainingFixture.kt', 'LmTrainingState.kt',
                 'LmSegmentWorker.kt', 'run_recovery.py', 'register_recovery.py', 'build_native.py']:
        sources[ROOT + name] = sha(repo / ROOT / name)
        if name.endswith('.kt') and manifest['extraSha256'].get(name) != sources[ROOT + name]:
            raise ValueError('compiled source differs: ' + name)
    if manifest['patchSha256'] != sources[ROOT + 'training-continuity.patch']:
        raise ValueError('compiled patch differs')
    for name, digest in sources.items():
        if sha(repo / name) != digest:
            raise ValueError('source mismatch: ' + name)
    description_command = [str(java), '-Xmx1g', '-XX:ActiveProcessorCount=1', '-cp',
                           str(jar) + ':' + str(stdlib), WORKER, 'describe']
    r = subprocess.run(description_command, capture_output=True, timeout=120, check=True)
    description = fields(r.stdout.decode().strip(), 'DESCRIPTION')
    if (description['bos'], description['eos'], description['vocab']) != ('256', '257', '258'):
        raise ValueError('unexpected native tokenizer')
    registration = {'schema': 'lm1-bounded-recovery-v1', 'sources': sources,
                    'sourceDigest': hashlib.sha256(canonical(sources)).hexdigest(),
                    'nativeDescription': description,
                    'descriptionCommand': description_command,
                    'descriptionStdoutSha256': hashlib.sha256(r.stdout).hexdigest(),
                    'limits': {'totalSteps': 24, 'chunkSteps': 8, 'maxAttempts': 4,
                               'timeoutSeconds': 120, 'heapMiB': 1024},
                    'java': {'path': str(java), 'sha256': sha(java)},
                    'jar': {'path': str(jar), 'sha256': sha(jar)},
                    'stdlib': {'path': str(stdlib), 'sha256': sha(stdlib)},
                    'scope': 'Tiny software recovery fixtures; no holdout, no 16M training, no promotion'}
    with (repo / REGISTRATION).open('xb') as out:
        out.write(canonical(registration))
    (build / 'native-description.tsv').write_bytes(r.stdout)
    print(json.dumps({'registration': REGISTRATION, 'sourceDigest': registration['sourceDigest'],
                      'nativeDescription': description, 'optimizerUpdatesDuringRegistration': 0}))


if __name__ == '__main__':
    main()
