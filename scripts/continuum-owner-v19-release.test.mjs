import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const read = rel => readFileSync(path.join(root, rel), "utf8");
const gradle = read("app/build.gradle.kts");
const script = read("scripts/render-mentor-owner-update-v19.sh");
const nextScript = read("scripts/render-mentor-owner-update-v20.sh");
const manifest = read("app/src/main/AndroidManifest.xml");
const expectedSigner = "2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc";

test("owner version advances to 0.3.19/20 without a new application ID", () => {
  assert.match(gradle, /if \(deviceAiOwnerSignedUpdate\)\s*\{\s*versionCode = 20\s*versionName = "0\.3\.19"/);
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

test("owner release executes the optional Android widget and lifecycle test source set", () => {
  const commands = script.replace(/[ \t]*\\\r?\n[ \t]*/g, " ").split(/\r?\n/);
  const androidGate = commands.find(line => line.startsWith("gradle ") && line.includes(":app:testDebugUnitTest"));
  assert.ok(androidGate, "Missing executed Android unit-test gate");
  assert.ok(androidGate.includes("-PcontinuumUiWidgetTests=true"),
    "Owner release omits M5 and builder lifecycle widgets from its real test source set");
  assert.match(gradle, /if \(continuumUiWidgetTests\) sourceSets\.getByName\("test"\)\.java\.srcDir\("src\/uiTest\/java"\)/);
});

test("v20 uses identical owner certificate and requires 0.3.18 byte-valid rollback", () => {
  assert.ok(nextScript.includes(expectedSigner));
  for (const phrase of [
    "CONTINUUM_RELEASE_SHA", "CONTINUUM_ASTRA_UI_SHA",
    'signed_release_ref="astra/render-mentor-apk-builder-20261019"',
    "git hash-object -- \"$source_path\"",
    "APPROVED_RELEASE_SOURCES", "CONTINUUM_MENTOR_SIGNING_P12_BASE64",
    'prior_code" == "19"', "versionCode='20' versionName='0.3.19'",
    "DeviceAI-Continuum-v0.3.18-$prior_sha.apk",
    "versionCode='19' versionName='0.3.18'",
    "sha256sum --check --status", "SIGNED_BUILD_VERIFIED_NOT_DEVICE_INSTALLED"
  ]) assert.ok(nextScript.includes(phrase), "Missing v20 gate: " + phrase);
  assert.ok(!nextScript.includes("CONTINUUM_MENTOR_SIGNING_PASSWORD=example"));
});

test("v20 attests all prior thirty-two sources and pixel-motion/widget tests", () => {
  const rows=nextScript.match(/done <<'APPROVED_RELEASE_SOURCES'\n([\s\S]*?)\nAPPROVED_RELEASE_SOURCES/);
  assert.ok(rows, "No v20 source attestation table");
  const lines=rows[1].trim().split(/\r?\n/);
  assert.equal(lines.length,34);
  assert.ok(lines.every(l=>/^[a-f0-9]{40} \S+$/.test(l)));
  assert.ok(nextScript.includes("app/src/uiTest/java/at/heinz/deviceai/ContinuumHologramMotionUiTest.kt"));
  assert.ok(nextScript.includes(".github/continuum/continuum-hologram-context-wiring.test.mjs"));
  const commands=nextScript.replace(/[ \t]*\\\r?\n[ \t]*/g," ").split(/\r?\n/);
  const gradleGate=commands.find(line=>line.startsWith("gradle ")&&line.includes(":app:testDebugUnitTest"));
  assert.ok(gradleGate?.includes("-PcontinuumUiWidgetTests=true"));
});
