import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import crypto from 'node:crypto';
import path from 'node:path';
import {initialLeaderState,decodeLeaderState,selectTeamleaderAction,runTeamleaderCycle,summarizeTeamleaderProgress,publicTeamleaderRepairReport}
 from './autonomous-teamleader.mjs';
import {parseTeamleaderArgs} from './teamleader-cli.mjs';
import {acquireContinuumBuildLease} from './shared-build-lease.mjs';

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
  investigate:(_r,_s,_h,id)=>{calls.investigations++;pending=null;return{status:'DONE',staticResult:'REPRODUCED_STATIC_FINDING',taskId:id,executed:3,failed:0}}
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
test('teamleader refuses symlinked state directory pointing back into source checkout',()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-leader-path-'));
 try{
  const source=path.join(home,'source'),alias=path.join(home,'alias');
  fs.mkdirSync(source);
  fs.symlinkSync(source,alias,'dir');
  assert.throws(()=>runTeamleaderCycle({
   root:source,stateDir:path.join(alias,'private-state'),driver:fake()
  }),/outside the repository/);
  assert.deepEqual(fs.readdirSync(source),[]);
 }finally{fs.rmSync(home,{recursive:true,force:true});}
});

test('owner-accepted repair runs before baseline and only records an independently verified patch',
 ()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 let owner={id:'repair-owner-one',source:'owner',kind:'REPAIR',state:'READY',
   subject:{base:A,chain:'node'}};
 driver.readyOwnerRepair=()=>owner;
 driver.ownerRepair=(_root,_state,_head,id)=>{
   assert.equal(id,owner.id);
   owner=null;
   return {status:'DONE',taskId:id,patchSha256:'c'.repeat(64),approvedForMerge:false,
     report:'/external/verified-report.json',
     profiles:[{profile:'node-continuum',passed:true,failed:0,executed:4}]};
 };
 const r=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(r.kind,'OWNER_REPAIR');
 assert.equal(r.outcome,'VERIFIED_PATCH_PENDING_REVIEW');
 assert.equal(r.artifact,'/external/verified-report.json');
 assert.equal(driver.calls.repairs,0,'owner task must not cause an unrequested baseline repair');
 assert.equal(decodeLeaderState(fs.readFileSync(r.statePath)).events.at(-1).kind,'OWNER_REPAIR');
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE',
  'do not repeat the exact same baseline after owner repair already checked it');
 assert.equal(driver.calls.repairs,0);
}));

test('two distinct accepted owner repairs run in consecutive local cycles on one unchanged HEAD',
 ()=>sandbox(({root,stateDir})=>{
  const d=fake(),head=A;
  const queue=['owner-first-task','owner-second-task'];
  let count=0;
  d.readyOwnerRepair=()=>queue.length?{
    id:queue[0],kind:'REPAIR',source:'owner',state:'READY',
    subject:{base:head,chain:'node'}
  }:null;
  d.ownerRepair=(_root,_state,_head,id)=>{
    assert.equal(id,queue.shift());
    count++;
    return {status:'DONE',taskId:id,patchSha256:'e'.repeat(64),
      approvedForMerge:false,report:'/out/'+id+'.json',
      profiles:[{profile:'node-continuum',passed:true,executed:5,failed:0}]};
  };
  assert.equal(runTeamleaderCycle({root,stateDir,driver:d}).kind,'OWNER_REPAIR');
  assert.equal(runTeamleaderCycle({root,stateDir,driver:d}).kind,'OWNER_REPAIR');
  assert.equal(runTeamleaderCycle({root,stateDir,driver:d}).outcome,'IDLE');
  assert.equal(count,2);
  assert.equal(d.calls.repairs,0,'do not waste a second baseline run');
 }));

