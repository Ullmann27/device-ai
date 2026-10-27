import test from 'node:test';import assert from 'node:assert/strict';
import {SubwordEncoder} from './portable-subword.mjs';
import {projectedSemantic} from '../../experimental/swb1-contrastive-20261027/contrastive-lab.mjs';

test('head-orthogonal semantic step removes first-order intent-direction interference',()=>{
 const m=new SubwordEncoder(73);const before=m.embeddings.slice(),head=m.head.slice();
 projectedSemantic(m,{left:'Der Filter ist geöffnet.',right:'Der Filter ist geschlossen.',similar:false});
 const component=m.embeddings.reduce((n,x,i)=>n+(x-before[i])*head[i%16],0);
 assert.ok(Math.abs(component)<1e-14,'residual head component='+component);
 assert.deepEqual(m.head,head);assert.equal(m.updates,1);assert.equal(m.save().length,32960);
});
test('projection rejects invalid rate without altering a model checkpoint',()=>{
 const m=new SubwordEncoder(73);const bytes=m.save();
 assert.throws(()=>projectedSemantic(m,{left:'links',right:'rechts',similar:false},Infinity));assert.deepEqual(m.save(),bytes);
});

test('native contrastive trials preserve original state and deterministic schedule snapshots',async()=>{
 const {runContrastiveTrial}=await import('../../experimental/swb1-contrastive-20261027/contrastive-lab.mjs');
 const {createNeuralState,exportWeights}=await import('./neural-core.mjs');
 const state=createNeuralState(),before=JSON.stringify(state),bytes=exportWeights(state);
 const train={split:'train',intent:[{text:'Hallo allerseits',label:0},{text:'Warum ist das passiert?',label:1}],semantic:[{left:'Das Tor ist auf.',right:'Das Tor steht offen.',similar:true},{left:'Das Tor ist auf.',right:'Das Tor ist geschlossen.',similar:false}]};
 const val={...train,split:'validation'};
 const a=runContrastiveTrial(state,train,val,{steps:16,every:8,seed:101});const b=runContrastiveTrial(state,train,val,{steps:16,every:8,seed:101});
 assert.deepEqual(a.bytes,b.bytes);assert.equal(a.report.stepsCompleted,16);assert.equal(a.report.productionEligible,false);assert.equal(a.report.finalSetUsed,false);
 assert.equal(JSON.stringify(state),before);assert.deepEqual(exportWeights(state),bytes);
 assert.throws(()=>runContrastiveTrial(state,train,{...val,split:'final'},{steps:16,every:8}),/SPLIT_ROLE/);
});
test('interruption produces no promotable checkpoint and no negative learning signal',async()=>{
 const {runContrastiveTrial}=await import('../../experimental/swb1-contrastive-20261027/contrastive-lab.mjs');const {createNeuralState}=await import('./neural-core.mjs');
 const t={split:'train',intent:[{text:'Hallo',label:0}],semantic:[{left:'Ein Tor',right:'Eine Tür',similar:true}]};
 const r=runContrastiveTrial(createNeuralState(),t,{...t,split:'validation'},{steps:16,every:8,cancelled:()=>true});assert.equal(r.report.status,'INFRA_INTERRUPTED_OR_BUDGET');assert.equal(r.report.researchGatePassed,false);assert.equal(r.report.stepsCompleted,0);
});
test('new frozen split does not reuse old curriculum or the observed 52 transfer phrases',async()=>{
 const fs=await import('node:fs');const {TRAIN,VALIDATION,AUDIT}=await import('./neural-curriculum.mjs');const {TRANSFER_SUITE_V1}=await import('./neural-fresh-transfer-20261026.mjs');const {sha256}=await import('./portable-subword.mjs');
 const dir=new URL('../../experimental/swb1-contrastive-20261027/fixtures/',import.meta.url);const seal=JSON.parse(fs.readFileSync(new URL('seal.json',dir)));
 const texts=s=>[...s.intent.map(x=>x.text),...s.semantic.flatMap(x=>[x.left,x.right])].map(x=>x.toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim());
 const old=new Set([TRAIN,VALIDATION,AUDIT,TRANSFER_SUITE_V1].flatMap(texts));const all=new Set();
 for(const split of ['train','validation','final']){const bytes=fs.readFileSync(new URL(split+'.json',dir));assert.equal(sha256(bytes),seal.files[split].sha256);const s=JSON.parse(bytes);assert.equal(s.semantic.filter(x=>x.similar).length,s.semantic.length/2);const current=new Set(texts(s));for(const t of current){assert.equal(old.has(t),false,t);assert.equal(all.has(t),false,t);}for(const t of current)all.add(t);}
});

test('a changing semantic sidecar cannot alter the frozen original intent predictions',async()=>{
 const {frozenIntentView}=await import('../../experimental/swb1-contrastive-20261027/contrastive-lab.mjs');
 const original=new SubwordEncoder(73),semantic=SubwordEncoder.restore(original.save());
 const question='Warum fehlt der Beleg?';const before=original.intentScore(question);
 for(let i=0;i<12;i++)semantic.updateIntent({text:question,label:1});
 assert.notEqual(semantic.intentScore(question),before);
 const view=frozenIntentView(original,semantic);assert.equal(view.intentScore(question),before);assert.equal(view.similarity('Ein Beleg','Ein Bericht'),semantic.similarity('Ein Beleg','Ein Bericht'));
});
