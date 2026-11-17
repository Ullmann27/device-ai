import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const source = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8');
const activity = source('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const transfer = source('app/src/main/java/at/heinz/deviceai/learning/PersonalLearningTransfer.kt');

test('original ledger review reaches existing codec and never invents source identity', () => {
  assert.match(transfer, /FileLearningExperienceStore\.inspectSnapshot\(bytes\)/);
  assert.match(transfer, /PersonalLearningBackup\("unverified\.legacy"/);
  assert.match(activity, /PersonalLearningTransfer\.previewDocument\(bytes, store\)/);
  assert.match(transfer, /val canApplyAutomatically: Boolean get\(\) = false/);
});
test('SAF export requires byte-identical bounded readback before reporting success', () => {
  const part = activity.split('private fun personalLearningDocument(')[1].split('private fun showOneAppMigrationCenter')[0];
  const write = part.indexOf('it.write(bytes)');
  const readback = part.indexOf('contentResolver.openInputStream(uri)', write);
  const equal = part.indexOf('MessageDigest.isEqual(bytes, verified)', readback);
  const success = part.indexOf('Lernprotokoll gesichert', equal);
  assert.ok(write >= 0 && readback > write && equal > readback && success > equal);
  assert.doesNotMatch(part, /appendMissing|updateStatus|updateMatching|\.delete\(/);
});
test('personal review shows whole source counts separately from eligible fact proposals', () => {
  const part = activity.split('private fun personalLearningDocument(')[1].split('private fun showOneAppMigrationCenter')[0];
  assert.match(part, /plan\.backup\.records\.size/);
  assert.match(part, /LearningStatus\.REVOKED/);
  assert.match(part, /ByteBuffer\.wrap\(bytes, 0, 4\)\.int == 0x4c455832/);
  assert.match(part, /Identität.*nicht enthalten/);
});
