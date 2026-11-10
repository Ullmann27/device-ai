#!/usr/bin/env python3
"""Serial existing Android test captures; no installs, dependencies or signing."""
from __future__ import annotations

import argparse
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

DEFAULT_TESTS = (
    'at.heinz.deviceai.CinemaNativeSequenceUiTest',
    'at.heinz.deviceai.CinemaStopReturnUiTest',
    'at.heinz.deviceai.CinemaButtonMaterialUiTest',
)


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output-dir', type=Path, default=os.environ.get('CONTINUUM_QA_DIR'))
    parser.add_argument('--gradle', default=os.environ.get('CONTINUUM_GRADLE_BIN', 'gradle'))
    parser.add_argument('--lock-file', type=Path, default=os.environ.get('CONTINUUM_QA_LOCK'))
    parser.add_argument('--test', action='append', dest='tests')
    parser.add_argument('--heap-mib', type=int, default=os.environ.get('CONTINUUM_GRADLE_HEAP', '2400'))
    parser.add_argument('--timeout-seconds', type=int, default=600)
    parser.add_argument('--phase', choices=('compile', 'capture'), default=os.environ.get('CONTINUUM_QA_PHASE'))
    parser.add_argument('--dry-run', action='store_true')
    return parser.parse_args()


def validate(args):
    task_alias = os.environ.get('CONTINUUM_TASKS', '')
    allowed_aliases = {
        '': None,
        ':app:compileDebugKotlin,:app:compileDebugUnitTestKotlin': 'compile',
        ':app:testDebugUnitTest': 'capture',
    }
    if task_alias not in allowed_aliases:
        raise ValueError('CONTINUUM_TASKS supports only fixed compile or capture tasks')
    alias_phase = allowed_aliases[task_alias]
    if args.phase and alias_phase and args.phase != alias_phase:
        raise ValueError('Phase conflicts with CONTINUUM_TASKS')
    args.phase = args.phase or alias_phase or 'capture'
    if args.phase not in ('compile', 'capture'):
        raise ValueError('Unsupported phase')
    if args.output_dir is None or args.lock_file is None:
        raise ValueError('Set output-dir and the shared cross-worktree lock-file')
    if not 256 <= args.heap_mib <= 4096 or not 30 <= args.timeout_seconds <= 1800:
        raise ValueError('Heap 256..4096 MiB; timeout 30..1800 seconds')
    args.repo = args.repo.resolve()
    args.output_dir = args.output_dir.resolve()
    args.lock_file = args.lock_file.resolve()
    if args.output_dir == args.repo or args.repo in args.output_dir.parents:
        raise ValueError('Output must be outside source')
    if args.lock_file == args.output_dir or args.output_dir in args.lock_file.parents:
        raise ValueError('Shared lock must be outside output')
    if args.output_dir.exists() and any(args.output_dir.iterdir()):
        raise ValueError('Output directory must be empty')
    raw = os.environ.get('CONTINUUM_TESTS')
    args.tests = args.tests or (raw.split(',') if raw else list(DEFAULT_TESTS))
    if not args.tests or len(args.tests) > 20 or len(set(args.tests)) != len(args.tests):
        raise ValueError('Use 1..20 distinct test selections')
    for test in args.tests:
        if not re.fullmatch(r'at\.heinz\.deviceai\.[A-Za-z0-9_.$]+', test):
            raise ValueError('Only explicit Device AI test names are supported')
    for key in ('JAVA_HOME', 'ANDROID_HOME', 'GRADLE_USER_HOME'):
        value = os.environ.get(key)
        if not value or not Path(value).is_dir():
            raise ValueError(f'Set {key} to an existing configured directory')
    gradle = shutil.which(args.gradle)
    if gradle is None:
        raise ValueError('Set --gradle to an existing executable; nothing is installed')
    args.gradle = gradle
    if not (args.repo / 'tools/capture/force_capture.gradle').is_file():
        raise ValueError('Existing capture init script is missing')


def git(repo, *args):
    return subprocess.check_output(['git', *args], cwd=repo, text=True).strip()


def stop(process):
    if process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait()


