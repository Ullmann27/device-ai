import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=(path)=>fs.readFileSync(path,'utf8');
const base='app/src/main/java/at/heinz/deviceai/';
const core=read(base+'ui/HollywoodFuiConstruct.kt');
const overlay=read(base+'ui/HollywoodFuiStageOverlay.kt');
const cinema=read(base+'ui/HollywoodFuiCinematics.kt');
const shell=read(base+'ui/JarvisShellRenderer.kt');
const window=read(base+'ui/JarvisWorkWindow.kt');
const host=read(base+'ui/JarvisWorkWindowHost.kt');
const settings=read(base+'ui/JarvisCinematicSettings.kt');
const agent=read(base+'AgentActivity.kt');
const orb=read(base+'ContinuumHologramView.kt');

test('original frame timeline and safe keep-out remain canonical',()=>{
  for(const stamp of ['ANCHOR_END_MS = 140L','BRACKETS_END_MS = 320L','DATA_END_MS = 900L','CLOSE_END_MS = 420L'])
    assert.ok(core.includes(stamp),stamp);
  assert.match(core,/fun orientedTilt\(/);
  assert.match(core,/fun arcLiftDp\(/);
  assert.match(window,/HollywoodFuiConstruct\.orientedTilt\(/);
  assert.match(window,/HollywoodFuiConstruct\.arcLiftDp\(/);
  assert.match(overlay,/HollywoodFuiConstruct\.orientedTilt\(/);
  assert.match(overlay,/HollywoodFuiConstruct\.arcLiftDp\(/);
  assert.match(overlay,/protectedBounds\.forEach \{ canvas\.clipOutRect\(it\) \}/);
});

test('text-safe 0.09 raster never controls structural rim and scan',()=>{
  const f=read(base+'ui/HollywoodFuiWorkActivity.kt');
  assert.match(f,/SETTLED_RASTER_OPACITY = \.09f/);
  assert.match(f,/fun structuralOpacity\(/);
  assert.match(cinema,/val rimAlpha = HollywoodFuiWorkActivity\.structuralOpacity\(frame\.grid, frame\.closeFrame\)/);
  assert.match(cinema,/val rasterAlpha = HollywoodFuiWorkActivity\.structuralOpacity\(innerRasterAlpha, frame\.closeFrame\)/);
  assert.match(overlay,/innerRasterAlpha = raster/);
  assert.match(overlay,/val scanFrame = if \(rimIsolationEnabled\)/);
  assert.match(overlay,/else frame\.copy\(grid = raster/);
  assert.match(host,/fui\.rimIsolationEnabled = JarvisCinematicSettings\.experimentEnabled/);
});

test('EXP01 slices glass and brackets into different Z planes, not native text',()=>{
  assert.match(core,/fun layerShiftDp\(/);
  assert.match(overlay,/layerShiftDp\(tilt, -24f, true\)/);
  assert.match(overlay,/layerShiftDp\(tilt, 20f, true\)/);
  assert.match(overlay,/canvas\.clipRect\(constructed\)/);
  assert.match(host,/fui\.zSliceEnabled = JarvisCinematicSettings\.experimentEnabled/);
});

test('only an honest completed tool result may trigger a current-window pulse',()=>{
  assert.match(host,/fun verifiedStep\(callId: String\): Boolean/);
  assert.match(overlay,/fun verifiedStep\(key: String\): Boolean/);
  assert.match(overlay,/lastPulseKey == key/);
  assert.match(overlay,/pulseAtElapsedMs/);
  assert.match(agent,/record\.status == ToolExecutionStatus\.SUCCEEDED && !record\.outcomeUncertain && !result\.replayed/);
  assert.match(agent,/workWindowHost\.verifiedStep\(record\.callId\)/);
});

test('cinema uses the owner-controlled existing window and frame budget',()=>{
  assert.match(settings,/DEPTH_EXPERIMENT_KEY = "exp-52-cine-depth"/);
  assert.equal([...settings.matchAll(/EXP_\d\d_[A-Z_]+\("/g)].length,15);
  assert.match(settings,/getBoolean\(DEPTH_EXPERIMENT_KEY, true\)/);
  assert.match(settings,/fun activateCinema52ByDefault\(/);
  assert.match(settings,/putBoolean\(DEPTH_EXPERIMENT_KEY, true\)/);
  assert.match(settings,/Experiment\.entries\.filter \{ it\.wired \}/);
  assert.match(agent,/JarvisCinematicSettings\.activateCinema52ByDefault\(this\)/);
  assert.match(shell,/JarvisTiles\.drawSurface\(/);
  assert.match(shell,/PerimeterWingChoreographer\.depth/);
  assert.match(read(base+'ui/JarvisShellView.kt'),/JarvisShellRenderer\.draw\(/);
  assert.match(host,/shell\.spatialTiltX/);
  assert.match(host,/shell\.spatialTiltY/);
  assert.match(orb,/windowAccentArgb = workWindowAccentArgb/);
});

test('EXP11 orb glow is tied to a real accepted window with a bounded time course',()=>{
  const cue=read(base+'ui/JarvisWindowOrbPulse.kt');
  assert.match(cue,/DURATION_MS = 650L/);
  assert.match(cue,/fun energy\(ageMillis: Long\)/);
  assert.match(orb,/val firstWindowFrame = active && !workWindowActive/);
  assert.match(orb,/if \(firstWindowFrame\) workWindowPulseStartedAtMs = now/);
  assert.match(orb,/JarvisWindowOrbPulse\.energy\(nowMillis - workWindowPulseStartedAtMs\)/);
  assert.match(orb,/workWindowPulseStartedAtMs = -1L/);
});

test('EXP14 shader rim uses pixel space after leaving dp scaled glass canvas',()=>{
  const start=overlay.indexOf('cinematics.drawMotesAndEnergy(');
  const end=overlay.indexOf('if (dataAlpha > 0f) drawTelemetry(',start);
  assert.ok(start>=0 && end>start);
  const draw=overlay.slice(start,end);
  assert.match(draw,/cinematics\.drawBrackets\([\s\S]*?canvas\.restore\(\)\s*canvas\.restore\(\)\s*if \(prismRimEnabled/);
  assert.match(draw,/prismRim\?\.draw\(canvas, stage, frame\.tiltDegrees, frame\.pulse, density\)/);
});

test('no fake external-send or new native frame timers introduced',()=>{
  assert.match(agent,/Intent\.ACTION_SEND/);
  assert.match(agent,/kein.*gesendet|nicht als gesendet bestätigt/i);
  for(const file of [
    'ui/HollywoodFuiStageOverlay.kt',
    'ui/PerimeterWingChoreographer.kt',
    'ui/SpatialPresenceAndLightBridge.kt',
    'avatar3d/SpatialWindowLight.kt'
  ]){
    const source=read(base+file);
    assert.doesNotMatch(source,/new Thread|ScheduledExecutor|setInterval|Timer\(/);
  }
});
