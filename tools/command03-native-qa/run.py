#!/usr/bin/env python3
"""Serial Android host QA, offline unless dependency resolution is explicit.

No installation, CI dispatch, owner signing, deployment, or device connection.
Focused tests retain original physical sources, assertions, Android SDK and main
compilation. They are explicitly not full-suite or release QA.
"""
import argparse
import fcntl
import fnmatch
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
import zipfile

HERE = Path(__file__).resolve().parent
MEMORY_HARNESS = 'docs/evidence/omega-apk54-20261111/harness/test-memory.gradle'
ORIGINAL_AVATAR_SHA256 = 'c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a'
TASKS = {'compile': ':app:compileDebugKotlin', 'unit-tests': ':app:testDebugUnitTest',
         'ui-compile': ':app:compileDebugUnitTestKotlin', 'ui-tests': ':app:testDebugUnitTest', 'lint': ':app:lintDebug',
         'unsigned': ':app:assembleRelease'}
CAPTURE_CLASS = 'at.heinz.deviceai.JarvisDeterministicCaptureUiTest'
CAPTURE_CASES = frozenset(('compact390', 'compact412', 'mediumShortLandscape',
    'mediumMultiwindow', 'mediumNearSquare', 'expandedCanonical', 'expandedTablet',
    'compactFont150', 'compactFont200', 'mediumFont150', 'mediumFont200',
    'expandedFont150', 'expandedFont200', 'referenceRasterActivityCanvas',
    'commandPhone392', 'commandFold904', 'commandComputer904'))


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def save_json(path, value):
    Path(path).write_text(json.dumps(value, indent=2) + '\n')


def resolve_settings(config_path=None, overrides=None):
    """Only task-specific settings; no discovery/download of an absent toolchain."""
    config_path = Path(config_path or os.environ.get('CONTINUUM_QA_CONFIG', HERE / 'toolchain.json'))
    data = json.loads(config_path.read_text()) if config_path.is_file() else {}
    overrides = overrides or {}
    discovery = next((base for start in (HERE, Path.cwd()) for base in (start, *start.parents)
                      if (base / 'app/build.gradle.kts').is_file()), None)
    if discovery is None and (HERE.parent / 'device-ai-android/app/build.gradle.kts').is_file():
        discovery = HERE.parent / 'device-ai-android'
    defaults = {'repo': discovery}
    mapping = {'repo': 'REPO', 'output_dir': 'OUTPUT_DIR', 'build_dir': 'BUILD_DIR', 'java_home': 'JAVA_HOME',
               'android_sdk': 'ANDROID_SDK', 'android_user_home': 'ANDROID_USER_HOME', 'gradle_home': 'GRADLE_HOME',
               'gradle_user_home': 'GRADLE_CACHE'}
    result = {}
    for name, variable in mapping.items():
        if name == 'output_dir' and result['repo'] is not None:
            defaults[name] = result['repo'].parent / (result['repo'].name + '-native-qa-output')
        if name == 'build_dir' and result.get('output_dir') is not None:
            defaults[name] = result['output_dir'] / 'build'
        if name == 'android_user_home' and result.get('output_dir') is not None:
            defaults[name] = result['output_dir'] / 'android-user'
        explicit = overrides.get(name) or os.environ.get('CONTINUUM_QA_' + variable)
        raw = explicit or data.get(name) or defaults.get(name)
        path = Path(raw).expanduser() if raw else None
        if path is not None and not path.is_absolute() and not explicit and name in data:
            path = config_path.resolve().parent / path
        result[name] = path.resolve() if path is not None else None
    result['build_tools_version'] = str(data.get('build_tools_version', '35.0.0'))
    result['config_path'] = str(config_path.resolve())
    return result


def git(repo, *arguments):
    return subprocess.check_output(['git', *arguments], cwd=repo)


