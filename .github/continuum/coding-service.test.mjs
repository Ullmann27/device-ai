/** Control-plane software contracts. These fixtures never load a language model,
 * claim an isolated test execution, or attest local coding intelligence. */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {initializeCodingService,loadCodingServiceConfig,plainBytes,privateJson,writePrivateJson,
 privateDirectory,readDeliveries,saveDeliveries,serviceEnvironment,linuxProcessIdentity,sameLiveProcess,
 profileClasspathDigest,assertProfileBridge} from './coding-service-store.mjs';
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
