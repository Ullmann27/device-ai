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
import {createWorktree,removeWorktree,runChain,validateRepairExecutor} from './repair-loop.mjs';
import {validateCodingTask,taskFingerprint} from './coding-task.mjs';
import {isLocalNumericPatch} from './local-numeric-patch.mjs';

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

/** Parse the fixed Node reporter, retaining duplicate cases, suite ancestry and repository-relative paths.
 * Short ids remain receipt aliases; they are not sufficient coverage identities on their own.
 */
export function parseJunit(xml,{root=null}={}){
 const cases=new Map(),suites=[],occurrences=new Map();
 const re=/<testsuite\b([^>]*?)>|<\/testsuite\s*>|<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
 const decode=s=>s?.replace(/&(?:amp|lt|gt|quot|apos);|&#(?:x[0-9a-f]+|[0-9]+);/gi,x=>{
  const known={'&amp;':'&','&lt;':'<','&gt;':'>','&quot;':'"','&apos;':"'"};
  if(known[x])return known[x];const n=x.startsWith('&#x')?parseInt(x.slice(3,-1),16):Number(x.slice(2,-1));
  return Number.isInteger(n)&&n>0&&n<=0x10ffff?String.fromCodePoint(n):x;
 });
 const attr=(s,k)=>decode((s.match(new RegExp('\\b'+k+'="([^"]*)"'))||[])[1]);
 let m;
 while((m=re.exec(xml))){
  if(m[0].startsWith('</testsuite')){suites.pop();continue;}
  if(m[1]!==undefined){if(!/\/\s*$/.test(m[1]))suites.push(attr(m[1],'name')??'');continue;}
  const a=m[2],name=attr(a,'name'),cls=attr(a,'classname'),file=attr(a,'file');
  if(!name)continue;
  const body=m[4]??'';
  const failed=/<(failure|error)\b/.test(body)||/\bfailure="/.test(a);
  const skipped=/<skipped\b/.test(body);
  const skipType=skipped?(attr(body.match(/<skipped\b([^>]*)>/)?.[1]??'','type')??'skipped'):null;
  const stem=file?path.basename(file).replace(/\.test\.[cm]?js$/,'').replace(/\.[cm]?js$/,''):cls;
  const source=file&&root?path.relative(fs.realpathSync(root),file).split(path.sep).join('/'):file??cls??'';
  const tuple=JSON.stringify([source,suites,name]),occurrence=(occurrences.get(tuple)??0)+1;
  occurrences.set(tuple,occurrence);
  const legacyId=`${stem}.${name}`;let id=legacyId;
  // Do not collapse parameterized or repeated names into one reported test.
  while(cases.has(id))id+='\0';
  cases.set(id,{failed,skipped,skipType,legacyId,identity:JSON.stringify([source,suites,name,occurrence]),alias:cls?`${cls}.${name}`:null});
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
  const cases=parseJunit(xml.toString('utf8'),{root:wt});
  return {ok:true,exitCode:r.status,xmlSha256:sha256(xml),cases,
   executed:[...cases.values()].filter(c=>!c.skipped).length,failed:[...cases.values()].filter(c=>c.failed).length};
 }finally{
  git(repo,['worktree','remove','--force',wt]);
  fs.rmSync(dir,{recursive:true,force:true});
 }
}

const matching=(cases,id)=>[...cases].filter(([key,c])=>key===id||c.legacyId===id||c.alias===id).map(([,c])=>c);
const lookup=(cases,id)=>{const matches=matching(cases,id);return matches.length===1?matches[0]:null;};
const sameSet=(a,b)=>a.length===b.length&&[...a].sort().every((x,i)=>x===[...b].sort()[i]);
const validTargets=targets=>Array.isArray(targets)&&targets.length>0&&targets.every(t=>typeof t==='string'&&t.length>0)&&new Set(targets).size===targets.length;
function envelopeReasons(receipt){
 if(!receipt||receipt.schema!==RECEIPT_SCHEMA||receipt.version!==1)return ['RECEIPT_SCHEMA'];
 return ['approvedForMerge','installed','deviceVerified'].some(k=>Object.hasOwn(receipt,k)&&receipt[k]!==false)?['FORBIDDEN_CLAIM']:[];
}

