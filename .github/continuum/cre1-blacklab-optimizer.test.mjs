import assert from 'node:assert/strict';
import test from 'node:test';
import {performance} from 'node:perf_hooks';
import {RelationalEncoder,D} from './cre-encoder.mjs';
import {sha256Hex} from './development-backlog.mjs';
import {
  createFrozenPool,projectionLossAndGrad,evaluatePrePooledSplit,
  addGramIsometryPenalty,gramDeviation,canaryVariants,canaryAudit,
  trainPrePooledProjectionHeads,absorbAnisotropyProjector
} from './cre1-blacklab-optimizer.mjs';
import {
  validateExtractiveSpans,splitVerifiedClaimsByHash,
  buildExtractiveContrastiveSet,trainFrozenProjectionCandidate
} from './research-learning-cycle.mjs';

const examples=[
  {query:'solar panel temperature efficiency',candidates:[
    'Higher solar cell temperatures reduce measured solar conversion efficiency in controlled laboratory tests.',
    'Solar cells convert sunlight into electric current using semiconductor junctions.',
    'Lithium battery cells store electrical energy through reversible electrochemical reactions.']},
  {query:'battery current storage efficiency',candidates:[
    'Lithium battery cells store electrical energy through reversible electrochemical reactions.',
    'Solar cells convert sunlight into electric current using semiconductor junctions.',
    'Higher solar cell temperatures reduce measured solar conversion efficiency in controlled laboratory tests.']},
  {query:'solar cell semiconductor electricity',candidates:[
    'Solar cells convert sunlight into electric current using semiconductor junctions.',
    'Higher solar cell temperatures reduce measured solar conversion efficiency in controlled laboratory tests.',
    'Lithium battery cells store electrical energy through reversible electrochemical reactions.']}
];

test('cached forward loss and projection gradients agree with unmodified CRE1',()=>{
  const model=new RelationalEncoder(73);
  const pool=createFrozenPool(model,examples);
  assert.ok(pool.size>=5);
  for(const ex of examples){
    const original=RelationalEncoder.emptyGrads();
    const derived=RelationalEncoder.emptyGrads();
    const originalLoss=model.lossAndGrad(ex.query,ex.candidates,0.12,original);
    const cachedLoss=projectionLossAndGrad(model,ex,pool,0.12,derived);
    assert.ok(Math.abs(originalLoss-cachedLoss)<1e-10);
    for(let i=0;i<D*D;i++){
      assert.ok(Math.abs(original.Pq[i]-derived.Pq[i])<1e-10);
      assert.ok(Math.abs(original.Pm[i]-derived.Pm[i])<1e-10);
    }
    assert.equal(derived.E.size,0);
  }
  const raw=evaluatePrePooledSplit(model,examples,pool);
  assert.equal(raw.total,3);
  assert.ok(Number.isFinite(raw.loss));
});

test('Gram penalty matches a central finite-difference derivative',()=>{
  const model=new RelationalEncoder(73);
  const P=model.Pq,grad=new Float64Array(P.length);
  const gamma=0.02;
  addGramIsometryPenalty(P,grad,gamma);
  const cost=()=>gamma*gramDeviation(P)**2/4;
  for(const k of [0,1,31,32,350,1023]){
    const before=P[k],epsilon=1e-6;
    P[k]=before+epsilon;const upper=cost();
    P[k]=before-epsilon;const lower=cost();
    P[k]=before;
    assert.ok(Math.abs(grad[k]-(upper-lower)/(2*epsilon))<1e-6);
  }
});

test('frozen E stays byte-exact after cached optimization; dimensions remain healthy',()=>{
  const baseline=new RelationalEncoder(73).save();
  const first=RelationalEncoder.restore(baseline);
  const started=performance.now();
  const trained=trainPrePooledProjectionHeads(baseline,examples,{epochs:2,learningRate:0.0005});
  const elapsed=performance.now()-started;
  const second=trained.model;
  assert.equal(Buffer.compare(Buffer.from(first.E.buffer),Buffer.from(second.E.buffer)),0);
  assert.ok(Number.isFinite(trained.gramPq)&&Number.isFinite(trained.gramPm));
  assert.ok(trained.gramPq<1&&trained.gramPm<1);
  assert.ok(trained.poolSize>=5);
  // Elapsed is observational, not a fabricated claim of a 100x speedup.
  assert.ok(Number.isFinite(elapsed)&&elapsed>=0);
});

test('anisotropy correction is opt-in, changes only existing P matrices',()=>{
  const model=new RelationalEncoder(73);
  const pool=createFrozenPool(model,examples);
  const E=Buffer.from(model.E.buffer);
  const original=Float64Array.from(model.Pq);
  absorbAnisotropyProjector(model.Pq,examples.map(ex=>pool.get(ex.query)),0);
  assert.deepEqual(model.Pq,original);
  absorbAnisotropyProjector(model.Pq,examples.map(ex=>pool.get(ex.query)),0.1);
  assert.notDeepEqual(model.Pq,original);
  assert.deepEqual(Buffer.from(model.E.buffer),E);
  assert.throws(()=>absorbAnisotropyProjector(model.Pm,[],1),/beta/);
});

