import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const activity = fs.readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt', 'utf8');
const view = fs.readFileSync('app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt', 'utf8');
const orb = fs.readFileSync('app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramRenderer.kt', 'utf8');

test('home microphone retains the cockpit for continuous voice replies', () => {
  assert.match(activity, /keepCockpitDuringHandsFree = !conversationActive && activeWorkArea == ContinuumWorkArea\.START/);
  assert.equal((activity.match(/keepCockpitDuringHandsFree && continuousConversation\.active && !conversationActive/g) || []).length, 2);
  assert.match(activity, /private fun stopConversationState\(\) \{\s*keepCockpitDuringHandsFree = false/);
});

test('reference cockpit reuses the original animated orb without a solid tile', () => {
  assert.match(view, /renderer\.draw\(painter, renderFrame, transparentBackdrop = true\)/);
  assert.match(orb, /fun draw\(p: ContinuumPainter, f: Frame, transparentBackdrop: Boolean = false\)/);
  assert.match(orb, /if \(!transparentBackdrop\) \{/);
  assert.match(orb, /drawOrbits\(p, f, front = true\)/);
});
