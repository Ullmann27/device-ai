// Reproducible masked-view retrieval over the existing portable SWB1 encoder.
// Kotlin counterpart: ContinuumContrastiveViews.kt. No external model or network.
import {SubwordEncoder} from './portable-subword.mjs';

class Rng {
  constructor(seed) { this.state = Number(BigInt(seed) & 0xffffffffn) >>> 0; }
  next() {
    this.state = (Math.imul(this.state, 1664525) + 1013904223) >>> 0;
    return this.state / 4294967296;
  }
}
function words(text) {
  if (typeof text !== 'string' || text.length < 1 || text.length > 2048)
    throw Error('Invalid memory');
  const normalized = text.toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim().slice(0,512);
  if (!normalized) throw Error('Empty memory');
  return normalized.split(' ').filter(Boolean);
}
function validate(memories) {
  if (!Array.isArray(memories) || memories.length < 2 || memories.length > 24)
    throw Error('Expected 2..24 memories');
  const normalized = memories.map(m => words(m).join(' '));
  if (new Set(normalized).size !== memories.length) throw Error('Duplicate memories');
}
function view(tokens,rng,drop) {
  const kept = tokens.filter(() => rng.next() >= drop);
  return (kept.length ? kept : [tokens[Math.min(tokens.length-1,Math.floor(rng.next()*tokens.length))]]).join(' ');
}
export function evaluateViews(model,memories,{seed=17,drop=0.65}={}) {
  if (!(model instanceof SubwordEncoder)) throw Error('Expected SWB1 encoder');
  validate(memories);
  if (!Number.isFinite(drop) || drop < 0 || drop > 0.95) throw Error('Invalid dropout');
  const rng = new Rng(seed);
  let top1 = 0, reciprocalRank = 0;
  for (let i=0;i<memories.length;i++) {
    const query = view(words(memories[i]),rng,drop);
    const scores = memories.map((m,j) => ({j,score:model.similarity(query,m)}));
    scores.sort((a,b) => b.score-a.score || a.j-b.j);
    const rank = scores.findIndex(x => x.j===i)+1;
    top1 += Number(rank===1);
    reciprocalRank += 1/rank;
  }
  return {reciprocalRank:reciprocalRank/memories.length,top1,total:memories.length};
}
/**
 * Positive: independently masked view and its original.
 * Negative: the same view and a different memory.
 * Does not mutate baseline or promote model weights. The caller must apply
 * task and frozen-holdout acceptance rules before persisting the candidate.
 */
export function trainContrastiveCandidate(
  baseline, memories, validation,
  {steps=240,seed=43,drop=0.65,rate=0.10,cancelled=()=>false}={}
) {
  if (!(baseline instanceof SubwordEncoder)) throw Error('Expected SWB1 encoder');
  validate(memories);validate(validation);
  if (!Number.isInteger(steps) || steps < 1 || steps > 500 ||
      !Number.isFinite(drop) || drop < 0 || drop > 0.95 ||
      !Number.isFinite(rate) || rate < 0.0001 || rate > 0.12 ||
      typeof cancelled !== 'function') throw Error('Invalid training limits');
  if (memories.some(m => validation.includes(m))) throw Error('Train/validation overlap');
  const candidate = SubwordEncoder.restore(baseline.save());
  const trainingBefore = evaluateViews(candidate,memories,{seed:17,drop});
  const validationBefore = evaluateViews(candidate,validation,{seed:29,drop});
  const tokenized = memories.map(words), rng = new Rng(seed);
  let completed = 0;
  for (;completed < steps && !cancelled();completed++) {
    const i = Math.min(memories.length-1,Math.floor(rng.next()*memories.length));
    const j = (i+1+Math.floor(rng.next()*(memories.length-1)))%memories.length;
    const noisy = view(tokenized[i],rng,drop);
    const pairs = [
      {left:noisy,right:memories[i],similar:true},
      {left:noisy,right:memories[j],similar:false}
    ];
    for (let k=0;k<2;k++) {
      if (cancelled()) return {
        candidate,steps:completed,interrupted:true,
        trainingBefore,trainingAfter:evaluateViews(candidate,memories,{seed:17,drop}),
        validationBefore,validationAfter:evaluateViews(candidate,validation,{seed:29,drop})
      };
      // Kotlin trainSemantic selects (updates+step)%2.
      const pair = pairs[candidate.updates%2];
      candidate.updateSemantic(pair,rate);
    }
  }
  return {
    candidate,steps:completed,interrupted:completed < steps,
    trainingBefore,trainingAfter:evaluateViews(candidate,memories,{seed:17,drop}),
    validationBefore,validationAfter:evaluateViews(candidate,validation,{seed:29,drop})
  };
}
