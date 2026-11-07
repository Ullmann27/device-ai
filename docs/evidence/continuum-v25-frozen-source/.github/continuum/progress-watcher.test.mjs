import test from "node:test";
import assert from "node:assert/strict";
import {
  captureProgressSample, appendProgressSample, progressTrend,
  formatProgressReport, assessLearningCandidate
} from "./progress-watcher.mjs";

function makeState(beliefs, cycles=[]) {
  return {generation:3,cycles,baby:{beliefs,
    quarantinedBeliefs:[{id:"off-topic"}]}};
}
function belief(explicit) {
  return {status:explicit?"GROUNDED_OBSERVATION":"PROVISIONAL",
    evidenceMode:explicit?"EXPLICIT_CITATION":"ATTRIBUTED_UNVERIFIED",
    citationUrls:explicit?["https://example.org/paper"]:[]};
}
function fixture(int, semantic, replay, hash="a", run="baseline-001") {
  return {suiteId:"frozen-heldout-v1",modelHash:hash.repeat(64),
    testSetHash:"f".repeat(64),runId:run,testSetUsedInTraining:false,
    tasks:{
      intent:{attempted:100,correct:int},
      semanticSimilarity:{attempted:100,correct:semantic},
      replayRetention:{attempted:100,correct:replay}
    }};
}

test("activity alone never becomes a model improvement claim",()=>{
  const s=captureProgressSample(makeState([belief(true),belief(false)],
    [{success:true},{success:false}]),100);
  assert.equal(s.research.cycles,2);
  assert.equal(s.research.failed,1);
  assert.equal(s.memory.citationCoverage,.5);
  assert.equal(s.benchmark,"NOT_EVALUATED");
  assert.match(formatProgressReport([],s),/NICHT GEMESSEN/);
});

test("watcher samples are bounded and repeated status generations are deduplicated",()=>{
  const s=captureProgressSample(makeState([belief(true)]),100);
  const duplicates=appendProgressSample([s],{...s,at:200,generation:25});
  assert.equal(duplicates.length,1);
  let history=[];
  for(let i=0;i<100;i++)history=appendProgressSample(history,
    captureProgressSample(makeState(Array.from({length:i+1},()=>belief(true))),i));
  assert.equal(history.length,64);
});

test("a coverage gain is named only as data quality and requires enough evidence",()=>{
  const before=captureProgressSample(makeState(Array.from({length:10},(_,i)=>belief(i<3))),100);
  const after=captureProgressSample(makeState(Array.from({length:10},(_,i)=>belief(i<7))),200);
  const trend=progressTrend([before,after]);
  assert.equal(trend.status,"CITATION_COVERAGE_INCREASED");
  assert.ok(trend.change>0.39);
  assert.match(trend.message,/kein Lern/);
  const low=progressTrend([before,captureProgressSample(makeState([belief(true)]),300)]);
  assert.equal(low.status,"INSUFFICIENT_DATA");
});

test("historical baseline is not mistaken for progress",()=>{
  const s=captureProgressSample(makeState([]),100);
  assert.equal(progressTrend([s]).status,"BASELINE_ONLY");
  assert.match(formatProgressReport([],s),/nicht automatisch/);
});

test("snapshot gate rejects missing holdout provenance, leaks and mismatched fixtures",()=>{
  const old=fixture(70,70,70),next=fixture(75,74,72,"b","candidate-002");
  assert.equal(assessLearningCandidate(old,{...next,testSetUsedInTraining:true}).decision,"INVALID_EVIDENCE");
  assert.equal(assessLearningCandidate(old,{...next,testSetHash:"c".repeat(64)}).decision,"INVALID_EVIDENCE");
  assert.equal(assessLearningCandidate(old,{...next,modelHash:old.modelHash}).decision,"INVALID_EVIDENCE");
  assert.equal(assessLearningCandidate(old,{...next,tasks:{...next.tasks,intent:{attempted:100,correct:101}}}).decision,"INVALID_EVIDENCE");
});

test("forgetting regression blocks promotion even if intent dramatically improves",()=>{
  const old=fixture(65,70,85),next=fixture(94,74,80,"b","candidate-002");
  const result=assessLearningCandidate(old,next);
  assert.equal(result.decision,"REJECT_REGRESSION");
  assert.ok(result.taskDeltas.replayRetention<-.03);
});

test("valid accuracy improvement only becomes eligible for review, never auto-promoted",()=>{
  const old=fixture(70,70,70),next=fixture(76,71,69,"b","candidate-002");
  assert.equal(assessLearningCandidate(old,next).decision,"ELIGIBLE_FOR_INDEPENDENT_REVIEW");
  assert.equal(assessLearningCandidate(old,fixture(70,70,70,"b","candidate-002")).decision,"NO_DEMONSTRATED_GAIN");
});
