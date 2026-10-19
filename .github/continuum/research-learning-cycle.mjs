/**
 * Research-only CRE1 training gate. This module cannot promote a model.
 * Bibliographic citations, LLM summaries, and [1] footnotes are not training evidence.
 * Each training span must match an original, license-cleared source byte-for-byte.
 */
import crypto from 'node:crypto';
import net from 'node:net';
import {RelationalEncoder, D} from './cre-encoder.mjs';
import {canonical, sha256Hex} from './development-backlog.mjs';
import {createFrozenPool, evaluatePrePooledSplit, canaryAudit,
  trainPrePooledProjectionHeads} from './cre1-blacklab-optimizer.mjs';

const HEX=/^[a-f0-9]{64}$/;
const HTTPS=/^https:\/\//i;
const KEYS=['accepted','auditSha256','createdAt','format','modelBytes','modelSha256','schema','version'];
const byteHash=value=>sha256Hex(Buffer.from(value,'utf8'));

export function validateExtractiveSpans(spans){
  if(!Array.isArray(spans)||spans.length>512)throw Error('Expected at most 512 source spans');
  const seen=new Set();
  return spans.map((row,index)=>{
    if(!row||typeof row!=='object'||Array.isArray(row))throw Error('Invalid source span: '+index);
    const {query,claim,sourceText,sourceSha256,url,trainingPermitted}=row;
    if(trainingPermitted!==true)throw Error('Source training rights not approved');
    if(typeof query!=='string'||query.length<8||query.length>240||
       typeof claim!=='string'||claim.length<45||claim.length>500||
       typeof sourceText!=='string'||sourceText.length>50000||
       typeof sourceSha256!=='string'||!HEX.test(sourceSha256)||
       sourceSha256!==byteHash(sourceText))throw Error('Source payload or SHA-256 invalid');
    const location=(()=>{try{return new URL(url)}catch{return null}})();
    const host=location?.hostname?.toLowerCase().replaceAll('[','').replaceAll(']','')??'';
    const excluded=['localhost','local','internal','invalid','test'];
    const isLocalHost=excluded.some(part=>host===part||host.endsWith('.'+part));
    if(!location||!HTTPS.test(url)||location.protocol!=='https:'||location.username||
       location.password||location.port||String(url).length>1000||
       net.isIP(host)!==0||!host.includes('.')||isLocalHost)
      throw Error('Invalid public HTTPS provenance');
    if(!sourceText.includes(claim)||claim.trim()!==claim||
       claim.split(/\s+/).length<7)throw Error('Claim must be an exact original source span');
    const id=byteHash(sourceSha256+'\0'+query+'\0'+claim);
    if(seen.has(id))throw Error('Duplicate training span');
    seen.add(id);
    return Object.freeze({id,sourceSha256,query,claim,url:location.href});
  });
}

/** Split by independent SOURCE hash, never claim hash: same document cannot leak into audit. */
export function splitVerifiedClaimsByHash(spans){
  const train=[],audit=[];
  for(const row of spans){
    if(!HEX.test(row.sourceSha256))throw Error('Invalid source hash in split');
    const nibble=parseInt(row.sourceSha256.at(-1),16);
    (nibble<12?train:audit).push(row);
  }
  return {train,audit};
}

/** Rank eligible negatives from other source documents by topical overlap.
 * A near-duplicate claim risks false-negative supervision, so veto it.
 * Fall back to unrelated documents only when there are too few topical ones.
 */
export function buildExtractiveContrastiveSet(rows){
  if(!Array.isArray(rows)||rows.length<3)return [];
  const words=value=>new Set(String(value).toLocaleLowerCase('de-DE')
    .split(/[^\p{L}\p{N}]+/u).filter(x=>x.length>=4));
  const overlap=(left,right)=>{
    const a=words(left),b=words(right);
    if(!a.size||!b.size)return 0;
    let shared=0;for(const x of a)if(b.has(x))shared++;
    return shared/Math.max(1,Math.min(a.size,b.size));
  };
  return rows.map(row=>{
    const ranked=rows.filter(other=>
      other.id!==row.id&&other.sourceSha256!==row.sourceSha256&&
      other.claim!==row.claim&&overlap(row.claim,other.claim)<0.9
    ).map(other=>({
      other,
      similarity:overlap(row.query,other.query)*2+overlap(row.claim,other.claim)
    })).sort((a,b)=>b.similarity-a.similarity||
      a.other.id.localeCompare(b.other.id));
    const negatives=[],used=new Set([row.claim]);
    // Same-document sibling negatives are safe only when the question
    // distinguishes this exact claim from the sibling. A generic, identical
    // document question would otherwise assign contradictory training labels.
    const ownMatch=overlap(row.query,row.claim);
    const siblings=rows.filter(other=>
      other.id!==row.id&&other.sourceSha256===row.sourceSha256&&
      other.query!==row.query&&other.claim!==row.claim&&
      overlap(row.claim,other.claim)<0.9&&
      ownMatch>=0.2&&ownMatch-overlap(row.query,other.claim)>=0.06
    ).sort((a,b)=>overlap(row.query,b.claim)-overlap(row.query,a.claim)||
      a.id.localeCompare(b.id));
    if(siblings.length){
      negatives.push(siblings[0].claim);
      used.add(siblings[0].claim);
    }
    for(const item of ranked){
      if(used.has(item.other.claim))continue;
      used.add(item.other.claim);
      negatives.push(item.other.claim);
      if(negatives.length>=3)break;
    }
    return negatives.length>=2?{query:row.query,candidates:[row.claim,...negatives]}:null;
  }).filter(Boolean);
}

