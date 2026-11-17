// Opt-in SWB2 Adam experiment. This changes ONLY experimental weights; SWB1 and
// Kotlin SGD / parity benchmarks remain untouched. Optimizer state is local.
import {SubwordEncoder} from './portable-subword.mjs';

const D=16,SCALE=2.5,CLIP=8;
const sigmoid=x=>x>=0?1/(1+Math.exp(-x)):Math.exp(x)/(1+Math.exp(x));

export function trainSwb2Adaptive(baseline,examples,{
 steps=1500,rate=0.02,beta1=0.9,beta2=0.999,epsilon=1e-8,
 cancelled=()=>false
}={}) {
 if(!(baseline instanceof SubwordEncoder)||baseline.version!==2)throw Error('SWB2 encoder required');
 if(!Array.isArray(examples)||examples.length<2||examples.length>128||
  !examples.every(e=>typeof e.left==='string'&&typeof e.right==='string'&&typeof e.similar==='boolean'))
  throw Error('Bounded labeled pairs required');
 if(!Number.isInteger(steps)||steps<1||steps>10000||
  !Number.isFinite(rate)||rate<=0||rate>0.1)throw Error('Invalid adaptive optimizer budget');
 const candidate=SubwordEncoder.restore(baseline.save());
 const moment=new Float64Array(candidate.embeddings.length);
 const variance=new Float64Array(candidate.embeddings.length);
 const counts=new Uint32Array(candidate.embeddings.length);
 let completed=0;
 for(let t=0;t<steps;t++){
  if(cancelled())break;
  const ex=examples[t%examples.length];
  const ai=candidate.features(ex.left),bi=candidate.features(ex.right);
  const a=candidate.encodeBuckets(ai),b=candidate.encodeBuckets(bi);
  const dot=a.reduce((sum,v,i)=>sum+v*b[i],0);
  const error=sigmoid(SCALE*dot)-(ex.similar?1:0);
  const gradients=new Map();
  const add=(ids,other,self)=>{
   for(const {id,weight} of ids)for(let i=0;i<D;i++){
    const index=id*D+i;
    const grad=error*SCALE*other[i]*(1-self[i]*self[i])*weight;
    gradients.set(index,(gradients.get(index)??0)+grad);
   }
  };
  add(ai,b,a);add(bi,a,b);
  // Sparse Adam with per-parameter bias correction. Overlapping buckets are
  // aggregated before a single update, unlike naive in-place pair updates.
  for(const [index,g] of gradients){
   const n=++counts[index];
   moment[index]=beta1*moment[index]+(1-beta1)*g;
   variance[index]=beta2*variance[index]+(1-beta2)*g*g;
   const mm=moment[index]/(1-Math.pow(beta1,n));
   const vv=variance[index]/(1-Math.pow(beta2,n));
   const next=candidate.embeddings[index]-rate*mm/(Math.sqrt(vv)+epsilon);
   candidate.embeddings[index]=Math.max(-CLIP,Math.min(CLIP,next));
  }
  candidate.updates=Math.min(1000000,candidate.updates+1);
  completed++;
 }
 return {candidate,steps:completed,interrupted:completed!==steps,
  before:baseline.evaluate(examples),after:candidate.evaluate(examples)};
}
