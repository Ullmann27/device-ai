import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const source=relative=>fs.readFileSync(path.join(root,relative),'utf8');
const ui=source('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const archive=source('app/src/main/java/at/heinz/deviceai/learning/ContinuumKnowledgeArchive.kt');
const core=source('app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt');
const gradle=source('app/build.gradle.kts');
const gate=source('scripts/continuum-fold7-owner-update-gate.sh');
const body=ui.split('private fun showOneAppMigrationCenter() {')[1]?.split('private fun showContinuumLearningDashboard() {')[0]??'';

test('single owner app ID is the standalone continuumlocal package',()=>{
  assert.match(gradle,/applicationId\s*=\s*if\s*\(continuumLocalStandalone\s*\|\|\s*continuumLocalStandaloneQa\)\s*"at\.heinz\.deviceai\.continuumlocal"/);
});
test('a separate no-install QA suffix is not the owner package',()=>{
  assert.match(gradle,/applicationIdSuffix\s*=\s*"\.qa"/);
  assert.match(gradle,/continuumLocalStandaloneQa/);
});
test('one-app migration is a first-class existing learning-centre action',()=>{
  const learning=ui.split('private fun showLearningCenter()')[1]?.slice(0,1100)??'';
  assert.match(learning,/CenterAction\("EINE HAUPT-APP · LERNSTAND UND DATENÜBERNAHME"/);
});
test('migration centre contains a real current-app status, not a fixed pretend success number',()=>{
  assert.match(body,/ContinuumLearningCore\(filesDir\)/);
  assert.match(body,/core\.status\(\)/);
  assert.match(body,/stats\(\)\.values\.sum\(\)/);
  assert.match(body,/BuildConfig\.APPLICATION_ID/);
});
test('migration status distinguishes training counter from actual Gemma load',()=>{
  assert.match(body,/status\.modelUpdates/);
  assert.match(body,/localRuntime\.state\s*==\s*LocalModelState\.READY/);
  assert.match(body,/nicht nachgewiesen bereit/);
});
test('public research consent and scheduler state are presented',()=>{
  assert.match(body,/ContinuumPublicStudyScheduler/);
  assert.match(body,/getPendingJob/);
  assert.match(body,/study\.enabled/);
});
test('existing owner weight, archive and export flows are reused, not bypassed',()=>{
  assert.match(body,/confirmWeightsImport\(\)/);
  assert.match(body,/showArchiveBackupMenu\(\)/);
  assert.match(body,/exportCoreWeights\(\)/);
  assert.match(ui,/ContinuumLearningCore\(filesDir\)\.importSnapshot\(bytes\)/);
});
test('single-app centre exposes real local direct inference and training measurements',()=>{
  assert.match(body,/runLocalSmokeTest\(direct = true\)/);
  assert.match(body,/ContinuumTrainingMonitorActivity::class\.java/);
  assert.match(body,/showContinuumLearningDashboard\(\)/);
  assert.match(body,/showPublicCurriculumDialog\(\)/);
});
test('no auto-collection of another app private data or surprise uninstall',()=>{
  assert.doesNotMatch(body,/getPackageContext|createPackageContext|pm uninstall|deletePackage|Runtime\.getRuntime/);
  assert.match(body,/Nichts deinstallieren/);
});
test('archive validates all source records before permanent writes',()=>{
  const i=archive.indexOf('fun importZip(input:InputStream)');
  const staged=archive.indexOf('staged.add(record)',i);
  const checked=archive.indexOf('validateImportedRecord(record)',i);
  const saved=archive.indexOf('staged.forEach { record ->\n            save(',i);
  assert.ok(i>0 && checked>i && staged>checked && saved>staged);
  assert.match(archive,/MAX_IMPORT_UNCOMPRESSED_BYTES=64L \* 1024L \* 1024L/);
});
test('archive checks existing on-device collisions before any new source commit',()=>{
  assert.match(archive,/val current=readFile\(existing\)/);
  assert.match(archive,/Existing archived source differs from verified backup/);
});
test('archive import tells owner how many new records were actually saved',()=>{
  assert.match(ui,/val before=archive\.stats\(\)\.values\.sum\(\)/);
  assert.match(ui,/val added=after-before/);
  assert.match(ui,/wirklich neue Quellen/);
});
test('SWB1 weight-import evaluation protects local user lessons',()=>{
  assert.match(core,/fun importSnapshot\(bytes:ByteArray\)/);
  assert.match(core,/!retains\(intentBefore,intentAfter\)/);
  assert.match(core,/!retains\(semanticBefore,semanticAfter\)/);
  assert.match(core,/replayRetained/);
});
test('original P12 and exact owner source stay behind release gate',()=>{
  assert.match(gate,/CONTINUUM_OWNER_LOCAL_APPROVED_SHA/);
  assert.match(gate,/continuum-local\.p12/);
  assert.match(gate,/continuum-fold7-standalone-apk\.sh/);
});
test('migration cannot be mistaken for complete transfer of revoked owner facts',()=>{
  assert.match(body,/Private App-Speicher sind getrennt/);
  assert.match(body,/Datei-Auswahl/);
});

test('Gemma multi-gigabyte import displays actual progress instead of only LÄDT',()=>{
  assert.match(ui,/Kopiert:.*1024L \* 1024L/);
  assert.match(ui,/snapshot\.elapsedMs \/ 1000L/);
  assert.match(ui,/snapshot\.phase/);
});
test('Gemma import UI is throttled to every 64 MiB or phase change',()=>{
  assert.match(ui,/lastUiBucket = -1L/);
  assert.match(ui,/snapshot\.copiedBytes \/ \(64L \* 1024L \* 1024L\)/);
  assert.match(ui,/snapshot\.phase != at\.heinz\.deviceai\.localmodel\.LocalModelImportPhase\.COPYING/);
});
test('import timeout is a progress watchdog and never claims false 120-second failure',()=>{
  const timeout=ui.split('private fun scheduleLifecycleTimeout(')[1]?.split('private fun clearLifecycleTimeout()')[0]??'';
  assert.match(timeout,/current\.copiedBytes > earlier\.copiedBytes/);
  assert.match(timeout,/scheduleLifecycleTimeout\(message, forImport = true\)/);
  assert.doesNotMatch(timeout,/if \(forImport\) localImportError = message/);
});
test('real native direct smoke test is a manual explicit action',()=>{
  assert.match(body,/runLocalSmokeTest\(direct = true\)/);
  assert.match(ui,/generated\.response\.isNotBlank\(\)/);
  assert.match(ui,/m2DirectProofModelSha = testedModel\.sha256/);
});
