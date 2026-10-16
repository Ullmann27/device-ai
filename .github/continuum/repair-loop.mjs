import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {parseKotlinDiagnostics,readJUnitDirectory,parseJUnit,parseJsErrorText,sha256} from './repair-diagnostics.mjs';
import {proposeCandidates,applyEdits} from './repair-strategies.mjs';
import {critique} from './repair-critic.mjs';
import {buildIndex,collectKotlin} from './code-analyzer.mjs';

/**
 * Test verifier and orchestrator of one repair session:
 *   reproduce (own run, never log text) -> diagnose -> propose -> critique ->
 *   apply in an isolated git worktree -> run the fixed check chain -> keep the
 *   first fully passing candidate as a patch, reset the worktree otherwise.
 * The original checkout is never modified. Commands come only from PROFILES.
 */
export const BRANCH_PREFIX='continuum-agent/';

const DEPTH=Number(process.env.CONTINUUM_REPAIR_DEPTH??0);
function run(cmd,args,{cwd,timeoutMs=120000,env}={}){
  const started=Date.now();
  // Child checks see the depth, so a test suite cannot start a nested repair loop.
  const childEnv={...process.env,...env,GIT_TERMINAL_PROMPT:'0',CONTINUUM_REPAIR_DEPTH:String(DEPTH+1)};
  delete childEnv.NODE_TEST_CONTEXT; // a nested `node --test` must report on its own, not to a parent runner
  const r=spawnSync(cmd,args,{cwd,timeout:timeoutMs,encoding:'utf8',maxBuffer:64*1024*1024,shell:false,env:childEnv});
  return {status:r.status,signal:r.signal,error:r.error?.code??null,stdout:r.stdout??'',stderr:r.stderr??'',ms:Date.now()-started};
}
export function git(cwd,args,opts={}){
  const r=run('git',args,{cwd,timeoutMs:opts.timeoutMs??120000});
  if(r.status!==0&&!opts.allowFailure)throw Error('git '+args[0]+' failed: '+(r.stderr||r.stdout).slice(-400));
  return r;
}

function nodeTests(wt){
  const dir=path.join(wt,'.github/continuum');
  return fs.existsSync(dir)?fs.readdirSync(dir).filter(f=>f.endsWith('.test.mjs')).sort().map(f=>'.github/continuum/'+f):[];
}

/** Fixed, reviewed check commands. Nothing here is derived from logs or inputs. */
export const PROFILES=Object.freeze({
  'node-continuum':(wt,out)=>({cmd:process.execPath,args:['--test','--test-reporter=junit','--test-reporter-destination='+path.join(out,'node-junit.xml'),...nodeTests(wt)],
    cwd:wt,junitFiles:[path.join(out,'node-junit.xml')],timeoutMs:300000}),
  // Kotlin incremental compilation is disabled: after a failed build its caches
  // produced phantom "Conflicting overloads" errors in untouched files.
  'android-compile':wt=>({cmd:'gradle',args:[':app:compileDebugUnitTestKotlin','--console=plain','-Pkotlin.incremental=false'],cwd:wt,kotlin:true,timeoutMs:1200000}),
  'android-unit':wt=>({cmd:'gradle',args:[':app:testDebugUnitTest','--console=plain','-Pkotlin.incremental=false'],cwd:wt,kotlin:true,
    junitDirs:[path.join(wt,'app/build/test-results/testDebugUnitTest')],cleanDirs:[path.join(wt,'app/build/test-results/testDebugUnitTest')],timeoutMs:1500000}),
  'jvm-harness':wt=>{
    // Local development only: JVM harness that compiles app sources without the Android SDK.
    const harness=process.env.CONTINUUM_JVM_HARNESS;
    if(!harness||!fs.existsSync(path.join(harness,'build.gradle.kts')))throw Error('CONTINUUM_JVM_HARNESS not configured');
    return {cmd:'gradle',args:['-p',harness,'test','--console=plain','-q','-Pkotlin.incremental=false'],cwd:wt,env:{DEVICE_AI_REPO:wt},kotlin:true,
      junitDirs:[path.join(harness,'build/test-results/test')],cleanDirs:[path.join(harness,'build/test-results/test')],timeoutMs:1500000};
  }
});
export const CHAINS=Object.freeze({node:['node-continuum'],android:['android-compile','android-unit'],jvm:['jvm-harness']});
/** Profiles whose evidence is executed tests (independent of flags in a report). */
export const TEST_PROFILES=Object.freeze(['node-continuum','android-unit','jvm-harness']);

