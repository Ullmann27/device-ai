import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = filename => readFileSync(new URL('../../' + filename, import.meta.url), 'utf8');
const source = read('app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyScheduler.kt');
const states = read('app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyCycleResult.kt');
const kotlinTests = read('app/src/test/java/at/heinz/deviceai/learning/ContinuumPublicStudyCycleResultTest.kt');

test('unavailable encyclopedia extracts are explicitly classified and never learned', () => {
  assert.match(source, /internal class WikimediaSourceUnavailable/);
  assert.match(source, /extract \?: throw WikimediaSourceUnavailable\(if\(missing\)"MISSING_PAGE" else "EMPTY_EXCERPT"\)/);
  assert.match(source, /catch \(_:IllegalArgumentException\) \{\s*throw WikimediaSourceUnavailable\(\)/);
  assert.match(states, /error is WikimediaSourceUnavailable -> "SOURCE_UNAVAILABLE_"\+error.reason/);
  assert.match(source, /e !is WikimediaSourceUnavailable/);
  assert.match(source, /ContinuumSourceNovelty\.pending\(listOf\(source\)/);
});

test('failure reports identify safe operation and may not become accepted after separate stage', () => {
  assert.match(source, /var operation="SELECT_TOPIC"/);
  for (const phase of ['FETCH_SOURCE','ARCHIVE_SOURCE','INDEX_SOURCE','TRAIN_SOURCE',
    'AUDIT_SOURCE','SAVE_EVIDENCE']) {
    assert.ok(source.includes('operation="'+phase+'"'), 'missing '+phase);
  }
  assert.match(states, /"FAILED_"\+operation\+"_"\+error\.javaClass\.simpleName/);
  assert.match(source, /failurePhase\(operation,e,cancelled\(\)\)/);
  assert.match(source, /ContinuumPublicStudyCycleResult\.outcome\(phase\)/);
  assert.match(states, /val primary = phase\.substringBefore\(';'\)/);
  assert.match(states, /primary\.startsWith\("FAILED_"\) -> "FAILED"/);
  assert.doesNotMatch(source, /phase\.contains\("ACCEPTED"\) -> "ACCEPTED"/);
  assert.match(kotlinTests, /anAcceptedLocalStageDoesNotReclassifyFailedResearch/);
});

test('research attempts and pending topics remain truthful when a source has failed', () => {
  assert.match(source, /ContinuumPublicStudyCycleResult\.markTopicCompleted\(phase\)/);
  assert.match(states, /return primary in setOf\("TRAINING_ACCEPTED", "TRAINING_REJECTED", "SOURCE_UNCHANGED"/);
  assert.match(source, /phase="FAILED_SAVE_CYCLE_"/);
  assert.match(source, /"Research cycle audit not persisted"/);
  assert.match(source, /if\(sourceAdded\)edit\.putLong\("sources"/);
  assert.match(source, /if\(weightsAccepted\)edit\.putLong\("accepted"/);
});

test('issue #76: an interrupt flag never loses the cycle audit, and a failed write carries a fixed cause', () => {
  assert.doesNotMatch(source, /check\(edit\.commit\(\)\)/);
  assert.match(source, /ContinuumStudyAuditWrite\.commit\(\{ edit\.commit\(\) \},stopRequested,/);
  assert.match(source, /\?\.let \{ throw ContinuumStudyAuditNotPersisted\(it\) \}/);
  assert.match(source, /if\(reason=="STOPPED"\)phase="INTERRUPTED"/);
  assert.match(source, /reschedule=false\s*\n\s*Log\.w\("ContinuumPublicStudy","Research cycle audit not persisted"\)/);
  assert.match(states, /internal object ContinuumStudyAuditWrite/);
  assert.match(states, /"STORAGE_FULL"/);
  assert.match(states, /"NOT_WRITABLE"/);
  assert.match(states, /"COMMIT_FALSE"/);
});

test('safety budget, source isolation and holdout gate are unchanged', () => {
  assert.match(source, /conn\.instanceFollowRedirects=false/);
  assert.match(source, /MAX_RESPONSE_BYTES/);
  assert.match(source, /MAX_ROUND_WALL_MS/);
  assert.match(source, /MentorSourceValidation\.checkSource\(result\)/);
  assert.match(source, /ContinuumKnowledgeArchive\.Kind\.WIKIPEDIA_EXCERPT/);
  assert.match(source, /core\.learnFromSources\(pending,cancelled\)/);
  assert.match(source, /outcome\?\.completedEvaluation==true && !cancelled\(\)/);
  assert.match(source, /ContinuumOfflineLearningBudget\.deferralReason\(continuumLearningResources\(\)\)/);
  assert.match(source, /ContinuumLearningRunBudget/);
});
