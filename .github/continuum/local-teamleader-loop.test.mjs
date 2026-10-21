import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,spawnSync} from 'node:child_process';
import {runTeamleaderLoop,validateLoopOptions,isFailedCycleOutcome,validateWorkerHeartbeat} from './local-teamleader-loop.mjs';
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


test('scheduler wait failure persists terminal ERROR rather than false WAITING',async()=>{
 const observed=[];
 await assert.rejects(runTeamleaderLoop({
  work:()=>({outcome:'NO_FAILURE'}),intervalMinutes:1,
  onHeartbeat:heartbeat=>observed.push(heartbeat),
  wait:async()=>{throw Error('scheduler storage unavailable');}
 }),/scheduler storage unavailable/);
 assert.deepEqual(observed.map(b=>b.state),['RUNNING','WAITING','ERROR']);
 assert.equal(observed.at(-1).nextAttemptAt,null);
 assert.match(observed.at(-1).detail,/scheduler storage unavailable/);
});

test('saved status validates legitimate heartbeats and rejects spoofed or corrupt states',async()=>{
 const beats=[];
 await runTeamleaderLoop({
  work:()=>({outcome:'NO_FAILURE'}),maxCycles:2,intervalMinutes:1,
  wait:async()=>{},onHeartbeat:heartbeat=>beats.push(heartbeat)
 });
 for(const beat of beats)assert.equal(validateWorkerHeartbeat(beat),true);
 const waiting=beats.find(b=>b.state==='WAITING');
 assert.ok(waiting);
 assert.equal(validateWorkerHeartbeat({...waiting,cycles:0,failures:0,
  failureStreak:0,lastOutcome:'VERIFIED_PATCH_PENDING_REVIEW'}),false,
  'zero completed cycles cannot claim a verified patch');
 assert.equal(validateWorkerHeartbeat({...waiting,cycles:1,lastOutcome:null}),false,
  'a completed cycle must have a declared outcome');
 for(const bad of [
  {schema:'bad-schema'},{version:9},{state:'VERIFIED'},
  {cycles:-1},{failures:10000},{failureStreak:-1},
  {lastOutcome:'FAKE_VERIFIED'},{startedAt:'invalid'},
  {nextAttemptAt:null},{nextAttemptAt:waiting.lastFinishedAt-1},
  {lastFinishedAt:null},{detail:{message:'fake success'}}
 ])assert.equal(validateWorkerHeartbeat({...waiting,...bad}),false,JSON.stringify(bad));
});

function persistentFixture(t){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-loop-'));
 t.after(()=>fs.rmSync(base,{recursive:true,force:true}));
 const sourceRoot=path.join(base,'source');fs.mkdirSync(sourceRoot);
 const git=(...args)=>{
  const result=spawnSync('git',['-C',sourceRoot,...args],{encoding:'utf8'});
  assert.equal(result.status,0,result.stderr);return result.stdout.trim();
 };
 git('init','-q');git('config','user.name','Loop Test');
 git('config','user.email','loop@example.invalid');
 fs.writeFileSync(path.join(sourceRoot,'source.txt'),'source\n');
 git('add','.');git('commit','-qm','fixture');
 let clock=1_000_000;
 return {sourceRoot,stateFile:path.join(base,'state','loop.json'),
  maxCycles:3,intervalMinutes:1,now:()=>clock,
  wait:async ms=>{clock+=ms;},git};
}

test('durable restart retains total cycle budget and pending backoff',async t=>{
 const fixture=persistentFixture(t),ids=[],waits=[];
 let workCalls=0;
 await assert.rejects(runTeamleaderLoop({...fixture,
  work:context=>{workCalls++;ids.push(context.cycleId);return {outcome:'CHECKS_FAILED'};},
  onHeartbeat:heartbeat=>{if(heartbeat.state==='WAITING')throw Error('host stopped');}
 }),/host stopped/);
 const summary=await runTeamleaderLoop({...fixture,
  work:context=>{workCalls++;ids.push(context.cycleId);return {outcome:'IDLE'};},
  wait:async ms=>{waits.push(ms);await fixture.wait(ms);}
 });
 assert.equal(workCalls,3,'restart must not grant three additional cycles');
 assert.equal(summary.cycles,3);assert.equal(summary.failures,1);
 assert.equal(summary.resumed,true);assert.equal(summary.state,'LIMIT_REACHED');
 assert.deepEqual(waits,[60_000,60_000]);
 assert.equal(new Set(ids).size,3,'each consumed decision has a stable unique identity');
 const checkpoint=JSON.parse(fs.readFileSync(fixture.stateFile,'utf8'));
 assert.equal(checkpoint.heartbeat.state,'LIMIT_REACHED');
 const again=await runTeamleaderLoop({...fixture,work:()=>{throw Error('terminal run repeated');}});
 assert.equal(again.cycles,3);assert.equal(again.lastOutcome,'IDLE');
});

