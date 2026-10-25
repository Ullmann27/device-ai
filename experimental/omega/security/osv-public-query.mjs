/** Explicit-consent, read-only public OSV querybatch adapter.
 * Sends only package ecosystem/name/version, never source, credentials or owner data.
 * Does not attempt to exploit, patch, install, authenticate or scan remote hosts.
 */
import {createHash} from 'node:crypto';
import {evaluateOwnedOsvSnapshot} from './advisory-intake.mjs';
import {readBoundedOsvResponse} from './bounded-osv-response.mjs';
const ENDPOINT='https://api.osv.dev/v1/querybatch';
const ID=/^[A-Za-z][A-Za-z0-9._-]{2,90}$/;
const MAX_BATCH=20, MAX_COMPONENTS=200, MAX_RESPONSE_BYTES=1_000_000;
const sha=x=>createHash('sha256').update(JSON.stringify(x)).digest('hex');
const fail=code=>{throw Error(code);};
const record=v=>v!==null&&typeof v==='object'&&!Array.isArray(v);

/** Caller must have obtained explicit permission before setting networkConsent. */
export async function queryOwnedOsvPublicApi({inventory,networkConsent=false,fetchImpl=globalThis.fetch,timeoutMs=8000}={}){
 if(networkConsent!==true)fail('OSV_EXPLICIT_NETWORK_CONSENT_REQUIRED');
 if(typeof fetchImpl!=='function'||!Number.isSafeInteger(timeoutMs)||timeoutMs<500||timeoutMs>10000)
   fail('OSV_INVALID_CLIENT');
 // Reuse strict owner-inventory validation; no scan if inventory is malformed.
 const local=evaluateOwnedOsvSnapshot({inventory,advisories:[]});
 if(inventory.components.length>MAX_COMPONENTS)fail('OSV_QUERY_BUDGET_EXCEEDED');
 const components=[...inventory.components].sort((a,b)=>(a.ecosystem+'|'+a.name+'|'+a.version)
   .localeCompare(b.ecosystem+'|'+b.name+'|'+b.version,'en'));
 const findings=[];
 for(let at=0;at<components.length;at+=MAX_BATCH){
   const batch=components.slice(at,at+MAX_BATCH);
   const queries=batch.map(x=>({package:{name:x.name,ecosystem:x.ecosystem},version:x.version}));
   let response,body;
   try{
     response=await fetchImpl(ENDPOINT,{method:'POST',redirect:'error',credentials:'omit',
       headers:{'Content-Type':'application/json','Accept':'application/json'},
       body:JSON.stringify({queries}),signal:AbortSignal.timeout(timeoutMs)});
     if(response?.ok!==true||response.status!==200)fail('OSV_REMOTE_RESPONSE_UNAVAILABLE');
     const length=response.headers?.get?.('content-length');
     if(length!==null&&length!==undefined&&(+length>MAX_RESPONSE_BYTES||!Number.isSafeInteger(+length)))
       fail('OSV_RESPONSE_TOO_LARGE');
     body=await readBoundedOsvResponse(response);
   }catch(e){
     if(['OSV_RESPONSE_TOO_LARGE','OSV_RESPONSE_INVALID','OSV_RESPONSE_STREAM_REQUIRED'].includes(e?.message))throw e;
     fail('OSV_REMOTE_RESPONSE_UNAVAILABLE');
   }
   if(typeof body!=='string'||Buffer.byteLength(body,'utf8')>MAX_RESPONSE_BYTES)fail('OSV_RESPONSE_TOO_LARGE');
   let data;try{data=JSON.parse(body);}catch{fail('OSV_RESPONSE_INVALID');}
   if(!record(data)||!Array.isArray(data.results)||data.results.length!==batch.length)fail('OSV_RESPONSE_INVALID');
   for(let index=0;index<batch.length;index++){
     const part=data.results[index];
     if(!record(part)||!Array.isArray(part.vulns)&&part.vulns!==undefined||
       (part.vulns?.length??0)>200)fail('OSV_RESPONSE_INVALID');
     // OSV querybatch can paginate per package. Never silently lose a page.
     if(part.next_page_token!==undefined&&part.next_page_token!==null&&part.next_page_token!=='')
       fail('OSV_PAGINATION_UNRESOLVED');
     const ids=[...new Set((part.vulns??[]).map(x=>{
       if(!record(x)||typeof x.id!=='string'||!ID.test(x.id))fail('OSV_RESPONSE_INVALID');
       return x.id;
     }))].sort();
     for(const id of ids)findings.push({advisoryId:id,ecosystem:batch[index].ecosystem,
       packageName:batch[index].name,installedVersion:batch[index].version,
       match:'OSV_API_VERSION_QUERY_REQUIRES_REVIEW',action:'OWNER_SECURITY_REVIEW_REQUIRED'});
   }
 }
 const report={schema:'CONTINUUM_OSV_NETWORK_REVIEW_V1',project:local.project,
   sourceCommit:local.sourceCommit,inventorySha256:local.inventorySha256,
   queriedComponents:components.length,source:'https://api.osv.dev/v1/querybatch',
   findings,reportStatus:'SECURITY_FINDINGS_PENDING_OWNER_REVIEW',
   coverage:'OSV_API_QUERY_ONLY_NO_VULNERABILITY_FREE_CLAIM',
   vulnerabilityFreeClaim:false,automaticActions:0,patchesGenerated:0};
 return Object.freeze({...report,reportSha256:sha(report)});
}
