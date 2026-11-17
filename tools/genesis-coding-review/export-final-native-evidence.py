"""Export finite synthetic review evidence, never runtime keys or owner grants."""
import hashlib, json, pathlib, shutil, subprocess, time

root = pathlib.Path(__file__).resolve().parents[2]
out = root / 'docs/research/omega/bot-genesis/coding-review/final-native'
assert not out.exists(), 'Evidence export must be fresh'
out.mkdir(parents=True)

def copy(source, target):
    assert source.is_file() and not source.is_symlink()
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)

for label, directory, case in [
    ('g04', '/workspace/genesis-g04-experiment', 'g04'),
    ('known-u03', '/workspace/genesis-known-u03-experiment', 'u03')
]:
    base = pathlib.Path(directory)
    run = base / 'runs' / case
    assert (run / 'driver.exit').read_text().strip() == '0'
    assert (run / 'independent-audit.exit').read_text().strip() == '0'
    for name in ['profile-seed.stdout', 'profile-seed.stderr', 'profile-seed.exit']:
        copy(base / name, out / label / name)
    for source in run.iterdir():
        if source.is_file():
            copy(source, out / label / source.name)
    for source in (run / 'evidence').iterdir():
        if source.is_file() and source.name != 'grant.json':
            copy(source, out / label / 'evidence' / source.name)
    # Current task grants and private MAC keys remain local. Only the existing
    # execution reports, public model observations and synthetic experience are exported.
    state = run / 'evidence/private-synthetic-state'
    for sub in ['builder/coding-jobs', 'coding-evidence', 'teamleader/reports']:
        directory = state / sub
        if directory.exists():
            for source in directory.rglob('*'):
                relative = source.relative_to(state)
                if source.is_file() and not source.is_symlink() and not any(
                    part.startswith('.') or any(word in part.lower() for word in
                    ['key', 'secret', 'credential', 'grant', 'approval']) for part in relative.parts
                ):
                    copy(source, out / label / 'execution-artifacts' / relative)
    if (state / 'experience.jsonl').exists():
        copy(state / 'experience.jsonl', out / label / 'execution-artifacts/experience.jsonl')

base = pathlib.Path('/workspace/genesis-g04-experiment')
copy(base / 'resume-prerequisites.json', out / 'prerequisites.json')
for name in ['kotlin-host-first-failure', 'kotlin-host']:
    for source in (base / name).iterdir():
        if source.is_file():
            copy(source, out / name / source.name)

live = []
for directory in pathlib.Path('/proc').iterdir():
    if not directory.name.isdigit():
        continue
    try:
        executable = (directory / 'exe').resolve(strict=True)
        if executable == pathlib.Path('/workspace/synapse-local-model/build-cpu/bin/llama-completion'):
            live.append(int(directory.name))
    except (FileNotFoundError, PermissionError, ProcessLookupError, OSError):
        pass
containers = subprocess.run(['docker', 'ps', '--format', '{{.ID}} {{.Image}} {{.Names}}'],
                            capture_output=True, text=True)
assert containers.returncode == 0 and not containers.stdout.strip() and not live
(out / 'cleanup.json').write_text(json.dumps({'atEpoch': time.time(), 'nativePids': live,
    'runningContainers': containers.stdout.splitlines(), 'dockerExit': containers.returncode}, indent=2) + '\n')

manifest = {str(p.relative_to(out)): {'bytes': p.stat().st_size,
    'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(out.rglob('*')) if p.is_file()}
(out / 'SHA256SUMS.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(json.dumps({'exportedFiles': len(manifest), 'bytes': sum(x['bytes'] for x in manifest.values()), 'directory': str(out)}))
