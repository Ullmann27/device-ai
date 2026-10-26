#!/usr/bin/env node
/**
 * Offline-first, read-only autonomous developer health worker.
 * Keeps checking while the owner's computer runs; never modifies any Git ref,
 * calls an LLM, merges a PR, downloads software or executes a generated patch.
 */
import {spawnSync} from 'node:child_process';
import {readdirSync,mkdirSync,openSync,closeSync,unlinkSync,writeFileSync,renameSync} from 'node:fs';
import {resolve,join,dirname,sep} from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const MIN_INTERVAL_MS=60*60*1000;
const TIMEOUT_MS=6*60*1000;
const SAFE_BRANCH=/^(?!main$|master$|production$|release\/)[a-zA-Z0-9][a-zA-Z0-9/_-]{0,126}$/;

function command(executable,args,timeout=10000){
 const r=spawnSync(executable,args,{cwd:ROOT,shell:false,encoding:'utf8',
   timeout,maxBuffer:2*1024*1024,env:{...process.env,GIT_TERMINAL_PROMPT:'0'}});
 if(r.error)throw r.error;
 return r;
}
function exactGit(args){
 const r=command('git',args);
 if(r.status!==0)throw Error('GIT_READ_FAILED: '+args[0]);
 return r.stdout.trim();
}
export function classifyNativeTestRun({exitCode,stdout,stderr='',error=null}){
 const match=[...String(stdout).matchAll(/^# (tests|pass|fail|cancelled|skipped|todo) (\d+)$/gm)];
 const values=new Map(match.map(x=>[x[1],Number(x[2])]));
 const valid=['tests','pass','fail','cancelled','skipped','todo']
   .every(k=>Number.isSafeInteger(values.get(k)));
 const total=values.get('tests')??0,pass=values.get('pass')??0;
 const complete=valid && total>0 && total===pass+
   values.get('fail')+values.get('cancelled')+values.get('skipped')+values.get('todo');
 const hasFail=exitCode!==0||error!==null||!complete||values.get('fail')!==0||
   values.get('cancelled')!==0;
 return Object.freeze({
   status:hasFail?'FAILED':
     values.get('skipped')||values.get('todo')?'PASS_WITH_UNEXECUTED_CASES':'ALL_EXECUTED_PASS',
   tests:valid?total:null,pass:valid?pass:null,
   failed:valid?values.get('fail'):null,skipped:valid?values.get('skipped'):null,
   todo:valid?values.get('todo'):null,
   exitCode,error:error?String(error).slice(0,300):null,
   stderrTail:String(stderr).slice(-1000)
 });
}
export function evaluateCheckout({branch,dirty,tests}){
 if(!SAFE_BRANCH.test(branch)||dirty)throw Error('UNSAFE_CHECKOUT');
 if(tests.length===0||tests.length>500||tests.some(x=>!/^[-a-zA-Z0-9_.]+\.test\.mjs$/.test(x)))
   throw Error('UNSAFE_TEST_INVENTORY');
 return Object.freeze({branch,tests:Object.freeze([...tests].sort())});
}

export function runOneCycle(outDirectory){
 const repo=exactGit(['rev-parse','--show-toplevel']);
 if(resolve(repo)!==ROOT)throw Error('INCORRECT_REPOSITORY_ROOT');
 const branch=exactGit(['symbolic-ref','--quiet','--short','HEAD']);
 const dirty=exactGit(['status','--porcelain','--untracked-files=no']).length>0;
 const names=readdirSync(join(ROOT,'.github','continuum')).filter(x=>x.endsWith('.test.mjs'));
 const {tests}=evaluateCheckout({branch,dirty,tests:names});
 const output=resolve(outDirectory);
 if(output===ROOT||output.startsWith(ROOT+sep))throw Error('OUTPUT_MUST_BE_OUTSIDE_REPO');
 mkdirSync(output,{recursive:true,mode:0o700});
 const lock=join(output,'worker.lock');
 const fd=openSync(lock,'wx',0o600);
 try{
   const sourceSha=exactGit(['rev-parse','--verify','HEAD']);
   const started=new Date().toISOString();
   const run=command(process.execPath,['--test','--test-concurrency=2',
     ...tests.map(x=>join('.github','continuum',x))],TIMEOUT_MS);
   const result=classifyNativeTestRun({exitCode:run.status,stdout:run.stdout,
     stderr:run.stderr,error:run.signal??null});
   // No read-only worker action ever constitutes a repair or learning receipt.
   const report={schema:'continuum-local-worker-v1',branch,sourceSha,started,
     completed:new Date().toISOString(),...result,
     candidatePromoted:false,patchApplied:false,weightsUpdated:false,
     ownerReviewRequired:true};
   const target=join(output,'latest.json'),tmp=join(output,'latest.partial.json');
   writeFileSync(tmp,JSON.stringify(report,null,2)+'\n',{encoding:'utf8',mode:0o600});
   renameSync(tmp,target);
   return report;
 }finally{closeSync(fd);unlinkSync(lock);}
}
async function main(){
 if(process.argv.length!==4||!['--once','--loop'].includes(process.argv[2]))
   throw Error('USAGE: node scripts/continuum-local-development-worker.mjs --once|--loop /external/status-folder');
 const mode=process.argv[2],out=process.argv[3];
 do{
   const report=runOneCycle(out);
   console.log(JSON.stringify(report));
   if(mode==='--once')break;
   await delay(MIN_INTERVAL_MS);
 }while(true);
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 main().catch(e=>{console.error('LOCAL_WORKER_FAILED: '+e.message);process.exitCode=1;});
}
