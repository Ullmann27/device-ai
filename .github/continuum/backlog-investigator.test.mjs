import test from 'node:test';
import assert from 'node:assert/strict';
import {buildIndex,integrationFindings} from './code-analyzer.mjs';
import {investigateFinding,verifyInvestigation} from './backlog-investigator.mjs';

const files=[
 {path:'main/java/p/App.kt',content:'package p\nclass App { fun live() { Connected().go() } }\nclass Connected { fun go()=1 }'},
 {path:'main/java/p/Lab.kt',content:'package p\nclass Orphan\nclass Isolated'}
];
const index=buildIndex(files);
const findings=integrationFindings(index,{productionRoots:['p.App']}).findings;
const analysis={index,findings};
test('INVESTIGATE worker reproduces findings but never invents wiring patches',()=>{
 const orphan=findings.find(f=>f.symbol==='p.Orphan');
 assert.ok(orphan);
 const task={id:'inv-0000000000000001',kind:'INVESTIGATE',subject:{
  finding:orphan.kind,file:'app/src/'+orphan.file,symbol:orphan.symbol,confidence:orphan.confidence
 }};
 const report=investigateFinding(analysis,task);
 assert.equal(report.kind,'REPRODUCED_STATIC_FINDING');
 assert.equal(report.proposedAction,'REVIEW_WIRING_AND_ADD_RUNTIME_TEST');
 assert.ok(verifyInvestigation(report,analysis,task));
 assert.ok(!verifyInvestigation({...report,kind:'VERIFIED_WIRING'},analysis,task));
});
test('INVESTIGATE declines changes for findings no longer present',()=>{
 const task={id:'inv-0000000000000002',kind:'INVESTIGATE',subject:{
  finding:'UNREFERENCED',file:'app/src/main/java/p/App.kt',symbol:'p.App',confidence:'high'
 }};
 const report=investigateFinding(analysis,task);
 assert.equal(report.kind,'NOT_A_STATIC_FINDING');
 assert.equal(report.proposedAction,'NO_AUTOMATIC_CHANGE');
});
test('only accepted structural INVESTIGATE tasks are eligible',()=>{
 assert.throws(()=>investigateFinding(analysis,{id:'x',kind:'TEST',subject:{}}),/structural/);
});
