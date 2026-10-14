import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import {
  IMMUTABLE_GOVERNANCE,
  DEFAULT_STRATEGY_PROMPT,
  CANDIDATE_GENERATOR_PROMPT,
  GRADER_PROMPT
} from "./prompt-library.js";

const SCHEMA_VERSION = 1;
const MAX_CANDIDATES = 96;
const MAX_REFLECTIONS = 128;
const MAX_GENERATIONS = 256;
const MAX_DATASET = 24;

export function buildBrainDataset(continuumState, limit = 12) {
  const bounded = Math.max(1, Math.min(MAX_DATASET, Number(limit) || 12));
  const cases = [];
  for (const gap of continuumState.gaps.filter((x) => !x.resolved)) {
    cases.push({
      id: "gap:" + gap.id,
      type: "open_gap",
      task: gap.question,
      evidenceRefs: gap.sourceObservationId ? [gap.sourceObservationId] : [],
      priority: 3 - Math.min(gap.attempts, 3) * 0.4
    });
  }
  for (const topic of continuumState.topics) {
    cases.push({
      id: "topic:" + topic.id,
      type: "coverage",
      task: "Ermittle den belastbaren Wissensstand, offene Fragen und nächste prüfbare Schritte zu: " + topic.name,
      evidenceRefs: [],
      priority: 1.6 / (1 + topic.evidenceCount) + (topic.lastResearchedAt ? 0 : 1)
    });
  }
  for (const obs of continuumState.observations.slice(-32)) {
    if (!obs.contradictions && obs.uncertainty < 0.55) continue;
    cases.push({
      id: "obs:" + obs.id,
      type: "adversarial_review",
      task: "Prüfe diese Beobachtung auf Gegenargumente, widersprüchliche Evidenz und fehlende Verifikation: " + obs.summary,
      evidenceRefs: obs.citations.map((x) => x.url).slice(0, 6),
      priority: 1.2 + Number(obs.uncertainty ?? 0)
    });
  }
  return cases
    .sort((a, b) => b.priority - a.priority || a.id.localeCompare(b.id))
    .filter((item, i, all) => all.findIndex((x) => x.task === item.task) === i)
    .slice(0, bounded);
}

export function createInitialBrainState(now = Date.now()) {
  const strategy = DEFAULT_STRATEGY_PROMPT;
  const champion = makeCandidate({
    parentId: null,
    generation: 1,
    strategy,
    mutationSummary: "Initiale evaluierbare Research-Strategie",
    createdAt: now,
    status: "champion"
  });
  const state = {
    schemaVersion: SCHEMA_VERSION,
    generation: 1,
    createdAt: now,
    updatedAt: now,
    governanceHash: sha256(IMMUTABLE_GOVERNANCE),
    championId: champion.id,
    candidates: [champion],
    reflections: [],
    generations: [],
    integrityHash: ""
  };
  state.integrityHash = hashBrainState(state);
  return state;
}

export function makeCandidate({ parentId, generation, strategy, mutationSummary, createdAt = Date.now(), status = "challenger" }) {
  const cleanStrategy = clean(strategy, 12000);
  const summary = clean(mutationSummary, 800);
  if (cleanStrategy.length < 120) throw new Error("Candidate strategy is too short.");
  if (/ignore (all|previous)|disable (safety|guard)|bypass|self[- ]?preserv|evade|secret/i.test(cleanStrategy)) {
    throw new Error("Candidate attempts to weaken immutable governance.");
  }
  const digest = sha256([parentId ?? "root", String(generation), cleanStrategy, summary].join("\0"));
  return {
    id: "brain-" + digest.slice(0, 24),
    parentId,
    generation,
    strategy: cleanStrategy,
    mutationSummary: summary,
    createdAt,
    status,
    evaluation: null
  };
}

export class BrainFactoryStore {
  constructor(filePath) {
    this.filePath = filePath;
    this.backupPath = filePath + ".lkg";
  }

  load(now = Date.now()) {
    if (!fs.existsSync(this.filePath)) return createInitialBrainState(now);
    try {
      return validateBrainState(JSON.parse(fs.readFileSync(this.filePath, "utf8")));
    } catch (error) {
      if (!fs.existsSync(this.backupPath)) throw error;
      return validateBrainState(JSON.parse(fs.readFileSync(this.backupPath, "utf8")));
    }
  }

