import hashlib
import json
from pathlib import Path
import subprocess
import sys

OUT = Path(__file__).resolve().parent
REPO = Path('/workspace/scratch/624aeb26d481/continuum-brain-r3')
SOURCE = 'e19bf2768dc24b7c2ea1040538fdd661269a540c'
PUBLIC_SOURCE = 'b04daf5db2cbe40a9fa757499a0d295176a1bfce'
TREE = 'aa439516d342a5410d4473275756a4fa85b4c981'
GRAPH_REPO = Path('/workspace/scratch/59ad19c6d3b7/tuning-memory')
GRAPH_SOURCE = '27a82397b2d7c85fa78c5818001bef945040f95e'
GRAPH_PATH = 'tools/tuning-memory-20261104/sources.txt'
PRIOR_COMMAND = Path('/workspace/scratch/59ad19c6d3b7/memory-context-proof/final/compile-command.json')
JAVA = Path('/workspace/scratch/b7d23de0e715/build-toolchain/jdk17/bin/java')

def sha(data):
    return hashlib.sha256(data).hexdigest()

def git_file(repo, commit, path):
    return subprocess.check_output(['git', '-C', str(repo), 'show', commit + ':' + path])

assert subprocess.check_output(['git', '-C', str(REPO), 'rev-parse', SOURCE + '^{tree}'], text=True).strip() == TREE
assert JAVA.is_file()
graph = git_file(GRAPH_REPO, GRAPH_SOURCE, GRAPH_PATH).decode().splitlines()
paths = [p for p in graph if p.startswith('app/src/main/')]
paths.append('app/src/test/java/at/heinz/deviceai/cognitive/ContextIntegrityFixture.kt')
source_hashes = {}
copies = []
for path in paths:
    data = git_file(REPO, SOURCE, path)
    destination = OUT / 'sources' / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
    source_hashes[path] = sha(data)
    copies.append(str(destination))

# Only this unchanged data-class declaration is needed by the compiler. The
# event hub is not executed; no surrogate retrieval/compiler code is introduced.
contract_path = 'app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryEvents.kt'
contract = git_file(REPO, SOURCE, contract_path)
declarations = [line for line in contract.decode().splitlines() if line.startswith('data class RecallHit(')]
assert declarations == ['data class RecallHit(val entryId: String, val score: Float, val rank: Int)']
recall_source = OUT / 'sources' / 'RecallHit.kt'
recall_source.write_text('package at.heinz.deviceai.learning\n\n' + declarations[0] + '\n')
copies.append(str(recall_source))
probe = OUT / 'ContradictionBoundaryRegressionTest.kt'
copies.append(str(probe))

prior = json.loads(PRIOR_COMMAND.read_text())
compiler_cp = prior[prior.index('-cp') + 1]
dependency_cp = prior[prior.index('-classpath') + 1]
dependencies = {path: sha(Path(path).read_bytes()) for path in dict.fromkeys((compiler_cp + ':' + dependency_cp).split(':'))}
classes = OUT / 'classes'
compile_command = [str(JAVA), '-Xmx768m', '-cp', compiler_cp,
                   'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                   '-jvm-target', '17', '-classpath', dependency_cp, '-d', str(classes), *copies]
junit_command = [str(JAVA), '-Xmx512m', '-cp', str(classes) + ':' + dependency_cp,
                 'org.junit.runner.JUnitCore',
                 'at.heinz.deviceai.cognitive.ContradictionBoundaryRegressionTest']
manifest = {
    'localSource': SOURCE, 'publicSourceWithIdenticalTree': PUBLIC_SOURCE, 'tree': TREE,
    'sources': source_hashes,
    'sourceGraphList': {'commit': GRAPH_SOURCE, 'path': GRAPH_PATH},
    'declarationExtraction': {'path': contract_path, 'sourceSha256': sha(contract),
                             'exactDeclaration': declarations[0], 'outputSha256': sha(recall_source.read_bytes())},
    'probeSha256': sha(probe.read_bytes()), 'runnerSha256': sha(Path(__file__).read_bytes()),
    'dependencies': dependencies, 'compileCommand': compile_command, 'junitCommand': junit_command,
    'androidFrameworkExecuted': False, 'activityExecuted': False,
    'activityProjection': 'The two-line filter/map expression is reproduced explicitly; actual Activity source was reviewed.',
    'productionFilesEdited': False,
}
(OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
with (OUT / 'compile.log').open('w') as log:
    compilation = subprocess.run(compile_command, stdout=log, stderr=subprocess.STDOUT, timeout=180)
print('compile exit', compilation.returncode, flush=True)
if compilation.returncode:
    print((OUT / 'compile.log').read_text(), flush=True)
    sys.exit(compilation.returncode)
with (OUT / 'junit.log').open('w') as log:
    result = subprocess.run(junit_command, stdout=log, stderr=subprocess.STDOUT, timeout=30)
(OUT / 'result.json').write_text(json.dumps({'compileExit': compilation.returncode, 'junitExit': result.returncode,
                                           'publicSource': PUBLIC_SOURCE, 'tree': TREE}, indent=2) + '\n')
print((OUT / 'junit.log').read_text(), flush=True)
sys.exit(result.returncode)
