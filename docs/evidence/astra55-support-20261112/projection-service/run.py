import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--repo', type=Path, required=True)
parser.add_argument('--cache', type=Path, required=True)
parser.add_argument('--app-build', type=Path, required=True)
parser.add_argument('--android-jar', type=Path, required=True)
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--service-ref')
args = parser.parse_args()
repo, cache, build, out = args.repo.resolve(), args.cache.resolve(), args.app_build.resolve(), args.out.resolve()
out.mkdir(parents=True, exist_ok=True)
frozen = out / 'frozen'
frozen.mkdir(exist_ok=True)

def artifact(group, name, version, ext='jar'):
    matches = list((cache / group / name / version).glob('*/*.' + ext))
    exact = [p for p in matches if p.name == f'{name}-{version}.{ext}']
    assert len(exact) == 1, (group, name, version, exact)
    return exact[0]

def sha(data):
    return hashlib.sha256(data).hexdigest()

def git(*command):
    return subprocess.check_output(['git', *command], cwd=repo, text=True).strip()

compiler = [artifact('org.jetbrains.kotlin', x, '2.4.0') for x in [
    'kotlin-compiler-embeddable', 'kotlin-build-tools-api', 'kotlin-stdlib',
    'kotlin-script-runtime', 'kotlin-daemon-embeddable']]
