/**
 * M3: independent RED→GREEN verifier for a CCC1 patch receipt (Review #370, point 2).
 *
 * The Kotlin Ccc1PatchReceipt only proves that its own fields are consistent. This verifier binds the
 * claims to the REAL repository state and re-executes the tests itself:
 *  - commit mode: base/red/green are commit objects, base ⊑ red ⊑ green, the patch hash is the sha256 of
 *    `git diff --binary --full-index red green`, the RED commit touches test files and the fix (red→green)
 *    touches NO test file;
 *  - patch mode (CCC1 repair): the patch file hashes to the claimed sha256, applies to the base commit
 *    and touches no test file;
 *  - in both modes the fixed node test command runs in fresh detached worktrees (RED state and GREEN
 *    state); the verifier's OWN JUnit XML decides. Claimed failing tests, executed and failed counts must
 *    equal what was observed, otherwise the receipt is REJECTED as forged/stale.
 * Output is an attestation bound to sha256(receipt bytes). It is review evidence, never merge, signing or
 * install authority. No command, path or runner is taken from the receipt.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

export const ATTESTATION_SCHEMA='continuum-ccc1-redgreen-attestation';
export const RECEIPT_SCHEMA='continuum-ccc1-patch-receipt';
export const NODE_PROFILE='node --test --test-reporter=junit (fixed profile node-redgreen, detached worktree)';
const SHA1=/^[0-9a-f]{40}$/, SHA256=/^[0-9a-f]{64}$/;
const sha256=b=>crypto.createHash('sha256').update(b).digest('hex');
export const isTestFile=x=>/(^|\/)(test|tests|__tests__|src\/test)(\/|$)|(?:^|[-_.])(?:test|spec)\.[cm]?[jt]s$|(?:Test|Spec)\.kt$/i.test(x);

function git(repo,args,opts={}){
 const r=spawnSync('git',['-C',repo,...args],{encoding:opts.binary?'buffer':'utf8',maxBuffer:64<<20,timeout:60000});
 return {code:r.status,out:r.stdout,err:String(r.stderr??'')};
}
const isCommit=(repo,sha)=>SHA1.test(sha)&&git(repo,['cat-file','-t',sha]).out?.trim()==='commit';
const ancestor=(repo,a,b)=>git(repo,['merge-base','--is-ancestor',a,b]).code===0;
const changed=(repo,a,b)=>git(repo,['diff','--name-only',a,b]).out.split('\n').filter(Boolean);

/** Parses node/JUnit XML into {id -> passed}; id = <file stem>.<testcase name> and <classname>.<name>. */
export function parseJunit(xml){
 const cases=new Map();
 const re=/<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
 const attr=(s,k)=>(s.match(new RegExp('\\b'+k+'="([^"]*)"'))||[])[1];
 let m;
 while((m=re.exec(xml))){
  const a=m[1],name=attr(a,'name'),cls=attr(a,'classname'),file=attr(a,'file');
  if(!name)continue;
  const body=m[3]??'';
  const failed=/<(failure|error)\b/.test(body)||/\bfailure="/.test(a);
  const skipped=/<skipped\b/.test(body);
  const stem=file?path.basename(file).replace(/\.test\.[cm]?js$/,'').replace(/\.[cm]?js$/,''):cls;
  cases.set(`${stem}.${name}`,{failed,skipped,alias:cls?`${cls}.${name}`:null});
 }
 return cases;
}

/** Runs the fixed node test profile in a fresh detached worktree at `commit`, optionally after applying a patch. */
export function runNodeProfile(repo,commit,{patchPath=null,timeoutMs=120000}={}){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-rg-'));
 const wt=path.join(dir,'wt');
 try{
  if(git(repo,['worktree','add','--detach',wt,commit]).code!==0)return {ok:false,reason:'WORKTREE'};
  if(patchPath){
   const ap=spawnSync('git',['-C',wt,'apply','--whitespace=nowarn',patchPath],{encoding:'utf8'});
   if(ap.status!==0)return {ok:false,reason:'PATCH_DOES_NOT_APPLY'};
  }
  const files=[];
  const walk=d=>{for(const e of fs.readdirSync(d,{withFileTypes:true})){
   if(e.name==='.git'||e.name==='node_modules')continue;
   const p=path.join(d,e.name);
   if(e.isDirectory())walk(p);else if(/\.test\.mjs$/.test(e.name))files.push(path.relative(wt,p));
  }};
  walk(wt);
  if(!files.length)return {ok:false,reason:'NO_TESTS'};
  const out=path.join(dir,'junit.xml');
  const r=spawnSync(process.execPath,['--test','--test-reporter=junit','--test-reporter-destination='+out,...files.sort()],
   {cwd:wt,encoding:'utf8',timeout:timeoutMs,env:{PATH:process.env.PATH,HOME:dir}});
  if(r.error||r.signal)return {ok:false,reason:'TIMEOUT_OR_SIGNAL'};
  const xml=fs.existsSync(out)?fs.readFileSync(out):Buffer.alloc(0);
  const cases=parseJunit(xml.toString('utf8'));
  return {ok:true,exitCode:r.status,xmlSha256:sha256(xml),cases,
   executed:[...cases.values()].filter(c=>!c.skipped).length,failed:[...cases.values()].filter(c=>c.failed).length};
 }finally{
  git(repo,['worktree','remove','--force',wt]);
  fs.rmSync(dir,{recursive:true,force:true});
 }
}

