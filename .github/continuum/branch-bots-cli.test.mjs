import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn,spawnSync} from 'node:child_process';
import {setTimeout as pause} from 'node:timers/promises';
import {createHash} from 'node:crypto';
import {BacklogStore} from './development-backlog.mjs';

const cli=fileURLToPath(new URL('./branch-bots-cli.mjs',import.meta.url));
function fixture(t){
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-branch-cli-')),root=path.join(temp,'repo'),state=path.join(temp,'state');fs.mkdirSync(root);
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:10000});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 const write=(name,value)=>{const file=path.join(root,name);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,value);};
 const commit=msg=>{git('add','.');git('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm',msg);return git('rev-parse','HEAD');};
 git('init','-q','-b','target');write('lib/base.mjs','export const base=1;\n');const base=commit('base');
 git('switch','-qc','ready');write('lib/ready.mjs','export const ready=1;\n');const source=commit('ready');git('switch','-q','target');
 t.after(()=>fs.rmSync(temp,{recursive:true,force:true}));
 const run=(...args)=>spawnSync(process.execPath,[cli,'--root',root,'--state',state,...args],{encoding:'utf8',timeout:15000});
 return {root,state,base,source,git,write,commit,run};
}

test('one-shot bot persists genuine source differences and only canonical proposed work',t=>{
 const f=fixture(t),r=f.run('--once');assert.equal(r.status,0,r.stderr);
 const result=JSON.parse(r.stdout);assert.equal(result.mode,'LOCAL_BRANCH_BOTS');assert.equal(result.cycle.queue.proposed,1);
 const report=JSON.parse(fs.readFileSync(result.cycle.report,'utf8'));
 assert.equal(createHash('sha256').update(fs.readFileSync(result.cycle.report)).digest('hex'),result.cycle.reportSha256);
 assert.equal(report.targetCommit,f.base);assert.equal(report.branches.find(x=>x.sourceCommit===f.source).missingCount,1);
 assert.equal(report.networkFetch,false);assert.equal(report.apk,'NOT_RUN');
 assert.equal(typeof report.apkEnvironment.hostReadyToAttemptQa,'boolean');
 assert.ok(Array.isArray(report.apkEnvironment.blockers));
 assert.equal(report.apkEnvironment.androidCompilationExecuted,false);
 assert.equal(report.apkEnvironment.androidTestExecuted,false);assert.equal(report.apkEnvironment.apkSigned,false);
 assert.deepEqual(result.cycle.apkEnvironment,report.apkEnvironment);
 assert.equal(new BacklogStore(path.join(f.state,'development-backlog.json')).read().doc.tasks[0].state,'PROPOSED');
 assert.equal(f.git('status','--porcelain'),'');assert.equal(f.git('rev-parse','HEAD'),f.base);
 const replay=f.run('--once');assert.equal(replay.status,0,replay.stderr);assert.equal(JSON.parse(replay.stdout).cycle.queue.proposed,0);
 assert.equal(JSON.parse(replay.stdout).cycle.report,result.cycle.report);
});

