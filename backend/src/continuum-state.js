import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

export const STATE_SCHEMA_VERSION = 1;
export const MAX_OBSERVATIONS = 512;
export const MAX_GAPS = 128;
export const MAX_CYCLES = 1024;

const DEFAULT_TOPICS = [
  "Künstliche Intelligenz und maschinelles Lernen",
  "Android Plattform und mobile Systeme",
  "Softwarearchitektur und Programmiersprachen",
  "IT-Sicherheit und Datenschutz",
  "Mathematik und Statistik",
  "Physik und Astronomie",
  "Chemie und Materialwissenschaft",
  "Biologie und Neurowissenschaft",
  "Medizinische Forschung allgemein",
  "Energie und Elektrotechnik",
  "Robotik und Automatisierung",
  "Wirtschaft und Technologie",
  "Geschichte und Gesellschaft",
  "Geografie und Umweltwissenschaft",
  "Linguistik und Kommunikation",
  "Psychologie und Kognitionswissenschaft",
  "Rechtssysteme und digitale Regulierung",
  "Ingenieurwesen und Fertigung",
  "Verkehr und Mobilität",
  "Wissenschaftsmethodik und Erkenntnistheorie"
];

export function createInitialState(now = Date.now()) {
  const state = {
    schemaVersion: STATE_SCHEMA_VERSION,
    generation: 1,
    createdAt: now,
    updatedAt: now,
    topics: DEFAULT_TOPICS.map((name, index) => ({
      id: "topic-" + String(index + 1).padStart(2, "0"),
      name,
      evidenceCount: 0,
      successCount: 0,
      failureCount: 0,
      lastResearchedAt: null,
      lastSuccessfulAt: null
    })),
    gaps: [],
    observations: [],
    cycles: [],
    integrityHash: ""
  };
  state.integrityHash = hashState(state);
  return state;
}

export function hashState(state) {
  const copy = structuredClone(state);
  copy.integrityHash = "";
  return sha256(stableStringify(copy));
}

export function validateState(state) {
  if (!state || state.schemaVersion !== STATE_SCHEMA_VERSION) throw new Error("Unsupported continuum state schema.");
  if (!Number.isInteger(state.generation) || state.generation < 1) throw new Error("Invalid continuum generation.");
  if (!Array.isArray(state.topics) || state.topics.length < 1 || state.topics.length > 128) throw new Error("Invalid topic set.");
  if (!Array.isArray(state.gaps) || state.gaps.length > MAX_GAPS) throw new Error("Invalid gap set.");
  if (!Array.isArray(state.observations) || state.observations.length > MAX_OBSERVATIONS) throw new Error("Invalid observations.");
  if (!Array.isArray(state.cycles) || state.cycles.length > MAX_CYCLES) throw new Error("Invalid cycles.");
  if (state.integrityHash !== hashState(state)) throw new Error("Continuum state integrity mismatch.");
  return state;
}

export class ContinuumStateStore {
  constructor(filePath) {
    this.filePath = filePath;
    this.backupPath = filePath + ".lkg";
  }

  load(now = Date.now()) {
    if (!fs.existsSync(this.filePath)) return createInitialState(now);
    try {
      return validateState(JSON.parse(fs.readFileSync(this.filePath, "utf8")));
    } catch (primaryError) {
      if (!fs.existsSync(this.backupPath)) throw primaryError;
      return validateState(JSON.parse(fs.readFileSync(this.backupPath, "utf8")));
    }
  }

  save(state, now = Date.now()) {
    const next = structuredClone(state);
    next.schemaVersion = STATE_SCHEMA_VERSION;
    next.generation = Number(next.generation ?? 0) + 1;
    next.updatedAt = now;
    next.gaps = next.gaps.slice(-MAX_GAPS);
    next.observations = next.observations.slice(-MAX_OBSERVATIONS);
    next.cycles = next.cycles.slice(-MAX_CYCLES);
    next.integrityHash = hashState(next);
    validateState(next);

    fs.mkdirSync(path.dirname(this.filePath), { recursive: true });
    if (fs.existsSync(this.filePath)) {
      const current = fs.readFileSync(this.filePath);
      try {
        validateState(JSON.parse(current.toString("utf8")));
        atomicWrite(this.backupPath, current);
      } catch {
        // Never bless an invalid primary as last-known-good.
      }
    }
    atomicWrite(this.filePath, Buffer.from(JSON.stringify(next, null, 2)));
    return next;
  }
}

