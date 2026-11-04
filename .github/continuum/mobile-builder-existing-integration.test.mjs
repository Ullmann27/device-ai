import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = p => readFileSync(path.join(root, p), 'utf8');
const src = 'app/src/main/java/at/heinz/deviceai/';
const router = read(src + 'ContinuumWorkerChatRouter.kt');
const agent = read(src + 'AgentActivity.kt');
const bridge = read(src + 'ShizukuBridge.kt');
const executor = read(src + 'DeviceToolExecutor.kt');
const routerTest = read('app/src/test/java/at/heinz/deviceai/ContinuumWorkerChatRouterTest.kt');

test('existing Shizuku and device tools stay available (no replacement with parallel toolgate)', () => {
  assert.match(bridge, /class ShizukuBridge/);
  assert.match(executor, /class DeviceToolExecutor/);
  assert.match(executor, /"install_apk" ->/);
  assert.match(agent, /private lateinit var bridge: ShizukuBridge/);
  assert.match(agent, /executor = DeviceToolExecutor\(this, bridge\)/);
});

test('mobile builder is added to existing worker chat routing', () => {
  assert.match(router, /data object MobileBuilder : ContinuumWorkerChatCommand\(\)/);
  assert.match(router, /"worker builder"/);
  assert.match(router, /"worker termux"/);
  assert.match(agent, /ContinuumWorkerChatCommand\.MobileBuilder -> openMobileBuilder\(\)/);
  assert.match(routerTest, /mobileBuilderNavigatesInsideExistingWorkerChat/);
});

test('builder stays in the existing agent UI and opens only an allowlisted Android activity', () => {
  assert.match(agent, /CenterAction\("HANDY-BUILDER · TERMUX"\) \{ openMobileBuilder\(\) \}/);
  // Stable category targets replace brittle dialog indices; the actual dispatcher must stay connected.
  const nav = read(src + 'ContinuumWorkAreaNavigationSpec.kt');
  assert.match(nav, /CategoryEntry\("Handy-Builder · Termux", "mobile-builder"\)/);
  const more = agent.slice(agent.indexOf('private fun showMoreWorkAreas()'), agent.indexOf('private fun showBrainScene()'));
  assert.match(more, /showDashboardCategory\("Mehr", ContinuumWorkAreaNavigationSpec\.moreEntries\)/);
  assert.match(more, /setItems\(entries\.map \{ it\.title \}\.toTypedArray\(\)\)/);
  assert.match(more, /navigateDashboardTarget\(entries\[index\]\.target\)/);
  assert.match(more, /"mobile-builder" -> openMobileBuilder\(\)/);
  assert.match(agent, /ContinuumMobileBuilderRoute\.destination\(BuildConfig\.APPLICATION_ID\)/);
  assert.match(agent, /Intent\(\)\.setClassName\(destination\.packageName, destination\.className\)/);
  assert.match(agent, /catch \(_:\s*ActivityNotFoundException\)/);
  assert.match(agent, /catch \(_:\s*SecurityException\)/);
  assert.doesNotMatch(agent, /startActivity\(Intent\(this, ContinuumMobileBuilderActivity::class\.java\)\)/);
});

test('primary AI dashboard remains the only Android launcher, preview builder stays owner-operated', () => {
  const manifest = read('app/src/main/AndroidManifest.xml');
  const gradle = read('app/build.gradle.kts');
  const route = read(src + 'ContinuumMobileBuilderRoute.kt');
  assert.equal((manifest.match(/android\.intent\.category\.LAUNCHER/g) || []).length, 1);
  assert.match(manifest, /android:name="\.AgentActivity"[\s\S]*android\.intent\.category\.LAUNCHER/);
  assert.match(manifest, /android:name="\.ContinuumMobileBuilderActivity"[\s\S]*android:enabled="\$\{mobileBuilderEnabled\}"/);
  assert.match(manifest, /android:name="\.ContinuumMobileBuilderActivity"[\s\S]*android:exported="true"/);
  assert.match(gradle, /applicationIdSuffix = "\.preview"/);
  assert.match(gradle, /applicationIdSuffix = "\.mentorpaired"/);
  assert.match(route, /const val PREVIEW_PACKAGE = "at\.heinz\.deviceai\.preview"/);
  assert.match(route, /const val MENTOR_PREVIEW_PACKAGE = "at\.heinz\.deviceai\.mentorpaired"/);
  assert.match(route, /externalPackage = !internal/);
});

test('builder is a subpage with explicit return to the original AI dashboard', () => {
  const builder = read(src + 'ContinuumMobileBuilderActivity.kt');
  assert.match(builder, /text = "← Zurück zu Device AI"/);
  assert.match(builder, /setOnClickListener \{ finish\(\) \}/);
  assert.match(agent, /startActivity\(intent\)/);
});

test('the old inaccurate PC-only guidance has been removed', () => {
  assert.doesNotMatch(agent, /Nur ein separater[\s\S]{0,60}eingeschalteter Entwicklerrechner/);
  assert.doesNotMatch(agent, /Es ist noch keine Build-Aufgabe auf einem Host gestartet/);
  assert.match(agent, /Ein PC ist optional/);
});
