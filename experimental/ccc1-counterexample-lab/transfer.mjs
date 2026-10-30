/** M3 · capability-transfer trial on the NOVEL cohort (opt-in research; never changes worker, ledger, branch or APK).
 * Arms: baseline (no experience) · broad experience · context experience · conservative guard (may abstain) ·
 * guard + independent verification (budget 2) · verification only, no experience (budget 2).
 * Usage: node experimental/ccc1-counterexample-lab/transfer.mjs <new-absolute-output-dir-outside-the-repo> */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {rankCandidates} from '../../.github/continuum/repair-loop.mjs';
import {repairExperienceKey} from '../../.github/continuum/repair-experience.mjs';
import {fixtureSet} from './fixtures.mjs';
import {novelFixtureSet,familyOf} from './novel.mjs';
import {sealFixtures,learnConservativeGuards,guardedShadowChoice,sha256} from './policy.mjs';
import {testCandidate} from './oracle.mjs';
import {verifyWithinBudget,summarizeTransfer} from './transfer-policy.mjs';

// Sealed BEFORE the first run (docs/research/m3/M3-PREREGISTRATION-20261030.md); never re-sealed after results.
export const NOVEL_SEALED='5a306c87c02be2fe9fe2bfe8da9beb0e8711c9de1d339e5a280f2f281adcfe68';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const out=process.argv[2];
if(process.argv.length!==3||!path.isAbsolute(out??'')||path.resolve(out)!==out||fs.existsSync(out)||
 fs.realpathSync(path.dirname(out))!==path.dirname(out)||out===root||out.startsWith(root+path.sep))
 throw Error('FRESH_ABSOLUTE_EXTERNAL_OUTPUT_REQUIRED');
const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:10000});
 if(r.status!==0)throw Error('GIT_STATE_UNAVAILABLE');return r.stdout.trim();};
const sourceSha=git('rev-parse','HEAD');
if(git('status','--porcelain'))throw Error('CLEAN_COMMITTED_SOURCE_REQUIRED');
const training=fixtureSet('training'),novel=novelFixtureSet();
if(sealFixtures(novel)!==NOVEL_SEALED)throw Error('NOVEL_SEAL_BROKEN');
fs.mkdirSync(out,{mode:0o700});
const evidenceDir=path.join(out,'evidence');fs.mkdirSync(evidenceDir,{mode:0o700});
const STRATEGIES=['JS_TYPO_REFERENCE','JS_MISSING_IMPORT'];
const observations=training.flatMap(task=>STRATEGIES.map(strategy=>
 testCandidate({task,strategy,cohort:'training',arm:'TRAIN',sourceSha,evidenceDir})));
const guards=learnConservativeGuards(observations,3);
const stats=scoped=>{const m=new Map();for(const r of observations){
 const kind=repairExperienceKey('REFERENCE_ERROR',r.strategy,scoped?r.contextKey:null);
 const s=m.get(kind)??{kind,successes:0,failures:0};s[r.passed?'successes':'failures']++;m.set(kind,s);}
 return [...m.values()];};
const broad=stats(false),context=stats(true);
const offered=task=>STRATEGIES.map((strategy,i)=>({id:String(i+1).padStart(16,'0'),strategy,
 diagnostic:'a'.repeat(64),diagnosticKind:'REFERENCE_ERROR',contextKey:task.contextKey,prior:.5}));
const rows=[];
for(const task of novel){
 const run=arm=>strategy=>testCandidate({task,strategy,cohort:'novel',arm,sourceSha,evidenceDir}).passed;
 const base=rankCandidates(offered(task)).map(c=>c.strategy);
 const general=rankCandidates(offered(task),broad).map(c=>c.strategy);
 const scoped=rankCandidates(offered(task),context);
 const guard=guardedShadowChoice(task,scoped,guards);
 const scopedOrder=scoped.map(c=>c.strategy);
 const guardOrder=guard.choice?[guard.choice,...STRATEGIES.filter(s=>s!==guard.choice)]:scopedOrder;
 rows.push({task:task.id,feature:task.feature,contextKey:task.contextKey,guardReason:guard.reason,
  baseline:verifyWithinBudget(base,run('baseline'),1),
  broad:verifyWithinBudget(general,run('broad'),1),
  context:verifyWithinBudget(scopedOrder,run('context'),1),
  guarded:verifyWithinBudget(guard.choice?[guard.choice]:[],run('guarded'),1),
  guardedVerify:verifyWithinBudget(guardOrder,run('guardedVerify'),2),
  verifyOnly:verifyWithinBudget(base,run('verifyOnly'),2)});
}
if(git('rev-parse','HEAD')!==sourceSha||git('status','--porcelain'))throw Error('SOURCE_CHANGED_DURING_EXPERIMENT');
const summary=summarizeTransfer(rows,familyOf);
const report={...summary,sourceSha,novelSeal:NOVEL_SEALED,trainingRecords:observations.length,
 trainingRecordsSha256:sha256(JSON.stringify(observations)),guards,evidenceFiles:fs.readdirSync(evidenceDir).length,
 limitations:['Synthetic JS fixtures authored by one instance; families are new forms, not new real projects',
  'Two fixed candidate strategies; no generated code','Child processes are not an OS sandbox',
  'No external attestation; no CCC1 ledger write; no Fold7 execution']};
fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n');
fs.writeFileSync(path.join(out,'rows.json'),JSON.stringify(rows,null,2)+'\n');
console.log(JSON.stringify({sourceSha,tasks:summary.tasks,arms:summary.arms,output:out},null,2));
