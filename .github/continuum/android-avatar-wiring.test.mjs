// M3: the rendering-free avatar contract is fed from the EXISTING runtime events of AgentActivity.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const activity=fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt',import.meta.url),'utf8');
const contract=fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt',import.meta.url),'utf8');

test('avatar director is wired to hologram state, TTS start/range/end and the TTS audio envelope',()=>{
 assert.match(activity,/AvatarDirector\(\s*at\.heinz\.deviceai\.cockpit\.JarvisAvatarSlot\.fanOut/);
 assert.match(activity,/val liveHologram = \(hologramView \?: ContinuumHologramView\(this\)\)\.also \{[^}]*avatarDirector\.attach\(it\)[^}]*\}/);
 assert.match(activity,/ContinuumHomeView\(this, liveHologram\)/);
 assert.match(activity,/avatarDirector\.onRuntime\(/);
 assert.match(activity,/avatarDirector\.speechStarted\(utteranceId, voiceAvatarText\)/);
 assert.match(activity,/avatarDirector\.wordRange\(utteranceId, start, end, audioFrame = frame\)/);
 // #562: measured PCM silence must stay distinguishable from missing audio.
 assert.match(activity,/avatarDirector\.audioLevel\(it, level, measuredPcmAvailable = measured != null\)/);
 const stopStart=activity.indexOf('private fun stopVoiceAnimation(interrupted: Boolean)');
 const stopEnd=activity.indexOf('private fun installVoiceProgressListener()',stopStart);
 assert.ok(stopStart>=0 && stopEnd>stopStart);
 const stop=activity.slice(stopStart,stopEnd);
 assert.match(stop,/speechTurnId\?\.let \{ speechChunks\.stop\(it\) \}\s*speechTurnId = null\s*speechChunkId = null/);
 assert.match(stop,/voiceUtteranceId\?\.let \{ avatarDirector\.speechEnded\(it, interrupted = interrupted\) \}/);
 assert.match(stop,/voiceUtteranceId = null/);
 const finishStart=activity.indexOf('private fun finish(id: String?, interrupted: Boolean = false, failed: Boolean = false)');
 const finishEnd=activity.indexOf('private fun speakBootstrap(',finishStart);
 assert.ok(finishStart>=0 && finishEnd>finishStart);
 const finish=activity.slice(finishStart,finishEnd).replace(/\/\/[^\n]*/g,'');
 assert.match(finish,/runOnUiThread\s*\{/);
 assert.match(finish,/if\s*\(id != null && id == voiceUtteranceId\)\s*\{\s*if \(!interrupted && !failed && continueSpokenReply\(\)\) return@runOnUiThread\s*stopVoiceAnimation\(interrupted \|\| failed\)\s*\}\s*if \(id != null\) onConversationSpeechFinished\(id, interrupted\)/);
 const continuationStart=activity.indexOf('private fun continueSpokenReply(): Boolean');
 const continuationEnd=activity.indexOf('private fun showWebLearningInbox()',continuationStart);
 assert.ok(continuationStart>=0 && continuationEnd>continuationStart);
 const continuation=activity.slice(continuationStart,continuationEnd).replace(/\/\/[^\n]*/g,'');
 assert.match(continuation,/val turn = speechTurnId \?: return false\s*val chunk = speechChunkId \?: return false\s*if \(speechChunks\.onChunkFinished\(turn, chunk\) !=\s*at\.heinz\.deviceai\.cognitive\.ApprovedSpeechChunkQueue\.Completion\.MORE_CHUNKS\) return false\s*voiceUtteranceId\?\.let \{ avatarDirector\.speechEnded\(it, interrupted = false\) \}\s*return speakNextChunk\(turn, TextToSpeech\.QUEUE_ADD\)/);
 assert.doesNotMatch(continuation,/stopVoiceAnimation\(|onConversationSpeechFinished\(/);
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
 assert.match(activity,/SAFETY_REFUSAL\) avatarBlocked = true/);
});
