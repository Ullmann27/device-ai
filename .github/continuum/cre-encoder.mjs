// CRE1: Continuum relational retrieval encoder (experimental until its audit gate passes).
//
// Why SWB1/SWB2 could not learn paraphrase retrieval (see docs/CONTINUUM-CIR-RESEARCH.md):
// every character 2-4-gram of a whole sentence is hashed into 256 buckets with up
// to 128 active at once, so unrelated sentences share many buckets and their
// mean vectors converge (collapse at the input); sigmoid(2.5*dot) of small tanh
// vectors starts at 0.5 with tiny gradients (SGD barely moves), while Adam pushes
// the unnormalised vectors into saturation (0.9-1.0 even for unrelated text); and
// each positive pair met one random negative.
//
// CRE1 instead:
//   - hashes whole words and the 3-5-grams of each word separately into 16384
//     buckets (few collisions), function words weighted 0.3;
//   - mean-pools the bucket embeddings, applies a trainable projection head
//     (one for questions, one for stored memories) and L2-normalises;
//   - is trained with InfoNCE (temperature tau) against the positive memory,
//     the other memories of the same person/thing (hard negatives: same name,
//     different relation) and random memories, with lazy Adam whose bias
//     corrections are products (no Math.pow), so Kotlin can reproduce it.
// Kotlin twin: app/src/main/java/at/heinz/deviceai/learning/RelationalRetrievalEncoder.kt
import crypto from 'node:crypto';
import { Lcg } from './relational-memory-data.mjs';

export const D = 32, V = 16384, MAGIC = 0x43524531; // "CRE1"
export const FUNCTION_WORDS = new Set(['der', 'die', 'das', 'den', 'dem', 'des', 'ein', 'eine', 'einer', 'eines', 'einem', 'einen',
  'und', 'oder', 'ich', 'du', 'er', 'sie', 'es', 'wir', 'ihr', 'mein', 'meine', 'meinen', 'meinem', 'meiner', 'sein', 'seine',
  'ist', 'sind', 'hat', 'haben', 'in', 'im', 'am', 'an', 'zu', 'zum', 'zur', 'von', 'vom', 'mit', 'fuer', 'auf', 'bei', 'als',
  'auch', 'noch', 'sehr', 'kann', 'soll', 'was', 'wie', 'wo', 'wer', 'wann', 'welche', 'welcher', 'welches', 'welchen']);

export function tokens(text) {
  if (typeof text !== 'string' || text.length < 1 || text.length > 2048) throw Error('Invalid text');
  return text.toLowerCase().replaceAll('ä', 'ae').replaceAll('ö', 'oe').replaceAll('ü', 'ue').replaceAll('ß', 'ss')
    .split(/[^\p{L}\p{N}]+/u).filter(Boolean).slice(0, 96);
}

export function bucket(feature) {
  let h = 0x811c9dc5;
  for (const byte of Buffer.from(feature, 'utf8')) h = Math.imul(h ^ byte, 0x01000193) >>> 0;
  return h % V;
}

/** Sparse features: [{id, w}] sorted by id, weights sum to 1. */
export function features(text) {
  const acc = new Map();
  const add = (id, w) => acc.set(id, (acc.get(id) ?? 0) + w);
  for (const t of tokens(text)) {
    const tw = FUNCTION_WORDS.has(t) ? 0.3 : 1;
    add(bucket('w:' + t), tw);
    if (/^\p{N}+$/u.test(t) || t.length < 3) continue;
    const p = '<' + t + '>', grams = [];
    for (let n = 3; n <= 5; n++) for (let i = 0; i + n <= p.length; i++) grams.push(p.slice(i, i + n));
    for (const g of grams) add(bucket('g:' + g), tw / grams.length);
  }
  if (!acc.size) throw Error('No features');
  let total = 0; for (const w of acc.values()) total += w;
  return [...acc.entries()].sort((a, b) => a[0] - b[0]).map(([id, w]) => ({ id, w: w / total }));
}

