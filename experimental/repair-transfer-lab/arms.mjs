/** M9/M10 repair policies. Each arm only ORDERS candidates and decides when to accept; every test run it would make
 * is a real cached execution of the project's own test file. Experience comes only from independently verified
 * training outcomes (full test file), never from the arm's own acceptance. */
import {createHash} from 'node:crypto';
export const ARMS=Object.freeze(['none','general','context','counterexample','verified','adaptive','selfcorrect']);
const h=s=>parseInt(createHash('sha256').update(s).digest('hex').slice(0,12),16);
export const strategyOf=c=>c.family+':'+c.kind;

export class Experience {
 constructor(){this.general=new Map();this.context=new Map();this.overfit=new Map();}
 cell(map,key){if(!map.has(key))map.set(key,{s:0,n:0});return map.get(key);}
 /** one executed training candidate: success = passes the FULL test file (independent of the visible subset) */
 observe(kind,strategy,{visiblePass,fullPass}){
  for(const c of [this.cell(this.general,strategy),this.cell(this.context,kind+'|'+strategy)]){c.n++;if(visiblePass&&fullPass)c.s++;}
  if(visiblePass){const o=this.cell(this.overfit,kind+'|'+strategy);o.n++;if(!fullPass)o.s++;}
 }
 rate(map,key){const c=map.get(key);return c?(c.s+1)/(c.n+2):0.5;}
 support(kind){let n=0;for(const [k,c] of this.context)if(k.startsWith(kind+'|'))n+=c.n;return n;}
 overfitRisk(kind,strategy){const o=this.overfit.get(kind+'|'+strategy);return o?{risk:(o.s+1)/(o.n+2),support:o.n}:{risk:0.5,support:0};}
 toJSON(){return {general:Object.fromEntries(this.general),context:Object.fromEntries(this.context),overfit:Object.fromEntries(this.overfit)};}
}

export function order(arm,task,cands,exp){
 const kind=task.diagnosis.kind,tie=c=>h(task.id+'|'+c.id);
 const score=c=>{
  const st=strategyOf(c);
  if(arm==='none')return 0;
  if(arm==='general')return exp.rate(exp.general,st);
  if(arm==='adaptive'||arm==='selfcorrect')return exp.support(kind)>=8?exp.rate(exp.context,kind+'|'+st):exp.rate(exp.general,st);
  return exp.rate(exp.context,kind+'|'+st);      // context, counterexample, verified
 };
 return [...cands].map(c=>({c,s:score(c),t:tie(c)})).sort((a,b)=>b.s-a.s||a.t-b.t).map(x=>x.c);
}

/** Runs one arm on one task. run(cand, subset) -> {passed, ms, rssKb, failing:[names]}; subset: 'visible'|'verifier' */
export function attempt(arm,task,cands,exp,run,{budget=10}={}){
 let queue=order(arm,task,cands,exp);
 const out={arm,task:task.id,visibleRuns:0,verifierRuns:0,ms:0,rssKb:0,accepted:null,refinements:0,tried:[]};
 const charge=r=>{out.ms+=r.ms;out.rssKb=Math.max(out.rssKb,r.rssKb??0);};
 const base=new Set(task.visibleFailing);
 const bonus=new Map();
 while(queue.length&&out.visibleRuns<budget){
  const c=queue.shift();
  if(out.tried.includes(c.id))throw Error('REPEATED_CANDIDATE');
  out.tried.push(c.id);
  const v=run(c,'visible');out.visibleRuns++;charge(v);
  if(v.passed){
   const needsCheck=arm==='verified'||arm==='adaptive'||arm==='selfcorrect'||
    (arm==='counterexample'&&(()=>{const r=exp.overfitRisk(task.diagnosis.kind,strategyOf(c));return r.risk>=0.2||r.support<3;})());
   if(needsCheck){const w=run(c,'verifier');out.verifierRuns++;charge(w);if(!w.passed)continue;}
   out.accepted=c;break;
  }
  if(arm==='selfcorrect'){
   // Diagnosis update from the failed attempt: did this edit change WHICH visible tests fail?
   const now=new Set(v.failing),changed=now.size!==base.size||[...now].some(x=>!base.has(x));
   const delta=changed?+0.5:-0.25;
   for(const q of queue)if(q.line===c.line)bonus.set(q.id,(bonus.get(q.id)??0)+delta);
   const before=queue[0]?.id;
   const pos=new Map(queue.map((q,i)=>[q.id,i]));
   queue.sort((a,b)=>(bonus.get(b.id)??0)-(bonus.get(a.id)??0)||pos.get(a.id)-pos.get(b.id));
   if(queue[0]?.id!==before)out.refinements++;
  }
 }
 return out;
}

/** exact two-sided binomial p for k successes out of n at 1/2 (sign test / McNemar on discordant pairs) */
export function binomTwoSided(k,n){
 if(n===0)return 1;
 const lg=x=>{let s=0;for(let i=2;i<=x;i++)s+=Math.log(i);return s;};
 const p=i=>Math.exp(lg(n)-lg(i)-lg(n-i)-n*Math.log(2));
 const obs=p(k);let s=0;for(let i=0;i<=n;i++){const q=p(i);if(q<=obs*(1+1e-9))s+=q;}
 return Math.min(1,s);
}
