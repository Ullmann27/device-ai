// Sealed audit of CRE1 against the production retriever and SWB1/SWB2 (same
// gate, same abstention rule, same training pairs). The configuration below was
// chosen on DEV only (grid over epochs, learning rate, same-relation negatives;
// see docs/CONTINUUM-CIR-RESEARCH.md); TEST is measured with it unchanged.
import fs from 'node:fs';
import { buildData, fingerprint, files } from './relational-memory-data.mjs';
import { RelationalEncoder, train, tokens, FUNCTION_WORDS } from './cre-encoder.mjs';
import { rankMemories, wordEvidence, EVIDENCE_THRESHOLD } from './dialogue-retrieval.mjs';
import { SubwordEncoder } from './portable-subword.mjs';
import { Lcg } from './relational-memory-data.mjs';

export const FROZEN = Object.freeze({ epochs: 15, lr: 0.005, tau: 0.1, batch: 8, size: 12, hard: 4, sameRelation: 0, delta: 0.1 });
export const SEEDS = Object.freeze([1, 2, 3, 4, 5]);

const contentTokens = t => tokens(t).filter(w => w.length >= 3 && !FUNCTION_WORDS.has(w));

/** Actual words (not hashed bucket IDs) present in training.
 * A hash collision must never designate an unseen person's name as known.
 * This conservatively protects the existing abstention rule.
 */
export function trainedVocabulary(pairs, memories) {
  const set = new Set();
  for (const t of [...pairs.flatMap(p => tokens(p.query)), ...memories.flatMap(m => tokens(m.text))]) set.add(t);
  return set;
}

/**
 * The hybrid decision used in the app: memories without word evidence never
 * qualify (production gate); among qualified memories the neural score ranks;
 * the answer is withheld when a memory outside the gate matches the question
 * clearly better (delta) or when an untrained question word that the store
 * knows is missing from the chosen memory (a different person or thing).
 */
export function decide(score, query, memories, { delta = FROZEN.delta, trained = null } = {}) {
  const scored = memories.map((m, i) => ({ i, n: score(query, m.text), ev: wordEvidence(query, m.text) }));
  let best = -Infinity; for (const s of scored) best = Math.max(best, s.n);
  const gated = scored.filter(s => s.ev >= EVIDENCE_THRESHOLD).sort((a, b) => b.n - a.n || a.i - b.i);
  if (!gated.length) return { answer: null, reason: 'no-word-evidence' };
  if (gated[0].n < best - delta) return { answer: null, reason: 'better-match-outside-evidence' };
  if (trained) {
    const chosen = new Set(tokens(memories[gated[0].i].text));
    const store = new Set(memories.flatMap(m => tokens(m.text)));
    const missing = contentTokens(query).filter(t => !trained.has(t) && store.has(t) && !chosen.has(t));
    if (missing.length) return { answer: null, reason: 'unmatched-name:' + missing.join(',') };
  }
  return { answer: memories[gated[0].i], score: gated[0].n };
}

function evaluate(answerOf, split) {
  const out = { 'seen-form': { n: 0, correct: 0 }, 'heldout-form': { n: 0, correct: 0 }, unanswerable: { n: 0, answered: 0 } };
  for (const q of split.questions) { const r = answerOf(q.text); out[q.form].n++; if (r?.id === q.target) out[q.form].correct++; }
  for (const q of split.unanswerable) { out.unanswerable.n++; if (answerOf(q.text)) out.unanswerable.answered++; }
  if (!out['heldout-form'].n) delete out['heldout-form'];
  return out;
}

const lexicalAnswer = memories => q => rankMemories(q, memories, { limit: 1 })[0] ?? null;

function swbTrained(kind, seed, pairs, memories) {
  const model = kind === 'SWB2' ? SubwordEncoder.createExperimentalSwb2(seed) : new SubwordEncoder(seed);
  const byId = new Map(memories.map(m => [m.id, m.text])), ids = [...byId.keys()], rng = new Lcg(seed * 13 + 5);
  for (let e = 0; e < FROZEN.epochs; e++) for (const i of rng.shuffle(pairs.map((_, k) => k))) {
    const p = pairs[i];
    let neg = ids[rng.int(ids.length)]; if (neg === p.memory) neg = ids[(ids.indexOf(neg) + 1) % ids.length];
    model.updateSemantic({ left: p.query, right: byId.get(p.memory), similar: true }, 0.1);
    model.updateSemantic({ left: p.query, right: byId.get(neg), similar: false }, 0.1);
  }
  return model;
}

