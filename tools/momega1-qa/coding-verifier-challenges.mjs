// Post-freeze independent review of newly exported canonical verification API.
// Synthetic trusted host fixtures only; never local-model or OS-isolation evidence.
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {spawnSync} from 'node:child_process';import {createHash} from 'node:crypto';import {pathToFileURL} from 'node:url';import assert from 'node:assert/strict';
const candidate=path.resolve(process.argv[2]),out=path.resolve(process.argv[3]);
const api=await import(pathToFileURL(path.join(candidate,'.github/continuum/ccc1-redgreen-verifier.mjs')));
const isolated=process.env.MOMEGA_QA_ISOLATION_CONFIG? (await import(pathToFileURL(path.join(candidate,'.github/continuum/isolated-executor.mjs')))).createIsolatedExecutor(JSON.parse(fs.readFileSync(process.env.MOMEGA_QA_ISOLATION_CONFIG,'utf8'))):null;
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'momega1-canonical-qa-'));const results=[];
const hash=b=>createHash('sha256').update(b).digest('hex');
function git(repo,...args){const p=spawnSync('git',['-C',repo,...args],{encoding:'utf8'});assert.equal(p.status,0,p.stderr);return p.stdout.trim();}
function probe(id,extra,expected,changes={}){
 if(process.env.MOMEGA_QA_ONLY&&process.env.MOMEGA_QA_ONLY!==id)return;
 const repo=path.join(temp,id);fs.mkdirSync(path.join(repo,'lib'),{recursive:true});fs.mkdirSync(path.join(repo,'.github/continuum'),{recursive:true});git(repo,'init','-q');
 fs.writeFileSync(path.join(repo,'lib/add.mjs'),'export function add(a,b){return a-b;}\n');
 fs.writeFileSync(path.join(repo,'.github/continuum/add.test.mjs'),"import test from 'node:test';import assert from 'node:assert/strict';import {add} from '../../lib/add.mjs';\ntest('sum',()=>assert.equal(add(2,3),5));\n"+extra);
 git(repo,'add','.');git(repo,'-c','user.name=QA','-c','user.email=qa@example.invalid','commit','-qm','baseline');const sha=git(repo,'rev-parse','HEAD');
 fs.writeFileSync(path.join(repo,'lib/add.mjs'),'export function add(a,b){return a+b;}\n');
 const bytes=spawnSync('git',['-C',repo,'diff','--binary','--full-index']).stdout;git(repo,'checkout','--','lib/add.mjs');const patchPath=path.join(temp,id+'.patch');fs.writeFileSync(patchPath,bytes);
 const task={schema:'continuum-coding-task',version:1,id:'qa-addition',sourceCommit:sha,kind:'BUGFIX',goal:'Add finite operands',allowedPaths:['lib/add.mjs'],acceptance:['add(2,3) is 5'],chain:'node',limits:{maxModelCalls:1,maxContextChars:4096}};
 const before=[git(repo,'rev-parse','HEAD'),git(repo,'status','--porcelain'),git(repo,'worktree','list','--porcelain')];
 let value,error;try{value=api.verifyCodingCandidate({repo,task,patchPath,patchSha256:hash(bytes),proposalSha256:'a'.repeat(64),out:path.join(temp,id+'-evidence'),deadline:Date.now()+60000,...(isolated?{executor:isolated}:{}),...changes});}catch(e){error=e.message;}
 const after=[git(repo,'rev-parse','HEAD'),git(repo,'status','--porcelain'),git(repo,'worktree','list','--porcelain')];
 const expectedError=id==='fake-executor'?'UNTRUSTED_ISOLATION_EXECUTOR':['expired','invalid-proposal-hash'].includes(id)?'CODING_VERIFICATION_REQUEST_INVALID':null;
 const actual=error?'THREW':value?.verdict;const preserved=JSON.stringify(before)===JSON.stringify(after);
 const branded=value?api.isTrustedCodingVerification(value)&&!api.isTrustedCodingVerification(structuredClone(value)):true;
 results.push({id,expected,actual,error,preserved,branded,pass:actual===expected&&preserved&&branded&&(!expectedError||error===expectedError),expectedError,evidence:value});
}
try{
 probe('positive',"test('retained',()=>{});",'VERIFIED');
 probe('skip-conversion',"test('retained',{skip:add(2,3)>0},()=>{});",'REJECTED');
 probe('rename',"test(add(2,3)>0?'replacement':'retained',()=>{});",'REJECTED');
 probe('new-nontarget-failure',"test('retained',()=>assert.ok(add(2,3)<0));",'REJECTED');
 probe('process-nonzero',"if(add(2,3)>0)process.exitCode=9;",'REJECTED');
 probe('expired','', 'THREW',{deadline:0});
 probe('fake-executor','', 'THREW',{executor:{execute(){throw Error('MUST_NOT_CALL');}}});
 probe('invalid-proposal-hash','', 'THREW',{proposalSha256:'not-a-sha'});
}finally{const report={candidateSha:git(candidate,'rev-parse','HEAD'),harnessSha256:hash(fs.readFileSync(new URL(import.meta.url))),evidenceClass:'HOST_INTEGRATION',results,summary:{total:results.length,pass:results.filter(x=>x.pass).length,fail:results.filter(x=>!x.pass).length}};fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report.summary));fs.rmSync(temp,{recursive:true,force:true});process.exitCode=report.summary.fail?1:0;}
