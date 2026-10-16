/**
 * CRE1 order-sensitive shadow features. No mutation of CRE1 file format,
 * weights, production retrieval, or Android inference. This is intentionally
 * a feature ABI experiment: existing CRE1 checkpoints were trained with a
 * different input feature map and MUST NOT be silently reinterpreted.
 */
import {D,RelationalEncoder,FUNCTION_WORDS,bucket,features,tokens} from './cre-encoder.mjs';

export const ORDER_SHADOW_VERSION=1;
export const ORDER_SHADOW_ID='CRE1_ORDER_SHADOW_V1';
const WEIGHTS=Object.freeze({base:0.70,bigrams:0.20,first:0.05,last:0.05});

/**
 * Preserve 70% of the existing CRE1 lexical features; allocate at most 20%
 * to directed adjacent content-word pairs and 10% to positional hints.
 * Position is NOT grammatical subject/object inference.
 */
export function orderShadowFeatures(text){
 const base=features(text);
 const terms=tokens(text).filter(t=>!FUNCTION_WORDS.has(t));
 const acc=new Map();
 const add=(id,weight)=>acc.set(id,(acc.get(id)??0)+weight);
 for(const {id,w} of base)add(id,w*WEIGHTS.base);
 if(terms.length>1){
  const per=WEIGHTS.bigrams/(terms.length-1);
  for(let i=0;i<terms.length-1;i++)
   add(bucket('order-b:'+terms[i]+'>'+terms[i+1]),per);
  add(bucket('order-first:'+terms[0]),WEIGHTS.first);
  add(bucket('order-last:'+terms.at(-1)),WEIGHTS.last);
 }else{
  // No order is observable with zero or one content word.
  for(const {id,w} of base)add(id,w*(1-WEIGHTS.base));
 }
 const total=[...acc.values()].reduce((sum,x)=>sum+x,0);
 if(!Number.isFinite(total)||total<=0)throw Error('Invalid order feature mass');
 return [...acc.entries()].sort((a,b)=>a[0]-b[0])
  .map(([id,w])=>({id,w:w/total}));
}

export function orderShadowEmbed(model,text,side='m'){
 if(!(model instanceof RelationalEncoder)||!['q','m'].includes(side))
  throw Error('CRE1 model and q/m side required');
 const h=model.pool(orderShadowFeatures(text));
 const z=model.project(side==='q'?model.Pq:model.Pm,h);
 return RelationalEncoder.normalise(z).e;
}

export function cosine(a,b){
 if(a.length!==D||b.length!==D)throw Error('CRE1 vector dimension required');
 let sum=0;
 for(let i=0;i<D;i++)sum+=a[i]*b[i];
 return sum;
}

/**
 * Structural audit only. Do not interpret this as downstream accuracy:
 * no order-sensitive model is trained or promoted here.
 */
export function orderShadowAudit(model,left,right){
 const baselineA=model.embed(left,'m'),baselineB=model.embed(right,'m');
 const shadowA=orderShadowEmbed(model,left,'m'),shadowB=orderShadowEmbed(model,right,'m');
 const baselineDistance=Math.hypot(...baselineA.map((v,i)=>v-baselineB[i]));
 const shadowDistance=Math.hypot(...shadowA.map((v,i)=>v-shadowB[i]));
 return {schema:ORDER_SHADOW_ID,version:ORDER_SHADOW_VERSION,
  baselineDistance,shadowDistance,
  baselineCosine:cosine(baselineA,baselineB),
  shadowCosine:cosine(shadowA,shadowB),
  productionEnabled:false,checkpointCompatibleForInference:false};
}
