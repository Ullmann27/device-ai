import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const base = 'app/src/main/java/at/heinz/deviceai/';
const read = path => fs.readFileSync(base + path, 'utf8');

test('EXP06 reaches the renderer before both window-light sources are combined', () => {
  const view = read('ContinuumHologramView.kt');
  const renderer = read('avatar3d/FilamentAvatarRenderer.kt');
  assert.match(view, /windowLightEnabled =\s*[\s\S]*?Experiment\.EXP_06_FACE_LIGHT/);
  assert.match(renderer, /CinemaWindowLightState\.resolve\(windowLightEnabled,/);
  assert.doesNotMatch(renderer, /val open = windowOpen \|\| flyIn\.open/);
});

test('EXP11 controls the whole transfer while receipt rims stay independent', () => {
  const host = read('ui/JarvisWorkWindowHost.kt');
  const overlay = read('ui/HollywoodFuiStageOverlay.kt');
  assert.match(host, /fui\.orbLinkEnabled = JarvisCinematicSettings\.experimentEnabled\(activity,\s*JarvisCinematicSettings\.Experiment\.EXP_11_ORB_LINK\)/);
  assert.match(overlay, /if \(orbLinkEnabled && fluid\.energy > 0f\) JarvisFluidLight\.drawTransfer/);
  assert.match(overlay, /val receipt = if \(orbLinkEnabled && workingFluid\.energy > 0f/);
  const admission = overlay.slice(overlay.indexOf('fun verifiedStep('), overlay.indexOf('fun setWorkActive('));
  assert.match(admission, /workPulseEnabled/);
  assert.doesNotMatch(admission, /orbLinkEnabled/);
});
