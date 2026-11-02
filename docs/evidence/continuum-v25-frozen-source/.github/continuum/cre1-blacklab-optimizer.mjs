/**
 * Mathematically bounded research-only CRE1 projection optimization.
 * Cache exactly the pooled frozen-E vectors. No changes to the CRE1 wire format.
 */
import {D,RelationalEncoder,features} from './cre-encoder.mjs';

export function createFrozenPool(model,examples){
  if(!Array.isArray(examples)||examples.length>512)throw Error('Frozen pool example budget exceeded');
  const cache=new Map();
  const get=text=>{
    if(typeof text!=='string'||!text||text.length>2048)throw Error('Invalid frozen pool text');
    if(!cache.has(text)){
      if(cache.size>=2048)throw Error('Frozen pool text budget exceeded');
      cache.set(text,model.pool(features(text)));
    }
    return cache.get(text);
  };
  for(const example of examples){
    get(example.query);
    for(const value of example.candidates)get(value);
  }
  return {size:cache.size,get};
}

function forward(model,text,side,pool){
  const h=pool.get(text);
  const P=side==='q'?model.Pq:model.Pm;
  const {e,n}=RelationalEncoder.normalise(model.project(P,h));
  return {h,e,n};
}
function scoreVec(q,m){
  let total=0;
  for(let i=0;i<D;i++)total+=q.e[i]*m.e[i];
  return total;
}
function backwardProjection(fw,derivative,grad){
  let dot=0;
  for(let i=0;i<D;i++)dot+=derivative[i]*fw.e[i];
  for(let r=0;r<D;r++){
    const dz=(derivative[r]-dot*fw.e[r])/fw.n;
    for(let c=0;c<D;c++)grad[r*D+c]+=dz*fw.h[c];
  }
}
function step(model,example,pool,tau,grads=null){
  const q=forward(model,example.query,'q',pool);
  const ms=example.candidates.map(text=>forward(model,text,'m',pool));
  const logits=ms.map(m=>scoreVec(q,m)/tau);
  const max=Math.max(...logits);
  const ex=logits.map(x=>Math.exp(x-max));
  const total=ex.reduce((a,b)=>a+b,0);
  const loss=Math.log(total)+max-logits[0];
  if(!Number.isFinite(loss))throw Error('Unstable CRE1 projection loss');
  if(!grads)return {loss,scores:logits};
  const dQ=new Float64Array(D);
  for(let j=0;j<ms.length;j++){
    const g=(ex[j]/total-(j===0?1:0))/tau;
    const dM=new Float64Array(D);
    for(let i=0;i<D;i++){
      dQ[i]+=g*ms[j].e[i];
      dM[i]=g*q.e[i];
    }
    backwardProjection(ms[j],dM,grads.Pm);
  }
  backwardProjection(q,dQ,grads.Pq);
  return {loss,scores:logits};
}

export function projectionLossAndGrad(model,example,pool,tau=0.12,grads=null){
  if(!Number.isFinite(tau)||tau<0.05||tau>1||
     !Array.isArray(example?.candidates)||example.candidates.length<2||
     example.candidates.length>12||new Set(example.candidates).size!==example.candidates.length)
    throw Error('Invalid projection learning example');
  return step(model,example,pool,tau,grads).loss;
}

export function evaluatePrePooledSplit(model,examples,pool,tau=0.12){
  if(!examples.length)return {correct:0,total:0,mrr:0,loss:null};
  let correct=0,mrr=0,loss=0;
  for(const example of examples){
    const result=step(model,example,pool,tau);
    loss+=result.loss;
    const ranking=result.scores.map((score,i)=>({score,i}))
      .sort((a,b)=>b.score-a.score||a.i-b.i);
    const rank=ranking.findIndex(x=>x.i===0)+1;
    if(rank===1)correct++;
    mrr+=1/rank;
  }
  return {correct,total:examples.length,mrr:mrr/examples.length,loss:loss/examples.length};
}

/** Gradient of (gamma/4) ||P^T P - I||_F^2 with respect to P. */
export function addGramIsometryPenalty(P,gradP,gamma=0.02){
  if(!Number.isFinite(gamma)||gamma<0||gamma>0.05||
     P.length!==D*D||gradP.length!==D*D)
    throw Error('Invalid isometry regularization');
  if(gamma===0)return;
  const gram=new Float64Array(D*D);
  for(let i=0;i<D;i++)for(let j=0;j<D;j++){
    let dot=0;
    for(let r=0;r<D;r++)dot+=P[r*D+i]*P[r*D+j];
    gram[i*D+j]=dot-(i===j?1:0);
  }
  for(let r=0;r<D;r++)for(let c=0;c<D;c++){
    let extra=0;
    for(let k=0;k<D;k++)extra+=P[r*D+k]*gram[k*D+c];
    gradP[r*D+c]+=gamma*extra;
  }
}

export function gramDeviation(P){
  if(P.length!==D*D)throw Error('Invalid CRE1 projection shape');
  let total=0;
  for(let i=0;i<D;i++)for(let j=0;j<D;j++){
    let dot=0;
    for(let r=0;r<D;r++)dot+=P[r*D+i]*P[r*D+j];
    const error=dot-(i===j?1:0);
    total+=error*error;
  }
  return Math.sqrt(total);
}

/** Experimental rank-one mean-direction removal, deliberately opt-in.
 * This linear rank-one correction is NOT exact centering and no benefit is assumed.
 */