test('durable cancellation during work records interruption and a stopped session',async t=>{
 const fixture=persistentFixture(t),controller=new AbortController(),beats=[];
 const summary=await runTeamleaderLoop({...fixture,signal:controller.signal,
  onHeartbeat:heartbeat=>beats.push(heartbeat),
  work:context=>{assert.equal(context.signal,controller.signal);controller.abort();
   return {outcome:'VERIFIED_PATCH_PENDING_REVIEW'};}
 });
 assert.equal(summary.cycles,1);assert.equal(summary.failures,1);
 assert.equal(summary.lastOutcome,'INTERRUPTED');assert.equal(summary.state,'STOPPED');
 assert.equal(beats.at(-1).lastOutcome,'INTERRUPTED');
 const again=await runTeamleaderLoop({...fixture,work:()=>{throw Error('stopped session repeated');}});
 assert.equal(again.cycles,1);assert.equal(again.state,'STOPPED');
});

test('pre-aborted durable run writes STOPPED without executing work',async t=>{
 const fixture=persistentFixture(t),controller=new AbortController();controller.abort();
 const summary=await runTeamleaderLoop({...fixture,signal:controller.signal,
  work:()=>{throw Error('pre-aborted work executed');}});
 assert.equal(summary.cycles,0);assert.equal(summary.state,'STOPPED');
 const saved=JSON.parse(fs.readFileSync(fixture.stateFile,'utf8'));
 assert.equal(saved.heartbeat.lastOutcome,null);
 assert.equal(validateWorkerHeartbeat(saved.heartbeat),true);
});

test('durable state fails closed on corruption, changed budgets and changed source',async t=>{
 const fixture=persistentFixture(t);
 await runTeamleaderLoop({...fixture,maxCycles:1,work:()=>({outcome:'IDLE'})});
 await assert.rejects(runTeamleaderLoop({...fixture,maxCycles:2,
  work:()=>{throw Error('changed budget executed');}}),/settings|budget/i);
 const original=fs.readFileSync(fixture.stateFile,'utf8');
 fs.writeFileSync(fixture.stateFile,'{broken');
 await assert.rejects(runTeamleaderLoop({...fixture,maxCycles:1,
  work:()=>{throw Error('corrupt state executed');}}),/checkpoint|corrupt/i);
 fs.writeFileSync(fixture.stateFile,original);
 fs.writeFileSync(path.join(fixture.sourceRoot,'untracked.txt'),'dirty');
 await assert.rejects(runTeamleaderLoop({...fixture,maxCycles:1,
  work:()=>{throw Error('dirty source executed');}}),/dirty/i);
 fixture.git('add','.');fixture.git('commit','-qm','new source');
 await assert.rejects(runTeamleaderLoop({...fixture,maxCycles:1,
  work:()=>{throw Error('different HEAD executed');}}),/source|HEAD/i);
});

test('durable scheduling requires a finite budget and state outside source',async t=>{
 const fixture=persistentFixture(t),work=()=>{throw Error('invalid durable work executed');};
 await assert.rejects(runTeamleaderLoop({...fixture,maxCycles:null,work}),/finite cycle budget/);
 await assert.rejects(runTeamleaderLoop({...fixture,
  stateFile:path.join(fixture.sourceRoot,'loop.json'),work}),/outside source/);
});

