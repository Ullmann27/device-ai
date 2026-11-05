#!/usr/bin/env node
/** Local discovery bot using the existing cadence and canonical proposed-task backlog. */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {setImmediate as checkpoint} from 'node:timers/promises';
import {canonical,sha256Hex} from './development-backlog.mjs';
import {acquireLoopLock,runTeamleaderLoop,validateLoopOptions} from './local-teamleader-loop.mjs';
import {privateDirectory,plainBytes,writePrivateJson} from './coding-service-store.mjs';
import {discoverBranches,proposeBranchReviews,branchStateDirectory,assertBranchStateFile,assertBranchSourceSafe} from './branch-discovery.mjs';
import {inspectHostEnvironment} from '../../scripts/continuum-release-host-preflight.mjs';
import {resolveTermuxEnvironment} from '../../scripts/continuum-mobile-builder.mjs';

const NUMBERS={maxSources:[1,5000,1000],maxChanges:[1,2000,200],timeoutMs:[1,300000,60000],
 maxProposals:[1,100,20],intervalMinutes:[1,1440,60],maxCycles:[1,1000,null]};

export function parseBranchBotArgs(args){
 const flags=new Set(['--once','--loop','--continue-scan']);
 const valued=new Set(['--root','--state','--target','--max-sources','--max-changes',
  '--timeout-ms','--max-proposals','--interval-minutes','--max-cycles']);
 const options={},seen=new Set();
 for(let i=0;i<args.length;i++){
  const arg=args[i];
  if(seen.has(arg))throw Error('BRANCH_BOTS_DUPLICATE_OPTION');seen.add(arg);
  if(flags.has(arg)){options[arg.slice(2).replace(/-([a-z])/g,(_,c)=>c.toUpperCase())]=true;continue;}
  if(!valued.has(arg))throw Error('BRANCH_BOTS_UNKNOWN_OPTION');
  const value=args[++i];if(!value||value.startsWith('--'))throw Error('BRANCH_BOTS_INVALID_ARGUMENT');
  options[arg.slice(2).replace(/-([a-z])/g,(_,c)=>c.toUpperCase())]=value;
 }
 if([options.once,options.loop].filter(Boolean).length!==1)throw Error('BRANCH_BOTS_MODE_REQUIRED');
 if(!options.state)throw Error('BRANCH_BOTS_INVALID_STATE');
 if(options.continueScan&&!options.once)throw Error('BRANCH_BOTS_INVALID_CONTINUE_MODE');
 if(options.loop&&options.maxCycles===undefined)throw Error('BRANCH_BOTS_FINITE_LOOP_REQUIRED');
 if(!options.loop&&(options.maxCycles!==undefined||options.intervalMinutes!==undefined))throw Error('BRANCH_BOTS_LOOP_OPTIONS_REQUIRE_LOOP');
 for(const [key,[min,max,fallback]] of Object.entries(NUMBERS)){
  const value=options[key];
  if(value===undefined){options[key]=fallback;continue;}
  if(!/^[0-9]+$/.test(value)||!Number.isSafeInteger(Number(value))||Number(value)<min||Number(value)>max)
   throw Error('BRANCH_BOTS_INVALID_'+key.toUpperCase());
  options[key]=Number(value);
 }
 if(options.loop)validateLoopOptions(options);
 return options;
}

function immutableJson(file,value){
 const bytes=Buffer.from(JSON.stringify(value)+'\n');
 assertBranchStateFile(file);
 if(!fs.existsSync(file)){
  try{writePrivateJson(file,value,{exclusive:true});}
  catch(error){if(error.code!=='EEXIST')throw error;}
 }
 if(!plainBytes(file,4*1024*1024,{privateFile:true}).equals(bytes))throw Error('BRANCH_BOTS_IMMUTABLE_RECORD_CHANGED');
}

