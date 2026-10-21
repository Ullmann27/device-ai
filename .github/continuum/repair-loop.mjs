import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {parseKotlinDiagnostics,readJUnitDirectory,parseJUnit,parseJsErrorText,sha256} from './repair-diagnostics.mjs';
import {proposeCandidates,applyEdits} from './repair-strategies.mjs';
import {critique} from './repair-critic.mjs';
import {buildIndex,collectKotlin} from './code-analyzer.mjs';
import {strictRepairDepth,finishRepairReport} from './repair-cycle-safety.mjs';
import {checkCandidateKotlinSyntax as inspectKotlinSyntax} from './candidate-kotlin-syntax.mjs';
import {isTermuxArm64,resolveTermuxEnvironment} from '../../scripts/continuum-mobile-builder.mjs';

/**
 * Test verifier and orchestrator of one repair session:
 *   reproduce (own run, never log text) -> diagnose -> propose -> critique ->
 *   apply in an isolated git worktree -> run the fixed check chain -> keep the
 *   first fully passing candidate as a patch, reset the worktree otherwise.
 * The original checkout is never modified. Commands come only from PROFILES.
 */
export const BRANCH_PREFIX='continuum-agent/';

const DEPTH=strictRepairDepth(process.env.CONTINUUM_REPAIR_DEPTH);
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

function backendTests(wt){
  const dir=path.join(wt,'backend/test');
  if(!fs.existsSync(dir))throw Error('Required backend Node test directory missing');
  const tests=fs.readdirSync(dir).filter(f=>f.endsWith('.test.js')).sort()
    .filter(f=>fs.lstatSync(path.join(dir,f)).isFile());
  if(!tests.length)throw Error('Required backend Node test suite missing');
  return tests.map(f=>'backend/test/'+f);
}

function nodeTests(wt){
  const dir=path.join(wt,'.github/continuum');
  if(!fs.existsSync(dir))throw Error('Required Continuum Node test directory missing');
  const tests=fs.readdirSync(dir).filter(f=>f.endsWith('.test.mjs')).sort()
    .filter(f=>fs.lstatSync(path.join(dir,f)).isFile());
  if(!tests.length)throw Error('Required Continuum Node test suite missing');
  return tests.map(f=>'.github/continuum/'+f);
}

/**
 * Reuse the mobile builder's pinned ARM64 toolchain instead of creating a
 * second Gradle setup. Explicitly installed paths only; no tool downloads.
 */
export function continuumGradleProfile(wt,kind,{env=process.env,arch=process.arch}={}){
  if(kind!=='compile'&&kind!=='unit')throw Error('Unsupported Gradle check profile');
  const target=kind==='compile'?':app:compileDebugUnitTestKotlin':':app:testDebugUnitTest';
  const args=[target,'--console=plain','-Pkotlin.incremental=false'];
  if(!isTermuxArm64(env,arch))
    return {cmd:'gradle',args,cwd:wt,kotlin:true};
  const resolved=resolveTermuxEnvironment(env,arch);
  const executable=p=>typeof p==='string'&&path.isAbsolute(p)&&
    fs.existsSync(p)&&fs.statSync(p).isFile()&&(fs.statSync(p).mode&0o111)!==0;
  if(!executable(resolved.CONTINUUM_GRADLE_PATH)||
     !executable(resolved.CONTINUUM_AAPT2_PATH)||
     !executable(path.join(resolved.JAVA_HOME??'','bin/java'))||
     !resolved.ANDROID_HOME||!fs.existsSync(path.join(resolved.ANDROID_HOME,'platforms/android-36/android.jar')))
    throw Error('ANSK_MISSING_PINNED_TERMUX_ANDROID_TOOLCHAIN');
  return {cmd:resolved.CONTINUUM_GRADLE_PATH,args:[
    '--no-daemon','--max-workers=2',...args,
    '-Pandroid.aapt2FromMavenOverride='+resolved.CONTINUUM_AAPT2_PATH,
    '-Pkotlin.compiler.execution.strategy=in-process',
    '-PdeviceAiMobileAidlFallback=true'
  ],cwd:wt,kotlin:true,env:{...resolved,GRADLE_OPTS:'-Dorg.gradle.jvmargs=-Xmx1536m'}};
}

