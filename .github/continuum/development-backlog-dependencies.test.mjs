import test from 'node:test';
import assert from 'node:assert/strict';
import * as B from './development-backlog.mjs';
import {makeDependencyDocument,setTaskState,deepFreeze,completeTaskOperations,sizes,modes}
  from '../../tools/tuning-workers-r2-20261104/fixtures.mjs';
import {positionCases} from '../../tools/tuning-workers-r2-20261104/position-cases.mjs';

const selectReference=doc=>doc.tasks.filter(task=>task.state==='READY'&&
  task.dependsOn.every(id=>doc.tasks.find(other=>other.id===id)?.state==='DONE')&&task.attempts<task.maxAttempts)
  .sort((a,b)=>b.priority-a.priority||a.createdAt-b.createdAt||(a.id<b.id?-1:1))[0]??null;

test('dependency selection retains exact task identity on frozen canonical documents up to 500 tasks',()=>{
  for(const size of sizes)for(const mode of modes){
    const doc=deepFreeze(makeDependencyDocument(size,mode));
    const before=B.canonical(doc);
    assert.strictEqual(B.nextEligible(doc),selectReference(doc),`${size}/${mode}`);
    assert.equal(B.canonical(doc),before);
  }
});

test('dependency lookups preserve the first, last and every power-of-two boundary up to 500 tasks',()=>{
  for(const {size,index,doc,selected} of positionCases()){
    assert.strictEqual(B.nextEligible(doc),selected,`${size}/${index}`);
    assert.strictEqual(B.nextEligible(doc),selectReference(doc),`${size}/${index}/reference`);
  }
});

test('unknown dependencies and missing claim targets remain rejected without changing a valid document',()=>{
  const doc=deepFreeze(makeDependencyDocument(20,'none')),before=B.canonical(doc);
  assert.throws(()=>B.applyOp(doc,{op:'propose',actor:'test',at:100,
    task:{id:'task-new',title:'Unknown dependency',kind:'TEST',source:'owner',dependsOn:['task-missing']}}),
    {code:'UNKNOWN_DEPENDENCY'});
  assert.throws(()=>B.applyOp(doc,{op:'claim',actor:'test',at:100,id:'task-missing',
    owner:'test',token:'d'.repeat(32),leaseMs:60_000}),{code:'NOT_FOUND'});
  assert.strictEqual(B.nextEligible(doc,null,null,null,'task-missing'),null);
  assert.equal(B.canonical(doc),before);
});

test('dependency states, attempts and replacement objects are reread on every selection call',()=>{
  const doc=makeDependencyDocument(20,'one-last');
  const dependency=doc.tasks.at(-1),selected=B.nextEligible(doc);
  assert.ok(selected);
  for(const state of ['PROPOSED','READY','IN_PROGRESS','VERIFYING','DONE']){
    setTaskState(dependency,state);B.validateDocument(doc);
    assert.strictEqual(B.nextEligible(doc),selectReference(doc),state);
    if(state==='DONE')assert.strictEqual(B.nextEligible(doc),selected);
  }
  selected.attempts=selected.maxAttempts;B.validateDocument(doc);
  assert.notStrictEqual(B.nextEligible(doc),selected);
  selected.attempts=0;
  doc.tasks[doc.tasks.length-1]=setTaskState(structuredClone(dependency),'PROPOSED');
  B.validateDocument(doc);
  assert.strictEqual(B.nextEligible(doc),null);
  doc.tasks[doc.tasks.length-1]=dependency;
  assert.strictEqual(B.nextEligible(doc),selected);
});

