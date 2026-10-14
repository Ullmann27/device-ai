import crypto from "node:crypto";

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
    schemaVersion: 1,
    createdAt: now,
    updatedAt: now,
    beliefs: [],
    goals: [],
    episodes: [],
    skills: Object.fromEntries(SKILLS.map(id => [id, {
      id, trials: 0, rewardSum: 0, successCount: 0, lastReward: null, lastAt: null
    }])),
    counters: { observations: 0, plans: 0, contradictions: 0 }
  };
}

export function migrateBabyState(value, now = Date.now()) {
  if (!value || value.schemaVersion !== 1) return createBabyState(now);
  const state = structuredClone(value);
  state.beliefs = Array.isArray(state.beliefs) ? state.beliefs.slice(-256) : [];
  state.goals = Array.isArray(state.goals) ? state.goals.slice(-128) : [];
  state.episodes = Array.isArray(state.episodes) ? state.episodes.slice(-128) : [];
  state.skills = state.skills && typeof state.skills === "object" ? state.skills : {};
  for (const id of SKILLS) {
    if (!state.skills[id]) state.skills[id] = {
      id, trials: 0, rewardSum: 0, successCount: 0, lastReward: null, lastAt: null
    };
  }
  state.counters = state.counters ?? { observations: 0, plans: 0, contradictions: 0 };
  return state;
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
    { id:"mechanism", question:q + " — Welche Mechanismen und Architekturen funktionieren nachweislich?", skill:"research" },
    { id:"evidence", question:q + " — Welche starken empirischen Ergebnisse und Benchmarks gibt es?", skill:"source-critique" },
    { id:"failure", question:q + " — Welche Grenzen, Fehlschläge, Gegenbeispiele und offenen Probleme sind bekannt?", skill:"source-critique" },
    { id:"implementation", question:q + " — Welche konkreten Implementierungsbausteine lassen sich daraus ableiten?", skill:"instruction-decomposition" }
  ];
  if(/sprache|dialog|emotion|benutzer|kommunik|grammar|language|conversation/.test(lower)){
    plan.push({id:"interaction",question:q+" — Welche Methoden für Dialogpragmatik, Benutzerfeedback, Emotionssignale und natürliche Interaktion sind empirisch belastbar?",skill:"dialogue-pragmatics"});
  }
  if(/plan|denken|zerleg|reason|selbst|ziel|entscheidung/.test(lower)){
    plan.push({id:"metacognition",question:q+" — Welche Methoden für Aufgabenzerlegung, Selbstüberwachung, Planung und Wahl des nächsten Lernziels sind empirisch belastbar?",skill:"planning"});
  }
  return plan.slice(0,6);
}

export function ingestEvidence(babyInput, {
  query, answer, citations = [], uncertainty = null, nextQuestions = [], now = Date.now()
}) {
  const baby = migrateBabyState(babyInput, now);
  const sourceDiversity = new Set(citations.map(x => x.source ?? domainOf(x.url))).size;
  const evidenceFactor = Math.min(0.52, citations.length * 0.055 + sourceDiversity * 0.045);
  const inferredUncertainty = uncertainty == null
    ? Math.max(0.12, 0.72 - evidenceFactor)
    : clamp(Number(uncertainty), 0.05, 0.95);
  const sentences = splitClaims(answer).slice(0, 10);
  const added = [];
  for (const sentence of sentences) {
    const normalized = sentence.toLocaleLowerCase("de-DE");
    const id = "belief-" + sha256(normalized).slice(0, 20);
    if (baby.beliefs.some(x => x.id === id)) continue;
    const refIndices=[...sentence.matchAll(/\[(\d{1,2})\]/g)]
      .map(m=>Number(m[1])-1).filter(i=>i>=0&&i<citations.length);
    const supported=refIndices.length
      ? [...new Set(refIndices)].map(i=>citations[i])
      : citations.slice(0,Math.min(3,citations.length));
    const contradictionIds = baby.beliefs
      .filter(old => looksContradictory(old.text, sentence))
      .map(x => x.id).slice(0, 6);
    const diversity=new Set(supported.map(x=>x.source??domainOf(x.url))).size;
    const meanRelevance=supported.length
      ? supported.reduce((n,x)=>n+Math.min(6,Number(x.relevance??2)),0)/supported.length : 0;
    const markerPenalty=refIndices.length?0:0.16;
    const supportGain=Math.min(.28,supported.length*.055+diversity*.04+meanRelevance*.018);
    const confidence = clamp(.56 + supportGain - inferredUncertainty*.34 - markerPenalty -
      contradictionIds.length * 0.08, 0.08, 0.92);
    baby.beliefs.push({
      id,
      text: sentence.replace(/^#+\s*/,""),
      query: clean(query, 400),
      confidence,
      uncertainty: 1 - confidence,
      status: confidence >= 0.68 && supported.length>0 ? "GROUNDED_OBSERVATION" : "PROVISIONAL",
      citationUrls: supported.map(x => x.url).filter(Boolean).slice(0, 8),
      supportCount:supported.length,
      sourceDiversity:diversity,
      contradictionIds,
      createdAt: now,
      lastValidatedAt: now
    });
    baby.counters.contradictions += contradictionIds.length;
    added.push(id);
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
    }))
  };
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
