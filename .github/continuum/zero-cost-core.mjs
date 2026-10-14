import crypto from "node:crypto";
import fs from "node:fs";
import { spawnSync } from "node:child_process";
import {
  createBabyState, migrateBabyState, analyzeInstruction, observeInteraction, addOwnerRule,
  buildDeepResearchPlan, ingestEvidence, addGoal, refreshSkillCurriculum, retrieveMemory,
  selectGoal, recordOutcome, babyStatus
} from "./baby-core.mjs";
import {
  fetchPublicPage, crawlPublicWeb, searchGitHubRepositories
} from "./public-web.mjs";
import {
  createWorldModel,migrateWorldModel,proposeHypothesis,selectHypothesisTest,
  evaluateHypothesisEvidence,worldModelStatus
} from "./world-model.mjs";

const STATE_FILE = ".continuum/state.json";
const EVENT = process.env.CONTINUUM_EVENT ?? "workflow_dispatch";
const REPOSITORY = process.env.CONTINUUM_REPOSITORY ?? "Ullmann27/device-ai";
const ISSUE = Number(process.env.CONTINUUM_ISSUE ?? 20);
const COMMENT = String(process.env.CONTINUUM_COMMENT ?? "").trim();
const COMMENT_USER = String(process.env.CONTINUUM_COMMENT_USER ?? "").trim();
const TOKEN = process.env.GITHUB_TOKEN ?? "";

const DEFAULT_TOPICS = [
  "autonome KI-Agenten und Evaluator-Optimizer-Architekturen",
  "Continual Learning und Schutz vor katastrophalem Vergessen",
  "Reasoning, Search und Test-Time Compute",
  "Active Learning und Unsicherheitsmessung",
  "Retrieval-Augmented Generation und Wissensprovenienz",
  "evolutionäre Optimierung von Agentenstrategien",
  "kleine lokale Sprachmodelle und CPU-Inferenz",
  "Android On-Device AI und LiteRT",
  "Agent-Evaluation, Benchmarking und Trace Grading",
  "selbstüberwachtes Repräsentationslernen",
  "Dialogpragmatik, Emotionserkennung und natürliche Benutzerinteraktion",
  "kognitive Architekturen, Weltmodelle und hierarchische Planung"
];

function freshState() {
  const now = Date.now();
  const state = {
    schemaVersion: 5,
    generation: 1,
    createdAt: now,
    updatedAt: now,
    topics: DEFAULT_TOPICS.map((name, i) => ({
      id: "topic-" + String(i + 1).padStart(2, "0"),
      name, evidence: 0, failures: 0, lastAt: null
    })),
    queue: [],
    observations: [],
    lessons: [],
    concepts: {},
    edges: {},
    cycles: [],
    interactions: [],
    strategyStats: defaultStrategyStats(),
    strategyRewardVersion:2,
    baby: createBabyState(now),
    worldModel:createWorldModel(now),
    integrityHash: ""
  };
  state.integrityHash = hashState(state);
  return state;
}

function loadState() {
  if (!fs.existsSync(STATE_FILE)) return freshState();
  const raw = fs.readFileSync(STATE_FILE, "utf8").trim();
  if (!raw) return freshState();
  const state = JSON.parse(raw);
  if (state.integrityHash !== hashState(state)) throw new Error("Continuum state integrity mismatch");
  if (state.schemaVersion === 1) {
    const migrated = freshState();
    migrated.lessons = Array.isArray(state.lessons) ? state.lessons.slice(-128) : [];
    migrated.interactions = Array.isArray(state.interactions) ? state.interactions.slice(-128) : [];
    migrated.generation = Math.max(2, Number(state.generation ?? 1) + 1);
    migrated.integrityHash = hashState(migrated);
    return migrated;
  }
  if (state.schemaVersion === 2) {
    const migrated = structuredClone(state);
    migrated.schemaVersion = 3;
    migrated.strategyStats = defaultStrategyStats();
    migrated.integrityHash = hashState(migrated);
    return migrated;
  }
  if (state.schemaVersion === 3) {
    const migrated = structuredClone(state);
    migrated.schemaVersion = 4;
    migrated.baby = createBabyState();
    migrated.integrityHash = hashState(migrated);
    return migrated;
  }
  if(state.schemaVersion===4){
    const migrated=structuredClone(state);
    migrated.schemaVersion=5;
    migrated.worldModel=createWorldModel();
    migrated.strategyStats=defaultStrategyStats();
    migrated.strategyRewardVersion=2;
    migrated.baby=migrateBabyState(migrated.baby);
    syncWorldModelFromBaby(migrated);
    migrated.integrityHash=hashState(migrated);
    return migrated;
  }
  if (state.schemaVersion !== 5) throw new Error("Unsupported state schema");
  state.baby = migrateBabyState(state.baby);
  state.worldModel=migrateWorldModel(state.worldModel);
  if(state.strategyRewardVersion!==2){
    state.strategyStats=defaultStrategyStats();
    state.strategyRewardVersion=2;
  }
  syncWorldModelFromBaby(state);
  return state;
}

function saveState(state) {
  state.generation += 1;
  state.updatedAt = Date.now();
  state.observations = state.observations.slice(-256);
  state.cycles = state.cycles.slice(-512);
  state.interactions = state.interactions.slice(-128);
  state.lessons = state.lessons.slice(-128);
  state.queue = dedupe(state.queue).slice(0, 64);
  state.concepts = trimObject(state.concepts, 800);
  state.edges = trimObject(state.edges, 2400);
  state.baby = migrateBabyState(state.baby);
  state.worldModel=migrateWorldModel(state.worldModel);
  state.strategyRewardVersion=2;
  state.integrityHash = hashState(state);
  fs.mkdirSync(".continuum", { recursive: true });
  fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
}

function hashState(state) {
  const copy = structuredClone(state);
  copy.integrityHash = "";
  return crypto.createHash("sha256").update(stable(copy)).digest("hex");
}

function stable(value) {
  if (Array.isArray(value)) return "[" + value.map(stable).join(",") + "]";
  if (value && typeof value === "object") {
    return "{" + Object.keys(value).sort().map(k => JSON.stringify(k) + ":" + stable(value[k])).join(",") + "}";
  }
  return JSON.stringify(value);
}

