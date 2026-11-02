import fs from 'node:fs';
import path from 'node:path';
import {git,createWorktree,removeWorktree,resetWorktree,repairSession} from './repair-loop.mjs';
import {sha256} from './repair-diagnostics.mjs';

/**
 * Reproducible fault-injection benchmark for the repair loop. Each fault is a
 * deliberate, exact edit committed on top of the current HEAD inside an
 * isolated worktree; the agent must reproduce, diagnose and repair it without
 * being told the fault. Two faults replay real historical CI failures of this
 * repository (48bd150 literal "\n" in Kotlin source, 373bddb Set.takeLast).
 */
export const FAULTS=Object.freeze([
  {id:'js-typo-reference',chain:'node',file:'.github/continuum/portable-subword.mjs',
    find:'intentScore(text){const z=this.encode(text);return sigmoid(',replace:'intentScore(text){const z=this.encode(text);return sigmiod(',
    expect:'JS_TYPO_REFERENCE'},
  {id:'js-missing-import',chain:'node',file:'.github/continuum/neural-core.mjs',
    find:"import {SubwordEncoder,sha256,buckets} from './portable-subword.mjs';",replace:"import {SubwordEncoder,buckets} from './portable-subword.mjs';",
    expect:'JS_MISSING_IMPORT'},
  // The ReferenceError is swallowed by a try/catch in ingestPublicSources, so it
  // only surfaces as wrong behavior: only the regression search can find it.
  {id:'js-masked-missing-import',chain:'node',file:'.github/continuum/neural-core.mjs',
    find:"import {SubwordEncoder,sha256,buckets} from './portable-subword.mjs';",replace:"import {SubwordEncoder,sha256} from './portable-subword.mjs';",
    expect:'REVERT_FILE_TO_PARENT'},
  {id:'kt-historical-escaped-newline-48bd150',chain:'kotlin',file:'app/src/test/java/at/heinz/deviceai/learning/ContinuumPublicModelPackageTest.kt',
    find:'.also {\n        it[0]=83; it[1]=87; it[2]=66; it[3]=49\n    }',replace:'.also {\\n        it[0]=83; it[1]=87; it[2]=66; it[3]=49\\n    }',
    expect:'KOTLIN_ESCAPED_NEWLINE'},
  {id:'kt-historical-set-takelast-373bddb',chain:'kotlin',file:'app/src/main/java/at/heinz/deviceai/learning/ContinuumSourceNovelty.kt',
    find:'.sorted().takeLast(120)',replace:'.toSet().takeLast(120)',expect:null},
  {id:'kt-typo-reference',chain:'kotlin',file:'app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt',
    find:'val candidate=TrainableSubwordEncoder.restore(baseline.save())\n            val before=candidate.evaluateIntent(INTENT_BOOTSTRAP)',
    replace:'val candidate=TrainableSubwordEncodr.restore(baseline.save())\n            val before=candidate.evaluateIntent(INTENT_BOOTSTRAP)',
    expect:'KOTLIN_TYPO_REFERENCE'},
  {id:'kt-missing-import',chain:'kotlin',file:'app/src/main/java/at/heinz/deviceai/learning/ModelCheckpointStore.kt',
    find:'import java.nio.file.Files\n',replace:'',expect:'KOTLIN_MISSING_IMPORT'},
  {id:'kt-behavior-regression',chain:'kotlin',file:'app/src/main/java/at/heinz/deviceai/learning/StrictJson.kt',
    find:'require(key !in result) { "Doppelter JSON-Schlüssel: "+key.take(40) }',replace:'// duplicate keys tolerated',
    expect:'REVERT_FILE_TO_PARENT'}
]);

export function injectFault(wt,fault){
  const file=path.join(wt,fault.file);
  if(!fs.existsSync(file))return {skipped:'file missing'};
  const text=fs.readFileSync(file,'utf8');
  if(text.split(fault.find).length!==2)return {skipped:'anchor not unique or absent at this revision'};
  fs.writeFileSync(file,text.replace(fault.find,()=>fault.replace));
  git(wt,['add','--',fault.file]);
  git(wt,['-c','user.name=continuum-benchmark','-c','user.email=continuum-benchmark@users.noreply.github.com',
    'commit','-q','-m','benchmark fault '+fault.id]);
  return {commit:git(wt,['rev-parse','HEAD']).stdout.trim(),original:text};
}

/**
 * Runs the selected faults sequentially in ONE worktree (incremental builds).
 * `chain` maps fault families to a check chain, e.g. {node:'node',kotlin:'android'}.
 */
export function runBenchmark({root,head,chains,faults=FAULTS,out,ledger,onSession=()=>{},log=()=>{}}){
  const selected=faults.filter(f=>chains[f.chain]);
  const results=[];
  const wt=createWorktree(root,head);
  try{
    for(const fault of selected){
      resetWorktree(wt,head);
      const injected=injectFault(wt,fault);
      if(injected.skipped){results.push({fault:fault.id,outcome:'SKIPPED',reason:injected.skipped});continue;}
      const faultPatch=git(wt,['diff','--binary',head,injected.commit]).stdout;
      const dir=path.join(out,fault.id);fs.mkdirSync(dir,{recursive:true});
      fs.writeFileSync(path.join(dir,'fault.patch'),faultPatch);
      log(`fault ${fault.id} injected as ${injected.commit.slice(0,12)}`);
      const report=repairSession({wt,base:injected.commit,chain:chains[fault.chain],out:dir,ledger,log:m=>log('  '+m)});
      const repaired=report.verified?fs.readFileSync(path.join(wt,fault.file),'utf8'):null;
      const result={fault:fault.id,chain:chains[fault.chain],baseCommit:injected.commit,faultPatchSha256:sha256(faultPatch),
        outcome:report.outcome,strategy:report.verified?.strategy??null,expectedStrategy:fault.expect,
        attempts:report.attempts.length,diagnostics:report.baseline?.diagnostics?.length??0,
        restoredExactly:repaired!=null&&repaired===injected.original,patchSha256:report.verified?.patchSha256??null,
        ms:Date.parse(report.finishedAt??new Date().toISOString())-Date.parse(report.startedAt)};
      fs.writeFileSync(path.join(dir,'report.json'),JSON.stringify(report,null,1)+'\n');
      results.push(result);
      ledger=onSession(report,result)??ledger;
    }
  }finally{removeWorktree(root,wt);}
  const attempted=results.filter(r=>r.outcome!=='SKIPPED');
  return {schemaVersion:1,head,chains,results,
    repaired:attempted.filter(r=>r.outcome==='VERIFIED').length,attempted:attempted.length,
    repairRate:attempted.length?Number((attempted.filter(r=>r.outcome==='VERIFIED').length/attempted.length).toFixed(3)):null,ledger};
}
