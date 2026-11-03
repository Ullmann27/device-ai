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
