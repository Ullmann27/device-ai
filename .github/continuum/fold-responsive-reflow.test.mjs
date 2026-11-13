import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const activity = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'), 'utf8');
const layout = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeLayout.kt'), 'utf8');
const composer = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/ComposerReflowDraft.kt'), 'utf8');

test('unfold reflows from actual laid out root window width, not stale screenWidthDp', () => {
  assert.match(activity, /root\.addOnLayoutChangeListener/);
  assert.match(activity, /\(root\.width - root\.paddingLeft - root\.paddingRight\)\.coerceAtLeast\(1\) \/ resources\.displayMetrics\.density/);
  assert.match(activity, /val measuredWidthDp = availableWidthDp\(\)/);
  assert.match(activity, /needsNavigationReflow\(measuredWidthDp\)/);
  assert.match(activity, /ContinuumHomeLayout\.needsWindowWidthReflow\(windowWidthDp, actualWidthDp\)/);
  assert.match(activity, /root\.addOnLayoutChangeListener \{[^\n]*-> applyViewportChange\(\) \}/);
  assert.match(activity, /renderedWindowWidthDp = actualWidthDp/);
  assert.match(activity, /setContentView\(buildUi\(\)\)/);
  assert.match(layout, /fun needsWindowWidthReflow\(configuredWidthDp: Float, measuredWidthDp: Float\): Boolean/);
});

test('reflow preserves the active agent, user draft, conversation views and dashboard telemetry', () => {
  // The posted rebuild now lives in the helper shared by layout and inset callbacks.
  const code = activity.split('fun applyViewportChange() {')[1]?.split('\n        if (android.os.Build.VERSION.SDK_INT >= 30) {')[0];
  assert.ok(code, 'size change handler');
  assert.match(code, /root\.post \{/);
  assert.match(code, /uiWindowRoot !== root \|\| !root\.isAttachedToWindow \|\| isFinishing \|\| isDestroyed/);
  // The PR #96 composer snapshot supersedes the historical single-string 'draft'.
  assert.match(code, /val composerDraft = if \(::input\.isInitialized\) \{/);
  assert.match(code, /ComposerReflowDraft\(input\.text\.toString\(\), input\.selectionStart,/);
  assert.match(code, /input\.selectionEnd, input\.hasFocus\(\), hadIme\)/);
  assert.match(code, /editor\.setText\(composerDraft\.text\)/);
  assert.match(code, /composerDraft\.safeSelection\(\)/);
  assert.match(code, /editor\.setSelection\(selectionStart, selectionEnd\)/);
  assert.match(code, /composerDraft\.hadFocus && rebuiltComposer != null/);
  assert.match(code, /rebuiltComposer\.requestFocus\(\)/);
  assert.match(code, /composerDraft\.imeWasVisible/);
  assert.match(code, /input === rebuiltComposer/);
  assert.match(code, /chatMessages\.add\(child\)/);
  assert.match(code, /chatMessages\.forEach \{ chatContainer\.addView\(it\) \}/);
  assert.match(code, /val previousHomeState = homeView\?\.state/);
  assert.match(code, /previousHomeState\?\.let \{ previous ->\s*homeView\?\.let \{ home -> home\.state = previous\.copy\(computer = home\.state\.computer\) \}\s*\}/);
  assert.doesNotMatch(code, /homeView\?\.state = it\b|home\.state = previous\s*(?:\n|\})/);
  assert.ok(code.indexOf('setContentView(buildUi())') < code.indexOf('previous.copy(computer = home.state.computer)'));
  const pane = activity.split('private fun buildConversationPane(wide: Boolean): View {')[1]?.split('\n    private fun ')[0];
  assert.ok(pane, 'rebuilt dashboard source');
  assert.match(pane, /homeView = home\s*home\.onComputerPresentationChanged = \{ computerController\?\.refreshGaze\(\) \}\s*computerController\?\.restoreFeed\(\)/);
  assert.doesNotMatch(code, /onDestroy\(|localRuntime\.unload|bootstrapMemory\s*=/);
  assert.match(composer, /fun safeSelection\(\): Pair<Int, Int>/);
});

test('Fold rail and phone navigation are mutually exclusive after a reflow', () => {
  const code = activity.split('private fun buildUi(): View {')[1]?.split('private fun buildConversationPane(wide: Boolean): View {')[0];
  assert.ok(code);
  assert.match(code, /bottomWorkAreaBar = null/);
  assert.match(code, /navigationView = null/);
  assert.match(code, /railView = null/);
  assert.match(code, /if \(wide\) \{/);
  assert.match(code, /val rail = buildRail\(\)\.also/);
  assert.match(code, /wideContent\.addView\(rail, LinearLayout\.LayoutParams/);
  assert.match(code, /phonePane = null\s+phoneNavHeight = 0/);
  assert.match(code, /phonePane = buildConversationPane\(wide = false\)/);
  assert.match(code, /ContinuumWorkAreaNavigationSpec\.showBottomBar\(wide, keyboardCompact\)/);
});

test('keyboard-only height change does not recreate the Fold dashboard', () => {
  const code = layout.split('fun needsWindowWidthReflow')[1]?.split('fun isWide')[0];
  assert.ok(code);
  assert.match(code, /abs\(configuredWidthDp - measuredWidthDp\) >= 24f/);
  assert.doesNotMatch(code, /screenHeightDp|configuredHeightDp|measuredHeightDp/);
  const viewport = activity.split('fun updateAvailableHeight() {')[1]?.split('fun needsNavigationReflow')[0];
  assert.ok(viewport, 'height-only viewport update');
  assert.match(viewport, /homeView\?\.updateAvailableViewport\(availableHeightDp\)/);
  assert.doesNotMatch(viewport, /setContentView|buildUi\(|buildConversationPane\(/);
});
