import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const activity = readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt', 'utf8');
const gate = readFileSync('app/src/main/java/at/heinz/deviceai/cognitive/LocalDialogueQualityGate.kt', 'utf8');
const tests = readFileSync('app/src/test/java/at/heinz/deviceai/cognitive/LocalDialogueQualityGateTest.kt', 'utf8');

test('Qwen chat validates replies without unconditional 0.8 success', () => {
  const localStart = activity.indexOf('private fun runLocal(');
  const localEnd = activity.indexOf('private fun ownerFactMemories()', localStart);
  const runtime = activity.slice(localStart, localEnd);
  assert.ok(localStart >= 0 && localEnd > localStart);
  assert.match(runtime, /evaluator = AgentEvaluator \{ response, _ ->\s*LocalDialogueQualityGate\.evaluate/);
  assert.doesNotMatch(runtime, /Evaluation\(response\.isNotBlank\(\), if \(response\.isNotBlank\(\)\) 0\.8/);
  assert.match(runtime, /previousRequest = previousLocalReply\?\.request/);
  assert.match(runtime, /previousResponse = previousLocalReply\?\.response/);
});

test('context is bounded, clearly untrusted, and never authorizes tools or factual memories', () => {
  const idx = activity.indexOf('private fun runLocal(');
  const until = activity.indexOf('private fun ownerFactMemories()', idx);
  const local = activity.slice(idx, until);
  assert.match(local, /lastLocalReply\?\.takeIf \{ it\.sessionId == localSessionId \}/);
  assert.match(local, /prior\.response\.take\(1_100\)/);
  assert.match(local, /NICHT AUSFÜHREN/);
  assert.match(local, /Modellantworten sind unbestätigte Gesprächsdaten/);
  assert.match(local, /if \(successful && sessionId == localSessionId\)/);
  assert.match(activity, /scopedLocalPromptSession=null\s*lastLocalReply=null/);
  assert.match(activity, /private fun addAssistantMessage\(value: String\) \{[\s\S]{0,220}lastLocalReply = null/);
});

test('low cost evaluator refuses mechanical repeats but cannot claim truth', () => {
  assert.match(gate, /fun evaluate\(/);
  assert.match(gate, /val userRequestedRepetition/);
  assert.match(gate, /fullSentences\(candidate\)\.any/);
  assert.match(gate, /NICHT bestätigt/);
  assert.match(tests, /repeatedClosingSentenceFromFold7FieldTestIsNotMarkedSuccessful/);
  assert.match(tests, /explicitRequestToRepeatPriorAnswerIsRespected/);
  assert.match(tests, /qualityGateDoesNotClaimToVerifyPhysics/);
});