export function runProfile(name,wt,out){
  const factory=PROFILES[name];if(!factory)throw Error('Unknown check profile '+name);
  const p=factory(wt,out);
  for(const d of p.cleanDirs??[])fs.rmSync(d,{recursive:true,force:true});
  for(const f of p.junitFiles??[])fs.rmSync(f,{force:true});
  const r=run(p.cmd,p.args,{cwd:p.cwd,timeoutMs:p.timeoutMs,env:p.env});
  const text=r.stdout+'\n'+r.stderr;
  const diagnostics=p.kotlin?parseKotlinDiagnostics(text,wt):[];
  const tests={total:0,failed:0,skipped:0,placeholders:0,executed:0,passed:0};
  const addTests=j=>{for(const k of Object.keys(tests))tests[k]+=j[k]??0;diagnostics.push(...j.diagnostics);};
  for(const dir of p.junitDirs??[])addTests(readJUnitDirectory(dir,wt));
  for(const file of p.junitFiles??[])if(fs.existsSync(file))addTests(parseJUnit(fs.readFileSync(file,'utf8'),wt));
  // Test files that crash while importing report only "test failed" in JUnit;
  // re-run just those (same fixed runner, TAP) to recover the real error location.
  // Only files that were arguments of the fixed command itself are re-run.
  if(p.junitFiles){
    const listed=new Set(p.args.filter(a=>/^\.github\/continuum\/[\w.-]+\.test\.mjs$/.test(a)));
    const crashed=[...new Set(diagnostics.filter(d=>!d.file&&listed.has(d.testFile)).map(d=>d.testFile))].slice(0,3);
    for(const file of crashed){
      const again=run(p.cmd,['--test','--test-reporter=tap',file],{cwd:p.cwd,timeoutMs:120000});
      diagnostics.unshift(...parseJsErrorText(again.stdout+'\n'+again.stderr,wt));
    }
  }
  // A test profile passes only with executed evidence: exit 0 with no real test
  // run (missing report, only file placeholders or skips) is not a pass.
  const testProfile=Boolean(p.junitFiles||p.junitDirs);
  const passed=r.status===0&&tests.failed===0&&!r.error&&!r.signal&&(!testProfile||tests.executed>0);
  return {profile:name,passed,testProfile,exitCode:r.status,timedOut:r.signal==='SIGTERM'||r.error==='ETIMEDOUT',ms:r.ms,tests,diagnostics,
    outputTail:text.slice(-3000)};
}

/**
 * A candidate's checks may not provide less evidence than the failing baseline
 * did: per test profile, executed tests must not drop below the baseline's
 * passing tests and skipped tests must not increase. Profiles the baseline never
 * reached (e.g. unit tests after a compile error) only require executed > 0.
 */
export function evidenceSufficient(baselineResults,candidateResults){
  const reasons=[];
  for(const r of candidateResults){
    if(!r.passed){reasons.push(r.profile+' did not pass');continue;}
    if(!r.testProfile)continue;
    const before=baselineResults.find(b=>b.profile===r.profile);
    if(r.tests.executed<=0)reasons.push(r.profile+' executed no tests');
    if(before?.tests){
      const passingBefore=Math.max(0,before.tests.passed??before.tests.executed-before.tests.failed);
      if(r.tests.executed<passingBefore)reasons.push(`${r.profile} executed ${r.tests.executed} tests, baseline had ${passingBefore} passing`);
      if(r.tests.skipped>before.tests.skipped)reasons.push(`${r.profile} skips ${r.tests.skipped-before.tests.skipped} more tests than the baseline`);
    }
  }
  return {sufficient:reasons.length===0,reasons};
}

export function runChain(chain,wt,out){
  const results=[];
  for(const name of CHAINS[chain]??(()=>{throw Error('Unknown chain '+chain);})()){
    const r=runProfile(name,wt,out);results.push(r);
    if(!r.passed)break;
  }
  return {passed:results.every(r=>r.passed),results,diagnostics:results.flatMap(r=>r.diagnostics)};
}

export function createWorktree(root,base){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-repair-wt-'));
  git(root,['worktree','add','--detach','--force',dir,base]);
  return dir;
}
export function resetWorktree(wt,base){
  git(wt,['reset','--hard','-q',base]);
  git(wt,['clean','-fdq','-e','build','-e','.gradle','-e','app/build']);
}
export function removeWorktree(root,wt){
  git(root,['worktree','remove','--force',wt],{allowFailure:true});
  fs.rmSync(wt,{recursive:true,force:true});
  git(root,['worktree','prune'],{allowFailure:true});
}

