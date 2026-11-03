/** Independent cancellation after observing the exact real native executable. */
import fs from 'node:fs';import {createHash,randomBytes} from 'node:crypto';import assert from 'node:assert/strict';
import {queryPinnedLocalCoding} from '/source/scripts/continuum-local-model-observation.mjs';
const userPrompt='Repair export const midpoint = (left, right) => left + right; to return the arithmetic midpoint. File lib/batches.mjs.';
const expected=fs.statSync(process.env.CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH);const scan=()=>fs.readdirSync('/proc').filter(x=>/^\d+$/.test(x)).filter(x=>{try{let s=fs.statSync('/proc/'+x+'/exe');return s.dev===expected.dev&&s.ino===expected.ino;}catch{return false;}});
const controller=new AbortController();let observed=[],cancelAt=null;const start=Date.now();
const timer=setInterval(()=>{const pids=scan();if(pids.length&&cancelAt===null){observed=pids;cancelAt=Date.now();controller.abort();}},100);
const safety=setTimeout(()=>controller.abort(),15000);let code=null,result=null;
try{result=await queryPinnedLocalCoding({userPrompt,allowedTrackedPaths:['lib/batches.mjs'],timeoutMs:30000,signal:controller.signal,observationKey:randomBytes(32).toString('hex'),binding:{executionId:randomBytes(32).toString('hex'),taskId:'independent-7b-cancel',sourceCommit:'c'.repeat(40),taskFingerprint:'d'.repeat(64),contextSha256:'e'.repeat(64),promptSha256:createHash('sha256').update(userPrompt).digest('hex'),requestIndex:1}});}catch(e){code=e.message;}finally{clearInterval(timer);clearTimeout(safety);}
const remaining=scan();const receipt={classification:'ACTUAL_NATIVE_CANCEL_CONTROL_NOT_SUCCESSFUL_INFERENCE',observedPinnedRuntimePids:observed,cancelAfterMs:cancelAt===null?null:cancelAt-start,elapsedMs:Date.now()-start,errorCode:code,resultReturned:result!==null,remainingPinnedRuntimePids:remaining};console.log(JSON.stringify(receipt));assert.ok(observed.length>0);assert.equal(code,'CODING_CANCELLED');assert.equal(result,null);assert.equal(remaining.length,0);