const lookup=(cases,id)=>cases.get(id)??[...cases.values()].find(c=>c.alias===id);
const sameSet=(a,b)=>a.length===b.length&&[...a].sort().every((x,i)=>x===[...b].sort()[i]);

function compareRuns(receipt,red,green,reasons){
 const targets=receipt.targetTests;
 if(!red.ok)reasons.push('RED_RUN_'+red.reason);
 if(!green.ok)reasons.push('GREEN_RUN_'+green.reason);
 if(!red.ok||!green.ok)return;
 const redFailing=[...red.cases].filter(([,c])=>c.failed).map(([id])=>id);
 if(red.exitCode===0||redFailing.length===0)reasons.push('RED_DID_NOT_FAIL');
 if(redFailing.some(id=>!targets.includes(id)&&!targets.includes(red.cases.get(id)?.alias)))reasons.push('RED_FAILS_OUTSIDE_TARGETS');
 if(targets.some(t=>!lookup(red.cases,t)))reasons.push('TARGET_MISSING_IN_RED');
 for(const t of targets){const c=lookup(green.cases,t);if(!c||c.failed||c.skipped)reasons.push('TARGET_NOT_PASSING_IN_GREEN');}
 if(green.exitCode!==0||green.failed!==0)reasons.push('GREEN_HAS_FAILURES');
 const r=receipt.red,g=receipt.green;
 if(!r||!g){reasons.push('CLAIMED_RUN_MISSING');return;}
 if(!sameSet(r.failingTests,redFailing))reasons.push('CLAIMED_RED_FAILURES_DIFFER');
 if(r.executed!==red.executed||r.failed!==red.failed)reasons.push('CLAIMED_RED_COUNTS_DIFFER');
 if(g.executed!==green.executed||g.failed!==green.failed||g.failingTests.length)reasons.push('CLAIMED_GREEN_COUNTS_DIFFER');
 if(!targets.every(t=>g.passedTargets.includes(t)))reasons.push('CLAIMED_GREEN_TARGETS_DIFFER');
}

/** RED stub (M2 behaviour): trusts the receipt's own claims, no git or re-run. */
export function verifyCommitReceipt({repo,receiptBytes}){
 let r=null;try{r=JSON.parse(Buffer.from(receiptBytes).toString('utf8'));}catch{}
 if(!r||r.schema!==RECEIPT_SCHEMA)return attestation(receiptBytes,'REJECTED',['RECEIPT_SCHEMA']);
 return attestation(receiptBytes,'VERIFIED',[]);
}
export function verifyRepairPatch({receiptBytes}){return attestation(receiptBytes,'VERIFIED',[]);}

function attestation(receiptBytes,verdict,reasons,red=null,green=null,patchSha256=null){
 const slim=r=>r&&r.ok?{xmlSha256:r.xmlSha256,exitCode:r.exitCode,executed:r.executed,failed:r.failed}:null;
 return {schema:ATTESTATION_SCHEMA,version:1,receiptSha256:sha256(Buffer.from(receiptBytes??'')),verdict,
  reasons:[...new Set(reasons)],command:NODE_PROFILE,patchSha256,red:slim(red),green:slim(green)};
}

// CLI: node ccc1-redgreen-verifier.mjs --repo <dir> --receipt <file> [--base <sha> --patch <file> --patch-sha <hex>]
if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1])){
 const arg=k=>{const i=process.argv.indexOf(k);return i>0?process.argv[i+1]:null;};
 const repo=path.resolve(arg('--repo')??'.'),receiptBytes=fs.readFileSync(arg('--receipt'));
 const out=arg('--patch')?verifyRepairPatch({repo,baseCommit:arg('--base'),patchPath:path.resolve(arg('--patch')),patchSha256:arg('--patch-sha'),receiptBytes})
  :verifyCommitReceipt({repo,receiptBytes});
 process.stdout.write(JSON.stringify(out)+'\n');
 process.exitCode=out.verdict==='VERIFIED'?0:1;
}
