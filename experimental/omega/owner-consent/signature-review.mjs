import {KeyObject, createHash, verify} from 'node:crypto';

/**
 * Isolated laboratory signature inspection. Does not authorize CCC1, grant
 * creation, Android, Termux, installation, or execution. Host controls the
 * trusted public key, expected binding, time and revocation status.
 */
const DOMAIN='continuum-owner-consent-signature-review-v1\n';
const SHA256=/^[a-f0-9]{64}$/;
const NONCE=/^[a-f0-9]{64}$/;
const ID=/^[a-z][a-z0-9-]{2,63}$/;
const FIELDS=Object.freeze([
  'schema','version','keyId','ownerId','deviceId','audience','action',
  'taskSha256','policyVersion','nonce','notBefore','expiresAt'
]);
const EXPECTED_FIELDS=Object.freeze([
  'keyId','ownerId','deviceId','audience','action','taskSha256','policyVersion'
]);
const fail=code=>{throw new Error(code);};
const match=(regex,value)=>typeof value==='string'&&regex.test(value);
function exactObject(value,fields){
  return value!==null&&typeof value==='object'&&!Array.isArray(value)&&
    Object.getPrototypeOf(value)===Object.prototype&&
    Object.keys(value).sort().join(',')===[...fields].sort().join(',');
}
function validClaims(c){
  return exactObject(c,FIELDS)&&
    c.schema==='continuum-owner-consent-lab'&&c.version===1&&
    match(SHA256,c.keyId)&&match(ID,c.ownerId)&&match(ID,c.deviceId)&&
    match(ID,c.audience)&&['PREPARE','RUN'].includes(c.action)&&
    match(SHA256,c.taskSha256)&&
    Number.isSafeInteger(c.policyVersion)&&c.policyVersion>=1&&
    match(NONCE,c.nonce)&&
    Number.isSafeInteger(c.notBefore)&&c.notBefore>=0&&
    Number.isSafeInteger(c.expiresAt)&&c.expiresAt>c.notBefore&&
    c.expiresAt-c.notBefore<=120000;
}

/** Fixed-field domain separation. Not a replacement for task canonicalization. */
export function signatureReviewBytes(claims){
  if(!validClaims(claims))fail('CONSENT_CLAIMS_INVALID');
  return Buffer.from(DOMAIN+JSON.stringify(FIELDS.map(field=>claims[field])),'utf8');
}

/**
 * A cryptographically valid signature is NOT a durable, executable consent.
 * This API never consumes a nonce, never calls a grant function, and never
 * returns an owner-approved or execution-enabled state.
 */
export function reviewConsentSignature({claims,signatureHex,publicKey,expected,revoked,now}={}){
  if(!(publicKey instanceof KeyObject)||publicKey.type!=='public'||
    publicKey.asymmetricKeyType!=='ec'||
    publicKey.asymmetricKeyDetails?.namedCurve!=='prime256v1')
    fail('CONSENT_PUBLIC_KEY_INVALID');
  if(revoked!==false)fail('CONSENT_KEY_NOT_ACTIVE');
  if(!Number.isSafeInteger(now)||now<0)fail('CONSENT_CLOCK_INVALID');
  const bytes=signatureReviewBytes(claims);
  if(!exactObject(expected,EXPECTED_FIELDS)||
    EXPECTED_FIELDS.some(field=>claims[field]!==expected[field]))
    fail('CONSENT_BINDING_MISMATCH');
  const actualKeyId=createHash('sha256')
    .update(publicKey.export({type:'spki',format:'der'})).digest('hex');
  if(actualKeyId!==expected.keyId)fail('CONSENT_KEY_PIN_MISMATCH');
  if(now<claims.notBefore||now>=claims.expiresAt)
    fail('CONSENT_TIME_INVALID');
  if(typeof signatureHex!=='string'||!/^[a-f0-9]{128}$/.test(signatureHex))
    fail('CONSENT_SIGNATURE_ENCODING_INVALID');
  if(!verify('sha256',bytes,{key:publicKey,dsaEncoding:'ieee-p1363'},
    Buffer.from(signatureHex,'hex')))
    fail('CONSENT_SIGNATURE_INVALID');
  return Object.freeze({
    status:'SIGNATURE_VALID_NOT_AUTHORIZED',nonce:claims.nonce,
    taskSha256:claims.taskSha256,action:claims.action,
    executionAllowed:false,grantCreationAllowed:false
  });
}