export function evaluateContrastiveSplit(model,examples,tau=0.12){
  if(!Array.isArray(examples)||!examples.length)return {correct:0,total:0,mrr:0,loss:null};
  let correct=0,mrr=0,loss=0;
  for(const ex of examples){
    const value=model.lossAndGrad(ex.query,ex.candidates,tau);
    if(!Number.isFinite(value))throw Error('Nonfinite contrastive loss');
    loss+=value;
    const ranked=ex.candidates.map((c,i)=>({i,score:model.score(ex.query,c)}))
      .sort((a,b)=>b.score-a.score||a.i-b.i);
    const rank=ranked.findIndex(x=>x.i===0)+1;
    if(rank===1)correct++;
    mrr+=1/rank;
  }
  return {correct,total:examples.length,mrr:mrr/examples.length,loss:loss/examples.length};
}

/**
 * Frozen embedding table; only the two existing projection matrices are updated.
 * This is projection-only adaptation, NOT a genuine low-rank LoRA layer.
 * A failed holdout keeps the untouched baseline bytes.
 */
export function trainFrozenProjectionCandidate(baselineBytes,train,audit,{
  epochs=4,learningRate=0.001,tau=0.12,anchorLambda=0.1,
  isoGamma=0.02,anisotropyBeta=0,deadlineAt=null
}={}){
  if(!Array.isArray(train)||!Array.isArray(audit)||train.length<3||audit.length<3||
     train.length>384||audit.length>128)throw Error('Independent train/audit sets required');
  // Both baseline and candidate use exactly the same frozen pooling vectors.
  const baseline=RelationalEncoder.restore(baselineBytes);
  const pool=createFrozenPool(baseline,[...train,...audit]);
  const trainBefore=evaluatePrePooledSplit(baseline,train,pool,tau);
  const auditBefore=evaluatePrePooledSplit(baseline,audit,pool,tau);
  const trained=trainPrePooledProjectionHeads(baselineBytes,train,{
    epochs,learningRate,tau,anchorLambda,isoGamma,anisotropyBeta,deadlineAt
  });
  const candidate=trained.model;
  const trainAfter=evaluatePrePooledSplit(candidate,train,pool,tau);
  const auditAfter=evaluatePrePooledSplit(candidate,audit,pool,tau);
  const canaries=canaryAudit(candidate,audit,pool);
  const accepted=trainAfter.loss+1e-8<trainBefore.loss&&
    auditAfter.correct>=auditBefore.correct&&
    auditAfter.mrr+1e-12>=auditBefore.mrr&&
    auditAfter.loss<=auditBefore.loss+0.005&&
    // ||P^T P-I||_F < 1 forces every Gram eigenvalue to stay positive.
    Number.isFinite(trained.gramPq)&&Number.isFinite(trained.gramPm)&&
    trained.gramPq<1&&trained.gramPm<1&&
    canaries.violations===0;
  const modelBytes=accepted?candidate.save():Buffer.from(baselineBytes);
  return {accepted,trainBefore,trainAfter,auditBefore,auditAfter,
    canaries,poolSize:trained.poolSize,gramPq:trained.gramPq,gramPm:trained.gramPm,
    modelBytes,modelSha256:sha256Hex(modelBytes),
    steps:accepted?candidate.steps:baseline.steps};
}

const AUDIT_KEYS=['accepted','auditCorrect','auditCount','auditSourceCount',
  'steps','trainCorrect','trainCount','trainSourceCount'];
function validatePromotionAudit(audit,modelBytes){
  if(!audit||typeof audit!=='object'||Array.isArray(audit)||
     Object.keys(audit).sort().join('|')!==AUDIT_KEYS.join('|')||
     audit.accepted!==true)throw Error('Incomplete or rejected promotion audit');
  const model=RelationalEncoder.restore(modelBytes);
  for(const key of AUDIT_KEYS.filter(x=>x!=='accepted')){
    if(!Number.isSafeInteger(audit[key])||audit[key]<0)throw Error('Invalid audit integer: '+key);
  }
  if(audit.steps!==model.steps||audit.trainCount<3||audit.trainCount>384||
     audit.auditCount<3||audit.auditCount>128||
     audit.trainSourceCount<3||audit.trainSourceCount>audit.trainCount||
     audit.auditSourceCount<3||audit.auditSourceCount>audit.auditCount||
     audit.trainCorrect>audit.trainCount||audit.auditCorrect>audit.auditCount)
    throw Error('Promotion audit/model mismatch');
  return audit;
}

