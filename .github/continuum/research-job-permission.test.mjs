import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const manifest=readFileSync(new URL('../../app/src/main/AndroidManifest.xml',import.meta.url),'utf8');
const study=readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyScheduler.kt',import.meta.url),'utf8');
const ui=readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt',import.meta.url),'utf8');

test('Android 14+ background network jobs declare ACCESS_NETWORK_STATE',()=>{
  assert.match(study,/setRequiredNetworkType\(JobInfo\.NETWORK_TYPE_ANY\)/);
  assert.match(manifest,/<uses-permission android:name="android\.permission\.ACCESS_NETWORK_STATE"\s*\/>/);
  assert.match(manifest,/<uses-permission android:name="android\.permission\.INTERNET"\s*\/>/);
});

test('persisted source research job is registered but never exported',()=>{
  assert.match(study,/setPersisted\(true\)/);
  assert.match(manifest,/<uses-permission android:name="android\.permission\.RECEIVE_BOOT_COMPLETED"\s*\/>/);
  assert.match(manifest,/<service\s+android:name="\.learning\.ContinuumPublicStudyJobService"\s+android:exported="false"\s+android:permission="android\.permission\.BIND_JOB_SERVICE"\s*\/>/);
});

test('owner consent, cellular network and measured 20pct resource gates remain required',()=>{
  assert.match(study,/if\(!enabled \|\| !internetAllowed\(\)\) \{ jobs\.cancel\(JOB_ID\); return false \}/);
  assert.match(study,/setRequiresCharging\(false\)/);
  assert.match(study,/setRequiresBatteryNotLow\(false\)/);
  assert.match(study,/setRequiresStorageNotLow\(true\)/);
  assert.match(study,/fun disable\(\):Boolean/);
  assert.match(ui,/FORSCHUNG FREIGEBEN/);
  assert.match(ui,/FORSCHUNG AUS/);
});
