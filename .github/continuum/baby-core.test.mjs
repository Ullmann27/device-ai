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
  legacy.beliefs=[{id:"belief-old",text:"Experience replay can reduce catastrophic forgetting but this claim still lacks sufficient independent support.",confidence:.92,uncertainty:.08,status:"GROUNDED_OBSERVATION",citationUrls:["https://example.org/a"],createdAt:50,lastValidatedAt:50}];
  const migrated=migrateBabyState(legacy,200);
  assert.equal(migrated.schemaVersion,3);
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

test("retrospective audit quarantines unrelated historical research without destroying provenance",()=>{
  const state=createBabyState(100);
  state.beliefs=[
    {id:"physics-off-topic",text:"Rudolf Clausius formulated important thermodynamic statements about the nature of heat and entropy.",
      query:"How does an AI assistant improve continual learning and memory retrieval?",
      status:"GROUNDED_OBSERVATION",confidence:.92,citationUrls:["https://example.org/heat"],createdAt:110},
    {id:"replay-on-topic",text:"Experience replay can reduce forgetting during continual learning by reusing older training examples.",
      query:"How does an AI assistant improve continual learning and memory retrieval?",
      status:"GROUNDED_OBSERVATION",confidence:.91,citationUrls:["https://example.org/replay"],createdAt:115}
  ];
  const migrated=migrateBabyState(state,200);
  assert.deepEqual(migrated.beliefs.map(x=>x.id),["replay-on-topic"]);
  assert.equal(migrated.beliefs[0].status,"PROVISIONAL");
  assert.equal(migrated.quarantinedBeliefs[0].reason,"OFF_TOPIC");
  assert.equal(migrated.quarantinedBeliefs[0].citationUrls[0],"https://example.org/heat");
  assert.deepEqual(migrateBabyState(migrated,210).quarantinedBeliefs,migrated.quarantinedBeliefs);
});

test("new off-topic content stays quarantined even with several citation markers",()=>{
  const state=createBabyState(100);
  const result=ingestEvidence(state,{
    query:"continual learning memory replay in artificial intelligence",
    answer:"Rudolf Clausius developed theoretical work on classical heat engines and thermodynamic entropy [1].",
    citations:[{url:"https://example.org/heat",title:"Heat studies",source:"Paper"}],now:200
  });
  assert.equal(result.baby.beliefs.length,0);
  assert.equal(result.baby.quarantinedBeliefs.length,1);
  assert.equal(babyStatus(result.baby).quarantinedBeliefs,1);
});

test("uncited attribution and invalid evidence URLs cannot promote a claim",()=>{
  const citations=[
    {url:"https://research.example/one",source:"OpenAlex"},
    {url:"https://other.example/two",source:"OpenAlex"},
    {url:"javascript:alert(1)",source:"Unknown"}
  ];
  const r=ingestEvidence(createBabyState(100),{
    query:"continual learning catastrophic forgetting",
    answer:"Continual learning methods can reduce catastrophic forgetting by replaying carefully selected older examples.",
    citations,uncertainty:.05,now:200
  });
  assert.equal(r.baby.beliefs.length,1);
  assert.equal(r.baby.beliefs[0].status,"PROVISIONAL");
  assert.equal(r.baby.beliefs[0].sourceDiversity,2);
  assert.equal(r.baby.beliefs[0].citationUrls.length,2);
  assert.equal(r.baby.beliefs[0].evidenceMode,"ATTRIBUTED_UNVERIFIED");
});
