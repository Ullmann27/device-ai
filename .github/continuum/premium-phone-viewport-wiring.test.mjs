import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const activity = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'), 'utf8');
const homeView = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeView.kt'), 'utf8');
const homeLayout = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeLayout.kt'), 'utf8');
const buildUi = activity.split('private fun buildUi(): View {')[1]?.split('private fun buildConversationPane(wide: Boolean): View {')[0];
const buildPane = activity.split('private fun buildConversationPane(wide: Boolean): View {')[1]?.split('private fun railWidthPx()')[0];

test('phone root fills the viewport, with an explicitly bottom-anchored navigation rail', () => {
  assert.ok(buildUi, 'buildUi must exist');
  assert.match(buildUi, /val root = android\.widget\.FrameLayout\(this\)/);
  assert.match(buildUi, /layoutParams = android\.view\.ViewGroup\.LayoutParams\([\s\S]*?MATCH_PARENT,[\s\S]*?MATCH_PARENT/);
  assert.match(buildUi, /phonePane = buildConversationPane\(wide = false\)/);
  assert.match(buildUi, /bottomMargin = phoneNavHeight/);
  assert.match(buildUi, /root\.addView\(buildBottomWorkAreaBar\(\), android\.widget\.FrameLayout\.LayoutParams\([\s\S]*?phoneNavHeight,[\s\S]*?Gravity\.BOTTOM/);
  assert.doesNotMatch(buildUi, /buildConversationPane\(wide = false\),[\s\S]*?LinearLayout\.LayoutParams\([\s\S]*?MATCH_PARENT, 0, 1f/);
});

test('home dashboard remains in a scrolling viewport, not hidden behind the navigation', () => {
  assert.ok(buildPane, 'buildConversationPane must exist');
  assert.match(buildPane, /dashboard\.addView\(home, matchWidth\(\)\)/);
  assert.match(buildPane, /dashboardSection = dashboardScroll/);
  assert.match(activity, /dashboardSection\?\.visibility = if \(chat\) View\.GONE else View\.VISIBLE/);
  assert.match(homeView, /val h = if \(jarvisShellStage && width \/ density >= 240f\)/);
  assert.match(homeView, /JarvisShellLayout\.forContent\(width \/ density, jarvisShell\.viewportHeightDp,/);
  assert.match(homeView, /else maxOf\(contentHeight, suggestedMinimumHeight\)/);
  assert.match(homeView, /setMeasuredDimension\(width, h\)/);
  assert.match(homeLayout, /fun phone\(widthDp: Float, fontScale: Float = 1f\): Layout/);
  // Both the current shell and compatibility layout preserve complete scrollable content.
  const shell = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/ui/JarvisShellLayout.kt'), 'utf8');
  const reference = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/ui/ContinuumReferenceLayout.kt'), 'utf8');
  assert.match(shell, /return Shell\(formFactor\(w, viewport\), w, maxOf\(viewport, y\)/);
  assert.match(reference, /val contentHeight = maxOf\(top \+ learningHeight \+ 16f, height\)/);
  assert.match(reference, /boxes\[Slot\.NAV\] = Box\(0f, contentHeight, width, contentHeight \+ ContinuumHomeLayout\.PHONE_NAV_HEIGHT\)/);
});

test('keyboard hides phone navigation and frees the same reserved space', () => {
  assert.match(buildUi, /val showNavigation = ContinuumWorkAreaNavigationSpec\.showBottomBar/);
  // Open Fold uses its labelled rail in both orientations; only the phone reserves this bottom bar.
  assert.match(buildUi, /val navigationPane = phonePane/);
  assert.match(buildUi, /val requiredMargin = if \(showNavigation\) phoneNavHeight else 0/);
  assert.match(buildUi, /paneParams\.bottomMargin = requiredMargin/);
  assert.match(buildUi, /navigationPane\.layoutParams = paneParams/);
  assert.match(buildPane, /externalBottomBarHeightDp = 0f/);
  assert.match(buildUi, /showBottomBar\(wide, keyboardCompact\) && !homeOwnsNavigation/);
  assert.match(activity, /syncPhoneNavigation\?\.invoke\(\)/);
});
