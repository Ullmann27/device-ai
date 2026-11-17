// Source-level contract (not a device test): AgentActivity must take its ephemeral
// local dialogue from the process-scoped LocalDialogueRetention so a Fold7
// recreation (density/theme/font/keyboard change) keeps the chat context.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const activity=fs.readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt','utf8');
test('Fold7 recreation keeps the ephemeral local dialogue of the same session',()=>{
  assert.ok(!/val localConversationContext\s*=\s*at\.heinz\.deviceai\.cognitive\.LocalConversationContext\(\)/.test(activity),
    'per-Activity LocalConversationContext is lost on recreation');
  assert.ok(activity.includes('LocalDialogueRetention.context'),'context must come from LocalDialogueRetention');
  assert.ok(activity.includes('LocalDialogueRetention.activityDestroyed(isChangingConfigurations)'),
    'onDestroy must forget the dialogue on a real finish and keep it on a configuration change');
  assert.ok(activity.includes('LocalDialogueRetention.replyFor(sessionId)'),'last local reply must be restored for the same session');
});
