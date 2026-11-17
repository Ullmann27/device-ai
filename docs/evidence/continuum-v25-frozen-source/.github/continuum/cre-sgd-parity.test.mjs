import test from 'node:test';
import assert from 'node:assert/strict';
import {RelationalEncoder,features} from './cre-encoder.mjs';
import {sgdStep,trainParityRounds,SGD_PARITY_ROUNDS} from './cre-sgd-parity.mjs';

test('bounded CRE1 SGD changes model weights and restores versioned checkpoint',()=>{
 const m=new RelationalEncoder(42),old=m.save();
 const loss=trainParityRounds(m);
 assert.equal(loss.length,SGD_PARITY_ROUNDS.length);
 assert.ok(loss.every(Number.isFinite));
 assert.equal(m.steps,3);
 assert.notDeepEqual(m.save(),old);
 assert.deepEqual(RelationalEncoder.restore(m.save()).save(),m.save());
});

test('analytic CRE1 SGD gradients agree with finite-difference InfoNCE',()=>{
 const m=new RelationalEncoder(73),pair=SGD_PARITY_ROUNDS[0];
 const fg=features(pair.q),id=fg[0].id,index=id*32+5;
 const epsilon=1e-6,initial=m.E[index];
 m.E[index]=initial+epsilon;
 const upper=m.lossAndGrad(pair.q,pair.memories,0.2);
 m.E[index]=initial-epsilon;
 const lower=m.lossAndGrad(pair.q,pair.memories,0.2);
 m.E[index]=initial;
 const numeric=(upper-lower)/(2*epsilon),rate=0.0001;
 const loss=sgdStep(m,pair.q,pair.memories,rate,0.2);
 const actual=(initial-m.E[index])/rate;
 assert.ok(Number.isFinite(loss));
 assert.ok(Math.abs(numeric-actual)<1e-6,'CRE1 gradient mismatch '+numeric+' versus '+actual);
});

test('invalid CRE1 SGD steps reject before mutating weights',()=>{
 const m=new RelationalEncoder(7),old=m.save();
 for(const opts of [{memories:['one']},{rate:0},{rate:0.5},{tau:0.01}]){
  assert.throws(()=>sgdStep(m,'wann',opts.memories??['a','b'],opts.rate??0.0001,opts.tau??0.2));
  assert.deepEqual(m.save(),old);
 }
});
