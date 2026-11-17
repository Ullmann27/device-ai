// Genuinely trainable token-level neural sequence decoder conditioned on
// SWB1/SWB2 query and memory embeddings, with analytical cross-entropy gradients
// and hard logit masking for ownerRules constraints.
import { SubwordEncoder, sha256 } from './portable-subword.mjs';

const D = 16;
const TAU = 2.0;
const CLIP = 6.0;
const BOS = '<bos>';
const EOS = '<eos>';

const clipVal = x => Math.max(-CLIP, Math.min(CLIP, x));

export function tokenizeForDecoder(text) {
  return (String(text ?? '').toLowerCase().match(/[\p{L}\p{N}]+/gu) ?? []).slice(0, 64);
}

/** Research-only neural token decoder; not wired to production chat or tool execution. */
export class TrainableContextDecoder {
  constructor(encoder, vocabTokens = []) {
    if (!(encoder instanceof SubwordEncoder)) throw Error('Expected SubwordEncoder');
    this.encoder = encoder;
    this.wq = new Float64Array(D).fill(0.5);
    this.wm = new Float64Array(D).fill(0.8);
    this.wp = new Float64Array(D).fill(0.6);
    this.bh = new Float64Array(D).fill(0.0);
    this.wo = new Float64Array(D).fill(1.0);

    this.vocab = [];
    this.tokenIndex = new Map();
    this.tokenVecs = [];
    this.tokenBias = [];
    this.updates = 0;

    this.registerTokens([BOS, EOS, ...vocabTokens]);
  }

  registerTokens(tokens) {
    for (const raw of tokens) {
      const tok = String(raw).toLowerCase().trim();
      if (!tok || this.tokenIndex.has(tok)) continue;
      if(this.vocab.length>=256)throw Error('Decoder vocabulary limit exceeded');
      const idx = this.vocab.length;
      this.vocab.push(tok);
      this.tokenIndex.set(tok, idx);
      const vec = tok === BOS
        ? Array.from({ length: D }, (_, i) => (i % 2 === 0 ? 0.25 : -0.25))
        : tok === EOS
          ? Array.from({ length: D }, (_, i) => (i % 2 === 0 ? -0.25 : 0.25))
          : this.encoder.encode(tok);
      this.tokenVecs.push(Float64Array.from(vec));
      this.tokenBias.push(0.0);
    }
  }

  #stepForward(qVec, mVec, prevTokenIdx, forbiddenSet = null) {
    const pVec = this.tokenVecs[prevTokenIdx];
    const h = new Float64Array(D);
    for (let i = 0; i < D; i++) {
      const u = this.wq[i] * qVec[i] + this.wm[i] * mVec[i] + this.wp[i] * pVec[i] + this.bh[i];
      h[i] = Math.tanh(u);
    }

    const V = this.vocab.length;
    const logits = new Float64Array(V);
    let maxLogit = -Infinity;

    for (let v = 0; v < V; v++) {
      const tok = this.vocab[v];
      if (tok === BOS || (forbiddenSet && forbiddenSet.has(tok))) {
        logits[v] = -1e9;
        continue;
      }
      const e = this.tokenVecs[v];
      let dot = 0.0;
      for (let i = 0; i < D; i++) {
        dot += h[i] * this.wo[i] * e[i];
      }
      const l = TAU * dot + this.tokenBias[v];
      logits[v] = l;
      if (l > maxLogit) maxLogit = l;
    }

    const probs = new Float64Array(V);
    let sumExp = 0.0;
    for (let v = 0; v < V; v++) {
      if (logits[v] <= -1e8) {
        probs[v] = 0.0;
      } else {
        const ex = Math.exp(logits[v] - maxLogit);
        probs[v] = ex;
        sumExp += ex;
      }
    }
    const invSum = sumExp > 0 ? 1.0 / sumExp : 1.0;
    for (let v = 0; v < V; v++) probs[v] *= invSum;

