#!/usr/bin/env node
/**
 * Continuum Werkstatt v1.
 *
 * Reuses the existing Teamleader, canonical development backlog and M4 release
 * gate. No user text becomes a shell command; no Git push/merge is performed.
 * Gradle/Node tests execute repository code: use a dedicated, non-privileged
 * computer or OS-isolated builder for repositories that are not trusted.
 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {runTeamleaderCycle,localTeamleaderDriver,decodeLeaderState,
  summarizeTeamleaderProgress} from './autonomous-teamleader.mjs';
import {runTeamleaderLoop,validateWorkerHeartbeat} from './local-teamleader-loop.mjs';
import {runM4Gate,verifyReleaseProofFile} from './m4-local-gate.mjs';
import {git} from './repair-loop.mjs';
import {practiceLesson,academyStatus,WORKER_LESSONS,FUTURE_DISCIPLINES} from './worker-academy.mjs';

const MODES=new Set(['doctor','status','once','loop','learn','verify-android']);
const SHA=/^[0-9a-f]{40}$/;
const DEFAULT_STATE=path.join(os.homedir(),'.local','state','device-ai-continuum');
const flags=new Set(['mode','root','state','interval-minutes','max-cycles','budget-minutes']);

export function parseWorkshopArgs(argv){
 const args={mode:'doctor',root:process.cwd(),state:DEFAULT_STATE,
  intervalMinutes:60,maxCycles:null,budgetMinutes:20};
 const used=new Set();
 for(let i=0;i<argv.length;i++){
  const key=argv[i].startsWith('--')?argv[i].slice(2):'';
  if(!flags.has(key)||used.has(key)||i+1>=argv.length||argv[i+1].startsWith('--'))
   throw Error('Unrecognized, duplicate or missing workshop option');
  used.add(key);
  const value=argv[++i];
  if(key==='mode')args.mode=value;
  else if(key==='root')args.root=value;
  else if(key==='state')args.state=value;
  else if(key==='interval-minutes')args.intervalMinutes=Number(value);
  else if(key==='max-cycles')args.maxCycles=Number(value);
  else if(key==='budget-minutes')args.budgetMinutes=Number(value);
 }
 if(!MODES.has(args.mode)||
   !Number.isSafeInteger(args.intervalMinutes)||args.intervalMinutes<1||args.intervalMinutes>1440||
   !Number.isSafeInteger(args.budgetMinutes)||args.budgetMinutes<1||args.budgetMinutes>30||
   (args.maxCycles!==null&&(!Number.isSafeInteger(args.maxCycles)||args.maxCycles<1||args.maxCycles>1000))||
   (args.mode!=='loop'&&(used.has('interval-minutes')||used.has('max-cycles'))))
  throw Error('Invalid workshop command or bounded limits');
 return args;
}

/** Never put a checkpoint or editable report inside the Git checkout. */
export function workshopPaths(root,state){
 const source=fs.realpathSync(root);
 const destination=path.resolve(state);
 if(destination===source||destination.startsWith(source+path.sep)||
    source===destination||source.startsWith(destination+path.sep))
  throw Error('Worker state must be separate from source checkout');
 const head=git(source,['rev-parse','HEAD']).stdout.trim();
 if(!SHA.test(head))throw Error('Workshop requires an immutable Git HEAD');
 return {root:source,state:destination,head};
}

