import test from 'node:test';
import assert from 'node:assert/strict';
import {SubwordEncoder, buckets} from './portable-subword.mjs';
import {createNeuralState, trainRound, ingestPublicSources, evaluateSuite, exportWeights, selectNeuralResearchTarget} from './neural-core.mjs';
import {TRAIN, VALIDATION, AUDIT} from './neural-curriculum.mjs';
import {dialogueTurn} from './neural-dialogue.mjs';

test('gradient updates change shared weights and survive checksummed restore', () => {
  const m=new SubwordEncoder(); const before=m.evaluate(TRAIN.intent).loss;
  for(let i=0;i<8000;i++)m.updateIntent(TRAIN.intent[i%TRAIN.intent.length],.12);
  assert.ok(m.evaluate(TRAIN.intent).loss<before*.75);
  const bytes=m.save(), restored=SubwordEncoder.restore(bytes);
  assert.equal(m.intentScore('Wie lernst du?'),restored.intentScore('Wie lernst du?'));
  bytes[40]^=1; assert.throws(()=>SubwordEncoder.restore(bytes),/checksum/);
  assert.throws(()=>buckets('!!!'));
});

test('curriculum, selection validation and audit have disjoint normalized texts',()=>{
  const texts=s=>new Set([...s.intent.map(x=>x.text),...s.semantic.flatMap(x=>[x.left,x.right])].map(x=>x.toLowerCase().replace(/[^\p{L}\p{N}]/gu,'')));
  const sets=[TRAIN,VALIDATION,AUDIT].map(texts);
  for(let i=0;i<3;i++)for(let j=i+1;j<3;j++)assert.equal([...sets[i]].filter(x=>sets[j].has(x)).length,0);
});

test('bounded training is persistent, multitask gated and rollback is byte exact',()=>{
  const state=createNeuralState();
  const result=trainRound(state,{steps:12000,now:1000});
  assert.ok(result.report.steps>0);
  assert.ok(result.report.candidateHash!==result.report.baselineHash);
  assert.equal(result.report.auditUsedForSelection,false);
  assert.ok(result.report.accepted,'first curriculum round should demonstrate held-out gain');
  for(const task of ['intent','semantic'])assert.ok(result.report.validationAfter[task].correct>=result.report.validationBefore[task].correct);
  const reloaded=JSON.parse(JSON.stringify(result.state));
  assert.deepEqual(exportWeights(reloaded),exportWeights(result.state));
  const cancelled=trainRound(reloaded,{steps:200,cancelled:()=>true,now:2000});
  assert.equal(cancelled.report.accepted,false);
  assert.deepEqual(exportWeights(cancelled.state),exportWeights(reloaded));
  assert.ok(evaluateSuite(SubwordEncoder.restore(exportWeights(reloaded)),AUDIT).intent.total>=20);
});

test('public replay rejects generated/private content, bounds and deduplicates sources',()=>{
  let s=createNeuralState();
  s=ingestPublicSources(s,[{title:'Replay learning',text:'Replay learning revisits older examples and helps retain previous representations.',url:'https://example.org/paper',source:'PublicWeb'}],100);
  assert.equal(s.replay.length,1); assert.equal(s.replay[0].trust,'PUBLIC_OBSERVATION');
  s=ingestPublicSources(s,[{title:'Private password',text:'Never train this owner secret',url:'file:///private',source:'OWNER'}],101);
  assert.equal(s.replay.length,1);
  const rows=Array.from({length:200},(_,i)=>({title:'Research topic '+i,text:'Research topic '+i+' describes repeated learning of representations with retained examples.',url:'https://example.org/'+i,source:'PublicWeb'}));
  s=ingestPublicSources(s,rows,200);assert.ok(s.replay.length<=96);
});

test('dialogue resolves follow-up context and never trains or exports owner secrets',()=>{
  const s={neural:createNeuralState(),lessons:[{text:'Mein Projekt heißt Sternenreise.',id:'owner-1'}],baby:{beliefs:[]}};
  const weights=exportWeights(s.neural);
  const first=dialogueTurn(s,'Was weißt du über Sternenreise?',100);
  assert.match(first.answer,/Sternenreise/);
  const second=dialogueTurn(s,'Und was weißt du noch darüber?',101);
  assert.match(second.resolvedQuery,/Sternenreise/);
  assert.equal(s.dialogue.turns.length,2);
  assert.deepEqual(weights,exportWeights(s.neural));
  assert.match(dialogueTurn(s,'xyz unbekanntes thema',102).answer,/keine|noch nicht/i);
});

test('later rounds can select an earlier safe checkpoint without dropping semantic retention',()=>{
  let s=createNeuralState();
  for(let i=0;i<3;i++)s=trainRound(s).state;
  assert.ok(s.accepted>=2,'safe intermediate checkpoints must allow continued learning');
  for(const r of s.history.filter(x=>x.accepted)){
    assert.ok(r.validationAfter.intent.correct>=r.validationBefore.intent.correct);
    assert.ok(r.validationAfter.semantic.correct>=r.validationBefore.semantic.correct);
  }
});

test('source replay is consumed by gradients and corrupt persistence fails closed',()=>{
  let s=ingestPublicSources(createNeuralState(),[{title:'Replay representations',text:'Repeated replay trains shared representations using retained public source examples.',url:'https://example.org/replay',source:'PublicWeb'}],123);
  const r=trainRound(s);assert.ok(r.report.sourceUpdates>0);
  const bad=structuredClone(r.state);bad.modelHash='0'.repeat(64);
  assert.throws(()=>exportWeights(bad),/hash/);
});

test('current JavaScript inference still matches committed Kotlin interchange fixture',async()=>{
  const fs=await import('node:fs');
  const fixture=new URL('../../app/src/test/resources/continuum-neural-swb1.base64',import.meta.url);
  const bytes=Buffer.from(fs.readFileSync(fixture,'utf8').trim(),'base64');
  const props=Object.fromEntries(fs.readFileSync(new URL('../../app/src/test/resources/continuum-neural-swb1.properties',import.meta.url),'utf8').trim().split('\n').map(x=>x.split('=')));
  const m=SubwordEncoder.restore(bytes);
  assert.equal(m.intentScore('Wie funktioniert dein Gedächtnis?'),Number(props.intent));
  assert.equal(m.similarity('pflanzen brauchen licht','sonne licht pflanzen'),Number(props.semantic));
  assert.equal(buckets('Grüße aus Österreich!').join(','),props.buckets);
});

test('neural curiosity proposes a source-bound question without inventing beliefs',()=>{
 const state=ingestPublicSources(createNeuralState(),[{title:'Continual learning replay',text:'Continual learning uses replay of examples to retain older learned representations.',url:'https://example.org/continual',source:'PublicWeb'}],123);
 const target=selectNeuralResearchTarget(state);
 assert.equal(target.experienceId,state.replay[0].id);
 assert.match(target.text,/Continual learning replay/);
 assert.equal(state.replay[0].trust,'PUBLIC_OBSERVATION');
 assert.equal(selectNeuralResearchTarget(createNeuralState()),null);
});

test('every persisted dialogue turn identifies the exact immutable model used',()=>{
 const state={neural:createNeuralState(),lessons:[{id:'fact',text:'Sternenreise ist mein Projekt.'}],baby:{beliefs:[]}};
 const before=state.neural.modelHash;
 const reply=dialogueTurn(state,'Erzähl mir etwas über Sternenreise',100);
 assert.equal(reply.modelHash,before);
 assert.equal(state.dialogue.turns[0].modelHash,before);
 assert.equal(state.neural.modelHash,before);
});
