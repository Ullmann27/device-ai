import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import {
  HypothesisTournament,
  HypothesisTournamentStore,
  createInitialHypothesisState,
  makeHypothesis,
  validateState
} from "../src/hypothesis-tournament.js";
import { createInitialState, makeObservation, mergeObservation } from "../src/continuum-state.js";

function researchFixture() {
  let state = createInitialState(1000);
  const observations = [
    makeObservation({
      topicId: "topic-01", query: "Agenten Evals",
      summary: "Mehrere technische Quellen berichten, dass wiederholbare Evaluatoren und Regressionstests die Zuverlässigkeit agentischer Systeme verbessern.",
      citations: [{ title: "A", url: "https://example.org/evals" }],
      nextQuestions: ["Welche Evaluationssignale korrelieren am stärksten mit realer Agentenleistung?"],
      contradictions: "Automatische Grader können selbst Fehlkalibrierungen aufweisen.", uncertainty: .45, now: 1100
    }),
    makeObservation({
      topicId: "topic-01", query: "Agent Memory",
      summary: "Episodisches Reflexionsgedächtnis kann Fehlersignale für spätere Versuche verfügbar machen, ohne Modellgewichte nach jedem Schritt zu ändern.",
      citations: [{ title: "B", url: "https://example.net/memory" }],
      nextQuestions: ["Wann verursacht Reflexionsgedächtnis statt Verbesserung eine Fehlerverstärkung?"],
      contradictions: "", uncertainty: .5, now: 1200
    })
  ];
  for (const obs of observations) state = mergeObservation(state, obs, obs.createdAt + 10);
  return state;
}

test("hypotheses are explicitly non-authoritative", () => {
  const h = makeHypothesis({
    generation: 1,
    question: "Verbessert Reflexionsgedächtnis robuste Planung?",
    hypothesis: "Ein begrenztes Reflexionsgedächtnis könnte die Wiederholung bereits erkannter Fehlpfade reduzieren.",
    evidenceRefs: ["https://example.org/a"],
    testQuestions: ["Vergleiche Erfolgsrate mit und ohne Reflexionsspeicher."]
  });
  assert.equal(h.status, "HYPOTHESIS");
  assert.equal(h.authority, "NONE");
});

test("tournament store is integrity checked with LKG recovery", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "hyp-store-"));
  try {
    const file = path.join(dir, "hyp.json");
    const store = new HypothesisTournamentStore(file);
    const first = store.save(createInitialHypothesisState(1000), 1100);
    assert.equal(validateState(first), first);
    const second = store.save(first, 1200);
    const bytes = Buffer.from(fs.readFileSync(file));
    bytes[Math.floor(bytes.length / 2)] ^= 1;
    fs.writeFileSync(file, bytes);
    const recovered = store.load(1300);
    assert.equal(recovered.integrityHash, first.integrityHash);
    assert.notEqual(second.integrityHash, first.integrityHash);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("generation reflection Elo tournament evolution and research questions form a closed loop", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "hyp-tournament-"));
  try {
    let now = 2000;
    const tournament = new HypothesisTournament({
      statePath: path.join(dir, "hyp.json"),
      now: () => now++,
      hypothesesPerRound: 3,
      generate: async () => [
        {
          question: "Welche Eval-Architektur ist robust?",
          hypothesis: "Ein unabhängiger Grader plus feste Holdouts reduziert unerkannte Regressionen gegenüber Selbstbewertung allein.",
          evidenceRefs: ["https://example.org/evals"], uncertainty: .3,
          testQuestions: ["Vergleiche Regressionen mit Selbstbewertung gegen unabhängigen Grader."]
        },
        {
          question: "Hilft episodisches Reflexionsgedächtnis?",
          hypothesis: "Kurze beobachtbare Fehlersignale im episodischen Speicher reduzieren wiederholte Fehlpfade in späteren Versuchen.",
          evidenceRefs: ["https://example.net/memory"], uncertainty: .4,
          testQuestions: ["Messe Wiederholungsfehler mit und ohne Reflexionsgedächtnis."]
        },
        {
          question: "Wie kombiniert man beide?",
          hypothesis: "Eine Kombination aus Reflexionsgedächtnis und externem Evaluator könnte bessere Recovery-Entscheidungen liefern als jede Komponente allein.",
          evidenceRefs: ["https://example.org/evals", "https://example.net/memory"], uncertainty: .5,
          testQuestions: ["Führe einen kontrollierten Ablationstest über beide Komponenten durch."]
        }
      ],
      reflect: async ({ hypothesis }) => ({
        validityRisks: "Korrelation ist noch keine Kausalität.",
        missingEvidence: "Es fehlen Ablationsstudien für " + hypothesis.id,
        falsificationTest: "Ablation mit identischen Aufgaben und Seeds.",
        score: hypothesis.evidenceRefs.length > 1 ? .9 : .75
      }),
      compare: async ({ a, b }) => ({
        winner: a.critiques[0].score >= b.critiques[0].score ? "A" : "B",
        reason: "Höhere Evidenzabdeckung und bessere Testbarkeit."
      }),
      evolve: async ({ leaders }) => ({
        question: "Welche Kombination generalisiert am besten?",
        hypothesis: "Die stärksten Elemente der führenden Ansätze werden mit festen Holdouts und Ablationen kombiniert, um Generalisierung statt bloßer Selbstbewertung zu messen.",
        evidenceRefs: [...new Set(leaders.flatMap((x) => x.evidenceRefs))],
        uncertainty: .35,
        testQuestions: ["Vergleiche die kombinierte Architektur gegen beide Einzelkomponenten auf neuen Aufgaben."]
      })
    });
    const result = await tournament.run(researchFixture());
    assert.equal(result.skipped, false);
    assert.equal(result.generation, 1);
    assert.ok(result.hypotheses.length >= 3);
    assert.ok(result.leaders.length >= 1);
    assert.ok(result.researchQuestions.length >= 1);
    assert.ok(result.leaders.every((x) => x.authority === "NONE"));
    const status = tournament.status();
    assert.equal(status.tournaments, 1);
    assert.ok(status.leaderElo > 1000);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("daily tournament budget prevents runaway agent loops", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "hyp-budget-"));
  try {
    let now = 3000;
    const common = {
      statePath: path.join(dir, "hyp.json"),
      now: () => now++,
      dailyTournamentLimit: 1,
      generate: async () => [
        { question: "Q one long?", hypothesis: "A sufficiently long hypothesis with evidence and a falsifiable prediction for testing.", evidenceRefs: ["https://a.example"], uncertainty: .5, testQuestions: ["Test question one?"] },
        { question: "Q two long?", hypothesis: "Another sufficiently long hypothesis with evidence and a falsifiable prediction for testing.", evidenceRefs: ["https://b.example"], uncertainty: .5, testQuestions: ["Test question two?"] }
      ],
      reflect: async () => ({ validityRisks: "risk", missingEvidence: "missing", falsificationTest: "test", score: .7 }),
      compare: async () => ({ winner: "A", reason: "better grounded hypothesis" }),
      evolve: async ({ leaders }) => ({ question: "Q evolved long?", hypothesis: "An evolved sufficiently long hypothesis that remains falsifiable and evidence grounded.", evidenceRefs: leaders.flatMap((x) => x.evidenceRefs), uncertainty: .4, testQuestions: ["Test evolved hypothesis?"] })
    };
    const tournament = new HypothesisTournament(common);
    assert.equal((await tournament.run(researchFixture())).skipped, false);
    assert.equal((await tournament.run(researchFixture())).skipped, true);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