export class RelationalEncoder {
  constructor(seed = 7) {
    const rng = new Lcg(seed);
    this.seed = seed >>> 0; this.steps = 0;
    this.E = new Float64Array(V * D);
    for (let i = 0; i < this.E.length; i++) this.E[i] = (rng.next() * 2 - 1) * 0.1;
    this.Pq = new Float64Array(D * D); this.Pm = new Float64Array(D * D);
    for (const P of [this.Pq, this.Pm]) for (let r = 0; r < D; r++) for (let c = 0; c < D; c++) P[r * D + c] = (r === c ? 1 : 0) + (rng.next() * 2 - 1) * 0.01;
    this.adam = null;
  }
  pool(f) { const h = new Float64Array(D); for (const { id, w } of f) { const o = id * D; for (let i = 0; i < D; i++) h[i] += w * this.E[o + i]; } return h; }
  project(P, h) { const z = new Float64Array(D); for (let r = 0; r < D; r++) { let s = 0; for (let c = 0; c < D; c++) s += P[r * D + c] * h[c]; z[r] = s; } return z; }
  static normalise(z) { let n = 0; for (const x of z) n += x * x; n = Math.sqrt(n); if (!(n > 0) || !Number.isFinite(n)) throw Error('Degenerate CRE1 embedding'); const e = new Float64Array(D); for (let i = 0; i < D; i++) e[i] = z[i] / n; return { e, n }; }
  forward(text, side) { const f = features(text), h = this.pool(f), z = this.project(side === 'q' ? this.Pq : this.Pm, h); const { e, n } = RelationalEncoder.normalise(z); return { f, h, z, e, n }; }
  embed(text, side = 'm') { return this.forward(text, side).e; }
  score(query, memory) { const a = this.embed(query, 'q'), b = this.embed(memory, 'm'); let s = 0; for (let i = 0; i < D; i++) s += a[i] * b[i]; return s; }

  /** InfoNCE loss and gradients for one query against candidates[0] (positive) and the rest. */
  lossAndGrad(query, candidates, tau, grads) {
    const q = this.forward(query, 'q'), ms = candidates.map(t => this.forward(t, 'm'));
    const logits = ms.map(m => { let s = 0; for (let i = 0; i < D; i++) s += q.e[i] * m.e[i]; return s / tau; });
    let max = -Infinity; for (const l of logits) max = Math.max(max, l);
    let sum = 0; const ex = logits.map(l => { const v = Math.exp(l - max); sum += v; return v; });
    const loss = Math.log(sum) + max - logits[0];
    if (!grads) return loss;
    const dEq = new Float64Array(D);
    ms.forEach((m, j) => {
      const g = ex[j] / sum - (j === 0 ? 1 : 0);
      if (g === 0) return;
      const dEm = new Float64Array(D);
      for (let i = 0; i < D; i++) { dEq[i] += g * m.e[i] / tau; dEm[i] = g * q.e[i] / tau; }
      this.backward(m, dEm, this.Pm, grads.Pm, grads.E);
    });
    this.backward(q, dEq, this.Pq, grads.Pq, grads.E);
    return loss;
  }
  backward(fw, dE, P, dP, dEmb) {
    let dot = 0; for (let i = 0; i < D; i++) dot += dE[i] * fw.e[i];
    const dz = new Float64Array(D); for (let i = 0; i < D; i++) dz[i] = (dE[i] - dot * fw.e[i]) / fw.n;
    const dh = new Float64Array(D);
    for (let r = 0; r < D; r++) { const g = dz[r]; if (g === 0) continue; for (let c = 0; c < D; c++) { dP[r * D + c] += g * fw.h[c]; dh[c] += P[r * D + c] * g; } }
    for (const { id, w } of fw.f) { let row = dEmb.get(id); if (!row) dEmb.set(id, row = new Float64Array(D)); for (let i = 0; i < D; i++) row[i] += w * dh[i]; }
  }

  /** Lazy Adam: embedding rows are only updated when they have a gradient. */
  apply(grads, lr, b1 = 0.9, b2 = 0.999, eps = 1e-8) {
    if (!this.adam) this.adam = { t: 0, b1t: 1, b2t: 1, mE: new Map(), vE: new Map(),
      mPq: new Float64Array(D * D), vPq: new Float64Array(D * D), mPm: new Float64Array(D * D), vPm: new Float64Array(D * D) };
    const a = this.adam; a.t++; a.b1t *= b1; a.b2t *= b2;
    const step = (w, o, g, m, v, k) => { m[k] = b1 * m[k] + (1 - b1) * g; v[k] = b2 * v[k] + (1 - b2) * g * g;
      w[o] -= lr * (m[k] / (1 - a.b1t)) / (Math.sqrt(v[k] / (1 - a.b2t)) + eps); };
    for (const id of [...grads.E.keys()].sort((x, y) => x - y)) {
      const g = grads.E.get(id); let m = a.mE.get(id), v = a.vE.get(id);
      if (!m) { a.mE.set(id, m = new Float64Array(D)); a.vE.set(id, v = new Float64Array(D)); }
      for (let i = 0; i < D; i++) step(this.E, id * D + i, g[i], m, v, i);
    }
    for (let k = 0; k < D * D; k++) { step(this.Pq, k, grads.Pq[k], a.mPq, a.vPq, k); step(this.Pm, k, grads.Pm[k], a.mPm, a.vPm, k); }
    this.steps++;
  }
  static emptyGrads() { return { E: new Map(), Pq: new Float64Array(D * D), Pm: new Float64Array(D * D) }; }

