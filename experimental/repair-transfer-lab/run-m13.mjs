/** M13 driver: counterexample mining on the sealed M9 task set with REAL test runs.
 *   node experimental/repair-transfer-lab/run-m13.mjs <new-abs-out-dir> <tasks.json> <seal>
 * Train modules generate hypotheses (+ counterexamples); dev modules (other code) test them independently.
 * Output: hypotheses.json (HYPOTHESIS/CONFIRMED/REFUTED/INCONCLUSIVE), never an experience or ledger write. */
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {LabRunner} from './sandbox.mjs';
import {sealTasks} from './tasks.mjs';
import {tokenize} from './jstoken.mjs';
import {applyEdit,candidatesInWindow} from './operators.mjs';
import {strategyOf} from './arms.mjs';
import {generateHypotheses,testHypotheses,separatingFeature} from './boundaries.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const [out,tasksFile,expectedSeal]=process.argv.slice(2);
const git=(...a)=>{const r=spawnSync('git',a,{cwd:root,encoding:'utf8'});if(r.status!==0)throw Error('GIT');return r.stdout.trim();};
if(!path.isAbsolute(out??'')||fs.existsSync(out)||out.startsWith(root+path.sep))throw Error('NEW_ABSOLUTE_OUT_DIR_OUTSIDE_REPO');
if(git('status','--porcelain'))throw Error('CLEAN_COMMITTED_SOURCE_REQUIRED');
const {tasks,seal}=JSON.parse(fs.readFileSync(tasksFile,'utf8'));
if(seal!==expectedSeal||sealTasks(tasks)!==seal)throw Error('TASK_SEAL_MISMATCH');
fs.mkdirSync(out,{recursive:true,mode:0o700});
const runner=new LabRunner({root,timeoutMs:6000});
function matrix(split){
 const rows=[];
 for(const t of tasks.filter(x=>x.split===split)){
  const original=fs.readFileSync(path.join(root,t.module),'utf8'),faulted=applyEdit(original,t.fault);
  for(const c of candidatesInWindow(tokenize(faulted),faulted,t.window[0],t.window[1])){
   const src=applyEdit(faulted,c);
   const v=runner.run({testRel:t.test,moduleRel:t.module,source:src,names:t.parts.visible,extra:t.extra});
   const full=v.passed?runner.run({testRel:t.test,moduleRel:t.module,source:src,names:null,extra:t.extra}):{passed:false};
   rows.push({task:t.id,kind:t.diagnosis.kind,bucket:t.diagnosis.failingBucket,module:path.basename(t.module,'.mjs'),
    strategy:strategyOf(c),success:v.passed&&full.passed});
  }
 }
 return rows;
}
const trainRows=matrix('train'),devRows=matrix('dev');
const hypotheses=generateHypotheses(trainRows);
const tested=testHypotheses(hypotheses,devRows).map(h=>h.status==='CONFIRMED'?h:{...h,boundary:separatingFeature(h,devRows)});
const summary={schema:1,sourceSha:git('rev-parse','HEAD'),seal,executions:runner.executions,
 trainCandidates:trainRows.length,devCandidates:devRows.length,hypotheses:tested.length,
 byStatus:Object.fromEntries(['CONFIRMED','REFUTED','INCONCLUSIVE'].map(s=>[s,tested.filter(h=>h.status===s).length])),
 rule:'Hypotheses never change ranking, ledger or permissions; CONFIRMED means a dev re-measurement on other modules, p<0.05 one-sided.'};
fs.writeFileSync(path.join(out,'hypotheses.json'),JSON.stringify(tested,null,1)+'\n');
fs.writeFileSync(path.join(out,'summary.json'),JSON.stringify(summary,null,1)+'\n');
fs.writeFileSync(path.join(out,'train-rows.json'),JSON.stringify(trainRows)+'\n');
fs.writeFileSync(path.join(out,'dev-rows.json'),JSON.stringify(devRows)+'\n');
console.log(JSON.stringify(summary,null,1));
