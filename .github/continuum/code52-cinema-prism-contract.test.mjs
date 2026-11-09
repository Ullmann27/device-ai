import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root = 'app/src/main/java/at/heinz/deviceai/ui/';
const shader = fs.readFileSync(root + 'AgslHologramEdgePainter.kt','utf8');
const overlay = fs.readFileSync(root + 'HollywoodFuiStageOverlay.kt','utf8');
const host = fs.readFileSync(root + 'JarvisWorkWindowHost.kt','utf8');
const settings = fs.readFileSync(root + 'JarvisCinematicSettings.kt','utf8');

test('EXP14 uses hardware-only single-pass stroke, never a full-window postprocess',()=>{
  assert.match(shader,/RuntimeShader\(SOURCE\)/);
  assert.match(shader,/style = Paint.Style.STROKE/);
  assert.match(shader,/canvas\.drawPath\(path, paint\)/);
  assert.match(shader,/!canvas\.isHardwareAccelerated/);
  assert.match(shader,/catch \(_: RuntimeException\)/);
  assert.doesNotMatch(shader,/setRenderEffect|createRuntimeShaderEffect|content\.eval\(/);
  assert.doesNotMatch(shader,/uniform shader content/);
});

test('EXP14 respects original avatar/dialog protected bounds and owner opt-in',()=>{
  assert.match(overlay,/protectedBounds\.forEach \{ canvas\.clipOutRect\(it\) \}/);
  assert.match(overlay,/prismRim\?\.draw\(canvas, stage, frame\.tiltDegrees, frame\.pulse, density\)/);
  assert.match(overlay,/Build\.VERSION\.SDK_INT >= 33/);
  assert.match(host,/fui\.prismRimEnabled = JarvisCinematicSettings\.experimentEnabled\(activity,/);
  assert.match(host,/Experiment\.EXP_14_GLASS_SHADER/);
  assert.match(settings,/getBoolean\(DEPTH_EXPERIMENT_KEY, false\)/);
});