/** `@@ -a,b +c,d @@` headers of a --unified=0 diff (counts default to 1). */
export function parseUnifiedZeroHunks(diff){
  const hunks=[];
  for(const m of String(diff).matchAll(/^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@/gm)){
    hunks.push({oldStart:Number(m[1]),oldCount:m[2]==null?1:Number(m[2]),newStart:Number(m[3]),newCount:m[4]==null?1:Number(m[4])});
    if(hunks.length>=50)break;
  }
  return hunks;
}

/** Resolve edit targets only to ordinary, existing worktree files.
 * Neither an absolute path nor a symlinked parent may escape the sandbox.
 * The Git checkout can contain symlinks, so path.join alone is not enough.
 */
export function boundedWorktreeFile(wt,relative){
  if(typeof relative!=='string'||!relative||
    relative.includes('\0')||path.isAbsolute(relative)||
    relative.split(/[\\/]+/).some(x=>x==='..'||x==='.'||x===''))
    throw Error('Invalid worktree edit path');
  const root=fs.realpathSync(wt);
  const target=path.resolve(root,relative);
  if(!target.startsWith(root+path.sep))throw Error('Edit escaped worktree');
  const info=fs.lstatSync(target);
  if(!info.isFile()||info.isSymbolicLink())throw Error('Edit requires an ordinary file');
  if(fs.realpathSync(target)!==target)throw Error('Symlinked directory in worktree edit');
  return target;
}

function context(wt,base,experience){
  const readFile=rel=>{
    try {
      const f=boundedWorktreeFile(wt,rel);
      return fs.statSync(f).size<1_000_000?fs.readFileSync(f,'utf8'):null;
    }catch { return null; }
  };
  let index=null;
  try{if(fs.existsSync(path.join(wt,'app/src')))index=buildIndex(collectKotlin(path.join(wt,'app/src')));}catch{}
  const jsSiblings=new Map();
  const jsDir=path.join(wt,'.github/continuum');
  if(fs.existsSync(jsDir))for(const f of fs.readdirSync(jsDir).filter(f=>f.endsWith('.mjs')&&!f.endsWith('.test.mjs')))
    jsSiblings.set('.github/continuum/'+f,fs.readFileSync(path.join(jsDir,f),'utf8'));
  const changedInBase=[];
  const parent=git(wt,['rev-parse','--verify','-q',base+'^'],{allowFailure:true});
  if(parent.status===0){
    const names=git(wt,['diff','--name-only',base+'^',base]).stdout.split('\n').filter(Boolean).slice(0,20);
    for(const file of names){
      const shown=git(wt,['show',base+'^:'+file],{allowFailure:true});
      const diff=git(wt,['diff','--unified=0','--no-color','--no-ext-diff',base+'^',base,'--',file],{allowFailure:true});
      changedInBase.push({file,parent:shown.status===0?shown.stdout:null,hunks:parseUnifiedZeroHunks(diff.stdout)});
    }
  }
  return {readFile,index,jsSiblings,changedInBase,experience};
}

/** Order by experience (Laplace-smoothed per diagnostic kind and strategy) blended with the prior. */
export function rankCandidates(candidates,stats=[],failedIds=new Set()){
  const byKey=new Map(stats.map(s=>[s.kind,s]));
  return candidates.filter(c=>!failedIds.has(c.diagnostic+':'+c.id)).map(c=>{
    const s=byKey.get((c.diagnosticKind+'|'+c.strategy).slice(0,80));
    const learned=s?(s.successes+1)/(s.successes+s.failures+2):0.5;
    return {...c,score:Number((0.6*learned+0.4*(c.prior??0.5)).toFixed(4))};
  }).sort((a,b)=>b.score-a.score||a.id.localeCompare(b.id));
}

/**
 * One repair session on commit `base` in `wt` (an existing clean worktree).
 * Returns a report; never touches the caller's checkout.
 */