    return { h, pVec, logits, probs };
  }

  /**
   * Computes average token cross-entropy loss on a target sequence without mutating weights.
   */
  sequenceLoss(query, memoryText, targetTokens) {
    const seq = [...targetTokens.map(t => t.toLowerCase()), EOS];
    this.registerTokens(seq);
    const qVec = this.encoder.encode(query);
    const mVec = this.encoder.encode(memoryText);
    let prevIdx = this.tokenIndex.get(BOS);
    let totalLoss = 0.0;

    for (const tok of seq) {
      const targetIdx = this.tokenIndex.get(tok);
      const { probs } = this.#stepForward(qVec, mVec, prevIdx);
      const p = Math.max(1e-12, probs[targetIdx]);
      totalLoss -= Math.log(p);
      prevIdx = targetIdx;
    }
    return totalLoss / seq.length;
  }

  /**
   * Performs one analytical gradient descent step over the target token sequence.
   */
  trainSequence(query, memoryText, targetTokens, rate = 0.15) {
    if (!Number.isFinite(rate) || rate <= 0 || rate > 0.5) throw Error('Invalid learning rate');
    const seq = [...targetTokens.map(t => t.toLowerCase()), EOS];
    this.registerTokens(seq);

    const qVec = this.encoder.encode(query);
    const mVec = this.encoder.encode(memoryText);
    const V = this.vocab.length;
    const T = seq.length;
    const invT = 1.0 / T;

    const gWq = new Float64Array(D);
    const gWm = new Float64Array(D);
    const gWp = new Float64Array(D);
    const gBh = new Float64Array(D);
    const gWo = new Float64Array(D);
    const gBias = new Float64Array(V);

    let prevIdx = this.tokenIndex.get(BOS);
    let totalLoss = 0.0;

    for (let t = 0; t < T; t++) {
      const targetIdx = this.tokenIndex.get(seq[t]);
      const { h, pVec, probs } = this.#stepForward(qVec, mVec, prevIdx);
      totalLoss -= Math.log(Math.max(1e-12, probs[targetIdx]));

      const weightedE = new Float64Array(D);
      for (let v = 0; v < V; v++) {
        const delta = (probs[v] - (v === targetIdx ? 1.0 : 0.0)) * invT;
        if (delta === 0.0) continue;
        gBias[v] += delta;
        const e = this.tokenVecs[v];
        for (let i = 0; i < D; i++) {
          weightedE[i] += delta * e[i];
        }
      }

      for (let i = 0; i < D; i++) {
        gWo[i] += TAU * h[i] * weightedE[i];
        const dLdh = TAU * this.wo[i] * weightedE[i];
        const dLdu = dLdh * (1.0 - h[i] * h[i]);
        gWq[i] += dLdu * qVec[i];
        gWm[i] += dLdu * mVec[i];
        gWp[i] += dLdu * pVec[i];
        gBh[i] += dLdu;
      }

      prevIdx = targetIdx;
    }

    for (let i = 0; i < D; i++) {
      this.wq[i] = clipVal(this.wq[i] - rate * gWq[i]);
      this.wm[i] = clipVal(this.wm[i] - rate * gWm[i]);
      this.wp[i] = clipVal(this.wp[i] - rate * gWp[i]);
      this.bh[i] = clipVal(this.bh[i] - rate * gBh[i]);
      this.wo[i] = clipVal(this.wo[i] - rate * gWo[i]);
    }
    for (let v = 0; v < V; v++) {
      this.tokenBias[v] = clipVal(this.tokenBias[v] - rate * gBias[v]);
    }
    this.updates++;
    return totalLoss * invT;
  }

  /**
   * Autoregressively decodes a token sequence conditioned on query & memory,
   * while strictly masking out any forbidden terms from ownerRules (logit = -1e9).
   */
  generate(query, memoryText, { maxTokens = 16, forbiddenTerms = [], ownerRules = [], groundedOnly = true } = {}) {
    if(!Number.isInteger(maxTokens)||maxTokens<1||maxTokens>64)throw Error('Decoder output limit exceeded');
    this.registerTokens(tokenizeForDecoder(memoryText));
    // Explicit machine-readable owner rules are enforceable; prose-only rules
    // must be compiled upstream. Never report compliance for unparsed mandates.
    if(!Array.isArray(ownerRules)||!Array.isArray(forbiddenTerms))throw Error('Invalid decoder constraints');
    const active=ownerRules.filter(rule=>rule?.active);
    if(active.some(rule=>!Array.isArray(rule.forbiddenTerms)))
      throw Error('Uncompiled owner rule: typed forbiddenTerms required');
    const forbiddenSet = new Set([...forbiddenTerms,...active.flatMap(rule=>rule.forbiddenTerms)]
      .map(t=>String(t).toLowerCase()));
    const allowed=new Set(tokenizeForDecoder(memoryText));
    if(groundedOnly)for(const tok of this.vocab){
      if(tok!==EOS&&!allowed.has(tok))forbiddenSet.add(tok);
    }
    // BOS is always unselectable in the probability computation.
    const qVec = this.encoder.encode(query);
    const mVec = this.encoder.encode(memoryText);
    const eosIdx = this.tokenIndex.get(EOS);

    let prevIdx = this.tokenIndex.get(BOS);
    const outTokens = [];
    const tokenProbabilities = [];

    for (let step = 0; step < maxTokens; step++) {
      const { probs } = this.#stepForward(qVec, mVec, prevIdx, forbiddenSet);
      let bestIdx = eosIdx;
      let bestProb = -1.0;

      for (let v = 0; v < probs.length; v++) {
        // Avoid immediate single-token stuttering
        if (v === prevIdx && v !== eosIdx) continue;
        if (probs[v] > bestProb) {
          bestProb = probs[v];
          bestIdx = v;
        }
      }
      if (bestIdx === eosIdx || bestProb <= 0) break;
      outTokens.push(this.vocab[bestIdx]);
      tokenProbabilities.push(Number(bestProb.toFixed(6)));
      prevIdx = bestIdx;
    }

    return {
      tokens: outTokens,
      text: outTokens.join(' '),
      tokenProbabilities,
      groundedOnly,
      verifiedNoForbiddenTokens: outTokens.every(tok=>!forbiddenSet.has(tok))
    };
  }
  /** Explicit experiment checkpoint. Embeddings are derived from the exact
   * supplied encoder snapshot; mismatched encoder snapshots are rejected. */
  save() {
    return {
      schema: 1,
      encoderSha256: sha256(this.encoder.save()),
      updates: this.updates,
      vocab: [...this.vocab],
      wq: Array.from(this.wq), wm: Array.from(this.wm),
      wp: Array.from(this.wp), bh: Array.from(this.bh),
      wo: Array.from(this.wo), tokenBias: [...this.tokenBias]
    };
  }
  static restore(encoder, snapshot) {
    if (!snapshot || snapshot.schema !== 1 ||
      snapshot.encoderSha256 !== sha256(encoder.save()) ||
      !Number.isInteger(snapshot.updates) || snapshot.updates < 0 || snapshot.updates > 1000000 ||
      !Array.isArray(snapshot.vocab) || snapshot.vocab.length < 2 || snapshot.vocab.length > 256 ||
      !snapshot.vocab.every(x => typeof x === 'string' && x.length <= 64)) throw Error('Invalid decoder snapshot');
    const m = new TrainableContextDecoder(encoder, snapshot.vocab);
    if (m.vocab.length !== snapshot.vocab.length ||
      m.vocab.some((v, i) => v !== snapshot.vocab[i])) throw Error('Decoder vocabulary mismatch');
    for (const key of ['wq','wm','wp','bh','wo']) {
      if (!Array.isArray(snapshot[key]) || snapshot[key].length !== D ||
          !snapshot[key].every(x => Number.isFinite(x) && Math.abs(x) <= CLIP)) throw Error('Invalid decoder weights');
      m[key].set(snapshot[key]);
    }
    if (!Array.isArray(snapshot.tokenBias) || snapshot.tokenBias.length !== m.vocab.length ||
        !snapshot.tokenBias.every(x => Number.isFinite(x) && Math.abs(x) <= CLIP)) throw Error('Invalid decoder token bias');
    m.tokenBias = snapshot.tokenBias.slice();
    m.updates = snapshot.updates;
    return m;
  }

}