import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const activity = fs.readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt', 'utf8');
const view = fs.readFileSync('app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt', 'utf8');
const orb = fs.readFileSync('app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramRenderer.kt', 'utf8');
const presentation = fs.readFileSync('app/src/main/java/at/heinz/deviceai/cognitive/CockpitConversationPresentation.kt', 'utf8');
const activityMember = (signature) => {
  const start = activity.indexOf(signature);
  assert.ok(start >= 0, 'Missing Activity member ' + signature);
  const end = activity.indexOf('\n    private fun ', start + signature.length);
  return activity.slice(start, end < 0 ? undefined : end);
};

test('home microphone retains the cockpit for continuous voice replies', () => {
  assert.match(activity, /keepCockpitDuringHandsFree = !conversationActive && activeWorkArea == ContinuumWorkArea\.START/);
  const decision = activityMember('private fun handsFreeKeepsHome(');
  assert.match(decision, /CockpitConversationPresentation\.keepHomeForResult\(\s*continuousConversation\.active, keepCockpitDuringHandsFree, activeWorkArea == ContinuumWorkArea\.START\)/);
  assert.doesNotMatch(decision, /\bconversationActive\b/);
  assert.match(presentation, /homeSelected: Boolean\s*\): Boolean = handsFreeActive && pinHomeCockpit && homeSelected/);
  assert.match(activityMember('private fun addUserMessage(value: String)'), /addUserMessage\(value, keepCockpit = false\)/);
  assert.match(activityMember('private fun addAssistantMessage(value: String, preserveLocalContext: Boolean = true)'),
    /addAssistantMessage\(value, preserveLocalContext, keepCockpit = false\)/);
  for (const signature of [
    'private fun addUserMessage(value: String, keepCockpit: Boolean)',
    'private fun addAssistantMessage(value: String, preserveLocalContext: Boolean, keepCockpit: Boolean,',
  ]) {
    const body = activityMember(signature);
    assert.match(body, /if \(!keepCockpit && !handsFreeKeepsHome\(\) && !currentToolScene\(\)\) \{\s*conversationActive = true\s*activeWorkArea = ContinuumWorkArea\.CHAT\s*refreshWorkAreaSelection\(\)\s*refreshConversationLayout\(\)/);
    assert.equal((body.match(/activeWorkArea = ContinuumWorkArea\.CHAT/g) || []).length, 1);
  }
  assert.match(activityMember('private fun focusChatComposer('),
    /keepCockpitDuringHandsFree = false\s*activeWorkArea = ContinuumWorkArea\.CHAT/);
  assert.match(activity, /private fun stopConversationState\(\) \{\s*keepCockpitDuringHandsFree = false/);
});

test('reference cockpit reuses the original animated orb without a solid tile', () => {
  assert.match(view, /renderer\.draw\(painter, renderFrame, transparentBackdrop = true\)/);
  assert.match(orb, /fun draw\(p: ContinuumPainter, f: Frame, transparentBackdrop: Boolean = false\)/);
  assert.match(orb, /if \(!transparentBackdrop\) \{/);
  assert.match(orb, /drawOrbits\(p, f, front = true\)/);
});