/** Legacy direct callers may omit ancillary metadata; supplied claims must match our own run. */
function compareClaimedRun(claim,observed,targets,side,reasons){
 const expected={exitCode:observed.exitCode,skipped:[...observed.cases.values()].filter(c=>c.skipped).length,
  timedOut:false,cancelled:false};
 for(const [field,value] of Object.entries(expected)){
  if(Object.hasOwn(claim,field)&&claim[field]!==value)reasons.push(`CLAIMED_${side}_${field.toUpperCase()}_DIFFERS`);
 }
 if(Object.hasOwn(claim,'command')&&!['node-redgreen',NODE_PROFILE].includes(claim.command))
  reasons.push(`CLAIMED_${side}_COMMAND_DIFFERS`);
 if(Object.hasOwn(claim,'passedTargets')){
  const expectedIds=targets.map(t=>lookup(observed.cases,t)).filter(c=>c&&!c.failed&&!c.skipped).map(c=>c.identity);
  const claims=Array.isArray(claim.passedTargets)?claim.passedTargets.map(t=>lookup(observed.cases,t)):[];
  const ids=claims.map(c=>c?.identity);
  if(!Array.isArray(claim.passedTargets)||claims.some(c=>!c||c.failed||c.skipped)||
     new Set(ids).size!==ids.length||!sameSet(ids,expectedIds))reasons.push(`CLAIMED_${side}_TARGETS_DIFFER`);
 }
}

function compareCoverage(red,green,reasons){
 const candidate=[...green.cases.values()];let cursor=0;
 for(const baseline of red.cases.values()){
  const identity=baseline.identity;
  const index=candidate.findIndex(c=>c.identity===identity);
  if(!identity||index<0){reasons.push('BASELINE_TEST_IDENTITY_MISSING');continue;}
  const observed=candidate[index];
  if(baseline.skipped!==observed.skipped||baseline.skipType!==observed.skipType)
   reasons.push('BASELINE_TEST_EXECUTION_CHANGED');
  if(index<cursor)reasons.push('BASELINE_TEST_ORDER_CHANGED');
  cursor=index+1;
 }
}

function compareRuns(receipt,red,green,reasons){
 const targets=receipt.targetTests;
 if(!red.ok)reasons.push('RED_RUN_'+red.reason);
 if(!green.ok)reasons.push('GREEN_RUN_'+green.reason);
 if(!red.ok||!green.ok)return;
 compareCoverage(red,green,reasons);
 const redFailing=[...red.cases].filter(([,c])=>c.failed).map(([id])=>id);
 if(red.exitCode===0||redFailing.length===0)reasons.push('RED_DID_NOT_FAIL');
 if(redFailing.some(id=>!targets.includes(id)&&!targets.includes(red.cases.get(id)?.alias)))reasons.push('RED_FAILS_OUTSIDE_TARGETS');
 if(targets.some(t=>!lookup(red.cases,t)))reasons.push('TARGET_MISSING_IN_RED');
 if(targets.some(t=>matching(red.cases,t).length>1||matching(green.cases,t).length>1))reasons.push('TARGET_IDENTITY_AMBIGUOUS');
 for(const observed of [red,green]){
  const ids=targets.map(t=>lookup(observed.cases,t)?.identity).filter(Boolean);
  if(new Set(ids).size!==ids.length)reasons.push('TARGET_IDENTITY_DUPLICATE');
 }
 for(const t of targets){const c=lookup(green.cases,t);if(!c||c.failed||c.skipped)reasons.push('TARGET_NOT_PASSING_IN_GREEN');}
 if(green.exitCode!==0||green.failed!==0)reasons.push('GREEN_HAS_FAILURES');
 const r=receipt.red,g=receipt.green;
 if(!r||!g||!Array.isArray(r.failingTests)||!Array.isArray(g.failingTests)||!Array.isArray(g.passedTargets)){
  reasons.push('CLAIMED_RUN_MISSING');return;
 }
 if(!sameSet(r.failingTests,redFailing))reasons.push('CLAIMED_RED_FAILURES_DIFFER');
 if(r.executed!==red.executed||r.failed!==red.failed)reasons.push('CLAIMED_RED_COUNTS_DIFFER');
 if(g.executed!==green.executed||g.failed!==green.failed||g.failingTests.length)reasons.push('CLAIMED_GREEN_COUNTS_DIFFER');
 compareClaimedRun(r,red,targets,'RED',reasons);
 compareClaimedRun(g,green,targets,'GREEN',reasons);
}

