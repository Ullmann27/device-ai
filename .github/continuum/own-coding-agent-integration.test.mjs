import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('../../', import.meta.url)));
const read = (path) => readFileSync(resolve(root, path), 'utf8');

test('Continuum developer dialog exposes its own model-free worker separately from optional generative model', () => {
  const activity = read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
  const start = activity.indexOf('private fun showDevelopmentDialog()');
  const end = activity.indexOf('private fun ', start + 30);
  assert.ok(start >= 0 && end > start);
  const dialog = activity.slice(start, end);
  assert.match(dialog, /EIGENEN CODING-WORKER PRÜFEN/);
  assert.match(dialog, /Ohne Sprachmodell: Unser eigener Worker/);
  assert.match(dialog, /Mit lokalem Sprachmodell: Zusätzlich neue Codeideen/);
  assert.match(dialog, /devDialog\?\.dismiss\(\)\s+openMobileBuilder\(\)/);
  assert.match(dialog, /SPRACHMODELL EINRICHTEN/);
  assert.doesNotMatch(dialog, /Runtime\.getRuntime\(\)|\.exec\(|startForegroundService\(/);
});

test('worker diagnostic route uses existing allowlisted build Activity, not executable model output', () => {
  const activity = read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
  assert.match(activity, /ContinuumMobileBuilderRoute\.destination\(BuildConfig\.APPLICATION_ID\)/);
  assert.match(activity, /Intent\(\)\.setClassName\(destination\.packageName, destination\.className\)/);
  assert.match(activity, /Explicit allowlisted Activity launch; never launch a shell/);
  const builder = read('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt');
  assert.match(builder, /ContinuumMobileBuildPlan/);
  assert.match(builder, /setPositiveButton\("Bauen"\)/);
});

test('generative coding remains constrained and model-agnostic with review-only outputs', () => {
  const protocol = read('app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentTypes.kt');
  const coordinator = read('app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentCoordinator.kt');
  assert.match(protocol, /No[A-Za-z]*|NotExecuted/);
  assert.match(protocol, /"APK built or installed"/);
  assert.match(coordinator, /DevelopmentOutputParser\.parseDeveloper/);
  assert.match(coordinator, /DevelopmentOutputParser\.parseCritic/);
  assert.match(coordinator, /DevelopmentOutputParser\.parseTests/);
  assert.match(coordinator, /DevelopmentOutputParser\.parseJudge/);
});