export function absorbAnisotropyProjector(P,pooledVectors,beta=0){
  if(!Number.isFinite(beta)||beta<0||beta>0.75)throw Error('Invalid anisotropy beta');
  if(beta===0)return;
  if(!Array.isArray(pooledVectors)||!pooledVectors.length||P.length!==D*D)
    throw Error('Invalid pooled corpus');
  const mean=new Float64Array(D);
  for(const h of pooledVectors){
    if(h.length!==D)throw Error('Invalid pooled vector');
    for(let k=0;k<D;k++)mean[k]+=h[k];
  }
  let norm=0;
  for(let k=0;k<D;k++){mean[k]/=pooledVectors.length;norm+=mean[k]*mean[k];}
  norm=Math.sqrt(norm);
  if(!(norm>1e-9))return;
  for(let k=0;k<D;k++)mean[k]/=norm;
  for(let r=0;r<D;r++){
    let dot=0;
    for(let k=0;k<D;k++)dot+=P[r*D+k]*mean[k];
    for(let c=0;c<D;c++)P[r*D+c]-=beta*dot*mean[c];
  }
}

export function canaryVariants(text){
  const originals=new Set();
  const number=/\b\d{1,8}\b/u.exec(text);
  if(number){
    const newNumber=String(Number(number[0])+1);
    originals.add(text.slice(0,number.index)+newNumber+text.slice(number.index+number[0].length));
  }
  const negation=/\b(nicht|kein|keine|keinen|niemals|never|not|without)\b/iu.exec(text);
  if(negation){
    const replacement=negation[0].toLocaleLowerCase('de-DE')==='nicht'?'': 'nicht';
    originals.add((text.slice(0,negation.index)+replacement+text.slice(negation.index+negation[0].length))
      .replace(/\s+/g,' ').trim());
  }
  originals.delete(text);
  return [...originals].filter(x=>x.length>=8).slice(0,2);
}

/** Audit-only: synthetic canaries must never enter the optimizer's training pool. */
export function canaryAudit(model,auditExamples,pool){
  let tested=0,violations=0;
  for(const row of auditExamples){
    const q=forward(model,row.query,'q',pool);
    const positive=scoreVec(q,forward(model,row.candidates[0],'m',pool));
    for(const canary of canaryVariants(row.candidates[0])){
      tested++;
      const h=model.pool(features(canary));
      const {e}=RelationalEncoder.normalise(model.project(model.Pm,h));
      let score=0;
      for(let i=0;i<D;i++)score+=q.e[i]*e[i];
      if(positive<=score+1e-12)violations++;
    }
  }
  return {tested,violations};
}

export function trainPrePooledProjectionHeads(baselineBytes,examples,{
  epochs=4,learningRate=0.001,tau=0.12,isoGamma=0.02,
  anchorLambda=0.1,anisotropyBeta=0,deadlineAt=null
}={}){
  if(!Number.isInteger(epochs)||epochs<1||epochs>20||
     !Number.isFinite(learningRate)||learningRate<=0||learningRate>0.01||
     !Number.isFinite(tau)||tau<0.05||tau>1||
     !Number.isFinite(isoGamma)||isoGamma<0||isoGamma>0.05||
     !Number.isFinite(anchorLambda)||anchorLambda<0||anchorLambda>1||
     !Number.isFinite(anisotropyBeta)||anisotropyBeta<0||anisotropyBeta>0.75||
     (deadlineAt!==null&&(!Number.isSafeInteger(deadlineAt)||deadlineAt<Date.now())))
    throw Error('CRE1 shadow optimizer parameters invalid');
  if(!Array.isArray(examples)||examples.length<3||examples.length>384)
    throw Error('CRE1 training budget invalid');
  const model=RelationalEncoder.restore(baselineBytes);
  const pool=createFrozenPool(model,examples);
  const baselineE=Buffer.from(model.E.buffer,model.E.byteOffset,model.E.byteLength);
  const frozenCopy=Buffer.from(baselineE);
  const anchorQ=Float64Array.from(model.Pq),anchorM=Float64Array.from(model.Pm);
  if(anisotropyBeta>0){
    absorbAnisotropyProjector(model.Pq,examples.map(x=>pool.get(x.query)),anisotropyBeta);
    absorbAnisotropyProjector(model.Pm,
      examples.flatMap(x=>x.candidates.map(c=>pool.get(c))),anisotropyBeta);
  }
  for(let epoch=0;epoch<epochs;epoch++){
    for(const example of examples){
      if(deadlineAt!==null&&Date.now()>deadlineAt)throw Error('CRE1 shadow cycle deadline exceeded');
      const grads=RelationalEncoder.emptyGrads();
      projectionLossAndGrad(model,example,pool,tau,grads);
      addGramIsometryPenalty(model.Pq,grads.Pq,isoGamma);
      addGramIsometryPenalty(model.Pm,grads.Pm,isoGamma);
      for(let k=0;k<D*D;k++){
        grads.Pq[k]+=anchorLambda*(model.Pq[k]-anchorQ[k]);
        grads.Pm[k]+=anchorLambda*(model.Pm[k]-anchorM[k]);
      }
      model.apply(grads,learningRate);
    }
  }
  if(!baselineE.equals(frozenCopy))throw Error('Frozen CRE1 embeddings changed');
  return {model,poolSize:pool.size,
    gramPq:gramDeviation(model.Pq),gramPm:gramDeviation(model.Pm)};
}
