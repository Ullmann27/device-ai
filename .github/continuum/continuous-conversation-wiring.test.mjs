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
  assert.match(tap, /if \(!continuousConversation\.active\) \{ toggleContinuousConversation\(\); return \}/);
  const longPress = body('private fun onChatMicrophoneLongPress(');
  assert.match(longPress, /if \(continuousConversation\.active\) endContinuousConversation\(/);
  assert.match(longPress, /else startVoiceInput\(\)/);
  assert.match(body('private fun toggleContinuousConversation('), /Geräteaktionen brauchen weiterhin deine ausdrückliche Bestätigung/);
  const recognizer = fs.readFileSync(dir + 'AndroidOnDeviceSpeechInput.kt', 'utf8');
  assert.match(recognizer, /EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SENTENCE_END_SILENCE_MS/);
  assert.match(recognizer, /SENTENCE_END_SILENCE_MS = 3_000L/);
});

test('listening resumes only after the bound reply and never over dialogs or approvals', () => {
  assert.match(body('private fun speakBootstrap('), /else continuousConversation\.assistantSpeechQueued\(id\)/);
  assert.match(body('private fun installVoiceProgressListener('), /if \(id != null\) onConversationSpeechFinished\(id, interrupted\)/);
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
