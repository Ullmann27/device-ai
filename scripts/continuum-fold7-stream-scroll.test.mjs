import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const path = resolve(import.meta.dirname, '../app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const activity = readFileSync(path, 'utf8');
const policy = readFileSync(
  resolve(import.meta.dirname, '../app/src/main/java/at/heinz/deviceai/cognitive/LocalGenerationDeadlinePolicy.kt'),
  'utf8'
);
const capture = readFileSync(
  resolve(import.meta.dirname, '../app/src/main/java/at/heinz/deviceai/cognitive/LocalGenerationCapture.kt'), 'utf8');
const outputPolicy = readFileSync(
  resolve(import.meta.dirname, '../app/src/main/java/at/heinz/deviceai/localmodel/LocalModelGenerationPolicy.kt'), 'utf8');

function section(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0 && to > from, `${start} → ${end}`);
  return source.slice(from, to);
}

function watchdogConnected(a, c, p) {
  const generate = section(a, 'private fun generateLocal(', 'private fun updateLocalStreamPreview(');
  const wait = section(generate, 'while (true) {', 'val readyDeadline');
  assert.match(generate, /val capture = at\.heinz\.deviceai\.cognitive\.LocalGenerationCapture\(\)/);
  assert.match(wait, /val snapshot = synchronized\(eventLock\) \{ capture\.poll\(elapsedMillis\(\)\) \}/);
  const stalled = section(wait, 'if (snapshot.completion in setOf(', '\n            }');
  assert.match(stalled, /LocalGenerationCompletion\.STALLED, LocalGenerationCompletion\.OUTPUT_LIMIT/);
  assert.match(stalled, /localRuntime\.cancel\(\)[\s\S]*return capturedGeneration\(overrideFailure = diagnostic\)/);
  const poll = section(c, '@Synchronized fun poll(', '@Synchronized fun snapshot(');
  assert.match(poll, /if \(completion != null\) return snapshot\(\)/);
  assert.match(poll, /val reason = LocalGenerationDeadlinePolicy\.stalled\(elapsedMillis, lastTextMillis, response\.length\)\s*\?: return snapshot\(\)/);
  assert.match(poll, /return finish\(if \(reason == LocalGenerationDeadlinePolicy\.StallReason\.OUTPUT_LIMIT\)\s*LocalGenerationCompletion\.OUTPUT_LIMIT else LocalGenerationCompletion\.STALLED, reason\.name\)/);
  assert.match(p, /STREAM_STALL_MILLIS = 120_000L/);
  assert.match(p, /STARTUP_STALL_MILLIS = 180_000L/);
  assert.match(p, /MAX_CAPTURED_CHARACTERS = LocalModelGenerationPolicy\.MAX_RESPONSE_CHARACTERS/);
  assert.match(outputPolicy, /MAX_RESPONSE_CHARACTERS = 64_000/);
  assert.match(p, /if \(capturedCharacters > MAX_CAPTURED_CHARACTERS\) return StallReason\.OUTPUT_LIMIT/);
  assert.match(p, /elapsedMillis - lastTextMillis >= STREAM_STALL_MILLIS/);
  assert.doesNotMatch(generate, /generationDeadline = generationStartedNanos/);
  assert.doesNotMatch(p, /ACTIVE_STREAM_MAX_MILLIS|BASE_MILLIS/);
}