function tokenize(text) {
  return String(text).toLocaleLowerCase("de-DE")
    .normalize("NFKC")
    .split(/[^\p{L}\p{N}]+/u)
    .filter(x => x.length >= 4 && x.length <= 28)
    .filter(x => !STOP.has(x));
}

const STOP = new Set([
  "dass","dieser","diese","dieses","einer","einem","einen","eine","eines","werden","wurde","wurden",
  "sind","auch","nicht","durch","über","unter","zwischen","gegen","ohne","nach","beim","oder","sowie",
  "aber","mehr","kann","können","wird","sich","eine","einer","with","from","that","this","have","been",
  "into","using","used","their","which","were","such","also","than","when","where","what","your",
  "welche","welcher","welches","konkreten","implementierungsbausteine","ableiten","funktionieren",
  "nachweislich","starken","empirischen","ergebnisse","benchmarks","grenzen","fehlschläge",
  "gegenbeispiele","offenen","probleme","bekannt","methoden","daraus","sollte"
]);

function learnGraph(state, texts) {
  for (const text of texts) {
    const words = tokenize(text).slice(0, 120);
    const unique = [...new Set(words)];
    for (const w of unique) state.concepts[w] = (state.concepts[w] ?? 0) + 1;
    const top = unique.slice(0, 28);
    for (let i = 0; i < top.length; i++) {
      for (let j = i + 1; j < Math.min(top.length, i + 7); j++) {
        const key = [top[i], top[j]].sort().join("~");
        state.edges[key] = (state.edges[key] ?? 0) + 1;
      }
    }
  }
}

function defaultStrategyStats() {
  return [
    { id:"technical-broad", attempts:0, rewardSum:0, lastReward:null },
    { id:"academic-replay", attempts:0, rewardSum:0, lastReward:null },
    { id:"uncertainty-curiosity", attempts:0, rewardSum:0, lastReward:null },
    { id:"adversarial-evidence", attempts:0, rewardSum:0, lastReward:null }
  ];
}

function chooseResearchStrategy(state) {
  if (!Array.isArray(state.strategyStats) || !state.strategyStats.length) state.strategyStats = defaultStrategyStats();
  const total = state.strategyStats.reduce((n,s)=>n+s.attempts,0) + 1;
  return state.strategyStats
    .map(s => ({
      ...s,
      ucb: s.attempts === 0 ? Number.POSITIVE_INFINITY :
        (s.rewardSum / s.attempts) + Math.sqrt(2 * Math.log(total) / s.attempts)
    }))
    .sort((a,b) => b.ucb - a.ucb || a.id.localeCompare(b.id))[0].id;
}

function recordStrategyReward(state, id, sources) {
  const stat = state.strategyStats.find(x=>x.id===id);
  if (!stat) return 0;
  const diversity = new Set(sources.map(x=>x.source)).size;
  const meanRelevance = sources.length ?
    sources.reduce((n,x)=>n+Number(x.relevance ?? 0),0)/sources.length : 0;
  const relevanceScore=Math.min(5,Math.log1p(Math.max(0,meanRelevance))*1.65);
  const diversityScore=Math.min(2,diversity*0.5);
  const volumeScore=Math.min(1.5,sources.length*0.11);
  const qualityFraction=sources.length? sources.filter(x=>sourceWeight(x.source)>=3).length/sources.length : 0;
  const reward=Math.min(10,relevanceScore+diversityScore+volumeScore+qualityFraction*1.5);
  stat.attempts += 1;
  stat.rewardSum += reward;
  stat.lastReward = reward;
  return reward;
}

function nextTarget(state) {
  state.baby = refreshSkillCurriculum(migrateBabyState(state.baby));
  state.worldModel=migrateWorldModel(state.worldModel);
  const hypothesis=(state.generation%2===0)?selectHypothesisTest(state.worldModel):null;
  if(hypothesis)return {type:"world-test",text:hypothesis.testQuestions[hypothesis.attempts%hypothesis.testQuestions.length],hypothesisId:hypothesis.id};
  const babyGoal = selectGoal(state.baby);
  if (babyGoal) return { type:"baby-goal", text:babyGoal.text, babyGoalId:babyGoal.id };
  if (state.queue.length) return { type: "queue", text: state.queue.shift() };
  const ranked = state.topics.map(t => {
    const age = t.lastAt ? Math.min(3, (Date.now() - t.lastAt) / 86400000) : 4;
    return { ...t, score: 3 / (1 + t.evidence) + age + Math.min(t.failures, 3) * .15 };
  }).sort((a,b) => b.score - a.score || a.id.localeCompare(b.id));
  return { type: "topic", text: ranked[0].name, topicId: ranked[0].id };
}

async function research(query, interactive = false, strategyId = "technical-broad") {
  const searchQueries = await planSearchQueries(query, interactive, strategyId);
  const results = [];
  for (const searchQuery of searchQueries.slice(0, 5)) {
    await Promise.allSettled([
      wikipedia(searchQuery).then(x => results.push(...x.map(v => ({...v, searchQuery})))),
      openAlex(searchQuery).then(x => results.push(...x.map(v => ({...v, searchQuery})))),
      crossref(searchQuery).then(x => results.push(...x.map(v => ({...v, searchQuery})))),
      arxiv(searchQuery).then(x => results.push(...x.map(v => ({...v, searchQuery}))))
    ]);
  }
  const deduped = [];
  const seen = new Set();
  for (const item of results) {
    const key = (item.url || item.title).toLocaleLowerCase("de-DE");
    if (!key || seen.has(key)) continue;
    seen.add(key);
    item.relevance = relevance(item, searchQueries);
    if (item.relevance >= 1.25 && domainGate(item,searchQueries)) deduped.push(item);
  }
  const github = [];
  for (const searchQuery of searchQueries.slice(0, 2)) {
    github.push(...await searchGitHubRepositories(searchQuery,TOKEN,3));
  }
  for (const item of github) {
    item.relevance = relevance(item,searchQueries) + 0.8;
    const key=(item.url||item.title).toLocaleLowerCase("de-DE");
    if(!seen.has(key) && item.relevance>=1.25 && domainGate(item,searchQueries)){seen.add(key);deduped.push(item);}
  }

  deduped.sort((a,b) => b.relevance - a.relevance ||
    sourceWeight(b.source) - sourceWeight(a.source) ||
    a.title.localeCompare(b.title));

  const initial=deduped.slice(0,10);
  const crawlSeeds=initial.filter(x=>/^https:/.test(x.url||"")).slice(0,4).map(x=>x.url);
  const crawled=crawlSeeds.length ? await crawlPublicWeb({
    seeds:crawlSeeds,query,maxPages:4,maxDepth:1,maxDomains:4
  }) : [];
  for(const page of crawled){
    page.relevance=relevance(page,searchQueries)+Number(page.relevance||0);
    const key=(page.url||page.title).toLocaleLowerCase("de-DE");
    if(!seen.has(key) && page.relevance>=0.7 && domainGate(page,searchQueries)){seen.add(key);deduped.push(page);}
  }
  deduped.sort((a,b)=>b.relevance-a.relevance||sourceWeight(b.source)-sourceWeight(a.source));
  return { sources: deduped.slice(0, 14), searchQueries };
}

