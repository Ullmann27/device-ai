import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = path => readFileSync(path, 'utf8');
const registry = JSON.parse(read('docs/CONTINUUM-EXPERIMENTE.json'));
const catalog = read('docs/research/CONTINUUM-EXTERNAL-CODE-INTEGRATION-20261027.md');
const agents = read('AGENTS.md');
const readme = read('README.md');
const code = read('app/src/main/java/at/heinz/deviceai/cognitive/MemoryRetrievalResearchGate.kt');
const junit = read('app/src/test/java/at/heinz/deviceai/cognitive/MemoryRetrievalResearchGateTest.kt');

test('permanent research catalog belongs to canonical Continuum experiment registry', () => {
  const ext = registry.externalResearchIntegration;
  assert.equal(registry.project, 'Ullmann27/device-ai');
  assert.equal(ext.ownerRequested, true);
  assert.equal(ext.reviewPullRequest, 198);
  assert.equal(ext.parentReviewPullRequest, 197);
  assert.equal(ext.upstreamSourcesMapped, 15);
  assert.equal(ext.directCodeImport, false);
  assert.equal(ext.allExternalRuntimeIntegrationsVerified, false);
  assert.equal(ext.ownerMemoryPromotionAllowed, false);
  assert.equal(ext.mergeToMainAuthorized, false);
  assert.deepEqual(ext.relevantExperiments.sort(), [
    'EXP-00','EXP-02','EXP-04','EXP-05',
    'EXP-06','EXP-07','EXP-09','EXP-11'
  ]);
});
test('all 15 upstream candidates are anchored to source links and local interfaces', () => {
  const ids = [...catalog.matchAll(/^\| R-(\d\d) \|/gm)].map(match => Number(match[1]));
  assert.deepEqual(ids, Array.from({length:15}, (_,i) => i + 1));
  for (const upstream of [
    'google-ai-edge/gallery', 'NickMonrad/kernel-ai-assistant',
    'NeuroMarshal/LocalChat', 'mem0ai/mem0', 'agiresearch/A-mem',
    'asg017/sqlite-vec', 'xiaowu0162/LongMemEval-V2',
    'SWE-agent/mini-swe-agent', 'Aider-AI/aider',
    'OpenHands/OpenHands', 'termux/android-build-tools',
    'amirghm/android-sdk-tools-arm64', 'xiaowu0162/LongMemEval',
    'google-ai-edge/LiteRT-LM'
  ]) assert.ok(catalog.includes(upstream), upstream);
  for (const local of ['HybridMemoryRetriever.kt','MemoryEcology.kt',
    'ProofCarryingMemory.kt','CognitiveDecisionBridge.kt',
    'MemoryRetrievalResearchGate.kt','continuum-local-owner-apk.sh'
  ]) assert.ok(catalog.includes(local), local);
});
test('all developer entrypoints preserve integration mission', () => {
  for (const value of [agents, readme, catalog])
    assert.ok(value.includes('CONTINUUM-EXTERNAL-CODE-INTEGRATION-20261027.md') ||
      value === catalog, 'Missing research catalog link');
  assert.match(agents, /bestehende.*Gehirn|bestehenden.*Gehirn/i);
  assert.match(agents, /keine.*zweite.*Memory-DB/i);
  assert.match(catalog, /keine.*Fremdquellen kopiert/i);
});
test('research evaluation uses existing brain retrieval, never writes or runs tools', () => {
  assert.match(code, /HybridMemoryRetriever\.retrieve\(/);
  assert.match(code, /allowedPrivacy = allowedPrivacy/);
  assert.match(code, /availableIds\.containsAll\(it\.expectedMemoryIds\)/);
  assert.match(code, /expectedAbstention/);
  assert.match(code, /meanReciprocalRank/);
  assert.match(code, /durationNanos/);
  assert.doesNotMatch(code, /\b(?:Runtime\.getRuntime|ProcessBuilder|MemoryStore\.put|insertMemory|writeMemory|executeTool)\s*\(/);
  assert.ok(junit.includes('privateFactsMustNotEnterPublicBenchmarkAndIdentifiersMustBeValid'));
  assert.ok(junit.includes('retrievalOfAnyDistractorCountsAsFailedAbstention'));
  assert.ok(junit.includes('semanticRecallUsesExistingHybridRetrieverNotAnotherDatabase'));
});
test('the historical owner release gates have not been weakened by research files', () => {
  const gradle = read('app/build.gradle.kts');
  const owner = read('scripts/continuum-local-owner-apk.sh');
  assert.match(gradle, /deviceAiOwnerSignedUpdate/);
  assert.match(gradle, /applicationIdSuffix = "\.mentorpaired"/);
  assert.match(owner, /CONTINUUM_OWNER_LOCAL_APPROVED_SHA/);
  assert.match(owner, /CONTINUUM_MENTOR_SIGNING_FILE/);
  assert.match(owner, /expected_signer='2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc'/);
});
