import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {proposeNodeContractTest} from './node-contract-synthesizer.mjs';
import {critique} from './repair-critic.mjs';
import {emptyDocument,applyOps,nextEligible} from './development-backlog.mjs';

const BASE='a'.repeat(40);
const MODULE='.github/continuum/math-utils.mjs';
const SOURCE='export function triple(n) { return n * 3; }\n';
const CASES=JSON.stringify([{args:[5],expected:15},{args:[8],expected:24}]);
function task(overrides={}){
 return {id:'owner-node-contract',title:'Test triple against independent expected values',
  kind:'TEST',source:'owner',state:'READY',
  subject:{base:BASE,chain:'node',operation:'ASSERT_NODE_EXPORT',
   modulePath:MODULE,exportName:'triple',casesJson:CASES,...overrides}};
}
function canonicalTask(subjectOverrides={}){
 const {state:_ignored,...candidate}=task(subjectOverrides);
 const ops=[{op:'propose',actor:'owner',at:1,task:candidate},
  {op:'accept',actor:'owner',at:2,id:candidate.id}];
 const backlog=applyOps(emptyDocument(0,'owner'),ops).doc;
 return nextEligible(backlog,['TEST'],['owner'],BASE);
}
const propose=(t=canonicalTask(),content=SOURCE)=>proposeNodeContractTest({
 files:[{path:MODULE,content}],task:t
});

test('canonical Kotlin/Node v2 backlog accepts a scalar-only owner contract and synthesizes exact tests',()=>{
 const accepted=canonicalTask();
 assert.equal(accepted.state,'READY');
 assert.equal(accepted.source,'owner');
 assert.equal(accepted.subject.casesJson,CASES);
 const r=propose(accepted);
 assert.equal(r.reason,null);
 assert.equal(r.candidate.critic.approved,true);
 assert.equal(r.candidate.edits.length,1);
 assert.equal(r.candidate.edits[0].file,'.github/continuum/math-utils.contract.test.mjs');
 assert.equal(r.candidate.edits[0].oldText,'');
 assert.match(r.candidate.edits[0].newText,/assert\.deepStrictEqual\(triple\(5\),15\)/);
 assert.match(r.candidate.edits[0].newText,/assert\.deepStrictEqual\(triple\(8\),24\)/);
 assert.equal(r.candidate.id,
  crypto.createHash('sha256').update(accepted.id+'\0'+JSON.stringify(accepted.subject))
   .digest('hex').slice(0,16));
});

test('unsupported higher-priority TEST operations cannot starve supported owner contracts',()=>{
 const head=BASE;
 const specs=[
  {id:'owner-manual-test',title:'Manual-only review',kind:'TEST',
   source:'owner',priority:999,
   subject:{base:head,chain:'node',operation:'MANUAL_REVIEW'}},
  {id:'owner-node-contract',title:'Node contract',kind:'TEST',
   source:'owner',priority:400,subject:task().subject}
 ];
 const ops=specs.flatMap(item=>[
  {op:'propose',actor:'owner',at:1,task:item},
  {op:'accept',actor:'owner',at:2,id:item.id}
 ]);
 const doc=applyOps(emptyDocument(0,'owner'),ops).doc;
 assert.equal(nextEligible(doc,['TEST'],['owner'],head).id,'owner-manual-test');
 const eligible=nextEligible(doc,['TEST'],['owner'],head,null,'ASSERT_NODE_EXPORT');
 assert.equal(eligible.id,'owner-node-contract');
 assert.ok(propose(eligible).candidate);
});

test('fresh generated contract runs real Node tests and incorrect expected output fails',
 {skip:Boolean(process.env.CONTINUUM_REPAIR_DEPTH),timeout:30000},()=>{
 const r=propose();
 assert.ok(r.candidate);
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-contract-'));
 try{
  const home=path.join(tmp,'.github','continuum');
  fs.mkdirSync(home,{recursive:true});
  fs.writeFileSync(path.join(home,'math-utils.mjs'),SOURCE);
  const generated=path.join(home,'math-utils.contract.test.mjs');
  fs.writeFileSync(generated,r.candidate.edits[0].newText);
  const {NODE_TEST_CONTEXT: _parentRunner,...childEnv}=process.env;
  const run=()=>spawnSync(process.execPath,['--test','--test-reporter=tap',generated],{
   cwd:tmp,encoding:'utf8',timeout:20000,env:childEnv});
  const green=run();
  assert.equal(green.status,0,green.stdout+'\n'+green.stderr);
  assert.match(green.stdout,/# pass 2/);
  fs.writeFileSync(generated,r.candidate.edits[0].newText.replace('triple(8),24','triple(8),25'));
  const red=run();
  assert.notEqual(red.status,0,'incorrect expected result must fail a real test');
  assert.match(red.stdout+red.stderr,/not deep.strictEqual|Expected values to be strictly deep-equal/i);
 }finally{fs.rmSync(tmp,{recursive:true,force:true});}
});

test('forged foreign, unaccepted and unsupported tasks are refused',()=>{
 const approved=canonicalTask();
 for(const invalid of [
  {...approved,state:'PROPOSED'}, {...approved,source:'web-text'},
  {...approved,subject:{...approved.subject,chain:'android'}},
  {...approved,subject:{...approved.subject,base:'x'.repeat(40)}},
  {...approved,subject:{...approved.subject,operation:'EXECUTE_SCRIPT'}},
  {...approved,subject:{...approved.subject,casesJson:'[1,2]'}},
  {...approved,subject:{...approved.subject,casesJson:'{}'}},
  {...approved,subject:{...approved.subject,casesJson:'[]'}},
  {...approved,subject:{...approved.subject,casesJson:'['}},
  {...approved,subject:{...approved.subject,modulePath:'../package.json'}},
  {...approved,subject:{...approved.subject,exportName:'constructor'}},
 ]){
  const result=propose(invalid);
  assert.equal(result.candidate,null,JSON.stringify(invalid.subject));
 }
 assert.equal(propose(approved, 'export const notTriple = n=>n*3;\n').candidate,null);
});

test('existing contract tests cannot be overwritten and critic preserves older tests',()=>{
 const approved=canonicalTask();
 const files=[{path:MODULE,content:SOURCE},
   {path:'.github/continuum/math-utils.contract.test.mjs',
    content:"test('do not replace',()=>{});\n"}];
 assert.equal(proposeNodeContractTest({files,task:approved}).candidate,null);
 const generated=propose().candidate;
 assert.ok(generated);
 assert.equal(critique({...generated,edits:[{
  file:generated.edits[0].file,oldText:'already exists',newText:generated.edits[0].newText
 }]}).approved,false);
 assert.equal(critique({strategy:'SYNTHESIZE_NODE_CONTRACT_TEST',edits:[{
  file:generated.edits[0].file,oldText:'',newText:'test("fake",()=>{});'
 }]}).approved,false);
});
