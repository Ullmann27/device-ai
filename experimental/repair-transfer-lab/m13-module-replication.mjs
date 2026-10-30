/**
 * M13 module-level replication audit (RESEARCH ONLY).
 * Candidate-level trials from one module share source, fixtures and defects; they cannot
 * be counted as independent Bernoulli observations for scientific confirmation.
 * This audit pairs each cell with other diagnoses of the same strategy *within* a module,
 * uses an exact one-sided sign test over independent module units, then applies Holm's
 * familywise multiplicity correction across all preregistered hypotheses.
 * Even a replicated candidate is never permission to train, merge or modify CCC1 ranking.
 */

const MAX_HYPOTHESES = 1000;
const MAX_ROWS = 100000;
const ID = /^[A-Za-z0-9][A-Za-z0-9._\/-]{0,160}$/;
const isFiniteInteger = (n, maximum) => Number.isSafeInteger(n) && n >= 0 && n <= maximum;

function validateRows(rows) {
  if (!Array.isArray(rows) || rows.length > MAX_ROWS) throw Error('M13_INVALID_ROWS');
  for (const r of rows) {
    if (!r || ![r.module, r.task, r.kind, r.strategy].every(x => typeof x === 'string' && ID.test(x)) ||
        typeof r.success !== 'boolean') throw Error('M13_INVALID_ROW');
  }
}

function signTail(positives, n) {
  if (!isFiniteInteger(positives, n) || !isFiniteInteger(n, MAX_ROWS)) throw Error('M13_INVALID_SIGN_DATA');
  if (n === 0) return 1;
  // Stable exact recurrence beginning with P(X=0) = 2^-n. Underflow for very
  // large n would incorrectly report p=0, so compute the upper tail in log space.
  let maximum = -Infinity;
  const terms = [];
  let logCombination = 0;
  for (let k = 0; k <= n; k++) {
    if (k > 0) logCombination += Math.log(n - k + 1) - Math.log(k);
    if (k >= positives) {
      const term = logCombination - n * Math.LN2;
      terms.push(term);
      if (term > maximum) maximum = term;
    }
  }
  const total = terms.reduce((s, v) => s + Math.exp(v - maximum), 0);
  return Math.min(1, Math.exp(maximum) * total);
}

function modulesByStrategy(rows) {
  const byModule = new Map();
  for (const r of rows) {
    const key = JSON.stringify([r.module, r.strategy]);
    let entry = byModule.get(key);
    if (!entry) {
      entry = {module:r.module, strategy:r.strategy, kinds:new Map()};
      byModule.set(key, entry);
    }
    let counts = entry.kinds.get(r.kind);
    if (!counts) {counts = {n:0, successes:0};entry.kinds.set(r.kind,counts)}
    counts.n++;
    if (r.success) counts.successes++;
  }
  return byModule;
}

/** Exact module-level audit: no file/network access, no authority, no model writes. */
export function auditM13ModuleReplication({hypotheses, trainRows, devRows,
  minimumIndependentModules = 6, alpha = 0.05} = {}) {
  if (!Array.isArray(hypotheses) || hypotheses.length > MAX_HYPOTHESES ||
      !isFiniteInteger(minimumIndependentModules, 100) || minimumIndependentModules < 6 ||
      !Number.isFinite(alpha) || alpha <= 0 || alpha > 0.05) throw Error('M13_INVALID_SETTINGS');
  validateRows(trainRows);
  validateRows(devRows);
  const seen = new Set();
  for (const h of hypotheses) {
    if (!h || !ID.test(h.id ?? '') || seen.has(h.id) || !ID.test(h.kind ?? '') ||
      !ID.test(h.strategy ?? '') || !['PREFER','DEMOTE'].includes(h.direction) ||
      !['CONFIRMED','REFUTED','INCONCLUSIVE'].includes(h.status)) throw Error('M13_INVALID_HYPOTHESIS');
    seen.add(h.id);
  }
  const trainModules = new Set(trainRows.map(x => x.module));
  if (devRows.some(x => trainModules.has(x.module))) throw Error('M13_TRAIN_DEV_MODULE_LEAK');
  const grouped = modulesByStrategy(devRows);
  const out = hypotheses.map(h => {
    if (h.status !== 'CONFIRMED') return {
      hypothesisId:h.id, status:'NOT_ORIGINALLY_CONFIRMED', modulesCompared:0,
      positiveModules:0, negativeModules:0, tiedModules:0,
      oneSidedP:null, holmAdjustedP:null, ownerReviewOnly:true
    };
    let compared=0, positive=0, negative=0, tied=0;
    for (const group of grouped.values()) {
      if (group.strategy !== h.strategy) continue;
      const target=group.kinds.get(h.kind);
      if (!target) continue;
      let otherN=0, otherSuccess=0;
      for (const [kind, summary] of group.kinds) {
        if (kind === h.kind) continue;
        otherN += summary.n;
        otherSuccess += summary.successes;
      }
      if (!otherN) continue;
      compared++;
      // Integer cross products avoid rounding ties due to floating-point fractions.
      const difference = target.successes * otherN - otherSuccess * target.n;
      if (difference === 0) tied++;
      else if ((h.direction === 'PREFER' && difference > 0) ||
               (h.direction === 'DEMOTE' && difference < 0)) positive++;
      else negative++;
    }
    const nonTies=positive+negative;
    return {hypothesisId:h.id,
      status:compared < minimumIndependentModules || nonTies < minimumIndependentModules
        ? 'INSUFFICIENT_INDEPENDENT_MODULES' : 'NOT_REPLICATED',
      modulesCompared:compared, positiveModules:positive,negativeModules:negative,tiedModules:tied,
      oneSidedP:nonTies >= minimumIndependentModules ? signTail(positive,nonTies) : null,
      holmAdjustedP:null,ownerReviewOnly:true};
  });
  // Include all preregistered hypotheses, including inconclusive/refuted ones, in the
  // multiplicity denominator. Otherwise a post hoc choice of successes inflates significance.
  const evaluated=out.filter(x => x.oneSidedP !== null)
    .sort((a,b) => a.oneSidedP-b.oneSidedP || a.hypothesisId.localeCompare(b.hypothesisId));
  let monotone=0;
  for (let rank=0; rank<evaluated.length; rank++) {
    const x=evaluated[rank];
    monotone=Math.max(monotone,Math.min(1,x.oneSidedP * (hypotheses.length-rank)));
    x.holmAdjustedP=monotone;
    x.status=monotone < alpha && x.positiveModules > x.negativeModules
      ? 'MODULE_REPLICATED_RESEARCH_ONLY' : 'NOT_REPLICATED';
  }
  return Object.freeze({
    analysis:'MODULE_PAIRED_SIGN_TEST_HOLM', independentModuleAssumptionNotVerified:true,
    trainModules:trainModules.size, devModules:new Set(devRows.map(x=>x.module)).size,
    hypothesisCount:hypotheses.length, moduleReplicated:out.filter(x=>x.status==='MODULE_REPLICATED_RESEARCH_ONLY').length,
    decisions:Object.freeze(out.map(x=>Object.freeze(x))),
    trainingAuthorized:false, productionActivationAuthorized:false,
    warning:'Statistical inference assumes independent sampled modules; code/tests may still share ancestry.'
  });
}
