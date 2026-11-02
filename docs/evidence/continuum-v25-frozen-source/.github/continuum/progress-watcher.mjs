import {neuralStatus} from "./neural-core.mjs";
// Deterministic, offline Continuum progress observations.
// Activity counters are not evidence of intelligence or model improvement.
const LIMIT = 64;
const TASKS = ["intent", "semanticSimilarity", "replayRetention"];

const count = value => Array.isArray(value) ? value.length : 0;
const ratio = (numerator, denominator) => denominator > 0 ? numerator / denominator : null;
const finiteTime = value => Number.isSafeInteger(value) && value >= 0;

export function captureProgressSample(state, at = Date.now()) {
  const beliefs = Array.isArray(state?.baby?.beliefs) ? state.baby.beliefs : [];
  const quarantined = count(state?.baby?.quarantinedBeliefs);
  const explicit = beliefs.filter(b => b?.evidenceMode === "EXPLICIT_CITATION" &&
    Array.isArray(b.citationUrls) && b.citationUrls.some(validPublicCitation)).length;
  const grounded = beliefs.filter(b => b?.status === "GROUNDED_OBSERVATION").length;
  const cycles = Array.isArray(state?.cycles) ? state.cycles : [];
  const successful = cycles.filter(c => c?.success === true).length;
  const sample = {
    version: 1,
    at: finiteTime(at) ? at : 0,
    generation: Math.max(0, Math.trunc(Number(state?.generation) || 0)),
    research: {cycles: cycles.length, successful, failed: cycles.length-successful},
    memory: {
      active: beliefs.length,
      grounded,
      explicitlyCited: explicit,
      quarantined,
      citationCoverage: ratio(explicit, beliefs.length)
    },
    benchmark: state?.neural ? neuralStatus(state.neural) : "NOT_EVALUATED"
  };
  return sample;
}

function validPublicCitation(value) {
  try {
    const url = new URL(String(value || ""));
    return ["http:", "https:"].includes(url.protocol) && !url.username && !url.password;
  } catch { return false; }
}

function fingerprint(sample) {
  // Generation and timestamp are deliberately excluded: status requests are not learning.
  return JSON.stringify([sample.research, sample.memory, sample.benchmark]);
}

export function appendProgressSample(history, sample) {
  const previous = Array.isArray(history) ? history.filter(x => x?.version === 1).slice(-LIMIT) : [];
  if (!sample || sample.version !== 1 || !sample.memory || !sample.research) return previous;
  if (previous.length && fingerprint(previous[previous.length-1]) === fingerprint(sample)) return previous;
  return [...previous, structuredClone(sample)].slice(-LIMIT);
}

export function progressTrend(history) {
  const rows = Array.isArray(history) ? history.filter(x => x?.version === 1) : [];
  if (rows.length < 2) return {status:"BASELINE_ONLY", change:null, message:"Noch keine vergleichbare zweite Messung."};
  const a=rows[rows.length-2], b=rows[rows.length-1];
  // Coverage is a narrow data-quality proxy, not a measure of model accuracy.
  if (a.memory?.active < 5 || b.memory?.active < 5 ||
      !Number.isFinite(a.memory?.citationCoverage) ||
      !Number.isFinite(b.memory?.citationCoverage)) {
    return {status:"INSUFFICIENT_DATA",change:null,message:"Zu wenige aktive Überzeugungen für einen stabilen Quotenvergleich."};
  }
  const change=b.memory.citationCoverage-a.memory.citationCoverage;
  const status=change >= .05 ? "CITATION_COVERAGE_INCREASED" :
    change <= -.05 ? "CITATION_COVERAGE_DECREASED" : "NO_MATERIAL_COVERAGE_CHANGE";
  return {status,change,previous:a.memory.citationCoverage,current:b.memory.citationCoverage,
    message:"Nur Änderung der expliziten Zitationsabdeckung; kein Lern- oder Intelligenznachweis."};
}

