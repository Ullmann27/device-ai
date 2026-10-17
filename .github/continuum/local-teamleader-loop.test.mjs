import test from 'node:test';
import assert from 'node:assert/strict';
import {runTeamleaderLoop,validateLoopOptions,isFailedCycleOutcome} from './local-teamleader-loop.mjs';
import {parseTeamleaderArgs,teamleaderExitCode} from './teamleader-cli.mjs';

test('three local cycles pace on a fixed one-hour schedule and persist honest heartbeat',async()=>{
 const waits=[],beats=[];
 let count=0;
 const summary=await runTeamleaderLoop({
  work:()=>({outcome:++count===1?'NO_FAILURE':'IDLE'}),
  intervalMinutes:60,maxCycles:3,
  wait:async ms=>{waits.push(ms);},
  onHeartbeat:hb=>beats.push(hb)
 });
 assert.deepEqual(waits,[3600000,3600000]);
 assert.equal(summary.cycles,3);
 assert.equal(summary.failures,0);
 assert.equal(summary.lastOutcome,'IDLE');
 assert.equal(beats.length,6,'each cycle has RUNNING then an outcome');
 assert.equal(beats.filter(b=>b.state==='RUNNING').length,3);
 const completed=beats.filter(b=>b.state!=='RUNNING');
 assert.equal(completed[0].state,'WAITING');
 assert.ok(completed[0].nextAttemptAt>=completed[0].lastFinishedAt+3600000);
 assert.equal(completed[2].state,'LIMIT_REACHED');
 assert.equal(completed[2].nextAttemptAt,null);
});

test('failures stay failed, sleep with increasing backoff and eventually recover',async()=>{
 const waits=[],beats=[],errors=[];
 let i=0;
 const summary=await runTeamleaderLoop({
  work:()=>{
   i++;
   if(i===1)throw Error('SDK temporarily unavailable');
   if(i===2)return {outcome:'CHECKS_FAILED',detail:'compile did not run'};
   return {outcome:'NO_FAILURE'};
  },
  intervalMinutes:1,maxCycles:3,
  wait:async ms=>{waits.push(ms);},
  onHeartbeat:hb=>beats.push(hb),
  log:msg=>errors.push(msg)
 });
 assert.deepEqual(waits,[60000,120000]);
 assert.equal(summary.cycles,3);
 assert.equal(summary.failures,2);
 assert.equal(summary.lastOutcome,'NO_FAILURE');
 assert.equal(beats.length,6);
 const done=beats.filter(b=>b.state!=='RUNNING');
 assert.equal(done[0].lastOutcome,'ERROR');
 assert.equal(done[1].lastOutcome,'CHECKS_FAILED');
 assert.equal(done[1].failureStreak,2);
 assert.equal(done[2].failureStreak,0);
 assert.equal(done[2].state,'LIMIT_REACHED');
 assert.equal(errors.length,2);
});

test('signal stopping terminates without another cycle or artificial success',async()=>{
 const controller=new AbortController();
 let calls=0;
 const summary=await runTeamleaderLoop({
  work:()=>{calls++;return {outcome:'IDLE'};},
  intervalMinutes:1,signal:controller.signal,
  wait:async()=>controller.abort()
 });
 assert.equal(summary.cycles,1);
 assert.equal(calls,1);
 assert.equal(summary.stoppedBySignal,true);
});

test('invalid intervals, cycle limits and CLI mode combinations are rejected',()=>{
 for(const opts of [
  {intervalMinutes:0},{intervalMinutes:1441},
  {intervalMinutes:0.5},{maxCycles:0},{maxCycles:1001}
 ])assert.throws(()=>validateLoopOptions(opts));
 for(const args of [
  [],['--once','--loop'],['--status','--loop'],
  ['--once','--interval-minutes','1'],
  ['--loop','--retry'],
  ['--loop','--interval-minutes','0'],
  ['--loop','--max-cycles','1001'],
  ['--loop','--max-cycles','1.5']
 ])assert.throws(()=>parseTeamleaderArgs(args));
 const parsed=parseTeamleaderArgs(['--loop','--interval-minutes','60',
   '--max-cycles','3','--budget-minutes','20']);
 assert.equal(parsed.loop,true);
 assert.equal(parsed.intervalMinutes,'60');
 assert.equal(parsed.maxCycles,'3');
});


