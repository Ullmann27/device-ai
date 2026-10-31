#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const FROZEN_BLOB = '622370c291ead9ab11ec8917c992037a2fcf9e17';
export const LABELS = Object.freeze(['SUPERSEDES_STATE', 'CORRECTS_EVENT', 'COEXISTS']);
const CORRECTION_CUE = /(?:korrektur|korrigier|falsch|berichtigung|entschuldigung|nicht [^.!?]* sondern)/iu;
const UPDATE_CUE = /(?:jetzt|inzwischen|mittlerweile|seit|ab sofort)/iu;

export function keywordOnlyPrediction(after) {
  if (typeof after !== 'string') throw Error('INVALID_AFTER_TEXT');
  if (CORRECTION_CUE.test(after)) return 'CORRECTS_EVENT';
  if (UPDATE_CUE.test(after)) return 'SUPERSEDES_STATE';
  return 'COEXISTS';
}

export function auditShortcut(rows) {
  if (!Array.isArray(rows) || rows.length !== 240) throw Error('FROZEN_CASE_COUNT_INVALID');
  const confusion = Object.fromEntries(LABELS.map(expected => [
    expected, Object.fromEntries(LABELS.map(predicted => [predicted, 0]))
  ]));
  const labelCounts = Object.fromEntries(LABELS.map(label => [label, 0]));
  let correct = 0;
  const mismatches = [];
  rows.forEach((row, i) => {
    const id = 'M6X-' + String(i + 1).padStart(3, '0');
    if (row?.id !== id || !LABELS.includes(row.expected)) throw Error('INVALID_GOLD_CASE_' + id);
    const predicted = keywordOnlyPrediction(row.after); // no before or unrelated evidence
    confusion[row.expected][predicted] += 1;
    labelCounts[row.expected] += 1;
    if (row.expected === predicted) correct += 1;
    else mismatches.push(id);
  });
  if (!LABELS.every(label => labelCounts[label] === 80)) throw Error('FROZEN_LABEL_IMBALANCE');
  return {
    test: 'RETROSPECTIVE_AFTER_TEXT_KEYWORD_SHORTCUT_ONLY',
    cases: rows.length,
    correct, accuracy: correct / rows.length,
    confusion, mismatches,
    generalization_proven: false,
    warning: correct === 240
      ? 'PERFECT_LEXICAL_SHORTCUT: Existing frozen 240-case holdout is insufficient by itself to demonstrate semantic generalization.'
      : 'LEXICAL_SHORTCUT_BASELINE_RECORDED: Evaluate EWM on independent adversarial cue-balanced data.'
  };
}

export function auditFrozenFile(file) {
  const bytes = readFileSync(file);
  const hash = createHash('sha1').update('blob ' + bytes.length + '\0').update(bytes).digest('hex');
  if (hash !== FROZEN_BLOB) throw Error('FROZEN_HOLDOUT_HASH_MISMATCH');
  const lines = bytes.toString('utf8').trimEnd().split('\n');
  const rows = lines.map((line, i) => {
    try { return JSON.parse(line); }
    catch { throw Error('INVALID_HOLDOUT_JSON_LINE_' + (i + 1)); }
  });
  return { ...auditShortcut(rows), frozen_git_blob: hash };
}

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isCli) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const file = resolve(root, 'docs/research/m6-independent/ewm-v2-foreign-240.jsonl');
  try {
    const audit = auditFrozenFile(file);
    process.stdout.write(JSON.stringify(audit, null, 2) + '\n');
    if (audit.correct === 240) process.exitCode = 1; // integrity warning, not model failure
  } catch (error) {
    process.stderr.write('M6_SHORTCUT_AUDIT_REJECTED: ' + error.message + '\n');
    process.exitCode = 2;
  }
}
