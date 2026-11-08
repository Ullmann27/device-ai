import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const activity = readFileSync(new URL('../app/src/main/java/at/heinz/deviceai/AgentActivity.kt', import.meta.url), 'utf8');
const capture = readFileSync(new URL('../app/src/main/java/at/heinz/deviceai/cognitive/LocalGenerationCapture.kt', import.meta.url), 'utf8');
const policy = readFileSync(new URL('../app/src/main/java/at/heinz/deviceai/cognitive/LocalGenerationDeadlinePolicy.kt', import.meta.url), 'utf8');

function section(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0 && to > from, `${start} → ${end}`);
  return source.slice(from, to);
}

function previewConnected(a, c) {
  const generate = section(a, 'private fun generateLocal(', 'private fun updateLocalStreamPreview(');
  const delta = section(generate, 'is LocalInferenceEvent.TextDelta -> {', 'is LocalInferenceEvent.ToolProposal ->');
  assert.match(delta, /val elapsed = \(now - generationStartedNanos\)\.coerceAtLeast\(0L\) \/ 1_000_000L/);
  assert.match(delta, /val snapshot = capture\.append\(event\.text,\s*elapsed\)/);
  assert.match(delta, /if \(event\.text\.isNotEmpty\(\) && \(snapshot\.streamedChunks == 1 \|\|\s*now - lastPreviewNanos >= TimeUnit\.MILLISECONDS\.toNanos\(1_000\)\)\)/);
  assert.match(delta, /runOnUiThread \{\s*updateLocalStreamPreview\(previewToken, cancellation,\s*snapshot\.response, snapshot\.streamedChunks, elapsed, snapshot\.firstTextMillis\)/);
  assert.match(delta, /if \(snapshot\.terminal\) \{\s*turnTrace\?\.event\(TraceStage\.MODEL_FAILED\)\s*cancelNative = true\s*completed\.countDown\(\)/);
  const append = section(c, '@Synchronized fun append(', '@Synchronized fun complete(');
  assert.match(append, /lastTextMillis = elapsedMillis/);
  assert.match(append, /val remaining = LocalGenerationDeadlinePolicy\.MAX_CAPTURED_CHARACTERS - response\.length\s*response\.append\(text\.take\(remaining\)\)/);
  assert.match(append, /if \(text\.length > remaining\) \{[\s\S]*return finish\(LocalGenerationCompletion\.OUTPUT_LIMIT, "OUTPUT_LIMIT"\)/);
  assert.match(generate, /finally \{\s*runOnUiThread \{ clearLocalStreamPreview\(previewToken\) \}/);
  const render = section(a, 'private fun updateLocalStreamPreview(', 'private fun clearLocalStreamPreview(');
  assert.match(render, /activeLocalStreamToken !== token/);
  assert.match(render, /activeChatCancellation !== cancellation/);
  assert.match(render, /try \{ cancellation\?\.throwIfCancelled\(\) \}\s*catch \(_: java\.util\.concurrent\.CancellationException\) \{ return \}/);
  assert.match(render, /draft\.take\(\s*at\.heinz\.deviceai\.cognitive\.LocalGenerationDeadlinePolicy\.MAX_CAPTURED_CHARACTERS\)/);
  assert.match(render, /ANTWORTENTWURF · NOCH UNGEPRÜFT/);
  assert.doesNotMatch(render, /speakBootstrap|addAssistantMessage|memoryStore|toolGate|\.append\(/);
}

function budgetConnected(a, c, p) {
  const generate = section(a, 'private fun generateLocal(', 'private fun updateLocalStreamPreview(');
  const deadline = generate.indexOf('val generationStartedNanos = System.nanoTime()');
  const invoke = generate.indexOf('start(callback)');
  assert.ok(deadline >= 0 && invoke > deadline);
  assert.match(generate, /fun elapsedMillis\(\) = \(System\.nanoTime\(\) - generationStartedNanos\)\.coerceAtLeast\(0L\) \/ 1_000_000L/);
  const wait = section(generate, 'while (true) {', 'val readyDeadline');
  assert.ok(generate.indexOf('while (true) {') > invoke);
  assert.match(wait, /while \(true\) \{\s*cancellation\?\.throwIfCancelled\(\)\s*if \(completed\.await\(250, TimeUnit\.MILLISECONDS\)\) \{\s*if \(synchronized\(eventLock\) \{ cancelNative \}\) localRuntime\.cancel\(\)\s*break\s*\}/);
  assert.match(wait, /if \(hardDeadlineMillis != null && elapsedMillis\(\) >= hardDeadlineMillis\) \{\s*synchronized\(eventLock\) \{ capture\.fail\("BOT_DEADLINE_EXCEEDED"\) \}\s*localRuntime\.cancel\(\)\s*return capturedGeneration\(overrideCompletion = LocalGenerationCompletion\.FAILED,\s*overrideFailure = "BOT_DEADLINE_EXCEEDED"\)/);
  const byteLimit = section(generate, 'if (event is LocalInferenceEvent.TextDelta && maxOutputBytes != null &&', 'when (event)');
  assert.match(byteLimit, /\(before\.response \+ event\.text\)\.toByteArray\(Charsets\.UTF_8\)\.size > maxOutputBytes/);
  assert.match(byteLimit, /capture\.fail\("BOT_OUTPUT_BUDGET_EXCEEDED"\)\s*cancelNative = true\s*completed\.countDown\(\)\s*return@LocalInferenceCallback/);
  assert.match(a, /val remainingMillis = turn\.remainingMillis\(\)[\s\S]*hardDeadlineMillis = remainingMillis/);
  assert.match(wait, /capture\.poll\(elapsedMillis\(\)\)/);
  const poll = section(c, '@Synchronized fun poll(', '@Synchronized fun snapshot(');
  assert.match(poll, /LocalGenerationDeadlinePolicy\.stalled\(elapsedMillis, lastTextMillis, response\.length\)/);
  const result = section(generate, 'fun capturedGeneration(', 'runOnUiThread {');
  assert.match(result, /val snapshot = capture\.snapshot\(\)/);
  assert.match(result, /val completion = overrideCompletion \?: snapshot\.completion \?: LocalGenerationCompletion\.FAILED/);
  assert.match(result, /val accepted = if \(completion == LocalGenerationCompletion\.COMPLETE\) proposalGuard\.acceptedProposal\(\) else null/);
  assert.match(result, /else LocalGeneration\(snapshot\.response, accepted,[\s\S]*overrideFailure \?: snapshot\.failure, completion\)/);
  assert.doesNotMatch(generate, /generationDeadline = generationStartedNanos/);
  assert.match(p, /STARTUP_STALL_MILLIS = 180_000L/);
  assert.match(p, /STREAM_STALL_MILLIS = 120_000L/);
  assert.match(p, /MAX_CAPTURED_CHARACTERS = 20_000/);
  assert.match(generate, /localRuntime\.inferenceProfiles\.lastOrNull\(\)\?\.safeSummary\(\)/);
}

function changed(source, before, after) {
  assert.ok(source.includes(before), before);
  assert.equal(source.indexOf(before), source.lastIndexOf(before), before);
  return source.replace(before, after);
}

test('native delta preview is throttled, bounded and tied to the active request', () => {
  previewConnected(activity, capture);
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
  budgetConnected(activity, capture, policy);
});

test('capture preview and budget contracts reject disconnected source copies', () => {
  for (const [name, a, c] of [
    ['stale preview text', changed(activity, 'snapshot.response, snapshot.streamedChunks, elapsed, snapshot.firstTextMillis)', 'before.response, snapshot.streamedChunks, elapsed, snapshot.firstTextMillis)'), capture],
    ['unbounded capture', activity, changed(capture, 'response.append(text.take(remaining))', 'response.append(text)')],
    ['exact-cap overflow', activity, changed(capture, 'text.length > remaining', 'text.length >= remaining')],
    ['frozen progress', activity, changed(capture, 'lastTextMillis = elapsedMillis', 'lastTextMillis = 0L')],
    ['queued STOP ignored', changed(activity, 'try { cancellation?.throwIfCancelled() }\n        catch (_: java.util.concurrent.CancellationException) { return }', 'Unit'), capture],
    ['missing native cancel', changed(activity, 'if (synchronized(eventLock) { cancelNative }) localRuntime.cancel()', 'Unit'), capture],
    ['deadline reported complete', changed(activity, 'return capturedGeneration(overrideCompletion = LocalGenerationCompletion.FAILED,\n                        overrideFailure = "BOT_DEADLINE_EXCEEDED")', 'return capturedGeneration()'), capture],
    ['byte limit not terminal', changed(activity, 'capture.fail("BOT_OUTPUT_BUDGET_EXCEEDED")', 'capture.snapshot()'), capture],
    ['discarded captured text', changed(activity, 'else LocalGeneration(snapshot.response, accepted,', 'else LocalGeneration("", accepted,'), capture]
  ]) {
    assert.throws(() => { previewConnected(a, c); budgetConnected(a, c, policy); }, assert.AssertionError, name);
  }
});
