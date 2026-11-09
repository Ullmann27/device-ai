/** Control-plane software contracts. These fixtures never load a language model,
 * claim an isolated test execution, or attest local coding intelligence. */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import childProcess from 'node:child_process';
import {syncBuiltinESMExports} from 'node:module';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {initializeCodingService,loadCodingServiceConfig,plainBytes,privateJson,writePrivateJson,
 privateDirectory,readDeliveries,saveDeliveries,serviceEnvironment,linuxProcessIdentity,sameLiveProcess,
 profileClasspathDigest,assertProfileBridge,serviceParticipants,serviceTaskReviewSha256,assertServiceReviewBinding} from './coding-service-store.mjs';
import {canonical,sha256Hex} from './development-backlog.mjs';
import {createCodingServiceReader} from './coding-service-reader.mjs';
import {LOCAL_MODEL_RUNTIME_CANDIDATES} from '../../scripts/continuum-local-model-pins.mjs';

function fixture(t){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-service-contract-'));
 t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 const project=path.join(root,'project'),profiles=path.join(root,'profiles'),classes=path.join(root,'classes'),state=path.join(root,'state');
 for(const p of [project,profiles,classes,state])fs.mkdirSync(p,{mode:0o700});
 fs.writeFileSync(path.join(classes,'ContractOnly.class'),'not bytecode; not invoked');
 const pin=LOCAL_MODEL_RUNTIME_CANDIDATES[0],runtimeEnvPath=path.join(root,'runtime.json'),manifest=path.join(root,'manifest.json');
 fs.writeFileSync(manifest,'{"contractFixture":true}');
 const runtime={CONTINUUM_LOCAL_BRAIN_MODEL:pin.modelRepository,CONTINUUM_LOCAL_BRAIN_MODEL_PATH:path.join(root,'not-loaded.gguf'),
  CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST:manifest,CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT:'gguf',CONTINUUM_LOCAL_BRAIN_MODEL_SHA256:pin.modelSha256,
  CONTINUUM_LOCAL_BRAIN_RUNTIME:'llama.cpp',CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND:'LLAMA_CPP',
  CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH:path.join(root,'not-run'),CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256:pin.runtimeSha256,
  CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL:'OWNER_APPROVED_ALTERNATE_GGUF'};
 fs.writeFileSync(runtimeEnvPath,JSON.stringify(runtime));
 const isolationConfig=path.join(root,'isolation.json');fs.writeFileSync(isolationConfig,'{}');
 const configPath=path.join(state,'config.json');
 const args={configPath,project,profileDirectory:profiles,profileClasspath:classes,runtimeEnvPath,isolationConfig,maxQueue:2,maxRecords:4,maxCycles:3};
 return {root,classes,state,project,profiles,configPath,args,runtimeEnvPath,runtime};
}
test('private durable config keeps service, project, model and profile identities separate',t=>{
 const f=fixture(t);const initial=initializeCodingService(f.args),config=loadCodingServiceConfig(f.configPath);
 assert.equal(initial.status,'INITIALIZED');assert.equal(initial.modelLoaded,false);
 assert.equal(config.project,f.project);assert.equal(config.profileDirectory,f.profiles);
 assert.match(config.configSha256,/^[a-f0-9]{64}$/);assert.match(config.profileClasspathSha256,/^[a-f0-9]{64}$/);
 for(const file of [f.configPath,path.join(f.state,'deliveries.json')])assert.equal(fs.statSync(file).mode&0o777,0o600);
 for(const name of ['ccc1','sessions','public-evidence'])assert.equal(fs.statSync(path.join(f.state,name)).mode&0o777,0o700);
 assert.throws(()=>initializeCodingService(f.args),/EEXIST/);
});
test('read-only status leaves unstarted state byte-identical and cannot create canonical keys',t=>{
 const f=fixture(t);initializeCodingService(f.args);
 const before=fs.readFileSync(path.join(f.state,'deliveries.json'));
 const reader=createCodingServiceReader({configPath:f.configPath});
 assert.equal(reader.getStatus().state,'STOPPED');assert.equal(reader.getStatus().maxConcurrentInference,1);
 assert.deepEqual(reader.listTasks(),{items:[],nextCursor:null});
 assert.deepEqual(fs.readFileSync(path.join(f.state,'deliveries.json')),before);
 assert.equal(fs.existsSync(path.join(f.state,'ccc1','ccc1-runtime.key')),false);
 assert.throws(()=>reader.getEvidence({taskId:'../secret',evidenceId:'result'}),/TASK_ID_INVALID/);
});
test('public permissions, symlinks, hardlinks and oversized private files fail closed',t=>{
 const f=fixture(t),file=path.join(f.state,'value.json');writePrivateJson(file,{a:1},{exclusive:true});
 assert.deepEqual(privateJson(file),{a:1});
 fs.chmodSync(file,0o644);assert.throws(()=>privateJson(file),/FILE_UNSAFE/);fs.chmodSync(file,0o600);
 fs.linkSync(file,path.join(f.state,'second.json'));assert.throws(()=>privateJson(file),/FILE_UNSAFE/);
 fs.unlinkSync(path.join(f.state,'second.json'));
 fs.symlinkSync(file,path.join(f.state,'link.json'));assert.throws(()=>privateJson(path.join(f.state,'link.json')));
 assert.throws(()=>plainBytes(file,2),/FILE_UNSAFE/);
 fs.chmodSync(f.state,0o755);assert.throws(()=>privateDirectory(f.state),/NOT_PRIVATE/);
});
test('runtime configuration rejects unknown cloud controls and nonreviewed model hashes',t=>{
 const f=fixture(t);
 fs.writeFileSync(f.runtimeEnvPath,JSON.stringify({...f.runtime,CONTINUUM_REMOTE_BRAIN_API_KEY:'not-a-real-secret'}));
 assert.throws(()=>initializeCodingService(f.args),/RUNTIME_CONFIG_INVALID/);
 fs.writeFileSync(f.runtimeEnvPath,JSON.stringify({...f.runtime,CONTINUUM_LOCAL_BRAIN_MODEL_SHA256:'1'.repeat(64)}));
 assert.throws(()=>initializeCodingService(f.args),/REVIEWED_RUNTIME_REQUIRED/);
 assert.equal(fs.existsSync(f.configPath),false);
});
test('bounded inbox rejects duplicate identities, unknown delivery states and excess records',t=>{
 const f=fixture(t);initializeCodingService(f.args);const config=loadCodingServiceConfig(f.configPath);
 const d={id:'owner-task',fingerprint:'a'.repeat(64),reviewSha256:'b'.repeat(64),ccc1TaskId:null,delivery:'UNKNOWN'};
 saveDeliveries(config,[d]);assert.equal(readDeliveries(config)[0].delivery,'UNKNOWN');
 saveDeliveries(config,[d,d]);assert.throws(()=>readDeliveries(config),/INBOX_CORRUPT/);
 saveDeliveries(config,[{...d,delivery:'SUCCESS'}]);assert.throws(()=>readDeliveries(config),/INBOX_CORRUPT/);
 saveDeliveries(config,Array.from({length:5},(_,i)=>({...d,id:'owner-'+i})));assert.throws(()=>readDeliveries(config),/INBOX_CORRUPT/);
});
test('profile bridge code change invalidates its pinned artifact',t=>{
 const f=fixture(t);initializeCodingService(f.args);const config=loadCodingServiceConfig(f.configPath);
 assert.equal(profileClasspathDigest(f.classes),config.profileClasspathSha256);assertProfileBridge(config);
 fs.writeFileSync(path.join(f.classes,'ContractOnly.class'),'different untrusted bytes');
 assert.throws(()=>assertProfileBridge(config),/CLASSPATH_CHANGED/);
 assert.throws(()=>createCodingServiceReader({configPath:f.configPath}).listAgents(),/CLASSPATH_CHANGED/);
});
test('source/profile overlap and artificial unbounded queue limits are rejected',t=>{
 const f=fixture(t);
 assert.throws(()=>initializeCodingService({...f.args,maxQueue:33}),/LIMIT_INVALID/);
 assert.throws(()=>initializeCodingService({...f.args,maxCycles:0}),/LIMIT_INVALID/);
 assert.throws(()=>initializeCodingService({...f.args,configPath:path.join(f.project,'config.json')}),/OVERLAPS/);
});
test('inherited cloud flags and Node injection never reach the worker environment',t=>{
 const f=fixture(t);initializeCodingService(f.args);const config=loadCodingServiceConfig(f.configPath);
 const saved=process.env.NODE_OPTIONS;process.env.NODE_OPTIONS='--require=/untrusted.js';
 try{const env=serviceEnvironment(config);assert.equal(env.NODE_OPTIONS,undefined);assert.equal(env.CONTINUUM_MOMEGA1,'1');
  assert.equal(env.CONTINUUM_CODING_AGENT_LOOP,undefined);assert.equal(env.CONTINUUM_REMOTE_BRAIN_API_KEY,undefined);
 }finally{if(saved===undefined)delete process.env.NODE_OPTIONS;else process.env.NODE_OPTIONS=saved;}
});
test('process identity binds boot and kernel start ticks, never a PID alone',()=>{
 const identity=linuxProcessIdentity();assert.equal(sameLiveProcess(identity),true);
 assert.equal(sameLiveProcess({pid:process.pid}),false);
 assert.equal(sameLiveProcess({...identity,startTicks:String(BigInt(identity.startTicks)+1n)}),false);
 assert.equal(sameLiveProcess({...identity,bootId:'00000000-0000-0000-0000-000000000000'}),false);
});

