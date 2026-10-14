import test from "node:test";
import assert from "node:assert/strict";
import {createWorldModel,proposeHypothesis,selectHypothesisTest,evaluateHypothesisEvidence,worldModelStatus} from "./world-model.mjs";

test("world model keeps hypotheses provisional until independent support arrives",()=>{
  let model=createWorldModel(100);
  let p=proposeHypothesis(model,{claim:"Experience replay can reduce catastrophic forgetting in continual learning systems when older examples are sampled during updates.",confidence:.45,now:110});
  model=p.model;
  assert.equal(worldModelStatus(model).provisional,1);
  const selected=selectHypothesisTest(model,120);
  assert.equal(selected.id,p.hypothesis.id);
  let r=evaluateHypothesisEvidence(model,p.hypothesis.id,{
    text:"Independent experiments report that experience replay reduces catastrophic forgetting in continual learning by rehearsing older examples.",
    citations:[{url:"https://example.org/paper-a"}],now:130
  });
  model=r.model;
  assert.equal(r.classification,"SUPPORT");
  assert.equal(r.hypothesis.status,"PROVISIONAL");
  assert.equal(r.hypothesis.independentSupportEvents,1);
  r=evaluateHypothesisEvidence(model,p.hypothesis.id,{
    text:"A second study finds experience replay reduces catastrophic forgetting when previous samples are rehearsed during sequential learning.",
    citations:[{url:"https://example.net/paper-b"}],now:140
  });
  assert.equal(r.hypothesis.status,"SUPPORTED");
});

test("independent contradictory evidence makes a hypothesis disputed",()=>{
  let model=createWorldModel(100);
  const p=proposeHypothesis(model,{claim:"A fixed replay buffer always prevents catastrophic forgetting in every continual learning task.",confidence:.6,now:110});
  model=p.model;
  const r=evaluateHypothesisEvidence(model,p.hypothesis.id,{
    text:"A fixed replay buffer does not always prevent catastrophic forgetting in every continual learning task.",
    citations:[{url:"https://example.edu/negative"}],now:120
  });
  assert.equal(r.classification,"CONTRADICT");
  assert.equal(r.hypothesis.status,"DISPUTED");
});


test("repeating the same source domains in a second cycle does not promote a hypothesis",()=>{
  let model=createWorldModel(100);
  const p=proposeHypothesis(model,{
    claim:"Experience replay can reduce catastrophic forgetting in continual learning systems when older examples are sampled during updates.",
    confidence:.5,now:110
  });
  model=p.model;
  let r=evaluateHypothesisEvidence(model,p.hypothesis.id,{
    text:"Experience replay reduces catastrophic forgetting by rehearsing older examples during continual learning.",
    citations:[
      {title:"Replay Study",url:"https://doi.org/10.1000/replay"},
      {title:"Replay Study",url:"https://publisher.example/replay"}
    ],now:120
  });
  model=r.model;
  assert.equal(r.hypothesis.status,"PROVISIONAL");
  r=evaluateHypothesisEvidence(model,p.hypothesis.id,{
    text:"Experience replay reduces catastrophic forgetting by rehearsing older examples during continual learning.",
    citations:[
      {title:"Replay Study",url:"https://doi.org/10.1000/replay"},
      {title:"Replay Study",url:"https://publisher.example/replay"}
    ],now:130
  });
  assert.equal(r.hypothesis.status,"PROVISIONAL");
  assert.equal(r.hypothesis.independentSupportEvents,1);
});

test("migration recalibrates a legacy one-cycle supported hypothesis back to provisional",()=>{
  let model=createWorldModel(100);
  model.schemaVersion=1;
  model.hypotheses=[{
    id:"hyp-legacy",
    claim:"Sequential learning can cause catastrophic forgetting in neural networks.",
    beliefIds:[],citationUrls:[],baseConfidence:.73,confidence:.95,status:"SUPPORTED",attempts:1,
    supportEvidence:[{id:"ev-old",classification:"SUPPORT",overlap:.5,domains:["doi.org","publisher.example"],text:"support",createdAt:120}],
    contradictEvidence:[],inconclusiveEvidence:[],createdAt:100,lastTestedAt:120,testQuestions:["test"]
  }];
  const migrated=(await import("./world-model.mjs")).migrateWorldModel(model,200);
  assert.equal(migrated.schemaVersion,2);
  assert.equal(migrated.hypotheses[0].status,"PROVISIONAL");
  assert.equal(migrated.hypotheses[0].independentSupportEvents,1);
});
