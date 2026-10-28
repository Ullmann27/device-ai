#!/usr/bin/env node
/** Fold7 Termux just-in-time public docs, separate from private user/model state.
 * Commands use fixed source IDs, never arbitrary URLs or secrets.
 * plan: no network; fetch: explicit owner consent, no model API.
 */
import {exactPublicDocTopic,PUBLIC_DOC_TOPICS,fetchPublicCodingDoc} from '../.github/continuum/coding-public-docs.mjs';
const [operation,id,approval,...rest]=process.argv.slice(2);
async function main() {
 if(rest.length||!['list','plan','fetch'].includes(operation))throw Error('DOCS_USAGE');
 if(operation==='list'){
  if(id!==undefined||approval!==undefined)throw Error('DOCS_USAGE');
  process.stdout.write(JSON.stringify({topics:PUBLIC_DOC_TOPICS,networkUsed:false})+'\n');return;
 }
 if(id===undefined)throw Error('DOCS_USAGE');
 const doc=exactPublicDocTopic(id);
 if(operation==='plan'){
  if(approval!==undefined)throw Error('DOCS_USAGE');
  process.stdout.write(JSON.stringify({...doc,networkUsed:false,ownerApprovalRequired:true})+'\n');return;
 }
 if(approval!=='--approve-network')throw Error('DOCS_OWNER_APPROVAL_REQUIRED');
 const result=await fetchPublicCodingDoc(id,{approved:true});
 process.stdout.write(JSON.stringify(result)+'\n');
}
main().catch(e=>{const errors=new Set(['DOCS_USAGE','DOCS_TOPIC_NOT_ALLOWED','DOCS_OWNER_APPROVAL_REQUIRED',
 'DOCS_INVALID_TIMEOUT','DOCS_ENDPOINT_INVALID','DOCS_UNAVAILABLE_OR_REDIRECTED',
 'DOCS_UNEXPECTED_CONTENT_TYPE','DOCS_RESPONSE_TOO_LARGE','DOCS_STREAM_UNAVAILABLE',
 'DOCS_INVALID_CHUNK','DOCS_NO_MEANINGFUL_TEXT']);
 process.stderr.write('CONTINUUM_PUBLIC_DOCS_BLOCKED='+(errors.has(e?.message)?e.message:'DOCS_NETWORK_OR_PARSE_FAILED')+'\n');
 process.exitCode=2;
});
