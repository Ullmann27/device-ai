import test from "node:test";
import assert from "node:assert/strict";
import {createBabyState,analyzeInstruction,ingestEvidence,addGoal,selectGoal,recordOutcome,babyStatus,buildDeepResearchPlan} from "./baby-core.mjs";
import {isBlockedAddress,extractPage} from "./public-web.mjs";

test("baby extracts interaction structure and constraints",()=>{
  const x=analyzeInstruction("Baue jetzt eine Recherchefunktion, aber ohne kostenpflichtige APIs und prüfe danach die Ergebnisse.");
  assert.equal(x.speechAct,"REQUEST");
  assert.ok(x.urgency>=0.8);
  assert.ok(x.constraints.some(v=>v.toLowerCase().includes("ohne")));
  assert.ok(x.clauses.length>=2);
});

test("baby turns evidence into bounded beliefs and epistemic goals",()=>{
  const state=createBabyState(100);
  const result=ingestEvidence(state,{
    query:"continual learning",
    answer:"Experience replay can reduce catastrophic forgetting in continual learning systems. Independent evaluations still show trade-offs between stability and plasticity.",
    citations:[
      {url:"https://example.org/a",source:"PaperA"},
      {url:"https://example.net/b",source:"PaperB"},
      {url:"https://example.com/c",source:"PaperC"}
    ],
    nextQuestions:["Which held-out benchmark best detects stability-plasticity regressions?"],
    now:200
  });
  const status=babyStatus(result.baby);
  assert.ok(status.beliefs>=1);
  assert.ok(status.openGoals>=1);
  assert.equal(result.baby.beliefs[0].citationUrls.length,3);
});

test("goal policy balances priority reward and exploration",()=>{
  let state=createBabyState(100);
  const a=addGoal(state,"Find independent evidence for replay retention","EVIDENCE_GAP",1,100);
  const b=addGoal(state,"Test a low priority formatting idea","OTHER",0.1,100);
  assert.equal(selectGoal(state,200).id,a.id);
  state=recordOutcome(state,{goalId:a.id,skills:["research"],success:true,reward:9,reflection:"Strong independent sources",now:300});
  assert.equal(state.skills.research.trials,1);
  assert.ok(buildDeepResearchPlan("How should memory work?").length>=4);
  assert.ok(b);
});

test("public web guard blocks local and reserved addresses",()=>{
  for(const ip of ["127.0.0.1","10.0.0.8","192.168.1.10","169.254.1.1","::1","fc00::1","2001:db8::1"])assert.equal(isBlockedAddress(ip),true);
  assert.equal(isBlockedAddress("8.8.8.8"),false);
  assert.equal(isBlockedAddress("1.1.1.1"),false);
});

test("html extraction strips active content and preserves public links",()=>{
  const page=extractPage('<html><head><title>Agent Memory</title><style>x{}</style></head><body><script>alert(1)</script><p>Persistent memory helps an agent learn from experience.</p><a href="/paper">Paper</a></body></html>',
    "https://example.org/start","text/html");
  assert.equal(page.title,"Agent Memory");
  assert.ok(page.text.includes("Persistent memory"));
  assert.ok(!page.text.includes("alert(1)"));
  assert.deepEqual(page.links,["https://example.org/paper"]);
});
