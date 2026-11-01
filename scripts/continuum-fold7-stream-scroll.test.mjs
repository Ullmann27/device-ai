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
  assert.doesNotMatch(activity, /generationDeadline = generationStartedNanos/);
  assert.match(activity, /LocalGenerationDeadlinePolicy\.stalled/);
  assert.match(policy, /STREAM_STALL_MILLIS = 120_000L/);
  assert.match(policy, /STARTUP_STALL_MILLIS = 180_000L/);
  assert.match(policy, /MAX_CAPTURED_CHARACTERS = 20_000/);
  assert.doesNotMatch(policy, /ACTIVE_STREAM_MAX_MILLIS|BASE_MILLIS/);
});

test('existing cancellation and unverified-draft protections remain in place', () => {
  assert.match(activity, /cancellation\?\.throwIfCancelled\(\)/);
  assert.match(activity, /if \(activeLocalStreamToken !== token\) return/);
  assert.match(activity, /runOnUiThread \{ clearLocalStreamPreview\(previewToken\) \}/);
  assert.match(activity, /terminalEventReceived = true/);
});
