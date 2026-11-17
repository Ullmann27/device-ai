import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const learning = 'app/src/main/java/at/heinz/deviceai/learning/';
const audit = read(learning + 'ContinuumIntelligenceAudit.kt');
const device = read(learning + 'ContinuumIntelligenceOnDevice.kt');
const core = read(learning + 'ContinuumLearningCore.kt');
const monitor = read('app/src/main/java/at/heinz/deviceai/ContinuumTrainingMonitorActivity.kt');

test('frozen CDX1 probe set includes exactly 20 evidence-supported and five missing-fact questions', () => {
  assert.equal((audit.match(/Probe\("(?:city|color|energy|tool)-\d\d"/g) || []).length, 20);
  assert.equal((audit.match(/Probe\("missing-\d\d"/g) || []).length, 5);
  assert.match(audit, /fun fixtureSha256\(/);
  assert.match(audit, /require\(probes.map \{ it.id \}.distinct\(\).size == probes.size\)/);
});

test('answer scoring requires literal evidence rather than loss improvement or training counters', () => {
  assert.match(audit, /correct = p.expected != null && actual == normalized\(p.expected\)/);
  assert.match(audit, /unsupportedClaim = p.expected == null && actual.isNotEmpty\(\) && !emptyOrAbstaining/);
  assert.match(audit, /noResponse = p.expected == null && actual.isEmpty\(\)/);
  assert.match(audit, /validAbstention = p.expected == null && emptyOrAbstaining/);
  assert.match(audit, /validAbstentions == 5 && noResponses == 0/);
  assert.doesNotMatch(audit, /""\s*,\s*"unbekannt"/);
});

test('existing core lock protects the full read-only inference audit', () => {
  assert.match(core, /fun intelligenceAudit\([\s\S]*?synchronized\(CORE_LOCK\) \{\s*ContinuumIntelligenceOnDevice.run\(this, cancelled\)/);
  assert.match(device, /core.pairedDecoderStatus\(\)/);
  assert.match(device, /core.experimentalPairedDecoderContinuation\(probe.evidence, probe.question, 48\)/);
  assert.match(device, /after.sha256 != before.sha256 \|\| after.updates != before.updates/);
  assert.match(device, /"BLOCKED_MODEL_CHANGED"/);
  assert.doesNotMatch(device, /trainExperimental|saveTo\(|commit\(|importSnapshot\(|rollbackTo\(/);
});

test('Android monitor audit is opt-in and off-main-thread, never marked as trained', () => {
  assert.match(monitor, /setOnClickListener \{ startIntelligenceAudit\(\) \}/);
  assert.match(monitor, /Thread\(\{/);
  assert.match(monitor, /ContinuumLearningCore\(appContext.filesDir\)\.intelligenceAudit/);
  assert.match(monitor, /intelligenceCancelled.set\(true\)/);
  assert.match(monitor, /intelligenceBusy.compareAndSet\(false, true\)/);
  assert.doesNotMatch(monitor, /trainExperimental.*Intelligence|intelligenceAudit\(\).*train/);
});

test('research audit never invents an offline result from a missing model', () => {
  assert.match(device, /"UNAVAILABLE_NO_CHECKPOINT"/);
  assert.match(device, /"UNAVAILABLE_CORRUPT"/);
  assert.match(device, /"UNAVAILABLE_NO_INFERENCE"/);
  assert.match(device, /"INTERRUPTED"/);
  assert.match(audit, /status == "MEASURED"/);
  assert.match(audit, /rows.take\(3\)/);
});
