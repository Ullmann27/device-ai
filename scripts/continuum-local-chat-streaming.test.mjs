import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const activity = readFileSync(new URL('../app/src/main/java/at/heinz/deviceai/AgentActivity.kt', import.meta.url), 'utf8');
test('native delta preview is throttled, bounded and tied to the active request', () => {
  const generate = activity.split('private fun generateLocal(')[1].split('private fun renderCloudPlan(')[0];
  assert.match(generate, /TimeUnit\.MILLISECONDS\.toNanos\(250\)/);
  assert.match(generate, /response\.length < 8_000/);
  assert.match(generate, /updateLocalStreamPreview\(previewToken, cancellation,/);
  assert.match(generate, /finally[\s\S]*clearLocalStreamPreview\(previewToken\)/);
  const render = activity.split('private fun updateLocalStreamPreview(')[1].split('private fun clearLocalStreamPreview(')[0];
  assert.match(render, /activeLocalStreamToken !== token/);
  assert.match(render, /activeChatCancellation !== cancellation/);
  assert.match(render, /ANTWORTENTWURF · NOCH UNGEPRÜFT/);
  assert.doesNotMatch(render, /speakBootstrap|addAssistantMessage|memoryStore|toolGate|\.append\(/);
});
test('stream completion removes only its own transient row', () => {
  const clear = activity.split('private fun clearLocalStreamPreview(')[1].split('private fun renderCloudPlan(')[0];
  assert.match(clear, /activeLocalStreamToken !== token/);
  assert.match(clear, /removeView/);
  assert.match(clear, /activeLocalStreamToken = null/);
});

test('Stop clears preview synchronously and queued rendering checks cancellation state', () => {
  const stop = activity.split('private fun cancelLocalRequest()')[1].split('private fun generateLocal(')[0];
  assert.match(stop, /activeLocalStreamToken\?\.let \{ clearLocalStreamPreview\(it\) \}/);
  const render = activity.split('private fun updateLocalStreamPreview(')[1].split('private fun clearLocalStreamPreview(')[0];
  assert.match(render, /cancellation\?\.throwIfCancelled\(\)/);
  assert.match(render, /localRuntime\.loadedModel\?\.id/);
});

test('native text deltas preserve standalone whitespace while ignoring empty chunks', () => {
  const runtime = readFileSync(new URL('../app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt', import.meta.url), 'utf8');
  assert.match(runtime, /text\.text\.isEmpty\(\) \|\| finished\.get\(\)/);
  assert.doesNotMatch(runtime, /text\.text\.isBlank\(\)/);
});

test('invocation time consumes the same generation budget and safe profile reaches existing status', () => {
  const generate = activity.split('private fun generateLocal(')[1].split('private fun updateLocalStreamPreview(')[0];
  const deadline = generate.indexOf('val generationDeadline = generationStartedNanos');
  const invoke = generate.indexOf('start(callback)');
  assert.ok(deadline >= 0 && invoke > deadline);
  assert.match(generate, /if \(completed\.await\(10, TimeUnit\.MILLISECONDS\)\) break/);
  assert.match(activity, /localRuntime\.inferenceProfiles\.lastOrNull\(\)\?\.safeSummary\(\)/);
});
