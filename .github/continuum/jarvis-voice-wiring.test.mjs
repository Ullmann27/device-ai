import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';

const root = resolve(fileURLToPath(new URL('../../', import.meta.url)));
const read = name => readFileSync(resolve(root,name),'utf8');
const activity = read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const policy = read('app/src/main/java/at/heinz/deviceai/jarvis/JarvisVoiceConversationPolicy.kt');
const junit = read('app/src/test/java/at/heinz/deviceai/jarvis/JarvisVoiceConversationPolicyTest.kt');

test('tap-to-talk is explicit; never an always-listening background service', () => {
  assert.match(activity,/startActivityForResult\(intent, requestCode\)/);
  assert.match(activity,/RecognizerIntent\.EXTRA_PREFER_OFFLINE, true/);
  assert.match(activity,/setOnLongClickListener \{/);
  assert.match(activity,/jarvisDialogueAutoSend = false/);
  assert.match(activity,/setPositiveButton\("Für diese Sitzung aktivieren"\)/);
  assert.doesNotMatch(activity,/registerAlwaysListeningJarvis|startJarvisBackgroundMicrophone/);
});

test('recognized speech cannot automatically enter tool-capable sendCurrentInput or runLocal', () => {
  const start=activity.indexOf('if (jarvisDialogueAutoSend && !neuralCoach.isActive())');
  const end=activity.indexOf('} else {',start);
  assert.ok(start>0 && end>start);
  const gated=activity.slice(start,end);
  assert.match(gated,/JarvisVoiceConversationPolicy\.evaluate\(spoken\)/);
  // the DIALOGUE_ONLY requirement now lives in the pure, JUnit-tested gate
  assert.match(policy,/decision\.routing == JarvisVoiceRouting\.DIALOGUE_ONLY && !busy && !executorShutdown && activityAlive/);
  assert.match(gated,/JarvisVoiceDispatchGate\.decide\(decision, busy,\s*localAgentExecutor\.isShutdown, !isFinishing && !isDestroyed, voiceStartedIn, sessionId\)/);
  assert.match(gated,/dispatch == at\.heinz\.deviceai\.jarvis\.JarvisVoiceDispatch\.AUTO_DIALOGUE/);
  assert.match(gated,/runContinuumDialogue\(decision\.cleanText, allowLocalModel = false\)/);
  assert.doesNotMatch(gated,/\bsendCurrentInput\(\)|\brunLocal\(/);
});

test('the existing model-capable path remains available only outside voice auto-dispatch', () => {
  assert.match(activity,/private fun runContinuumDialogue\([\s\S]*?allowLocalModel:Boolean=true/);
  assert.match(activity,/if\(allowLocalModel && LocalFirstPolicy\.afterMemory\(/);
  assert.match(activity,/CognitiveDecisionBridge\([\s\S]*?null\s*\)\.respond\(request\.take\(512\)\)/);
});

test('a user-started mic tap interrupts TTS and returns to Android recognizer', () => {
  // unconditional: an utterance queued but not yet started has voiceSpeaking=false and would be heard by the mic
  assert.match(activity,/voiceEngine\?\.stop\(\)\s*stopVoiceAnimation\(\)\s*startVoiceInput\(\)/);
  assert.match(activity,/private fun speakBootstrap\(message: String\) \{[\s\S]*?\|\| voiceListening\) return/);
  assert.match(activity,/startVoiceInput\(\)/);
  assert.match(activity,/private fun addHomeGreeting\(value: String\) \{[\s\S]*?speakBootstrap\(value\)/);
});

test('policy requires manual review for device tools, memory writes and secrets',()=>{
  assert.match(policy,/MANUAL_REVIEW/);
  assert.match(policy,/fun evaluate\(recognized: String\?\)/);
  assert.match(policy,/bankkonto/);
  assert.match(policy,/herd/);
  assert.match(policy,/passwort/);
  assert.match(junit,/actionsSecretsHardwareAndWriteRequestsStayManual/);
  assert.match(junit,/oversizedAndControlMarkedTranscriptsAreNeverDispatched/);
});

test('voice mode is not persisted as an owner, gateway or device permission',()=>{
  const prefix=activity.slice(0,activity.indexOf('private fun sendCurrentInput()'));
  assert.match(prefix,/private var jarvisDialogueAutoSend = false/);
  assert.doesNotMatch(prefix,/bootstrapMemory\.jarvisDialogueAutoSend|ToolGate\.authorize\(jarvisDialogueAutoSend\)/);
});

// Android delivers onActivityResult while the Activity is paused ("An activity can never receive a result in the
// resumed state", Activity.java). A flag that onPause clears and onResume sets is therefore always false inside the
// result handler; requiring it made every DIALOGUE_ONLY result a draft (VOICE-1 blocker, reproduced on #251 7a011384).
test('the voice dispatch never depends on a flag that onPause clears and onResume sets', () => {
  const body = name => { const i = activity.indexOf(`override fun ${name}()`); return activity.slice(i, activity.indexOf('\n    }\n', i)); };
  const cleared = [...body('onPause').matchAll(/(\w+) = false/g)].map(m => m[1]);
  const set = new Set([...body('onResume').matchAll(/(\w+) = true/g)].map(m => m[1]));
  const resumeFlags = cleared.filter(f => set.has(f));
  assert.ok(resumeFlags.includes('foregroundLearningActive'), 'lifecycle flags not found');
  const start = activity.indexOf('SPEECH_REQUEST -> if (resultCode == RESULT_OK) {');
  const handler = activity.slice(start, activity.indexOf('DEV_SPEECH_REQUEST -> if', start));
  assert.ok(start > 0 && handler.includes('runContinuumDialogue(decision.cleanText, allowLocalModel = false)'));
  for (const flag of resumeFlags) assert.doesNotMatch(handler, new RegExp(`\\b${flag}\\b`), flag);
});

test('one recognizer at a time and every result is bound to the chat that started it', () => {
  assert.match(activity,/private fun startVoiceInput\(requestCode: Int = SPEECH_REQUEST\) \{\s*\/\/[^\n]*\n\s*if \(voiceListening\) return/);
  assert.match(activity,/voiceRequestSession = if \(requestCode == SPEECH_REQUEST\) sessionId else null/);
  assert.match(activity,/val voiceStartedIn = if\(requestCode==SPEECH_REQUEST\) voiceRequestSession else null/);
  assert.match(policy,/startedInSession != null && startedInSession == currentSession -> JarvisVoiceDispatch\.AUTO_DIALOGUE/);
});


test('stale speech is rejected before either manual composer or auto-dialogue routing',()=>{
  const start=activity.indexOf('SPEECH_REQUEST -> if (resultCode == RESULT_OK) {');
  const end=activity.indexOf('DEV_SPEECH_REQUEST -> if (resultCode == RESULT_OK)',start);
  assert.ok(start>0 && end>start);
  const body=activity.slice(start,end);
  const guard=body.indexOf('if (!spoken.isNullOrBlank() && voiceStartedIn != null && voiceStartedIn == sessionId)');
  const manual=body.indexOf('input.setText(spoken)');
  const auto=body.indexOf('JarvisVoiceDispatchGate.decide(');
  assert.ok(guard>=0 && auto>guard && manual>guard);
  assert.match(policy,/startedInSession == null \|\| startedInSession != currentSession -> JarvisVoiceDispatch.IGNORE/);
});