function toolPresent(executable){
 const result=spawnSync(executable,['--version'],{
  encoding:'utf8',timeout:2500,maxBuffer:16*1024,shell:false,
  env:{...process.env,GIT_TERMINAL_PROMPT:'0'}
 });
 return result.status===0&&!result.error;
}
function fileExists(root,file){
 const full=path.join(root,file);
 return fs.existsSync(full)&&fs.lstatSync(full).isFile()&&!fs.lstatSync(full).isSymbolicLink();
}
export function identifyWorkshops(root,available={}){
 const has=tool=>available[tool]===true;
 const android=fileExists(root,'app/build.gradle.kts')&&
  (fileExists(root,'settings.gradle.kts')||fileExists(root,'settings.gradle'));
 const flutter=fileExists(root,'pubspec.yaml')&&fs.existsSync(path.join(root,'lib'));
 const godot=fileExists(root,'project.godot');
 const node=fileExists(root,'package.json')||fileExists(root,'backend/package.json');
 const capabilities=[
  {id:'android-gradle',detected:android,
   executable:has('gradle')&&Boolean(available.androidSdk),
   procedure:'Existing M4 gate: Node checks, independent repair-E2E, Kotlin tests, lint, isolated preview APK',
   reason:!android?'ANDROID_PROJECT_NOT_DETECTED':!has('gradle')?'GRADLE_UNAVAILABLE':
     !available.androidSdk?'ANDROID_SDK_UNAVAILABLE':null},
  {id:'node',detected:node,executable:node&&has('node'),
   procedure:'Existing node-continuum and node-backend fixed test profiles',
   reason:!node?'NODE_PROJECT_NOT_DETECTED':!has('node')?'NODE_UNAVAILABLE':null},
  {id:'flutter',detected:flutter,executable:false,
   procedure:'Recognized; Flutter recipe, platform signing and integration-test adapter require independent validation',
   reason:flutter?'RECIPE_NOT_YET_VERIFIED':'FLUTTER_PROJECT_NOT_DETECTED'},
  {id:'godot',detected:godot,executable:false,
   procedure:'Recognized; signed Android export preset and verified Godot recipe are not yet integrated',
   reason:godot?'RECIPE_NOT_YET_VERIFIED':'GODOT_PROJECT_NOT_DETECTED'}
 ];
 return capabilities;
}

export function diagnoseWorkshop({root,state,head},tools={
 node:toolPresent('node'),gradle:toolPresent('gradle'),
 androidSdk:Boolean(process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT)
}){
 const dirty=git(root,['status','--porcelain']).stdout.trim()!=='';
 return {
  schema:'continuum-workshop-doctor',version:1,head,
  sourceClean:!dirty,mainWritesAllowed:false,
  capabilities:identifyWorkshops(root,tools),
  academyCurriculum:WORKER_LESSONS.map(x=>({id:x.id,topic:x.topic,limit:x.limit})),
  plannedDisciplines:FUTURE_DISCIPLINES,
  limits:['Dedicated OS isolation required for untrusted repository build scripts',
   'Existing development backlog is authoritative',
   'No automatic merges, installations or privileged device access',
   'A configured compiler/SDK and real executed tests are mandatory']
 };
}

function writePrivateJson(file,data){
 fs.mkdirSync(path.dirname(file),{recursive:true,mode:0o700});
 const temp=file+'.tmp-'+process.pid+'-'+crypto.randomBytes(5).toString('hex');
 try{
  const fd=fs.openSync(temp,'wx',0o600);
  try{fs.writeFileSync(fd,JSON.stringify(data,null,2)+'\n');fs.fsyncSync(fd);}
  finally{fs.closeSync(fd);}
  fs.renameSync(temp,file);
 }finally{fs.rmSync(temp,{force:true});}
}

export function readWorkshopStatus({root,state,head}){
 const heartbeatFile=path.join(state,'workshop-heartbeat.json');
 const leaderFile=path.join(state,'teamleader-state.json');
 let heartbeat=null,progress=null,heartbeatState='NOT_CONNECTED';
 if(fs.existsSync(heartbeatFile)){
  try {
   const value=JSON.parse(fs.readFileSync(heartbeatFile,'utf8'));
   if(!validateWorkerHeartbeat(value))throw Error('INVALID_HEARTBEAT');
   heartbeat=value;
   heartbeatState=value.state==='RUNNING'&&Date.now()-value.startedAt>40*60_000
    ?'STALE_RUNNING':value.state;
  }catch { heartbeatState='CORRUPT'; }
 }
 if(fs.existsSync(leaderFile)){
  try {
   const state=decodeLeaderState(fs.readFileSync(leaderFile));
   progress=summarizeTeamleaderProgress(state);
  }catch { progress={valid:false,reason:'CORRUPT_TEAMLEADER_STATE'}; }
 }
 return {schema:'continuum-workshop-status',version:1,
  head,heartbeatState,heartbeat,progress,
  academy:academyStatus({root,state}),automaticMerge:false,
  note:'Only host-side signed/tested evidence proves a completed build. Android READY is local status only.'};
}