export function audit({ seeds = SEEDS } = {}) {
  const data = buildData();
  const { curriculum } = files();
  const pairs = curriculum.pairs, trainMemories = curriculum.memories;
  const trained = trainedVocabulary(pairs, trainMemories);
  const rows = [];
  const lexical = { dev: evaluate(lexicalAnswer(data.dev.memories), data.dev), test: evaluate(lexicalAnswer(data.test.memories), data.test) };
  for (const seed of seeds) {
    const cre = new RelationalEncoder(seed);
    const run = train(cre, pairs, trainMemories, { ...FROZEN, seed: seed * 7 + 1 });
    const creScore = (q, m) => cre.score(q, m);
    const row = { seed, loss: Number(run.loss.toFixed(6)), updates: run.updates };
    for (const split of ['dev', 'test']) {
      const mem = data[split].memories;
      row[split] = {
        CRE1: evaluate(q => decide(creScore, q, mem, { trained }).answer, data[split]),
        CRE1_without_name_rule: evaluate(q => decide(creScore, q, mem).answer, data[split]),
        CRE1_neural_only: evaluate(q => { let b = null, s = -Infinity; for (const m of mem) { const v = cre.score(q, m.text); if (v > s) { s = v; b = m; } } return b; }, data[split])
      };
      for (const kind of ['SWB1', 'SWB2']) {
        const model = swbTrained(kind, seed, pairs, trainMemories);
        row[split][kind] = evaluate(q => decide((a, b) => model.similarity(a, b), q, mem, { trained }).answer, data[split]);
      }
    }
    rows.push(row);
  }
  // Retention (continual learning): learn DEV pairs on top of the TRAIN model, with and
  // without replay of TRAIN pairs, then measure TRAIN-domain retrieval again.
  const retention = seeds.map(seed => {
    const base = new RelationalEncoder(seed);
    train(base, pairs, trainMemories, { ...FROZEN, seed: seed * 7 + 1 });
    const trainSplit = { questions: data.train.questions.map(q => ({ ...q, form: 'seen-form' })), unanswerable: data.train.unanswerable };
    const before = evaluate(q => decide((a, b) => base.score(a, b), q, data.train.memories, { trained }).answer, trainSplit);
    const devPairs = data.dev.questions.map(q => ({ query: q.text, memory: q.target }));
    const allMemories = [...trainMemories, ...data.dev.memories.map(({ id, text }) => ({ id, text }))];
    const result = {};
    for (const replay of [false, true]) {
      const model = RelationalEncoder.restore(base.save());
      const stream = replay ? [...devPairs, ...new Lcg(seed).shuffle(pairs).slice(0, devPairs.length)] : devPairs;
      train(model, stream, allMemories, { ...FROZEN, epochs: 5, seed: seed * 11 + 3 });
      result[replay ? 'withReplay' : 'withoutReplay'] = evaluate(q => decide((a, b) => model.score(a, b), q, data.train.memories, { trained }).answer, trainSplit);
    }
    return { seed, before, ...result };
  });
  const total = r => (r['seen-form']?.correct ?? 0) + (r['heldout-form']?.correct ?? 0);
  const perSeed = rows.map(r => ({ seed: r.seed, creCorrect: total(r.test.CRE1), creFalse: r.test.CRE1.unanswerable.answered,
    lexicalCorrect: total(lexical.test), lexicalFalse: lexical.test.unanswerable.answered }));
  const retained = retention.every(r => r.withReplay['seen-form'].correct >= r.before['seen-form'].correct * 0.95);
  const gate = {
    moreCorrectThanProductionEverySeed: perSeed.every(s => s.creCorrect > s.lexicalCorrect),
    noMoreFalseAnswersEverySeed: perSeed.every(s => s.creFalse <= s.lexicalFalse),
    retainsTrainedDomainWithReplay: retained
  };
  return { schema: 1, model: 'CRE1', frozen: FROZEN, seeds, testFingerprint: fingerprint(data.test),
    devFingerprint: fingerprint(data.dev), lexical, rows, retention, perSeed, gate,
    promotionEligible: Object.values(gate).every(Boolean),
    note: 'Synthetic, single-author data. TEST was not used for any selection. Not a claim of general language understanding.' };
}

if (process.argv[1]?.endsWith('relational-retrieval-audit.mjs')) {
  const result = audit();
  const mean = k => (result.rows.reduce((s, r) => s + k(r), 0) / result.rows.length).toFixed(2);
  const fmt = r => `seen ${r['seen-form'].correct}/${r['seen-form'].n}` + (r['heldout-form'] ? ` heldout ${r['heldout-form'].correct}/${r['heldout-form'].n}` : '') + ` falseAnswers ${r.unanswerable.answered}/${r.unanswerable.n}`;
  console.log('LEXICAL (production) TEST', fmt(result.lexical.test), '| DEV', fmt(result.lexical.dev));
  for (const r of result.rows) for (const k of ['CRE1', 'CRE1_without_name_rule', 'CRE1_neural_only', 'SWB1', 'SWB2']) console.log('seed', r.seed, k.padEnd(22), 'TEST', fmt(r.test[k]), '| DEV', fmt(r.dev[k]));
  for (const r of result.retention) console.log('retention seed', r.seed, 'before', r.before['seen-form'].correct, 'without replay', r.withoutReplay['seen-form'].correct, 'with replay', r.withReplay['seen-form'].correct, '/', r.before['seen-form'].n);
  console.log('gate', JSON.stringify(result.gate), 'promotionEligible', result.promotionEligible);
  if (process.argv[2]) fs.writeFileSync(process.argv[2], JSON.stringify(result, null, 1) + '\n');
}
