import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { IMMUTABLE_GOVERNANCE } from "./prompt-library.js";

const SCHEMA_VERSION = 1;
const MAX_HYPOTHESES = 192;
const MAX_TOURNAMENTS = 96;
const MAX_REFLECTIONS = 384;

export function createInitialHypothesisState(now = Date.now()) {
  const state = {
    schemaVersion: SCHEMA_VERSION,
    generation: 0,
    createdAt: now,
    updatedAt: now,
    hypotheses: [],
    tournaments: [],
    reflections: [],
    integrityHash: ""
  };
  state.integrityHash = hashState(state);
  return state;
}

export function makeHypothesis({
  generation,
  question,
  hypothesis,
  evidenceRefs = [],
  parentIds = [],
  uncertainty = 0.5,
  testQuestions = [],
  createdAt = Date.now()
}) {
  const q = clean(question, 500);
  const h = clean(hypothesis, 2400);
  if (q.length < 8 || h.length < 30) throw new Error("Hypothesis is too short.");
  const refs = evidenceRefs.map((x) => clean(x, 1000)).filter(Boolean).slice(0, 12);
  const parents = parentIds.map((x) => clean(x, 80)).filter(Boolean).slice(0, 6);
  const tests = testQuestions.map((x) => clean(x, 320)).filter((x) => x.length >= 8).slice(0, 8);
  const digest = sha256([String(generation), q, h, refs.join("|"), parents.join("|")].join("\0"));
  return {
    id: "hyp-" + digest.slice(0, 24),
    generation,
    parentIds: parents,
    question: q,
    hypothesis: h,
    evidenceRefs: refs,
    uncertainty: clamp(Number(uncertainty), 0, 1),
    testQuestions: tests,
    createdAt,
    status: "HYPOTHESIS",
    authority: "NONE",
    elo: 1000,
    wins: 0,
    losses: 0,
    critiques: []
  };
}

export class HypothesisTournamentStore {
  constructor(filePath) {
    this.filePath = filePath;
    this.backupPath = filePath + ".lkg";
  }

  load(now = Date.now()) {
    if (!fs.existsSync(this.filePath)) return createInitialHypothesisState(now);
    try {
      return validateState(JSON.parse(fs.readFileSync(this.filePath, "utf8")));
    } catch (error) {
      if (!fs.existsSync(this.backupPath)) throw error;
      return validateState(JSON.parse(fs.readFileSync(this.backupPath, "utf8")));
    }
  }

  save(state, now = Date.now()) {
    const next = structuredClone(state);
    next.updatedAt = now;
    next.hypotheses = next.hypotheses.slice(-MAX_HYPOTHESES);
    next.tournaments = next.tournaments.slice(-MAX_TOURNAMENTS);
    next.reflections = next.reflections.slice(-MAX_REFLECTIONS);
    next.integrityHash = hashState(next);
    validateState(next);
    fs.mkdirSync(path.dirname(this.filePath), { recursive: true });
    if (fs.existsSync(this.filePath)) {
      const current = fs.readFileSync(this.filePath);
      try {
        validateState(JSON.parse(current.toString("utf8")));
        atomicWrite(this.backupPath, current);
      } catch {}
    }
    atomicWrite(this.filePath, Buffer.from(JSON.stringify(next, null, 2)));
    return next;
  }
}

export class HypothesisTournament {
  constructor({
    statePath,
    generate,
    reflect,
    compare,
    evolve,
    now = () => Date.now(),
    hypothesesPerRound = 4,
    dailyTournamentLimit = 2
  }) {
    if (!statePath) throw new Error("statePath required.");
    for (const [name, fn] of Object.entries({ generate, reflect, compare, evolve })) {
      if (typeof fn !== "function") throw new Error(name + " adapter required.");
    }
    this.store = new HypothesisTournamentStore(statePath);
    this.generate = generate;
    this.reflect = reflect;
    this.compare = compare;
    this.evolve = evolve;
    this.now = now;
    this.hypothesesPerRound = clampInt(hypothesesPerRound, 2, 6);
    this.dailyTournamentLimit = clampInt(dailyTournamentLimit, 1, 8);
  }

  status() {
    const state = this.store.load(this.now());
    const ranked = [...state.hypotheses].sort((a, b) => b.elo - a.elo || a.id.localeCompare(b.id));
    return {
      generation: state.generation,
      hypotheses: state.hypotheses.length,
      tournaments: state.tournaments.length,
      reflections: state.reflections.length,
      leaderId: ranked[0]?.id ?? null,
      leaderElo: ranked[0]?.elo ?? null,
      updatedAt: state.updatedAt
    };
  }

