import {SubwordEncoder,sha256,buckets} from './portable-subword.mjs';
import {TRAIN,VALIDATION,AUDIT,ANDROID_VALIDATION} from './neural-curriculum.mjs';
const VERSION=1, MAX_REPLAY=96;
const normalize=s=>s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim();
const reserved=new Set([VALIDATION,AUDIT].flatMap(s=>[...s.intent.map(x=>x.text),...s.semantic.flatMap(x=>[x.left,x.right])]).map(normalize));
export const SUITE_HASH=sha256(JSON.stringify({VALIDATION,AUDIT}));
export function createNeuralState(){
  const bytes=new SubwordEncoder().save();
  return {version:VERSION,rounds:0,accepted:0,paused:false,model:bytes.toString('base64'),modelHash:sha256(bytes),lastKnownGood:null,replay:[],history:[]};
}
export function restoreNeuralState(value){
  if(value==null)return createNeuralState();
  if(value.version!==VERSION||!Array.isArray(value.replay)||value.replay.length>MAX_REPLAY||!Array.isArray(value.history)||value.history.length>64)throw Error('Invalid neural state');
  const bytes=Buffer.from(value.model,'base64');
  if(sha256(bytes)!==value.modelHash)throw Error('Neural model hash mismatch');
  SubwordEncoder.restore(bytes);
  for(const e of value.replay){
    if(e.trust!=='PUBLIC_OBSERVATION'||!publicUrl(e.url)||typeof e.left!=='string'||typeof e.right!=='string'||e.hash!==sha256(e.left+'\n'+e.right+'\n'+e.url))throw Error('Invalid replay provenance');
    buckets(e.left);buckets(e.right);
  }
  return structuredClone(value);
}
export const exportWeights=state=>Buffer.from(restoreNeuralState(state).model,'base64');
export const evaluateSuite=(model,suite)=>({intent:model.evaluate(suite.intent),semantic:model.evaluate(suite.semantic)});
function publicUrl(value){try{const u=new URL(value);return u.protocol==='https:'&&!u.username&&!u.password&&!/^(localhost|127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|\[|169\.254\.)/.test(u.hostname)&&u.hostname.includes('.');}catch{return false;}}
export function ingestPublicSources(value,sources,now=Date.now()){
  const state=restoreNeuralState(value);
  for(const source of sources.slice(0,256)){
    if(!['PublicWeb','Wikipedia','OpenAlex','Crossref','arXiv','GitHub'].includes(source.source)||!publicUrl(source.url))continue;
    const left=String(source.title??'').replace(/\s+/g,' ').trim().slice(0,256);
    const right=String(source.text??'').replace(/\s+/g,' ').trim().slice(0,1000);
    if(left.length<4||right.length<40||reserved.has(normalize(left))||reserved.has(normalize(right)))continue;
    try{buckets(left);buckets(right);}catch{continue;}
    const hash=sha256(left+'\n'+right+'\n'+source.url);
    if(state.replay.some(e=>e.hash===hash))continue;
    state.replay.push({id:'public-'+hash.slice(0,20),hash,left,right,url:source.url,source:source.source,at:now,trust:'PUBLIC_OBSERVATION',status:'REPLAY_PENDING',replays:0});
  }
  // Deterministic hash-priority reservoir: retains old material across restarts,
  // prevents a single large new batch from evicting all previous experience.
  state.replay.sort((a,b)=>a.hash.localeCompare(b.hash));state.replay=state.replay.slice(0,MAX_REPLAY);
  return state;
}
function noRegression(before,after){return ['intent','semantic'].every(k=>after[k].correct>=before[k].correct&&after[k].loss<=before[k].loss+.015);}
export function trainRound(value,{steps=12000,now=Date.now(),cancelled=()=>false,maxMillis=25000,
  researchReplayEvery=8,researchReplayRate=.035}={}){
  if(!Number.isInteger(steps)||steps<1||steps>24000||!Number.isInteger(maxMillis)||maxMillis<1||maxMillis>30000)throw Error('Invalid training budget');
  // Defaults are byte-for-byte compatible with the existing public-source learning path.
  // Nondefault values are research-only; the shadow runner never publishes weights.
  if(![0,8,16,32,64].includes(researchReplayEvery)||
     ![.035,.015,.0075].includes(researchReplayRate))throw Error('Invalid research replay policy');
  const state=restoreNeuralState(value),baseline=SubwordEncoder.restore(exportWeights(state)),candidate=SubwordEncoder.restore(baseline.save());
  const androidBefore=evaluateSuite(baseline,ANDROID_VALIDATION);
  const validationBefore=evaluateSuite(baseline,VALIDATION),retentionBefore=evaluateSuite(baseline,TRAIN);
  const start=performance.now();let completed=0,sourceUpdates=0,selected=null,selectedSteps=0;
  const replayCounts=new Map();let selectedReplayCounts=new Map();
  let bestLoss=validationBefore.intent.loss+validationBefore.semantic.loss;
  // Rotate curriculum across rounds; both tasks always replay in the same round.
  const offset=state.rounds*17;
  for(;completed<steps&&!state.paused&&!cancelled()&&performance.now()-start<maxMillis;completed++){
    const n=Math.floor(completed/2)+offset;
    if(completed%2===0)candidate.updateIntent(TRAIN.intent[n%TRAIN.intent.length]);
    else if(state.replay.length&&researchReplayEvery>0&&n%researchReplayEvery===researchReplayEvery-1){
      const e=state.replay[Math.floor(n/8)%state.replay.length];
      candidate.updateSemantic({left:e.left,right:e.right,similar:true},researchReplayRate);sourceUpdates++;
      replayCounts.set(e.id,(replayCounts.get(e.id)??0)+1);
    }else candidate.updateSemantic(TRAIN.semantic[n%TRAIN.semantic.length]);
    // Selection sees validation only. Report-only audit is evaluated after selection.
    if((completed+1)%1000===0||completed+1===steps){
      const v=evaluateSuite(candidate,VALIDATION),r=evaluateSuite(candidate,TRAIN);
      const loss=v.intent.loss+v.semantic.loss;
      if(noRegression(validationBefore,v)&&noRegression(retentionBefore,r)&&noRegression(androidBefore,evaluateSuite(candidate,ANDROID_VALIDATION))&&loss<bestLoss-.001){
        selected=candidate.save();selectedSteps=completed+1;bestLoss=loss;selectedReplayCounts=new Map(replayCounts);
      }
    }
  }
  const proposed=selected?SubwordEncoder.restore(selected):candidate;
  const validationAfter=evaluateSuite(proposed,VALIDATION),retentionAfter=evaluateSuite(proposed,TRAIN);
  const complete=completed===steps&&!cancelled()&&!state.paused;
  const gain=['intent','semantic'].some(k=>validationAfter[k].loss<validationBefore[k].loss-.001);
  const accepted=complete&&selected!==null&&gain&&noRegression(validationBefore,validationAfter)&&noRegression(retentionBefore,retentionAfter);
  const beforeBytes=baseline.save(),afterBytes=proposed.save();
  const report={version:1,round:state.rounds+1,at:now,steps:completed,selectedSteps,sourceUpdates,accepted,
    reason:!complete?'PAUSED_OR_INTERRUPTED':!noRegression(validationBefore,validationAfter)?'VALIDATION_REGRESSION':!noRegression(retentionBefore,retentionAfter)?'REPLAY_REGRESSION':!gain?'NO_MEASURED_GAIN':'MULTITASK_GATE_PASSED',
    baselineHash:sha256(beforeBytes),candidateHash:sha256(afterBytes),suiteHash:SUITE_HASH,
    validationBefore,validationAfter,retentionBefore,retentionAfter,androidBefore,androidAfter:evaluateSuite(proposed,ANDROID_VALIDATION),
    auditBefore:evaluateSuite(baseline,AUDIT),auditAfter:evaluateSuite(proposed,AUDIT),auditUsedForSelection:false,
    benchmarkKind:'AUTHORED_SMALL_HOLDOUT_NOT_EXTERNAL_CERTIFICATION',replayExamples:state.replay.length};
  state.rounds++;if(accepted){state.accepted++;state.lastKnownGood=state.model;state.model=afterBytes.toString('base64');state.modelHash=report.candidateHash;
    for(const e of state.replay){const used=selectedReplayCounts.get(e.id)??0;if(used){e.replays+=used;e.status='REPLAYED_IN_ACCEPTED_MODEL';}} // Participation, not a truth claim.
  }
  state.history=[...state.history,report].slice(-64);
  return {state,report};
}
export function neuralStatus(value){
  const s=restoreNeuralState(value),m=SubwordEncoder.restore(exportWeights(s)),audit=evaluateSuite(m,AUDIT),last=s.history.at(-1);
  return {version:1,modelHash:s.modelHash,updates:m.updates,rounds:s.rounds,accepted:s.accepted,paused:s.paused,replay:s.replay.length,audit,suiteHash:SUITE_HASH,lastDecision:last?.reason??'NOT_TRAINED'};
}
export function formatNeuralStatus(value){
  const s=neuralStatus(value);
  return '**Neuronaler Kern:** '+s.updates+' Gewichtsupdates · '+s.accepted+'/'+s.rounds+' Lernrunden übernommen'+(s.paused?' · PAUSIERT':'')+'\n'+
    '**Separater Audit:** Intent '+s.audit.intent.correct+'/'+s.audit.intent.total+' (Loss '+s.audit.intent.loss.toFixed(4)+'), Semantik '+s.audit.semantic.correct+'/'+s.audit.semantic.total+' (Loss '+s.audit.semantic.loss.toFixed(4)+')\n'+
    '**Replay:** '+s.replay+' öffentliche Beobachtungen · letzte Entscheidung '+s.lastDecision+'\n'+
    'Kleine selbst verfasste Testmenge, keine externe Zertifizierung; Audit-Beispiele steuern weder Gradienten noch Modellfreigabe.';
}

export function selectNeuralResearchTarget(value){
 const state=restoreNeuralState(value);if(!state.replay.length)return null;
 const model=SubwordEncoder.restore(exportWeights(state));
 const ranked=state.replay.map(e=>{
   const p=model.similarity(e.left,e.right);
   const uncertainty=1-Math.abs(p-.5)*2;
   return {e,score:uncertainty/(1+Number(e.researchAttempts??0))+.1/(1+e.replays)};
 }).sort((a,b)=>b.score-a.score||a.e.id.localeCompare(b.e.id));
 const chosen=ranked[0];
 return {type:'neural-curiosity',experienceId:chosen.e.id,score:chosen.score,
   text:'Welche unabhängigen Experimente prüfen oder widerlegen die Aussagen zu '+chosen.e.left+'?'};
}