test('CLI restores installed Termux paths for diagnosis and keeps missing tools blocked',t=>{
 const f=fixture(t),home=path.join(path.dirname(f.root),'owner-home'),java=path.join(home,'jdk'),sdk=path.join(home,'android-sdk');
 const gradle=path.join(home,'continuum-toolchains','gradle-8.11.1','bin','gradle'),aapt2=path.join(home,'aapt2');
 fs.mkdirSync(path.join(java,'bin'),{recursive:true});fs.mkdirSync(path.dirname(gradle),{recursive:true});
 fs.mkdirSync(path.join(sdk,'platforms','android-36'),{recursive:true});
 fs.writeFileSync(path.join(sdk,'platforms','android-36','android.jar'),'fixture only');fs.writeFileSync(aapt2,'fixture only');
 const guard='#!/bin/sh\ncase "$PATH" in "$JAVA_HOME/bin:"*) ;; *) exit 71 ;; esac\n';
 fs.writeFileSync(path.join(java,'bin','java'),guard+'printf \'openjdk version "17.0.1"\\n\' >&2\n',{mode:0o700});
 fs.writeFileSync(path.join(java,'bin','javac'),guard+'printf \'javac 17.0.1\\n\'\n',{mode:0o700});
 fs.writeFileSync(gradle,guard+'[ "$ANDROID_HOME" = "$HOME/android-sdk" ] && [ "$ANDROID_SDK_ROOT" = "$ANDROID_HOME" ] || exit 72\nprintf \'Gradle 8.11.1\\n\'\n',{mode:0o700});
 // Only the architecture discriminator is simulated; these fixtures never build Android.
 const preload=path.join(home,'termux-arch.mjs');fs.writeFileSync(preload,"Object.defineProperty(process,'arch',{value:'arm64'});\n");
 const env={...process.env,HOME:home,PREFIX:'/data/data/com.termux/files/usr',JAVA_HOME:java,CONTINUUM_AAPT2_PATH:aapt2};
 delete env.ANDROID_HOME;delete env.ANDROID_SDK_ROOT;delete env.CONTINUUM_GRADLE_PATH;
 const before={...process.env},input={...env};
 const run=toolEnv=>spawnSync(process.execPath,['--import',preload,cli,'--root',f.root,'--state',f.state,'--once'],{
  encoding:'utf8',timeout:15000,env:toolEnv});
 const r=run(env);assert.equal(r.status,0,r.stderr);
 const cycle=JSON.parse(r.stdout).cycle,reportBytes=fs.readFileSync(cycle.report),report=JSON.parse(reportBytes);
 assert.deepEqual(cycle.apkEnvironment.blockers,[]);assert.equal(cycle.apkEnvironment.hostReadyToAttemptQa,true);
 assert.equal(cycle.apk,'NOT_RUN');assert.equal(cycle.executedTests,0);assert.equal(cycle.approvedForMerge,false);
 assert.equal(cycle.apkEnvironment.androidCompilationExecuted,false);assert.equal(cycle.apkEnvironment.androidTestExecuted,false);
 assert.equal(cycle.apkEnvironment.apkSigned,false);assert.deepEqual(report.apkEnvironment,cycle.apkEnvironment);
 assert.equal(createHash('sha256').update(reportBytes).digest('hex'),cycle.reportSha256);
 assert.equal(new BacklogStore(path.join(f.state,'development-backlog.json')).read().doc.tasks[0].state,'PROPOSED');
 const missing=run({...env,HOME:path.join(home,'missing-tools')});assert.equal(missing.status,0,missing.stderr);
 const unavailable=JSON.parse(missing.stdout).cycle.apkEnvironment;
 assert.equal(unavailable.hostReadyToAttemptQa,false);
 assert.ok(unavailable.blockers.includes('SDK36_NOT_FOUND'));assert.ok(unavailable.blockers.includes('GRADLE8111_NOT_FOUND'));
 assert.equal(f.git('status','--porcelain'),'');assert.equal(f.git('rev-parse','HEAD'),f.base);
 assert.deepEqual({...process.env},before);assert.deepEqual(env,input);
});

test('finite loop uses the existing durable scheduler and stops at the approved budget',t=>{
 const f=fixture(t),r=f.run('--loop','--max-cycles','1','--interval-minutes','1');assert.equal(r.status,0,r.stderr);
 const result=JSON.parse(r.stdout);assert.equal(result.loop.cycles,1);assert.equal(result.loop.state,'LIMIT_REACHED');
 const checkpoint=JSON.parse(fs.readFileSync(path.join(f.state,'branch-bots-loop.json'),'utf8'));
 assert.equal(checkpoint.schema,'continuum-local-loop-checkpoint');assert.equal(checkpoint.source.head,f.base);assert.equal(checkpoint.heartbeat.cycles,1);
 const replay=f.run('--loop','--max-cycles','1','--interval-minutes','1');assert.equal(replay.status,0,replay.stderr);
 assert.equal(JSON.parse(replay.stdout).loop.resumed,true);assert.equal(JSON.parse(replay.stdout).loop.cycles,1);
});

