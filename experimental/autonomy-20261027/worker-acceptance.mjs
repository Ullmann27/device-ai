/** Actual OS acceptance of the existing worker on an owned, public Git fixture.
 * Deliberate errors/timeouts are expected test events, never learning signals. */
import fs from 'node:fs';import path from 'node:path';import {spawn,spawnSync,execFileSync} from 'node:child_process';import {fileURLToPath} from 'node:url';import crypto from 'node:crypto';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const out=path.resolve(process.argv[2]??'');if(!process.argv[2]||out===root||out.startsWith(root+path.sep)||fs.existsSync(out))throw Error('NEW_EXTERNAL_OUTPUT_REQUIRED');
fs.mkdirSync(out,{recursive:false,mode:0o700});const repo=path.join(out,'source'),evidence=path.join(out,'receipts');
fs.mkdirSync(path.join(repo,'scripts'),{recursive:true});fs.mkdirSync(path.join(repo,'.github/continuum'),{recursive:true});
const workerBlobs={};for(const name of ['continuum-local-development-worker.mjs','continuum-worker-lease.mjs']){const bytes=fs.readFileSync(path.join(root,'scripts',name));fs.writeFileSync(path.join(repo,'scripts',name),bytes);workerBlobs[name]=crypto.createHash('sha256').update(bytes).digest('hex');}
const control=path.join(out,'controlled-mode');fs.writeFileSync(control,'pass');
fs.writeFileSync(path.join(repo,'.github/continuum/probe.test.mjs'),"import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';test('owned acceptance probe',async()=>{const mode=fs.readFileSync(process.env.CONTINUUM_WORKER_TEST_MODE_FILE,'utf8');if(mode==='wait')await new Promise(r=>setTimeout(r,10000));assert.equal(mode,'pass');});\n");
const git=(...args)=>execFileSync('git',args,{cwd:repo,encoding:'utf8'}).trim();git('init','-q','-b','test/owned-worker-acceptance');git('add','.');git('-c','user.name=Continuum fixture','-c','user.email=fixture@example.invalid','commit','-qm','immutable public worker fixture');
const head=git('rev-parse','HEAD');const env={...process.env,CONTINUUM_WORKER_TEST_MODE_FILE:control};delete env.NODE_TEST_CONTEXT;
const script=path.join(repo,'scripts/continuum-local-development-worker.mjs');const config=path.join(out,'budgets.json');fs.writeFileSync(config,JSON.stringify({timeoutMs:1500,intervalMs:500,maxCycles:2}));
const options=['--config',config],runs=[];const check=(ok,msg)=>{if(!ok)throw Error(msg);};
const waitFor=async condition=>{const end=Date.now()+10000;while(!condition()){if(Date.now()>end)throw Error('ACCEPTANCE_WAIT_TIMEOUT');await new Promise(r=>setTimeout(r,20));}};
const last=()=>JSON.parse(fs.readFileSync(path.join(evidence,'latest.json')));
const once=(label,mode,expected)=>{
 fs.writeFileSync(control,mode);const r=spawnSync(process.execPath,[script,'--once',evidence,...options],{cwd:repo,env,encoding:'utf8',timeout:10000});
 const report=last();check(report.status===expected,label+': '+JSON.stringify(report));check(report.sourceSha===head&&report.sourceUnchanged,label+': source drift');
 runs.push({label,exit:r.status,...report});
};
once('healthy baseline','pass','ALL_EXECUTED_PASS');once('deliberate failing test','fail','FAILED');once('successful retry after failed test','pass','ALL_EXECUTED_PASS');once('real deadline exhaustion','wait','FAILED');check(last().error==='TIMEOUT','missing actual timeout');once('successful retry after timeout','pass','ALL_EXECUTED_PASS');
for(const signal of ['SIGTERM','SIGKILL']){
 fs.writeFileSync(control,'wait');const child=spawn(process.execPath,[script,'--once',evidence,...options],{cwd:repo,env,stdio:'ignore'});
 const lease=path.join(repo,'.git/continuum-health-worker.json');await waitFor(()=>{try{return Boolean(JSON.parse(fs.readFileSync(lease)).child);}catch{return false;}});
 const stopped=new Promise(r=>child.once('exit',(code,sig)=>r({code,signal:sig})));child.kill(signal);const termination=await stopped;
 if(signal==='SIGTERM'){check(last().status==='INTERRUPTED','TERM not documented');runs.push({label:'controlled SIGTERM',termination,...last()});}
 else runs.push({label:'controlled SIGKILL',termination,cycleCompleted:false});
 once('restart after '+signal,'pass','ALL_EXECUTED_PASS');if(signal==='SIGKILL')check(last().recoveredLease,'KILL lease not recovered');
}
fs.writeFileSync(control,'fail');const previous=last().id;const loop=spawn(process.execPath,[script,'--loop',evidence,...options],{cwd:repo,env,stdio:'ignore'});const ended=new Promise(r=>loop.once('exit',r));
await waitFor(()=>last().id!==previous&&last().status==='FAILED');const failed=last();fs.writeFileSync(control,'pass');await ended;check(last().status==='ALL_EXECUTED_PASS','scheduler did not continue after failure');
runs.push({label:'same scheduler first failed cycle',...failed},{label:'same scheduler successful next cycle',...last()});
check(git('status','--porcelain')==='','fixture source changed');git('bundle','create',path.join(out,'public-source.bundle'),'HEAD');
const report={schema:'continuum-worker-native-acceptance-v1',controllerSourceSha:execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),fixtureSourceSha:head,workerBlobs,node:process.version,passed:true,realProcesses:true,androidDevice:false,modelInference:false,learningSignalsProduced:0,automaticPatchPromotion:false,runs};
fs.writeFileSync(path.join(out,'acceptance.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({passed:true,controller:report.controllerSourceSha,fixture:head,recordedEvents:runs.length,sourceUnchanged:true}));
