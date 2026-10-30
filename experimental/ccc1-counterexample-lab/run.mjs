/** Entirely opt-in synthetic experiment. NEVER changes worker, ledger, branch, or APK. */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {testCandidate as testOracleCandidate} from './oracle.mjs';
import {rankCandidates} from '../../.github/continuum/repair-loop.mjs';
import {repairExperienceKey} from '../../.github/continuum/repair-experience.mjs';
import {fixtureSet} from './fixtures.mjs';
import {sha256,sealFixtures,learnConservativeGuards,guardedShadowChoice,
 summarizeTrial} from './policy.mjs';

const SEALED='23a3a3bbc3d4769ccaaa92d5a73c6032dd4beee631cde12b0768208932d30831';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const out=process.argv[2];
if(path.isAbsolute(out??'')&&(path.resolve(out)!==out||
 fs.realpathSync(path.dirname(out))!==path.dirname(out)))
 throw Error('NONCANONICAL_OUTPUT_PATH');
if(process.argv.length!==3||!path.isAbsolute(out??'')||fs.existsSync(out)||
 out===root||out.startsWith(root+path.sep))throw Error('FRESH_ABSOLUTE_EXTERNAL_OUTPUT_REQUIRED');
function git(...args) {
 const result=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:10000});
 if(result.status!==0)throw Error('GIT_STATE_UNAVAILABLE');
 return result.stdout.trim();
}
const sourceSha=git('rev-parse','HEAD');
if(git('status','--porcelain'))throw Error('CLEAN_COMMITTED_SOURCE_REQUIRED');
const training=fixtureSet('training'),holdout=fixtureSet('holdout');
if(sealFixtures(holdout)!==SEALED||
 new Set([...training,...holdout].map(t=>t.id)).size!==training.length+holdout.length)
 throw Error('HOLDOUT_SEAL_BROKEN');
fs.mkdirSync(out,{recursive:false,mode:0o700});
const evidence=path.join(out,'evidence');
fs.mkdirSync(evidence,{mode:0o700});
function testCandidate(task,strategy,cohort,arm) {
 return testOracleCandidate({task,strategy,cohort,arm,sourceSha,evidenceDir:evidence});
}
const strategies=['JS_TYPO_REFERENCE','JS_MISSING_IMPORT'];
const observations=[];
for(const task of training)for(const strategy of strategies)
 observations.push(testCandidate(task,strategy,'training','TRAIN'));
const guards=learnConservativeGuards(observations,3);
function stats(scoped) {
 const m=new Map();
 for(const r of observations){
  const kind=repairExperienceKey('REFERENCE_ERROR',r.strategy,
   scoped?r.contextKey:null);
  const s=m.get(kind)??{kind,successes:0,failures:0};
  s[r.passed?'successes':'failures']++;
  m.set(kind,s);
 }
 return [...m.values()];
}
const broad=stats(false),context=stats(true);
function candidates(task) {
 return strategies.map((strategy,i)=>({id:String(i+1).padStart(16,'0'),
  strategy,diagnostic:'a'.repeat(64),diagnosticKind:'REFERENCE_ERROR',
  contextKey:task.contextKey,prior:.5}));
}
const rows=[];
for(const task of holdout){
 const offered=candidates(task);
 const baseline=rankCandidates(offered),general=rankCandidates(offered,broad);
 const scoped=rankCandidates(offered,context);
 const guard=guardedShadowChoice(task,scoped,guards);
 const chosen={baseline:baseline[0].strategy,broad:general[0].strategy,
  context:scoped[0].strategy,guarded:guard.choice};
 const row={task:task.id,contextKey:task.contextKey,feature:task.feature,
  guardedReason:guard.reason};
 for(const [arm,strategy] of Object.entries(chosen)){
  row[arm+'Strategy']=strategy;
  row[arm+'Passed']=strategy===null?null:
   testCandidate(task,strategy,'holdout',arm).passed;
 }
 rows.push(row);
}
if(git('rev-parse','HEAD')!==sourceSha||git('status','--porcelain'))
 throw Error('SOURCE_CHANGED_DURING_EXPERIMENT');
const summary=summarizeTrial(rows,SEALED,sealFixtures(holdout));
const report={...summary,sourceSha,trainingCases:training.length,
 trainingRecords:observations.length,guards,
 trainingRecordsSha256:sha256(JSON.stringify(observations)),
 evidenceFiles:fs.readdirSync(evidence).length,sourceUnchanged:true,
 limitations:['Synthetic heldout pattern families reuse training templates',
 'Feature is source-derived, not a general semantic precondition',
 'Child processes execute authored fixtures but are not OS-confined',
 'No independent external reviewer attests results',
 'No generated code, no hardware gain, no release authorization']};
fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n');
fs.writeFileSync(path.join(out,'rows.json'),JSON.stringify(rows,null,2)+'\n');
console.log(JSON.stringify({sourceSha,holdout:summary.tasks,arms:summary.arms,
 guards,output:out,productionActivationAllowed:false},null,2));
