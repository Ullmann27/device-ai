import test from 'node:test';
import assert from 'node:assert/strict';
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