test('explicit participant selection is strict, immutable and bound to the legacy-compatible owner review',()=>{
 const legacy={builderId:'bot-forge',verifierId:'bot-lint',reporterId:'bot-echo'};
 assert.deepEqual(serviceParticipants(),legacy);assert.ok(Object.isFrozen(serviceParticipants()));
 const selected={builderId:'bot-mira',verifierId:'bot-atlas',reporterId:'bot-mnemos'};
 for(const bad of [null,[],{}, {...selected,verifierId:selected.builderId},{...selected,reporterId:selected.verifierId},
  {...selected,builderId:'../bot'}, {...selected,reporterId:null},{...selected,tools:['shell']}]){
  assert.throws(()=>serviceParticipants(bad),/SERVICE_PARTICIPANTS_INVALID/);
 }
 const config={configSha256:'c'.repeat(64),project:'/authorized/project',sourceCommit:'a'.repeat(40)},fingerprint='f'.repeat(64);
 assert.equal(serviceTaskReviewSha256(config,fingerprint),
  sha256Hex(canonical({config:config.configSha256,project:config.project,task:fingerprint,source:config.sourceCommit})));
 const delivery={fingerprint,participants:selected,reviewSha256:serviceTaskReviewSha256(config,fingerprint,selected)};
 assertServiceReviewBinding(config,delivery);
 for(const participants of [undefined,legacy,{...selected,builderId:selected.verifierId,verifierId:selected.builderId}]){
  assert.throws(()=>assertServiceReviewBinding(config,{...delivery,participants}),/SERVICE_TASK_REVIEW_CHANGED/);
 }
 assert.throws(()=>assertServiceReviewBinding({...config,configSha256:'d'.repeat(64)},delivery),/SERVICE_TASK_REVIEW_CHANGED/);
});

