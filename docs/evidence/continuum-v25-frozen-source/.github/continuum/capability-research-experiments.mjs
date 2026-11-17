/** Reporting only. Run explicitly with Node; stdout is the sole output. */
import fs from 'node:fs';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {createNeuralState,exportWeights} from './neural-core.mjs';
import {digest,canonical,createSynthesisGrammar,synthesize,verifyProgram,createRepairMemory,
  assertDisjointSplits,repairTask,operatorMutations,createFrozenCommittee,numericResearchCandidate} from './capability-research.mjs';
import {PROTOCOL,trainingTasks,holdoutTasks,adversarialTasks,oracleFor,memoryContext,dataFingerprint} from './capability-research-fixtures.mjs';

const summary=rows=>({tasks:rows.length,exact:rows.filter(r=>r.exact).length,
  candidates:rows.filter(r=>r.status!=='ABSTAIN').length,
  oracleCalls:rows.reduce((n,r)=>n+(r.oracleCalls??0),0),
  candidateLookups:rows.reduce((n,r)=>n+(r.candidateLookups??0),0),
  disagreementLookups:rows.reduce((n,r)=>n+(r.disagreementLookups??0),0),
  attempts:rows.reduce((n,r)=>n+(r.attempts??0),0),
  verifierExecutions:rows.reduce((n,r)=>n+(r.executed??0),0)});
const judged=(result,task)=>({...result,exact:Boolean(result.ast)&&verifyProgram(result.ast,task.examples).passed,
  independentErrors:result.ast?verifyProgram(result.ast,task.examples).errors:null});
const rehash=record=>{const {contentHash,...body}=record;return {...body,contentHash:digest(body)};};

function inspectMemoryAttacks(training,holdout,adversarial) {
  const memory=createRepairMemory({trainingTasks:training,holdoutTasks:[...holdout,...adversarial]});
  const first=training[0],receipt=memory.learn(first.id,first.patch),attacks=[];
  const attack=(name,change,rehashBytes=true)=>{
    let candidate=structuredClone(receipt);change(candidate);if(rehashBytes)candidate=rehash(candidate);
    attacks.push({name,...memory.import(candidate)});
  };
  attack('corrupt-bytes',r=>r.witness.executed--,false);
  attack('rehash-forged-proof',r=>{r.witness.repaired=['min','x','y'];});
  attack('holdout-contamination',r=>{r.provenance.taskId=holdout[0].id;r.provenance.taskSnapshot=digest(holdout[0]);});
  attack('family-alias',r=>{r.provenance.family=holdout[0].family;});
  attack('source-drift',r=>{r.provenance.taskSnapshot='f'.repeat(64);});
  attack('training-permission-forgery',r=>{r.modelTrainingAuthorized=true;});
  let splitAliasRejected=false;
  try{
    assertDisjointSplits(training,[{...first,id:'aliased-heldout',seed:997,family:'renamed-family',split:'holdout'}]);
  }catch(error){splitAliasRejected=error.message==='RESEARCH_SPLIT_CONTENT_ALIAS';}
  const staleIds=memory.active().map(r=>r.contentHash);memory.revoke(receipt.contentHash);
  const staleReplay=repairTask(adversarial.find(t=>t.family==='operator-reversal'),{memory,contextIds:staleIds});
  return {attacks,acceptedAttacks:attacks.filter(a=>a.valid).length,splitAliasRejected,
    revokedReceiptReimport:memory.import(receipt),activeAfterRevocation:memory.active().length,
    staleContextUsedRevokedHint:staleReplay.trace.some(t=>t.memoryHash===receipt.contentHash),
    revokedFallbackStatus:staleReplay.status,statistics:memory.statistics()};
}

