// Source-level contract (not a device test): the dialogue/TTS state reaches the avatar port.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const a=fs.readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt','utf8');
test('AgentActivity feeds hologram state and TTS level into the avatar port (no-op default)',()=>{
  assert.ok(a.includes('at.heinz.deviceai.avatar.AvatarController()'),'controller with no-op default');
  const body=a.slice(a.indexOf('private fun refreshHologramState('),a.indexOf('private fun stopVoiceAnimation('));
  assert.ok(body.includes('avatar.update(at.heinz.deviceai.avatar.AvatarController.activityOf(state.name)'),'state hook');
  assert.ok(a.includes('avatar.lipSync('),'lipsync hook');
});