/** Fixed, reviewed check commands. Nothing here is derived from logs or inputs. */
export const PROFILES=Object.freeze({
  'node-continuum':(wt,out)=>({cmd:process.execPath,args:['--test','--test-reporter=junit','--test-reporter-destination='+path.join(out,'node-junit.xml'),...nodeTests(wt)],
    cwd:wt,junitFiles:[path.join(out,'node-junit.xml')],timeoutMs:300000}),
  'node-backend':(wt,out)=>{
    const tests=backendTests(wt);
    if(!tests.length)throw Error('Required backend Node test suite missing');
    return {cmd:process.execPath,args:['--test','--test-reporter=junit',
      '--test-reporter-destination='+path.join(out,'backend-junit.xml'),...tests],
      cwd:wt,junitFiles:[path.join(out,'backend-junit.xml')],timeoutMs:300000};
  },
  // On ARM64 Termux the existing pinned owner toolchain must also be used
  // for worktree checks. Never fall back to an unavailable desktop aapt2.
  // Outside Termux these are the unchanged deterministic Gradle profiles.
  'android-compile':wt=>({...continuumGradleProfile(wt,'compile'),timeoutMs:1200000}),
  'android-unit':wt=>({...continuumGradleProfile(wt,'unit'),
    junitDirs:[path.join(wt,'app/build/test-results/testDebugUnitTest')],
    cleanDirs:[path.join(wt,'app/build/test-results/testDebugUnitTest')],timeoutMs:1500000}),
  'jvm-harness':wt=>{
    // Local development only: JVM harness that compiles app sources without the Android SDK.
    const harness=process.env.CONTINUUM_JVM_HARNESS;
    if(!harness||!fs.existsSync(path.join(harness,'build.gradle.kts')))throw Error('CONTINUUM_JVM_HARNESS not configured');
    return {cmd:'gradle',args:['-p',harness,'test','--console=plain','-q','-Pkotlin.incremental=false'],cwd:wt,env:{DEVICE_AI_REPO:wt},kotlin:true,
      junitDirs:[path.join(harness,'build/test-results/test')],cleanDirs:[path.join(harness,'build/test-results/test')],timeoutMs:1500000};
  }
});
export const CHAINS=Object.freeze({
  node:['node-continuum'],
  stability:['node-continuum','node-backend'],
  android:['android-compile','android-unit'],
  jvm:['jvm-harness']
});
/** Profiles whose evidence is executed tests (independent of flags in a report). */
export const TEST_PROFILES=Object.freeze(['node-continuum','node-backend','android-unit','jvm-harness']);

