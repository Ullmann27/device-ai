#!/usr/bin/env python3
"""Adapter for the unchanged repository capture CLI, with focused test sources."""
import os
from pathlib import Path
import re
import sys
import run


def main():
    settings = run.resolve_settings()
    repo = settings['repo']
    args = sys.argv[1:]
    if len(args) < 7 or args[0] != '-I' or Path(args[1]).resolve() != repo / 'tools/capture/force_capture.gradle' or args[2] != ':app:testDebugUnitTest':
        raise ValueError('Only the existing capture_jarvis.py invocation is accepted')
    if args[3] != '-PcontinuumUiWidgetTests=true' or not args[4].startswith('-PcontinuumUiEvidenceDir='):
        raise ValueError('Expected explicit UI evidence arguments')
    remaining = args[5:]
    if len(remaining) % 2 or any(remaining[i] != '--tests' for i in range(0, len(remaining), 2)):
        raise ValueError('Only explicit capture test methods are accepted')
    tests = remaining[1::2]
    if not tests or len(tests) != len(set(tests)):
        raise ValueError('Select distinct capture cases')
    expected = os.environ.get('CONTINUUM_CAPTURE_SOURCE_COMMIT', '')
    if Path(os.environ.get('CONTINUUM_BUILD_ROOT', '')).resolve() != repo or not re.fullmatch(r'[0-9a-f]{40}', expected):
        raise ValueError('Capture source provenance was not supplied by the outer CLI')
    version = os.environ.get('CONTINUUM_NATIVE_VERSION_CODE', '')
    log_name = os.environ.get('CONTINUUM_BUILD_LOG', '')
    if not re.fullmatch(r'[0-9]{2,4}', version) or not re.fullmatch(r'capture-[0-9a-f]{12}-[0-9]+\.log', log_name):
        raise ValueError('Set the explicit native version; use the outer capture CLI')
    forwarded = [sys.executable, str(run.HERE / 'run.py'), '--mode', 'ui-tests', '--version-code', version,
        '--label', log_name[:-4], '--expected-sha', expected, '--capture-dir', args[4].split('=', 1)[1],
        '--heap-mib', '1536', '--metaspace-mib', '384', '--resource-sample-seconds', '2',
        '--focus-test-sources', '--test-helper', 'app/src/uiTest/java/at/heinz/deviceai/JarvisHomeTestRoutes.kt']
    for test in tests:
        forwarded += ['--test', test]
    os.execv(sys.executable, forwarded)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError) as error:
        print('NATIVE_CAPTURE_BLOCKED: ' + str(error), file=sys.stderr)
        raise SystemExit(69)
