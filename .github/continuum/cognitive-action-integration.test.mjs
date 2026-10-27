import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const root = fileURLToPath(new URL('../..', import.meta.url));
const source = name => fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai', name), 'utf8');

test('Android source routes bootstrap actions through the existing cognitive agent (source wiring only)', () => {
  const activity = source('AgentActivity.kt');
  const action = activity.slice(activity.indexOf('private fun executeBootstrapReadOnly('),
    activity.indexOf('private fun confirmBootstrapTraining('));
  assert.match(action, /BoundedAgentOrchestrator\(/);
  assert.match(action, /ReadOnlyToolPlan/);
  assert.match(action, /memoryStore\.all\(\) \+ ownerFactMemories\(\)/);
  assert.match(action, /experienceStore = experienceStore/);
  assert.match(action, /checkpointContinuumAsync\(\)/);
  assert.match(action, /sessionId != actionSessionId/);
  assert.doesNotMatch(action, /gate\.execute\(/, 'bootstrap must not retain a competing execution path');
  const executor = source('DeviceToolExecutor.kt');
  const probe = executor.indexOf('DeviceStorageProbe.read(context.filesDir)');
  assert.ok(probe > 0 && probe < executor.indexOf('if (!bridge.isReady())'));
  assert.match(activity, /decision\.tool != "get_storage_info" && !bridge\.isReady\(\)/);
});

const deps = process.env.CONTINUUM_FABRIC_JVM_DEPS;
test('actual Android shell adapters compile against official APIs and the unchanged Binder contract', {
  skip: deps && process.env.CONTINUUM_SHELL_ANDROID_DEPS ? false : 'NOT_RUN: configure official Kotlin and Shizuku/annotation jars',
  timeout: 120_000
}, t => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'continuum-shell-compile-'));
  try {
    const result = spawnSync('bash', [path.join(root, 'scripts/continuum-shell-android-compile.sh'), out], {
      cwd: root, env: process.env, encoding: 'utf8', timeout: 110_000, maxBuffer: 1024 * 1024
    });
    const log = ['javac.txt', 'compile.txt'].map(name => fs.existsSync(path.join(out, name)) ?
      fs.readFileSync(path.join(out, name), 'utf8') : '').join('\n');
    assert.equal(result.error, undefined, result.error?.message);
    assert.equal(result.status, 0, (result.stdout + result.stderr + log).slice(-8_000));
    assert.match(result.stdout, /ANDROID_SHELL_ADAPTER_COMPILE_PASS/);
    t.diagnostic(result.stdout.trim());
  } finally { fs.rmSync(out, {recursive:true,force:true}); }
});

test('native natural request → actual tool → receipt → persisted episode, with negative regressions', {
  skip: deps ? false : 'NOT_RUN: set CONTINUUM_FABRIC_JVM_DEPS to run the real Kotlin graph',
  timeout: 120_000
}, t => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'continuum-cognitive-jvm-'));
  try {
    const result = spawnSync('bash', [path.join(root, 'scripts/continuum-cognitive-action-jvm.sh'), out], {
      cwd: root, env: process.env, encoding: 'utf8', timeout: 110_000, maxBuffer: 1024 * 1024
    });
    const compiler = path.join(out, 'compile.txt');
    assert.equal(result.error, undefined, result.error?.message);
    assert.equal(result.status, 0, (result.stdout + result.stderr +
      (fs.existsSync(compiler) ? fs.readFileSync(compiler, 'utf8') : '')).slice(-8_000));
    assert.match(result.stdout, /OK \([1-9]\d* tests\)/);
    const receipt = fs.readFileSync(path.join(out, 'action-receipt.txt'), 'utf8');
    assert.match(receipt, /state=SUCCEEDED/);
    assert.match(receipt, /source=tool-call:native-storage-e2e-1:sha256:[a-f0-9]{64}/);
    assert.match(receipt, /executions=1\npersistedAfterRestart=true\nownerFactsUnchanged=true\nweightsUpdated=false/);
    t.diagnostic(result.stdout.trim().replaceAll('\n', ' '));
    t.diagnostic(receipt.trim().replaceAll('\n', ' | '));
  } finally { fs.rmSync(out, {recursive: true, force: true}); }
});
