#!/usr/bin/env python3
"""Compile pinned native sources into an isolated research artifact."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

SOURCE = 'app/src/main/java/at/heinz/deviceai/learning/lm/'
PINS = {
    'LmTensor.kt': 'a5497f18d09be50f7afbd201d54d97f012f7508f8c53c834ff6c10ef278290ab',
    'LmModel.kt': 'abb4b50cc443344aefe720844aad4a9ca3e3d967973cc31a4e45ca07b732f099',
    'LmBpeTokenizer.kt': 'fcff707a5a1679244cffbb6583e1594b6084e941c7e918f834d71499029b628e',
    'LmCheckpointStore.kt': '09bf68c1c765b6ed625d71672a2aa836fedc3f587546e9197d4b21ee0e446b17',
}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, required=True)
    p.add_argument('--lib', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--patch', type=Path)
    p.add_argument('--extra', type=Path, action='append', required=True)
    a = p.parse_args()
    repo, lib, out = a.repo.resolve(strict=True), a.lib.resolve(strict=True), a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = out / 'source' / SOURCE
    source.mkdir(parents=True)
    for name, expected in PINS.items():
        original = repo / SOURCE / name
        if sha(original) != expected:
            raise ValueError('native source pin changed: ' + name)
        shutil.copyfile(original, source / name)
    if a.patch:
        command = ['patch', '--batch', '--fuzz=0', '-p1', '-i', str(a.patch.resolve(strict=True))]
        r = subprocess.run(command, cwd=out / 'source', capture_output=True, timeout=15)
        (out / 'patch.stdout.txt').write_bytes(r.stdout)
        (out / 'patch.stderr.txt').write_bytes(r.stderr)
        r.check_returncode()
    extras = []
    for item in a.extra:
        target = out / item.name
        if target.exists():
            raise ValueError('duplicate research source: ' + item.name)
        shutil.copyfile(item, target)
        extras.append(target)
    stdlib = lib / 'kotlin-stdlib-2.0.20.jar'
    annotations = lib / 'annotations-24.0.1.jar'
    if not stdlib.is_file() or not annotations.is_file():
        raise ValueError('required pinned toolchain jars unavailable')
    jar = out / 'checks.jar'
    command = ['java', '-Xmx768m', '-cp', str(lib / '*'),
               'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
               '-classpath', str(stdlib) + ':' + str(annotations), '-jvm-target', '17',
               *[str(source / name) for name in PINS], *map(str, extras), '-d', str(jar)]
    r = subprocess.run(command, capture_output=True, timeout=120)
    (out / 'compile.stdout.txt').write_bytes(r.stdout)
    (out / 'compile.stderr.txt').write_bytes(r.stderr)
    manifest = {'compileCommand': command, 'compileExitCode': r.returncode,
                'sourceSha256': {name: sha(source / name) for name in PINS},
                'extraSha256': {x.name: sha(x) for x in extras},
                'toolSha256': {x.name: sha(x) for x in sorted(lib.glob('*.jar'))},
                'patchSha256': sha(a.patch) if a.patch else None,
                'jarSha256': sha(jar) if jar.exists() else None,
                'classpath': str(jar) + ':' + str(stdlib)}
    (out / 'build.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({'output': str(out), 'compileExitCode': r.returncode, 'jarSha256': manifest['jarSha256']}))
    r.check_returncode()


if __name__ == '__main__':
    main()
