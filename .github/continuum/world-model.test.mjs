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
