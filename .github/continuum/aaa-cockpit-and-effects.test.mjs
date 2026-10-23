import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const kotlin = (relative) => fs.readFileSync(path.join(root,relative),'utf8');
const activity = kotlin('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const cockpit = kotlin('app/src/main/java/at/heinz/deviceai/ui/ContinuumBentoCockpit.kt');
const effects = kotlin('app/src/main/java/at/heinz/deviceai/ui/ContinuumSciFiEffects.kt');
const home = kotlin('app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeView.kt');
const system = kotlin('app/src/main/java/at/heinz/deviceai/ui/ContinuumSystemGaugesView.kt');
const collector = kotlin('app/src/main/java/at/heinz/deviceai/ContinuumDashboardCollector.kt');
const phone = kotlin('app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeElements.kt');
const wide = kotlin('app/src/main/java/at/heinz/deviceai/ui/ContinuumWideElements.kt');
const state = kotlin('app/src/main/java/at/heinz/deviceai/ui/ContinuumDashboardState.kt');

test('no new Flutter dependency and existing native layout remains authoritative', () => {
  assert.match(home, /class ContinuumHomeView/);
  assert.match(home, /ContinuumHomeLayout\.phone/);
  assert.match(home, /ContinuumHomeLayout\.wide/);
  assert.doesNotMatch(effects, /BackdropFilter|RenderEffect\.createBlurEffect|BlurMaskFilter/);
  assert.doesNotMatch(cockpit, /Flutter|hardcoded.*98%/i);
});
test('scanline effect runs in draw pass, never as an input-intercepting view', () => {
  assert.match(effects, /fun drawScanlines\(canvas: Canvas, width: Float, height: Float\)/);
  assert.match(home, /override fun dispatchDraw\(canvas: Canvas\)/);
  assert.match(home, /super\.dispatchDraw\(canvas\)/);
  assert.match(home, /sciFiEffects\.drawScanlines\(canvas/);
  assert.match(cockpit, /sciFiEffects\.drawScanlines\(canvas/);
  assert.doesNotMatch(effects, /setOnTouchListener|setOnClickListener/);
});
test('background glows behind live cards, do not obscure labels or inflate telemetry', () => {
  assert.match(home, /override fun onDraw\(canvas: Canvas\)/);
  assert.match(home, /sciFiEffects\.drawAmbient\(canvas/);
  assert.match(effects, /RadialGradient/);
  assert.doesNotMatch(effects, /92%|78%|98%|247|432 GB|\bCanvas\.saveLayer\b/);
});
test('all existing cockpit destinations remain connected to owner callbacks', () => {
  for (const center of ['showLearningCenter','showDevelopmentCenter','showMemoryCenter','showSystemCenter']) {
    assert.match(activity, new RegExp(center + '\\('));
  }
  assert.match(activity, /cockpit\.action\(this, action\.label/);
  assert.match(activity, /action\.run\(\)/);
  assert.match(activity, /private fun showResearchModeDialog/);
  assert.match(activity, /private fun showContinuumLearningDashboardDialog/);
  assert.match(cockpit, /fun formattedStatus\(raw: String\): String/);
});

test('system gauges show only measured values and explain unknown GPU', () => {
  assert.match(system, /telemetry\.appCpuPercent/);
  assert.match(system, /telemetry\.memoryPercent/);
  assert.match(system, /telemetry\.storagePercent/);
  assert.match(system, /Triple\("GPU", null/);
  assert.match(system, /contentDescription = describeTelemetry\(\)/);
  assert.match(system, /private fun describeTelemetry\(\): String = values\.joinToString/);
  assert.match(activity, /ContinuumSystemGaugesView\(this, systemSnapshot\)/);
  assert.match(activity, /liveHardwareGauges\?\.updateTelemetry\(state\.system\)/);
  assert.match(activity, /App-CPU statt Gesamt-CPU/);
});
test('device storage legend does not present unidentified data as measured system bytes', () => {
  assert.match(collector, /Übrige Gerätebelegung/);
  assert.match(collector, /App: eigene Dateien/);
  assert.match(collector, /App: Wissensquellen/);
  assert.match(collector, /App: Projektdateien/);
  assert.doesNotMatch(collector, /ContinuumDashboardState\.Segment\("System", other/);
});
test('positive measured event deltas use green in both form factors', () => {
  for (const cards of [phone, wide]) {
    assert.match(cards, /if \(change > 0\) ContinuumTheme\.GREEN/);
    assert.match(cards, /if \(change < 0\) ContinuumTheme\.RED/);
  }
});

test('both phone and Fold storage cards show compact legend without losing measured category names', () => {
  assert.match(state, /fun compactStorageLabel\(label: String\)/);
  for (const original of ['App: eigene Dateien','App: Wissensquellen','App: Projektdateien','Übrige Gerätebelegung']) {
    assert.ok(state.includes(original), 'full storage category is retained: ' + original);
  }
  for (const cards of [phone, wide]) assert.match(cards, /ContinuumDashboardState\.compactStorageLabel\(seg\.label\)/);
  assert.match(phone, /segments\.joinToString/);
});
