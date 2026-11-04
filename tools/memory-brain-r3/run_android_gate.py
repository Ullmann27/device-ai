#!/usr/bin/env python3
"""Run source-bound Android tests under C1's existing locks; never build an APK."""
from __future__ import annotations

import argparse
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


WORK = Path('/workspace/scratch/624aeb26d481')
QA = WORK / 'qa-r3'
TOOLCHAIN = Path('/workspace/scratch/b6ee64d418cf/toolchain')
CACHE = Path('/workspace/scratch/d663e8444310/gradle-cache')
# This is the observed order in /workspace/scratch/174c84456ee3/qa/run_c1_gate.py.
LOCK_PATHS = (
    TOOLCHAIN / 'gradle-build.lock',
    Path('/workspace/scratch/d663e8444310/qa/gradle.lock'),
)


class GateStopped(Exception):
    """End preflight while retaining its explicit NOT_RUN reason and exit code."""


def now() -> str:
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def digest(path: Path) -> str:
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')


def stop_own_process(process: subprocess.Popen) -> None:
    if process.poll() is not None:
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait(timeout=10)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', required=True, type=Path)
    parser.add_argument('--expected-sha', required=True)
    parser.add_argument('--expected-tree', required=True)
    parser.add_argument('--label', required=True)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument('--full', action='store_true')
    selection.add_argument('--test-class', action='append', default=[])
    parser.add_argument('--ui', action='store_true')
    parser.add_argument('--lint', action='store_true')
    parser.add_argument('--timeout-seconds', type=int, default=1200)
    args = parser.parse_args()
    repo = args.repo.resolve()
    if not repo.is_relative_to(WORK) or not repo.is_dir():
        parser.error('The checkout must be an existing root-owned worktree under ' + str(WORK))
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,95}', args.label):
        parser.error('Use a new simple evidence label, up to 96 characters.')
    for value in (args.expected_sha, args.expected_tree):
        if not re.fullmatch(r'[0-9a-f]{40}', value):
            parser.error('Expected source SHA and tree must be complete lowercase Git object IDs.')
    if args.ui and args.full:
        parser.error('UI mode requires explicit --test-class selections.')
    if not 60 <= args.timeout_seconds <= 1800:
        parser.error('Timeout must be between 60 and 1800 seconds.')
    for value in args.test_class:
        if not re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+', value):
            parser.error('Use explicit fully qualified test class names.')

    output = QA / args.label
    output.mkdir(parents=True, exist_ok=False)
    receipt_path = output / 'result.json'
    record = {
        'schemaVersion': 1,
        'taskId': 'BRAIN-R3-ANDROID-GATE',
        'requestedAt': now(),
        'checkout': str(repo),
        'expectedCommit': args.expected_sha,
        'expectedTree': args.expected_tree,
        'locks': [str(path) for path in LOCK_PATHS],
        'lockWaitLimitSeconds': 45,
        'gradleCache': str(CACHE),
        'runnerSha256': digest(Path(__file__)),
        'status': 'PREFLIGHT',
        'gradleExitCode': None,
        'apkRequested': False,
        'uiMode': args.ui,
        'testClasses': args.test_class,
        'lintRequested': args.lint,
    }
    locks = []
    process = None
    initial = None
    before = None
    code = 70

    def git(*parts: str) -> str:
        return subprocess.check_output(['git', *parts], cwd=repo, text=True).strip()

    def identity() -> dict:
        return {'commit': git('rev-parse', 'HEAD'), 'tree': git('rev-parse', 'HEAD^{tree}'),
                'dirty': git('status', '--porcelain=v1')}

    def source_manifest() -> dict:
        paths = subprocess.check_output(
            ['git', 'ls-files', '-z', '--', 'app', 'gradle', 'gradle.properties',
             'build.gradle.kts', 'settings.gradle.kts'], cwd=repo).decode().split('\0')
        return {name: digest(repo / name) for name in sorted(paths) if name}

    try:
        initial = identity()
        record['sourceBefore'] = initial
        if (initial['commit'] != args.expected_sha or initial['tree'] != args.expected_tree
                or initial['dirty']):
            record.update(status='NOT_RUN_SOURCE_MISMATCH_OR_DIRTY')
            code = 76
            raise GateStopped
        required = [TOOLCHAIN / 'jdk17/bin/java', TOOLCHAIN / 'gradle-8.11.1/bin/gradle',
                    TOOLCHAIN / 'android-sdk/platforms/android-36/android.jar',
                    TOOLCHAIN / 'android-sdk/build-tools/35.0.0/aapt2', CACHE]
        missing = [str(path) for path in required if not path.exists()]
        if missing:
            record.update(status='NOT_RUN_TOOLCHAIN_MISSING', missingPaths=missing)
            code = 75
            raise GateStopped

        deadline = time.monotonic() + 45
        record['status'] = 'WAITING_FOR_LOCKS'
        write_json(receipt_path, record)
        for path in LOCK_PATHS:
            if not path.is_file():
                record.update(status='NOT_RUN_SHARED_LOCK_MISSING', missingLock=str(path))
                code = 75
                raise GateStopped
            handle = path.open('r+')
            locks.append(handle)
            while True:
                try:
                    fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    break
                except BlockingIOError:
                    if time.monotonic() >= deadline:
                        record.update(status='NOT_RUN_SHARED_BUILD_LOCK_BUSY')
                        code = 75
                        raise GateStopped
                    time.sleep(0.25)

        if identity() != initial:
            record.update(status='NOT_RUN_SOURCE_CHANGED_WHILE_WAITING')
            code = 76
            raise GateStopped
        record['freeBytesBefore'] = shutil.disk_usage(repo).free
        if record['freeBytesBefore'] < 1024 ** 3:
            record.update(status='NOT_RUN_LESS_THAN_1_GIB_FREE')
            code = 75
            raise GateStopped
        before = source_manifest()
        write_json(output / 'source-before.json', before)
        record['sourceManifestSha256'] = digest(output / 'source-before.json')

        init = output / 'bounded-tests.init.gradle'
        init.write_text("allprojects {\n"
                        "  tasks.withType(org.gradle.api.tasks.testing.Test).configureEach {\n"
                        "    maxParallelForks = 1\n"
                        "    jvmArgs '-XX:ActiveProcessorCount=2', '-XX:+UseSerialGC'\n"
                        "    outputs.upToDateWhen { false }\n"
                        "  }\n"
                        "}\n")
        android_user = output / 'android-user'
        temporary = output / 'tmp'
        android_user.mkdir()
        temporary.mkdir()
        env = dict(os.environ)
        for name in list(env):
            if name in {'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS',
                        'JAVA_OPTS', 'GRADLE_OPTS', 'KOTLIN_OPTS', 'NODE_TEST_CONTEXT',
                        'CONTINUUM_REPAIR_DEPTH', 'CONTINUUM_QA_DEPTH'} or (
                    name.startswith('CONTINUUM_') and 'SIGNING' in name):
                env.pop(name, None)
        env.update(JAVA_HOME=str(TOOLCHAIN / 'jdk17'),
                   ANDROID_HOME=str(TOOLCHAIN / 'android-sdk'),
                   ANDROID_SDK_ROOT=str(TOOLCHAIN / 'android-sdk'),
                   ANDROID_USER_HOME=str(android_user), GRADLE_USER_HOME=str(CACHE),
                   TMPDIR=str(temporary))
        env['PATH'] = str(TOOLCHAIN / 'jdk17/bin') + os.pathsep + env.get('PATH', '')
        command = [str(TOOLCHAIN / 'gradle-8.11.1/bin/gradle'), '--offline', '--no-daemon',
                   '--no-build-cache', '--max-workers=1', '--console=plain',
                   '-Pkotlin.compiler.execution.strategy=in-process',
                   '-Pandroid.builder.sdkDownload=false',
                   '-Dorg.gradle.jvmargs=-Xmx1536m -XX:ActiveProcessorCount=2 '
                   '-XX:+UseSerialGC -Dfile.encoding=UTF-8',
                   '--init-script', str(init)]
        if args.ui:
            command.append('-PcontinuumUiWidgetTests=true')
        command.append(':app:testDebugUnitTest')
        for test_class in args.test_class:
            command += ['--tests', test_class]
        if args.lint:
            command.append(':app:lintDebug')
        record.update(status='RUNNING', startedAt=now(), command=command,
                      testTaskUpToDateDisabled=True, buildCacheDisabled=True,
                      dependencyDownloadsPermitted=False)
        write_json(receipt_path, record)
        print(json.dumps({'status': 'RUNNING', 'receipt': str(receipt_path),
                          'source': initial}), flush=True)
        started = time.monotonic()
        last_heartbeat = started
        stop_reason = None
        with (output / 'gradle.log').open('x') as log:
            log.write(json.dumps(record) + '\n')
            log.flush()
            process = subprocess.Popen(command, cwd=repo, env=env, stdout=log,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            while process.poll() is None:
                elapsed = time.monotonic() - started
                free = shutil.disk_usage(repo).free
                if elapsed >= args.timeout_seconds or free < 128 * 1024 ** 2:
                    stop_reason = 'TIME_LIMIT' if elapsed >= args.timeout_seconds else 'DISK_RESERVE'
                    stop_own_process(process)
                    break
                if time.monotonic() - last_heartbeat >= 30:
                    print(json.dumps({'status': 'RUNNING', 'elapsedSeconds': round(elapsed, 1),
                                      'freeDiskBytes': free, 'log': str(output / 'gradle.log')}), flush=True)
                    last_heartbeat = time.monotonic()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    pass
            actual_code = process.wait()
            code = actual_code if stop_reason is None else 124
            record.update(gradleExitCode=actual_code, elapsedSeconds=round(time.monotonic() - started, 3),
                          status='PASS' if code == 0 else 'FAIL', stopReason=stop_reason)
            log.write('\nEXIT_CODE=' + str(actual_code) + '\n')

        junit = output / 'junit'
        junit.mkdir()
        counts = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0, 'suites': 0}
        for path in sorted((repo / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
            shutil.copy2(path, junit / path.name)
            suite = ET.parse(path).getroot()
            for name in ('tests', 'failures', 'errors', 'skipped'):
                counts[name] += int(suite.attrib.get(name, '0'))
            counts['suites'] += 1
        record['junit'] = counts
        if code == 0 and (counts['tests'] == 0 or counts['failures'] or counts['errors']):
            record.update(status='FAIL_TEST_RECEIPT')
            code = 1
        if args.lint:
            lint = repo / 'app/build/reports/lint-results-debug.xml'
            if lint.is_file():
                shutil.copy2(lint, output / lint.name)
                record['lintIssues'] = len(ET.parse(lint).getroot().findall('issue'))
            elif code == 0:
                record.update(status='FAIL_LINT_RECEIPT_MISSING')
                code = 1
    except GateStopped:
        pass
    except KeyboardInterrupt:
        record.update(status='INTERRUPTED')
        code = 130
    except Exception as exc:
        record.update(status='ERROR', error=type(exc).__name__ + ': ' + str(exc))
        code = 70
    finally:
        if process is not None and process.poll() is None:
            stop_own_process(process)
        if initial is not None:
            try:
                after_identity = identity()
                record['sourceAfter'] = after_identity
                unchanged = after_identity == initial
                if before is not None:
                    after = source_manifest()
                    write_json(output / 'source-after.json', after)
                    unchanged = unchanged and before == after
                record['sourceUnchanged'] = unchanged
                if not unchanged:
                    record.update(status='FAIL_SOURCE_CHANGED_DURING_GATE')
                    code = 76
            except Exception as exc:
                record.update(status='ERROR_SOURCE_RECHECK', sourceRecheckError=type(exc).__name__)
                code = 70
        record.update(wrapperExitCode=code, finishedAt=now(),
                      freeBytesAfter=shutil.disk_usage(repo).free)
        write_json(receipt_path, record)
        for handle in reversed(locks):
            fcntl.flock(handle, fcntl.LOCK_UN)
            handle.close()
        print(json.dumps(record), flush=True)
    return code


if __name__ == '__main__':
    sys.exit(main())
