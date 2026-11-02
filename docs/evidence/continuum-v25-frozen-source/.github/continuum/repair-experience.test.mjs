import test from 'node:test';
import assert from 'node:assert/strict';
import {summarizeExperiences,verifyExperienceFile} from './repair-experience.mjs';
test('learns outcomes of repeated repair attempts',()=>{
 const result=summarizeExperiences([{kind:'SET_TAKELAST',status:'COMPILES'},{kind:'SET_TAKELAST',status:'REJECTED'}]);
 assert.deepEqual(result,[{kind:'SET_TAKELAST',successes:1,failures:1}]);
});
test('rejects malformed history',()=>{
 assert.throws(()=>verifyExperienceFile({schemaVersion:1,experiences:[{kind:'x',successes:-1,failures:0}]}));
});
