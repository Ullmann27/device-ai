import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time

root = Path('/workspace/scratch/b31a9d731016/device-ai-wiring')
base = Path('/workspace/scratch/b31a9d731016/build-evidence')
gate, expected = sys.argv[1:3]
run_dir = base / ('final-' + expected[:12])
run_dir.mkdir(parents=True, exist_ok=True)

def git(*args):
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()

def source():
    return {'commit': git('rev-parse', 'HEAD'), 'tree': git('rev-parse', 'HEAD^{tree}'),
            'branch': git('branch', '--show-current'),
            'trackedChanges': git('status', '--porcelain', '--untracked-files=no'),
            'untrackedSource': git('ls-files', '--others', '--exclude-standard', '--',
                                   'app/src', 'scripts', 'backend/src', 'backend/test')}

before = source()
if before['commit'] != expected or before['trackedChanges'] or before['untrackedSource']:
    raise SystemExit('Source is not the clean expected commit')
for key in ['CONTINUUM_LOCAL_SIGNING_FILE', 'CONTINUUM_LOCAL_SIGNING_PASSWORD',
            'CONTINUUM_LOCAL_SIGNING_P12_BASE64', 'CONTINUUM_MENTOR_SIGNING_FILE',
            'CONTINUUM_MENTOR_SIGNING_PASSWORD', 'CONTINUUM_MENTOR_SIGNING_P12_BASE64']:
    if os.environ.get(key):
        raise SystemExit('Signing material is forbidden in local QA')

gradle = ['gradle', '--no-daemon', '--max-workers=1', '--console=plain',
          '-Dorg.gradle.jvmargs=-Xmx1600m -XX:MaxMetaspaceSize=512m',
          '-Pkotlin.compiler.execution.strategy=in-process',
          '-PdeviceAiMentorPreview=true', '-PcontinuumStandaloneVersionCode=47']
commands = {
    'node-special': [(['node', '--test', 'scripts/continuum-atomic-acceptance.test.mjs',
        'scripts/continuum-gguf-preflight.test.mjs', 'scripts/continuum-gguf-preflight.integration.test.mjs',
        'scripts/continuum-owner-v38-release.test.mjs', 'scripts/continuum-owner-continuous-release.test.mjs',
        'scripts/continuum-owner-backend-gate.test.mjs'], root)],
    'node-critical': [(['node', 'scripts/continuum-reliability-qa.mjs', 'critical', '--out',
                        str(run_dir / 'critical')], root)],
    'node-full': [(['node', 'scripts/continuum-reliability-qa.mjs', 'full-node', '--out',
                    str(run_dir / 'full-node')], root)],
    'backend': [(['npm', 'ci', '--ignore-scripts', '--no-audit', '--no-fund'], root / 'backend'),
                (['npm', 'run', 'check'], root / 'backend'), (['npm', 'test'], root / 'backend')],
    'android': [(gradle + ['--rerun-tasks', '-PcontinuumUiWidgetTests=true',
                          '-PcontinuumLocalStandaloneQa=true', ':app:compileDebugKotlin',
                          ':app:testDebugUnitTest', ':app:lintDebug'], root)],
    'unsigned': [(gradle + ['-PcontinuumLocalRenderUnsigned=true',
                           ':app:assembleRelease', ':app:bundleRelease'], root)],
}
if gate not in commands:
    raise SystemExit('Unknown gate')
log_path = run_dir / (gate + '.log')
receipt_path = run_dir / (gate + '-receipt.json')
if log_path.exists() or receipt_path.exists():
    raise SystemExit('An existing receipt must not be overwritten')
started = datetime.datetime.now(datetime.timezone.utc).isoformat()
started_ms = int(time.time() * 1000)
results = []
with log_path.open('x') as log:
    for command, cwd in commands[gate]:
        print('COMMAND ' + json.dumps(command), file=log, flush=True)
        result = subprocess.run(command, cwd=cwd, stdout=log, stderr=subprocess.STDOUT)
        results.append({'command': command, 'cwd': str(cwd), 'exitCode': result.returncode})
        if result.returncode:
            break
after = source()
receipt = {'gate': gate, 'startedAt': started, 'startedEpochMillis': started_ms,
           'finishedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
           'sourceBefore': before, 'sourceAfter': after, 'sourceUnchanged': before == after,
           'commands': results, 'logSha256': hashlib.sha256(log_path.read_bytes()).hexdigest(),
           'logBytes': log_path.stat().st_size, 'localUnsignedQaOnly': True,
           'deviceAcceptance': 'NOT_RUN'}
receipt['status'] = 'PASS' if before == after and all(r['exitCode'] == 0 for r in results) else 'FAIL'
receipt_path.write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps({'gate': gate, 'status': receipt['status'], 'receipt': str(receipt_path)}), flush=True)
raise SystemExit(0 if receipt['status'] == 'PASS' else 1)