  save() {
    const body = Buffer.alloc(4 + 4 + 4 + 4 + (V * D + 2 * D * D) * 8); let o = 0;
    body.writeInt32BE(MAGIC, o); o += 4; body.writeUInt32BE(this.seed, o); o += 4; body.writeInt32BE(this.steps, o); o += 4; body.writeInt32BE(D, o); o += 4;
    for (const arr of [this.E, this.Pq, this.Pm]) for (const x of arr) { if (!Number.isFinite(x)) throw Error('Non-finite weight'); body.writeDoubleBE(x, o); o += 8; }
    return Buffer.concat([body, crypto.createHash('sha256').update(body).digest()]);
  }
  static restore(bytes) {
    const data = Buffer.from(bytes), body = data.subarray(0, -32);
    if (data.length !== 16 + (V * D + 2 * D * D) * 8 + 32) throw Error('Invalid CRE1 size');
    if (!crypto.timingSafeEqual(crypto.createHash('sha256').update(body).digest(), data.subarray(-32))) throw Error('CRE1 checksum mismatch');
    let o = 0; if (body.readInt32BE(o) !== MAGIC) throw Error('Not a CRE1 model'); o += 4;
    const m = new RelationalEncoder(0); m.seed = body.readUInt32BE(o); o += 4; m.steps = body.readInt32BE(o); if(m.steps<0||m.steps>1000000)throw Error('Invalid CRE1 training steps'); o += 4;
    if (body.readInt32BE(o) !== D) throw Error('Invalid CRE1 dimension'); o += 4;
    for (const arr of [m.E, m.Pq, m.Pm]) for (let i = 0; i < arr.length; i++) { const value = body.readDoubleBE(o); if(!Number.isFinite(value))throw Error('Non-finite CRE1 checkpoint parameter'); arr[i]=value; o += 8; }
    return m;
  }
}

export const sha256 = bytes => crypto.createHash('sha256').update(bytes).digest('hex');

/**
 * Candidates for one training pair: the positive memory first, then up to
 * `hard` other memories of the same subject (same name, other relation) and
 * random memories up to `size`. Deterministic for a given rng.
 */
export function candidatesFor(pair, memories, bySubject, rng, { size = 12, hard = 4, sameRelation = 0, byRelation = null } = {}) {
  const positive = memories.get(pair.memory);
  const out = [positive.text], seen = new Set([pair.memory]);
  for (const id of rng.shuffle(bySubject.get(positive.subject) ?? [])) {
    if (out.length > hard) break;
    if (!seen.has(id)) { seen.add(id); out.push(memories.get(id).text); }
  }
  // same relation, another subject: only the name tells them apart
  let added = 0;
  for (const id of rng.shuffle(byRelation?.get(positive.relation) ?? [])) {
    if (added >= sameRelation) break;
    if (!seen.has(id)) { seen.add(id); out.push(memories.get(id).text); added++; }
  }
  const ids = [...memories.keys()];
  for (let guard = 0; out.length < size && guard < size * 8; guard++) {
    const id = ids[rng.int(ids.length)];
    if (!seen.has(id)) { seen.add(id); out.push(memories.get(id).text); }
  }
  return out;
}

/** subject of a memory id "split-subject-relation" (curriculum memories carry no subject field). */
export const subjectOf = id => id.split('-').slice(1, -1).join('-');
export const relationOf = id => id.split('-').at(-1);

export function train(model, pairs, memoryList, { epochs = 30, batch = 8, lr = 0.01, tau = 0.1, size = 12, hard = 4, sameRelation = 0, seed = 99, cancelled = () => false } = {}) {
  const memories = new Map(memoryList.map(m => [m.id, { ...m, subject: m.subject ?? subjectOf(m.id), relation: m.relation ?? relationOf(m.id) }]));
  const bySubject = new Map(), byRelation = new Map();
  for (const m of memories.values()) {
    if (!bySubject.has(m.subject)) bySubject.set(m.subject, []); bySubject.get(m.subject).push(m.id);
    if (!byRelation.has(m.relation)) byRelation.set(m.relation, []); byRelation.get(m.relation).push(m.id);
  }
  const rng = new Lcg(seed);
  let last = 0, updates = 0;
  for (let e = 0; e < epochs; e++) {
    const order = rng.shuffle(pairs.map((_, i) => i));
    let total = 0;
    for (let b = 0; b < order.length; b += batch) {
      if (cancelled()) return { epochs: e, updates, interrupted: true, loss: last };
      const grads = RelationalEncoder.emptyGrads();
      const slice = order.slice(b, b + batch);
      for (const i of slice) total += model.lossAndGrad(pairs[i].query, candidatesFor(pairs[i], memories, bySubject, rng, { size, hard, sameRelation, byRelation }), tau, grads);
      const scale = 1 / slice.length;
      for (const row of grads.E.values()) for (let i = 0; i < D; i++) row[i] *= scale;
      for (let k = 0; k < D * D; k++) { grads.Pq[k] *= scale; grads.Pm[k] *= scale; }
      model.apply(grads, lr); updates++;
    }
    last = total / pairs.length;
  }
  return { epochs, updates, interrupted: false, loss: last };
}
