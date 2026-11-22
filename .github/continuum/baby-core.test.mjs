import test from "node:test";
import assert from "node:assert/strict";
import {createBabyState,migrateBabyState,analyzeInstruction,observeInteraction,addOwnerRule,ingestEvidence,addGoal,refreshSkillCurriculum,retrieveMemory,evaluateMemoryRetrieval,selectGoal,recordOutcome,babyStatus,buildDeepResearchPlan} from "./baby-core.mjs";
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
  legacy.beliefs=[{id:"belief-old",text:"A broad claim about replay is not confirmed by any independent evidence and remains provisional until carefully revalidated.",confidence:.92,uncertainty:.08,status:"GROUNDED_OBSERVATION",citationUrls:["https://example.org/a"],createdAt:50,lastValidatedAt:50}];
  const migrated=migrateBabyState(legacy,200);
  assert.equal(migrated.schemaVersion,3);
  assert.equal(migrated.memoryRewardVersion,2);
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


test("deep research plan uses distinct focused search queries for interaction and metacognition",()=>{
  const plan=buildDeepResearchPlan("Wie lernt ein Assistent Sprache, Emotion, Planung und Gedächtnis?");
  const interaction=plan.find(x=>x.id==="interaction");
  const meta=plan.find(x=>x.id==="metacognition");
  assert.ok(interaction.searchQuery.includes("affective computing"));
  assert.ok(interaction.searchQuery.includes("dialogue pragmatics"));
  assert.ok(meta.searchQuery.includes("hierarchical planning"));
  assert.ok(meta.searchQuery.includes("memory retrieval"));
});

test("html extraction removes navigation chrome before learning",()=>{
  const page=extractPage('<html><head><title>Agent Paper</title></head><body><nav>Skip to main content Register Login</nav><header>Open Menu</header><main><p>Dialogue agents can adapt from explicit user feedback and calibrated uncertainty.</p></main><footer>Privacy Statement</footer></body></html>',
    "https://example.org/paper","text/html");
  assert.ok(page.text.includes("Dialogue agents"));
  assert.ok(!page.text.includes("Register Login"));
  assert.ok(!page.text.includes("Privacy Statement"));
});


test("baby memory migration removes navigation noise and merges citation-marker duplicates",()=>{
  const legacy=createBabyState(100);
  legacy.schemaVersion=2;
  legacy.beliefs=[
    {id:"a",text:"[1] Experience replay can reduce catastrophic forgetting during continual learning when older examples are rehearsed.",confidence:.7,status:"GROUNDED_OBSERVATION",citationUrls:["https://a.example/p"],supportCount:1,sourceDiversity:1,contradictionIds:[],createdAt:100,lastValidatedAt:100},
    {id:"b",text:"[2] Experience replay can reduce catastrophic forgetting during continual learning when older examples are rehearsed.",confidence:.75,status:"GROUNDED_OBSERVATION",citationUrls:["https://b.example/p"],supportCount:1,sourceDiversity:1,contradictionIds:[],createdAt:110,lastValidatedAt:110},
    {id:"noise",text:"Skip to main content Open Menu Current Archives Register Login Privacy Statement",confidence:.9,status:"GROUNDED_OBSERVATION",citationUrls:["https://noise.example"],supportCount:1,sourceDiversity:1,contradictionIds:[],createdAt:120,lastValidatedAt:120}
  ];
  legacy.skills["memory-retrieval"]={id:"memory-retrieval",trials:2,rewardSum:18,successCount:2,lastReward:9,lastAt:120};
  const migrated=migrateBabyState(legacy,200);
  assert.equal(migrated.schemaVersion,3);
  assert.equal(migrated.beliefs.length,1);
  assert.equal(migrated.beliefs[0].citationUrls.length,2);
  assert.equal(migrated.skills["memory-retrieval"].trials,0);
});

