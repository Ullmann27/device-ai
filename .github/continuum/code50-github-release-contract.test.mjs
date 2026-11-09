import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// Code51 channel continuation; registered workflow/script paths retain their historical Code50 names.
// Diese Prüfungen belegen nur statische Eigenschaften der Dateien. Sie belegen weder einen
// erfolgreichen Workflow-Lauf noch eine Signierung noch Besitzerfreigaben.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const qa = read('.github/workflows/continuum-code50-github-qa.yml');
const sign = read('.github/workflows/continuum-code50-sign-release.yml');
const script = read('scripts/continuum-code50-github-signer.sh');
const checker = read('scripts/continuum-code51-junit-check.py');
const branch = 'copilot/code51-master-integrator-20261108';
const fingerprint = '6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23';

const jobBlock = (text, name) => {
  const match = text.match(new RegExp(`^  ${name}:\\n([\\s\\S]*?)(?=^  [A-Za-z0-9_-]+:\\n|(?![\\s\\S]))`, 'm'));
  assert.ok(match, `job ${name} fehlt`);
  return match[1];
};

test('QA-Workflow startet nur manuell, ist SHA- und Branch-gebunden und hat keinen Signier-/Geheimniszugriff', () => {
  assert.match(qa, /^on:\n  workflow_dispatch:\n    inputs:\n      expected_sha:/m);
  assert.doesNotMatch(qa, /^\s+(push|pull_request|pull_request_target|schedule|workflow_run):/m);
  assert.match(qa, /^permissions:\n  contents: read\n/m);
  assert.doesNotMatch(qa, /secrets\./);
  assert.doesNotMatch(qa, /^\s+environment:/m);
  assert.doesNotMatch(qa, /apksigner sign|zipalign|keytool|gh release create/);
  for (const job of ['node-backend', 'android-host']) {
    const block = jobBlock(qa, job);
    assert.ok(block.includes(`github.ref == 'refs/heads/${branch}'`), `${job}: Branch-Guard`);
    assert.ok(block.includes('test "$EXPECTED_SHA" = "$GITHUB_SHA"'), `${job}: expected_sha-Bindung`);
  }
  assert.match(checker, /EXPECTED_TESTS = 4197/);
  assert.match(qa, /name: Code51-UNSIGNED-NOT-INSTALLABLE/);
  assert.match(checker, /tests_skipped=/);
  assert.match(qa, /continuum-code51-junit-check\.py/);
  assert.match(qa, /code50_apk_sha256=/);
  assert.match(qa, /native-code: 'arm64-v8a'/);
});

test('JDK21 lifecycle, full widget suite and lint are required in the Android job', () => {
  const block = jobBlock(qa, 'android-host');
  assert.doesNotMatch(qa, /continue-on-error/);
  assert.match(block, /java-version: "21"/);
  assert.match(checker, /LocalModelReloadLifecycleTest/);
  assert.doesNotMatch(checker.split('REQUIRED =')[0], /LocalModelReloadLifecycleTest/);
  assert.match(block, /:app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug/);
  assert.match(block, /-PcontinuumUiWidgetTests=true/);
  assert.doesNotMatch(block, /--tests | -x /);
});

test('Signier-Workflow: nur manuell, Preflight ohne Geheimnisse, Geheimnisse nur im geschützten Job', () => {
  assert.match(sign, /^on:\n  workflow_dispatch:\n    inputs:\n/m);
  assert.doesNotMatch(sign, /^\s+(push|pull_request|pull_request_target|schedule|workflow_run):/m);
  for (const input of ['source_sha', 'qa_run_id', 'owner_approved_sha']) {
    assert.match(sign, new RegExp(`      ${input}:\\n        description: [^\\n]+\\n        required: true\\n        type: string`));
  }
  const preflight = jobBlock(sign, 'preflight');
  const release = jobBlock(sign, 'release-original-signed');
  assert.doesNotMatch(preflight, /secrets\.|environment:/);
  assert.match(preflight, /continuum-code50-github-signer\.sh preflight/);
  assert.match(release, /needs: preflight/);
  assert.match(release, /environment: continuum-original-signing/);
  assert.match(release, /secrets\.CONTINUUM_LOCAL_SIGNING_P12_BASE64/);
  assert.match(release, /secrets\.CONTINUUM_LOCAL_SIGNING_PASSWORD/);
  assert.match(release, /continuum-code50-github-signer\.sh sign/);
  assert.equal([...sign.matchAll(/secrets\./g)].length, 2);
  assert.match(sign, /^permissions:\n  contents: read\n/m);
  assert.match(release, /permissions:\n      contents: write\n      actions: read/);
  assert.ok(sign.includes(`github.ref == 'refs/heads/${branch}'`));
  assert.doesNotMatch(sign, /render/i);
  // Eingaben nur als env-Zuordnung (kein Skript-Injection-Pfad), nie direkt im run-Text.
  for (const line of sign.split('\n').filter(l => l.includes('${{ inputs.'))) {
    assert.match(line, /^\s+(SOURCE_SHA|QA_RUN_ID|OWNER_APPROVED_SHA): \$\{\{ inputs\.[a-z_]+ \}\}$/, line);
  }
});

