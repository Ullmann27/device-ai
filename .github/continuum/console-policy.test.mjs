import test from "node:test";
import assert from "node:assert/strict";
import {isOwnerComment} from "./console-policy.mjs";
import {createBabyState,ingestEvidence,babyStatus,migrateBabyState} from "./baby-core.mjs";

test("only repository owner comments may trigger learning and memory mutations",()=>{
  assert.equal(isOwnerComment("Ullmann27/device-ai","Ullmann27"),true);
  assert.equal(isOwnerComment("Ullmann27/device-ai","ullmann27"),true);
  for (const login of ["stranger","github-actions[bot]","Ullmann27-similar","","admin"]) {
    assert.equal(isOwnerComment("Ullmann27/device-ai",login),false);
  }
  assert.equal(isOwnerComment("invalid-repository","Ullmann27"),false);
  assert.equal(isOwnerComment("Ullmann27/device-ai/other","Ullmann27"),false);
});

test("evidence quality counters distinguish raw source attribution from explicit citations",()=>{
  let state=createBabyState(100);
  state=ingestEvidence(state,{
    query:"continual learning experience replay",
    answer:"Continual learning systems can use experience replay to rehearse stored examples across sequential tasks.",
    citations:[{url:"https://example.org/replay"}],uncertainty:.1,now:200
  }).baby;
  let status=babyStatus(state);
  assert.equal(status.explicitlyCited,0);
  assert.equal(status.unattributedOrLegacy,1);
  state=ingestEvidence(state,{
    query:"continual learning replay",
    answer:"Continual learning can use replay to reduce forgetting during sequential adaptation [1].",
    citations:[{url:"https://example.org/replay",title:"Learning replay"}],uncertainty:.05,now:210
  }).baby;
  status=babyStatus(state);
  assert.equal(status.beliefs,2);
  assert.equal(status.explicitlyCited,1);
  assert.equal(status.unattributedOrLegacy,1);
  assert.equal(migrateBabyState(state,300).beliefs.length,2);
});
