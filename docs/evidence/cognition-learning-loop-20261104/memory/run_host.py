#!/usr/bin/env python3
"""Frozen dependencies, freshly compiled targets."""
import argparse
import fcntl
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import time
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[4]
HERE = pathlib.Path(__file__).resolve().parent
TOOLCHAIN = pathlib.Path('/workspace/scratch/b6ee64d418cf/toolchain')
MODULES = TOOLCHAIN / 'gradle-cache/caches/modules-2/files-2.1'
LOCKS = [TOOLCHAIN / 'gradle-build.lock', pathlib.Path('/workspace/scratch/d663e8444310/qa/gradle.lock')]
BASE = pathlib.Path('/workspace/scratch/174c84456ee3/continuum-c1/app/build/tmp/kotlin-classes/debug')

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def jar(group, artifact, version):
    matches = list((MODULES / group / artifact / version).glob('*/*.jar'))
    if len(matches) != 1:
        raise RuntimeError(f'Expected one cached jar: {group}/{artifact}/{version}')
    return matches[0]

def run(command, output):
    print('RUN:', ' '.join(map(str, command)), flush=True)
    with output.open('w') as log:
        result = subprocess.run(list(map(str, command)), cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    print(output.read_text(), flush=True)
    return result.returncode

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=pathlib.Path)
    parser.add_argument('--base-jar', type=pathlib.Path, required=True)
    parser.add_argument('--write-legacy-fixture', action='store_true')
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    handles = []
    deadline = time.monotonic() + 45
    for lock in LOCKS:
        handle = lock.open('a')
        while True:
            try:
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    (output / 'status.json').write_text(json.dumps({'status': 'LOCK_BUSY', 'lock': str(lock), 'exit': 75}))
                    print('LOCK_BUSY:', lock, flush=True)
                    return 75
                time.sleep(1)
        handles.append(handle)
    if not args.base_jar.exists():
        files = sorted(p for p in BASE.rglob('*') if p.is_file())
        before = {str(p.relative_to(BASE)): sha(p) for p in files}
        with zipfile.ZipFile(args.base_jar, 'w', zipfile.ZIP_DEFLATED) as archive:
            for path in files:
                archive.write(path, path.relative_to(BASE))
        after = {str(p.relative_to(BASE)): sha(p) for p in files}
        if before != after:
            raise RuntimeError('C1 classes changed during snapshot')
        (HERE / 'frozen-base-classes.sha256.json').write_text(json.dumps(before, sort_keys=True, indent=2) + '\n')
        (HERE / 'frozen-base.json').write_text(json.dumps({
            'source': str(BASE), 'files': len(files), 'jar': str(args.base_jar),
            'sha256': sha(args.base_jar), 'createdAtUnix': time.time(),
            'sourceHead': subprocess.check_output(['git', '-C', str(BASE.parents[4]), 'rev-parse', 'HEAD'], text=True).strip()
        }, indent=2) + '\n')
    compiler = [jar('org.jetbrains.kotlin', x, '2.4.0') for x in (
        'kotlin-compiler-embeddable', 'kotlin-build-tools-api', 'kotlin-stdlib',
        'kotlin-script-runtime', 'kotlin-daemon-embeddable')]
    compiler += [jar('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'),
                 jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
                 TOOLCHAIN / 'gradle-8.11.1/lib/annotations-24.0.1.jar']
    runtime = [args.base_jar, jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.0'),
               TOOLCHAIN / 'gradle-8.11.1/lib/junit-4.13.2.jar',
               TOOLCHAIN / 'gradle-8.11.1/lib/hamcrest-core-1.3.jar']
    sources = [ROOT / ('app/src/main/java/at/heinz/deviceai/' + p) for p in (
        'cognitive/ProofCarryingMemory.kt', 'learning/SignedOwnerCorrectionJournal.kt')]
    tests = ['at.heinz.deviceai.cognitive.ProofCarryingMemoryCanonicalEncodingTest',
             'at.heinz.deviceai.learning.SignedOwnerCorrectionJournalTest']
    compatibility = ROOT / 'app/src/test/java/at/heinz/deviceai/learning/SignedOwnerCorrectionJournalEncodingTest.kt'
    if compatibility.exists():
        tests.append('at.heinz.deviceai.learning.SignedOwnerCorrectionJournalEncodingTest')
    sources += [ROOT / ('app/src/test/java/' + test.replace('.', '/') + '.kt') for test in tests]
    sources += [HERE / 'LegacyJournalFixtureMain.kt']
    snapshot = output / 'sources'
    snapshot.mkdir()
    frozen_sources = []
    for source in sources:
        target = snapshot / source.name
        shutil.copyfile(source, target)
        frozen_sources.append(target)
    dependencies = {str(path): sha(path) for path in set(compiler + runtime)}
    manifest = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                'module': 'DeviceAI_app_debug', 'sources': {str(p.relative_to(ROOT)): sha(p) for p in sources},
                'dependencies': dependencies, 'locks': list(map(str, LOCKS)), 'tests': tests}
    (output / 'manifest.json').write_text(json.dumps(manifest, sort_keys=True, indent=2) + '\n')
    java = TOOLCHAIN / 'jdk17/bin/java'
    run([java, '-version'], output / 'java.txt')
    cp = ':'.join(map(str, runtime))
    classes = output / 'classes'
    command = [java, '-Xmx512m', '-cp', ':'.join(map(str, compiler)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
               '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-module-name', 'DeviceAI_app_debug',
               '-Xfriend-paths=' + str(args.base_jar), '-classpath', cp, '-d', classes, *frozen_sources]
    commands = [list(map(str, command))]
    compile_exit = run(command, output / 'compile.txt')
    test_exit = None
    if compile_exit == 0:
        command = [java, '-Xmx512m', '-cp', str(classes) + ':' + cp, 'org.junit.runner.JUnitCore', *tests]
        commands.append(list(map(str, command)))
        test_exit = run(command, output / 'junit.txt')
        if args.write_legacy_fixture:
            command = [java, '-Xmx256m', '-cp', str(classes) + ':' + cp,
                       'at.heinz.deviceai.learning.LegacyJournalFixtureMain', output / 'legacy-fixture']
            commands.append(list(map(str, command)))
            fixture_exit = run(command, output / 'legacy-fixture.txt')
            if fixture_exit != 0:
                raise RuntimeError('Legacy fixture generation failed')
    if dependencies != {str(path): sha(path) for path in set(compiler + runtime)}:
        raise RuntimeError('Dependencies changed during test')
    (output / 'commands.json').write_text(json.dumps(commands, indent=2) + '\n')
    (output / 'status.json').write_text(json.dumps({'compileExit': compile_exit, 'junitExit': test_exit,
                                                   'dependenciesUnchanged': True}, indent=2) + '\n')
    shutil.rmtree(classes, ignore_errors=True)
    return compile_exit or test_exit or 0

if __name__ == '__main__':
    sys.exit(main())
