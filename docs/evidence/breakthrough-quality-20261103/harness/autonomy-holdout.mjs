import fs from 'node:fs';import path from 'node:path';import os from 'node:os';import crypto from 'node:crypto';import {spawnSync} from 'node:child_process';import {pathToFileURL} from 'node:url';
const [fixtureFile,outPath]=process.argv.slice(2);fs.mkdirSync(outPath,{recursive:false});
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const invoke=(cmd,args,opts={})=>{const p=spawnSync(cmd,args,{encoding:'utf8',...opts});if(p.status!==0)throw Error(cmd+' '+JSON.stringify(args)+' '+p.stderr);return p.stdout.trim();};
const g=(repo,...args)=>invoke('git',['-C',repo,...args]);
const put=(repo,file,data)=>{const f=path.join(repo,file);fs.mkdirSync(path.dirname(f),{recursive:true});fs.writeFileSync(f,data);};
const commit=(repo,msg)=>{g(repo,'add','-A');g(repo,'-c','user.name=QA','-c','user.email=qa@example.invalid','commit','-qm',msg);return g(repo,'rev-parse','HEAD');};
const snapshots=[['baseline','64ec06a73f50cae9ee49035c840710d87777d50f'],['candidate','799dd763e5a20de29d78fdd1dd26f7a5a5f0b0b0']];
const modules=[];for(const [label,rev]of snapshots){const code=invoke('git',['show',rev+':.github/continuum/ccc1-redgreen-verifier.mjs']);const f=path.resolve(outPath,label+'.mjs');fs.writeFileSync(f,code+'\n');modules.push({label,rev,hash:sha(fs.readFileSync(f)),api:await import(pathToFileURL(f))});}
const frozen=JSON.parse(fs.readFileSync(fixtureFile));const results=[];
for(const c of frozen.cases){
 const repo=fs.mkdtempSync(path.join(os.tmpdir(),'qa-executor-'+c.id+'-'));g(repo,'init','-q');
 const [n,aux,next]=c.syntheticNames;const s=c.scenario;const pre="import test,{describe} from 'node:test';import assert from 'node:assert/strict';import {fixed} from '../src/config.mjs';\n";
 put(repo,'src/config.mjs','export const fixed=false;\n');put(repo,'README.md','QA synthetic fixture\n');const base=commit(repo,'base');
 let target='target.'+n;
 if(s==='DUPLICATE_NAME_DIFFERENT_FILES'){
  for(const dir of ['left','right'])put(repo,dir+'/shared.test.mjs',pre+`test(${JSON.stringify(n)},()=>assert.equal(fixed,true));\n`);target='shared.'+n;
 }else if(s==='DUPLICATE_NAME_DIFFERENT_SUITES'){
  put(repo,'tests/target.test.mjs',pre+`for(const suite of ${JSON.stringify([aux,next])})describe(suite,()=>test(${JSON.stringify(n)},()=>assert.equal(fixed,true)));\n`);
 }else if(s==='DUPLICATE_OCCURRENCES'){
  put(repo,'tests/target.test.mjs',pre+`for(let i=0;i<2;i++)test(${JSON.stringify(n)},()=>assert.equal(fixed,true));\n`);
 }else{
  let extra=`test(${JSON.stringify(aux)},()=>assert.ok(true));\n`;
  if(s==='NON_TARGET_REMOVED')extra=`if(!fixed)test(${JSON.stringify(aux)},()=>assert.ok(true));\n`;
  if(s==='NON_TARGET_RENAMED')extra=`test(fixed?${JSON.stringify(next)}:${JSON.stringify(aux)},()=>assert.ok(true));\n`;
  if(s==='PASS_BECOMES_SKIPPED')extra=`test(${JSON.stringify(aux)},{skip:fixed},()=>assert.ok(true));\n`;
  if(s==='SKIP_BECOMES_TODO')extra=`test(${JSON.stringify(aux)},fixed?{todo:true}:{skip:true},()=>{});\n`;
  if(s==='CHILD_SUITE_MOVED')extra=`describe(fixed?${JSON.stringify(next)}:${JSON.stringify(aux)},()=>test('retained_leaf',()=>assert.ok(true)));\n`;
  if(s==='INTERRUPTED_BASELINE')extra=`if(!fixed)process.kill(process.pid,'SIGKILL');\n`;
  put(repo,'tests/target.test.mjs',pre+`test(${JSON.stringify(n)},()=>assert.equal(fixed,true));\n`+extra);
 }
 const red=commit(repo,'frozen failing target and retained controls');put(repo,'src/config.mjs','export const fixed=true;\n');const green=commit(repo,'change production fixture only');
 const patch=spawnSync('git',['-C',repo,'diff','--binary','--full-index',red,green]).stdout;const patchPath=path.join(repo,'valid.patch');fs.writeFileSync(patchPath,patch);
 for(const {label,rev,hash,api}of modules){
  const r=api.runNodeProfile(repo,red),v=api.runNodeProfile(repo,green);const failing=[...r.cases??[]].filter(([,x])=>x.failed).map(([id])=>id);
  const receipt={schema:'continuum-ccc1-patch-receipt',version:1,repo:'QA/synthetic',baseSha:base,redSha:red,greenSha:green,patchSha256:sha(patch),targetTests:[target],red:{sha:red,command:'node-redgreen',executed:r.executed??0,failed:r.failed??0,skipped:[...r.cases?.values()??[]].filter(x=>x.skipped).length,failingTests:failing,passedTargets:[],exitCode:r.exitCode??1,timedOut:false,cancelled:false},green:{sha:green,command:'node-redgreen',executed:v.executed??0,failed:v.failed??0,skipped:[...v.cases?.values()??[]].filter(x=>x.skipped).length,failingTests:[],passedTargets:[target],exitCode:v.exitCode??0,timedOut:false,cancelled:false},approvedForMerge:false,installed:false,deviceVerified:false};
  if(s==='FOREIGN_COMMIT'){receipt.greenSha='f'.repeat(40);receipt.green.sha=receipt.greenSha;}
  if(s==='PATCH_PATH_MISMATCH')receipt.patchSha256='0'.repeat(64);
  const bytes=Buffer.from(JSON.stringify(receipt));const commitResult=api.verifyCommitReceipt({repo,receiptBytes:bytes});
  const patchResult=api.verifyRepairPatch({repo,baseCommit:s==='FOREIGN_COMMIT'?'f'.repeat(40):red,patchPath,patchSha256:receipt.patchSha256,receiptBytes:bytes});
  const expected=s==='UNCHANGED_POSITIVE'?'VERIFIED':'REJECTED';
  results.push({id:c.id,scenario:s,label,sourceSha:rev,sourceHash:hash,repo,expected,commit:commitResult,patch:patchResult,pass:commitResult.verdict===expected&&patchResult.verdict===expected});
  fs.writeFileSync(path.join(outPath,'results.json'),JSON.stringify({fixtureSha256:sha(fs.readFileSync(fixtureFile)),cases:results},null,2)+'\n');
  console.log(c.id,label,commitResult.verdict,patchResult.verdict,results.at(-1).pass);
 }
}