test('accepted owner TEST is dispatched before baseline and a verified new contract is review-only',
 ()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 let testTask={id:'owner-node-contract',kind:'TEST',source:'owner',state:'READY',
  subject:{base:A,chain:'node',operation:'ASSERT_NODE_EXPORT'}};
 driver.readyOwnerContractTask=()=>testTask;
 driver.ownerContractTask=(_root,_state,_head,id)=>{
  assert.equal(id,testTask.id);
  testTask=null;
  return {status:'DONE',taskId:id,approvedForMerge:false,
   patchSha256:'d'.repeat(64),patch:'/external/review.patch',receipt:'/external/verification.json',
   proof:{passed:true,baselineExecuted:1,candidateExecuted:3,independentExecuted:3}};
 };
 const first=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(first.kind,'OWNER_TEST');
 assert.equal(first.outcome,'VERIFIED_PATCH_PENDING_REVIEW');
 assert.equal(first.artifact,'/external/review.patch');
 assert.equal(driver.calls.repairs,0);
 const second=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(second.kind,'BASELINE_REPAIR','owner TEST must not silently consume the baseline');
 assert.equal(second.outcome,'NO_FAILURE');
 assert.equal(driver.calls.repairs,1);
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
 assert.equal(driver.calls.repairs,1);
}));

test('owner TEST cannot falsely claim DONE without increasing or independently checking tests',
 ()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 driver.readyOwnerContractTask=()=>({
  id:'owner-node-contract',kind:'TEST',source:'owner',state:'READY',
  subject:{base:A,chain:'node',operation:'ASSERT_NODE_EXPORT'}
 });
 driver.ownerContractTask=()=>({
  status:'DONE',taskId:'owner-node-contract',approvedForMerge:false,
  patchSha256:'d'.repeat(64),receipt:'/receipt.json',
  proof:{passed:true,baselineExecuted:2,candidateExecuted:2,independentExecuted:2}
 });
 const r=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(r.kind,'OWNER_TEST');
 assert.equal(r.outcome,'ERROR');
 assert.match(r.detail,/without independent contract evidence/);
 assert.equal(driver.calls.repairs,0);
}));

test('failed owner TEST also leaves the immutable HEAD eligible for one real baseline',()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 let ownerTest=true;
 driver.readyOwnerContractTask=()=>ownerTest?{
  id:'owner-failing-test',kind:'TEST',source:'owner',state:'READY',
  subject:{base:A,chain:'node',operation:'ASSERT_NODE_EXPORT'}
 }:null;
 driver.ownerContractTask=()=>{ownerTest=false;return {status:'FAILED',reason:'incomplete contract'}};
 const failed=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(failed.kind,'OWNER_TEST');
 assert.equal(failed.outcome,'CHECKS_FAILED');
 assert.equal(decodeLeaderState(fs.readFileSync(failed.statePath)).attemptedHeads.length,0);
 const baseline=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(baseline.kind,'BASELINE_REPAIR');
 assert.equal(baseline.outcome,'NO_FAILURE');
 assert.equal(driver.calls.repairs,1);
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
}));

test('owner REPAIR retains priority over TEST and both finish on the same HEAD',
 ()=>sandbox(({root,stateDir})=>{
 const driver=fake(),work=[];
 let ownerRepair=true,ownerTest=true;
 driver.readyOwnerRepair=()=>ownerRepair?{id:'repair-owner-one',kind:'REPAIR',
  source:'owner',state:'READY',subject:{base:A,chain:'node'}}:null;
 driver.readyOwnerContractTask=()=>ownerTest?{id:'owner-node-contract',kind:'TEST',
  source:'owner',state:'READY',
  subject:{base:A,chain:'node',operation:'ASSERT_NODE_EXPORT'}}:null;
 driver.ownerRepair=(_r,_s,_h,id)=>{
  ownerRepair=false;work.push(id);return {status:'DONE',taskId:id,
    approvedForMerge:false,patchSha256:'a'.repeat(64),
    profiles:[{profile:'node-continuum',passed:true,executed:2,failed:0}]};
 };
 driver.ownerContractTask=(_r,_s,_h,id)=>{
  ownerTest=false;work.push(id);return {status:'DONE',taskId:id,
   approvedForMerge:false,patchSha256:'b'.repeat(64),receipt:'/report',
   proof:{passed:true,baselineExecuted:2,candidateExecuted:4,independentExecuted:4}};
 };
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).kind,'OWNER_REPAIR');
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).kind,'OWNER_TEST');
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
 assert.deepEqual(work,['repair-owner-one','owner-node-contract']);
 assert.equal(driver.calls.repairs,0);
}));