test('selected owner CLI roles survive admission/restart and cannot change the approved canonical task',async t=>{
 const f=fixture(t),source=fileURLToPath(new URL('../..',import.meta.url)),trustedRoot=path.join(f.root,'trusted-source');
 fs.mkdirSync(trustedRoot);
 for(const name of ['.github','scripts','tools'])fs.cpSync(path.join(source,name),path.join(trustedRoot,name),{recursive:true});
 const git=(root,...args)=>{
  const result=childProcess.spawnSync('git',args,{cwd:root,encoding:'utf8',shell:false});
  assert.equal(result.status,0,result.stderr);return result.stdout.trim();
 };
 for(const root of [trustedRoot,f.project]){
  if(root===f.project){fs.mkdirSync(path.join(root,'lib'));fs.writeFileSync(path.join(root,'lib','offset.mjs'),'export const offset=(a,b)=>a-b;\n');}
  git(root,'init','-q','-b','service-participant-fixture');git(root,'add','.');
  git(root,'-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','software contract fixture');
 }
 const store=await import(pathToFileURL(path.join(trustedRoot,'.github/continuum/coding-service-store.mjs')));
 store.initializeCodingService(f.args);
 const config=store.loadCodingServiceConfig(f.configPath);
 const {codingServiceCli:cli}=await import(pathToFileURL(path.join(trustedRoot,'scripts/continuum-coding-service.mjs')));
 const {createCodingServiceReader:readerOf}=await import(pathToFileURL(path.join(trustedRoot,'.github/continuum/coding-service-reader.mjs')));
 const selected={builderId:'bot-mira',verifierId:'bot-atlas',reporterId:'bot-mnemos'},legacy=serviceParticipants();
 const profiles=new Map([...Object.values(legacy),...Object.values(selected)].map(agentId=>[agentId,{
  agentId,workspaceId:'workspace-'+agentId,resourceBudget:{maxModelCalls:1,deadlineMillis:120_000}}]));
 // Metadata-port double only. No JVM, model, executor or competence is attested.
 const nativeSpawn=childProcess.spawnSync;
 t.mock.method(childProcess,'spawnSync',(command,args,options)=>{
  if(command!=='java')return nativeSpawn(command,args,options);
  assert.deepEqual(args.slice(0,4),['-Xmx128m','-cp',f.classes,'at.heinz.deviceai.chief.HandoffHostChecks']);
  const value=args[4]==='get'?profiles.get(args[6])??null:{items:[...profiles.values()],nextAfterId:null};
  return {status:0,stdout:JSON.stringify(value),stderr:''};
 });

 syncBuiltinESMExports();t.after(()=>{t.mock.restoreAll();syncBuiltinESMExports();});
 const task={schema:'continuum-coding-task',version:1,id:'selected-owner-task',sourceCommit:git(f.project,'rev-parse','HEAD'),
  kind:'BUGFIX',goal:'Repair a synthetic offset function',allowedPaths:['lib/offset.mjs'],acceptance:['offset(2,3) is 5'],
  chain:'node',limits:{maxModelCalls:1,maxContextChars:4096}};
 const taskFile=path.join(f.root,'task.json');fs.writeFileSync(taskFile,JSON.stringify(task));
 const args=['--config',f.configPath,'--task',taskFile];
 const roles=['--builder',selected.builderId,'--verifier',selected.verifierId,'--reporter',selected.reporterId];
 const review=await cli(['prepare',...args,...roles]),old=await cli(['prepare',...args]);
 assert.deepEqual(review.participants,selected);assert.deepEqual(old.participants,legacy);
 assert.notEqual(review.reviewSha256,old.reviewSha256);

 await t.test('missing, unknown and nondistinct identities never reserve a task',async()=>{
  await assert.rejects(cli(['prepare',...args,'--builder',selected.builderId]),/SERVICE_PARTICIPANTS_ALL_REQUIRED/);
  await assert.rejects(cli(['prepare',...args,'--builder','bot-missing','--verifier','bot-atlas','--reporter','bot-mnemos']),
   /BOT_ASSIGNMENT_PROFILE_UNAVAILABLE/);
  await assert.rejects(cli(['prepare',...args,'--builder','bot-atlas','--verifier','bot-atlas','--reporter','bot-mnemos']),
   /SERVICE_PARTICIPANTS_INVALID/);
  assert.deepEqual(store.readDeliveries(config),[]);
 });
 await t.test('changed or omitted roles cannot spend an earlier review',async()=>{
  await assert.rejects(cli(['submit',...args,'--approve',review.reviewSha256]),/SERVICE_EXPLICIT_OWNER_REVIEW_REQUIRED/);
  await assert.rejects(cli(['submit',...args,...roles,'--approve',old.reviewSha256]),/SERVICE_EXPLICIT_OWNER_REVIEW_REQUIRED/);
  assert.deepEqual(store.readDeliveries(config),[]);
 });
 await t.test('exact review admits once, preserves the journal after reopen and has no model result',async()=>{
  const admitted=await cli(['submit',...args,...roles,'--approve',review.reviewSha256]);
  assert.equal(admitted.delivery,'PENDING');assert.equal(admitted.executed,false);
  const journal=path.join(f.state,'ccc1','tasks',admitted.canonicalTaskId+'.jsonl'),bytes=fs.readFileSync(journal);
  const duplicate=await cli(['submit',...args,...roles,'--approve',review.reviewSha256]);
  assert.equal(duplicate.duplicate,true);assert.equal(duplicate.canonicalTaskId,admitted.canonicalTaskId);
  const persisted=store.readDeliveries(config)[0];assert.deepEqual(persisted.participants,selected);
  const item=readerOf({configPath:f.configPath}).listTasks().items[0];
  assert.equal(item.builderId,selected.builderId);assert.equal(item.verifierId,selected.verifierId);
  assert.equal(item.reporterId,selected.reporterId);assert.deepEqual(item.evidenceIds,[]);
  assert.deepEqual(fs.readFileSync(journal),bytes);
  assert.deepEqual(fs.readdirSync(path.join(f.state,'public-evidence')),[]);
 });
 await t.test('active roster projects selected rather than default participants without asserting inference',()=>{
  writePrivateJson(path.join(f.state,'service.json'),{schema:'continuum-coding-service-process',version:1,
   instanceId:'a'.repeat(32),configSha256:config.configSha256,process:linuxProcessIdentity(),activeTaskId:task.id,state:'TASK_ACTIVE'});
  const reader=readerOf({configPath:f.configPath}),items=reader.listAgents().items;
  assert.deepEqual(items.filter(i=>i.state==='ASSIGNED_TO_ACTIVE_TASK').map(i=>i.agentId).sort(),Object.values(selected).sort());
  for(const item of items){assert.deepEqual(item.verifiedCapabilities,[]);assert.deepEqual(item.permissionScope,[]);
   assert.equal(item.modelActive,Object.values(selected).includes(item.agentId)?null:false);}
  fs.unlinkSync(path.join(f.state,'service.json'));
 });
 await t.test('altered selection is blocked on restart but the owner can still revoke its exact grant',async()=>{
  const delivery=store.readDeliveries(config)[0];
  store.saveDeliveries(config,[{...delivery,participants:legacy}]);
  const item=readerOf({configPath:f.configPath}).listTasks().items[0];
  assert.equal(item.state,'BLOCKED');assert.equal(item.failureCode,'SERVICE_TASK_REVIEW_CHANGED');
  const cancelled=await cli(['cancel','--config',f.configPath,'--task-id',task.id]);
  assert.equal(cancelled.delivery,'RETIRED');
  store.saveDeliveries(config,[{...store.readDeliveries(config)[0],participants:selected}]);
  const final=readerOf({configPath:f.configPath}).listTasks().items[0];assert.equal(final.terminal,true);
  assert.deepEqual(final.evidenceIds,[]);
  const replay=await cli(['submit',...args,...roles,'--approve',review.reviewSha256]);
  assert.equal(replay.duplicate,true);assert.equal(replay.delivery,'RETIRED');
 });
 await t.test('changed source and revoked profile block a new admission',async()=>{
  profiles.delete(selected.builderId);
  await assert.rejects(cli(['prepare',...args,...roles]),/BOT_ASSIGNMENT_PROFILE_UNAVAILABLE/);
  fs.writeFileSync(path.join(f.project,'lib','offset.mjs'),'export const offset=(a,b)=>a+b;\n');
  await assert.rejects(cli(['prepare',...args]),/SERVICE_SOURCE_DIRTY/);
 });
});