test('CLI refuses unknown, duplicate and unbounded modes before creating state',t=>{
 const f=fixture(t);
 for(const args of [[],['--loop'],['--once','--loop'],['--once','--once'],['--once','--shell','echo'],['--once','--max-cycles','2'],['--loop','--max-cycles','0']]){
  const r=f.run(...args);assert.notEqual(r.status,0);assert.match(r.stderr,/BRANCH_BOTS_(?:INVALID|UNKNOWN|DUPLICATE|MODE|FINITE|LOOP)/);assert.equal(fs.existsSync(f.state),false);
 }
});

test('CLI incomplete coverage is nonzero and never reports a completed full inventory',t=>{
 const f=fixture(t);f.git('switch','-qc','other');f.write('lib/other.mjs','export const other=1;\n');f.commit('other');f.git('switch','-q','target');
 const r=f.run('--once','--max-sources','1');assert.equal(r.status,1,r.stderr);
 const result=JSON.parse(r.stdout);assert.equal(result.cycle.complete,false);assert.equal(result.cycle.outcome,'ERROR');
});

test('resumable single passes reach later branches across process restarts',t=>{
 const f=fixture(t),sources=[f.source];
 for(const name of ['alpha','bravo','charlie','delta']){
  f.git('switch','-qc',name);f.write('lib/'+name+'.mjs','export const value=1;\n');
  sources.push(f.commit(name));f.git('switch','-q','target');
 }
 const seen=[];
 for(let i=0;i<3;i++){
  const r=f.run('--once','--continue-scan','--max-sources','2');assert.equal(r.status,0,r.stderr);
  const cycle=JSON.parse(r.stdout).cycle;
  assert.equal(cycle.complete,false);assert.equal(cycle.pageComplete,true);
  assert.equal(cycle.coverage.sourceOffset,i*2);assert.equal(cycle.scanRestarted,false);
  const report=JSON.parse(fs.readFileSync(cycle.report,'utf8'));
  seen.push(...report.branches.map(x=>x.sourceCommit));
  const saved=JSON.parse(fs.readFileSync(path.join(f.state,'branch-bots-scan.json'),'utf8'));
  assert.equal(saved.lastReportSha256,cycle.reportSha256);assert.deepEqual(saved.nextCursor,report.nextCursor);
 }
 assert.deepEqual(seen.sort(),sources.sort());
 const next=f.run('--once','--continue-scan','--max-sources','2');assert.equal(next.status,0,next.stderr);
 assert.equal(JSON.parse(next.stdout).cycle.coverage.sourceOffset,0);
 const tasks=new BacklogStore(path.join(f.state,'development-backlog.json')).read().doc.tasks;
 assert.equal(tasks.length,5);assert.ok(tasks.every(x=>x.state==='PROPOSED'&&x.subject.automaticExecution===false));
 assert.equal(f.git('status','--porcelain'),'');assert.equal(f.git('rev-parse','HEAD'),f.base);
});

test('finite scheduler records successful pages without declaring a full inventory',t=>{
 const f=fixture(t);f.git('switch','-qc','alpha');f.write('lib/alpha.mjs','export const alpha=1;\n');
 f.commit('alpha');f.git('switch','-q','target');
 const run=f.run('--loop','--max-cycles','1','--max-sources','1');assert.equal(run.status,0,run.stderr);
 const first=JSON.parse(run.stdout);
 assert.equal(first.loop.cycles,1);assert.equal(first.loop.failures,0);assert.equal(first.loop.state,'LIMIT_REACHED');
 assert.equal(first.cycle.complete,false);assert.equal(first.cycle.coverage.sourceOffset,0);
 const resumed=f.run('--once','--continue-scan','--max-sources','1');assert.equal(resumed.status,0,resumed.stderr);
 const second=JSON.parse(resumed.stdout).cycle;
 assert.equal(second.coverage.sourceOffset,1);assert.equal(second.complete,false);
 assert.equal(second.nextCursor,null);assert.equal(second.approvedForMerge,false);assert.equal(second.apk,'NOT_RUN');
});

