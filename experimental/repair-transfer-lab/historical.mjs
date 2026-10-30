/** M9/M10 reality check: the 5 small REAL historical fixes of .github/continuum (fix commit with its own test).
 * For each: a throwaway worktree at the fix commit, the source file reset to its parent version (the real bug),
 * the fix's own test files must be red; then the self-correcting agent searches the same edit vocabulary in a
 * +-5-line window around the lines the real fix changed (budget 10). Success = the fix's tests pass; exact =
 * identical to the real fixed file. Usage: node historical.mjs <new-abs-out-dir> */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {LabRunner} from './sandbox.mjs';
import {tokenize} from './jstoken.mjs';
import {candidatesInWindow,applyEdit} from './operators.mjs';
import {Experience,attempt} from './arms.mjs';
import {diagnoseTap} from './diagnose.mjs';
import {failingNames} from './tasks.mjs';
const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const out=process.argv[2];
if(!path.isAbsolute(out??'')||fs.existsSync(out)||out.startsWith(repo+path.sep))throw Error('NEW_ABSOLUTE_OUT_DIR_OUTSIDE_REPO');
const COMMITS=['4c697c8f04fbb5b0477ae5e48b3d052cce383d34','81ccc374bcec6c1dcd08decde033777e3342fea9',
 '3e8e0ef7341fe1886f160c53b1949a7f1ac763c7','0fc7a3e9ac4af67fbe9dd50a489fb7aec5715823','ed6a04234b69948ea9636ac7e68bff35d2790c2a'];
const git=(cwd,...a)=>{const r=spawnSync('git',a,{cwd,encoding:'utf8',maxBuffer:50_000_000});if(r.status!==0)throw Error('GIT '+a.join(' ')+': '+r.stderr);return r.stdout;};
fs.mkdirSync(out,{recursive:true,mode:0o700});
const results=[];
for(const sha of COMMITS){
 const wt=fs.mkdtempSync(path.join(os.tmpdir(),'hist-'));fs.rmSync(wt,{recursive:true});
 git(repo,'worktree','add','-q','--detach',wt,sha);
 try{
  const files=git(repo,'show','--numstat','--format=',sha).trim().split('\n').map(l=>l.split('\t')[2]);
  const source=files.find(f=>f.startsWith('.github/continuum/')&&f.endsWith('.mjs')&&!f.endsWith('.test.mjs'));
  const tests=files.filter(f=>f.endsWith('.test.mjs'));
  const fixed=fs.readFileSync(path.join(wt,source),'utf8'),buggy=git(repo,'show',sha+'^:'+source);
  // lines the real fix touched, in parent numbering
  const hunks=[...git(repo,'show','-U0','--format=',sha,'--',source).matchAll(/^@@ -(\d+)(?:,(\d+))?/gm)].map(m=>[Number(m[1]),Number(m[1])+Math.max(0,Number(m[2]??1)-1)]);
  const lo=Math.min(...hunks.map(h=>h[0]))-5,hi=Math.max(...hunks.map(h=>h[1]))+5;
  const runner=new LabRunner({root:wt,timeoutMs:20000});
  const extra=fs.existsSync(path.join(wt,'.github/continuum/fixtures'))?['.github/continuum/fixtures']:[];
  const runAll=src=>tests.map(t=>runner.run({testRel:t,moduleRel:source,source:src,extra}));
  const before=runAll(buggy),afterFix=runAll(fixed);
  const red=before.some(r=>!r.passed),fixGreen=afterFix.every(r=>r.passed);
  const row={commit:sha.slice(0,8),source,tests,window:[lo,hi],bugReproduced:red,fixGreenHere:fixGreen};
  if(red&&fixGreen){
   const cands=candidatesInWindow(tokenize(buggy),buggy,lo,hi);
   const log=before.map(r=>r.log).join('\n');
   const task={id:'hist-'+sha.slice(0,8),diagnosis:diagnoseTap(log),visibleFailing:failingNames(log)};
   const run=c=>{const rs=runAll(applyEdit(buggy,c));return {passed:rs.every(r=>r.passed),ms:rs.reduce((a,r)=>a+r.ms,0),rssKb:0,
    failing:rs.flatMap(r=>failingNames(r.log))};};
   const a=attempt('selfcorrect',task,cands,new Experience(),(c,subset)=>subset==='visible'?run(c):{passed:true,ms:0,rssKb:0,failing:[]},{budget:10});
   const anyRepair=cands.some(c=>runAll(applyEdit(buggy,c)).every(r=>r.passed));
   Object.assign(row,{candidates:cands.length,diagnosis:task.diagnosis.kind,agentRepaired:!!a.accepted,
    agentExact:!!a.accepted&&applyEdit(buggy,a.accepted)===fixed,visibleRuns:a.visibleRuns,
    anyCandidateInVocabularyRepairs:anyRepair,fixWithinVocabulary:cands.some(c=>applyEdit(buggy,c)===fixed)});
  }
  results.push(row);
  console.error(JSON.stringify(row));
 }finally{git(repo,'worktree','remove','--force',wt);}
}
const summary={schema:1,cohort:'HISTORICAL_REAL_FIXES',commits:results.length,
 bugsReproduced:results.filter(r=>r.bugReproduced&&r.fixGreenHere).length,
 agentRepaired:results.filter(r=>r.agentRepaired).length,
 fixWithinVocabulary:results.filter(r=>r.fixWithinVocabulary).length,
 anyCandidateRepairs:results.filter(r=>r.anyCandidateInVocabularyRepairs).length,results};
fs.writeFileSync(path.join(out,'historical.json'),JSON.stringify(summary,null,1)+'\n');
console.log(JSON.stringify(summary,null,1));
