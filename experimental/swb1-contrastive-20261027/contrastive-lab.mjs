/** Isolated SWB1 counterexperiments. Uses the existing forward/backpropagation
 * and snapshot ABI. No production state, ledger or Android activation writes. */
import {SubwordEncoder,sha256} from '../../.github/continuum/portable-subword.mjs';
import {TRAIN,VALIDATION,ANDROID_VALIDATION} from '../../.github/continuum/neural-curriculum.mjs';
import {restoreNeuralState,trainRound,exportWeights} from '../../.github/continuum/neural-core.mjs';

export const POLICIES=Object.freeze(['CONTROL_REPLAY','VERIFIED_BALANCED','HEAD_ORTHOGONAL','SEMANTIC_ONLY','SWB2_SHADOW','FROZEN_INTENT_SWB2']);
export function projectedSemantic(model,example,rate=.10){
 const before=model.embeddings.slice(),head=model.head.slice(0,16);
 model.updateSemantic(example,rate);
 const norm=head.reduce((n,x)=>n+x*x,0);
 if(norm===0)return;
 for(let offset=0;offset<before.length;offset+=16){
  let dot=0;for(let i=0;i<16;i++)dot+=(model.embeddings[offset+i]-before[offset+i])*head[i];
  for(let i=0;i<16;i++)model.embeddings[offset+i]=Math.max(-8,Math.min(8,model.embeddings[offset+i]-dot/norm*head[i]));
 }
 // This removes only a first-order head direction, not exact intent/logit drift.
 // The independent retained-capability gates must still pass.
}
export function measurements(model,suite){
 const intent=model.evaluate(suite.intent),semantic=model.evaluate(suite.semantic);
 const predictions=suite.semantic.map(e=>({id:e.id??null,expected:e.similar,probability:model.similarity(e.left,e.right)}));
 const falseSimilar=predictions.filter(x=>!x.expected&&x.probability>=.5).length;
 const missedSimilar=predictions.filter(x=>x.expected&&x.probability<.5).length;
 return {intent,semantic,falseSimilar,missedSimilar,predictions};
}
const protectedMeasures=m=>[TRAIN,VALIDATION,ANDROID_VALIDATION].map(s=>({intent:m.evaluate(s.intent),semantic:m.evaluate(s.semantic)}));
function noLoss(before,after){return ['intent','semantic'].every(k=>after[k].correct>=before[k].correct&&after[k].loss<=before[k].loss+.015);}
function retention(before,after){return before.every((s,i)=>noLoss(s,after[i]));}
function random(seed){let n=seed>>>0;return()=>{n=(Math.imul(1664525,n)+1013904223)>>>0;return n/4294967296;};}
function shuffle(items,rng){const a=items.slice();for(let i=a.length-1;i>0;i--){const j=Math.floor(rng()*(i+1));[a[i],a[j]]=[a[j],a[i]];}return a;}

