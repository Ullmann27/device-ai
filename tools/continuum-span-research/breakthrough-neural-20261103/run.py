#!/usr/bin/env python3
"""Bounded real JVM experiment, same inputs and unchanged scorer for both models."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import resource
import subprocess
import time

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
BASE = ROOT/'docs/evidence/issue340-20261101/m2/frozen-dev/checkpoint.json'
CURRENT = ROOT/'docs/evidence/synapse-iii-20261102/m2-adapter/candidate/checkpoint-17.json'
SOURCES = [HERE/'GeneralizationProbe.kt', ROOT/'tools/continuum-span-research/Cdx2PointerCandidate.kt',
           ROOT/'tools/continuum-span-research/Cdx2CounterfactualShadow.kt',
           ROOT/'app/src/main/java/at/heinz/deviceai/learning/span/EvidenceBoundNaturalLocationProbe.kt']

def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path): return json.loads(Path(path).read_text())
def write(path, value): Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n')
def same_tuple(a, b):
    if b is None: return a == {'decision':'NOANSWER'}
    return a.get('decision') == 'SPAN' and all(a.get(k) == b[k] for k in ['text','sourceId','revision','sha256','start','endExclusive'])

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('inputs', type=Path)
    parser.add_argument('out', type=Path)
    parser.add_argument('--corpus', type=Path)
    parser.add_argument('--corpus-sha256')
    parser.add_argument('--corruption-controls', action='store_true')
    args = parser.parse_args()
    assert args.inputs.exists() and not args.out.exists()
    if args.corpus:
        assert args.corpus_sha256 and sha(args.corpus) == args.corpus_sha256
    assert not (args.corpus and args.corruption_controls)
    inputs = read(args.inputs)
    assert all(set(c) == {'id','question','sources'} for c in inputs['cases'])
    assert len({c['id'] for c in inputs['cases']}) == len(inputs['cases'])
    args.out.mkdir(parents=True); (args.out/'classes').mkdir(); (args.out/'logs').mkdir()
    cp = '/workspace/continuum-build-env/jvm-deps/json-20240303.jar:/workspace/continuum-build-env/kotlin-official/kotlinc/lib/kotlin-stdlib.jar'
    records=[]
    affinity = sorted(os.sched_getaffinity(0))[:2] if hasattr(os, 'sched_getaffinity') else []
    def limit():
        if affinity: os.sched_setaffinity(0, affinity)
    def run(command, name):
        before=resource.getrusage(resource.RUSAGE_CHILDREN); start=time.perf_counter()
        env=dict(os.environ, JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -Xmx1g')
        process=subprocess.run(list(map(str,command)), cwd=ROOT, env=env, capture_output=True, text=True, timeout=180, preexec_fn=limit)
        after=resource.getrusage(resource.RUSAGE_CHILDREN)
        (args.out/'logs'/f'{name}.stdout').write_text(process.stdout)
        (args.out/'logs'/f'{name}.stderr').write_text(process.stderr)
        records.append(dict(name=name,command=list(map(str,command)),exitCode=process.returncode,
                            wallMillis=(time.perf_counter()-start)*1000,
                            userCpuSeconds=after.ru_utime-before.ru_utime, systemCpuSeconds=after.ru_stime-before.ru_stime,
                            cumulativeChildPeakRssKiB=after.ru_maxrss))
        write(args.out/'commands.json', records)
        if process.returncode: raise RuntimeError(f'{name} failed: {process.stderr[-1500:]}')
    run(['kotlinc','-cp',cp,*SOURCES,'-d',args.out/'classes'], 'compile')
    run(['java','-cp',str(args.out/'classes')+':'+cp,'at.heinz.deviceai.learning.span.GeneralizationProbe',
         BASE,CURRENT,args.inputs,args.out/'predictions'], 'predict')
    # No labels reach the model process. Scoring begins only once both predictions exist.
    result = dict(schema='BREAKTHROUGH_NEURAL_PROBE_V1',sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
                  sourceHashes={str(p.relative_to(ROOT)):sha(p) for p in SOURCES+[HERE/'run.py', ROOT/'scripts/cdx2-blind-score.mjs']},
                  checkpointHashes={'base11':sha(BASE),'current13':sha(CURRENT)},inputsSha256=sha(args.inputs),
                  cpuAffinity=affinity,heapLimitBytes=1073741824,device='NOT_RUN',promotion='FORBIDDEN_RESEARCH_ONLY',arms={})
    if args.corpus:
        corpus=read(args.corpus); original={c['id']:c for c in corpus['cases']}
        assert inputs == {'cases':[{k:c[k] for k in ['id','question','sources']} for c in corpus['cases']]}
        result['corpusSha256']=sha(args.corpus)
        for arm in ['base11','current13']:
            prediction=args.out/'predictions'/f'{arm}-predictions.json'
            run(['node',ROOT/'scripts/cdx2-blind-score.mjs',args.corpus,prediction,args.out/f'{arm}-score.json',args.corpus_sha256],f'score-{arm}')
            scores=read(args.out/f'{arm}-score.json')
            modes={mode:{key:sum(split[mode][key] for split in scores['splits'].values()) for key in
                   ['supported','exactSupported','missing','correctAbstentions','unsupportedPositive','falseSourceAssignments','falseAbstentions','malformed']}
                   for mode in ['RAW','RULE','VERIFIED']}
            diagnostics=[]
            for row in read(prediction)['cases']:
                gold=original[row['id']]['expected']; options=row['options']
                target=[o for o in options if same_tuple(o['tuple'],gold)]
                competitors=[o for o in options if target and o['features']==target[0]['features'] and not same_tuple(o['tuple'],gold)]
                diagnostics.append(dict(id=row['id'],category=original[row['id']].get('category','unspecified'),
                    candidateCount=len(options),goldAvailable=bool(target),identicalFeatureCompetitors=len(competitors),
                    classifierNll=-math.log(max(target[0]['probability'],1e-300)) if target else None,
                    ruleReason=row['ruleReason']))
            write(args.out/f'{arm}-diagnostics.json', diagnostics)
            result['arms'][arm]=dict(modes=modes,goldAbsent=sum(not d['goldAvailable'] for d in diagnostics),
                                    featureAliasCases=sum(d['identicalFeatureCompetitors']>0 for d in diagnostics))
        a=read(args.out/'base11-score.json')['records'];b=read(args.out/'current13-score.json')['records']
        result['pairedCurrentVsBase']={mode:dict(improvements=sum(not x[mode]['exact'] and y[mode]['exact'] for x,y in zip(a,b)),
                                                  regressions=sum(x[mode]['exact'] and not y[mode]['exact'] for x,y in zip(a,b))) for mode in ['RAW','RULE','VERIFIED']}
    if args.corruption_controls:
        for arm in ['base11','current13']:
            rows=read(args.out/'predictions'/f'{arm}-predictions.json')['cases']
            ok=all(row[mode]=={'decision':'NOANSWER'} for row in rows for mode in ['RAW','RULE','VERIFIED'])
            result['arms'][arm]=dict(cases=len(rows),allModesAbstained=ok)
            assert ok, 'corruption positive must reject'
    result['timings']=read(args.out/'predictions/timings.json')
    result['childPeakRssKiB']=resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
    result['rssScope']='Cumulative child high-water mark includes Kotlin compilation; Linux host only'
    write(args.out/'report.json',result)
    print(json.dumps({k:v for k,v in result.items() if k not in ['sourceHashes','timings']},indent=2))

if __name__=='__main__': main()
