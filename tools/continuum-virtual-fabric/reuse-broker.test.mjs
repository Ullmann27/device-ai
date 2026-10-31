import test from 'node:test';
import assert from 'node:assert/strict';
import { ComputeReuseBroker } from './reuse-broker.mjs';

const d = c => c.repeat(64);
const param = (id, other = {}) => ({
  id,tenantId:'owner', modelRevision:d('a'),inputDigest:d('b'),
  parametersDigest:d('c'),policyRevision:d('d'), memoryEpoch:0,
  taskType:'PURE',deterministic:true, ...other
});
const verified = () => new ComputeReuseBroker({now:()=>1_000,verifyReceipt:(entry,receipt)=>receipt.value===entry.inputDigest});
test('3000 logical requests collapse to 30 actual distinct computations',()=>{
  const broker=verified(); const leaderIds=[]; let execute=0,join=0;
  for(let i=0;i<3000;i++) {
    const digit=(i%30).toString(16).padStart(64,'0');
    const p=param('job-'+i,{inputDigest:digit});
    const r=broker.submit(p);
    if(r.action==='EXECUTE') {execute++; leaderIds.push({id:r.jobId,digest:digit});}
    if(r.action==='JOIN') join++;
  }
  assert.equal(execute,30); assert.equal(join,2970);
  assert.equal(broker.snapshot().inflightCalculations,30);
  for(const leader of leaderIds) {
    const done=broker.complete(leader.id,{value:leader.digest});
    assert.equal(done.deliveredTo.length,100);
  }
  assert.equal(broker.snapshot().activeRequests,0);
  for(let i=0;i<3000;i++){
    const digit=(i%30).toString(16).padStart(64,'0');
    assert.equal(broker.submit(param('repeat-'+i,{inputDigest:digit})).action,'CACHE_HIT');
  }
  assert.equal(broker.snapshot().hit,3000);
});
test('tenant isolation prevents cross-user reuse',()=>{
  const b=verified();b.submit(param('a'));
  assert.equal(b.submit(param('b',{tenantId:'other'})).action,'EXECUTE');
});
test('changed model revision prevents reuse',()=>{
  const b=verified();b.submit(param('a'));
  assert.equal(b.submit(param('b',{modelRevision:d('f')})).action,'EXECUTE');
});
test('changed memory epoch prevents stale computation reuse',()=>{
  const b=verified();b.submit(param('a'));
  assert.equal(b.submit(param('b',{memoryEpoch:1})).action,'EXECUTE');
});
test('changed policy revision prevents context reuse',()=>{
  const b=verified();b.submit(param('a'));
  assert.equal(b.submit(param('b',{policyRevision:d('e')})).action,'EXECUTE');
});
test('changed generation parameters prevent reuse',()=>{
  const b=verified();b.submit(param('a'));
  assert.equal(b.submit(param('b',{parametersDigest:d('e')})).action,'EXECUTE');
});
test('generative or non-deterministic work never coalesces',()=>{
  const b=verified();
  for (const params of [{taskType:'GENERATIVE',deterministic:true},{taskType:'PURE',deterministic:false}]) {
    const a=b.submit(param('a-'+params.taskType,{...params}));
    const c=b.submit(param('b-'+params.taskType,{...params}));
    assert.equal(a.action,'EXECUTE');assert.equal(c.action,'EXECUTE');
  }
});
test('default-deny receipt checker prevents cached unproven results',()=>{
  const b=new ComputeReuseBroker();
  b.submit(param('a'));
  assert.throws(()=>b.complete('a',{value:d('b')}),/not independently verified/);
  assert.equal(b.snapshot().cachedResults,0);
});
test('verification failure does not discard existing leader',()=>{
  const b=verified();b.submit(param('a'));
  assert.throws(()=>b.complete('a',{value:d('1')}),/not independently verified/);
  assert.equal(b.snapshot().inflightCalculations,1);
  b.complete('a',{value:d('b')});
  assert.equal(b.submit(param('b')).action,'CACHE_HIT');
});
test('cache TTL expires and forces new computation',()=>{
  let now=1000;
  const b=new ComputeReuseBroker({now:()=>now,ttlMs:50,verifyReceipt:(e,r)=>e.inputDigest===r.value});
  b.submit(param('a'));b.complete('a',{value:d('b')});
  now=1051;
  assert.equal(b.submit(param('b')).action,'EXECUTE');
});
test('LRU evicts old result when full',()=>{
  const b=new ComputeReuseBroker({maxCached:1,verifyReceipt:(e,r)=>e.inputDigest===r.value});
  b.submit(param('a'));b.complete('a',{value:d('b')});
  b.submit(param('b',{inputDigest:d('2')}));b.complete('b',{value:d('2')});
  assert.equal(b.snapshot().cachedResults,1);
  assert.equal(b.snapshot().evicted,1);
  assert.equal(b.submit(param('c')).action,'EXECUTE');
});
test('leader cancellation releases joined tasks for retry',()=>{
  const b=verified();b.submit(param('a'));
  b.submit(param('b'));
  assert.deepEqual(b.cancel('a').cancelled,['a','b']);
  assert.equal(b.submit(param('retry')).action,'EXECUTE');
});
test('maximum waiter quota blocks unbounded fanout',()=>{
  const b=new ComputeReuseBroker({maxWaiters:1});
  b.submit(param('a'));b.submit(param('b'));
  assert.throws(()=>b.submit(param('c')),/waiter quota/i);
});
test('active request limit prevents memory exhaustion',()=>{
  const b=new ComputeReuseBroker({maxActive:2});
  b.submit(param('a'));b.submit(param('b'));
  assert.throws(()=>b.submit(param('c')),/Active job quota/);
});
test('invalid input digest or repeated job id is rejected',()=>{
  const b=verified();b.submit(param('a'));
  assert.throws(()=>b.submit(param('a')),/Duplicate active job/);
  assert.throws(()=>b.submit(param('bad',{inputDigest:'not-a-digest'})),/Invalid input digest/);
});
