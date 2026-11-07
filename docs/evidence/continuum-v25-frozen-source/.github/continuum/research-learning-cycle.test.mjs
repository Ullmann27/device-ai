import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import test from 'node:test';
import {RelationalEncoder} from './cre-encoder.mjs';
import {sha256Hex} from './development-backlog.mjs';
import {
  validateExtractiveSpans,splitVerifiedClaimsByHash,buildExtractiveContrastiveSet,
  trainFrozenProjectionCandidate,createSignedCre1Package,verifySignedCre1Package,
  runResearchLearningCycle
} from './research-learning-cycle.mjs';

const sourceTopics=[
  ['solar cell temperature efficiency',
    'Higher solar cell temperatures reduce measured solar conversion efficiency in controlled photovoltaic experiments.'],
  ['battery charging electrochemical storage',
    'Lithium battery cells store electrical energy through reversible electrochemical reactions during repeated charging cycles.'],
  ['water freezing crystal structure',
    'Water molecules form an ordered crystal structure when liquid samples freeze under controlled pressure conditions.'],
  ['forest soil carbon retention',
    'Forest soils retain organic carbon as fallen leaves decompose through microbial activity beneath the canopy.'],
  ['radio telescope distant hydrogen',
    'Radio telescopes detect emissions from distant hydrogen clouds using calibrated receivers and directional antenna arrays.'],
  ['bridge strain load measurement',
    'Bridge strain gauges measure deformation as calibrated vehicle loads pass across reinforced concrete support structures.'],
  ['protein folding amino acids',
    'Protein folding depends on interactions between amino acid residues within the surrounding aqueous chemical environment.'],
  ['volcanic ash atmospheric transport',
    'Volcanic ash particles travel through atmospheric layers according to wind direction and observed particle size distributions.']
];
function fixture(i) {
  // Distinct topics provide genuine negatives; numbered copies of one claim
  // are intentionally rejected by the near-duplicate supervision gate.
  const [query,claim]=sourceTopics[i%sourceTopics.length];
  const sourceText='Quelle '+i+'\n'+claim+'\nCopyright and evidence labels must be inspected separately.';
  return {query,claim,sourceText,
    sourceSha256:sha256Hex(Buffer.from(sourceText,'utf8')),
    url:'https://example.org/research/'+i,trainingPermitted:true};
}
function splitFixture() {
  const items=validateExtractiveSpans(Array.from({length:80},(_,i)=>fixture(i)));
  const {train,audit}=splitVerifiedClaimsByHash(items);
  assert.ok(train.length>=5&&audit.length>=3);
  return {train,audit};
}

test('metadata/LLM summaries cannot masquerade as extractive source training',()=>{
  const good=fixture(1);
  assert.equal(validateExtractiveSpans([good]).length,1);
  assert.throws(()=>validateExtractiveSpans([{...good,sourceSha256:'0'.repeat(64)}]),/SHA-256/);
  assert.throws(()=>validateExtractiveSpans([{...good,claim:'A fabricated claim that is not present in the source text and can never serve as a supported training target.'}]),/exact original/);
  assert.throws(()=>validateExtractiveSpans([{...good,trainingPermitted:false}]),/rights/);
  assert.throws(()=>validateExtractiveSpans([{...good,trainingPermitted:undefined}]),/rights/);
  assert.throws(()=>validateExtractiveSpans([{...good,url:'https://localhost:8443/private'}]),/provenance/);
  assert.throws(()=>validateExtractiveSpans([{...good,url:'https://127.0.0.1/source'}]),/provenance/);
  assert.throws(()=>validateExtractiveSpans([{...good,url:'https://router.local/source'}]),/provenance/);
  assert.throws(()=>validateExtractiveSpans([good,good]),/Duplicate/);
  const revision={...fixture(2),url:good.url};
  assert.throws(()=>validateExtractiveSpans([good,revision]),/source revisions/);
});

test('same original source never leaks into both training and sealed audit',()=>{
  const original=fixture(22);
  const other={...original,claim:'Eine weitere ausreichend lange technische Passage steht nicht im Originaldokument und muss deshalb verworfen werden.'};
  assert.throws(()=>validateExtractiveSpans([other]),/exact original/);
  const sibling=fixture(23);
  const sourceText=original.sourceText+'\n'+sibling.claim;
  const sourceSha256=sha256Hex(Buffer.from(sourceText,'utf8'));
  const validated=validateExtractiveSpans([
    {...original,sourceText,sourceSha256},
    {...sibling,sourceText,sourceSha256,url:original.url}
  ]);
  const {train,audit}=splitVerifiedClaimsByHash(validated);
  assert.equal(train.length+audit.length,2);
  assert.ok(train.length===2||audit.length===2);
  const two=splitFixture();
  assert.ok(two.train.every(a=>!two.audit.some(b=>a.sourceSha256===b.sourceSha256)));
  assert.equal(buildExtractiveContrastiveSet(two.train).length,two.train.length);
});

test('numbered near-duplicate claims cannot supply contrastive negatives',()=>{
  const rows=Array.from({length:4},(_,i)=>{
    const claim='Verifizierte Forschungsquelle Nummer '+i+
      ' beschreibt reproduzierbare Vergleichstests mit getrennten und überprüften Referenzdaten für sichere Modelle.';
    return {...fixture(i),claim,sourceText:claim,
      sourceSha256:sha256Hex(Buffer.from(claim,'utf8'))};
  });
  assert.deepEqual(buildExtractiveContrastiveSet(validateExtractiveSpans(rows)),[]);
});

