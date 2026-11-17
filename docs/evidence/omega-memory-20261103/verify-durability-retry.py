#!/usr/bin/env python3
"""Real Linux fsync(EIO) regression. Requires official JVM deps and strace; no device/power-loss claim."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--deps', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[3]
out = args.output.resolve()
assert not out.is_relative_to(repo), 'Use a disposable output directory outside the repository'
assert shutil.which('strace'), 'UNAVAILABLE: strace is required for real syscall fault injection'
out.mkdir(parents=True, exist_ok=True)
cp = ':'.join(str(p) for p in sorted(args.deps.resolve().glob('*.jar')))
assert cp, 'Official Kotlin JVM jars are required'
sources = [repo / 'app/src/main/java/at/heinz/deviceai/learning' / (name + '.kt')
           for name in ['ContinuumLearningExperience', 'Lex3LearningExperienceStore']]
sources.append(Path(__file__).with_name('DurabilityRetryProbe.kt'))
receipt = {'source_sha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip(),
           'source_hashes': {str(p.relative_to(repo)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
           'cases': []}
classes = out / 'classes'
command = ['java', '-Xmx512m', '-cp', cp, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
           '-no-stdlib', '-no-reflect', '-classpath', cp, '-jvm-target', '17', '-d', str(classes), *map(str, sources)]
with (out / 'compile.log').open('w') as log:
    subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
base = ['java', '-Xmx128m', '-cp', str(classes) + ':' + cp, 'at.heinz.deviceai.learning.DurabilityRetryProbe']
for mode in ['status', 'append', 'batch', 'matching']:
    fixture = out / ('fixture-' + mode)
    assert not fixture.exists(), 'Use a fresh output directory for each run'
    subprocess.run([*base, str(fixture), 'init'], check=True)
    trace = out / (mode + '.strace')
    command = ['strace', '-f', '-e', 'trace=fsync,rename', '-e', 'inject=fsync:error=EIO:when=4',
               '-o', str(trace), *base, str(fixture), mode]
    run = subprocess.run(command, text=True, capture_output=True)
    (out / (mode + '.log')).write_text(run.stdout + run.stderr)
    assert run.returncode == 0, mode + ': Java probe failed; inspect raw logs'
    lines = trace.read_text().splitlines()
    injected = [i for i, line in enumerate(lines) if 'fsync(' in line and '(INJECTED)' in line]
    assert len(injected) == 1, mode + ': expected exactly one real fsync EIO'
    after = lines[injected[0] + 1:]
    syncs = sum('fsync(' in line and '= 0' in line for line in after)
    assert syncs >= 2, mode + ': retry acknowledged without successful root and parent fsync'
    receipt['cases'].append({'mode': mode, 'exit': run.returncode, 'successful_fsyncs_after_eio': syncs})
(out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt, indent=2))
