import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const read = rel => readFileSync(path.join(root, rel), "utf8");
const gradle = read("app/build.gradle.kts");
const script = read("scripts/render-mentor-owner-update-v19.sh");
const manifest = read("app/src/main/AndroidManifest.xml");
const expectedSigner = "2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc";

test("owner version advances to 0.3.18/19 without a new application ID", () => {
  assert.match(gradle, /if \(deviceAiOwnerSignedUpdate\)\s*\{\s*versionCode = 19\s*versionName = "0\.3\.18"/);
  assert.match(gradle, /applicationId = "at\.heinz\.deviceai"/);
  assert.match(gradle, /applicationIdSuffix = "\.mentorpaired"/);
  assert.match(manifest, /android:enabled="\$\{mobileBuilderEnabled\}"/);
});

test("release uses existing signer, pinned source, and refuses dirty or unapproved code", () => {
  assert.ok(script.includes(expectedSigner));
  for (const phrase of [
    'CONTINUUM_RELEASE_SHA', 'CONTINUUM_ASTRA_UI_SHA',
    'signed_release_ref="astra/render-mentor-apk-builder-20261019"',
    'git -c core.fileMode=false diff --quiet HEAD --',
    'git hash-object -- "$source_path"', 'APPROVED_RELEASE_SOURCES',
    'CONTINUUM_MENTOR_SIGNING_P12_BASE64', 'CONTINUUM_MENTOR_SIGNING_PASSWORD'
  ]) assert.ok(script.includes(phrase), "Missing release gate: " + phrase);
});

test("release never replaces the old APK without byte-checked 0.3.17 rollback", () => {
  for (const marker of [
    'prior_code" == "18"', "versionCode='19'", 'version_name=0.3.18',
    'DeviceAI-Continuum-v0.3.17-$prior_sha.apk',
    "versionCode='18'", 'sha256sum --check --status',
    'apksigner', 'SIGNED_BUILD_VERIFIED_NOT_DEVICE_INSTALLED'
  ]) assert.ok(script.includes(marker), "Missing rollback or sign invariant: " + marker);
  assert.ok(!script.includes("CONTINUUM_MENTOR_SIGNING_PASSWORD=example"));
});

test("relevant UI and CCC1 source blobs are explicitly attested", () => {
  for (const rel of [
    "AgentActivity.kt", "ContinuumHologramView.kt", "ContinuumHologramRenderer.kt",
    "ContinuumOrbitalRing.kt", "ContinuumSystemGaugesView.kt", "ContinuumHomeElements.kt",
    "ContinuumDashboardCollector.kt", "ContinuumCcc1Bridge.kt", "app/build.gradle.kts"
  ]) assert.ok(script.includes(rel), "Missing source attestation: " + rel);
});
