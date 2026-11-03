import test from 'node:test';import assert from 'node:assert/strict';
import {parseNativeGenerationCount} from './continuum-local-model-observation.mjs';
test('native pinned completion counters preserve actual timestamped format',()=>{
 assert.equal(parseNativeGenerationCount('0.11.333.480 I common_perf_print:        eval time =    9170.77 ms /   199 runs   (46.08 ms per token)\n'),199);
 assert.equal(parseNativeGenerationCount('llama_perf_context_print: eval time = 1.0 ms / 5 runs\n'),5);
 assert.equal(parseNativeGenerationCount('0.1 I common_perf_print: prompt eval time = 1.0 ms / 15 tokens\n'),null);
});
