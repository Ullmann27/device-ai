"""Export synthetic failure evidence; never private runtime keys or task grants."""
import hashlib
import json
import pathlib
import shutil

root = pathlib.Path(__file__).resolve().parents[2]
base = pathlib.Path('/workspace/independence-s01-experiment')
out = root / 'docs/research/omega/independence/native-review/s01-evidence'
assert not out.exists()
out.mkdir()

def copy(file, relative):
    assert file.is_file() and not file.is_symlink()
    target = out / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(file, target)

for name in ['driver.log', 'driver.exit', 'native-resources.json', 'isolation.json',
             'independent-failure-audit.json', 'independent-failure-audit.stderr',
             'independent-failure-audit.exit', 'cleanup-inspection.json']:
    copy(base / name, name)
for file in (base / 'run').iterdir():
    if not file.is_file(): continue
    # The explicit scoped owner review capability stays private.
    if file.name.endswith('-prepare.stdout'): continue
    if file.name.endswith('.command.json') and '--approve' in json.loads(file.read_text()): continue
    copy(file, pathlib.Path('cli') / file.name)
state = base / 'service/ccc1'
for sub in ['tasks', 'builder/coding-jobs', 'teamleader/reports', 'coding-evidence']:
    for file in (state / sub).rglob('*'):
        if file.is_file():
            relative = file.relative_to(state)
            assert not any('key' in part or 'grant' in part or 'credential' in part for part in relative.parts)
            copy(file, pathlib.Path('canonical') / relative)
manifest = {str(file.relative_to(out)): {'bytes': file.stat().st_size,
    'sha256': hashlib.sha256(file.read_bytes()).hexdigest()} for file in sorted(out.rglob('*')) if file.is_file()}
(out / 'SHA256SUMS.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(json.dumps({'files': len(manifest), 'bytes': sum(item['bytes'] for item in manifest.values()), 'path': str(out)}))
