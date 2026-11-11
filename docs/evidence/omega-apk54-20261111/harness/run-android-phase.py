from pathlib import Path
import argparse
import collections
import datetime
import fcntl
import hashlib
import json
import os
import re
import shutil
import subprocess
import tarfile
import time

parser = argparse.ArgumentParser()
parser.add_argument('--phase', choices=['ui-recheck', 'full'], required=True)
parser.add_argument('--source', required=True)
args = parser.parse_args()
assert re.fullmatch('[0-9a-f]{40}', args.source), 'INVALID_SOURCE_SHA'
repo = Path('/workspace/scratch/2d62a799e417/device-ai')
qa = Path('/workspace/scratch/2d62a799e417/qa')
tag = args.source[:8]
out = qa / ('android-' + args.phase + '-' + tag)
harness = repo / 'docs/evidence/omega-apk54-20261111/harness'

def git(*argv):
    return subprocess.check_output(['git', *argv], cwd=repo, text=True).strip()

def identity():
    return {'commit': git('rev-parse', 'HEAD'), 'tree': git('rev-parse', 'HEAD^{tree}'),
            'branch': git('branch', '--show-current'),
            'status': git('status', '--porcelain=v1', '--untracked-files=all')}

def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

lock = open('/tmp/continuum-omega-gradle.lock', 'a+')
print('QUEUED', args.phase, args.source, flush=True)
fcntl.flock(lock, fcntl.LOCK_EX)
before = identity()
assert before['commit'] == args.source and not before['status'], 'EXACT_CLEAN_SOURCE_REQUIRED'
if args.phase == 'full':
    prior = json.loads((qa / ('android-ui-recheck-' + tag) / 'driver.json').read_text())
    assert prior['status'] == 'PASS' and prior['source'] == before and prior['sourceUnchanged'], 'UI_RECHECK_REQUIRED'
for name, expected in json.loads((harness / 'SOURCE.json').read_text())['hashes'].items():
    assert digest(harness / name) == expected, 'HARNESS_HASH_MISMATCH:' + name
out.mkdir()
env = os.environ.copy()
env.update(CONTINUUM_CAPTURE_SOURCE_COMMIT=args.source, CONTINUUM_QA_HEAP='3072m',
           CONTINUUM_QA_METASPACE='896m')
init = 'omega-ui-recheck.init.gradle' if args.phase == 'ui-recheck' else 'full-journal.init.gradle'
cmd = ['python', '/workspace/scratch/6139925ecf70/toolchain/run_observed.py', '--continue',
       '-Pkotlin.compiler.execution.strategy=in-process', '-PdeviceAiMentorPreview=true',
       '-PcontinuumOwnerArm64=true', '-PcontinuumStandaloneVersionCode=54',
       '-PcontinuumLocalStandaloneQa=true', '-PcontinuumUiWidgetTests=true',
       '-PcontinuumUiEvidenceDir=' + str(out), '-I', str(harness / 'test-memory.gradle'),
       '-I', 'tools/capture/force_capture.gradle', '-I', str(harness / init), ':app:testDebugUnitTest']
if args.phase == 'ui-recheck':
    cmd.append(':app:lintDebug')
report = {'source': before, 'phase': args.phase, 'command': cmd, 'cwd': str(repo),
          'startedAt': now(), 'status': 'RUNNING', 'strictPdfAllowance': 'NOT_SET',
          'driverSha256': digest(Path(__file__))}
