import test from 'node:test';
import assert from 'node:assert/strict';
import {SubwordEncoder} from './portable-subword.mjs';
import {createNeuralState,exportWeights} from './neural-core.mjs';
import {isLocalNumericPatch} from './local-numeric-patch.mjs';
import {canonical,digest,evaluateAst,integerGrid,createSynthesisGrammar,synthesize,verifyProgram,
  patchOperator,assertDisjointSplits,createRepairMemory,repairTask,createFrozenCommittee,contextCost,
  numericResearchCandidate} from './capability-research.mjs';
import {trainingTasks,holdoutTasks,adversarialTasks,PROTOCOL} from './capability-research-fixtures.mjs';
import {runCapabilityResearch} from './capability-research-experiments.mjs';

const examples=spec=>integerGrid(3).map(input=>({input:[...input],expected:spec(...input)}));
const simple=(id,family,seed,source,spec,split='train')=>({id,family,seed,source,split,examples:examples(spec)});
const first=simple('train-probe','sum-probe',3,['sub','x','y'],(x,y)=>x+y);
const patch={path:[],from:'sub',to:'add'};
const rehash=record=>{const {contentHash,...body}=record;return {...body,contentHash:digest(body)};};
const grammar=createSynthesisGrammar();

test('finite interpreter executes nested arithmetic and rejects executable text, excessive ASTs and overflow',()=>{
  assert.equal(evaluateAst(['max',0,['sub','x','y']],[5,8]),0);
  assert.equal(evaluateAst(['abs',['sub','x','y']],[-3,4]),7);
  for(const ast of [['exec','x'],['add','x'],['constructor','x','y'],'process.exit()'])
    assert.throws(()=>evaluateAst(ast,[1,2]),/INVALID_AST/);
  let deep='x';for(let i=0;i<12;i++)deep=['abs',deep];
  assert.throws(()=>evaluateAst(deep,[1,2]),/AST_LIMIT/);
  let huge='x';for(let i=0;i<6;i++)huge=['mul',huge,huge];
  assert.throws(()=>evaluateAst(huge,[1000,0]),/AST_LIMIT/);
  assert.throws(()=>evaluateAst(['mul',['mul','x','x'],['mul',['mul','x','x'],['mul','x','x']]],[1000,0]),/ARITHMETIC_OVERFLOW/);
});

test('grammar construction is deterministic, bounded, target-free and contains composed functions',()=>{
  const other=createSynthesisGrammar();assert.equal(grammar.fingerprint,other.fingerprint);
  assert.ok(grammar.programs.length>100);assert.ok(grammar.generated>grammar.programs.length);
  assert.ok(grammar.programs.some(p=>verifyProgram(p.ast,examples((x,y)=>Math.abs(x-y))).passed));
  assert.throws(()=>createSynthesisGrammar({maxNodes:6}),/GRAMMAR_LIMIT/);
});

test('counterexample acquisition eliminates actual candidates and respects oracle budget',()=>{
  const requested=[],result=synthesize({grammar,oracle:input=>{requested.push(input);return Math.max(0,input[0]-input[1]);}});
  assert.ok(verifyProgram(result.ast,examples((x,y)=>Math.max(0,x-y))).passed);
  assert.ok(result.trace.some(r=>r.counterexample));assert.ok(result.trace.some(r=>r.after<r.before));
  assert.ok(result.acquisitions<=8);assert.equal(result.oracleCalls,requested.length);
  assert.ok(result.disagreementLookups>0);assert.equal(result.autoApplyAuthorized,false);
});

test('passive baseline executes its own queries and outside-grammar tasks cannot acquire a false proof',()=>{
  const p=synthesize({grammar,oracle:([x,y])=>x-y,mode:'passive',seed:73});
  assert.ok(verifyProgram(p.ast,examples((x,y)=>x-y)).passed);assert.equal(p.disagreementLookups,0);
  const outside=synthesize({grammar,oracle:([x,y])=>x*y});
  assert.equal(outside.status,'ABSTAIN');assert.equal(outside.verifiedBeyondQueryDomain,false);
  assert.throws(()=>synthesize({grammar,oracle:()=>NaN}),/INVALID_ORACLE/);
});

