import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const script = resolve(root, 'scripts/continuum-fold7-standalone-apk.sh');
const shell = readFileSync(script, 'utf8');
const gradle = readFileSync(resolve(root, 'app/build.gradle.kts'), 'utf8');
const manifest = readFileSync(resolve(root, 'app/src/main/AndroidManifest.xml'), 'utf8');
const route = readFileSync(resolve(root, 'app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderRoute.kt'), 'utf8');

test('local release script is syntactically valid Bash', () => {
  const result = spawnSync('bash', ['-n', script], {encoding: 'utf8', timeout: 10000});
  assert.equal(result.status, 0, result.stderr);
});

test('reject any unsupported command or versionCode before using private signing key', () => {
  for (const args of [[], ['owner'], ['build', '25'], ['build', 'not-a-number'], ['build', '10000']]) {
    const result = spawnSync('bash', [script, ...args], {
      encoding: 'utf8', timeout: 10000
    });
    assert.notEqual(result.status, 0, 'Invalid command must fail closed: ' + args.join(' '));
  }
});

test('new app ID is distinct from original and is stable across debug and release', () => {
  assert.match(gradle, /applicationId = if \(continuumLocalStandalone \|\| continuumLocalStandaloneQa \|\| continuumLocalRenderUnsigned\) "at\.heinz\.deviceai\.continuumlocal" else "at\.heinz\.deviceai"/);
  assert.match(gradle, /signingConfig = signingConfigs\.getByName\("continuumLocal"\)/);
  assert.match(gradle, /buildTypes\.getByName\("release"\)/);
  assert.equal(/applicationIdSuffix = "\.continuumlocal"/.test(gradle), false);
  assert.match(route, /LOCAL_MAINAPP_PACKAGE = "at\.heinz\.deviceai\.continuumlocal"/);
});

test('standalone build cannot reuse old owner credentials', () => {
  assert.match(gradle, /mentorSigningPath\.isNullOrBlank\(\) && mentorSigningPassword\.isNullOrBlank\(\)/);
  assert.match(gradle, /!continuumLocalSigningPath\.isNullOrBlank\(\)/);
  assert.match(gradle, /keyAlias = "continuum-local"/);
  assert.match(shell, /Do not load historic owner-signing credentials/);
  assert.match(shell, /-storepass:env CONTINUUM_LOCAL_SIGNING_PASSWORD/);
  assert.doesNotMatch(shell, /-genkeypair/);
});

test('new private signer stays outside repository and release uses it persistently', () => {
  assert.match(shell, /\.local\/share\/device-ai-continuum\/signing/);
  assert.match(shell, /chmod 600 "\$keyfile"/);
  assert.match(shell, /restore the P12 backup|restore backup/);
  assert.match(shell, /trusted_fp.*actual_fp/);
  assert.match(shell, /fingerprint.*keydir/);
  assert.match(shell, /version_name="0\.3\.\$\(\(version - 1\)\)"/);
});

test('release is compiled for real complete app not QA/demo or unsigned APK', () => {
  assert.match(shell, /:app:lintRelease :app:assembleRelease/);
  assert.match(shell, /continuum-fusion-full-regression\.sh "\$sha"/);
  assert.doesNotMatch(shell, /--tests /);
  assert.match(shell, /app\/build\/outputs\/apk\/release\/app-release\.apk/);
  assert.match(shell, /verify --verbose --print-certs/);
  assert.match(shell, /apk_fp.*actual_fp/);
  assert.match(shell, /versionCode='\$version' versionName='\$version_name'/);
  assert.match(shell, /LOCAL_RELEASE_SIGNED_NOT_DEVICE_INSTALLED/);
});

test('no installer, no app-data erasure, no arbitrary artifacts or source writes', () => {
  assert.equal(/\badb\s+(?:install|uninstall)|\bpm\s+(?:install|uninstall|clear)\b/.test(shell), false);
  assert.equal(/\b(?:rm -rf|git reset --hard|git clean -fdx)\b/.test(shell), false);
  assert.match(shell, /storage\/downloads\/Continuum_back_up/);
  assert.match(shell, /ALTE CONTINUUM-V25-APP NICHT DEINSTALLIEREN/);
  assert.match(manifest, /android:allowBackup="false"/);
});

test('complete app exposes local builder and separated data store', () => {
  assert.match(gradle, /deviceAiMentorPreview &&/);
  assert.match(gradle, /mobileBuilderEnabled/);
  assert.match(gradle, /continuumLocalStandalone -> "Continuum Lokal"/);
  assert.match(route, /currentApplicationId == LOCAL_MAINAPP_PACKAGE/);
});

test('established Continuum Lokal certificate cannot be replaced by a local fingerprint file', () => {
  assert.match(shell, /expected_fp='6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23'/);
  assert.match(shell, /actual_fp.*expected_fp/);
  assert.doesNotMatch(shell, /-genkeypair|Neues Signierpasswort/);
  assert.ok(shell.indexOf('continuum-fusion-full-regression.sh') < shell.indexOf('Lokales Signierpasswort:'));
  assert.match(shell, /env -u CONTINUUM_LOCAL_SIGNING_FILE -u CONTINUUM_LOCAL_SIGNING_PASSWORD/);
});
