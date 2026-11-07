import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';

export const M3_FIXTURE_SHA256 = 'c12eb68895fa2171e2258e73784dcb7ee5fc3c5053c96f0c9e57da16a9ad5b41';
const hash = value => createHash('sha256').update(value, 'utf8').digest('hex');
const MAX_BYTES = 2_000_000;
function bounded(text) {
  if (typeof text !== 'string' || Buffer.byteLength(text, 'utf8') > MAX_BYTES || text.includes('\0'))
    throw new Error('Invalid or oversized input');
  return text;
}
function frozenCases(fixture) {
  if (hash(bounded(fixture)) !== M3_FIXTURE_SHA256) throw new Error('Frozen fixture digest mismatch');
  return new Map(fixture.split('\n').filter(x => x.trim() && !x.startsWith('#')).map(line => {
    const [id, category, , expected] = line.split('\t');
    return [id, {category, expected}];
  }));
}
function parseReport(text, cases) {
  const lines = bounded(text).split(/\r?\n/);
  if (lines.shift() !== 'id\tcategory\tpass\texpected\tactual') throw new Error('Invalid report header');
  const rows = new Map();
  for (const line of lines) {
    // Existing evaluator summaries are comments, never acceptance evidence.
    if (!line.trim() || line.startsWith('#')) continue;
    const parts = line.split('\t');
    if (parts.length !== 5) throw new Error('Invalid report columns');
    const [id, category, pass, expected, actual] = parts;
    const reference = cases.get(id);
    if (!reference) throw new Error('Report contains unknown case: ' + id);
    if (rows.has(id)) throw new Error('Report contains duplicate case: ' + id);
    if (category !== reference.category || expected !== reference.expected)
      throw new Error('Report metadata differs from frozen fixture: ' + id);
    if (!['1', '0', '0!'].includes(pass)) throw new Error('Invalid report status: ' + id);
    rows.set(id, {id, category, correct: pass === '1', harmful: pass === '0!', actual});
  }
  if (rows.size !== cases.size) throw new Error('Incomplete report coverage');
  return rows;
}
const counts = rows => ({total: rows.length, correct: rows.filter(x => x.correct).length,
  wrong: rows.filter(x => !x.correct).length, harmful: rows.filter(x => x.harmful).length});
const delta = (before, after) => ({baseline: counts(before), candidate: counts(after),
  deltaCorrect: counts(after).correct - counts(before).correct});

/** Compares EXISTING scorer output. Does not judge answers, authenticate execution,
 * establish grader independence, or convert a development set into a blind holdout. */
export function compareOfflineReports({fixture, baseline, candidate}) {
  const cases = frozenCases(fixture);
  const before = parseReport(baseline, cases), after = parseReport(candidate, cases);
  const regressions = [], improvements = [];
  for (const [id, row] of before) {
    if (row.correct && !after.get(id).correct) regressions.push(id);
    if (!row.correct && after.get(id).correct) improvements.push(id);
  }
  const categories = {};
  for (const category of new Set([...cases.values()].map(x => x.category))) {
    categories[category] = delta([...before.values()].filter(x => x.category === category),
      [...after.values()].filter(x => x.category === category));
  }
  const total = delta([...before.values()], [...after.values()]);
  return {schemaVersion: 1, benchmark: 'continuum-m3-de-v1',
    status: total.candidate.harmful ? 'UNSAFE' : regressions.length ? 'REGRESSION' : 'NO_REGRESSION_REPORTED',
    evidenceLevel: 'HOST_DETERMINISTIC_REPORTS_ONLY', blindHoldout: false, deviceVerified: false,
    limitation: 'Submitted scorer output only; requires separate SHA-bound execution logs and independent review. No Gemma or Fold7 performance claim.',
    digests: {fixture: hash(fixture), baseline: hash(baseline), candidate: hash(candidate)},
    total, categories, regressions, improvements};
}
function readBounded(file) {
  const stat = fs.statSync(file);
  if (!stat.isFile() || stat.size > MAX_BYTES) throw new Error('Invalid or oversized input file');
  return fs.readFileSync(file, 'utf8');
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [baselineFile, candidateFile, ...extra] = process.argv.slice(2);
    if (!baselineFile || !candidateFile || extra.length)
      throw new Error('Usage: node scripts/continuum-offline-eval-compare.mjs BASELINE.tsv CANDIDATE.tsv');
    const result = compareOfflineReports({
      fixture: readBounded(new URL('../app/src/test/resources/continuum-eval/m3-de.tsv', import.meta.url)),
      baseline: readBounded(baselineFile), candidate: readBounded(candidateFile)});
    process.stdout.write(JSON.stringify(result, null, 2) + '\n');
    process.exitCode = result.status === 'NO_REGRESSION_REPORTED' ? 0 : 1;
  } catch (error) {
    process.stderr.write('OFFLINE_EVAL_REJECTED: ' + error.message + '\n');
    process.exitCode = 2;
  }
}
