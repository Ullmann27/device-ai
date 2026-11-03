import test from 'node:test';import assert from 'node:assert/strict';
import {parseNativeGenerationCount} from './continuum-local-model-observation.mjs';
test('native pinned completion counters preserve actual timestamped format',()=>{
 assert.equal(parseNativeGenerationCount('0.11.333.480 I common_perf_print:        eval time =    9170.77 ms /   199 runs   (46.08 ms per token)\n'),199);
 assert.equal(parseNativeGenerationCount('llama_perf_context_print: eval time = 1.0 ms / 5 runs\n'),5);
 assert.equal(parseNativeGenerationCount('0.1 I common_perf_print: prompt eval time = 1.0 ms / 15 tokens\n'),null);
});
import {normalizeNativeCodingOutput,serializeLocalCodingPrompt,localModelSandboxArgs} from './continuum-local-model-observation.mjs';
import {buildCodingRequest} from './continuum-local-brain.mjs';
const runtimeSha256='c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a';
const modelSha256='cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046';
test('only known native completion trailer is removed; raw hash input is unchanged',()=>{
 const raw=' {"plan":["fixture"]} [end of text]\n\n\n';
 assert.equal(normalizeNativeCodingOutput(raw,{runtimeSha256}),'{"plan":["fixture"]}');
 assert.equal(normalizeNativeCodingOutput(raw,{runtimeSha256:'f'.repeat(64)}),raw.trim());
 assert.equal(normalizeNativeCodingOutput('invalid text [end of text] extra\n',{runtimeSha256}),'invalid text [end of text] extra');
 assert.ok(raw.endsWith('[end of text]\n\n\n'));
});
test('official Qwen bytes select ChatML, with schema constraint supplied as inert data',()=>{
 const {body}=buildCodingRequest({userPrompt:'Synthetic source only',allowedTrackedPaths:['lib/a.mjs'],model:'display label'});
 const prompt=serializeLocalCodingPrompt(body,{modelSha256});
 assert.ok(prompt.startsWith('<|im_start|>system\n'));assert.ok(prompt.endsWith('<|im_start|>assistant\n'));
 assert.ok(prompt.includes('<|im_start|>user\nSynthetic source only'));
 const args=localModelSandboxArgs({schemaFd:7});
 assert.ok(args.includes('--json-schema-file'));assert.ok(args.includes('/schema.json'));
});
