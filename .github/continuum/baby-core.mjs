import crypto from "node:crypto";

const SKILL_CURRICULUM = {
  "instruction-decomposition":"Welche Methoden und Benchmarks verbessern die Zerlegung komplexer Benutzeraufträge in überprüfbare Teilziele?",
  "research":"Welche Methoden verbessern autonome Informationssuche, Quellenabdeckung und Rechercheeffizienz bei KI-Agenten?",
  "source-critique":"Welche Verfahren bewerten Quellenqualität, Widersprüche, Unabhängigkeit und Evidenzstärke robust?",
  "synthesis":"Welche Verfahren erzeugen aus mehreren Quellen präzise, widerspruchsbewusste und zitierbare Synthesen?",
  "communication":"Welche Verfahren verbessern adaptive, verständliche und nutzerorientierte Kommunikation persönlicher KI-Assistenten?",
  "dialogue-pragmatics":"Welche empirisch robusten Verfahren lernen Dialogpragmatik, Gesprächskontext und implizite Benutzerabsichten?",
  "affect-understanding":"Welche empirisch robusten Verfahren erkennen emotionale und soziale Signale in Textdialogen ohne sie zu überinterpretieren?",
  "planning":"Welche Verfahren verbessern hierarchische Planung, Aufgabenzerlegung, Selbstüberwachung und Zielauswahl bei KI-Agenten?",
  "memory-retrieval":"Welche Gedächtnisarchitekturen verbessern episodischen und semantischen Abruf, Relevanzranking und Langzeitkonsistenz bei KI-Agenten?",
  "self-check":"Welche Evaluator-, Verifier- und Self-Check-Verfahren erkennen Fehler und Regressionen bei autonomen KI-Agenten?"
};

const SKILLS = [
  "instruction-decomposition",
  "research",
  "source-critique",
  "synthesis",
  "communication",
  "dialogue-pragmatics",
  "affect-understanding",
  "planning",
  "memory-retrieval",
  "self-check"
];

export function createBabyState(now = Date.now()) {
  return {
    schemaVersion: 3,
    createdAt: now,
    updatedAt: now,
    beliefs: [],
    quarantinedBeliefs: [],
    goals: [],
    episodes: [],
    skills: Object.fromEntries(SKILLS.map(id => [id, {
      id, trials: 0, rewardSum: 0, successCount: 0, lastReward: null, lastAt: null
    }])),
    counters: { observations: 0, plans: 0, contradictions: 0 },
    interactionProfile: {
      total:0, meanLength:0, meanUrgency:0,
      speechActs:{}, affects:{}, constraints:{}
    },
    ownerRules: [],
    memoryRewardVersion:2
  };
}

