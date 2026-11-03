/** Applies a bounded synthetic proposal and tests it inside the real namespace. */
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync} from 'node:child_process';import {pathToFileURL} from 'node:url';import {createHash} from 'node:crypto';
const [target,receiptPath,outFile]=process.argv.slice(2);if(!target||!receiptPath||!outFile)throw Error('USAGE target receipt-or-BASELINE outFile');
const {localModelSandboxArgs,localModelSeccompFilter}=await import(pathToFileURL(path.join(target,'scripts/continuum-local-model-observation.mjs')));
const frozen=JSON.parse(fs.readFileSync(path.join(import.meta.dirname,'blind-host-prompt.json'),'utf8'));let source=frozen.source,proposal=null;
if(receiptPath!=='BASELINE'){
 const receipt=JSON.parse(fs.readFileSync(receiptPath,'utf8'));proposal=receipt.parsed?.proposal??receipt.proposal;
 if(!proposal||!Array.isArray(proposal.patches)||proposal.patches.length!==1)throw Error('BOUNDED_PROPOSAL_REQUIRED');
 const p=proposal.patches[0];if(p.file!=='lib/batches.mjs'||typeof p.oldText!=='string'||!p.oldText||typeof p.newText!=='string'||!p.newText||p.oldText.length>3200||p.newText.length>3200||p.oldText===p.newText||source.split(p.oldText).length!==2)throw Error('PATCH_NOT_UNIQUE_OR_OUT_OF_SCOPE');source=source.replace(p.oldText,p.newText);
}
const checks=`import assert from 'node:assert/strict';import {batches} from '/candidate.mjs';
const cases=[{name:'empty',items:[],width:3,want:[]},{name:'exact multiple',items:['a','b','c','d','e','f'],width:3,want:[['a','b','c'],['d','e','f']]},{name:'remainder',items:[7,8,9,10,11],width:2,want:[[7,8],[9,10],[11]]},{name:'wide',items:[5],width:9,want:[[5]]}];let failed=0;for(const c of cases){try{assert.deepEqual(batches(c.items,c.width),c.want);console.log('PASS '+c.name);}catch{failed++;console.log('FAIL '+c.name);}}
for(const width of [0,-2,1.5,NaN]){try{assert.throws(()=>batches([1],width),RangeError);console.log('PASS invalid width '+width);}catch{failed++;console.log('FAIL invalid width '+width);}}process.exitCode=failed?1:0;`;
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'momega1-independent-patch-'));const files=[['candidate.mjs',source],['checks.mjs',checks],['seccomp',localModelSeccompFilter()]];for(const [name,data] of files)fs.writeFileSync(path.join(dir,name),data,{mode:0o600});
const fds=[fs.openSync(path.join(dir,'candidate.mjs'),'r'),fs.openSync(process.execPath,'r'),fs.openSync(path.join(dir,'checks.mjs'),'r'),fs.openSync(path.join(dir,'seccomp'),'r')];
const args=localModelSandboxArgs().map(x=>x==='/model.gguf'?'/candidate.mjs':x==='/prompt.txt'?'/checks.mjs':x);args.splice(args.indexOf('--')+1,Infinity,'/runtime','--jitless','--max-old-space-size=64','/checks.mjs');
let r;try{r=spawnSync('/usr/bin/prlimit',['--as=4294967296','--cpu=3','--nofile=64','--fsize=1048576','--','/usr/bin/bwrap',...args],{env:{},cwd:'/',shell:false,stdio:['ignore','pipe','pipe',...fds],encoding:'utf8',timeout:5000,maxBuffer:8192});}finally{for(const fd of fds)fs.closeSync(fd);fs.rmSync(dir,{recursive:true,force:true});}
const result={classification:'INDEPENDENT_SYNTHETIC_PATCH_TEST',baseline:receiptPath==='BASELINE',sourceSha256:createHash('sha256').update(source).digest('hex'),status:r.status,signal:r.signal,error:r.error?.code??null,stdout:r.stdout,stderr:r.stderr};fs.writeFileSync(outFile,JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));process.exitCode=r.status===0?0:1;
