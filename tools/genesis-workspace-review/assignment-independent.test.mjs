// Independent host contract probes. The profile port is deliberately synthetic;
// actual persisted-profile/container proof is a separate integration test.
import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const root=process.env.GENESIS_REVIEW_TARGET;
assert.ok(root,'exact candidate checkout required');
const {createCcc1BotAssignment:create,assertCcc1BotAssignment:admit}=
  await import(pathToFileURL(path.join(root,'.github/continuum/ccc1-bot-assignment.mjs')));
const canonicalId='t-'+'1'.repeat(16);
function fixture(){
 const task={schema:'continuum-coding-task',version:1,id:'review-task',sourceCommit:'a'.repeat(40),kind:'BUGFIX',
  goal:'Bounded software contract test',allowedPaths:['lib/example.mjs'],acceptance:['Contract passes'],chain:'node',
  limits:{maxModelCalls:1,maxContextChars:2048}};
 const values=new Map(['bot-forge','bot-lint'].map(agentId=>[agentId,{agentId,workspaceId:'workspace-'+agentId,
  resourceBudget:{maxModelCalls:1,deadlineMillis:1000}}]));
 const store={get:id=>values.get(id)};
 const assignment=create({task,ccc1TaskId:canonicalId,builderId:'bot-forge',verifierId:'bot-lint',profileStore:store});
 const check=(stage='BUILD',value=assignment)=>admit(value,{task,ccc1TaskId:canonicalId,stage});
 return {task,values,store,assignment,check};
}
test('both exact principals re-resolve and expose one shared immutable expiry',()=>{
 const f=fixture(),build=f.check(),verify=f.check('VERIFY');
 assert.equal(build.agentId,'bot-forge');assert.equal(verify.agentId,'bot-lint');
 assert.equal(build.expiresAt,verify.expiresAt);assert.ok(Object.isFrozen(build));
 assert.equal(f.task.limits.maxModelCalls,1);
});
test('expiry boundary blocks both stages rather than renewing execution time',()=>{
 const f=fixture(),realNow=Date.now;
 try{Date.now=()=>f.assignment.expiresAt;
  assert.throws(()=>f.check(),/EXPIRED/);assert.throws(()=>f.check('VERIFY'),/EXPIRED/);
 }finally{Date.now=realNow;}
});
test('mutating original task after authorization invalidates binding',()=>{
 const f=fixture();f.task.allowedPaths.push('lib/other.mjs');
 assert.throws(()=>f.check(),/TASK_MISMATCH/);
});
test('same fields in clone or prototype wrapper cannot reconstruct origin',()=>{
 const f=fixture();
 for(const value of [{...f.assignment},Object.create(f.assignment),JSON.parse(JSON.stringify(f.assignment))])
  assert.throws(()=>f.check('VERIFY',value),/TRUSTED_ORIGIN/);
});
test('removing verifier blocks builder before next profile checkpoint',()=>{
 const f=fixture();f.values.delete('bot-lint');assert.throws(()=>f.check(),/PROFILE_UNAVAILABLE/);
});
test('decreasing verifier lifetime invalidates both stages without changing old expiry',()=>{
 const f=fixture(),expiry=f.assignment.expiresAt;
 f.values.get('bot-lint').resourceBudget.deadlineMillis=1;
 assert.throws(()=>f.check(),/PROFILE_BUDGET_CHANGED/);
 assert.throws(()=>f.check('VERIFY'),/PROFILE_BUDGET_CHANGED/);
 assert.equal(f.assignment.expiresAt,expiry);
});
test('increasing a profile cap cannot extend the admitted assignment',()=>{
 const f=fixture(),expiry=f.assignment.expiresAt;
 f.values.get('bot-forge').resourceBudget.deadlineMillis=600000;
 f.values.get('bot-forge').resourceBudget.maxModelCalls=8;
 assert.equal(f.check().expiresAt,expiry);assert.equal(f.check().maxModelCalls,1);
});
test('reader replacement cannot revive a removed principal',()=>{
 const f=fixture(),old=f.values.get('bot-forge');
 f.store.get=()=>old;f.values.delete('bot-forge');
 assert.throws(()=>f.check(),/PROFILE_UNAVAILABLE/);
});
test('invalid current budget values fail closed',()=>{
 for(const value of [NaN,Infinity,-1,1.5,600001]){
  const f=fixture();f.values.get('bot-forge').resourceBudget.deadlineMillis=value;
  assert.throws(()=>f.check(),/PROFILE_BUDGET_UNAVAILABLE/);
 }
});