test('dependency state checks stop in declared dependency order and before the attempt budget',()=>{
  const doc=makeDependencyDocument(20,'sixteen-first-blocked');
  const ready=doc.tasks.find(task=>task.state==='READY');
  for(const task of doc.tasks)if(task!==ready&&task.state==='READY')setTaskState(task,'PROPOSED');
  ready.attempts=ready.maxAttempts;
  B.validateDocument(doc);
  const reads=[];
  for(const id of ready.dependsOn){
    const task=doc.tasks.find(t=>t.id===id),state=task.state;
    Object.defineProperty(task,'state',{enumerable:true,get(){reads.push(id);return state;}});
  }
  assert.strictEqual(B.nextEligible(doc),null);
  // Top-level READY checks read each state once; the additional read belongs to
  // the first blocked dependency, despite the selected task's exhausted budget.
  const counts=new Map();for(const id of reads)counts.set(id,(counts.get(id)??0)+1);
  assert.equal(counts.get(ready.dependsOn[0]),2);
  for(const id of ready.dependsOn.slice(1))assert.equal(counts.get(id),1);
});

test('out-of-order proposals, idempotent proposals and completion remain sorted before a batch claimNext',()=>{
  const propose=(id,extra={})=>({op:'propose',actor:'test',at:1,
    task:{id,title:id,kind:'TEST',source:'owner',...extra}});
  const ops=[propose('task-zulu'),propose('task-alpha',{priority:1000,dependsOn:['task-zulu']}),
    propose('task-middle'),{...propose('task-zulu'),ifAbsent:true},
    {op:'accept',actor:'test',at:2,id:'task-zulu'},
    {op:'accept',actor:'test',at:2,id:'task-alpha'},
    ...completeTaskOperations('task-zulu'),
    {op:'claimNext',actor:'test',at:20,owner:'test',token:'d'.repeat(32),leaseMs:60_000,kinds:['TEST']}];
  const result=B.applyOps(B.emptyDocument(0,'test'),ops);
  assert.ok(result.results.every(r=>r.ok));
  assert.deepEqual(result.doc.tasks.map(t=>t.id),['task-alpha','task-middle','task-zulu']);
  assert.equal(result.results.at(-1).id,'task-alpha');
  assert.equal(result.doc.tasks[0].state,'IN_PROGRESS');
  assert.equal(result.doc.tasks[0].attempts,1);
});

test('migrated findings can be dependencies without changing claim error precedence or evidence checks',()=>{
  const v1={schemaVersion:1,tasks:[
    {kind:'UNREFERENCED',symbol:'Zulu',file:'main/Z.kt'},
    {kind:'DEAD_CLUSTER',symbol:'Alpha',file:'main/A.kt'}
  ]};
  let doc=B.migrateV1(v1,{at:1,actor:'test'});
  const dependency=doc.tasks.at(-1).id;
  doc=B.applyOps(doc,[
    {op:'propose',actor:'test',at:2,task:{id:'task-dependent',title:'Depends on migrated task',kind:'TEST',source:'owner',dependsOn:[dependency]}},
    {op:'accept',actor:'test',at:3,id:'task-dependent'},
    {op:'accept',actor:'test',at:3,id:dependency}
  ]).doc;
  const claim={op:'claim',actor:'test',at:10,id:'task-dependent',owner:'test',token:'d'.repeat(32),leaseMs:60_000};
  const dependent=doc.tasks.find(t=>t.id==='task-dependent');
  dependent.attempts=dependent.maxAttempts;B.validateDocument(doc);
  assert.throws(()=>B.applyOp(doc,claim),{code:'DEPENDENCY_NOT_DONE',message:'DEPENDENCY_NOT_DONE: task-dependent depends on unfinished tasks'});
  doc=B.applyOps(doc,completeTaskOperations(dependency)).doc;
  assert.throws(()=>B.applyOp(doc,claim),{code:'BUDGET_EXHAUSTED',message:'BUDGET_EXHAUSTED: task-dependent used 3 of 3 attempts'});
  doc.tasks.find(t=>t.id==='task-dependent').attempts=0;
  const claimed=B.applyOp(doc,claim).doc;
  assert.equal(claimed.tasks.find(t=>t.id==='task-dependent').state,'IN_PROGRESS');
  assert.throws(()=>B.applyOp(claimed,{op:'complete',actor:'test',at:20,id:'task-dependent',evidence:{}}),{code:'INVALID_TRANSITION'});
});
