import test from "node:test";
import assert from "node:assert/strict";
import {createBabyState,migrateBabyState,analyzeInstruction,observeInteraction,addOwnerRule,ingestEvidence,addGoal,refreshSkillCurriculum,retrieveMemory,selectGoal,recordOutcome,babyStatus,buildDeepResearchPlan} from "./baby-core.mjs";
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


test("legacy overconfident beliefs are downgraded until revalidated",()=>{
  const legacy=createBabyState(100);
  legacy.schemaVersion=1;
  legacy.beliefs=[{id:"belief-old",text:"An old broad claim with insufficient precise support.",confidence:.92,uncertainty:.08,status:"GROUNDED_OBSERVATION",citationUrls:["https://example.org/a"],createdAt:50,lastValidatedAt:50}];
  const migrated=migrateBabyState(legacy,200);
  assert.equal(migrated.schemaVersion,2);
  assert.equal(migrated.beliefs[0].status,"PROVISIONAL");
  assert.ok(migrated.beliefs[0].confidence<=.55);
});

test("interaction profile and explicit owner rules learn without becoming web beliefs",()=>{
  let state=createBabyState(100);
  const observed=observeInteraction(state,"Bitte recherchiere das jetzt dringend, aber ohne kostenpflichtige APIs.",200);
  state=observed.baby;
  assert.equal(state.interactionProfile.total,1);
  assert.equal(observed.analysis.affect,"URGENT");
  const ruled=addOwnerRule(state,"Antworte auf Deutsch und nenne messbare Fortschritte.",300);
  assert.equal(ruled.baby.ownerRules.length,1);
  assert.equal(ruled.baby.beliefs.length,0);
});


test("weak skills autonomously create curriculum goals and competent skills close them",()=>{
  let state=createBabyState(100);
  state=refreshSkillCurriculum(state,110);
  assert.ok(state.goals.some(g=>g.origin==="SKILL_CURRICULUM:affect-understanding"&&g.status==="OPEN"));
  const goal=state.goals.find(g=>g.origin==="SKILL_CURRICULUM:affect-understanding");
  state=recordOutcome(state,{goalId:goal.id,skills:["affect-understanding"],success:true,reward:8,reflection:"first",now:120});
  state=recordOutcome(state,{goalId:goal.id,skills:["affect-understanding"],success:true,reward:9,reflection:"second",now:130});
  state=refreshSkillCurriculum(state,140);
  assert.equal(state.goals.find(g=>g.origin==="SKILL_CURRICULUM:affect-understanding").status,"SATISFIED");
});

test("memory retrieval ranks explicit owner rules and grounded beliefs without treating episodes as facts",()=>{
  let state=createBabyState(100);
  state=addOwnerRule(state,"Antworte bei technischen Fragen mit messbaren Testergebnissen.",110).baby;
  state=ingestEvidence(state,{
    query:"continual learning replay",
    answer:"Experience replay can reduce catastrophic forgetting during continual learning when older examples are rehearsed [1].",
    citations:[{title:"Replay",url:"https://example.org/replay",source:"paper"}],
    uncertainty:.15,now:120
  }).baby;
  state=recordOutcome(state,{skills:["research"],success:true,reward:8,reflection:"Replay research found useful retention evidence",now:130});
  const rows=retrieveMemory(state,"technischen Testergebnissen replay learning",8);
  assert.ok(rows.some(x=>x.kind==="OWNER_RULE"));
  assert.ok(rows.some(x=>x.kind==="BELIEF"));
  assert.ok(rows.some(x=>x.kind==="EPISODE"));
});
