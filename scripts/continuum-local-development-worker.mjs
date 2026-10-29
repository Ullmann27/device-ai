#!/usr/bin/env node
/** Owner-local health checks using the existing source/test inventory.
 * Never grants repair, merge, owner memory or productive weight permissions.
 * Test subprocesses are NOT an OS sandbox; use owner-reviewed isolated checkouts. */
import {spawn,spawnSync} from 'node:child_process';
import fs from 'node:fs';
import {resolve,join,dirname,sep} from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';
import crypto from 'node:crypto';
import {workerLease} from './continuum-worker-lease.mjs';
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const SAFE_BRANCH=/^(?!main$|master$|production$|release\/)[a-zA-Z0-9][a-zA-Z0-9/_-]{0,126}$/;
const digest=s=>crypto.createHash('sha256').update(s).digest('hex');
function git(args){const r=spawnSync('git',args,{cwd:ROOT,encoding:'utf8',timeout:10000,maxBuffer:2*1024*1024,env:{...process.env,GIT_TERMINAL_PROMPT:'0'}});if(r.error||r.status!==0)throw Error('GIT_READ_FAILED: '+args[0]);return r.stdout.trim();}
function regular(file){if(fs.existsSync(file)&&!fs.lstatSync(file).isFile())throw Error('UNSAFE_REPORT_PATH');}
function atomic(file,data){regular(file);const tmp=file+'.'+crypto.randomUUID()+'.partial';const fd=fs.openSync(tmp,'wx',0o600);try{fs.writeFileSync(fd,JSON.stringify(data,null,2)+'\n');fs.fsyncSync(fd);}finally{fs.closeSync(fd);}fs.renameSync(tmp,file);}
function directory(out){const p=resolve(out);if(p===ROOT||p.startsWith(ROOT+sep))throw Error('OUTPUT_MUST_BE_OUTSIDE_REPO');fs.mkdirSync(p,{recursive:true,mode:0o700});const c=fs.realpathSync(p);if(c===ROOT||c.startsWith(ROOT+sep))throw Error('OUTPUT_SYMLINK_TO_REPO_REFUSED');return c;}
function event(out,kind,detail={}){const file=join(out,'events.jsonl');regular(file);fs.appendFileSync(file,JSON.stringify({at:new Date().toISOString(),kind,...detail})+'\n',{mode:0o600});}
export function classifyNativeTestRun({exitCode,stdout,stderr='',error=null}){
 const matches=[...String(stdout).matchAll(/^# (tests|pass|fail|cancelled|skipped|todo) (\d+)$/gm)];
 const values=new Map(matches.map(m=>[m[1],Number(m[2])]));
 const valid=matches.length===6 && ['tests','pass','fail','cancelled','skipped','todo'].every(k=>Number.isSafeInteger(values.get(k)));
 const total=values.get('tests')??0,pass=values.get('pass')??0;
 const complete=valid&&total>0&&total===pass+values.get('fail')+values.get('cancelled')+values.get('skipped')+values.get('todo');
 const failed=exitCode!==0||error!==null||!complete||values.get('fail')!==0||values.get('cancelled')!==0;
 return {status:failed?'FAILED':values.get('skipped')||values.get('todo')?'PASS_WITH_UNEXECUTED_CASES':'ALL_EXECUTED_PASS',tests:valid?total:null,pass:valid?pass:null,failed:valid?values.get('fail'):null,cancelled:valid?values.get('cancelled'):null,skipped:valid?values.get('skipped'):null,todo:valid?values.get('todo'):null,exitCode,error:error?String(error).slice(0,300):null,stderrTail:String(stderr).slice(-1000)};
}
export function evaluateCheckout({branch,dirty,tests}){
 if(!SAFE_BRANCH.test(branch)||dirty)throw Error('UNSAFE_CHECKOUT');
 if(tests.length===0||tests.length>500||new Set(tests).size!==tests.length||tests.some(x=>!/^[-a-zA-Z0-9_.]+\.test\.mjs$/.test(x)))throw Error('UNSAFE_TEST_INVENTORY');
 return {branch,tests:[...tests].sort()};
}
function checkout(){
 if(resolve(git(['rev-parse','--show-toplevel']))!==ROOT)throw Error('INCORRECT_REPOSITORY_ROOT');
 const branch=git(['symbolic-ref','--quiet','--short','HEAD']);const sourceSha=git(['rev-parse','HEAD']);
 const tracked=git(['ls-files','-z','--',':(glob).github/continuum/*.test.mjs']).split('\0').filter(Boolean);
 const names=tracked.map(name=>{let full=ROOT;for(const part of name.split('/')){full=join(full,part);if(fs.lstatSync(full).isSymbolicLink())throw Error('UNSAFE_TEST_FILE_TYPE');}if(!fs.lstatSync(full).isFile())throw Error('UNSAFE_TEST_FILE_TYPE');return name.slice('.github/continuum/'.length);});
 const {tests}=evaluateCheckout({branch,dirty:git(['status','--porcelain=v1','--untracked-files=all']).length>0,tests:names});
 return {branch,sourceSha,tree:git(['rev-parse','HEAD^{tree}']),tests,inventoryHash:digest(JSON.stringify(tests.map(name=>({name,sha256:digest(fs.readFileSync(join(ROOT,'.github/continuum',name)))}))))};
}
function options(o={}){
 const value={timeoutMs:360000,heapMb:256,testConcurrency:2,maxEvidenceMb:64,intervalMs:3600000,maxCycles:null,...o};
 if(Object.keys(value).some(k=>!['timeoutMs','heapMb','testConcurrency','maxEvidenceMb','intervalMs','maxCycles'].includes(k))||!Number.isInteger(value.timeoutMs)||value.timeoutMs<100||value.timeoutMs>360000||!Number.isInteger(value.heapMb)||value.heapMb<64||value.heapMb>512||!Number.isInteger(value.maxEvidenceMb)||value.maxEvidenceMb<4||value.maxEvidenceMb>512||![1,2].includes(value.testConcurrency)||!Number.isInteger(value.intervalMs)||value.intervalMs<100||value.intervalMs>86400000||value.maxCycles!==null&&(!Number.isInteger(value.maxCycles)||value.maxCycles<1||value.maxCycles>1000))throw Error('INVALID_WORKER_BUDGET');
 return value;
}
function execute(args,config,{out,id,signal,lease}){
 return new Promise((resolveRun,reject)=>{
  const env={...process.env,GIT_TERMINAL_PROMPT:'0'};delete env.NODE_TEST_CONTEXT;
  const child=spawn(process.execPath,[`--max-old-space-size=${config.heapMb}`,...args],{cwd:ROOT,shell:false,env,detached:true,stdio:['ignore','pipe','pipe']});
  const stdoutParts=[],stderrParts=[];let storedBytes=0,error=null,killed=false,shutdown=null;
  const tap=join(out,'cycles',id+'.tap'),err=join(out,'cycles',id+'.stderr');
  const tapFd=fs.openSync(tap,'wx',0o600),errFd=fs.openSync(err,'wx',0o600);
  const kill=reason=>{if(killed)return;killed=true;error=reason;try{process.kill(-child.pid,'SIGTERM');}catch(e){if(e.code!=='ESRCH')error='SIGNAL_ERROR';}shutdown=new Promise(done=>setTimeout(()=>{try{process.kill(-child.pid,'SIGKILL');}catch{}finally{done();}},500));};
  const onAbort=()=>kill('OWNER_INTERRUPTED');signal?.addEventListener('abort',onAbort,{once:true});
  child.on('spawn',()=>{try{lease.child(child.pid);}catch(e){kill('LEASE_CHILD_BIND_FAILED');}if(signal?.aborted)onAbort();});
  const timer=setTimeout(()=>kill('TIMEOUT'),config.timeoutMs);
  const stopPoll=setInterval(()=>{if(fs.existsSync(join(out,'STOP')))onAbort();},100);
  const take=(chunk,which)=>{const remaining=2*1024*1024-storedBytes;const kept=chunk.subarray(0,Math.max(0,remaining));fs.writeSync(which==='out'?tapFd:errFd,kept);(which==='out'?stdoutParts:stderrParts).push(kept);storedBytes+=kept.length;if(chunk.length>remaining)kill('OUTPUT_LIMIT');};
  child.stdout.on('data',b=>take(b,'out'));child.stderr.on('data',b=>take(b,'err'));
  child.on('error',e=>{error=e.code||'SPAWN_ERROR';});
  child.on('close',async(code,termination)=>{
   clearTimeout(timer);clearInterval(stopPoll);signal?.removeEventListener('abort',onAbort);
   // Ignored-stdio descendants can outlive runner close. Keep the lease until
   // the already requested process-group escalation has run.
   if(shutdown)await shutdown;
   fs.closeSync(tapFd);fs.closeSync(errFd);
   const stdout=Buffer.concat(stdoutParts),stderr=Buffer.concat(stderrParts);
   resolveRun({exitCode:code,stdout:stdout.toString('utf8'),stderr:stderr.toString('utf8'),error:error||termination||null,signal:termination,stdoutHash:digest(stdout),stderrHash:digest(stderr)});
  });
 });
}
async function runCycle(outDirectory,{signal,...supplied}={},heldLease=null){
 const config=options(supplied),out=directory(outDirectory);
 // Cancellation before launch is not a completed test cycle. Do not acquire
 // a lease, write a running intent or start Node for an already stopped job.
 if(signal?.aborted||fs.existsSync(join(out,'STOP')))throw Error('OWNER_INTERRUPTED_BEFORE_START');
 const before=checkout();
 const lease=heldLease??workerLease(git(['rev-parse','--absolute-git-dir']));
 const id=new Date().toISOString().replace(/[:.]/g,'-')+'-'+crypto.randomUUID();
 try{
  const cyclesDir=join(out,'cycles');
  if(fs.existsSync(cyclesDir)&&(!fs.lstatSync(cyclesDir).isDirectory()||fs.lstatSync(cyclesDir).isSymbolicLink()))throw Error('UNSAFE_REPORT_DIRECTORY');
  fs.mkdirSync(cyclesDir,{recursive:true,mode:0o700});
  const used=fs.readdirSync(cyclesDir).reduce((n,name)=>{const st=fs.lstatSync(join(cyclesDir,name));if(!st.isFile())throw Error('UNSAFE_REPORT_FILE');return n+st.size;},0);
  if(used+2*1024*1024+131072>config.maxEvidenceMb*1024*1024)throw Error('EVIDENCE_BUDGET_EXHAUSTED');
  if(lease.recovered&&fs.existsSync(join(out,'running.json')))event(out,'ABANDONED_CYCLE_RECOVERED',{previous:JSON.parse(fs.readFileSync(join(out,'running.json'),'utf8')).id});
  const started=new Date().toISOString();const running={id,sourceSha:before.sourceSha,branch:before.branch,started,pid:process.pid,recoveredLease:lease.recovered};
  atomic(join(out,'running.json'),running);event(out,'CYCLE_STARTED',running);
  const run=await execute(['--test','--test-reporter=tap',`--test-concurrency=${config.testConcurrency}`,...before.tests.map(x=>join('.github','continuum',x))],config,{out,id,signal,lease});
  let result=classifyNativeTestRun(run),sourceUnchanged=false;
  try{const after=checkout();sourceUnchanged=JSON.stringify(after)===JSON.stringify(before);}catch{}
  if(!sourceUnchanged)result={...result,status:'FAILED',error:'CHECKOUT_DRIFT_DURING_TEST'};
  if(run.error==='OWNER_INTERRUPTED')result={...result,status:'INTERRUPTED'};
  let leaseHeld=true;try{lease.check();}catch{leaseHeld=false;result={...result,status:'FAILED',error:'WORKER_LEASE_LOST'};}
  const report={schema:'continuum-local-worker-v2',id,branch:before.branch,sourceSha:before.sourceSha,sourceTree:before.tree,inventoryHash:before.inventoryHash,inventoryCount:before.tests.length,started,completed:new Date().toISOString(),sourceUnchanged,leaseHeld,...result,signal:run.signal,stdoutHash:run.stdoutHash,stderrHash:run.stderrHash,limits:config,recoveredLease:lease.recovered,candidatePromoted:false,patchApplied:false,weightsUpdated:false,ownerReviewRequired:true};
  atomic(join(out,'cycles',id+'.json'),report);atomic(join(out,'latest.json'),report);event(out,'CYCLE_COMPLETED',{id,status:report.status});return report;
 }finally{fs.rmSync(join(out,'running.json'),{force:true});if(!heldLease)lease.release();}
}
export async function runOneCycle(outDirectory,config={}){return runCycle(outDirectory,config);}
export async function runWorker(outDirectory,{signal,...supplied}={}){
 const config=options(supplied),out=directory(outDirectory);let cycles=0,failures=0;
 const stop=new AbortController();const combined=signal?AbortSignal.any([signal,stop.signal]):stop.signal;
 const poll=setInterval(()=>{if(fs.existsSync(join(out,'STOP')))stop.abort();},100);
 let lease;
 try{
  if(combined.aborted||fs.existsSync(join(out,'STOP')))return {cycles,failures,interrupted:true};
  lease=workerLease(git(['rev-parse','--absolute-git-dir']));
  while(!combined.aborted&&!fs.existsSync(join(out,'STOP'))&&(config.maxCycles===null||cycles<config.maxCycles)){
   try{const report=await runCycle(out,{...config,signal:combined},lease);if(report.status!=='ALL_EXECUTED_PASS')failures++;console.log(JSON.stringify(report));}
   catch(e){failures++;event(out,e.message==='OWNER_INTERRUPTED_BEFORE_START'?'CYCLE_INTERRUPTED':'CYCLE_REJECTED',{error:e.message,...(e.message==='OWNER_INTERRUPTED_BEFORE_START'?{started:false}:{})});console.error('LOCAL_WORKER_CYCLE_FAILED: '+e.message);if(e.message==='EVIDENCE_BUDGET_EXHAUSTED')break;}
   cycles++;if(config.maxCycles!==null&&cycles>=config.maxCycles)break;
   try{await delay(config.intervalMs,undefined,{signal:combined});}catch(e){if(e.name!=='AbortError')throw e;}
  }
 }finally{
  clearInterval(poll);if(lease)lease.release();event(out,'WORKER_STOPPED',{cycles,ownerStopped:combined.aborted||fs.existsSync(join(out,'STOP'))});
 }
 return {cycles,failures,interrupted:combined.aborted||fs.existsSync(join(out,'STOP'))};
}
async function main(){
 const [mode,out,flag,file]=process.argv.slice(2);
 if(!['--once','--loop'].includes(mode)||!out||(flag&&flag!=='--config')||(flag&&!file)||process.argv.length>6)throw Error('USAGE: node worker --once|--loop /external/out [--config /external/budget.json]');
 const config=options(file?JSON.parse(fs.readFileSync(file,'utf8')):{});const ctrl=new AbortController();
 for(const sig of ['SIGINT','SIGTERM'])process.once(sig,()=>ctrl.abort());
 if(mode==='--once'){const report=await runOneCycle(out,{...config,signal:ctrl.signal});console.log(JSON.stringify(report));if(report.status!=='ALL_EXECUTED_PASS')process.exitCode=1;}
 else {const summary=await runWorker(out,{...config,signal:ctrl.signal});if(summary.failures>0||summary.interrupted)process.exitCode=1;}
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(e=>{console.error('LOCAL_WORKER_FAILED: '+e.message);process.exitCode=1;});
