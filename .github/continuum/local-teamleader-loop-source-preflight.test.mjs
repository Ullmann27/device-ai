/** Real Git fixtures prove that rejected source configuration cannot run its
 * fsmonitor/filter commands through the scheduler's own status checks. */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {runTeamleaderLoop} from './local-teamleader-loop.mjs';

function fixture(t){
 const saved=new Map(['GIT_CONFIG_NOSYSTEM','GIT_CONFIG_GLOBAL'].map(key=>[key,process.env[key]]));
 process.env.GIT_CONFIG_NOSYSTEM='1';process.env.GIT_CONFIG_GLOBAL='/dev/null';
 t.after(()=>{for(const [key,value] of saved)if(value===undefined)delete process.env[key];else process.env[key]=value;});
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'loop-source-preflight-'));
 t.after(()=>fs.rmSync(home,{recursive:true,force:true}));
 const sourceRoot=path.join(home,'source');fs.mkdirSync(sourceRoot);
 const git=(args,{allowMissing=false}={})=>{
  const r=spawnSync('git',['-C',sourceRoot,...args],{encoding:'utf8',timeout:5000,shell:false});
  assert.ok(r.status===0||allowMissing&&r.status===1,r.stderr);return r;
 };
 git(['init','-q','-b','source-preflight-fixture']);
 fs.writeFileSync(path.join(sourceRoot,'source.txt'),'source\n');
 fs.writeFileSync(path.join(sourceRoot,'.gitattributes'),'source.txt filter=fixture\n');
 git(['add','.']);git(['-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','source fixture']);
 const marker=path.join(home,'command-invoked'),monitor=path.join(home,'fsmonitor.sh'),filter=path.join(home,'clean.sh');
 fs.writeFileSync(monitor,"#!/bin/sh\nprintf invoked >> '"+marker+"'\nprintf 'token\\000'\n",{mode:0o700});
 fs.writeFileSync(filter,"#!/bin/sh\nprintf invoked >> '"+marker+"'\ncat\n",{mode:0o700});
 const denied=Error('SOURCE_PREFLIGHT_REJECTED');
 const sourcePreflight=async root=>{
  assert.equal(root,sourceRoot);
  // Deliberate asynchronous boundary: an un-awaited policy must not race Git.
  await new Promise(resolve=>setImmediate(resolve));
  const config=git(['config','--local','--get-regexp','^(core\\.fsmonitor|filter\\..*\\.clean)$'],{allowMissing:true});
  if(config.status===0)throw denied;
 };
 const armMonitor=()=>git(['config','core.fsmonitor',monitor]);
 const armFilter=()=>{
  git(['config','filter.fixture.clean',filter]);
  // The bytes stay identical; the changed stat makes Git evaluate the filter.
  fs.writeFileSync(path.join(sourceRoot,'source.txt'),'source\n');
 };
 return {sourceRoot,stateFile:path.join(home,'state','loop.json'),marker,denied,sourcePreflight,armMonitor,armFilter};
}

test('initial durable source preflight rejects fsmonitor before any Git status or checkpoint',async t=>{
 const f=fixture(t);f.armMonitor();let calls=0,failure;
 try{await runTeamleaderLoop({...f,maxCycles:1,work:()=>{calls++;return {outcome:'IDLE'};}});}
 catch(error){failure=error;}
 assert.equal(fs.existsSync(f.marker),false,'Git status must not execute the rejected fsmonitor');
 assert.equal(failure,f.denied);assert.equal(calls,0);
 assert.equal(fs.existsSync(f.stateFile),false);assert.equal(fs.existsSync(f.stateFile+'.lock'),false);
});

test('configuration changed while waiting is rejected before the next scheduler status check',async t=>{
 const f=fixture(t);let calls=0,failure;
 try{await runTeamleaderLoop({...f,maxCycles:2,intervalMinutes:1,
  work:()=>{calls++;return {outcome:'IDLE'};},wait:async()=>f.armMonitor()});}
 catch(error){failure=error;}
 assert.equal(fs.existsSync(f.marker),false,'The between-cycle status must not invoke the changed fsmonitor');
 assert.equal(failure,f.denied);assert.equal(calls,1);
 const saved=JSON.parse(fs.readFileSync(f.stateFile));
 assert.equal(saved.heartbeat.state,'ERROR');assert.equal(saved.heartbeat.cycles,1);
 assert.equal(saved.heartbeat.nextAttemptAt,null);assert.equal(fs.existsSync(f.stateFile+'.lock'),false);
});

test('configuration changed during work is rejected before post-work Git filters or success acceptance',async t=>{
 const f=fixture(t);let calls=0;
 const summary=await runTeamleaderLoop({...f,maxCycles:2,wait:async()=>{},work:()=>{
  calls++;f.armFilter();return {outcome:'VERIFIED_PATCH_PENDING_REVIEW'};
 }});
 assert.equal(fs.existsSync(f.marker),false,'The post-work status must not execute the changed clean filter');
 assert.equal(calls,1);assert.equal(summary.state,'ERROR');assert.equal(summary.lastOutcome,'ERROR');
 assert.equal(summary.cycles,1);assert.equal(summary.failures,1);
 const saved=JSON.parse(fs.readFileSync(f.stateFile));
 assert.equal(saved.heartbeat.detail,'SOURCE_PREFLIGHT_REJECTED');assert.equal(saved.heartbeat.nextAttemptAt,null);
 assert.equal(fs.existsSync(f.stateFile+'.lock'),false);
});

test('an invalid preflight option fails closed before a source status or work callback',async t=>{
 const f=fixture(t);f.armMonitor();let calls=0,failure;
 try{await runTeamleaderLoop({...f,maxCycles:1,sourcePreflight:'not executable',work:()=>{calls++;return {outcome:'IDLE'};}});}
 catch(error){failure=error;}
 assert.equal(fs.existsSync(f.marker),false);
 assert.match(failure?.message??'',/source preflight.*function/i);assert.equal(calls,0);
 assert.equal(fs.existsSync(f.stateFile),false);
});
