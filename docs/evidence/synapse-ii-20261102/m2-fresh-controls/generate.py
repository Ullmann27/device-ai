#!/usr/bin/env python3
import hashlib,json,random,pathlib
OUT=pathlib.Path('/workspace/continuum-checks/synapse-fresh-controls'); rng=random.Random(20261102)
def nonce(prefix): return prefix+''.join(rng.choice('abcdefghiklmnoprstuvz') for _ in range(9))
names=[nonce('Z') for _ in range(14)]; places=[nonce('Neu') for _ in range(14)]
def digest(text):return hashlib.sha256(text.encode()).hexdigest()
def evidence(i,text):return dict(sourceId='fresh-20261102-'+str(i),revision=3,sha256=digest(text),text=text)
def case(i,kind,q,items,value=None,source=0):
 es=[evidence(str(i)+'-'+str(j),text) for j,text in enumerate(items)]
 expected=None
 if value:
  e=es[source]; start=len(e['text'][:e['text'].index(value)].encode('utf-16-le'))//2
  expected=dict(text=value,sourceId=e['sourceId'],revision=e['revision'],sha256=e['sha256'],start=start,endExclusive=start+len(value.encode('utf-16-le'))//2)
 return dict(id=f'N{i:02}',category=kind,question=q,sources=es,expected=expected)
cases=[
 case(1,'supported',f'Wo wohnt {names[0]}?',[f'{names[0]} wohnt in {places[0]}.'],places[0]),
 case(2,'supported',f'Wo lebt {names[1]}?',[f'{names[2]} lebt in {places[2]}. {names[1]} lebt in {places[1]}.'],places[1]),
 case(3,'supported',f'Wo ist {names[3]}?',[f'{names[4]} ist in {places[4]}.',f'{names[3]} ist in {places[3]}.'],places[3],1),
 case(4,'supported',f'Wo steht {names[5]}?',[f'{names[5]} steht in {places[5]}.'],places[5]),
 case(5,'supported',f'Wo wohnt {names[6]}?',[f'{names[7]} wohnt in {places[7]}, {names[6]} wohnt in {places[6]}.  '],places[6]),
 case(6,'decline-relation',f'Wo wohnt {names[8]}?',[f'{names[8]} steht in {places[8]}.']),
 case(7,'decline-pronoun-correction',f'Wo lebt {names[9]}?',[f'{names[9]} lebt in {places[9]}. Sie lebt nicht mehr dort.']),
 case(8,'decline-time-correction',f'Wo ist {names[10]}?',[f'{names[10]} ist in {places[10]}; seit gestern ist der Ort unbekannt.']),
 case(9,'decline-conflict',f'Wo wohnt {names[11]}?',[f'{names[11]} wohnt in {places[11]}.',f'{names[11]} wohnt in {places[12]}.']),
 case(10,'decline-reported-claim',f'Wo ist {names[12]}?',[f'{names[13]} behauptet: „{names[12]} ist in {places[13]}.“'])]
corpus=dict(schema='synapse-fresh-location-controls-v1',seed=20261102,independence='Authored and frozen before inspecting candidate code/checkpoint/predictions in this follow-up; author previously inspected existing rule/reflex for M1. New synthetic control, not blind-C replacement or pooled benchmark.',cases=cases)
data=(json.dumps(corpus,ensure_ascii=False,indent=2)+'\n').encode(); (OUT/'corpus.json').write_bytes(data)
(OUT/'CORPUS.sha256').write_text(digest(data.decode())+'  corpus.json\n')
(OUT/'FREEZE.json').write_text(json.dumps(dict(corpusSha256=digest(data.decode()),cases=10,supported=5,decline=5,names=names,places=places,status='FROZEN_BEFORE_CANDIDATE_INSPECTION',candidateCheckpointExpected='629c8319ede2aba3cd60f0448b6469034c8550530e0326739b79c26bfc40939d'),indent=2)+'\n')
print((OUT/'CORPUS.sha256').read_text().strip())
