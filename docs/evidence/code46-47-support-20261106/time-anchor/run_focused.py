from pathlib import Path
import hashlib
import json
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

root = Path('/workspace/scratch/b31a9d731016/time-anchor-test-contract')
evidence = Path(__file__).resolve().parent
cache = Path('/workspace/scratch/b31a9d731016/build-check/gradle-cache/caches/modules-2/files-2.1')
worker = Path('/workspace/scratch/b31a9d731016/build-check/gradle-cache/.tmp/gradle-worker-classpath2594457099072408548txt')
phase = sys.argv[1]
assert phase in ('red', 'green')
out = evidence / (phase + '-classes')
out.mkdir()
runtime = worker.read_text().splitlines()[1]
runtime = ':'.join(p for p in runtime.split(':') if '/kotlin-classes/debugUnitTest' not in p)
app_jar = next(p for p in runtime.split(':') if '/bundleDebugClassesToRuntimeJar/classes.jar' in p)
compiler_root = cache / 'org.jetbrains.kotlin/kotlin-compiler-embeddable/2.4.0'
pom = next(compiler_root.glob('*/*.pom'))
namespaces = {'m': 'http://maven.apache.org/POM/4.0.0'}
compiler_jars = [next(compiler_root.glob('*/*.jar'))]
for dependency in ET.parse(pom).findall('m:dependencies/m:dependency', namespaces):
    group = dependency.findtext('m:groupId', namespaces=namespaces)
    artifact = dependency.findtext('m:artifactId', namespaces=namespaces)
    version = dependency.findtext('m:version', namespaces=namespaces)
    compiler_jars.append(next((cache / group / artifact / version).glob('*/*.jar')))
compiler_jars.append(next((cache / 'org.jetbrains/annotations/13.0').glob('*/*.jar')))
source_paths = [
    'app/src/main/java/at/heinz/deviceai/cognitive/LocalDecisionPromptFrame.kt',
    'app/src/main/java/at/heinz/deviceai/cognitive/LocalPromptFrame.kt',
    'app/src/main/java/at/heinz/deviceai/cognitive/LocalClockIntent.kt',
    'app/src/test/java/at/heinz/deviceai/cognitive/LocalPromptSizeAndStabilityTest.kt',
    'app/src/test/java/at/heinz/deviceai/cognitive/LocalDecisionPromptFrameTest.kt',
]
source_hashes = {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in source_paths}
activity = 'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'
source_hashes[activity] = hashlib.sha256((root / activity).read_bytes()).hexdigest()
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
assert head == '9ee7535bb131504cc85419afd30f446089a40171'
command = ['java', '-Xmx512m', '-cp', ':'.join(map(str, compiler_jars)),
           'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
           '-jvm-target', '17', '-Xfriend-paths=' + app_jar,
           '-classpath', runtime, '-d', str(out)] + source_paths
started = time.time()
compiled = subprocess.run(command, cwd=root, text=True, capture_output=True, timeout=90)
(evidence / (phase + '-compile.txt')).write_text(compiled.stdout + compiled.stderr)
result = {'source': head, 'phase': phase, 'source_sha256': source_hashes,
          'compile_command': command, 'compile_exit': compiled.returncode}
print('COMPILE_EXIT', compiled.returncode)
print(compiled.stdout + compiled.stderr)
if compiled.returncode:
    (evidence / (phase + '-result.json')).write_text(json.dumps(result, indent=2) + '\n')
    raise SystemExit(compiled.returncode)
test_command = ['java', '-Xmx256m', '-cp', str(out) + ':' + runtime,
                'org.junit.runner.JUnitCore',
                'at.heinz.deviceai.cognitive.LocalPromptSizeAndStabilityTest',
                'at.heinz.deviceai.cognitive.LocalDecisionPromptFrameTest']
tested = subprocess.run(test_command, cwd=root, text=True, capture_output=True, timeout=60)
(evidence / (phase + '-junit.txt')).write_text(tested.stdout + tested.stderr)
result.update({'test_command': test_command, 'test_exit': tested.returncode,
               'elapsed_seconds': round(time.time() - started, 3)})
(evidence / (phase + '-result.json')).write_text(json.dumps(result, indent=2) + '\n')
print('JUNIT_EXIT', tested.returncode)
print(tested.stdout + tested.stderr)
raise SystemExit(tested.returncode)
