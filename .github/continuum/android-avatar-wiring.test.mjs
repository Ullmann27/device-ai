// M3: the rendering-free avatar contract is fed from the EXISTING runtime events of AgentActivity.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const activity=fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt',import.meta.url),'utf8');
const contract=fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt',import.meta.url),'utf8');

test('avatar director is wired to hologram state, TTS start/range/end and the TTS audio envelope',()=>{
 assert.match(activity,/AvatarDirector\(at\.heinz\.deviceai\.avatar\.AvatarSinkRegistry\.sink\)/);
 assert.match(activity,/avatarDirector\.onRuntime\(/);
 assert.match(activity,/avatarDirector\.speechStarted\(utteranceId, voiceAvatarText\)/);
 assert.match(activity,/avatarDirector\.wordRange\(utteranceId, start, end\)/);
 assert.match(activity,/avatarDirector\.audioLevel\(it, level\)/);
 assert.match(activity,/avatarDirector\.speechEnded\(id, interrupted\)/);
});

test('contract stays rendering-free and defaults to the no-op placeholder',()=>{
 assert.match(contract,/var sink: AvatarSink = NoOpAvatarSink/);
 assert.doesNotMatch(contract,/android\.(view|graphics|opengl)|import android/);
});

test('approval dialogs and blocked outcomes drive WAITING_FOR_APPROVAL and BLOCKED',()=>{
 assert.match(activity,/awaitingApproval = avatarAwaitingApproval/);
 assert.match(activity,/blocked = avatarBlocked/);
 assert.match(activity,/avatarAwaitingApproval = true/);
 assert.match(activity,/avatarBlocked = run\.blocker != null/);
 assert.match(activity,/NonModelReplyKind\.SAFETY_REFUSAL\) avatarBlocked = true/);
});