test("memory retrieval reward depends on relevance quality rather than raw hit count",()=>{
  const good=[
    {kind:"OWNER_RULE",score:2.5,confidence:1},
    {kind:"BELIEF",score:2.4,status:"GROUNDED_OBSERVATION",confidence:.8},
    {kind:"EPISODE",score:1.8,status:"SUCCESS"}
  ];
  const weak=Array.from({length:10},(_,i)=>({kind:"BELIEF",score:.15,status:"PROVISIONAL",confidence:.2,id:String(i)}));
  assert.ok(evaluateMemoryRetrieval(good)>evaluateMemoryRetrieval(weak));
  assert.ok(evaluateMemoryRetrieval([])<5);
});

test("grounded duplicate cannot inherit confidence, identity or citations from provisional row",()=>{
  const claim="Experience replay can reduce catastrophic forgetting during continual learning when older examples are rehearsed.";
  const unverified={
    id:"provisional-high",text:claim,confidence:.94,uncertainty:.06,
    status:"PROVISIONAL",citationUrls:["https://unverified.example/claim"],
    supportCount:1,sourceDiversity:0,contradictionIds:[],createdAt:100,lastValidatedAt:100
  };
  const supported={
    id:"grounded-verified",text:"[1] "+claim,confidence:.73,uncertainty:.27,
    status:"GROUNDED_OBSERVATION",citationUrls:["https://publisher.example/paper"],
    supportCount:2,sourceDiversity:2,contradictionIds:[],createdAt:120,lastValidatedAt:120
  };
  for(const beliefs of [[unverified,supported],[supported,unverified]]){
    const state=createBabyState(90);
    state.beliefs=structuredClone(beliefs);
    const snapshot=structuredClone(state);
    const migrated=migrateBabyState(state,140);
    assert.equal(migrated.beliefs.length,1);
    const belief=migrated.beliefs[0];
    assert.equal(belief.status,"GROUNDED_OBSERVATION");
    assert.equal(belief.id,"grounded-verified");
    assert.equal(belief.confidence,.73);
    assert.deepEqual(belief.citationUrls,["https://publisher.example/paper"]);
    assert.equal(belief.supportCount,2);
    assert.equal(belief.sourceDiversity,2);
    assert.equal(belief.createdAt,120);
    assert.equal(belief.lastValidatedAt,120);
    assert.deepEqual(state,snapshot,"migration must not change its input");
  }

  const onlyProvisional=createBabyState(90);
  onlyProvisional.beliefs=[unverified,{...unverified,id:"provisional-second",confidence:.42}];
  const deduplicated=migrateBabyState(onlyProvisional,140).beliefs;
  assert.equal(deduplicated.length,1);
  assert.equal(deduplicated[0].status,"PROVISIONAL");
});

test("evidence ingestion never grafts unverified citations onto verified same-claim support",()=>{
  const answer="Experience replay can reduce catastrophic forgetting during continual learning when older examples are rehearsed and outcomes are measured [1][2][3].";
  const unverified=[{url:"https://unverified.example/guess",source:"unverified",relevance:1}];
  const verified=[
    {url:"https://publisher.example/a",source:"paperA",relevance:4},
    {url:"https://publisher.example/b",source:"paperB",relevance:4},
    {url:"https://publisher.example/c",source:"paperC",relevance:4}
  ];
  let baby=createBabyState(50);
  baby=ingestEvidence(baby,{query:"replay",answer,citations:unverified,uncertainty:.95,now:100}).baby;
  assert.equal(baby.beliefs.length,1);
  assert.equal(baby.beliefs[0].status,"PROVISIONAL");

  baby=ingestEvidence(baby,{query:"replay",answer,citations:verified,uncertainty:.05,now:200}).baby;
  assert.equal(baby.beliefs.length,1);
  assert.equal(baby.beliefs[0].status,"GROUNDED_OBSERVATION");
  assert.deepEqual(baby.beliefs[0].citationUrls,verified.map(x=>x.url));
  const admitted=structuredClone(baby.beliefs[0]);

  baby=ingestEvidence(baby,{query:"replay",answer,citations:unverified,uncertainty:.95,now:300}).baby;
  assert.deepEqual(baby.beliefs[0].citationUrls,admitted.citationUrls);
  assert.equal(baby.beliefs[0].confidence,admitted.confidence);
  assert.equal(baby.beliefs[0].lastValidatedAt,admitted.lastValidatedAt);
  assert.equal(baby.beliefs[0].status,"GROUNDED_OBSERVATION");
});
