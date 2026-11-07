#!/usr/bin/env node
// NF-5b failure analysis, POST HOC (not pre-registered): for every task, which candidates meet all of the goal's
// examples in the behaviour probe, i.e. how many functions the examples leave open. Runs the same sandboxed probe as
// the study on a temporary copy of the study modules; nothing in the working tree is executed.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {TASKS,MODULES} from './nf5-ranker-study.mjs';
import {indexExports,interpretGoal} from '../.github/continuum/ccc1-interpreter.mjs';
import {probeCandidates} from '../.github/continuum/behaviour-probe.mjs';

const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const home=fs.mkdtempSync(path.join(os.tmpdir(),'nf5b-amb-'));
const root=path.join(home,'device-ai'),dir=path.join(root,'.github','continuum');
fs.mkdirSync(dir,{recursive:true});
for(const m of MODULES)fs.copyFileSync(path.join(ROOT,'.github/continuum',m+'.mjs'),path.join(dir,m+'.mjs'));
const candidates=indexExports(root);
console.log('candidates',candidates.length);
for(const t of TASKS){
 const spec=interpretGoal(t.goal);
 if(spec.arity===null||!spec.acceptance?.length){console.log(t.id.padEnd(18),'no examples');continue;}
 const usable=candidates.filter(c=>spec.arity>=c.required&&spec.arity<=c.max);
 const probe=probeCandidates({root,candidates:usable,cases:spec.acceptance});
 const full=[...probe].filter(([,p])=>!p.skipped&&p.total>0&&p.matched===p.total).map(([k])=>k.replace('.github/continuum/',''));
 const intended=t.gold===null?[]:[t.gold,...t.equivalent].map(k=>k.replace('.github/continuum/',''));
 const other=full.filter(k=>!intended.includes(k));
 console.log(t.id.padEnd(18),'examples',spec.acceptance.length,'probed',String(usable.length).padStart(3),
  'meetAll',full.length,'intendedAmongThem',full.some(k=>intended.includes(k)),'others',JSON.stringify(other));
}
fs.rmSync(home,{recursive:true,force:true});
