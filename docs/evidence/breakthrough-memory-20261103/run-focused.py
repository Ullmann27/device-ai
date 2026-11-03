#!/usr/bin/env python3
"""Compile the real store and focused tests with official prepared JVM dependencies.

Output must be a fresh directory outside source. No Android/Gradle/device claim.
"""
import argparse
import hashlib
import json
import pathlib
import resource
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--deps', type=pathlib.Path, required=True)
parser.add_argument('--output', type=pathlib.Path, required=True)
parser.add_argument('--benchmark', action='store_true')
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[3]
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=False)
classpath = ':'.join(str(p) for p in sorted(args.deps.resolve().glob('*.jar')))
assert classpath, 'No JVM dependencies'
main = root / 'app/src/main/java/at/heinz/deviceai/learning'
tests = root / 'app/src/test/java/at/heinz/deviceai/learning'
sources = [main / p for p in ['ContinuumLearningExperience.kt', 'Lex3LearningExperienceStore.kt', 'PersonalLearningTransfer.kt']]
test_names = ['LearningExperienceStoreTest', 'Lex3LearningExperienceStoreTest', 'PersonalLearningTransferTest', 'HistoricalLearningLedgerTest', 'LearningExperienceReplayCacheTest']
sources += [tests / (p + '.kt') for p in test_names]
sources += [pathlib.Path(__file__).with_name('ReplayBenchmark.kt').resolve()]
sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
receipt = {'source_sha': sha, 'tracked_status': subprocess.check_output(['git', 'status', '--short'], cwd=root, text=True),
           'source_hashes': {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}, 'runs': []}

def run(name, command):
    start = time.monotonic()
    with (out / (name + '.txt')).open('w') as log:
        log.write('SOURCE_SHA=' + sha + '\nCOMMAND=' + repr(command) + '\n')
        log.flush()
        proc = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT)
        log.write('\nEXIT=' + str(proc.returncode) + '\n')
    receipt['runs'].append({'name': name, 'exit': proc.returncode, 'seconds': time.monotonic()-start,
        'peak_child_rss_kib': resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss})
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(name, 'exit', proc.returncode, 'seconds', round(time.monotonic()-start, 3), flush=True)
    if proc.returncode:
        raise SystemExit(proc.returncode)

run('compile', ['java', '-Xmx512m', '-cp', classpath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
    '-no-stdlib', '-no-reflect', '-classpath', classpath, '-jvm-target', '17', '-d', str(out / 'classes')] + [str(p) for p in sources])
run('focused', ['/usr/bin/time', '-v', 'java', '-Xmx256m', '-cp', str(out / 'classes') + ':' + classpath,
    'org.junit.runner.JUnitCore'] + ['at.heinz.deviceai.learning.' + n for n in test_names])
if args.benchmark:
    run('benchmark', ['/usr/bin/time', '-v', 'java', '-Xms64m', '-Xmx256m', '-cp', str(out / 'classes') + ':' + classpath,
        'at.heinz.deviceai.learning.ReplayBenchmark'])