export function migrateBabyState(value, now = Date.now()) {
  if (!value) return createBabyState(now);
  const state = structuredClone(value);
  if(state.schemaVersion===1){
    state.schemaVersion=2;
    state.beliefs=(Array.isArray(state.beliefs)?state.beliefs:[]).map(b=>{
      if(Number.isFinite(b.supportCount))return b;
      const confidence=Math.min(.55,Number(b.confidence??.4));
      return {...b,confidence,uncertainty:1-confidence,status:"PROVISIONAL",
        supportCount:Array.isArray(b.citationUrls)?Math.min(3,b.citationUrls.length):0,
        sourceDiversity:0};
    });
    state.interactionProfile={total:0,meanLength:0,meanUrgency:0,speechActs:{},affects:{},constraints:{}};
    state.ownerRules=[];
  }
  if(state.schemaVersion===2){
    state.schemaVersion=3;
    state.memoryRewardVersion=2;
    if(state.skills?.["memory-retrieval"]){
      state.skills["memory-retrieval"]={
        id:"memory-retrieval",trials:0,rewardSum:0,successCount:0,lastReward:null,lastAt:null
      };
    }
  }
  if(state.schemaVersion!==3)return createBabyState(now);
  if(state.memoryRewardVersion!==2){
    state.memoryRewardVersion=2;
    if(state.skills?.["memory-retrieval"]){
      state.skills["memory-retrieval"]={
        id:"memory-retrieval",trials:0,rewardSum:0,successCount:0,lastReward:null,lastAt:null
      };
    }
  }
  // Re-audit old evidence on every load. Quarantined material is retained for review,
  // not silently converted into trusted knowledge or dropped without an audit trail.
  state.quarantinedBeliefs = Array.isArray(state.quarantinedBeliefs)
    ? state.quarantinedBeliefs.slice(-64) : [];
  const reviewed = [];
  for (const belief of consolidateBeliefs(Array.isArray(state.beliefs) ? state.beliefs : [])) {
    const rejection = beliefQualityReason(belief.query, belief.text);
    if (rejection) {
      state.quarantinedBeliefs = quarantineBelief(state.quarantinedBeliefs, belief, rejection, now);
      continue;
    }
    // Older confidence scores were based on source counts, not on an explicit claim
    // citation. Recalibrate once; repeated migrations must be idempotent.
    if (belief.status === "GROUNDED_OBSERVATION" && belief.evidenceMode !== "EXPLICIT_CITATION") {
      belief.status = "PROVISIONAL";
      belief.confidence = Math.min(Number(belief.confidence ?? .4), .62);
      belief.uncertainty = 1 - belief.confidence;
      belief.evidenceMode = "LEGACY_UNVERIFIED";
    }
    reviewed.push(belief);
  }
  state.beliefs = reviewed.slice(-256);
  state.goals = Array.isArray(state.goals) ? state.goals.slice(-128) : [];
  state.episodes = Array.isArray(state.episodes) ? state.episodes.slice(-128) : [];
  state.skills = state.skills && typeof state.skills === "object" ? state.skills : {};
  for (const id of SKILLS) {
    if (!state.skills[id]) state.skills[id] = {
      id, trials: 0, rewardSum: 0, successCount: 0, lastReward: null, lastAt: null
    };
  }
  state.counters = state.counters ?? { observations: 0, plans: 0, contradictions: 0 };
  state.interactionProfile=state.interactionProfile??{total:0,meanLength:0,meanUrgency:0,speechActs:{},affects:{},constraints:{}};
  state.ownerRules=Array.isArray(state.ownerRules)?state.ownerRules.slice(-32):[];
  return state;
}

export function observeInteraction(babyInput,text,now=Date.now()){
  const baby=migrateBabyState(babyInput,now);
  const analysis=analyzeInstruction(text);
  const p=baby.interactionProfile;
  const n=p.total;
  p.total=n+1;
  p.meanLength=(p.meanLength*n+String(text).length)/(n+1);
  p.meanUrgency=(p.meanUrgency*n+analysis.urgency)/(n+1);
  p.speechActs[analysis.speechAct]=(p.speechActs[analysis.speechAct]??0)+1;
  p.affects[analysis.affect]=(p.affects[analysis.affect]??0)+1;
  for(const constraint of analysis.constraints){
    const key=clean(constraint.toLocaleLowerCase("de-DE"),80);
    p.constraints[key]=(p.constraints[key]??0)+1;
  }
  baby.updatedAt=now;
  return {baby,analysis};
}

export function addOwnerRule(babyInput,text,now=Date.now()){
  const baby=migrateBabyState(babyInput,now);
  const cleaned=clean(text,600);
  if(cleaned.length<5)return {baby,rule:null};
  const id="rule-"+sha256(cleaned.toLocaleLowerCase("de-DE")).slice(0,20);
  let rule=baby.ownerRules.find(x=>x.id===id);
  if(!rule){
    rule={id,text:cleaned,createdAt:now,provenance:"USER_EXPLICIT",active:true};
    baby.ownerRules.push(rule);
    baby.ownerRules=baby.ownerRules.slice(-32);
  }
  baby.updatedAt=now;
  return {baby,rule};
}

