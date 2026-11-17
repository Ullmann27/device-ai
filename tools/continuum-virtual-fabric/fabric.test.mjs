import test from 'node:test';
import assert from 'node:assert/strict';
import { VirtualFabric } from './fabric.mjs';
const now=()=>1000;
function fixture() {
  const f=new VirtualFabric({now});
  f.registerTenant({id:'owner',maxRunning:100,maxQueued:200});
  f.registerNode({id:'gpu-1',cpuSlots:4,gpuSlots:1,ramMb:16384,vramMb:16384,expiresAtMs:1000000});
  f.registerModel({id:'small',cpuSlots:1,gpuSlots:1,ramMb:4096,vramMb:4096});
  return f;
}
test('100 logical instances cannot create extra physical GPU throughput',()=>{
  const f=fixture();
  for(let i=0;i<100;i++) {
    f.registerModel({id:'logical-'+i,cpuSlots:1,gpuSlots:1,ramMb:1024,vramMb:1024});
    f.submit({id:'task-'+i,tenantId:'owner',modelId:'logical-'+i,maxRuntimeMs:10000});
  }
  assert.equal(f.snapshot().logicalModels,101);
  assert.equal(f.snapshot().running,1);
  assert.equal(f.snapshot().queued,99);
  f.complete('task-0');
  assert.equal(f.snapshot().running,1);
  assert.equal(f.snapshot().queued,98);
});
test('memory is not oversold',()=>{
  const f=new VirtualFabric({now});
  f.registerTenant({id:'t',maxRunning:10});
  f.registerNode({id:'cpu',cpuSlots:8,gpuSlots:0,ramMb:1024,vramMb:0,expiresAtMs:1000000});
  f.registerModel({id:'large',cpuSlots:1,ramMb:768});
  for(let i=0;i<3;i++) f.submit({id:'j'+i,tenantId:'t',modelId:'large',maxRuntimeMs:1000});
  assert.equal(f.snapshot().running,1);
  assert.equal(f.snapshot().queued,2);
});
test('tenant concurrency quota is enforced',()=>{
  const f=fixture();
  f.registerNode({id:'gpu-2',cpuSlots:4,gpuSlots:1,ramMb:16384,vramMb:16384,expiresAtMs:1000000});
  f.registerTenant({id:'customer',maxRunning:1});
  for(let i=0;i<2;i++) f.submit({id:'c'+i,tenantId:'customer',modelId:'small',maxRuntimeMs:1000});
  assert.equal(f.snapshot().running,1);
  assert.equal(f.snapshot().queued,1);
});
test('commercial workload is blocked on unlicensed nodes',()=>{
  const f=fixture();
  f.submit({id:'commercial',tenantId:'owner',modelId:'small',maxRuntimeMs:1000,commercial:true});
  assert.equal(f.getJob('commercial').status,'QUEUED');
  f.registerNode({id:'owned-commercial',cpuSlots:2,gpuSlots:1,ramMb:16000,vramMb:16000,commercialAllowed:true,expiresAtMs:1000000});
  assert.equal(f.getJob('commercial').nodeId,'owned-commercial');
});
test('short lease cannot admit a long job',()=>{
  const f=fixture();
  f.registerTenant({id:'other'});
  f.registerNode({id:'near-expiry',cpuSlots:2,gpuSlots:1,ramMb:10000,vramMb:10000,expiresAtMs:1050});
  f.submit({id:'first',tenantId:'owner',modelId:'small',maxRuntimeMs:1000});
  f.submit({id:'second',tenantId:'other',modelId:'small',maxRuntimeMs:1000});
  assert.equal(f.getJob('second').status,'QUEUED');
});
test('cancel and complete release resources exactly once',()=>{
  const f=fixture();
  f.submit({id:'a',tenantId:'owner',modelId:'small',maxRuntimeMs:1000});
  f.submit({id:'b',tenantId:'owner',modelId:'small',maxRuntimeMs:1000});
  f.cancel('a');
  assert.equal(f.getJob('b').status,'RUNNING');
  assert.throws(()=>f.complete('a'),/Only running/);
  f.complete('b');
  assert.equal(f.snapshot().nodes[0].used.gpuSlots,0);
});
test('revoked physical host cancels reservations',()=>{
  const f=fixture();
  f.submit({id:'a',tenantId:'owner',modelId:'small',maxRuntimeMs:1000});
  f.revokeNode('gpu-1');
  assert.equal(f.getJob('a').status,'CANCELLED');
  assert.equal(f.snapshot().nodes.length,0);
});
test('invalid IDs and duplicate jobs fail closed',()=>{
  const f=fixture();
  f.registerTenant({id:'limited',maxRunning:1,maxQueued:1});
  assert.throws(()=>f.registerModel({id:'bad id',ramMb:1024}),/Invalid model id/);
  f.submit({id:'first',tenantId:'limited',modelId:'small',maxRuntimeMs:1000});
  f.submit({id:'queued',tenantId:'limited',modelId:'small',maxRuntimeMs:1000});
  assert.throws(()=>f.submit({id:'third',tenantId:'limited',modelId:'small',maxRuntimeMs:1000}),/Tenant queue limit/);
  assert.throws(()=>f.submit({id:'first',tenantId:'limited',modelId:'small',maxRuntimeMs:1000}),/Duplicate job/);
});
test('lease validity covers the entire requested runtime',()=>{
  const f=new VirtualFabric({now});
  f.registerTenant({id:'owner'});
  f.registerNode({id:'short',cpuSlots:1,gpuSlots:0,ramMb:1024,vramMb:0,expiresAtMs:1500});
  f.registerModel({id:'cpu',ramMb:128});
  f.submit({id:'long',tenantId:'owner',modelId:'cpu',maxRuntimeMs:1000});
  assert.equal(f.getJob('long').status,'QUEUED');
});
test('snapshots are defensive copies',()=>{
  const f=fixture(), snap=f.snapshot();
  snap.nodes[0].capacity.gpuSlots=900;
  assert.equal(f.snapshot().nodes[0].capacity.gpuSlots,1);
});
test('timed-out job does not produce a false success',()=>{
  let clock=1000;
  const f=new VirtualFabric({now:()=>clock});
  f.registerTenant({id:'tenant'});
  f.registerNode({id:'node',cpuSlots:1,gpuSlots:0,ramMb:4096,vramMb:0,expiresAtMs:10000});
  f.registerModel({id:'small',ramMb:512});
  f.submit({id:'job',tenantId:'tenant',modelId:'small',maxRuntimeMs:100});
  clock=1101;
  assert.throws(()=>f.complete('job'),/Only running/);
  assert.equal(f.getJob('job').status,'CANCELLED');
  assert.equal(f.snapshot().nodes[0].used.cpuSlots,0);
});