test('retry is bounded by caller and cannot preempt accepted Owner REPAIR or TEST',
 ()=>sandbox(({root,stateDir})=>{
  const d=fake();
  d.readyOwnerRepair=()=>({id:'owner-rerun-repair',kind:'REPAIR',
   source:'owner',state:'READY',subject:{base:A,chain:'node'}});
  d.ownerRepair=(_r,_s,_h,id)=>({
   status:'DONE',taskId:id,patchSha256:'c'.repeat(64),
   approvedForMerge:false,
   profiles:[{profile:'node-continuum',passed:true,failed:0,executed:2}]
  });
  const result=runTeamleaderCycle({root,stateDir,driver:d,retry:true});
  assert.equal(result.kind,'OWNER_REPAIR');
  assert.equal(result.outcome,'VERIFIED_PATCH_PENDING_REVIEW');
  assert.equal(d.calls.repairs,0,'retry does not override an accepted task');
 }));

test('owner repair evidence cannot be faked by a driver claiming DONE',
 ()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 driver.readyOwnerRepair=()=>({id:'repair-owner-one',source:'owner',kind:'REPAIR',
   state:'READY',subject:{base:A,chain:'node'}});
 driver.ownerRepair=()=>({status:'DONE',taskId:'repair-owner-one',
   patchSha256:'c'.repeat(64),approvedForMerge:false,profiles:[]});
 const r=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(r.kind,'OWNER_REPAIR');
 assert.equal(r.outcome,'ERROR');
 assert.match(r.detail,/without independent test evidence/);
 assert.equal(driver.calls.repairs,0);
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
test('Teamleader refuses fabricated DONE when no tests actually executed',()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 runTeamleaderCycle({root,stateDir,driver});
 driver.setPending({id:'inv-0123456789abcdef',kind:'INVESTIGATE',state:'READY'});
 driver.investigate=(_r,_s,_h,id)=>({status:'DONE',
  taskId:id,staticResult:'REPRODUCED_STATIC_FINDING',executed:0,failed:0});
 const result=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(result.outcome,'ERROR');
 assert.match(result.detail,/without matching executed checks/);
}));

test('verified repair is a PROPOSAL only; never described as applied',()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 driver.repair=()=>({outcome:'VERIFIED',verified:{strategy:'KOTLIN_MISSING_IMPORT'},
  independent:{passed:true,profiles:[{profile:'node-continuum',passed:true,failed:0,executed:3}]},
  artifact:path.join(stateDir,'reports','review.patch')});
 const r=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(r.outcome,'VERIFIED_PATCH_PENDING_REVIEW');
 assert.equal(r.artifact,path.join(stateDir,'reports','review.patch'));
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
}));
test('an internally passing repair without an independent proof is NOT verified',()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 driver.repair=()=>({outcome:'VERIFIED',verified:{strategy:'JS_TYPO_REFERENCE'},
  artifact:path.join(stateDir,'reports','provisional.patch')});
 const result=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(result.outcome,'NO_VERIFIED_REPAIR');
 const progress=summarizeTeamleaderProgress(
  decodeLeaderState(fs.readFileSync(result.statePath)));
 assert.equal(progress.independentlyVerifiedPatchProposals,0);
}));

test('a failed second-worktree check vetoes an otherwise passing repair',()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 driver.repair=()=>({outcome:'VERIFIED',verified:{strategy:'JS_TYPO_REFERENCE'},
  independent:{passed:false,reason:'Second worktree tests failed'}});
 const result=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(result.outcome,'NO_VERIFIED_REPAIR');
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
test('unexpected stop remains INTERRUPTED and no duplicate patch attempt occurs',()=>sandbox(({root,stateDir})=>{
 const driver=fake(),first=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(driver.calls.repairs,1);
 const state=JSON.parse(fs.readFileSync(first.statePath,'utf8'));
 state.events.pop(); // simulate process death after RUNNING journal commit
 const {integrity:_old,...data}=state;
 state.integrity=crypto.createHash('sha256').update(JSON.stringify(data)).digest('hex');
 fs.writeFileSync(first.statePath,JSON.stringify(state)+'\n');
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
 const verified=decodeLeaderState(fs.readFileSync(first.statePath));
 assert.equal(verified.events.at(-1).outcome,'INTERRUPTED');
 assert.equal(driver.calls.repairs,1);
}));

test('old lock of a definitively dead same-host process recovers automatically',()=>sandbox(({root,stateDir})=>{
 const driver=fake(),first=runTeamleaderCycle({root,stateDir,driver});
 const lock=first.statePath+'.lock';
 fs.writeFileSync(lock,JSON.stringify({pid:99999999,host:os.hostname(),at:1}));
 assert.equal(runTeamleaderCycle({root,stateDir,driver,now:()=>600000}).outcome,'IDLE');
 assert.equal(fs.existsSync(lock),false);
}));

test('repeat checking a proven head requires explicit manual retry',()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 runTeamleaderCycle({root,stateDir,driver});
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
 assert.equal(runTeamleaderCycle({root,stateDir,driver,retry:true}).outcome,'NO_FAILURE');
 assert.equal(driver.calls.repairs,2);
}));

