#!/usr/bin/env node
import fs from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const file = resolve(root, 'docs/research/m6-independent/ewm-v2-foreign-240.jsonl');
const expectedGitBlob = '622370c291ead9ab11ec8917c992037a2fcf9e17';
const bytes = fs.readFileSync(file);
const actualGitBlob = createHash('sha1')
  .update(Buffer.from('blob ' + bytes.length + '\0')).update(bytes).digest('hex');
if (actualGitBlob !== expectedGitBlob) {
  throw Error('M6_HOLDOUT_IMMUTABILITY_VIOLATION:' + actualGitBlob);
}
const lines = bytes.toString('utf8').trimEnd().split('\n');
if (lines.length !== 240) throw Error('M6_EXPECTED_240_GOT_' + lines.length);
const counts = {SUPERSEDES_STATE: 0, CORRECTS_EVENT: 0, COEXISTS: 0};
const ids = new Set();
const pairs = new Set();
for (let i = 0; i < lines.length; i++) {
  const row = JSON.parse(lines[i]);
  if (Object.keys(row).sort().join(',') !== 'after,before,expected,group,id,unrelated') {
    throw Error('M6_SCHEMA_INVALID_ROW_' + (i + 1));
  }
  const id = 'M6X-' + String(i + 1).padStart(3,'0');
  if (row.id !== id || ids.has(row.id)) throw Error('M6_ID_INVALID_' + id);
  ids.add(id);
  if (!Object.hasOwn(counts, row.expected)) throw Error('M6_UNKNOWN_LABEL_' + id);
  counts[row.expected]++;
  for (const key of ['before','after','unrelated','group']) {
    if (typeof row[key] !== 'string' || row[key].trim().length < 5 || row[key].length > 360) {
      throw Error('M6_INVALID_' + key + '_' + id);
    }
  }
  if (row.before === row.after || pairs.has(row.before + '\0' + row.after)) {
    throw Error('M6_DUPLICATE_PAIR_' + id);
  }
  pairs.add(row.before + '\0' + row.after);
}
for (const [label, count] of Object.entries(counts)) {
  if (count !== 80) throw Error('M6_UNBALANCED_' + label + '_' + count);
}
process.stdout.write(JSON.stringify({
  status: 'SCHEMA_AND_IMMUTABILITY_ONLY_NOT_MODEL_RESULT',
  gitBlob: actualGitBlob, cases: lines.length, counts
}) + '\n');
