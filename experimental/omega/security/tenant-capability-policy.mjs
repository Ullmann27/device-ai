/**
 * Research-only tenant-boundary policy for future Auto Access Lizenz.
 * The caller MUST supply a trusted, independently pinned Ed25519 key and
 * resource ownership looked up on the server, never client-supplied ownership.
 * No DB, identity-provider, key lifecycle, token issuer or HTTP endpoint exists.
 */
import {createHash,createPublicKey,verify} from 'node:crypto';
const SHA=/^[a-f0-9]{64}$/;
const ID=/^[a-z][a-z0-9-]{2,79}$/;
const NONCE=/^[a-f0-9]{32}$/;
const B64=/^[A-Za-z0-9+/]{86}==$/;
const ACTIONS=new Set(['READ','WRITE']);
const deny=Object.freeze({allowed:false,reason:'DENIED'});
const plain=x=>x!==null&&typeof x==='object'&&!Array.isArray(x)&&
 (Object.getPrototypeOf(x)===Object.prototype||Object.getPrototypeOf(x)===null);
const exact=(v,k)=>plain(v)&&Object.keys(v).sort().join('|')===[...k].sort().join('|');
const schema=['schema','audience','issuer','subjectId','tenantId','resourceId',
  'resourceType','actions','notBeforeMs','expiresAtMs','nonce'];
const canonical=c=>JSON.stringify(Object.fromEntries(schema.map(k=>[k,c[k]])));
/** Canonical bytes for fixtures and independently controlled authorized signers. */
export function serializeTenantCapabilityClaims(c){
 if(!exact(c,schema))throw Error('TENANT_CAPABILITY_INVALID');
 return canonical(c);
}
/** Deny unless every checked property, issuer, tenant and signature is valid. */
export function evaluateSignedTenantCapability({grant,resource,action,nowMs,
  trustedPublicKey,expectedKeySha256}={}){
 try{
  if(!exact(grant,[...schema,'signature'])||!exact(resource,['tenantId','resourceId','resourceType'])||
     !Number.isSafeInteger(nowMs)||nowMs<0||!SHA.test(expectedKeySha256??'')||
     !ACTIONS.has(action)||!ID.test(resource.tenantId??'')||
     !ID.test(resource.resourceId??'')||!ID.test(resource.resourceType??'')||
     grant.schema!=='AUTO_ACCESS_SIGNED_CAPABILITY_V1'||grant.audience!=='auto-access-license'||
     grant.issuer!=='auto-access-authz'||!ID.test(grant.subjectId??'')||
     !ID.test(grant.tenantId??'')||!ID.test(grant.resourceId??'')||
     !ID.test(grant.resourceType??'')||!NONCE.test(grant.nonce??'')||
     !Array.isArray(grant.actions)||grant.actions.length===0||grant.actions.length>2||
     new Set(grant.actions).size!==grant.actions.length||
     grant.actions.some(a=>!ACTIONS.has(a))||!grant.actions.includes(action)||
     grant.tenantId!==resource.tenantId||grant.resourceId!==resource.resourceId||
     grant.resourceType!==resource.resourceType||
     !Number.isSafeInteger(grant.notBeforeMs)||!Number.isSafeInteger(grant.expiresAtMs)||
     grant.notBeforeMs>nowMs||nowMs>=grant.expiresAtMs||
     grant.expiresAtMs-grant.notBeforeMs>300_000||
     grant.expiresAtMs<=grant.notBeforeMs||
     typeof grant.signature!=='string'||!B64.test(grant.signature))return deny;
  if(trustedPublicKey?.type==='private')return deny;
  const key=trustedPublicKey?.type==='public' ? trustedPublicKey : createPublicKey(trustedPublicKey);
  if(key.asymmetricKeyType!=='ed25519')return deny;
  const pinned=createHash('sha256').update(key.export({format:'der',type:'spki'})).digest('hex');
  if(pinned!==expectedKeySha256)return deny;
  const {signature,...claims}=grant;
  const signatureBytes=Buffer.from(signature,'base64');
  if(signatureBytes.length!==64||signatureBytes.toString('base64')!==signature)return deny;
  if(!verify(null,Buffer.from(canonical(claims),'utf8'),key,signatureBytes))return deny;
  return Object.freeze({allowed:true,reason:'SIGNED_CAPABILITY_MATCHED',subjectId:grant.subjectId,
    tenantId:grant.tenantId,resourceId:grant.resourceId,action});
 }catch{return deny;}
}