export function makeObservation({ topicId, query, summary, citations, nextQuestions, contradictions, uncertainty, now = Date.now() }) {
  const cleanCitations = citations
    .map((x) => ({ title: cleanText(x.title, 180), url: normalizeHttpsUrl(x.url) }))
    .filter((x) => x.url)
    .filter((x, i, all) => all.findIndex((y) => y.url === x.url) === i)
    .slice(0, 12);
  if (cleanCitations.length === 0) throw new Error("Research observation requires at least one HTTPS citation.");
  const cleanSummary = cleanText(summary, 5000);
  if (cleanSummary.length < 40) throw new Error("Research summary is too short.");
  const sourceDigest = sha256(cleanCitations.map((x) => x.url).sort().join("\n"));
  const contentHash = sha256([topicId, query, cleanSummary, sourceDigest].join("\0"));
  return {
    id: "obs-" + contentHash.slice(0, 24),
    topicId,
    query: cleanText(query, 280),
    summary: cleanSummary,
    citations: cleanCitations,
    sourceDigest,
    contentHash,
    nextQuestions: sanitizeQuestions(nextQuestions),
    contradictions: cleanText(String(contradictions ?? ""), 1200),
    uncertainty: clamp(Number(uncertainty ?? 0.5), 0, 1),
    createdAt: now,
    trust: "WEB_OBSERVATION",
    authority: "NONE"
  };
}

export function makeGap(question, sourceObservationId, now = Date.now()) {
  const normalized = cleanText(question, 260);
  if (normalized.length < 8) return null;
  const id = "gap-" + sha256(normalized.toLocaleLowerCase("de-DE")).slice(0, 24);
  return {
    id,
    question: normalized,
    sourceObservationId,
    createdAt: now,
    lastTriedAt: null,
    attempts: 0,
    resolved: false
  };
}

export function mergeObservation(state, observation, now = Date.now()) {
  const next = structuredClone(state);
  if (!next.observations.some((x) => x.id === observation.id)) {
    next.observations.push(observation);
  }
  const topic = next.topics.find((x) => x.id === observation.topicId);
  if (topic) {
    topic.evidenceCount += 1;
    topic.successCount += 1;
    topic.lastResearchedAt = now;
    topic.lastSuccessfulAt = now;
  }
  for (const question of observation.nextQuestions) {
    const gap = makeGap(question, observation.id, now);
    if (!gap) continue;
    const existing = next.gaps.find((x) => x.id === gap.id);
    if (!existing) next.gaps.push(gap);
  }
  return next;
}

export function recordCycle(state, cycle, now = Date.now()) {
  const next = structuredClone(state);
  next.cycles.push({
    id: cycle.id,
    targetType: cycle.targetType,
    targetId: cycle.targetId,
    query: cleanText(cycle.query, 280),
    strategy: cleanText(cycle.strategy, 80),
    success: Boolean(cycle.success),
    reason: cleanText(cycle.reason ?? "", 500),
    observationId: cycle.observationId ?? null,
    startedAt: cycle.startedAt,
    completedAt: now
  });
  const topic = next.topics.find((x) => x.id === cycle.targetId);
  if (topic) {
    topic.lastResearchedAt = now;
    if (!cycle.success) topic.failureCount += 1;
  }
  const gap = next.gaps.find((x) => x.id === cycle.targetId);
  if (gap) {
    gap.lastTriedAt = now;
    gap.attempts += 1;
    if (cycle.success) gap.resolved = true;
  }
  return next;
}

export function stateSummary(state) {
  validateState(state);
  const successful = state.cycles.filter((x) => x.success).length;
  return {
    generation: state.generation,
    updatedAt: state.updatedAt,
    topics: state.topics.length,
    observations: state.observations.length,
    openGaps: state.gaps.filter((x) => !x.resolved).length,
    cycles: state.cycles.length,
    successfulCycles: successful,
    failedCycles: state.cycles.length - successful,
    citationCount: state.observations.reduce((n, x) => n + x.citations.length, 0),
    integrityHash: state.integrityHash
  };
}

function atomicWrite(target, bytes) {
  const tmp = target + ".tmp-" + process.pid + "-" + crypto.randomBytes(4).toString("hex");
  fs.writeFileSync(tmp, bytes);
  const handle = fs.openSync(tmp, "r");
  try { fs.fsyncSync(handle); } finally { fs.closeSync(handle); }
  fs.renameSync(tmp, target);
}

function stableStringify(value) {
  if (Array.isArray(value)) return "[" + value.map(stableStringify).join(",") + "]";
  if (value && typeof value === "object") {
    return "{" + Object.keys(value).sort().map((key) =>
      JSON.stringify(key) + ":" + stableStringify(value[key])
    ).join(",") + "}";
  }
  return JSON.stringify(value);
}

function sanitizeQuestions(values) {
  if (!Array.isArray(values)) return [];
  return values
    .map((x) => cleanText(String(x), 260))
    .filter((x) => x.length >= 8)
    .filter((x, i, all) => all.indexOf(x) === i)
    .slice(0, 10);
}

function cleanText(value, max) {
  return String(value ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim().slice(0, max);
}

function normalizeHttpsUrl(value) {
  try {
    const url = new URL(String(value ?? ""));
    if (url.protocol !== "https:") return null;
    url.hash = "";
    return url.toString().slice(0, 1000);
  } catch {
    return null;
  }
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, Number.isFinite(value) ? value : min));
}
