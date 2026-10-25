import test from 'node:test';
import assert from 'node:assert/strict';
import {queryOwnedOsvPublicApi} from './osv-public-query.mjs';
import {evaluateOwnedOsvSnapshot} from './advisory-intake.mjs';

const inventory=(count=1)=>({schema:'CONTINUUM_OWNER_INVENTORY_V1',project:'snapshot-owner',
 sourceCommit:'a'.repeat(40),components:Array.from({length:count},(_,i)=>({
  ecosystem:'npm',name:'pkg-'+String(i).padStart(2,'0'),version:'1.2.3',direct:i===0}))});
const response=queries=>({ok:true,status:200,headers:{get:()=>null},
 body:new ReadableStream({start(c){c.enqueue(new TextEncoder().encode(JSON.stringify({
  results:queries.map(()=>({vulns:[{id:'OSV-2026-001'}]}))})));c.close();}})});
const deferred=()=>{let resolve;const promise=new Promise(r=>{resolve=r;});return {promise,resolve};};

test('pending response remains bound to the validated inventory despite caller mutation',async()=>{
 const input=inventory(),original=structuredClone(input);
 const expected=evaluateOwnedOsvSnapshot({inventory:original,advisories:[]});
 const gate=deferred();let queries;
 const pending=queryOwnedOsvPublicApi({inventory:input,networkConsent:true,
  fetchImpl:async(_url,init)=>{queries=JSON.parse(init.body).queries;await gate.promise;return response(queries);}});
 try{
  input.components[0].name='different-pkg';input.components[0].version='9.9.9';
  input.components[0].ecosystem='PyPI';input.components[0].direct=false;
  input.sourceCommit='b'.repeat(40);input.project='different-owner';input.components=[];
 }finally{gate.resolve();}
 const out=await pending;
 assert.deepEqual(queries,[{package:{name:'pkg-00',ecosystem:'npm'},version:'1.2.3'}]);
 assert.equal(out.findings[0].packageName,'pkg-00');
 assert.equal(out.findings[0].installedVersion,'1.2.3');assert.equal(out.findings[0].ecosystem,'npm');
 assert.equal(out.sourceCommit,original.sourceCommit);assert.equal(out.project,original.project);
 assert.equal(out.inventorySha256,expected.inventorySha256);assert.equal(out.queriedComponents,1);
});

test('later batches use the same snapshot rather than mutated future components',async()=>{
 const input=inventory(21),original=structuredClone(input),requests=[],gate=deferred();
 const pending=queryOwnedOsvPublicApi({inventory:input,networkConsent:true,
  fetchImpl:async(_url,init)=>{const queries=JSON.parse(init.body).queries;requests.push(queries);
   if(requests.length===1)await gate.promise;return response(queries);}});
 try{input.components[20].name='changed-later';input.components[20].version='8.8.8';}
 finally{gate.resolve();}
 const out=await pending;
 assert.equal(requests.length,2);assert.equal(requests[0].length,20);assert.equal(requests[1].length,1);
 assert.deepEqual(requests.flat(),original.components.map(c=>({package:{name:c.name,ecosystem:c.ecosystem},version:c.version})));
 assert.equal(out.findings.length,21);assert.equal(out.findings[20].packageName,'pkg-20');
 assert.equal(out.findings[20].installedVersion,'1.2.3');
});

test('invalid and over-budget inventories still fail before transport',async()=>{
 let calls=0;const fetchImpl=async()=>{calls++;throw Error('must not run');};
 const malformed=inventory();malformed.components[0].version='invalid version';
 await assert.rejects(queryOwnedOsvPublicApi({inventory:malformed,networkConsent:true,fetchImpl}),/OMEGA_ADVISORY_INPUT_INVALID/);
 await assert.rejects(queryOwnedOsvPublicApi({inventory:inventory(201),networkConsent:true,fetchImpl}),/OSV_QUERY_BUDGET_EXCEEDED/);
 assert.equal(calls,0);
});

test('caller-only fields are neither transmitted nor included in canonical inventory evidence',async()=>{
 const input=inventory(),expected=evaluateOwnedOsvSnapshot({inventory:input,advisories:[]});
 input.privateMemo='not-for-transport';input.components[0].privateMemo='also-private';let body;
 const out=await queryOwnedOsvPublicApi({inventory:input,networkConsent:true,
  fetchImpl:async(_url,init)=>{body=init.body;return response(JSON.parse(body).queries);}});
 assert.equal(body.includes('private'),false);assert.equal(out.inventorySha256,expected.inventorySha256);
 assert.equal(out.automaticActions,0);assert.equal(out.patchesGenerated,0);assert.equal(out.vulnerabilityFreeClaim,false);
});
