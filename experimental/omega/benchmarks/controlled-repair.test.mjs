import test from 'node:test';
import assert from 'node:assert/strict';
import {runTrial,benchmark} from './controlled-repair.mjs';
test('controlled replay measures actual accepted patch in separate scoring worktree',
 {skip:Boolean(process.env.CONTINUUM_REPAIR_DEPTH)},()=>{
 const task={id:'development',old:'a+b',correct:'a>b?a-b:b-a',example:[1,3],expected:2,oracle:(a,b)=>Math.abs(a-b)};
 const baseline=runTrial(task,'baseline',0),vhsr=runTrial(task,'vhsr',0);
 assert.equal(baseline.accepted,true);assert.equal(baseline.falseAcceptance,true);
 assert.equal(vhsr.correct,true);assert.equal(vhsr.proposalCalls,2);assert.equal(vhsr.counterexamples,1);
 assert.equal(vhsr.independent.researchVerification.status,'PASS');
 assert.equal(baseline.sourceClean&&vhsr.sourceClean,true);
 assert.equal(vhsr.modelCalls,0);
});
test('benchmark repetition count is bounded',()=>{
 for(const repetitions of [0,4,Infinity,1.5])assert.throws(()=>benchmark({repetitions}),/REPETITION_LIMIT/);
});
