"""One reserved S01 task using only the accepted owner CLI. No model API bypass."""
import argparse
import hashlib
import json
import os
import pathlib
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--source', required=True)
parser.add_argument('--source-sha', required=True)
parser.add_argument('--classpath-file', required=True)
parser.add_argument('--isolation-config', required=True)
args = parser.parse_args()
source = pathlib.Path(args.source).resolve(strict=True)
here = pathlib.Path(__file__).resolve().parent
docs = here.parents[1] / 'docs/research/omega/independence/native-review'
base = pathlib.Path('/workspace/independence-s01-experiment')
run = base / 'run'
config = base / 'service/config.json'
profiles = base / 'profiles'
runtime_file = pathlib.Path('/workspace/synapse-local-model/runtime-env.json')
runtime = json.loads(runtime_file.read_text())
material = json.loads((docs / 'materialization.json').read_text())
typed = json.loads((docs / 'typed-task.json').read_text())
classpath = pathlib.Path(args.classpath_file).read_text().strip()
assert classpath and not classpath.endswith(':') and '' not in classpath.split(':')
assert typed['id'] == 'independence-s01' and typed['limits']['maxModelCalls'] == 1
assert typed['sourceCommit'] == material['commit']
assert subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip() == args.source_sha
assert not subprocess.check_output(['git', '-C', str(source), 'status', '--porcelain'], text=True).strip()
assert subprocess.check_output(['git', '-C', material['repository'], 'rev-parse', 'HEAD'], text=True).strip() == material['commit']
assert not subprocess.check_output(['git', '-C', material['repository'], 'status', '--porcelain'], text=True).strip()
assert not run.exists() and not config.exists() and not profiles.exists(), 'No replacement of uncertain prior execution'
run.mkdir(mode=0o700)
env = {k: os.environ[k] for k in ['PATH', 'HOME', 'TMPDIR', 'JAVA_HOME'] if k in os.environ}
env['LANG'] = 'C.UTF-8'
counter = 0

def execute(label, command, expect=0, timeout=45):
    global counter
    counter += 1
    stem = run / f'{counter:03d}-{label}'
    stem.with_suffix('.command.json').write_text(json.dumps(command) + '\n')
    with stem.with_suffix('.stdout').open('w') as stdout, stem.with_suffix('.stderr').open('w') as stderr:
        result = subprocess.run(command, env=env, stdout=stdout, stderr=stderr, timeout=timeout)
    stem.with_suffix('.exit').write_text(str(result.returncode) + '\n')
    assert result.returncode == expect, f'{label} exit={result.returncode}; inspect {stem}'
    text = stem.with_suffix('.stdout').read_text().strip()
    return json.loads(text.splitlines()[-1]) if text else None

cli_path = source / 'scripts/continuum-coding-service.mjs'

def cli(label, operation, *flags):
    return execute(label, ['node', str(cli_path), operation, '--config', str(config), *map(str, flags)])

# Pin current reviewed artifacts independently. Do not put environment secrets in logs.
pins = {}
for key, expected in [('CONTINUUM_LOCAL_BRAIN_MODEL_PATH', 'cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046'),
                      ('CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH', 'c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a')]:
    item = pathlib.Path(runtime[key]); digest = hashlib.sha256()
    with item.open('rb') as stream:
        while chunk := stream.read(1024 * 1024): digest.update(chunk)
    assert digest.hexdigest() == expected
    pins[key] = {'path': str(item), 'sha256': expected, 'bytes': item.stat().st_size}
(run / 'launch.json').write_text(json.dumps({'source': str(source), 'sourceSha': args.source_sha,
    'fixture': material, 'typedTaskSha256': hashlib.sha256((docs / 'typed-task.json').read_bytes()).hexdigest(),
    'driverSha256': hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest(),
    'classpathFile': args.classpath_file, 'classpath': classpath, 'approvedArtifacts': pins,
    'maxModelCalls': 1, 'externalAnswer': False, 'startedAt': time.time()}, indent=2) + '\n')
