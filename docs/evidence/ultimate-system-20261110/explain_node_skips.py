import json, subprocess, xml.etree.ElementTree as ET
from pathlib import Path

root = Path('/workspace/scratch/c9f74e2967e3')
repo = root / 'device-ai'
qa = root / 'qa'
report = json.loads((qa / 'node-release-bridge-final/report.json').read_text())
source = report['source']['commit']
assert report['status'] == 'PASS' and report['sourceUnchanged']
files = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', source, '--', '.github/continuum', 'scripts'], cwd=repo, text=True).splitlines()
texts = {p: subprocess.check_output(['git', 'show', source + ':' + p], cwd=repo, text=True) for p in files if p.endswith('.test.mjs')}
rows = []
for case in ET.parse(qa / 'node-release-bridge-final/full-node.xml').iter('testcase'):
    skip = case.find('skipped')
    if skip is None:
        continue
    name = case.get('name')
    raw = skip.get('message') or skip.text
    if not isinstance(raw, str) or not raw.strip():
        raise ValueError('Missing textual skip reason: ' + str(name))
    reason = raw.strip()
    row = {'class': case.get('classname'), 'test': name, 'reason': reason}
    if reason.lower() in {'true', 'false'}:
        matches = [(p, text) for p, text in texts.items() if name in text]
        assert len(matches) == 1, (name, matches)
        path, text = matches[0]
        if 'CONTINUUM_WORKSPACE_HOST_PROOF' in text:
            assert "skip:process.env.CONTINUUM_WORKSPACE_HOST_PROOF!=='1'" in text
            reason = "NOT_RUN: CONTINUUM_WORKSPACE_HOST_PROOF != '1'; the real Docker workspace proof was not enabled."
        elif path.endswith(('kotlin-delegate-acceptance.test.mjs', 'kotlin-delegate-synthesizer.test.mjs', 'owner-kotlin-preview.test.mjs')):
            assert "Boolean(process.env.CONTINUUM_REPAIR_DEPTH)||" in text
            assert "spawnSync('kotlinc',['-version'],{encoding:'utf8',timeout:15000}).status!==0" in text
            reason = 'NOT_RUN: the test guard requires no CONTINUUM_REPAIR_DEPTH and a successful kotlinc -version CLI probe; at least one prerequisite was absent. Original JUnit does not distinguish the two.'
        else:
            raise ValueError('Unexplained boolean skip: ' + name)
        row.update(reason=reason, rawReason=raw, sourceFile=path, sourceCommit=source, reasonBasis='Verified source guard; not a claim that the skipped test ran')
    assert isinstance(row['reason'], str) and row['reason'].lower() not in {'true', 'false'}
    rows.append(row)
check = next(c for c in report['checks'] if c['id'] == 'full-node')
assert len(rows) == check['tests']['skipped'] == 37
assert len({r['test'] for r in rows}) == len(rows)
(qa / 'skipped-node-tests.json').write_text(json.dumps(rows, indent=2) + '\n')
print(json.dumps({'skipped': len(rows), 'booleanReasonsExplained': sum('rawReason' in r for r in rows), 'source': source}))
