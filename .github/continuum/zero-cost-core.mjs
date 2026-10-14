import crypto from "node:crypto";
import fs from "node:fs";
import { spawnSync } from "node:child_process";

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
  "selbstüberwachtes Repräsentationslernen"
];

function freshState() {
  const now = Date.now();
  const state = {
    schemaVersion: 1,
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
    integrityHash: ""
  };
  state.integrityHash = hashState(state);
  return state;
}

function loadState() {
  if (!fs.existsSync(STATE_FILE)) return freshState();
  const state = JSON.parse(fs.readFileSync(STATE_FILE, "utf8"));
  if (state.schemaVersion !== 1) throw new Error("Unsupported state schema");
  if (state.integrityHash !== hashState(state)) throw new Error("Continuum state integrity mismatch");
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
  "into","using","used","their","which","were","such","also","than","when","where","what","your"
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

function nextTarget(state) {
  if (state.queue.length) return { type: "queue", text: state.queue.shift() };
  const ranked = state.topics.map(t => {
    const age = t.lastAt ? Math.min(3, (Date.now() - t.lastAt) / 86400000) : 4;
    return { ...t, score: 3 / (1 + t.evidence) + age + Math.min(t.failures, 3) * .15 };
  }).sort((a,b) => b.score - a.score || a.id.localeCompare(b.id));
  return { type: "topic", text: ranked[0].name, topicId: ranked[0].id };
}

async function research(query) {
  const results = [];
  await Promise.allSettled([
    wikipedia(query).then(x => results.push(...x)),
    openAlex(query).then(x => results.push(...x)),
    crossref(query).then(x => results.push(...x))
  ]);
  const deduped = [];
  const seen = new Set();
  for (const item of results) {
    const key = (item.url || item.title).toLocaleLowerCase("de-DE");
    if (!key || seen.has(key)) continue;
    seen.add(key);
    deduped.push(item);
  }
  return deduped.slice(0, 9);
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

async function maybeCopilot(query, evidence) {
  if (EVENT !== "issue_comment" || process.env.CONTINUUM_COPILOT !== "1") return null;
  const prompt = [
    "Du bist die interaktive Oberfläche des Device-AI-Continuum-Kerns.",
    "Beantworte ausschließlich aus dem folgenden abgerufenen Evidenzpaket.",
    "Erfinde keine Quellen. Wenn die Evidenz nicht reicht, sage das.",
    "Schreibe auf Deutsch, präzise, kompakt und mit [1], [2] usw. passend zu den Quellen.",
    "",
    "FRAGE: " + query,
    "",
    "EVIDENZ:",
    ...evidence.map((s,i) => "[" + (i+1) + "] " + s.title + " — " + s.url + "\n" + s.text.slice(0,1400))
  ].join("\n");
  const run = spawnSync("copilot", ["-p", prompt, "-s"], {
    encoding: "utf8",
    timeout: 90000,
    env: { ...process.env, GITHUB_TOKEN: TOKEN }
  });
  if (run.status !== 0 || !run.stdout?.trim()) return null;
  return clean(run.stdout, 7000);
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
    citations: sources.map(x => ({ title:x.title, url:x.url, source:x.source })).slice(0,9),
    createdAt: now,
    trust: "PUBLIC_SOURCE_OBSERVATION",
    authority: "NONE"
  };
}

async function researchTurn(state, query, interact = false) {
  const sources = await research(query);
  if (!sources.length) throw new Error("Keine belastbare öffentliche Quelle gefunden.");
  learnGraph(state, sources.map(x => x.text));
  const deterministic = synthesize(query, sources, state);
  const copilot = interact ? await maybeCopilot(query, sources) : null;
  const answer = copilot || deterministic.text ||
    "Quellen wurden gefunden, aber für eine belastbare Synthese reicht der extrahierte Text noch nicht.";
  const observation = makeObservation(query, sources, answer);
  if (!state.observations.some(x => x.id === observation.id)) state.observations.push(observation);
  state.queue.push(
    "Welche unabhängigen Quellen widersprechen oder begrenzen die bisherigen Aussagen zu: " + query,
    "Welche messbaren Benchmarks oder Experimente prüfen den Fortschritt bei: " + query
  );
  return { observation, answer, sources, copilot: Boolean(copilot) };
}

function statusText(state) {
  const top = Object.entries(state.concepts).sort((a,b) => b[1]-a[1]).slice(0,10).map(x=>x[0]).join(", ");
  const successes = state.cycles.filter(x=>x.success).length;
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
    state.interactions.push({ at:Date.now(), user:COMMENT_USER, text:clean(body,1000) });
    if (lower === "/status" || lower === "status") {
      await reply(statusText(state));
    } else if (lower.startsWith("/learn ")) {
      const lesson = clean(body.slice(7),1200);
      const id = "lesson-" + crypto.createHash("sha256").update(lesson).digest("hex").slice(0,20);
      if (!state.lessons.some(x=>x.id===id)) state.lessons.push({id,text:lesson,createdAt:Date.now(),provenance:"USER_EXPLICIT"});
      learnGraph(state,[lesson]);
      await reply("## Continuum · Lektion übernommen\n\nAls **explizite Nutzerlektion** gespeichert: " + lesson + "\n\nSie wird nicht mit Webquellen gleichgesetzt.");
    } else {
      const query = lower.startsWith("/research ") ? body.slice(10).trim()
        : lower.startsWith("/ask ") ? body.slice(5).trim()
        : body;
      if (!query) {
        await reply("Schreibe eine Frage, `/research <Thema>`, `/learn <Text>` oder `/status`.");
      } else {
        const turn = await researchTurn(state, query, true);
        state.cycles.push({ at:Date.now(), query, success:true, sourceCount:turn.sources.length, interactive:true });
        await reply(formatResearch(query,turn,state));
      }
    }
  } else {
    const target = nextTarget(state);
    try {
      const turn = await researchTurn(state,target.text,false);
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
