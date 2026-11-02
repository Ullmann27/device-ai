import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {gateSummary,verifyGateReport} from './m4-local-gate.mjs';
import {MANDATORY_REPAIR_CASES} from './m4-repair-e2e.mjs';

const node={passed:true,results:[
 {profile:'node-continuum',passed:true,testProfile:true,exitCode:0,
  tests:{executed:5,failed:0}}
]};
const repair={passed:true,executed:4,mandatoryRepairs:[...MANDATORY_REPAIR_CASES],
 sha256:'a'.repeat(64)};

test('M4 always rejects a green Node run without an actual repair E2E receipt',()=>{
 const proof=gateSummary(node,null,null,false,null);
 assert.equal(proof.nodePassed,true);
 assert.equal(proof.repairE2ePassed,false);
 assert.equal(proof.passed,false);
});

test('M4 accepts only exactly shaped mandatory repair receipts',()=>{
 assert.equal(gateSummary(node,null,null,false,repair).passed,true);
 for(const invalid of [
  null,{...repair,passed:false},{...repair,executed:0},
  {...repair,sha256:'x'.repeat(64)},{...repair,mandatoryRepairs:[]},
  {...repair,mandatoryRepairs:['not a repair']},{...repair,executed:'4'}
 ])assert.equal(gateSummary(node,null,null,false,invalid).passed,false,
  JSON.stringify(invalid));
});

test('old version-1 M4 release documents cannot silently bypass mandatory repair proof',()=>{
 const head='a'.repeat(40);
 const android={passed:true,results:[
  {profile:'android-compile',passed:true,testProfile:false,exitCode:0,
   tests:{executed:0,failed:0}},
  {profile:'android-unit',passed:true,testProfile:true,exitCode:0,
   tests:{executed:5,failed:0}}
 ]};
 const fullNode={passed:true,results:[...node.results,{
  profile:'node-backend',passed:true,testProfile:true,exitCode:0,
  tests:{executed:3,failed:0}
 }]};
 const apk={exitCode:0,timedOut:false,bytes:4096,artifactSha256:'b'.repeat(64)};
 const oldSummary=gateSummary(fullNode,android,apk,true);
 const oldDocument={schema:'continuum-m4-local-gate',version:1,head,
  startedAt:'2026-10-17T00:00:00Z',node:fullNode,android,apk,errors:[],
  complete:true,summary:oldSummary,finishedAt:'2026-10-17T00:00:01Z'};
 oldDocument.reportSha256=crypto.createHash('sha256').update(JSON.stringify(oldDocument)).digest('hex');
 assert.equal(verifyGateReport(oldDocument,{expectedHead:head}).releaseReady,false);
});