compiler += [artifact('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'),
             artifact('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
             artifact('org.jetbrains', 'annotations', '23.0.0')]
runtime = list((cache / 'org.robolectric').glob('*/4.14.1/*/*.jar'))
runtime += [artifact('org.robolectric', 'nativeruntime-dist-compat', '1.0.16')]
runtime += [artifact('org.jetbrains.kotlin', x, '2.4.0') for x in ['kotlin-stdlib', 'kotlin-reflect']]
coordinates = [
    ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.11.0'),
    ('org.jetbrains', 'annotations', '23.0.0'),
    ('androidx.annotation', 'annotation-jvm', '1.9.1'),
    ('junit', 'junit', '4.13.2'), ('org.hamcrest', 'hamcrest-core', '1.3'),
    ('javax.inject', 'javax.inject', '1'), ('javax.annotation', 'javax.annotation-api', '1.3.2'),
    ('com.google.guava', 'guava', '33.3.1-jre'), ('com.google.guava', 'failureaccess', '1.0.2'),
    ('com.google.guava', 'listenablefuture', '9999.0-empty-to-avoid-conflict-with-guava'),
    ('com.google.errorprone', 'error_prone_annotations', '2.30.0'),
    ('com.google.j2objc', 'j2objc-annotations', '3.0.0'),
    ('org.checkerframework', 'checker-qual', '3.43.0'),
    ('com.google.testparameterinjector', 'test-parameter-injector', '1.18'),
    ('org.bouncycastle', 'bcprov-jdk18on', '1.78.1'),
    ('org.conscrypt', 'conscrypt-openjdk-uber', '2.5.2'), ('org.yaml', 'snakeyaml', '2.3'),
    ('com.almworks.sqlite4java', 'sqlite4java', '1.0.392')]
coordinates += [('org.ow2.asm', name, '9.7.1') for name in ['asm', 'asm-commons', 'asm-tree', 'asm-analysis', 'asm-util']]
runtime += [artifact(*coordinate) for coordinate in coordinates]
for group, name, version in [('androidx.test', 'monitor', '1.7.2'),
                             ('androidx.test.espresso', 'espresso-idling-resource', '3.6.1')]:
    aar = artifact(group, name, version, 'aar')
    jar = frozen / (name + '.jar')
    jar.write_bytes(zipfile.ZipFile(aar).read('classes.jar'))
    runtime.append(jar)
android = artifact('org.robolectric', 'android-all-instrumented', '15-robolectric-12650502-i7')
sdk = android.parent
original_app_classes = build / 'tmp/kotlin-classes/debug'
environment_sources = [original_app_classes, build / 'intermediates/javac/debug/compileDebugJavaWithJavac/classes',
               build / 'intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar']
assert all(p.exists() for p in environment_sources), environment_sources
environment = []
environment_manifest = []
for index, original in enumerate(environment_sources):
    target = frozen / ('environment-' + str(index))
    if original.is_dir():
        before = {str(p.relative_to(original)): sha(p.read_bytes()) for p in sorted(original.rglob('*')) if p.is_file()}
        shutil.copytree(original, target)
        copied = {str(p.relative_to(target)): sha(p.read_bytes()) for p in sorted(target.rglob('*')) if p.is_file()}
        after = {str(p.relative_to(original)): sha(p.read_bytes()) for p in sorted(original.rglob('*')) if p.is_file()}
        assert before == copied == after, 'Environment changed while freezing'
        environment_manifest.append({'index': index, 'files': len(before),
            'directory_manifest_sha256': sha(json.dumps(before, sort_keys=True).encode())})
    else:
        target = target.with_suffix('.jar')
        shutil.copy2(original, target)
        assert sha(original.read_bytes()) == sha(target.read_bytes())
        environment_manifest.append({'index': index, 'sha256': sha(target.read_bytes())})
    environment.append(target)
app_classes = environment[0]
files = ['app/src/main/java/at/heinz/deviceai/' + name for name in [
    'ContinuumLiveProjectionService.kt', 'AndroidLiveComputer.kt', 'ContinuumScreenObservationService.kt',
    'cognitive/LiveComputerActions.kt', 'AndroidLiveNodePort.kt']]
files += ['app/src/uiTest/java/at/heinz/deviceai/ContinuumLiveProjectionServiceUiTest.kt']
manifest = []
sources = []
for path in files:
    data = (repo / path).read_bytes()
    original = data
    if args.service_ref and path.endswith('/ContinuumLiveProjectionService.kt'):
        data = subprocess.check_output(['git', 'show', args.service_ref + ':' + path], cwd=repo)
    target = frozen / Path(path).name
    target.write_bytes(data)
    sources.append(str(target))
    manifest.append({'path': path, 'worktree_before_sha256': sha(original), 'frozen_sha256': sha(data)})
metadata = {'head_before': git('rev-parse', 'HEAD'), 'service_ref': args.service_ref or 'WORKTREE',
            'service_commit': git('rev-parse', args.service_ref) if args.service_ref else git('rev-parse', 'HEAD'),
            'compiler': 'Kotlin 2.4.0', 'robolectric': '4.14.1', 'sdk': 35, 'max_heap_mb': 512,
            'gradle': 'NOT_RUN', 'fold7': 'NOT_RUN', 'environment': 'Existing app classes; friend module; not a full source rebuild',
            'environment_manifest': environment_manifest}
metadata['java'] = subprocess.run(['java', '-version'], text=True, capture_output=True).stderr.strip()
java_tmp = frozen / 'java-tmp'
java_tmp.mkdir(exist_ok=True)
java = ['java', '-XX:ActiveProcessorCount=1', '-XX:-UsePerfData', '-Xmx512m', '-Djava.io.tmpdir=' + str(java_tmp)]
classpath = ':'.join(str(p) for p in [args.android_jar, *environment, *runtime])
classes = frozen / 'classes'
command = [*java, '-cp', ':'.join(str(p) for p in compiler), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
           '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-Xfriend-paths=' + str(app_classes),
           '-classpath', classpath, '-d', str(classes), *sources]
result = subprocess.run(command, text=True, capture_output=True)
(out / 'compile.txt').write_text(result.stdout + result.stderr)
metadata['compile_exit'] = result.returncode
print('COMPILE_EXIT', result.returncode, flush=True)
print(result.stdout + result.stderr, flush=True)
if not result.returncode:
    command = [*java, '-Drobolectric.offline=true', '-Drobolectric.dependency.dir=' + str(sdk),
               '-cp', ':'.join(str(p) for p in [classes, *environment, *runtime, args.android_jar]),
               'org.junit.runner.JUnitCore', 'at.heinz.deviceai.ContinuumLiveProjectionServiceUiTest']
    result = subprocess.run(command, text=True, capture_output=True)
    (out / 'junit.txt').write_text(result.stdout + result.stderr)
    metadata['junit_exit'] = result.returncode
    print('JUNIT_EXIT', result.returncode, flush=True)
    print(result.stdout + result.stderr, flush=True)
metadata['head_after'] = git('rev-parse', 'HEAD')
for item in manifest:
    item['worktree_after_sha256'] = sha((repo / item['path']).read_bytes())
metadata['sources'] = manifest
metadata['source_unchanged'] = all(item['worktree_before_sha256'] == item['worktree_after_sha256'] for item in manifest)
(out / 'result.json').write_text(json.dumps(metadata, indent=2) + '\n')
(out / 'runtime-artifacts.json').write_text(json.dumps([
    {'name': p.name, 'sha256': sha(p.read_bytes())} for p in [*compiler, *runtime, android, args.android_jar, environment[-1]]
], indent=2) + '\n')
assert metadata['source_unchanged'], 'Sources changed during the run'
raise SystemExit(metadata.get('junit_exit', metadata['compile_exit']))
