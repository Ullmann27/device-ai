"""Execute frozen memory source with JUnit."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--repo', required=True)
parser.add_argument('--source', required=True)
parser.add_argument('--out', required=True)
parser.add_argument('--test-class', action='append', required=True)
args = parser.parse_args()
repo = Path(args.repo).resolve()
out = Path(args.out).resolve()
out.mkdir(parents=True, exist_ok=False)
test_names = args.test_class
assert all(re.fullmatch(r'[A-Za-z][A-Za-z0-9]*', name) for name in test_names)

def digest(data):
    return hashlib.sha256(data).hexdigest()

def git(*arguments):
    return subprocess.check_output(['git', '-C', str(repo), *arguments])

source = git('rev-parse', args.source + '^{commit}').decode().strip()
tree = git('rev-parse', source + '^{tree}').decode().strip()
graph_path = 'docs/evidence/memory-brain-r3-20261104/contradiction-red/manifest.json'
graph_data = git('show', source + ':' + graph_path)
graph = json.loads(graph_data)
paths = list(graph['sources']) + [
    'app/src/main/java/at/heinz/deviceai/cognitive/ProofCarryingMemory.kt',
    *['app/src/test/java/at/heinz/deviceai/cognitive/' + name + '.kt' for name in test_names],
]
copies, hashes = [], {}
for path in dict.fromkeys(paths):
    data = git('show', source + ':' + path)
    target = out / 'sources' / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    copies.append(str(target))
    hashes[path] = digest(data)

contract_path = 'app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryEvents.kt'
contract = git('show', source + ':' + contract_path)
declarations = [line for line in contract.decode().splitlines() if line.startswith('data class RecallHit(')]
assert declarations == ['data class RecallHit(val entryId: String, val score: Float, val rank: Int)']
extracted = out / 'sources' / 'RecallHit.kt'
extracted.write_text('package at.heinz.deviceai.learning\n\n' + declarations[0] + '\n')
copies.append(str(extracted))

prior = graph['compileCommand']
java = prior[0]
compiler_cp = prior[prior.index('-cp') + 1]
dependencies_cp = prior[prior.index('-classpath') + 1]
for name, expected in graph['dependencies'].items():
    assert digest(Path(name).read_bytes()) == expected, 'Dependency mismatch: ' + name
classes = out / 'classes'
compile_command = [java, '-Xmx768m', '-XX:ActiveProcessorCount=2', '-cp', compiler_cp,
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
    '-jvm-target', '17', '-classpath', dependencies_cp, '-d', str(classes), *copies]
junit_command = [java, '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp',
    str(classes) + ':' + dependencies_cp, 'org.junit.runner.JUnitCore',
    *['at.heinz.deviceai.cognitive.' + name for name in test_names]]
manifest = {'source': source, 'tree': tree, 'sources': hashes,
    'sourceGraphSha256': digest(graph_data), 'dependencies': graph['dependencies'],
    'recallHitExtraction': {'sourcePath': contract_path, 'sourceSha256': digest(contract),
        'declaration': declarations[0], 'extractedSha256': digest(extracted.read_bytes())},
    'compileCommand': compile_command, 'junitCommand': junit_command,
    'runnerSha256': digest(Path(__file__).read_bytes()), 'testClasses': test_names,
    'androidFrameworkExecuted': False, 'androidKeystoreExecuted': False,
    'signer': 'Test-local HmacSHA256 fixture; actual mutation and ledger logic'}
(out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
with (out / 'compile.log').open('w') as log:
    compiled = subprocess.run(compile_command, stdout=log, stderr=subprocess.STDOUT, timeout=180)
result = {'source': source, 'tree': tree, 'compileExit': compiled.returncode, 'junitExit': None}
if compiled.returncode == 0:
    with (out / 'junit.log').open('w') as log:
        junit = subprocess.run(junit_command, stdout=log, stderr=subprocess.STDOUT, timeout=90)
    result['junitExit'] = junit.returncode
    print((out / 'junit.log').read_text(), flush=True)
else:
    print((out / 'compile.log').read_text(), flush=True)
(out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result), flush=True)
sys.exit(compiled.returncode or result['junitExit'] or 0)
