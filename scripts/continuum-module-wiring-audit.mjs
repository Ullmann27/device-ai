#!/usr/bin/env node
/**
 * Static wiring inventory for the owner APK source tree.
 * This is a source-level audit, never proof of successful runtime inference,
 * mobile installation, training quality or signing.
 */
import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const file = relative => {
  const absolute = resolve(root, relative);
  if (!existsSync(absolute)) return null;
  return readFileSync(absolute, 'utf8');
};
const test = (name, files, evidence) => ({
  name,
  source_present: files.every(path => file(path) !== null),
  conditions: evidence.map(([path, expression, meaning]) => ({
    meaning,
    matched: expression.test(file(path) ?? '')
  }))
});
const base = 'app/src/main/java/at/heinz/deviceai/';
const learning = base + 'learning/';
const checks = [
  test('SWB1 memory and deterministic dialogue',
    [learning + 'ContinuumLearningCore.kt', learning + 'ContinuumCognitiveRuntime.kt', base + 'AgentActivity.kt'],
    [
      [base + 'AgentActivity.kt', /CognitiveDecisionBridge\(/, 'Main chat calls cognitive bridge'],
      [learning + 'ContinuumLearningCore.kt', /replyWithEvidenceInConversation\(/, 'SWB1 dialogue with evidence exists']
    ]),
  test('LiteRT language model import and conditional chat route',
    [base + 'AgentActivity.kt', base + 'localmodel/LocalModelCatalog.kt'],
    [
      [base + 'AgentActivity.kt', /LocalFirstPolicy\.afterMemory\(/, 'Chat route selects local model after memory'],
      [base + 'AgentActivity.kt', /runLocalSmokeTest\(/, 'Tool-free model test exists']
    ]),
  test('CRE1 research and auto dialogue opt-in',
    [learning + 'Cre1ResearchLab.kt', learning + 'ContinuumAutoDialogueCurriculum.kt',
      learning + 'ContinuumAutoDialogueResearch.kt', learning + 'ContinuumAutonomousScheduler.kt'],
    [
      [learning + 'ContinuumAutoDialogueResearch.kt', /setEnabled\(/, 'Explicit owner enable/disable API'],
      [learning + 'ContinuumAutonomousScheduler.kt', /ContinuumAutoDialogueLab\(/, 'Existing bounded daily scheduler runs a dialogue round'],
      [learning + 'Cre1ResearchLab.kt', /after\s*\+\s*1e-9\s*<\s*before/, 'Independent CRE1 audit must improve']
    ]),
  test('CDX1 guarded offline research',
    [learning + 'ContinuumDualCoreAutoStudy.kt', learning + 'ContinuumCdxLargeLab.kt'],
    [
      [learning + 'ContinuumDualCoreAutoStudy.kt', /shouldStart\(/, 'Bounded optional research stage'],
      [learning + 'ContinuumCdxLargeLab.kt', /trainExperimentalPairedDecoder\(/, 'Separate CDX1 training path']
    ]),
  test('LM1 research runtime (not owner chat)',
    [learning + 'lm/LmRuntime.kt', learning + 'lm/LmModel.kt'],
    [
      [learning + 'lm/LmRuntime.kt', /fun\s+generate\(/, 'LM1 generation API exists'],
      [base + 'AgentActivity.kt', /(?:import\s+at\.heinz\.deviceai\.learning\.lm\.LmRuntime|LmRuntime\s*\()/, 'LM1 called by owner Android chat']
    ]),
  test('Standalone GGUF / local context scripts',
    ['scripts/continuum-gguf.mjs', 'scripts/continuum-local-context.mjs'],
    [
      ['scripts/continuum-gguf.mjs', /./, 'Standalone GGUF helper exists'],
      [base + 'AgentActivity.kt', /continuum-gguf\.mjs/, 'Android chat directly invokes GGUF helper']
    ]),
  test('On-device job is finite and resource bounded',
    [learning + 'ContinuumAutonomousScheduler.kt', learning + 'ContinuumOfflineLearningBudget.kt'],
    [
      [learning + 'ContinuumOfflineLearningBudget.kt', /MAX_WALL_MILLIS\s*=\s*15_000L/, '15 second per-job wall bound'],
      [learning + 'ContinuumOfflineLearningBudget.kt', /MIN_BATTERY_PERCENT\s*=\s*20/, 'Battery floor present']
    ])
];
const errors = checks.filter(x => !x.source_present || x.conditions.some(y => !y.matched && ![
  'LM1 called by owner Android chat',
  'Android chat directly invokes GGUF helper'
].includes(y.meaning)));
console.log(JSON.stringify({
  kind: 'STATIC_SOURCE_INVENTORY_NOT_DEVICE_TEST',
  branch_commit: 'Report does not assert any signed release',
  modules: checks.map(x => ({
    name: x.name,
    sources_present: x.source_present,
    evidence: x.conditions
  })),
  all_required_static_invariants_met: errors.length === 0,
  failures: errors.map(x => x.name)
}, null, 2));
if (errors.length) process.exitCode = 1;