def main():
    args = arguments()
    validate(args)
    if git(args.repo, 'status', '--porcelain', '--untracked-files=no'):
        raise ValueError('Commit tracked source changes before capture')
    source = git(args.repo, 'rev-parse', 'HEAD')
    main_tree = git(args.repo, 'rev-parse', 'HEAD:app/src/main')
    command = [args.gradle, '--no-daemon', '--max-workers=1', '--console=plain',
               f'-Dorg.gradle.jvmargs=-Xmx{args.heap_mib}m -XX:MaxMetaspaceSize=384m',
               '-Pkotlin.compiler.execution.strategy=in-process',
               '-PdeviceAiMentorPreview=true', '-PcontinuumOwnerLocalQa=true',
               '-PcontinuumOwnerLocalVersionCode=52', '-PcontinuumUiWidgetTests=true',
               f'-PcontinuumUiEvidenceDir={args.output_dir}']
    if args.phase == 'compile':
        command.extend([':app:compileDebugKotlin', ':app:compileDebugUnitTestKotlin'])
    else:
        command.extend(['-I', str(args.repo / 'tools/capture/force_capture.gradle'), ':app:testDebugUnitTest'])
        for test in args.tests:
            command.extend(['--tests', test])
    if args.dry_run:
        print(json.dumps({'status': 'NOT_RUN', 'phase': args.phase, 'source_commit': source, 'command': command}, indent=2))
        return 0
    args.lock_file.parent.mkdir(parents=True, exist_ok=True)
    with args.lock_file.open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError('Shared Gradle lock is busy; no process was started')
        args.output_dir.mkdir(parents=True, exist_ok=True)
        environment = os.environ.copy()
        environment['CONTINUUM_CAPTURE_SOURCE_COMMIT'] = source
        started = time.monotonic()
        started_ns = time.time_ns()
        with (args.output_dir / 'run.log').open('w') as log:
            process = subprocess.Popen(command, cwd=args.repo, env=environment,
                                       stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                code = process.wait(timeout=args.timeout_seconds)
            except subprocess.TimeoutExpired:
                stop(process)
                code = 124
            except BaseException:
                stop(process)
                raise
        errors, images, tests = [], [], []
        for png in sorted(args.output_dir.glob('*.png')):
            metadata_path = png.with_suffix('.json')
            metadata = json.loads(metadata_path.read_text()) if metadata_path.is_file() else {}
            digest = hashlib.sha256(png.read_bytes()).hexdigest()
            declared = metadata.get('sha256', metadata.get('png_sha256'))
            if metadata.get('source_commit') != source or declared not in (None, digest):
                errors.append(f'Frame provenance mismatch: {png.name}')
            images.append({'file': png.name, 'sha256': digest,
                           'metadata': metadata_path.name if metadata_path.is_file() else None,
                           'declared_hash_matches': None if declared is None else declared == digest})
        result_root = args.repo / 'app/build/test-results/testDebugUnitTest'
        for xml in sorted(result_root.glob('TEST-*.xml')):
            if xml.stat().st_mtime_ns < started_ns:
                continue
            suite = ET.parse(xml).getroot()
            tests.append({key: suite.attrib.get(key) for key in ('name', 'tests', 'failures', 'errors', 'skipped')})
        if args.phase == 'capture' and (not images or not tests):
            errors.append('Missing fresh image or JUnit results')
        if any(int(row.get(key) or 0) for row in tests for key in ('failures', 'errors', 'skipped')):
            errors.append('Fresh JUnit failures/errors/skips retained')
        after = git(args.repo, 'rev-parse', 'HEAD')
        dirty = git(args.repo, 'status', '--porcelain', '--untracked-files=no')
        if after != source or dirty:
            errors.append('Source changed during capture')
        exit_code = code if code != 0 else (2 if errors else 0)
        report = {'source_commit': source, 'application_main_tree': main_tree, 'phase': args.phase,
                  'result_scope': 'KOTLIN_COMPILATION_ONLY' if args.phase == 'compile' else 'HOST_NATIVE_TEST_CAPTURE',
                  'tests_selected': args.tests if args.phase == 'capture' else [],
                  'command': command, 'exit_code': exit_code, 'gradle_exit_code': code,
                  'elapsed_seconds': time.monotonic() - started, 'frames': images, 'junit': tests,
                  'validation_errors': errors, 'post_run_source_commit': after,
                  'tracked_changes_after_run': dirty,
                  'renderer': 'NOT_RUN' if args.phase == 'compile' else 'Fixture-specific; defaults use Robolectric API28 native Canvas / 2D fallback',
                  'Fold7': 'NOT_RUN', 'Filament': 'NOT_RUN', 'GPU': 'NOT_RUN', 'inference': 'NOT_RUN'}
        (args.output_dir / 'capture-run.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps({'output': str(args.output_dir), 'exit_code': exit_code,
                          'phase': args.phase, 'frames': len(images), 'fresh_junit_suites': len(tests)}))
        return exit_code


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, OSError, subprocess.CalledProcessError) as failure:
        print(str(failure), file=sys.stderr)
        sys.exit(2)
