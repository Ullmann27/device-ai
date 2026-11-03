import test from 'node:test';
import assert from 'node:assert/strict';
import {executeTypedBridge} from './continuum-typed-coding-bridge.mjs';
test('MΩ Android run selects admitted local-model observation gate',async()=>{
 let params;
 const r=await executeTypedBridge({op:'run',id:'momega-owner',hash:'a'.repeat(64)},{
 isolationLoader:()=>({testOnly:true}),run:async p=>{params=p;throw Error('CODING_LOCAL_MODEL_OBSERVATION_REQUIRED');}});
 assert.equal(params.requireIsolation,true);assert.equal(params.requireLocalModelObservation,true);
 assert.equal(r.status,'BLOCKED');assert.equal(r.failure.code,'CODING_LOCAL_MODEL_OBSERVATION_REQUIRED');
});
