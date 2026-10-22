import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const activity = read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const spec = read('app/src/main/java/at/heinz/deviceai/ContinuumWorkAreaNavigationSpec.kt');
const manifest = read('app/src/main/AndroidManifest.xml');

test('exactly five owner-visible work areas remain in fixed order', () => {
  for (const name of ['START', 'CHAT', 'LERNEN', 'ENTWICKELN', 'MEHR']) {
    assert.match(spec, new RegExp('\\b' + name + '\\("'));
    assert.match(spec, new RegExp('ContinuumWorkArea\\.' + name));
  }
  assert.match(spec, /fun showBottomBar\(wide: Boolean, keyboardVisible: Boolean\): Boolean/);
});

test('phone bottom navigation stays outside conversation pane and above system bar', () => {
  assert.match(activity, /buildConversationPane\(wide = false\)[\s\S]*?MATCH_PARENT, 0, 1f[\s\S]*?root\.addView\(\s*buildBottomWorkAreaBar\(\)/);
  assert.match(activity, /root\.setOnApplyWindowInsetsListener/);
  assert.match(activity, /bottomWorkAreaBar\?\.visibility/);
  assert.match(activity, /ContinuumWorkAreaNavigationSpec\.showBottomBar/);
  assert.match(activity, /insets\.isVisible\(WindowInsets\.Type\.ime\(\)\)/);
});

test('all navigation taps route to existing trusted screens', () => {
  assert.match(activity, /ContinuumWorkArea\.START -> showHomeDashboard\(\)/);
  assert.match(activity, /ContinuumWorkArea\.CHAT -> focusChatComposer\(\)/);
  assert.match(activity, /ContinuumWorkArea\.LERNEN -> showLearningCenter\(\)/);
  assert.match(activity, /ContinuumWorkArea\.ENTWICKELN -> showDevelopmentCenter\(\)/);
  assert.match(activity, /ContinuumWorkArea\.MEHR -> showMoreWorkAreas\(\)/);
  assert.match(activity, /showMoreWorkAreas\(\)[\s\S]*?showModelCenter\(\)/);
  assert.match(activity, /contentDescription = ContinuumWorkAreaNavigationSpec\.accessibilityLabel\(destination\)/);
});

test('home work areas are visible without opening expert-only controls', () => {
  assert.match(activity, /DEINE ARBEITSBEREICHE · DIREKT ÖFFNEN/);
  assert.match(activity, /workAreaQuickRow/);
  assert.match(activity, /setOnClickListener \{ navigateWorkArea\(destination\) \}/);
  assert.match(activity, /nexusExpertExpanded = false/);
});

test('desktop sidebar offers same five work areas without second bottom bar', () => {
  assert.match(activity, /buildSidebar\(\)/);
  assert.match(activity, /sectionLabel\("ARBEITSBEREICHE"/);
  assert.match(activity, /for \(destination in ContinuumWorkAreaNavigationSpec\.destinations\)/);
  assert.match(activity, /buildWorkAreaButton\(destination, compact = false\)/);
  assert.match(spec, /!wide && !keyboardVisible/);
});

test('existing chat, learning monitoring and owner permission boundaries remain', () => {
  assert.match(activity, /private fun focusChatComposer\(\)/);
  assert.match(activity, /private fun showLearningCenter\(\)/);
  assert.match(activity, /private fun showDevelopmentCenter\(\)/);
  assert.match(activity, /private fun openMobileBuilder\(\)/);
  assert.match(activity, /startActivity\(Intent\(this@AgentActivity, ContinuumTrainingMonitorActivity::class\.java\)\)/);
  assert.match(activity, /setOnClickListener \{ openMobileBuilder\(\) \}/);
  assert.match(manifest, /android:name="\.AgentActivity"/);
  assert.doesNotMatch(spec, /Runtime\.getRuntime|ProcessBuilder|FileOutputStream|train\(|startActivity|deleteRecursively/);
});
