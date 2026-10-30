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
  assert.match(gated,/JarvisVoiceRouting\.DIALOGUE_ONLY/);
  assert.match(gated,/!busy && !localAgentExecutor\.isShutdown && foregroundLearningActive/);
  assert.match(gated,/runContinuumDialogue\(decision\.cleanText, allowLocalModel = false\)/);
  assert.doesNotMatch(gated,/\bsendCurrentInput\(\)|\brunLocal\(/);
});

test('the existing model-capable path remains available only outside voice auto-dispatch', () => {
  assert.match(activity,/private fun runContinuumDialogue\([\s\S]*?allowLocalModel:Boolean=true/);
  assert.match(activity,/if\(allowLocalModel && LocalFirstPolicy\.afterMemory\(/);
  assert.match(activity,/CognitiveDecisionBridge\([\s\S]*?null\s*\)\.respond\(request\.take\(512\)\)/);
});

test('a user-started mic tap interrupts TTS and returns to Android recognizer', () => {
  assert.match(activity,/if \(voiceSpeaking\) \{\s*voiceEngine\?\.stop\(\)\s*stopVoiceAnimation\(\)/);
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
