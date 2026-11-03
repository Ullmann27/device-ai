#!/usr/bin/env python3
"""Compile the exact pool source/tests against previously verified real app classes.

No API stubs. Caller supplies SOURCE_REF and output directory; tests remain this
checkout's frozen tests for an unchanged baseline/candidate comparison.
"""
import hashlib
import json
import pathlib
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[2]
source_ref, output_arg = sys.argv[1:]
out = pathlib.Path(output_arg).resolve()
out.mkdir(parents=True, exist_ok=True)
compiled = pathlib.Path('/workspace/continuum-breakthrough-quality/app/build/tmp/kotlin-classes')
cp = ':'.join([str(compiled / 'debugUnitTest'), str(compiled / 'debug'),
               pathlib.Path('/workspace/continuum-build-env/baseline64-ui-classpath.txt').read_text().strip()])
production = 'app/src/main/java/at/heinz/deviceai/chief/ChiefWorkerPool.kt'
tests = ['app/src/test/java/at/heinz/deviceai/chief/ChiefWorkerPoolTest.kt',
         'app/src/test/java/at/heinz/deviceai/chief/ChiefWorkerPoolShutdownTest.kt']
sha = subprocess.check_output(['git', 'rev-parse', source_ref], cwd=root, text=True).strip()
inputs = []
paths = []
for path in [production] + tests:
    data = (subprocess.check_output(['git', 'show', sha + ':' + path], cwd=root)
            if path == production else (root / path).read_bytes())
    dest = out / pathlib.Path(path).name
    dest.write_bytes(data)
    paths.append(str(dest))
    inputs.append({'path': path, 'sha256': hashlib.sha256(data).hexdigest(),
                   'source': sha if path == production else 'frozen checkout tests'})
commands = [
    ['/workspace/continuum-build-env/kotlin-official/kotlinc/bin/kotlinc', *paths,
     '-jvm-target', '17', '-cp', cp, '-d', str(out / 'classes')],
    ['/usr/bin/java', '-Xmx384m', '-cp', str(out / 'classes') + ':' + cp,
     'org.junit.runner.JUnitCore', 'at.heinz.deviceai.chief.ChiefWorkerPoolTest',
     'at.heinz.deviceai.chief.ChiefWorkerPoolShutdownTest'],
]
results = []
for name, command in zip(['compile', 'junit'], commands):
    with (out / (name + '.txt')).open('w') as stream:
        result = subprocess.run(command, cwd=root, stdout=stream, stderr=subprocess.STDOUT, timeout=120)
    (out / (name + '.exit')).write_text(str(result.returncode) + '\n')
    results.append({'name': name, 'command': command, 'exit': result.returncode})
    print(name, result.returncode)
    if result.returncode:
        print((out / (name + '.txt')).read_text())
        break
(out / 'binding.json').write_text(json.dumps({'productionCommit': sha, 'inputs': inputs,
    'commands': results, 'dependencyBasis': 'previously verified Chief7247e99d real compiled app classes'}, indent=2) + '\n')
sys.exit(results[-1]['exit'])
