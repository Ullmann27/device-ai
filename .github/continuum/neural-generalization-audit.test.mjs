import test from 'node:test';
import assert from 'node:assert/strict';
import {audit,MEMORIES,HOLDOUTS,TRAINING} from './neural-generalization-audit.mjs';

test('SWB2 holdout audit uses real training and separates exact training texts from evaluation',()=>{
 const trained=new Set(TRAINING.flatMap(x=>[x.left,x.right]));
 for(const c of HOLDOUTS)assert.ok(!trained.has(c.query));
 for(const m of MEMORIES)assert.ok(!trained.has(m.text));
 const r=audit();
 assert.equal(r.schema,1);assert.equal(r.model,'SWB2_EXPERIMENTAL');
 assert.equal(r.before.total,HOLDOUTS.length);
 assert.equal(r.after.total,HOLDOUTS.length);
 assert.equal(r.trainedHash.length,64);
 assert.notEqual(r.initialHash,r.trainedHash);
 assert.ok(r.falsePositives>=0&&r.falsePositives<=r.negativeQueries);
 assert.equal(typeof r.promotionEligible,'boolean');
});
