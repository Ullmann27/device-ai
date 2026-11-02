#!/usr/bin/env node
/** Strict scoring of an immutable, previously frozen CDX2 corpus against independently produced predictions. */
import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';

const MODES = Object.freeze(['RAW', 'VERIFIED', 'RULE']);
const FIELDS = Object.freeze(['text', 'sourceId', 'revision', 'sha256', 'start', 'endExclusive']);
const digest = text => crypto.createHash('sha256').update(text, 'utf8').digest('hex');
const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const isInt = value => Number.isSafeInteger(value);

function fail(message) { throw new Error(message); }
function sourceTupleIsValid(candidate, sources) {
  if (!isObject(candidate) || candidate.decision !== 'SPAN' ||
      FIELDS.some(key => !Object.hasOwn(candidate, key))) return false;
  if (typeof candidate.text !== 'string' || typeof candidate.sourceId !== 'string' ||
      typeof candidate.sha256 !== 'string' || !isInt(candidate.revision) ||
      !isInt(candidate.start) || !isInt(candidate.endExclusive) ||
      candidate.start < 0 || candidate.endExclusive <= candidate.start) return false;
  const source = sources.find(s => s.sourceId === candidate.sourceId &&
      s.revision === candidate.revision && s.sha256 === candidate.sha256);
  return Boolean(source && digest(source.text) === source.sha256 &&
      candidate.endExclusive <= source.text.length &&
      source.text.slice(candidate.start, candidate.endExclusive) === candidate.text);
}
function matches(candidate, expected) {
  if (expected === null) return candidate === null;
  return isObject(candidate) && FIELDS.every(k => candidate[k] === expected[k]);
}
function normalize(prediction) {
  if (prediction === null || isObject(prediction) && prediction.decision === 'NOANSWER' &&
      Object.keys(prediction).length === 1) return null;
  if (isObject(prediction) && prediction.decision === 'SPAN') return prediction;
  return undefined;
}
function wilson95(success, total) {
  if (!total) return null;
  const z = 1.95996398454, p = success / total;
  const den = 1 + z * z / total;
  const center = (p + z * z / (2 * total)) / den;
  const radius = z * Math.sqrt(p * (1 - p) / total + z * z / (4 * total * total)) / den;
  return [Math.max(0, center - radius), Math.min(1, center + radius)];
}
export function score(corpus, predictions) {
  if (!isObject(corpus) || !Array.isArray(corpus.cases) ||
      !isObject(predictions) || !Array.isArray(predictions.cases)) fail('Invalid corpus or prediction schema');
  const corpusIds = corpus.cases.map(c => c.id);
  const predictIds = predictions.cases.map(c => c.id);
  if (!corpusIds.length || new Set(corpusIds).size !== corpusIds.length ||
      new Set(predictIds).size !== predictIds.length ||
      corpusIds.length !== predictIds.length ||
      corpusIds.some(id => !predictIds.includes(id))) fail('Missing, duplicate or unexpected prediction case id');
  const rows = new Map(predictions.cases.map(c => [c.id, c]));
  const result = { schema: 'CDX2_BLIND_SCORE_V1', cases: corpusIds.length, splits: {}, records: [] };
  for (const c of corpus.cases) {
    if (!Array.isArray(c.sources) || !['PRIMARY', 'ADVERSARIAL'].includes(c.split) ||
        !(c.expected === null || isObject(c.expected))) fail('Invalid frozen case ' + c.id);
    if (c.sources.some(s => !isObject(s) || typeof s.text !== 'string' ||
        digest(s.text) !== s.sha256)) fail('Frozen source integrity mismatch: ' + c.id);
    if (c.expected !== null && !sourceTupleIsValid({ decision: 'SPAN', ...c.expected }, c.sources))
      fail('Invalid frozen expected-source tuple: ' + c.id);
    const actual = rows.get(c.id), split = result.splits[c.split] ||= {};
    const one = { id: c.id, split: c.split, expected: c.expected };
    for (const mode of MODES) {
      const counts = split[mode] ||= { supported: 0, exactSupported: 0, missing: 0,
        correctAbstentions: 0, unsupportedPositive: 0, falseSourceAssignments: 0,
        falseAbstentions: 0, malformed: 0 };
      const candidate = normalize(actual[mode]);
      if (c.expected === null) counts.missing++; else counts.supported++;
      if (candidate === undefined) counts.malformed++;
      else if (candidate === null) {
        if (c.expected === null) counts.correctAbstentions++;
        else counts.falseAbstentions++;
      } else {
        const validSource = sourceTupleIsValid(candidate, c.sources);
        if (!validSource || c.expected !== null &&
            (candidate.sourceId !== c.expected.sourceId || candidate.revision !== c.expected.revision ||
             candidate.sha256 !== c.expected.sha256)) counts.falseSourceAssignments++;
        if (!matches(candidate, c.expected)) counts.unsupportedPositive++;
        else if (c.expected !== null) counts.exactSupported++;
      }
      one[mode] = { prediction: candidate === undefined ? 'MALFORMED' : candidate,
        exact: candidate !== undefined && matches(candidate, c.expected) };
    }
    result.records.push(one);
  }
  for (const split of Object.values(result.splits)) for (const mode of MODES) {
    const m = split[mode];
    m.supportedWilson95 = wilson95(m.exactSupported, m.supported);
    m.abstainWilson95 = wilson95(m.correctAbstentions, m.missing);
    m.primaryGate = m.supported === 20 && m.missing === 5 &&
      m.exactSupported >= 16 && m.correctAbstentions === 5 &&
      m.falseSourceAssignments === 0 && m.malformed === 0;
  }
  return result;
}
export function cli(args) {
  if (args.length !== 4) fail('Usage: node cdx2-blind-score.mjs FROZEN_CORPUS.json PREDICTIONS.json OUTPUT.json EXPECTED_CORPUS_SHA256');
  const [corpusPath, predictionPath, outputPath, expectedSha] = args;
  if (!/^[a-f0-9]{64}$/.test(expectedSha)) fail('Expected immutable corpus SHA-256 is required');
  const corpusBytes = fs.readFileSync(corpusPath);
  if (digest(corpusBytes) !== expectedSha) fail('Frozen corpus SHA-256 changed');
  if (path.resolve(outputPath) === path.resolve(predictionPath) ||
      path.resolve(outputPath) === path.resolve(corpusPath) || fs.existsSync(outputPath))
    fail('Output must not overwrite inputs or an existing score');
  const report = score(JSON.parse(corpusBytes), JSON.parse(fs.readFileSync(predictionPath, 'utf8')));
  report.corpusSha256 = expectedSha;
  report.predictionsSha256 = digest(fs.readFileSync(predictionPath));
  report.promotion = 'FORBIDDEN_RESEARCH_ONLY';
  fs.writeFileSync(outputPath, JSON.stringify(report, null, 2) + '\n', { flag: 'wx', mode: 0o600 });
  process.stdout.write('CDX2_BUNDLED_SCORER_COMPLETE cases=' + report.cases +
      ' primary=' + JSON.stringify(report.splits.PRIMARY) + '\n');
}
if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(new URL(import.meta.url).pathname)) {
  try { cli(process.argv.slice(2)); } catch (error) { console.error('CDX2_SCORE_FAILED: ' + error.message); process.exitCode = 1; }
}