test('CLI budget, command and retry flags are validated before execution',()=>{
 assert.deepEqual(parseTeamleaderArgs(['--once','--budget-minutes','5']),
  {once:true,loop:false,status:false,retry:false,budgetMinutes:'5'});
 assert.throws(()=>parseTeamleaderArgs(['--once','--status']),/exactly one/);
 assert.throws(()=>parseTeamleaderArgs(['--status','--retry']),/requires --once/);
 assert.throws(()=>parseTeamleaderArgs(['--once','--budget-minutes','35']),/Budget/);
 assert.throws(()=>parseTeamleaderArgs(['--once','--exec','curl']),/Unknown/);
});
test('idle unchanged source HEAD receives exactly one structural discovery, new HEAD re-enables scan',
 ()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 runTeamleaderCycle({root,stateDir,driver});
 for(let i=0;i<24;i++)assert.equal(
  runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
 assert.equal(driver.calls.prepared,1,'one analysis instead of 24 hourly scans');
 const stored=decodeLeaderState(fs.readFileSync(path.join(stateDir,'teamleader-state.json')));
 assert.deepEqual(stored.discoveryHeads,[A]);
 driver.setHead(B);
 runTeamleaderCycle({root,stateDir,driver});
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
 assert.equal(driver.calls.prepared,2);
}));
test('crashed discovery records a failure and never hot-loops on the same HEAD',
 ()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 runTeamleaderCycle({root,stateDir,driver});
 let scans=0;
 driver.prepareSafeInvestigation=()=>{scans++;throw Error('unavailable analyzer')};
 const failed=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(failed.outcome,'ERROR');
 assert.match(failed.detail,/unavailable analyzer/);
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
 assert.equal(scans,1);
 const history=decodeLeaderState(fs.readFileSync(failed.statePath));
 assert.equal(history.events.at(-1).outcome,'ERROR');
 assert.deepEqual(history.discoveryHeads,[A]);
}));
test('Teamleader can read older version-1 states that lack discoveryHeads',
 ()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 const first=runTeamleaderCycle({root,stateDir,driver});
 const state=JSON.parse(fs.readFileSync(first.statePath,'utf8'));
 delete state.discoveryHeads;
 const {integrity:_old,...data}=state;
 state.integrity=crypto.createHash('sha256').update(JSON.stringify(data)).digest('hex');
 fs.writeFileSync(first.statePath,JSON.stringify(state)+'\n');
 assert.deepEqual(decodeLeaderState(fs.readFileSync(first.statePath)).discoveryHeads,[]);
 assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'IDLE');
}));

