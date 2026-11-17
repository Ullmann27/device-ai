import test from 'node:test';
import assert from 'node:assert/strict';
import {createCcc1BotAssignment,assertCcc1BotAssignment} from './ccc1-bot-assignment.mjs';
import {runCodingTask} from './coding-orchestrator.mjs';

const task={schema:'continuum-coding-task',version:1,id:'bot-task',sourceCommit:'a'.repeat(40),kind:'BUGFIX',
 goal:'Repair the controlled source fixture',allowedPaths:['src/example.mjs'],acceptance:['The fixed tests pass'],
 chain:'node',limits:{maxModelCalls:1,maxContextChars:2048}};
const taskId='t-'+'b'.repeat(16);
function setup(){
 const profiles=new Map(['bot-forge','bot-lint'].map(agentId=>[agentId,{agentId,workspaceId:'workspace-'+agentId,displayName:agentId,
  resourceBudget:{maxModelCalls:1,deadlineMillis:120_000}}]));
 const store={get:id=>profiles.get(id)};
 const assignment=createCcc1BotAssignment({task,ccc1TaskId:taskId,builderId:'bot-forge',verifierId:'bot-lint',profileStore:store});
 return {profiles,store,assignment};
}
const check=(assignment,stage='BUILD',spec=task)=>assertCcc1BotAssignment(assignment,{task:spec,ccc1TaskId:taskId,stage});

test('existing profile port selects distinct immutable identities without changing task budget',()=>{
 const {assignment}=setup();
 assert.equal(check(assignment).agentId,'bot-forge');
 assert.equal(check(assignment).persistentWorkspaceId,'workspace-bot-forge');
 assert.equal(check(assignment,'VERIFY').agentId,'bot-lint');
 assert.ok(Object.isFrozen(assignment)&&Object.isFrozen(assignment.builder)&&Object.isFrozen(assignment.verifier));
 assert.equal(task.limits.maxModelCalls,1);
 assert.throws(()=>{assignment.builder.agentId='bot-lint';},TypeError);
});
test('copied JSON and unregistered identities cannot acquire assignment origin',()=>{
 const {assignment,store}=setup();
 assert.throws(()=>check(JSON.parse(JSON.stringify(assignment))),/TRUSTED_ORIGIN/);
 assert.throws(()=>createCcc1BotAssignment({task,ccc1TaskId:taskId,builderId:'bot-missing',verifierId:'bot-lint',profileStore:store}),/PROFILE_UNAVAILABLE/);
});
test('same builder/verifier and unsupported stage are rejected',()=>{
 const {assignment,store}=setup();
 assert.throws(()=>createCcc1BotAssignment({task,ccc1TaskId:taskId,builderId:'bot-forge',verifierId:'bot-forge',profileStore:store}),/INDEPENDENT_PRINCIPAL/);
 assert.throws(()=>check(assignment,'MERGE'),/STAGE_INVALID/);
});
test('prepared task fingerprint and canonical task cannot be exchanged',()=>{
 const {assignment}=setup();
 assert.throws(()=>check(assignment,'BUILD',{...task,goal:'A different request'}),/TASK_MISMATCH/);
 assert.throws(()=>assertCcc1BotAssignment(assignment,{task,ccc1TaskId:'t-'+'c'.repeat(16),stage:'BUILD'}),/TASK_MISMATCH/);
});
test('rename retains identity; removal and workspace change invalidate current association',()=>{
 const {assignment,profiles}=setup();
 profiles.get('bot-forge').displayName='Renamed';assert.equal(check(assignment).agentId,'bot-forge');
 profiles.get('bot-lint').workspaceId='workspace-bot-forge';assert.throws(()=>check(assignment),/PROFILE_UNAVAILABLE/);
 profiles.delete('bot-lint');assert.throws(()=>check(assignment),/PROFILE_UNAVAILABLE/);
});
test('replacing the external store method cannot substitute the admitted reader',()=>{
 const {assignment,store,profiles}=setup();
 store.get=id=>({agentId:id,workspaceId:'workspace-'+id});
 profiles.delete('bot-forge');assert.throws(()=>check(assignment),/PROFILE_UNAVAILABLE/);
});
test('profile model cap restricts task and a later budget reduction revokes the association',()=>{
 const {assignment,store,profiles}=setup();
 assert.throws(()=>createCcc1BotAssignment({task:{...task,limits:{...task.limits,maxModelCalls:2}},ccc1TaskId:taskId,
  builderId:'bot-forge',verifierId:'bot-lint',profileStore:store}),/MODEL_BUDGET_EXCEEDED/);
 profiles.get('bot-forge').resourceBudget.maxModelCalls=0;
 assert.throws(()=>check(assignment),/PROFILE_BUDGET_CHANGED/);
});
test('bot execution requires actual admitted isolation before reading journals or invoking provider',async()=>{
 const {assignment}=setup();let calls=0;
 await assert.rejects(runCodingTask({project:'/nonexistent',stateDir:'/nonexistent',id:task.id,botAssignment:assignment,
  providerFactory:()=>{calls++;throw Error('must not run');}}),/CODING_ISOLATION_REQUIRED/);
 assert.equal(calls,0);
});