function checkEcKey(input,kind){
  const key=kind==='private'?crypto.createPrivateKey(input):crypto.createPublicKey(input);
  const curve=key.asymmetricKeyDetails?.namedCurve;
  if(key.asymmetricKeyType!=='ec'||!['prime256v1','P-256'].includes(curve))throw Error('P-256 key required');
  return key;
}
export function createSignedCre1Package({modelBytes,auditReport,privateKeyPem,createdAt=Date.now()}){
  if(!auditReport?.accepted)throw Error('Rejected model cannot be signed');
  if(!Number.isSafeInteger(createdAt)||createdAt<0)throw Error('Invalid signed time');
  validatePromotionAudit(auditReport,modelBytes);
  const auditBytes=Buffer.from(canonical(auditReport),'utf8');
  const payload={schema:'continuum-signed-cre1-package',version:1,format:'CRE1',
    modelSha256:sha256Hex(modelBytes),modelBytes:modelBytes.length,
    auditSha256:sha256Hex(auditBytes),accepted:true,createdAt};
  const signatureBase64=crypto.sign('sha256',Buffer.from(canonical(payload),'utf8'),
    checkEcKey(privateKeyPem,'private')).toString('base64');
  return {manifestJson:canonical({...payload,signatureBase64})+'\n',auditBytes,
    modelBytes:Buffer.from(modelBytes)};
}
export function verifySignedCre1Package({modelBytes,auditBytes,manifestJson,publicKeyPem}){
  if(typeof manifestJson!=='string'||manifestJson.length>4096)throw Error('Invalid signed manifest size');
  const manifest=JSON.parse(manifestJson);
  if(manifestJson.trim()!==canonical(manifest))throw Error('Noncanonical signed manifest');
  const {signatureBase64,...payload}=manifest;
  if(Object.keys(payload).sort().join('|')!==KEYS.join('|')||
     payload.schema!=='continuum-signed-cre1-package'||payload.version!==1||
     payload.format!=='CRE1'||payload.accepted!==true||
     !HEX.test(payload.modelSha256)||!HEX.test(payload.auditSha256)||
     payload.modelSha256!==sha256Hex(modelBytes)||payload.modelBytes!==modelBytes.length||
     payload.auditSha256!==sha256Hex(auditBytes)||
     !Number.isSafeInteger(payload.createdAt)||payload.createdAt<0||
     typeof signatureBase64!=='string'||signatureBase64.length>500)
    throw Error('Invalid signed model manifest or model/audit hash');
  const audit=JSON.parse(Buffer.from(auditBytes).toString('utf8'));
  if(canonical(audit)!==Buffer.from(auditBytes).toString('utf8'))throw Error('Noncanonical audit');
  validatePromotionAudit(audit,modelBytes);
  if(!crypto.verify('sha256',Buffer.from(canonical(payload),'utf8'),
      checkEcKey(publicKeyPem,'public'),Buffer.from(signatureBase64,'base64')))
    throw Error('Invalid publisher signature');
  return Object.freeze(payload);
}

/** No autonomous promotion, filesystem mutation, or private data ingestion. */
export function runResearchLearningCycle({
  baselineBytes,spans=[],privateKeyPem=null,now=Date.now(),trainingOptions={}
}={}){
  const verified=validateExtractiveSpans(spans);
  const {train,audit}=splitVerifiedClaimsByHash(verified);
  const trainSet=buildExtractiveContrastiveSet(train);
  const auditSet=buildExtractiveContrastiveSet(audit);
  if(trainSet.length<3||auditSet.length<3)return {
    accepted:false,reason:'INSUFFICIENT_INDEPENDENT_EXTRACTIVE_EVIDENCE',
    trainSources:new Set(train.map(x=>x.sourceSha256)).size,
    auditSources:new Set(audit.map(x=>x.sourceSha256)).size,
    signedPackage:null
  };
  const trained=trainFrozenProjectionCandidate(baselineBytes,trainSet,auditSet,trainingOptions);
  const auditReport={accepted:trained.accepted,steps:trained.steps,
    trainCount:trainSet.length,auditCount:auditSet.length,
    trainSourceCount:new Set(train.map(x=>x.sourceSha256)).size,
    auditSourceCount:new Set(audit.map(x=>x.sourceSha256)).size,
    trainCorrect:trained.trainAfter.correct,auditCorrect:trained.auditAfter.correct};
  const signedPackage=trained.accepted&&privateKeyPem
    ?createSignedCre1Package({modelBytes:trained.modelBytes,auditReport,privateKeyPem,createdAt:now})
    :null;
  return {accepted:trained.accepted,reason:trained.accepted?'SHADOW_EVALUATED':'HOLDOUT_GATE_REJECTED',
    modelSha256:trained.modelSha256,trainCount:trainSet.length,auditCount:auditSet.length,
    auditReport,signedPackage,canaries:trained.canaries,
    shadowMetrics:{
      trainBefore:trained.trainBefore,trainAfter:trained.trainAfter,
      auditBefore:trained.auditBefore,auditAfter:trained.auditAfter,
      poolSize:trained.poolSize,gramPq:trained.gramPq,gramPm:trained.gramPm
    },
    shadowModelBytes:trained.accepted?Buffer.from(trained.modelBytes):null};
}
