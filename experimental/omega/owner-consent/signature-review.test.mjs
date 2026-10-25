import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash,generateKeyPairSync,randomBytes,sign} from 'node:crypto';
import {signatureReviewBytes,reviewConsentSignature} from './signature-review.mjs';

function fixture(){
  const {publicKey,privateKey}=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
  const keyId=createHash('sha256')
    .update(publicKey.export({type:'spki',format:'der'})).digest('hex');
  const expected={
    keyId,ownerId:'owner-lab',deviceId:'device-lab',audience:'host-lab',
    action:'PREPARE',taskSha256:'a'.repeat(64),policyVersion:1
  };
  const claims={
    schema:'continuum-owner-consent-lab',version:1,...expected,
    nonce:randomBytes(32).toString('hex'),notBefore:1000,expiresAt:61000
  };
  const signatureHex=sign('sha256',signatureReviewBytes(claims),
    {key:privateKey,dsaEncoding:'ieee-p1363'}).toString('hex');
  return {claims,signatureHex,publicKey,privateKey,expected,revoked:false,now:2000};
}
test('valid P-256 signature never becomes executable authority',()=>{
  const r=reviewConsentSignature(fixture());
  assert.equal(r.status,'SIGNATURE_VALID_NOT_AUTHORIZED');
  assert.equal(r.executionAllowed,false);
  assert.equal(r.grantCreationAllowed,false);
  assert.equal(Object.isFrozen(r),true);
});
test('PREPARE signature cannot authorize RUN',()=>{
  const f=fixture();
  assert.throws(()=>reviewConsentSignature({...f,
    expected:{...f.expected,action:'RUN'}}),/CONSENT_BINDING_MISMATCH/);
});
test('changing nonce after signing invalidates signature',()=>{
  const f=fixture();
  assert.throws(()=>reviewConsentSignature({...f,
    claims:{...f.claims,nonce:randomBytes(32).toString('hex')}}),/CONSENT_SIGNATURE_INVALID/);
});
test('expired consent is rejected at exclusive expiry boundary',()=>{
  const f=fixture();
  assert.throws(()=>reviewConsentSignature({...f,now:f.claims.expiresAt}),/CONSENT_TIME_INVALID/);
});
test('not-yet-valid consent is rejected',()=>{
  const f=fixture();
  assert.throws(()=>reviewConsentSignature({...f,now:f.claims.notBefore-1}),/CONSENT_TIME_INVALID/);
});
test('revoked or undefined key status always fails closed',()=>{
  const f=fixture();
  assert.throws(()=>reviewConsentSignature({...f,revoked:true}),/CONSENT_KEY_NOT_ACTIVE/);
  assert.throws(()=>reviewConsentSignature({...f,revoked:undefined}),/CONSENT_KEY_NOT_ACTIVE/);
});
test('private key object is not an acceptable verifier input',()=>{
  const f=fixture();
  assert.throws(()=>reviewConsentSignature({...f,publicKey:f.privateKey}),/CONSENT_PUBLIC_KEY_INVALID/);
});
test('unknown ownerApproved field cannot create authority',()=>{
  const f=fixture();
  assert.throws(()=>reviewConsentSignature({...f,
    claims:{...f.claims,ownerApproved:true}}),/CONSENT_CLAIMS_INVALID/);
});
test('different pinned public key is rejected',()=>{
  const f=fixture();
  const {publicKey}=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
  assert.throws(()=>reviewConsentSignature({...f,publicKey}),/CONSENT_KEY_PIN_MISMATCH/);
});
test('altered source task fingerprint cannot be approved',()=>{
  const f=fixture();
  assert.throws(()=>reviewConsentSignature({...f,
    expected:{...f.expected,taskSha256:'b'.repeat(64)}}),/CONSENT_BINDING_MISMATCH/);
});
test('bad signature bytes fail closed',()=>{
  const f=fixture();
  assert.throws(()=>reviewConsentSignature({...f,signatureHex:'00'.repeat(64)}),
    /CONSENT_SIGNATURE_INVALID/);
  assert.throws(()=>reviewConsentSignature({...f,signatureHex:'00'}),
    /CONSENT_SIGNATURE_ENCODING_INVALID/);
});
test('invalid duration is rejected',()=>{
  const f=fixture();
  assert.throws(()=>signatureReviewBytes({...f.claims,expiresAt:121001}),
    /CONSENT_CLAIMS_INVALID/);
});
test('replay is deliberately NOT prevented by this signature-only verifier',()=>{
  const f=fixture();
  const first=reviewConsentSignature(f);
  const second=reviewConsentSignature(f);
  assert.equal(first.nonce,second.nonce);
  assert.equal(second.grantCreationAllowed,false);
});
