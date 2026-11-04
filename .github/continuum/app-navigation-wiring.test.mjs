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
const menu = read('app/src/main/java/at/heinz/deviceai/ui/JarvisMenu.kt');
const tiles = read('app/src/main/java/at/heinz/deviceai/ui/JarvisTiles.kt');
const routes = read('app/src/main/java/at/heinz/deviceai/ui/JarvisShellContent.kt');
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
  assert.match(spec, /fun showBottomBar\(wide: Boolean, keyboardVisible: Boolean, portraitFold: Boolean = false\): Boolean/);
});

test('phone bottom navigation stays outside conversation pane and above system bar', () => {
  const ui = member('private fun buildUi(): View');
  assert.match(ui, /val root = android\.widget\.FrameLayout\(this\)/);
  assert.match(ui, /phonePane = buildConversationPane\(wide = false\)/);
  assert.match(ui, /bottomMargin = phoneNavHeight/);
  assert.match(ui, /root\.addView\(buildBottomWorkAreaBar\(\), android\.widget\.FrameLayout\.LayoutParams\([\s\S]*?phoneNavHeight,[\s\S]*?Gravity\.BOTTOM/);
  assert.match(activity, /root\.setOnApplyWindowInsetsListener/);
  assert.match(activity, /bottomWorkAreaBar\?\.visibility/);
  assert.match(activity, /ContinuumWorkAreaNavigationSpec\.showBottomBar/);
  assert.match(activity, /insets\.isVisible\(WindowInsets\.Type\.ime\(\)\)/);
});

test('all navigation taps route to existing trusted screens', () => {
  const navigation = member('private fun navigateWorkArea(');
  assert.match(navigation, /ContinuumWorkArea\.START -> showHomeDashboard\(\)/);
  assert.match(navigation, /ContinuumWorkArea\.CHAT -> focusChatComposer\(\)/);
  assert.match(navigation, /ContinuumWorkArea\.LERNEN -> showDashboardCategory\("KI-System", ContinuumWorkAreaNavigationSpec\.systemEntries\)/);
  assert.match(navigation, /ContinuumWorkArea\.ENTWICKELN -> showDashboardCategory\("Werkzeuge", ContinuumWorkAreaNavigationSpec\.toolEntries\)/);
  assert.match(navigation, /ContinuumWorkArea\.MEHR -> showMoreWorkAreas\(\)/);
  assert.match(member('private fun showMoreWorkAreas()'), /showDashboardCategory\("Mehr", ContinuumWorkAreaNavigationSpec\.moreEntries\)/);
  const category = member('private fun showDashboardCategory(');
  assert.match(category, /setItems\(entries\.map \{ it\.title \}\.toTypedArray\(\)\)/);
  assert.match(category, /navigateDashboardTarget\(entries\[index\]\.target\)/);
  const target = member('private fun navigateDashboardTarget(');
  assert.match(target, /"model-management" -> showModelCenter\(\)/);
  assert.match(target, /else -> menuNavigator\.navigate\(target\)/);
  assert.match(spec, /CategoryEntry\("Lernen", "learning"\)/);
  assert.match(spec, /CategoryEntry\("Entwicklerwerkzeuge", "development"\)/);
  assert.match(routes, /"learning" to ContinuumHomeLayout\.Slot\.LABEL_LEARNING/);
  assert.match(menu, /"development" to Slot\.LABEL_DEVELOPING/);
  assert.match(menu, /if \(openTarget\?\.invoke\(target\) != true\) open\(slot\)/);
  assert.match(activity, /JarvisMenuNavigator\(cockpitTriggers, \{ slot -> openHomeSlot\(slot, 0f, 0f\) \}/);
  const slots = member('private fun openHomeSlot(');
  assert.match(slots, /Slot\.LEARNING -> showLearningCenter\(\)/);
  assert.match(slots, /Slot\.LABEL_DEVELOPING -> showDevelopmentCenter\(\)/);
  assert.match(activity, /contentDescription = ContinuumWorkAreaNavigationSpec\.accessibilityLabel\(destination\)/);
});

test('home work areas are reachable without expert-only controls', () => {
  const bar = member('private fun buildBottomWorkAreaBar(): View');
  assert.match(bar, /for \(destination in ContinuumWorkAreaNavigationSpec\.destinations\)/);
  assert.match(bar, /setOnClickListener \{ navigateWorkArea\(destination\) \}/);
  assert.match(bar, /contentDescription = ContinuumWorkAreaNavigationSpec\.accessibilityLabel\(destination\)/);
  assert.doesNotMatch(activity, /nexusExpertExpanded|Weitere KI-Funktionen anzeigen/);
});

test('Fold rail offers every current destination without a second bottom bar', () => {
  const rail = member('private fun buildRail(): View');
  const current = tiles.split('val railNav = listOf(')[1]?.split('val bottomNav')[0];
  assert.ok(current, 'single rail entry list');
  assert.deepEqual([...current.matchAll(/NavItem\("([^"]+)"/g)].map(m => m[1]),
    ['home', 'chat', 'memory', 'logic', 'research', 'agents', 'tasks', 'web', 'learning', 'device', 'system']);
  assert.match(menu, /val railEntries: List<Entry> = JarvisTiles\.railNav\.map/);
  assert.match(rail, /val entries = at\.heinz\.deviceai\.ui\.JarvisMenu\.railEntries/);
  assert.match(rail, /for \(entry in entries\)/);
  assert.match(rail, /contentDescription = entry\.label/);
  assert.match(rail, /setOnClickListener \{ menuNavigator\.navigate\(entry\.target\) \}/);
  const ui = member('private fun buildUi(): View');
  assert.match(ui, /val rail = buildRail\(\)\.also/);
  assert.match(ui, /wideContent\.addView\(rail, LinearLayout\.LayoutParams/);
  // Directive IV uses the labelled rail for medium/expanded windows in both orientations.
  assert.match(spec, /!wide && !keyboardVisible/);
  assert.doesNotMatch(ui, /buildPortraitWorkAreaBar\(\)/);
});

test('existing chat, learning monitoring and owner permission boundaries remain', () => {
  assert.match(activity, /private fun focusChatComposer\(\)/);
  assert.match(activity, /private fun showLearningCenter\(\)/);
  assert.match(activity, /private fun showDevelopmentCenter\(\)/);
  assert.match(activity, /private fun openMobileBuilder\(\)/);
  assert.match(activity, /startActivity\(Intent\(this@AgentActivity, ContinuumTrainingMonitorActivity::class\.java\)\)/);
  assert.match(member('private fun showMoreWorkAreas()'), /ContinuumWorkAreaNavigationSpec\.moreEntries/);
  assert.match(spec, /CategoryEntry\("Handy-Builder · Termux", "mobile-builder"\)/);
  assert.match(member('private fun navigateDashboardTarget('), /"mobile-builder" -> openMobileBuilder\(\)/);
  assert.match(manifest, /android:name="\.AgentActivity"/);
  assert.doesNotMatch(spec, /Runtime\.getRuntime|ProcessBuilder|FileOutputStream|train\(|startActivity|deleteRecursively/);
});


test('Fold rail and start screen reach all actions at short heights', () => {
  const rail = member('private fun buildRail(): View');
  assert.match(rail, /JarvisMenuRail\.rows\(\(b - t\) \/ density, entries\.size\)/);
  assert.match(rail, /JarvisMenuRail\.minHeightDp\(entries\.size\)/);
  assert.match(rail, /return ScrollView\(this\)/);
  assert.match(member('private fun buildConversationPane(wide: Boolean): View'), /val dashboardScroll = ScrollView\(this\)/);
});

test('start screen adapts to large fonts, rotated compact screens and the unfolded Fold', () => {
  const layout = read('app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeLayout.kt');
  // Legacy callers retain their pure geometry API; the live shell uses Directive IV width classes.
  assert.match(layout, /fun isWide\(widthDp: Float, heightDp: Float\) = widthDp >= 600f && heightDp >= 480f/);
  assert.match(activity, /val windowHeight = renderedWindowHeightDp \?: resources\.configuration\.screenHeightDp\.toFloat\(\)/);
  assert.match(activity, /renderedWindowWidthDp = actualWidthDp/);
  assert.match(activity, /root\.addOnLayoutChangeListener/);
  // Density transforms still match slot drawing; adaptive geometry keeps layout scale1 instead of shrinking controls.
  const home = read('app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeView.kt');
  assert.match(home, /val k = density \* \(layoutSpec\?\.scale \?: 1f\)\n\s+canvas\.scale\(k, k\)/);
  assert.match(home, /element\.draw\(painter, width \/ k, height \/ k, state\)/);
  assert.match(activity, /val wide = windowWidthDp >= at\.heinz\.deviceai\.ui\.JarvisWindowProfile\.MEDIUM_MIN_DP/);
  assert.match(activity, /home\.configure\(windowWidth, wide, railOffset = /);
  assert.match(read('app/src/main/java/at/heinz/deviceai/ui/ContinuumReferenceLayout.kt'), /return Layout\(wide, width, 1f, contentHeight, boxes,/);
  assert.match(home, /ContinuumReferenceLayout\.create\(windowWidthDp, if \(wide\) windowHeightDp else 0f,\s*wide, textScale, compactPresentation\)/);
  assert.match(home, /JarvisShellLayout\.forContent\(width \/ density, jarvisShell\.viewportHeightDp,\s*resources\.configuration\.fontScale/);
  assert.match(read('app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeView.kt'), /fontScale\.coerceIn\(\.85f, 2f\)/);
});

test('recreated activity preserves chat selection and draft without persistent More selection', () => {
  assert.match(activity, /WORK_AREA_STATE_KEY = "continuum_work_area_state"/);
  assert.match(activity, /outState\.putString\(\s*WORK_AREA_STATE_KEY/);
  assert.match(activity, /conversationActive = savedInstanceState\?\.getString\(WORK_AREA_STATE_KEY\)/);
  assert.match(activity, /refreshConversationLayout\(\)\s*refreshWorkAreaSelection\(\)/);
  assert.match(spec, /fun selectionAfterTap\(current: ContinuumWorkArea, tapped: ContinuumWorkArea\)/);
  assert.match(activity, /activeWorkArea = ContinuumWorkAreaNavigationSpec\.selectionAfterTap/);
});

test('design acceptance snapshot is owner-triggered, needs no permission and records the layout metrics', () => {
  const snapshot = read('app/src/main/java/at/heinz/deviceai/ui/ContinuumUiSnapshot.kt');
  assert.match(activity, /actionButton\("Startbildschirm für Design-Abnahme sichern"\)/);
  assert.match(activity, /ContinuumUiSnapshot\.metrics\(this, homeView\?\.wide == true, homeView\?\.layoutScale\)/);
  assert.match(snapshot, /if \(Build\.VERSION\.SDK_INT < 29\) return null/);
  for (const field of ['screenWidthDp', 'screenHeightDp', 'densityDpi', 'fontScale', 'wideLayout', 'layoutScale', 'insetsPx'])
    assert.match(snapshot, new RegExp(`put\\("${field}"`));
  assert.doesNotMatch(read('app/src/main/AndroidManifest.xml'), /WRITE_EXTERNAL_STORAGE|READ_MEDIA_IMAGES/);
});
