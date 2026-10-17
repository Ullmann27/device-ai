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
 const requiredNodeProfiles=['node-continuum','node-backend'];
 const backendCovered=requiredNodeProfiles.every(name=>
   node?.results?.some(r=>r.profile===name&&r.passed===true&&
     r.testProfile===true&&r.tests?.executed>0));
 const nodeOK=valid(node)&&(!runAndroid||backendCovered);
 const androidOK=runAndroid&&valid(android);
 const apkOK=runAndroid&&apk?.exitCode===0&&apk?.timedOut===false&&
   /^[0-9a-f]{64}$/.test(apk?.artifactSha256??'')&&
   Number.isSafeInteger(apk?.bytes)&&apk.bytes>0;
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

/**
 * Verify a persisted local quality report without rerunning code from it.
 * A clean NODE_ONLY proof is useful during development but is NEVER a
 * release-quality baseline: releaseReady always requires JVM tests and APK.
 *
 * SHA-256 is an accidental-corruption check, NOT a signature or independent
 * guarantee that the producer really executed the reported tests.
 */
export function verifyGateReport(record,{expectedHead=null}={}){
 const reject=reason=>({valid:false,releaseReady:false,reason});
 if(!record||typeof record!=='object'||Array.isArray(record))
  return reject('INVALID_REPORT');
 if(record.schema!=='continuum-m4-local-gate'||record.version!==1||
   typeof record.head!=='string'||!SHA.test(record.head)||
   (expectedHead!==null&&record.head!==expectedHead)||
   !Array.isArray(record.errors)||!record.errors.every(x=>typeof x==='string')||
   typeof record.complete!=='boolean'||!record.summary||
   !/^[a-f0-9]{64}$/.test(record.reportSha256??''))
  return reject('INVALID_REPORT');
 const {reportSha256,path:ignoredRuntimePath,...signed}=record;
 if(hash(JSON.stringify(signed))!==reportSha256)return reject('REPORT_HASH_MISMATCH');
 const summary=gateSummary(record.node,record.android,record.apk,
   record.summary.scope==='NODE_KOTLIN_APK');
 if(JSON.stringify(summary)!==JSON.stringify(record.summary)||
   record.complete!==(summary.passed&&record.errors.length===0))
  return reject('INCONSISTENT_PROOF');
 if(!summary.passed||!record.complete)return {valid:true,releaseReady:false,reason:'CHECKS_NOT_GREEN'};
 if(summary.scope!=='NODE_KOTLIN_APK')
  return {valid:true,releaseReady:false,reason:'ANDROID_NOT_TESTED'};
 return {valid:true,releaseReady:true,reason:null};
}

/**
 * Independently checks that a claimed release APK actually exists in the
 * proof directory with the same bytes and SHA-256. No subprocess, network or
 * code from the proof is executed. Symlinks and external paths are rejected.
 */
export function verifyReleaseProofFile(reportFile,{expectedHead=null}={}){
 const reject=reason=>({valid:false,releaseReady:false,reason});
 let absolute,record;
 try{
  absolute=path.resolve(reportFile);
  const stat=fs.lstatSync(absolute);
  if(!stat.isFile()||stat.isSymbolicLink()||stat.size<20||stat.size>8*1024*1024)
   return reject('INVALID_REPORT_FILE');
  record=JSON.parse(fs.readFileSync(absolute,'utf8'));
 }catch{return reject('INVALID_REPORT_FILE')}
 const verified=verifyGateReport(record,{expectedHead});
 if(!verified.releaseReady)return verified;
 const artifact=record.apk?.artifact;
 if(typeof artifact!=='string'||path.resolve(artifact)!==
   path.join(path.dirname(absolute),'app-debug.apk'))
  return reject('UNSAFE_APK_PATH');
 let fd;
 try{
  const info=fs.lstatSync(artifact);
  if(!info.isFile()||info.isSymbolicLink()||
    info.size!==record.apk.bytes||info.size>250*1024*1024)
   return reject('APK_MISSING_OR_CHANGED');
  const digest=crypto.createHash('sha256');
  const buffer=Buffer.allocUnsafe(64*1024);
  fd=fs.openSync(artifact,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
  let count=0,n;
  while((n=fs.readSync(fd,buffer,0,buffer.length,null))>0){
   digest.update(buffer.subarray(0,n));count+=n;
   if(count>250*1024*1024)return reject('APK_TOO_LARGE');
  }
  if(count!==info.size||digest.digest('hex')!==record.apk.artifactSha256)
   return reject('APK_HASH_MISMATCH');
  return verified;
 }catch{return reject('APK_UNREADABLE')}
 finally{if(fd!==undefined)fs.closeSync(fd);}
}

export function parseGateArgs(args){
 const opts={android:false,budgetMinutes:60};
 const seen=new Set();
 for(let i=0;i<args.length;i++){
  const flag=args[i];
  if(!['--root','--out','--budget-minutes','--android','--verify'].includes(flag)||seen.has(flag))
   throw Error('Unknown or duplicate gate option '+flag);
  seen.add(flag);
  if(flag==='--android'){opts.android=true;continue;}
  const value=args[++i];
  if(!value||value.startsWith('--'))throw Error('Missing value for '+flag);
  if(flag==='--root')opts.root=value;
  else if(flag==='--out')opts.out=value;
  else if(flag==='--verify')opts.verify=value;
  else opts.budgetMinutes=Number(value);
 }
 if(opts.verify!==undefined){
  if(seen.size!==1)throw Error('--verify cannot be combined with build options');
  return {verify:opts.verify};
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
  const nodeResult=safeStage(()=>runChain(android?'stability':'node',wt,nodeDir,{deadline}));
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
    if(gradle.status===0&&!report.apk.timedOut){
     const built=path.join(wt,'app','build','outputs','apk','debug','app-debug.apk');
     if(fs.existsSync(built)&&fs.statSync(built).isFile()&&fs.statSync(built).size>0){
      const destination=path.join(outDir,'app-debug.apk');
      fs.copyFileSync(built,destination,fs.constants.COPYFILE_EXCL);
      const bytes=fs.readFileSync(destination);
      report.apk.artifactSha256=hash(bytes);
      report.apk.bytes=bytes.length;
      report.apk.artifact=destination;
     }else report.errors.push('APK: Gradle reported success without an app-debug.apk artifact');
    }
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
  const options=parseGateArgs(process.argv.slice(2));
  if(options.verify!==undefined){
   const head=git(process.cwd(),['rev-parse','HEAD']).stdout.trim();
   if(!SHA.test(head))throw Error('Cannot verify proof without a Git HEAD');
   const result=verifyReleaseProofFile(options.verify,{expectedHead:head});
   process.stdout.write(JSON.stringify({head,proof:options.verify,...result},null,2)+'\n');
   if(!result.releaseReady)process.exitCode=1;
  }else{
   const result=runM4Gate(options);
   process.stdout.write(JSON.stringify({head:result.head,report:result.path,
    summary:result.summary,errors:result.errors,complete:result.complete},null,2)+'\n');
   if(!result.complete)process.exitCode=1;
  }
 }catch(e){
  console.error('M4 local gate:',e.message);
  process.exitCode=1;
 }
}
