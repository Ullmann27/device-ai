#!/usr/bin/env python3
"""Independent data authoring only. No candidate imports, training, or model execution."""
import hashlib, json, random, re
from pathlib import Path
ROOT = Path(__file__).resolve().parent
REPO = Path('/workspace/continuum-post-fable')
SEED = 34020261101
rng = random.Random(SEED)
used = set()
def token(prefix):
    while True:
        value = prefix + ''.join(rng.choice('bcdfghjklmnprstvz') + rng.choice('aeiou') for _ in range(3))
        if value not in used:
            used.add(value)
            return value

def place(words=1):
    return ' '.join(token('Uv') for _ in range(words))
def sha(text): return hashlib.sha256(text.encode('utf-8')).hexdigest()
def units(text): return len(text.encode('utf-16-le')) // 2
cases=[]
def add(split, category, question, texts, selected=None):
    cid=f'C{len(cases)+1:03}'
    sources=[{'sourceId': f'bc:{cid}:s{i+1}', 'revision': 1+(len(cases)+i)%7,
              'text': text, 'sha256': sha(text)} for i,text in enumerate(texts)]
    expected=None
    if selected is not None:
        source_index, answer=selected
        source=sources[source_index]
        start=source['text'].index(answer)
        assert source['text'].count(answer)==1
        expected={k:source[k] for k in ('sourceId','revision','sha256')}
        expected.update(text=answer,start=units(source['text'][:start]),endExclusive=units(source['text'][:start+len(answer)]))
    cases.append(dict(id=cid,split=split,category=category,question=question,sources=sources,expected=expected))

verbs=['ist','wohnt','lebt','steht']
for i in range(20):
    a,b,c=token('Zy'),token('Qe'),token('Qe')
    la,lb,lc=place(1+i%3),place(1+(i+1)%3),place()
    v=verbs[i%4]
    forms=[f'{a} {v} in {la}.',f'{b} {v} in {lb}, {a} {v} in {la}.',
           f'{a} {v} in {la} und {b} {v} in {lb}.',f'{b} {v} in {lb}; {a} {v} in {la}.',
           f'{b} {v} in {lb}. {a} {v} in {la}.']
    target=forms[i%5]
    if i==16: target='🔷 Archiv. '+target
    count=1+i%4
    texts=[f'{token("Qe")} {v} in {place()}. ' + f'{c} {v} in {lc}.' for _ in range(count-1)]
    slot=(i*3+1)%count
    texts.insert(slot,target)
    add('PRIMARY','SUPPORTED',f'Wo {v} {a}?',texts,(slot,la))
for i in range(5):
    a,b=token('Zy'),token('Qe'); v=verbs[i%4]
    texts=[f'{b} {v} in {place(1+i%3)}.']
    if i==1: texts.append(f'{a} liest ein Buch.')
    if i==2: texts.append(f'{a} spielt Schach.')
    if i==3: texts.extend([f'{token("Qe")} ist in {place()}.',f'{token("Qe")} lebt in {place()}.',f'{a} singt.'])
    if i==4: texts.append(f'{a} zeichnet eine Karte.')
    add('PRIMARY','MISSING',f'Wo {v} {a}?',texts)

# Five independently authored controls in each predeclared family.
for i in range(5):
    a,b=token('Zy'),token('Qe'); la,lb=place(1+i%3),place(); v=verbs[i%4]
    patterns=[f'{a} {v} nicht in {la}.',f'{a} {v} in {la}, aber nicht in {lb}.',
              f'{a} {v} nicht in {la}, sondern in {lb}.',f'{a} {v} in {la}. Nein, falsch.',
              f'{a} {v} in {la}. Widerruf: {a} {v} nicht in {la}.']
    add('ADVERSARIAL','NEGATION',f'Wo {v} {a}?',[patterns[i]])
for i in range(5):
    a=token('Zy'); la,lb=place(1+i%3),place(); v=verbs[i%4]
    texts=[f'{a} {v} in {la}.',f'{a} {v} in {lb}.'] if i%2==0 else [f'{a} {v} in {la}; {a} {v} in {lb}.']
    if i==4: texts.extend([f'{token("Qe")} ist in {place()}.',f'{token("Qe")} steht in {place()}.'])
    add('ADVERSARIAL','CONFLICT',f'Wo {v} {a}?',texts)
for i in range(5):
    a=token('Zy'); la,lb=place(1+i%3),place(); v=verbs[i%4]
    patterns=[f'{a} {v} heute in {la}.',f'Gestern {v} {a} in {la}.',
              f'{a} {v} seit gestern in {la}.',f'{a} {v} in {la}. Morgen {v} {a} in {lb}.',
              f'{a} {v} früher in {la}, heute in {lb}.']
    add('ADVERSARIAL','TIME',f'Wo {v} {a}?',[patterns[i]])
