import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

base = Path('/workspace/scratch/b31a9d731016')
root = Path('/workspace/scratch/7df35dbe256f/device-ai-code47')
phase = sys.argv[1]
out = Path('/workspace/scratch/7df35dbe256f/qa/pdf-review') / phase
out.mkdir(parents=True, exist_ok=False)
classes = out / 'classes'
classes.mkdir()
env_root = base / 'build-env'
dependencies = [base / 'device-ai-wiring/app/build/tmp/kotlin-classes/debug',
                env_root / 'test-libs/junit-4.13.2.jar',
                env_root / 'test-libs/hamcrest-core-1.3.jar',
                env_root / 'test-libs/json-20240303.jar']
sources = [root / 'app/src/main/java/at/heinz/deviceai/cognitive/OwnerDocumentIntent.kt',
           root / 'app/src/main/java/at/heinz/deviceai/cognitive/OwnerAppLookupIntent.kt',
           root / 'app/src/test/java/at/heinz/deviceai/cognitive/OwnerDocumentIntentTest.kt',
           root / 'app/src/test/java/at/heinz/deviceai/cognitive/OwnerDocumentPoliteIntentTest.kt']
tests = ['at.heinz.deviceai.cognitive.OwnerDocumentIntentTest',
         'at.heinz.deviceai.cognitive.OwnerDocumentPoliteIntentTest']
extra = root / 'app/src/test/java/at/heinz/deviceai/cognitive/OwnerDocumentAddressTest.kt'
if extra.exists():
    sources.append(extra)
    tests.append('at.heinz.deviceai.cognitive.OwnerDocumentAddressTest')
compile_command = [str(env_root / 'kotlinc/bin/kotlinc'), '-J-Xmx256m',
                   '-jvm-target', '17', '-module-name', 'DeviceAI_app_debug',
                   '-Xfriend-paths=' + str(dependencies[0]),
                   '-classpath', os.pathsep.join(map(str, dependencies)),
                   '-d', str(classes)] + list(map(str, sources))
runtime_cp = [classes] + dependencies + [env_root / 'kotlinc/lib/kotlin-stdlib.jar']
test_command = [str(env_root / 'jdk17-pinned/bin/java'), '-Xmx256m', '-cp',
                os.pathsep.join(map(str, runtime_cp)), 'org.junit.runner.JUnitCore'] + tests
manifest = [{'path': str(p.relative_to(root)),
             'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in sources]
results = []
for name, command in [('compile', compile_command), ('junit', test_command)]:
    with (out / (name + '.log')).open('x') as log:
        result = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT)
    results.append({'step': name, 'command': command, 'exitCode': result.returncode})
    if result.returncode:
        break
receipt = {'phase': phase, 'dependencySource': 'c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e',
           'rootCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
           'sources': manifest, 'results': results, 'physicalFold7': 'NOT_RUN',
           'androidUiExecution': 'NOT_RUN'}
(out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps({'phase': phase, 'results': [{'step': r['step'], 'exitCode': r['exitCode']} for r in results]}))
print((out / (results[-1]['step'] + '.log')).read_text()[-3000:])