manifest = out / 'driver.json'
manifest.write_text(json.dumps(report, indent=2)+'\n')
log = out / 'gradle.log'
epoch = time.time()
started = time.monotonic()
print('START', args.phase, report['startedAt'], flush=True)
try:
    with log.open('x') as stream:
        run = subprocess.run(cmd, cwd=repo, env=env, stdout=stream, stderr=subprocess.STDOUT)
    report['exit'] = run.returncode
    events = [json.loads(line) for line in (out / 'junit-progress.ndjson').read_text().splitlines()]
    starts = [e for e in events if e['type'] == 'run_start']
    ends = [e for e in events if e['type'] == 'suite_end']
    cases = [e for e in events if e['type'] == 'test_result']
    report['suiteEnd'] = ends[-1] if ends else None
    report['counts'] = dict(collections.Counter(e['result'] for e in cases))
    native_pdf = [e for e in cases
                  if e['class'] == 'at.heinz.deviceai.PdfIntakeNativeDecodeProbeUiTest'
                  and e['name'] == 'actualProviderReadRendersAndExtractsSyntheticPdf']
    report['strictPdfNativeProbe'] = native_pdf
    strict_pdf_pass = len(native_pdf) == 1 and native_pdf[0]['result'] == 'SUCCESS'
    observed = sorted({e['class'] for e in cases})
    report['observedClasses'] = observed
    target_init = init if args.phase == 'ui-recheck' else 'omega-focused.init.gradle'
    required = json.loads(re.search(r'def selectedClasses = (\[.*\])', (harness / target_init).read_text()).group(1))
    report['missingRequiredClasses'] = sorted(set(required) - set(observed))
    (out / 'failures.json').write_text(json.dumps([e for e in cases if e['result'] == 'FAILURE'], indent=2)+'\n')
    (out / 'skips.json').write_text(json.dumps([e for e in cases if e['result'] == 'SKIPPED'], indent=2)+'\n')
    assert starts and ends, 'COMPLETE_JUNIT_REQUIRED'
    assert starts[0]['source']['head'] == args.source and ends[-1]['sourceUnchanged'], 'JUNIT_SOURCE_MISMATCH'
    assert starts[0]['environment']['projectProperties']['continuumPdfAllowMissingHostNative'] is None, 'STRICT_PDF_ALLOWANCE_REFUSED'
    if args.phase == 'full':
        assert not starts[0]['includePatterns'] and not starts[0]['excludePatterns'], 'UNFILTERED_SCOPE_REQUIRED'
    with tarfile.open(out / 'junit-xml.tar.gz', 'w:gz') as archive:
        for file in sorted((repo / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
            archive.add(file, arcname=file.name)
    for name in ['lint-results-debug.xml', 'lint-results-debug.html', 'lint-results-debug.txt']:
        path = repo / 'app/build/reports' / name
        if args.phase == 'ui-recheck' and path.exists() and path.stat().st_mtime >= epoch:
            shutil.copy2(path, out / name)
    if args.phase == 'full':
        gatecmd = ['python', 'scripts/continuum-code52-junit-check.py',
                   'app/build/test-results/testDebugUnitTest', str(out / 'junit-gate-counts.txt')]
        gate = subprocess.run(gatecmd, cwd=repo, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (out / 'junit-gate.log').write_text(gate.stdout)
        report['existingJunitGate'] = {'command': gatecmd, 'exit': gate.returncode, 'output': gate.stdout}
    suite = ends[-1]
    usable = suite['passed'] > 0 and suite['failed'] == 0
    if args.phase == 'ui-recheck':
        usable = usable and suite['skipped'] == 0
    else:
        usable = usable and strict_pdf_pass
    report['status'] = 'PASS' if run.returncode == 0 and usable and not report['missingRequiredClasses'] and (args.phase != 'full' or report['existingJunitGate']['exit'] == 0) else 'FAIL'
except Exception as error:
    report['status'] = 'FAIL'
    report['error'] = type(error).__name__ + ': ' + str(error)
finally:
    report.update(finishedAt=now(), elapsedSeconds=round(time.monotonic()-started, 3),
                  sourceAfter=identity(), logSha256=digest(log),
                  driverAfterSha256=digest(Path(__file__)))
    report['sourceUnchanged'] = before == report['sourceAfter']
    if not report['sourceUnchanged'] or report['driverAfterSha256'] != report['driverSha256']:
        report['status'] = 'FAIL'
    manifest.write_text(json.dumps(report, indent=2)+'\n')
print(json.dumps({k: report.get(k) for k in ['status','phase','exit','counts','sourceUnchanged','missingRequiredClasses','error']}), flush=True)
raise SystemExit(0 if report['status'] == 'PASS' else 1)
