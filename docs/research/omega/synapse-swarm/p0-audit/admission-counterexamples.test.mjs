// Independent integration checks; historical C03 is retained unchanged separately.
import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const root=process.env.SYNAPSE_P0_TARGET;
if(!root)throw Error('SYNAPSE_P0_TARGET must identify the exact frozen source checkout');
const runtime=await import(pathToFileURL(path.join(root,'.github/continuum/coding-provider.mjs')));
const verifier=await import(pathToFileURL(path.join(root,'.github/continuum/ccc1-redgreen-verifier.mjs')));
const {assertCodingModelObservationBinding}=await import(pathToFileURL(path.join(root,'.github/continuum/coding-orchestrator.mjs')));
const fabricated={schema:'momega1-local-process-observation-v1',evidenceClass:'LOCAL_MODEL_HOST',verifiedModelExecution:true,exitCode:0,pid:1000,outputTokens:100,elapsedMs:1};
test('public observation predicate never grants private producer identity to fabricated or frozen data',()=>{
 assert.equal(typeof runtime.isTrustedLocalModelObservation,'function');
 for(const value of [null,undefined,true,0,'trusted',{},fabricated,Object.freeze({...fabricated}),structuredClone(fabricated),Object.create(fabricated)]){
  assert.equal(runtime.isTrustedLocalModelObservation(value),false);
  assert.throws(()=>assertCodingModelObservationBinding({raw:{trace:{modelObservation:value}}}),/CODING_LOCAL_MODEL_OBSERVATION_REQUIRED/);
 }
});
test('public verifier predicate never grants independent execution identity to copied claimed success',()=>{
 assert.equal(typeof verifier.isTrustedCodingVerification,'function');
 const claim={schema:'momega1-coding-verification',version:1,evidenceClass:'HOST_INTEGRATION',verdict:'VERIFIED',red:{exitCode:1},green:{exitCode:0}};
 for(const value of [null,undefined,true,{},claim,Object.freeze({...claim}),structuredClone(claim),Object.create(claim)])
  assert.equal(verifier.isTrustedCodingVerification(value),false);
});
