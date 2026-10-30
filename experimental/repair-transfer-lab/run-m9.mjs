/** M9/M10 driver.
 *   node experimental/repair-transfer-lab/run-m9.mjs prepare <new-abs-out-dir>
 *   node experimental/repair-transfer-lab/run-m9.mjs run <new-abs-out-dir> <tasks.json> <seal> [reps]
 * Both modes need a clean committed checkout and an output directory outside the repository. Research only:
 * no ledger, worker, branch or app is touched. */
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {LabRunner} from './sandbox.mjs';
import {buildTasks,sealTasks,failingNames} from './tasks.mjs';
import {tokenize} from './jstoken.mjs';
import {applyEdit,candidatesInWindow} from './operators.mjs';
import {ARMS,Experience,attempt,strategyOf,binomTwoSided} from './arms.mjs';
import {VISIBLE_BUDGET} from './config.mjs';
const sha=s=>createHash('sha256').update(s).digest('hex');
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const [mode,out,tasksFile,expectedSeal,repsArg]=process.argv.slice(2);
const git=(...a)=>{const r=spawnSync('git',a,{cwd:root,encoding:'utf8'});if(r.status!==0)throw Error('GIT');return r.stdout.trim();};
if(!['prepare','run'].includes(mode)||!path.isAbsolute(out??'')||fs.existsSync(out)||out.startsWith(root+path.sep))
 throw Error('USAGE: prepare|run <new-absolute-out-dir-outside-repo> ...');
if(git('status','--porcelain'))throw Error('CLEAN_COMMITTED_SOURCE_REQUIRED');
const head=git('rev-parse','HEAD');
fs.mkdirSync(out,{recursive:true,mode:0o700});
const write=(f,v)=>fs.writeFileSync(path.join(out,f),JSON.stringify(v,null,1)+'\n');
const log=m=>process.stderr.write(m+'\n');

if(mode==='prepare'){
 const runner=new LabRunner({root});
 const {tasks,modules}=buildTasks({root,runner,log});
 const seal=sealTasks(tasks);
 write('tasks.json',{schema:1,sourceSha:head,seal,tasks});write('modules.json',modules);
 fs.writeFileSync(path.join(out,'seal.txt'),seal+'\n');
 console.log(JSON.stringify({head,seal,tasks:tasks.length,bySplit:Object.fromEntries(['train','dev','holdout'].map(s=>[s,tasks.filter(t=>t.split===s).length])),
  excluded:modules.filter(m=>m.excluded).map(m=>m.module+':'+m.excluded),executions:runner.executions},null,1));
 process.exit(0);
}