export function runProfile(name,wt,out,{deadline=null,forceRerun=false}={}){
  const factory=PROFILES[name];if(!factory)throw Error('Unknown check profile '+name);
  const p=factory(wt,out);
  for(const d of p.cleanDirs??[])fs.rmSync(d,{recursive:true,force:true});
  for(const f of p.junitFiles??[])fs.rmSync(f,{force:true});
  if(deadline!==null&&(!Number.isSafeInteger(deadline)||deadline<=Date.now()))
    throw Error('Repair test deadline exhausted before '+name);
  const remaining=()=>deadline===null?p.timeoutMs:Math.min(p.timeoutMs,Math.max(1,deadline-Date.now()));
  if(forceRerun&&name!=='android-compile'&&name!=='android-unit')
    throw Error('Forced rerun is supported for Android check profiles only');
  const args=forceRerun?[...p.args,'--rerun-tasks']:p.args;
  const r=run(p.cmd,args,{cwd:p.cwd,timeoutMs:remaining(),env:p.env});
  const text=r.stdout+'\n'+r.stderr;
  const diagnostics=p.kotlin?parseKotlinDiagnostics(text,wt):[];
  const tests={total:0,failed:0,skipped:0,placeholders:0,executed:0,passed:0};
  const skippedCaseHashes=[];
  let completeSkipIdentities=true;
  const addTests=j=>{
    for(const k of ['total','failed','skipped','placeholders','executed','passed'])tests[k]+=j[k]??0;
    diagnostics.push(...j.diagnostics);
    if(j.skipped>0){
      const ids=j.skippedCaseHashes;
      if(!Array.isArray(ids)||ids.length!==j.skipped||
         ids.some(id=>typeof id!=='string'||!/^[0-9a-f]{64}$/.test(id)))
        completeSkipIdentities=false;
      else skippedCaseHashes.push(...ids);
    }
  };
  for(const dir of p.junitDirs??[])addTests(readJUnitDirectory(dir,wt));
  for(const file of p.junitFiles??[])if(fs.existsSync(file))addTests(parseJUnit(fs.readFileSync(file,'utf8'),wt));
  // Permit pre-existing JUnit skips only with a complete, stable case-identity
  // digest. ANSK independently compares the same identity across all green runs.
  if(tests.skipped>0&&completeSkipIdentities&&
     skippedCaseHashes.length===tests.skipped&&
     new Set(skippedCaseHashes).size===skippedCaseHashes.length)
    tests.skippedCaseDigest=sha256(skippedCaseHashes.sort().join('\0'));
  // Test files that crash while importing report only "test failed" in JUnit;
  // re-run just those (same fixed runner, TAP) to recover the real error location.
  // Only files that were arguments of the fixed command itself are re-run.
  if(p.junitFiles){
    const listed=new Set(p.args.filter(a=>/^\.github\/continuum\/[\w.-]+\.test\.mjs$/.test(a)));
    const crashed=[...new Set(diagnostics.filter(d=>!d.file&&listed.has(d.testFile)).map(d=>d.testFile))].slice(0,3);
    for(const file of crashed){
      if(deadline!==null&&Date.now()>=deadline)break;
      const again=run(p.cmd,['--test','--test-reporter=tap',file],{
        cwd:p.cwd,timeoutMs:Math.min(120000,remaining())
      });
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

export function runChain(chain,wt,out,{deadline=null,forceRerun=false}={}){
  const results=[];
  for(const name of CHAINS[chain]??(()=>{throw Error('Unknown chain '+chain);})()){
    const r=runProfile(name,wt,out,{deadline,forceRerun});results.push(r);
    if(!r.passed)break;
  }
  return {passed:results.every(r=>r.passed),results,diagnostics:results.flatMap(r=>r.diagnostics)};
}

export function createWorktree(root,base){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-repair-wt-'));
  try{
    git(root,['worktree','add','--detach','--force',dir,base]);
    return dir;
  }catch(error){
    // Even a failed Git add can leave an allocated directory or registration.
    git(root,['worktree','remove','--force',dir],{allowFailure:true});
    try{fs.rmSync(dir,{recursive:true,force:true});}
    catch(cleanupError){throw new AggregateError([error,cleanupError],
      'Worktree creation and cleanup failed');}
    const pruned=git(root,['worktree','prune'],{allowFailure:true});
    if(pruned.status!==0)throw new AggregateError([error,Error(pruned.stderr)],
      'Worktree creation and pruning failed');
    throw error;
  }
}
export function resetWorktree(wt,base){
  git(wt,['reset','--hard','-q',base]);
  git(wt,['clean','-fdq','-e','build','-e','.gradle','-e','app/build']);
}
export function removeWorktree(root,wt){
  const absolute=path.resolve(wt);
  if(path.dirname(absolute)!==path.resolve(os.tmpdir())||
    !path.basename(absolute).startsWith('continuum-repair-wt-'))
    throw Error('Refusing to remove an unmanaged worktree path');
  const removed=git(root,['worktree','remove','--force',absolute],{allowFailure:true});
  let fileError=null;
  try{fs.rmSync(absolute,{recursive:true,force:true});}
  catch(error){fileError=error;}
  const pruned=git(root,['worktree','prune'],{allowFailure:true});
  const failures=[];
  if(removed.status!==0)failures.push('git worktree remove: '+(removed.stderr||removed.stdout).slice(-180));
  if(fileError)failures.push('worktree directory removal: '+fileError.message.slice(0,180));
  if(pruned.status!==0)failures.push('git worktree prune: '+(pruned.stderr||pruned.stdout).slice(-180));
  if(failures.length)throw Error('Worktree cleanup incomplete: '+failures.join('; '));
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
    relative.includes('\0')||/[\\/]{2}/.test(relative)||path.isAbsolute(relative)||
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

/** Fail fast on invalid JavaScript after a candidate is applied, but before
 * starting the full test chain. The trusted Node executable parses the file
 * without executing its top-level statements. This is NOT a security sandbox.
 */
export function checkCandidateJsSyntax(wt,relativeFiles,{timeoutMs=10000}={}){
  if(!Array.isArray(relativeFiles)||relativeFiles.length>3||
     relativeFiles.some(f=>typeof f!=='string')||
     !Number.isSafeInteger(timeoutMs)||timeoutMs<1||timeoutMs>20000)
    throw Error('Invalid syntax-check request');
  const checked=[];
  for(const relative of [...new Set(relativeFiles)]){
    if(!/\.(?:mjs|cjs|js)$/.test(relative))continue;
    const absolute=boundedWorktreeFile(wt,relative);
    const result=run(process.execPath,['--check',absolute],{
      cwd:wt,timeoutMs,env:{NODE_OPTIONS:'',NODE_V8_COVERAGE:''}
    });
    if(result.status!==0||result.error||result.signal){
      return {passed:false,checked,failedFile:relative,
        reason:result.error||result.signal?'SYNTAX_CHECK_INTERRUPTED':'INVALID_JS_SYNTAX'};
    }
    checked.push(relative);
  }
  return {passed:true,checked,failedFile:null,reason:null};
}

/**
 * Kotlin preflight over ordinary files from the isolated candidate worktree.
 * Fails safely on lexically broken files but does NOT claim to be a compiler.
 */
export function checkCandidateKotlinSyntax(wt,relativeFiles,{base=null}={}){
  if(!Array.isArray(relativeFiles)||relativeFiles.length>3||
     relativeFiles.some(f=>typeof f!=='string'))
    throw Error('Invalid Kotlin syntax-check request');
  const files=Object.create(null);
  for(const relative of [...new Set(relativeFiles)]){
    if(!/\.(?:kt|kts)$/.test(relative))continue;
    const full=boundedWorktreeFile(wt,relative);
    const size=fs.statSync(full).size;
    if(size<1||size>750_000)return {passed:false,checked:[],
      failedFile:relative,reason:'EMPTY_OR_OVERSIZED_KOTLIN_SOURCE'};
    files[relative]=fs.readFileSync(full,'utf8');
    if(base&&relative.endsWith('.kt')){
      const old=git(wt,['show',base+':'+relative],{allowFailure:true});
      if(old.status===0){
        const getPackage=t=>t.match(/^\s*package\s+([\w.]+)/m)?.[1]??'';
        if(getPackage(old.stdout)!==getPackage(files[relative]))
          return {passed:false,checked:[],failedFile:relative,
            reason:'KOTLIN_PACKAGE_CHANGED'};
      }
    }
  }
  return inspectKotlinSyntax(files);
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

/** Historical success must not make a potentially destructive whole-file revert
 * outrank a localized repair. A hunk revert is less risky but still follows
 * direct diagnostic fixes. These classes are safety constraints, not learned
 * preferences; confidence continues to sort WITHIN a safety class.
 */
export function repairCandidateRiskTier(strategy){
  if(strategy==='REVERT_FILE_TO_PARENT')return 2;
  if(strategy==='LOCAL_MODEL_PATCH')return 1;
  if(strategy==='REVERT_HUNK_TO_PARENT')return 1;
  return 0;
}

/** Order by non-destructive risk tier, then Laplace-smoothed experience/prior. */
export function rankCandidates(candidates,stats=[],failedIds=new Set()){
  const byKey=new Map(stats.map(s=>[s.kind,s]));
  return candidates.filter(c=>!failedIds.has(c.diagnostic+':'+c.id)).map(c=>{
    const s=byKey.get((c.diagnosticKind+'|'+c.strategy).slice(0,80));
    const learned=s?(s.successes+1)/(s.successes+s.failures+2):0.5;
    return {...c,score:Number((0.6*learned+0.4*(c.prior??0.5)).toFixed(4))};
  }).sort((a,b)=>repairCandidateRiskTier(a.strategy)-repairCandidateRiskTier(b.strategy)||
    b.score-a.score||a.id.localeCompare(b.id));
}

/** Observe a neural ranking without granting any authority over execution.
 * All inputs are independent copies. Even a malicious, throwing, or corrupted
 * observer cannot change the candidates or report a validated repair.
 */
export function runRepairShadowObserver(observer,candidates,ranked,stats,failedIds,diagnostics){
 if(observer===null||observer===undefined)return null;
 const active=ranked.map(c=>c.id);
 try{
  if(typeof observer!=='function')throw Error('UNTRUSTED_SHADOW_OBSERVER');
  const proposal=observer({candidates:structuredClone(candidates),
   stats:structuredClone(stats),failedIds:new Set(failedIds),
   diagnostics:structuredClone(diagnostics)});
  const order=proposal?.shadowOrder;
  if(!Array.isArray(order)||order.length!==active.length||
     order.some(id=>typeof id!=='string')||
     new Set(order).size!==order.length||
     order.some(id=>!active.includes(id)))
    throw Error('INVALID_CEFR_SHADOW_ORDER');
  const byId=new Map(ranked.map(c=>[c.id,repairCandidateRiskTier(c.strategy)]));
  for(let i=1;i<order.length;i++)
   if(byId.get(order[i-1])>byId.get(order[i]))
    throw Error('CEFR_SHADOW_TIER_VIOLATION');
  return {mode:proposal.mode==='CEFR_DEV_SHADOW'?'CEFR_DEV_SHADOW':'SHADOW_NO_MODEL',
   candidateCount:active.length,reordered:order.some((id,i)=>id!==active[i]),
   modelSha256:/^[a-f0-9]{64}$/.test(proposal.modelSha256??'')?
    proposal.modelSha256:null,
   productionOrder:active,shadowOrder:order};
 }catch{
  return {mode:'CEFR_SHADOW_REJECTED',candidateCount:active.length,
   reordered:false,modelSha256:null,productionOrder:active,shadowOrder:active};
 }
}

/**
 * One repair session on commit `base` in `wt` (an existing clean worktree).
 * Returns a report; never touches the caller's checkout.
 */
export function repairSession({wt,base,chain,out,ledger={attempts:[],experiences:[]},maxCandidates=4,deadline=Date.now()+25*60000,log=()=>{},shadowObserver=null,candidateProvider=null}){
  if(DEPTH>0)throw Error('Nested repair sessions are not allowed');
  if(!path.isAbsolute(out))throw Error('Report directory must be absolute (checks run inside the worktree)');
  fs.mkdirSync(out,{recursive:true});
  resetWorktree(wt,base);
  // This worker ranks deterministic repair strategies. An installed model
  // elsewhere does not prove that this session invoked generative inference.
  const report={schemaVersion:1,base,chain,startedAt:new Date().toISOString(),
    inference:{mode:'DETERMINISTIC_ONLY',modelId:null,modelInvocations:0,
      reason:'No generative model is invoked by this repair pipeline'},
    baseline:null,attempts:[],verified:null,outcome:null};
  const baseline=runChain(chain,wt,out,{deadline});
  report.baseline={passed:baseline.passed,results:baseline.results.map(({outputTail,diagnostics,...r})=>({...r,diagnostics:diagnostics.length})),
    diagnostics:baseline.diagnostics};
  log(`baseline ${baseline.passed?'PASS':'FAIL'} with ${baseline.diagnostics.length} diagnostics`);
  if(baseline.passed)return finishRepairReport(report,'NO_FAILURE');
  const actionable=baseline.diagnostics.filter(d=>d.file);
  if(!actionable.length){
    report.baselineTail=baseline.results.at(-1)?.outputTail;
    return finishRepairReport(report,'NO_CODE_DIAGNOSTIC');
  }
  const ctx=context(wt,base,ledger.attempts);
  // Parser infrastructure failures retain audit history, but do not prove
  // that a candidate is invalid. Only an actual syntax rejection suppresses it.
  const failedIds=new Set(ledger.attempts.filter(a=>a.outcome!=='VERIFIED'&&
    a.outcome!=='SYNTAX_CHECK_ERROR'&&
    !(a.outcome==='FAILED_SYNTAX'&&a.syntaxReason!=='INVALID_JS_SYNTAX'))
    .map(a=>a.diagnostic+':'+a.candidate));
  const localScope=Array.isArray(candidateProvider?.allowedPaths)&&candidateProvider.allowedPaths.length<=2
    ? [...candidateProvider.allowedPaths].filter(file=>typeof file==='string') : [];
  const proposals=proposeCandidates(actionable,{...ctx,allowLocalModelPatches:candidateProvider!==null,
    allowedLocalPaths:localScope});
  if(candidateProvider!==null){
    // Trusted opt-in adapter only; model data cannot set a strategy, diagnostic,
    // risk, test command or authority. Every proposal still traverses all gates.
    report.inference={mode:'LOCAL_CANDIDATE_PROVIDER',modelId:null,modelInvocations:0,
      localRequests:0,verifiedModelExecution:false,reason:'No model execution independently established'};
    try{
      if(typeof candidateProvider!=='function'||!localScope.length||Date.now()>=deadline)throw Error('LOCAL_PROVIDER_UNAVAILABLE');
      const result=candidateProvider({diagnostics:structuredClone(actionable),readFile:ctx.readFile,base,chain,deadline});
      const patch=result?.patch;
      if(result?.mode!=='LOCAL_LOOPBACK_PATCH_SHADOW'||result.trusted!==false||result.autoApply!==false||
        !patch||Object.keys(patch).sort().join(',')!=='file,newText,oldText'||
        typeof patch.file!=='string'||!localScope.includes(patch.file)||typeof patch.oldText!=='string'||typeof patch.newText!=='string'||
        !patch.oldText||!patch.newText||patch.oldText===patch.newText||
        patch.oldText.length>1600||patch.newText.length>1600||/[\0]/.test(patch.oldText+patch.newText))
        throw Error('LOCAL_PATCH_SCHEMA_REJECTED');
      const before=ctx.readFile(patch.file);
      if(typeof before!=='string'||before.split(patch.oldText).length!==2)
        throw Error('LOCAL_PATCH_NOT_EXACT');
      const edits=[{file:patch.file,oldText:before,newText:before.replace(patch.oldText,()=>patch.newText)}];
      proposals.push({id:sha256('LOCAL_MODEL_PATCH\0'+JSON.stringify(edits)).slice(0,16),
        strategy:'LOCAL_MODEL_PATCH',diagnostic:actionable[0].fingerprint,
        diagnosticKind:actionable[0].kind,prior:0.2,
        description:'Opt-in local candidate; requires independent tests and owner review',edits});
      report.inference.localRequests=Number.isSafeInteger(result.localRequests)?Math.min(1,Math.max(0,result.localRequests)):0;
      report.inference.candidateStatus='PROPOSED';
    }catch{
      // No prompts, secrets, model text or transport errors in durable reports.
      report.inference.candidateStatus='UNAVAILABLE_OR_REJECTED';
    }
  }
  const ranked=rankCandidates(proposals,ledger.experiences,failedIds);
  const shadow=runRepairShadowObserver(shadowObserver,proposals,ranked,
    ledger.experiences,failedIds,actionable);
  if(shadow!==null)report.cefrShadow=shadow;
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
    // Fast, nonexecuting grammar gate: an invalid .mjs/.js/.cjs candidate
    // must not consume a full regression/Gradle cycle. Compiler and executed
    // tests remain mandatory even if the syntax gate passes.
    let syntax;
    try {
      syntax=checkCandidateJsSyntax(wt,c.edits.map(e=>e.file),{
        timeoutMs:Math.min(10000,Math.max(1,deadline-Date.now()))
      });
    } catch(error) {
      attempt.outcome='SYNTAX_CHECK_ERROR';
      attempt.syntaxReason=error?.code??'INVALID_CHECK_REQUEST';
      report.attempts.push(attempt);
      continue;
    }
    if(syntax.passed){
      const kotlin=checkCandidateKotlinSyntax(wt,c.edits.map(e=>e.file),{base});
      attempt.kotlinSyntax=kotlin;
      if(!kotlin.passed)syntax=kotlin;
    }
    attempt.syntax=syntax;
    if(!syntax.passed) {
      attempt.outcome='FAILED_SYNTAX';
      report.attempts.push(attempt);
      log(c.strategy+' -> FAILED_SYNTAX');
      continue;
    }
    const check=runChain(chain,wt,out,{deadline});
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
  return finishRepairReport(report,report.verified?'VERIFIED':'NO_VERIFIED_REPAIR');
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
