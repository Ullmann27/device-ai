import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('../../', import.meta.url)));
const read = path => readFileSync(resolve(root, path), 'utf8');
const pkg = 'app/src/main/java/at/heinz/deviceai/';
const ui = read(pkg + 'ContinuumTrainingMonitorActivity.kt');
const store = read(pkg + 'learning/ContinuumTrainingMonitor.kt');
const research = read(pkg + 'learning/ContinuumPublicStudyScheduler.kt');
const offline = read(pkg + 'learning/ContinuumAutonomousScheduler.kt');
const agent = read(pkg + 'AgentActivity.kt');
const manifest = read('app/src/main/AndroidManifest.xml');
const gradle = read('app/build.gradle.kts');

test('owner can open the native live monitor without exported Android access', () => {
  assert.match(agent, /TRAININGS-MONITOR · LIVE/);
  assert.match(agent, /Intent\(this@AgentActivity, ContinuumTrainingMonitorActivity::class\.java\)/);
  assert.match(manifest, /android:name="\.ContinuumTrainingMonitorActivity"\s+android:exported="false"/);
});

test('visualisation differentiates actual weight training, research and recent completion', () => {
  assert.match(ui, /training != null -> "TRAINIERT GERADE"/);
  assert.match(ui, /work != null -> "FORSCHT \/ PRÜFT GERADE"/);
  assert.match(ui, /justCompleted -> "TRAINING ABGESCHLOSSEN"/);
  assert.match(ui, /WARTET AUF NÄCHSTEN ZYKLUS/);
  assert.match(ui, /phaseName\(running\.phase\)/);
  assert.match(ui, /postDelayed\(this, 1_000L\)/);
});

test('training state is persisted by channel and stale starts are not shown as live', () => {
  assert.match(store, /const val PUBLIC = "public"/);
  assert.match(store, /const val OFFLINE = "offline"/);
  assert.match(store, /const val CDX = "cdx"/);
  assert.match(store, /RUNNING_TIMEOUT_MS = 45_000L/);
  assert.match(store, /phase == "TRAIN_WEIGHTS"/);
  assert.match(store, /p\.getLong\("elapsed", Long\.MAX_VALUE\) > SystemClock\.elapsedRealtime\(\)/);
});

test('public research records the real source and audit lifecycle and keeps the 15-minute plan', () => {
  assert.match(research, /ContinuumTrainingMonitor\.begin\(this@ContinuumPublicStudyJobService/);
  for (const state of ['FETCH_SOURCE','DISCOVER_TOPICS','ARCHIVE_SOURCE','AUDIT']) {
    assert.match(research, new RegExp('ContinuumTrainingMonitor\\.PUBLIC,"' + state + '"'));
  }
  assert.match(research, /observeTraining \{ trainingPhase ->/);
  assert.doesNotMatch(research, /ContinuumTrainingMonitor\.PUBLIC,"TRAIN_WEIGHTS"/);
  assert.match(research, /ContinuumTrainingMonitor\.end\(this@ContinuumPublicStudyJobService/);
  assert.match(research, /\.setPeriodic\(ContinuumPublicStudyPolicy\.PERIOD_MS\)/);
});

test('offline job records deferral, failures and interrupts without rewriting model weights', () => {
  assert.match(offline, /ContinuumTrainingMonitor\.begin\(this@ContinuumAutonomousJobService/);
  assert.match(offline, /"DEFERRED"/);
  assert.match(offline, /"FAILED"/);
  assert.match(offline, /"INTERRUPTED"/);
  assert.match(offline, /core\.bootstrapOfflineCurriculum\(cancelled\)/);
  assert.match(offline, /core\.bootstrapSemanticCurriculum\(cancelled\)/);
  assert.match(offline, /observeTraining \{ trainingPhase ->/);
  assert.doesNotMatch(offline, /ContinuumTrainingMonitor\.OFFLINE,"TRAIN_WEIGHTS"/);
});

test('CDX1 audits are separate and preview version increases without touching main', () => {
  assert.match(agent, /ContinuumTrainingMonitor\.CDX/);
  assert.match(agent, /if\(!report\.finalized\)"DEFERRED"/);
  assert.match(gradle, /versionCode = if \(deviceAiMentorPreview\) 9 else if \(deviceAiPreview\) 6 else 4/);
  assert.match(ui, /Android darf Jobs/);
});