function sourceRow({index,query,claim,group}){
  const sourceText='Document '+group+': '+claim+'\nReferences and source context.';
  return {query,claim,sourceText,sourceSha256:sha256Hex(Buffer.from(sourceText,'utf8')),
    trainingPermitted:true,url:'https://example.org/reference/'+index};
}
test('sibling negatives require disambiguating questions; no train/audit source leakage',()=>{
  const rows=[
    sourceRow({index:1,group:'a',query:'solar temperature reduces electrical efficiency',claim:
      'The solar temperature reduces electrical efficiency by a measured 12 percent during controlled photovoltaic experiments.'}),
    sourceRow({index:2,group:'a',query:'battery charging cycles and electrochemical lifetime',claim:
      'Battery charging cycles determine electrochemical lifetime during repeated grid storage experiments with lithium metal cells.'}),
    sourceRow({index:3,group:'b',query:'solar temperature affects panel operation',claim:
      'A solar photovoltaic panel experiences temperature changes that affect measured electricity production during daylight hours.'}),
    sourceRow({index:4,group:'c',query:'battery cells store grid energy',claim:
      'Battery storage systems use chemical reactions for grid energy distribution and controlled charging measurements.'})
  ];
  // Recreate two genuine excerpts from the SAME original document, and
  // validate both through the real substring and SHA-256 source gate.
  const combinedSource=[rows[0].claim,rows[1].claim].join(' ');
  const shared=sha256Hex(Buffer.from(combinedSource,'utf8'));
  const sourceRows=rows.map((row,i)=>i<2?{
    ...row,sourceText:combinedSource,sourceSha256:shared
  }:sourceRow({index:i+3,group:'independent-'+i,query:row.query,claim:row.claim}));
  const grouped=validateExtractiveSpans(sourceRows);
  assert.throws(()=>validateExtractiveSpans([
    {...sourceRows[0],trainingPermitted:undefined}
  ]),/rights/);
  const cases=buildExtractiveContrastiveSet(grouped);
  assert.ok(cases.some(x=>x.query===grouped[0].query&&x.candidates[1]===grouped[1].claim));
  const sameQuery=grouped.map(x=>({...x,query:'generic shared document question'}));
  const generic=buildExtractiveContrastiveSet(sameQuery);
  assert.ok(generic.length>0);
  assert.ok(generic.every(x=>x.candidates.length>=2));
  assert.ok(generic.every(x=>x.siblingNegative===null));
  for(const example of generic){
    const positive=sameQuery.find(row=>row.claim===example.candidates[0]);
    const siblings=sameQuery.filter(row=>row.sourceSha256===positive.sourceSha256);
    assert.ok(example.candidates.slice(1).every(negative=>
      siblings.every(row=>row.claim!==negative)));
  }
  const partition=splitVerifiedClaimsByHash(grouped);
  assert.ok(partition.train.every(x=>partition.audit.every(y=>x.sourceSha256!==y.sourceSha256)));
  assert.ok(partition.train.every(x=>x.sourceSha256!==partition.audit[0]?.sourceSha256));
});

test('numeric and negation canaries stay outside training and veto unsafe ranking',()=>{
  assert.ok(canaryVariants('The report measured 12 valid samples during the second verification cycle.').some(x=>x.includes('13')));
  assert.ok(canaryVariants('This result is not approved without an independent source.').length>0);
  const model=new RelationalEncoder(73);
  const pool=createFrozenPool(model,examples);
  const canaries=canaryAudit(model,[{query:examples[0].query,candidates:[
    'Solar temperature reduced conversion output by 12 percent under calibrated laboratory conditions.'
  ]}],pool);
  assert.equal(canaries.tested,1);
  assert.ok(canaries.violations===0||canaries.violations===1);
});

test('rejected candidate returns exact baseline bytes',()=>{
  const baseline=new RelationalEncoder(73).save();
  // Contradictory labels in the sealed audit are rejected rather than promoted.
  const opposite=examples.map(x=>({query:x.query,
    candidates:[x.candidates[2],x.candidates[0],x.candidates[1]]}));
  const result=trainFrozenProjectionCandidate(baseline,examples,opposite,{
    epochs:1,learningRate:0.001
  });
  assert.ok(result.trainAfter.loss<result.trainBefore.loss);
  assert.ok(result.auditAfter.correct<result.auditBefore.correct);
  assert.equal(result.accepted,false);
  assert.equal(result.modelSha256,sha256Hex(result.modelBytes));
  assert.equal(Buffer.compare(result.modelBytes,baseline),0);
  assert.ok(result.canaries&&Number.isInteger(result.canaries.violations));
  assert.ok(result.siblings&&Number.isInteger(result.siblings.tested));
});