async function planSearchQueries(query, interactive, strategyId) {
  const q = String(query).trim();
  const queries = [q];
  const lower = q.toLocaleLowerCase("de-DE");
  if (/autonom|wissenslück|forschungsfrage|agent/.test(lower)) {
    queries.push(
      "autonomous AI agents continual learning knowledge gap detection curiosity-driven exploration",
      "self improving research agents uncertainty driven planning evaluator optimizer",
      "active learning epistemic uncertainty autonomous scientific discovery agents"
    );
  }
  if (/continual|vergess|lernen/.test(lower)) {
    queries.push("continual learning catastrophic forgetting experience replay online learning");
  }
  if (/reason|denken|überleg|search/.test(lower)) {
    queries.push("AI reasoning search test-time compute self-reflection agents");
  }
  if (/android|on-device|handy|apk/.test(lower)) {
    queries.push("on-device AI Android continual learning edge inference local language model");
  }
  if (/sprache|dialog|emotion|benutzer|kommunik|pragmatik|language|conversation|affect/.test(lower)) {
    queries.push(
      "dialogue systems pragmatics user modeling affective computing emotion recognition natural language processing",
      "adaptive conversational agents user feedback personalization dialogue state tracking preference learning",
      "human AI interaction conversational agent correction learning affective computing benchmark"
    );
  }
  if (/plan|denken|zerleg|reason|metakogn|selbstüberwach|ziel|entscheidung/.test(lower)) {
    queries.push(
      "LLM agent task decomposition hierarchical planning metacognitive monitoring uncertainty benchmark",
      "autonomous agents self reflection planning verifier uncertainty goal selection"
    );
  }
  if (strategyId === "academic-replay") {
    queries.push(
      q + " experience replay catastrophic forgetting benchmark",
      q + " meta-learning rehearsal continual learning"
    );
  } else if (strategyId === "uncertainty-curiosity") {
    queries.push(
      q + " epistemic uncertainty curiosity information gain active learning",
      q + " novelty detection knowledge gaps exploration"
    );
  } else if (strategyId === "adversarial-evidence") {
    queries.push(
      q + " limitations failure modes negative results evaluation",
      q + " critique benchmark reproducibility independent evidence"
    );
  }
  if (interactive && process.env.CONTINUUM_COPILOT === "1") {
    const planned = runCopilot([
      "Erzeuge 5 präzise Suchanfragen für wissenschaftliche und technische Recherche.",
      "Gib nur eine Suchanfrage pro Zeile aus, keine Erklärungen.",
      "Nutze englische Fachbegriffe, wenn sie bessere Treffer liefern.",
      "Thema: " + q
    ].join("\n"), "gpt-6-luna", 1);
    if (planned?.text) {
      for (const line of planned.text.split(/\r?\n/)) {
        const cleaned = line.replace(/^[-*\d.)\s]+/, "").trim();
        if (cleaned.length >= 8 && cleaned.length <= 220) queries.push(cleaned);
      }
    }
  }
  return dedupe(queries).slice(0, 6);
}

function relevance(item, queries) {
  const title = new Set(tokenize(item.title));
  const text = new Set(tokenize(item.text));
  const terms = new Set(queries.flatMap(tokenize));
  let score = 0;
  for (const term of terms) {
    if (title.has(term)) score += 2.4;
    if (text.has(term)) score += 0.45;
  }
  if (/agent|learning|reason|uncertainty|model|artificial|intelligence|neural|benchmark|evaluation/i.test(item.title)) score += 1.0;
  return score;
}

function domainGate(item,queries){
  const query=queries.join(" ").toLocaleLowerCase("en-US");
  const aiQuestion=/(\bai\b|\bki\b|artificial intelligence|machine learning|neural|agent|llm|language model|continual|active learning|reasoning|dialog|emotion|natural language|world model|reinforcement|metacogn|planning)/i.test(query);
  if(!aiQuestion)return true;
  const text=(String(item.title??"")+" "+String(item.text??"")).toLocaleLowerCase("en-US");
  const strongAi=/(artificial intelligence|machine learning|deep learning|neural|\bai\b|\bagent(s|ic)?\b|llm|large language model|language model|continual learning|active learning|reinforcement learning|transformer|natural language processing|dialogue system|conversational agent|affective computing|autonomous agent|catastrophic forgetting|world model|metacognitive|task decomposition|hierarchical planning)/i.test(text);
  return strongAi;
}

function sourceWeight(source) {
  return ({ arXiv:5, GitHub:4, OpenAlex:3, PublicWeb:2.5, Crossref:2, Wikipedia:1 }[source] ?? 0);
}

async function wikipedia(query) {
  const searchUrl = "https://de.wikipedia.org/w/api.php?action=query&list=search&format=json&utf8=1&srlimit=3&srsearch=" +
    encodeURIComponent(query);
  const search = await getJson(searchUrl);
  const hits = search?.query?.search ?? [];
  if (!hits.length) return [];
  const titles = hits.map(x => x.title).join("|");
  const url = "https://de.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1&redirects=1&format=json&titles=" +
    encodeURIComponent(titles);
  const pages = Object.values((await getJson(url))?.query?.pages ?? {});
  return pages.filter(x => x?.title && x?.extract).map(x => ({
    source: "Wikipedia",
    title: x.title,
    url: "https://de.wikipedia.org/wiki/" + encodeURIComponent(x.title.replace(/ /g, "_")),
    text: clean(x.extract, 2600)
  }));
}

