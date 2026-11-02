#!/usr/bin/env python3
"""Actual frozen-input JVM shadow run; score only after all label-free predictions exist."""
import hashlib,json,os,subprocess,sys,time
from pathlib import Path
root=Path(__file__).resolve().parents[2]
freeze=root/'docs/evidence/synapse-iii-20261102/m2-freeze'
out=Path(sys.argv[1]).resolve();out.mkdir(parents=True,exist_ok=False)
build=out/'classes';build.mkdir();logs=out/'logs';logs.mkdir()
manifest=json.loads((freeze/'FREEZE.json').read_text())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for n,s in manifest['splits'].items(): assert sha(freeze/(n+'.json'))==s['sha256']
pins=json.loads((root/'docs/evidence/synapse-ii-20261102/m2/source-pins.json').read_text()) if (root/'docs/evidence/synapse-ii-20261102/m2/source-pins.json').exists() else {}
assert sha(root/'tools/continuum-span-research/Cdx2PointerCandidate.kt')=='9c7d33b64d7fb1b13e3ceb22f2b538f3e4e62608d477f7e9868ce1f3b79c5f2e'
java=os.environ.get('SHADOW_JAVA','/workspace/post-fable-toolchain/jdk21/bin/java')
kotlinc=os.environ.get('SHADOW_KOTLINC','/workspace/post-fable-toolchain/bin/kotlinc')
jars=os.environ.get('SHADOW_JARS','/workspace/continuum-toolchain')
cp=f'{jars}/json.jar:{jars}/stdlib.jar'; commands=[]
def run(cmd,name):
 t=time.perf_counter();p=subprocess.run(list(map(str,cmd)),capture_output=True,text=True,timeout=180)
 (logs/(name+'.stdout')).write_text(p.stdout);(logs/(name+'.stderr')).write_text(p.stderr)
 commands.append(dict(name=name,command=list(map(str,cmd)),exitCode=p.returncode,millis=(time.perf_counter()-t)*1000))
 (out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
 assert p.returncode==0, name+': '+p.stderr[-2000:]
 return p
src=[root/'tools/continuum-span-research/Cdx2CounterfactualShadowChecks.kt',root/'tools/continuum-span-research/Cdx2PointerCandidate.kt',root/'app/src/main/java/at/heinz/deviceai/learning/span/EvidenceBoundNaturalLocationProbe.kt',root/'tools/continuum-span-research/Cdx2CounterfactualShadow.kt']
run([kotlinc,'-cp',cp,*src,'-d',build],'compile');cp=str(build)+':'+cp
requests=json.loads((freeze/'holdout-d-inputs.json').read_text())['cases']
assert all(set(c)=={'id','question','sources'} for c in requests)
req=out/'requests';req.mkdir();raw=out/'baseline-outputs';raw.mkdir();rows=[]
for c in requests:
 p=req/(c['id']+'.json');p.write_text(json.dumps(c,ensure_ascii=False)+'\n');o=raw/(c['id']+'.json')
 run([java,'-cp',cp,'at.heinz.deviceai.learning.span.Cdx2PointerCandidate','predict',root/'docs/evidence/issue340-20261101/m2/frozen-dev/checkpoint.json',p,o],c['id'])
 rows.append(dict(id=c['id'],**{k:json.loads(o.read_text())[k] for k in ['RAW','VERIFIED','RULE']}))
(out/'baseline-predictions.json').write_text(json.dumps({'cases':rows},indent=2)+'\n')
run([java,'-cp',cp,'at.heinz.deviceai.learning.span.Cdx2CounterfactualShadow','run',freeze/'train.json',freeze/'validation.json',freeze/'holdout-d-inputs.json',out/'candidate'],'train-predict')
# Only now supply labels to independent unchanged scorer, with precommitted corpus hash.
for name,p in [('baseline',out/'baseline-predictions.json'),('seed17',out/'candidate/predictions-17.json'),('seed29',out/'candidate/predictions-29.json'),('ablation17',out/'candidate/ablation-predictions-17.json'),('ablation29',out/'candidate/ablation-predictions-29.json')]:
 run(['node',root/'scripts/cdx2-blind-score.mjs',freeze/'holdout-d.json',p,out/(name+'-score.json'),manifest['splits']['holdout-d']['sha256']],'score-'+name)
for seed in [17,29]:
 run([java,'-cp',cp,'at.heinz.deviceai.learning.span.Cdx2CounterfactualShadowChecks',out/f'candidate/checkpoint-{seed}.json'],f'independent-controls-{seed}')
base=json.loads((out/'baseline-score.json').read_text())
gates={}
for name in ['seed17','seed29','ablation17','ablation29']:
 s=json.loads((out/(name+'-score.json')).read_text())
 passed=s['splits']['PRIMARY']['RAW']['primaryGate'] and all(v['unsupportedPositive']==0 and v['falseSourceAssignments']==0 and v['malformed']==0 for v in [b['RAW'] for b in s['splits'].values()])
 improved=sum(not a['RAW']['exact'] and b['RAW']['exact'] for a,b in zip(base['records'],s['records']))
 regressed=sum(a['RAW']['exact'] and not b['RAW']['exact'] for a,b in zip(base['records'],s['records']))
 gates[name]={'status':'SHADOW_PASS_NOT_PROMOTED' if passed else 'REJECTED','pairedRawImprovements':improved,'pairedRawRegressions':regressed}
report=dict(schema='SYNAPSE_III_SHADOW_REPLAY_V1',sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),sourceHashes={str(p.relative_to(root)):sha(p) for p in src},holdoutSha256=manifest['splits']['holdout-d']['sha256'],promotion='FORBIDDEN_RESEARCH_ONLY',selection='fixed60epochs/0.05/seeds17,29; no test-based tuning',gates=gates,device='NOT_RUN',latencyComparison='NOT_COMPARABLE: cold per-case baseline vs warm batch candidate; no speedup claim')
(out/'replay-report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({n:json.loads((out/(n+'-score.json')).read_text())['splits'] for n in ['baseline','seed17','seed29']},indent=2))
