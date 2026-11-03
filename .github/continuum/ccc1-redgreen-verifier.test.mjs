// M3: the CCC1 RED→GREEN receipt is checked against a REAL git repository and re-executed tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {verifyCommitReceipt,verifyRepairPatch,parseJunit,ATTESTATION_SCHEMA} from './ccc1-redgreen-verifier.mjs';

const sha256=b=>crypto.createHash('sha256').update(b).digest('hex');
function g(repo,...args){const r=spawnSync('git',['-C',repo,...args],{encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();}
function commit(repo,files,msg){
 for(const [f,c] of Object.entries(files)){fs.mkdirSync(path.dirname(path.join(repo,f)),{recursive:true});fs.writeFileSync(path.join(repo,f),c);}
 g(repo,'add','-A');g(repo,'-c','user.name=t','-c','user.email=t@t','commit','-qm',msg);return g(repo,'rev-parse','HEAD');
}
const T=`import test from 'node:test';import assert from 'node:assert';`;
function fixture(){
 const repo=fs.mkdtempSync(path.join(os.tmpdir(),'rg-repo-'));
 g(repo,'init','-q');
 const base=commit(repo,{'src/sum.mjs':'export const sum=(a,b)=>a-b;\n','tests/basic.test.mjs':`${T}test('truthy',()=>assert.ok(1));\n`},'base');
 const red=commit(repo,{'tests/sum.test.mjs':`${T}import {sum} from '../src/sum.mjs';test('adds_two',()=>assert.equal(sum(1,1),2));\n`},'red');
 const green=commit(repo,{'src/sum.mjs':'export const sum=(a,b)=>a+b;\n'},'green');
 return {repo,base,red,green};
}
// Same key order and escaping as Ccc1PatchReceipt.toJson() for plain ASCII values.
function receipt(f,over={}){
 const patch=spawnSync('git',['-C',f.repo,'diff','--binary','--full-index',f.red,over.greenSha??f.green]).stdout;
 const r={schema:'continuum-ccc1-patch-receipt',version:1,repo:'Ullmann27/device-ai',baseSha:f.base,redSha:f.red,greenSha:f.green,
  patchSha256:sha256(patch),targetTests:['sum.adds_two'],
  red:{sha:f.red,command:'node-redgreen',executed:2,failed:1,skipped:0,failingTests:['sum.adds_two'],passedTargets:[],exitCode:1,timedOut:false,cancelled:false},
  green:{sha:f.green,command:'node-redgreen',executed:2,failed:0,skipped:0,failingTests:[],passedTargets:['sum.adds_two'],exitCode:0,timedOut:false,cancelled:false},
  approvedForMerge:false,installed:false,deviceVerified:false};
 const merged={...r,...over};
 if(over.greenSha){merged.green={...r.green,sha:over.greenSha};}
 return Buffer.from(JSON.stringify(merged));
}

test('genuine RED→GREEN chain is VERIFIED from re-executed tests and bound to the receipt bytes',()=>{
 const f=fixture();const bytes=receipt(f);
 const a=verifyCommitReceipt({repo:f.repo,receiptBytes:bytes});
 assert.equal(a.schema,ATTESTATION_SCHEMA);
 assert.equal(a.verdict,'VERIFIED',a.reasons.join());
 assert.equal(a.receiptSha256,sha256(bytes));
 assert.match(a.red.xmlSha256,/^[0-9a-f]{64}$/);assert.equal(a.red.failed,1);assert.equal(a.green.failed,0);
});

test('forged receipts are rejected against the real repository',()=>{
 const f=fixture();
 // GREEN commit that does not fix anything.
 g(f.repo,'checkout','-q','--detach',f.red);
 const fake=commit(f.repo,{'README.md':'nothing fixed\n'},'fake green');
 const noFix=verifyCommitReceipt({repo:f.repo,receiptBytes:receipt({...f,green:fake},{})});
 assert.equal(noFix.verdict,'REJECTED');assert.ok(noFix.reasons.includes('TARGET_NOT_PASSING_IN_GREEN'),noFix.reasons.join());
 // Placeholder patch hash (M2 fixture style "a".repeat(64)).
 assert.ok(verifyCommitReceipt({repo:f.repo,receiptBytes:receipt(f,{patchSha256:'a'.repeat(64)})}).reasons.includes('PATCH_HASH_MISMATCH'));
 // Commit ids that do not exist in the repository.
 assert.ok(verifyCommitReceipt({repo:f.repo,receiptBytes:receipt(f,{greenSha:'b'.repeat(40)})}).reasons.includes('COMMIT_NOT_IN_REPOSITORY'));
 // Claimed test results that differ from the re-run.
 const lie=verifyCommitReceipt({repo:f.repo,receiptBytes:receipt(f,{targetTests:['sum.adds_two'],
  red:{sha:f.red,command:'x',executed:2,failed:1,skipped:0,failingTests:['sum.other'],passedTargets:[],exitCode:1,timedOut:false,cancelled:false}})});
 assert.equal(lie.verdict,'REJECTED');assert.ok(lie.reasons.includes('CLAIMED_RED_FAILURES_DIFFER'),lie.reasons.join());
 const counts=verifyCommitReceipt({repo:f.repo,receiptBytes:receipt(f,{
  green:{sha:f.green,command:'x',executed:9,failed:0,skipped:0,failingTests:[],passedTargets:['sum.adds_two'],exitCode:0,timedOut:false,cancelled:false}})});
 assert.ok(counts.reasons.includes('CLAIMED_GREEN_COUNTS_DIFFER'),counts.reasons.join());
});

test('a fix that edits the target test is rejected even if everything is green',()=>{
 const f=fixture();
 g(f.repo,'checkout','-q','--detach',f.red);
 const cheat=commit(f.repo,{'tests/sum.test.mjs':`${T}test('adds_two',()=>assert.ok(true));\n`},'cheat');
 const a=verifyCommitReceipt({repo:f.repo,receiptBytes:receipt({...f,green:cheat})});
 assert.equal(a.verdict,'REJECTED');assert.ok(a.reasons.includes('FIX_MODIFIES_TESTS'),a.reasons.join());
});

test('patch mode (CCC1 repair): base fails, base+patch passes, tampered patch is rejected',()=>{
 const f=fixture();
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rg-patch-'));
 const patchPath=path.join(dir,'fix.patch');
 const bytes=spawnSync('git',['-C',f.repo,'diff','--binary','--full-index',f.red,f.green]).stdout;
 fs.writeFileSync(patchPath,bytes);
 g(f.repo,'checkout','-q',f.red);
 const r=receipt(f);
 const ok=verifyRepairPatch({repo:f.repo,baseCommit:f.red,patchPath,patchSha256:sha256(bytes),receiptBytes:r});
 assert.equal(ok.verdict,'VERIFIED',ok.reasons.join());
 fs.appendFileSync(patchPath,'\n');
 const bad=verifyRepairPatch({repo:f.repo,baseCommit:f.red,patchPath,patchSha256:sha256(bytes),receiptBytes:r});
 assert.ok(bad.reasons.includes('PATCH_HASH_MISMATCH'),bad.reasons.join());
});

test('JUnit parser maps node test files to stable ids and sees failures',()=>{
 const c=parseJunit('<testsuites><testcase name="a" classname="test" file="/x/sum.test.mjs"/>'+
  '<testcase name="b" classname="test" file="/x/sum.test.mjs" failure="x"><failure message="x">e</failure></testcase></testsuites>');
 assert.equal(c.get('sum.a').failed,false);assert.equal(c.get('sum.b').failed,true);
});