export async function runBranchBotCycle({root,stateDir,targetRef='HEAD',maxSources=1000,maxChanges=200,
 timeoutMs=60000,maxProposals=20,signal=null,cursor=null,paged=false}={}){
 if(signal?.aborted)return {outcome:'INTERRUPTED',complete:false,detail:'Stopped before branch discovery'};
 const report=discoverBranches({root,targetRef,maxSources,maxChanges,timeoutMs,cursor});
 // Synchronous bounded Git reads yield before any queue/write boundary so
 // SIGINT/SIGTERM observed during discovery can stop the cycle without work admission.
 await checkpoint();
 if(signal?.aborted)return {outcome:'INTERRUPTED',complete:false,detail:'Stopped after read-only branch discovery'};
 // Reuse the existing host profile. Tool availability never proves a build,
 // dependency-cache completeness, device behaviour or release acceptance.
 const apkEnvironment=inspectHostEnvironment(resolveTermuxEnvironment());
 await checkpoint();
 if(signal?.aborted)return {outcome:'INTERRUPTED',complete:false,detail:'Stopped after APK environment diagnosis'};
 const state=branchStateDirectory(root,stateDir);privateDirectory(state);
 const reports=path.join(state,'branch-discovery-reports');privateDirectory(reports,{create:true});
 const artifact={...report,apkEnvironment};
 const digest=sha256Hex(Buffer.from(JSON.stringify(artifact)+'\n')),file=path.join(reports,digest+'.json');
 immutableJson(file,artifact);
 const queue=proposeBranchReviews({report,stateDir:state,maxProposals});
 const pageComplete=!report.branches.some(branch=>branch.changesTruncated);
 return {outcome:report.coverage.complete||paged&&pageComplete?'DONE_INVESTIGATION':'ERROR',complete:report.coverage.complete,
  detail:report.coverage.complete?'Pinned branch differences recorded; integration review pending':
   paged&&pageComplete?'Bounded source page recorded; full inventory unverified':'Incomplete branch/path coverage; no full inventory claim',
  targetCommit:report.targetCommit,report:file,reportSha256:digest,coverage:report.coverage,queue,pageComplete,nextCursor:report.nextCursor,
  executedTests:0,apk:'NOT_RUN',apkEnvironment,approvedForMerge:false};
}

function readScanState(file,config){
 assertBranchStateFile(file);
 if(!fs.existsSync(file))return null;
 let saved;
 try{saved=JSON.parse(plainBytes(file,65536,{privateFile:true}));}
 catch{throw Error('BRANCH_BOTS_SCAN_STATE_INVALID');}
 if(!saved||typeof saved!=='object'||Array.isArray(saved))throw Error('BRANCH_BOTS_SCAN_STATE_INVALID');
 const {integrity,...data}=saved;
 if(Object.keys(data).sort().join()!=='configSha256,lastReportSha256,nextCursor,schema,version'||
    data.schema!=='continuum-branch-scan-cursor'||data.version!==1||
    !/^[a-f0-9]{64}$/.test(data.configSha256)||!/^[a-f0-9]{64}$/.test(data.lastReportSha256)||
    integrity!==sha256Hex(canonical(data))||
    data.nextCursor!==null&&(!data.nextCursor||typeof data.nextCursor!=='object'||Array.isArray(data.nextCursor)))
  throw Error('BRANCH_BOTS_SCAN_STATE_INVALID');
 if(data.configSha256!==sha256Hex(canonical(config)))throw Error('BRANCH_BOTS_SCAN_CONFIG_CHANGED');
 const reportFile=path.join(path.dirname(file),'branch-discovery-reports',data.lastReportSha256+'.json');
 let report;
 try{
  const bytes=plainBytes(reportFile,4*1024*1024,{privateFile:true});
  if(sha256Hex(bytes)!==data.lastReportSha256)throw Error('HASH_MISMATCH');
  report=JSON.parse(bytes);
  if(report.schema!=='continuum-branch-discovery'||report.version!==1||
     report.sourceRoot!==config.root||report.targetRef!==config.targetRef||
     canonical(report.nextCursor)!==canonical(data.nextCursor))throw Error('CURSOR_MISMATCH');
 }catch{throw Error('BRANCH_BOTS_SCAN_REPORT_CHANGED');}
 return data;
}

