import test from 'node:test';
import assert from 'node:assert/strict';
import {emptyLedger,recordSession,verifyLedger} from './repair-ledger.mjs';

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

test('unverified session cannot poison history through claimed failed alternatives',()=>{
 const pending=report();
 pending.attempts.unshift({
  candidate:'3'.repeat(16),diagnostic:'2'.repeat(16),
  diagnosticKind:'JS_UNRESOLVED',strategy:'JS_MISSING_IMPORT',
  outcome:'FAILED_TESTS',edits:[],
  tests:[{profile:'node-continuum',passed:false,tests:{
   total:5,executed:5,failed:1,skipped:0
  }}]
 });
 const current=recordSession(emptyLedger(),pending,{trigger:'local-teamleader'});
 assert.equal(current.sessions[0].outcome,'PENDING_INDEPENDENT_VERIFICATION');
 assert.equal(current.attempts.length,2,'audit still records failed trial and proposal');
 assert.equal(current.experiences.length,0,'neither label allowed before independent verification');
 const confirmed=recordSession(emptyLedger(),pending,{independentPass:true});
 assert.equal(confirmed.experiences.length,2);
});

test('invalid syntax is retained as negative repair experience without fabricated test execution',()=>{
 const source=report();
 source.outcome='NO_VERIFIED_REPAIR';source.verified=null;
 source.attempts[0].outcome='FAILED_SYNTAX';
 source.attempts[0].syntax={passed:false,checked:[],
  failedFile:'.github/continuum/math.mjs',reason:'INVALID_JS_SYNTAX'};
 delete source.attempts[0].tests;
 const result=recordSession(emptyLedger(),source);
 assert.equal(result.attempts.length,1);
 assert.equal(result.attempts[0].outcome,'FAILED_SYNTAX');
 assert.deepEqual(result.attempts[0].tests,[]);
 assert.equal(result.attempts[0].syntaxReason,'INVALID_JS_SYNTAX');
 assert.equal(result.attempts[0].patchSha256,null);
 assert.deepEqual(result.experiences,[{
  kind:'JS_UNRESOLVED|JS_TYPO_REFERENCE',successes:0,failures:1
 }]);
 assert.deepEqual(verifyLedger(JSON.parse(JSON.stringify(result))),result);
});

test('syntax checker errors remain audit evidence without becoming failed-strategy labels',()=>{
 const source=report();
 source.outcome='NO_VERIFIED_REPAIR';source.verified=null;
 source.attempts[0].outcome='SYNTAX_CHECK_ERROR';
 source.attempts[0].syntaxReason='INVALID_CHECK_REQUEST';
 delete source.attempts[0].tests;
 const result=recordSession(emptyLedger(),source);
 assert.equal(result.attempts.length,1);
 assert.equal(result.attempts[0].outcome,'SYNTAX_CHECK_ERROR');
 assert.equal(result.attempts[0].syntaxReason,'INVALID_CHECK_REQUEST');
 assert.deepEqual(result.attempts[0].tests,[]);
 assert.deepEqual(result.experiences,[],'checker infrastructure errors do not condemn a strategy');
 source.attempts[0].outcome='FAILED_SYNTAX';
 source.attempts[0].syntax={passed:false,checked:[],
  failedFile:'.github/continuum/math.mjs',reason:'SYNTAX_CHECK_INTERRUPTED'};
 const interrupted=recordSession(emptyLedger(),source);
 assert.equal(interrupted.attempts[0].syntaxReason,'SYNTAX_CHECK_INTERRUPTED');
 assert.deepEqual(interrupted.experiences,[],'interrupted parsing says nothing about syntax validity');
});

test('an edit too large for the ledger is recorded by digest, keeps the session record and is never replayed',async()=>{
 const {proposeCandidates}=await import('./repair-strategies.mjs');
 const crypto=await import('node:crypto');
 const big='x'.repeat(15000);
 const source=report();
 // e.g. a whole-region revert of a large Kotlin file, independently verified
 source.attempts[0].edits=[{file:'app/src/main/java/Big.kt',oldText:big,newText:big+'y',line:3}];
 const result=recordSession(emptyLedger(),source,{trigger:'accepted-owner-task',independentPass:true});
 const [edit]=result.attempts[0].edits;
 assert.equal(result.attempts[0].outcome,'VERIFIED');
 assert.equal(result.sessions[0].outcome,'VERIFIED');
 assert.deepEqual(edit,{file:'app/src/main/java/Big.kt',oldText:'',newText:'',line:3,omitted:{
  oldSha256:crypto.createHash('sha256').update(big).digest('hex'),
  newSha256:crypto.createHash('sha256').update(big+'y').digest('hex'),oldLength:15000,newLength:15001}});
 assert.equal(source.attempts[0].edits[0].oldText,big,'the session report keeps the full edit');
 // a file whose two characters an empty hunk would match exactly once: the replay must still not happen
 const replays=proposeCandidates([{fingerprint:'2'.repeat(16),kind:'JS_UNRESOLVED'}],
  {readFile:()=>'ab',experience:result.attempts,jsSiblings:new Map(),changedInBase:[]});
 assert.equal(replays.some(c=>c.strategy==='EXPERIENCE_REPLAY'),false);
 // a small verified edit is still replayed as before
 const small=report();
 small.attempts[0].edits=[{file:'a.mjs',oldText:'ab',newText:'ac',line:1}];
 const kept=recordSession(emptyLedger(),small,{trigger:'accepted-owner-task',independentPass:true});
 assert.deepEqual(kept.attempts[0].edits,small.attempts[0].edits);
 assert.equal(proposeCandidates([{fingerprint:'2'.repeat(16),kind:'JS_UNRESOLVED'}],
  {readFile:()=>'ab',experience:kept.attempts,jsSiblings:new Map(),changedInBase:[]})
  .filter(c=>c.strategy==='EXPERIENCE_REPLAY').length,1);
 // an omission cannot smuggle text or wrong lengths past the ledger check
 const forged=structuredClone(result);
 forged.attempts[0].edits[0].oldText='x';
 assert.throws(()=>verifyLedger(forged),/Invalid ledger edits/);
 const short=structuredClone(result);
 short.attempts[0].edits[0].omitted.oldLength=1;short.attempts[0].edits[0].omitted.newLength=1;
 assert.throws(()=>verifyLedger(short),/Invalid ledger edits/);
});