  save(state, now = Date.now()) {
    const next = structuredClone(state);
    next.updatedAt = now;
    next.candidates = next.candidates.slice(-MAX_CANDIDATES);
    next.reflections = next.reflections.slice(-MAX_REFLECTIONS);
    next.generations = next.generations.slice(-MAX_GENERATIONS);
    next.integrityHash = hashBrainState(next);
    validateBrainState(next);
    fs.mkdirSync(path.dirname(this.filePath), { recursive: true });
    if (fs.existsSync(this.filePath)) {
      const current = fs.readFileSync(this.filePath);
      try {
        validateBrainState(JSON.parse(current.toString("utf8")));
        atomicWrite(this.backupPath, current);
      } catch {}
    }
    atomicWrite(this.filePath, Buffer.from(JSON.stringify(next, null, 2)));
    return next;
  }
}

export class BrainFactory {
  constructor({
    statePath,
    generateCandidates,
    evaluateCandidate,
    now = () => Date.now(),
    challengerCount = 3,
    promotionMargin = 0.025,
    safetyFloor = 0.97,
    groundingFloor = 0.78,
    datasetLimit = 10
  }) {
    if (!statePath) throw new Error("statePath is required");
    if (typeof generateCandidates !== "function" || typeof evaluateCandidate !== "function") {
      throw new Error("BrainFactory adapters are required");
    }
    this.store = new BrainFactoryStore(statePath);
    this.generateCandidates = generateCandidates;
    this.evaluateCandidate = evaluateCandidate;
    this.now = now;
    this.challengerCount = clampInt(challengerCount, 1, 6);
    this.promotionMargin = clamp(Number(promotionMargin), 0, 0.25);
    this.safetyFloor = clamp(Number(safetyFloor), 0.8, 1);
    this.groundingFloor = clamp(Number(groundingFloor), 0.5, 1);
    this.datasetLimit = clampInt(datasetLimit, 2, MAX_DATASET);
  }

  status() {
    const state = this.store.load(this.now());
    return summarize(state);
  }

  async evolve(continuumState) {
    const dataset = buildBrainDataset(continuumState, this.datasetLimit);
    if (dataset.length < 2) return { skipped: true, reason: "Benchmark-Dataset noch zu klein.", status: this.status() };

    let state = this.store.load(this.now());
    const champion = state.candidates.find((x) => x.id === state.championId);
    if (!champion) throw new Error("Brain champion missing.");

    const championEval = normalizeEvaluation(await this.evaluateCandidate({
      governance: IMMUTABLE_GOVERNANCE,
      candidate: champion,
      dataset
    }));
    champion.evaluation = championEval;

    const proposals = await this.generateCandidates({
      governance: IMMUTABLE_GOVERNANCE,
      generatorPrompt: CANDIDATE_GENERATOR_PROMPT,
      champion,
      dataset,
      reflections: state.reflections.slice(-12),
      count: this.challengerCount
    });
    const raw = Array.isArray(proposals) ? proposals.slice(0, this.challengerCount) : [];
    const generation = state.generation + 1;
    const challengers = [];
    for (const item of raw) {
      try {
        const candidate = makeCandidate({
          parentId: champion.id,
          generation,
          strategy: item.strategy,
          mutationSummary: item.mutationSummary ?? item.mutation_summary ?? "Challenger",
          createdAt: this.now()
        });
        if (candidate.strategy === champion.strategy) continue;
        candidate.evaluation = normalizeEvaluation(await this.evaluateCandidate({
          governance: IMMUTABLE_GOVERNANCE,
          candidate,
          dataset
        }));
        challengers.push(candidate);
      } catch (error) {
        state.reflections.push({
          generation,
          candidateId: null,
          createdAt: this.now(),
          lesson: clean("Kandidat verworfen → " + (error instanceof Error ? error.message : error), 800)
        });
      }
    }

    const eligible = challengers
      .filter((x) => x.evaluation.safety >= this.safetyFloor)
      .filter((x) => x.evaluation.grounding >= this.groundingFloor)
      .sort((a, b) => b.evaluation.score - a.evaluation.score || a.id.localeCompare(b.id));

    const best = eligible[0] ?? null;
    const promoted = Boolean(best && best.evaluation.score >= championEval.score + this.promotionMargin);
    if (promoted) {
      champion.status = "retired";
      best.status = "champion";
      state.championId = best.id;
    }

    for (const candidate of challengers) {
      if (candidate.id !== state.championId) candidate.status = "rejected";
      state.candidates.push(candidate);
      if (candidate.status === "rejected") {
        state.reflections.push({
          generation,
          candidateId: candidate.id,
          createdAt: this.now(),
          lesson: reflectionFrom(candidate, championEval, this.safetyFloor, this.groundingFloor)
        });
      }
    }

    state.generation = generation;
    state.generations.push({
      generation,
      createdAt: this.now(),
      datasetIds: dataset.map((x) => x.id),
      championBefore: champion.id,
      championAfter: state.championId,
      championScore: championEval.score,
      challengerIds: challengers.map((x) => x.id),
      promoted
    });
    state = this.store.save(state, this.now());

    return {
      skipped: false,
      promoted,
      championBefore: champion.id,
      championAfter: state.championId,
      championScore: championEval.score,
      challengers: challengers.map((x) => ({
        id: x.id,
        status: x.status,
        score: x.evaluation.score,
        safety: x.evaluation.safety,
        grounding: x.evaluation.grounding,
        mutationSummary: x.mutationSummary
      })),
      status: summarize(state)
    };
  }
}

