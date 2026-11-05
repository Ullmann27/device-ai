// M3: the CCC1 RED→GREEN receipt is checked against a REAL git repository and re-executed tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {verifyCommitReceipt,verifyRepairPatch,parseJunit,runNodeProfile,junitHasFileIdentity,ATTESTATION_SCHEMA} from './ccc1-redgreen-verifier.mjs';

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

function coverageFixture(t,mode){
 const repo=fs.mkdtempSync(path.join(os.tmpdir(),'rg-coverage-'));
 t.after(()=>fs.rmSync(repo,{recursive:true,force:true}));g(repo,'init','-q');
 const broken='export const fixed=false; export const sum=(a,b)=>a-b;\n';
 const corrected='export const fixed=true; export const sum=(a,b)=>a+b;\n';
 const base=commit(repo,{'src/sum.mjs':broken},'base');
 const registration={
  missing:"if(!fixed)test('retained',()=>assert.ok(1));",
  renamed:"test(fixed?'replacement':'retained',()=>assert.ok(1));",
  skipped:"test('retained',{skip:fixed},()=>assert.ok(1));",
  todo:"test('retained',{todo:fixed},()=>assert.ok(1));",
  suite:"describe(fixed?'replacement':'original',()=>test('retained',()=>assert.ok(1)));",
  duplicate:"test('retained',()=>assert.ok(1));if(!fixed)test('retained',()=>assert.ok(1));",
  reordered:"for(const name of (fixed?['b','a']:['a','b']))test(name,()=>assert.ok(1));",
  skipType:"test('retained',fixed?{todo:true}:{skip:true},()=>assert.ok(1));",
  retain:"describe('outer',()=>describe('inner',()=>{test('parameter [a & b]',()=>assert.ok(1));test('retained',()=>assert.ok(1));test('retained',()=>assert.ok(1));}));test('later',{todo:true},()=>{});",
  extension:"test('retained',()=>assert.ok(1));if(fixed)test('additional',()=>assert.ok(1));"
 }[mode];
 const tests=`${T}import {describe} from 'node:test';import {sum,fixed} from '../src/sum.mjs';test('adds_two',()=>assert.equal(sum(1,1),2));${registration}\n`;
 const red=commit(repo,{'tests/sum.test.mjs':tests},'red');
 const green=commit(repo,{'src/sum.mjs':corrected},'green');
 const f={repo,base,red,green};const r=JSON.parse(receipt(f));
 r.red.executed=mode==='retain'?4:['duplicate','reordered'].includes(mode)?3:mode==='skipType'?1:2;
 r.green.executed=mode==='retain'?4:mode==='extension'||mode==='reordered'?3:['missing','skipped','todo','skipType'].includes(mode)?1:2;
 r.red.skipped=mode==='retain'||mode==='skipType'?1:0;
 r.green.skipped=['retain','skipped','todo','skipType'].includes(mode)?1:0;
 const patch=spawnSync('git',['-C',repo,'diff','--binary','--full-index',red,green]).stdout;
 const patchPath=path.join(repo,'fix.patch');fs.writeFileSync(patchPath,patch);
 return {...f,bytes:Buffer.from(JSON.stringify(r)),patch,patchPath,tests};
}

for(const mode of ['missing','renamed','skipped','todo','suite','duplicate','reordered','skipType']){
 test('executor coverage rejects '+mode+' in commit and patch modes',t=>{
  const f=coverageFixture(t,mode),before=g(f.repo,'status','--porcelain'),worktrees=g(f.repo,'worktree','list','--porcelain');
  const commitResult=verifyCommitReceipt({repo:f.repo,receiptBytes:f.bytes});
  const patchResult=verifyRepairPatch({repo:f.repo,baseCommit:f.red,patchPath:f.patchPath,patchSha256:sha256(f.patch),receiptBytes:f.bytes});
  if(process.env.CCC1_PROOF_DIR){
   fs.mkdirSync(process.env.CCC1_PROOF_DIR,{recursive:true});
   fs.writeFileSync(path.join(process.env.CCC1_PROOF_DIR,mode+'.json'),JSON.stringify({mode,fixture:{repo:f.repo,base:f.base,red:f.red,green:f.green,testPath:'tests/sum.test.mjs',testSha256:sha256(f.tests),tests:f.tests},patchSha256:sha256(f.patch),patch:f.patch.toString(),receipt:JSON.parse(f.bytes),commitResult,patchResult},null,2)+'\n');
  }
  assert.equal(g(f.repo,'status','--porcelain'),before);assert.equal(g(f.repo,'worktree','list','--porcelain'),worktrees);
  for(const result of [commitResult,patchResult]){
   assert.equal(result.verdict,'REJECTED',mode+' accepted: '+JSON.stringify(result));
   assert.ok(result.reasons.some(x=>x.startsWith('BASELINE_')),mode+': '+result.reasons.join());
  }
 });
}

for(const mode of ['retain','extension'])test('executor coverage accepts '+mode,t=>{
 const f=coverageFixture(t,mode);
 for(const result of [verifyCommitReceipt({repo:f.repo,receiptBytes:f.bytes}),
  verifyRepairPatch({repo:f.repo,baseCommit:f.red,patchPath:f.patchPath,patchSha256:sha256(f.patch),receiptBytes:f.bytes})]){
  assert.equal(result.verdict,'VERIFIED',result.reasons.join());
 }
});