  async run(researchState) {
    const now = this.now();
    let state = this.store.load(now);
    const dayAgo = now - 24 * 60 * 60 * 1000;
    if (state.tournaments.filter((x) => x.createdAt >= dayAgo).length >= this.dailyTournamentLimit) {
      return { skipped: true, reason: "Tagesbudget für Hypothesen-Turniere erreicht.", status: this.status() };
    }
    const context = buildTournamentContext(researchState);
    if (context.observations.length < 2) {
      return { skipped: true, reason: "Zu wenig belegte Beobachtungen für ein Hypothesen-Turnier.", status: this.status() };
    }

    const generation = state.generation + 1;
    const raw = await this.generate({ governance: IMMUTABLE_GOVERNANCE, context, count: this.hypothesesPerRound });
    const hypotheses = [];
    for (const item of (Array.isArray(raw) ? raw : []).slice(0, this.hypothesesPerRound)) {
      try {
        const candidate = makeHypothesis({
          generation,
          question: item.question,
          hypothesis: item.hypothesis,
          evidenceRefs: item.evidenceRefs ?? item.evidence_refs ?? [],
          uncertainty: item.uncertainty,
          testQuestions: item.testQuestions ?? item.test_questions ?? [],
          createdAt: this.now()
        });
        if (!candidate.evidenceRefs.length) continue;
        const critique = await this.reflect({ governance: IMMUTABLE_GOVERNANCE, hypothesis: candidate, context });
        candidate.critiques.push({
          createdAt: this.now(),
          validityRisks: clean(critique.validityRisks ?? critique.validity_risks ?? "", 1000),
          missingEvidence: clean(critique.missingEvidence ?? critique.missing_evidence ?? "", 1000),
          falsificationTest: clean(critique.falsificationTest ?? critique.falsification_test ?? "", 700),
          score: clamp(Number(critique.score), 0, 1)
        });
        hypotheses.push(candidate);
      } catch (error) {
        state.reflections.push({
          generation,
          createdAt: this.now(),
          hypothesisId: null,
          lesson: clean("Generation verworfen: " + (error instanceof Error ? error.message : error), 900)
        });
      }
    }
    if (hypotheses.length < 2) {
      return { skipped: true, reason: "Zu wenige belegte Hypothesen nach Reflection-Gate.", status: this.status() };
    }

    const matches = [];
    for (let i = 0; i < hypotheses.length; i++) {
      for (let j = i + 1; j < hypotheses.length; j++) {
        const a = hypotheses[i];
        const b = hypotheses[j];
        const verdict = await this.compare({ governance: IMMUTABLE_GOVERNANCE, a, b, context });
        const winner = verdict.winner === "B" ? b : a;
        const loser = winner.id === a.id ? b : a;
        updateElo(winner, loser);
        matches.push({
          aId: a.id,
          bId: b.id,
          winnerId: winner.id,
          reason: clean(verdict.reason ?? "", 700)
        });
      }
    }

    const ranked = [...hypotheses].sort((a, b) => b.elo - a.elo || b.critiques[0].score - a.critiques[0].score);
    let evolved = null;
    if (ranked.length >= 2) {
      try {
        const childRaw = await this.evolve({
          governance: IMMUTABLE_GOVERNANCE,
          leaders: ranked.slice(0, 2),
          context
        });
        evolved = makeHypothesis({
          generation,
          question: childRaw.question,
          hypothesis: childRaw.hypothesis,
          evidenceRefs: childRaw.evidenceRefs ?? childRaw.evidence_refs ?? [],
          parentIds: ranked.slice(0, 2).map((x) => x.id),
          uncertainty: childRaw.uncertainty,
          testQuestions: childRaw.testQuestions ?? childRaw.test_questions ?? [],
          createdAt: this.now()
        });
        if (evolved.evidenceRefs.length) {
          const critique = await this.reflect({ governance: IMMUTABLE_GOVERNANCE, hypothesis: evolved, context });
          evolved.critiques.push({
            createdAt: this.now(),
            validityRisks: clean(critique.validityRisks ?? critique.validity_risks ?? "", 1000),
            missingEvidence: clean(critique.missingEvidence ?? critique.missing_evidence ?? "", 1000),
            falsificationTest: clean(critique.falsificationTest ?? critique.falsification_test ?? "", 700),
            score: clamp(Number(critique.score), 0, 1)
          });
          const verdict = await this.compare({ governance: IMMUTABLE_GOVERNANCE, a: ranked[0], b: evolved, context });
          const winner = verdict.winner === "B" ? evolved : ranked[0];
          const loser = winner.id === evolved.id ? ranked[0] : evolved;
          updateElo(winner, loser);
          matches.push({
            aId: ranked[0].id,
            bId: evolved.id,
            winnerId: winner.id,
            reason: clean("Evolution-Challenge: " + (verdict.reason ?? ""), 700)
          });
          hypotheses.push(evolved);
        } else {
          evolved = null;
        }
      } catch (error) {
        state.reflections.push({
          generation,
          createdAt: this.now(),
          hypothesisId: null,
          lesson: clean("Evolution verworfen: " + (error instanceof Error ? error.message : error), 900)
        });
      }
    }

    state.generation = generation;
    state.hypotheses.push(...hypotheses);
    const leaders = [...hypotheses].sort((a, b) => b.elo - a.elo).slice(0, 3);
    for (const h of hypotheses) {
      if (!leaders.some((x) => x.id === h.id)) {
        const weakest = h.critiques[0];
        state.reflections.push({
          generation,
          createdAt: this.now(),
          hypothesisId: h.id,
          lesson: clean(
            "Hypothese verlor Turnier → Elo " + h.elo +
            " → Schwachstelle: " + (weakest?.validityRisks || weakest?.missingEvidence || "niedrige Vergleichsbewertung"),
            900
          )
        });
      }
    }
    state.tournaments.push({
      generation,
      createdAt: this.now(),
      hypothesisIds: hypotheses.map((x) => x.id),
      matches,
      leaderIds: leaders.map((x) => x.id)
    });
    state = this.store.save(state, this.now());

    const researchQuestions = leaders
      .flatMap((x) => x.testQuestions.map((q) => ({
        hypothesisId: x.id,
        question: q,
        elo: x.elo
      })))
      .filter((x, i, all) => all.findIndex((y) => y.question === x.question) === i)
      .slice(0, 10);

    return {
      skipped: false,
      generation,
      hypotheses: hypotheses.map((x) => ({ id: x.id, elo: x.elo, uncertainty: x.uncertainty })),
      leaders: leaders.map((x) => ({ id: x.id, elo: x.elo, hypothesis: x.hypothesis, authority: x.authority })),
      researchQuestions,
      status: this.status()
    };
  }
}