export function formatProgressReport(history, current) {
  const rows=appendProgressSample(history,current);
  const trend=progressTrend(rows);
  const m=current.memory, r=current.research;
  const percent=m.citationCoverage === null ? "keine Daten" : (m.citationCoverage*100).toFixed(1)+"%";
  return [
    "## Continuum · Fortschrittswächter",
    "",
    "**Forschungsaktivität:** "+r.cycles+" Zyklen · "+r.successful+" erfolgreich · "+r.failed+" fehlgeschlagen",
    "**Wissensqualität:** "+m.active+" aktive Überzeugungen · "+m.explicitlyCited+" mit expliziter Quellenreferenz · "+m.quarantined+" in Quarantäne",
    "**Zitationsabdeckung:** "+percent,
    "**Trend:** "+trend.status+(trend.change==null?"":" · "+(trend.change*100).toFixed(1)+" Prozentpunkte"),
    current.benchmark === "NOT_EVALUATED"
      ? "**Neuronale Testleistung:** NICHT GEMESSEN (kein lokaler Held-out-Benchmark an diese Console angeschlossen)"
      : "**Neuronaler Audit (separat, klein, selbst verfasst):** Intent "+current.benchmark.audit.intent.correct+"/"+current.benchmark.audit.intent.total+
        " · Semantik "+current.benchmark.audit.semantic.correct+"/"+current.benchmark.audit.semantic.total+
        " · Gewichtsupdates "+current.benchmark.updates+" · übernommene Runden "+current.benchmark.accepted+"/"+current.benchmark.rounds,

    "",
    "_Mehr Recherche, mehr Speicher und höhere interne Rewards sind keine Belege für bessere Modellleistung. "+
    "Eine Zitation ist nicht automatisch eine geprüfte wahre Aussage. "+
    "Modellfreigaben verwenden getrennte Validierungs- und Replay-Prüfungen; der Audit steuert keine Freigabe._"
  ].join("\n");
}

// Pure decision policy for a future on-device evaluation harness. The caller must
// independently authenticate and reproduce these measurements. This function
// neither trains a model nor writes weights, state, files or permissions.
export function assessLearningCandidate(baseline, candidate, {
  minimumGain = .01, maximumTaskRegression = .03
} = {}) {
  const invalid = reason => ({decision:"INVALID_EVIDENCE",reason,taskDeltas:{}});
  if (!Number.isFinite(minimumGain) || minimumGain < 0 || minimumGain > .2 ||
      !Number.isFinite(maximumTaskRegression) || maximumTaskRegression < 0 || maximumTaskRegression > .2) {
    return invalid("INVALID_THRESHOLDS");
  }
  const validHash=x=>typeof x==="string" && /^[a-f0-9]{64}$/.test(x);
  const validRun=x=>typeof x==="string" && /^[A-Za-z0-9._:-]{8,128}$/.test(x);
  for(const evidence of [baseline,candidate]) {
    if (!evidence || typeof evidence !== "object" ||
        !validHash(evidence.modelHash) || !validHash(evidence.testSetHash) ||
        !validRun(evidence.runId) || typeof evidence.suiteId!=="string" ||
        !/^[A-Za-z0-9._:-]{4,128}$/.test(evidence.suiteId) ||
        evidence.testSetUsedInTraining !== false ||
        !evidence.tasks || typeof evidence.tasks!=="object") {
      return invalid("MISSING_HOLDOUT_PROVENANCE");
    }
    for(const task of TASKS) {
      const metric=evidence.tasks[task];
      if(!metric || !Number.isSafeInteger(metric.attempted) || metric.attempted < 20 ||
        !Number.isSafeInteger(metric.correct) || metric.correct<0 ||
        metric.correct>metric.attempted) {
        return invalid("INVALID_TASK_METRICS");
      }
    }
  }
  if (baseline.suiteId!==candidate.suiteId || baseline.testSetHash!==candidate.testSetHash ||
      baseline.modelHash===candidate.modelHash || baseline.runId===candidate.runId) {
    return invalid("INCOMPARABLE_SNAPSHOTS");
  }
  const deltas={};
  for(const task of TASKS) {
    const oldMetric=baseline.tasks[task],newMetric=candidate.tasks[task];
    if(oldMetric.attempted!==newMetric.attempted)
      return invalid("INCOMPARABLE_TEST_COUNTS");
    deltas[task]=newMetric.correct/newMetric.attempted-oldMetric.correct/oldMetric.attempted;
  }
  if(Object.values(deltas).some(delta=>delta< -maximumTaskRegression-1e-10))
    return {decision:"REJECT_REGRESSION",reason:"TASK_RETENTION_REGRESSION",taskDeltas:deltas};
  if(!Object.values(deltas).some(delta=>delta>=minimumGain-1e-10))
    return {decision:"NO_DEMONSTRATED_GAIN",reason:"IMPROVEMENT_BELOW_THRESHOLD",taskDeltas:deltas};
  return {decision:"ELIGIBLE_FOR_INDEPENDENT_REVIEW",reason:"POLICY_THRESHOLDS_MET",
    taskDeltas:deltas};
}

