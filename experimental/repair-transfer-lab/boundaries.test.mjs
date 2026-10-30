import test from 'node:test';
import assert from 'node:assert/strict';
import {generateHypotheses,testHypotheses,binomUpper,separatingFeature} from './boundaries.mjs';
const row=(task,kind,strategy,success,bucket='1',module='m')=>({task,kind,bucket,strategy,success,module});
function data(prefix,kind,strat,succ,fail,other){
 const out=[];let i=0;
 for(let k=0;k<succ;k++)out.push(row(prefix+i++,kind,strat,true));
 for(let k=0;k<fail;k++)out.push(row(prefix+i++,kind,strat,false));
 for(let k=0;k<other;k++)out.push(row(prefix+i++,'OTHER',strat,k%10===0));
 return out;
}
test('hypotheses come with their counterexamples and stay separate from confirmed findings',()=>{
 const train=data('t','NUM_OFF1','CONST:INC',6,4,40);
 const all=generateHypotheses(train);
 // the rare successes under OTHER also yield a DEMOTE hypothesis for that diagnosis
 assert.deepEqual(all.map(x=>x.direction+':'+x.kind).sort(),['DEMOTE:OTHER','PREFER:NUM_OFF1']);
 const h=all.find(x=>x.direction==='PREFER');
 assert.equal(h.status,'HYPOTHESIS');
 assert.equal(h.counterexamples.length,4,'the four training tasks where the preference was wrong');
});
test('only an independent re-measurement can confirm; the opposite outcome refutes',()=>{
 const h=generateHypotheses(data('t','NUM_OFF1','CONST:INC',6,4,40)).filter(x=>x.direction==='PREFER');
 assert.equal(testHypotheses(h,data('d','NUM_OFF1','CONST:INC',6,2,40))[0].status,'CONFIRMED');
 assert.equal(testHypotheses(h,data('d','NUM_OFF1','CONST:INC',0,8,40))[0].status,'REFUTED');
 assert.equal(testHypotheses(h,data('d','NUM_OFF1','CONST:INC',1,1,40))[0].status,'INCONCLUSIVE');
 assert.equal(h[0].status,'HYPOTHESIS','testing returns new objects; the stored hypothesis is not overwritten');
});
test('exact binomial upper tail and a separating feature',()=>{
 assert.ok(Math.abs(binomUpper(3,3,0.5)-0.125)<1e-12);
 assert.ok(Math.abs(binomUpper(0,5,0.3)-1)<1e-9);
 const rows=[...Array(5)].map((_,i)=>row('a'+i,'K','S',true,'1'))
  .concat([...Array(5)].map((_,i)=>row('b'+i,'K','S',false,'4+')));
 const f=separatingFeature({kind:'K',strategy:'S'},rows);
 assert.equal(f.feature,'bucket');assert.equal(f.high.value,'1');assert.equal(f.low.value,'4+');
});
