import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import {
  ContinuumStateStore,
  createInitialState,
  makeObservation,
  mergeObservation,
  stateSummary,
  validateState
} from "../src/continuum-state.js";
import { AutonomousResearchPlanner, ContinuumResearchDaemon } from "../src/continuum-daemon.js";

test("state persists with integrity and last-known-good recovery", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "device-ai-continuum-"));
  try {
    const file = path.join(dir, "state.json");
    const store = new ContinuumStateStore(file);
    const first = store.save(createInitialState(1000), 1100);
    assert.equal(validateState(first), first);
    const second = store.save(first, 1200);
    const damaged = Buffer.from(fs.readFileSync(file));
    damaged[Math.floor(damaged.length / 2)] ^= 1;
    fs.writeFileSync(file, damaged);
    const recovered = store.load(1300);
    assert.equal(recovered.integrityHash, first.integrityHash);
    assert.notEqual(second.integrityHash, first.integrityHash);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("observation requires citations and creates research gaps without authority", () => {
  const state = createInitialState(1000);
  assert.throws(() => makeObservation({
    topicId: "topic-01", query: "AI", summary: "x".repeat(60), citations: []
  }));
  const observation = makeObservation({
    topicId: "topic-01",
    query: "KI Grundlagen",
    summary: "Eine vorsichtige, belegte Zusammenfassung mit ausreichend Inhalt für die persistente Beobachtung.",
    citations: [{ title: "Source", url: "https://example.org/paper" }],
    nextQuestions: ["Welche offenen Evaluationsprobleme bestehen derzeit?"],
    contradictions: "",
    uncertainty: 0.4,
    now: 1100
  });
  const next = mergeObservation(state, observation, 1200);
  assert.equal(next.observations.length, 1);
  assert.equal(next.gaps.length, 1);
  assert.equal(next.observations[0].trust, "WEB_OBSERVATION");
  assert.equal(next.observations[0].authority, "NONE");
});

test("planner prioritizes unresolved gap and changes query strategy after failures", () => {
  const state = createInitialState(1000);
  state.gaps.push({
    id: "gap-abc", question: "Welche Messmethode ist hier am robustesten?",
    sourceObservationId: "obs-1", createdAt: 1000, lastTriedAt: null, attempts: 0, resolved: false
  });
  const planner = new AutonomousResearchPlanner();
  const target = planner.choose(state, 2000);
  assert.equal(target.type, "gap");
  const first = planner.queryFor(target, state);
  state.cycles.push({
    id: "cycle-1", targetType: "gap", targetId: target.id, query: first.query,
    strategy: first.strategy, success: false, reason: "no sources", observationId: null,
    startedAt: 2000, completedAt: 2100
  });
  const second = planner.queryFor(target, state);
  assert.notEqual(first.strategy, second.strategy);
});

test("daemon learns cited observation, persists gaps and respects interval budget", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "device-ai-daemon-"));
  try {
    let now = 100000;
    const daemon = new ContinuumResearchDaemon({
      statePath: path.join(dir, "state.json"),
      now: () => now,
      minIntervalMs: 60000,
      dailyCycleLimit: 4,
      research: async ({ query }) => ({
        summary: "Die Recherche liefert eine vorsichtige Synthese aus mehreren überprüfbaren Quellen zu " + query + ".",
        citations: [
          { title: "A", url: "https://example.org/a" },
          { title: "B", url: "https://example.net/b" }
        ],
        nextQuestions: ["Welche unabhängigen Replikationen bestätigen das Ergebnis?"],
        contradictions: "Keine im begrenzten Suchlauf gefunden.",
        uncertainty: 0.35,
        topicHint: ""
      })
    });
    const result = await daemon.runOnce();
    assert.equal(result.success, true);
    assert.equal(result.observation.citations.length, 2);
    assert.equal(result.observation.authority, "NONE");
    assert.equal(daemon.status().observations, 1);
    const skipped = await daemon.runOnce();
    assert.equal(skipped.skipped, true);
    now += 60001;
    const second = await daemon.runOnce();
    assert.equal(second.success, true);
    assert.equal(stateSummary(daemon.store.load(now)).cycles, 2);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("failed research is recorded and alternative strategy becomes available", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "device-ai-daemon-fail-"));
  try {
    let now = 100000;
    const daemon = new ContinuumResearchDaemon({
      statePath: path.join(dir, "state.json"),
      now: () => now,
      minIntervalMs: 60000,
      research: async () => { throw new Error("no cited evidence"); }
    });
    const first = await daemon.runOnce();
    assert.equal(first.success, false);
    now += 60001;
    const second = await daemon.runOnce();
    assert.equal(second.success, false);
    const state = daemon.store.load(now);
    assert.equal(state.cycles.length, 2);
    assert.ok(state.cycles.every((x) => !x.success));
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
