import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const activity = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'), 'utf8');
const learning = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt'), 'utf8');
const archive = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/learning/ContinuumKnowledgeArchive.kt'), 'utf8');

function readout() {
  const start = activity.indexOf('private fun continuumDashboardText(): String');
  const end = activity.indexOf('private fun showContinuumLearningDashboardDialog(', start);
  assert.ok(start >= 0 && end > start, 'live learning dashboard must exist');
  return activity.slice(start, end);
}

test('installed application ID and sandbox boundaries are explicitly visible', () => {
  const report = readout();
  assert.match(report, /BuildConfig\.APPLICATION_ID/);
  assert.match(report, /getrennte private Daten/);
  assert.match(report, /nicht automatisch/);
});
test('source archive, experience ledger, and encoder updates stay distinct', () => {
  const report = readout();
  assert.match(report, /core\.modelUpdates/);
  assert.match(report, /SWB1-Subword-Encoder/);
  assert.match(report, /keine Gemma-4-Gewichtsupdates/);
  assert.match(report, /ContinuumKnowledgeArchive\(filesDir\)\.stats\(\)\.values\.sum\(\)/);
  assert.match(report, /core\.experiences\.total/);
  assert.match(learning, /private val modelFile=File\(directory,"subword-v1\.bin"\)/);
  assert.match(learning, /private val store=FileLearningExperienceStore\(directory\)/);
  assert.match(archive, /class ContinuumKnowledgeArchive/);
});
test('public research permission, scheduler and counters are not confused with learning', () => {
  const report = readout();
  for (const signal of ['publicStudy.enabled', 'publicRegistered',
    'getPendingJob(', 'getLong("attempts", 0L)',
    'getLong("sources", 0L)', 'getLong("accepted", 0L)']) {
    assert.ok(report.includes(signal), 'missing: '+signal);
  }
});
test('readout does not import, train, revoke or overwrite an old application', () => {
  const report = readout();
  assert.doesNotMatch(report, /\.importSnapshot\(|\.trainExperimental|\.learnFromSources\(|\.revokeOwnerFact\(|\.delete\(|\.rollbackTo\(/);
  assert.match(report, /runCatching \{\s*at\.heinz\.deviceai\.learning\.ContinuumKnowledgeArchive/);
  assert.match(report, /getOrNull\(\)/);
});
test('owner learning summary does not claim Gemma weight training', () => {
  const start = activity.indexOf('private fun learningCenterStatus(): String');
  const end = activity.indexOf('private fun showDevelopmentCenter()', start);
  assert.ok(start>=0 && end>start);
  assert.match(activity.slice(start,end), /SWB1-Encoder-Updates \(nicht Gemma 4\)/);
});