function terminalConnected(a, c) {
  const generate = section(a, 'private fun generateLocal(', 'private fun updateLocalStreamPreview(');
  const callback = section(generate, 'val callback =', 'val started =');
  assert.match(callback, /val before = capture\.snapshot\(\)\s*if \(before\.terminal\) return@LocalInferenceCallback/);
  const completed = callback.slice(callback.indexOf('is LocalInferenceEvent.Completed ->'));
  assert.match(completed, /capture\.complete\(\)\s*completed\.countDown\(\)/);
  const append = section(c, '@Synchronized fun append(', '@Synchronized fun complete(');
  assert.match(append, /if \(completion != null \|\| text\.isEmpty\(\)\) return snapshot\(\)/);
  assert.match(c, /@Synchronized fun complete\(\): Snapshot = finish\(LocalGenerationCompletion\.COMPLETE\)/);
  const finish = c.slice(c.indexOf('private fun finish('));
  assert.match(finish, /if \(completion == null\) \{\s*completion = value\s*failure = message\s*\}\s*return snapshot\(\)/);
  const stop = generate.slice(generate.indexOf('} catch (cancelled: java.util.concurrent.CancellationException)'));
  assert.match(stop, /synchronized\(eventLock\) \{ capture\.cancel\(cancelled\.message\) \}\s*runCatching \{ localRuntime\.cancel\(\) \}\s*throw cancelled/);
  assert.match(stop, /finally \{\s*runOnUiThread \{ clearLocalStreamPreview\(previewToken\) \}/);
  const clear = section(a, 'private fun clearLocalStreamPreview(', 'private fun renderCloudPlan(');
  assert.match(clear, /if \(activeLocalStreamToken !== token\) return/);
}

function changed(source, before, after) {
  assert.ok(source.includes(before), before);
  assert.equal(source.indexOf(before), source.lastIndexOf(before), before);
  return source.replace(before, after);
}

test('stream preview does not force focus to bottom or make live draft selectable', () => {
  const preview = activity.split('private fun updateLocalStreamPreview(')[1]
    .split('private fun clearLocalStreamPreview(')[0];
  assert.ok(preview.includes('if (!streamUserPausedScrolling) scrollToBottom()'));
  assert.ok(!preview.includes('preview.setTextIsSelectable(true)'));
  assert.ok(!activity.includes('chatScroll.fullScroll(View.FOCUS_DOWN)'));
});

test('owner scrolling pauses auto-follow while a native response streams', () => {
  assert.match(activity, /MotionEvent\.ACTION_MOVE -> streamUserPausedScrolling = true/);
  assert.match(activity, /activeLocalStreamToken != null && streamUserPausedScrolling/);
  assert.match(activity, /isChatNearBottom\(\)/);
  assert.match(activity, /currentScroll\.scrollTo\(0, bottom\)/);
});

test('time deadline is based on progress, not fixed wall clock', () => {
  watchdogConnected(activity, capture, policy);
});

test('existing cancellation and unverified-draft protections remain in place', () => {
  terminalConnected(activity, capture);
});

test('watchdog and terminal contracts reject disconnected source copies', () => {
  for (const [name, a, c, p] of [
    ['missing poll', changed(activity, 'capture.poll(elapsedMillis())', 'capture.snapshot()'), capture, policy],
    ['frozen elapsed', changed(activity, 'capture.poll(elapsedMillis())', 'capture.poll(0L)'), capture, policy],
    ['ignored progress', activity, changed(capture, 'stalled(elapsedMillis, lastTextMillis, response.length)', 'stalled(elapsedMillis, null, response.length)'), policy],
    ['exact-cap overflow', activity, capture, changed(policy, 'capturedCharacters > MAX_CAPTURED_CHARACTERS', 'capturedCharacters >= MAX_CAPTURED_CHARACTERS')],
    ['lost partial', changed(activity, 'return capturedGeneration(overrideFailure = diagnostic)', 'return LocalGeneration("", null, emptyList(), diagnostic)'), capture, policy],
    ['late callback', changed(activity, 'if (before.terminal) return@LocalInferenceCallback', ''), capture, policy],
    ['late append', activity, changed(capture, 'completion != null || text.isEmpty()', 'text.isEmpty()'), policy],
    ['terminal overwrite', activity, changed(capture, 'if (completion == null) {', 'if (true) {'), policy],
    ['unbound completion', changed(activity, 'capture.complete()', 'capture.snapshot()'), capture, policy],
    ['unbound STOP', changed(activity, 'capture.cancel(cancelled.message)', 'capture.snapshot()'), capture, policy],
    ['retained preview', changed(activity, 'runOnUiThread { clearLocalStreamPreview(previewToken) }', 'Unit'), capture, policy]
  ]) {
    assert.throws(() => { watchdogConnected(a, c, p); terminalConnected(a, c); }, assert.AssertionError, name);
  }
});