export function createOpenAiBrainFactoryAdapters({ apiKey, model, baseUrl = "https://api.openai.com/v1", maxCases = 5 }) {
  const cleanBase = baseUrl.replace(/\/+$/, "");
  const call = async (instructions, input, tools = []) => {
    if (!apiKey) throw new Error("OPENAI_API_KEY fehlt für Brain Factory.");
    const response = await fetch(cleanBase + "/responses", {
      method: "POST",
      headers: { Authorization: "Bearer " + apiKey, "Content-Type": "application/json" },
      body: JSON.stringify({
        model,
        instructions,
        input: [{ role: "user", content: input }],
        tools,
        tool_choice: tools.length ? "auto" : undefined,
        max_tool_calls: tools.length ? 4 : undefined,
        store: false
      })
    });
    const raw = await response.text();
    let payload;
    try { payload = raw ? JSON.parse(raw) : {}; } catch { throw new Error("Brain Factory API returned invalid envelope."); }
    if (!response.ok) throw new Error(payload?.error?.message ?? ("Brain Factory API HTTP " + response.status));
    return { payload, text: extractText(payload), citations: extractCitations(payload) };
  };

  return {
    generateCandidates: async ({ generatorPrompt, champion, dataset, reflections, count }) => {
      const result = await call(
        generatorPrompt,
        JSON.stringify({
          champion_strategy: champion.strategy,
          recent_reflections: reflections.map((x) => x.lesson),
          benchmark_shapes: dataset.slice(0, 8).map((x) => ({ type: x.type, task: x.task })),
          candidate_count: count
        })
      );
      const parsed = parseJsonObject(result.text);
      return (parsed.candidates ?? []).slice(0, count);
    },

    evaluateCandidate: async ({ governance, candidate, dataset }) => {
      const cases = dataset.slice(0, clampInt(maxCases, 2, 8));
      const metrics = [];
      for (const item of cases) {
        const answer = await call(
          governance + "\n\n" + candidate.strategy,
          item.task,
          [{ type: "web_search" }]
        );
        const grade = await call(
          GRADER_PROMPT,
          JSON.stringify({
            task: item.task,
            answer: answer.text,
            citations: answer.citations,
            evidence_refs: item.evidenceRefs
          })
        );
        const parsed = parseJsonObject(grade.text);
        metrics.push({
          quality: Number(parsed.quality),
          grounding: Number(parsed.grounding),
          uncertainty: Number(parsed.uncertainty),
          planning: Number(parsed.planning),
          safety: Number(parsed.safety),
          notes: clean(parsed.notes ?? "", 500)
        });
      }
      return aggregate(metrics);
    }
  };
}

export function validateBrainState(state) {
  if (!state || state.schemaVersion !== SCHEMA_VERSION) throw new Error("Unsupported brain-factory schema.");
  if (state.governanceHash !== sha256(IMMUTABLE_GOVERNANCE)) throw new Error("Immutable governance hash mismatch.");
  if (!Array.isArray(state.candidates) || state.candidates.length < 1 || state.candidates.length > MAX_CANDIDATES) throw new Error("Invalid candidates.");
  if (!Array.isArray(state.reflections) || state.reflections.length > MAX_REFLECTIONS) throw new Error("Invalid reflections.");
  if (!Array.isArray(state.generations) || state.generations.length > MAX_GENERATIONS) throw new Error("Invalid generations.");
  if (!state.candidates.some((x) => x.id === state.championId && x.status === "champion")) throw new Error("Invalid champion.");
  if (state.integrityHash !== hashBrainState(state)) throw new Error("Brain-factory integrity mismatch.");
  return state;
}