def source_state(repo):
    status = git(repo, 'status', '--porcelain=v1').decode()
    untracked = []
    for name in git(repo, 'ls-files', '--others', '--exclude-standard', '-z').decode().split('\0'):
        path = repo / name
        if name and path.is_file() and not path.is_symlink():
            info = path.stat()
            untracked.append({'path': name, 'bytes': info.st_size, 'modified_ns': info.st_mtime_ns})
    return {'commit': git(repo, 'rev-parse', 'HEAD').decode().strip(),
            'tree': git(repo, 'rev-parse', 'HEAD^{tree}').decode().strip(),
            'clean': not status, 'status': status,
            'tracked_diff_sha256': hashlib.sha256(git(repo, 'diff', 'HEAD', '--binary')).hexdigest(),
            'untracked_metadata': untracked}


def validate_cached_models(repo, cache):
    manifest = json.loads((repo / 'app/owner-face-models.lock.json').read_text())
    pins = []
    for model in manifest['models']:
        path = cache / 'caches/continuum-owner-face-models' / (model['sha256'] + '-' + model['filename'])
        if not path.is_file() or path.is_symlink() or path.stat().st_size != model['byteLength'] or digest(path) != model['sha256']:
            raise RuntimeError('Pinned model is not cached; no download attempted: ' + model['id'])
        pins.append({'id': model['id'], 'sha256': model['sha256'], 'bytes': model['byteLength']})
    cv = manifest['opencv']
    group, name, version = cv['coordinate'].split(':')
    candidates = (cache / 'caches/modules-2/files-2.1' / group / name / version).rglob('*.aar')
    if not any(p.is_file() and not p.is_symlink() and p.stat().st_size == cv['byteLength'] and digest(p) == cv['sha256'] for p in candidates):
        raise RuntimeError('Pinned OpenCV AAR is not cached; no download attempted')
    return pins


def resource_snapshot():
    result = {'wall_time': time.time(), 'java_processes': [], 'memory_kib': {}, 'cgroup': {}}
    try:
        for line in Path('/proc/meminfo').read_text().splitlines():
            name, value = line.split(':', 1)
            if name in ('MemTotal', 'MemAvailable', 'SwapTotal', 'SwapFree', 'Shmem'):
                result['memory_kib'][name] = int(value.split()[0])
    except (OSError, ValueError):
        pass
    for directory in Path('/proc').glob('[0-9]*'):
        try:
            fields = dict(line.split(':', 1) for line in (directory / 'status').read_text().splitlines() if ':' in line)
            if fields.get('Name', '').strip() in ('java', 'javac'):
                result['java_processes'].append({'pid': int(directory.name),
                    'parent_pid': int(fields.get('PPid', '0')),
                    'rss_kib': int(fields.get('VmRSS', '0 kB').split()[0])})
        except (OSError, ValueError):
            continue
    result['java_rss_kib'] = sum(item['rss_kib'] for item in result['java_processes'])
    for name in ('memory.max', 'memory.current', 'memory.peak', 'memory.events'):
        try:
            result['cgroup'][name] = (Path('/sys/fs/cgroup') / name).read_text().strip()
        except OSError:
            continue
    result['cgroup_counters_available'] = bool(result['cgroup'])
    return result


def focused_sources(repo, tests, helpers, ui, commit):
    roots = [repo / 'app/src/test/java', repo / 'app/src/test/kotlin']
    if ui:
        roots += [repo / 'app/src/uiTest/java', repo / 'app/src/uiTest/kotlin']
    selected = {}
    for test in tests:
        if '*' in test or '$' in test:
            raise RuntimeError('Focused compilation requires explicit top-level classes or methods')
        parts, found = test.split('.'), []
        while parts and not found:
            for root in roots:
                for suffix in ('.kt', '.java'):
                    candidate = root / ('/'.join(parts) + suffix)
                    if candidate.is_file() and not candidate.is_symlink():
                        found.append(candidate.resolve())
            parts.pop()
        if len(found) != 1:
            raise RuntimeError('Cannot uniquely resolve physical test source: ' + test)
        selected.setdefault(found[0], {'role': 'selected_test', 'filters': []})['filters'].append(test)
    for name in helpers:
        unresolved = repo / name
        path = unresolved.resolve()
        if unresolved.is_symlink() or not path.is_file() or not any(path.is_relative_to(root) for root in roots) or path.suffix not in ('.kt', '.java'):
            raise RuntimeError('Helper must be an existing physical test source: ' + name)
        selected.setdefault(path, {'role': 'existing_helper', 'filters': []})
    files = []
    for path, details in sorted(selected.items()):
        if not path.is_relative_to(repo):
            raise RuntimeError('Focused source escaped the checkout')
        files.append({'path': str(path.relative_to(repo)), 'bytes': path.stat().st_size,
                      'sha256': digest(path), **details})
    return {'scope': 'FOCUSED_HOST_TESTS_ONLY', 'source_commit': commit, 'files': files}


