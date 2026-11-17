import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=(p)=>fs.readFileSync(path.join(root,p),'utf8');
const hologram=read('app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt');
const activity=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const ring=read('app/src/main/java/at/heinz/deviceai/ui/ContinuumOrbitalRing.kt');
const collector=read('app/src/main/java/at/heinz/deviceai/ContinuumDashboardCollector.kt');
const gauges=read('app/src/main/java/at/heinz/deviceai/ui/ContinuumSystemGaugesView.kt');
const labels=read('app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeElements.kt');
const gradle=read('app/build.gradle.kts');
const ccc1=read('app/src/main/java/at/heinz/deviceai/ContinuumCcc1Bridge.kt');
const builderActivity=read('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt');
const essential=read('app/src/main/java/at/heinz/deviceai/ui/AvatarEssentialMotionPolicy.kt');
const phasePolicy=read('app/src/main/java/at/heinz/deviceai/ui/JarvisComputePhasePolicy.kt');

const hologramSection=(start,end)=>{
  const a=hologram.indexOf(start);
  assert.notEqual(a,-1,'Missing '+start);
  const b=hologram.indexOf(end,a+start.length);
  assert.ok(b>a,'Missing ending '+end);
  return hologram.slice(a,b);
};

test('living hologram schedules subsequent visible frames, preserves essential avatar motion and stops callbacks on detach',()=>{
  const callback=hologramSection('private val drawFrame = Choreographer.FrameCallback {','/** Owns the single visible-view frame callback');
  const schedule=hologramSection('private fun scheduleFrame() {','private val motionObserver');
  const detached=hologramSection('override fun onDetachedFromWindow() {','override fun onVisibilityAggregated(');
  assert.match(callback,/frameTimeNanos ->\s*framePending = false\s*if \(canAnimateFrame\(\)\) \{\s*if \(framePacing\.admit\(frameTimeNanos, renderBudget\(\)\.frameDelayMillis\)\) \{/);
  assert.match(callback,/advanceFrame\(frameTimeNanos \/ 1_000_000L\)[\s\S]*?postInvalidateOnAnimation\(\)\s*}\s*scheduleFrame\(\)\s*}\s*}\s*$/);
  assert.match(schedule,/if \(canAnimateFrame\(\) && !framePending\) \{\s*framePending = true[\s\S]*?choreographer\.postFrameCallbackDelayed\(drawFrame, delay\)\s*}\s*}\s*$/);
  assert.match(detached,/choreographer\.removeFrameCallback\(drawFrame\)\s*framePacing\.reset\(\)\s*framePending = false\s*lastFrameMs = 0L/);
  assert.match(detached,/lastFrameMs = 0L[\s\S]*?super\.onDetachedFromWindow\(\)\s*}\s*$/);
  assert.doesNotMatch(detached,/\bscheduleFrame\s*\(|\bpostFrameCallback(?:Delayed)?\s*\(/);
  assert.match(hologram,/AvatarEssentialMotionPolicy\.frameLoopAllowed\(/);
  assert.match(hologram,/JarvisComputePhasePolicy\.apply\(/);
  assert.match(essential,/budget\.animate \|\| avatarEnabled/);
  assert.match(essential,/frameLoopAllowed && budget\.animate/);
  assert.match(phasePolicy,/if \(!base\.animate\) return base/);
  assert.match(hologram,/override fun onVisibilityAggregated\(isVisible: Boolean\)/);
});
test('orbital rings use visible phase movement, not the old near-static factor',()=>{
  assert.match(ring,/val yaw = \(phase - \.4f\) \* 1\.25f \* speed/);
  assert.doesNotMatch(ring,/val yaw = \(phase - \.4f\) \* \.18f \* speed/);
});
test('header and the six orbital text labels are fitted to their real drawing boxes',()=>{
  assert.match(labels,/protected fun fittedTitle/);
  assert.match(labels,/ContinuumSurfaces\.fit\(p, title, titleStyle, available\)/);
  assert.match(labels,/val alignment = if \(mirrored\) TextAlign\.RIGHT else TextAlign\.LEFT/);
  assert.match(labels,/ContinuumSurfaces\.raisedControlCard\(p,/);
  assert.match(labels,/ContinuumSurfaces\.fit\(p, subtitle, style, w - 91f\)/);
});
test('battery, app CPU, RAM and GPU measurements remain honestly distinguished',()=>{
  assert.match(collector,/BatteryManager\.BATTERY_PROPERTY_CAPACITY/);
  assert.match(collector,/Process\.getElapsedCpuTime\(\)/);
  assert.match(collector,/getMemoryInfo\(info\)/);
  assert.match(gauges,/Triple\("Akku", telemetry\.batteryPercent/);
  assert.match(gauges,/Triple\("GPU", null,/);
  assert.match(gauges,/fun updateTelemetry\(next:/);
  assert.match(activity,/liveHardwareGauges\?\.updateTelemetry\(state\.system\)/);
});
test('CCC1 integrated in same owner-app source without changing private app identity',()=>{
  assert.match(ccc1,/internal object ContinuumCcc1Bridge/);
  assert.match(builderActivity,/ContinuumCcc1Bridge\.command\(operation, chain, orderId = requestId, taskId = task\)/);
  // Current owner build keeps the historical default and explicitly opts into 26+.
  assert.match(gradle,/versionCode = continuumOwnerLocalVersion \?: 25/);
  assert.match(gradle,/versionName = if \(continuumOwnerLocalVersion != null\) "0\.3\.\$\{continuumOwnerLocalVersion - 1\}" else "0\.3\.24"/);
  assert.match(gradle,/applicationIdSuffix = "\.mentorpaired"/);
});
test('existing on-device learning state and protected independent training are never reset by launch',()=>{
  assert.match(activity,/ContinuumDualCoreAutoStudy\.isEnabled\(this\)/);
  assert.match(collector,/ContinuumLearningCore\(files\)/);
  assert.match(collector,/ContinuumKnowledgeArchive\(files\)\.stats\(\)\.values\.sum\(\)/);
});
