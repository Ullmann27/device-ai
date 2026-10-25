import test from 'node:test';
import assert from 'node:assert/strict';
import {queryOwnedOsvPublicApi} from './osv-public-query.mjs';
const inventory={schema:'CONTINUUM_OWNER_INVENTORY_V1',project:'testowner',sourceCommit:'a'.repeat(40),
 components:[{ecosystem:'npm',name:'example-pkg',version:'1.2.3',direct:true}]};
const response=(data,headers={})=>({ok:true,status:200,headers:{get:k=>headers[k]??null},text:async()=>JSON.stringify(data)});
const run=(opts={})=>queryOwnedOsvPublicApi({inventory,networkConsent:true,fetchImpl:async()=>response({results:[{vulns:[{id:'GHSA-aaaa-bbbb-cccc'}]}]}),...opts});
test('network consent is off by default; no outbound calls',async()=>{
 let calls=0;
 await assert.rejects(queryOwnedOsvPublicApi({inventory,fetchImpl:async()=>{calls++;return null;}}),/OSV_EXPLICIT_NETWORK_CONSENT_REQUIRED/);
 assert.equal(calls,0);
});
test('official endpoint, minimal package/version query and no credentials',async()=>{
 let args;
 const out=await run({fetchImpl:async(url,init)=>{args={url,init};return response({results:[{vulns:[{id:'GHSA-aaaa-bbbb-cccc'}]}]});}});
 assert.equal(args.url,'https://api.osv.dev/v1/querybatch');
 assert.equal(args.init.method,'POST');assert.equal(args.init.credentials,'omit');
 assert.deepEqual(JSON.parse(args.init.body),{queries:[{package:{ecosystem:'npm',name:'example-pkg'},version:'1.2.3'}]});
 assert.equal(out.findings.length,1);assert.equal(out.vulnerabilityFreeClaim,false);assert.equal(out.patchesGenerated,0);
 assert.equal(out.findings[0].match,'OSV_API_VERSION_QUERY_REQUIRES_REVIEW');
});
test('no results are NOT a security certificate',async()=>{
 const r=await run({fetchImpl:async()=>response({results:[{}]})});
 assert.equal(r.findings.length,0);assert.equal(r.vulnerabilityFreeClaim,false);
});
test('invalid response ordering/shape fails closed',async()=>{
 for(const data of [{results:[]},{results:[{vulns:'bad'}]},
 {results:[{vulns:[{id:'bad id'}]}]},null]){
   await assert.rejects(run({fetchImpl:async()=>response(data)}),/OSV_RESPONSE_INVALID/);
 }
});
test('pagination fails closed rather than silently dropping advisories',async()=>{
 await assert.rejects(run({fetchImpl:async()=>response({results:[{vulns:[],next_page_token:'next'}]})}),/OSV_PAGINATION_UNRESOLVED/);
});
test('oversized response and upstream failure are rejected',async()=>{
 await assert.rejects(run({fetchImpl:async()=>response({results:[{}]},{'content-length':'1000001'})}),/OSV_REMOTE_RESPONSE_UNAVAILABLE/);
 await assert.rejects(run({fetchImpl:async()=>{throw Error('offline');}}),/OSV_REMOTE_RESPONSE_UNAVAILABLE/);
 await assert.rejects(run({fetchImpl:async()=>({...response({results:[{}]}),text:async()=>(' '.repeat(1_000_001))})}),/OSV_RESPONSE_TOO_LARGE/);
});
test('malformed pinned owner inventory fails before any outbound query',async()=>{
 let calls=0;
 await assert.rejects(run({inventory:{...inventory,sourceCommit:'main'},fetchImpl:async()=>{calls++;return null;}}),/OMEGA_ADVISORY_INPUT_INVALID/);
 assert.equal(calls,0);
});
test('same advisory duplicate is deduplicated, deterministic evidence',async()=>{
 const a=await run({fetchImpl:async()=>response({results:[{vulns:[{id:'OSV-2026-001'},{id:'OSV-2026-001'}]}]})});
 const b=await run({fetchImpl:async()=>response({results:[{vulns:[{id:'OSV-2026-001'}]}]})});
 assert.deepEqual(a,b);assert.equal(a.findings.length,1);
});
