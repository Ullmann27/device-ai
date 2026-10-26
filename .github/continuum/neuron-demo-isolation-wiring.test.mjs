import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const ui = read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const sandbox = read('app/src/main/java/at/heinz/deviceai/learning/NeuralDemoSandbox.kt');
const tests = read('app/src/test/java/at/heinz/deviceai/learning/NeuralDemoSandboxTest.kt');
const marker = '                    NeuralCommandKind.DEMO -> {';
const demo = ui.slice(ui.lastIndexOf(marker), ui.indexOf('                val bytes = if (save) lab.save() else null',ui.lastIndexOf(marker)));

test('actual Android DEMO arm only starts temporary sandbox and never persists', () => {
 assert.ok(ui.includes('import at.heinz.deviceai.learning.NeuralDemoSandbox'));
 assert.ok(ui.includes('command.kind == NeuralCommandKind.DEMO) NeuralDialogue()'));
 assert.ok(demo.includes('NeuralDemoSandbox.run(cancellation::get, progressCallback)'));
 assert.ok(demo.includes('NEURON · ISOLIERTE DEMO'));
 assert.ok(!demo.includes('lab.trainDemo('));
 assert.ok(!demo.includes('save = true'));
 assert.ok(!demo.includes('save = !'));
 assert.ok(!demo.includes('bootstrapMemory.compareAndSaveNeuralSnapshot'));
 assert.ok(!ui.includes('NEURON · DEMO nicht gestartet'));
});
test('real isolated helper never accepts restored production state or persists weights', () => {
 assert.match(sandbox, /val disposableModel = NeuralDialogue\(\)/);
 assert.match(sandbox, /return disposableModel\.trainDemo\(/);
 for (const forbidden of ['BootstrapMemory','filesDir','neuralSnapshot','save(','restore(','File(','compareAndSave']) {
  assert.ok(!sandbox.includes(forbidden), 'Unexpected side effect: ' + forbidden);
 }
});
test('native regression sources protect trained, empty and interrupted model states', () => {
 for(const identifier of [
   'previouslyTrainedNeuralWeightsAndLessonsAreNotTouched',
   'cancellationDoesNotTouchPriorWeightsOrPublishTemporaryModel',
   'sandboxDemoWorksWithNoStoredNeuralLessons'
 ]) assert.ok(tests.includes(identifier));
 assert.ok(tests.includes('assertArrayEquals(originalBytes, trained.save())'));
});
