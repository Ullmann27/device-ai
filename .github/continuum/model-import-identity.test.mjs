import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const activity = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt', import.meta.url), 'utf8');
const section = (a, b) => activity.slice(activity.indexOf(a), activity.indexOf(b, activity.indexOf(a)));
test('a rejected import cannot relabel the independently loaded runtime as failed', () => {
  const imported = section('private fun importModel(uri: Uri)', 'private fun reloadInstalledModels()');
  assert.match(imported, /localImportError = "Modellimport fehlgeschlagen: " \+ \(operation\.failureType/);
  assert.doesNotMatch(imported, /localRuntimeError =/);
  assert.match(imported, /modelImports\.start\(metadata\.first, metadata\.second, metadata\.third\)/);
  const coordinator = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/localmodel/LocalModelImportCoordinator.kt', import.meta.url), 'utf8');
  assert.match(coordinator, /openInput\(\)\.use \{ source ->[\s\S]*catalog\.import\(id, version, source, expectedSha256\)/);
  assert.match(imported, /forImport = true/);
  const ui = section('private fun refreshLocalModelUi()', 'private fun scheduleLifecycleTimeout');
  assert.match(ui, /localRuntimeError != null -> LocalModelUiState.ERROR/);
  assert.doesNotMatch(ui, /localImportError != null -> LocalModelUiState.ERROR/);
  assert.match(ui, /localImportError\?\.let \{ card\.append/);
  assert.match(ui, /SHA-256: \$\{it.sha256\}/);
});
test('smoke result names the captured catalog identity and rejects a model swap', () => {
  const smoke = section('private fun runLocalSmokeTest', 'private fun runLocal(');
  assert.match(smoke, /val testedModel = localRuntime.loadedModel/);
  assert.equal((smoke.match(/check\(localRuntime.loadedModel == testedModel\)/g) || []).length, 2);
  assert.match(smoke, /Geprüfte Katalogidentität: \$identity/);
  assert.doesNotMatch(smoke, /direkte lokale Qwen-Inferenz/);
});
