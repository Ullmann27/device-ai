import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { workspaceNav } from './workspace-nav-contract.mjs';

const activity = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt', import.meta.url), 'utf8');
const nav = workspaceNav(activity);
const bodyFrom = (start, end) => {
  const a = activity.indexOf(start), b = activity.indexOf(end, a + start.length);
  assert.ok(a >= 0 && b > a, 'Missing Android source boundary ' + start);
  return activity.slice(a, b);
};

test('every work area routes to an existing Android screen, never to shell, training or installation', () => {
  assert.ok(nav.areas.length >= 10);
  for (const area of nav.areas) {
    const fn = nav.route(area);
    assert.ok(fn, 'No route for ' + area);
    assert.ok(activity.includes('private fun ' + fn + '('), fn + ' is not an existing screen');
  }
  assert.doesNotMatch(nav.routeBody, /exec\(|installPackage|launchApkInstall|trainSgdStep|Runtime\.getRuntime/);
});

test('phones get the bar under the composer; it gives way to the keyboard and reflects chat', () => {
  const pane = bodyFrom('private fun buildConversationPane(wide: Boolean): View', 'private fun buildWorkspaceBar(): View');
  assert.match(pane, /pane\.addView\(buildComposer\(\), matchWidth\(\)\)\n\s+if \(!wide\) pane\.addView\(buildWorkspaceBar\(\)/);
  const bar = bodyFrom('private fun buildWorkspaceBar(): View', 'private fun workspaceBarCell(');
  assert.match(bar, /ContinuumWorkspaceNavSpec\.bottomBar/);
  assert.match(bar, /showWorkAreaList\(ContinuumWorkspaceNavSpec\.more\)/);
  assert.match(activity, /keyboardVisible = imeVisible/);
  assert.deepEqual(nav.bottomBar.slice(0, 2), ['START', 'CHAT']);
});

test('wide screens list the work areas in a scrollable sidebar', () => {
  const side = bodyFrom('private fun buildSidebar(): View', 'private fun buildConversationPane(');
  assert.match(side, /ARBEITSBEREICHE/);
  assert.match(side, /ContinuumWorkspaceNavSpec\.homeGrid/);
  assert.match(side, /openWorkArea\(item\.area\)/);
  assert.match(side, /isFillViewport = true/);
});