# Existing on-disk store is seeded by actual compiled Kotlin, never by fixture JSON.
seed = execute('profile-seed', ['java', '-Xmx256m', '-cp', classpath,
    'at.heinz.deviceai.chief.HandoffHostChecks', 'seed', str(profiles)])
assert seed['count'] == 20
cli('init', 'init', '--project', material['repository'], '--profiles', profiles,
    '--classpath', classpath, '--runtime-env', runtime_file, '--isolation-config', args.isolation_config,
    '--max-queue', 1, '--max-records', 4, '--max-cycles', 4)
before = cli('status-before', 'status')
assert before['state'] == 'STOPPED' and before['queued'] == 0
agents = cli('agents-before', 'agents', '--limit', 20)
assert len(agents['items']) == 20
review = cli('prepare', 'prepare', '--task', docs / 'typed-task.json')
assert review['task']['sourceCommit'] == material['commit']
submitted = cli('submit', 'submit', '--task', docs / 'typed-task.json', '--approve', review['reviewSha256'])
assert submitted['duplicate'] is False and submitted['executed'] is False
started = time.monotonic()
cli('start', 'start')
outcome = None
while time.monotonic() - started < 120:
    status = cli('status', 'status')
    tasks = cli('tasks', 'tasks', '--limit', 4)
    item = next((item for item in tasks['items'] if item['taskId'] == typed['id']), None)
    if item and (item.get('delivery') == 'RETIRED' or item.get('delivery') == 'UNKNOWN'):
        outcome = item
        break
    if status['state'] in ['STOPPED', 'ORPHAN_WORKER']:
        break
    time.sleep(1)
elapsed = time.monotonic() - started
if outcome is None or not outcome.get('terminal'):
    cli('stop-incomplete', 'stop')
    (run / 'outcome.json').write_text(json.dumps({'verdict': 'BLOCKED', 'reason': 'NO_TERMINAL_CONFIRMED_STATE',
        'outcome': outcome, 'elapsedSeconds': elapsed}, indent=2) + '\n')
    raise SystemExit(2)
result = cli('evidence-result', 'evidence', '--task-id', typed['id'], '--evidence-id', 'result')
report = cli('evidence-report', 'evidence', '--task-id', typed['id'], '--evidence-id', 'report')
assert result['content']['modelExecution']['verifiedCalls'] <= 1
assert report['content']['reporterId'] == 'bot-echo'
cli('stop', 'stop')
for attempt in range(20):
    stopped = cli('status-stopped', 'status')
    if stopped['state'] == 'STOPPED': break
    time.sleep(0.2)
assert stopped['state'] == 'STOPPED'
# Actual service restart, with no new submit. It must not replay a terminal task.
cli('restart', 'start')
restarted = cli('tasks-restarted', 'tasks', '--limit', 4)
assert restarted['items'][0]['canonicalTaskId'] == outcome['canonicalTaskId']
assert restarted['items'][0]['state'] == outcome['state']
after = cli('evidence-restarted', 'evidence', '--task-id', typed['id'], '--evidence-id', 'result')
assert after['sha256'] == result['sha256']
cli('stop-after-restart', 'stop')
for attempt in range(20):
    stopped = cli('status-final', 'status')
    if stopped['state'] == 'STOPPED': break
    time.sleep(0.2)
assert stopped['state'] == 'STOPPED' and stopped['queued'] == 0
final = {'schema': 'independence-s01-service-client-v1', 'sourceSha': args.source_sha,
    'task': typed['id'], 'canonicalState': outcome['state'], 'elapsedSeconds': elapsed,
    'result': result, 'report': report, 'restartSameEvidence': True,
    'status': stopped, 'extraIndependentTests': 'NOT_RUN', 'technicalRecall': 'NOT_RUN',
    'deviceVerified': False}
(run / 'outcome.json').write_text(json.dumps(final, indent=2) + '\n')
print(json.dumps({'canonicalState': outcome['state'], 'evidence': str(run), 'restartSameEvidence': True}))
raise SystemExit(0 if outcome['state'] == 'COMPLETED' else 1)
