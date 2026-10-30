#!/usr/bin/env node
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = file => readFileSync(new URL('../' + file, import.meta.url), 'utf8');
const activity = read('app/src/main/java/at/heinz/deviceai/ContinuumOwnerUpdateActivity.kt');
const agent = read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const manifest = read('app/src/main/AndroidManifest.xml');
const updater = read('app/src/main/java/at/heinz/deviceai/intelligence/ContinuumOwnerUpdater.kt');
const catalog = read('app/src/main/java/at/heinz/deviceai/intelligence/ContinuumOwnerUpdateCatalog.kt');
const policy = read('app/src/main/java/at/heinz/deviceai/intelligence/ContinuumSignedUpdatePolicy.kt');
const gradle = read('app/build.gradle.kts');

test('same original owner app, update activity in full production manifest', () => {
  assert.match(gradle, /at\.heinz\.deviceai\.continuumlocal/);
  assert.match(manifest, /android:name="\.ContinuumOwnerUpdateActivity"/);
  assert.match(manifest, /android:exported="false"/);
  assert.match(manifest, /android:name="\.ContinuumOwnerUpdateReceiver"/);
  assert.match(agent, /CenterAction\("UPDATES PRÜFEN UND LADEN", accent = true\)/);
  assert.match(agent, /startActivity\(Intent\(this, ContinuumOwnerUpdateActivity::class\.java\)\)/);
});

test('one owner tap checks then downloads: never requires a second in-app button', () => {
  assert.match(activity, /text = "Updates prüfen und laden"/);
  assert.match(activity, /setOnClickListener \{ checkForUpdate\(\) \}/);
  assert.match(activity, /updater\.check \{ result ->/);
  assert.match(activity, /is ContinuumOwnerUpdater\.Check\.Available -> \{[\s\S]*?downloadAndInstall\(check\.release\)/);
  assert.doesNotMatch(activity, /installButton|confirmUpdate\(|AlertDialog/);
  assert.match(activity, /updater\.downloadAndPrepare\(release,/);
  assert.match(activity, /checkButton\.isEnabled = false/);
});

test('update comes from pinned HTTPS and never bypasses Android installer', () => {
  assert.match(catalog, /const val HOST = "continuum-owner-v23-release-preflight\.onrender\.com"/);
  assert.match(catalog, /const val MANIFEST_PATH = "\/continuum-local\/latest\.json"/);
  assert.match(updater, /ContinuationOwnerUpdateReceiver|ContinuumOwnerUpdateReceiver/);
  assert.match(updater, /PackageInstaller\.SessionParams/);
  assert.match(updater, /session\.commit\(pending\.intentSender\)/);
  assert.match(updater, /context, id, callback,/);
  assert.match(policy, /ORIGINAL_SIGNER_SHA256/);
  assert.match(policy, /release\.explicitlyApproved/);
  assert.match(updater, /verifyArchive\(pending, release\)/);
  assert.match(updater, /installedSignerSha256\(\)/);
});

test('local release builder guards source, original certificate and interactive owner secret', () => {
  const build = read('scripts/continuum-fold7-standalone-apk.sh');
  assert.match(build, /git status --porcelain/);
  assert.match(build, /continuum-local\.p12/);
  assert.match(build, /CONTINUUM_LOCAL_SIGNING_PASSWORD/);
  assert.match(build, /read -r -s -p 'Lokales Signierpasswort: '/);
  assert.match(build, /:app:lintRelease :app:assembleRelease/);
  assert.match(build, /at\.heinz\.deviceai\.continuumlocal/);
  assert.match(build, /apksigner|\$signer/);
});