export function repairSession({wt,base,chain,out,ledger={attempts:[],experiences:[]},maxCandidates=4,deadline=Date.now()+25*60000,log=()=>{}}){
  if(DEPTH>0)throw Error('Nested repair sessions are not allowed');
  if(!path.isAbsolute(out))throw Error('Report directory must be absolute (checks run inside the worktree)');
  fs.mkdirSync(out,{recursive:true});
  resetWorktree(wt,base);
  const report={schemaVersion:1,base,chain,startedAt:new Date().toISOString(),baseline:null,attempts:[],verified:null,outcome:null};
  const baseline=runChain(chain,wt,out);
  report.baseline={passed:baseline.passed,results:baseline.results.map(({outputTail,diagnostics,...r})=>({...r,diagnostics:diagnostics.length})),
    diagnostics:baseline.diagnostics};
  log(`baseline ${baseline.passed?'PASS':'FAIL'} with ${baseline.diagnostics.length} diagnostics`);
  if(baseline.passed){report.outcome='NO_FAILURE';return report;}
  const actionable=baseline.diagnostics.filter(d=>d.file);
  if(!actionable.length){report.outcome='NO_CODE_DIAGNOSTIC';report.baselineTail=baseline.results.at(-1)?.outputTail;return report;}
  const ctx=context(wt,base,ledger.attempts);
  const failedIds=new Set(ledger.attempts.filter(a=>a.outcome!=='VERIFIED').map(a=>a.diagnostic+':'+a.candidate));
  const ranked=rankCandidates(proposeCandidates(actionable,ctx),ledger.experiences,failedIds);
  log(`${ranked.length} candidates: `+ranked.map(c=>c.strategy+'@'+c.score).join(', '));
  const perStrategy=new Map();
  for(const c of ranked){
    if(report.attempts.filter(a=>a.outcome!=='REJECTED_BY_CRITIC').length>=maxCandidates||Date.now()>deadline)break;
    // keep strategy diversity: at most two tries per strategy family in one session
    const family=c.strategy.split(':')[0];
    if((perStrategy.get(family)??0)>=2)continue;
    perStrategy.set(family,(perStrategy.get(family)??0)+1);
    const verdict=critique(c);
    const attempt={candidate:c.id,strategy:c.strategy,diagnostic:c.diagnostic,diagnosticKind:c.diagnosticKind,
      description:c.description,score:c.score,critic:verdict,edits:c.edits};
    if(!verdict.approved){attempt.outcome='REJECTED_BY_CRITIC';report.attempts.push(attempt);log('critic rejected '+c.strategy+': '+verdict.reasons.join('; '));continue;}
    resetWorktree(wt,base);
    try{
      const prepared=[...applyEdits(ctx.readFile,c.edits)].map(([file,content])=>
        [boundedWorktreeFile(wt,file),content]);
      // Verify every destination BEFORE the first write; no partial patches
      // can be left because a subsequent file resolved outside the worktree.
      for(const [file,content] of prepared)fs.writeFileSync(file,content);
    }catch(e){attempt.outcome='NOT_APPLICABLE';attempt.error=e.message;report.attempts.push(attempt);continue;}
    const check=runChain(chain,wt,out);
    attempt.tests=check.results.map(r=>({profile:r.profile,passed:r.passed,testProfile:r.testProfile,exitCode:r.exitCode,ms:r.ms,tests:r.tests}));
    attempt.remainingDiagnostics=check.diagnostics.slice(0,10);
    const evidence=evidenceSufficient(baseline.results,check.results);
    attempt.evidence=evidence;
    attempt.outcome=!check.passed?'FAILED_TESTS':evidence.sufficient?'VERIFIED':'INSUFFICIENT_EVIDENCE';
    report.attempts.push(attempt);
    log(`${c.strategy} -> ${attempt.outcome}`);
    if(attempt.outcome==='VERIFIED'){
      const patch=git(wt,['diff','--binary',base]).stdout;
      report.verified={candidate:c.id,strategy:c.strategy,description:c.description,patch,patchSha256:sha256(patch),
        files:[...new Set(c.edits.map(e=>e.file))],tests:attempt.tests,reviewRequired:verdict.reviewRequired,
        confidence:Number((c.score*(verdict.reviewRequired?0.7:1)).toFixed(3))};
      fs.writeFileSync(path.join(out,'repair.patch'),patch);
      break;
    }
  }
  if(!report.verified)resetWorktree(wt,base);
  report.outcome=report.verified?'VERIFIED':'NO_VERIFIED_REPAIR';
  report.finishedAt=new Date().toISOString();
  return report;
}

/** Commit the verified worktree state on a namespaced proposal branch (local only). */
export function commitProposal(wt,report,{branch,message}){
  if(!branch.startsWith(BRANCH_PREFIX)||!/^[a-z0-9._/-]{1,120}$/.test(branch))throw Error('Proposal branches must live under '+BRANCH_PREFIX);
  git(wt,['checkout','-q','-B',branch]);
  git(wt,['add','--',...report.verified.files]);
  git(wt,['-c','user.name=continuum-developer-agent','-c','user.email=continuum-developer-agent@users.noreply.github.com',
    'commit','-q','-m',message]);
  return git(wt,['rev-parse','HEAD']).stdout.trim();
}