async function openAlex(query) {
  const url = "https://api.openalex.org/works?per-page=3&search=" + encodeURIComponent(query);
  const data = await getJson(url);
  return (data?.results ?? []).map(work => ({
    source: "OpenAlex",
    title: work.title ?? "OpenAlex work",
    url: work.doi || work.id,
    text: clean([
      work.title,
      reconstructAbstract(work.abstract_inverted_index),
      work.primary_location?.source?.display_name,
      work.publication_year ? "Publikationsjahr " + work.publication_year : ""
    ].filter(Boolean).join(". "), 2600)
  })).filter(x => x.text.length > 40 && /^https:/.test(x.url ?? ""));
}

async function arxiv(query) {
  const words = tokenize(query).slice(0, 10).join(" ");
  if (!words) return [];
  const url = "https://export.arxiv.org/api/query?search_query=all%3A" +
    encodeURIComponent(words) + "&start=0&max_results=3&sortBy=submittedDate&sortOrder=descending";
  const response = await fetch(url, {
    headers: { "User-Agent": "DeviceAI-Continuum/0.2 (https://github.com/Ullmann27/device-ai)" },
    signal: AbortSignal.timeout(12000)
  });
  if (!response.ok) throw new Error("arXiv HTTP " + response.status);
  const xml = await response.text();
  const entries = [...xml.matchAll(/<entry>([\s\S]*?)<\/entry>/g)].map(m => m[1]);
  return entries.map(entry => {
    const title = decodeXml((entry.match(/<title>([\s\S]*?)<\/title>/)?.[1] ?? "").replace(/\s+/g," ").trim());
    const summary = decodeXml((entry.match(/<summary>([\s\S]*?)<\/summary>/)?.[1] ?? "").replace(/\s+/g," ").trim());
    const id = decodeXml((entry.match(/<id>([\s\S]*?)<\/id>/)?.[1] ?? "").trim());
    return { source:"arXiv", title, url:id, text:clean(title + ". " + summary,2600) };
  }).filter(x => x.title && /^https:/.test(x.url) && x.text.length > 80);
}