/** Commit mode: receipt fields baseSha/redSha/greenSha/patchSha256/targetTests/red/green (Kotlin canonical JSON). */
export function verifyCommitReceipt({repo,receiptBytes,run=runNodeProfile}){
 const reasons=[];
 let receipt;
 try{receipt=JSON.parse(Buffer.from(receiptBytes).toString('utf8'));}catch{receipt=null;}
 const envelope=envelopeReasons(receipt);
 if(envelope.length)return attestation(receiptBytes,'REJECTED',envelope);
 const {baseSha,redSha,greenSha,patchSha256,targetTests}=receipt;
 if(!validTargets(targetTests))reasons.push('TARGET_TESTS');
 if(![baseSha,redSha,greenSha].every(s=>isCommit(repo,s)))reasons.push('COMMIT_NOT_IN_REPOSITORY');
 if(new Set([baseSha,redSha,greenSha]).size!==3)reasons.push('COMMITS_NOT_DISTINCT');
 if(reasons.length)return attestation(receiptBytes,'REJECTED',reasons);
 if(!ancestor(repo,baseSha,redSha)||!ancestor(repo,redSha,greenSha))reasons.push('NOT_A_LINEAR_BASE_RED_GREEN_CHAIN');
 const diff=git(repo,['diff','--binary','--full-index',redSha,greenSha],{binary:true});
 if(diff.code!==0||!SHA256.test(patchSha256)||sha256(diff.out)!==patchSha256)reasons.push('PATCH_HASH_MISMATCH');
 if(!changed(repo,baseSha,redSha).some(isTestFile))reasons.push('RED_COMMIT_ADDS_NO_TEST');
 if(changed(repo,redSha,greenSha).some(isTestFile))reasons.push('FIX_MODIFIES_TESTS');
 if(receipt.red?.sha!==redSha||receipt.green?.sha!==greenSha)reasons.push('RUN_SHA_MISMATCH');
 if(reasons.length)return attestation(receiptBytes,'REJECTED',reasons);
 const red=run(repo,redSha),green=run(repo,greenSha);
 compareRuns(receipt,red,green,reasons);
 return attestation(receiptBytes,reasons.length?'REJECTED':'VERIFIED',reasons,red,green,patchSha256);
}

/** Patch mode for CCC1 repair receipts: RED = base commit, GREEN = base + patch file. */
export function verifyRepairPatch({repo,baseCommit,patchPath,patchSha256,receiptBytes,run=runNodeProfile}){
 const reasons=[];
 let receipt=null;
 try{receipt=JSON.parse(Buffer.from(receiptBytes).toString('utf8'));}catch{}
 const envelope=envelopeReasons(receipt);
 if(envelope.length)return attestation(receiptBytes,'REJECTED',envelope);
 if(!receipt||!validTargets(receipt.targetTests))reasons.push('TARGET_TESTS');
 if(!isCommit(repo,baseCommit))reasons.push('COMMIT_NOT_IN_REPOSITORY');
 // Optional legacy fields remain optional; supplied source/hash claims must agree.
 if(Object.hasOwn(receipt,'patchSha256')&&receipt.patchSha256!==patchSha256)reasons.push('RECEIPT_PATCH_HASH_MISMATCH');
 if(Object.hasOwn(receipt,'redSha')&&receipt.redSha!==baseCommit||
    Object.hasOwn(receipt.red??{},'sha')&&receipt.red.sha!==baseCommit||
    Object.hasOwn(receipt.green??{},'sha')&&receipt.green.sha!==receipt.greenSha)reasons.push('RUN_SHA_MISMATCH');
 let bytes=null;try{bytes=fs.readFileSync(patchPath);}catch{reasons.push('PATCH_MISSING');}
 if(bytes&&(!SHA256.test(patchSha256)||sha256(bytes)!==patchSha256))reasons.push('PATCH_HASH_MISMATCH');
 if(bytes){
  const ns=spawnSync('git',['apply','--numstat',patchPath],{encoding:'utf8'});
  const files=ns.status===0?ns.stdout.split('\n').filter(Boolean).map(l=>l.split('\t')[2]):null;
  if(!files||!files.length)reasons.push('PATCH_UNREADABLE');else if(files.some(isTestFile))reasons.push('FIX_MODIFIES_TESTS');
 }
 if(reasons.length)return attestation(receiptBytes,'REJECTED',reasons);
 const red=run(repo,baseCommit),green=run(repo,baseCommit,{patchPath});
 compareRuns(receipt,red,green,reasons);
 return attestation(receiptBytes,reasons.length?'REJECTED':'VERIFIED',reasons,red,green,patchSha256);
}


const CODING_VERIFICATIONS=new WeakSet();
export const isTrustedCodingVerification=value=>CODING_VERIFICATIONS.has(value);
function freezeEvidence(value){
 if(value&&typeof value==='object'){for(const child of Object.values(value))freezeEvidence(child);Object.freeze(value);}
 return value;
}
/** Execute the canonical fixed chain twice from immutable source, never a worker boolean.
 * Both checkouts are fresh; candidate build state and reports are never reused.
 * Legacy reviewed numeric repairs retain host compatibility; general execution
 * requires the existing branded OS executor. Missing isolation never falls back.
 */
