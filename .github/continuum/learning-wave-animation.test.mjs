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
  assert.match(phone, /val measured = series\[leftIndex\] \+ \(series\[rightIndex\] - series\[leftIndex\]\) \* fraction/);
  assert.match(phone, /val y = b - \(b - t\) \* \.95f \* \(measured \/ maxV\)/);
});

test('animation is lifecycle-bound, visible and backed by active learning', () => {
  assert.match(home, /state\.learning\.live && state\.learning\.series\.size >= 2/);
  assert.match(home, /windowVisibility == View\.VISIBLE/);
  assert.match(home, /views\[Slot\.LEARNING\]\?\.invalidate\(\)/);
  assert.match(home, /postOnAnimationDelayed\(waveFrame, 33L\)/);
  assert.match(home, /override fun onDetachedFromWindow\(\)/);
  assert.match(home, /removeCallbacks\(waveFrame\)/);
  assert.match(home, /element\.motionPhase = if \(slot == Slot\.LEARNING/);
  assert.doesNotMatch(home, /androidx\.compose|Handler\.postDelayed\(this, 16/);
});