export function analyzeInstruction(text) {
  const cleaned = clean(text, 1600);
  const lower = cleaned.toLocaleLowerCase("de-DE");
  const speechAct = /^\s*(wer|was|wie|wo|wann|warum|wieso|welche|kann|könnt|ist|sind|hat|haben)\b/.test(lower) || /\?$/.test(cleaned)
    ? "QUESTION"
    : /\b(mach|baue|erstelle|suche|prüfe|recherchiere|entwickle|zeige|erkläre|analysiere|finde|lerne)\b/.test(lower)
      ? "REQUEST"
      : /\b(gut|super|richtig|falsch|nein|ja|besser|schlechter|feedback)\b/.test(lower)
        ? "FEEDBACK"
        : "STATEMENT";
  const affect = /\b(wütend|ärger|schei|verdammt|frustriert|genervt)\b/.test(lower)
    ? "FRUSTRATED"
    : /\b(super|freu|gut|stark|toll|perfekt)\b/.test(lower)
      ? "POSITIVE"
      : /\b(dringend|sofort|jetzt|unbedingt|schnell)\b/.test(lower)
        ? "URGENT"
        : "NEUTRAL";
  const urgency = /\b(sofort|jetzt|dringend|unbedingt|gleich)\b/.test(lower) ? 0.9 : 0.35;
  const constraints = [];
  for (const pattern of [
    /\bohne\s+[^,.!?;]{2,100}/gi,
    /\bkeine?n?\s+[^,.!?;]{2,100}/gi,
    /\bmuss\s+[^,.!?;]{2,100}/gi,
    /\bdarf\s+nicht\s+[^,.!?;]{2,100}/gi,
    /\bnur\s+[^,.!?;]{2,100}/gi
  ]) {
    for (const match of cleaned.matchAll(pattern)) constraints.push(clean(match[0], 120));
  }
  const clauses = cleaned.split(/(?:[.;!?]|\bund\s+dann\b|\bdanach\b|\baußerdem\b)/i)
    .map(x => clean(x, 260)).filter(x => x.length >= 8).slice(0, 8);
  return {
    speechAct, affect, urgency,
    constraints: [...new Set(constraints)].slice(0, 8),
    clauses,
    complexity: Math.min(1, (clauses.length + constraints.length * 1.5 + cleaned.length / 220) / 10)
  };
}

export function buildDeepResearchPlan(question) {
  const q = clean(question, 600);
  const lower=q.toLocaleLowerCase("de-DE");
  const plan=[
    {
      id:"mechanism",
      question:q + " — Welche Mechanismen und Architekturen funktionieren nachweislich?",
      searchQuery:"personal AI assistant continual learning architecture adaptive memory online learning",
      skill:"research"
    },
    {
      id:"evidence",
      question:q + " — Welche starken empirischen Ergebnisse und Benchmarks gibt es?",
      searchQuery:"AI agent continual adaptation benchmark retention generalization calibration evaluation",
      skill:"source-critique"
    },
    {
      id:"failure",
      question:q + " — Welche Grenzen, Fehlschläge, Gegenbeispiele und offenen Probleme sind bekannt?",
      searchQuery:"adaptive AI agent failure modes regression negative results benchmark continual learning",
      skill:"source-critique"
    },
    {
      id:"implementation",
      question:q + " — Welche konkreten Implementierungsbausteine lassen sich daraus ableiten?",
      searchQuery:"personal AI assistant architecture memory active learning planner verifier implementation",
      skill:"instruction-decomposition"
    }
  ];
  if(/sprache|dialog|emotion|benutzer|kommunik|grammar|language|conversation/.test(lower)){
    plan.push({
      id:"interaction",
      question:q+" — Welche Methoden für Dialogpragmatik, Benutzerfeedback, Emotionssignale und natürliche Interaktion sind empirisch belastbar?",
      searchQuery:"dialogue pragmatics affective computing conversational agents user modeling user feedback preference learning emotion recognition benchmark",
      skill:"dialogue-pragmatics"
    });
  }
  if(/plan|denken|zerleg|reason|selbst|ziel|entscheidung|gedächtnis|memory|unsicher/.test(lower)){
    plan.push({
      id:"metacognition",
      question:q+" — Welche Methoden für Aufgabenzerlegung, Selbstüberwachung, Planung, Memory-Retrieval und Wahl des nächsten Lernziels sind empirisch belastbar?",
      searchQuery:"LLM agents hierarchical planning task decomposition metacognitive monitoring memory retrieval uncertainty calibration goal selection benchmark",
      skill:"planning"
    });
  }
  return plan.slice(0,6);
}

