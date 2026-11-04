import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
const root = resolve(import.meta.dirname, '../..');
const view = readFileSync(resolve(root, 'app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt'), 'utf8');
const activity = readFileSync(resolve(root, 'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'), 'utf8');

test('real drawn Canvas frames are counted separately from animation callbacks', () => {
  assert.match(view, /private var visibleFrameCount = 0L/);
  assert.match(view, /private var drawnFrameCount = 0L/);
  const start = view.indexOf('override fun onDraw(canvas: Canvas)');
  const end = view.indexOf('override fun dispatchDraw(canvas: Canvas)', start);
  assert.ok(start >= 0 && end > start, 'actual Android draw callback');
  const draw = view.slice(start, end);
  assert.match(draw, /if \(width <= 0 \|\| height <= 0\) return/);
  assert.match(draw, /renderer\.draw\(painter, renderFrame\)/);
  // One completed Canvas callback, after all native-head/orb/status alternatives, counts once.
  assert.match(draw, /\}\s*drawnFrameCount\+\+\s*canvas\.restoreToCount\(saved\)/);
  assert.equal((draw.match(/drawnFrameCount\+\+/g) || []).length, 1);
  assert.doesNotMatch(view.slice(view.indexOf('private fun advanceFrame()')), /drawnFrameCount\+\+/);
  assert.match(view, /fun sampleMotion\(\): MotionSample/);
});

test('Fold7 owner can run a 1.6s diagnostic without changing animation preferences', () => {
  assert.match(activity, /CenterAction\("HOLOGRAMM · LIVE-FRAMES PRÜFEN"/);
  assert.match(activity, /private fun showHologramMotionAudit\(\)/);
  assert.match(activity, /val first = target\.sampleMotion\(\)/);
  assert.match(activity, /val second = target\.sampleMotion\(\)/);
  assert.match(activity, /}, 1_600L\)/);
  const start = activity.indexOf('private fun showHologramMotionAudit()');
  const end = activity.indexOf('private fun showQuickActionsDialog()', start);
  const body = activity.slice(start, end);
  assert.doesNotMatch(body, /setAnimationsEnabled|Settings\.Global\.put|isPowerSaveMode\s*=/);
  assert.match(body, /callbackDelta == 0L/);
  assert.match(body, /drawnDelta == 0L/);
  assert.match(body, /phaseDelta <= 0f/);
});
