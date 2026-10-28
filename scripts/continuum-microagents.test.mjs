import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {MICRO_AGENT_JOBS,microAgentPlan,runMicroAgentChecks} from './continuum-microagents.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
test('one sequential contract role for each existing area, no new independent workers',()=>{
 const all=microAgentPlan();
 assert.deepEqual(all.map(x=>x.id),['gemma','deepseek','coding','transfer','update']);
 assert.deepEqual(Object.keys(MICRO_AGENT_JOBS),all.map(x=>x.id));
 assert.equal(new Set(all.flatMap(x=>x.tests)).size,all.flatMap(x=>x.tests).length);
});
test('arbitrary role/task names cannot become shell commands',()=>{
 for(const x of ['../../../etc/passwd','deepseek;curl','create-key','main','',null]){
   assert.throws(()=>microAgentPlan(x),/LOCAL_AGENT_UNKNOWN_ROLE/);
 }
});
test('a real role uses only fixed Node test files and a sanitized subprocess environment',()=>{
 let calls=0;
 const report=runMicroAgentChecks({root,selection:'deepseek',runner:(cmd,args,opts)=>{
   calls++;
   assert.equal(cmd,process.execPath);
   assert.equal(args[0],'--test');
   assert.equal(args.length,3);
   assert.equal(opts.shell,false);
   assert.equal(opts.env.CONTINUUM_REMOTE_BRAIN_API_KEY,undefined);
   assert.equal(opts.env.GITHUB_TOKEN,undefined);
   assert.equal(opts.env.NODE_OPTIONS,'');
   assert.equal(opts.env.NODE_USE_ENV_PROXY,'0');
   return {status:0,stdout:'test passed',stderr:''};
 }});
 assert.equal(calls,1);
 assert.equal(report.completed,true);
 assert.equal(report.results[0].status,'CONTRACT_TESTS_PASS');
 assert.equal(report.results[0].verifiedCodingRepair,false);
 assert.equal(report.gemmaNativeInferenceVerified,false);
 assert.equal(report.codeGenerationAvailable,false);
 assert.equal(report.deepSeekCodingConnected,false);
 assert.equal(report.ownerPrivilegesGranted,false);
});
test('failed deterministic test refuses onward role execution',()=>{
 let called=0;
 const report=runMicroAgentChecks({root,selection:'all',runner:()=>{
   called++;return {status:1,stdout:'',stderr:'synthetic failure'};
 }});
 assert.equal(called,1);
 assert.equal(report.completed,false);
 assert.equal(report.results[0].status,'CONTRACT_TESTS_FAIL');
 assert.equal(report.results.length,1);
});
test('timeout/termination cannot be presented as a passed check',()=>{
 const report=runMicroAgentChecks({root,selection:'gemma',
   runner:()=>({status:null,signal:'SIGTERM',error:{code:'ETIMEDOUT'}})});
 assert.equal(report.completed,false);
 assert.equal(report.results[0].status,'INTERRUPTED');
});
test('invalid unbounded execution budget is rejected before running tests',()=>{
 for(const value of [-1,0,999,90001,NaN,Infinity,'60000'])
   assert.throws(()=>runMicroAgentChecks({root,selection:'coding',limitMs:value}),
     /LOCAL_AGENT_INVALID_BUDGET/);
});
test('untrusted path is not executable and all paths are constant',()=>{
 assert.throws(()=>runMicroAgentChecks({root:'/',selection:'gemma',runner:()=>({status:0})}));
 assert.ok(microAgentPlan().every(x=>x.tests.every(f=>f.endsWith('.test.mjs'))));
});
test('CLI can list offline roles without a model, token or model download',()=>{
 const cli=path.resolve(root,'scripts/continuum-microagents.mjs');
 const run=spawnSync(process.execPath,[cli,'--list'],{encoding:'utf8',timeout:5000});
 assert.equal(run.status,0,run.stderr);
 const data=JSON.parse(run.stdout);
 assert.equal(data.roles.length,5);
 assert.equal(data.deepSeekCodingConnected,false);
});
test('CLI rejects unauthorized commands and network/deployment requests',()=>{
 const cli=path.resolve(root,'scripts/continuum-microagents.mjs');
 for(const args of [[],['--run'],['--download','deepseek'],['--run','../../'],['--install','27']]){
   const r=spawnSync(process.execPath,[cli,...args],{encoding:'utf8',timeout:5000});
   assert.notEqual(r.status,0,args.join(' '));
   assert.match(r.stderr,/CONTINUUM_MICRO_AGENT_BLOCKED/);
 }
});
