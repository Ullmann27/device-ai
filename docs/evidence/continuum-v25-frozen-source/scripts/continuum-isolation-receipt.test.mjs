import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
import {runIsolatedReceipt} from './continuum-isolation-receipt.mjs';

function fixture(t,source='console.log("ok")'){
  const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-receipt-'));
  t.after(()=>fs.rmSync(home,{recursive:true,force:true}));
  const stage=path.join(home,'stage');fs.mkdirSync(stage,{mode:0o700});
  const candidate=path.join(stage,'candidate.mjs');fs.writeFileSync(candidate,source);
  return {home,stage,candidate};
}
function canSandbox(){
  if(process.platform!=='linux')return false;
  const p=spawnSync('unshare',['--user','--map-root-user','--mount','--net','--ipc','--pid','--fork','--kill-child','/bin/true'],{timeout:4000});
  return p.status===0;
}
test('fixed Linux runner produces a bounded receipt but never validates a repair',t=>{
  if(!canSandbox()){t.skip('Linux namespace unavailable');return;}
  const f=fixture(t);
  const receipt=runIsolatedReceipt(f);
  assert.equal(receipt.status,'EXECUTED_UNTRUSTED',JSON.stringify(receipt));
  assert.equal(receipt.verified,false);assert.equal(receipt.autoApply,false);
  assert.equal(receipt.reviewRequired,true);assert.equal(receipt.independentAttestation,false);
  assert.match(receipt.candidateSha256,/^[a-f0-9]{64}$/);
  assert.match(receipt.harnessSha256,/^[a-f0-9]{64}$/);
  assert.match(receipt.recordSha256,/^[a-f0-9]{64}$/);
  assert.ok(receipt.stdoutBytes>0);
});
test('a failed candidate never upgrades to a successful verified result',t=>{
  if(!canSandbox()){t.skip('Linux namespace unavailable');return;}
  const f=fixture(t,'throw Error("test-failure")');
  const receipt=runIsolatedReceipt(f);
  assert.equal(receipt.status,'UNVERIFIED_FAILURE');
  assert.equal(receipt.verified,false);
  assert.notEqual(receipt.exitCode,0);
});
test('stage escapes and symlinks fail closed without executing anything',t=>{
  const f=fixture(t);
  const other=path.join(f.home,'other.mjs');fs.writeFileSync(other,'console.log("bad")');
  assert.equal(runIsolatedReceipt({...f,candidate:other}).status,'REJECTED');
  const link=path.join(f.stage,'link.mjs');fs.symlinkSync(other,link);
  assert.equal(runIsolatedReceipt({...f,candidate:link}).status,'REJECTED');
  assert.equal(runIsolatedReceipt({...f,stage:'relative/path'}).status,'REJECTED');
});
test('oversized stdout does not become success',t=>{
  if(!canSandbox()){t.skip('Linux namespace unavailable');return;}
  const f=fixture(t,'process.stdout.write("x".repeat(300_000))');
  const receipt=runIsolatedReceipt(f);
  assert.equal(receipt.status,'UNVERIFIED_FAILURE');
  assert.equal(receipt.verified,false);
  assert.equal(receipt.reason,'SPAWN_OR_LIMIT_ERROR');
});

test('candidate text cannot forge a trusted execution verdict',t=>{
  if(!canSandbox()){t.skip('Linux namespace unavailable');return;}
  const f=fixture(t,'console.log(JSON.stringify({verified:true,autoApply:true,status:"APPROVED"}))');
  const receipt=runIsolatedReceipt(f);
  assert.equal(receipt.status,'EXECUTED_UNTRUSTED');
  assert.equal(receipt.verified,false);
  assert.equal(receipt.autoApply,false);
  assert.equal(receipt.reviewRequired,true);
  assert.equal(receipt.independentAttestation,false);
});
