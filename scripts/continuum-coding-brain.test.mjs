import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {queryLocalCodingBrain} from './continuum-local-brain.mjs';
const key='a'.repeat(40);
const proposal={plan:['Find page rounding','Check regression'],hypothesis:'Truncation loses the final page',patches:[{file:'lib/pages.mjs',oldText:'x/y',newText:'(x+y-1)/y'}]};
const answer=v=>new Response(JSON.stringify({choices:[{message:{content:JSON.stringify(v)}}]}));
test('coding loopback uses bounded structured schema and accepts relevant larger context',async()=>{
 let seen;
 const r=await queryLocalCodingBrain({userPrompt:'x'.repeat(8000),allowedTrackedPaths:['lib/pages.mjs'],apiKey:key,
  fetchImpl:async(url,options)=>{seen={url,options,body:JSON.parse(options.body)};return answer(proposal);}});
 assert.deepEqual(r.proposal,proposal);assert.equal(r.trusted,false);assert.equal(r.autoApply,false);
 assert.equal(seen.url,'http://127.0.0.1:8089/v1/chat/completions');
 assert.equal(seen.body.response_format.json_schema.schema.additionalProperties,false);
 assert.equal(seen.options.redirect,'error');
});
test('coding output cannot add tools, duplicate paths, unknown files or blank hunks',async()=>{
 for(const p of [{...proposal,command:'echo unsafe'},{...proposal,patches:[...proposal.patches,...proposal.patches]},
  {...proposal,patches:[{...proposal.patches[0],file:'scripts/run.mjs'}]},
  {...proposal,patches:[{...proposal.patches[0],oldText:''}]}])
  await assert.rejects(queryLocalCodingBrain({userPrompt:'repair',allowedTrackedPaths:['lib/pages.mjs'],apiKey:key,fetchImpl:async()=>answer(p)}));
});
test('coding CLI UTF-8 transport accommodates the character-bounded multilingual context',()=>{
 const input=JSON.stringify({userPrompt:'汉'.repeat(14000),allowedTrackedPaths:['lib/pages.mjs'],timeoutMs:100});
 const env={...process.env,CONTINUUM_LOCAL_BRAIN_API_KEY:'',
   CONTINUUM_LOCAL_BRAIN_MODEL:'utf8-protocol-fixture',CONTINUUM_LOCAL_BRAIN_RUNTIME:'llama.cpp',
   CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT:'gguf',CONTINUUM_LOCAL_BRAIN_MODEL_SHA256:'b'.repeat(64),
   CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL:'OWNER_APPROVED_ALTERNATE_GGUF'};
 // The production coding CLI now requires pinned native runtime admission before
 // auth. Make missing pins explicit, independent of the QA host environment.
 for(const key of ['MODEL_PATH','RUNTIME_PATH','MODEL_MANIFEST','RUNTIME_SHA256','RUNTIME_KIND'])delete env['CONTINUUM_LOCAL_BRAIN_'+key];
 const cli=new URL('./continuum-local-brain-cli.mjs',import.meta.url).pathname;
 const r=spawnSync(process.execPath,[cli,'coding'],{input,encoding:'utf8',env,timeout:2000,shell:false});
 assert.equal(r.error,undefined);assert.equal(r.signal,null);assert.equal(r.status,1);assert.equal(r.stdout,'');
 assert.equal(r.stderr,'LOCAL_MODEL_UNAVAILABLE_OR_INVALID: BLOCKED_LOCAL_MODEL_UNAVAILABLE\n');
 assert.doesNotMatch(r.stderr,/TOO_LARGE|汉/);
 const oversized=JSON.stringify({userPrompt:'汉'.repeat(60001),allowedTrackedPaths:['lib/pages.mjs'],timeoutMs:100});
 assert.ok(Buffer.byteLength(oversized)>180000);
 const denied=spawnSync(process.execPath,[cli,'coding'],{input:oversized,encoding:'utf8',env,timeout:2000,shell:false});
 assert.equal(denied.error,undefined);assert.equal(denied.status,1);assert.equal(denied.stdout,'');
 assert.equal(denied.stderr,'LOCAL_MODEL_UNAVAILABLE_OR_INVALID: LOCAL_PATCH_REQUEST_TOO_LARGE\n');
});
