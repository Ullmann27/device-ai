import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

p = argparse.ArgumentParser()
p.add_argument('--repo', required=True)
p.add_argument('--source', required=True)
p.add_argument('--baseline-manifest', required=True)
p.add_argument('--out', required=True)
a = p.parse_args()
repo = Path(a.repo).resolve()
out = Path(a.out).resolve()
out.mkdir(parents=True, exist_ok=False)
baseline = json.loads(Path(a.baseline_manifest).read_text())

def digest(data):
    return hashlib.sha256(data).hexdigest()

def git(*args):
    return subprocess.check_output(['git', '-C', str(repo), *args])

source = git('rev-parse', a.source + '^{commit}').decode().strip()
tree = git('rev-parse', source + '^{tree}').decode().strip()
test_names = ['ContradictionBoundaryRegressionTest', 'ContradictionBoundaryHoldoutTest',
              'AiContextIntegrityTest', 'AiContextCompilerTest']
paths = list(baseline['sources'])
paths += ['app/src/test/java/at/heinz/deviceai/cognitive/' + name + '.kt' for name in test_names]
copies, hashes = [], {}
for path in dict.fromkeys(paths):
    data = git('show', source + ':' + path)
    destination = out / 'sources' / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
    copies.append(str(destination))
    hashes[path] = digest(data)

contract_path = 'app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryEvents.kt'
contract = git('show', source + ':' + contract_path)
declarations = [line for line in contract.decode().splitlines() if line.startswith('data class RecallHit(')]
assert declarations == ['data class RecallHit(val entryId: String, val score: Float, val rank: Int)']
declaration_path = out / 'sources' / 'RecallHit.kt'
declaration_path.write_text('package at.heinz.deviceai.learning\n\n' + declarations[0] + '\n')
copies.append(str(declaration_path))

prior = baseline['compileCommand']
java = prior[0]
compiler_cp = prior[prior.index('-cp') + 1]
dependency_cp = prior[prior.index('-classpath') + 1]
for filename, expected in baseline['dependencies'].items():
    assert digest(Path(filename).read_bytes()) == expected, 'Dependency changed: ' + filename
classes = out / 'classes'
compile_command = [java, '-Xmx768m', '-cp', compiler_cp,
                   'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                   '-jvm-target', '17', '-classpath', dependency_cp, '-d', str(classes), *copies]
junit_command = [java, '-Xmx512m', '-cp', str(classes) + ':' + dependency_cp,
                 'org.junit.runner.JUnitCore',
                 *['at.heinz.deviceai.cognitive.' + name for name in test_names]]
manifest = {'source': source, 'tree': tree, 'sources': hashes,
            'sourceGraphManifestSha256': digest(Path(a.baseline_manifest).read_bytes()),
            'dependencies': baseline['dependencies'],
            'declarationExtraction': {'sourcePath': contract_path, 'sourceSha256': digest(contract),
                                      'exactDeclaration': declarations[0],
                                      'outputSha256': digest(declaration_path.read_bytes())},
            'compileCommand': compile_command, 'junitCommand': junit_command,
            'runnerSha256': digest(Path(__file__).read_bytes()),
            'androidFrameworkExecuted': False, 'activityExecuted': False}
(out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
with (out / 'compile.log').open('w') as log:
    compilation = subprocess.run(compile_command, stdout=log, stderr=subprocess.STDOUT, timeout=180)
print('compile exit', compilation.returncode, flush=True)
if compilation.returncode:
    print((out / 'compile.log').read_text(), flush=True)
    sys.exit(compilation.returncode)
with (out / 'junit.log').open('w') as log:
    result = subprocess.run(junit_command, stdout=log, stderr=subprocess.STDOUT, timeout=60)
(out / 'result.json').write_text(json.dumps({'source': source, 'tree': tree,
                                            'compileExit': compilation.returncode,
                                            'junitExit': result.returncode}, indent=2) + '\n')
print((out / 'junit.log').read_text(), flush=True)
sys.exit(result.returncode)