export function buildTournamentContext(researchState) {
  return {
    observations: researchState.observations.slice(-24).map((x) => ({
      id: x.id,
      summary: x.summary,
      citations: x.citations.map((c) => c.url).slice(0, 6),
      uncertainty: x.uncertainty,
      contradictions: x.contradictions
    })),
    openGaps: researchState.gaps.filter((x) => !x.resolved).slice(-16).map((x) => ({
      id: x.id,
      question: x.question,
      attempts: x.attempts
    }))
  };
}

export function createOpenAiHypothesisAdapters({ apiKey, model, baseUrl = "https://api.openai.com/v1" }) {
  const cleanBase = baseUrl.replace(/\/+$/, "");

  async function call(instructions, payload, schema) {
    if (!apiKey) throw new Error("OPENAI_API_KEY fehlt für Hypothesen-Turnier.");
    const response = await fetch(cleanBase + "/responses", {
      method: "POST",
      headers: { Authorization: "Bearer " + apiKey, "Content-Type": "application/json" },
      body: JSON.stringify({
        model,
        instructions,
        input: [{ role: "user", content: JSON.stringify(payload) }],
        text: { format: { type: "json_schema", name: schema.name, strict: true, schema: schema.schema } },
        store: false
      })
    });
    const raw = await response.text();
    let envelope;
    try { envelope = raw ? JSON.parse(raw) : {}; } catch { throw new Error("Hypothesis API returned invalid envelope."); }
    if (!response.ok) throw new Error(envelope?.error?.message ?? ("Hypothesis API HTTP " + response.status));
    return JSON.parse(extractText(envelope));
  }

  return {
    generate: async ({ governance, context, count }) => call(
      governance + "\n\nRolle: Generation Agent. Erzeuge diverse, prüfbare Hypothesen aus vorhandener Evidenz. " +
      "Keine Hypothese ist ein Fakt. Verwende nur evidence_refs aus dem gelieferten Kontext.",
      { context, count },
      {
        name: "hypothesis_generation",
        schema: {
          type: "object",
          properties: {
            hypotheses: {
              type: "array", minItems: 2, maxItems: 6,
              items: {
                type: "object",
                properties: {
                  question: { type: "string", minLength: 8, maxLength: 500 },
                  hypothesis: { type: "string", minLength: 30, maxLength: 2400 },
                  evidence_refs: { type: "array", minItems: 1, maxItems: 12, items: { type: "string", maxLength: 1000 } },
                  uncertainty: { type: "number", minimum: 0, maximum: 1 },
                  test_questions: { type: "array", minItems: 1, maxItems: 8, items: { type: "string", minLength: 8, maxLength: 320 } }
                },
                required: ["question", "hypothesis", "evidence_refs", "uncertainty", "test_questions"],
                additionalProperties: false
              }
            }
          },
          required: ["hypotheses"],
          additionalProperties: false
        }
      }
    ).then((x) => x.hypotheses.slice(0, count)),

    reflect: async ({ governance, hypothesis, context }) => call(
      governance + "\n\nRolle: Reflection Agent. Prüfe die Hypothese wie ein skeptischer Peer Reviewer. " +
      "Suche Gültigkeitsrisiken, fehlende Evidenz und einen konkreten Falsifikationstest.",
      { hypothesis, context },
      {
        name: "hypothesis_reflection",
        schema: {
          type: "object",
          properties: {
            validity_risks: { type: "string", maxLength: 1000 },
            missing_evidence: { type: "string", maxLength: 1000 },
            falsification_test: { type: "string", maxLength: 700 },
            score: { type: "number", minimum: 0, maximum: 1 }
          },
          required: ["validity_risks", "missing_evidence", "falsification_test", "score"],
          additionalProperties: false
        }
      }
    ),

    compare: async ({ governance, a, b, context }) => call(
      governance + "\n\nRolle: Ranking Agent. Vergleiche A und B auf Evidenztreue, Testbarkeit, Erklärungswert, " +
      "Neuheitswert und Unsicherheitskalibrierung. Wähle genau einen Sieger; elo ist nicht sichtbar.",
      { a, b, context },
      {
        name: "hypothesis_pairwise_rank",
        schema: {
          type: "object",
          properties: {
            winner: { type: "string", enum: ["A", "B"] },
            reason: { type: "string", minLength: 10, maxLength: 700 }
          },
          required: ["winner", "reason"],
          additionalProperties: false
        }
      }
    ),

    evolve: async ({ governance, leaders, context }) => call(
      governance + "\n\nRolle: Evolution Agent. Erzeuge aus zwei starken Hypothesen eine neue Hypothese, " +
      "die deren Stärken kombiniert, bekannte Kritik adressiert und klar falsifizierbar bleibt.",
      { leaders, context },
      {
        name: "hypothesis_evolution",
        schema: {
          type: "object",
          properties: {
            question: { type: "string", minLength: 8, maxLength: 500 },
            hypothesis: { type: "string", minLength: 30, maxLength: 2400 },
            evidence_refs: { type: "array", minItems: 1, maxItems: 12, items: { type: "string", maxLength: 1000 } },
            uncertainty: { type: "number", minimum: 0, maximum: 1 },
            test_questions: { type: "array", minItems: 1, maxItems: 8, items: { type: "string", minLength: 8, maxLength: 320 } }
          },
          required: ["question", "hypothesis", "evidence_refs", "uncertainty", "test_questions"],
          additionalProperties: false
        }
      }
    )
  };
}

