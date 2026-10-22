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
const member = signature => {
  const start = activity.indexOf(signature);
  assert.ok(start >= 0, 'missing ' + signature);
  const next = activity.indexOf('\n    private fun ', start + signature.length);
  return activity.slice(start, next < 0 ? undefined : next);
};

test('exactly five owner-visible work areas remain in fixed order', () => {
  for (const name of ['START', 'CHAT', 'LERNEN', 'ENTWICKELN', 'MEHR']) {
    assert.match(spec, new RegExp('\\b' + name + '\\("'));
    assert.match(spec, new RegExp('ContinuumWorkArea\\.' + name));
  }
  assert.match(spec, /fun showBottomBar\(wide: Boolean, keyboardVisible: Boolean\): Boolean/);
});

test('phone bottom navigation stays outside conversation pane and above system bar', () => {
  const ui = member('private fun buildUi(): View');
  assert.match(ui, /val root = android\\.widget\\.FrameLayout\\(this\\)/);
  assert.match(ui, /phonePane = buildConversationPane\\(wide = false\\)/);
  assert.match(ui, /bottomMargin = phoneNavHeight/);
  assert.match(ui, /root\\.addView\\(buildBottomWorkAreaBar\\(\\), android\\.widget\\.FrameLayout\\.LayoutParams\\([\\s\\S]*?phoneNavHeight,[\\s\\S]*?Gravity\\.BOTTOM/);
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

test('home work areas are reachable without expert-only controls', () => {
  const bar = member('private fun buildBottomWorkAreaBar(): View');
  assert.match(bar, /for \(destination in ContinuumWorkAreaNavigationSpec\.destinations\)/);
  assert.match(bar, /setOnClickListener \{ navigateWorkArea\(destination\) \}/);
  assert.match(bar, /contentDescription = ContinuumWorkAreaNavigationSpec\.accessibilityLabel\(destination\)/);
  assert.doesNotMatch(activity, /nexusExpertExpanded|Weitere KI-Funktionen anzeigen/);
});

test('Fold rail offers the five work areas without a second bottom bar', () => {
  const rail = member('private fun buildRail(): View');
  for (const area of ['START', 'CHAT', 'LERNEN', 'ENTWICKELN'])
    assert.match(rail, new RegExp('navigateWorkArea\\(ContinuumWorkArea\\.' + area + '\\)'));
  assert.match(rail, /showMoreWorkAreas\(\)/);
  assert.match(activity, /buildRail\(\),/);
  assert.match(spec, /!wide && !keyboardVisible/);
});

test('existing chat, learning monitoring and owner permission boundaries remain', () => {
  assert.match(activity, /private fun focusChatComposer\(\)/);
  assert.match(activity, /private fun showLearningCenter\(\)/);
  assert.match(activity, /private fun showDevelopmentCenter\(\)/);
  assert.match(activity, /private fun openMobileBuilder\(\)/);
  assert.match(activity, /startActivity\(Intent\(this@AgentActivity, ContinuumTrainingMonitorActivity::class\.java\)\)/);
  assert.match(member('private fun showMoreWorkAreas()'), /4 -> openMobileBuilder\(\)/);
  assert.match(manifest, /android:name="\.AgentActivity"/);
  assert.doesNotMatch(spec, /Runtime\.getRuntime|ProcessBuilder|FileOutputStream|train\(|startActivity|deleteRecursively/);
});


test('Fold rail and start screen reach all actions at short heights', () => {
  assert.match(member('private fun buildRail(): View'), /element\.targets\(\(b - t\) \/ density\)/);
  assert.match(member('private fun buildConversationPane(wide: Boolean): View'), /val dashboardScroll = ScrollView\(this\)/);
});

test('start screen adapts to large fonts, rotated compact screens and the unfolded Fold', () => {
  const layout = read('app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeLayout.kt');
  assert.match(layout, /fun isWide\(widthDp: Float, heightDp: Float\) = widthDp >= 700f && heightDp >= 500f/);
  assert.match(activity, /val wide = at\.heinz\.deviceai\.ui\.ContinuumHomeLayout\.isWide\(/);
  assert.match(activity, /home\.configure\(windowWidth, wide, railOffset = /);
  assert.match(read('app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeView.kt'), /fontScale\.coerceIn\(\.85f, 1\.3f\)/);
});

test('recreated activity preserves chat selection and draft without persistent More selection', () => {
  assert.match(activity, /WORK_AREA_STATE_KEY = "continuum_work_area_state"/);
  assert.match(activity, /outState\.putString\(\s*WORK_AREA_STATE_KEY/);
  assert.match(activity, /conversationActive = savedInstanceState\?\.getString\(WORK_AREA_STATE_KEY\)/);
  assert.match(activity, /refreshConversationLayout\(\)\s*refreshWorkAreaSelection\(\)/);
  assert.match(spec, /fun selectionAfterTap\(current: ContinuumWorkArea, tapped: ContinuumWorkArea\)/);
  assert.match(activity, /activeWorkArea = ContinuumWorkAreaNavigationSpec\.selectionAfterTap/);
});
