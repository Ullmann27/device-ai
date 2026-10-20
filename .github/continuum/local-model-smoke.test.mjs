import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt', import.meta.url), 'utf8');
const route = source.indexOf('if (request.equals("Continuum Modelltest", true))');
const mentor = source.indexOf('val groqQuestion = GroqInvocationRouter.extractQuestion(request)');
const start = source.indexOf('private fun runLocalSmokeTest()');
const end = source.indexOf('private fun runLocal(text: String', start);
const block = source.slice(start, end);

test('explicit owner command has precedence over all alternate chat routing', () => {
  assert.ok(route > 0);
  assert.ok(route < mentor);
  assert.match(source.slice(route, mentor), /runLocalSmokeTest\(\)/);
});
test('diagnostic uses isolated LiteRT-LM generation and preserves the real agent pipeline', () => {
  assert.match(block, /localRuntime\.streamThinking\(/);
  assert.match(block, /generateLocal\(cancellation\)/);
  assert.doesNotMatch(block, /runLocal\(|ChatReasoningRouter\s*\(|showGroqMentorDialog\s*\(|createToolGate\(|persistRequestedMemory\(|Runtime\.getRuntime|exec\(/);
});
test('the diagnostic requires a loaded model, blocks empty responses and surfaces failures', () => {
  assert.match(block, /localRuntime\.loadedModel == null/);
  assert.match(block, /LocalModelState\.READY/);
  assert.match(block, /generated\.response\.isNotBlank\(\)/);
  assert.match(block, /generated\.failure\?\.let/);
  assert.match(block, /MODELLTEST FEHLGESCHLAGEN/);
  assert.match(block, /activeChatCancellation !== cancellation/);
});
