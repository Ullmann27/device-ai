import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const code = (p) => fs.readFileSync(path.join(root, p), 'utf8');
const policy = code('app/src/main/java/at/heinz/deviceai/cognitive/InternetConsentPolicy.kt');
const labels = code('app/src/main/java/at/heinz/deviceai/cognitive/InternetAccessModeLabels.kt');
const activity = code('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const unit = code('app/src/test/java/at/heinz/deviceai/cognitive/InternetAccessModeLabelsTest.kt');

test('fresh-install restore stays ALWAYS_ON and the settings dialog advertises that same default', () => {
  assert.match(policy, /"ALWAYS_ON", null -> InternetAccessMode\.ALWAYS_ON/);
  assert.match(labels, /InternetAccessMode\.ALWAYS_ON -> "Dauerhaft erlauben · ALWAYS_ON \(Standard\)"/);
  assert.match(labels, /InternetAccessMode\.ASK -> "Auf Nachfrage"/);
  assert.doesNotMatch(labels, /Auf Nachfrage \(Standard\)/);
  assert.doesNotMatch(activity, /Auf Nachfrage \(Standard\)/);
  assert.match(activity, /InternetAccessModeLabels\.dialogOptions\(modes\)/);
  assert.match(unit, /settingsStandardLabelMatchesFreshInstallRestoreDefault/);
  assert.match(unit, /InternetConsentPolicy\.restoredMode\(null\)/);
  assert.match(unit, /InternetAccessModeLabels\.advertisedDefault\(\)/);
});
