import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const base = 'app/src/main/java/at/heinz/deviceai/';
const read = (path) => fs.readFileSync(base + path, 'utf8');
const source = {
  orb: read('ui/ContinuumBrainOrbRenderer.kt'),
  brain: read('ui/ContinuumBrainSceneView.kt'),
  tiles: read('ui/JarvisTiles.kt'),
  tileEnergy: read('ui/JarvisTileEnergy.kt'),
  shell: read('ui/JarvisShellRenderer.kt'),
  shellView: read('ui/JarvisShellView.kt'),
  motion: read('ui/JarvisMotion.kt'),
  cinematic: read('ui/JarvisCinematicSettings.kt'),
  overlay: read('ui/HollywoodFuiStageOverlay.kt'),
  host: read('ui/JarvisWorkWindowHost.kt'),
  avatar: read('avatar3d/FilamentAvatarRenderer.kt'),
  face: read('avatar3d/AvatarMorphMapper.kt'),
  framing: read('avatar3d/AvatarFraming.kt'),
  idle: read('avatar3d/AvatarIdleMotion.kt'),
  gaze: read('ui/JarvisWindowGaze.kt')
};
const contains = (name, parts) => {
  for (const part of parts) assert.ok(source[name].includes(part), name + ' missing ' + part);
};
const assertMaterialClocks = (shell, shellView) => {
  assert.match(shell, /JarvisTiles\.drawSurface\(p, box, tile, f\.glow > \.5f,\s*if \(animatorScale > 0f && depthBudget\?\.animate != false\)\s*\(if \(pressed\) materialTimeMs else timeMs\) else 0L,/);
  assert.match(shell, /glass\?\.drawBackground\(shell\.width, shell\.height, timeMs\)/);
  assert.match(shellView, /val sceneTime = if \(ambientMotion\(scale\)\) now else 0L/);
  assert.match(shellView, /val materialTime = if \(motionAllowed\(scale\)\) now else 0L/);
  assert.match(shellView, /JarvisShellRenderer\.draw\(textPainter, s, content, elapsed, scale, tiles, glass, sceneTime, deferForeground = true,/);
  assert.match(shellView, /materialTimeMs = materialTime/);
};

test('PR #550 phase safety and PR #558 original-orb hypothesis marker remain live', () => {
  contains('orb', [
    's.phase in -1_000_000f..1_000_000f',
    'if (stable.hypothesis)',
    'for (i in 0 until 12)',
    'val first = FloatArray(3)',
    'val second = FloatArray(3)'
  ]);
  contains('brain', ['hypothesis = effects.hypothesisVisible(now)', 'ContinuumBrainOrbRenderer.Signals(']);
  const regression = fs.readFileSync('app/src/test/java/at/heinz/deviceai/ui/ContinuumOrbNonFiniteSignalsTest.kt', 'utf8');
  assert.match(regression, /phase = Float.MAX_VALUE/);
  assert.match(regression, /phase = -Float.MAX_VALUE/);
  const marker = fs.readFileSync('app/src/test/java/at/heinz/deviceai/ui/ContinuumOrbHypothesisMarkerTest.kt','utf8');
  assert.match(marker, /assertEquals\(12, arcs.size\)/);
});

test('PRs #489 #541 #549 animations arrive at the actual visible and budgeted tiles', () => {
  contains('motion', ['at.heinz.deviceai.motion.MotionPresets', 'DampedSpring.fromStiffness']);
  contains('tileEnergy', ['CYCLE_MS = 13_000L', 'fun sample(', 'cos(angle)', 'sin(angle)']);
  contains('tiles', ['JarvisTileEnergy.sample(timeMs, b.width, b.height, tile.target, radius)', 'JarvisTileEnergy.sample(timeMs - 140L']);
  assertMaterialClocks(source.shell, source.shellView);
  contains('shellView', [
    'var ambientMotionEnabled = true',
    'private fun ambientMotion(scale: Float) = ambientMotionEnabled && motionAllowed(scale)',
    '!powerManager.isPowerSaveMode',
    'motionBudgetProvider?.invoke()?.animate != false'
  ]);
});

test('material clock contract rejects ungated motion and background coupling', () => {
  assertMaterialClocks(source.shell, source.shellView);
  for (const [name, before, after] of [
    ['shell', 'animatorScale > 0f && depthBudget?.animate != false', 'animatorScale > 0f'],
    ['shell', 'animatorScale > 0f && depthBudget?.animate != false', 'depthBudget?.animate != false'],
    ['shell', '(if (pressed) materialTimeMs else timeMs) else 0L', 'materialTimeMs else 0L'],
    ['shell', 'drawBackground(shell.width, shell.height, timeMs)', 'drawBackground(shell.width, shell.height, materialTimeMs)'],
    ['shellView', 'val sceneTime = if (ambientMotion(scale)) now else 0L', 'val sceneTime = now'],
    ['shellView', 'val materialTime = if (motionAllowed(scale)) now else 0L', 'val materialTime = now'],
    ['shellView', 'tiles, glass, sceneTime, deferForeground = true', 'tiles, glass, materialTime, deferForeground = true'],
    ['shellView', 'materialTimeMs = materialTime', 'materialTimeMs = sceneTime']
  ]) {
    assert.ok(source[name].includes(before), before);
    const mutant = {...source, [name]: source[name].replace(before, after)};
    assert.throws(() => assertMaterialClocks(mutant.shell, mutant.shellView), assert.AssertionError);
  }
});

test('PRs #565 #617 #659 #666 #677 #678 original avatar still owns one GLB renderer', () => {
  contains('face', ['private val idleMotion = AvatarIdleMotion()', 'private val gazeBlink = GazeBlinkPolicy()', 'private val headYawSpring = CriticalDampedSpring()', 'gazeBlinkEnabled && motion && pose.gazeDirected']);
  contains('avatar', ['mapper.update(pose, seconds, motion, gazeBlinkEnabled = gazeBlinkEnabled)', 'applySurfaceRadiance(coreEnergy', 'applyCoreLight(coreEnergy', 'SpatialWindowLight.tint(']);
  contains('framing', ['class', 'data class']);
  contains('host', ['home.hologram.setWorkWindowCamera(', 'fui.present(elapsed, closing, reduced, open)', 'fui.prismRimEnabled', 'fui.zSliceEnabled']);
  contains('gaze', ['fun fromProjection(']);
});

test('Cinema 52 defaults to all wired effects and clips decorative glass around the native UI', () => {
  contains('cinematic', ['DEPTH_EXPERIMENT_KEY', 'getBoolean(DEPTH_EXPERIMENT_KEY, true)', 'activateCinema52ByDefault', 'putBoolean(DEPTH_EXPERIMENT_KEY, true)', 'experiment.wired && enabled(context)']);
  contains('overlay', ['protectedBounds.forEach { canvas.clipOutRect(it) }', 'cinematics.drawBrackets(painter', 'prismRim?.draw(canvas, stage, frame.tiltDegrees, frame.pulse, density)']);
  const part = source.overlay.slice(source.overlay.indexOf('cinematics.drawMotesAndEnergy('), source.overlay.indexOf('if (dataAlpha > 0f) drawTelemetry('));
  assert.match(part, /cinematics\.drawBrackets\([\s\S]*?canvas\.restore\(\)\s*canvas\.restore\(\)\s*if \(prismRimEnabled/);
  assert.equal([...source.cinematic.matchAll(/EXP_\d\d_[A-Z_]+\("/g)].length, 15);
  assert.equal([...source.cinematic.matchAll(/wired = true/g)].length, 15);
});

test('Astra #661 Java2D reference-fidelity tool and preview both remain available', () => {
  for (const path of [
    'scripts/hollywood-fui-reference-fidelity.sh',
    'scripts/hollywood-fui-cinematics-preview.sh',
    'tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/HollywoodFuiReferenceFidelity.kt',
    'tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/HollywoodFuiCinematicsPreview.kt'
  ]) assert.ok(fs.existsSync(path), path);
});
