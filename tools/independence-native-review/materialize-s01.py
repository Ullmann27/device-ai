"""Create only the frozen owner-visible repository, never a solution or hidden tests."""
import hashlib
import json
import pathlib
import subprocess
import sys

frozen = pathlib.Path(__file__).resolve().parent / 'frozen' / 's01'
manifest = json.loads((frozen / 'manifest.json').read_text())
for name, expected in manifest['files'].items():
    content = (frozen / name).read_bytes()
    assert len(content) == expected['bytes']
    assert hashlib.sha256(content).hexdigest() == expected['sha256']

destination = pathlib.Path(sys.argv[1]).resolve()
assert not destination.exists(), 'Never replace a prior fixture or consumed attempt'
(destination / 'lib').mkdir(parents=True)
(destination / '.github' / 'continuum').mkdir(parents=True)
source = (frozen / 'subject.mjs').read_bytes()
test = (frozen / 'owner.test.mjs').read_bytes()
assert test.count(b"from './subject.mjs'") == 1
test = test.replace(b"from './subject.mjs'", b"from '../../lib/subject.mjs'")
(destination / 'lib' / 'subject.mjs').write_bytes(source)
(destination / '.github' / 'continuum' / 'owner.test.mjs').write_bytes(test)

def git(*args):
    return subprocess.check_output(['git', '-C', str(destination), *args], text=True).strip()

git('init', '-q', '-b', 'frozen-s01')
git('config', 'user.name', 'Continuum independent fixture')
git('config', 'user.email', 'fixture@localhost')
git('add', '--', 'lib/subject.mjs', '.github/continuum/owner.test.mjs')
git('commit', '-q', '-m', 'Frozen independent S01 owner task')
print(json.dumps({
    'schema': 'independence-s01-materialization-v1',
    'repository': str(destination),
    'commit': git('rev-parse', 'HEAD'),
    'tree': git('rev-parse', 'HEAD^{tree}'),
    'status': git('status', '--porcelain'),
    'freezeManifestSha256': hashlib.sha256((frozen / 'manifest.json').read_bytes()).hexdigest(),
    'sourceSha256': hashlib.sha256(source).hexdigest(),
    'ownerTestSha256': hashlib.sha256(test).hexdigest(),
    'independentTestSha256': manifest['files']['independent.test.mjs']['sha256'],
    'testTransformation': 'Only relative import from ./subject.mjs to ../../lib/subject.mjs',
    'independentTestsInOwnerRepository': False,
    'modelCalls': 0,
}, indent=2))