export function verifyCodingCandidate({repo,task,patchPath,patchSha256,proposalSha256,out,deadline,
 executor=null,executorAbortMode=null,checkpoint=null,abortWatch=null}={}){
 const spec=validateCodingTask(task);
 validateRepairExecutor(executor,{abortWatch,executorAbortMode});
 if(!SHA256.test(proposalSha256??'')||!SHA256.test(patchSha256??'')||!Number.isSafeInteger(deadline)||Date.now()>=deadline)
  throw Error('CODING_VERIFICATION_REQUEST_INVALID');
 if(!isCommit(repo,spec.sourceCommit))throw Error('CODING_VERIFICATION_SOURCE_INVALID');
 const patch=fs.readFileSync(patchPath);
 if(sha256(patch)!==patchSha256)throw Error('CODING_VERIFICATION_PATCH_HASH');
 let redTree=null,greenTree=null;
 try{
  greenTree=createWorktree(repo,spec.sourceCommit);
  if(git(greenTree,['apply','--check','--binary',patchPath]).code!==0||
     git(greenTree,['apply','--binary',patchPath]).code!==0)throw Error('CODING_VERIFICATION_PATCH_INVALID');
  const files=git(greenTree,['diff','--name-only',spec.sourceCommit]).out.trim().split('\n').filter(Boolean);
  if(!files.length||files.some(f=>!spec.allowedPaths.includes(f)||isTestFile(f)))throw Error('CODING_VERIFICATION_SCOPE');
  if(!executor&&!files.every(file=>isLocalNumericPatch({file,
    oldText:git(repo,['cat-file','blob',spec.sourceCommit+':'+file]).out,
    newText:fs.readFileSync(path.join(greenTree,file),'utf8')})))throw Error('CODING_ISOLATION_REQUIRED');
  redTree=createWorktree(repo,spec.sourceCommit);
  const redOut=path.join(out,'red'),greenOut=path.join(out,'green');
  fs.mkdirSync(redOut,{recursive:true,mode:0o700});fs.mkdirSync(greenOut,{recursive:true,mode:0o700});
  const options={deadline,executor,executorAbortMode,checkpoint,abortWatch};
  const red=runChain(spec.chain,redTree,redOut,options),green=runChain(spec.chain,greenTree,greenOut,options),reasons=[];
  const redProfiles=red.results.filter(r=>r.testProfile);
  if(red.passed||!redProfiles.some(r=>r.exitCode!==null&&r.exitCode!==0&&r.tests?.failed>0&&r.tests?.executed>0)||
     red.results.some(r=>r.error||r.signal||r.timedOut))reasons.push('RED_DID_NOT_FAIL_COHERENTLY');
  if(!green.passed)reasons.push('GREEN_HAS_FAILURES');
  for(const before of redProfiles){
    const after=green.results.find(r=>r.profile===before.profile);
    if(!after){reasons.push('BASELINE_PROFILE_MISSING');continue;}
    if(before.tests?.evidenceComplete===false||after.tests?.evidenceComplete===false)reasons.push('INCOMPLETE_TEST_EVIDENCE');
    const a=before.tests?.caseIdentities,b=after.tests?.caseIdentities;
    if(!Array.isArray(a)||!a.length||!Array.isArray(b)||!b.length){reasons.push('TEST_IDENTITIES_MISSING');continue;}
    compareCoverage({cases:new Map(a.map((c,i)=>[i,c]))},{cases:new Map(b.map((c,i)=>[i,c]))},reasons);
  }
  if(!green.results.some(r=>r.testProfile&&r.tests?.executed>0))reasons.push('GREEN_NO_EXECUTED_TESTS');
  const result=freezeEvidence({schema:'momega1-coding-verification',version:1,
    taskFingerprint:taskFingerprint(spec),sourceCommit:spec.sourceCommit,chain:spec.chain,patchSha256,proposalSha256,
    verdict:reasons.length?'REJECTED':'VERIFIED',reasons:[...new Set(reasons)],red,green,
    diagnostics:[...red.diagnostics,...green.diagnostics],evidenceClass:'HOST_INTEGRATION',
    execution:executor?'OS_ISOLATED':'HOST_REVIEWED_NUMERIC'});
  CODING_VERIFICATIONS.add(result);return result;
 }finally{
  if(redTree)removeWorktree(repo,redTree);
  if(greenTree)removeWorktree(repo,greenTree);
 }
}

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
