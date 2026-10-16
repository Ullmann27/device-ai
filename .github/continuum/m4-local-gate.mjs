#!/usr/bin/env node
/**
 * Offline milestone evidence generator. No GitHub Actions, paid model or CI
 * service required. Runs real fixed test profiles inside a detached Git
 * worktree; only immutable reports are written outside the checkout.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {git,createWorktree,removeWorktree,runChain} from './repair-loop.mjs';
import {resolveOutputDestination} from './backlog-repair-worker.mjs';

const SHA=/^[0-9a-f]{40}$/;
const hash=s=>crypto.createHash('sha256').update(s).digest('hex');

export function gateSummary(node,android,apk,runAndroid){
 const valid=x=>x?.passed===true&&Array.isArray(x.results)&&x.results.length>0&&
   x.results.every(r=>r.passed===true&&r.tests?.failed===0)&&
   x.results.some(r=>r.testProfile&&r.tests?.executed>0);
 const nodeOK=valid(node);
 const androidOK=runAndroid&&valid(android);
 const apkOK=runAndroid&&apk?.exitCode===0&&apk?.timedOut===false;
 return {
  passed:nodeOK&&(!runAndroid||(androidOK&&apkOK)),
  nodePassed:nodeOK,
  androidPassed:runAndroid?androidOK:null,
  apkPassed:runAndroid?apkOK:null,
  scope:runAndroid?'NODE_KOTLIN_APK':'NODE_ONLY',
  executedNodeTests:node?.results?.reduce((sum,r)=>sum+(r.tests?.executed??0),0)??0,
  executedAndroidTests:android?.results?.reduce((sum,r)=>sum+(r.tests?.executed??0),0)??0
 };
}

export function parseGateArgs(args){
 const opts={android:false,budgetMinutes:60};
 const seen=new Set();
 for(let i=0;i<args.length;i++){
  const flag=args[i];
  if(!['--root','--out','--budget-minutes','--android'].includes(flag)||seen.has(flag))
   throw Error('Unknown or duplicate gate option '+flag);
  seen.add(flag);
  if(flag==='--android'){opts.android=true;continue;}
  const value=args[++i];
  if(!value||value.startsWith('--'))throw Error('Missing value for '+flag);
  if(flag==='--root')opts.root=value;
  else if(flag==='--out')opts.out=value;
  else opts.budgetMinutes=Number(value);
 }
 if(!Number.isSafeInteger(opts.budgetMinutes)||opts.budgetMinutes<1||opts.budgetMinutes>120)
  throw Error('Gate budget must be between 1 and 120 minutes');
 return opts;
}

function safeStage(fn){
 try{return {result:fn(),error:null};}
 catch(e){return {result:null,error:String(e?.message??e).slice(0,500)};}
}

export function runM4Gate({root=process.cwd(),out=path.join(os.homedir(),'.local','state','device-ai-continuum','proofs'),
 android=false,budgetMinutes=60}={}){
 if(!Number.isSafeInteger(budgetMinutes)||budgetMinutes<1||budgetMinutes>120)
  throw Error('Gate budget must be 1..120 minutes');
 const source=fs.realpathSync(root);
 const destination=resolveOutputDestination(out);
 if(destination===source||destination.startsWith(source+path.sep))
  throw Error('Evidence directory must remain outside the repository');
 const head=git(source,['rev-parse','HEAD']).stdout.trim();
 if(!SHA.test(head))throw Error('Expected immutable 40-character Git HEAD');
 if(git(source,['status','--porcelain']).stdout.trim())
  throw Error('Cannot certify a dirty checkout');
 fs.mkdirSync(destination,{recursive:true,mode:0o700});
 const outDir=fs.mkdtempSync(path.join(destination,'m4-'+head.slice(0,12)+'-'));
 const deadline=Date.now()+budgetMinutes*60_000;
 const report={schema:'continuum-m4-local-gate',version:1,head,
  startedAt:new Date().toISOString(),node:null,android:null,apk:null,
  errors:[],complete:false,summary:null};
 let wt=null;
 try{
  wt=createWorktree(source,head);
  const nodeDir=path.join(outDir,'node');
  fs.mkdirSync(nodeDir,{recursive:true,mode:0o700});
  const nodeResult=safeStage(()=>runChain('node',wt,nodeDir,{deadline}));
  report.node=nodeResult.result;
  if(nodeResult.error)report.errors.push('NODE: '+nodeResult.error);
  if(android){
   const androidDir=path.join(outDir,'android');
   fs.mkdirSync(androidDir,{recursive:true,mode:0o700});
   const ar=safeStage(()=>runChain('android',wt,androidDir,{deadline}));
   report.android=ar.result;
   if(ar.error)report.errors.push('ANDROID: '+ar.error);
   const remaining=deadline-Date.now();
   if(remaining>0){
    const gradle=spawnSync('gradle',[':app:assembleDebug','--console=plain',
      '-Pkotlin.incremental=false'],{
      cwd:wt,timeout:Math.min(remaining,30*60_000),encoding:'utf8',
      shell:false,maxBuffer:4*1024*1024,
      env:{...process.env,GIT_TERMINAL_PROMPT:'0'}
    });
    report.apk={exitCode:gradle.status,timedOut:gradle.error?.code==='ETIMEDOUT'||
      Boolean(gradle.signal),error:gradle.error?.message?.slice(0,300)??null,
      outputTail:(gradle.stdout??'').slice(-1000)+'\n'+(gradle.stderr??'').slice(-1000)};
   }else report.errors.push('APK: gate budget exhausted');
  }
 }catch(e){
  report.errors.push('GATE: '+String(e?.message??e).slice(0,500));
 }finally{
  if(wt){
   try{removeWorktree(source,wt);}
   catch(e){report.errors.push('WORKTREE_CLEANUP: '+String(e.message).slice(0,250));}
  }
  const status=git(source,['status','--porcelain'],{allowFailure:true});
  if(status.status!==0||status.stdout.trim())
   report.errors.push('SOURCE: checkout not clean after gate');
  report.summary=gateSummary(report.node,report.android,report.apk,android);
  report.complete=report.summary.passed&&report.errors.length===0;
  report.finishedAt=new Date().toISOString();
  report.reportSha256=hash(JSON.stringify(report));
  const reportPath=path.join(outDir,'gate-report.json');
  fs.writeFileSync(reportPath,JSON.stringify(report,null,2)+'\n',{mode:0o600,flag:'wx'});
  report.path=reportPath;
 }
 return report;
}

if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href){
 try{
  const result=runM4Gate(parseGateArgs(process.argv.slice(2)));
  process.stdout.write(JSON.stringify({head:result.head,report:result.path,
   summary:result.summary,errors:result.errors,complete:result.complete},null,2)+'\n');
  if(!result.complete)process.exitCode=1;
 }catch(e){
  console.error('M4 local gate:',e.message);
  process.exitCode=1;
 }
}