test('measured progress distinguishes real completed tasks, proposed patch and failed checks',
 ()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 const first=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(first.outcome,'NO_FAILURE');
 const base=decodeLeaderState(fs.readFileSync(first.statePath));
 let progress=summarizeTeamleaderProgress(base);
 assert.equal(progress.checkedHeads,1);
 assert.equal(progress.baselineClean,1);
 assert.equal(progress.completedInvestigations,0);
 assert.equal(progress.independentlyVerifiedPatchProposals,0);
 assert.equal(progress.testFailuresDetected,0);
 assert.equal(progress.currentlyUnfinished,0);
 driver.setPending({id:'inv-0123456789abcdef',kind:'INVESTIGATE',state:'READY'});
 const result=runTeamleaderCycle({root,stateDir,driver});
 assert.equal(result.outcome,'DONE_INVESTIGATION');
 progress=summarizeTeamleaderProgress(
  decodeLeaderState(fs.readFileSync(first.statePath)));
 assert.equal(progress.completedInvestigations,1);
 assert.equal(progress.independentlyVerifiedPatchProposals,0);
 assert.equal(progress.lastOutcome,'DONE_INVESTIGATION');
 const pending=initialLeaderState();
 pending.events.push({id:'tl-'+'0'.repeat(20),head:A,kind:'BASELINE_REPAIR',
   outcome:'RUNNING',detail:'queued',elapsedMs:0,at:12345});
 assert.equal(summarizeTeamleaderProgress(pending).currentlyUnfinished,1);
}));
test('discovery heads counter counts analysis only, not hourly idle loops',
 ()=>sandbox(({root,stateDir})=>{
 const driver=fake();
 const first=runTeamleaderCycle({root,stateDir,driver});
 for(let i=0;i<8;i++)runTeamleaderCycle({root,stateDir,driver});
 const stats=summarizeTeamleaderProgress(
  decodeLeaderState(fs.readFileSync(first.statePath)));
 assert.equal(stats.analyzedHeads,1);
 assert.equal(stats.windowEvents,2);
 assert.equal(stats.baselineClean,1);
}));

test('state policy rejects malformed heads and never schedules unauthorised actions',()=>{
 const blank=initialLeaderState();
 assert.equal(selectTeamleaderAction(blank,A).kind,'BASELINE_REPAIR');
 assert.throws(()=>selectTeamleaderAction(blank,'main'),/immutable commit/);
 assert.equal(selectTeamleaderAction({...blank,attemptedHeads:[A]},A).kind,'IDLE');
});

test('unverified repair is redacted consistently without modifying the audit original',()=>{
 const original={outcome:'VERIFIED',verified:{candidate:'winner',patchSha256:'f'.repeat(64)},
  independent:{passed:false},attempts:[{outcome:'VERIFIED',candidate:'winner'},
   {outcome:'FAILED_TESTS',candidate:'loser'}]};
 const publicReport=publicTeamleaderRepairReport(original);
 assert.equal(publicReport.outcome,'NO_VERIFIED_REPAIR');
 assert.equal(publicReport.verified,null);
 assert.deepEqual(publicReport.attempts.map(a=>a.outcome),
  ['INSUFFICIENT_EVIDENCE','FAILED_TESTS']);
 assert.equal(original.outcome,'VERIFIED');
 assert.equal(original.attempts[0].outcome,'VERIFIED');
});

test('successful independent second-worktree result keeps verified proposal intact',()=>{
 const report={outcome:'VERIFIED',verified:{patchSha256:'e'.repeat(64)},
  independent:{passed:true},attempts:[{outcome:'VERIFIED'}]};
 assert.equal(publicTeamleaderRepairReport(report),report);
});

test('legacy internal VERIFIED without independent observation cannot leak success',()=>{
 const report={outcome:'VERIFIED',verified:{patchSha256:'d'.repeat(64)},
  attempts:[{outcome:'VERIFIED'}]};
 const publicReport=publicTeamleaderRepairReport(report);
 assert.equal(publicReport.outcome,'NO_VERIFIED_REPAIR');
 assert.equal(publicReport.verified,null);
 assert.equal(publicReport.attempts[0].outcome,'INSUFFICIENT_EVIDENCE');
});

test('a running Android build blocks teamleader before any repair and releases local state lock',()=>
 sandbox(({root,stateDir})=>{
  const driver=fake();
  const release=acquireContinuumBuildLease(root);
  try{
   assert.throws(()=>runTeamleaderCycle({root,stateDir,driver}),/CONTINUUM_BUILD_LEASE_BUSY/);
   assert.equal(driver.calls.repairs,0);
  }finally{release();}
  assert.equal(runTeamleaderCycle({root,stateDir,driver}).outcome,'NO_FAILURE');
  assert.equal(driver.calls.repairs,1);
 }));

test('the owner release of a dead build lease is its own exclusive CLI mode',()=>{
 assert.equal(parseTeamleaderArgs(['--release-dead-lease']).releaseDeadLease,true);
 assert.throws(()=>parseTeamleaderArgs(['--release-dead-lease','--once']),/exactly one/);
 assert.throws(()=>parseTeamleaderArgs(['--release-dead-lease','--release-dead-lease']),/Duplicate/);
});