export async function executeWorkshop(args,{driverFactory=localTeamleaderDriver,
 gate=runM4Gate,practice=practiceLesson,onHeartbeat=()=>{}}={}){
 const target=workshopPaths(args.root,args.state);
 if(args.mode==='doctor')return diagnoseWorkshop(target);
 if(args.mode==='status')return readWorkshopStatus(target);
 if(git(target.root,['status','--porcelain']).stdout.trim())
  throw Error('Worker refuses a dirty source checkout');
 if(args.mode==='learn')return {mode:'learn',...practice({root:target.root,state:target.state}),automaticMerge:false};
 if(args.mode==='verify-android'){
  const outcome=gate({root:target.root,out:path.join(target.state,'proofs'),
   android:true,budgetMinutes:Math.min(120,Math.max(30,args.budgetMinutes*3))});
  if(outcome.head!==target.head||outcome.complete!==true)throw Error('Android M4 gate not complete; no accepted APK');
  const proof=verifyReleaseProofFile(outcome.path,{expectedHead:target.head});
  if(!proof.releaseReady)throw Error('Android gate proof not release ready');
  return {mode:'verify-android',head:target.head,
   complete:true,proof:outcome.path,artifact:outcome.apk.artifact,
   artifactSha256:outcome.apk.artifactSha256,automaticMerge:false};
 }
 const driver=await driverFactory();
 const practiceAfterCycle=()=>{
  // Old and non-Continuum checkouts have no fixed approved curriculum.
  // A running Continuum checkout practices exactly one skill per worker cycle.
  if(!fileExists(target.root,'.github/continuum/worker-academy.mjs'))
   return {status:'ACADEMY_NOT_INSTALLED',passed:false};
  try{return practice({root:target.root,state:target.state});}
  catch(e){return {status:'ACADEMY_FAILED',passed:false,errorCode:String(e?.message??e).slice(0,100)};}
 };
 const work=()=>{
  const result=runTeamleaderCycle({
   root:target.root,stateDir:target.state,driver,budgetMinutes:args.budgetMinutes
  });
  return {...result,skillPractice:practiceAfterCycle()};
 };
 if(args.mode==='once'){
  const result=work();
  return {mode:'once',...result,automaticMerge:false};
 }
 const heartbeatPath=path.join(target.state,'workshop-heartbeat.json');
 const controller=new AbortController();
 const stop=()=>controller.abort();
 process.once('SIGINT',stop);process.once('SIGTERM',stop);
 try{
  const result=await runTeamleaderLoop({
   work,intervalMinutes:args.intervalMinutes,maxCycles:args.maxCycles,
   signal:controller.signal,
   onHeartbeat:hb=>{writePrivateJson(heartbeatPath,hb);onHeartbeat(hb);}
  });
  return {mode:'loop',...result,automaticMerge:false};
 }finally{
  process.removeListener('SIGINT',stop);process.removeListener('SIGTERM',stop);
 }
}

if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href){
 try{
  const args=parseWorkshopArgs(process.argv.slice(2));
  const result=await executeWorkshop(args);
  process.stdout.write(JSON.stringify(result,null,2)+'\n');
 }catch(error){
  console.error('[continuum-workshop]',String(error?.message??error));
  process.exitCode=1;
 }
}
