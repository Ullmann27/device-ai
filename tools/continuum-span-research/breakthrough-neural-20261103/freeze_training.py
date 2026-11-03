#!/usr/bin/env python3
"""Fixed author-known training grammar, disjoint names from development and QA."""
import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'docs/evidence/breakthrough-neural-20261103/training'
def generate():
    cases=[]
    for name,city in [('Alvori','Belvane'),('Cevrila','Dorvane'),('Efroni','Felvane'),
                      ('Gavrika','Helvane'),('Ivrani','Jelvane'),('Kavren','Lelvane')]:
        forms=[(f'In {city} wohnt {name}.',city),
               (f'{name} wohnt in L’{city}.',f'L’{city}'),
               (f'{name} wohnt in {city} am See.',f'{city} am See'),
               (f'{name} wohnt in Neu Groß {city} Nord.',f'Neu Groß {city} Nord')]
        for text,answer in forms:
            for positive in [True,False]:
                cid=f'NEURALTRAIN{len(cases)+1:03d}'
                question=f'Wo wohnt {name if positive else "Mivora"}?'
                source=dict(sourceId=cid+'-source',revision=1,text=text,sha256=hashlib.sha256(text.encode()).hexdigest())
                expected=None
                if positive:
                    start=text.index(answer)
                    expected={k:source[k] for k in ['sourceId','revision','sha256']}
                    expected.update(text=answer,start=len(text[:start].encode('utf-16-le'))//2,
                                    endExclusive=len(text[:start+len(answer)].encode('utf-16-le'))//2)
                cases.append(dict(id=cid,split='ADVERSARIAL',question=question,sources=[source],expected=expected))
    return dict(cases=cases)
if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=False)
    corpus=generate(); data=json.dumps(corpus,ensure_ascii=False,indent=2)+'\n'
    (OUT/'corpus.json').write_text(data)
    (OUT/'FREEZE.json').write_text(json.dumps(dict(schema='BREAKTHROUGH_NEURAL_TRAINING_V1',cases=48,
        author='neural implementer',selection='fixed seed17 epochs60 rate0.05; two residual weights; frozen prior13',
        corpusSha256=hashlib.sha256(data.encode()).hexdigest()),indent=2)+'\n')
    print((OUT/'FREEZE.json').read_text())
