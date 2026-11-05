// Owner requirement #544 Q1-CONTINUOUS-01: an explicitly started hands-free conversation.
// Ordinary dictation stays preview-only; only the deliberate mode sends, and it never
// reopens the microphone over a dialog or a pending approval.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const dir = 'app/src/main/java/at/heinz/deviceai/';
const activity = fs.readFileSync(dir + 'AgentActivity.kt', 'utf8');
const policy = fs.readFileSync(dir + 'cognitive/ContinuousConversationMode.kt', 'utf8');
const unit = fs.readFileSync('app/src/test/java/at/heinz/deviceai/cognitive/ContinuousConversationModeTest.kt', 'utf8');

function body(signature) {
  const start = activity.indexOf(signature);
  assert.ok(start >= 0, 'missing ' + signature);
  const open = activity.indexOf('{', start);
  let depth = 0;
  for (let i = open; i < activity.length; i++) {
    if (activity[i] === '{') depth++;
    else if (activity[i] === '}' && --depth === 0) return activity.slice(start, i + 1);
  }
  assert.fail('unbalanced ' + signature);
}

test('dictation remains a draft; only the explicit mode routes recognized speech to Send', () => {
  const dictation = body('private fun applyLocalSpeechText(');
  assert.doesNotMatch(dictation, /sendCurrentInput\(|applyConversationSpeech\(/);
  const start = body('private fun startVoiceInput(requestCode: Int = SPEECH_REQUEST)');
  assert.match(start, /val conversationTurn = if \(requestCode == SPEECH_REQUEST\) continuousConversation\.listeningTurn\(scopeSession\) else null/);
  assert.match(start, /if\(conversationTurn!=null\)applyConversationSpeech\(conversationTurn,spoken\) else applyLocalSpeechText\(requestCode,spoken\)/);
  const conversation = body('private fun applyConversationSpeech(');
  assert.match(conversation, /continuousConversation\.recognized\(turnToken, sessionId, spoken\)/);
  assert.match(conversation, /sendCurrentInput\(\)/);
  assert.match(conversation, /continuousConversation\.sendRejected\(\)/);
});

test('owner decision: a microphone tap starts hands-free talk; long press keeps silent dictation', () => {
  assert.match(activity, /setOnClickListener \{ onChatMicrophoneTap\(\) \}/);
  assert.match(activity, /setOnLongClickListener \{ onChatMicrophoneLongPress\(\); true \}/);
  assert.match(activity, /"voice-input" -> onChatMicrophoneTap\(\)/);
  const tap = body('private fun onChatMicrophoneTap(');
  // #564 keeps the cockpit visible during hands-free talk; the inactive tap must still start the mode.
  assert.match(tap, /if \(!continuousConversation\.active\) \{[\s\S]*?toggleContinuousConversation\(\)[\s\S]*?return\s*\}/);
  assert.doesNotMatch(tap.slice(0, tap.indexOf('toggleContinuousConversation()')), /startVoiceInput\(\)/);
  const longPress = body('private fun onChatMicrophoneLongPress(');
  assert.match(longPress, /if \(continuousConversation\.active\) endContinuousConversation\(/);
  assert.match(longPress, /else startVoiceInput\(\)/);
  assert.match(body('private fun toggleContinuousConversation('), /Geräteaktionen brauchen weiterhin deine ausdrückliche Bestätigung/);
  const recognizer = fs.readFileSync(dir + 'AndroidOnDeviceSpeechInput.kt', 'utf8');
  assert.match(recognizer, /EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SENTENCE_END_SILENCE_MS/);
  assert.match(recognizer, /SENTENCE_END_SILENCE_MS = 3_000L/);
});

test('listening resumes only after the bound reply and never over dialogs or approvals', () => {
  const bootstrap = body('private fun speakBootstrap(');
  assert.match(bootstrap, /speakableChunks\(message, maxChunks = 15\)/);
  assert.match(bootstrap, /if \(chunks\.truncated\) chunks\.pieces \+ SPOKEN_REST_IN_CHAT else chunks\.pieces/);
  assert.match(bootstrap, /if \(!speechChunks\.beginTurn\(turn\)\) return\s*pieces\.forEachIndexed \{ index, piece ->\s*speechChunks\.appendApproved\(turn, "\$turn-\$index", piece, approvalEvidenceId = "visible-final-reply"\)\s*\}\s*speechChunks\.markLastChunkSubmitted\(turn\)\s*speechTurnId = turn\s*if \(!speakNextChunk\(turn, TextToSpeech\.QUEUE_FLUSH\)\) stopVoiceAnimation\(\)/);
  assert.doesNotMatch(bootstrap, /assistantSpeechQueued\(/);
  const next = body('private fun speakNextChunk(').replace(/\/\/[^\n]*/g, '');
  assert.match(next, /val chunk = speechChunks\.takeNextReady\(turn\) \?: return false\s*val id = "device-ai-" \+ UUID\.randomUUID\(\)\s*voiceUtteranceId = id\s*speechChunkId = chunk\.chunkId/);
  assert.match(next, /voiceSignal\.activate\(id\)\s*voiceAvatarText = chunk\.approvedText\s*if \(voiceEngine\?\.speak\(chunk\.approvedText, queueMode, null, id\) != TextToSpeech\.SUCCESS\) return false\s*continuousConversation\.assistantSpeechQueued\(id\)\s*return true/);
  const finish = body('private fun finish(id: String?, interrupted: Boolean = false, failed: Boolean = false)').replace(/\/\/[^\n]*/g, '');
  assert.match(finish, /if\s*\(id != null && id == voiceUtteranceId\)\s*\{\s*if \(!interrupted && !failed && continueSpokenReply\(\)\) return@runOnUiThread\s*stopVoiceAnimation\(interrupted \|\| failed\)\s*\}\s*if \(id != null\) onConversationSpeechFinished\(id, interrupted\)/);
  assert.match(body('private fun installVoiceProgressListener('), /if \(id != null\) onConversationSpeechFinished\(id, interrupted\)/);
  assert.match(policy, /if \(state != State\.SPEAKING \|\| utteranceId != replyUtterance\) return Decision\.IGNORE/);
  const resume = body('private fun resumeConversationAfterReply(');
  for (const guard of ['foregroundLearningActive', 'hasWindowFocus()', '!avatarAwaitingApproval', '!voiceListening', 'input.text.isNullOrBlank()'])
    assert.ok(resume.includes(guard), 'resume guard missing: ' + guard);
  assert.match(resume, /busy \|\| botLookupPending \|\| voiceSpeaking/);
});

test('ending the recognition scope (pause, new chat, bot switch) ends hands-free mode', () => {
  assert.match(body('private fun cancelSpeechInputScope('), /stopConversationState\(\)/);
  const stop = body('private fun stopConversationState(');
  for (const callback of ['conversationListen', 'conversationResume', 'conversationIdleCheck'])
    assert.ok(stop.includes('mainHandler.removeCallbacks(' + callback + ')'));
  assert.match(body('override fun onPause('), /cancelSpeechInputScope\(\)/);
});

test('pure policy and its JVM regressions cover turns, stale callbacks, pause, stop and approvals', () => {
  assert.doesNotMatch(policy, /^import android\./m);
  for (const name of ['severalGermanTurnsIncludingFollowUpRunWithoutNewGesture', 'lateRecognizerResultOfAnOldTurnIsIgnored',
    'oldReplyCallbackCannotReopenTheMicrophone', 'spokenStopEndsTheModeAndIsNotSentAsChat', 'spokenPauseKeepsTheModeAndTapResumes',
    'silenceIsRetriedOnceThenPauses', 'openDialogPausesSoNoVoiceTurnCanAnswerAnApprovalPrompt', 'newChatSessionEndsTheMode'])
    assert.match(unit, new RegExp('fun ' + name + '\\('));
});
