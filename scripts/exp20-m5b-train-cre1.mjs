// EXP-20 M5b arm A: isolated CRE1 research training on the M5b TRAIN split only (never VAL/SEALED, never app assets).
// usage: node scripts/exp20-m5b-train-cre1.mjs <parity.cre1> <m5b-train.tsv> <out.cre1>
// Starts from the frozen parity checkpoint; hyperparameters are preregistered and fixed here.
import fs from 'node:fs';
import crypto from 'node:crypto';
import { RelationalEncoder, train } from '../.github/continuum/cre-encoder.mjs';

const [parity, trainTsv, out] = process.argv.slice(2);
if (!parity || !trainTsv || !out) { console.error('usage: <parity.cre1> <m5b-train.tsv> <out.cre1>'); process.exit(2); }
const rows = fs.readFileSync(trainTsv, 'utf8').trimEnd().split('\n').slice(1).map(l => l.split('\t'));
if (rows.some(r => r[1] !== 'TRAIN')) { console.error('refusing: input contains non-TRAIN rows'); process.exit(3); }
const memories = [], pairs = [];
for (const r of rows) {
  const [group, , , role, cand, text] = r;
  if (role === 'QUERY') continue;
  memories.push({ id: `${group}-${cand}`, text, subject: group, relation: 'm5b' });
  if (role === 'TRUE') pairs.push({ group, memory: `${group}-${cand}` });
}
const queries = new Map(rows.filter(r => r[3] === 'QUERY').map(r => [r[0], r[5]]));
const trainPairs = pairs.map(p => ({ query: queries.get(p.group), memory: p.memory }));
const model = RelationalEncoder.restore(fs.readFileSync(parity));
const HP = { epochs: 20, batch: 8, lr: 0.01, tau: 0.1, size: 8, hard: 7, sameRelation: 0, seed: 20261031 };
const result = train(model, trainPairs, memories, HP);
const bytes = model.save();
fs.writeFileSync(out, bytes);
const sha = crypto.createHash('sha256').update(bytes).digest('hex');
console.log(JSON.stringify({ pairs: trainPairs.length, memories: memories.length, hp: HP, result, steps: model.steps, sha256: sha }));
