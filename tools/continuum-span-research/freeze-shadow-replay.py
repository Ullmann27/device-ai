#!/usr/bin/env python3
"""Freeze synthetic shadow-replay splits BEFORE implementing/evaluating the candidate.
Self-authored controls, not external blind data. No mutation/reads of old corpora.
"""
import hashlib
import json
from pathlib import Path
import random
import sys

def write(p, x):
    with p.open('x', encoding='utf-8') as f:
        json.dump(x, f, ensure_ascii=False, indent=2); f.write('\n')

def main():
    out=Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=False)
    manifest={'schema':'SHADOW_REPLAY_FREEZE_V1','seed':2026110203,
      'selection':'fixed before implementation; synthetic same grammar, fresh identity/value vocabulary',
      'promotion':'FORBIDDEN_RESEARCH_ONLY','splits':{}}
    identities=set()
    for split, count, seed, prefix in [('train',80,711,'Ta'),('validation',20,912,'Va'),('holdout-d',55,1319,'Da')]:
        rng=random.Random(seed); cases=[]
        def word():
            w=prefix+''.join(rng.choice('bcdfghjklmnprstv')+rng.choice('aeiou') for _ in range(3))
            assert w not in identities; identities.add(w); return w
        for i in range(count):
            who, other, a, b=word(),word(),word(),word(); ident=f'{split}-{i+1:03}'
            if split=='holdout-d':
                kind='STATIC' if i<20 else 'MISSING' if i<25 else 'SWAP' if i<35 else 'MIXED'
                bucket='PRIMARY' if i<25 else 'ADVERSARIAL'
            else:
                kind='SWAP' if i<count//2 else 'MIXED'; bucket='ADVERSARIAL'
            relation=rng.choice(['ist','wohnt','lebt','steht'])
            mismatch=next(v for v in ['ist','wohnt','lebt','steht'] if v!=relation)
            source_texts=[f'{who} {relation} in {a}. {other} {relation} in {b}.']
            expected_place=a
            if kind=='MISSING':
                question=f'Wo {relation} {word()}?'; expected_place=None
            else: question=f'Wo {relation} {who}?'
            if kind=='SWAP':
                source_texts=[f'{other} {relation} in {a}. {who} {relation} in {b}.']; expected_place=b
            elif kind=='MIXED':
                source_texts=([f'{who} {mismatch} in {a}.',f'{who} {relation} in {b}.'] if i%2 else
                    [f'{who} {relation} in {a}, {who} {mismatch} in {b}.'])
                expected_place=None
            sources=[{'sourceId':ident+f':s{j+1}','revision':1+j,
                      'sha256':hashlib.sha256(t.encode()).hexdigest(),'text':t} for j,t in enumerate(source_texts)]
            expected=None
            if expected_place:
                src=sources[0]; start=src['text'].index(expected_place)
                expected={k:src[k] for k in ['sourceId','revision','sha256']}
                expected.update(text=expected_place,start=start,endExclusive=start+len(expected_place))
            cases.append(dict(id=ident,category=kind,split=bucket,question=question,sources=sources,expected=expected))
        corpus={'schema':'SHADOW_REPLAY_CORPUS_V1','cases':cases}
        p=out/(split+'.json'); write(p,corpus)
        if split=='holdout-d': write(out/'holdout-d-inputs.json',{'cases':[{k:c[k] for k in ['id','question','sources']} for c in cases]})
        manifest['splits'][split]={'items':count,'seed':seed,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
    write(out/'FREEZE.json',manifest)
    print(json.dumps(manifest,indent=2))
if __name__=='__main__': main()