export function ingestEvidence(babyInput, {
  query, answer, citations = [], uncertainty = null, nextQuestions = [], now = Date.now()
}) {
  const baby = migrateBabyState(babyInput, now);
  const sourceDiversity = new Set(citations.map(x => domainOf(x.url)).filter(Boolean)).size;
  const evidenceFactor = Math.min(0.52, citations.length * 0.055 + sourceDiversity * 0.045);
  const inferredUncertainty = uncertainty == null
    ? Math.max(0.12, 0.72 - evidenceFactor)
    : clamp(Number(uncertainty), 0.05, 0.95);
  const sentences = splitClaims(answer).slice(0, 10);
  const added = [];
  for (const sentence of sentences) {
    const quality = beliefQualityReason(query, sentence);
    if (quality) {
      baby.quarantinedBeliefs = quarantineBelief(baby.quarantinedBeliefs, {
        id: "belief-" + sha256(sentence.toLocaleLowerCase("de-DE")).slice(0, 20),
        text: sentence, query
      }, quality, now);
      continue;
    }
    const normalized = sentence.toLocaleLowerCase("de-DE");
    const id = "belief-" + sha256(normalized).slice(0, 20);
    const existing=baby.beliefs.find(x=>x.id===id);
    const refIndices=[...sentence.matchAll(/\[(\d{1,2})\]/g)]
      .map(m=>Number(m[1])-1).filter(i=>i>=0&&i<citations.length);
    const supported=refIndices.length
      ? [...new Set(refIndices)].map(i=>citations[i]).filter(x=>isValidCitationUrl(x?.url))
      : citations.slice(0,Math.min(3,citations.length)).filter(x=>isValidCitationUrl(x?.url));
    const contradictionIds = baby.beliefs
      .filter(old => looksContradictory(old.text, sentence))
      .map(x => x.id).slice(0, 6);
    const diversity=new Set(supported.map(x=>domainOf(x.url)).filter(Boolean)).size;
    const meanRelevance=supported.length
      ? supported.reduce((n,x)=>n+Math.min(6,Number(x.relevance??2)),0)/supported.length : 0;
    const markerPenalty=refIndices.length?0:0.16;
    const supportGain=Math.min(.28,supported.length*.055+diversity*.04+meanRelevance*.018);
    const confidence = clamp(.56 + supportGain - inferredUncertainty*.34 - markerPenalty -
      contradictionIds.length * 0.08, 0.08, 0.92);
    const candidate={
      id,
      text: sentence.replace(/^#+\s*/,""),
      query: clean(query, 400),
      confidence,
      uncertainty: 1 - confidence,
      status: confidence >= 0.68 && supported.length>0 && refIndices.length>0
        ? "GROUNDED_OBSERVATION" : "PROVISIONAL",
      evidenceMode: refIndices.length && supported.length>0 ? "EXPLICIT_CITATION" : "ATTRIBUTED_UNVERIFIED",
      citationUrls: supported.map(x => x.url).slice(0, 8),
      supportCount:supported.length,
      sourceDiversity:diversity,
      contradictionIds,
      createdAt: existing?.createdAt??now,
      lastValidatedAt: now
    };
    if(existing){
      const mergedUrls=[...new Set([...(existing.citationUrls??[]),...candidate.citationUrls])].slice(0,8);
      Object.assign(existing,candidate,{
        confidence:Math.max(Number(existing.confidence??0),candidate.confidence),
        uncertainty:1-Math.max(Number(existing.confidence??0),candidate.confidence),
        status:(existing.evidenceMode==="EXPLICIT_CITATION"&&existing.status==="GROUNDED_OBSERVATION")||
          candidate.status==="GROUNDED_OBSERVATION" ? "GROUNDED_OBSERVATION":"PROVISIONAL",
        evidenceMode:candidate.evidenceMode==="EXPLICIT_CITATION"||existing.evidenceMode==="EXPLICIT_CITATION"
          ? "EXPLICIT_CITATION":"ATTRIBUTED_UNVERIFIED",
        citationUrls:mergedUrls,
        supportCount:Math.max(Number(existing.supportCount??0),candidate.supportCount),
        sourceDiversity:Math.max(Number(existing.sourceDiversity??0),candidate.sourceDiversity)
      });
    }else{
      baby.beliefs.push(candidate);
      added.push(id);
    }
    baby.counters.contradictions += contradictionIds.length;
  }
  baby.counters.observations += 1;
  const goals = [];
  for (const question of nextQuestions) {
    const goal = addGoal(baby, question, "EVIDENCE_GAP", inferredUncertainty + 0.3, now);
    if (goal) goals.push(goal.id);
  }
  if (citations.length < 2) {
    const goal = addGoal(baby, "Finde unabhängige Gegen- oder Bestätigungsquellen für: " + clean(query, 280),
      "SOURCE_DIVERSITY", 0.9, now);
    if (goal) goals.push(goal.id);
  }
  if (baby.counters.contradictions > 0) {
    const goal = addGoal(baby, "Prüfe erkannte widersprüchliche Aussagen rund um: " + clean(query, 280),
      "CONTRADICTION", 1.0, now);
    if (goal) goals.push(goal.id);
  }
  baby.beliefs = baby.beliefs.slice(-256);
  baby.goals = baby.goals.slice(-128);
  baby.updatedAt = now;
  return { baby, addedBeliefIds: added, addedGoalIds: goals };
}

export function addGoal(babyInput, text, origin = "USER", priority = 0.6, now = Date.now()) {
  const baby = babyInput;
  const cleaned = clean(text, 500);
  if (cleaned.length < 8) return null;
  const id = "goal-" + sha256(cleaned.toLocaleLowerCase("de-DE")).slice(0, 20);
  const existing = baby.goals.find(x => x.id === id);
  if (existing) {
    existing.priority = Math.max(existing.priority, clamp(priority, 0, 1.5));
    return existing;
  }
  const goal = {
    id, text: cleaned, origin,
    priority: clamp(priority, 0, 1.5),
    attempts: 0,
    rewardSum: 0,
    status: "OPEN",
    createdAt: now,
    lastTriedAt: null
  };
  baby.goals.push(goal);
  return goal;
}

export function refreshSkillCurriculum(babyInput,now=Date.now()){
  const baby=migrateBabyState(babyInput,now);
  for(const [skillId,question] of Object.entries(SKILL_CURRICULUM)){
    const skill=baby.skills[skillId];
    const mean=skill?.trials ? skill.rewardSum/skill.trials : null;
    const origin="SKILL_CURRICULUM:"+skillId;
    const existing=baby.goals.find(g=>g.origin===origin);
    const competent=(skill?.trials??0)>=2 && mean!==null && mean>=7.5;
    if(competent){
      if(existing)existing.status="SATISFIED";
      continue;
    }
    const priority=(skill?.trials??0)===0 ? 1.45 :
      (skill?.trials??0)<2 ? 1.2 :
      mean!==null&&mean<6.5 ? 1.35 : .95;
    if(existing){
      existing.status="OPEN";
      existing.priority=Math.max(existing.priority,priority);
    }else{
      addGoal(baby,question,origin,priority,now);
    }
  }
  baby.updatedAt=now;
  return baby;
}

export function evaluateMemoryRetrieval(rows){
  if(!Array.isArray(rows)||!rows.length)return 2;
  const top=rows.slice(0,5);
  const meanScore=top.reduce((n,x)=>n+Math.min(3,Number(x.score??0)),0)/top.length;
  const kinds=new Set(top.map(x=>x.kind)).size;
  const grounded=top.filter(x=>x.kind==="BELIEF"&&x.status==="GROUNDED_OBSERVATION").length;
  const provisional=top.filter(x=>x.kind==="BELIEF"&&x.status==="PROVISIONAL").length;
  return clamp(3+meanScore*.9+kinds*.45+grounded*.3-provisional*.12,2,9);
}

export function retrieveMemory(babyInput,query,limit=6){
  const baby=migrateBabyState(babyInput);
  const q=tokens(query);
  const scoreText=(text)=>{
    const t=tokens(text);
    if(!q.size||!t.size)return 0;
    const overlap=[...q].filter(x=>t.has(x)).length;
    return overlap/q.size;
  };
  const rows=[];
  for(const rule of baby.ownerRules.filter(x=>x.active)){
    rows.push({kind:"OWNER_RULE",id:rule.id,text:rule.text,score:.75+scoreText(rule.text)*1.8,confidence:1});
  }
  for(const belief of baby.beliefs){
    const overlap=scoreText(belief.text);
    if(overlap<=0)continue;
    rows.push({
      kind:"BELIEF",id:belief.id,text:belief.text,
      score:overlap*2+Number(belief.confidence??.3)*.65+(belief.status==="GROUNDED_OBSERVATION"?.25:0),
      confidence:Number(belief.confidence??.3),status:belief.status
    });
  }
  for(const episode of baby.episodes){
    const overlap=scoreText(episode.reflection);
    if(overlap<=0)continue;
    rows.push({
      kind:"EPISODE",id:episode.id,text:episode.reflection,
      score:overlap*1.6+Number(episode.reward??0)/20,
      confidence:null,status:episode.success?"SUCCESS":"FAILURE"
    });
  }
  for(const goal of baby.goals.filter(x=>x.status==="OPEN")){
    const overlap=scoreText(goal.text);
    if(overlap<=0)continue;
    rows.push({kind:"OPEN_GOAL",id:goal.id,text:goal.text,score:overlap*1.7+goal.priority*.25,confidence:null,status:goal.status});
  }
  return rows.sort((a,b)=>b.score-a.score||a.id.localeCompare(b.id)).slice(0,Math.max(1,Math.min(12,limit)));
}

export function selectGoal(babyInput, now = Date.now()) {
  const baby = migrateBabyState(babyInput, now);
  return baby.goals.filter(x => x.status === "OPEN").map(goal => {
    const age = goal.lastTriedAt ? Math.min(1, (now - goal.lastTriedAt) / 86400000) : 1;
    const mean = goal.attempts ? goal.rewardSum / goal.attempts : 0;
    const exploration = goal.attempts ? Math.sqrt(2 * Math.log(2 + baby.counters.plans) / goal.attempts) : 2;
    return { goal, score: goal.priority + age * 0.35 + mean * 0.18 + exploration * 0.22 };
  }).sort((a,b) => b.score - a.score || a.goal.id.localeCompare(b.goal.id))[0]?.goal ?? null;
}

export function recordOutcome(babyInput, {
  goalId = null, skills = [], success = false, reward = 0, reflection = "", now = Date.now()
}) {
  const baby = migrateBabyState(babyInput, now);
  const boundedReward = clamp(Number(reward), 0, 10);
  if (goalId) {
    const goal = baby.goals.find(x => x.id === goalId);
    if (goal) {
      goal.attempts += 1;
      goal.rewardSum += boundedReward;
      goal.lastTriedAt = now;
      if (success && boundedReward >= 6.5) goal.status = "SATISFIED";
    }
  }
  for (const id of skills) {
    const skill = baby.skills[id] ?? (baby.skills[id] = {
      id, trials: 0, rewardSum: 0, successCount: 0, lastReward: null, lastAt: null
    });
    skill.trials += 1;
    skill.rewardSum += boundedReward;
    skill.successCount += success ? 1 : 0;
    skill.lastReward = boundedReward;
    skill.lastAt = now;
  }
  baby.counters.plans += 1;
  baby.episodes.push({
    id:"episode-" + sha256([String(now),goalId ?? "",reflection].join("\0")).slice(0,20),
    goalId,
    success:Boolean(success),
    reward:boundedReward,
    reflection:clean(reflection, 800),
    createdAt:now
  });
  baby.episodes = baby.episodes.slice(-128);
  baby.updatedAt = now;
  return baby;
}

export function babyStatus(babyInput) {
  const baby = migrateBabyState(babyInput);
  return {
    beliefs:baby.beliefs.length,
    quarantinedBeliefs:baby.quarantinedBeliefs.length,
    grounded:baby.beliefs.filter(x=>x.status==="GROUNDED_OBSERVATION").length,
    provisional:baby.beliefs.filter(x=>x.status==="PROVISIONAL").length,
    contradictions:baby.counters.contradictions,
    openGoals:baby.goals.filter(x=>x.status==="OPEN").length,
    satisfiedGoals:baby.goals.filter(x=>x.status==="SATISFIED").length,
    episodes:baby.episodes.length,
    skills:Object.values(baby.skills).map(s=>({
      id:s.id,
      trials:s.trials,
      meanReward:s.trials ? s.rewardSum/s.trials : null,
      successRate:s.trials ? s.successCount/s.trials : null
    })),
    interactionProfile:baby.interactionProfile,
    ownerRules:baby.ownerRules.filter(x=>x.active).length,
    curriculumOpen:baby.goals.filter(x=>x.status==="OPEN"&&String(x.origin).startsWith("SKILL_CURRICULUM:")).length
  };
}

const TOPIC_STOPWORDS = new Set([
  "welche","welcher","welches","methoden","method","methods","empirisch","empirical",
  "sollte","sollen","können","könnte","unter","diese","dieser","wurde","werden",
  "einen","einer","eine","nicht","durch","gegen","ohne","dabei","between",
  "which","should","could","would","their","about","there","using","with",
  "from","does","have","that","these","those","robust","evidence","based",
  "improve","improves","improving","vergleichen","verbessern","mehreren",
  "systemen","system","systems","approach","approaches","starke","strong"
]);

function topicTokens(value) {
  const words = String(value??"").toLocaleLowerCase("de-DE")
    .normalize("NFKC").split(/[^\\p{L}\\p{N}]+/u)
    .filter(x => x.length >= 5 && !TOPIC_STOPWORDS.has(x));
  return new Set(words.map(x => {
    if (/^(lern|learning|learner|trainier|training)/.test(x)) return "learning";
    if (/^(gedächtn|memory|retriev|erinner)/.test(x)) return "memory";
    if (/^(agent|assistent|assistant)/.test(x)) return "assistant";
    if (/^(dialog|gespräch|conversation)/.test(x)) return "dialogue";
    if (/^(forsch|research|recherch)/.test(x)) return "research";
    if (/^(vergess|forget)/.test(x)) return "forgetting";
    if (/^(unsicher|uncertaint)/.test(x)) return "uncertainty";
    if (/^(plan|planung)/.test(x)) return "planning";
    return x;
  }));
}

function beliefQualityReason(query,text) {
  if (!sanitizeBeliefText(text)) return "NON_CLAIM_OR_NAVIGATION";
  const q = topicTokens(query);
  // A missing legacy query must not cause irreversible removal.
  if (q.size < 2) return null;
  const claim = topicTokens(text);
  if (![...q].some(token => claim.has(token))) return "OFF_TOPIC";
  return null;
}

function quarantineBelief(previous, belief, reason, now) {
  const items = Array.isArray(previous) ? previous.slice(-64) : [];
  const id = String(belief.id ?? "belief-" + sha256(String(belief.text))).slice(0,80);
  if (!items.some(item=>item.id===id && item.reason===reason)) {
    items.push({
      id, reason, query:clean(belief.query,240), text:clean(belief.text,500),
      citationUrls:Array.isArray(belief.citationUrls)?belief.citationUrls.slice(0,6):[],
      auditedAt:now
    });
  }
  return items.slice(-64);
}

function isValidCitationUrl(value) {
  try {
    const url = new URL(String(value??""));
    return ["http:","https:"].includes(url.protocol) && !url.username && !url.password;
  } catch { return false; }
}

function consolidateBeliefs(values){
  const byKey=new Map();
  for(const raw of values){
    const text=sanitizeBeliefText(raw?.text);
    if(!text)continue;
    const key=text.toLocaleLowerCase("de-DE").replace(/[^\p{L}\p{N}]+/gu," ").trim();
    if(!key)continue;
    const candidate={...raw,text};
    const existing=byKey.get(key);
    if(!existing){
      byKey.set(key,candidate);
      continue;
    }
    const existingConfidence=Number(existing.confidence??0);
    const candidateConfidence=Number(candidate.confidence??0);
    const strongest=candidateConfidence>existingConfidence?candidate:existing;
    const weaker=strongest===candidate?existing:candidate;
    strongest.citationUrls=[...new Set([...(strongest.citationUrls??[]),...(weaker.citationUrls??[])])].slice(0,8);
    strongest.supportCount=Math.max(Number(strongest.supportCount??0),Number(weaker.supportCount??0));
    strongest.sourceDiversity=Math.max(Number(strongest.sourceDiversity??0),Number(weaker.sourceDiversity??0));
    strongest.contradictionIds=[...new Set([...(strongest.contradictionIds??[]),...(weaker.contradictionIds??[])])].slice(0,8);
    if(existing.status==="GROUNDED_OBSERVATION"||candidate.status==="GROUNDED_OBSERVATION")
      strongest.status="GROUNDED_OBSERVATION";
    strongest.createdAt=Math.min(Number(existing.createdAt??Infinity),Number(candidate.createdAt??Infinity));
    strongest.lastValidatedAt=Math.max(Number(existing.lastValidatedAt??0),Number(candidate.lastValidatedAt??0));
    byKey.set(key,strongest);
  }
  return [...byKey.values()].sort((a,b)=>(a.createdAt??0)-(b.createdAt??0));
}

function sanitizeBeliefText(value){
  const text=String(value??"")
    .replace(/^\s*\[\d{1,2}\]\s*/,"")
    .replace(/^\s*#+\s*(mechanism|evidence|failure|implementation|interaction|metacognition)\s*/i,"")
    .replace(/\s+/g," ").trim();
  if(!text)return null;
  if(/skip to main|main navigation|site footer|open menu|current archives|privacy statement|register login|home\s*\/\s*archives/i.test(text))return null;
  const words=text.split(/\s+/).filter(Boolean);
  const predicate=/\b(is|are|can|may|improves?|reduces?|shows?|finds?|allows?|requires?|learns?|prevents?|causes?|ist|sind|kann|können|zeigt|verbessert|reduziert|ermöglicht|verhindert|verursacht|lernt)\b/i.test(text);
  if(words.length<12&&!predicate)return null;
  return text.slice(0,650);
}

function splitClaims(text) {
  return String(text).split(/(?<=[.!?])\s+/)
    .map(x=>clean(x,500))
    .filter(x=>x.length>=45 && x.length<=500)
    .filter(x=>x.split(/\s+/).length>=7)
    .filter(x=>!/^#+\s*[^.!?]+$/.test(x))
    .filter((x,i,a)=>a.indexOf(x)===i);
}

function looksContradictory(a,b) {
  const A=tokens(a), B=tokens(b);
  const union=new Set([...A,...B]);
  const inter=[...A].filter(x=>B.has(x)).length;
  const similarity=union.size ? inter/union.size : 0;
  if(similarity<0.45)return false;
  return hasNegation(a)!==hasNegation(b);
}
function hasNegation(text){return /\b(nicht|kein|keine|never|no|not|cannot|can't|fails?|unmöglich)\b/i.test(text);}
function tokens(text){return new Set(String(text).toLocaleLowerCase("de-DE").split(/[^\p{L}\p{N}]+/u).filter(x=>x.length>=4));}
function domainOf(url){try{return new URL(url).hostname;}catch{return "";}}
function sha256(value){return crypto.createHash("sha256").update(String(value)).digest("hex");}
function clean(value,max){return String(value??"").replace(/[\u0000-\u001f\u007f]/g," ").replace(/\s+/g," ").trim().slice(0,max);}
function clamp(v,min,max){return Math.min(max,Math.max(min,Number.isFinite(v)?v:min));}
