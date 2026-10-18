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

test('unverified teamleader calls never create positive or negative learning labels',()=>{
 const source=report();
 for(const meta of [{trigger:'local-teamleader'},{independentPass:null},
                    {independentPass:'true'}]){
  const result=recordSession(emptyLedger(),source,meta);
  assert.equal(result.sessions[0].outcome,'PENDING_INDEPENDENT_VERIFICATION');
  assert.equal(result.sessions[0].verifiedStrategy,null);
  assert.equal(result.sessions[0].patchSha256,null);
  assert.equal(result.attempts[0].outcome,'NOT_APPLICABLE');
  assert.equal(result.attempts[0].patchSha256,null);
  assert.equal(result.experiences.length,0,'unknown must not become training data');
 }
 assert.equal(source.attempts[0].outcome,'VERIFIED','original report preserved');
});

test('explicit independent rejection is negative, not an unknown outcome',()=>{
 const result=recordSession(emptyLedger(),report(),{independentPass:false});
 assert.equal(result.sessions[0].outcome,'NO_VERIFIED_REPAIR');
 assert.equal(result.attempts[0].outcome,'INSUFFICIENT_EVIDENCE');
 assert.deepEqual(result.experiences,[{
  kind:'JS_UNRESOLVED|JS_TYPO_REFERENCE',successes:0,failures:1
 }]);
});

test('non-verified sessions retain their original failure diagnoses',()=>{
 const source=report();
 source.outcome='NO_VERIFIED_REPAIR';
 source.verified=null;
 source.attempts[0].outcome='FAILED_TESTS';
 const result=recordSession(emptyLedger(),source,{});
 assert.equal(result.sessions[0].outcome,'NO_VERIFIED_REPAIR');
 assert.equal(result.attempts[0].outcome,'FAILED_TESTS');
 assert.equal(result.experiences[0].failures,1);
});