def junit_summary(build, since, selected=()):
    result = {'fresh_files': 0, 'older_files_ignored': 0, 'tests': 0,
              'failures': 0, 'errors': 0, 'skipped': 0, 'classes': [], 'failure_details': []}
    classes = set()
    matched = set()
    for path in sorted((build / 'app-build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
        if path.stat().st_mtime < since:
            result['older_files_ignored'] += 1
            continue
        result['fresh_files'] += 1
        for case in ET.parse(path).getroot().iter('testcase'):
            result['tests'] += 1
            classname = case.get('classname', '')
            fullname = classname + '.' + case.get('name', '')
            classes.add(classname)
            for pattern in selected:
                if fnmatch.fnmatchcase(classname, pattern) or fnmatch.fnmatchcase(fullname, pattern) or fullname.startswith(pattern + '['):
                    matched.add(pattern)
            for tag, key in (('failure', 'failures'), ('error', 'errors'), ('skipped', 'skipped')):
                detail = case.find(tag)
                if detail is not None:
                    result[key] += 1
                    if tag != 'skipped':
                        result['failure_details'].append({'test': fullname, 'kind': tag,
                            'message': detail.get('message', '')[:2000],
                            'detail': (detail.text or '')[:6000]})
    result['classes'] = sorted(classes)
    result['selected_filters_without_fresh_result'] = sorted(set(selected) - matched)
    return result


def validate_unsigned_apk(apk, version, settings, environment):
    tools = settings['android_sdk'] / 'build-tools' / settings['build_tools_version']
    badging = subprocess.check_output([str(tools / 'aapt2'), 'dump', 'badging', str(apk)], text=True, env=environment)
    expected = "package: name='at.heinz.deviceai.continuumlocal' versionCode='%d' versionName='0.3.%d'" % (version, version - 1)
    if expected not in badging or not re.search(r"^native-code:\s*'arm64-v8a'\s*$", badging, re.M) or 'application-debuggable' in badging:
        raise RuntimeError('APK package/version/ABI/debuggable contract failed')
    if "targetSdkVersion:'36'" not in badging or not re.search(r"^(?:sdkVersion|minSdkVersion):'26'$", badging, re.M):
        raise RuntimeError('APK SDK contract differs')
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise RuntimeError('APK ZIP integrity failed')
        if any(re.match(r'META-INF/[^/]+\.(?:SF|RSA|DSA|EC)$', name, re.I) for name in archive.namelist()):
            raise RuntimeError('Unexpected JAR signature')
        avatar_hash = hashlib.sha256(archive.read('assets/avatar3d/continuum-avatar.glb')).hexdigest()
        if avatar_hash != ORIGINAL_AVATAR_SHA256:
            raise RuntimeError('Original avatar bytes differ')
    subprocess.run([str(tools / 'zipalign'), '-c', '-P', '16', '-v', '4', str(apk)],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, env=environment)
    signed = subprocess.run([str(tools / 'apksigner'), 'verify', str(apk)],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=environment)
    if signed.returncode == 0:
        raise RuntimeError('Unexpectedly signed artifact')
    with apk.open('rb') as stream:
        stream.seek(max(0, apk.stat().st_size - 65557))
        tail = stream.read()
        end = tail.rfind(b'PK\x05\x06')
        if end < 0:
            raise RuntimeError('APK end-of-directory record missing')
        offset = int.from_bytes(tail[end + 16:end + 20], 'little')
        if offset >= 16:
            stream.seek(offset - 16)
            if stream.read(16) == b'APK Sig Block 42':
                raise RuntimeError('Unexpected APK signing block')
    return {'path': str(apk), 'bytes': apk.stat().st_size, 'sha256': digest(apk),
            'status': 'UNSIGNED_NOT_INSTALLABLE', 'package': 'at.heinz.deviceai.continuumlocal',
            'version_code': version, 'version_name': '0.3.' + str(version - 1),
            'abi': 'arm64-v8a', 'zip_crc': 'PASS', 'alignment_16k': 'PASS',
            'signature': 'ABSENT_AS_REQUIRED', 'original_avatar_sha256': avatar_hash}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', choices=TASKS, required=True)
    parser.add_argument('--version-code', type=int, required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--config')
    for key in ('repo', 'output-dir', 'java-home', 'android-sdk', 'android-user-home', 'gradle-home', 'gradle-user-home', 'build-dir'):
        parser.add_argument('--' + key)
    parser.add_argument('--resolve-dependencies', action='store_true',
                        help='Permit the normal Gradle repositories for this run; validate pinned assets afterward')
    parser.add_argument('--selection', help='Named selection from the adjacent selections.json')
    parser.add_argument('--test', action='append', default=[])
    parser.add_argument('--full-suite', action='store_true')
    parser.add_argument('--focus-test-sources', action='store_true')
    parser.add_argument('--test-helper', action='append', default=[])
    parser.add_argument('--isolate-test-classes', action='store_true',
                        help='Run selected UI test classes in separate sequential JVMs with unchanged memory limits')
    parser.add_argument('--expected-sha')
    parser.add_argument('--heap-mib', type=int, default=2048)
    parser.add_argument('--metaspace-mib', type=int, default=512)
    parser.add_argument('--resource-sample-seconds', type=int, default=20)
    parser.add_argument('--allow-dirty', action='store_true')
    parser.add_argument('--capture-dir', type=Path)
    parser.add_argument('--plan', action='store_true')
    args = parser.parse_args()
    compile_only = args.mode == 'ui-compile'
    is_ui = args.mode in ('ui-compile', 'ui-tests')
    if args.selection:
        selections = json.loads((HERE / 'selections.json').read_text())
        group = selections.get(args.selection)
        selection_mode = 'ui-tests' if compile_only else args.mode
        if not group or group['mode'] != selection_mode or args.test or args.test_helper or args.full_suite:
            parser.error('Named selection must match the mode and cannot be combined with other tests/helpers')
        args.test = group['tests']
        args.test_helper = group['helpers']
        args.focus_test_sources = True
    if not re.fullmatch(r'[a-z0-9][a-z0-9._-]{0,63}', args.label):
        parser.error('Invalid run label')
    if not 26 <= args.version_code <= 9999:
        parser.error('Version code must be 26–9999')
    if args.expected_sha and not re.fullmatch(r'[0-9a-f]{40}', args.expected_sha):
        parser.error('Expected SHA must have 40 lowercase hexadecimal characters')
    if not 512 <= args.heap_mib <= 4096 or not 256 <= args.metaspace_mib <= 1024:
        parser.error('Heap must be 512–4096 MiB; metaspace 256–1024 MiB')
    if not 2 <= args.resource_sample_seconds <= 30:
        parser.error('Resource sampling interval must be 2–30 seconds')
    is_test = args.mode in ('unit-tests', 'ui-tests')
    has_test_sources = is_test or compile_only
    if has_test_sources and not args.test and not args.full_suite:
        parser.error('Choose focused tests or explicitly --full-suite')
    if not has_test_sources and (args.test or args.full_suite):
        parser.error('Test selections apply only to test execution or UI test compilation')
    if args.test and args.full_suite:
        parser.error('Choose focused execution or full suite')
    if len(args.test) > 30 or any(not re.fullmatch(r'[A-Za-z0-9_.*$]+', test) for test in args.test):
        parser.error('Invalid test filter')
    if args.focus_test_sources and (not has_test_sources or not args.test or args.full_suite):
        parser.error('Focused source compilation requires selected tests')
    if args.test_helper and not args.focus_test_sources:
        parser.error('Helpers require --focus-test-sources')
    if compile_only and not args.focus_test_sources:
        parser.error('UI compilation requires focused physical sources and an explicit UI test selection')
    if args.isolate_test_classes and (args.mode != 'ui-tests' or not args.test):
        parser.error('Test-class isolation requires selected UI tests')
    overrides = {key: getattr(args, key) for key in ('repo', 'output_dir', 'java_home', 'android_sdk', 'android_user_home', 'gradle_home', 'gradle_user_home', 'build_dir')}
    settings = resolve_settings(args.config, overrides)
    for name in ('repo', 'output_dir', 'java_home', 'android_sdk', 'gradle_home', 'gradle_user_home'):
        if settings[name] is None:
            raise RuntimeError('Configure ' + name + '; no toolchain installation attempted')
    repo, build, java, sdk, cache = (settings[name] for name in ('repo', 'build_dir', 'java_home', 'android_sdk', 'gradle_user_home'))
    output_dir = settings['output_dir']
    if build.is_relative_to(repo) or output_dir.is_relative_to(repo):
        raise RuntimeError('Build and evidence output must remain outside the source checkout')
    gradle = settings['gradle_home'] / 'bin/gradle'
    for path in (repo / 'app/build.gradle.kts', java / 'bin/javac', gradle,
                 sdk / 'platforms/android-36/android.jar', sdk / 'build-tools' / settings['build_tools_version'] / 'aapt2'):
        if not path.is_file():
            raise RuntimeError('Missing existing toolchain/source file: ' + str(path))
    if not shutil.which('node'):
        raise RuntimeError('Existing face-model preparation requires Node')
    before = source_state(repo)
    if args.expected_sha and before['commit'] != args.expected_sha:
        raise RuntimeError('Checkout differs from expected SHA')
    if not before['clean'] and not args.allow_dirty:
        raise RuntimeError('Source-bound QA requires a clean checkout; --allow-dirty is a separate diagnostic only')
    if args.capture_dir:
        args.capture_dir = args.capture_dir.resolve()
        allowed = {CAPTURE_CLASS + '.' + case for case in CAPTURE_CASES}
        if args.mode != 'ui-tests' or args.full_suite or not args.test or any(test not in allowed for test in args.test):
            raise RuntimeError('Capture accepts only the existing explicit deterministic methods')
        if not args.capture_dir.is_relative_to(output_dir / 'captures') or not before['clean']:
            raise RuntimeError('Capture needs a clean source and output below output_dir/captures')
        if args.capture_dir.exists() and any(p.name != 'gradle-capture.log' for p in args.capture_dir.iterdir()):
            raise RuntimeError('Capture output already contains evidence')
    try:
        pins = {'status': 'VERIFIED', 'models': validate_cached_models(repo, cache)}
    except RuntimeError as error:
        if not args.resolve_dependencies:
            raise
        pins = {'status': 'NOT_YET_CACHED', 'detail': str(error)}
    focus = focused_sources(repo, args.test, args.test_helper, is_ui, before['commit']) if args.focus_test_sources else None
    environment = os.environ.copy()
    for name in list(environment):
        if (name.startswith('CONTINUUM_') and 'SIGNING' in name) or name in ('ORIGINAL_P12', 'ORIGINAL_PASSWORD'):
            environment.pop(name, None)
    environment.update(JAVA_HOME=str(java), ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk),
                       ANDROID_USER_HOME=str(settings['android_user_home']),
                       GRADLE_USER_HOME=str(cache), PATH=str(java / 'bin') + os.pathsep + environment['PATH'])
    runs = output_dir / 'runs'
    log, receipt, resources = (runs / (args.label + suffix) for suffix in ('.log', '.json', '-resources.jsonl'))
    selection = runs / (args.label + '-tests.json')
    sources_file = runs / (args.label + '-test-sources.json')
    actual_sources = runs / (args.label + '-compiled-test-sources.json')
    command = [str(gradle)] + ([] if args.resolve_dependencies else ['--offline']) + [
        '--no-daemon', '--no-build-cache', '--max-workers=1', '--console=plain',
        '--project-cache-dir', str(build / 'project-cache'),
        f'-Dorg.gradle.jvmargs=-Xmx{args.heap_mib}m -XX:MaxMetaspaceSize={args.metaspace_mib}m '
        '-XX:ReservedCodeCacheSize=128m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError '
        '-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts',
        '-Dcontinuum.native.buildRoot=' + str(build), '-Pkotlin.compiler.execution.strategy=in-process',
        '-PdeviceAiMentorPreview=true', '-PcontinuumOwnerArm64=true',
        '-PcontinuumStandaloneVersionCode=' + str(args.version_code),
        '-PcontinuumLocalRenderUnsigned=true' if args.mode == 'unsigned' else '-PcontinuumLocalStandaloneQa=true',
        '-I', str(HERE / 'build-location.init.gradle')]
    harness = None
    if is_ui:
        path = repo / MEMORY_HARNESS
        harness = path.read_bytes() if path.is_file() else git(repo, 'show', 'HEAD:' + MEMORY_HARNESS)
        command += ['-PcontinuumUiWidgetTests=true', '-I', str(output_dir / 'original-test-memory.init.gradle')]
    if args.resolve_dependencies and args.mode == 'compile':
        command += ['-PcontinuumUiWidgetTests=true', '-I', str(HERE / 'resolve-dependencies.init.gradle')]
    if args.capture_dir:
        command += ['-I', str(repo / 'tools/capture/force_capture.gradle'), '-PcontinuumUiEvidenceDir=' + str(args.capture_dir)]
        environment['CONTINUUM_CAPTURE_SOURCE_COMMIT'] = before['commit']
    if args.test and is_test:
        command += ['-Dcontinuum.native.testSelection=' + str(selection), '-I', str(HERE / 'test-selection.init.gradle')]
        if args.isolate_test_classes:
            command += ['-Dcontinuum.native.isolateTestClasses=true']
    if focus:
        command += ['-Dcontinuum.native.testSources=' + str(sources_file),
                    '-Dcontinuum.native.compiledTestSources=' + str(actual_sources),
                    '-I', str(HERE / 'focused-test-sources.init.gradle')]
    command += [TASKS[args.mode]]
    build_parent = build
    while not build_parent.exists():
        build_parent = build_parent.parent
    plan = {'mode': args.mode, 'label': args.label, 'checkout': str(repo), 'build_output': str(build),
            'command': command, 'source': before, 'offline': not args.resolve_dependencies,
            'dependency_resolution': 'EXPLICIT_PROJECT_REPOSITORIES' if args.resolve_dependencies else 'CACHE_ONLY',
            'original_signing_used': False,
            'device_tests': 'NOT_RUN', 'cached_model_pins_verified': pins,
            'toolchain': {key: str(value) if isinstance(value, Path) else value for key, value in settings.items()},
            'java_memory_limits_mib': {'heap': args.heap_mib, 'metaspace': args.metaspace_mib},
            'resource_sample_seconds': args.resource_sample_seconds, 'workspace_safety_buffer_bytes': 128 * 1024**2,
            'selected_tests': args.test, 'focused_test_sources': focus,
            'verification_scope': ('FOCUSED_HOST_TEST_COMPILATION_ONLY' if compile_only else
                                   'FOCUSED_HOST_TESTS_ONLY' if focus else 'LOCAL_BUILD_OR_SELECTED_TEST_EXECUTION'),
            'memory_harness_sha256': hashlib.sha256(harness).hexdigest() if harness else None,
            'free_bytes': {'build_volume': shutil.disk_usage(build_parent).free, 'workspace': shutil.disk_usage(HERE).free}}
    if compile_only:
        plan['test_execution'] = 'NOT_RUN_COMPILE_ONLY'
    if args.isolate_test_classes:
        plan['test_worker_policy'] = {'fork_every': 1, 'max_parallel_forks': 1,
                                     'memory_limits': 'UNCHANGED_ORIGINAL_UI_HARNESS'}
    if args.capture_dir:
        plan['capture'] = {'directory': str(args.capture_dir), 'fidelity': 'android_activity_host_native_canvas_api28_explicit_2d_head',
                           'filament_verified': False, 'physical_device_verified': False,
                           'frame_validation': 'existing tools/capture/capture_jarvis.py validates source and PNG hashes'}
    if args.plan:
        print(json.dumps(plan, indent=2))
        return 0
    minimum = (700 if args.mode == 'unsigned' else 450) * 1024**2
    if plan['free_bytes']['build_volume'] < minimum or plan['free_bytes']['workspace'] < 128 * 1024**2:
        raise RuntimeError('Insufficient free build/workspace capacity; no Gradle process started')
    output_dir.mkdir(parents=True, exist_ok=True)
    with (output_dir / 'native-build.lock').open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('Another native QA run holds the serial lock')
        runs.mkdir(exist_ok=True)
        if any(path.exists() for path in (log, receipt, resources, selection, sources_file, actual_sources)):
            raise RuntimeError('Run label already exists; retain old evidence and choose another')
        if harness:
            (output_dir / 'original-test-memory.init.gradle').write_bytes(harness)
        if args.test:
            save_json(selection, args.test)
        if focus:
            save_json(sources_file, focus)
        build.mkdir(parents=True, exist_ok=True)
        if args.capture_dir:
            args.capture_dir.mkdir(parents=True, exist_ok=True)
        started, wall_started = time.monotonic(), time.time()
        print('START ' + args.label + ' / ' + args.mode + ' / ' + before['commit'], flush=True)
        interrupted, stop_signal, peak, lowest = False, 0, 0, None
        def terminate(signum, _frame):
            nonlocal stop_signal
            stop_signal = signum
            raise KeyboardInterrupt
        previous = signal.signal(signal.SIGTERM, terminate)
        with log.open('x') as output, resources.open('x') as resource_output:
            def record():
                nonlocal peak, lowest
                sample = resource_snapshot()
                sample['elapsed_seconds'] = round(time.monotonic() - started, 3)
                peak = max(peak, sample['java_rss_kib'])
                available = sample['memory_kib'].get('MemAvailable')
                if available is not None:
                    lowest = available if lowest is None else min(lowest, available)
                resource_output.write(json.dumps(sample) + '\n')
                resource_output.flush()
            record()
            process = subprocess.Popen(command, cwd=repo, env=environment, stdout=output,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            last_heartbeat = started
            try:
                while True:
                    try:
                        process.wait(timeout=args.resource_sample_seconds)
                        break
                    except subprocess.TimeoutExpired:
                        record()
                        if time.monotonic() - last_heartbeat >= 20:
                            print('RUNNING %.0fs %s' % (time.monotonic() - started, log), flush=True)
                            last_heartbeat = time.monotonic()
            except KeyboardInterrupt:
                interrupted = True
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                try:
                    process.wait(timeout=3 if args.capture_dir else 10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
            finally:
                signal.signal(signal.SIGTERM, previous)
                record()
        after = source_state(repo)
        text = log.read_text(errors='replace')
        report = {**plan, 'elapsed_seconds': round(time.monotonic() - started, 3), 'gradle_exit': process.returncode,
            'interrupted': interrupted, 'source_after': after, 'source_unchanged': before == after, 'log': str(log),
            'free_bytes_after': {'build_volume': shutil.disk_usage(build).free, 'workspace': shutil.disk_usage(HERE).free},
            'resource_monitor': {'path': str(resources), 'interval_seconds': args.resource_sample_seconds,
                'sampled_java_peak_rss_kib': peak, 'lowest_sampled_mem_available_kib': lowest,
                'scope': 'visible process namespace; sampling alone does not establish a kill cause'},
            'gradle_daemon_disappeared': 'Gradle build daemon disappeared unexpectedly' in text,
            'kotlin_compiler_errors': [line for line in text.splitlines() if line.startswith('e: ')][:100]}
        if is_test:
            report['junit'] = junit_summary(build, wall_started, args.test)
        if focus:
            report['compiled_test_source_receipt'] = str(actual_sources) if actual_sources.is_file() else None
            report['focused_source_hashes_unchanged'] = all(digest(repo / entry['path']) == entry['sha256'] for entry in focus['files'])
        validation_error = None
        try:
            report['cached_model_pins_after'] = {'status': 'VERIFIED', 'models': validate_cached_models(repo, cache)}
        except RuntimeError as error:
            validation_error = str(error)
            report['cached_model_pins_after'] = {'status': 'FAILED', 'detail': validation_error}
        if args.mode == 'unsigned' and process.returncode == 0 and before == after:
            try:
                report['apk'] = validate_unsigned_apk(build / 'app-build/outputs/apk/release/app-release-unsigned.apk',
                                                      args.version_code, settings, environment)
            except (RuntimeError, OSError, subprocess.CalledProcessError, KeyError, zipfile.BadZipFile) as error:
                validation_error = str(error)
                report['apk_validation_error'] = validation_error
        test_evidence_ok = not is_test or (report['junit']['fresh_files'] > 0 and
            report['junit']['tests'] > report['junit']['skipped'] and
            not report['junit']['failures'] and not report['junit']['errors'] and
            not report['junit']['selected_filters_without_fresh_result'])
        good = process.returncode == 0 and before == after and not validation_error and test_evidence_ok and report.get('focused_source_hashes_unchanged', True)
        report['status'] = 'PASS_LOCAL' if good else 'FAILED_OR_SOURCE_CHANGED'
        if good and compile_only:
            report['status'] = ('PASS_LOCAL_TEST_COMPILATION_ONLY' if before['clean'] else
                                'PASS_LOCAL_DIRTY_TEST_COMPILATION_ONLY')
        elif good and not before['clean']:
            report['status'] = 'PASS_LOCAL_DIRTY_DIAGNOSTIC_ONLY'
        if process.returncode == 0 and is_test and not test_evidence_ok:
            report['status'] = 'NO_COMPLETE_FRESH_TEST_EVIDENCE'
        save_json(receipt, report)
        summary = {key: report[key] for key in ('label', 'status', 'elapsed_seconds', 'gradle_exit', 'source_unchanged',
                                               'log', 'resource_monitor', 'gradle_daemon_disappeared', 'verification_scope')}
        summary.update(receipt=str(receipt), junit=report.get('junit'), apk=report.get('apk'),
                       compiler_error_count=len(report['kotlin_compiler_errors']), compiler_errors=report['kotlin_compiler_errors'][:8])
        print(json.dumps(summary, indent=2))
        relevant = [line for line in text.splitlines() if re.search(r'^(FAILURE:|BUILD (FAILED|SUCCESSFUL)|Execution failed|Gradle build daemon|> Task .*FAILED|.* > .* FAILED$)', line)]
        if relevant:
            print('\n'.join(relevant[-20:]))
        return 128 + (stop_signal or signal.SIGINT) if interrupted else process.returncode or (0 if good else 65)


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (RuntimeError, OSError, subprocess.CalledProcessError, ValueError) as error:
        print('NATIVE_QA_BLOCKED: ' + str(error), file=sys.stderr)
        sys.exit(69)
