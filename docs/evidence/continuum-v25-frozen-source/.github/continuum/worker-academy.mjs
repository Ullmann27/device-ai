#!/usr/bin/env node
/**
 * Continuum Worker Academy: supervised-by-tests, offline practice.
 *
 * The academy learns which verified procedures work for THIS source revision.
 * It does not train an LLM, crawl uncontrolled websites, or promote raw texts
 * to facts. Source and test content are always treated as data, not commands.
 * Only fixed, reviewed Node test paths are executable; no user-provided shell.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {git} from './repair-loop.mjs';

const SHA=/^[a-f0-9]{40}$/;
const SHA256=/^[a-f0-9]{64}$/;
const MAX_HISTORY=80;
const DEFAULT_STATE=path.join(os.homedir(),'.local','state','device-ai-continuum');

export const WORKER_LESSONS=Object.freeze([
 {id:'android-architecture',topic:'Android/Kotlin and APK safety',
  source:'app/src/main/java/at/heinz/deviceai/cognitive/DeviceTeamleader.kt',
  test:'.github/continuum/android-integration-wiring.test.mjs',
  limit:'Static wiring tests cannot prove functionality on a physical Samsung.'},
 {id:'node-api-security',topic:'Node services, consent and Groq boundaries',
  source:'backend/src/groq-mentor.js',
  test:'.github/continuum/groq-mentor-ui-wiring.test.mjs',
  limit:'Mocked Node tests cannot prove live upstream availability.'},
 {id:'secure-networking',topic:'Secure service boundaries and authorization',
  source:'backend/src/mentor-pairing.js',
  test:'backend/test/mentor-pairing.test.js',
  limit:'Protocol tests do not replace penetration testing or third-party audits.'},
 {id:'repair-engineering',topic:'Git diagnostics and bounded repair',
  source:'.github/continuum/repair-loop.mjs',
  test:'.github/continuum/repair-diagnostics.test.mjs',
  limit:'Diagnostics tests cannot establish autonomous coding competence.'},
 {id:'research-evidence',topic:'Research provenance, trust classes and evidence',
  source:'.github/continuum/research-learning-cycle.mjs',
  test:'.github/continuum/research-learning-cycle.test.mjs',
  limit:'Test evidence cannot establish truth for external claims.'},
 {id:'neural-retrieval',topic:'CRE1/SWB1 verified retrieval experiments',
  source:'.github/continuum/cre1-blacklab-optimizer.mjs',
  test:'.github/continuum/cre1-blacklab-optimizer.test.mjs',
  limit:'Retrieval checks do not train or validate a generative language model.'},
 {id:'teamleader-recovery',topic:'Worker scheduling, persistence and recovery',
  source:'.github/continuum/autonomous-teamleader.mjs',
  test:'.github/continuum/autonomous-teamleader.test.mjs',
  limit:'Unit tests do not imply the host worker is always running.'}
].map(item=>Object.freeze(item)));

export const FUTURE_DISCIPLINES=Object.freeze([
 {id:'ios-swift',state:'NEEDS_MACOS_TOOLCHAIN',reason:'Xcode and an eligible Apple signing environment are needed for real iOS compilation.'},
 {id:'flutter-dart',state:'RECIPE_NOT_VERIFIED',reason:'Compiler, device integration and signed artifacts need a verified build recipe.'},
 {id:'godot',state:'RECIPE_NOT_VERIFIED',reason:'Requires verified Godot Android exporter and signed export preset.'},
 {id:'financial-systems',state:'NO_DOMAIN_CERTIFICATION',reason:'Practice uses only synthetic data; no banking credentials or production transactions.'},
 {id:'mobile-security',state:'AUTHORIZED_TESTING_ONLY',reason:'No external scanning or exploitation without explicit ownership and scope approval.'}
].map(item=>Object.freeze(item)));

function digest(bytes){return crypto.createHash('sha256').update(bytes).digest('hex');}
function safeFile(root,name){
 if(!/^[A-Za-z0-9._/-]{4,180}$/.test(name)||name.startsWith('/')||
     name.split('/').includes('..'))throw Error('INVALID_CURRICULUM_PATH');
 const full=path.resolve(root,name);
 if(!full.startsWith(root+path.sep))throw Error('LESSON_OUTSIDE_REPOSITORY');
 const parts=name.split('/');
 let cursor=root;
 for(const part of parts){
  cursor=path.join(cursor,part);
  const info=fs.lstatSync(cursor);
  if(info.isSymbolicLink())throw Error('SYMLINK_IN_LESSON');
 }
 const stat=fs.statSync(full);
 if(!stat.isFile()||stat.size===0||stat.size>2_000_000)
  throw Error('UNSAFE_LESSON_SIZE');
 return {full,sha256:digest(fs.readFileSync(full))};
}
function atomic(file,obj){
 fs.mkdirSync(path.dirname(file),{recursive:true,mode:0o700});
 const tmp=file+'.tmp-'+process.pid+'-'+crypto.randomBytes(8).toString('hex');
 try{
  const fd=fs.openSync(tmp,'wx',0o600);
  try{fs.writeFileSync(fd,JSON.stringify(obj,null,2)+'\n');fs.fsyncSync(fd);}
  finally{fs.closeSync(fd);}
  fs.renameSync(tmp,file);
 }finally{fs.rmSync(tmp,{force:true});}
}
function readLedger(file){
 if(!fs.existsSync(file))return {schema:'continuum-worker-academy',version:1,sequence:0,history:[]};
 const st=fs.lstatSync(file);
 if(!st.isFile()||st.isSymbolicLink()||st.size>130_000)
  throw Error('UNTRUSTED_ACADEMY_LEDGER');
 const doc=JSON.parse(fs.readFileSync(file,'utf8'));
 if(doc.schema!=='continuum-worker-academy'||doc.version!==1||
    !Number.isSafeInteger(doc.sequence)||doc.sequence<0||
    !Array.isArray(doc.history)||doc.history.length>MAX_HISTORY)
  throw Error('CORRUPT_ACADEMY_LEDGER');
 for(const h of doc.history){
  if(!WORKER_LESSONS.some(l=>l.id===h.lesson)||!SHA.test(h.head)||
    !SHA256.test(h.sourceSha256)||!SHA256.test(h.testSha256)||
    typeof h.passed!=='boolean'||!Number.isSafeInteger(h.executed)||
    h.executed<0||!Number.isSafeInteger(h.at)||h.at<0||
    !['PASSED','FAILED','NOT_EXECUTED','TIMEOUT'].includes(h.status))
   throw Error('CORRUPT_ACADEMY_EVIDENCE');
 }
 return doc;
}
function ensureLocation(root,state){
 const src=fs.realpathSync(root),store=path.resolve(state);
 if(src===store||src.startsWith(store+path.sep)||store.startsWith(src+path.sep))
  throw Error('Academy state must be strictly outside the Git checkout');
 const head=git(src,['rev-parse','HEAD']).stdout.trim();
 if(!SHA.test(head))throw Error('Immutable Git HEAD required');
 return {src,store,head};
}

export function academyStatus({root,state=DEFAULT_STATE}){
 const {src,store,head}=ensureLocation(root,state);
 const ledger=readLedger(path.join(store,'worker-academy-v1.json'));
 const latest=WORKER_LESSONS.map(l=>{
  const seen=[...ledger.history].reverse().find(h=>h.lesson===l.id);
  return {lesson:l.id,topic:l.topic,
   status:seen?.status??'NOT_PRACTICED',
   sourceHead:seen?.head??null,verifiedForCurrentHead:Boolean(seen?.passed&&seen.head===head),
   checksExecuted:seen?.executed??0,limit:l.limit};
 });
 return {schema:'continuum-worker-academy-status',version:1,head,
  completedPracticeRuns:ledger.history.filter(x=>x.passed).length,
  latest,planned:FUTURE_DISCIPLINES,
  learns:'Verified test/recipe performance, NOT neural weights or general domain expertise',
  externalActions:false,changesToMain:false};
}

/** Exactly one fixed test suite per call; never execute arbitrary user text. */
export function practiceLesson({
 root,state=DEFAULT_STATE,run=spawnSync,clock=Date.now,deadlineMs=90_000
}={}){
 if(!Number.isSafeInteger(deadlineMs)||deadlineMs<1000||deadlineMs>300_000)
  throw Error('INVALID_ACADEMY_BUDGET');
 const {src,store,head}=ensureLocation(root,state);
 if(git(src,['status','--porcelain']).stdout.trim())
  throw Error('Academy refuses dirty checkout');
 fs.mkdirSync(store,{recursive:true,mode:0o700});
 const lock=path.join(store,'worker-academy.lock');
 let lockFd;
 try {lockFd=fs.openSync(lock,'wx',0o600);}
 catch(e){if(e.code==='EEXIST')throw Error('ACADEMY_BUSY');throw e;}
 try{
  const file=path.join(store,'worker-academy-v1.json');
  const doc=readLedger(file);
  const lesson=WORKER_LESSONS[doc.sequence%WORKER_LESSONS.length];
  const source=safeFile(src,lesson.source);
  const test=safeFile(src,lesson.test);
  const result=run(process.execPath,
   ['--test','--test-reporter=tap',test.full],
   {cwd:src,timeout:deadlineMs,encoding:'utf8',shell:false,
    maxBuffer:750_000,env:{...process.env,GIT_TERMINAL_PROMPT:'0',
     CONTINUUM_WORKER_ACADEMY:'1'}});
  const output=String(result.stdout??'');
  const count=/# tests (\d+)(?:\r?\n|$)/g;
  const failures=/# fail (\d+)(?:\r?\n|$)/g;
  let exec=0,failed=Number.POSITIVE_INFINITY;
  for(const m of output.matchAll(count))exec=Number(m[1]);
  for(const m of output.matchAll(failures))failed=Number(m[1]);
  const passed=result.status===0&&!result.error&&!result.signal&&exec>0&&failed===0;
  const status=passed?'PASSED':
   result.error?.code==='ETIMEDOUT'||result.signal?'TIMEOUT':
   exec===0?'NOT_EXECUTED':'FAILED';
  const evidence={lesson:lesson.id,head,sourceSha256:source.sha256,
   testSha256:test.sha256,executed:exec,passed,status,at:clock()};
  const updated={schema:doc.schema,version:1,sequence:doc.sequence+1,
   history:[...doc.history,evidence].slice(-MAX_HISTORY)};
  atomic(file,updated);
  return {schema:'continuum-worker-practice-evidence',version:1,...evidence,
   discipline:lesson.topic,capability:'Verified fixed test recipe only',
   userSecretsRead:false,automaticMerge:false};
 }finally{
  fs.closeSync(lockFd);
  fs.unlinkSync(lock);
 }
}

export function academyCliArgs(argv){
 const allowed=new Set(['mode','root','state']);
 const o={mode:'status',root:process.cwd(),state:DEFAULT_STATE};
 const seen=new Set();
 for(let i=0;i<argv.length;i++){
  const k=argv[i].startsWith('--')?argv[i].slice(2):'';
  if(!allowed.has(k)||seen.has(k)||i+1>=argv.length||argv[i+1].startsWith('--'))
   throw Error('Invalid academy CLI option');
  seen.add(k);o[k]=argv[++i];
 }
 if(!['status','practice'].includes(o.mode))throw Error('Unknown academy mode');
 return o;
}

if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href){
 try{
  const opts=academyCliArgs(process.argv.slice(2));
  const out=opts.mode==='practice'?practiceLesson(opts):academyStatus(opts);
  process.stdout.write(JSON.stringify(out,null,2)+'\n');
  if(opts.mode==='practice'&&!out.passed)process.exitCode=1;
 }catch(e){console.error('[worker-academy]',String(e?.message??e));process.exitCode=1;}
}
