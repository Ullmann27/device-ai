#!/usr/bin/env python3
"""Bounded combined source verification."""
import argparse
import datetime
import fcntl
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
TOOLS = Path('/workspace/scratch/b6ee64d418cf/toolchain')
LOCKS = [TOOLS / 'gradle-build.lock', Path('/workspace/scratch/d663e8444310/qa/gradle.lock')]
BASE_SHA = '44e45d4c047a1cd65a4ca8b8faccae498a559b09cb4bd5ae99a04db4ac94048d'
TESTS = [
    'cognitive.ProofCarryingMemoryCanonicalEncodingTest',
    'learning.SignedOwnerCorrectionJournalTest',
    'learning.SignedOwnerCorrectionJournalEncodingTest',
    'learning.SignedOwnerCorrectionJournalCoverageTest',
    'learning.ContinuumSourceReplayCoverageTest',
    'learning.ContinuumLearningCoreTest',
    'learning.LearningIntegrationTest',
    'learning.ContinuumSourceNoveltyTest',
    'learning.ContinuumSourceResumeTest',
    'learning.ContinuumSourceRoundJournalTest',
    'learning.ContinuumSourceLearningResumeIntegrationTest',
]
PRODUCTION = [
    'cognitive/ProofCarryingMemory.kt',
    'learning/SignedOwnerCorrectionJournal.kt',
    'learning/ContinuumLearningCore.kt',
    'learning/ContinuumSourceNovelty.kt',
    'learning/ContinuumPublicStudyScheduler.kt',
    'learning/ContinuumLearningExperience.kt',
    'learning/TrainableSubwordEncoder.kt',
    'learning/MentorSource.kt',
    'learning/ContinuumKnowledgeArchive.kt',
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, sort_keys=True, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=Path)
    parser.add_argument('--base-jar', required=True, type=Path)
    parser.add_argument('--toolchain', type=Path, default=TOOLS)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    status = {'startedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'status': 'NOT_RUN', 'compileExit': None, 'junitExit': None,
              'hostOnly': True, 'androidBuild': 'NOT_RUN', 'device': 'NOT_RUN'}
    handles = []
    classes = output / 'classes'
    started = time.monotonic()
    try:
        if sha(args.base_jar) != BASE_SHA:
            raise RuntimeError('Frozen dependency hash mismatch')
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
                        status.update(status='LOCK_BUSY', lock=str(path), exit=75)
                        return 75
                    time.sleep(0.5)
        cache = args.toolchain / 'gradle-cache/caches/modules-2/files-2.1'

        def jar(group, artifact, version):
            matches = list((cache / group / artifact / version).glob('*/*.jar'))
            if len(matches) != 1:
                raise RuntimeError('Expected one dependency: ' + artifact)
            return matches[0]

        compiler = [jar('org.jetbrains.kotlin', name, '2.4.0') for name in (
            'kotlin-compiler-embeddable', 'kotlin-build-tools-api', 'kotlin-stdlib',
            'kotlin-script-runtime', 'kotlin-daemon-embeddable')]
        compiler += [jar('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'),
                     jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
                     args.toolchain / 'gradle-8.11.1/lib/annotations-24.0.1.jar']
        runtime = [args.base_jar, jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.0'),
                   args.toolchain / 'gradle-8.11.1/lib/junit-4.13.2.jar',
                   args.toolchain / 'gradle-8.11.1/lib/hamcrest-core-1.3.jar',
                   args.toolchain / 'android-sdk/platforms/android-36/android.jar']
        sources = [ROOT / ('app/src/main/java/at/heinz/deviceai/' + p) for p in PRODUCTION]
        sources += [ROOT / ('app/src/test/java/at/heinz/deviceai/' + t.replace('.', '/') + '.kt')
                    for t in TESTS]
        snapshot = output / 'sources'
        snapshot.mkdir()
        frozen = []
        for source in sources:
            target = snapshot / source.name
            shutil.copyfile(source, target)
            frozen.append(target)
        resources = ROOT / 'app/src/test/resources'
        resource_files = sorted(p for p in resources.rglob('*') if p.is_file())
        source_hashes = {str(p.relative_to(ROOT)): sha(p) for p in sources}
        resource_hashes = {str(p.relative_to(ROOT)): sha(p) for p in resource_files}
        dependencies = {str(p): sha(p) for p in set(compiler + runtime)}
        manifest = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                    'sources': source_hashes, 'resources': resource_hashes, 'dependencies': dependencies,
                    'runnerSha256': sha(Path(__file__)), 'tests': TESTS, 'locks': list(map(str, LOCKS)),
                    'freshProductionFiles': len(PRODUCTION), 'module': 'DeviceAI_app_debug',
                    'scope': 'Fresh target sources with frozen real C1 dependency classes; synthetic fixtures only'}
        write_json(output / 'manifest.json', manifest)
        java = args.toolchain / 'jdk17/bin/java'
        cp = ':'.join(map(str, runtime))
        commands = {
            'java': [java, '-version'],
            'compile': [java, '-Xmx512m', '-cp', ':'.join(map(str, compiler)),
                        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                        '-jvm-target', '17', '-module-name', 'DeviceAI_app_debug',
                        '-Xfriend-paths=' + str(args.base_jar), '-classpath', cp, '-d', classes, *frozen],
            'junit': [java, '-Xmx512m', '-cp', str(classes) + ':' + str(resources) + ':' + cp,
                      'org.junit.runner.JUnitCore', *['at.heinz.deviceai.' + t for t in TESTS]],
        }
        write_json(output / 'commands.json', {name: list(map(str, cmd)) for name, cmd in commands.items()})
        exit_code = 0
        for name, command in commands.items():
            with (output / (name + '.txt')).open('w') as log:
                result = subprocess.run(list(map(str, command)), cwd=ROOT,
                                        stdout=log, stderr=subprocess.STDOUT, timeout=180)
            status[name + 'Exit'] = result.returncode
            print(name + ':', result.returncode, flush=True)
            if result.returncode:
                exit_code = result.returncode
                break
        if status['junitExit'] is not None:
            junit = (output / 'junit.txt').read_text()
            passed = re.search(r'OK \((\d+) tests?\)', junit)
            failed = re.search(r'Tests run: (\d+),\s+Failures: (\d+)', junit)
            if passed:
                status.update(tests=int(passed[1]), failures=0)
            elif failed:
                status.update(tests=int(failed[1]), failures=int(failed[2]))
            else:
                status['unparsedJUnitSummary'] = True
                exit_code = exit_code or 2
            print(junit, flush=True)
        else:
            print((output / 'compile.txt').read_text() if (output / 'compile.txt').exists() else '', flush=True)
        status['dependenciesUnchanged'] = dependencies == {str(p): sha(p) for p in set(compiler + runtime)}
        status['sourcesUnchanged'] = source_hashes == {str(p.relative_to(ROOT)): sha(p) for p in sources}
        status['resourcesUnchanged'] = resource_hashes == {str(p.relative_to(ROOT)): sha(p) for p in resource_files}
        if not all(status[k] for k in ('dependenciesUnchanged', 'sourcesUnchanged', 'resourcesUnchanged')):
            exit_code = exit_code or 2
        status.update(status='PASS' if exit_code == 0 else 'FAIL', exit=exit_code)
        return exit_code
    except subprocess.TimeoutExpired as error:
        status.update(status='TIMEOUT', error=str(error), exit=124)
        return 124
    except Exception as error:
        status.update(status='ERROR', error=str(error), exit=2)
        return 2
    finally:
        status['seconds'] = round(time.monotonic() - started, 3)
        write_json(output / 'status.json', status)
        print(json.dumps(status), flush=True)
        shutil.rmtree(classes, ignore_errors=True)
        for handle in reversed(handles):
            handle.close()


if __name__ == '__main__':
    sys.exit(main())
