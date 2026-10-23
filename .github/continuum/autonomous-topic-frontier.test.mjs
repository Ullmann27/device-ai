import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const root=new URL('../../',import.meta.url);
const read=path=>readFileSync(new URL(path,root),'utf8');
const policy=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyPolicy.kt');
const frontier=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumTopicFrontier.kt');
const scheduler=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyScheduler.kt');
const archive=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumKnowledgeArchive.kt');
const learning=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt');
const ui=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');

test('auto-discovered titles can reach only the official MediaWiki API',()=>{
  assert.match(policy,/validArticleTitle\(title:String\)/);
  assert.match(policy,/require\(validArticleTitle\(title\)\)/);
  assert.match(policy,/https:\/\/de\.wikipedia\.org\/w\/api\.php\?action=query&prop=links/);
  assert.match(policy,/plnamespace=0&pllimit=16/);
  assert.match(scheduler,/val url=URL\(ContinuumPublicStudyPolicy\.encyclopediaLinksApiUrl\(sourceTitle\)\)/);
  assert.match(scheduler,/url\.protocol=="https" && url\.host=="de\.wikipedia\.org"/);
  assert.match(scheduler,/conn\.instanceFollowRedirects=false/g);
});

test('bounded topic frontier persists discoveries and avoids duplicate processing',()=>{
  assert.match(frontier,/MAX_PENDING=128/);
  assert.match(frontier,/MAX_VISITED=384/);
  assert.match(frontier,/filter \{ it !in seen && it !in seeds \}/);
  assert.match(frontier,/cycle%4L!=0L/);
  assert.match(scheduler,/putString\("topic_pending"/);
  assert.match(scheduler,/putString\("topic_visited"/);
  assert.match(scheduler,/scheduler\.addDiscovered\(discovered\)/);
  assert.match(scheduler,/scheduler\.recordCycle\(phase,selectedTopic,added,accepted,stopRequested=\{ stopping\.get\(\) \}\)/);
  assert.match(scheduler,/Automatisch entdeckte Themen in Warteschlange/);
});

test('provider rate limits are obeyed, and background work remains finite',()=>{
  assert.match(scheduler,/if\(status==429 \|\| status==503\)/);
  assert.match(scheduler,/getHeaderField\("Retry-After"\)/);
  assert.match(scheduler,/fun isRateLimited\(\)/);
  assert.match(scheduler,/if\(!scheduler\.enabled \|\| scheduler\.isRateLimited\(\)\)return false/);
  assert.match(scheduler,/MAX_ROUND_WALL_MS/);
  assert.match(scheduler,/setRequiresCharging\(false\)/);
  assert.match(scheduler,/setRequiresBatteryNotLow\(false\)/);
  assert.match(scheduler,/setRequiresStorageNotLow\(true\)/);
});

test('archive capacity grows without deleting sources or exhausting phone storage',()=>{
  assert.match(archive,/MAX_RECORDS=4096/);
  assert.match(archive,/MIN_FREE_BYTES=128L \* 1024L \* 1024L/);
  assert.match(archive,/require\(root\.usableSpace>=MIN_FREE_BYTES\)/);
  assert.match(archive,/require\(files\.size<MAX_RECORDS\)/);
  assert.match(archive,/MessageDigest\.getInstance\("SHA-256"\)/);
  assert.match(archive,/fun exportZip\(/);
  assert.match(archive,/fun importZip\(/);
});

test('training rejection identifies each failing holdout and does not promote unsafe candidates',()=>{
  const start=learning.indexOf('fun trainExtendedCurriculum(');
  const end=learning.indexOf('fun teachIntent(',start);
  assert.ok(start>=0&&end>start);
  const block=learning.slice(start,end);
  assert.match(block,/val blockers=checks\.filterNot/);
  assert.match(block,/Schutzprüfung nicht bestanden:/);
  assert.match(block,/if\(accepted\)saveModel\(candidate\.save\(\),marker\)/);
  assert.match(block,/Besitzer-Lektionen/);
  assert.match(learning,/Detaillierte Prüfungen/);
});

test('owner facts win, and retrieval is clearly labelled a non-verified excerpt',()=>{
  const start=ui.indexOf('private fun runContinuumDialogue(');
  const end=ui.indexOf('private fun beginNeuralCoach(',start);
  assert.ok(start>=0&&end>start);
  const block=ui.slice(start,end);
  assert.match(block,/if\(!turn\.grounded && request\.length in 6\.\.240/);
  assert.match(block,/archiveAnswer=archiveHit/);
  assert.match(block,/nicht unabhängig verifizierter/);
  assert.match(block,/document\.url/);
  assert.match(block,/turn\.grounded \|\| archiveAnswer!=null/);
  assert.doesNotMatch(block,/requestPermissions\(|ProcessBuilder\(/);
});

test('research and legal backup menu indices match their action labels',()=>{
  const start=ui.indexOf('private fun showLearningLabChoices(');
  const end=ui.indexOf('private fun showPublicStudyControls(',start);
  assert.ok(start>=0&&end>start);
  const block=ui.slice(start,end);
  assert.match(block,/6 -> showRisArchiveImport\(\)/);
  assert.match(block,/7 -> showArchiveBackupMenu\(\)/);
  assert.match(block,/8 -> showExtendedCurriculumDialog\(\)/);
});
