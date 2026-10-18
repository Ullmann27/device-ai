import test from 'node:test';
import assert from 'node:assert/strict';
import {critique} from './repair-critic.mjs';

const protectedFiles=[
 '.github/continuum/m4-local-gate.mjs',
 '.github/continuum/m4-repair-e2e.mjs',
 '.github/continuum/m4-junit-evidence.mjs',
 '.github/continuum/m4-workflow-contract.test.mjs',
 '.github/continuum/cegis-intake.mjs',
 '.github/continuum/cegis-proposal-adapter.mjs',
 '.github/continuum/local-m4-android-gate.test.mjs',
 'scripts/local-m4-android.sh',
 'scripts/local-m4-release-gate.sh',
 '.github/workflows/continuum-branch-tests.yml'
];

function candidate(file){
 return {id:'proof-test',strategy:'CEGIS_EXPRESSION_SYNTHESIS',prior:0.9,
  edits:[{file,oldText:'export const result = 1;',
          newText:'export const result = 2;'}]};
}

test('an autonomous CEGIS proposal cannot edit gate, receipt, intake or launcher sources',()=>{
 for(const file of protectedFiles){
  const response=critique(candidate(file));
  assert.equal(response.approved,false,file);
  assert.ok(response.reasons.some(s=>s.includes('protected path')||
   s.includes('tests are the specification')),file+': '+JSON.stringify(response.reasons));
 }
});

test('ordinary JavaScript production fixes remain eligible for independent verification',()=>{
 const fix=critique(candidate('backend/src/calculate.mjs'));
 assert.equal(fix.approved,true,JSON.stringify(fix.reasons));
 const android=critique(candidate('app/src/main/java/at/heinz/deviceai/cognitive/Arithmetic.kt'));
 assert.equal(android.approved,true,JSON.stringify(android.reasons));
});

test('a generated coding candidate must not alter previously existing tests',()=>{
 const response=critique(candidate('backend/test/calculate.test.js'));
 assert.equal(response.approved,false);
 assert.match(response.reasons.join('; '),/tests are the specification/);
});
