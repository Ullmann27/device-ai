"""Measure frozen production redactors sequentially."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import statistics
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--qa-root', type=Path, required=True)
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--variant', action='append', required=True)
args = parser.parse_args()
root = args.out.resolve()
root.mkdir(parents=True, exist_ok=False)
variants = {}
for value in args.variant:
    label, directory = value.split('=', 1)
    assert re.fullmatch(r'[a-z][a-z0-9_-]*', label)
    assert label not in variants
    source_directory = (args.qa_root / directory).resolve()
    manifest_bytes = (source_directory / 'manifest.json').read_bytes()
    manifest = json.loads(manifest_bytes)
    variants[label] = (source_directory, manifest_bytes, manifest)
assert len(variants) >= 2

def digest(data):
    return hashlib.sha256(data).hexdigest()

driver = Path(__file__).with_name('RedactorExperiment.java')
shutil.copyfile(driver, root / driver.name)
first = next(iter(variants.values()))[2]
java = Path(first['compileCommand'][0])
subprocess.run([str(java.with_name('javac')), str(root / driver.name)], check=True, timeout=30)
runs, values = [], {}
labels = list(variants)
for round_number in range(len(labels)):
    order = labels[round_number:] + labels[:round_number]
    for label in order:
        source_directory, manifest_bytes, manifest = variants[label]
        assert manifest['dependencies'] == first['dependencies']
        cp = manifest['compileCommand'][manifest['compileCommand'].index('-classpath') + 1]
        command = [str(java), '-Xmx384m', '-XX:ActiveProcessorCount=1', '-cp',
            str(root) + ':' + str(source_directory / 'classes') + ':' + cp, 'RedactorExperiment']
        target = root / f'{round_number}-{label}.jsonl'
        with target.open('x') as output:
            result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, timeout=90)
        assert result.returncode == 0, target.read_text()
        rows = [json.loads(line) for line in target.read_text().splitlines()]
        assert len(rows) == 20
        for row in rows:
            values.setdefault(row['case'], {}).setdefault(label, []).append(row)
        runs.append({'label': label, 'round': round_number, 'source': manifest['source'],
            'tree': manifest['tree'], 'manifestSha256': digest(manifest_bytes),
            'compilerClassSha256': digest((source_directory / 'classes/at/heinz/deviceai/cognitive/AiContextCompiler.class').read_bytes()),
            'compilerSourceSha256': manifest['sources']['app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt'],
            'output': target.name, 'outputSha256': digest(target.read_bytes()), 'command': command})
        print(json.dumps({'round': round_number, 'label': label, 'samples': len(rows)}), flush=True)
summary = []
for name, samples in values.items():
    item = {'case': name, 'variants': {}}
    for label, rows in samples.items():
        item['variants'][label] = {
            'cpuMicrosecondsPerCall': statistics.median(x['threadCpuNanos'] / x['iterations'] / 1000 for x in rows),
            'allocationBytesPerCall': statistics.median(x['allocatedBytes'] / x['iterations'] for x in rows),
            'outputSha256': sorted({x['outputSha256'] for x in rows}), 'samples': len(rows)}
    summary.append(item)
receipt = {'runs': runs, 'driverSha256': digest(driver.read_bytes()),
    'runnerSha256': digest(Path(__file__).read_bytes()),
    'scope': 'Actual private production method; reflection included. Host JVM only; no model, Android device, battery or whole-app performance claim.',
    'design': 'Rotated sequential process order; each variant occupies each position once. Fixed synthetic inputs; 300 warmup calls then five samples of 200 calls per workload.'}
(root / 'manifest.json').write_text(json.dumps(receipt, indent=2) + '\n')
(root / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
