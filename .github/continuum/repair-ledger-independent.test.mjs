import test from 'node:test';
import assert from 'node:assert/strict';
import {emptyLedger,recordSession} from './repair-ledger.mjs';

function report(){
 return {
  schemaVersion:1,base:'a'.repeat(40),chain:'node',
  outcome:'VERIFIED',finishedAt:'2026-10-16T00:00:00.000Z',
  baseline:{passed:false,diagnostics:[{message:'failed'}]},
  verified:{strategy:'JS_TYPO_REFERENCE',patchSha256:'f'.repeat(64)},
  attempts:[{
   candidate:'1'.repeat(16),diagnostic:'2'.repeat(16),
   diagnosticKind:'JS_UNRESOLVED',strategy:'JS_TYPO_REFERENCE',
   outcome:'VERIFIED',edits:[],score:0.8,
   tests:[{profile:'node-continuum',passed:true,tests:{
    total:5,executed:5,failed:0,skipped:0
   }}]
  }]
 };
}

test('failed independent patch check records a negative learning signal without fake success',()=>{
 const source=report();
 const result=recordSession(emptyLedger(),source,{
  trigger:'accepted-owner-task',independentPass:false
 });
 assert.equal(result.sessions.length,1);
 assert.equal(result.sessions[0].outcome,'NO_VERIFIED_REPAIR');
 assert.equal(result.sessions[0].verifiedStrategy,null);
 assert.equal(result.sessions[0].patchSha256,null);
 assert.equal(result.attempts[0].outcome,'INSUFFICIENT_EVIDENCE');
 assert.equal(result.attempts[0].patchSha256,null);
 assert.deepEqual(result.experiences,[{
  kind:'JS_UNRESOLVED|JS_TYPO_REFERENCE',successes:0,failures:1
 }]);
 assert.equal(source.outcome,'VERIFIED','diagnostic report remains unmodified');
 assert.equal(source.attempts[0].outcome,'VERIFIED');
});

test('independently verified patch keeps legitimate positive experience',()=>{
 const result=recordSession(emptyLedger(),report(),{
  trigger:'accepted-owner-task',independentPass:true
 });
 assert.equal(result.sessions[0].outcome,'VERIFIED');
 assert.equal(result.sessions[0].patchSha256,'f'.repeat(64));
 assert.equal(result.attempts[0].outcome,'VERIFIED');
 assert.deepEqual(result.experiences,[{
  kind:'JS_UNRESOLVED|JS_TYPO_REFERENCE',successes:1,failures:0
 }]);
});

test('other legacy repair ledger calls preserve existing semantics',()=>{
 const result=recordSession(emptyLedger(),report(),{trigger:'local-teamleader'});
 assert.equal(result.sessions[0].outcome,'VERIFIED');
 assert.equal(result.experiences[0].successes,1);
});
