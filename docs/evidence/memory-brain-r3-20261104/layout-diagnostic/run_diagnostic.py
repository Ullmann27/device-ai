"""Read-only reproduction using immutable copies of the actual completed Android compilation."""
from pathlib import Path
import csv
import hashlib
import json
import shutil
import subprocess

OUT = Path(__file__).resolve().parent
REPO = Path('/workspace/scratch/624aeb26d481/continuum-brain-r3')
SOURCE = 'ec2543bef908d7d346a9767f9b095e84254090d1'
BASE = 'e19bf2768dc24b7c2ea1040538fdd661269a540c'
JAVA = Path('/workspace/scratch/b6ee64d418cf/toolchain/jdk17/bin/java')
JAVAC = JAVA.with_name('javac')
PRIOR = Path('/workspace/scratch/624aeb26d481/qa-r3/r3-full-unit-lint-retry1/result.json')

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def git(*args):
    return subprocess.check_output(['git', *args], cwd=REPO, text=True).strip()

assert git('rev-parse', 'HEAD') == SOURCE
assert not git('status', '--porcelain')
prior = json.loads(PRIOR.read_text())
assert prior['sourceBefore']['commit'] == SOURCE
assert prior['sourceUnchanged'] is True
manifest = {'localSource': SOURCE, 'baselineSource': BASE,
            'currentTree': git('rev-parse', SOURCE + '^{tree}'),
            'baselineTree': git('rev-parse', BASE + '^{tree}'),
            'parentBuildReceipt': str(PRIOR), 'parentBuildReceiptSha256': sha(PRIOR),
            'sourceBlobs': {}, 'classSnapshot': {}, 'newAndroidBuild': False,
            'productionEdits': False, 'deviceOrPixelsClaimed': False}
paths = [
    'app/src/main/java/at/heinz/deviceai/ui/ContinuumLandscapeCockpitLayout.kt',
    'app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeLayout.kt',
    'app/src/main/java/at/heinz/deviceai/ui/ContinuumReferenceLayout.kt',
    'app/src/main/java/at/heinz/deviceai/ui/JarvisShellLayout.kt',
    'app/src/main/java/at/heinz/deviceai/ui/JarvisDesign.kt',
    'app/src/main/java/at/heinz/deviceai/ui/JarvisCockpitGrid.kt',
    'app/src/main/java/at/heinz/deviceai/ui/JarvisWindowProfile.kt',
    'app/src/main/java/at/heinz/deviceai/ui/JarvisMenuRail.kt',
    'app/src/test/java/at/heinz/deviceai/ui/ContinuumLandscapeCockpitLayoutTest.kt',
]
for name in paths:
    current = git('rev-parse', SOURCE + ':' + name)
    baseline = git('rev-parse', BASE + ':' + name)
    assert current == baseline, name
    manifest['sourceBlobs'][name] = {'baseline': baseline, 'current': current, 'sha256': sha(REPO / name)}
    dest = OUT / 'sources' / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(REPO / name, dest)

snapshot = OUT / 'actual-compiled-classes'
copied = []
main = REPO / 'app/build/tmp/kotlin-classes/debug/at/heinz/deviceai/ui'
test = REPO / 'app/build/tmp/kotlin-classes/debugUnitTest/at/heinz/deviceai/ui'
for source in sorted(main.glob('*.class')) + sorted(test.glob('ContinuumLandscapeCockpitLayoutTest*.class')):
    dest = snapshot / 'at/heinz/deviceai/ui' / source.name
    dest.parent.mkdir(parents=True, exist_ok=True)
    before = sha(source)
    shutil.copyfile(source, dest)
    assert sha(dest) == before
    copied.append((source, dest, before))
for source, dest, before in copied:
    assert sha(source) == before, 'Concurrent class change: ' + str(source)
    manifest['classSnapshot'][str(source)] = {'copy': str(dest.relative_to(OUT)), 'sha256': before}

cache = Path('/workspace/scratch/d663e8444310/gradle-cache/caches/modules-2/files-2.1')
jars = []
for group, artifact, version in [('org.jetbrains.kotlin','kotlin-stdlib','2.4.0'),
                                  ('junit','junit','4.13.2'), ('org.hamcrest','hamcrest-core','1.3')]:
    matches = list((cache / group / artifact / version).glob('*/*.jar'))
    assert len(matches) == 1
    jars.append(matches[0])
manifest['dependencies'] = {str(path): sha(path) for path in jars}
cp = ':'.join([str(snapshot), *map(str, jars)])
classes = OUT / 'probe-classes'
classes.mkdir(exist_ok=True)
fixture = OUT / 'LayoutDiagnostic.java'
compile_command = [str(JAVAC), '--release', '17', '-cp', cp, '-d', str(classes), str(fixture)]
with (OUT / 'compile.log').open('w') as log:
    compiled = subprocess.run(compile_command, stdout=log, stderr=subprocess.STDOUT, timeout=30)
manifest['compileCommand'] = compile_command
manifest['compileExit'] = compiled.returncode
assert compiled.returncode == 0, (OUT / 'compile.log').read_text()
runtime = ':'.join([str(classes), cp])
for mode, filename in [('junit', 'original-junit.log'), ('geometry', 'geometry.tsv')]:
    command = [str(JAVA), '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', runtime, 'LayoutDiagnostic', mode]
    with (OUT / filename).open('w') as log:
        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=30)
    manifest[mode] = {'command': command, 'exitCode': result.returncode, 'output': filename,
                      'outputSha256': sha(OUT / filename)}
manifest['fixtureSha256'] = sha(fixture)
manifest['runnerSha256'] = sha(Path(__file__))
manifest['headUnchanged'] = git('rev-parse', 'HEAD') == SOURCE and not git('status','--porcelain')
with (OUT / 'geometry.tsv').open() as stream:
    rows = list(csv.DictReader(stream, delimiter='\t'))
manifest['violations'] = [r for r in rows if r['strictTarget'] == 'false']
(OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(json.dumps({key:manifest[key] for key in ['compileExit','junit','geometry','headUnchanged','violations']},indent=2))
