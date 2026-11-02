import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const root=new URL('../../',import.meta.url);
const read=p=>readFileSync(new URL(p,root),'utf8');
const curriculum=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumCdxLargeCurriculum.kt');
const rounds=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumCdxLargeLab.kt');
const bridge=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumDecoderBridge.kt');
const core=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt');
const ui=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');

test('large CDX1 curriculum supplies 576 fictional pairs with 50 untouched audit cases',()=>{
  assert.match(curriculum,/const val STAGES=12/);
  assert.match(curriculum,/const val PAIRS_PER_STAGE=48/);
  assert.match(curriculum,/const val TOTAL_TRAIN=STAGES\*PAIRS_PER_STAGE/);
  assert.match(curriculum,/\(1\.\.48\)\.flatMap\(::cases\)/);
  assert.match(curriculum,/\(91\.\.94\)\.flatMap\(::cases\)/);
  assert.match(curriculum,/require\(training\.toSet\(\)\.intersect\(tests\.toSet\(\)\)\.isEmpty\(\)\)/);
  assert.match(curriculum,/require\(tests\.size==50/);
  assert.match(curriculum,/DecoderSplit\.AUDIT/);
});

test('a CDX1 candidate must pass novel AND historic audits before checkpoint promotion',()=>{
  assert.match(bridge,/protectedAudits:List<DecoderEvidenceCorpus>/);
  assert.match(bridge,/require\(protectedInputs\.size==protectedInputs\.toSet\(\)\.size/);
  assert.match(bridge,/protectedInputs\.none \{ it in trainingInputs \|\| it in heldoutInputs \}/);
  assert.match(bridge,/val protectedSafe=protectedAfter\.zip\(protectedBefore\)\.all/);
  assert.match(bridge,/auditAfter<=auditBefore &&/);
  // Accepted research weights are published with their exact SWB1 snapshot atomically.
  assert.match(bridge,/pairedBundles\.commit\(candidate\.save\(\),pairedEncoderSnapshot,/);
  assert.doesNotMatch(bridge,/if\(accepted\)candidate\.saveTo\(pairedCheckpoint\)/);
  assert.match(core,/protectedAudits:List<DecoderEvidenceCorpus>/);
  assert.match(rounds,/protectedAudits=listOf\(protected\)/);
});

test('CDX1 large rounds are owner-approved and persisted only on completed evaluation',()=>{
  assert.match(rounds,/private val stateFile=File\(filesDir,"learning-core-v2\/cdx-pairs-v1-stage\.txt"\)/);
  assert.match(rounds,/val finished=report\.completedSteps==curriculum\.TRAIN_STEPS && !cancelled\(\)/);
  assert.match(rounds,/if\(finished\)writeStage\(index\+1\)/);
  assert.match(rounds,/StandardCopyOption\.ATOMIC_MOVE/);
  assert.match(rounds,/check\(core\.modelHash\(\)==swbBefore\)/);
  assert.match(rounds,/val protected=DecoderEvidenceCorpus\(curriculum\.historicAudit,DecoderSplit\.AUDIT\)/);
  assert.doesNotMatch(rounds,/openConnection\(|ProcessBuilder|exec\(|requestPermissions/);
});

test('Android UI offers a clearly separated CDX1 large-lab action and reporting',()=>{
  assert.match(ui,/CDX1 · GROSSES PAARARCHIV · 12 STUFEN/);
  assert.match(ui,/fun showCdxLargeLabDialog\(/);
  assert.match(ui,/fun runCdxLargeLab\(/);
  assert.match(ui,/\.setPositiveButton\("NÄCHSTE STUFE PRÜFEN"\)/);
  assert.match(ui,/\.setNeutralButton\("GROSSES ARCHIV"\)/);
  assert.match(ui,/Chatfreigabe: NEIN/);
});
