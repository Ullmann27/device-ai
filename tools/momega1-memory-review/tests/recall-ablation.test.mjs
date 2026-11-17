// Boundary-only synthetic recall control review; no model or repair is executed.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {setup} from './ablation-fixture.mjs';
import {url,api,envelope} from './fixture.mjs';
const {runCodingTask}=await import(url('coding-orchestrator.mjs'));
function active(t){
 const f=setup(t);fs.writeFileSync(path.join(f.stateDir,'ccc1-runtime.key'),f.key);
 const old=f.current.journal.entries()[0];fs.unlinkSync(f.current.journal.file);
 const {hash,prevHash,mac,...goal}=old;goal.deadline=Date.now()+3600000;goal.maxMemoryBytes=2000000000;
 f.current.journal.append(goal);f.current.binding={...f.current.binding,deadline:goal.deadline,maxMemoryBytes:goal.maxMemoryBytes};
 const recall=api.recallTechnicalExperiences({...f.query,now:Date.now()});assert.equal(recall.items.length,1,'positive eligible ancestry control');
 return f;
}
test('A01 nonBoolean toggle refuses before any state access',async()=>{for(const technicalRecallEnabled of [null,0,1,'false',{},[]])await assert.rejects(runCodingTask({project:'/nonexistent-review-project',stateDir:'/nonexistent-review-state',id:'invalid',technicalRecallEnabled}),/CODING_RECALL_OPTION_INVALID/);});
for(const mode of ['default','true','false'])test('A02 '+mode+' controls actual providerFactory context on identical synthetic history',async t=>{
 const f=active(t),before=fs.readFileSync(f.file),calls=[];
 const options={project:f.project,stateDir:f.stateDir,id:f.current.task.id,env:{},providerFactory:input=>{calls.push(input);throw Error('REVIEW_STOP_AT_PROVIDER_BOUNDARY');}};
 if(mode!=='default')options.technicalRecallEnabled=mode==='true';
 await assert.rejects(runCodingTask(options),/REVIEW_STOP_AT_PROVIDER_BOUNDARY/);
 assert.equal(calls.length,1);assert.equal(calls[0].task.sourceCommit,f.newSha);
 if(mode==='false')assert.equal(calls[0].technicalRecall,'');else{assert.match(calls[0].technicalRecall,/GIT_ANCESTOR/);assert.match(calls[0].technicalRecall,new RegExp(f.oldSha));assert.match(calls[0].technicalRecall,new RegExp(f.newSha));assert.match(calls[0].technicalRecall,/no permission/);}
 assert.deepEqual(fs.readFileSync(f.file),before,'recall toggle must not mutate historical ledger before provider');
});
for(const enabled of [true,false])test('A03 returned recall count reflects '+enabled+' on eligible history',async t=>{
 const f=active(t);f.current.journal.append(envelope({binding:f.current.binding,sender:'REASONER',kind:'ABSTAIN',sequence:1,payload:{reason:'Synthetic terminal control; no repair executed'}}));
 const result=await runCodingTask({project:f.project,stateDir:f.stateDir,id:f.current.task.id,env:{},technicalRecallEnabled:enabled,providerFactory:()=>{throw Error('terminal control must never invoke provider');}});
 assert.equal(result.technicalRecallCount,enabled?1:0);assert.equal(result.status,'ABSTAINED');assert.equal(result.approvedForMerge,false);assert.equal(result.neuralTraining,false);
});
