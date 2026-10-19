import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = name => readFileSync(path.join(root, name), 'utf8');
const base = 'app/src/main/java/at/heinz/deviceai/';
const activity = read(base + 'ContinuumMobileBuilderActivity.kt');
const receiver = read(base + 'ContinuumMobileBuildResultReceiver.kt');
const policy = read(base + 'ContinuumMobileResultPolicy.kt');
const manifest = read('app/src/main/AndroidManifest.xml');
const junit = read('app/src/test/java/at/heinz/deviceai/ContinuumMobileResultPolicyTest.kt');

test('callback target is explicit, preview-only and not exported', () => {
  assert.match(manifest, /android:name="\.ContinuumMobileBuildResultReceiver"[\s\S]*?android:enabled="\$\{mobileBuilderEnabled\}"[\s\S]*?android:exported="false"/);
  assert.match(activity, /Intent\(this, ContinuumMobileBuildResultReceiver::class\.java\)/);
  assert.match(activity, /PendingIntent\.FLAG_ONE_SHOT or PendingIntent\.FLAG_MUTABLE/);
  assert.match(activity, /putExtra\(ContinuumMobileBuildResultReceiver\.COMMAND_PENDING_INTENT, callback\)/);
  assert.match(receiver, /COMMAND_PENDING_INTENT = "com\.termux\.RUN_COMMAND_PENDING_INTENT"/);
});

test('only one local job can be outstanding; callback binds id and operation', () => {
  assert.match(activity, /val pendingId = preferences\.getString/);
  assert.match(activity, /if \(pendingId != null\)/);
  assert.match(activity, /UUID\.randomUUID\(\)\.toString\(\)/);
  assert.match(receiver, /id != prefs\.getString\(JOB_ID, null\)/);
  assert.match(receiver, /operationName != prefs\.getString\(JOB_OPERATION, null\)/);
});

test('result requires official Termux bundle including real exit and error', () => {
  assert.match(receiver, /const val RESULT_BUNDLE = "result"/);
  assert.match(receiver, /bundle\.containsKey\("exitCode"\)/);
  assert.match(receiver, /bundle\.containsKey\("err"\)/);
  assert.match(receiver, /bundle\?\.getString\("stdout"\)/);
  assert.match(policy, /exitCode == null \|\| termuxError != -1/);
  assert.match(policy, /apkSha256 != null && Regex\("\[0-9a-f\]\{64\}"\)\.matches\(apkSha256\)/);
  assert.match(policy, /signerSha256 != null && Regex\("\[0-9a-f\]\{64\}"\)\.matches\(signerSha256\)/);
});

test('user receives a receipt only, no installation or private build log persisted', () => {
  assert.match(activity, /"com\.termux\.RUN_COMMAND_BACKGROUND", true/);
  assert.match(receiver, /putString\(LAST_MESSAGE, text\)/);
  assert.doesNotMatch(receiver, /putString\(LAST_MESSAGE, stdout\)|putString\(LAST_MESSAGE, bundle/);
  assert.doesNotMatch(activity, /installPackage|PackageInstaller|Runtime\.getRuntime\(\)/);
  assert.match(junit, /fun buildNeedsSignatureEvidenceAndCompletedTermuxStatus/);
});

test('live Android APK build still requires independent device check', () => {
  assert.match(policy, /BUILD_REPORTED_UNVERIFIED_ON_DEVICE/);
  assert.match(activity, /kein Build-Erfolg bestätigt/);
  assert.match(receiver, /Outcome\.BUILD_REPORTED_UNVERIFIED_ON_DEVICE/);
});
