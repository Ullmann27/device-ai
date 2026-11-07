import test from 'node:test';
import assert from 'node:assert/strict';
import {runSharedStageExperiment} from './shared-stage-experiment.mjs';

test('3000 unique tasks reuse 30 shared heavy computation stages',()=>{
 const r=runSharedStageExperiment({jobs:3000,sourceVersions:30});
 assert.equal(r.uniqueFinalOutputs,3000);
 assert.equal(r.optimizedExpensiveStages,30);
 assert.equal(r.avoidedExpensiveStages,2970);
 assert.equal(r.heavyStageWorkRatio,100);
 assert.equal(r.correctAllOutputs,true);
});
test('counterexample: distinct source versions require all heavy computations',()=>{
 const r=runSharedStageExperiment({jobs:3000,sourceVersions:3000});
 assert.equal(r.uniqueFinalOutputs,3000);
 assert.equal(r.optimizedExpensiveStages,3000);
 assert.equal(r.avoidedExpensiveStages,0);
 assert.equal(r.correctAllOutputs,true);
});
test('bad stage parameter bounds are rejected',()=>{
 assert.throws(()=>runSharedStageExperiment({jobs:5,sourceVersions:6}),/Invalid experiment size/);
});