export function validateState(state) {
  if (!state || state.schemaVersion !== SCHEMA_VERSION) throw new Error("Unsupported hypothesis schema.");
  if (!Array.isArray(state.hypotheses) || state.hypotheses.length > MAX_HYPOTHESES) throw new Error("Invalid hypotheses.");
  if (!Array.isArray(state.tournaments) || state.tournaments.length > MAX_TOURNAMENTS) throw new Error("Invalid tournaments.");
  if (!Array.isArray(state.reflections) || state.reflections.length > MAX_REFLECTIONS) throw new Error("Invalid reflections.");
  for (const h of state.hypotheses) {
    if (h.status !== "HYPOTHESIS" || h.authority !== "NONE") throw new Error("Hypothesis authority violation.");
    if (!Number.isFinite(h.elo) || h.elo < 100 || h.elo > 3000) throw new Error("Invalid Elo.");
  }
  if (state.integrityHash !== hashState(state)) throw new Error("Hypothesis state integrity mismatch.");
  return state;
}

function updateElo(winner, loser, k = 24) {
  const expectedWinner = 1 / (1 + 10 ** ((loser.elo - winner.elo) / 400));
  const delta = Math.max(1, Math.round(k * (1 - expectedWinner)));
  winner.elo = Math.min(3000, winner.elo + delta);
  loser.elo = Math.max(100, loser.elo - delta);
  winner.wins += 1;
  loser.losses += 1;
}

function hashState(state) {
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
    for (const content of item.content ?? []) {
      if (content.type === "output_text" && content.text) parts.push(content.text);
    }
  }
  return parts.join("\n").trim();
}

function clean(value, max) {
  return String(value ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim().slice(0, max);
}

function sha256(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex");
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, Number.isFinite(value) ? value : min));
}

function clampInt(value, min, max) {
  return Math.round(clamp(Number(value), min, max));
}