test('real persisted Kotlin profiles and fresh CLI processes retain the selected owner team',{
 skip:process.env.CONTINUUM_SERVICE_PROFILE_HOST_PROOF!=='1'?'NOT_RUN: provide the compiled exact-source Kotlin profile bridge':false,
 timeout:60000
},t=>{
 const f=fixture(t),classpath=process.env.GENESIS_HANDOFF_CLASSPATH;
 assert.ok(classpath,'GENESIS_HANDOFF_CLASSPATH must identify actual compiled source and host runtime dependencies');
 const run=(command,args,options={})=>{
  const result=childProcess.spawnSync(command,args,{encoding:'utf8',shell:false,timeout:15000,maxBuffer:512*1024,...options});
  assert.equal(result.status,0,result.stderr);return result.stdout.trim();
 };
 const source=fileURLToPath(new URL('../..',import.meta.url));
 const cli=(...args)=>JSON.parse(run(process.execPath,[path.join(source,'scripts/continuum-coding-service.mjs'),...args]));
 const seed=JSON.parse(run('java',['-Xmx128m','-cp',classpath,'at.heinz.deviceai.chief.HandoffHostChecks','seed',f.profiles]));
 assert.equal(seed.count,20);
 fs.mkdirSync(path.join(f.project,'lib'));
 fs.writeFileSync(path.join(f.project,'lib','offset.mjs'),'export const offset=(a,b)=>a-b;\n');
 for(const args of [['init','-q','-b','real-profile-fixture'],['add','.'],
  ['-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','synthetic task only']])
  run('git',args,{cwd:f.project});
 const task={schema:'continuum-coding-task',version:1,id:'real-profile-task',
  sourceCommit:run('git',['rev-parse','HEAD'],{cwd:f.project}),kind:'BUGFIX',
  goal:'Synthetic bounded profile admission; do not execute a coding worker',allowedPaths:['lib/offset.mjs'],
  acceptance:['offset(2,3) is 5'],chain:'node',limits:{maxModelCalls:1,maxContextChars:4096}};
 const taskFile=path.join(f.root,'task.json');fs.writeFileSync(taskFile,JSON.stringify(task));
 initializeCodingService({...f.args,profileClasspath:classpath});
 const options=['--config',f.configPath,'--task',taskFile];
 const participants={builderId:'bot-mira',verifierId:'bot-atlas',reporterId:'bot-mnemos'};
 const selection=['--builder',participants.builderId,'--verifier',participants.verifierId,'--reporter',participants.reporterId];
 const reviewed=cli('prepare',...options,...selection);
 assert.deepEqual(reviewed.participants,participants);
 const first=cli('submit',...options,...selection,'--approve',reviewed.reviewSha256);
 assert.equal(first.executed,false);assert.equal(first.delivery,'PENDING');
 const duplicate=cli('submit',...options,...selection,'--approve',reviewed.reviewSha256);
 assert.equal(duplicate.duplicate,true);assert.equal(duplicate.canonicalTaskId,first.canonicalTaskId);
 const tasks=cli('tasks','--config',f.configPath).items;assert.equal(tasks.length,1);
 for(const [role,id] of Object.entries(participants))assert.equal(tasks[0][role],id);
 assert.deepEqual(tasks[0].evidenceIds,[]);
 const agents=cli('agents','--config',f.configPath,'--limit','20').items;assert.equal(agents.length,20);
 assert.ok(agents.every(a=>a.state==='REGISTERED'&&a.modelActive===false&&a.verifiedCapabilities.length===0));
 assert.equal(cli('cancel','--config',f.configPath,'--task-id',task.id).delivery,'RETIRED');
 assert.equal(cli('tasks','--config',f.configPath).items[0].terminal,true);
 assert.equal(cli('status','--config',f.configPath).state,'STOPPED');
 assert.deepEqual(fs.readdirSync(path.join(f.state,'public-evidence')),[]);
});