test('training is research-only, leaves frozen E unchanged and rejects holdout failures',()=>{
  const {train,audit}=splitFixture();
  const baseline=new RelationalEncoder(73).save();
  const trainSet=buildExtractiveContrastiveSet(train).slice(0,5);
  const auditSet=buildExtractiveContrastiveSet(audit).slice(0,3);
  const result=trainFrozenProjectionCandidate(baseline,trainSet,auditSet,{epochs:1,learningRate:0.0003});
  assert.equal(result.trainBefore.total,5);
  assert.equal(result.auditBefore.total,3);
  assert.ok(typeof result.accepted==='boolean');
  assert.equal(result.modelSha256,sha256Hex(result.modelBytes));
  const old=RelationalEncoder.restore(baseline),candidate=RelationalEncoder.restore(result.modelBytes);
  assert.deepEqual(candidate.E,old.E);
  if(!result.accepted) assert.deepEqual(result.modelBytes,baseline);
});

test('signed CRE1 research package binds model, audit, publisher and canonical manifest',()=>{
  const baseline=new RelationalEncoder(73).save();
  const {privateKey,publicKey}=crypto.generateKeyPairSync('ec',{namedCurve:'prime256v1'});
  const report={accepted:true,steps:0,trainCount:10,auditCount:4,
    trainSourceCount:10,auditSourceCount:4,trainCorrect:7,auditCorrect:3};
  const pkg=createSignedCre1Package({
    modelBytes:baseline,auditReport:report,privateKeyPem:privateKey,createdAt:1750000000000
  });
  const verified=verifySignedCre1Package({
    modelBytes:pkg.modelBytes,auditBytes:pkg.auditBytes,manifestJson:pkg.manifestJson,publicKeyPem:publicKey
  });
  assert.equal(verified.format,'CRE1');
  const changedModel=Buffer.from(baseline);changedModel[100]^=1;
  assert.throws(()=>verifySignedCre1Package({
    modelBytes:changedModel,auditBytes:pkg.auditBytes,manifestJson:pkg.manifestJson,publicKeyPem:publicKey
  }),/manifest|hash/);
  assert.throws(()=>verifySignedCre1Package({
    modelBytes:pkg.modelBytes,auditBytes:Buffer.from('{}'),
    manifestJson:pkg.manifestJson,publicKeyPem:publicKey
  }),/manifest|hash/);
  const swapped=JSON.parse(pkg.manifestJson);swapped.version=2;
  assert.throws(()=>verifySignedCre1Package({
    modelBytes:pkg.modelBytes,auditBytes:pkg.auditBytes,
    manifestJson:JSON.stringify(swapped),publicKeyPem:publicKey
  }),/manifest|signature/);
  const stranger=crypto.generateKeyPairSync('ec',{namedCurve:'prime256v1'});
  assert.throws(()=>verifySignedCre1Package({
    modelBytes:pkg.modelBytes,auditBytes:pkg.auditBytes,
    manifestJson:pkg.manifestJson,publicKeyPem:stranger.publicKey
  }),/signature/);
});

test('PEM keys remain compatible and unsupported signing algorithms are rejected',()=>{
  const modelBytes=new RelationalEncoder(73).save();
  const auditReport={accepted:true,steps:0,trainCount:3,auditCount:3,
    trainSourceCount:3,auditSourceCount:3,trainCorrect:2,auditCorrect:2};
  const pair=crypto.generateKeyPairSync('ec',{namedCurve:'prime256v1'});
  const privateKeyPem=pair.privateKey.export({format:'pem',type:'pkcs8'});
  const publicKeyPem=pair.publicKey.export({format:'pem',type:'spki'});
  const pkg=createSignedCre1Package({modelBytes,auditReport,privateKeyPem});
  assert.equal(verifySignedCre1Package({...pkg,publicKeyPem}).format,'CRE1');
  for(const unsupported of [
    crypto.generateKeyPairSync('ec',{namedCurve:'secp384r1'}),
    crypto.generateKeyPairSync('ed25519')
  ]){
    assert.throws(()=>createSignedCre1Package({
      modelBytes,auditReport,privateKeyPem:unsupported.privateKey
    }),/P-256/);
    assert.throws(()=>verifySignedCre1Package({
      ...pkg,publicKeyPem:unsupported.publicKey
    }),/P-256/);
  }
  assert.throws(()=>createSignedCre1Package({
    modelBytes,auditReport,privateKeyPem:pair.publicKey
  }));
});

test('no training or signing occurs without enough independent original spans',()=>{
  const baseline=new RelationalEncoder(73).save();
  const status=runResearchLearningCycle({baselineBytes:baseline,spans:[fixture(1)]});
  assert.equal(status.accepted,false);
  assert.equal(status.reason,'INSUFFICIENT_INDEPENDENT_EXTRACTIVE_EVIDENCE');
  assert.equal(status.signedPackage,null);
  assert.throws(()=>createSignedCre1Package({
    modelBytes:baseline,auditReport:{accepted:false},privateKeyPem:'bogus'
  }),/Rejected/);
});
