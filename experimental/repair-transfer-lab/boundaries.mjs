/** M13 · counterexample mining over REAL test runs: where does a repair strategy work, where does it fail?
 * Hypotheses are generated automatically from the training matrix and stored SEPARATELY from experience
 * (status HYPOTHESIS). Only an independent re-measurement on other modules (dev split) may turn one into a
 * CONFIRMED research finding; nothing here changes CCC1 ranking, ledger, worker or permissions. Pure functions. */
import {binomTwoSided} from './arms.mjs';

/** rows: one per executed candidate {task, kind, bucket, strategy, success} */
export function cells(rows){
 const by=new Map(),base=new Map();
 for(const r of rows){
  const k=r.kind+'|'+r.strategy;
  const c=by.get(k)??{kind:r.kind,strategy:r.strategy,n:0,s:0,tasks:new Set(),hits:new Set()};
  c.n++;if(r.success){c.s++;c.hits.add(r.task);}c.tasks.add(r.task);by.set(k,c);
  const b=base.get(r.strategy)??{n:0,s:0};b.n++;if(r.success)b.s++;base.set(r.strategy,b);
 }
 return {cells:[...by.values()],base};
}

/** H+ : the strategy is at least twice as often right under this diagnosis; H- : at most half as often. */
export function generateHypotheses(rows,{minN=8,minSuccess=3}={}){
 const {cells:cs,base}=cells(rows),out=[];
 for(const c of cs){
  const b=base.get(c.strategy),pBase=b.s/b.n,p=c.s/c.n;
  if(c.n<minN||b.n<minN)continue;
  let direction=null;
  if(c.s>=minSuccess&&p>=2*pBase)direction='PREFER';
  else if(pBase>0&&p<=pBase/2&&b.s>=minSuccess)direction='DEMOTE';
  if(!direction)continue;
  out.push({id:'H-'+direction+'-'+c.kind+'-'+c.strategy,direction,kind:c.kind,strategy:c.strategy,status:'HYPOTHESIS',
   training:{n:c.n,successes:c.s,rate:+p.toFixed(4),baseRate:+pBase.toFixed(4)},
   // counterexamples: training tasks under this diagnosis where the claim did not hold
   counterexamples:direction==='PREFER'?[...c.tasks].filter(t=>!c.hits.has(t)).sort():[...c.hits].sort()});
 }
 return out.sort((a,b)=>a.id.localeCompare(b.id));
}

/** exact one-sided binomial P(X >= k) for n trials at probability p */
export function binomUpper(k,n,p){
 if(n===0)return 1;
 const lg=x=>{let s=0;for(let i=2;i<=x;i++)s+=Math.log(i);return s;};
 let s=0;for(let i=k;i<=n;i++)s+=Math.exp(lg(n)-lg(i)-lg(n-i)+i*Math.log(Math.max(p,1e-12))+(n-i)*Math.log(Math.max(1-p,1e-12)));
 return Math.min(1,s);
}

/** Independent re-measurement on other code: CONFIRMED only if the effect holds at p < alpha against the dev base rate. */
export function testHypotheses(hypotheses,devRows,{alpha=0.05,minN=6}={}){
 const {cells:cs,base}=cells(devRows);
 return hypotheses.map(h=>{
  const c=cs.find(x=>x.kind===h.kind&&x.strategy===h.strategy),b=base.get(h.strategy);
  if(!c||!b||c.n<minN)return {...h,status:'INCONCLUSIVE',dev:{n:c?.n??0,successes:c?.s??0,reason:'TOO_FEW_DEV_OBSERVATIONS'}};
  const pBase=b.s/b.n;
  const p=h.direction==='PREFER'?binomUpper(c.s,c.n,pBase):binomUpper(c.n-c.s,c.n,1-pBase);
  const status=p<alpha?'CONFIRMED':(h.direction==='PREFER'?c.s/c.n<=pBase:c.s/c.n>=pBase)?'REFUTED':'INCONCLUSIVE';
  return {...h,status,dev:{n:c.n,successes:c.s,rate:+(c.s/c.n).toFixed(4),baseRate:+pBase.toFixed(4),pOneSided:+p.toFixed(6)}};
 });
}

/** For a hypothesis that failed: which observable feature separates successes from failures inside its cell? */
export function separatingFeature(h,rows){
 const r=rows.filter(x=>x.kind===h.kind&&x.strategy===h.strategy);
 const best={feature:null,gap:0};
 for(const feature of ['bucket','module']){   // observable only: never the injected fault family
  const groups=new Map();
  for(const x of r){const g=groups.get(x[feature])??{n:0,s:0};g.n++;if(x.success)g.s++;groups.set(x[feature],g);}
  const rates=[...groups.entries()].filter(([,g])=>g.n>=3).map(([v,g])=>({value:v,rate:g.s/g.n,n:g.n}));
  if(rates.length<2)continue;
  rates.sort((a,b)=>b.rate-a.rate);
  const gap=rates[0].rate-rates.at(-1).rate;
  if(gap>best.gap)Object.assign(best,{feature,gap:+gap.toFixed(4),high:rates[0],low:rates.at(-1)});
 }
 return best.feature?best:null;
}
export {binomTwoSided};
