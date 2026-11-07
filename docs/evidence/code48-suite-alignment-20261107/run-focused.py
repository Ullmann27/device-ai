import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path('/workspace/scratch/3d1967be6193')
SOURCE = ROOT / 'candidate'
attempt, expected = sys.argv[1:3]
assert attempt.replace('-', '').isalnum()
OUT = ROOT / 'qa/full-suite-repair' / attempt
OUT.mkdir(parents=True, exist_ok=False)


def source():
    def git(*args):
        return subprocess.check_output(['git', *args], cwd=SOURCE, text=True).strip()
    return {'commit': git('rev-parse', 'HEAD'), 'tree': git('rev-parse', 'HEAD^{tree}'),
            'trackedChanges': git('status', '--porcelain', '--untracked-files=no'),
            'untrackedSource': git('ls-files', '--others', '--exclude-standard', '--', 'app/src')}


before = source()
assert before['commit'] == expected and not before['trackedChanges'] and not before['untrackedSource']
command = ['gradle', '--no-daemon', '--max-workers=2', '--console=plain',
           '-Dorg.gradle.jvmargs=-Xmx1600m -XX:MaxMetaspaceSize=512m',
           '-Pkotlin.compiler.execution.strategy=in-process', '-PdeviceAiMentorPreview=true',
           '-PcontinuumStandaloneVersionCode=48', '-PcontinuumUiWidgetTests=true',
           '-PcontinuumLocalStandaloneQa=true',
           '--init-script', str(ROOT / 'qa/full-suite-repair/focused-tests.init.gradle'),
           ':app:testDebugUnitTest']
started = datetime.datetime.now(datetime.timezone.utc).isoformat()
with (OUT / 'gradle.log').open('w') as log:
    print('COMMAND ' + json.dumps(command), file=log, flush=True)
    result = subprocess.run(command, cwd=SOURCE, stdout=log, stderr=subprocess.STDOUT)
after = source()
copied = []
for path in sorted((SOURCE / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
    if path.stat().st_mtime >= datetime.datetime.fromisoformat(started).timestamp():
        shutil.copy2(path, OUT / path.name)
        copied.append({'file': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
receipt = {'attempt': attempt, 'sourceBefore': before, 'sourceAfter': after,
           'sourceUnchanged': before == after, 'command': command, 'cwd': str(SOURCE),
           'startedAt': started, 'finishedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
           'exitCode': result.returncode, 'junit': copied,
           'logSha256': hashlib.sha256((OUT / 'gradle.log').read_bytes()).hexdigest(),
           'status': 'PASS' if result.returncode == 0 and before == after else 'FAIL',
           'deviceAcceptance': 'NOT_RUN'}
(OUT / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt))
raise SystemExit(result.returncode)
