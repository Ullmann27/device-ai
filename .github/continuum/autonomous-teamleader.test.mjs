import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {initialLeaderState,decodeLeaderState,selectTeamleaderAction,runTeamleaderCycle}
 from './autonomous-teamleader.mjs';
import {parseTeamleaderArgs} from './teamleader-cli.mjs';

const A='a'.repeat(40),B='b'.repeat(40);
function sandbox(fn){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-teamleader-test-'));
 try{return fn({root:path.join(dir,'source'),stateDir:path.join(dir,'state')});}
 finally{fs.rmSync(dir,{recursive:true,force:true});}
}
function fake(head=A){
 const calls={repairs:0,investigations:0,prepared:0};
 let pending=null;
 return {calls,setPending(t){pending=t},setHead(h){head=h},
  head:()=>head,
  readyInvestigation:()=>pending,
  prepareSafeInvestigation:()=>{calls.prepared++;return null},
  repair:()=>{calls.repairs++;return {outcome:'NO_FAILURE'}},
  investigate:(_r,_s,_h,id)=>{calls.investigations++;pending=null;return{status:'DONE',staticResult:'REPRODUCED_STATIC_FINDING',taskId:id}}
 };
}
test('zero-paid-service worker repairs/checks each immutable head once and then idles',()=>sandbox(({root,stateDir})=>{
 const driver=fake(),first=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(first.kind,'BASELINE_REPAIR');assert.equal(first.outcome,'NO_FAILURE');
 assert.equal(driver.calls.repairs,1);
 for(let i=0;i<30;i++)assert.equal(
  runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
 assert.equal(driver.calls.repairs,1,'same HEAD must never repeat fixed tests hourly');
 assert.equal(decodeLeaderState(fs.readFileSync(first.statePath)).events.length,2);
 driver.setHead(B);
 const next=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(next.outcome,'NO_FAILURE');assert.equal(driver.calls.repairs,2);
 assert.equal(decodeLeaderState(fs.readFileSync(first.statePath)).attemptedHeads.length,2);
}));
test('READY read-only INVESTIGATE is handled once; no write or deployment',()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 runTeamleaderCycle({root,stateDir,driver});
 driver.setPending({id:'inv-0123456789abcdef',kind:'INVESTIGATE',state:'READY'});
 const result=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(result.outcome,'DONE_INVESTIGATION');
 assert.equal(result.kind,'INVESTIGATE');
 assert.equal(driver.calls.investigations,1);
 assert.equal(driver.calls.repairs,1);
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
}));
test('verified repair is a PROPOSAL only; never described as applied',()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 driver.repair=()=>({outcome:'VERIFIED',verified:{strategy:'KOTLIN_MISSING_IMPORT'},
  artifact:path.join(stateDir,'reports','review.patch')});
 const r=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(r.outcome,'VERIFIED_PATCH_PENDING_REVIEW');
 assert.equal(r.artifact,path.join(stateDir,'reports','review.patch'));
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
}));
test('failed or interrupted work is preserved, never marked successful',()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 driver.repair=()=>{throw Error('offline compiler unavailable')};
 const r=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(r.outcome,'ERROR');
 assert.match(r.detail,/offline compiler unavailable/);
 const s=decodeLeaderState(fs.readFileSync(r.statePath));
 assert.equal(s.events.at(-1).outcome,'ERROR');
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
}));
test('tampered state, invalid accepted task, and an existing lock fail closed',()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 const first=runTeamleaderCycle({root,stateDir,driver});
 const p=first.statePath;
 const tampered=JSON.parse(fs.readFileSync(p,'utf8'));
 tampered.events[1].outcome='VERIFIED_PATCH_PENDING_REVIEW';
 fs.writeFileSync(p,JSON.stringify(tampered));
 assert.throws(()=>runTeamleaderCycle({root,stateDir,driver}),/Invalid or corrupted/);
 fs.rmSync(p);
 driver.setPending({id:'rep-0123456789abcdef',kind:'REPAIR',state:'READY'});
 // First cycle checks fresh HEAD; after this, invalid task must be rejected.
 assert.throws(()=>runTeamleaderCycle({root,stateDir,driver}),/Untrusted investigation task/);
 driver.setPending(null);
 fs.writeFileSync(p+'.lock','{"pid":100,"at":1}');
 assert.throws(()=>runTeamleaderCycle({root,stateDir,driver}),/already running/);
}));
test('CLI budget, command and retry flags are validated before execution',()=>{
 assert.deepEqual(parseTeamleaderArgs(['--once','--budget-minutes','5']),
  {once:true,status:false,retry:false,budgetMinutes:'5'});
 assert.throws(()=>parseTeamleaderArgs(['--once','--status']),/exactly one/);
 assert.throws(()=>parseTeamleaderArgs(['--status','--retry']),/requires --once/);
 assert.throws(()=>parseTeamleaderArgs(['--once','--budget-minutes','35']),/Budget/);
 assert.throws(()=>parseTeamleaderArgs(['--once','--exec','curl']),/Unknown/);
});
test('state policy rejects malformed heads and never schedules unauthorised actions',()=>{
 const blank=initialLeaderState();
 assert.equal(selectTeamleaderAction(blank,A).kind,'BASELINE_REPAIR');
 assert.throws(()=>selectTeamleaderAction(blank,'main'),/immutable commit/);
 assert.equal(selectTeamleaderAction({...blank,attemptedHeads:[A]},A).kind,'IDLE');
});