const {tasks,seal}=JSON.parse(fs.readFileSync(tasksFile,'utf8'));
if(seal!==expectedSeal||sealTasks(tasks)!==seal)throw Error('TASK_SEAL_MISMATCH');
for(const t of tasks)if(sha(fs.readFileSync(path.join(root,t.module),'utf8'))!==t.originalSha256)throw Error('MODULE_CHANGED_SINCE_SEAL: '+t.module);
const reps=Number(repsArg??3);
function prepared(t){
 const original=fs.readFileSync(path.join(root,t.module),'utf8');
 const faulted=applyEdit(original,t.fault);
 if(sha(faulted)!==t.faultedSha256)throw Error('FAULT_NOT_REPRODUCED '+t.id);
 const cands=candidatesInWindow(tokenize(faulted),faulted,t.window[0],t.window[1]);
 if(!cands.some(c=>c.id===t.correctCandidate))throw Error('CORRECT_CANDIDATE_MISSING '+t.id);
 return {original,faulted,cands};
}
const P=new Map(tasks.map(t=>[t.id,prepared(t)]));
const reports=[];
for(let rep=1;rep<=reps;rep++){
 const runner=new LabRunner({root});
 const runFor=t=>(c,subset)=>{
  const r=runner.run({testRel:t.test,moduleRel:t.module,source:applyEdit(P.get(t.id).faulted,c),names:subset==='full'?null:t.parts[subset],extra:t.extra});
  return {...r,failing:failingNames(r.log)};
 };
 // 1) experience from training: every candidate of every training task, labelled by the FULL test file
 const exp=new Experience();let trainRuns=0;
 for(const t of tasks.filter(t=>t.split==='train')){
  const run=runFor(t);
  for(const c of P.get(t.id).cands){
   const v=run(c,'visible');trainRuns++;
   const full=v.passed?run(c,'full'):{passed:false};if(v.passed)trainRuns++;
   exp.observe(t.diagnosis.kind,strategyOf(c),{visiblePass:v.passed,fullPass:full.passed});
  }
 }
 // 2) every arm on dev and holdout; accepted patches judged by the FULL test file and by exact restoration
 const rows=[];
 for(const t of tasks.filter(t=>t.split!=='train')){
  const run=runFor(t),{original,faulted,cands}=P.get(t.id);
  for(const arm of ARMS){
   const a=attempt(arm,t,cands,exp,run,{budget:VISIBLE_BUDGET});
   const acc=a.accepted;
   const full=acc?run(acc,'full'):null;
   rows.push({rep,split:t.split,task:t.id,family:t.family,kind:t.diagnosis.kind,candidates:cands.length,arm,
    accepted:!!acc,exact:!!acc&&applyEdit(faulted,acc)===original,fullPass:!!full?.passed,
    sameSite:!!acc&&acc.start===t.fault.start,visibleRuns:a.visibleRuns,verifierRuns:a.verifierRuns,
    ms:Math.round(a.ms),rssKb:a.rssKb,refinements:a.refinements,correctRank:null});
  }
 }
 reports.push({rep,rows,trainRuns,executions:runner.executions,experienceBytes:JSON.stringify(exp).length,experience:exp.toJSON()});
 log('rep '+rep+' executions='+runner.executions);
}
// summaries
const outcome=r=>[r.task,r.arm,r.accepted,r.exact,r.fullPass,r.visibleRuns,r.verifierRuns].join('|');
const deterministic=reports.every(x=>JSON.stringify(x.rows.map(outcome))===JSON.stringify(reports[0].rows.map(outcome)));
function summarize(rows){
 const s={};
 for(const arm of ARMS){
  const r=rows.filter(x=>x.arm===arm),n=r.length;
  s[arm]={tasks:n,solvedExact:r.filter(x=>x.exact).length,acceptedFullPass:r.filter(x=>x.accepted&&x.fullPass).length,
   falseSuccess:r.filter(x=>x.accepted&&!x.fullPass).length,unnecessaryEdit:r.filter(x=>x.accepted&&!x.exact).length,
   unsolved:r.filter(x=>!x.accepted).length,visibleRuns:r.reduce((a,x)=>a+x.visibleRuns,0),verifierRuns:r.reduce((a,x)=>a+x.verifierRuns,0),
   ms:r.reduce((a,x)=>a+x.ms,0),peakRssKb:Math.max(0,...r.map(x=>x.rssKb)),refinements:r.reduce((a,x)=>a+x.refinements,0)};
 }
 return s;
}
const first=reports[0].rows,hold=first.filter(r=>r.split==='holdout');
function paired(armA,armB,rows){
 const by=a=>new Map(rows.filter(r=>r.arm===a).map(r=>[r.task,r]));
 const A=by(armA),B=by(armB);let aOnly=0,bOnly=0,fewer=0,more=0;
 for(const [k,a] of A){const b=B.get(k);if(a.exact&&!b.exact)aOnly++;if(b.exact&&!a.exact)bOnly++;
  const ca=a.visibleRuns+a.verifierRuns,cb=b.visibleRuns+b.verifierRuns;if(ca<cb)fewer++;if(ca>cb)more++;}
 return {[armA+'SolvedOnly']:aOnly,[armB+'SolvedOnly']:bOnly,pSolved:binomTwoSided(Math.min(aOnly,bOnly),aOnly+bOnly),
  [armA+'FewerRuns']:fewer,[armA+'MoreRuns']:more,pRuns:binomTwoSided(Math.min(fewer,more),fewer+more)};
}
const families=[...new Set(hold.map(r=>r.family))];
const report={schema:1,sourceSha:head,seal,reps,deterministicAcrossReps:deterministic,
 executionsPerRep:reports.map(r=>r.executions),trainRunsPerRep:reports.map(r=>r.trainRuns),experienceBytes:reports[0].experienceBytes,
 dev:summarize(first.filter(r=>r.split==='dev')),holdout:summarize(hold),
 holdoutKnownFamilies:summarize(hold.filter(r=>r.family!=='ARITH')),holdoutNovelARITH:summarize(hold.filter(r=>r.family==='ARITH')),
 holdoutByFamily:Object.fromEntries(families.map(f=>[f,summarize(hold.filter(r=>r.family===f))])),
 pairedVsNone:Object.fromEntries(ARMS.filter(a=>a!=='none').map(a=>[a,paired(a,'none',hold)])),
 selfcorrectVsVerified:paired('selfcorrect','verified',hold),
 msPerRepHoldout:reports.map(x=>Object.fromEntries(ARMS.map(a=>[a,x.rows.filter(r=>r.split==='holdout'&&r.arm===a).reduce((s,r)=>s+r.ms,0)]))),
 limitations:['Faults are single-token edits injected into real project modules; repairs come from the same edit vocabulary',
  'Localization is simulated: a ±5-line window around the fault (positions are not used for ranking)',
  'No language model generates candidates (none runs in this environment)','Child processes are not an OS sandbox',
  'Timing on the cloud host, not the Fold7']};
write('report.json',report);
for(const x of reports){write('rows-rep'+x.rep+'.json',x.rows);}
write('experience-rep1.json',reports[0].experience);
console.log(JSON.stringify({deterministic,holdout:report.holdout,novel:report.holdoutNovelARITH},null,1));