function decodeXml(value) {
  return String(value)
    .replace(/&lt;/g,"<").replace(/&gt;/g,">").replace(/&amp;/g,"&")
    .replace(/&quot;/g,'"').replace(/&#39;/g,"'");
}

async function crossref(query) {
  const url = "https://api.crossref.org/works?rows=3&query.bibliographic=" + encodeURIComponent(query);
  const data = await getJson(url);
  return (data?.message?.items ?? []).map(item => ({
    source: "Crossref",
    title: Array.isArray(item.title) ? item.title[0] : item.title || "Crossref work",
    url: item.URL,
    text: clean([
      Array.isArray(item.title) ? item.title[0] : item.title,
      stripTags(item.abstract ?? ""),
      Array.isArray(item["container-title"]) ? item["container-title"][0] : "",
      item.published?.["date-parts"]?.[0]?.[0] ? "Publikationsjahr " + item.published["date-parts"][0][0] : ""
    ].filter(Boolean).join(". "), 2600)
  })).filter(x => x.text.length > 40 && /^https:/.test(x.url ?? ""));
}

function reconstructAbstract(index) {
  if (!index || typeof index !== "object") return "";
  const positions = [];
  for (const [word, list] of Object.entries(index)) {
    for (const p of list ?? []) positions.push([p, word]);
  }
  return positions.sort((a,b) => a[0]-b[0]).map(x => x[1]).join(" ");
}

function stripTags(value) {
  return String(value).replace(/<[^>]*>/g, " ").replace(/\s+/g, " ");
}

async function getJson(url) {
  const response = await fetch(url, {
    headers: {
      "Accept": "application/json",
      "User-Agent": "DeviceAI-Continuum/0.1 (https://github.com/Ullmann27/device-ai)"
    },
    signal: AbortSignal.timeout(12000)
  });
  if (!response.ok) throw new Error("HTTP " + response.status + " for " + new URL(url).hostname);
  return response.json();
}

function synthesize(query, sources, state) {
  const q = new Set(tokenize(query));
  const candidates = [];
  for (const src of sources) {
    const sentences = src.text.split(/(?<=[.!?])\s+/).filter(x => x.length >= 45 && x.length <= 420);
    for (const sentence of sentences) {
      const words = tokenize(sentence);
      const overlap = words.filter(x => q.has(x)).length;
      const known = words.reduce((n,w) => n + Math.log1p(state.concepts[w] ?? 0), 0) / Math.max(1, words.length);
      const score = overlap * 2.8 + known + Math.min(words.length, 35) / 35;
      candidates.push({ sentence: clean(sentence, 420), score, src });
    }
  }
  candidates.sort((a,b) => b.score - a.score);
  const selected = [];
  for (const item of candidates) {
    if (selected.some(x => similarity(x.sentence, item.sentence) > .72)) continue;
    selected.push(item);
    if (selected.length >= 5) break;
  }
  const citations = [];
  for (const item of selected) {
    let index = citations.findIndex(x => x.url === item.src.url);
    if (index < 0) {
      citations.push({ title: item.src.title, url: item.src.url, source: item.src.source });
      index = citations.length - 1;
    }
    item.citation = index + 1;
  }
  return {
    text: selected.map(x => x.sentence + " [" + x.citation + "]").join("\n\n"),
    citations
  };
}

function similarity(a,b) {
  const A = new Set(tokenize(a)), B = new Set(tokenize(b));
  const intersection = [...A].filter(x => B.has(x)).length;
  const union = new Set([...A,...B]).size;
  return union ? intersection / union : 0;
}

function relatedConcepts(state, query, limit = 8) {
  const keys = tokenize(query);
  const scores = {};
  for (const [edge,count] of Object.entries(state.edges)) {
    const [a,b] = edge.split("~");
    if (keys.includes(a)) scores[b] = (scores[b] ?? 0) + count;
    if (keys.includes(b)) scores[a] = (scores[a] ?? 0) + count;
  }
  return Object.entries(scores).sort((a,b) => b[1]-a[1]).slice(0,limit).map(x => x[0]);
}

function runCopilot(prompt, model = "auto", maxCredits = 2) {
  if (process.env.CONTINUUM_COPILOT !== "1") return null;
  const run = spawnSync("copilot", [
    "-p", prompt, "-s", "--model", model,
    "--max-ai-credits", String(maxCredits), "--no-ask-user"
  ], {
    encoding: "utf8",
    timeout: 120000,
    env: { ...process.env, GITHUB_TOKEN: TOKEN }
  });
  if (run.status === 0 && run.stdout?.trim()) return { text: clean(run.stdout, 9000), model };
  if (model !== "auto") {
    const fallback = spawnSync("copilot", [
      "-p", prompt, "-s", "--model", "auto",
      "--max-ai-credits", String(maxCredits), "--no-ask-user"
    ], {
      encoding: "utf8",
      timeout: 120000,
      env: { ...process.env, GITHUB_TOKEN: TOKEN }
    });
    if (fallback.status === 0 && fallback.stdout?.trim()) return { text: clean(fallback.stdout,9000), model:"auto" };
  }
  return null;
}

async function maybeCopilot(query, evidence, state = null) {
  if (EVENT !== "issue_comment" || process.env.CONTINUUM_COPILOT !== "1") return null;
  const prompt = [
    "Du bist die interaktive Oberfläche des Device-AI-Continuum-Kerns.",
    "Beantworte ausschließlich aus dem folgenden abgerufenen Evidenzpaket.",
    "Erfinde keine Quellen. Wenn die Evidenz nicht reicht, sage das.",
    "Schreibe auf Deutsch, präzise, kompakt und mit [1], [2] usw. passend zu den Quellen.",
    "",
    "FRAGE: " + query,
    "",
    ...(state?.baby?.ownerRules?.filter(x=>x.active).length ? [
      "EXPLIZITE NUTZERREGELN FÜR KOMMUNIKATION/PROJEKT:",
      ...state.baby.ownerRules.filter(x=>x.active).slice(-12).map(x=>"- "+x.text),
      ""
    ] : []),
    ...(state ? (()=>{
      const memory=retrieveMemory(state.baby,query,6);
      return memory.length ? [
        "RELEVANTE EIGENE ERINNERUNG (Kontext, keine neue Evidenz; aktuelle Quellen haben Vorrang):",
        ...memory.map(m=>"- ["+m.kind+"] "+m.text+" · score="+m.score.toFixed(2)),
        ""
      ] : [];
    })() : []),
    "EVIDENZ:",
    ...evidence.map((s,i) => "[" + (i+1) + "] " + s.title + " — " + s.url + "\n" + s.text.slice(0,1400))
  ].join("\n");
  return runCopilot(prompt, "gpt-6-astra", 2);
}

function makeObservation(query, sources, answer) {
  const now = Date.now();
  const hash = crypto.createHash("sha256")
    .update(query + "\0" + sources.map(x => x.url).sort().join("\n") + "\0" + answer)
    .digest("hex");
  return {
    id: "obs-" + hash.slice(0,24),
    query: clean(query, 300),
    answer: clean(answer, 7000),
    citations: sources.map(x => ({
      title:x.title, url:x.url, source:x.source,
      relevance:Number.isFinite(Number(x.relevance))?Number(x.relevance):null
    })).slice(0,12),
    createdAt: now,
    trust: "PUBLIC_SOURCE_OBSERVATION",
    authority: "NONE"
  };
}

async function researchTurn(state, query, interact = false, goalId = null, hypothesisId = null) {
  const strategyId = chooseResearchStrategy(state);
  const retrieval = await research(query, interact, strategyId);
  const sources = retrieval.sources;
  if (!sources.length) {
    recordStrategyReward(state, strategyId, []);
    throw new Error("Keine hinreichend relevante öffentliche Quelle gefunden.");
  }
  learnGraph(state, sources.map(x => x.text));
  const deterministic = synthesize(query, sources, state);
  const copilot = interact ? await maybeCopilot(query, sources, state) : null;
  const answer = copilot?.text || deterministic.text ||
    "Quellen wurden gefunden, aber für eine belastbare Synthese reicht der extrahierte Text noch nicht.";
  const citationSources = copilot?.text ? sources : deterministic.citations;
  const observation = makeObservation(query, citationSources, answer);
  observation.searchQueries = retrieval.searchQueries;
  observation.synthesisModel = copilot?.model ?? "zero-cost-retrieval";
  observation.retrievalStrategy = strategyId;
  observation.retrievalReward = recordStrategyReward(state, strategyId, sources);
  if (!state.observations.some(x => x.id === observation.id)) state.observations.push(observation);

  const epistemic = ingestEvidence(state.baby,{
    query,
    answer,
    citations:observation.citations,
    uncertainty:Math.max(0.08,1-Math.min(0.9,observation.retrievalReward/11)),
    nextQuestions:[
      "Welche unabhängigen Quellen widersprechen oder begrenzen die bisherigen Aussagen zu: "+query,
      "Welcher messbare Benchmark würde den Fortschritt bei dieser Frage am besten prüfen?"
    ]
  });
  const goalOrigin=goalId
    ? String(epistemic.baby.goals.find(g=>g.id===goalId)?.origin??"")
    : "";
  const goalSkill=goalOrigin.startsWith("SKILL_CURRICULUM:")
    ? goalOrigin.slice("SKILL_CURRICULUM:".length)
    : "";
  const turnSkills=["research","source-critique","synthesis","self-check"];
  if(goalSkill)turnSkills.push(goalSkill);
  state.baby = recordOutcome(epistemic.baby,{
    goalId,
    skills:[...new Set(turnSkills)],
    success:true,
    reward:observation.retrievalReward,
    reflection:"Rechercheweg "+strategyId+" lieferte "+sources.length+" relevante Quellen; Synthese="+observation.synthesisModel
  });
  syncWorldModelFromBaby(state);
  let hypothesisEvaluation=null;
  if(hypothesisId){
    const tested=evaluateHypothesisEvidence(state.worldModel,hypothesisId,{text:answer,citations:observation.citations});
    state.worldModel=tested.model;
    hypothesisEvaluation=tested.hypothesis?{
      id:tested.hypothesis.id,
      classification:tested.classification,
      status:tested.hypothesis.status,
      confidence:tested.hypothesis.confidence
    }:null;
  }
  state.baby=refreshSkillCurriculum(state.baby);
  const nextBabyGoal=selectGoal(state.baby);
  if(nextBabyGoal)state.queue.push(nextBabyGoal.text);
  state.queue.push(
    "Welche unabhängigen Quellen widersprechen oder begrenzen die bisherigen Aussagen zu: " + query,
    "Welche messbaren Benchmarks oder Experimente prüfen den Fortschritt bei: " + query
  );
  return { observation, answer, sources, copilot: Boolean(copilot), synthesisModel: copilot?.model ?? null, hypothesisEvaluation };
}

function hypothesisCandidateText(value){
  const text=String(value??"")
    .replace(/^\s*\[\d{1,2}\]\s*/,"")
    .replace(/^\s*#+\s*(mechanism|evidence|failure|implementation|interaction|metacognition)\s*/i,"")
    .replace(/\s+/g," ").trim();
  const words=text.split(/\s+/).filter(Boolean);
  if(words.length<12)return null;
  if(/skip to main|main navigation|site footer|open menu|current archives|privacy statement|register login|view pdf html|home\s*\/\s*archives/i.test(text))return null;
  return text.slice(0,650);
}

function syncWorldModelFromBaby(state){
  state.worldModel=migrateWorldModel(state.worldModel);
  const eligible=state.baby.beliefs.map(b=>({belief:b,claim:hypothesisCandidateText(b.text)}))
    .filter(x=>
      x.claim &&
      x.belief.status==="GROUNDED_OBSERVATION" &&
      Number(x.belief.confidence??0)>=.68 &&
      Number(x.belief.supportCount??0)>=1 &&
      Number(x.belief.sourceDiversity??0)>=1
    );

  // Untested provisional hypotheses are rebuilt from normalized grounded beliefs.
  // Anything already tested, supported or disputed keeps its history.
  state.worldModel.hypotheses=state.worldModel.hypotheses.filter(h=>
    h.status!=="PROVISIONAL" || Number(h.attempts??0)>0
  );

  for(const {belief,claim} of eligible.slice(-32)){
    const p=proposeHypothesis(state.worldModel,{
      claim,beliefId:belief.id,citationUrls:belief.citationUrls,
      confidence:belief.confidence
    });
    state.worldModel=p.model;
  }
}

async function deepResearchTurn(state,query){
  state.baby=migrateBabyState(state.baby);
  const rootGoal=addGoal(state.baby,query,"USER_DEEP_RESEARCH",1.5);
  const plan=buildDeepResearchPlan(query);
  const allSources=[],parts=[];
  for(const step of plan){
    const strategyId=chooseResearchStrategy(state);
    const retrieval=await research(step.question,false,strategyId);
    if(!retrieval.sources.length)continue;
    const reward=recordStrategyReward(state,strategyId,retrieval.sources);
    allSources.push(...retrieval.sources);
    const part=synthesize(step.question,retrieval.sources,state);
    parts.push({step,text:part.text,reward,sources:retrieval.sources});
    learnGraph(state,retrieval.sources.map(x=>x.text));
    const epi=ingestEvidence(state.baby,{
      query:step.question,answer:part.text,citations:part.citations,
      uncertainty:Math.max(0.08,1-Math.min(.9,reward/11)),
      nextQuestions:["Welche Evidenz würde diesen Teilplan am stärksten falsifizieren?"]
    });
    const stepSkills=[step.skill,"research","self-check"];
    if(step.id==="interaction")stepSkills.push("affect-understanding","communication");
    if(step.id==="metacognition")stepSkills.push("planning","memory-retrieval");
    state.baby=recordOutcome(epi.baby,{
      goalId:rootGoal?.id,
      skills:[...new Set(stepSkills)],
      success:true,reward,
      reflection:"Deep-Research-Teil "+step.id+" mit Strategie "+strategyId+" und "+retrieval.sources.length+" Quellen."
    });
  }
  const unique=[];const seen=new Set();
  for(const s of allSources.sort((a,b)=>(b.relevance||0)-(a.relevance||0))){
    const k=s.url||s.title;if(!k||seen.has(k))continue;seen.add(k);unique.push(s);if(unique.length>=16)break;
  }
  if(!unique.length)throw new Error("Deep Research fand keine hinreichend relevante öffentliche Evidenz.");
  const copilot=await maybeCopilot(query,unique,state);
  const fallback=parts.map(p=>"### "+p.step.id+"\n"+p.text).join("\n\n");
  const answer=copilot?.text||fallback;
  const citations=copilot?.text?unique:unique.filter(s=>fallback.includes(s.title)||fallback.includes(s.url)).slice(0,12);
  const observation=makeObservation(query,citations.length?citations:unique.slice(0,12),answer);
  observation.deepResearch=true;
  observation.plan=plan.map(x=>({id:x.id,question:x.question,skill:x.skill}));
  observation.synthesisModel=copilot?.model??"multi-pass-zero-cost";
  if(!state.observations.some(x=>x.id===observation.id))state.observations.push(observation);
  const finalReward=Math.min(10,parts.length*1.4+new Set(unique.map(x=>x.source)).size*.7+Math.min(3,unique.length*.12));
  const epi=ingestEvidence(state.baby,{
    query,answer,citations:observation.citations,uncertainty:Math.max(.08,1-finalReward/11),
    nextQuestions:plan.map(x=>x.question)
  });
  state.baby=recordOutcome(epi.baby,{
    goalId:rootGoal?.id,
    skills:["instruction-decomposition","research","source-critique","synthesis","communication","self-check"],
    success:true,reward:finalReward,
    reflection:"Deep Research verband "+parts.length+" Teilpläne, "+unique.length+" Quellen und "+new Set(unique.map(x=>x.source)).size+" Quellentypen."
  });
  syncWorldModelFromBaby(state);
  return {observation,answer,sources:unique,copilot:Boolean(copilot),synthesisModel:copilot?.model??null,deep:true};
}

async function directUrlTurn(state,value){
  const page=await fetchPublicPage(value);
  learnGraph(state,[page.text]);
  const copilot=await maybeCopilot("Fasse diese öffentliche Seite zusammen, nenne Kernaussagen, Unsicherheiten und was als Nächstes geprüft werden sollte.",[page]);
  const answer=copilot?.text||page.text.slice(0,5000);
  const observation=makeObservation(page.url,[page],answer);
  observation.directUrl=true;
  if(!state.observations.some(x=>x.id===observation.id))state.observations.push(observation);
  const epi=ingestEvidence(state.baby,{
    query:page.url,answer,citations:[{url:page.url,source:"PublicWeb"}],
    uncertainty:.45,nextQuestions:["Welche unabhängige Quelle bestätigt oder widerlegt die Kernaussagen dieser Seite?"]
  });
  state.baby=recordOutcome(epi.baby,{skills:["research","source-critique","communication"],success:true,reward:6.5,
    reflection:"Direkte öffentliche URL gelesen und als einzelne Quelle markiert."});
  syncWorldModelFromBaby(state);
  return {observation,answer,sources:[page],copilot:Boolean(copilot),synthesisModel:copilot?.model??null};
}

function statusText(state) {
  const top = Object.entries(state.concepts).sort((a,b) => b[1]-a[1]).slice(0,10).map(x=>x[0]).join(", ");
  const successes = state.cycles.filter(x=>x.success).length;
  const bs=babyStatus(state.baby);
  const ws=worldModelStatus(state.worldModel);
  return [
    "## Continuum · Zero-Cost Status",
    "",
    "**Generation:** " + state.generation,
    "**Forschungszyklen:** " + state.cycles.length + " · erfolgreich " + successes + " · fehlgeschlagen " + (state.cycles.length-successes),
    "**Quellenbeobachtungen:** " + state.observations.length,
    "**Explizite Nutzerlektionen:** " + state.lessons.length,
    "**Offene Forschungsfragen:** " + state.queue.length,
    "**Gelernte Begriffe:** " + Object.keys(state.concepts).length,
    "**Gelernte Begriffsverbindungen:** " + Object.keys(state.edges).length,
    "**Recherche-Strategien:** " + state.strategyStats.map(s =>
      s.id + "=" + (s.attempts ? (s.rewardSum/s.attempts).toFixed(2) : "neu")
    ).join(" · "),
    "**Baby-Core:** "+bs.beliefs+" Überzeugungen ("+bs.grounded+" grounded, "+bs.provisional+" vorläufig) · "+bs.openGoals+" offene epistemische Ziele · "+bs.contradictions+" erkannte Widersprüche",
    "**Skills:** "+bs.skills.map(s=>s.id+"="+(s.meanReward==null?"neu":s.meanReward.toFixed(2))).join(" · "),
    "**Interaktion gelernt:** "+bs.interactionProfile.total+" Nachrichten · Ø Dringlichkeit "+bs.interactionProfile.meanUrgency.toFixed(2)+" · Nutzerregeln "+bs.ownerRules,
    "**Selbst-Curriculum:** "+bs.curriculumOpen+" aktuell offene Skill-Lernziele",
    "**World-Model:** "+ws.total+" Hypothesen · "+ws.supported+" unterstützt · "+ws.disputed+" umstritten · "+ws.provisional+" vorläufig · "+ws.evidenceTests+" Falsifikationstests",
    top ? "**Stärkste Begriffe:** " + top : "**Stärkste Begriffe:** noch keine",
    "",
    "Der Zustand lebt auf dem Branch `continuum-state` und ist unabhängig von einer installierten APK."
  ].join("\n");
}

function formatResearch(query, turn, state) {
  const refs = turn.observation.citations.map((x,i) => (i+1) + ". [" + x.title + "](" + x.url + ") · " + x.source).join("\n");
  const related = relatedConcepts(state, query);
  return [
    "## Continuum · Recherche",
    "",
    "**Frage:** " + clean(query,300),
    turn.copilot ? "**Synthese:** GitHub Copilot CLI auf dem abgerufenen Evidenzpaket" : "**Synthese:** eigener Zero-Cost-Retrievalkern",
    "",
    turn.answer,
    "",
    related.length ? "**Assoziativ gelernte Begriffe:** " + related.join(", ") : "",
    turn.hypothesisEvaluation ? "**Hypothesenprüfung:** "+turn.hypothesisEvaluation.classification+
      " · "+turn.hypothesisEvaluation.status+
      " · Vertrauen "+(turn.hypothesisEvaluation.confidence*100).toFixed(1)+"%" : "",
    "",
    "### Quellen",
    refs,
    "",
    "_Quelleninhalte bleiben Beobachtungen ohne Geräte- oder Faktenautorität. Der Kern hat seinen Begriffsgraphen und seine Forschungsagenda aktualisiert._"
  ].filter(Boolean).join("\n");
}

async function reply(body) {
  if (!TOKEN || !ISSUE) return;
  const response = await fetch("https://api.github.com/repos/" + REPOSITORY + "/issues/" + ISSUE + "/comments", {
    method: "POST",
    headers: {
      "Authorization": "Bearer " + TOKEN,
      "Accept": "application/vnd.github+json",
      "X-GitHub-Api-Version": "2026-03-10",
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ body: body.slice(0,60000) })
  });
  if (!response.ok) throw new Error("GitHub reply failed: HTTP " + response.status);
}

function clean(value,max) {
  return String(value ?? "").replace(/[\u0000-\u001f\u007f]/g," ").replace(/\s+/g," ").trim().slice(0,max);
}
function dedupe(values) {
  const seen=new Set(); return values.filter(x=>{ const k=String(x).toLocaleLowerCase("de-DE"); if(seen.has(k))return false;seen.add(k);return true;});
}
function trimObject(object,max) {
  return Object.fromEntries(Object.entries(object).sort((a,b)=>b[1]-a[1]).slice(0,max));
}

const state = loadState();
try {
  if (EVENT === "issue_comment") {
    const body = COMMENT;
    const lower = body.toLocaleLowerCase("de-DE");
    const observed=observeInteraction(state.baby,body);
    state.baby=observed.baby;
    const interactionAnalysis=observed.analysis;
    state.interactions.push({ at:Date.now(), user:COMMENT_USER, text:clean(body,1000), analysis:interactionAnalysis });
    if (lower === "/status" || lower === "status") {
      await reply(statusText(state));
    } else if(lower.startsWith("/memory ")){
      const query=body.slice(8).trim();
      const memory=retrieveMemory(state.baby,query,10);
      const bodyText=memory.length
        ? memory.map(m=>"- **"+m.kind+"** · score "+m.score.toFixed(2)+"\n  "+m.text).join("\n")
        : "Dazu wurde noch keine relevante eigene Erinnerung gefunden.";
      state.baby=recordOutcome(state.baby,{
        skills:["memory-retrieval"],success:true,reward:memory.length?Math.min(9,5+memory.length*.5):3,
        reflection:"Expliziter Memory-Retrieval für: "+clean(query,240)+" · Treffer "+memory.length
      });
      await reply("## Continuum · Memory Retrieval\n\n"+bodyText);
    } else if(lower==="/hypotheses"){
      syncWorldModelFromBaby(state);
      const items=state.worldModel.hypotheses
        .slice().sort((a,b)=>b.confidence-a.confidence)
        .slice(0,12);
      const bodyText=items.length
        ? items.map(h=>"- `"+h.id+"` · "+h.status+" · "+(h.confidence*100).toFixed(1)+"%\n  "+h.claim).join("\n")
        : "Noch keine hinreichend gestützte Hypothese im Rulebook.";
      await reply("## Continuum · World-Model Hypothesen\n\n"+bodyText+"\n\nMit `/verify <hyp-id>` kann eine Hypothese gezielt gegen neue öffentliche Evidenz geprüft werden.");
    } else if(lower.startsWith("/verify ")){
      syncWorldModelFromBaby(state);
      const id=body.slice(8).trim();
      const h=state.worldModel.hypotheses.find(x=>x.id===id);
      if(!h)await reply("Hypothese nicht gefunden. Verwende zuerst `/hypotheses`.");
      else{
        const question=h.testQuestions[h.attempts%h.testQuestions.length];
        const turn=await researchTurn(state,question,true,null,h.id);
        state.cycles.push({at:Date.now(),query:question,success:true,sourceCount:turn.sources.length,interactive:true,verifyHypothesis:h.id});
        await reply(formatResearch(question,turn,state));
      }
    } else if (lower.startsWith("/rule ")) {
      const added=addOwnerRule(state.baby,body.slice(6));
      state.baby=added.baby;
      if(!added.rule)await reply("Verwendung: `/rule <explizite Kommunikations- oder Projektregel>`");
      else await reply("## Continuum · Nutzerregel\n\nGespeichert: **"+added.rule.text+"**\n\nDie Regel wird als explizite Nutzerpräferenz getrennt von Webwissen geführt.");
    } else if (lower.startsWith("/feedback ")) {
      const feedback=body.slice(10).trim();
      const good=/^(good|gut|positiv|richtig|ja)\b/i.test(feedback);
      const bad=/^(bad|schlecht|negativ|falsch|nein)\b/i.test(feedback);
      if(!good&&!bad)await reply("Verwendung: `/feedback good <Hinweis>` oder `/feedback bad <Hinweis>`");
      else{
        state.baby=recordOutcome(state.baby,{
          skills:["communication","self-check"],
          success:good,reward:good?8:2,
          reflection:"Explizites Nutzerfeedback: "+clean(feedback,500)
        });
        await reply("## Continuum · Feedback gelernt\n\nKommunikations- und Self-Check-Skill wurden mit **"+(good?"positivem":"negativem")+"** Nutzerfeedback aktualisiert.");
      }
    } else if (lower.startsWith("/learn ")) {
      const lesson = clean(body.slice(7),1200);
      const id = "lesson-" + crypto.createHash("sha256").update(lesson).digest("hex").slice(0,20);
      if (!state.lessons.some(x=>x.id===id)) state.lessons.push({id,text:lesson,createdAt:Date.now(),provenance:"USER_EXPLICIT"});
      learnGraph(state,[lesson]);
      await reply("## Continuum · Lektion übernommen\n\nAls **explizite Nutzerlektion** gespeichert: " + lesson + "\n\nSie wird nicht mit Webquellen gleichgesetzt.");
    } else if(lower.startsWith("/deep ")){
      const query=body.slice(6).trim();
      if(!query)await reply("Verwendung: `/deep <Forschungsfrage>`");
      else{
        const turn=await deepResearchTurn(state,query);
        state.cycles.push({at:Date.now(),query,success:true,sourceCount:turn.sources.length,interactive:true,deep:true});
        await reply(formatResearch(query,turn,state));
      }
    } else if(lower.startsWith("/url ")){
      const value=body.slice(5).trim();
      if(!value)await reply("Verwendung: `/url https://öffentliche-seite.example/...`");
      else{
        const turn=await directUrlTurn(state,value);
        state.cycles.push({at:Date.now(),query:value,success:true,sourceCount:1,interactive:true,directUrl:true});
        await reply(formatResearch(value,turn,state));
      }
    } else {
      const query = lower.startsWith("/research ") ? body.slice(10).trim()
        : lower.startsWith("/ask ") ? body.slice(5).trim()
        : body;
      if (!query) {
        await reply("Schreibe eine Frage, `/deep <Frage>`, `/research <Thema>`, `/memory <Thema>`, `/url <öffentliche URL>`, `/learn <Text>` oder `/status`.");
      } else {
        const turn = await researchTurn(state, query, true);
        state.cycles.push({ at:Date.now(), query, success:true, sourceCount:turn.sources.length, interactive:true });
        await reply(formatResearch(query,turn,state));
      }
    }
  } else {
    const target = nextTarget(state);
    try {
      const turn = await researchTurn(state,target.text,false,target.babyGoalId??null,target.hypothesisId??null);
      state.cycles.push({ at:Date.now(), query:target.text, success:true, sourceCount:turn.sources.length, interactive:false });
      if (target.topicId) {
        const topic=state.topics.find(x=>x.id===target.topicId);
        if(topic){topic.evidence+=1;topic.lastAt=Date.now();}
      }
    } catch(error) {
      state.cycles.push({ at:Date.now(), query:target.text, success:false, reason:clean(error.message,400), interactive:false });
      if(target.topicId){const topic=state.topics.find(x=>x.id===target.topicId);if(topic){topic.failures+=1;topic.lastAt=Date.now();}}
    }
  }
} catch (error) {
  state.cycles.push({ at:Date.now(), query:COMMENT || "autonomous", success:false, reason:clean(error.message,500), interactive:EVENT==="issue_comment" });
  if (EVENT === "issue_comment") await reply("## Continuum · Fehler\n\n" + clean(error.message,1000) + "\n\nDer bisherige Lernzustand bleibt erhalten.");
}
saveState(state);
console.log(statusText(state));
