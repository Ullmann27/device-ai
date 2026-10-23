import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const source = relative => readFileSync(path.join(root, relative), 'utf8');
const manifest = source('app/src/main/AndroidManifest.xml');
const gradle = source('app/build.gradle.kts');
const activity = source('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt');
const plan = source('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuildPlan.kt');

test('requires owner grant for Termux command permission', () => {
  assert.match(manifest, /<uses-permission android:name="com\.termux\.permission\.RUN_COMMAND"\s*\/>/);
  assert.match(activity, /checkSelfPermission\(ContinuumMobileBuildPlan\.TERMUX_PERMISSION\)/);
  assert.match(activity, /requestPermissions\(/);
});
test('local build launcher is preview-gated, production disabled', () => {
  assert.match(manifest, /android:name="\.ContinuumMobileBuilderActivity"[\s\S]*?android:enabled="\$\{mobileBuilderEnabled\}"/);
  assert.match(gradle, /manifestPlaceholders\["mobileBuilderEnabled"\] = deviceAiPreview \|\| deviceAiMentorPreview/);
});
test('fixed Termux command entrypoint with no raw chat or shell', () => {
  assert.match(plan, /NODE_EXECUTABLE = "\/data\/data\/com\.termux\/files\/usr\/bin\/node"/);
  assert.match(plan, /BUILDER_SCRIPT = "\$PROJECT_DIR\/scripts\/continuum-mobile-builder\.mjs"/);
  assert.match(plan, /enum class Operation\([\s\S]*DOCTOR[\s\S]*BUILD[\s\S]*STATUS/);
  assert.doesNotMatch(activity, /(?:Runtime\.getRuntime\(|ProcessBuilder\(|su\s|sh\s-c)/);
  assert.match(activity, /putExtra\("com\.termux\.RUN_COMMAND_PATH", plan\.executable\)/);
  assert.match(activity, /putExtra\("com\.termux\.RUN_COMMAND_ARGUMENTS", plan\.args\.toTypedArray\(\)\)/);
});
test('requires explicit owner confirmation before expensive build', () => {
  assert.match(activity, /val confirmation = when \(operation\)[\s\S]*Operation\.BUILD ->/);
  assert.match(activity, /if \(confirmation != null\)[\s\S]*setPositiveButton\("Bestätigen"\) \{ _, _ -> dispatch\(operation\) \}/);
  assert.match(activity, /startService\(intent\)/);
});
test('does not claim successful build or installation just for dispatching', () => {
  assert.match(activity, /kein Build-Erfolg bestätigt|kein Erfolg bestätigt|Kein bestätigter Erfolg/);
  assert.doesNotMatch(activity, /PackageInstaller|installPackage|adb\s+install/);
  assert.match(plan, /if \(operation == Operation\.BUILD\)/);
  assert.match(plan, /args\.add\("--project"\)/);
});
