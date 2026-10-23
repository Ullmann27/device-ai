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

test('living hologram schedules subsequent visible frames and stops callbacks on detach',()=>{
  assert.match(hologram,/private val drawFrame = Runnable \{[\s\S]*?invalidate\(\)[\s\S]*?scheduleFrame\(\)/);
  assert.match(hologram,/private fun scheduleFrame\(\)[\s\S]*?postOnAnimationDelayed\(drawFrame,/);
  assert.match(hologram,/override fun onDetachedFromWindow\(\) \{[\s\S]*?removeCallbacks\(drawFrame\)/);
  assert.match(hologram,/motionAllowed = animationsEnabled && ValueAnimator\.areAnimatorsEnabled\(\) && !powerManager\.isPowerSaveMode/);
  assert.match(hologram,/override fun onVisibilityAggregated\(isVisible: Boolean\)/);
});
test('orbital rings use visible phase movement, not the old near-static factor',()=>{
  assert.match(ring,/val yaw = \(phase - \.4f\) \* 1\.25f \* speed/);
  assert.doesNotMatch(ring,/val yaw = \(phase - \.4f\) \* \.18f \* speed/);
});
test('header and the six orbital text labels are fitted to their real drawing boxes',()=>{
  assert.match(labels,/protected fun fittedTitle/);
  assert.match(labels,/ContinuumSurfaces\.fit\(p, title, titleStyle, titleWidth\)/);
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
  assert.match(ccc1,/class ContinuumCcc1Bridge/);
  assert.match(activity,/ContinuumCcc1Bridge/);
  assert.match(gradle,/if \(deviceAiOwnerSignedUpdate\) \{\s*versionCode = 18\s*versionName = "0\.3\.17"/);
  assert.match(gradle,/applicationIdSuffix = "\.mentorpaired"/);
});
test('existing on-device learning state and protected independent training are never reset by launch',()=>{
  assert.match(activity,/ContinuumDualCoreAutoStudy\.isEnabled\(this\)/);
  assert.match(collector,/ContinuumLearningCore\(files\)/);
  assert.match(collector,/WebLearningJournal\(context\)\.notes\(\)\.size/);
});
