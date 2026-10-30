/** Builds the M9 task set from REAL project modules and their REAL test files: faults are single-token edits that the
 * module's visible tests detect and whose exact inverse is in the repair vocabulary. Deterministic for a commit. */
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {tokenize} from './jstoken.mjs';
import {editsAt,applyEdit,candidatesInWindow} from './operators.mjs';
import {topLevelNames} from './sandbox.mjs';
import {diagnoseTap} from './diagnose.mjs';
import {SPLITS,FAMILIES_BY_SPLIT,FAULTS_PER_FAMILY,MAX_FAULT_TRIES,WINDOW_LINES} from './config.mjs';
const sha=s=>createHash('sha256').update(s).digest('hex');
/** deterministic shuffle (mulberry32 seeded by a string) */
export function seededOrder(items,seedText){
 let a=parseInt(sha(seedText).slice(0,8),16)>>>0;
 const rnd=()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296;};
 const out=[...items];for(let i=out.length-1;i>0;i--){const j=Math.floor(rnd()*(i+1));[out[i],out[j]]=[out[j],out[i]];}return out;
}
export function splitNames(names){
 return {visible:names.filter((_,i)=>i%4<2),verifier:names.filter((_,i)=>i%4===2),final:names.filter((_,i)=>i%4===3)};
}
export const failingNames=log=>[...log.matchAll(/^not ok \d+ - (.*?)(?: # .*)?$/gm)].map(m=>m[1]).sort();

export function buildTasks({root,runner,log=()=>{}}){
 const tasks=[],modules=[];
 for(const [split,entries] of Object.entries(SPLITS))for(const entry of entries){
  const original=fs.readFileSync(path.join(root,entry.module),'utf8');
  const base=runner.run({testRel:entry.test,moduleRel:entry.module,source:original,extra:entry.extra});
  const names=topLevelNames(base.log);
  const info={split,...entry,originalSha256:sha(original),baselinePassed:base.passed,tests:base.tests,topLevel:names.length};
  if(!base.passed||names.length<6){modules.push({...info,excluded:'BASELINE_RED_OR_TOO_FEW_TESTS'});log('exclude '+entry.module);continue;}
  const parts=splitNames(names);
  const vis=runner.run({testRel:entry.test,moduleRel:entry.module,source:original,names:parts.visible,extra:entry.extra});
  if(!vis.passed){modules.push({...info,excluded:'VISIBLE_SUBSET_RED'});continue;}
  modules.push({...info,split:split,parts});
  const tokens=tokenize(original);
  for(const family of FAMILIES_BY_SPLIT[split]){
   const sites=[];
   tokens.forEach((_,k)=>{for(const e of editsAt(tokens,k))if(e.family===family)sites.push(e);});
   let taken=0,tries=0;
   for(const fault of seededOrder(sites,entry.module+'|'+family)){
    if(taken>=FAULTS_PER_FAMILY||tries>=MAX_FAULT_TRIES)break;
    const faulted=applyEdit(original,fault);
    let ftokens;try{ftokens=tokenize(faulted);}catch{continue;}
    const line=fault.line??tokens[fault.at].line;
    const cands=candidatesInWindow(ftokens,faulted,line-WINDOW_LINES,line+WINDOW_LINES);
    const inverse=cands.find(c=>applyEdit(faulted,c)===original);
    if(!inverse)continue;                       // outside the repair vocabulary: not a task
    tries++;
    const r=runner.run({testRel:entry.test,moduleRel:entry.module,source:faulted,names:parts.visible,extra:entry.extra});
    if(r.passed||r.status==='TIMEOUT'||!(r.tests>0))continue;   // undetected by visible tests, timeout or load failure
    taken++;
    tasks.push({id:split+'-'+path.basename(entry.module,'.mjs')+'-'+family.toLowerCase()+'-'+taken,split,family,
     test:entry.test,module:entry.module,extra:entry.extra??[],originalSha256:info.originalSha256,
     fault:{start:fault.start,end:fault.end,text:fault.text,from:fault.from,kind:fault.kind,line},
     faultedSha256:sha(faulted),window:[line-WINDOW_LINES,line+WINDOW_LINES],candidates:cands.length,
     correctCandidate:inverse.id,parts,diagnosis:diagnoseTap(r.log),visibleFailing:failingNames(r.log)});
    log(tasks.at(-1).id+' candidates='+cands.length+' diag='+tasks.at(-1).diagnosis.kind);
   }
  }
 }
 return {tasks,modules};
}
/** Seal over everything that defines the experiment, nothing measured. */
export function sealTasks(tasks){
 const canon=tasks.map(t=>({id:t.id,split:t.split,family:t.family,test:t.test,module:t.module,originalSha256:t.originalSha256,
  fault:t.fault,faultedSha256:t.faultedSha256,window:t.window,correctCandidate:t.correctCandidate,parts:t.parts}));
 return sha(JSON.stringify(canon));
}