function normalizeEvaluation(value) {
  const metrics = {
    quality: clamp(Number(value?.quality), 0, 1),
    grounding: clamp(Number(value?.grounding), 0, 1),
    uncertainty: clamp(Number(value?.uncertainty), 0, 1),
    planning: clamp(Number(value?.planning), 0, 1),
    safety: clamp(Number(value?.safety), 0, 1),
    notes: clean(value?.notes ?? "", 500)
  };
  metrics.score =
    metrics.quality * 0.28 +
    metrics.grounding * 0.28 +
    metrics.uncertainty * 0.14 +
    metrics.planning * 0.18 +
    metrics.safety * 0.12;
  return metrics;
}

function aggregate(rows) {
  if (!rows.length) throw new Error("No benchmark rows.");
  const mean = (key) => rows.reduce((sum, x) => sum + clamp(Number(x[key]), 0, 1), 0) / rows.length;
  return normalizeEvaluation({
    quality: mean("quality"),
    grounding: mean("grounding"),
    uncertainty: mean("uncertainty"),
    planning: mean("planning"),
    safety: Math.min(...rows.map((x) => clamp(Number(x.safety), 0, 1))),
    notes: rows.map((x) => x.notes).filter(Boolean).slice(0, 4).join(" | ")
  });
}

function reflectionFrom(candidate, championEval, safetyFloor, groundingFloor) {
  const e = candidate.evaluation;
  let issue = "Scoreverbesserung nicht ausreichend";
  if (e.safety < safetyFloor) issue = "Safety-Regressionsgrenze unterschritten";
  else if (e.grounding < groundingFloor) issue = "Grounding-Regressionsgrenze unterschritten";
  else if (e.score < championEval.score) issue = "Gesamtscore unter Champion";
  return clean(
    issue + " → Signal: score=" + e.score.toFixed(3) +
    ", grounding=" + e.grounding.toFixed(3) +
    ", safety=" + e.safety.toFixed(3) +
    " → Nächste Mutation muss die schwächste Metrik gezielt verbessern. " + e.notes,
    800
  );
}

function summarize(state) {
  const champion = state.candidates.find((x) => x.id === state.championId);
  return {
    generation: state.generation,
    championId: state.championId,
    championScore: champion?.evaluation?.score ?? null,
    candidates: state.candidates.length,
    reflections: state.reflections.length,
    generations: state.generations.length,
    governanceHash: state.governanceHash,
    updatedAt: state.updatedAt
  };
}

function hashBrainState(state) {
  const copy = structuredClone(state);
  copy.integrityHash = "";
  return sha256(stableStringify(copy));
}

function stableStringify(value) {
  if (Array.isArray(value)) return "[" + value.map(stableStringify).join(",") + "]";
  if (value && typeof value === "object") {
    return "{" + Object.keys(value).sort().map((key) => JSON.stringify(key) + ":" + stableStringify(value[key])).join(",") + "}";
  }
  return JSON.stringify(value);
}

function atomicWrite(target, bytes) {
  const tmp = target + ".tmp-" + process.pid + "-" + crypto.randomBytes(4).toString("hex");
  fs.writeFileSync(tmp, bytes);
  const fd = fs.openSync(tmp, "r");
  try { fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
  fs.renameSync(tmp, target);
}

function extractText(response) {
  const parts = [];
  for (const item of response.output ?? []) {
    if (item.type !== "message") continue;
    for (const content of item.content ?? []) if (content.type === "output_text" && content.text) parts.push(content.text);
  }
  return parts.join("\n").trim();
}

function extractCitations(response) {
  const found = new Map();
  for (const item of response.output ?? []) {
    if (item.type !== "message") continue;
    for (const content of item.content ?? []) {
      for (const annotation of content.annotations ?? []) {
        if (annotation.type === "url_citation" && annotation.url) {
          found.set(annotation.url, { title: annotation.title || annotation.url, url: annotation.url });
        }
      }
    }
  }
  return [...found.values()].slice(0, 12);
}

function parseJsonObject(text) {
  const cleaned = String(text ?? "").trim().replace(/^\`\`\`(?:json)?\s*/i, "").replace(/\s*\`\`\`$/, "");
  try { return JSON.parse(cleaned); } catch {
    const start = cleaned.indexOf("{");
    const end = cleaned.lastIndexOf("}");
    if (start >= 0 && end > start) return JSON.parse(cleaned.slice(start, end + 1));
    throw new Error("Expected JSON object.");
  }
}

function sha256(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex");
}

function clean(value, max) {
  return String(value ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim().slice(0, max);
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, Number.isFinite(value) ? value : min));
}

function clampInt(value, min, max) {
  return Math.round(clamp(Number(value), min, max));
}