test('split gate rejects family, seed, ID overlap and renamed identical source/evidence',()=>{
  const held=simple('held-probe','distance-probe',101,['abs',['add','x','y']],(x,y)=>Math.abs(x-y),'holdout');
  assert.equal(assertDisjointSplits([first],[held]),true);
  for(const key of ['id','family','seed'])assert.throws(()=>assertDisjointSplits([first],[{...held,[key]:first[key]}]),/SPLIT_OVERLAP/);
  assert.throws(()=>assertDisjointSplits([first],[{...first,id:'alias',family:'alias',seed:103,split:'holdout'}]),/CONTENT_ALIAS/);
  assert.equal(assertDisjointSplits(trainingTasks(),[...holdoutTasks(),...adversarialTasks()]),true);
});

test('memory executes evidence, keeps caller inputs unchanged, and stores only shadow hints',()=>{
  const before=canonical(first),memory=createRepairMemory({trainingTasks:[first]});
  const receipt=memory.learn(first.id,patch);assert.equal(memory.validate(receipt).valid,true);
  assert.equal(canonical(first),before);assert.equal(receipt.authority,'SHADOW_ONLY');
  assert.equal(receipt.witness.executed,49);assert.equal(receipt.autoApplyAuthorized,false);
  assert.equal(memory.statistics().writesPerformed,false);assert.ok(memory.statistics().proofExecutions>=49);
  assert.throws(()=>memory.learn(first.id,{path:[],from:'sub',to:'min'}),/UNVERIFIED_MEMORY/);
});

test('corruption, self-rehashed proof and holdout provenance cannot enter verified memory',()=>{
  const memory=createRepairMemory({trainingTasks:[first]}),receipt=memory.learn(first.id,patch);
  const corrupt=structuredClone(receipt);corrupt.witness.executed=1;
  assert.equal(memory.import(corrupt).reason,'CONTENT_HASH_MISMATCH');
  const forged=structuredClone(receipt);forged.witness.repaired=['max','x','y'];
  assert.equal(memory.import(rehash(forged)).reason,'PROOF_OR_PROVENANCE_MISMATCH');
  const contamination=structuredClone(receipt);contamination.provenance.taskId='held-probe';
  assert.equal(memory.import(rehash(contamination)).reason,'UNREGISTERED_TRAINING_PROVENANCE');
  assert.throws(()=>memory.learn('held-probe',patch),/TRAINING_PROVENANCE_REQUIRED/);
});

test('fresh replay verifies transfer and rejects wrong remembered hints before fallback',()=>{
  const memory=createRepairMemory({trainingTasks:[first]});memory.learn(first.id,patch);
  const task=simple('new-probe','new-shape',101,['abs',['sub','x','y']],(x,y)=>Math.abs(x+y),'holdout');
  const baseline=repairTask(task),reused=repairTask(task,{memory});
  assert.equal(reused.status,'VERIFIED_FINITE_DOMAIN');assert.ok(verifyProgram(reused.ast,task.examples).passed);
  assert.ok(reused.trace[0].memoryHash);assert.equal(reused.autoApplyAuthorized,false);
  assert.ok(reused.attempts<=baseline.attempts);
  const negative=simple('negative-probe','changed-goal',211,['abs',['sub','x','y']],(x,y)=>Math.abs(Math.min(x,y)),'holdout');
  const baseNegative=repairTask(negative),replayNegative=repairTask(negative,{memory});
  assert.ok(verifyProgram(replayNegative.ast,negative.examples).passed);
  assert.ok(replayNegative.trace.some(r=>r.memoryHash&&!r.passed));
  assert.ok(replayNegative.executed>=baseNegative.executed);
});

test('revocation blocks saved receipt, reimport, stale context IDs and mutation of exposed copies',()=>{
  const memory=createRepairMemory({trainingTasks:[first]}),receipt=memory.learn(first.id,patch);
  const exposed=memory.active();exposed[0].hint.to='max';assert.equal(memory.active()[0].hint.to,'add');
  const contextIds=[receipt.contentHash];assert.equal(memory.revoke(receipt.contentHash),true);
  assert.equal(memory.import(receipt).reason,'REVOKED');assert.deepEqual(memory.active(contextIds),[]);
  assert.throws(()=>memory.learn(first.id,patch),/REVOKED_MEMORY/);
  const replay=repairTask(first,{memory,contextIds});assert.ok(replay.trace.every(t=>t.memoryHash===null));
  assert.equal(replay.status,'VERIFIED_FINITE_DOMAIN');
});

