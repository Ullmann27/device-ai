// Independent Omega acceptance: synthetic receipt consistency only, never execution authentication.
// Review another exact checkout with CONTINUUM_OMEGA_REVIEW_ROOT; no target sources are changed.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';

const root=path.resolve(process.env.CONTINUUM_OMEGA_REVIEW_ROOT || path.join(import.meta.dirname,'..'));
const {verifyRepairReceipt}=await import(pathToFileURL(path.join(root,'.github/continuum/ccc1-verifier.mjs')));
const {REPAIR_PROFILE_COMMAND}=await import(pathToFileURL(path.join(root,'scripts/continuum-mobile-coding.mjs')));
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');

function fixture(t) {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'omega-independent-receipt-'));
  t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const bytes=Buffer.from('diff --git a/example b/example\n');
  const file=path.join(dir,'candidate.patch');
  fs.writeFileSync(file,bytes);
  const run=(phase,executed,failed)=>({phase,executed,failed,passed:failed===0,exitCode:failed===0?0:1,timedOut:false,command:REPAIR_PROFILE_COMMAND.node});
  const receipt={schema:'continuum-mobile-coding-receipt',version:1,operation:'code-repair',
    job:'22222222-2222-4222-8222-222222222222',commit:'a'.repeat(40),order:{chain:'node'},
    status:'REPAIR_PATCH_VERIFIED_PENDING_REVIEW',toolchain:{node:process.version},
    approvedForMerge:false,installed:false,apk:null,deviceVerified:false,
    patch:{path:file,bytes:bytes.length,sha256:digest(bytes)},
    commands:[run('baseline',5,2),run('candidate',7,0),run('independent',7,0)],
    evidence:{verificationSha256:'b'.repeat(64),evidenceSufficient:true,
      baselineExecuted:5,baselineFailed:2,candidateExecuted:7,independentExecuted:7}};
  return {receipt,bind:{job:receipt.job,commit:receipt.commit,chain:'node'}};
}

test('quality control: consistent baseline failure and two retained passes are accepted',t=>{
  const {receipt,bind}=fixture(t);
  assert.equal(verifyRepairReceipt(receipt,bind).verdict,'CONFIRMED');
});

const mutations=[
  ['candidate expansion lost at independent run',r=>{r.commands[2].executed=6;r.evidence.independentExecuted=6;}],
  ['summary conceals candidate count',r=>{r.commands[1].executed=4;}],
  ['summary conceals independent count',r=>{r.commands[2].executed=4;}],
  ['baseline summary lies about failed count',r=>{r.evidence.baselineFailed=1;}],
  ['baseline summary lies about executed count',r=>{r.evidence.baselineExecuted=4;}],
  ['string counts',r=>{r.commands[1].executed='7';r.evidence.candidateExecuted='7';}],
  ['fractional counts',r=>{r.commands[2].executed=7.5;r.evidence.independentExecuted=7.5;}],
  ['nonfinite counts',r=>{r.commands[2].executed=Infinity;r.evidence.independentExecuted=Infinity;}],
  ['unsafe integer counts',r=>{r.commands[2].executed=Number.MAX_SAFE_INTEGER+1;r.evidence.independentExecuted=r.commands[2].executed;}],
  ['baseline failure count exceeds total',r=>{r.commands[0].failed=6;r.evidence.baselineFailed=6;}],
  ['baseline timeout',r=>{r.commands[0].timedOut=true;}],
  ['baseline successful exit',r=>{r.commands[0].exitCode=0;}],
  ['baseline signal-like exit',r=>{r.commands[0].exitCode=null;}],
  ['baseline negative exit',r=>{r.commands[0].exitCode=-1;}],
  ['candidate timeout marker absent',r=>{delete r.commands[1].timedOut;}],
  ['duplicate candidate phase',r=>{r.commands[2].phase='candidate';}],
  ['null command item',r=>{r.commands[1]=null;}],
  ['object commands',r=>{r.commands={};}],
  ['missing baseline',r=>{r.commands.shift();}],
  ['extra unaudited phase',r=>{r.commands.push({...r.commands[1],phase:'other'});}],
  ['non-allowlisted command',r=>{r.commands[1].command+=' --test-name-pattern=easy';}],
  ['unbound commit',r=>{r.commit='c'.repeat(40);}],
];
for(const [label,mutate] of mutations) test('quality rejects '+label,t=>{
  const {receipt,bind}=fixture(t);
  mutate(receipt);
  assert.equal(verifyRepairReceipt(receipt,bind).verdict,'INCONCLUSIVE');
});

test('quality rejects unrecognized chain without looking up inherited profile properties',t=>{
  const {receipt,bind}=fixture(t);
  for(const chain of ['unknown','constructor','__proto__']) {
    const changed=structuredClone(receipt);
    changed.order.chain=chain;
    assert.equal(verifyRepairReceipt(changed,{...bind,chain}).verdict,'INCONCLUSIVE');
  }
});
