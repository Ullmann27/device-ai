import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

// Source contracts only. The integration owner runs Kotlin/Android lifecycle tests separately.
const activity = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt', import.meta.url), 'utf8');
const factory = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/cockpit/MemoryEventSource.kt', import.meta.url), 'utf8');

test('Activity defers its app-directory recall publisher until the compiler is first used', () => {
  const initializer = /private val contextCompiler\s+by lazy\s*\{([\s\S]*?)\n    \}/.exec(activity)?.[1];
  assert.ok(initializer, 'The Activity compiler must be lazy; filesDir is unavailable during Activity construction');
  assert.match(initializer, /AiContextCompiler\s*\(/);
  assert.match(initializer, /\.withRecallObserver\(ContinuumMemoryRecallPublisher\(filesDir\)\)/);
  assert.match(initializer, /AiContextPolicy\(maxBytes = 10_500, memoryLimit = 8, researchSourceLimit = 4, nativeToolCatalog = true\)/);
  assert.match(activity, /contextCompiler\.compile\(/, 'The instrumented compiler must remain on the real conversation path');
});

test('Activity keeps the per-app subscription and detaches queued mapper work before closing upstream', () => {
  const subscription = /private fun subscribeMemoryEvents\(\)\s*\{([\s\S]*?)\n    \}/.exec(activity)?.[1];
  assert.ok(subscription);
  assert.match(subscription, /MemoryEventSources\.forApp\(filesDir\)/);
  assert.match(subscription, /source\.subscribe\(mapper\)/);
  assert.match(subscription, /cockpitSubscriptions \+= AutoCloseable \{ mapper\.detach\(\); subscription\.close\(\) \}/);
  const destroy = /override fun onDestroy\(\)\s*\{([\s\S]*?)\n    \}/.exec(activity)?.[1];
  assert.ok(destroy);
  assert.match(destroy, /cockpitSubscriptions\.forEach \{ runCatching \{ it\.close\(\) \} \}/);
  assert.match(destroy, /cockpitSubscriptions = emptyList\(\)/);
});

test('the public app factory delegates to the guarded adapter and preserves the absent legacy lookup', () => {
  assert.match(factory, /fun forApp\(appDirectory: java\.io\.File\): MemoryEventSource =\s*ContinuumMemoryEventSource\.forApp\(appDirectory\)/);
  assert.match(factory, /val production: MemoryEventSource\? = null/);
});
