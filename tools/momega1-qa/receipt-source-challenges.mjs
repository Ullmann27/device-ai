// Independent host execution probes, separate from unknown model tasks.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
const verifier=path.resolve(process.argv[2]);
const api=await import(pathToFileURL(verifier));
const out=path.resolve(process.argv[3]);
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'momega1-verify-'));
const results=[];
const digest=b=>crypto.createHash('sha256').update(b).digest('hex');
function git(repo,...args){const r=spawnSync('git',['-C',repo,...args],{encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();}
function commit(repo,files){for(const [name,body] of Object.entries(files)){const f=path.join(repo,name);fs.mkdirSync(path.dirname(f),{recursive:true});fs.writeFileSync(f,body);}git(repo,'add','-A');git(repo,'-c','user.name=QA','-c','user.email=qa@example.invalid','commit','-qm','synthetic fixture');return git(repo,'rev-parse','HEAD');}
function fixture(id,body,greenBody='export default true;\n'){
 const repo=path.join(tmp,id);fs.mkdirSync(repo);git(repo,'init','-q');
 const base=commit(repo,{'src/state.mjs':'export default false;\n','tests/smoke.test.mjs':"import test from 'node:test';test('smoke',()=>{});\n"});
 const red=commit(repo,{'tests/probe.test.mjs':"import test from 'node:test';import assert from 'node:assert/strict';import fixed from '../src/state.mjs';\ntest('target',()=>assert.equal(fixed,true));\n"+body});
 const green=commit(repo,{'src/state.mjs':greenBody});
 const bytes=spawnSync('git',['-C',repo,'diff','--binary','--full-index',red,green]).stdout;
 const patchPath=path.join(tmp,id+'.patch');fs.writeFileSync(patchPath,bytes);
 const rr=api.runNodeProfile(repo,red),gr=api.runNodeProfile(repo,green);
 const targets=[...rr.cases].filter(([,c])=>c.failed).map(([id])=>id);
 const claim=(r,sha,isGreen)=>({sha,command:'node-redgreen',executed:r.executed,failed:r.failed,exitCode:r.exitCode,skipped:[...r.cases.values()].filter(c=>c.skipped).length,failingTests:isGreen?[]:targets,passedTargets:isGreen?targets:[],timedOut:false,cancelled:false});
 return {repo,red,green,patchPath,patchSha256:digest(bytes),receipt:{schema:api.RECEIPT_SCHEMA,version:1,repo:'Ullmann27/device-ai',baseSha:base,redSha:red,greenSha:green,patchSha256:digest(bytes),targetTests:targets,red:claim(rr,red,false),green:claim(gr,green,true),approvedForMerge:false,installed:false,deviceVerified:false}};
}
function check(f,id,expected,mutate=()=>{}){
 for(const mode of ['commit','patch']){
  const receipt=structuredClone(f.receipt);mutate(receipt);
  const before=[git(f.repo,'rev-parse','HEAD'),git(f.repo,'status','--porcelain'),git(f.repo,'worktree','list','--porcelain')];
  let result,error;try{result=mode==='commit'?api.verifyCommitReceipt({repo:f.repo,receiptBytes:Buffer.from(JSON.stringify(receipt))}):api.verifyRepairPatch({repo:f.repo,baseCommit:f.red,patchPath:f.patchPath,patchSha256:f.patchSha256,receiptBytes:Buffer.from(JSON.stringify(receipt))});}catch(e){error=e.message;}
  const after=[git(f.repo,'rev-parse','HEAD'),git(f.repo,'status','--porcelain'),git(f.repo,'worktree','list','--porcelain')];
  results.push({id,mode,expected,status:result?.verdict,error,reason:result?.reasons,callerPreserved:JSON.stringify(before)===JSON.stringify(after),pass:result?.verdict===expected&&JSON.stringify(before)===JSON.stringify(after)});
 }
}
try{
 const good=fixture('correlated-source-claim',"test('retained',()=>{});\n");
 check(good,'green-sha-correlated-wrong-existing-commit','REJECTED',r=>{r.greenSha=r.baseSha;r.green.sha=r.baseSha;});
 check(good,'green-sha-correlated-nonexistent-commit','REJECTED',r=>{r.greenSha='f'.repeat(40);r.green.sha=r.greenSha;});
}finally{
 const report={evidenceClass:'HOST_INTEGRATION',verifier,verifierSha256:digest(fs.readFileSync(verifier)),harnessSha256:digest(fs.readFileSync(new URL(import.meta.url))),node:process.version,results,summary:{total:results.length,pass:results.filter(x=>x.pass).length,fail:results.filter(x=>!x.pass).length}};
 fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report.summary));fs.rmSync(tmp,{recursive:true,force:true});process.exitCode=report.summary.fail?1:0;
}