test('changed refs restart source paging while crash replay deduplicates proposals',t=>{
 const f=fixture(t);f.git('switch','-qc','alpha');f.write('lib/alpha.mjs','export const alpha=1;\n');
 f.commit('alpha');f.git('switch','-q','target');
 const args=['--once','--continue-scan','--max-sources','1'];
 const first=f.run(...args);assert.equal(first.status,0,first.stderr);
 const saved=path.join(f.state,'branch-bots-scan.json');fs.unlinkSync(saved);
 const replay=f.run(...args);assert.equal(replay.status,0,replay.stderr);
 assert.equal(JSON.parse(replay.stdout).cycle.queue.proposed,0);
 f.git('update-ref','refs/archive/source-alias',f.source);
 const changed=f.run(...args);assert.equal(changed.status,0,changed.stderr);
 const cycle=JSON.parse(changed.stdout).cycle;
 assert.equal(cycle.scanRestarted,true);assert.equal(cycle.coverage.sourceOffset,0);
 assert.equal(cycle.complete,false);assert.equal(cycle.executedTests,0);
});

test('scan state rejects corruption, changed settings and altered report bytes',t=>{
 const f=fixture(t),args=['--once','--continue-scan'];
 const first=f.run(...args);assert.equal(first.status,0,first.stderr);
 const file=path.join(f.state,'branch-bots-scan.json'),bytes=fs.readFileSync(file);
 const queue=fs.readFileSync(path.join(f.state,'development-backlog.json'));
 fs.writeFileSync(file,'{"broken":true}\n');
 const corrupt=f.run(...args);assert.equal(corrupt.status,1);assert.match(corrupt.stderr,/BRANCH_BOTS_SCAN_STATE_INVALID/);
 fs.writeFileSync(file,bytes);
 const changed=f.run(...args,'--max-sources','1');assert.equal(changed.status,1);assert.match(changed.stderr,/BRANCH_BOTS_SCAN_CONFIG_CHANGED/);
 assert.deepEqual(fs.readFileSync(file),bytes);
 const report=JSON.parse(first.stdout).cycle.report;fs.appendFileSync(report,' ');
 const tampered=f.run(...args);assert.equal(tampered.status,1);assert.match(tampered.stderr,/BRANCH_BOTS_SCAN_REPORT_CHANGED/);
 assert.deepEqual(fs.readFileSync(file),bytes);assert.deepEqual(fs.readFileSync(path.join(f.state,'development-backlog.json')),queue);
});

test('scan state cannot follow links or steal a live existing scheduler lock',t=>{
 const f=fixture(t),args=['--once','--continue-scan'];
 const first=f.run(...args);assert.equal(first.status,0,first.stderr);
 const file=path.join(f.state,'branch-bots-scan.json'),bytes=fs.readFileSync(file);
 const outside=path.join(path.dirname(f.root),'outside.json');fs.writeFileSync(outside,bytes,{mode:0o600});
 fs.unlinkSync(file);fs.symlinkSync(outside,file);
 const linked=f.run(...args);assert.equal(linked.status,1);assert.match(linked.stderr,/UNSAFE_STATE_FILE/);
 assert.deepEqual(fs.readFileSync(outside),bytes);fs.unlinkSync(file);fs.writeFileSync(file,bytes,{mode:0o600});
 const lock=file+'.lock';fs.mkdirSync(lock,{mode:0o700});
 const owner=JSON.stringify({pid:process.pid,host:os.hostname(),nonce:'b'.repeat(32)});
 fs.writeFileSync(path.join(lock,'owner.json'),owner,{mode:0o600});
 const busy=f.run(...args);assert.equal(busy.status,1);assert.match(busy.stderr,/Local loop already running/);
 assert.equal(fs.readFileSync(path.join(lock,'owner.json'),'utf8'),owner);assert.deepEqual(fs.readFileSync(file),bytes);
});

test('partial paths remain errors while subsequent pages keep progressing',t=>{
 const f=fixture(t);f.git('switch','-qc','alpha');f.write('lib/a.mjs','export const a=1;\n');
 f.write('lib/b.mjs','export const b=1;\n');f.commit('two paths');f.git('switch','-q','target');
 const args=['--once','--continue-scan','--max-sources','1','--max-changes','1'];
 const partial=f.run(...args);assert.equal(partial.status,1,partial.stderr);
 const first=JSON.parse(partial.stdout).cycle;
 assert.equal(first.outcome,'ERROR');assert.equal(first.pageComplete,false);assert.equal(first.complete,false);
 const next=f.run(...args);assert.equal(next.status,0,next.stderr);
 const last=JSON.parse(next.stdout).cycle;
 assert.equal(last.coverage.sourceOffset,1);assert.equal(last.pageComplete,true);assert.equal(last.complete,false);
 assert.equal(last.nextCursor,null);
});

