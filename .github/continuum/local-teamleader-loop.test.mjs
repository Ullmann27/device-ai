import test from 'node:test';
import assert from 'node:assert/strict';
import {runTeamleaderLoop,validateLoopOptions} from './local-teamleader-loop.mjs';
import {parseTeamleaderArgs} from './teamleader-cli.mjs';

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
 assert.equal(beats.length,3);
 assert.equal(beats[0].state,'WAITING');
 assert.ok(beats[0].nextAttemptAt>=beats[0].lastFinishedAt+3600000);
 assert.equal(beats[2].state,'LIMIT_REACHED');
 assert.equal(beats[2].nextAttemptAt,null);
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
 assert.equal(beats[0].lastOutcome,'ERROR');
 assert.equal(beats[1].lastOutcome,'CHECKS_FAILED');
 assert.equal(beats[1].failureStreak,2);
 assert.equal(beats[2].failureStreak,0);
 assert.equal(beats[2].state,'LIMIT_REACHED');
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
