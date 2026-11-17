import test from 'node:test';
import assert from 'node:assert/strict';
import { ContinuumExpertMesh } from './expert-mesh.mjs';

const now=()=>1000;
const node=(id,opts={})=>({
  id,cpuSlots:4,gpuSlots:0,ramMb:4096,vramMb:0,
  expiresAtMs:1000000,...opts
});
test('50 model instances comprise five equal logical roles',()=>{
  const mesh=new ContinuumExpertMesh({prototypeCount:50,now});
  assert.equal(mesh.snapshot().logicalModels,50);
  assert.deepEqual(Object.values(mesh.snapshot().groups),[10,10,10,10,10]);
});
test('100 expert declarations do not manufacture computation',()=>{
  const mesh=new ContinuumExpertMesh({prototypeCount:100,now});
  assert.equal(mesh.snapshot().logicalModels,100);
  const job=mesh.request({component:'coding-agent',taskId:'c-1'});
  assert.equal(job.status,'QUEUED');
  assert.equal(mesh.snapshot().running,0);
});
test('coding-agent can reserve existing CPU capacity',()=>{
  const mesh=new ContinuumExpertMesh({now});
  mesh.registerAuthorizedNode(node('cpu'));
  const job=mesh.request({component:'coding-agent',taskId:'c-1'});
  assert.equal(mesh.job(job.id).status,'RUNNING');
  assert.equal(mesh.job(job.id).nodeId,'cpu');
});
test('local agent and memory roles share bounded capacity',()=>{
  const mesh=new ContinuumExpertMesh({now});
  mesh.registerAuthorizedNode(node('cpu',{cpuSlots:2,ramMb:2048}));
  mesh.request({component:'coding-agent',taskId:'c-1'});
  mesh.request({component:'memory-engine',taskId:'m-1'});
  mesh.request({component:'cognitive-runtime',taskId:'r-1'});
  assert.equal(mesh.snapshot().running,2);
  assert.equal(mesh.snapshot().queued,1);
  mesh.complete('m-1');
  assert.equal(mesh.snapshot().running,2);
  assert.equal(mesh.snapshot().queued,0);
});
test('training model must have actual authorized GPU and VRAM',()=>{
  const mesh=new ContinuumExpertMesh({now});
  mesh.registerAuthorizedNode(node('cpu'));
  mesh.request({component:'nemo-trainer',taskId:'train-1'});
  assert.equal(mesh.job('train-1').status,'QUEUED');
  mesh.registerAuthorizedNode(node('gpu',{gpuSlots:1,ramMb:8192,vramMb:8192}));
  assert.equal(mesh.job('train-1').status,'RUNNING');
  assert.equal(mesh.job('train-1').nodeId,'gpu');
});
test('unknown components cannot submit',()=>{
  const mesh=new ContinuumExpertMesh({now});
  assert.throws(()=>mesh.request({component:'untrusted-network',taskId:'u-1'}),/Unknown component/);
});
test('owner resource node can revoke work',()=>{
  const mesh=new ContinuumExpertMesh({now});
  mesh.registerAuthorizedNode(node('cpu'));
  mesh.request({component:'coding-agent',taskId:'job'});
  mesh.revokeNode('cpu');
  assert.equal(mesh.job('job').status,'CANCELLED');
  assert.equal(mesh.snapshot().running,0);
});
test('duplicate job IDs cannot be reused by alternate components',()=>{
  const mesh=new ContinuumExpertMesh({now});
  mesh.request({component:'coding-agent',taskId:'job'});
  assert.throws(()=>mesh.request({component:'memory-engine',taskId:'job'}),/Duplicate job/);
});

test('1000, 2000 and 3000 specialists are registered with five balanced roles',()=>{
  for(const count of [1000,2000,3000]){
    const mesh=new ContinuumExpertMesh({prototypeCount:count,now});
    assert.equal(mesh.snapshot().logicalModels,count);
    assert.deepEqual(Object.values(mesh.snapshot().groups),Array(5).fill(count/5));
    assert.equal(mesh.snapshot().running,0);
  }
});
test('3000 registrations do not create phantom GPU or CPU throughput',()=>{
  const mesh=new ContinuumExpertMesh({prototypeCount:3000,now});
  mesh.request({component:'coding-agent',taskId:'work'});
  assert.equal(mesh.snapshot().queued,1);
  assert.equal(mesh.snapshot().nodes.length,0);
});