test('unverified repair and interruption increment failure counters, not successes',async()=>{
 const beats=[];
 const outcomes=['NO_VERIFIED_REPAIR','INTERRUPTED','IDLE'];
 let index=0;
 const summary=await runTeamleaderLoop({
  work:()=>({outcome:outcomes[index++]}),intervalMinutes:1,maxCycles:3,
  wait:async()=>{},onHeartbeat:hb=>beats.push(hb)
 });
 assert.equal(summary.failures,2);
 assert.equal(summary.lastOutcome,'IDLE');
 assert.equal(beats.filter(b=>b.state!=='RUNNING')[1].failureStreak,2);
});

test('unknown or unfinished worker result must fail closed',async()=>{
 for(const outcome of ['RUNNING','MADE_UP_STATUS']){
  const summary=await runTeamleaderLoop({
   work:()=>({outcome}),maxCycles:1
  });
  assert.equal(summary.failures,1);
  assert.equal(summary.lastOutcome,'ERROR');
 }
});

test('signal while waiting persists terminal STOPPED heartbeat',async()=>{
 const beats=[],controller=new AbortController();
 const summary=await runTeamleaderLoop({
  work:()=>({outcome:'IDLE'}),signal:controller.signal,
  wait:async()=>controller.abort(),onHeartbeat:hb=>beats.push(hb)
 });
 assert.equal(summary.stoppedBySignal,true);
 assert.equal(summary.cycles,1);
 assert.equal(beats.at(-1).state,'STOPPED');
 assert.equal(beats.at(-1).nextAttemptAt,null);
});

test('one-shot CLI exits nonzero for failed, unverified or unknown outcomes',()=>{
 const failing=['NO_CODE_DIAGNOSTIC','NO_VERIFIED_REPAIR','INTERRUPTED','CHECKS_FAILED','ERROR',
  'RUNNING','UNKNOWN',null,undefined];
 for(const outcome of failing){
  assert.equal(isFailedCycleOutcome(outcome),true);
  assert.equal(teamleaderExitCode({outcome}),1,
   'scheduled CLI must not report '+String(outcome)+' as success');
 }
 assert.equal(teamleaderExitCode(null),1);
 for(const outcome of ['NO_FAILURE',
  'VERIFIED_PATCH_PENDING_REVIEW','DONE_INVESTIGATION',
  'NO_READY_INVESTIGATION','IDLE']){
  assert.equal(isFailedCycleOutcome(outcome),false);
  assert.equal(teamleaderExitCode({outcome}),0);
 }
});

test('failure backoff never retries before a user configured interval',async()=>{
 const waits=[];let cycles=0;
 const result=await runTeamleaderLoop({
  intervalMinutes:120,maxCycles:2,
  work:()=>({outcome:++cycles===1?'CHECKS_FAILED':'IDLE'}),
  wait:async ms=>waits.push(ms)
 });
 assert.equal(result.failures,1);
 assert.deepEqual(waits,[120*60_000],
  'failure must not turn a two-hour schedule into an hourly schedule');
});


test('a failing baseline without a code diagnostic remains a failed worker cycle',async()=>{
 assert.equal(isFailedCycleOutcome('NO_CODE_DIAGNOSTIC'),true);
 assert.equal(teamleaderExitCode({outcome:'NO_CODE_DIAGNOSTIC'}),1);
 const beats=[],waits=[];
 let calls=0;
 const summary=await runTeamleaderLoop({
  work:()=>({outcome:++calls<3?'NO_CODE_DIAGNOSTIC':'NO_FAILURE'}),
  intervalMinutes:1,maxCycles:3,
  wait:async ms=>waits.push(ms),
  onHeartbeat:hb=>beats.push(hb)
 });
 assert.equal(summary.failures,2);
 assert.equal(summary.lastOutcome,'NO_FAILURE');
 assert.deepEqual(waits,[60_000,120_000]);
 const done=beats.filter(b=>b.state!=='RUNNING');
 assert.equal(done[0].lastOutcome,'NO_CODE_DIAGNOSTIC');
 assert.equal(done[0].failureStreak,1);
 assert.equal(done[1].failureStreak,2);
 assert.equal(done[2].failureStreak,0);
});
