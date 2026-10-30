import test from 'node:test';
import assert from 'node:assert/strict';
import {claimReservedExecution,verifyPersistedClaim} from './continuum-nvidia-execution-claim.mjs';
const t0=Date.parse('2026-10-30T22:00:00Z');
const blank=()=>({schema:'continuum-nvidia-schedule-ledger',version:1,reservations:[
 {runId:'100',sequence:0,atMillis:t0,utcDate:'2026-10-30'}]});
test('RED: mismatched run or sequence cannot claim execution',()=>{
 assert.throws(()=>claimReservedExecution(blank(),{runId:'101',sequence:0,nowMs:t0+1}),/RESERVATION_NOT_FOUND/);
 assert.throws(()=>claimReservedExecution(blank(),{runId:'100',sequence:1,nowMs:t0+1}),/RESERVATION_NOT_FOUND/);
});
test('GREEN: matching run is claimed without mutating original ledger',()=>{
 const x=blank(),y=claimReservedExecution(x,{runId:'100',sequence:0,nowMs:t0+1});
 assert.equal(y.reservations[0].claimedAtMillis,t0+1);
 assert.equal(x.reservations[0].claimedAtMillis,undefined);
});
test('RED: repeated claim fails closed',()=>{
 const x=claimReservedExecution(blank(),{runId:'100',sequence:0,nowMs:t0+1});
 assert.throws(()=>claimReservedExecution(x,{runId:'100',sequence:0,nowMs:t0+2}),/RESERVATION_ALREADY_CLAIMED/);
});
test('RED: future and stale reservations cannot authorize model',()=>{
 assert.throws(()=>claimReservedExecution(blank(),{runId:'100',sequence:0,nowMs:t0-1}),/RESERVATION_EXPIRED/);
 assert.throws(()=>claimReservedExecution(blank(),{runId:'100',sequence:0,nowMs:t0+30*60_000+1}),/RESERVATION_EXPIRED/);
});
test('RED: malformed claim metadata is rejected',()=>{
 assert.throws(()=>claimReservedExecution(blank(),{runId:'x',sequence:0,nowMs:t0}),/EXECUTION_CLAIM_INVALID/);
});

test('GREEN: exact persisted claim readback authorizes continuation',()=>{
 const ledger=claimReservedExecution(blank(),{runId:'100',sequence:0,nowMs:t0+1});
 assert.equal(verifyPersistedClaim(ledger,{runId:'100',sequence:0,claimedAtMillis:t0+1}),true);
});
test('RED: missing, stale, mismatched and altered claim readback fail closed',()=>{
 const args={runId:'100',sequence:0,claimedAtMillis:t0+1};
 const ledger=claimReservedExecution(blank(),{runId:'100',sequence:0,nowMs:t0+1});
 for(const item of [blank(),null,{...ledger,reservations:[]},
   {...ledger,reservations:[{...ledger.reservations[0],claimedAtMillis:t0+2}]}])
   assert.throws(()=>verifyPersistedClaim(item,args),/GITHUB_LEDGER_CLAIM_UNVERIFIED/);
 assert.throws(()=>verifyPersistedClaim(ledger,{...args,runId:'101'}),/GITHUB_LEDGER_CLAIM_UNVERIFIED/);
});
