import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {planVirtualModelRouteFromRegistry} from './compute-fabric-model-adapter.mjs';

const fixture=new URL('../../app/src/test/resources/compute-fabric-planning-v1.json',import.meta.url);
const read=()=>JSON.parse(fs.readFileSync(fixture,'utf8'));
const request=()=>({taskId:'fabric-coding-01',taskSha256:'a'.repeat(64),
 modelId:'continuum/small-coder',revision:'b'.repeat(40),dataClassification:'PUBLIC',
 allowFallback:false,allowedFallbackModels:[],internetEnabled:false,ownerAllowsRemoteCompute:false,
 maxChargeMicrounits:0,maxInputBytes:1024,maxOutputBytes:2048,deadlineMillis:9000});
const offer=()=>({nodeId:'local-worker',kind:'LOCAL_WORKER',modelId:'continuum/small-coder',
 revision:'b'.repeat(40),available:true,modelLoaded:true,ownerEnrolled:true,
 maxInputBytes:16000,maxOutputBytes:4096,declaredChargeMicrounits:0,expiresAtMillis:10000});
function plan(snapshot=read(),req=request(),offers=[offer()],nowMillis=2000){
 return planVirtualModelRouteFromRegistry({snapshotJson:JSON.stringify(snapshot),request:req,modelOffers:offers,nowMillis});
}

test('actual registry snapshot constrains the existing planner without granting authority',()=>{
 const p=plan();
 assert.equal(p.status,'ROUTE_PROPOSED_NOT_AUTHORIZED');
 assert.equal(p.selected.nodeId,'local-worker');
 assert.equal(p.selected.executable,false);
 assert.equal(p.selected.ownerEnrollmentAttested,false);
 assert.equal(p.registryTransportAuthenticated,false);
 assert.equal(p.performsInference,false);
 assert.equal(p.registrySnapshotSha256.length,64);
 assert.ok(Object.isFrozen(p));
});

test('self-enrolled model offer outside registry cannot create a route',()=>{
 assert.equal(plan(read(),request(),[{...offer(),nodeId:'unregistered'}]).selected,null);
});

test('task hash, ID, output, deadline and actual input size stay bound',()=>{
 for(const change of [{taskSha256:'c'.repeat(64)},{taskId:'foreign'},
  {maxOutputBytes:4097},{deadlineMillis:10001},{maxInputBytes:1}]){
  assert.throws(()=>plan(read(),{...request(),...change}),/REGISTRY_TASK_BINDING_INVALID/);
 }
});

test('insufficient RAM/runtime/output and unsupported task/backend exclude node',()=>{
 for(const change of [{maxRamMb:512},{maxRuntimeMillis:999},{maxOutputBytes:1024},
  {taskTypes:['EMBEDDING']},{backends:['GPU']},{issuedAtMillis:2001},{expiresAtMillis:2999}]){
  const s=read();Object.assign(s.nodes[0],change);
  assert.equal(plan(s).selected,null,JSON.stringify(change));
 }
});

test('explicit assignment cannot be replaced with another enrolled node',()=>{
 const s=read();s.task.assignedNodeId='another-worker';
 assert.equal(plan(s).selected,null);
});

test('expired or future snapshot fails closed; duplicated and malformed nodes rejected',()=>{
 assert.throws(()=>plan(read(),request(),[offer()],10000),/REGISTRY_SNAPSHOT_STALE/);
 assert.throws(()=>plan(read(),request(),[offer()],999),/REGISTRY_SNAPSHOT_STALE/);
 for(const mutate of [s=>s.version=2,s=>s.executable=true,s=>s.nodes.push(s.nodes[0]),
  s=>s.nodes[0].maxRamMb=Infinity,s=>s.task.inlineContext='private memory',s=>s.nodes[0].ownerBinding='owner:private']){
  const s=read();mutate(s);assert.throws(()=>plan(s),/REGISTRY_SNAPSHOT_INVALID/);
 }
 const raw=fs.readFileSync(fixture,'utf8').replace('"version":1','"version":1,"version":1');
 assert.throws(()=>planVirtualModelRouteFromRegistry({snapshotJson:raw,request:request(),modelOffers:[offer()],nowMillis:2000}));
});

test('remote consent and private-context rules still belong to existing planner',()=>{
 const remote={...offer(),kind:'REMOTE_PROVIDER'};
 assert.equal(plan(read(),request(),[remote]).selected,null);
 const allowed={...request(),internetEnabled:true,ownerAllowsRemoteCompute:true};
 assert.equal(plan(read(),allowed,[remote]).selected.nodeId,'local-worker');
 assert.equal(plan(read(),{...allowed,dataClassification:'OWNER_PRIVATE'},[remote]).selected,null);
});

test('resource snapshot is not permission and cannot attest model bytes or locality',()=>{
 const p=plan();assert.equal(p.selected.actualModelRevisionAttested,false);
 assert.equal(p.grantsPermission,false);assert.equal(p.resourceAndModelDeclarationsOnly,true);
});

test('native JVM export feeds Node byte-for-byte when cross-language runner is configured',t=>{
 const file=process.env.CONTINUUM_FABRIC_SNAPSHOT_FILE;
 if(!file){t.skip('Run scripts/continuum-fabric-jvm-check.sh for the actual JVM→Node test');return;}
 const raw=fs.readFileSync(file,'utf8');
 assert.equal(raw.trim(),fs.readFileSync(fixture,'utf8').trim());
 const p=planVirtualModelRouteFromRegistry({snapshotJson:raw,request:request(),modelOffers:[offer()],nowMillis:2000});
 assert.equal(p.selected.nodeId,'local-worker');
 assert.equal(p.registryTransportAuthenticated,false);
});
