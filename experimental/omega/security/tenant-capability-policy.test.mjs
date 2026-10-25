import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash,generateKeyPairSync,sign} from 'node:crypto';
import {evaluateSignedTenantCapability,serializeTenantCapabilityClaims} from './tenant-capability-policy.mjs';
const {privateKey,publicKey}=generateKeyPairSync('ed25519');
const {privateKey:attackerPrivate,publicKey:attackerPublic}=generateKeyPairSync('ed25519');
const digest=key=>createHash('sha256').update(key.export({format:'der',type:'spki'})).digest('hex');
const keySha=digest(publicKey),at=1700000000000;
const resource={tenantId:'tenant-one',resourceId:'record-abc',resourceType:'license'};
const claims={schema:'AUTO_ACCESS_SIGNED_CAPABILITY_V1',audience:'auto-access-license',issuer:'auto-access-authz',
 subjectId:'user-123',tenantId:'tenant-one',resourceId:'record-abc',resourceType:'license',
 actions:['READ'],notBeforeMs:at-2000,expiresAtMs:at+2000,nonce:'a'.repeat(32)};
const signed=(c=claims,key=privateKey)=>({...c,signature:sign(null,Buffer.from(serializeTenantCapabilityClaims(c)),key).toString('base64')});
const check=(overrides={})=>evaluateSignedTenantCapability({grant:signed(),resource,action:'READ',nowMs:at,
 trustedPublicKey:publicKey,expectedKeySha256:keySha,...overrides});
test('valid Ed25519 capability grants only the matching tenant resource and READ',()=>{
 const r=check();assert.equal(r.allowed,true);assert.equal(r.tenantId,'tenant-one');
});
test('cross-tenant resource is denied even when record ID is identical',()=>{
 assert.equal(check({resource:{...resource,tenantId:'tenant-two'}}).allowed,false);
});
test('cross-resource ID and type never inherit another resource grant',()=>{
 for(const changed of [{...resource,resourceId:'record-other'},{...resource,resourceType:'secret'}])
  assert.equal(check({resource:changed}).allowed,false);
});
test('write and delete are denied to a read-only capability',()=>{
 assert.equal(check({action:'WRITE'}).allowed,false);assert.equal(check({action:'DELETE'}).allowed,false);
});
test('unexpired grant with explicit WRITE authorizes only same tenant and resource',()=>{
 const grant=signed({...claims,actions:['READ','WRITE']});
 assert.equal(check({grant,action:'WRITE'}).allowed,true);
 assert.equal(check({grant,action:'DELETE'}).allowed,false);
});
test('expiry, premature use and excessive token lifetime fail closed',()=>{
 assert.equal(check({nowMs:at+2000}).allowed,false);
 assert.equal(check({nowMs:at-2001}).allowed,false);
 assert.equal(check({grant:signed({...claims,expiresAtMs:at+400000})}).allowed,false);
});
test('tampered or forged tenant claims fail signature verification',()=>{
 assert.equal(check({grant:{...signed(),tenantId:'tenant-two'}}).allowed,false);
 assert.equal(check({grant:{...signed(),subjectId:'user-evil'}}).allowed,false);
 assert.equal(check({grant:signed(claims,attackerPrivate)}).allowed,false);
});
test('attacker key cannot replace externally pinned trusted key',()=>{
 assert.equal(check({trustedPublicKey:attackerPublic}).allowed,false);
 assert.equal(check({expectedKeySha256:digest(attackerPublic)}).allowed,false);
});
test('unknown fields, wildcard scope, reused actions and malformed signatures fail',()=>{
 for(const grant of [{...signed(),admin:true},{...signed(),signature:'A'.repeat(88)},
   signed({...claims,tenantId:'*'}),signed({...claims,actions:['READ','READ']})])
  assert.equal(check({grant}).allowed,false);
});
test('claims with swapped audience or issuer are denied even when signed',()=>{
 assert.equal(check({grant:signed({...claims,audience:'other-system'})}).allowed,false);
 assert.equal(check({grant:signed({...claims,issuer:'unknown-issuer'})}).allowed,false);
});
test('no caller-supplied resource ownership is accepted without resource tuple',()=>{
 assert.equal(check({resource:{resourceId:'record-abc'}}).allowed,false);
});
test('malformed keys, clock and permissions never throw or succeed',()=>{
 for(const opts of [{nowMs:NaN},{trustedPublicKey:'garbage'},{action:'ADMIN'},{grant:null},{expectedKeySha256:'invalid'}])
  assert.equal(check(opts).allowed,false);
});