export function runCapabilityResearch() {
  const train=trainingTasks(),holdout=holdoutTasks(),adversarial=adversarialTasks();
  assertDisjointSplits(train,[...holdout,...adversarial]);
  const grammar=createSynthesisGrammar();
  // Nothing fits or selects policies from the heldout rows below. Memory is
  // fully fitted before their oracles are supplied to any algorithm.
  const memory=createRepairMemory({trainingTasks:train,holdoutTasks:[...holdout,...adversarial]});
  for(const task of train)memory.learn(task.id,task.patch);
  const fitCost=memory.statistics();
  const state=createNeuralState(),stateBefore=canonical(state),snapshot=exportWeights(state);
  const committee=createFrozenCommittee(snapshot),records=memoryContext(memory,train);
  const evaluation=[...holdout,...adversarial];
  const synthesis=evaluation.map(task=>({id:task.id,family:task.family,seed:task.seed,split:task.split,
    baseline:judged(synthesize({grammar,oracle:oracleFor(task),mode:'passive',seed:task.seed}),task),
    prototype:judged(synthesize({grammar,oracle:oracleFor(task),mode:'disagreement',seed:task.seed}),task)}));
  const repairs=evaluation.map(task=>{
    const baseline=judged(repairTask(task),task),prototype=judged(repairTask(task,{memory}),task);
    const hints=memory.active();const naive=hints.flatMap(h=>operatorMutations(task.source)
      .filter(c=>c.patch.from===h.hint.from&&c.patch.to===h.hint.to))[0];
    return {id:task.id,family:task.family,seed:task.seed,split:task.split,baseline,prototype,
      unsafeUnverifiedReplayNegativeControl:{claimedSuccess:Boolean(naive),
        actuallyExact:Boolean(naive)&&verifyProgram(naive.ast,task.examples).passed}};
  });
  const contexts=evaluation.map(task=>{
    const out={id:task.id,family:task.family,seed:task.seed,split:task.split};
    for(const mode of ['lexical','swb1','committee']){
      const context=committee.select({query:task.query,records,budget:PROTOCOL.contextBudget,mode});
      const hints=memory.active(context.ids),wanted=task.patch?`${task.patch.from}:${task.patch.to}`:null;
      const relevant=hints.filter(h=>`${h.hint.from}:${h.hint.to}`===wanted).length;
      out[mode]={...context,relevantHints:relevant,irrelevantHints:hints.length-relevant,
        relevantCoverage:wanted!==null&&relevant>0,repair:judged(repairTask(task,{memory,contextIds:context.ids}),task)};
    }
    return out;
  });
  if(canonical(state)!==stateBefore||!snapshot.equals(exportWeights(state)))throw Error('RESEARCH_NEURON_STATE_MUTATION');
  const normal=rows=>rows.filter(r=>r.split==='holdout');
  const contextSummary=mode=>({tasks:normal(contexts).length,
    relevantCoverage:normal(contexts).filter(r=>r[mode].relevantCoverage).length,
    relevantHints:normal(contexts).reduce((n,r)=>n+r[mode].relevantHints,0),
    irrelevantHints:normal(contexts).reduce((n,r)=>n+r[mode].irrelevantHints,0),
    contextCost:normal(contexts).reduce((n,r)=>n+r[mode].cost,0),
    compactRoutes:normal(contexts).filter(r=>r[mode].route==='COMPACT_AGREEMENT').length,
    repairs:summary(normal(contexts).map(r=>r[mode].repair))});
  const allVerified=[...repairs.flatMap(r=>[r.baseline,r.prototype]),
    ...contexts.flatMap(r=>['lexical','swb1','committee'].map(m=>r[m].repair))];
  const adapterTask=holdout[0],adapterResult=repairs[0].prototype;
  const report={schema:'continuum-capability-research-report',version:1,status:'RESEARCH_SHADOW',
    sourceBaseline:'377bc05b8a8d96494f4cee7be8665fde3317f1e7',
    protocol:PROTOCOL,protocolHash:digest(PROTOCOL),dataFingerprint:dataFingerprint(),
    hashScheme:'SHA-256 canonical JSON; module and model snapshot hashes use raw bytes',
    moduleHashes:Object.fromEntries(['capability-research.mjs','capability-research-fixtures.mjs','capability-research-experiments.mjs']
      .map(name=>[name,crypto.createHash('sha256').update(fs.readFileSync(new URL(name,import.meta.url))).digest('hex')])),
    runtime:{node:process.version,platform:process.platform,architecture:process.arch,androidExecuted:false},
    splitManifest:{training:train.map(({id,family,seed})=>({id,family,seed})),
      holdout:holdout.map(({id,family,seed})=>({id,family,seed})),
      adversarial:adversarial.map(({id,family,seed})=>({id,family,seed})),overlapChecked:true,
      holdoutDistinctSourceAndOraclePairs:new Set(holdout.map(t=>digest({source:t.source,examples:t.examples}))).size,
      holdoutDistinctOracleFunctionsOnScoreDomain:new Set(holdout.map(t=>digest(t.examples))).size,
      semanticDependence:'Absolute-distance and two-sided-gap families have identical oracle outputs; repeated seeds are not independent competencies.',
      holdoutUsedForFitting:false,holdoutPublicAndConsumed:true},
    synthesis:{experiment:'EXP-02',grammar:{fingerprint:grammar.fingerprint,programs:grammar.programs.length,
      generated:grammar.generated,sharedInterpreterEvaluations:grammar.interpreterEvaluations},
      holdout:{baseline:summary(normal(synthesis).map(r=>r.baseline)),prototype:summary(normal(synthesis).map(r=>r.prototype))},
      rows:synthesis},
    memory:{experiment:'EXP-06/07/12',trainingProofCost:fitCost,
      holdout:{baseline:summary(normal(repairs).map(r=>r.baseline)),prototype:summary(normal(repairs).map(r=>r.prototype))},
      unsafeNegativeControlFalseClaims:repairs.filter(r=>r.unsafeUnverifiedReplayNegativeControl.claimedSuccess&&
        !r.unsafeUnverifiedReplayNegativeControl.actuallyExact).length,
      integrity:inspectMemoryAttacks(train,holdout,adversarial),rows:repairs},
    context:{experiment:'EXP-03/04',model:{origin:'NEURON createNeuralState/exportWeights, untrained SWB1',
      snapshotHash:committee.snapshotHash,updates:committee.updates,weightsChanged:false},
      costUnit:'ceil(UTF-8 bytes / 4), proxy not tokenizer tokens',
      holdout:{lexical:contextSummary('lexical'),swb1:contextSummary('swb1'),prototype:contextSummary('committee')},rows:contexts},
    integration:{numericAdapterAccepted:numericResearchCandidate(adapterTask.source,adapterResult.ast).authority==='SHADOW_ONLY',
      canonicalRepairGateExecuted:false,androidCompilationExecuted:false,productionComponentsChanged:false,
      automaticallyAcceptedWrongRepairs:allVerified.filter(r=>r.status==='VERIFIED_FINITE_DOMAIN'&&!r.exact).length},
    regressions:{synthesisExact:normal(synthesis).filter(r=>r.baseline.exact&&!r.prototype.exact).map(r=>r.id),
      synthesisAcquisitionWork:normal(synthesis).filter(r=>r.prototype.candidateLookups+r.prototype.disagreementLookups>
        r.baseline.candidateLookups+r.baseline.disagreementLookups).map(r=>r.id),
      memoryAttempts:repairs.filter(r=>r.prototype.attempts>r.baseline.attempts).map(r=>r.id),
      contextCoverage:contexts.filter(r=>r.lexical.relevantCoverage&&!r.committee.relevantCoverage).map(r=>r.id),
      contextRepairAttempts:contexts.filter(r=>r.committee.repair.attempts>r.lexical.repair.attempts).map(r=>r.id)},
    promotionEligible:false,modelTrainingAuthorized:false,
    limitations:['Authored small bounded tasks, public holdouts consumed by this audit.',
      'Twelve heldout rows contain five distinct source/oracle pairs and four distinct bounded-domain oracle functions.',
      'Synthesis oracle-query savings do not imply lower CPU work; cached disagreement comparisons are reported.',
      'Memory training proof cost is reported separately and must be amortized; no persistent authenticated store is claimed.',
      'No language generation, learned-model improvement, external benchmark, Android runtime or novel method was measured.']};
  return {...report,reportHash:digest(report)};
}

if(process.argv[1]===fileURLToPath(import.meta.url))process.stdout.write(JSON.stringify(runCapabilityResearch(),null,2)+'\n');
