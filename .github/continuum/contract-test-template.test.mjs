import test from 'node:test';
import assert from 'node:assert/strict';
import {renderOwnerNodeContract,CONTRACT_PREFIX} from './contract-test-template.mjs';
import {critique} from './repair-critic.mjs';

const file='.github/continuum/math-utils.contract.test.mjs';
const cases=JSON.stringify([{args:[1],expected:3},{args:[4],expected:12}]);
function candidate(text,oldText=''){
 return {strategy:'SYNTHESIZE_NODE_CONTRACT_TEST',prior:0.5,
  contractSpec:{file,exportName:'triple',casesJson:cases},
  edits:[{file,oldText,newText:text}]};
}

test('only exact deterministic source rendered from bounded owner cases passes the critic',()=>{
 const correct=renderOwnerNodeContract(file,'triple',cases);
 assert.ok(correct.startsWith(CONTRACT_PREFIX));
 assert.equal(critique(candidate(correct)).approved,true);
 const threats=[
  correct+"test('fraud',()=>{});\n",
  correct.replace('triple(4),12','triple(4),4'),
  correct.replace("import { triple }","import { compromised }"),
  correct.replace("from \"./math-utils.mjs\"","from \"./remote.mjs\""),
  CONTRACT_PREFIX+"import test from 'node:test';\ntest('fake',()=>{});\n"
 ];
 for(const text of threats)assert.equal(critique(candidate(text)).approved,false,text.slice(0,100));
});

test('cannot overwrite an existing contract or any pre-existing test',()=>{
 const content=renderOwnerNodeContract(file,'triple',cases);
 assert.equal(critique(candidate(content,'existing contract')).approved,false);
 assert.equal(critique({strategy:'SYNTHESIZE_NODE_CONTRACT_TEST',
  contractSpec:{file:'.github/continuum/repair-critic.test.mjs',
   exportName:'triple',casesJson:cases},
  edits:[{file:'.github/continuum/repair-critic.test.mjs',
   oldText:'',newText:content}]}).approved,false);
});

test('invalid cases, non-primitive values and arbitrary module paths cannot be rendered',()=>{
 const invalid=[
  '[]','{}','null','not json',
  JSON.stringify([{args:[{object:'code'}],expected:3}]),
  JSON.stringify([{args:[2],expected:{result:6}}]),
  JSON.stringify([{args:[1],expected:'x'.repeat(121)}]),
  JSON.stringify(Array(5).fill({args:[1],expected:3}))
 ];
 for(const bad of invalid)
  assert.equal(renderOwnerNodeContract(file,'triple',bad),null);
 assert.equal(renderOwnerNodeContract('../etc/passwd','triple',cases),null);
 assert.equal(renderOwnerNodeContract(file,'evil();',cases),null);
 assert.equal(renderOwnerNodeContract(file,'triple','a'.repeat(301)),null);
});


test('automatic repair candidates cannot rewrite contract-template, backlog or teamleader authority',()=>{
 for(const path of [
  '.github/continuum/contract-test-template.mjs',
  '.github/continuum/development-backlog.mjs',
  '.github/continuum/node-contract-synthesizer.mjs',
  '.github/continuum/owner-contract-worker.mjs',
  '.github/continuum/autonomous-teamleader.mjs',
  '.github/continuum/backlog-cli.mjs',
  '.github/continuum/kotlin-delegate-synthesizer.mjs',
  '.github/continuum/kotlin-delegate-acceptance.mjs',
  '.github/continuum/owner-kotlin-preview.mjs',
  '.github/continuum/local-teamleader-loop.mjs'
 ]){
  const result=critique({strategy:'JS_FIX_IDENTIFIER_TYPO',
   edits:[{file:path,oldText:'const flag = false;',newText:'const flag = true;'}]});
  assert.equal(result.approved,false,path);
  assert.match(result.reasons.join(', '),/protected path/);
 }
});