test('Signierskript: feste Identität und Fail-Closed-Gates', () => {
  assert.match(script, /^set -Eeuo pipefail\nset \+x\n/m);
  assert.ok(script.includes(`fingerprint="${fingerprint}"`));
  assert.ok(script.includes('pkg="at.heinz.deviceai.continuumlocal"'));
  assert.ok(script.includes('code="51"'));
  assert.ok(script.includes('name_version="0.3.50"'));
  assert.ok(script.includes(`branch="${branch}"`));
  assert.ok(script.includes('tag="continuum-local-code${code}-${source_sha}"'));
  for (const gate of [
    'ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING', 'ENVIRONMENT_BRANCH_POLICY_NOT_EXACT',
    'APPROVED_SHA_DIFFERS_FROM_SOURCE', 'SOURCE_DIFFERS_FROM_RUN_COMMIT', 'QA_IDENTITY_OR_RESULT_MISMATCH',
    'NODE_BACKEND_NOT_GREEN', 'ANDROID_HOST_NOT_GREEN', 'RELEASE_ALREADY_EXISTS', 'NOT_HIGHER_THAN_LATEST_RELEASE',
    'UNSIGNED_APK_UNEXPECTEDLY_SIGNED', 'ORIGINAL_CERTIFICATE_MISMATCH', 'SIGNED_APK_CERTIFICATE_MISMATCH',
    'GITHUB_READBACK_SHA_MISMATCH', 'PUBLIC_LATEST_METADATA_MISMATCH', 'PUBLIC_DOWNLOAD_SHA_MISMATCH'
  ]) assert.ok(script.includes(gate), gate);
  assert.ok(script.includes('"tests_total=4197" "tests_failed=0" "tests_errors=0" "tests_skipped=18" "lifecycle_jdk21=PASS"'));
  assert.ok(script.includes('CODE50_BASELINE_CHANGED'));
  assert.ok(script.includes('LATEST_RELEASE_UNREADABLE'));
  assert.ok(script.includes('.event=="workflow_dispatch"'));
  // Preflight läuft vor jeder Schlüsselberührung, Veröffentlichung erst nach dem Entwurfs-Readback.
  assert.ok(script.indexOf('ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING') < script.indexOf('ORIGINAL_P12_BASE64_INVALID'));
  assert.ok(script.indexOf('--draft --latest=false') < script.indexOf('GITHUB_READBACK_SHA_MISMATCH'));
  assert.ok(script.indexOf('GITHUB_READBACK_SHA_MISMATCH') < script.indexOf('--draft=false --latest=true'));
  assert.ok(script.indexOf('--draft=false --latest=true') < script.indexOf('PUBLIC_DOWNLOAD_SHA_MISMATCH'));
  assert.match(script, /rollback\(\) \{ gh release edit "\$tag" --repo "\$repo" --draft=true/);
});

test('Signierskript gibt Schlüsselmaterial nie aus und umgeht keine Gates', () => {
  assert.doesNotMatch(script, /set -x|set -o xtrace|bash -x/);
  // Einzige erlaubte Verwendung: Dekodieren in eine 0600-Datei, nie in die Protokollausgabe.
  const decode = 'printf \'%s\' "$ORIGINAL_P12" | base64 --decode > "$tmp/original.p12"';
  assert.equal(script.split(decode).length, 2);
  assert.doesNotMatch(script.replace(decode, ''), /\b(echo|printf|cat|tee)\b[^\n]*\$\{?(ORIGINAL_P12|ORIGINAL_PASSWORD|SIGN_PASS)\b/);
  assert.doesNotMatch(script, /--ks-pass pass:|--key-pass pass:|-storepass [^:]/);
  assert.doesNotMatch(script, /--force|push -f|curl[^\n]*(-X|--request) (DELETE|POST)/);
  assert.doesNotMatch(script, /git (merge|push|tag)/);
  assert.doesNotMatch(script, /\|\| true\n[^\n]*(sign|publish)/);
});

test('Code-50-Besitzerausnahme ist in AGENTS.md und CLAUDE.md eng begrenzt dokumentiert', () => {
  for (const f of ['AGENTS.md', 'CLAUDE.md']) {
    const text = read(f);
    assert.match(text, /BESITZER-AUSNAHME NUR FÜR CODE 50 \(08\.11\.2026\)/, f);
    assert.match(text, /nicht aufgehoben|hebt \*\*nicht\*\* auf/, f);
    assert.match(text, /Kanalwahl ist keine Signaturfreigabe/, f);
    assert.match(text, /bleibt für alle \*\*anderen\*\* Aufgaben unverändert gültig/, f);
  }
});
