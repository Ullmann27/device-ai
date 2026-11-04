#!/usr/bin/env python3
"""Bounded source-round JVM regression."""
import argparse
import fcntl
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
TOOLS = Path('/workspace/scratch/b6ee64d418cf/toolchain')
CACHE = TOOLS / 'gradle-cache/caches/modules-2/files-2.1'
LOCKS = [TOOLS / 'gradle-build.lock', Path('/workspace/scratch/d663e8444310/qa/gradle.lock')]
BASE_SHA = '44e45d4c047a1cd65a4ca8b8faccae498a559b09cb4bd5ae99a04db4ac94048d'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def jar(group, artifact, version):
    matches = list((CACHE / group / artifact / version).glob('*/*.jar'))
    if len(matches) != 1:
        raise RuntimeError('Cached dependency missing: ' + artifact)
    return matches[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=Path)
    parser.add_argument('--base-jar', required=True, type=Path)
    parser.add_argument('--legacy-start', action='store_true')
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    assert sha(args.base_jar) == BASE_SHA
    handles = []
    deadline = time.monotonic() + 35
    for path in LOCKS:
        handle = path.open('a')
        handles.append(handle)
        while True:
            try:
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    (output / 'status.json').write_text(json.dumps({'status': 'LOCK_BUSY', 'lock': str(path)}))
                    print('LOCK_BUSY', path, flush=True)
                    return 75
                time.sleep(0.5)
    compiler = [jar('org.jetbrains.kotlin', name, '2.4.0') for name in (
        'kotlin-compiler-embeddable', 'kotlin-build-tools-api', 'kotlin-stdlib',
        'kotlin-script-runtime', 'kotlin-daemon-embeddable')]
    compiler += [jar('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'),
                 jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
                 TOOLS / 'gradle-8.11.1/lib/annotations-24.0.1.jar']
    runtime = [args.base_jar, jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.0'),
               TOOLS / 'gradle-8.11.1/lib/junit-4.13.2.jar',
               TOOLS / 'gradle-8.11.1/lib/hamcrest-core-1.3.jar',
               TOOLS / 'android-sdk/platforms/android-36/android.jar']
    sources = [ROOT / ('app/src/main/java/at/heinz/deviceai/learning/' + name + '.kt')
               for name in ('ContinuumSourceNovelty', 'ContinuumKnowledgeArchive',
                            'ContinuumLearningExperience', 'MentorSource',
                            'ContinuumPublicStudyScheduler')]
    tests = ['ContinuumSourceNoveltyTest', 'ContinuumSourceResumeTest']
    if not args.legacy_start:
        tests.append('ContinuumSourceRoundJournalTest')
    sources += [ROOT / ('app/src/test/java/at/heinz/deviceai/learning/' + name + '.kt') for name in tests]
    if args.legacy_start:
        sources.append(Path(__file__).parent / 'LegacyRoundStart.kt')
    snapshot = output / 'sources'
    snapshot.mkdir()
    frozen = []
    for source in sources:
        target = snapshot / source.name
        shutil.copyfile(source, target)
        frozen.append(target)
    dependencies = {str(p): sha(p) for p in set(compiler + runtime)}
    manifest = {
        'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'sources': {str(p.relative_to(ROOT)): sha(p) for p in sources},
        'dependencies': dependencies, 'locks': list(map(str, LOCKS)),
        'legacyStartBoundary': args.legacy_start,
        'tests': tests, 'hostOnly': True, 'deviceTest': False, 'trainingRun': False
    }
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n')
    java = TOOLS / 'jdk17/bin/java'
    cp = ':'.join(map(str, runtime))
    classes = output / 'classes'
    commands = [
        [java, '-version'],
        [java, '-Xmx384m', '-cp', ':'.join(map(str, compiler)),
         'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
         '-jvm-target', '17', '-module-name', 'DeviceAI_app_debug',
         '-Xfriend-paths=' + str(args.base_jar), '-classpath', cp, '-d', classes, *frozen],
        [java, '-Xmx256m', '-cp', str(classes) + ':' + cp,
         'org.junit.runner.JUnitCore', *['at.heinz.deviceai.learning.' + name for name in tests]]
    ]
    (output / 'commands.json').write_text(json.dumps([list(map(str, c)) for c in commands], indent=2) + '\n')
    statuses = {}
    try:
        for name, command in zip(('java', 'compile', 'junit'), commands):
            with (output / (name + '.txt')).open('w') as log:
                result = subprocess.run(list(map(str, command)), cwd=ROOT, stdout=log,
                                        stderr=subprocess.STDOUT, timeout=90)
            statuses[name + 'Exit'] = result.returncode
            print((output / (name + '.txt')).read_text(), flush=True)
            if result.returncode:
                break
        statuses['dependenciesUnchanged'] = dependencies == {str(p): sha(p) for p in set(compiler + runtime)}
        assert statuses['dependenciesUnchanged']
        (output / 'status.json').write_text(json.dumps(statuses, indent=2) + '\n')
        return statuses.get('compileExit', 1) or statuses.get('junitExit', 1)
    finally:
        shutil.rmtree(classes, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
