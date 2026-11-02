/**
 * Research-only neural ranker based on Argon's NTW1-v3 equations.
 * NO execution of candidate code, no I/O, no tools, no owner-memory access.
 * It is NOT a verified-CCC1 receipt parser or safe code runner.
 * All updates are simulated labelled research observations until an independent
 * external verifier adapter and sealed holdout are implemented and validated.
 */
const SD=16,HD=12,TD=8,MAX_OP=12;
const finiteVector=(v,n)=>v instanceof Float64Array&&v.length===n&&v.every(Number.isFinite);
// Numerical admission cap, not a semantic feature-normalization schema.
const MAX_STATE_ABS=1_000_000;
const sigmoid=x=>1/(1+Math.exp(-Math.max(-20,Math.min(20,x))));
export class CoinWmV3ShadowModel{
  constructor(maxOperators=5,seed=20261026){
    if(!Number.isSafeInteger(maxOperators)||maxOperators<1||maxOperators>MAX_OP)throw Error('INVALID_OPERATOR_COUNT');
    Object.defineProperty(this,'status',{value:'RESEARCH_SHADOW',writable:false,configurable:false});
    Object.defineProperty(this,'promotionEligible',{value:false,writable:false,configurable:false});
    this.maxOperators=maxOperators;
    let s=seed>>>0;
    const rand=()=>{s=(Math.imul(s,1664525)+1013904223)>>>0;return (s/4294967296-0.5)*0.35;};
    this.encoder=Float64Array.from({length:HD*SD},rand);
    this.encoderAtInit=new Float64Array(this.encoder);
    this.encoderBias=new Float64Array(HD);
    this.policy=Array.from({length:maxOperators},()=>Float64Array.from({length:HD},rand));
    this.policyBias=new Float64Array(maxOperators);
    this.transitions=Array.from({length:maxOperators},()=>Float64Array.from({length:TD*HD},rand));
    this.fisherEnc=new Float64Array(HD*SD);
    this.anchorEnc=null;
    this.fisherPolicy=Array.from({length:maxOperators},()=>new Float64Array(HD));
    this.anchorPolicy=Array.from({length:maxOperators},()=>null);
    this.updatesCount=0;
  }
  #assertNumericState(){
    const vector=(v,n)=>finiteVector(v,n);
    const bank=(v,n,k)=>Array.isArray(v)&&v.length===n&&v.every(x=>vector(x,k));
    const anchors=(v,n,k)=>Array.isArray(v)&&v.length===n&&v.every(x=>x===null||vector(x,k));
    if(!Number.isSafeInteger(this.maxOperators)||this.maxOperators<1||this.maxOperators>MAX_OP||
       !Number.isSafeInteger(this.updatesCount)||this.updatesCount<0||this.updatesCount>100000||
       !vector(this.encoder,HD*SD)||!vector(this.encoderAtInit,HD*SD)||
       !vector(this.encoderBias,HD)||!vector(this.policyBias,this.maxOperators)||
       !bank(this.policy,this.maxOperators,HD)||!bank(this.transitions,this.maxOperators,TD*HD)||
       !vector(this.fisherEnc,HD*SD)||this.fisherEnc.some(v=>v<0)||
       !bank(this.fisherPolicy,this.maxOperators,HD)||this.fisherPolicy.some(v=>v.some(x=>x<0))||
       !(this.anchorEnc===null||vector(this.anchorEnc,HD*SD))||
       !anchors(this.anchorPolicy,this.maxOperators,HD))throw Error('INVALID_MODEL_STATE');
  }
  #validate(state,op){
    this.#assertNumericState();
    if(!finiteVector(state,SD)||state.some(v=>Math.abs(v)>MAX_STATE_ABS))throw Error('INVALID_STATE');
    if(!Number.isSafeInteger(op)||op<0||op>=this.maxOperators)throw Error('INVALID_OPERATOR');
  }
  #forward(state,op){
    this.#validate(state,op);
    const h=new Float64Array(HD);
    for(let r=0;r<HD;r++){
      let v=this.encoderBias[r];
      for(let c=0;c<SD;c++)v+=this.encoder[r*SD+c]*state[c];
      if(!Number.isFinite(v))throw Error('NUMERIC_OVERFLOW');
      h[r]=Math.tanh(v);
    }
    let logit=this.policyBias[op];
    for(let i=0;i<HD;i++)logit+=this.policy[op][i]*h[i];
    if(!Number.isFinite(logit))throw Error('NUMERIC_OVERFLOW');
    const p=sigmoid(logit);
    const residual=new Float64Array(TD);
    for(let t=0;t<TD;t++){
      let v=0;
      for(let i=0;i<HD;i++)v+=this.transitions[op][t*HD+i]*h[i];
      if(!Number.isFinite(v))throw Error('NUMERIC_OVERFLOW');
      residual[t]=sigmoid(v);
    }
    return {h,p,residual};
  }
  score(state,op){
    const {p,residual}=this.#forward(state,op);
    return 0.75*p+0.25*(1-residual.reduce((s,x)=>s+x,0)/TD);
  }
  rank(state,activeCount=this.maxOperators){
    if(!Number.isSafeInteger(activeCount)||activeCount<1||activeCount>this.maxOperators)throw Error('INVALID_ACTIVE_COUNT');
    return Object.freeze(Array.from({length:activeCount},(_,i)=>Object.freeze({operatorIndex:i,score:this.score(state,i)})).sort((a,b)=>b.score-a.score));
  }
  encoderWeightDriftL2(){
    let sum=0;for(let i=0;i<this.encoder.length;i++){const d=this.encoder[i]-this.encoderAtInit[i];sum+=d*d;}return Math.sqrt(sum);
  }
  /** Simulated research labels only, NOT a proof of external execution. */
  /** Stage every update so rejection never leaves a partial live checkpoint.
   * This is research numerics only: no receipt admission or weight promotion.
   */
  trainResearchSample(input){
    this.#assertNumericState();
    const staged=new CoinWmV3ShadowModel(this.maxOperators,0);
    const vectors=['encoder','encoderAtInit','encoderBias','policyBias','fisherEnc'];
    const banks=['policy','transitions','fisherPolicy'];
    for(const key of vectors)staged[key]=new Float64Array(this[key]);
    for(const key of banks)staged[key]=this[key].map(v=>new Float64Array(v));
    staged.anchorEnc=this.anchorEnc===null?null:new Float64Array(this.anchorEnc);
    staged.anchorPolicy=this.anchorPolicy.map(v=>v===null?null:new Float64Array(v));
    staged.updatesCount=this.updatesCount;
    const result=staged.#trainResearchSampleInPlace(input);
    staged.#assertNumericState();
    for(const key of vectors)this[key].set(staged[key]);
    for(const key of banks)for(let i=0;i<this.maxOperators;i++)this[key][i].set(staged[key][i]);
    this.updatesCount=staged.updatesCount;
    return result;
  }
  #trainResearchSampleInPlace({state16,operatorIndex,success,observedNext8,learningRate=0.25,ewcLambda=1.2}){
    if(typeof success!=='boolean'||!finiteVector(observedNext8,TD)||observedNext8.some(v=>v<0||v>1))throw Error('INVALID_RESEARCH_LABEL');
    if(!Number.isFinite(learningRate)||learningRate<=0||learningRate>0.3||
      !Number.isFinite(ewcLambda)||ewcLambda<0||ewcLambda>5)throw Error('INVALID_HYPERPARAMETER');
    if(this.updatesCount>=100000)throw Error('UPDATE_BUDGET_EXHAUSTED');
    const {h,p,residual}=this.#forward(state16,operatorIndex);
    const delta=p-(success?1:0);
    const transDelta=new Float64Array(TD);
    for(let t=0;t<TD;t++)transDelta[t]=0.4*(residual[t]-observedNext8[t])*residual[t]*(1-residual[t]);
    const pol=this.policy[operatorIndex],trans=this.transitions[operatorIndex],dh=new Float64Array(HD);
    for(let r=0;r<HD;r++){
      let g=delta*pol[r];
      for(let t=0;t<TD;t++)g+=transDelta[t]*trans[t*HD+r];
      dh[r]=g*(1-h[r]*h[r]);
    }
    const anchor=this.anchorPolicy[operatorIndex],fish=this.fisherPolicy[operatorIndex];
    for(let r=0;r<HD;r++){
      let grad=delta*h[r];
      if(anchor)grad+=ewcLambda*fish[r]*(pol[r]-anchor[r]);
      pol[r]-=learningRate*grad;
      fish[r]=0.9*fish[r]+0.1*(delta*h[r])**2;
    }
    this.policyBias[operatorIndex]-=learningRate*0.25*delta;
    for(let t=0;t<TD;t++)for(let r=0;r<HD;r++)trans[t*HD+r]-=learningRate*transDelta[t]*h[r];
    for(let r=0;r<HD;r++){
      this.encoderBias[r]-=learningRate*0.5*dh[r];
      for(let c=0;c<SD;c++){
        const i=r*SD+c,raw=dh[r]*state16[c];
        const reg=this.anchorEnc?ewcLambda*this.fisherEnc[i]*(this.encoder[i]-this.anchorEnc[i]):0;
        this.encoder[i]-=learningRate*0.6*(raw+reg);
        this.fisherEnc[i]=0.9*this.fisherEnc[i]+0.1*raw*raw;
      }
    }
    this.updatesCount++;
    return Object.freeze({updated:true,status:'RESEARCH_SHADOW',promotionEligible:false});
  }
  consolidateEwcAnchors(activeCount=this.maxOperators){
    this.#assertNumericState();
    if(!Number.isSafeInteger(activeCount)||activeCount<1||activeCount>this.maxOperators)throw Error('INVALID_ACTIVE_COUNT');
    this.anchorEnc=new Float64Array(this.encoder);
    for(let i=0;i<activeCount;i++)this.anchorPolicy[i]=new Float64Array(this.policy[i]);
  }
}
