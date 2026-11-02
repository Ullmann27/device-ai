#!/usr/bin/env python3
import hashlib,json,pathlib,shutil,subprocess,time
R=pathlib.Path('/workspace/continuum-post-fable'); S=pathlib.Path('/workspace/continuum-checks/synapse-fresh-controls'); O=R/'docs/evidence/synapse-ii-20261102/m2-fresh-controls'
PIN='6c73763bf77cf8c1defaeb04574ef39f18156b6bcbc4a597dc20a860a26811e0'; CPIN='629c8319ede2aba3cd60f0448b6469034c8550530e0326739b79c26bfc40939d'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,x):p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n')
assert sha(S/'corpus.json')==PIN
assert not O.exists();O.mkdir(parents=True)
for name in ['generate.py','evaluate.py','corpus.json','CORPUS.sha256','FREEZE.json']:shutil.copy2(S/name,O/name)
for name in ['requests','outputs','logs']:(O/name).mkdir()
checkpoint=R/'docs/evidence/issue340-20261101/m2/frozen-dev/checkpoint.json'
assert sha(checkpoint)==CPIN and checkpoint.stat().st_size==910
execution=json.loads((R/'docs/evidence/synapse-ii-20261102/m2/execution.json').read_text())
base=next(x['command'] for x in execution if x['name']=='C001')[:7]
assert base[4]=='predict'
freeze=json.loads((S/'FREEZE.json').read_text()); tokens=freeze['names']+freeze['places']; overlaps=[]; checked=[]
# Scan the prior research datasets, without feeding them or gold labels to prediction.
for root in [R/'docs/evidence/issue340-20261101/m2',R/'docs/evidence',R/'app/src/test/resources',pathlib.Path('/workspace/continuum-checks/pr330-review-private')]:
 if not root.exists():continue
 for p in root.rglob('*.json'):
  if O in p.parents or p.stat().st_size>20_000_000:continue
  if p in checked:continue
  checked.append(p); text=p.read_text(errors='replace')
  for token in tokens:
   if token in text:overlaps.append(dict(file=str(p),token=token))
assert not overlaps,overlaps
corpus=json.loads((S/'corpus.json').read_text()); modes=['RAW','VERIFIED','RULE']; rows=[]; commands=[]
fields=['text','sourceId','revision','sha256','start','endExclusive']
for c in corpus['cases']:
 request={k:c[k] for k in ['id','question','sources']}; req=O/'requests'/f"{c['id']}.json"; out=O/'outputs'/f"{c['id']}.json";write(req,request)
 # base ends with old request; replace it; checkpoint is the frozen existing file.
 cmd=base[:6]+[str(req),str(out)]
 started=time.monotonic();run=subprocess.run(cmd,capture_output=True,text=True,timeout=30)
 (O/'logs'/f"{c['id']}.stdout").write_text(run.stdout);(O/'logs'/f"{c['id']}.stderr").write_text(run.stderr)
 commands.append(dict(id=c['id'],command=cmd,exitCode=run.returncode,wallSeconds=time.monotonic()-started));assert run.returncode==0
 prediction=json.loads(out.read_text());row=dict(id=c['id'],category=c['category'],expected=c['expected'])
 for m in modes:
  p=prediction[m];expected=c['expected'];is_span=p.get('decision')=='SPAN'
  exact=(p.get('decision')=='NOANSWER') if expected is None else (is_span and all(p.get(k)==expected[k] for k in fields))
  row[m]=dict(exact=exact,falsePositive=expected is None and is_span,unsupportedAbstention=expected is not None and p.get('decision')=='NOANSWER',prediction=p)
 rows.append(row)
assert sha(checkpoint)==CPIN and sha(S/'corpus.json')==PIN
metrics={m:dict(exact=sum(r[m]['exact'] for r in rows),total=10,supportedExact=sum(r[m]['exact'] for r in rows if r['expected'] is not None),supportedTotal=5,declineExact=sum(r[m]['exact'] for r in rows if r['expected'] is None),declineTotal=5,falsePositive=sum(r[m]['falsePositive'] for r in rows),unsupportedAbstention=sum(r[m]['unsupportedAbstention'] for r in rows)) for m in modes}
report=dict(schema='SYNAPSE_NEW_10_CONTROLS_V1',status='COMPLETED_HOST_ONLY',seed=20261102,corpusSha256=PIN,checkpointSha256=CPIN,checkpointBytes=910,caseCount=10,independentFreezeBeforeCandidateInspection=True,rulePriorKnowledge='Auditor had read existing rule/reflex for M1; no new-rule-blindness claim.',freshness=dict(checkedFiles=len(checked),overlaps=overlaps,scope='Prior issue340 datasets and JSON evidence/resources, including available pr330 A/B private corpora. Novel generated identities and complete inputs; templates may overlap.'),metrics=metrics,rows=rows,failures=[r for r in rows if not all(r[m]['exact'] for m in modes)],notPooledWithConsumedBlindC=True,promotion='FORBIDDEN_RESEARCH_ONLY',training='NOT_RUN',device='NOT_RUN',commands=commands)
write(O/'report.json',report);write(O/'source-pins.json',{str(p.relative_to(R)):sha(p) for p in [R/'tools/continuum-span-research/Cdx2PointerCandidate.kt',R/'app/src/main/java/at/heinz/deviceai/learning/span/EvidenceBoundNaturalLocationProbe.kt',checkpoint]})
(O/'SHA256SUMS').write_text(''.join(f'{sha(p)}  {p.relative_to(O)}\n' for p in sorted(O.rglob('*')) if p.is_file()))
print(json.dumps(metrics));print('failures='+','.join(r['id'] for r in report['failures']))