test('repair verifier abstains on unavailable operation rather than accepting nearest remembered hint',()=>{
  const memory=createRepairMemory({trainingTasks:[first]});memory.learn(first.id,patch);
  const task=simple('product-probe','product-probe',101,['sub','x','y'],(x,y)=>x*y,'holdout');
  assert.equal(repairTask(task,{memory}).status,'ABSTAIN');
  assert.equal(repairTask(task,{memory,maxAttempts:1}).attempts,1);
});

test('SWB1 committee uses a restored NEURON snapshot without changing bytes or state',()=>{
  const state=createNeuralState(),before=canonical(state),bytes=exportWeights(state),copy=Buffer.from(bytes);
  const committee=createFrozenCommittee(bytes),records=[
    {id:'one',text:'signed subtraction difference operands delta',strategy:'add:sub'},
    {id:'two',text:'upper maximum positive bound larger',strategy:'min:max'}];
  const result=committee.select({query:'subtraction difference',records,budget:40});
  assert.ok(result.ids.length>0);assert.ok(result.cost<=40);assert.equal(result.modelTrainingAuthorized,false);
  assert.equal(canonical(state),before);assert.ok(bytes.equals(copy));assert.ok(exportWeights(state).equals(copy));
  assert.throws(()=>createFrozenCommittee(SubwordEncoder.createExperimentalSwb2().save()),/SWB1_REQUIRED/);
});

test('context baselines execute rankings and all selectors respect byte-cost budget without duplicate IDs',()=>{
  const committee=createFrozenCommittee(new SubwordEncoder(3).save());
  const records=[{id:'a',text:'subtract difference operands',strategy:'add:sub'},
    {id:'b',text:'subtract difference operands lesson',strategy:'add:sub'},
    {id:'c',text:'upper bound maximum positive',strategy:'min:max'}];
  for(const mode of ['lexical','swb1','committee']){
    const r=committee.select({query:'subtract operands',records,budget:18,mode});
    assert.ok(r.cost<=18);assert.equal(new Set(r.ids).size,r.ids.length);
    assert.equal(r.cost,r.ids.reduce((n,id)=>n+contextCost(records.find(x=>x.id===id).text),0));
  }
  assert.throws(()=>committee.select({query:'probe',records:[records[0],records[0]]}),/CONTEXT_INPUT/);
  const compact=committee.select({query:records[0].text,records:[records[0]],budget:18});
  assert.equal(compact.route,'COMPACT_AGREEMENT');assert.deepEqual(compact.ids,['a']);
  const tiedRecords=records.map(r=>r.id==='b'?{...r,text:records[0].text}:r);
  const diverse=committee.select({query:'subtract operands',records:tiedRecords,budget:18});
  assert.equal(diverse.route,'DIVERSE_UNCERTAINTY');assert.ok(diverse.ids.includes('c'));
});

test('restricted numeric proposal passes the existing actual validator and grants no authority',()=>{
  const proposal=numericResearchCandidate(['add','x','y'],['abs',['sub','x','y']]);
  assert.equal(isLocalNumericPatch(proposal.edits[0]),true);
  assert.equal(proposal.verifiedByCanonicalRepairGate,false);assert.equal(proposal.autoApplyAuthorized,false);
  assert.deepEqual(patchOperator(['max',2,['max',-2,'x']],{path:[],from:'max',to:'min'}),['min',2,['max',-2,'x']]);
});

test('report executes all three prototypes with deterministic raw outcomes and no promotion',()=>{
  const a=runCapabilityResearch(),b=runCapabilityResearch();
  assert.equal(a.reportHash,b.reportHash);assert.equal(canonical(a),canonical(b));
  assert.equal(a.synthesis.rows.length,15);assert.equal(a.memory.rows.length,15);assert.equal(a.context.rows.length,15);
  assert.equal(a.memory.integrity.acceptedAttacks,0);assert.equal(a.memory.integrity.splitAliasRejected,true);
  assert.equal(a.memory.integrity.staleContextUsedRevokedHint,false);
  assert.equal(a.integration.automaticallyAcceptedWrongRepairs,0);
  assert.equal(a.context.model.weightsChanged,false);assert.equal(a.protocol.maxAcquisitions,PROTOCOL.maxAcquisitions);
  assert.equal(a.promotionEligible,false);assert.equal(a.splitManifest.holdoutUsedForFitting,false);
});
