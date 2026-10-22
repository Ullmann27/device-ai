import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const source = file => readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/', file), 'utf8');
const activity = source('ContinuumMobileBuilderActivity.kt');
const receiver = source('ContinuumMobileBuildResultReceiver.kt');
const coding = source('ContinuumMobileCodingJob.kt');
const plan = source('ContinuumMobileBuildPlan.kt');
const manifest = readFileSync(path.join(root, 'app/src/main/AndroidManifest.xml'), 'utf8');

test('coding job is owner-confirmed and arguments stay within the fixed Node/Termux plan', () => {
  assert.match(activity, /ContinuumMobileCodingJob\.newOrder\(/);
  assert.match(activity, /ContinuumMobileCodingJob\.problems\(order\)/);
  assert.match(activity, /\.setPositiveButton\("Auftrag starten"\) \{ _, _ -> dispatchCoding\(order\) \}/);
  assert.match(activity, /dispatchApprovedCommand\("CODE_TEST", order\.jobId, ContinuumMobileCodingJob\.command\(order\)\)/);
  assert.match(coding, /ContinuumMobileBuildPlan\.BUILDER_SCRIPT, "code-test"/);
  assert.match(coding, /ContinuumMobileBuildPlan\.NODE_EXECUTABLE/);
  assert.match(plan, /enum class Operation\([\s\S]*DOCTOR[\s\S]*BUILD[\s\S]*STATUS/);
  assert.doesNotMatch(activity, /ProcessBuilder\(|Runtime\.getRuntime\(|sh\s+-c/);
});

test('coding and existing builder operations share immutable callback identity and one owner lease', () => {
  assert.match(activity, /val pendingId = preferences\.getString/);
  assert.match(activity, /if \(pendingId != null\)/);
  assert.match(activity, /data = android\.net\.Uri\.parse\("continuum:\/\/termux-callback\/\$\{operationName\}\/\$requestId"\)/);
  assert.match(activity, /PendingIntent\.FLAG_ONE_SHOT or PendingIntent\.FLAG_MUTABLE/);
  assert.match(receiver, /operationName != prefs\.getString\(JOB_OPERATION, null\)/);
  assert.match(receiver, /callbackData\.pathSegments != listOf\(operationName, id\)/);
  assert.match(manifest, /android:name="\.ContinuumMobileBuildResultReceiver"[\s\S]*android:exported="false"/);
});

test('a coding result never enters the APK-build success policy or persists raw stdout', () => {
  assert.match(receiver, /codingOrder = operationName == "CODE_TEST"/);
  assert.match(receiver, /ContinuumMobileCodingJob\.evaluate\(\s*id, exitCode, errorCode, if \(truncated\) null else stdout/);
  assert.match(receiver, /\.putString\(LAST_MESSAGE, ContinuumMobileCodingJob\.explanation\(receipt\)\)/);
  assert.match(receiver, /operation = operation \?: return/);
  assert.doesNotMatch(receiver, /putString\(LAST_MESSAGE, stdout\)/);
  assert.match(coding, /Outcome\.PATCH_VERIFIED_PENDING_REVIEW/);
  assert.match(coding, /nicht übernommen und braucht deine Prüfung/);
});

test('existing build remains explicitly gated, and coding cannot install or merge', () => {
  assert.match(activity, /if \(operation == ContinuumMobileBuildPlan\.Operation\.BUILD\)/);
  assert.match(activity, /setPositiveButton\("Bauen"\) \{ _, _ -> dispatch\(operation\) \}/);
  assert.match(activity, /text = "← Zurück zu Device AI"/);
  assert.match(coding, /r\["approvedForMerge"\] != false/);
  assert.match(coding, /r\["installed"\] != false/);
  assert.match(coding, /r\["deviceVerified"\] != false/);
  assert.doesNotMatch(activity, /PackageInstaller|installPackage|adb\s+install/);
});
