import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve, dirname, join } from "node:path";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const config = JSON.parse(readFileSync(join(root,"config/continuum-selected-models.json"),"utf8"));
const selected = readFileSync(join(root,"app/src/main/java/at/heinz/deviceai/ContinuumSelectedModels.kt"),"utf8");
const agent = readFileSync(join(root,"app/src/main/java/at/heinz/deviceai/AgentActivity.kt"),"utf8");

function literal(name) {
  const match = selected.match(new RegExp("const val " + name + " = \"([^\"]+)\""));
  assert.ok(match, "Kotlin model selection literal must exist: " + name);
  return match[1];
}

test("owner-selected Gemma metadata is byte-identical to repository model selection", () => {
  assert.equal(literal("LOCAL_ID"), config.roles.on_device.catalog_id);
  assert.equal(literal("LOCAL_VERSION"), config.roles.on_device.catalog_version);
  assert.equal(literal("LOCAL_FILENAME"), config.roles.on_device.filename);
  assert.equal(literal("LOCAL_SHA256"), config.roles.on_device.file_sha256);
  assert.equal(literal("LOCAL_SOURCE"), config.roles.on_device.source);
  assert.equal(literal("REMOTE_MODEL_ID"), config.roles.research_teacher.model_id);
  assert.equal(literal("REMOTE_SOURCE"), config.roles.research_teacher.source);
  assert.equal(config.roles.research_teacher.download_state,"NOT_DOWNLOADED");
  assert.equal(config.roles.on_device.device_load_test,"NOT_RUN");
});
test("existing owner model center shows choices without automatically downloading", () => {
  assert.match(agent,/UNSERE AUSGEWÄHLTEN MODELLE/);
  assert.match(agent,/ContinuumSelectedModels\.REMOTE_STATUS/);
  assert.match(agent,/ContinuumSelectedModels\.RETIREMENT_GATE/);
  assert.match(agent,/showModelImportDialog\(useSelectedGemma = true\)/);
  assert.match(agent,/Intent\(Intent\.ACTION_VIEW,\s*Uri\.parse\(ContinuumSelectedModels\.LOCAL_SOURCE\)\)/);
  assert.match(agent,/id\.setText\(ContinuumSelectedModels\.LOCAL_ID\)/);
  assert.match(agent,/version\.setText\(ContinuumSelectedModels\.LOCAL_VERSION\)/);
  assert.match(agent,/digest\.setText\(ContinuumSelectedModels\.LOCAL_SHA256\)/);
  assert.match(agent,/modelCatalog\.import\(metadata\.first, metadata\.second, it, metadata\.third\)/);
});
test("selected model has a live status binding and never grants shell rights", () => {
  assert.match(agent,/ContinuumSelectedModels\.gemmaStatus\(models, loaded\)/);
  assert.match(agent,/selectedModelStatusText\?\.text/);
  assert.match(agent,/selectedModelStatusText = null/);
  assert.match(selected,/kein verbundener Inferenzserver/);
  assert.doesNotMatch(selected,/(Runtime\.getRuntime|ProcessBuilder|exec\(|\.install\(|File\()/);
});
