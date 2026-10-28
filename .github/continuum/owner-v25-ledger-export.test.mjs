import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const source = fs.readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt', 'utf8');
const transfer = fs.readFileSync('app/src/main/java/at/heinz/deviceai/learning/PersonalLearningTransfer.kt', 'utf8');
const store = fs.readFileSync('app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningExperience.kt', 'utf8');

function section(from, until) {
  const begin = source.indexOf(from);
  assert.ok(begin >= 0, 'missing start ' + from);
  const end = source.indexOf(until, begin + from.length);
  assert.ok(end > begin, 'missing end ' + until);
  return source.slice(begin, end);
}

test('owner v25 requires explicit file picker and never uploads the ledger', () => {
  const confirm = section('private fun confirmPersonalLedgerExport()', 'private fun writePersonalLedger(');
  assert.match(confirm, /setPositiveButton\("DATEI SICHERN"\)/);
  assert.match(confirm, /Intent\.ACTION_CREATE_DOCUMENT/);
  assert.match(confirm, /PERSONAL_LEDGER_EXPORT_REQUEST/);
  assert.doesNotMatch(confirm, /delete\(|clear\(|upload\(|enqueue\(/);
  assert.match(source, /PERSONAL_LEDGER_EXPORT_REQUEST -> \{/);
});

test('full current ledger export includes revocation, is read back, and does not mutate source', () => {
  const exportCode = section('private fun writePersonalLedger(', 'private fun exportCoreWeights(');
  assert.match(exportCode, /PersonalLearningTransfer\.export\(/);
  assert.match(exportCode, /PersonalLearningTransfer\.inspect\(bytes\)/);
  assert.match(exportCode, /openOutputStream\(uri, "wt"\)/);
  assert.match(exportCode, /openInputStream\(uri\)/);
  assert.match(exportCode, /MessageDigest\.isEqual\(bytes, actual\)/);
  assert.doesNotMatch(exportCode, /\.append\(|saveModel\(|\.updateStatus\(|\.delete\(/);
  assert.match(transfer, /item\.status\.name/);
  assert.match(transfer, /LearningStatus\.valueOf/);
  assert.match(transfer, /val canApplyAutomatically: Boolean get\(\) = false/);
  assert.match(store, /const val MAX_RECORDS=256/);
});

test('one unique request code and no personal content in diagnostics', () => {
  const requests = [...source.matchAll(/const\s+val\s+(\w+_REQUEST)\s*=\s*(\d+)/g)]
    .map(m => ({ name: m[1], code: Number(m[2]) }));
  assert.ok(requests.length >= 12, 'request-code inventory incomplete');
  assert.equal(new Set(requests.map(r => r.name)).size, requests.length);
  assert.equal(new Set(requests.map(r => r.code)).size, requests.length);
  const resultHandler = section('override fun onActivityResult(', 'private fun exportBacklogDocument(');
  assert.match(resultHandler, /PERSONAL_LEDGER_EXPORT_REQUEST\s*->\s*\{/);
  const exportCode = section('private fun writePersonalLedger(', 'private fun exportCoreWeights(');
  assert.doesNotMatch(exportCode, /\.content\b|\.annotation\b|\.sourceRef\b/);
  assert.match(exportCode, /Lernprotokoll-Sicherung nicht abschließend bestätigt/);
});
