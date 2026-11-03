// Frozen independent scenarios: docs/evidence/delta-challenger-20261103/FROZEN-CASES.md.
// Usage: node tools/delta-challenger/challenge.mjs <verifier.mjs> <output.json>
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';

const verifierPath=path.resolve(process.argv[2]),outputPath=path.resolve(process.argv[3]);
const api=await import(pathToFileURL(verifierPath));
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const results=[],fixtures=[];
const root=fs.mkdtempSync(path.join(os.tmpdir(),'delta-challenger-'));
const env={...process.env,GIT_AUTHOR_DATE:'2026-11-03T00:00:00Z',GIT_COMMITTER_DATE:'2026-11-03T00:00:00Z'};
function git(repo,...args){const p=spawnSync('git',['-C',repo,...args],{encoding:'utf8',env});assert.equal(p.status,0,p.stderr);return p.stdout.trim();}
function write(repo,files){for(const [p,s] of Object.entries(files)){fs.mkdirSync(path.dirname(path.join(repo,p)),{recursive:true});fs.writeFileSync(path.join(repo,p),s);}}
function commit(repo,files,msg){write(repo,files);git(repo,'add','-A');git(repo,'-c','user.name=DELTA','-c','user.email=delta@example.invalid','commit','-qm',msg);return git(repo,'rev-parse','HEAD');}
const prefix="import test from 'node:test';import assert from 'node:assert/strict';import s from '../src/state.mjs';\n";
const target="test('target',()=>assert.equal(s.answer,2));\n";
const state=over=>`export default ${JSON.stringify({answer:1,keep:true,count:2,mode:'run',extend:false,...over})};\n`;
function fixture(id,body='',green={},extraFiles={},red={}){
 const repo=path.join(root,id);fs.mkdirSync(repo);git(repo,'init','-q');
 const base=commit(repo,{'src/state.mjs':state(red),'tests/basic.test.mjs':"import test from 'node:test';test('smoke',()=>{});\n"},'base');
 const tests={'tests/check.test.mjs':prefix+target+body,...extraFiles};
 const redSha=commit(repo,tests,'red');
 const greenSha=commit(repo,{'src/state.mjs':state({...red,answer:2,...green})},'green');
 const patch=spawnSync('git',['-C',repo,'diff','--binary','--full-index',redSha,greenSha]).stdout;
 const patchPath=path.join(root,`${id}.patch`);fs.writeFileSync(patchPath,patch);
 const redRun=api.runNodeProfile(repo,redSha),greenRun=api.runNodeProfile(repo,greenSha);
 const failing=redRun.ok?[...redRun.cases].filter(([,c])=>c.failed).map(([id])=>id):['check.target'];
 const runClaim=(r,shaValue,isGreen)=>({sha:shaValue,command:'node-redgreen',executed:r.executed??0,failed:r.failed??0,
  skipped:r.ok?[...r.cases.values()].filter(c=>c.skipped).length:0,failingTests:isGreen?[]:failing,
  passedTargets:isGreen?failing:[],exitCode:r.exitCode??1,timedOut:false,cancelled:false});
 const receipt={schema:api.RECEIPT_SCHEMA,version:1,repo:'Ullmann27/device-ai',baseSha:base,redSha,greenSha,patchSha256:sha(patch),
  targetTests:failing,red:runClaim(redRun,redSha,false),green:runClaim(greenRun,greenSha,true),approvedForMerge:false,installed:false,deviceVerified:false};
 const f={id,repo,base,redSha,greenSha,patchPath,patchSha256:sha(patch),receipt};
 const serialize=r=>({...r,cases:r.cases?[...r.cases]:null});
 fixtures.push({id,base,redSha,greenSha,patchSha256:sha(patch),testsSha256:sha(JSON.stringify(tests)),
  redSourceSha256:sha(state(red)),greenSourceSha256:sha(state({...red,answer:2,...green})),red:serialize(redRun),green:serialize(greenRun)});
 return f;
}
function check(f,id,expected='REJECTED',{mode='commit',mutate=()=>{},override={}}={}){
 const receipt=structuredClone(f.receipt);mutate(receipt);
 const before={head:git(f.repo,'rev-parse','HEAD'),status:git(f.repo,'status','--porcelain'),worktrees:git(f.repo,'worktree','list','--porcelain')};
 let attestation,error;
 try{attestation=mode==='patch'?api.verifyRepairPatch({repo:f.repo,baseCommit:f.redSha,patchPath:f.patchPath,patchSha256:f.patchSha256,receiptBytes:Buffer.from(JSON.stringify(receipt)),...override})
  :api.verifyCommitReceipt({repo:f.repo,receiptBytes:Buffer.from(JSON.stringify(receipt)),...override});}
 catch(e){error={name:e.name,message:e.message};}
 const after={head:git(f.repo,'rev-parse','HEAD'),status:git(f.repo,'status','--porcelain'),worktrees:git(f.repo,'worktree','list','--porcelain')};
 const callerPreserved=JSON.stringify(before)===JSON.stringify(after);
 const actual=error?'THREW':attestation.verdict;
 const pass=actual===expected&&callerPreserved;
 const result={id,fixture:f.id,mode,expected,actual,pass,callerPreserved,receipt,attestation,error};results.push(result);
 process.stdout.write(`${id} ${mode} expected=${expected} actual=${actual} ${pass?'PASS':'FAIL'} ${attestation?.reasons?.join(',')??error?.message??''}\n`);
}
try{
 const ordinary=fixture('ordinary',"test('retained',()=>{});\n");
 for(const mode of ['commit','patch'])check(ordinary,'D01','VERIFIED',{mode});
 const extension=fixture('extension',"test('retained',()=>{});if(s.extend)test('added',()=>{});\n",{extend:true});
 for(const mode of ['commit','patch'])check(extension,'D02','VERIFIED',{mode});
 const drop=fixture('drop',"if(s.keep)test('retained',()=>{});\n",{keep:false});
 for(const mode of ['commit','patch'])check(drop,'D03','REJECTED',{mode});
 check(fixture('replace',"test(s.keep?'retained':'replacement',()=>{});\n",{keep:false}),'D04');
 check(fixture('skip',"test('retained',{skip:s.mode==='skip'},()=>{});\n",{mode:'skip'}),'D05');
 check(fixture('todo',"test('retained',{todo:s.mode==='todo'},()=>{});\n",{mode:'todo'}),'D06');
 check(fixture('duplicate',"for(let i=0;i<s.count;i++)test('repeated',()=>{});\n",{count:1}),'D07');
 const nested="test('north',async t=>{await t.test('leaf',()=>{});});test('south',async t=>{if(s.keep)await t.test('leaf',()=>{});});\n";
 check(fixture('nested-loss',nested,{keep:false}),'D08');
 check(fixture('nested-retained',nested),'D09','VERIFIED');
 const nestedImport="import test from 'node:test';import s from '../../src/state.mjs';";
 check(fixture('path-loss','',{keep:false},{'tests/a/shared.test.mjs':nestedImport+"test('leaf',()=>{});",'tests/b/shared.test.mjs':nestedImport+"if(s.keep)test('leaf',()=>{});"}),'D10');
 const ambiguous=fixture('alias-ambiguous','',{}, {'tests/else.test.mjs':prefix+"test('target',()=>{});"});
 check(ambiguous,'D11','REJECTED',{mutate:r=>{r.targetTests=['test.target'];r.green.passedTargets=r.targetTests;}});
 check(ordinary,'D12','VERIFIED',{mutate:r=>{r.targetTests=['test.target'];r.green.passedTargets=r.targetTests;}});
 check(ordinary,'D13','REJECTED',{mutate:r=>{r.targetTests=['check.target','test.target'];r.green.passedTargets=r.targetTests;}});
 check(fixture('preexisting-skip',"test('retained',{skip:true},()=>{});\n"),'D14','VERIFIED');
 check(fixture('preexisting-todo',"test('retained',{todo:true},()=>{});\n"),'D15','VERIFIED');
 for(const side of ['red','green'])for(const [field,value] of Object.entries({exitCode:side==='red'?0:9,command:'not-node-redgreen',timedOut:true,cancelled:true,skipped:99,passedTargets:['not.real']})){
  check(ordinary,`D16-${side}-${field}`,'REJECTED',{mutate:r=>{r[side][field]=value;}});
 }
 for(const [side,field] of [['red','failingTests'],['green','failingTests'],['green','passedTargets']])check(ordinary,`D16-malformed-${side}-${field}`,'REJECTED',{mutate:r=>{r[side][field]=null;}});
 for(const [field,value] of Object.entries({approvedForMerge:true,installed:true,deviceVerified:true,schema:'forged',version:999}))check(ordinary,`D17-${field}`,'REJECTED',{mode:'patch',mutate:r=>{r[field]=value;}});
 // Actual Node profile owns reporter output; hostile text cannot select another runner/report.
 // Direct malformed parser probes are diagnostic only, never substituted for real profile runs.
 for(const [id,xml] of Object.entries({truncated:'<testsuites><testcase name="a" classname="test"/><',absent:'',zero:'<testsuites></testsuites>',unclosed:'<testsuite name="x"><testcase name="a" classname="test"/>'})){
  let parsed,error;try{parsed=api.parseJunit(xml);}catch(e){error=e.message;}
  results.push({id:`D18-${id}`,mode:'parser-only',expected:'NO_USABLE_CASES_OR_THROW',actual:error?'THREW':`${parsed.size}_CASES`,pass:!!error||parsed.size===0,error});
 }
 const timeout=fixture('timeout',"if(s.keep)await new Promise(resolve=>setTimeout(resolve,150));\n",{keep:false});
 const timeoutRun=api.runNodeProfile(timeout.repo,timeout.redSha,{timeoutMs:25});
 const retry=api.runNodeProfile(ordinary.repo,ordinary.greenSha);
 results.push({id:'D19',mode:'actual-profile',expected:'TIMEOUT_THEN_SUCCESS',actual:{timeoutRun,retry:{ok:retry.ok,exitCode:retry.exitCode}},pass:!timeoutRun.ok&&timeoutRun.reason==='TIMEOUT_OR_SIGNAL'&&retry.ok&&retry.exitCode===0});
 check(ordinary,'D20-wrong-patch-hash','REJECTED',{mode:'patch',override:{patchSha256:'0'.repeat(64)}});
 check(ordinary,'D20-missing-target','REJECTED',{mutate:r=>{r.targetTests=['absent.target'];r.green.passedTargets=r.targetTests;}});
 const patchTests=spawnSync('git',['-C',ordinary.repo,'diff','--binary','--full-index',ordinary.base,ordinary.greenSha]).stdout;
 const testsPatchPath=path.join(root,'edits-tests.patch');fs.writeFileSync(testsPatchPath,patchTests);
 check(ordinary,'D20-test-edit','REJECTED',{mode:'patch',override:{baseCommit:ordinary.base,patchPath:testsPatchPath,patchSha256:sha(patchTests)}});
}finally{
 const report={baseline:'64ec06a73f50cae9ee49035c840710d87777d50f',freeze:'e12c9dc8',node:process.version,
  verifierPath,verifierSha256:sha(fs.readFileSync(verifierPath)),harnessSha256:sha(fs.readFileSync(new URL(import.meta.url))),
  profile:api.NODE_PROFILE,fixtures,results,summary:{total:results.length,pass:results.filter(r=>r.pass).length,fail:results.filter(r=>!r.pass).length}};
 fs.mkdirSync(path.dirname(outputPath),{recursive:true});fs.writeFileSync(outputPath,JSON.stringify(report,null,2)+'\n');
 process.stdout.write(JSON.stringify(report.summary)+'\n');
 fs.rmSync(root,{recursive:true,force:true});
 process.exitCode=report.summary.fail?1:0;
}