test('durable integrity validation rejects plausible counter edits',async t=>{
 const fixture=persistentFixture(t);
 await runTeamleaderLoop({...fixture,maxCycles:1,work:()=>({outcome:'IDLE'})});
 const saved=JSON.parse(fs.readFileSync(fixture.stateFile,'utf8'));
 saved.heartbeat.detail='fabricated verification';
 fs.writeFileSync(fixture.stateFile,JSON.stringify(saved));
 await assert.rejects(runTeamleaderLoop({...fixture,maxCycles:1,
  work:()=>{throw Error('tampered state executed');}}),/corrupt.*checkpoint/i);
});

test('source drift while waiting terminates the saved cadence before another cycle',async t=>{
 const fixture=persistentFixture(t);let calls=0;
 await assert.rejects(runTeamleaderLoop({...fixture,
  work:()=>{calls++;return {outcome:'IDLE'};},
  wait:async ms=>{await fixture.wait(ms);
   fs.writeFileSync(path.join(fixture.sourceRoot,'source.txt'),'changed\n');}
 }),/dirty/i);
 assert.equal(calls,1);
 const saved=JSON.parse(fs.readFileSync(fixture.stateFile,'utf8'));
 assert.equal(saved.heartbeat.state,'ERROR');assert.equal(saved.heartbeat.nextAttemptAt,null);
});

test('source drift during a cycle cannot retain its reported verification success',async t=>{
 const fixture=persistentFixture(t);
 const summary=await runTeamleaderLoop({...fixture,work:()=>{
  fs.writeFileSync(path.join(fixture.sourceRoot,'source.txt'),'changed\n');
  return {outcome:'VERIFIED_PATCH_PENDING_REVIEW'};
 }});
 assert.equal(summary.state,'ERROR');assert.equal(summary.lastOutcome,'ERROR');
 assert.equal(summary.failures,1);assert.equal(summary.cycles,1);
});

test('durable loop rejects a competing scheduler before its work executes',async t=>{
 const fixture=persistentFixture(t);
 let enter,release;
 const entered=new Promise(resolve=>{enter=resolve;});
 const blocked=new Promise(resolve=>{release=resolve;});
 const first=runTeamleaderLoop({...fixture,maxCycles:1,
  work:async()=>{enter();await blocked;return {outcome:'IDLE'};}});
 await entered;
 try{
  await assert.rejects(runTeamleaderLoop({...fixture,maxCycles:1,
   work:()=>{throw Error('competing work executed');}}),/already running|busy/i);
 }finally{release();await first;}
});

test('SIGKILL leaves RUNNING and restart consumes the crashed attempt without replay',{timeout:10_000},async t=>{
 const fixture=persistentFixture(t);
 const moduleUrl=new URL('./local-teamleader-loop.mjs',import.meta.url).href;
 const child=spawn(process.execPath,['--input-type=module','-e',
  `import {runTeamleaderLoop} from ${JSON.stringify(moduleUrl)};
   await runTeamleaderLoop({sourceRoot:process.argv[1],stateFile:process.argv[2],
    maxCycles:1,intervalMinutes:1,work:async()=>{
     process.stdout.write('RUNNING\\n');setInterval(()=>{},1000);await new Promise(()=>{});
    }});`,fixture.sourceRoot,fixture.stateFile],{stdio:['ignore','pipe','pipe']});
 let stderr='';child.stderr.on('data',data=>{stderr+=data;});
 t.after(()=>{if(child.exitCode===null)child.kill('SIGKILL');});
 await new Promise((resolve,reject)=>{
  child.once('error',reject);
  child.stdout.once('data',resolve);
  child.once('exit',()=>reject(Error('child exited before RUNNING: '+stderr)));
 });
 const exited=new Promise(resolve=>child.once('exit',resolve));
 child.kill('SIGKILL');await exited;
 assert.equal(JSON.parse(fs.readFileSync(fixture.stateFile,'utf8')).heartbeat.state,'RUNNING');
 const summary=await runTeamleaderLoop({...fixture,now:Date.now,maxCycles:1,
  work:()=>{throw Error('crashed attempt repeated');}});
 assert.equal(summary.cycles,1);assert.equal(summary.failures,1);
 assert.equal(summary.lastOutcome,'INTERRUPTED');assert.equal(summary.state,'LIMIT_REACHED');
});
