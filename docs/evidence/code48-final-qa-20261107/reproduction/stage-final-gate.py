import collections
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path('/workspace/scratch/3d1967be6193')
SOURCE = ROOT / 'candidate'
SHA = '5d91442e98f78a2af623b8a3c74920805f670cb9'
TREE = 'ae79915e7b6df784f0ba78ead4a44b44dedec08a'
RUN = ROOT / 'qa/combined/final-5d91442e98f7-notice-final'
OUT = ROOT / 'qa/final-handoff-staging/final-gates'
OUT.mkdir(parents=True, exist_ok=True)
gate = sys.argv[1]
assert gate in {'node-full', 'node-critical', 'backend', 'node-special', 'android', 'unsigned'}


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


receipt_path = RUN / (gate + '-receipt.json')
receipt = json.loads(receipt_path.read_text())
assert receipt['gate'] == gate
assert receipt['status'] == 'PASS' and receipt['sourceUnchanged']
assert receipt['sourceBefore'] == receipt['sourceAfter']
assert receipt['sourceBefore']['commit'] == SHA
assert receipt['sourceBefore']['tree'] == TREE
assert not receipt['sourceBefore']['trackedChanges']
assert not receipt['sourceBefore']['untrackedSource']
assert all(c['exitCode'] == 0 for c in receipt['commands'])
log_path = RUN / (gate + '.log')
assert sha256(log_path) == receipt['logSha256']
assert log_path.stat().st_size == receipt['logBytes']
files = [(receipt_path, receipt_path.name), (log_path, log_path.name)]
summary = {'gate': gate, 'status': 'PASS', 'sourceCommit': SHA, 'sourceTree': TREE,
           'sourceUnchanged': True, 'startedAt': receipt['startedAt'],
           'finishedAt': receipt['finishedAt'], 'commands': receipt['commands'],
           'deviceAcceptance': 'NOT_RUN', 'signingAndPublication': 'NOT_RUN'}

if gate in {'node-full', 'node-critical'}:
    profile = 'full-node' if gate == 'node-full' else 'critical'
    folder = RUN / profile
    report = json.loads((folder / 'report.json').read_text())
    assert report['status'] == 'PASS' and report['sourceUnchanged']
    assert report['source']['commit'] == SHA
    summary['checks'] = []
    for check in report['checks']:
        item = {k: check.get(k) for k in ['id', 'status', 'reason']}
        item['tests'] = {k: v for k, v in check.get('tests', {}).items()
                         if isinstance(v, (int, bool))}
        summary['checks'].append(item)
    files.extend((p, p.relative_to(RUN).as_posix()) for p in sorted(folder.rglob('*')) if p.is_file())
elif gate in {'backend', 'node-special'}:
    log = log_path.read_text()
    counts = {}
    for key in ['tests', 'suites', 'pass', 'fail', 'cancelled', 'skipped', 'todo']:
        matches = re.findall(r'^\s*ℹ\s+' + key + r'\s+(\d+)\s*$', log, re.MULTILINE)
        assert matches, key
        counts[key] = int(matches[-1])
    assert counts['fail'] == 0 and counts['cancelled'] == 0
    assert counts['tests'] == counts['pass'] + counts['skipped'] + counts['todo']
    summary['tests'] = counts
elif gate == 'android':
    folder = SOURCE / 'app/build/test-results/testDebugUnitTest'
    xml_files = sorted(folder.glob('TEST-*.xml'))
    assert xml_files
    totals = collections.Counter()
    suites = []
    skipped = []
    for path in xml_files:
        assert path.stat().st_mtime * 1000 >= receipt['startedEpochMillis'], path
        suite = ET.parse(path).getroot()
        assert suite.tag == 'testsuite'
        counts = {k: int(suite.get(k, '0')) for k in ['tests', 'failures', 'errors', 'skipped']}
        cases = suite.findall('testcase')
        assert len(cases) == counts['tests'], path
        assert sum(c.find('failure') is not None for c in cases) == counts['failures']
        assert sum(c.find('error') is not None for c in cases) == counts['errors']
        assert sum(c.find('skipped') is not None for c in cases) == counts['skipped']
        totals.update(counts)
        suites.append({'name': suite.get('name'), **counts, 'timeSeconds': float(suite.get('time', '0')),
                       'file': path.name, 'sha256': sha256(path)})
        for case in cases:
            element = case.find('skipped')
            if element is not None:
                skipped.append({'suite': suite.get('name'), 'test': case.get('name'),
                                'reason': element.get('message') or element.text or ''})
        files.append((path, 'android-junit/' + path.name))
    assert totals['failures'] == 0 and totals['errors'] == 0
    totals['passed'] = totals['tests'] - totals['skipped']
    summary['tests'] = dict(totals)
    summary['suiteCount'] = len(suites)
    summary['suites'] = suites
    summary['skippedCases'] = skipped
    lint = SOURCE / 'app/build/reports/lint-results-debug.xml'
    assert lint.stat().st_mtime * 1000 >= receipt['startedEpochMillis']
    lint_root = ET.parse(lint).getroot()
    issues = lint_root.findall('issue')
    severities = dict(collections.Counter(i.get('severity') for i in issues))
    assert severities.get('Error', 0) == 0 and severities.get('Fatal', 0) == 0
    summary['lint'] = {'status': 'PASS', 'severityCounts': severities,
                       'issueCount': len(issues), 'sha256': sha256(lint)}
    files.append((lint, 'android-lint/lint-results-debug.xml'))
    assert 'BUILD SUCCESSFUL' in log_path.read_text()
elif gate == 'unsigned':
    assert 'BUILD SUCCESSFUL' in log_path.read_text()
    summary['artifactInspection'] = 'SEPARATE_INDEPENDENT_AUDIT_REQUIRED'

archive = OUT / (gate + '-evidence.zip')
manifest = []
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
    for path, name in sorted(files, key=lambda pair: pair[1]):
        entry = zipfile.ZipInfo(name, date_time=(2026, 11, 7, 0, 0, 0))
        entry.compress_type = zipfile.ZIP_DEFLATED
        entry.external_attr = 0o100644 << 16
        bundle.writestr(entry, path.read_bytes(), compresslevel=9)
        manifest.append({'path': name, 'bytes': path.stat().st_size, 'sha256': sha256(path)})
summary['rawEvidence'] = {'archive': archive.name, 'bytes': archive.stat().st_size,
                          'sha256': sha256(archive), 'files': manifest}
shutil.copy2(receipt_path, OUT / receipt_path.name)
summary_path = OUT / (gate + '-summary.json')
summary_path.write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps({'gate': gate, 'summary': str(summary_path), 'tests': summary.get('tests'),
                  'archiveBytes': archive.stat().st_size, 'archiveSha256': sha256(archive)}))
