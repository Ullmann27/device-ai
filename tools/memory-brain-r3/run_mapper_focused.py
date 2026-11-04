import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

p = argparse.ArgumentParser()
p.add_argument('--repo', required=True)
p.add_argument('--source', required=True)
p.add_argument('--test-source', required=True)
p.add_argument('--baseline-manifest', required=True)
p.add_argument('--out', required=True)
a = p.parse_args()
repo, out = Path(a.repo).resolve(), Path(a.out).resolve()
out.mkdir(parents=True, exist_ok=False)
baseline = json.loads(Path(a.baseline_manifest).read_text())
def git(*args):
    return subprocess.check_output(['git', '-C', str(repo), *args])
def sha(data):
    return hashlib.sha256(data).hexdigest()
source = git('rev-parse', a.source + '^{commit}').decode().strip()
test_source = git('rev-parse', a.test_source + '^{commit}').decode().strip()
paths = ['app/src/main/java/at/heinz/deviceai/cockpit/CockpitAnimationEvent.kt',
         'app/src/main/java/at/heinz/deviceai/cockpit/CockpitContracts.kt',
         'app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt']
copies, hashes = [], {}
for path in paths:
    data = git('show', source + ':' + path)
    dest = out / Path(path).name
    dest.write_bytes(data)
    copies.append(str(dest))
    hashes[path] = sha(data)
mapper_path = 'app/src/main/java/at/heinz/deviceai/cockpit/MemoryEventSource.kt'
original = git('show', source + ':' + mapper_path).decode()
factory_start = original.index('\nobject MemoryEventSources {')
mapper_start = original.index('\n/**\n * Maps sink calls')
extracted = original[:factory_start] + original[mapper_start:]
dest = out / 'MemoryEventSource.kt'
dest.write_text(extracted)
copies.append(str(dest))
test_path = 'app/src/test/java/at/heinz/deviceai/cockpit/MemoryEventMapperLifecycleRegressionTest.kt'
test = git('show', test_source + ':' + test_path)
dest = out / Path(test_path).name
dest.write_bytes(test)
copies.append(str(dest))
prior = baseline['compileCommand']
java, compiler_cp = prior[0], prior[prior.index('-cp') + 1]
dependency_cp = prior[prior.index('-classpath') + 1]
for filename, expected in baseline['dependencies'].items():
    assert sha(Path(filename).read_bytes()) == expected, 'Dependency changed: ' + filename
classes = out / 'classes'
compile_command = [java, '-Xmx512m', '-cp', compiler_cp,
                   'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                   '-jvm-target', '17', '-classpath', dependency_cp, '-d', str(classes), *copies]
junit_command = [java, '-Xmx256m', '-cp', str(classes) + ':' + dependency_cp,
                 'org.junit.runner.JUnitCore',
                 'at.heinz.deviceai.cockpit.MemoryEventMapperLifecycleRegressionTest']
manifest = {'source': source, 'tree': git('rev-parse', source + '^{tree}').decode().strip(),
            'testSource': test_source, 'testPath': test_path, 'testSha256': sha(test),
            'sources': hashes, 'dependencies': baseline['dependencies'],
            'mapperExtraction': {'sourcePath': mapper_path, 'originalSha256': sha(original.encode()),
                                 'compiledSha256': sha(extracted.encode()),
                                 'onlyOmission': 'Unused MemoryEventSources factory; complete interfaces and mapper are unchanged.'},
            'compileCommand': compile_command, 'junitCommand': junit_command,
            'runnerSha256': sha(Path(__file__).read_bytes()),
            'androidFrameworkExecuted': False, 'activityExecuted': False}
(out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
with (out / 'compile.log').open('w') as log:
    compiled = subprocess.run(compile_command, stdout=log, stderr=subprocess.STDOUT, timeout=120)
print('compile exit', compiled.returncode, flush=True)
if compiled.returncode:
    print((out / 'compile.log').read_text(), flush=True)
    sys.exit(compiled.returncode)
with (out / 'junit.log').open('w') as log:
    result = subprocess.run(junit_command, stdout=log, stderr=subprocess.STDOUT, timeout=60)
(out / 'result.json').write_text(json.dumps({'source': source, 'testSource': test_source,
                                            'compileExit': compiled.returncode,
                                            'junitExit': result.returncode}, indent=2) + '\n')
print((out / 'junit.log').read_text(), flush=True)
sys.exit(result.returncode)
