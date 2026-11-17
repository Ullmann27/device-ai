from pathlib import Path
import argparse, hashlib, json, subprocess, sys

p = argparse.ArgumentParser()
p.add_argument('name')
p.add_argument('--production-ref', required=True)
p.add_argument('--test-ref', required=True)
p.add_argument('--only-new', action='store_true')
a = p.parse_args()
root = Path('/workspace/scratch/2d62a799e417/omega-memory')
out = Path(__file__).parent / a.name
out.mkdir(exist_ok=False)
cache = Path('/workspace/scratch/c9f74e2967e3/gradle-home/caches/modules-2/files-2.1')
java = '/workspace/scratch/6139925ecf70/toolchain/jdk-21.0.13+9/bin/java'
baseline = '/workspace/scratch/6139925ecf70/device-ai/app/build/tmp/kotlin-classes/debug'

def jar(group, name, version):
    return str(next((cache / group / name / version).glob('*/*.jar')))

prior = json.loads(Path('/workspace/scratch/2d62a799e417/memory-qa/budget-green/manifest.json').read_text())
sources = [path for path in prior['sources'] if '/main/' in path]
tests = [] if a.only_new else [path for path in prior['sources'] if '/test/' in path]
tests.append('app/src/test/java/at/heinz/deviceai/cognitive/OwnerObjectStateLedgerHistoryPromptTest.kt')
paths, hashes = [], {}
for relative in sources + tests:
    ref = a.production_ref if relative in sources else a.test_ref
    data = subprocess.check_output(['git', 'show', ref + ':' + relative], cwd=root)
    target = out / 'sources' / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    paths.append(str(target))
    hashes[relative] = hashlib.sha256(data).hexdigest()

compiler = [jar('org.jetbrains.kotlin', n, '2.4.0') for n in
            ['kotlin-compiler-embeddable', 'kotlin-build-tools-api', 'kotlin-stdlib',
             'kotlin-script-runtime', 'kotlin-daemon-embeddable']]
compiler += [jar('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'),
             jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
             jar('org.jetbrains', 'annotations', '13.0')]
cp = [baseline, jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.0'),
      jar('junit', 'junit', '4.13.2'), jar('org.hamcrest', 'hamcrest-core', '1.3')]
compile_command = [java, '-Xmx512m', '-XX:ActiveProcessorCount=1', '-cp', ':'.join(compiler),
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
    '-jvm-target', '17', '-Xfriend-paths=' + baseline, '-classpath', ':'.join(cp),
    '-d', str(out / 'classes'), *paths]
classes = ['at.heinz.deviceai.cognitive.' + Path(path).stem for path in tests]
run_command = [java, '-Xmx256m', '-XX:ActiveProcessorCount=1', '-cp',
    ':'.join([str(out / 'classes')] + cp), 'org.junit.runner.JUnitCore', *classes]
manifest = {'production_ref': a.production_ref, 'test_ref': a.test_ref,
            'sources': hashes, 'compile_command': compile_command, 'run_command': run_command,
            'scope': 'Fresh selected Kotlin sources and JUnit tests; cached real app dependencies; no Gradle'}
(out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
with (out / 'compile.log').open('w') as f:
    code = subprocess.call(compile_command, cwd=root, stdout=f, stderr=subprocess.STDOUT)
print((out / 'compile.log').read_text(), flush=True)
if code:
    (out / 'exit.txt').write_text(str(code))
    sys.exit(code)
with (out / 'test.log').open('w') as f:
    code = subprocess.call(run_command, cwd=root, stdout=f, stderr=subprocess.STDOUT)
print((out / 'test.log').read_text(), flush=True)
(out / 'exit.txt').write_text(str(code))
sys.exit(code)
