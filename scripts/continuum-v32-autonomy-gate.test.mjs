import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const code=p=>fs.readFileSync(path.join(root,p),'utf8');

test('owner autonomy defaults to bounded research and proposals with durable stop',()=>{
  const control=code('app/src/main/java/at/heinz/deviceai/cognitive/ContinuumAutonomyControl.kt');
  assert.match(control,/RESEARCH_AND_PROPOSALS/);
  assert.match(control,/PAUSED/);
  assert.match(control,/\.putString\(LEVEL, value\.name\)\.commit\(\)/);
  assert.match(control,/return runCatching/);
});

test('emergency stop reaches all three jobs and is exposed in the real app',()=>{
  const activity=code('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
  const publicStudy=code('app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyScheduler.kt');
  const offline=code('app/src/main/java/at/heinz/deviceai/learning/ContinuumAutonomousScheduler.kt');
  const partner=code('app/src/main/java/at/heinz/deviceai/learning/ContinuumDeviceTeamleaderScheduler.kt');
  assert.match(activity,/AUTONOMIE · STUFE \/ NOT-STOPP/);
  assert.match(activity,/jobs\.cancel\(at\.heinz\.deviceai\.learning\.ContinuumAutonomousScheduler\.JOB_ID\)/);
  assert.match(activity,/jobs\.cancel\(at\.heinz\.deviceai\.learning\.ContinuumDeviceTeamleaderScheduler\.JOB_ID\)/);
  assert.match(activity,/ContinuumPublicStudyScheduler\(this\)\.disable\(\)/);
  assert.match(publicStudy,/ContinuumAutonomyControl\.mayResearch/);
  assert.match(offline,/ContinuumAutonomyControl\.level/);
  assert.match(partner,/ContinuumAutonomyControl\.mayPropose/);
});

test('public internet defaults to owner-requested mode but explicit off survives upgrades',()=>{
  const activity=code('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
  assert.match(activity,/if \(stored == "OFF"\)/);
  assert.match(activity,/InternetAccessMode\.ALWAYS_ON/);
  assert.match(activity,/initial_public_study_migration_done/);
  assert.match(activity,/PublicKnowledgeTopicRouter\.topic/);
});

test('partner status is based on DNA and durable run evidence, not simulated agents',()=>{
  const mission=code('app/src/main/java/at/heinz/deviceai/ContinuumJarvisMissionControlActivity.kt');
  assert.match(mission,/ContinuumDnaGuardian/);
  assert.match(mission,/DeviceTeamleaderStatusStore/);
  assert.match(mission,/kein belegter Prüflauf/);
  assert.match(mission,/keinen aktiven Schutz-Nachweis/);
});
