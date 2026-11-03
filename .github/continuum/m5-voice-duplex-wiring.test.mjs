import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
const root=resolve(fileURLToPath(new URL('../../',import.meta.url)));
const activity=readFileSync(resolve(root,'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'),'utf8');
const body=(signature)=>{
  const start=activity.indexOf(signature);
  assert.ok(start>=0,`missing ${signature}`);
  const next=activity.indexOf('\n    private fun ',start+signature.length);
  return activity.slice(start,next<0?undefined:next);
};

// M5 milestone 2 (Argon 4, 7.1): STT start stops TTS and avatar mouth synchronously.
test('voice input barge-in silences TTS engine and avatar mouth before the recognizer starts',()=>{
  const start=body('private fun startVoiceInput(');
  const stopEngine=start.indexOf('voiceEngine?.stop()');
  const stopMouth=start.indexOf('stopVoiceAnimation()');
  const recognizer=start.indexOf('speech.start(');
  assert.ok(stopEngine>=0,'startVoiceInput must stop the TTS engine');
  assert.ok(stopMouth>=0,'startVoiceInput must stop the avatar mouth/voice signal synchronously, not only via the async TTS onStop callback');
  assert.ok(stopMouth<recognizer,'mouth must be silenced before the microphone opens');
});

// M5 milestone 2: half-duplex echo guard – the assistant never talks into an open microphone.
test('assistant TTS is refused while the on-device microphone turn is active',()=>{
  const speak=body('private fun speakBootstrap(');
  assert.match(speak,/LocalSpeechInputPolicy\.maySpeak\([^)]*microphoneActive\s*=\s*voiceListening/);
});

// M5 milestone 3: speech -> composer handoff keeps the owner's typed draft.
test('dictation is merged at the cursor and never overwrites a typed composer draft',()=>{
  const apply=body('private fun applyLocalSpeechText(');
  assert.match(apply,/LocalSpeechInputPolicy\.mergeDictation\(\s*input\.text\.toString\(\)\s*,[^;]*input\.selectionStart[^;]*input\.selectionEnd[^;]*spoken\s*\)/);
  assert.match(apply,/input\.setText\(merged\.text\)/);
  assert.doesNotMatch(apply,/input\.setText\(spoken\)/);
  // Still preview-only: recognition never sends or executes.
  assert.doesNotMatch(apply,/sendCurrentInput\(|dispatch\(|startDevelopmentJob\(/);
});

// M5 milestone 4: the only native model-load path passes the resource/thermal admission.
test('model load path is gated by measured RAM/thermal admission before the native engine starts',()=>{
  const load=body('private fun loadModel(');
  const gate=load.indexOf('LocalModelLoadAdmission.loadIfAdmitted(');
  assert.ok(gate>=0,'loadModel must route through LocalModelLoadAdmission');
  assert.match(load,/localModelResourceSnapshot\(\)/);
  assert.doesNotMatch(load.replace(/LocalModelLoadAdmission\.loadIfAdmitted\(localRuntime/g,''),/localRuntime\.load\(/,
    'no direct localRuntime.load bypass');
});
