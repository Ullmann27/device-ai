// Testing SPI only. The builder supplies a thin bridge to ACTUAL candidate admission.
// No bridge exists on baseline: exit2 means NOT_RUN, never PASS or a fabricated RED.
import {pathToFileURL} from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
import {cases} from './adapter-cases.mjs';
const file=process.argv[2];
if(!file){console.error('NOT_RUN: provide candidate bridge exporting evaluateCase(fixture); no production adapter exists in this baseline.');process.exit(2);}
const adapter=await import(pathToFileURL(path.resolve(file)).href);
assert.equal(typeof adapter.evaluateCase,'function');
let failed=0;
for(const c of cases){try{const r=await adapter.evaluateCase(c.make());assert.equal(r?.decision,c.expected);if(c.expected==='REJECT')assert.equal(r.effectCalls,0,'rejected admission must not execute');console.log('PASS '+c.id);}catch(e){failed++;console.log('FAIL '+c.id+' '+e.message);}}
console.log(JSON.stringify({kind:'ADMISSION_CONTRACT_ONLY',total:cases.length,passed:cases.length-failed,failed,actualModelInference:false,deviceVerified:false}));
process.exitCode=failed?1:0;
