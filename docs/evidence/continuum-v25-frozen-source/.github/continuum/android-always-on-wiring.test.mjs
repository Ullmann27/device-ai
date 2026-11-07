// UI wiring contracts only; consent and revocation behavior are covered by JVM tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const activity=fs.readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt','utf8');
test('persistent ALWAYS_ON is explicit, revocable and uses the existing web gate',()=>{
 assert.match(activity,/InternetAccessMode\.valueOf/);
 assert.match(activity,/Dauerhaft erlauben/);
 assert.match(activity,/authorizeAlwaysOn\(call,mode\)/);
 assert.match(activity,/approvedModelWebSession\.getAndSet\(null\)\?\.cancel\(\)/);
 assert.match(activity,/session\.checkAuthorized\(\)/);
 assert.match(activity,/session\.isAuthorized\(\)/);
});
test('local model receives web policy and keeps bounded tool requests',()=>{
 assert.match(activity,/Internetmodus:/);
 assert.match(activity,/modelWebCalls >= 2/);
 assert.match(activity,/Keine privaten Erinnerungen/);
 assert.match(activity,/CognitiveDecisionBridge\(/);
});
test('orb states follow real work and Android TTS callbacks',()=>{
 for(const state of ['READY','LISTENING','THINKING','SPEAKING','RESEARCH','LEARNING'])
  assert.ok(activity.includes('State.'+state),'Unwired state '+state);
 assert.match(activity,/UtteranceProgressListener/);
 assert.match(activity,/override fun onAudioAvailable/);
 assert.match(activity,/voiceSignal\.append\(utteranceId,audio\)/);
 assert.match(fs.readFileSync('app/src/main/java/at/heinz/deviceai/TtsHologramSignal.kt','utf8'),/HologramPcmLevel\.rms/);
 assert.match(activity,/override fun onRangeStart/);
});
