import test from 'node:test';
import assert from 'node:assert/strict';

const task={schema:'continuum-coding-task',version:1,id:'repair-interval',sourceCommit:'a'.repeat(40),
 kind:'BUGFIX',goal:'Repair reversed interval calculation',allowedPaths:['lib/interval.mjs'],
 acceptance:['Reversed inputs produce the same distance'],chain:'node',limits:{maxModelCalls:3,maxContextChars:12000}};

test('typed task pins scope, source, acceptance and resource bounds',async()=>{
 const {validateCodingTask,taskFingerprint}=await import('./coding-task.mjs');
 const valid=validateCodingTask(task);
 assert.equal(Object.isFrozen(valid.allowedPaths),true);
 assert.equal(taskFingerprint(valid),taskFingerprint({...task,allowedPaths:[...task.allowedPaths]}));
 assert.notEqual(taskFingerprint(valid),taskFingerprint({...task,sourceCommit:'b'.repeat(40)}));
 for(const change of [{acceptance:[]},{allowedPaths:['../owner.key']},{allowedPaths:['scripts/worker.mjs']},
  {allowedPaths:['.github/continuum/ccc1-worker.mjs']},{allowedPaths:['app/src/test/Spec.kt']},
  {allowedPaths:['lib/a.mjs','lib/b.mjs','lib/c.mjs','lib/d.mjs']},{limits:{maxModelCalls:4,maxContextChars:12000}},
  {sourceCommit:'main'},{kind:'RUN_SHELL'},{command:'rm -rf /'},{chain:'android'}])
  assert.throws(()=>validateCodingTask({...task,...change}));
});

test('structured proposal is data only and cannot widen task scope',async()=>{
 const {validateCodingProposal}=await import('./coding-task.mjs');
 const p={plan:['Locate the interval error','Run the fixed test chain'],hypothesis:'The sign is reversed',
  patches:[{file:'lib/interval.mjs',oldText:'a-b',newText:'a>b?a-b:b-a'}]};
 assert.equal(validateCodingProposal(p,task).patches.length,1);
 for(const change of [{patches:[{file:'lib/other.mjs',oldText:'a',newText:'b'}]},
  {command:'node anything'},{plan:[]},{patches:[p.patches[0],p.patches[0]]},
  {patches:[{...p.patches[0],oldText:''}]}])assert.throws(()=>validateCodingProposal({...p,...change},task));
});

test('autonomous candidates cannot modify the new Harness or encrypted-storage control boundary',async()=>{
 const {isCodingPath}=await import('./coding-task.mjs');const {critique}=await import('./repair-critic.mjs');
 for(const file of ['.github/continuum/harness-coding-provider.mjs','.github/continuum/storage-fabric.mjs','.github/continuum/storage-fabric-catalog.mjs']){
  assert.equal(isCodingPath(file),false,file);
  assert.equal(critique({strategy:'LOCAL_SCOPED_PATCH',edits:[{file,oldText:'const flag = false;',newText:'const flag = true;'}]}).approved,false,file);
 }
});
