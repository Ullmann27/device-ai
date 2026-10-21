import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=(relative)=>fs.readFileSync(path.join(root,relative),'utf8');
const learning='app/src/main/java/at/heinz/deviceai/learning/';
const app='app/src/main/java/at/heinz/deviceai/AgentActivity.kt';
const worker=read(learning+'ContinuumAutonomousScheduler.kt');
const policy=read(learning+'ContinuumDualCoreAutoStudy.kt');
const bridge=read(learning+'ContinuumDecoderBridge.kt');
const binding=read(learning+'ContinuumPairedEncoderBinding.kt');
const ui=read(app);

test('CDX1 is strictly opt-in and bounded by existing JobScheduler budget',()=>{
  assert.match(policy,/getBoolean\(OWNER_OPT_IN,\s*false\)/);
  assert.match(policy,/MIN_REMAINING_MILLIS\s*=\s*6_500L/);
  assert.match(policy,/remainingMillis\s*>=\s*MIN_REMAINING_MILLIS/);
  assert.match(worker,/ContinuumDualCoreAutoStudy\.isEnabled/);
  assert.match(worker,/ContinuumDualCoreAutoStudy\.shouldStart/);
  assert.match(worker,/ContinuumOfflineLearningBudget\.MAX_WALL_MILLIS/);
  assert.match(worker,/ContinuumCdxLargeLab\(filesDir\)\.status/);
  assert.match(worker,/lab\.next\s*\{/);
});

test('The UI presents explicit enable and disable actions',()=>{
  assert.match(ui,/CDX1 · HINTERGRUND-ÜBEN · EIN\/AUS/);
  assert.match(ui,/setEnabled\(this,false\)/);
  assert.match(ui,/setEnabled\(this,true\)/);
  assert.match(ui,/ContinuumAutonomousScheduler\(this\)\.restore/);
});

test('Research-only CDX1 events never authorize productive chat',()=>{
  assert.match(worker,/ContinuumTrainingMonitor\.CDX/);
  assert.match(worker,/round\.accepted -> "ACCEPTED"/);
  assert.match(worker,/!round\.finalized -> "DEFERRED"/);
  assert.match(bridge,/private val pairedBinding/);
  assert.match(bridge,/pairedEncoderSnapshot=binding\.frozenEncoder \?: encoderSnapshot/);
  assert.match(bridge,/if\(binding\.state==ContinuumPairedEncoderBinding\.State\.CORRUPT\)return null/);
  assert.match(binding,/LearningSnapshot\.decode/);
  assert.match(binding,/ATOMIC_MOVE/);
  assert.match(binding,/Files\.isSymbolicLink/);
});

test('The CDX1 auto-study policy does not introduce tools, network or separate schedules',()=>{
  assert.doesNotMatch(policy,/https?:|WebView|Runtime\.getRuntime|ProcessBuilder|Shell|Intent\(/);
  assert.doesNotMatch(worker,/setPeriodic\(.*CDX|schedule\(.*CDX/);
});
