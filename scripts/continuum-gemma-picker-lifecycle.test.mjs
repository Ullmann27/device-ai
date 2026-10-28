import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const src = readFileSync(resolve(root, 'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'), 'utf8');
const modelPath = 'app/src/main/java/at/heinz/deviceai/AgentActivity.kt';

test('Gemma picker retains public model ID/version/SHA across Android activity recreation', () => {
  for (const name of [
    'MODEL_IMPORT_ID_STATE_KEY',
    'MODEL_IMPORT_VERSION_STATE_KEY',
    'MODEL_IMPORT_SHA256_STATE_KEY'
  ]) {
    assert.match(src, new RegExp('private const val ' + name + ' = "[a-z0-9_]+"'));
    assert.match(src, new RegExp('outState\\.putString\\(' + name + ', '));
    assert.match(src, new RegExp('state\\.getString\\(' + name + '\\)'));
  }
});

test('Restored file selection metadata is validated, never trusted', () => {
  assert.match(src, /sha\.matches\(Regex\("\[a-f0-9\]\{64\}"\)\)/);
  assert.match(src, /id\.matches\(Regex\(/);
  assert.match(src, /version\.matches\(Regex\(/);
  assert.match(src, /pendingModelImport = Triple\(id, version, sha\)/);
});

test('Picker returning valid URI cannot fail silently if metadata was lost', () => {
  const beginning = src.indexOf('private fun importModel(uri: Uri)');
  assert.ok(beginning >= 0, modelPath);
  const part = src.slice(beginning, beginning + 1450);
  assert.doesNotMatch(part, /pendingModelImport \?: return/);
  assert.match(part, /if \(metadata == null\)/);
  assert.match(part, /Gemma-Import abgebrochen/);
  assert.match(part, /refreshLocalModelUi\(\)/);
});

test('User can see that an actual large file import has started', () => {
  const start = src.indexOf('private fun importModel(uri: Uri)');
  const end = src.indexOf('private fun reloadInstalledModels()', start);
  const part = src.slice(start, end);
  assert.match(part, /modelImportActive = true/);
  assert.match(part, /Modelldatei wird lokal geprüft/);
  assert.match(part, /Modelldatei geprüft\. Gemma wird jetzt geladen/);
  assert.match(part, /setTitle\("Gemma-Import fehlgeschlagen"\)/);
  assert.match(part, /Technischer Grund:/);
  assert.match(part, /\.setPositiveButton\("VERSTANDEN", null\)/);
});

test('Canceled Android document picker leaves old model intact', () => {
  const start = src.indexOf('MODEL_IMPORT_REQUEST -> {');
  const end = src.indexOf('DEV_SOURCE_REQUEST ->', start);
  assert.ok(start > 0 && end > start);
  const part = src.slice(start, end);
  assert.match(part, /resultCode != RESULT_OK/);
  assert.match(part, /pendingModelImport = null/);
  assert.match(part, /uri == null/);
  assert.match(part, /Keine lesbare Modelldatei ausgewählt/);
  assert.match(part, /else -> importModel\(uri\)/);
  assert.doesNotMatch(part, /\.delete\(\)|clearData\(/);
});

test('Gemma import still verifies SHA-256 and CPU support; no direct owner writes', () => {
  assert.match(src, /modelCatalog\.import\(metadata\.first, metadata\.second, it, metadata\.third\)/);
  assert.match(src, /localAgentExecutor\.execute/);
  assert.doesNotMatch(src.slice(src.indexOf('private fun importModel(uri: Uri)'), src.indexOf('private fun reloadInstalledModels()')), /\bpm uninstall\b|\bpm clear\b/);
});
