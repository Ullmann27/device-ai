import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
const root=resolve(fileURLToPath(new URL('../../',import.meta.url)));
const read=path=>readFileSync(resolve(root,path),'utf8');
const activity=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const router=read('app/src/main/java/at/heinz/deviceai/ContinuumWorkerChatRouter.kt');
const tests=read('app/src/test/java/at/heinz/deviceai/ContinuumWorkerChatRouterTest.kt');
const builder=read('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt');

test('coding agent has one integrated entry from Android home, no second app',()=>{
  assert.match(activity,/CODING-AGENT · SPRACHE & APP-IDEEN/);
  assert.match(activity,/setOnClickListener \{ showDevelopmentDialog\(\) \}/);
  assert.match(activity,/CONTINUUM.*Teamleiter|ContinuumDeviceTeamleaderScheduler/);
});

test('developer speech uses actual German Android recognizer and a unique callback',()=>{
  assert.match(activity,/private const val DEV_SPEECH_REQUEST = 2221/);
  assert.match(activity,/startVoiceInput\(DEV_SPEECH_REQUEST\)/);
  assert.match(activity,/RecognizerIntent\.ACTION_RECOGNIZE_SPEECH/);
  assert.match(activity,/RecognizerIntent\.EXTRA_LANGUAGE, "de-AT"/);
  assert.match(activity,/startActivityForResult\(intent, requestCode\)/);
  assert.match(activity,/DEV_SPEECH_REQUEST -> if \(resultCode == RESULT_OK\)/);
  assert.match(activity,/devDialog\?\.isShowing == true/);
  assert.match(activity,/devController\.setGoal\(safe\)/);
  assert.match(activity,/safe\.length/);
});

test('voice text is always previewed; neither recognition nor chat command executes code',()=>{
  const speech = activity.slice(activity.indexOf('DEV_SPEECH_REQUEST -> if (resultCode'),activity.indexOf('CONTINUUM_PACKAGE_MODEL_REQUEST -> {',activity.indexOf('DEV_SPEECH_REQUEST -> if (resultCode')));
  assert.ok(speech.length>0);
  assert.doesNotMatch(speech,/startDevelopmentJob\(|dispatch\(|startService\(|openMobileBuilder\(|sendCurrentInput\(/);
  assert.match(activity,/if \(task == null\)/);
  assert.match(activity,/val coordinator = DevelopmentCoordinator\(LocalModelReasoningAdapter\(localRuntime\)/);
  assert.match(router,/spokenDraftPattern/);
  assert.match(tests,/spokenGermanCodingRequestCreatesOnlySafeDraft/);
  assert.match(builder,/RUN_COMMAND/);
});

test('existing mobile builder remains opt-in, not falsely labelled an app generator',()=>{
  assert.match(activity,/noch keine lokale Build-Aufgabe gestartet/);
  assert.match(activity,/kompatibles lokales Sprachmodell benötigt/);
  assert.match(builder,/Lokalen Build starten\?/);
  assert.match(builder,/setPositiveButton\("Bauen"\)/);
});
