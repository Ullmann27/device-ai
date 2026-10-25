import test from 'node:test';
import assert from 'node:assert/strict';
import {inventoryFromOwnedNpmLock} from './npm-lock-inventory.mjs';
import {evaluateOwnedOsvSnapshot} from './advisory-intake.mjs';
const commit='a'.repeat(40);
const lock={lockfileVersion:3,packages:{'':{dependencies:{'@team/util':'1.0.0'},devDependencies:{cli:'2.0.0'}},
  'node_modules/@team/util':{version:'1.0.0'},'node_modules/cli':{version:'2.0.0'},
  'node_modules/cli/node_modules/transitive':{version:'0.2.3'}}};
const parse=x=>inventoryFromOwnedNpmLock(x,{project:'owner-lab',sourceCommit:commit});
test('npm v3 lock inventory keeps direct and transitive versions',()=>{
 const {inventory,coverage,unresolvedEntries,lockfileSha256}=parse(lock);
 assert.equal(coverage,'NPM_LOCK_PACKAGES_ENUMERATED');assert.equal(unresolvedEntries,0);
 assert.match(lockfileSha256,/^[a-f0-9]{64}$/);
 assert.equal(inventory.components.length,3);
 assert.equal(inventory.components.find(x=>x.name==='transitive').direct,false);
 assert.equal(inventory.components.find(x=>x.name==='@team/util').direct,true);
});
test('npm v2 packages accepted, identical nested versions deduplicated',()=>{
 const two={...lock,lockfileVersion:2,packages:{...lock.packages,
 'node_modules/deeper/node_modules/transitive':{version:'0.2.3'}}};
 assert.equal(parse(two).inventory.components.length,3);
});
test('unresolved workspace/link dependencies are flagged, never omitted as safe',()=>{
 const x={...lock,packages:{...lock.packages,'packages/workspace':{version:'1.0.0'},
  'node_modules/link':{link:true,resolved:'packages/workspace'}}};
 const result=parse(x);
 assert.equal(result.unresolvedEntries,2);
 assert.equal(result.coverage,'INCOMPLETE_INVENTORY_REVIEW_REQUIRED');
});
test('reject malformed npm lock and unpinned provenance',()=>{
 for(const invalid of [{}, {lockfileVersion:1,packages:lock.packages},
 {lockfileVersion:3,packages:{'':null}},
 {...lock,packages:{...lock.packages,'node_modules/../../secret':{version:'1.0.0'}}}]){
   assert.throws(()=>parse(invalid),/OMEGA_NPM_LOCK_INVALID/);
 }
 assert.throws(()=>inventoryFromOwnedNpmLock(lock,{project:'owner-lab',sourceCommit:'main'}),/OMEGA_NPM_LOCK_INVALID/);
});
test('integration: owned npm lock and OSV evidence produce only a review finding',()=>{
 const {inventory}=parse(lock);
 const advisories=[{id:'OSV-2026-TEST',affected:[{package:{ecosystem:'npm',name:'transitive'},versions:['0.2.3']}]}];
 const r=evaluateOwnedOsvSnapshot({inventory,advisories});
 assert.equal(r.findings.length,1);
 assert.equal(r.findings[0].installedVersion,'0.2.3');
 assert.equal(r.automaticActions,0);
 assert.equal(r.vulnerabilityFreeClaim,false);
});
