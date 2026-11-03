// Independent CONTRACT_TEST only. Disposable WeakSet brands below replace only
// the live observation validators, never actual inference or verifier execution.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {spawnSync} from 'node:child_process';
import {createHmac} from 'node:crypto';
import {fixture,canonical,writeRecords,url,root} from '../../../momega1-memory-review/tests/fixture.mjs';

const rt=new WeakSet(),vf=new WeakSet();
const key='jarvis-review-fixture-'+process.pid;
globalThis[Symbol.for(key)]={rt,vf};
const seam='data:text/javascript;base64,'+Buffer.from(`const {rt,vf}=globalThis[Symbol.for(${JSON.stringify(key)})];export const isTrustedLocalModelObservation=x=>rt.has(x);export const isTrustedCodingVerification=x=>vf.has(x);`).toString('base64');
const helperUrl=url('ccc1-technical-recall.mjs');
const helper=fs.readFileSync(new URL(helperUrl),'utf8').replace(/from '([^']+)'/g,(all,spec)=>{
 if(spec==='./coding-provider.mjs'||spec==='./ccc1-redgreen-verifier.mjs')return 'from '+JSON.stringify(seam);
 return spec.startsWith('.')?'from '+JSON.stringify(new URL(spec,helperUrl).href):all;
});
const api=await import('data:text/javascript;base64,'+Buffer.from(helper).toString('base64'));
function admitted(f,{model={},verification={}}={}){
 const observation=Object.freeze({...f.modelObservation,verifiedModelExecution:true,...model});rt.add(observation);
 const verified=Object.freeze({...f.verification,...verification});vf.add(verified);
 return {...f.options,taskId:f.taskId,modelTask:f.modelTask,modelObservation:observation,verification:verified,reflection:f.reflection};
}
function persisted(f){return JSON.parse(fs.readFileSync(f.file,'utf8')).record.technicalProof;}
function assertMac(proof,key){const {mac,...data}=proof;assert.equal(mac,createHmac('sha256',key).update('continuum-momega1-technical-proof-v1\0'+canonical(data)).digest('hex'));}
function freshRecall(f){
 const script=`import fs from 'node:fs';import {recallTechnicalExperiences} from ${JSON.stringify(helperUrl)};const q=JSON.parse(fs.readFileSync(0,'utf8'));q.key=Buffer.from(q.key,'hex');const r=recallTechnicalExperiences(q);process.stdout.write(JSON.stringify({count:r.items.length,status:r.status,kind:r.items[0]?.kind??null}));`;
 const r=spawnSync(process.execPath,['--input-type=module','-e',script],{input:JSON.stringify({...f.options,key:f.key.toString('hex')}),encoding:'utf8',timeout:5000});
 assert.equal(r.status,0,r.stderr);return JSON.parse(r.stdout);
}

for(const [name,extra] of [
 ['top-level optional verifier field',{verification:{optional:undefined}}],
 ['nested verifier diagnostics',{verification:{diagnostics:[{message:'synthetic RED',file:undefined,location:{line:2,column:undefined}}]}}],
 ['undefined in verifier arrays',{verification:{extra:{items:[1,undefined,{hint:undefined,retained:'value'}]}}}],
 ['sparse verifier arrays',{verification:{extra:{items:Array(3)}}}],
 ['nested optional runtime observation',{model:{extra:{optional:undefined,kept:{a:undefined,b:true}}}}],
 ['optional runtime array slots',{model:{extra:[undefined,'kept',undefined]}}]
])test('RT01 persisted snapshot keeps valid MAC and fresh-process recall: '+name,t=>{
 const f=fixture(t),args=admitted(f,extra);
 assert.equal(api.recordTechnicalExperience(args),true);
 assertMac(persisted(f),f.key);
 assert.equal(f.recall().items.length,1);
 assert.deepEqual(freshRecall(f),{count:1,status:'OK',kind:'TECHNICAL_EXPERIENCE_REVIEW_ONLY'});
});

test('RT02 normalization does not mutate original branded observations',t=>{
 const f=fixture(t),nested={optional:undefined,items:[undefined,'same']};
 const args=admitted(f,{verification:{nested}});
 api.recordTechnicalExperience(args);
 assert.ok(Object.hasOwn(nested,'optional'));assert.equal(nested.items[0],undefined);
 assert.ok(Object.isFrozen(args.modelObservation));assert.ok(Object.isFrozen(args.verification));
 assert.deepEqual(persisted(f).verification.nested,{items:[null,'same']});assertMac(persisted(f),f.key);
});

test('RT03 post-serialization model binding change is rejected before ledger write',t=>{
 const f=fixture(t);const altered={...f.modelObservation,verifiedModelExecution:false};
 const args=admitted(f,{model:{toJSON(){return altered;}}});
 assert.throws(()=>api.recordTechnicalExperience(args));assert.equal(fs.existsSync(f.file),false);
});

test('RT04 post-serialization verifier patch substitution is rejected before ledger write',t=>{
 const f=fixture(t),altered={...f.verification,patchSha256:'9'.repeat(64)};
 const args=admitted(f,{verification:{toJSON(){return altered;}}});
 assert.throws(()=>api.recordTechnicalExperience(args));assert.equal(fs.existsSync(f.file),false);
});

test('RT05 persisted content tamper stays rejected even after outer hash-chain rewrite',t=>{
 const f=fixture(t);api.recordTechnicalExperience(admitted(f,{verification:{optional:undefined}}));
 const record=JSON.parse(fs.readFileSync(f.file)).record;record.technicalProof.modelObservation.outputSha256='9'.repeat(64);
 writeRecords(f.file,[record]);assert.equal(f.recall().items.length,0);assert.equal(freshRecall(f).count,0);
});

for(const kind of ['model','verifier'])test('RT06 JSON-copy '+kind+' cannot inherit the original brand',t=>{
 const f=fixture(t),args=admitted(f);
 if(kind==='model')args.modelObservation=JSON.parse(JSON.stringify(args.modelObservation));
 else args.verification=JSON.parse(JSON.stringify(args.verification));
 assert.throws(()=>api.recordTechnicalExperience(args),kind==='model'?/RUNTIME_OBSERVATION_REQUIRED/:/INDEPENDENT_VERIFICATION_REQUIRED/);
 assert.equal(fs.existsSync(f.file),false);
});

for(const kind of ['circular','bigint'])test('RT07 unserializable '+kind+' is refused before ledger write',t=>{
 const f=fixture(t),extra=kind==='circular'?{}:{value:1n};if(kind==='circular')extra.self=extra;
 assert.throws(()=>api.recordTechnicalExperience(admitted(f,{verification:{extra}})));assert.equal(fs.existsSync(f.file),false);
});

test('RT08 signed optional numeric normalization remains stable',t=>{
 const f=fixture(t);api.recordTechnicalExperience(admitted(f,{verification:{extra:{negativeZero:-0,nan:NaN,infinity:Infinity}}}));
 assertMac(persisted(f),f.key);assert.equal(freshRecall(f).count,1);
});

test('RT09 a false execution tier is never repaired by serialization',t=>{
 const f=fixture(t),args=admitted(f,{model:{verifiedModelExecution:false}});
 assert.throws(()=>api.recordTechnicalExperience(args),/MODEL_EXECUTION_PROOF_UNAVAILABLE/);assert.equal(fs.existsSync(f.file),false);
});
