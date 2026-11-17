import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

parser = argparse.ArgumentParser()
parser.add_argument('--repo', type=Path, required=True)
parser.add_argument('--toolchain', type=Path, required=True)
parser.add_argument('--cache', type=Path, required=True)
parser.add_argument('--case', choices=['baseline', 'focused'], required=True)
parser.add_argument('--out', type=Path, required=True)
args = parser.parse_args()
repo = args.repo.resolve()
out = args.out.resolve()
out.mkdir(parents=True, exist_ok=True)
main = repo / 'app/src/main/java/at/heinz/deviceai'
test = repo / 'app/src/test/java/at/heinz/deviceai'
production = [main / p for p in [
    'HologramAudio.kt', 'TtsHologramSignal.kt', 'avatar/AvatarContract.kt',
    'avatar/AvatarDirector.kt', 'avatar3d/AvatarGlbContract.kt',
    'avatar3d/AvatarIdleMotion.kt', 'avatar3d/AvatarMorphMapper.kt']]
if args.case == 'baseline':
    tests = [Path(__file__).resolve().with_name('MeasuredSilenceBaselineTest.kt')]
else:
    tests = [test / p for p in [
        'HologramAudioTest.kt', 'TtsHologramSignalTest.kt',
        'avatar/AvatarDirectorTest.kt', 'avatar/AvatarObservedGazeTest.kt',
        'avatar/AvatarSpeechAdmissionTest.kt', 'avatar/AvatarSpeechBoundaryTest.kt',
        'avatar/AvatarSpeechClockTest.kt', 'avatar/AvatarSpeechOrderingRegressionTest.kt',
        'avatar/AvatarSpeechReattachmentTest.kt',
        'avatar3d/AvatarMouthCouplingTest.kt', 'avatar3d/AvatarArticulationBoundaryTest.kt',
        'avatar3d/AvatarMorphTransitionTest.kt', 'avatar3d/AvatarMeasuredSilenceTest.kt',
        'avatar3d/AvatarSpeechPipelineTest.kt']]
jars = []
for pattern in ['junit/junit/4.13.2/*/junit-4.13.2.jar',
                'org.hamcrest/hamcrest-core/1.3/*/hamcrest-core-1.3.jar',
                'org.json/json/20240303/*/json-20240303.jar']:
    matches = list(args.cache.glob(pattern))
    if len(matches) != 1:
        raise SystemExit('Expected one pinned dependency: ' + pattern)
    jars.append(matches[0].resolve())

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def git(*words):
    return subprocess.check_output(['git', '-C', str(repo), *words], text=True).strip()

def inputs():
    return {str(p.relative_to(repo)): digest(p) for p in production + tests}

env = os.environ.copy()
for key in list(env):
    if key.startswith('CONTINUUM_') and ('SIGNING' in key or 'P12' in key):
        env.pop(key)
java_home = args.toolchain / 'jdk17'
compiler = args.toolchain / 'kotlin-2.4.0/kotlinc/bin/kotlinc'
env['JAVA_HOME'] = str(java_home)
env['JAVA_OPTS'] = '-Xmx512m -XX:ActiveProcessorCount=1 -XX:+UseSerialGC'
classes = []
for path in tests:
    source = path.read_text()
    package = re.search(r'^package\s+([\w.]+)', source, re.M).group(1)
    name = re.search(r'^class\s+(\w+)', source, re.M).group(1)
    classes.append(package + '.' + name)
jar = out / 'tests.jar'
classpath = os.pathsep.join(map(str, jars))
compile_cmd = [str(compiler), '-jvm-target', '17', '-cp', classpath,
               '-include-runtime', '-d', str(jar), *map(str, production + tests)]
test_cmd = [str(java_home / 'bin/java'), '-Xmx384m', '-XX:ActiveProcessorCount=1',
            '-XX:+UseSerialGC', '-cp', str(jar) + os.pathsep + classpath,
            'org.junit.runner.JUnitCore', *classes]
receipt = {
    'startedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'case': args.case, 'localHead': git('rev-parse', 'HEAD'),
    'localHeadTree': git('rev-parse', 'HEAD^{tree}'),
    'sourceBefore': inputs(), 'productionFiles': [str(p.relative_to(repo)) for p in production],
    'dependencies': {str(p): digest(p) for p in jars},
    'compilerCommand': compile_cmd, 'testCommand': test_cmd,
    'androidActivityCompiled': False, 'robolectricRun': False, 'fold7Run': False,
    'sharedGradleCache': 'read-only dependency jars; no Gradle process or cache mutation',
    'classNames': classes,
}
started = time.monotonic()
with (out / 'compile.log').open('w') as log:
    compile_result = subprocess.run(compile_cmd, cwd=repo, env=env, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=180)
receipt['compileExitCode'] = compile_result.returncode
code = compile_result.returncode
if code == 0:
    with (out / 'junit.log').open('w') as log:
        result = subprocess.run(test_cmd, cwd=repo, env=env, stdout=log,
                                stderr=subprocess.STDOUT, timeout=90)
    code = result.returncode
    receipt['testExitCode'] = code
    output = (out / 'junit.log').read_text()
    passed = re.search(r'OK \((\d+) tests?\)', output)
    failed = re.search(r'Tests run: (\d+),\s+Failures: (\d+)', output)
    if passed:
        receipt['tests'] = int(passed.group(1)); receipt['failures'] = 0
    elif failed:
        receipt['tests'] = int(failed.group(1)); receipt['failures'] = int(failed.group(2))
    print(output, flush=True)
else:
    print((out / 'compile.log').read_text(), flush=True)
receipt['elapsedSeconds'] = round(time.monotonic() - started, 3)
receipt['sourceAfter'] = inputs()
receipt['sourceUnchanged'] = receipt['sourceBefore'] == receipt['sourceAfter']
receipt['rawLogs'] = {p.name: digest(p) for p in out.glob('*.log')}
(out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps({k:receipt.get(k) for k in [
    'case', 'compileExitCode', 'testExitCode', 'tests', 'failures',
    'elapsedSeconds', 'sourceUnchanged']}), flush=True)
raise SystemExit(code if receipt['sourceUnchanged'] else 125)
