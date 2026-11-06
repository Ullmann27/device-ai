import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const activity = readFileSync(new URL('../app/src/main/java/at/heinz/deviceai/AgentActivity.kt', import.meta.url), 'utf8');
const localStart = activity.indexOf('when (ownerLocalIntent)');
assert.ok(localStart >= 0, 'Missing owner-local route');
const localEnd = activity.indexOf('OwnerAppLookupIntent.inspect(', localStart);
assert.ok(localEnd > localStart, 'Missing following app-intake route');
const local = activity.slice(localStart, localEnd);

test('a ready loaded model receives the original greeting or capability request before bootstrap fallback', () => {
  const dialogue = local.split('OwnerChatLocalIntent.STORAGE')[0];
  assert.match(dialogue, /localRuntime.state == LocalModelState.READY && localRuntime.loadedModel != null/);
  assert.match(dialogue, /addUserMessage\(request\)\s+runLocal\(request\)\s+return/);
  assert.ok(dialogue.indexOf('runLocal(request)') < dialogue.indexOf('BootstrapBrain.decide'));
  assert.doesNotMatch(dialogue, /runPublicKnowledge|runResearch|runCloud/);
});

test('storage remains a fresh read-only native probe even with a model loaded', () => {
  const start = local.indexOf('OwnerChatLocalIntent.STORAGE -> {');
  const end = local.indexOf('OwnerChatLocalIntent.CLOCK -> {', start);
  assert.ok(start >= 0 && end > start, 'Missing bounded storage branch');
  const storage = local.slice(start, end);
  assert.match(storage, /executeBootstrapReadOnly\(request,/);
  assert.match(storage, /tool = "get_storage_info"/);
  assert.match(storage, /executeBootstrapReadOnly\(request,\s*BootstrapDecision\(BootstrapKind\.DEVICE_TOOL, tool = "get_storage_info"\)\)\s*return/);
  assert.doesNotMatch(storage, /runLocal|runResearch|runCloud/);
});

test('system center opens a visible connection screen and Activity forwards live status', () => {
  assert.match(activity, /CenterAction\("SHIZUKU · VERBINDEN"\) \{ showShizukuConnectionDialog\(\) \}/);
  assert.match(activity, /shizukuConnectionDialog\?\.updateStatus\(message\)/);
  assert.match(activity, /shizukuConnectionDialog\?\.dismiss\(\)/);
});

test('rendering a conversation updates the selected navigation destination', () => {
  assert.match(activity, /private fun addUserMessage\(value: String\) = addUserMessage\(value, keepCockpit = false\)/);
  assert.match(activity, /private fun addAssistantMessage\(value: String, preserveLocalContext: Boolean = false\) =\s*addAssistantMessage\(value, preserveLocalContext, keepCockpit = false\)/);
  for (const signature of [
    'private fun addUserMessage(value: String, keepCockpit: Boolean)',
    'private fun addAssistantMessage(value: String, preserveLocalContext: Boolean, keepCockpit: Boolean)',
  ]) {
    assert.ok(activity.includes(signature), 'Missing message implementation ' + signature);
    const method = activity.split(signature)[1].split('\n    private fun ')[0];
    assert.match(method, /if \(!keepCockpit && !handsFreeKeepsHome\(\) && !currentToolScene\(\)\) \{\s+conversationActive = true\s+activeWorkArea = ContinuumWorkArea\.CHAT\s+refreshWorkAreaSelection\(\)/);
  }
});

test('oversized local replies show the existing user guidance in the conversation', () => {
  const failure = activity.split('val keepFailedOwnerTurn =')[1].split('if (keepFailedOwnerTurn)')[0];
  assert.match(failure, /LOCAL_MODEL_RESPONSE_EXCEEDS_AGENT_LIMIT/);
  assert.match(failure, /ContinuumUiCopy.modelFailure/);
});