export function runContrastiveTrial(input,train,validation,{policy='VERIFIED_BALANCED',seed=73,steps=8000,every=1000,maxMillis=30000,cancelled=()=>false}={}){
 if(!POLICIES.includes(policy)||!Number.isSafeInteger(seed)||seed<0||!Number.isInteger(steps)||steps<1||steps>8000||!Number.isInteger(every)||every<1||every>steps||!Number.isInteger(maxMillis)||maxMillis<1||maxMillis>30000)throw Error('INVALID_CONTRASTIVE_BUDGET');
 if(train.split!=='train'||validation.split!=='validation'||!train.semantic.length||!validation.semantic.length)throw Error('INVALID_SPLIT_ROLE');
 const inputText=JSON.stringify(input),state=restoreNeuralState(input),initial=exportWeights(state);
 const baseline=SubwordEncoder.restore(initial),before=measurements(baseline,validation),oldBefore=protectedMeasures(baseline);
 const model=['SWB2_SHADOW','FROZEN_INTENT_SWB2'].includes(policy)?baseline.upgradeToSwb2():SubwordEncoder.restore(initial);
 const view=m=>policy==='FROZEN_INTENT_SWB2'?frozenIntentView(baseline,m):m;
 const initialParameterHash=sha256(JSON.stringify({embeddings:model.embeddings,head:model.head}));
 const rng=random(seed);const sem=shuffle(train.semantic,rng),intent=shuffle([...train.intent,...TRAIN.intent],rng),oldSem=shuffle(TRAIN.semantic,rng);
 const start=performance.now(),checkpoints=[];let completed=0,publicUpdates=0,selected=null,bestDiagnostic=null,legacy=null;
 const inspect=(m,at)=>{
  const current=measurements(m,validation),protectedAfter=protectedMeasures(m);
  const retained=retention(oldBefore,protectedAfter);
  const gain=current.semantic.correct>=before.semantic.correct+2;
  const eligible=retained&&gain&&current.intent.correct>=before.intent.correct&&current.intent.loss<=before.intent.loss+.015&&current.semantic.loss<=before.semantic.loss+.015;
  const record={steps:at,modelHash:sha256(m.save()),newValidation:current,protectedAfter,retained,validationGain:gain,researchGatePassed:eligible};
  checkpoints.push(record);
  const rank=r=>r.newValidation.semantic.correct*1000+r.newValidation.intent.correct*10-r.newValidation.semantic.loss-r.newValidation.intent.loss;
  if(!bestDiagnostic||rank(record)>rank(bestDiagnostic.record))bestDiagnostic={record,bytes:m.save()};
  if(eligible&&(!selected||rank(record)>rank(selected.record)))selected={record,bytes:m.save()};
 };
 if(policy==='CONTROL_REPLAY'){
  const r=trainRound(state,{steps,now:1,maxMillis,cancelled});legacy=r.report;completed=r.report.steps;publicUpdates=r.report.sourceUpdates;inspect(SubwordEncoder.restore(exportWeights(r.state)),r.report.selectedSteps);
 }else{
  for(;completed<steps;completed++){
   if(cancelled()||performance.now()-start>=maxMillis)break;
   if(policy!=='SEMANTIC_ONLY'&&completed%4===1)model.updateIntent(intent[Math.floor(completed/4)%intent.length]);
   else{
    const ex=completed%4===3?oldSem[Math.floor(completed/4)%oldSem.length]:sem[Math.floor(completed/2)%sem.length];
    if(policy==='HEAD_ORTHOGONAL')projectedSemantic(model,ex);else model.updateSemantic(ex);
   }
   if((completed+1)%every===0||completed+1===steps)inspect(view(model),completed+1);
  }
 }
 const complete=completed===steps&&!cancelled();
 if(!complete)selected=null;
 if(JSON.stringify(input)!==inputText||!initial.equals(exportWeights(input)))throw Error('BASELINE_CHANGED_DURING_TRIAL');
 const bytes=selected?.bytes??bestDiagnostic?.bytes??initial;
 return {report:{schema:'continuum-contrastive-shadow-v1',policy,seed,stepsRequested:steps,stepsCompleted:completed,millis:performance.now()-start,status:complete?'COMPLETED':'INFRA_INTERRUPTED_OR_BUDGET',startingModelHash:sha256(initial),initialParameterHash,outputModelHash:sha256(bytes),format:SubwordEncoder.restore(bytes).version===1?'SWB1':'SWB2',inferenceContract:policy==='FROZEN_INTENT_SWB2'?'SWB1_INTENT_PLUS_SWB2_SEMANTIC_RESEARCH':'SINGLE_ENCODER',intentEncoderHash:policy==='FROZEN_INTENT_SWB2'?sha256(initial):null,publicSourceUpdates:publicUpdates,before,protectedBefore:oldBefore,checkpoints,selectedSteps:selected?.record.steps??null,researchGatePassed:Boolean(selected),productionEligible:false,promoted:false,weightsWritten:false,baselinePreserved:true,finalSetUsed:false,legacyReport:legacy},bytes};
}

/** Two existing codecs behind a research view. This contract is not accepted by
 * Android's single-SWB1 productive loader, and never overwrites that file. */
export function frozenIntentView(intent,semantic){
 return {
  intentScore:text=>intent.intentScore(text),similarity:(a,b)=>semantic.similarity(a,b),
  evaluate:items=>{
   if(items.every(e=>'label' in e))return intent.evaluate(items);
   if(items.every(e=>typeof e.similar==='boolean'))return semantic.evaluate(items);
   throw Error('MIXED_EVALUATION_SCHEMA');
  },
  save:()=>semantic.save()
 };
}
