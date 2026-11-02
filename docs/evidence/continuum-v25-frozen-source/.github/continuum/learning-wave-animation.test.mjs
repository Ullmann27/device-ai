import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const source = (file) => fs.readFileSync(path.join(root, file), 'utf8');
const phone = source('app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeElements.kt');
const wide = source('app/src/main/java/at/heinz/deviceai/ui/ContinuumWideElements.kt');
const home = source('app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeView.kt');
const collector = source('app/src/main/java/at/heinz/deviceai/ContinuumDashboardCollector.kt');

test('both layouts display the same measured journal series and native painter', () => {
  assert.match(phone, /WaveChart\.draw\(p, 14f, 72f, w - 11f, 112f, l\.series, phase = motionPhase\)/);
  assert.match(wide, /WaveChart\.draw\(p, 13f, 60f, 256f, 107f, l\.series, phase = motionPhase\)/);
  assert.match(collector, /series = ContinuumDashboardMath\.hourlySeries\(times, now\)/);
  assert.doesNotMatch(phone + wide + home, /androidx\.compose|ResearchTrainingMetrics|mockLoss|trainingLoss = 0\./i);
});

test('missing or zero measurements render no fabricated decorative peaks', () => {
  assert.match(phone, /if \(series\.size < 2 \|\| series\.maxOrNull\(\)\?\.let \{ it <= 0f \} != false\) return/);
  // The marker follows the drawn Bézier between two real samples (WaveChartMarkerTest checks the geometry).
  assert.match(phone, /val \(x, y\) = markerOnCurve\(l, t, r, b, series, maxV, phase\)/);
  assert.match(phone, /py\(i0\) \+ \(py\(i0 \+ 1\) - py\(i0\)\) \* u \* u \* \(3f - 2f \* u\)/);
});

test('new-sample highlights are finite, foreground-bound and never edit measurements', () => {
  assert.match(home, /foregroundActive && isAttachedToWindow/);
  assert.match(home, /now\(\) < maxOf\(learningMotionUntil, modelMotionUntil, systemMotionUntil\)/);
  assert.match(home, /value\.learning\.series != field\.learning\.series/);
  assert.match(home, /windowVisibility == View\.VISIBLE/);
  assert.match(home, /ValueAnimator\.areAnimatorsEnabled\(\)/);
  assert.match(home, /!powerManager\.isPowerSaveMode/);
  assert.match(home, /views\[Slot\.LEARNING\]\?\.invalidate\(\)/);
  assert.match(home, /views\[Slot\.MODEL\]\?\.invalidate\(\)/);
  assert.match(home, /views\[Slot\.SYSTEM_ACTIVITY\]\?\.invalidate\(\)/);
  assert.match(home, /postOnAnimationDelayed\(waveFrame, 66L\)/);
  assert.match(home, /override fun onDetachedFromWindow\(\)/);
  assert.match(home, /removeCallbacks\(waveFrame\)/);
  assert.match(home, /element\.motionPhase = if \(active && waveAnimationEnabled\(\)\)/);
  assert.match(phone, /if \(i < lit\) \{/);
  assert.match(phone, /val scan = phase\.coerceIn\(0f, 1f\) \* lit/);
  assert.doesNotMatch(home, /androidx\.compose|Handler\.postDelayed\(this, 16/);
});