test('proposal budget deferrals are recovered on the next source sweep',t=>{
 const f=fixture(t);
 for(const name of ['alpha','bravo']){
  f.git('switch','-qc',name);f.write('lib/'+name+'.mjs','export const value=1;\n');f.commit(name);f.git('switch','-q','target');
 }
 const args=['--once','--continue-scan','--max-sources','2','--max-proposals','1'];
 const cycles=[];
 for(let i=0;i<3;i++){const r=f.run(...args);assert.equal(r.status,0,r.stderr);cycles.push(JSON.parse(r.stdout).cycle);}
 assert.deepEqual(cycles.map(x=>x.coverage.sourceOffset),[0,2,0]);
 assert.deepEqual(cycles.map(x=>x.queue.proposed),[1,1,1]);assert.equal(cycles[0].queue.deferred,1);
 const tasks=new BacklogStore(path.join(f.state,'development-backlog.json')).read().doc.tasks;
 assert.equal(tasks.length,3);assert.ok(tasks.every(x=>x.state==='PROPOSED'));
});

test('termination during diagnosis leaves resumable state and queue unadvanced',async t=>{
 const f=fixture(t),home=path.join(path.dirname(f.root),'jdk'),marker=path.join(home,'started');
 fs.mkdirSync(path.join(home,'bin'),{recursive:true});
 fs.writeFileSync(path.join(home,'bin','java'),'#!/bin/sh\nprintf ready > "$JAVA_HOME/started"\nsleep 0.3\nprintf \'openjdk version "17.0.1"\\n\' >&2\n',{mode:0o700});
 fs.writeFileSync(path.join(home,'bin','javac'),'#!/bin/sh\nprintf \'javac 17.0.1\\n\'\n',{mode:0o700});
 const child=spawn(process.execPath,[cli,'--root',f.root,'--state',f.state,'--once','--continue-scan'],{
  env:{...process.env,JAVA_HOME:home},stdio:['ignore','pipe','pipe']});
 let stdout='',stderr='';child.stdout.on('data',x=>stdout+=x);child.stderr.on('data',x=>stderr+=x);
 const done=new Promise((resolve,reject)=>{child.on('error',reject);child.on('close',(code,signal)=>resolve({code,signal}));});
 try{
  for(let i=0;i<100&&!fs.existsSync(marker);i++)await pause(20);
  assert.equal(fs.existsSync(marker),true,stderr);child.kill('SIGTERM');
  const result=await done;assert.equal(result.code,1,stderr);assert.equal(result.signal,null);
  assert.equal(JSON.parse(stdout).cycle.outcome,'INTERRUPTED');
  for(const name of ['branch-bots-scan.json','branch-bots-scan.json.lock','development-backlog.json'])
   assert.equal(fs.existsSync(path.join(f.state,name)),false,name);
 }finally{if(child.exitCode===null&&child.signalCode===null)child.kill('SIGKILL');await done;}
});

test('CLI cannot write through a state path into the source checkout',t=>{
 const f=fixture(t),r=spawnSync(process.execPath,[cli,'--root',f.root,'--state',path.join(f.root,'state'),'--once'],{encoding:'utf8',timeout:10000});
 assert.notEqual(r.status,0);assert.match(r.stderr,/STATE_INSIDE_SOURCE/);assert.equal(f.git('status','--porcelain'),'');
});

test('CLI rejects a configuration mask before the existing scheduler can inspect source',t=>{
 const f=fixture(t),r=spawnSync(process.execPath,[cli,'--root',f.root,'--state',f.state,'--loop','--max-cycles','1'],{
  encoding:'utf8',timeout:10000,env:{...process.env,GIT_CONFIG:'/dev/null'}});
 assert.equal(r.status,1);assert.match(r.stderr,/BRANCH_BOTS_INVALID_GIT_ENVIRONMENT/);
 assert.equal(fs.existsSync(f.state),false);
});