async function runBranchBotPage({root,stateDir,signal,...config}){
 if(signal?.aborted)return {outcome:'INTERRUPTED',complete:false,detail:'Stopped before source page'};
 assertBranchSourceSafe(root);
 const state=branchStateDirectory(root,stateDir);privateDirectory(state);
 const file=path.join(state,'branch-bots-scan.json');assertBranchStateFile(file);
 const release=acquireLoopLock(file);
 try{
  const settings={root,...config},saved=readScanState(file,settings);
  let cycle,scanRestarted=false;
  try{cycle=await runBranchBotCycle({root,stateDir:state,signal,...config,paged:true,cursor:saved?.nextCursor??null});}
  catch(error){
   if(error.message!=='DISCOVERY_CURSOR_STALE')throw error;
   scanRestarted=true;
   cycle=await runBranchBotCycle({root,stateDir:state,signal,...config,paged:true});
  }
  await checkpoint();
  if(signal?.aborted)return {...cycle,outcome:'INTERRUPTED',complete:false,detail:'Stopped before cursor persistence'};
  if(!cycle.report)return cycle;
  const data={schema:'continuum-branch-scan-cursor',version:1,configSha256:sha256Hex(canonical(settings)),
   nextCursor:cycle.nextCursor,lastReportSha256:cycle.reportSha256};
  writePrivateJson(file,{...data,integrity:sha256Hex(canonical(data))});
  return {...cycle,scanRestarted,scanState:file};
 }finally{release();}
}

export async function branchBotsMain(argv=process.argv.slice(2)){
 const options=parseBranchBotArgs(argv),root=fs.realpathSync(path.resolve(options.root??process.cwd()));
 // The existing scheduler must not inherit a different Git worktree or index.
 for(const key of ['GIT_DIR','GIT_WORK_TREE','GIT_INDEX_FILE','GIT_COMMON_DIR','GIT_OBJECT_DIRECTORY',
  'GIT_ALTERNATE_OBJECT_DIRECTORIES','GIT_SHALLOW_FILE','GIT_NAMESPACE','GIT_CONFIG'])
  if(process.env[key]!==undefined)throw Error('BRANCH_BOTS_INVALID_GIT_ENVIRONMENT');
 assertBranchSourceSafe(root);
 const stateDir=branchStateDirectory(root,options.state);privateDirectory(stateDir);
 const config={targetRef:options.target??'HEAD',maxSources:options.maxSources,maxChanges:options.maxChanges,
  timeoutMs:options.timeoutMs,maxProposals:options.maxProposals};
 const controller=new AbortController(),stop=()=>controller.abort();
 process.on('SIGINT',stop);process.on('SIGTERM',stop);
 try{
  if(options.once){
   const cycle=await (options.continueScan?runBranchBotPage:runBranchBotCycle)({root,stateDir,...config,signal:controller.signal});
   return {mode:'LOCAL_BRANCH_BOTS',cycle,exitCode:cycle.outcome==='DONE_INVESTIGATION'?0:1};
  }
  // Cadence owns restart/locking; this file binds its work callback's immutable settings.
  immutableJson(path.join(stateDir,'branch-bots-loop-config.json'),{
   schema:'continuum-branch-bots-loop-config',version:1,root,...config});
  const stateFile=path.join(stateDir,'branch-bots-loop.json');assertBranchStateFile(stateFile);
  const heartbeat=path.join(stateDir,'branch-bots-heartbeat.json');assertBranchStateFile(heartbeat);
  let cycle=null;
  const loop=await runTeamleaderLoop({sourceRoot:root,stateFile,intervalMinutes:options.intervalMinutes,
   maxCycles:options.maxCycles,signal:controller.signal,sourcePreflight:()=>assertBranchSourceSafe(root),
   work:async()=>{
    // Configuration is checked again immediately before each scheduler callback.
    assertBranchSourceSafe(root);
    cycle=await runBranchBotPage({root,stateDir,...config,signal:controller.signal});return cycle;
   },
   onHeartbeat:value=>writePrivateJson(heartbeat,value)});
  return {mode:'LOCAL_BRANCH_BOTS',loop,cycle,exitCode:loop.failures>0||loop.stoppedBySignal||loop.state==='ERROR'?1:0};
 }finally{process.off('SIGINT',stop);process.off('SIGTERM',stop);}
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 branchBotsMain().then(result=>{process.stdout.write(JSON.stringify(result,null,2)+'\n');process.exitCode=result.exitCode;})
  .catch(error=>{process.stderr.write(String(error?.message??error).slice(0,300)+'\n');process.exitCode=1;});
}
