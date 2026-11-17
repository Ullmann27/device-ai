#!/usr/bin/env python3
"""Read-only reanalysis of frozen PR357 results; never rewrites the holdout or scores predictions."""
import hashlib
import json
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[2]
evidence = root / 'docs/evidence/sovereign-cognition-20261102'
corpus_path = evidence / 'frozen-context-holdout.json'
corpus_bytes = corpus_path.read_bytes()
digest = hashlib.sha256(corpus_bytes).hexdigest()
assert digest == (evidence / 'frozen-context-holdout.sha256').read_text().split()[0]
cases = json.loads(corpus_bytes)['cases']
parent = json.loads((evidence / 'final-audit/parent.json').read_text())
candidate = json.loads((evidence / 'final-audit/candidate.json').read_text())
assert parent['corpusSha256'] == candidate['corpusSha256'] == digest
by_id = []
for report in [parent, candidate]:
    rows = report['rows']
    assert len({r['id'] for r in rows}) == len(rows) == len(cases)
    by_id.append({r['id']: r for r in rows})
records = []
groups = {}
for case in cases:
    a, b = [rows[case['id']] for rows in by_id]
    expected = case['expected']
    assert a['expected'] == b['expected'] == expected
    assert a['correct'] == (a['actual'] == expected)
    assert b['correct'] == (b['actual'] == expected)
    record = {'id': case['id'], 'expected': expected, 'parentActual': a['actual'],
              'candidateActual': b['actual'], 'parentCorrect': a['correct'], 'candidateCorrect': b['correct']}
    key = json.dumps([case['kind'], case['question'], case['content'], case['maxBytes'], expected], ensure_ascii=False)
    groups.setdefault(key, []).append(record)
    records.append(record)
for group in groups.values():
    assert len({(r['parentActual'], r['candidateActual']) for r in group}) == 1, 'Inconsistent duplicate predictions'
def summarize(rows):
    return {'count': len(rows), 'parentCorrect': sum(r['parentCorrect'] for r in rows),
            'candidateCorrect': sum(r['candidateCorrect'] for r in rows),
            'improved': sum(not r['parentCorrect'] and r['candidateCorrect'] for r in rows),
            'regressed': sum(r['parentCorrect'] and not r['candidateCorrect'] for r in rows),
            'unchanged': sum(r['parentCorrect'] == r['candidateCorrect'] for r in rows),
            'parentWrongAnswers': sum(r['parentActual'] is not None and not r['parentCorrect'] for r in rows),
            'candidateWrongAnswers': sum(r['candidateActual'] is not None and not r['candidateCorrect'] for r in rows),
            'candidateCorrectNegativeAbstentions': sum(r['expected'] is None and r['candidateActual'] is None for r in rows)}
report = {'status': 'HOST_VERIFIED_REANALYSIS_OF_RESEARCH_SHADOW', 'corpusSha256': digest,
          'holdoutChanged': False, 'newInferencePerformed': False,
          'allRows': summarize(records), 'uniqueCases': summarize([g[0] for g in groups.values()]),
          'deduplicationKey': ['kind', 'question', 'content', 'maxBytes', 'expected'],
          'duplicates': [[r['id'] for r in g] for g in groups.values() if len(g) > 1],
          'limitations': ['Existing synthetic compiler/verifier predictions only; no new CDX/Gemma/device result.',
                         'The original72 rows and original reports are unchanged.'], 'rows': records}
assert hashlib.sha256(corpus_path.read_bytes()).hexdigest() == digest
if len(sys.argv) == 2:
    with Path(sys.argv[1]).open('x', encoding='utf-8') as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
        handle.write('\n')
print(json.dumps({'allRows': report['allRows'], 'uniqueCases': report['uniqueCases'], 'corpusSha256': digest}, ensure_ascii=False))
