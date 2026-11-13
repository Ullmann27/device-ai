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
const recognizer=read('app/src/main/java/at/heinz/deviceai/AndroidOnDeviceSpeechInput.kt');
const germanLocale=read('app/src/main/java/at/heinz/deviceai/cognitive/GermanOnDeviceSpeechLocale.kt');
const builder=read('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt');
const navigation=read('app/src/main/java/at/heinz/deviceai/ContinuumWorkAreaNavigationSpec.kt');
const menu=read('app/src/main/java/at/heinz/deviceai/ui/JarvisMenu.kt');

test('coding agent has one integrated entry from Android home, no second app',()=>{
  // The existing home slot and More category reach development through the same trusted navigator.
  assert.match(activity,/Slot\.LABEL_DEVELOPING -> showDevelopmentCenter\(\)/);
  assert.match(activity,/ContinuumWorkArea\.MEHR -> showMoreWorkAreas\(\)/);
  assert.match(activity,/showDashboardCategory\("Mehr", ContinuumWorkAreaNavigationSpec\.moreEntries\)/);
  assert.match(navigation,/CategoryEntry\("Entwicklerwerkzeuge", "development"\)/);
  assert.match(activity,/navigateDashboardTarget\(entries\[index\]\.target\)/);
  assert.match(activity,/else -> menuNavigator\.navigate\(target\)/);
  assert.match(menu,/"development" to Slot\.LABEL_DEVELOPING/);
  assert.match(menu,/if \(openTarget\?\.invoke\(target\) != true\) open\(slot\)/);
  assert.match(activity,/JarvisMenuNavigator\(cockpitTriggers, \{ slot -> openHomeSlot\(slot, 0f, 0f\) \}/);
  assert.match(activity,/CenterAction\("ENTWICKLUNGSAUFGABE · STARTEN", accent = true\) \{ showDevelopmentDialog\(\) \}/);
  assert.match(activity,/CONTINUUM.*Teamleiter|ContinuumDeviceTeamleaderScheduler/);
});

test('developer speech uses actual German Android recognizer and a unique callback',()=>{
  assert.match(activity,/private const val DEV_SPEECH_REQUEST = 2221/);
  assert.match(activity,/startVoiceInput\(DEV_SPEECH_REQUEST\)/);
  assert.match(activity,/AndroidOnDeviceSpeechInput\(this\)/);
  assert.match(recognizer,/SpeechRecognizer\.createOnDeviceSpeechRecognizer\(context\)/);
  assert.match(recognizer,/RecognizerIntent\.ACTION_RECOGNIZE_SPEECH/);
  assert.match(recognizer,/RecognizerIntent\.EXTRA_LANGUAGE,\s*language\.tag/);
  assert.match(recognizer,/val language = GermanOnDeviceSpeechLocale\(\)/);
  assert.match(recognizer,/language\.retry\(failure\)/);
  assert.match(germanLocale,/var tag: String = "de-AT"/);
  assert.match(germanLocale,/tag = "de-DE"/);
  assert.match(germanLocale,/triedAlternative = true/);
  assert.match(germanLocale,/Failure\.LANGUAGE_UNAVAILABLE/);
  assert.match(germanLocale,/Failure\.LANGUAGE_UNSUPPORTED/);
  assert.match(activity,/applyLocalSpeechText\(requestCode,spoken\)/);
  assert.match(activity,/requestCode==DEV_SPEECH_REQUEST/);
  assert.match(activity,/devDialog\?\.isShowing==true/);
  assert.match(activity,/devController\.setGoal\(safe\)/);
  assert.match(activity,/val safe=spoken\.take\(400\)/);
});

test('voice text is always previewed; neither recognition nor chat command executes code',()=>{
  const start=activity.indexOf('private fun applyLocalSpeechText(');
  const end=activity.indexOf('override fun onRequestPermissionsResult(',start);
  assert.ok(start>=0 && end>start);
  const speech=activity.slice(start,end);
  // M5: dictation is merged into the composer draft at the cursor (still preview-only).
  assert.match(speech,/LocalSpeechInputPolicy\.mergeDictation\(/);
  assert.match(speech,/input\.setText\(merged\.text\)/);
  assert.match(speech,/devController\.setGoal\(safe\)/);
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
  assert.match(builder,/if \(confirmation != null\)[\s\S]*setPositiveButton\("Bestätigen"\)/);
});
