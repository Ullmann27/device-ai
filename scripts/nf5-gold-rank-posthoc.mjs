#!/usr/bin/env node
// NF-5 failure analysis, POST HOC (not pre-registered): position of the intended function among the candidates the
// gate would allow for each task, under the lexical ranker and the documentation-association ranker. Ranking only;
// no candidate function is imported or executed.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {TASKS,MODULES,docsTexts} from './nf5-ranker-study.mjs';
import {indexExports,interpretGoal,LexicalLanguageCore} from '../.github/continuum/ccc1-interpreter.mjs';
import {capabilityCheck} from '../.github/continuum/ccc1-gate.mjs';
import {AssocLanguageCore} from '../.github/continuum/assoc-language-core.mjs';

const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const home=fs.mkdtempSync(path.join(os.tmpdir(),'nf5-rank-'));
const dir=path.join(home,'device-ai','.github','continuum');
fs.mkdirSync(dir,{recursive:true});
for(const m of MODULES)fs.copyFileSync(path.join(ROOT,'.github/continuum',m+'.mjs'),path.join(dir,m+'.mjs'));
const candidates=indexExports(path.join(home,'device-ai'));
const docs=new AssocLanguageCore();docs.learnFromDocs(docsTexts());
const lexical=new LexicalLanguageCore();
console.log('candidates',candidates.length);
for(const t of TASKS.filter(t=>t.gold)){
 const spec=interpretGoal(t.goal);
 const allowed=candidates.map((c,i)=>({c,i})).filter(({c})=>spec.arity>=c.required&&spec.arity<=c.max&&
  capabilityCheck(c.module,c.export,JSON.stringify(spec.acceptance)).allowed);
 const rankOf=async core=>{
  const s=await core.rank(spec.goal,candidates);
  const order=allowed.slice().sort((a,b)=>s[b.i]-s[a.i]||a.i-b.i);
  const k=order.findIndex(({c})=>c.module+'#'+c.export===t.gold);
  return k<0?'n/a':k+1;
 };
 console.log(t.id.padEnd(16),'testable',String(allowed.length).padStart(3),'lexical',String(await rankOf(lexical)).padStart(4),
  'docs',String(await rankOf(docs)).padStart(4));
}
fs.rmSync(home,{recursive:true,force:true});
