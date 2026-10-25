import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(new URL("../" + path, import.meta.url), "utf8");
}
const preset = read("app/src/main/java/at/heinz/deviceai/localmodel/LocalModelImportPreset.kt");
const activity = read("app/src/main/java/at/heinz/deviceai/AgentActivity.kt");
const source = JSON.parse(read("config/continuum-selected-models.json"));
const expected = source.roles.on_device;

test("Gemma import preset stays byte-for-byte aligned with versioned model selection", () => {
  assert.ok(preset.includes('GEMMA4_E4B_ID = "' + expected.catalog_id + '"'));
  assert.ok(preset.includes('GEMMA4_E4B_VERSION = "' + expected.catalog_version + '"'));
  assert.ok(preset.includes('GEMMA4_E4B_SHA256 = "' + expected.file_sha256 + '"'));
  assert.match(expected.file_sha256, /^[a-f0-9]{64}$/);
  assert.equal(expected.device_load_test, "NOT_RUN");
});
test("model selector reuses original picker and original hash-verified importer", () => {
  assert.match(activity, /GEMMA 4 E4B · PRÜFWERT VORBELEGEN/);
  assert.match(activity, /showModelImportDialog\(preselectGemma4 = true\)/);
  assert.match(activity, /digest\.setText\(LocalModelImportPreset\.GEMMA4_E4B_SHA256\)/);
  assert.match(activity, /source\.use \{ modelCatalog\.import\(metadata\.first, metadata\.second, it, metadata\.third\) \}/);
  assert.match(activity, /ACTION_OPEN_DOCUMENT/);
  assert.equal((activity.match(/private fun showModelImportDialog\(/g) ?? []).length, 1);
});
