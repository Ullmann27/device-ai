import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const activity = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'), 'utf8');
const layout = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeLayout.kt'), 'utf8');

test('unfold reflows from actual laid out root window width, not stale screenWidthDp', () => {
  assert.match(activity, /root\.addOnLayoutChangeListener/);
  assert.match(activity, /\(right - left\) \/ resources\.displayMetrics\.density/);
  assert.match(activity, /ContinuumHomeLayout\.needsWindowWidthReflow\(windowWidthDp, measuredWidthDp\)/);
  assert.match(activity, /renderedWindowWidthDp = actualWidthDp/);
  assert.match(activity, /setContentView\(buildUi\(\)\)/);
  assert.match(layout, /fun needsWindowWidthReflow\(configuredWidthDp: Float, measuredWidthDp: Float\): Boolean/);
});

test('reflow preserves the active agent, user draft, conversation views and dashboard telemetry', () => {
  const code = activity.split('root.addOnLayoutChangeListener')[1]?.split('return root')[0];
  assert.ok(code, 'size change handler');
  // since the Fold7 composer fix (222a6a4d) the draft keeps text, selection, focus and IME, and is restored after the rebuild
  assert.match(code, /if \(::input\.isInitialized\) \{[\s\S]*?ComposerReflowDraft\(input\.text\.toString\(\), input\.selectionStart,\s*input\.selectionEnd, input\.hasFocus\(\), hadIme\)/);
  assert.match(code, /editor\.setText\(composerDraft\.text\)/);
  assert.match(code, /chatMessages\.add\(child\)/);
  assert.match(code, /chatMessages\.forEach \{ chatContainer\.addView\(it\) \}/);
  assert.match(code, /previousHomeState\?\.let \{ homeView\?\.state = it \}/);
  assert.doesNotMatch(code, /onDestroy\(|localRuntime\.unload|bootstrapMemory\s*=/);
});

test('Fold rail and phone navigation are mutually exclusive after a reflow', () => {
  const code = activity.split('private fun buildUi(): View {')[1]?.split('private fun buildConversationPane(wide: Boolean): View {')[0];
  assert.ok(code);
  assert.match(code, /bottomWorkAreaBar = null/);
  assert.match(code, /navigationView = null/);
  assert.match(code, /railView = null/);
  assert.match(code, /if \(wide\) \{/);
  assert.match(code, /wideContent\.addView\(buildRail\(\)/);
  assert.match(code, /phonePane = buildConversationPane\(wide = false\)/);
});

test('keyboard-only height change does not recreate the Fold dashboard', () => {
  const code = layout.split('fun needsWindowWidthReflow')[1]?.split('fun isWide')[0];
  assert.ok(code);
  assert.match(code, /abs\(configuredWidthDp - measuredWidthDp\) >= 24f/);
  assert.doesNotMatch(code, /screenHeightDp|configuredHeightDp|measuredHeightDp/);
});