test('executor coverage rejects interrupted execution and then verifies a fresh retry',t=>{
 const f=coverageFixture(t,'extension');
 const slow=commit(f.repo,{'tests/timeout.test.mjs':"import test from 'node:test';test('slow',async()=>new Promise(()=>{}));setInterval(()=>{},1000);\n"},'timeout');
 assert.deepEqual(runNodeProfile(f.repo,slow,{timeoutMs:100}),{ok:false,reason:'TIMEOUT_OR_SIGNAL'});
 assert.equal(verifyCommitReceipt({repo:f.repo,receiptBytes:f.bytes}).verdict,'VERIFIED');
 assert.equal(g(f.repo,'worktree','list','--porcelain').match(/^worktree /gm).length,1);
});

test('executor coverage rejects ambiguous repeated target claims before running',t=>{
 const f=coverageFixture(t,'extension'),r=JSON.parse(f.bytes);r.targetTests.push(r.targetTests[0]);
 let calls=0;
 const result=verifyCommitReceipt({repo:f.repo,receiptBytes:Buffer.from(JSON.stringify(r)),run:()=>{calls++;throw Error('must reject before execution');}});
 assert.equal(result.verdict,'REJECTED');assert.ok(result.reasons.includes('TARGET_TESTS'));assert.equal(calls,0);
});

test('public Delta findings reject contradictory receipt claims after genuine execution',async t=>{
 const f=coverageFixture(t,'extension');
 const cases=[['D13-resolved-alias','commit',r=>{r.targetTests=['sum.adds_two','test.adds_two'];r.green.passedTargets=r.targetTests;}]];
 for(const side of ['red','green'])for(const [field,value] of Object.entries({exitCode:side==='red'?0:9,
  command:'not-node-redgreen',timedOut:true,cancelled:true,skipped:99,...(side==='red'?{passedTargets:['not.real']}:{})})){
  cases.push([`D16-${side}-${field}`,'commit',r=>{r[side][field]=value;}]);
 }
 for(const [field,value] of Object.entries({approvedForMerge:true,installed:true,deviceVerified:true,schema:'forged',version:999})){
  cases.push([`D17-${field}`,'patch',r=>{r[field]=value;}]);
 }
 for(const [id,mode,mutate] of cases)await t.test(id,()=>{
  const r=JSON.parse(f.bytes);mutate(r);const receiptBytes=Buffer.from(JSON.stringify(r));
  const before=g(f.repo,'worktree','list','--porcelain');
  const result=mode==='commit'?verifyCommitReceipt({repo:f.repo,receiptBytes}):
   verifyRepairPatch({repo:f.repo,baseCommit:f.red,patchPath:f.patchPath,patchSha256:sha256(f.patch),receiptBytes});
  assert.equal(g(f.repo,'worktree','list','--porcelain'),before);
  if(process.env.CCC1_DELTA_PROOF_DIR){
   fs.mkdirSync(process.env.CCC1_DELTA_PROOF_DIR,{recursive:true});
   fs.writeFileSync(path.join(process.env.CCC1_DELTA_PROOF_DIR,id+'.json'),JSON.stringify({id,mode,
    publicChallenge:'b1bca66f54f80ef627ab17201e29335ee3210f86',fixture:{base:f.base,red:f.red,green:f.green,
     testPath:'tests/sum.test.mjs',testSha256:sha256(f.tests)},patchSha256:sha256(f.patch),receipt:r,result},null,2)+'\n');
  }
  assert.equal(result.verdict,'REJECTED',JSON.stringify(result));
 });
});

test('Delta follow-up preserves omitted optional legacy metadata and unique aliases',t=>{
 const f=coverageFixture(t,'extension'),r=JSON.parse(f.bytes);
 for(const side of ['red','green'])for(const field of ['command','exitCode','skipped','timedOut','cancelled'])delete r[side][field];
 delete r.red.passedTargets;
 for(const field of ['approvedForMerge','installed','deviceVerified'])delete r[field];
 r.targetTests=['test.adds_two'];r.green.passedTargets=r.targetTests;
 const receiptBytes=Buffer.from(JSON.stringify(r));
 for(const result of [verifyCommitReceipt({repo:f.repo,receiptBytes}),
  verifyRepairPatch({repo:f.repo,baseCommit:f.red,patchPath:f.patchPath,patchSha256:sha256(f.patch),receiptBytes})]){
  assert.equal(result.verdict,'VERIFIED',result.reasons.join());
 }
});

// APK42 / owner report: under Node 22 the JUnit reporter omits <testcase file=…>, so seven genuine
// RED→GREEN chains were REJECTED with misleading TARGET_MISSING reasons. The verifier now names the
// runtime cause explicitly and still never returns VERIFIED without file-bound test identities.
test('JUnit without file identity is rejected with an explicit runtime reason, never verified',()=>{
 const node22='<testsuites><testcase name="adds_two" time="0.001" classname="test"/></testsuites>';
 const node24='<testsuites><testcase name="adds_two" time="0.001" classname="test" file="/w/tests/sum.test.mjs"/></testsuites>';
 const mixed=node24.replace('</testsuites>','<testcase name="x" classname="test"/></testsuites>');
 assert.equal(junitHasFileIdentity(node22),false);
 assert.equal(junitHasFileIdentity(mixed),false);
 assert.equal(junitHasFileIdentity(node24),true);
 const f=fixture();
 const a=verifyCommitReceipt({repo:f.repo,receiptBytes:receipt(f)});
 if(Number(process.versions.node.split('.')[0])>=24){
  assert.equal(a.verdict,'VERIFIED',a.reasons.join());
 }else{
  assert.equal(a.verdict,'REJECTED');
  assert.ok(a.reasons.includes('RED_RUN_JUNIT_FILE_IDENTITY_UNSUPPORTED_RUNTIME'),a.reasons.join());
 }
});