for i in range(5):
    a,b=token('Zy'),token('Qe'); la,lb=place(1+i%3),place(1+(i+1)%3); v=verbs[i%4]
    texts=[f'{a} {v} in {la}, {b} {v} in {lb}.']
    question=f'Wo {v} {b}?'
    if i==1: texts=[f'{b} {v} in {lb} und {a} {v} in {la}.']
    if i==2: texts=[f'{a} {v} in {la}.',f'{b} {v} in {lb}.']
    if i==3: texts=[f'{b} {v} in {lb}; {a} {v} in {la}.']
    if i==4: texts=[f'🟪 Register. {a} {v} in {la}. {b} {v} in {lb}.']
    add('ADVERSARIAL','ROLE',question,texts,(1 if i==2 else 0,lb))
for i in range(5):
    a=token('Zy'); la,lb=place(1+i%3),place(); v=verbs[i%4]; other=verbs[(i+1)%4]
    texts=[f'{a} {other} in {la}.']
    if i==2: texts.append(f'{a} {v} in {lb}.')
    if i==3: texts=[f'{a} {v} in {lb}, {a} {other} in {la}.']
    if i==4: texts=[f'{token("Qe")} {v} in {lb}.',f'{a} {other} in {la}.']
    add('ADVERSARIAL','RELATION',f'Wo {v} {a}?',texts)
for i in range(5):
    a=token('Zy'); la,lb=place(1+i%3),place(); v=verbs[i%4]
    texts=[f'{a} {v} in {la}.',f'{a} {v} in {la}.']
    if i==1: texts=[f'{a} {v} in {la}.',f'{a} {v} nicht in {lb}.']
    if i==2: texts=[f'{a} {v} in {la}.',f'{a} {v} heute in {lb}.']
    if i==3: texts=[f'{a} {v} in {la}.',f'{a} liest ein Buch.']
    if i==4: texts=[f'{a} {v} in {la}.',f'{a} {v} in {la}.',f'{token("Qe")} ist in {lb}.',f'{token("Qe")} steht in {place()}.']
    add('ADVERSARIAL','SAME_NAME_SOURCE_BOUNDARY',f'Wo {v} {a}?',texts)

assert len(cases)==55
assert sum(c['expected'] is not None for c in cases[:25])==20
for c in cases:
    assert len(c['question'])<160 and 1<=len(c['sources'])<=4
    assert sum(units(s['text']) for s in c['sources'])<=4096
    for s in c['sources']:
        assert re.fullmatch(r'[a-zA-Z0-9._:-]{1,96}',s['sourceId'])
        assert s['sha256']==sha(s['text'])
    if c['expected']:
        e=c['expected']; s=next(s for s in c['sources'] if s['sourceId']==e['sourceId'])
        extracted=s['text'].encode('utf-16-le')[e['start']*2:e['endExclusive']*2].decode('utf-16-le')
        assert extracted==e['text'] and 1<=len(extracted.split())<=3

legacy_paths=[
 'docs/evidence/jarvis-evolution-20261101/blind-a/corpus/fixture.json',
 'docs/evidence/jarvis-evolution-20261101/blind-b/corpus/fixture.json',
 'tools/continuum-span-research/Cdx1OracleBoundExperiment.kt']
legacy_raw={p:(REPO/p).read_text(encoding='utf-8') for p in legacy_paths}
all_legacy='\n'.join(legacy_raw.values())
assert not any(re.search(r'(?<![\w-])'+re.escape(t)+r'(?![\w-])',all_legacy) for t in used)
assert not any(c['question'] in all_legacy for c in cases)
assert not any(s['text'] in all_legacy for c in cases for s in c['sources'])

def write(name,data):
    (ROOT/name).write_text(json.dumps(data,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
write('corpus.json',{'schema':'ISSUE340_BLIND_C_V1','seed':SEED,'cases':cases})
write('inputs.json',{'schema':'ISSUE340_BLIND_C_INPUT_V1','cases':[{k:c[k] for k in ('id','question','sources')} for c in cases]})
write('overlap.json',{'scope':'Only published synthetic A/B fixtures and Oracle corpus generator source; no owner records or candidate code read.',
 'references':[{'path':p,'sha256':sha(t)} for p,t in legacy_raw.items()],
 'exactQuestionOverlaps':0,'exactFullSourceOverlaps':0,'syntheticProperTokenOverlaps':0,
 'oracleMethod':'All independent proper tokens absent from entire public Oracle source, which includes every generator name/location literal; therefore no generated Oracle question/source can exactly overlap.',
 'namespaces':['Zy','Qe','Uv'],'caseCount':len(cases)})
print(json.dumps({'path':str(ROOT/'corpus.json'),'sha256':sha((ROOT/'corpus.json').read_text()),'primarySupported':20,'primaryMissing':5,'adversarial':30}))
