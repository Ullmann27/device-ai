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
  assert.match(homeView, /setMeasuredDimension\(width, maxOf\(contentHeight, suggestedMinimumHeight\)\)/);
  assert.match(homeLayout, /fun phone\(widthDp: Float, fontScale: Float = 1f\): Layout/);
  // Scrolling height follows the complete reflowed grid; optional phone NAV starts after that content.
  assert.match(homeLayout, /val contentHeight = cardTop \+ \(\(cards\.size \+ columns - 1\) \/ columns\) \* \(cardHeight \+ gap\)/);
  assert.match(homeLayout, /boxes\[Slot\.NAV\] = Box\(0f, contentHeight, width, contentHeight \+ PHONE_NAV_HEIGHT\)/);
});

test('keyboard hides phone navigation and frees the same reserved space', () => {
  assert.match(buildUi, /val showNavigation = ContinuumWorkAreaNavigationSpec\.showBottomBar/);
  // Fold portrait and the phone share the same keyboard-safe margin policy,
  // retaining phoneNavHeight for phones and portraitNavHeight for the Fold dock.
  assert.match(buildUi, /val navigationPane = phonePane \?: if \(portraitFold\) widePane else null/);
  assert.match(buildUi, /if \(portraitFold\) portraitNavHeight else phoneNavHeight/);
  assert.match(buildUi, /navigationPane\.layoutParams = paneParams/);
});
