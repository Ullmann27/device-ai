import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const source=p=>fs.readFileSync(path.join(root,p),'utf8');
const cockpit=source('app/src/main/java/at/heinz/deviceai/ContinuumJarvisMissionControlActivity.kt');
const home=source('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const manifest=source('app/src/main/AndroidManifest.xml');
const monitor=source('app/src/main/java/at/heinz/deviceai/learning/ContinuumTrainingMonitor.kt');
const receiver=source('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuildResultReceiver.kt');

test('existing Continuum app opens a private read-only mission cockpit',()=>{
  assert.match(manifest,/android:name="\.ContinuumJarvisMissionControlActivity"\s*android:exported="false"/);
  assert.match(home,/CenterAction\("JARVIS · MISSION CONTROL"\)/);
  assert.match(home,/Intent\(this, ContinuumJarvisMissionControlActivity::class\.java\)/);
});

test('three views show existing real device evidence, never fictitious companies or employees',()=>{
  assert.match(cockpit,/ContinuumTrainingMonitor\.read\(this, it\)/);
  assert.match(monitor,/RUNNING_TIMEOUT_MS = 45_000L/);
  assert.match(cockpit,/state\.stale/);
  assert.match(cockpit,/KEIN BELEG/);
  assert.match(cockpit,/keine simulierten Mitarbeiter/);
  assert.doesNotMatch(cockpit,/Lena Fischer|Lena Schmidt|14 neue Leads/);
});

test('coding and update status depend on actual receipts',()=>{
  assert.match(cockpit,/ContinuumMobileBuildResultReceiver\.PREFERENCES/);
  assert.match(cockpit,/ContinuumMobileBuildResultReceiver\.LAST_MESSAGE/);
  assert.match(cockpit,/ContinuumMobileBuildResultReceiver\.LAST_SHA/);
  assert.match(cockpit,/continuum-owner-install-v1/);
  assert.match(receiver,/const val LAST_MESSAGE = "last_message"/);
  assert.match(cockpit,/KEIN INSTALLATIONSBELEG/);
});

test('no arbitrary device control, hidden API calls, or unapproved external models',()=>{
  assert.doesNotMatch(cockpit,/TelecomManager|CALL_PHONE|ACTION_CALL|READ_CONTACTS|RECORD_AUDIO|ACCESSIBILITY_SERVICE/);
  assert.doesNotMatch(cockpit,/java\.net|HttpURLConnection|OkHttp|Runtime\.getRuntime|ProcessBuilder|\.exec\(/);
  assert.match(cockpit,/ContinuumOwnerUpdateActivity::class\.java/);
  assert.match(cockpit,/class MissionBuildingsView/);
  assert.match(cockpit,/override fun onTouchEvent/);
});

test('two supplied original Jarvis transcripts retained with timestamps',()=>{
  for(const clip of ['A','B']){
    const srt=source('docs/research/jarvis-video-20261030/Jarvis-Videotranskript-'+clip+'-20261030.srt');
    assert.match(srt,/00:00:00,/);
    assert.match(srt,/ --> /);
    assert.ok(srt.length>1000);
  }
});
